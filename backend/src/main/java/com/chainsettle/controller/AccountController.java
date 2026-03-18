package com.chainsettle.controller;

import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.model.dto.CreateAccountRequest;
import com.chainsettle.service.AccountService;
import com.chainsettle.service.OrgIdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountService accountService;
    private final OrgIdentityService orgIdentityService;

    public AccountController(final AccountService accountService, final OrgIdentityService orgIdentityService) {
        this.accountService = accountService;
        this.orgIdentityService = orgIdentityService;
    }

    @PostMapping
    @Operation(summary = "Create a token account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public ResponseEntity<AccountResponse> createAccount(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @Valid @RequestBody final CreateAccountRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(accountService.createAccount(orgIdentityService.requireOrg(orgHeader), request));
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get a token account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization header"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public AccountResponse getAccount(
        @RequestHeader(value = "X-ChainSettle-Org", required = false) final String orgHeader,
        @PathVariable final String accountId
    ) {
        return accountService.getAccount(orgIdentityService.resolveReadOrg(orgHeader), accountId);
    }

    @GetMapping
    @Operation(summary = "List accounts")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Accounts returned"),
        @ApiResponse(responseCode = "400", description = "Invalid organization header"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public List<AccountResponse> listAccounts(
        @RequestHeader(value = "X-ChainSettle-Org", required = false) final String orgHeader,
        @RequestParam(value = "org", required = false) final String orgName
    ) {
        return accountService.listAccounts(orgIdentityService.resolveReadOrg(orgHeader), orgName);
    }

    @DeleteMapping("/{accountId}")
    @Operation(summary = "Deactivate an account")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Account deactivated"),
        @ApiResponse(responseCode = "400", description = "Invalid request or organization header"),
        @ApiResponse(responseCode = "404", description = "Account not found"),
        @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public AccountResponse deactivateAccount(
        @RequestHeader("X-ChainSettle-Org") final String orgHeader,
        @PathVariable final String accountId
    ) {
        return accountService.deactivateAccount(orgIdentityService.requireOrg(orgHeader), accountId);
    }
}
