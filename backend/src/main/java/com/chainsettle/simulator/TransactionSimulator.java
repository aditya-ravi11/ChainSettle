package com.chainsettle.simulator;

import com.chainsettle.model.dto.BatchTransferRequest;
import com.chainsettle.model.dto.CreateAccountRequest;
import com.chainsettle.model.dto.DvPRequest;
import com.chainsettle.model.dto.MintTokenRequest;
import com.chainsettle.model.dto.TransferRequest;
import com.chainsettle.service.AccountService;
import com.chainsettle.service.SettlementService;
import com.chainsettle.service.TransferService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionSimulator {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionSimulator.class);
    private static final String ALPHA_USD = "ACC-BANKALPHA-USD";
    private static final String ALPHA_EUR = "ACC-BANKALPHA-EUR";
    private static final String ALPHA_BOND = "ACC-BANKALPHA-BOND";
    private static final String BETA_USD = "ACC-BANKBETA-USD";
    private static final String BETA_EUR = "ACC-BANKBETA-EUR";
    private static final String BETA_BOND = "ACC-BANKBETA-BOND";
    private static final String CLEARING_USD = "ACC-CLEARINGHOUSE-USD";
    private static final String CLEARING_EUR = "ACC-CLEARINGHOUSE-EUR";

    private final AccountService accountService;
    private final TransferService transferService;
    private final SettlementService settlementService;
    private final Random random = new Random();
    private final AtomicBoolean bootstrapped = new AtomicBoolean(false);
    private final Instant startedAt = Instant.now();

    @Value("${simulator.enabled:true}")
    private boolean enabled;

    @Value("${simulator.initial-mint-amount:10000000}")
    private BigDecimal initialMintAmount;

    @Value("${simulator.initial-mint-amount-eur:5000000}")
    private BigDecimal initialMintAmountEur;

    @Value("${simulator.min-transfer-amount:1000}")
    private BigDecimal minTransferAmount;

    @Value("${simulator.max-transfer-amount:500000}")
    private BigDecimal maxTransferAmount;

    @Value("${simulator.dvp-probability:0.20}")
    private double dvpProbability;

    @Value("${simulator.batch-probability:0.10}")
    private double batchProbability;

    @Value("${simulator.failure-probability:0.05}")
    private double failureProbability;

    @Value("${simulator.duration-minutes:0}")
    private long durationMinutes;

    public TransactionSimulator(
        final AccountService accountService,
        final TransferService transferService,
        final SettlementService settlementService
    ) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.settlementService = settlementService;
    }

    @Scheduled(initialDelay = 15000L, fixedDelayString = "${simulator.interval-ms:2000}")
    public void simulate() {
        if (!enabled || expired()) {
            return;
        }

        try {
            if (bootstrapped.compareAndSet(false, true)) {
                bootstrapAccounts();
            }

            switch (selectAction(random.nextDouble())) {
                case BATCH -> simulateBatchTransfer();
                case DVP -> simulateDvp();
                case TRANSFER -> simulateTransfer();
            }
        } catch (Exception exception) {
            LOGGER.debug("Simulator iteration skipped: {}", exception.getMessage());
        }
    }

    SimulationAction selectAction(final double roll) {
        if (roll < batchProbability) {
            return SimulationAction.BATCH;
        }
        if (roll < batchProbability + dvpProbability) {
            return SimulationAction.DVP;
        }
        return SimulationAction.TRANSFER;
    }

    private boolean expired() {
        return durationMinutes > 0 && Duration.between(startedAt, Instant.now()).toMinutes() >= durationMinutes;
    }

    private void bootstrapAccounts() {
        createCashAccount("BankAlpha", ALPHA_USD, "USD");
        createCashAccount("BankAlpha", ALPHA_EUR, "EUR");
        createAssetAccount("BankAlpha", ALPHA_BOND, "BOND-US10Y");
        createCashAccount("BankBeta", BETA_USD, "USD");
        createCashAccount("BankBeta", BETA_EUR, "EUR");
        createAssetAccount("BankBeta", BETA_BOND, "BOND-US10Y");
        createCashAccount("ClearingHouse", CLEARING_USD, "USD");
        createCashAccount("ClearingHouse", CLEARING_EUR, "EUR");

        mint("ClearingHouse", ALPHA_USD, initialMintAmount);
        mint("ClearingHouse", BETA_USD, initialMintAmount);
        mint("ClearingHouse", CLEARING_USD, initialMintAmount.multiply(BigDecimal.valueOf(2)));
        mint("ClearingHouse", ALPHA_EUR, initialMintAmountEur);
        mint("ClearingHouse", BETA_EUR, initialMintAmountEur);
        mint("ClearingHouse", CLEARING_EUR, initialMintAmountEur.multiply(BigDecimal.valueOf(2)));
        mint("ClearingHouse", ALPHA_BOND, BigDecimal.valueOf(50));
        mint("ClearingHouse", BETA_BOND, BigDecimal.valueOf(50));
    }

    private void createCashAccount(final String org, final String accountId, final String currency) {
        accountService.createAccount(org, new CreateAccountRequest(
            accountId,
            org,
            currency,
            BigDecimal.ZERO,
            "CASH",
            null,
            "seed-" + accountId
        ));
    }

    private void createAssetAccount(final String org, final String accountId, final String assetType) {
        accountService.createAccount(org, new CreateAccountRequest(
            accountId,
            org,
            "UNITS",
            BigDecimal.ZERO,
            "ASSET",
            assetType,
            "seed-" + accountId
        ));
    }

    private void mint(final String org, final String accountId, final BigDecimal amount) {
        transferService.mint(org, new MintTokenRequest(accountId, amount, "seed-mint-" + accountId));
    }

    private void simulateTransfer() {
        final boolean failure = random.nextDouble() < failureProbability;
        final boolean euro = random.nextBoolean();
        final String fromAccount = euro ? ALPHA_EUR : ALPHA_USD;
        final String toAccount = euro ? BETA_EUR : BETA_USD;
        final BigDecimal amount = failure
            ? maxTransferAmount.multiply(BigDecimal.valueOf(100))
            : randomAmount();
        transferService.transfer("BankAlpha", new TransferRequest(
            fromAccount,
            toAccount,
            amount,
            euro ? "EUR" : "USD",
            Map.of("purpose", failure ? "Failure simulation" : "Institutional settlement", "referenceId", "SIM-" + Instant.now().toEpochMilli()),
            "sim-transfer-" + Instant.now().toEpochMilli()
        ));
    }

    private void simulateBatchTransfer() {
        transferService.batchTransfer("BankAlpha", new BatchTransferRequest(
            List.of(
                new TransferRequest(ALPHA_USD, BETA_USD, randomAmount(), "USD", Map.of("batch", 1), "sim-batch-1-" + Instant.now().toEpochMilli()),
                new TransferRequest(BETA_USD, CLEARING_USD, randomAmount(), "USD", Map.of("batch", 2), "sim-batch-2-" + Instant.now().toEpochMilli()),
                new TransferRequest(CLEARING_USD, ALPHA_USD, randomAmount(), "USD", Map.of("batch", 3), "sim-batch-3-" + Instant.now().toEpochMilli())
            ),
            "sim-batch-" + Instant.now().toEpochMilli()
        ));
    }

    private void simulateDvp() {
        final DvPRequest request = new DvPRequest(
            new DvPRequest.SettlementLegRequest(ALPHA_USD, BETA_USD, randomAmount(), "USD", "CASH"),
            new DvPRequest.SettlementLegRequest(BETA_BOND, ALPHA_BOND, BigDecimal.valueOf(5 + random.nextInt(5)), "UNITS", "BOND-US10Y"),
            "sim-dvp-" + Instant.now().toEpochMilli()
        );
        final var settlement = settlementService.initiate("BankAlpha", request);
        if (random.nextDouble() < failureProbability) {
            settlementService.cancel("ClearingHouse", settlement.settlementId(), "Cancelled by simulator", "sim-cancel-" + Instant.now().toEpochMilli());
        } else {
            settlementService.execute("ClearingHouse", settlement.settlementId(), "sim-exec-" + Instant.now().toEpochMilli());
        }
    }

    private BigDecimal randomAmount() {
        final double base = Math.exp(random.nextGaussian() * 0.75 + 10);
        final BigDecimal scaled = BigDecimal.valueOf(base)
            .remainder(maxTransferAmount.subtract(minTransferAmount))
            .add(minTransferAmount);
        return scaled.setScale(2, RoundingMode.HALF_UP);
    }

    enum SimulationAction {
        TRANSFER,
        DVP,
        BATCH
    }
}
