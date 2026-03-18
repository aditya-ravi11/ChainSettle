package com.chainsettle.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BurnTokenRequest(
    @NotBlank String accountId,
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    String clientRequestId
) {
}

