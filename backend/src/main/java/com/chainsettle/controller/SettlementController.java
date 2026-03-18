package com.chainsettle.controller;

import com.chainsettle.model.dto.DvPRequest;
import com.chainsettle.model.dto.SettlementResponse;
import com.chainsettle.service.OrgIdentityService;
import com.chainsettle.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settlements/dvp")
public class SettlementController {
    private final SettlementService settlementService;
    private final OrgIdentityService orgIdentityService;

    public SettlementController(final SettlementService settlementService, final OrgIdentityService orgIdentityService) {
        this.settlementService = settlementService;
        this.orgIdentityService = orgIdentityService;
    }

    @PostMapping
    @Operation(summary = "Initiate a DvP settlement")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "DvP settlement initiated"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<SettlementResponse> initiate(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @Valid @RequestBody final DvPRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(settlementService.initiate(orgIdentityService.requireOrg(orgHeader), request));
    }

    @PostMapping("/{settlementId}/execute")
    @Operation(summary = "Execute a DvP settlement")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "DvP settlement executed"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "404", description = "Settlement not found"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public SettlementResponse execute(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @PathVariable final String settlementId,
        @RequestParam(value = "clientRequestId", required = false) final String clientRequestId
    ) {
        return settlementService.execute(orgIdentityService.requireOrg(orgHeader), settlementId, clientRequestId);
    }

    @PostMapping("/{settlementId}/cancel")
    @Operation(summary = "Cancel a pending DvP settlement")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "DvP settlement cancelled"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "404", description = "Settlement not found"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public SettlementResponse cancel(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @PathVariable final String settlementId,
        @RequestParam(value = "reason", required = false) final String reason,
        @RequestParam(value = "clientRequestId", required = false) final String clientRequestId
    ) {
        return settlementService.cancel(orgIdentityService.requireOrg(orgHeader), settlementId, reason, clientRequestId);
    }

    @GetMapping("/{settlementId}")
    @Operation(summary = "Get DvP settlement details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settlement returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization header"),
        @ApiResponse(responseCode = "404", description = "Settlement not found"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public SettlementResponse getSettlement(
        @RequestHeader(value = "X-ChainSettle-Org", required = false) final String orgHeader,
        @PathVariable final String settlementId
    ) {
        return settlementService.getSettlement(orgIdentityService.resolveReadOrg(orgHeader), settlementId);
    }

    @GetMapping
    @Operation(summary = "List DvP settlements")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Settlements returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public List<SettlementResponse> listSettlements(
        @RequestHeader(value = "X-ChainSettle-Org", required = false) final String orgHeader
    ) {
        return settlementService.getAllSettlements(orgIdentityService.resolveReadOrg(orgHeader));
    }
}
