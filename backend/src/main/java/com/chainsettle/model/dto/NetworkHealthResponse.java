package com.chainsettle.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "NetworkHealthResponse", description = "Fabric network health summary")
public record NetworkHealthResponse(
    String status,
    String channelName,
    String chaincodeName,
    List<PeerStatus> peers,
    Instant timestamp,
    String message
) {
    public record PeerStatus(String organization, String endpoint, boolean connected) {
    }
}

