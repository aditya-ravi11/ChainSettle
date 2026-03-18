package com.chainsettle.controller;

import com.chainsettle.model.dto.NetworkHealthResponse;
import com.chainsettle.service.EventListenerService;
import com.chainsettle.service.FabricGatewayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/network")
public class NetworkController {
    private final FabricGatewayService fabricGatewayService;
    private final EventListenerService eventListenerService;

    public NetworkController(
        final FabricGatewayService fabricGatewayService,
        final EventListenerService eventListenerService
    ) {
        this.fabricGatewayService = fabricGatewayService;
        this.eventListenerService = eventListenerService;
    }

    @GetMapping("/health")
    @Operation(summary = "Get overall Fabric network health")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Network health returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public NetworkHealthResponse getHealth() {
        return fabricGatewayService.getNetworkHealth();
    }

    @GetMapping("/peers")
    @Operation(summary = "Get peer connectivity state")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Peer status returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public java.util.List<NetworkHealthResponse.PeerStatus> getPeers() {
        return fabricGatewayService.getNetworkHealth().peers();
    }

    @GetMapping("/channel")
    @Operation(summary = "Get channel metadata")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Channel metadata returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public Map<String, Object> getChannel() {
        final NetworkHealthResponse health = fabricGatewayService.getNetworkHealth();
        return Map.of(
            "channelName", health.channelName(),
            "chaincodeName", health.chaincodeName(),
            "lastEventAt", eventListenerService.getLastEventAt(),
            "status", health.status()
        );
    }
}
