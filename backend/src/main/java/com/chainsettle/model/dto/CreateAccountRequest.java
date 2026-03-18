package com.chainsettle.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAccountRequest(
    @NotBlank String accountId,
    @NotBlank String orgName,
    @NotBlank String currency,
    @NotNull @DecimalMin("0.00") BigDecimal initialBalance,
    @NotBlank String accountType,
    String assetType,
    String clientRequestId
) {
}

