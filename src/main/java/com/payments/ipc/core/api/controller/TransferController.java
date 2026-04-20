package com.payments.ipc.core.api.controller;

import com.payments.ipc.core.api.dto.TransferRequest;
import com.payments.ipc.core.api.dto.TransferResponse;
import com.payments.ipc.core.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final LedgerService ledgerService;

    @PostMapping
    public ResponseEntity<TransferResponse> initiateTransfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // We pass the payload and the key to our @Transactional service
        TransferResponse response = ledgerService.executeTransfer(request, idempotencyKey);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}