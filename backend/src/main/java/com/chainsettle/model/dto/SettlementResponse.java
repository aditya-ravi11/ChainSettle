package com.chainsettle.model.dto;

public record SettlementResponse(
    String settlementId,
    String clientRequestId,
    DvPRequest.SettlementLegRequest cashLeg,
    DvPRequest.SettlementLegRequest assetLeg,
    String status,
    String initiatedBy,
    String createdAt,
    String completedAt,
    String failureReason
) {
}

