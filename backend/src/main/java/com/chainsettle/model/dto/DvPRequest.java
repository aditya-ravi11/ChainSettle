package com.chainsettle.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Schema(name = "DvPRequest", description = "Delivery versus payment initiation request")
public record DvPRequest(
    @Valid @NotNull SettlementLegRequest cashLeg,
    @Valid @NotNull SettlementLegRequest assetLeg,
    String clientRequestId
) {
    public record SettlementLegRequest(
        @NotBlank String fromAccountId,
        @NotBlank String toAccountId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String currency,
        @Schema(example = "BOND-US10Y") String asset
    ) {
    }
}

