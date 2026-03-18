package com.chainsettle.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.model.dto.NetworkHealthResponse;
import com.chainsettle.model.entity.AccountSnapshot;
import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.AccountSnapshotRepository;
import com.chainsettle.repository.SettlementRecordRepository;
import com.chainsettle.repository.TransactionRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    @Mock
    private TransactionRecordRepository transactionRecordRepository;

    @Mock
    private AccountSnapshotRepository accountSnapshotRepository;

    @Mock
    private SettlementRecordRepository settlementRecordRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private FabricGatewayService fabricGatewayService;

    @Mock
    private TransferService transferService;

    private AnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new AnalyticsService(
            transactionRecordRepository,
            accountSnapshotRepository,
            settlementRecordRepository,
            accountService,
            fabricGatewayService,
            transferService
        );
    }

    @Test
    void getDailyVolumeAggregatesMatchingTransactions() {
        when(transactionRecordRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(
                transaction("TXN-1", "BankAlpha", "BankBeta", "25.00"),
                transaction("TXN-2", "ClearingHouse", "BankAlpha", "10.00"),
                transaction("TXN-3", "BankBeta", "ClearingHouse", "99.00")
            ));

        final Map<String, Object> result = analyticsService.getDailyVolume("BankAlpha", LocalDate.of(2026, 3, 18));

        assertThat(result.get("orgName")).isEqualTo("BankAlpha");
        assertThat(result.get("volume")).isEqualTo(new BigDecimal("35.00"));
    }

    @Test
    void getNetPositionCalculatesInflowsAndOutflows() {
        when(transactionRecordRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(
            transaction("TXN-1", "BankAlpha", "BankBeta", "30.00"),
            transaction("TXN-2", "ClearingHouse", "BankAlpha", "45.00"),
            transaction("TXN-3", "BankAlpha", "ClearingHouse", "5.00")
        ));

        final Map<String, Object> result = analyticsService.getNetPosition("BankAlpha");

        assertThat(result.get("inflows")).isEqualTo(new BigDecimal("45.00"));
        assertThat(result.get("outflows")).isEqualTo(new BigDecimal("35.00"));
        assertThat(result.get("netPosition")).isEqualTo(new BigDecimal("10.00"));
    }

    @Test
    void getBalancesFallsBackToLedgerAccountsWhenSnapshotsMissing() {
        when(accountSnapshotRepository.findAll()).thenReturn(List.of());
        when(accountService.listAccounts("ClearingHouse", null)).thenReturn(List.of(
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

        final var balances = analyticsService.getBalances();

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).accountId()).isEqualTo("ACC-BANKALPHA-USD");
        assertThat(balances.get(0).balance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void getSummaryUsesRepositoryCountsAndNetworkHealth() {
        when(transactionRecordRepository.findTop200ByOrderByCreatedAtDesc()).thenReturn(List.of(
            transaction("TXN-1", "BankAlpha", "BankBeta", "25.00"),
            transaction("TXN-2", "BankAlpha", "ClearingHouse", "15.00")
        ));
        when(transactionRecordRepository.count()).thenReturn(12L);
        when(settlementRecordRepository.count()).thenReturn(4L);
        when(accountSnapshotRepository.findAll()).thenReturn(List.of(latestSnapshot("ACC-BANKALPHA-USD", "BankAlpha", "1000.00")));
        when(fabricGatewayService.getNetworkHealth()).thenReturn(new NetworkHealthResponse(
            "UP",
            "settlement-channel",
            "token-settlement",
            List.of(),
            Instant.now(),
            "healthy"
        ));

        final var summary = analyticsService.getSummary();

        assertThat(summary.totalTransactions()).isEqualTo(12L);
        assertThat(summary.totalSettlements()).isEqualTo(4L);
        assertThat(summary.activeAccounts()).isEqualTo(1L);
        assertThat(summary.totalVolume()).isEqualByComparingTo("40.00");
        assertThat(summary.networkStatus()).isEqualTo("UP");
    }

    private TransactionRecord transaction(
        final String txId,
        final String fromOrg,
        final String toOrg,
        final String amount
    ) {
        final TransactionRecord record = new TransactionRecord();
        record.setTxId(txId);
        record.setTxType("TRANSFER");
        record.setFromOrg(fromOrg);
        record.setToOrg(toOrg);
        record.setAmount(new BigDecimal(amount));
        record.setCurrency("USD");
        record.setStatus("SETTLED");
        record.setOrgInitiator(fromOrg);
        record.setCreatedAt(Instant.parse("2026-03-18T10:00:00Z"));
        return record;
    }

    private AccountSnapshot latestSnapshot(final String accountId, final String orgName, final String balance) {
        final AccountSnapshot snapshot = new AccountSnapshot();
        snapshot.setAccountId(accountId);
        snapshot.setOrgName(orgName);
        snapshot.setCurrency("USD");
        snapshot.setAccountType("CASH");
        snapshot.setBalance(new BigDecimal(balance));
        snapshot.setSnapshotTime(Instant.parse("2026-03-18T10:00:00Z"));
        return snapshot;
    }
}
