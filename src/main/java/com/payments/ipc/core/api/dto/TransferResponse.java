package com.payments.ipc.core.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class TransferResponse {
    private UUID transactionId;
    private String status;
    private String message;
}