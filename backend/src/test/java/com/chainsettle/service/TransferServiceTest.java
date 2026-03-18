package com.chainsettle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.chainsettle.model.dto.TransferRequest;
import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.TransactionRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock
    private FabricGatewayService fabricGatewayService;

    @Mock
    private TransactionRecordRepository transactionRecordRepository;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(fabricGatewayService, transactionRecordRepository, new ObjectMapper());
    }

    @Test
    void transferParsesLedgerResponse() {
        when(fabricGatewayService.submitTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn("""
                {
                  "txId": "TXN-ABC123",
                  "status": "SETTLED",
                  "txType": "TRANSFER",
                  "fromAccountId": "ACC-BANKALPHA-USD",
                  "toAccountId": "ACC-BANKBETA-USD",
                  "fromOrg": "BankAlpha",
                  "toOrg": "BankBeta",
                  "amount": 1000.00,
                  "currency": "USD",
                  "timestamp": "2026-03-18T14:22:01Z",
                  "settlementRef": null,
                  "metadata": {"purpose": "Trade settlement"}
                }
                """);
        when(transactionRecordRepository.findByTxId("TXN-ABC123")).thenReturn(Optional.empty());

        final var response = transferService.transfer("BankAlpha", new TransferRequest(
            "ACC-BANKALPHA-USD",
            "ACC-BANKBETA-USD",
            new BigDecimal("1000.00"),
            "USD",
            Map.of("purpose", "Trade settlement"),
            "REQ-1"
        ));

        assertThat(response.txId()).isEqualTo("TXN-ABC123");
        assertThat(response.amount()).isEqualByComparingTo("1000.00");
        assertThat(response.fromOrg()).isEqualTo("BankAlpha");
    }

    @Test
    void enrichUsesPersistedFabricMetadataWhenAvailable() {
        final TransactionRecord record = new TransactionRecord();
        record.setTxId("TXN-1");
        record.setStatus("SETTLED");
        record.setFabricTxId("fabric-123");
        record.setBlockNumber(42L);
        when(transactionRecordRepository.findByTxId("TXN-1")).thenReturn(Optional.of(record));

        final var enriched = transferService.enrich(new com.chainsettle.model.dto.TransferResponse(
            "TXN-1",
            "SETTLED",
            "TRANSFER",
            "ACC-A",
            "ACC-B",
            "BankAlpha",
            "BankBeta",
            new BigDecimal("10.00"),
            "USD",
            "2026-03-18T10:00:00Z",
            null,
            null,
            null,
            Map.of()
        ));

        assertThat(enriched.blockNumber()).isEqualTo(42L);
        assertThat(enriched.fabricTxId()).isEqualTo("fabric-123");
    }
}

