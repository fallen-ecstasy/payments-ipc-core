package com.payments.ipc.core.service;

import com.payments.ipc.core.api.dto.TransferRequest;
import com.payments.ipc.core.api.dto.TransferResponse;
import com.payments.ipc.core.entity.Account;
import com.payments.ipc.core.entity.LedgerEntry;
import com.payments.ipc.core.entity.Transaction;
import com.payments.ipc.core.repository.AccountRepository;
import com.payments.ipc.core.repository.LedgerEntryRepository;
import com.payments.ipc.core.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepo;
    private final TransactionRepository transactionRepo;
    private final LedgerEntryRepository entryRepo;

    // Wrap the entire process in a single ACID transaction.
    // If anything fails, the database rolls back completely.
    @Transactional
    public TransferResponse executeTransfer(TransferRequest request, String idempotencyKey) {
        log.info("Starting transfer of {} from {} to {}", request.getAmount(), request.getSourceAccountId(), request.getDestinationAccountId());

        // 1. Fetch & Lock Accounts (Prevents Race Conditions)
        // Note: In a true massive-scale system, you must sort the IDs before locking to prevent Deadlocks.
        Account source = accountRepo.findByIdForUpdate(request.getSourceAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Source account not found"));
        Account destination = accountRepo.findByIdForUpdate(request.getDestinationAccountId())
                .orElseThrow(() -> new IllegalArgumentException("Destination account not found"));

        // 2. Business Validation
        if (source.getId().equals(destination.getId())) {
            throw new IllegalArgumentException("Cannot transfer funds to the same account");
        }
        if (source.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient funds in source account");
        }

        // 3. Record the Transaction
        Transaction tx = new Transaction(idempotencyKey, "COMPLETED");
        transactionRepo.save(tx);

        // 4. Create Immutable Ledger Entries (The Double-Entry Rule)
        LedgerEntry debit = new LedgerEntry(tx, source, request.getAmount(), "DEBIT");
        LedgerEntry credit = new LedgerEntry(tx, destination, request.getAmount(), "CREDIT");
        entryRepo.saveAll(List.of(debit, credit));

        // 5. Update Balances
        source.setBalance(source.getBalance().subtract(request.getAmount()));
        destination.setBalance(destination.getBalance().add(request.getAmount()));
        accountRepo.saveAll(List.of(source, destination));

        log.info("Transfer completed successfully. Transaction ID: {}", tx.getId());
        return new TransferResponse(tx.getId(), "COMPLETED", "Transfer successful");
    }
}