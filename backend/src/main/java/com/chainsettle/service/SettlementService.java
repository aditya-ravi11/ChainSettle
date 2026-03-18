package com.chainsettle.service;

import com.chainsettle.exception.SettlementFailedException;
import com.chainsettle.model.dto.DvPRequest;
import com.chainsettle.model.dto.SettlementResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SettlementService {
    private final FabricGatewayService fabricGatewayService;
    private final ObjectMapper objectMapper;

    public SettlementService(final FabricGatewayService fabricGatewayService, final ObjectMapper objectMapper) {
        this.fabricGatewayService = fabricGatewayService;
        this.objectMapper = objectMapper;
    }

    public SettlementResponse initiate(final String org, final DvPRequest request) {
        try {
            final String payload = fabricGatewayService.submitTransaction(
                org,
                "InitiateDvP",
                writeJson(request.cashLeg()),
                writeJson(request.assetLeg()),
                request.clientRequestId() == null ? generatedId("dvp") : request.clientRequestId()
            );
            return parseSettlement(payload);
        } catch (IllegalStateException exception) {
            throw new SettlementFailedException(exception.getMessage());
        }
    }

    public SettlementResponse execute(final String org, final String settlementId, final String clientRequestId) {
        try {
            return parseSettlement(fabricGatewayService.submitTransaction(
                org,
                "ExecuteDvP",
                settlementId,
                clientRequestId == null ? generatedId("dvp-execute") : clientRequestId
            ));
        } catch (IllegalStateException exception) {
            throw new SettlementFailedException(exception.getMessage());
        }
    }

    public SettlementResponse cancel(final String org, final String settlementId, final String reason, final String clientRequestId) {
        try {
            return parseSettlement(fabricGatewayService.submitTransaction(
                org,
                "CancelDvP",
                settlementId,
                reason == null ? "Cancelled via API" : reason,
                clientRequestId == null ? generatedId("dvp-cancel") : clientRequestId
            ));
        } catch (IllegalStateException exception) {
            throw new SettlementFailedException(exception.getMessage());
        }
    }

    public SettlementResponse getSettlement(final String org, final String settlementId) {
        return parseSettlement(fabricGatewayService.evaluateTransaction(org, "GetDvPSettlement", settlementId));
    }

    public List<SettlementResponse> getAllSettlements(final String org) {
        try {
            final List<Map<String, Object>> settlements = objectMapper.readValue(
                fabricGatewayService.evaluateTransaction(org, "GetAllDvPSettlements"),
                new TypeReference<>() {
                }
            );
            return settlements.stream().map(this::convertSettlement).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse settlements", exception);
        }
    }

    public SettlementResponse parseSettlement(final String payload) {
        try {
            return convertSettlement(objectMapper.readValue(payload, new TypeReference<>() {
            }));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse settlement payload", exception);
        }
    }

    private SettlementResponse convertSettlement(final Map<String, Object> map) {
        return new SettlementResponse(
            (String) map.get("settlementId"),
            (String) map.get("clientRequestId"),
            objectMapper.convertValue(map.get("leg1"), DvPRequest.SettlementLegRequest.class),
            objectMapper.convertValue(map.get("leg2"), DvPRequest.SettlementLegRequest.class),
            (String) map.get("status"),
            (String) map.get("initiatedBy"),
            (String) map.get("createdAt"),
            (String) map.get("completedAt"),
            (String) map.get("failureReason")
        );
    }

    private String generatedId(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String writeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize settlement request", exception);
        }
    }
}

