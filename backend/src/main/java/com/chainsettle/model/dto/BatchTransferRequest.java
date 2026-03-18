package com.chainsettle.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BatchTransferRequest(
    @NotEmpty List<@Valid TransferRequest> transfers,
    String clientRequestId
) {
}

