package com.chainsettle.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;

@Schema(name = "TransferResponse", description = "Transfer submission result")
public record TransferResponse(
    @Schema(example = "TXN-20260318-000042") String txId,
    String status,
    String txType,
    String fromAccountId,
    String toAccountId,
    String fromOrg,
    String toOrg,
    BigDecimal amount,
    String currency,
    String timestamp,
    Long blockNumber,
    String fabricTxId,
    String settlementRef,
    Map<String, Object> metadata
) {
}

