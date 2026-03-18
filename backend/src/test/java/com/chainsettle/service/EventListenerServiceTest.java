package com.chainsettle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.model.entity.SettlementRecord;
import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.AccountSnapshotRepository;
import com.chainsettle.repository.SettlementRecordRepository;
import com.chainsettle.repository.TransactionRecordRepository;
import com.chainsettle.websocket.DashboardWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.hyperledger.fabric.client.ChaincodeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventListenerServiceTest {
    @Mock
    private FabricGatewayService fabricGatewayService;

    @Mock
    private TransactionRecordRepository transactionRecordRepository;

    @Mock
    private SettlementRecordRepository settlementRecordRepository;

    @Mock
    private AccountSnapshotRepository accountSnapshotRepository;

    @Mock
    private DashboardWebSocketHandler dashboardWebSocketHandler;

    @Mock
    private AccountService accountService;

    @Mock
    private ExecutorService executorService;

    private EventListenerService eventListenerService;

    @BeforeEach
    void setUp() {
        eventListenerService = new EventListenerService(
            fabricGatewayService,
            transactionRecordRepository,
            settlementRecordRepository,
            accountSnapshotRepository,
            dashboardWebSocketHandler,
            accountService,
            new ObjectMapper(),
            executorService
        );
        lenient().when(accountService.listAccounts("ClearingHouse", null)).thenReturn(List.of(
            new AccountResponse(
                "ACC-BANKALPHA-USD",
                "BankAlpha",
                "USD",
                new BigDecimal("1000.00"),
                "ACTIVE",
                "CASH",
                null,
                "2026-03-18T10:00:00Z",
                "2026-03-18T10:00:00Z"
            )
        ));
        lenient().when(accountSnapshotRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void tokensTransferredEventPersistsTransactionAndPushesTopics() {
        when(fabricGatewayService.evaluateTransaction("ClearingHouse", "GetTransaction", "TXN-1"))
            .thenReturn(ledgerTransactionJson("TXN-1", "TRANSFER"));
        when(transactionRecordRepository.findByTxId("TXN-1")).thenReturn(Optional.empty());

        eventListenerService.handleChaincodeEvent(event(
            "TokensTransferred",
            "{\"txId\":\"TXN-1\"}"
        ));

        final ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getTxId()).isEqualTo("TXN-1");
        assertThat(captor.getValue().getTxType()).isEqualTo("TRANSFER");
        verify(dashboardWebSocketHandler).publishTransaction(any(TransactionRecord.class));
        verify(dashboardWebSocketHandler).publishEvent(eq("TokensTransferred"), any(Map.class));
        verify(accountSnapshotRepository).saveAll(any());
        verify(dashboardWebSocketHandler).publishBalances(any());
    }

    @Test
    void tokensMintedEventPersistsTransactionAndPushesTopics() {
        when(fabricGatewayService.evaluateTransaction("ClearingHouse", "GetTransaction", "TXN-MINT"))
            .thenReturn(ledgerTransactionJson("TXN-MINT", "MINT"));
        when(transactionRecordRepository.findByTxId("TXN-MINT")).thenReturn(Optional.empty());

        eventListenerService.handleChaincodeEvent(event(
            "TokensMinted",
            "{\"txId\":\"TXN-MINT\"}"
        ));

        final ArgumentCaptor<TransactionRecord> captor = ArgumentCaptor.forClass(TransactionRecord.class);
        verify(transactionRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getTxType()).isEqualTo("MINT");
        verify(dashboardWebSocketHandler).publishEvent(eq("TokensMinted"), any(Map.class));
        verify(accountSnapshotRepository).saveAll(any());
    }

    @Test
    void dvpCompletedEventPersistsSettlementAndPushesTopics() {
        when(settlementRecordRepository.findBySettlementId("DVP-1")).thenReturn(Optional.empty());

        eventListenerService.handleChaincodeEvent(event(
            "DvPCompleted",
            """
            {
              "settlementId": "DVP-1",
              "leg1Summary": {"txId":"TXN-CASH"},
              "leg2Summary": {"txId":"TXN-ASSET"}
            }
            """
        ));

        final ArgumentCaptor<SettlementRecord> captor = ArgumentCaptor.forClass(SettlementRecord.class);
        verify(settlementRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getSettlementId()).isEqualTo("DVP-1");
        assertThat(captor.getValue().getStatus()).isEqualTo("COMPLETED");
        verify(dashboardWebSocketHandler).publishEvent(eq("DvPCompleted"), any(Map.class));
        verify(accountSnapshotRepository).saveAll(any());
    }

    @Test
    void dvpCancelledEventPersistsSettlementAndPushesTopics() {
        when(settlementRecordRepository.findBySettlementId("DVP-2")).thenReturn(Optional.empty());

        eventListenerService.handleChaincodeEvent(event(
            "DvPCancelled",
            """
            {
              "settlementId": "DVP-2",
              "reason": "Cancelled by simulator"
            }
            """
        ));

        final ArgumentCaptor<SettlementRecord> captor = ArgumentCaptor.forClass(SettlementRecord.class);
        verify(settlementRecordRepository).save(captor.capture());
        assertThat(captor.getValue().getSettlementId()).isEqualTo("DVP-2");
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
        verify(dashboardWebSocketHandler).publishEvent(eq("DvPCancelled"), any(Map.class));
        verify(accountSnapshotRepository).saveAll(any());
    }

    @Test
    void accountCreatedEventRefreshesSnapshotsAndPushesTopics() {
        eventListenerService.handleChaincodeEvent(event(
            "AccountCreated",
            """
            {
              "accountId": "ACC-BANKALPHA-USD",
              "orgName": "BankAlpha",
              "currency": "USD",
              "accountType": "CASH"
            }
            """
        ));

        verify(transactionRecordRepository, never()).save(any());
        verify(settlementRecordRepository, never()).save(any());
        verify(dashboardWebSocketHandler).publishEvent(eq("AccountCreated"), any(Map.class));
        verify(accountSnapshotRepository).saveAll(any());
        verify(dashboardWebSocketHandler).publishBalances(any());
    }

    private ChaincodeEvent event(final String eventName, final String payload) {
        final ChaincodeEvent event = org.mockito.Mockito.mock(ChaincodeEvent.class);
        when(event.getEventName()).thenReturn(eventName);
        when(event.getPayload()).thenReturn(payload.getBytes(StandardCharsets.UTF_8));
        lenient().when(event.getTransactionId()).thenReturn("fabric-tx-1");
        lenient().when(event.getBlockNumber()).thenReturn(7L);
        return event;
    }

    private String ledgerTransactionJson(final String txId, final String txType) {
        return """
            {
              "txId": "%s",
              "txType": "%s",
              "fromAccountId": "ACC-A",
              "toAccountId": "ACC-B",
              "fromOrg": "BankAlpha",
              "toOrg": "BankBeta",
              "amount": 100.00,
              "currency": "USD",
              "status": "SETTLED",
              "initiatedBy": "BankAlpha",
              "timestamp": "2026-03-18T10:00:00Z",
              "settlementRef": null,
              "metadata": {"purpose":"Settlement"}
            }
            """.formatted(txId, txType);
    }
}
