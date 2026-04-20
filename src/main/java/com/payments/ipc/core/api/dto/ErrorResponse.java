package com.payments.ipc.core.api.dto;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;

    // Used for returning specific field validation errors (e.g., {"amount": "must be greater than zero"})
    private Map<String, String> validationErrors;
}