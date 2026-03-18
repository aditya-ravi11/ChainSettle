package com.chainsettle.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(name = "AccountResponse", description = "Ledger-backed account view")
public record AccountResponse(
    @Schema(example = "ACC-BANKALPHA-001") String accountId,
    @Schema(example = "BankAlpha") String orgName,
    @Schema(example = "USD") String currency,
    @Schema(example = "1000000.00") BigDecimal balance,
    @Schema(example = "ACTIVE") String status,
    @Schema(example = "CASH") String accountType,
    @Schema(example = "BOND-US10Y") String assetType,
    String createdAt,
    String updatedAt
) {
}

