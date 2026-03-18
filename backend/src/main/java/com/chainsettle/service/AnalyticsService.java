package com.chainsettle.service;

import com.chainsettle.model.dto.AccountResponse;
import com.chainsettle.model.dto.AnalyticsSummary;
import com.chainsettle.model.dto.BalanceSnapshotResponse;
import com.chainsettle.model.dto.TransferResponse;
import com.chainsettle.model.entity.AccountSnapshot;
import com.chainsettle.model.entity.SettlementRecord;
import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.AccountSnapshotRepository;
import com.chainsettle.repository.SettlementRecordRepository;
import com.chainsettle.repository.TransactionRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {
    private final TransactionRecordRepository transactionRecordRepository;
    private final AccountSnapshotRepository accountSnapshotRepository;
    private final SettlementRecordRepository settlementRecordRepository;
    private final AccountService accountService;
    private final FabricGatewayService fabricGatewayService;
    private final TransferService transferService;

    public AnalyticsService(
        final TransactionRecordRepository transactionRecordRepository,
        final AccountSnapshotRepository accountSnapshotRepository,
        final SettlementRecordRepository settlementRecordRepository,
        final AccountService accountService,
        final FabricGatewayService fabricGatewayService,
        final TransferService transferService
    ) {
        this.transactionRecordRepository = transactionRecordRepository;
        this.accountSnapshotRepository = accountSnapshotRepository;
        this.settlementRecordRepository = settlementRecordRepository;
        this.accountService = accountService;
        this.fabricGatewayService = fabricGatewayService;
        this.transferService = transferService;
    }

    public Map<String, Object> getDailyVolume(final String org, final LocalDate date) {
        final Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        final Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1);
        final BigDecimal volume = transactionRecordRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
            .filter(record -> org.equals(record.getFromOrg()) || org.equals(record.getToOrg()))
            .map(TransactionRecord::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("orgName", org, "date", date, "volume", volume);
    }

    public Map<String, Object> getNetPosition(final String org) {
        BigDecimal inflows = BigDecimal.ZERO;
        BigDecimal outflows = BigDecimal.ZERO;
        for (TransactionRecord record : transactionRecordRepository.findTop200ByOrderByCreatedAtDesc()) {
            if (org.equals(record.getToOrg())) {
                inflows = inflows.add(record.getAmount());
            }
            if (org.equals(record.getFromOrg())) {
                outflows = outflows.add(record.getAmount());
            }
        }
        return Map.of(
            "orgName", org,
            "inflows", inflows,
            "outflows", outflows,
            "netPosition", inflows.subtract(outflows)
        );
    }

    public AnalyticsSummary getSummary() {
        final List<TransactionRecord> transactions = transactionRecordRepository.findTop200ByOrderByCreatedAtDesc();
        final BigDecimal totalVolume = transactions.stream()
            .map(TransactionRecord::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AnalyticsSummary(
            transactionRecordRepository.count(),
            settlementRecordRepository.count(),
            getBalances().size(),
            totalVolume,
            fabricGatewayService.getNetworkHealth().status(),
            Instant.now()
        );
    }

    public List<BalanceSnapshotResponse> getBalances() {
        final List<AccountSnapshot> snapshots = accountSnapshotRepository.findAll();
        if (snapshots.isEmpty()) {
            return accountService.listAccounts("ClearingHouse", null).stream()
                .map(account -> new BalanceSnapshotResponse(
                    account.accountId(),
                    account.orgName(),
                    account.currency(),
                    account.accountType(),
                    account.assetType(),
                    account.balance(),
                    Instant.now()
                ))
                .toList();
        }
        return snapshots.stream()
            .collect(Collectors.toMap(
                AccountSnapshot::getAccountId,
                snapshot -> snapshot,
                (left, right) -> left.getSnapshotTime().isAfter(right.getSnapshotTime()) ? left : right,
                LinkedHashMap::new
            ))
            .values()
            .stream()
            .sorted(Comparator.comparing(AccountSnapshot::getOrgName).thenComparing(AccountSnapshot::getAccountId))
            .map(snapshot -> new BalanceSnapshotResponse(
                snapshot.getAccountId(),
                snapshot.getOrgName(),
                snapshot.getCurrency(),
                snapshot.getAccountType(),
                snapshot.getAssetType(),
                snapshot.getBalance(),
                snapshot.getSnapshotTime()
            ))
            .toList();
    }

    public List<TransferResponse> getPerspectiveFeed() {
        return transactionRecordRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .map(record -> transferService.enrich(new TransferResponse(
                record.getTxId(),
                record.getStatus(),
                record.getTxType(),
                record.getFromAccountId(),
                record.getToAccountId(),
                record.getFromOrg(),
                record.getToOrg(),
                record.getAmount(),
                record.getCurrency(),
                record.getCreatedAt().toString(),
                record.getBlockNumber(),
                record.getFabricTxId(),
                record.getSettlementRef(),
                Map.of()
            )))
            .toList();
    }

    public List<SettlementRecord> getSettlementRecords() {
        return settlementRecordRepository.findAll();
    }
}

