package com.chainsettle.service;

import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.model.entity.AccountSnapshot;
import com.chainsettle.model.entity.SettlementRecord;
import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.AccountSnapshotRepository;
import com.chainsettle.repository.SettlementRecordRepository;
import com.chainsettle.repository.TransactionRecordRepository;
import com.chainsettle.websocket.DashboardWebSocketHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.hyperledger.fabric.client.ChaincodeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class EventListenerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EventListenerService.class);

    private final FabricGatewayService fabricGatewayService;
    private final TransactionRecordRepository transactionRecordRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final DashboardWebSocketHandler dashboardWebSocketHandler;
    private final AccountService accountService;
    private final ObjectMapper objectMapper;
    private final ExecutorService fabricEventExecutor;
    private final AtomicReference<Instant> lastEventAt = new AtomicReference<>();

    @Value("${chainsettle.fabric.event-listener-enabled:true}")
    private boolean eventListenerEnabled;

    public EventListenerService(
        final FabricGatewayService fabricGatewayService,
        final TransactionRecordRepository transactionRecordRepository,
        final SettlementRecordRepository settlementRecordRepository,
        final AccountSnapshotRepository accountSnapshotRepository,
        final DashboardWebSocketHandler dashboardWebSocketHandler,
        final AccountService accountService,
        final ObjectMapper objectMapper,
        final ExecutorService fabricEventExecutor
    ) {
        this.fabricGatewayService = fabricGatewayService;
        this.transactionRecordRepository = transactionRecordRepository;
        this.settlementRecordRepository = settlementRecordRepository;
        this.accountSnapshotRepository = accountSnapshotRepository;
        this.dashboardWebSocketHandler = dashboardWebSocketHandler;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
        this.fabricEventExecutor = fabricEventExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startEventListener() {
        if (!eventListenerEnabled) {
            return;
        }
        fabricEventExecutor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    fabricGatewayService.streamChaincodeEvents("ClearingHouse", this::handleChaincodeEvent);
                } catch (Exception exception) {
                    LOGGER.warn("Fabric event stream disconnected: {}", exception.getMessage());
                    sleepQuietly();
                }
            }
        });
    }

    public Instant getLastEventAt() {
        return lastEventAt.get();
    }

    public void handleChaincodeEvent(final ChaincodeEvent event) {
        try {
            final String eventName = event.getEventName();
            final String payload = new String(event.getPayload(), StandardCharsets.UTF_8);
            final Map<String, Object> rawPayload = objectMapper.readValue(payload, new TypeReference<>() {
            });

            switch (eventName) {
                case "TokensTransferred" -> persistTransaction(event, rawPayload);
                case "TokensMinted" -> persistTransaction(event, rawPayload);
                case "TokensBurned" -> persistTransaction(event, rawPayload);
                case "DvPCompleted" -> persistSettlement(eventName, rawPayload);
                case "DvPCancelled" -> persistSettlement(eventName, rawPayload);
                case "AccountCreated" -> persistAccountSnapshots(eventName, rawPayload);
                default -> dashboardWebSocketHandler.publishEvent(eventName, rawPayload);
            }

            lastEventAt.set(Instant.now());
        } catch (Exception exception) {
            LOGGER.warn("Failed to process chaincode event {}: {}", event.getEventName(), exception.getMessage());
        }
    }

    private void persistAccountSnapshots(final String eventName, final Map<String, Object> rawPayload) {
        dashboardWebSocketHandler.publishEvent(eventName, rawPayload);
        refreshSnapshots();
    }

    private void persistTransaction(final ChaincodeEvent event, final Map<String, Object> rawPayload) {
        final String txId = (String) rawPayload.get("txId");
        final String ledgerPayload = fabricGatewayService.evaluateTransaction("ClearingHouse", "GetTransaction", txId);
        final TransactionRecord record = toTransactionRecord(event, ledgerPayload);
        transactionRecordRepository.findByTxId(record.getTxId()).orElseGet(() -> transactionRecordRepository.save(record));
        dashboardWebSocketHandler.publishTransaction(record);
        dashboardWebSocketHandler.publishEvent(event.getEventName(), rawPayload);
        refreshSnapshots();
    }

    private void persistSettlement(final String eventName, final Map<String, Object> rawPayload) {
        final String settlementId = (String) rawPayload.get("settlementId");
        final Optional<SettlementRecord> existing = settlementRecordRepository.findBySettlementId(settlementId);
        final SettlementRecord record = existing.orElseGet(SettlementRecord::new);
        record.setSettlementId(settlementId);
        record.setSettlementType("DVP");
        record.setStatus("DvPCompleted".equals(eventName) ? "COMPLETED" : "CANCELLED");
        record.setCashLegJson(writeJson(rawPayload.getOrDefault("leg1Summary", Map.of())));
        record.setAssetLegJson(writeJson(rawPayload.getOrDefault("leg2Summary", Map.of())));
        if (record.getCreatedAt() == null) {
            record.setCreatedAt(Instant.now());
        }
        record.setCompletedAt(Instant.now());
        settlementRecordRepository.save(record);
        dashboardWebSocketHandler.publishEvent(eventName, rawPayload);
        refreshSnapshots();
    }

    private TransactionRecord toTransactionRecord(final ChaincodeEvent event, final String ledgerPayload) {
        try {
            final Map<String, Object> map = objectMapper.readValue(ledgerPayload, new TypeReference<>() {
            });
            final TransactionRecord record = new TransactionRecord();
            record.setTxId((String) map.get("txId"));
            record.setTxType((String) map.get("txType"));
            record.setFromAccountId((String) map.get("fromAccountId"));
            record.setToAccountId((String) map.get("toAccountId"));
            record.setFromOrg((String) map.get("fromOrg"));
            record.setToOrg((String) map.get("toOrg"));
            record.setAmount(objectMapper.convertValue(map.get("amount"), java.math.BigDecimal.class));
            record.setCurrency((String) map.get("currency"));
            record.setStatus((String) map.get("status"));
            record.setOrgInitiator((String) map.get("initiatedBy"));
            record.setFabricTxId(event.getTransactionId());
            record.setBlockNumber(event.getBlockNumber());
            record.setSettlementRef((String) map.get("settlementRef"));
            record.setMetadataJson(writeJson(map.get("metadata")));
            record.setCreatedAt(Instant.parse((String) map.get("timestamp")));
            return record;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist transaction event", exception);
        }
    }

    private void refreshSnapshots() {
        final List<AccountResponse> accounts = accountService.listAccounts("ClearingHouse", null);
        final List<AccountSnapshot> snapshots = accounts.stream()
            .map(account -> {
                final AccountSnapshot snapshot = new AccountSnapshot();
                snapshot.setAccountId(account.accountId());
                snapshot.setOrgName(account.orgName());
                snapshot.setCurrency(account.currency());
                snapshot.setAccountType(account.accountType());
                snapshot.setAssetType(account.assetType());
                snapshot.setBalance(account.balance());
                snapshot.setSnapshotTime(Instant.now());
                return snapshot;
            })
            .toList();
        accountSnapshotRepository.saveAll(snapshots);
        dashboardWebSocketHandler.publishBalances(snapshots);
    }

    private String writeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize event payload", exception);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
