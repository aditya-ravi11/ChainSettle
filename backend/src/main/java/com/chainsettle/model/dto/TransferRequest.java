package com.chainsettle.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

@Schema(name = "TransferRequest", description = "Token transfer request")
public record TransferRequest(
    @NotBlank String fromAccountId,
    @NotBlank String toAccountId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank String currency,
    Map<String, Object> metadata,
    String clientRequestId
) {
}

