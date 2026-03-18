package com.chainsettle.model.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record BalanceSnapshotResponse(
    String accountId,
    String orgName,
    String currency,
    String accountType,
    String assetType,
    BigDecimal balance,
    Instant snapshotTime
) {
}

