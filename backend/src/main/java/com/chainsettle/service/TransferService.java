package com.chainsettle.service;

import com.chainsettle.exception.AccountNotFoundException;
import com.chainsettle.exception.InsufficientBalanceException;
import com.chainsettle.model.dto.BatchTransferRequest;
import com.chainsettle.model.dto.BurnTokenRequest;
import com.chainsettle.model.dto.MintTokenRequest;
import com.chainsettle.model.dto.TransferRequest;
import com.chainsettle.model.dto.TransferResponse;
import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.TransactionRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final FabricGatewayService fabricGatewayService;
    private final TransactionRecordRepository transactionRecordRepository;
    private final ObjectMapper objectMapper;

    public TransferService(
        final FabricGatewayService fabricGatewayService,
        final TransactionRecordRepository transactionRecordRepository,
        final ObjectMapper objectMapper
    ) {
        this.fabricGatewayService = fabricGatewayService;
        this.transactionRecordRepository = transactionRecordRepository;
        this.objectMapper = objectMapper;
    }

    public TransferResponse mint(final String org, final MintTokenRequest request) {
        return enrich(parseSingle(fabricGatewayService.submitTransaction(
            org,
            "MintTokens",
            request.accountId(),
            request.amount().toPlainString(),
            request.clientRequestId() == null ? generatedId("mint") : request.clientRequestId()
        )));
    }

    public TransferResponse burn(final String org, final BurnTokenRequest request) {
        return enrich(parseSingle(fabricGatewayService.submitTransaction(
            org,
            "BurnTokens",
            request.accountId(),
            request.amount().toPlainString(),
            request.clientRequestId() == null ? generatedId("burn") : request.clientRequestId()
        )));
    }

    public TransferResponse transfer(final String org, final TransferRequest request) {
        try {
            return enrich(parseSingle(fabricGatewayService.submitTransaction(
                org,
                "Transfer",
                request.fromAccountId(),
                request.toAccountId(),
                request.amount().toPlainString(),
                request.currency(),
                writeJson(request.metadata()),
                request.clientRequestId() == null ? generatedId("transfer") : request.clientRequestId()
            )));
        } catch (IllegalStateException exception) {
            if (exception.getMessage().contains("Insufficient balance")) {
                throw new InsufficientBalanceException(exception.getMessage());
            }
            if (exception.getMessage().contains("Account not found")) {
                throw new AccountNotFoundException(exception.getMessage());
            }
            throw exception;
        }
    }

    public List<TransferResponse> batchTransfer(final String org, final BatchTransferRequest request) {
        final List<Map<String, Object>> encodedTransfers = request.transfers().stream()
            .map(transfer -> {
                final Map<String, Object> item = new LinkedHashMap<>();
                item.put("fromAccountId", transfer.fromAccountId());
                item.put("toAccountId", transfer.toAccountId());
                item.put("amount", transfer.amount().toPlainString());
                item.put("currency", transfer.currency());
                item.put("metadataJson", writeJson(transfer.metadata()));
                item.put("clientRequestId", transfer.clientRequestId() == null ? generatedId("batch-item") : transfer.clientRequestId());
                return item;
            })
            .toList();
        final String payload = fabricGatewayService.submitTransaction(
            org,
            "BatchTransfer",
            writeJson(encodedTransfers),
            request.clientRequestId() == null ? generatedId("batch") : request.clientRequestId()
        );
        return parseList(payload).stream().map(this::enrich).toList();
    }

    public TransferResponse getTransaction(final String org, final String txId) {
        return enrich(parseSingle(fabricGatewayService.evaluateTransaction(org, "GetTransaction", txId)));
    }

    public List<TransferResponse> getHistory(final String org, final String accountId, final int limit) {
        return parseList(fabricGatewayService.evaluateTransaction(org, "GetTransactionHistory", accountId, Integer.toString(limit)))
            .stream()
            .map(this::enrich)
            .toList();
    }

    public TransferResponse parseSingle(final String payload) {
        try {
            final Map<String, Object> map = objectMapper.readValue(payload, new TypeReference<>() {
            });
            return objectMapper.convertValue(map, TransferResponse.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse transfer payload", exception);
        }
    }

    public List<TransferResponse> parseList(final String payload) {
        try {
            final List<Map<String, Object>> list = objectMapper.readValue(payload, new TypeReference<>() {
            });
            return list.stream()
                .map(value -> objectMapper.convertValue(value, TransferResponse.class))
                .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse transfer list payload", exception);
        }
    }

    public TransferResponse enrich(final TransferResponse response) {
        final TransactionRecord persisted = transactionRecordRepository.findByTxId(response.txId()).orElse(null);
        if (persisted == null) {
            return response;
        }
        return new TransferResponse(
            response.txId(),
            persisted.getStatus(),
            response.txType(),
            response.fromAccountId(),
            response.toAccountId(),
            response.fromOrg(),
            response.toOrg(),
            response.amount(),
            response.currency(),
            response.timestamp(),
            persisted.getBlockNumber(),
            persisted.getFabricTxId(),
            response.settlementRef(),
            response.metadata()
        );
    }

    private String generatedId(final String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private String writeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize request metadata", exception);
        }
    }
}
