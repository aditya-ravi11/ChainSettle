package com.chainsettle.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "AnalyticsSummary", description = "Network-wide analytics rollup")
public record AnalyticsSummary(
    long totalTransactions,
    long totalSettlements,
    long activeAccounts,
    BigDecimal totalVolume,
    String networkStatus,
    Instant lastUpdated
) {
}

