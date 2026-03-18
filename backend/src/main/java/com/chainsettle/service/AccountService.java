package com.chainsettle.service;

import com.chainsettle.exception.AccountNotFoundException;
import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.model.dto.CreateAccountRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountService {
    private final FabricGatewayService fabricGatewayService;
    private final ObjectMapper objectMapper;

    public AccountService(final FabricGatewayService fabricGatewayService, final ObjectMapper objectMapper) {
        this.fabricGatewayService = fabricGatewayService;
        this.objectMapper = objectMapper;
    }

    public AccountResponse createAccount(final String org, final CreateAccountRequest request) {
        final String payload = fabricGatewayService.submitTransaction(
            org,
            "CreateAccount",
            request.accountId(),
            request.orgName(),
            request.currency(),
            request.initialBalance().toPlainString(),
            request.accountType(),
            request.assetType() == null ? "" : request.assetType(),
            request.clientRequestId() == null ? generatedId("acct") : request.clientRequestId()
        );
        return parseAccount(payload);
    }

    public AccountResponse getAccount(final String org, final String accountId) {
        try {
            return parseAccount(fabricGatewayService.evaluateTransaction(org, "GetAccount", accountId));
        } catch (IllegalStateException exception) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
    }

    public List<AccountResponse> listAccounts(final String org, final String orgFilter) {
        final String payload = orgFilter == null || orgFilter.isBlank()
            ? fabricGatewayService.evaluateTransaction(org, "GetAllAccounts")
            : fabricGatewayService.evaluateTransaction(org, "GetAccountsByOrg", orgFilter);
        return parseAccounts(payload);
    }

    public AccountResponse deactivateAccount(final String org, final String accountId) {
        return parseAccount(fabricGatewayService.submitTransaction(
            org,
            "DeactivateAccount",
            accountId,
            generatedId("deactivate")
        ));
    }

    public List<AccountResponse> parseAccounts(final String payload) {
        try {
            final List<Map<String, Object>> values = objectMapper.readValue(payload, new TypeReference<>() {
            });
            return values.stream().map(this::toAccountResponse).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse account list", exception);
        }
    }

    public AccountResponse parseAccount(final String payload) {
        try {
            return toAccountResponse(objectMapper.readValue(payload, new TypeReference<>() {
            }));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse account payload", exception);
        }
    }

    private AccountResponse toAccountResponse(final Map<String, Object> value) {
        return objectMapper.convertValue(value, AccountResponse.class);
    }

    private String generatedId(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}

