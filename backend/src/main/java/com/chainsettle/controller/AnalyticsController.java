package com.chainsettle.controller;

import com.chainsettle.model.dto.AnalyticsSummary;
import com.chainsettle.model.dto.BalanceSnapshotResponse;
import com.chainsettle.model.dto.TransferResponse;
import com.chainsettle.service.AnalyticsService;
import com.chainsettle.service.OrgIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    private final OrgIdentityService orgIdentityService;

    public AnalyticsController(final AnalyticsService analyticsService, final OrgIdentityService orgIdentityService) {
        this.analyticsService = analyticsService;
        this.orgIdentityService = orgIdentityService;
    }

    @GetMapping("/volume")
    @Operation(summary = "Get daily settlement volume for an organization")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Daily volume returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization or date"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public Map<String, Object> getVolume(
        @RequestParam("org") final String orgName,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate date
    ) {
        return analyticsService.getDailyVolume(orgIdentityService.normalize(orgName), date);
    }

    @GetMapping("/net-position")
    @Operation(summary = "Get net settlement position for an organization")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Net position returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public Map<String, Object> getNetPosition(@RequestParam("org") final String orgName) {
        return analyticsService.getNetPosition(orgIdentityService.normalize(orgName));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get overall analytics summary")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Analytics summary returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public AnalyticsSummary getSummary() {
        return analyticsService.getSummary();
    }

    @GetMapping("/balances")
    @Operation(summary = "Get latest balances for dashboard visualizations")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Balance snapshots returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public List<BalanceSnapshotResponse> getBalances() {
        return analyticsService.getBalances();
    }

    @GetMapping("/perspective-feed")
    @Operation(summary = "Get initial Perspective.js data feed")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Perspective feed returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public List<TransferResponse> getPerspectiveFeed() {
        return analyticsService.getPerspectiveFeed();
    }
}
