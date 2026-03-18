package com.chainsettle.chaincode;

import com.chainsettle.chaincode.model.DvPSettlement;
import com.chainsettle.chaincode.model.TokenAccount;
import com.chainsettle.chaincode.model.TokenTransaction;
import com.chainsettle.chaincode.util.JsonUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

@Contract(
    name = "token-settlement",
    info = @Info(
        title = "ChainSettle Token Settlement Contract",
        description = "Institutional token settlement and DvP simulator",
        version = "1.0.0",
        license = @License(name = "Apache-2.0"),
        contact = @Contact(email = "chainsettle@example.com", name = "ChainSettle")
    )
)
@Default
public class TokenSettlementContract implements ContractInterface {
    private static final String ACCOUNT_PREFIX = "ACCOUNT#";
    private static final String TX_PREFIX = "TX#";
    private static final String DVP_PREFIX = "DVP#";
    private static final String IDEMPOTENCY_PREFIX = "IDEMPOTENCY#";
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String CreateAccount(
        final Context ctx,
        final String accountId,
        final String orgName,
        final String currency,
        final String initialBalance,
        final String accountType,
        final String assetType,
        final String clientRequestId
    ) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "CreateAccount", clientRequestId);

        ensureHasText(accountId, "Account ID is required");
        ensureHasText(orgName, "Organization name is required");
        ensureHasText(currency, "Currency is required");

        final String key = accountKey(accountId);
        if (stateExists(stub, key)) {
            throw new ChaincodeException("Account already exists: " + accountId, "ACCOUNT_ALREADY_EXISTS");
        }

        final TokenAccount account = new TokenAccount();
        account.setAccountId(accountId);
        account.setOrgName(orgName);
        account.setCurrency(currency.toUpperCase(Locale.ROOT));
        account.setBalance(parseAmount(initialBalance));
        account.setAccountType(defaultIfBlank(accountType, "CASH"));
        account.setAssetType(blankToNull(assetType));
        account.setStatus("ACTIVE");
        account.setCreatedAt(now());
        account.setUpdatedAt(account.getCreatedAt());

        saveAccount(stub, account);
        rememberIdempotency(stub, "CreateAccount", clientRequestId, key);
        emitEvent(stub, "AccountCreated", Map.of(
            "accountId", accountId,
            "orgName", orgName,
            "currency", account.getCurrency(),
            "accountType", account.getAccountType(),
            "timestamp", account.getCreatedAt()
        ));
        return JsonUtil.toJson(account);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAccount(final Context ctx, final String accountId) {
        return JsonUtil.toJson(loadAccount(ctx.getStub(), accountId));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAccountsByOrg(final Context ctx, final String orgName) {
        final String selector = selector(Map.of(
            "docType", "tokenAccount",
            "orgName", orgName
        ));
        final List<TokenAccount> accounts = queryObjects(ctx.getStub(), selector, TokenAccount.class);
        return JsonUtil.toJson(accounts);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllAccounts(final Context ctx) {
        final List<TokenAccount> accounts = queryObjects(ctx.getStub(), selector(Map.of("docType", "tokenAccount")),
            TokenAccount.class);
        return JsonUtil.toJson(accounts);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String DeactivateAccount(final Context ctx, final String accountId, final String clientRequestId) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "DeactivateAccount", clientRequestId);
        final TokenAccount account = loadAccount(stub, accountId);
        account.setStatus("INACTIVE");
        account.setUpdatedAt(now());
        saveAccount(stub, account);
        rememberIdempotency(stub, "DeactivateAccount", clientRequestId, accountKey(accountId));
        return JsonUtil.toJson(account);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String MintTokens(final Context ctx, final String accountId, final String amount, final String clientRequestId) {
        requireClearingHouse(ctx.getClientIdentity());
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "MintTokens", clientRequestId);

        final TokenAccount account = loadActiveAccount(stub, accountId);
        final BigDecimal mintAmount = parseAmount(amount);
        account.setBalance(scale(account.getBalance().add(mintAmount)));
        account.setUpdatedAt(now());
        saveAccount(stub, account);

        final TokenTransaction transaction = buildTransaction(
            defaultIfBlank(clientRequestId, "MINT-" + stub.getTxId()),
            "MINT",
            clientRequestId,
            null,
            account,
            mintAmount,
            account.getCurrency(),
            Map.of("operation", "mint"),
            null,
            getClientOrgName(ctx)
        );
        saveTransaction(stub, transaction);
        rememberIdempotency(stub, "MintTokens", clientRequestId, transaction.getTxId());

        emitEvent(stub, "TokensMinted", Map.of(
            "txId", transaction.getTxId(),
            "accountId", accountId,
            "amount", mintAmount,
            "currency", account.getCurrency(),
            "newBalance", account.getBalance(),
            "timestamp", transaction.getTimestamp()
        ));
        return JsonUtil.toJson(transaction);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String BurnTokens(final Context ctx, final String accountId, final String amount, final String clientRequestId) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "BurnTokens", clientRequestId);
        final TokenAccount account = loadActiveAccount(stub, accountId);
        final BigDecimal burnAmount = parseAmount(amount);
        ensureSufficientBalance(account, burnAmount);
        account.setBalance(scale(account.getBalance().subtract(burnAmount)));
        account.setUpdatedAt(now());
        saveAccount(stub, account);

        final TokenTransaction transaction = buildTransaction(
            defaultIfBlank(clientRequestId, "BURN-" + stub.getTxId()),
            "BURN",
            clientRequestId,
            account,
            null,
            burnAmount,
            account.getCurrency(),
            Map.of("operation", "burn"),
            null,
            getClientOrgName(ctx)
        );
        saveTransaction(stub, transaction);
        rememberIdempotency(stub, "BurnTokens", clientRequestId, transaction.getTxId());
        return JsonUtil.toJson(transaction);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String Transfer(
        final Context ctx,
        final String fromAccountId,
        final String toAccountId,
        final String amount,
        final String currency,
        final String metadataJson,
        final String clientRequestId
    ) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "Transfer", clientRequestId);
        final TokenTransaction transaction = executeTransfer(
            ctx,
            fromAccountId,
            toAccountId,
            amount,
            currency,
            metadataJson,
            clientRequestId,
            null,
            "TRANSFER",
            true
        );
        rememberIdempotency(stub, "Transfer", clientRequestId, transaction.getTxId());
        return JsonUtil.toJson(transaction);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String BatchTransfer(final Context ctx, final String transfersJson, final String clientRequestId) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "BatchTransfer", clientRequestId);
        final List<TransferInstruction> instructions = JsonUtil.readList(transfersJson, TransferInstruction.class);
        if (instructions.isEmpty()) {
            throw new ChaincodeException("Batch transfer requires at least one transfer", "INVALID_BATCH");
        }

        final Map<String, TokenAccount> stagedAccounts = new LinkedHashMap<>();
        final List<TokenTransaction> transactions = new ArrayList<>();
        int instructionIndex = 0;
        for (TransferInstruction instruction : instructions) {
            if (instruction.getClientRequestId() != null && !instruction.getClientRequestId().isBlank()) {
                rejectDuplicateRequest(stub, "Transfer", instruction.getClientRequestId());
            }
            final TokenTransaction transaction = prepareTransfer(
                ctx,
                instruction.getFromAccountId(),
                instruction.getToAccountId(),
                instruction.getAmount(),
                instruction.getCurrency(),
                instruction.getMetadataJson(),
                instruction.getClientRequestId(),
                null,
                "BATCH_TRANSFER",
                defaultIfBlank(clientRequestId, stub.getTxId()) + "-batch-" + instructionIndex,
                stagedAccounts
            );
            transactions.add(transaction);
            instructionIndex++;
        }

        commitAccounts(stub, stagedAccounts);
        for (TokenTransaction transaction : transactions) {
            saveTransaction(stub, transaction);
            emitTransferEvent(stub, transaction);
        }
        for (TransferInstruction instruction : instructions) {
            rememberIdempotency(stub, "Transfer", instruction.getClientRequestId(), "BATCH#" + defaultIfBlank(clientRequestId, stub.getTxId()));
        }
        rememberIdempotency(stub, "BatchTransfer", clientRequestId, "BATCH#" + stub.getTxId());
        return JsonUtil.toJson(transactions);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String InitiateDvP(final Context ctx, final String leg1Json, final String leg2Json, final String clientRequestId) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "InitiateDvP", clientRequestId);

        final DvPSettlement settlement = new DvPSettlement();
        settlement.setSettlementId(generateBusinessId("DVP", stub.getTxId()));
        settlement.setClientRequestId(blankToNull(clientRequestId));
        settlement.setLeg1(JsonUtil.fromJson(leg1Json, DvPSettlement.SettlementLeg.class));
        settlement.setLeg2(JsonUtil.fromJson(leg2Json, DvPSettlement.SettlementLeg.class));
        settlement.setStatus("PENDING");
        settlement.setInitiatedBy(getClientOrgName(ctx));
        settlement.setCreatedAt(now());
        settlement.setCompletedAt(null);

        validateDvPLegs(stub, settlement);

        saveSettlement(stub, settlement);
        rememberIdempotency(stub, "InitiateDvP", clientRequestId, settlement.getSettlementId());
        return JsonUtil.toJson(settlement);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String ExecuteDvP(final Context ctx, final String settlementId, final String clientRequestId) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "ExecuteDvP", clientRequestId);
        final DvPSettlement settlement = loadSettlement(stub, settlementId);
        if (!Objects.equals("PENDING", settlement.getStatus())) {
            throw new ChaincodeException("Settlement is not pending: " + settlementId, "SETTLEMENT_NOT_PENDING");
        }

        validateDvPLegs(stub, settlement);
        final Map<String, TokenAccount> stagedAccounts = new LinkedHashMap<>();

        final TokenTransaction leg1Txn = prepareTransfer(
            ctx,
            settlement.getLeg1().getFromAccountId(),
            settlement.getLeg1().getToAccountId(),
            settlement.getLeg1().getAmount().toPlainString(),
            settlement.getLeg1().getCurrency(),
            JsonUtil.toJson(Map.of("asset", settlement.getLeg1().getAsset(), "settlementId", settlementId, "leg", "leg1")),
            settlementId + "-leg1",
            settlementId,
            "DVP",
            settlementId + "-leg1",
            stagedAccounts
        );
        final TokenTransaction leg2Txn = prepareTransfer(
            ctx,
            settlement.getLeg2().getFromAccountId(),
            settlement.getLeg2().getToAccountId(),
            settlement.getLeg2().getAmount().toPlainString(),
            settlement.getLeg2().getCurrency(),
            JsonUtil.toJson(Map.of("asset", settlement.getLeg2().getAsset(), "settlementId", settlementId, "leg", "leg2")),
            settlementId + "-leg2",
            settlementId,
            "DVP",
            settlementId + "-leg2",
            stagedAccounts
        );

        commitAccounts(stub, stagedAccounts);
        saveTransaction(stub, leg1Txn);
        saveTransaction(stub, leg2Txn);

        settlement.setStatus("COMPLETED");
        settlement.setCompletedAt(now());
        saveSettlement(stub, settlement);
        rememberIdempotency(stub, "ExecuteDvP", clientRequestId, settlementId);

        emitEvent(stub, "DvPCompleted", Map.of(
            "settlementId", settlementId,
            "leg1Summary", Map.of(
                "txId", leg1Txn.getTxId(),
                "fromAccountId", leg1Txn.getFromAccountId(),
                "toAccountId", leg1Txn.getToAccountId(),
                "amount", leg1Txn.getAmount(),
                "currency", leg1Txn.getCurrency()
            ),
            "leg2Summary", Map.of(
                "txId", leg2Txn.getTxId(),
                "fromAccountId", leg2Txn.getFromAccountId(),
                "toAccountId", leg2Txn.getToAccountId(),
                "amount", leg2Txn.getAmount(),
                "currency", leg2Txn.getCurrency()
            )
        ));

        return JsonUtil.toJson(settlement);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String CancelDvP(final Context ctx, final String settlementId, final String reason, final String clientRequestId) {
        final ChaincodeStub stub = ctx.getStub();
        rejectDuplicateRequest(stub, "CancelDvP", clientRequestId);
        final DvPSettlement settlement = loadSettlement(stub, settlementId);
        if (!Objects.equals("PENDING", settlement.getStatus())) {
            throw new ChaincodeException("Only pending settlements can be cancelled", "SETTLEMENT_NOT_PENDING");
        }
        settlement.setStatus("CANCELLED");
        settlement.setFailureReason(defaultIfBlank(reason, "Cancelled by operator"));
        settlement.setCompletedAt(now());
        saveSettlement(stub, settlement);
        rememberIdempotency(stub, "CancelDvP", clientRequestId, settlementId);
        emitEvent(stub, "DvPCancelled", Map.of(
            "settlementId", settlementId,
            "reason", settlement.getFailureReason(),
            "timestamp", settlement.getCompletedAt()
        ));
        return JsonUtil.toJson(settlement);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetDvPSettlement(final Context ctx, final String settlementId) {
        return JsonUtil.toJson(loadSettlement(ctx.getStub(), settlementId));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllDvPSettlements(final Context ctx) {
        return JsonUtil.toJson(queryObjects(ctx.getStub(), selector(Map.of("docType", "dvpSettlement")), DvPSettlement.class));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetTransaction(final Context ctx, final String txId) {
        return JsonUtil.toJson(loadTransaction(ctx.getStub(), txId));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetTransactionHistory(final Context ctx, final String accountId, final String limit) {
        final int parsedLimit = limit == null || limit.isBlank() ? 100 : Integer.parseInt(limit);
        final String selector = "{\"selector\":{\"docType\":\"tokenTransaction\",\"$or\":[{\"fromAccountId\":\"" + accountId
            + "\"},{\"toAccountId\":\"" + accountId + "\"}]}}";
        final List<TokenTransaction> transactions = queryObjects(ctx.getStub(), selector, TokenTransaction.class).stream()
            .sorted(Comparator.comparing(TokenTransaction::getTimestamp).reversed())
            .limit(parsedLimit)
            .collect(Collectors.toList());
        return JsonUtil.toJson(transactions);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetTransactionsByDateRange(final Context ctx, final String startDate, final String endDate) {
        final String selector = "{\"selector\":{\"docType\":\"tokenTransaction\",\"timestamp\":{\"$gte\":\"" + startDate
            + "\",\"$lte\":\"" + endDate + "\"}}}";
        return JsonUtil.toJson(queryObjects(ctx.getStub(), selector, TokenTransaction.class));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetSettlementVolume(final Context ctx, final String orgName, final String date) {
        final String selector = "{\"selector\":{\"docType\":\"tokenTransaction\",\"status\":\"SETTLED\",\"timestamp\":{\"$regex\":\"^"
            + date + "\"}}}";
        final BigDecimal volume = queryObjects(ctx.getStub(), selector, TokenTransaction.class).stream()
            .filter(tx -> Objects.equals(orgName, tx.getFromOrg()) || Objects.equals(orgName, tx.getToOrg()))
            .map(TokenTransaction::getAmount)
            .reduce(ZERO, BigDecimal::add);
        return JsonUtil.toJson(Map.of("orgName", orgName, "date", date, "volume", scale(volume)));
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetNetPosition(final Context ctx, final String orgName) {
        final List<TokenTransaction> transactions = queryObjects(ctx.getStub(),
            selector(Map.of("docType", "tokenTransaction", "status", "SETTLED")), TokenTransaction.class);
        BigDecimal inflows = ZERO;
        BigDecimal outflows = ZERO;
        for (TokenTransaction transaction : transactions) {
            if (Objects.equals(orgName, transaction.getToOrg())) {
                inflows = inflows.add(transaction.getAmount());
            }
            if (Objects.equals(orgName, transaction.getFromOrg())) {
                outflows = outflows.add(transaction.getAmount());
            }
        }
        return JsonUtil.toJson(Map.of(
            "orgName", orgName,
            "inflows", scale(inflows),
            "outflows", scale(outflows),
            "netPosition", scale(inflows.subtract(outflows))
        ));
    }

    private TokenTransaction executeTransfer(
        final Context ctx,
        final String fromAccountId,
        final String toAccountId,
        final String amount,
        final String currency,
        final String metadataJson,
        final String clientRequestId,
        final String settlementRef,
        final String txType,
        final boolean emitTransferEvent
    ) {
        final ChaincodeStub stub = ctx.getStub();
        final Map<String, TokenAccount> stagedAccounts = new LinkedHashMap<>();
        final TokenTransaction transaction = prepareTransfer(
            ctx,
            fromAccountId,
            toAccountId,
            amount,
            currency,
            metadataJson,
            clientRequestId,
            settlementRef,
            txType,
            defaultIfBlank(clientRequestId, txType + "-" + stub.getTxId()),
            stagedAccounts
        );
        commitAccounts(stub, stagedAccounts);
        saveTransaction(stub, transaction);
        if (emitTransferEvent) {
            emitTransferEvent(stub, transaction);
        }
        return transaction;
    }

    private TokenTransaction prepareTransfer(
        final Context ctx,
        final String fromAccountId,
        final String toAccountId,
        final String amount,
        final String currency,
        final String metadataJson,
        final String clientRequestId,
        final String settlementRef,
        final String txType,
        final String txIdSeed,
        final Map<String, TokenAccount> stagedAccounts
    ) {
        final ChaincodeStub stub = ctx.getStub();
        ensureHasText(fromAccountId, "Source account ID is required");
        ensureHasText(toAccountId, "Destination account ID is required");
        if (Objects.equals(fromAccountId, toAccountId)) {
            throw new ChaincodeException("Self transfers are not permitted", "SELF_TRANSFER_NOT_ALLOWED");
        }

        final TokenAccount fromAccount = loadWorkingActiveAccount(stub, stagedAccounts, fromAccountId);
        final TokenAccount toAccount = loadWorkingActiveAccount(stub, stagedAccounts, toAccountId);
        ensureCurrenciesMatch(currency, fromAccount, toAccount);

        final BigDecimal transferAmount = parseAmount(amount);
        ensureSufficientBalance(fromAccount, transferAmount);

        fromAccount.setBalance(scale(fromAccount.getBalance().subtract(transferAmount)));
        fromAccount.setUpdatedAt(now());
        toAccount.setBalance(scale(toAccount.getBalance().add(transferAmount)));
        toAccount.setUpdatedAt(now());
        final TokenTransaction transaction = buildTransaction(
            txIdSeed,
            txType,
            clientRequestId,
            fromAccount,
            toAccount,
            transferAmount,
            currency,
            JsonUtil.readMap(metadataJson),
            settlementRef,
            getClientOrgName(ctx)
        );

        return transaction;
    }

    private void validateDvPLegs(final ChaincodeStub stub, final DvPSettlement settlement) {
        validateSettlementLeg(stub, settlement.getLeg1(), "CASH", "CASH");
        validateSettlementLeg(stub, settlement.getLeg2(), "ASSET", settlement.getLeg2() == null ? null : settlement.getLeg2().getAsset());
    }

    private void validateSettlementLeg(
        final ChaincodeStub stub,
        final DvPSettlement.SettlementLeg leg,
        final String expectedAccountType,
        final String expectedAssetType
    ) {
        if (leg == null) {
            throw new ChaincodeException("Settlement leg is required", "INVALID_SETTLEMENT");
        }
        ensureHasText(leg.getFromAccountId(), "Settlement leg fromAccountId is required");
        ensureHasText(leg.getToAccountId(), "Settlement leg toAccountId is required");
        ensureHasText(leg.getCurrency(), "Settlement leg currency is required");
        if (Objects.equals(leg.getFromAccountId(), leg.getToAccountId())) {
            throw new ChaincodeException("Settlement leg must transfer between distinct accounts", "INVALID_SETTLEMENT");
        }
        if (leg.getAmount() == null || leg.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ChaincodeException("Settlement leg amount must be greater than zero", "INVALID_AMOUNT");
        }
        final TokenAccount fromAccount = loadActiveAccount(stub, leg.getFromAccountId());
        final TokenAccount toAccount = loadActiveAccount(stub, leg.getToAccountId());
        ensureCurrenciesMatch(leg.getCurrency(), fromAccount, toAccount);
        ensureAccountType(fromAccount, expectedAccountType, "INVALID_SETTLEMENT_ACCOUNT_TYPE");
        ensureAccountType(toAccount, expectedAccountType, "INVALID_SETTLEMENT_ACCOUNT_TYPE");
        if (Objects.equals("CASH", expectedAccountType)) {
            final String asset = defaultIfBlank(leg.getAsset(), "CASH");
            if (!Objects.equals("CASH", asset)) {
                throw new ChaincodeException("Cash leg must declare CASH as the asset", "INVALID_SETTLEMENT_ASSET");
            }
        } else if (Objects.equals("ASSET", expectedAccountType)) {
            ensureHasText(expectedAssetType, "Asset leg must declare an asset type");
            if (!Objects.equals(expectedAssetType, fromAccount.getAssetType())
                || !Objects.equals(expectedAssetType, toAccount.getAssetType())) {
                throw new ChaincodeException("Asset leg accounts must match the requested asset type",
                    "INVALID_SETTLEMENT_ASSET");
            }
        }
    }

    private void requireClearingHouse(final ClientIdentity identity) {
        if (!Objects.equals("ClearingHouseMSP", identity.getMSPID())) {
            throw new ChaincodeException("Only ClearingHouse may mint new tokens", "UNAUTHORIZED_MINT");
        }
    }

    private TokenAccount loadAccount(final ChaincodeStub stub, final String accountId) {
        final String value = stub.getStringState(accountKey(accountId));
        if (value == null || value.isBlank()) {
            throw new ChaincodeException("Account not found: " + accountId, "ACCOUNT_NOT_FOUND");
        }
        return JsonUtil.fromJson(value, TokenAccount.class);
    }

    private TokenAccount loadActiveAccount(final ChaincodeStub stub, final String accountId) {
        final TokenAccount account = loadAccount(stub, accountId);
        if (!Objects.equals("ACTIVE", account.getStatus())) {
            throw new ChaincodeException("Account is not active: " + accountId, "ACCOUNT_INACTIVE");
        }
        return account;
    }

    private TokenTransaction loadTransaction(final ChaincodeStub stub, final String txId) {
        final String value = stub.getStringState(transactionKey(txId));
        if (value == null || value.isBlank()) {
            throw new ChaincodeException("Transaction not found: " + txId, "TRANSACTION_NOT_FOUND");
        }
        return JsonUtil.fromJson(value, TokenTransaction.class);
    }

    private DvPSettlement loadSettlement(final ChaincodeStub stub, final String settlementId) {
        final String value = stub.getStringState(settlementKey(settlementId));
        if (value == null || value.isBlank()) {
            throw new ChaincodeException("Settlement not found: " + settlementId, "SETTLEMENT_NOT_FOUND");
        }
        return JsonUtil.fromJson(value, DvPSettlement.class);
    }

    private void saveAccount(final ChaincodeStub stub, final TokenAccount account) {
        stub.putStringState(accountKey(account.getAccountId()), JsonUtil.toJson(account));
    }

    private void saveTransaction(final ChaincodeStub stub, final TokenTransaction transaction) {
        stub.putStringState(transactionKey(transaction.getTxId()), JsonUtil.toJson(transaction));
    }

    private void saveSettlement(final ChaincodeStub stub, final DvPSettlement settlement) {
        stub.putStringState(settlementKey(settlement.getSettlementId()), JsonUtil.toJson(settlement));
    }

    private void commitAccounts(final ChaincodeStub stub, final Map<String, TokenAccount> stagedAccounts) {
        for (TokenAccount account : stagedAccounts.values()) {
            saveAccount(stub, account);
        }
    }

    private void emitTransferEvent(final ChaincodeStub stub, final TokenTransaction transaction) {
        emitEvent(stub, "TokensTransferred", Map.of(
            "txId", transaction.getTxId(),
            "from", transaction.getFromAccountId(),
            "to", transaction.getToAccountId(),
            "amount", transaction.getAmount(),
            "currency", transaction.getCurrency(),
            "status", transaction.getStatus(),
            "timestamp", transaction.getTimestamp()
        ));
    }

    private TokenTransaction buildTransaction(
        final String txIdSeed,
        final String txType,
        final String clientRequestId,
        final TokenAccount fromAccount,
        final TokenAccount toAccount,
        final BigDecimal amount,
        final String currency,
        final Map<String, Object> metadata,
        final String settlementRef,
        final String initiatedBy
    ) {
        final TokenTransaction transaction = new TokenTransaction();
        transaction.setTxId(generateBusinessId("TXN", txIdSeed));
        transaction.setClientRequestId(blankToNull(clientRequestId));
        transaction.setTxType(txType);
        transaction.setFromAccountId(fromAccount == null ? null : fromAccount.getAccountId());
        transaction.setToAccountId(toAccount == null ? null : toAccount.getAccountId());
        transaction.setFromOrg(fromAccount == null ? null : fromAccount.getOrgName());
        transaction.setToOrg(toAccount == null ? null : toAccount.getOrgName());
        transaction.setAmount(scale(amount));
        transaction.setCurrency(currency.toUpperCase(Locale.ROOT));
        transaction.setStatus("SETTLED");
        transaction.setInitiatedBy(initiatedBy);
        transaction.setEndorsedBy(List.of(initiatedBy));
        transaction.setTimestamp(now());
        transaction.setSettlementRef(blankToNull(settlementRef));
        transaction.setMetadata(metadata == null ? Map.of() : metadata);
        return transaction;
    }

    private TokenAccount loadWorkingActiveAccount(
        final ChaincodeStub stub,
        final Map<String, TokenAccount> stagedAccounts,
        final String accountId
    ) {
        if (stagedAccounts.containsKey(accountId)) {
            return stagedAccounts.get(accountId);
        }
        final TokenAccount account = copyAccount(loadActiveAccount(stub, accountId));
        stagedAccounts.put(accountId, account);
        return account;
    }

    private TokenAccount copyAccount(final TokenAccount source) {
        final TokenAccount copy = new TokenAccount();
        copy.setDocType(source.getDocType());
        copy.setAccountId(source.getAccountId());
        copy.setOrgName(source.getOrgName());
        copy.setCurrency(source.getCurrency());
        copy.setBalance(scale(source.getBalance()));
        copy.setAccountType(source.getAccountType());
        copy.setAssetType(source.getAssetType());
        copy.setStatus(source.getStatus());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private void ensureAccountType(final TokenAccount account, final String expectedAccountType, final String errorCode) {
        if (!Objects.equals(expectedAccountType, account.getAccountType())) {
            throw new ChaincodeException(
                "Account " + account.getAccountId() + " must be of type " + expectedAccountType,
                errorCode
            );
        }
    }

    private <T> List<T> queryObjects(final ChaincodeStub stub, final String selector, final Class<T> targetType) {
        final List<T> results = new ArrayList<>();
        QueryResultsIterator<KeyValue> iterator = null;
        try {
            iterator = stub.getQueryResult(selector);
            for (KeyValue value : iterator) {
                results.add(JsonUtil.fromJson(value.getStringValue(), targetType));
            }
        } catch (Exception exception) {
            throw new ChaincodeException("Unable to execute rich query", "QUERY_EXECUTION_ERROR");
        } finally {
            if (iterator != null) {
                try {
                    iterator.close();
                } catch (Exception ignored) {
                }
            }
        }
        return results;
    }

    private void emitEvent(final ChaincodeStub stub, final String eventName, final Map<String, Object> payload) {
        stub.setEvent(eventName, JsonUtil.toJson(payload).getBytes());
    }

    private void rejectDuplicateRequest(final ChaincodeStub stub, final String operation, final String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return;
        }
        if (stateExists(stub, idempotencyKey(operation, clientRequestId))) {
            throw new ChaincodeException("Duplicate request rejected for " + operation + ": " + clientRequestId,
                "DUPLICATE_REQUEST");
        }
    }

    private void rememberIdempotency(final ChaincodeStub stub, final String operation, final String clientRequestId,
                                     final String referenceValue) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return;
        }
        stub.putStringState(idempotencyKey(operation, clientRequestId), referenceValue);
    }

    private boolean stateExists(final ChaincodeStub stub, final String key) {
        final String current = stub.getStringState(key);
        return current != null && !current.isBlank();
    }

    private void ensureSufficientBalance(final TokenAccount account, final BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ChaincodeException("Insufficient balance for account " + account.getAccountId(),
                "INSUFFICIENT_BALANCE");
        }
    }

    private void ensureCurrenciesMatch(final String currency, final TokenAccount fromAccount, final TokenAccount toAccount) {
        final String normalizedCurrency = currency.toUpperCase(Locale.ROOT);
        if (!Objects.equals(normalizedCurrency, fromAccount.getCurrency())
            || !Objects.equals(normalizedCurrency, toAccount.getCurrency())) {
            throw new ChaincodeException("Currency mismatch between request and accounts", "CURRENCY_MISMATCH");
        }
    }

    private BigDecimal parseAmount(final String amount) {
        try {
            final BigDecimal parsed = scale(new BigDecimal(amount));
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ChaincodeException("Amount must be greater than zero", "INVALID_AMOUNT");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ChaincodeException("Invalid numeric amount: " + amount, "INVALID_AMOUNT");
        }
    }

    private BigDecimal scale(final BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String getClientOrgName(final Context ctx) {
        return mapMspToOrg(ctx.getClientIdentity().getMSPID());
    }

    private String mapMspToOrg(final String mspId) {
        return switch (mspId) {
            case "BankAlphaMSP" -> "BankAlpha";
            case "BankBetaMSP" -> "BankBeta";
            case "ClearingHouseMSP" -> "ClearingHouse";
            default -> mspId;
        };
    }

    private String selector(final Map<String, Object> fields) {
        return JsonUtil.toJson(Map.of("selector", fields));
    }

    private String generateBusinessId(final String prefix, final String seed) {
        final String compactSeed = seed.replace("-", "").toUpperCase(Locale.ROOT);
        if (compactSeed.length() <= 18) {
            return prefix + "-" + compactSeed;
        }
        final String suffix = compactSeed.substring(0, 8) + compactSeed.substring(compactSeed.length() - 10);
        return prefix + "-" + suffix;
    }

    private String accountKey(final String accountId) {
        return ACCOUNT_PREFIX + accountId;
    }

    private String transactionKey(final String txId) {
        return TX_PREFIX + txId;
    }

    private String settlementKey(final String settlementId) {
        return DVP_PREFIX + settlementId;
    }

    private String idempotencyKey(final String operation, final String requestId) {
        return IDEMPOTENCY_PREFIX + operation + "#" + requestId;
    }

    private String now() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now().atOffset(ZoneOffset.UTC));
    }

    private void ensureHasText(final String value, final String message) {
        if (value == null || value.isBlank()) {
            throw new ChaincodeException(message, "VALIDATION_ERROR");
        }
    }

    private String defaultIfBlank(final String value, final String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public static class TransferInstruction {
        private String fromAccountId;
        private String toAccountId;
        private String amount;
        private String currency;
        private String metadataJson;
        private String clientRequestId;

        public String getFromAccountId() {
            return fromAccountId;
        }

        public void setFromAccountId(final String fromAccountId) {
            this.fromAccountId = fromAccountId;
        }

        public String getToAccountId() {
            return toAccountId;
        }

        public void setToAccountId(final String toAccountId) {
            this.toAccountId = toAccountId;
        }

        public String getAmount() {
            return amount;
        }

        public void setAmount(final String amount) {
            this.amount = amount;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(final String currency) {
            this.currency = currency;
        }

        public String getMetadataJson() {
            return metadataJson;
        }

        public void setMetadataJson(final String metadataJson) {
            this.metadataJson = metadataJson;
        }

        public String getClientRequestId() {
            return clientRequestId;
        }

        public void setClientRequestId(final String clientRequestId) {
            this.clientRequestId = clientRequestId;
        }
    }
}
