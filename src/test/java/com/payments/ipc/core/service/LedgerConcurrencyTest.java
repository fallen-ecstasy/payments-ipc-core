package com.payments.ipc.core.service;


import com.payments.ipc.core.api.dto.TransferRequest;
import com.payments.ipc.core.entity.Account;
import com.payments.ipc.core.repository.AccountRepository;
import com.payments.ipc.core.repository.LedgerEntryRepository;
import com.payments.ipc.core.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class LedgerConcurrencyTest {

    @Autowired
    private LedgerService ledgerService;
    @Autowired
    private AccountRepository accountRepo;
    @Autowired
    private TransactionRepository transactionRepo;
    @Autowired
    private LedgerEntryRepository entryRepo;

    private UUID accountAId;
    private UUID accountBId;

    @BeforeEach
    void setUp() {
        // Clean the database before each test
        entryRepo.deleteAll();
        transactionRepo.deleteAll();
        accountRepo.deleteAll();

        // Seed Account A with $100.00
        Account accountA = Account.builder()
                .name("Source Account")
                .balance(new BigDecimal("100.00"))
                .build();
        accountA = accountRepo.save(accountA);
        accountAId = accountA.getId();

        // Seed Account B with $0.00
        Account accountB = Account.builder()
                .name("Destination Account")
                .balance(new BigDecimal("0.00"))
                .build();
        accountB = accountRepo.save(accountB);
        accountBId = accountB.getId();
    }

    @Test
    void testConcurrentTransfers_PessimisticLockingPreventsOverdraft() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        // The latch ensures all threads wait until we count down to zero,
        // then they all execute exactly at the same time.
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successfulTransfers = new AtomicInteger(0);
        AtomicInteger failedTransfers = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executorService.execute(() -> {
                try {
                    startLatch.await(); // Wait for the starting gun

                    TransferRequest request = new TransferRequest();
                    request.setSourceAccountId(accountAId);
                    request.setDestinationAccountId(accountBId);
                    request.setAmount(new BigDecimal("10.00"));

                    // Generate a unique idempotency key for each thread so Redis doesn't block them.
                    // We are testing database row-locking here, not the Redis interceptor.
                    ledgerService.executeTransfer(request, UUID.randomUUID().toString());

                    successfulTransfers.incrementAndGet();
                } catch (Exception e) {
                    // We expect some of these to throw an IllegalStateException ("Insufficient funds")
                    failedTransfers.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // FIRE!
        startLatch.countDown();

        // Wait for all threads to finish
        endLatch.await();
        executorService.shutdown();

        // --- ASSERTIONS ---
        Account finalAccountA = accountRepo.findById(accountAId).orElseThrow();
        Account finalAccountB = accountRepo.findById(accountBId).orElseThrow();

        // If pessimistic locking failed, multiple threads would read $100, withdraw $10,
        // and overwrite each other, leaving Account A with $90 instead of $0.
        // Because of our @Lock, the database processes them sequentially.
        assertEquals(new BigDecimal("0.00"), finalAccountA.getBalance().stripTrailingZeros());
        assertEquals(new BigDecimal("100.00"), finalAccountB.getBalance().stripTrailingZeros());

        // Out of 10 concurrent requests trying to pull $10 from a $100 account,
        // exactly 10 should succeed, and 0 should fail.
        assertEquals(10, successfulTransfers.get());
        assertEquals(0, failedTransfers.get());

        // Verify double-entry ledger mathematically balances
        long totalLedgerEntries = entryRepo.count();
        assertEquals(20, totalLedgerEntries); // 10 transactions * 2 entries (debit/credit)
    }
}
