package com.chainsettle.controller;

import com.chainsettle.model.dto.BatchTransferRequest;
import com.chainsettle.model.dto.BurnTokenRequest;
import com.chainsettle.model.dto.MintTokenRequest;
import com.chainsettle.model.dto.TransferRequest;
import com.chainsettle.model.dto.TransferResponse;
import com.chainsettle.service.OrgIdentityService;
import com.chainsettle.service.TransferService;
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
@RequestMapping("/api/v1")
public class TransferController {
    private final TransferService transferService;
    private final OrgIdentityService orgIdentityService;

    public TransferController(final TransferService transferService, final OrgIdentityService orgIdentityService) {
        this.transferService = transferService;
        this.orgIdentityService = orgIdentityService;
    }

    @PostMapping("/tokens/mint")
    @Operation(summary = "Mint tokens into an account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tokens minted"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<TransferResponse> mint(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @Valid @RequestBody final MintTokenRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transferService.mint(orgIdentityService.requireOrg(orgHeader), request));
    }

    @PostMapping("/tokens/burn")
    @Operation(summary = "Burn tokens from an account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Tokens burned"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<TransferResponse> burn(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @Valid @RequestBody final BurnTokenRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transferService.burn(orgIdentityService.requireOrg(orgHeader), request));
    }

    @PostMapping("/transfers")
    @Operation(summary = "Submit a token transfer")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Transfer submitted"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<TransferResponse> transfer(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @Valid @RequestBody final TransferRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transferService.transfer(orgIdentityService.requireOrg(orgHeader), request));
    }

    @PostMapping("/transfers/batch")
    @Operation(summary = "Submit a batch transfer")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Batch transfer submitted"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<List<TransferResponse>> batchTransfer(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @Valid @RequestBody final BatchTransferRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(transferService.batchTransfer(orgIdentityService.requireOrg(orgHeader), request));
    }

    @GetMapping("/transfers/{txId}")
    @Operation(summary = "Get transfer details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization header"),
        @ApiResponse(responseCode = "404", description = "Transfer not found"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public TransferResponse getTransfer(
        @RequestHeader(value = "X-ChainSettle-Org", required = false) final String orgHeader,
        @PathVariable final String txId
    ) {
        return transferService.getTransaction(orgIdentityService.resolveReadOrg(orgHeader), txId);
    }

    @GetMapping("/transfers")
    @Operation(summary = "Get transaction history for an account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transfer history returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public List<TransferResponse> getTransferHistory(
        @RequestHeader(value = "X-ChainSettle-Org", required = false) final String orgHeader,
        @RequestParam("account") final String accountId,
        @RequestParam(value = "limit", defaultValue = "100") final int limit
    ) {
        return transferService.getHistory(orgIdentityService.resolveReadOrg(orgHeader), accountId, limit);
    }
}
