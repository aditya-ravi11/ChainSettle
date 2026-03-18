package com.chainsettle.chaincode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chainsettle.chaincode.model.DvPSettlement;
import com.chainsettle.chaincode.model.TokenAccount;
import com.chainsettle.chaincode.model.TokenTransaction;
import com.chainsettle.chaincode.util.JsonUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenSettlementContractTest {
    private final TokenSettlementContract contract = new TokenSettlementContract();
    private final Map<String, String> state = new ConcurrentHashMap<>();
    private Context context;
    private ChaincodeStub stub;
    private ClientIdentity identity;

    @BeforeEach
    void setUp() {
        context = mock(Context.class);
        stub = mock(ChaincodeStub.class);
        identity = mock(ClientIdentity.class);

        when(context.getStub()).thenReturn(stub);
        when(context.getClientIdentity()).thenReturn(identity);
        when(identity.getMSPID()).thenReturn("BankAlphaMSP");

        final AtomicInteger txCounter = new AtomicInteger(1);
        when(stub.getTxId()).thenAnswer(invocation -> "tx-" + txCounter.getAndIncrement());
        when(stub.getStringState(anyString())).thenAnswer(invocation ->
            state.getOrDefault(invocation.getArgument(0), ""));
        doAnswer(invocation -> {
            state.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(stub).putStringState(anyString(), anyString());
        doAnswer(invocation -> null).when(stub).setEvent(anyString(), any(byte[].class));
    }

    @Test
    void createAccountPersistsAccount() {
        final String accountJson = contract.CreateAccount(
            context,
            "ACC-BANKALPHA-USD",
            "BankAlpha",
            "USD",
            "1000.00",
            "CASH",
            "",
            "REQ-1"
        );

        final TokenAccount account = JsonUtil.fromJson(accountJson, TokenAccount.class);
        assertThat(account.getAccountId()).isEqualTo("ACC-BANKALPHA-USD");
        assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(account.getAccountType()).isEqualTo("CASH");
        assertThat(state).containsKey("ACCOUNT#ACC-BANKALPHA-USD");
    }

    @Test
    void mintTokensRequiresClearingHouseIdentity() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "1000.00", "CASH", "", "REQ-A");

        assertThatThrownBy(() -> contract.MintTokens(context, "ACC-BANKALPHA-USD", "250.00", "REQ-B"))
            .isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Only ClearingHouse");
    }

    @Test
    void transferRejectsInsufficientBalance() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "100.00", "CASH", "", "REQ-A");
        contract.CreateAccount(context, "ACC-BANKBETA-USD", "BankBeta", "USD", "200.00", "CASH", "", "REQ-B");

        assertThatThrownBy(() -> contract.Transfer(
            context,
            "ACC-BANKALPHA-USD",
            "ACC-BANKBETA-USD",
            "999.00",
            "USD",
            "{}",
            "REQ-C"
        )).isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Insufficient balance");
    }

    @Test
    void transferRejectsInactiveAccounts() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "100.00", "CASH", "", "REQ-A");
        contract.CreateAccount(context, "ACC-BANKBETA-USD", "BankBeta", "USD", "200.00", "CASH", "", "REQ-B");
        contract.DeactivateAccount(context, "ACC-BANKBETA-USD", "REQ-C");

        assertThatThrownBy(() -> contract.Transfer(
            context,
            "ACC-BANKALPHA-USD",
            "ACC-BANKBETA-USD",
            "10.00",
            "USD",
            "{}",
            "REQ-D"
        )).isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("not active");
    }

    @Test
    void transferRejectsSelfTransfer() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "100.00", "CASH", "", "REQ-A");

        assertThatThrownBy(() -> contract.Transfer(
            context,
            "ACC-BANKALPHA-USD",
            "ACC-BANKALPHA-USD",
            "10.00",
            "USD",
            "{}",
            "REQ-B"
        )).isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Self transfers");
    }

    @Test
    void transferRejectsCurrencyMismatch() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "100.00", "CASH", "", "REQ-A");
        contract.CreateAccount(context, "ACC-BANKBETA-EUR", "BankBeta", "EUR", "200.00", "CASH", "", "REQ-B");

        assertThatThrownBy(() -> contract.Transfer(
            context,
            "ACC-BANKALPHA-USD",
            "ACC-BANKBETA-EUR",
            "10.00",
            "USD",
            "{}",
            "REQ-C"
        )).isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Currency mismatch");
    }

    @Test
    void duplicateTransferRequestIsRejected() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "1000.00", "CASH", "", "REQ-A");
        contract.CreateAccount(context, "ACC-BANKBETA-USD", "BankBeta", "USD", "1000.00", "CASH", "", "REQ-B");

        contract.Transfer(context, "ACC-BANKALPHA-USD", "ACC-BANKBETA-USD", "10.00", "USD", "{}", "REQ-TX");

        assertThatThrownBy(() -> contract.Transfer(
            context,
            "ACC-BANKALPHA-USD",
            "ACC-BANKBETA-USD",
            "10.00",
            "USD",
            "{}",
            "REQ-TX"
        )).isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Duplicate request");
    }

    @Test
    void executeDvPUpdatesBothLegs() {
        seedDvPAccounts("1000.00", "1000.00", "100.00", "20.00");

        final String settlementJson = contract.InitiateDvP(
            context,
            "{\"fromAccountId\":\"ACC-BANKALPHA-USD\",\"toAccountId\":\"ACC-BANKBETA-USD\",\"amount\":100.00,\"currency\":\"USD\",\"asset\":\"CASH\"}",
            "{\"fromAccountId\":\"ACC-BANKBETA-BOND\",\"toAccountId\":\"ACC-BANKALPHA-BOND\",\"amount\":5.00,\"currency\":\"UNITS\",\"asset\":\"BOND-US10Y\"}",
            "REQ-DVP"
        );

        final DvPSettlement initiated = JsonUtil.fromJson(settlementJson, DvPSettlement.class);
        final String completedJson = contract.ExecuteDvP(context, initiated.getSettlementId(), "REQ-DVP-EXEC");
        final DvPSettlement completed = JsonUtil.fromJson(completedJson, DvPSettlement.class);

        assertThat(completed.getStatus()).isEqualTo("COMPLETED");
        assertThat(account("ACC-BANKALPHA-USD").getBalance()).isEqualByComparingTo("900.00");
        assertThat(account("ACC-BANKBETA-USD").getBalance()).isEqualByComparingTo("1100.00");
        assertThat(account("ACC-BANKALPHA-BOND").getBalance()).isEqualByComparingTo("25.00");
        assertThat(account("ACC-BANKBETA-BOND").getBalance()).isEqualByComparingTo("95.00");
    }

    @Test
    void executeDvPRollsBackWhenAssetLegFails() {
        seedDvPAccounts("1000.00", "1000.00", "2.00", "20.00");

        final String settlementJson = contract.InitiateDvP(
            context,
            "{\"fromAccountId\":\"ACC-BANKALPHA-USD\",\"toAccountId\":\"ACC-BANKBETA-USD\",\"amount\":100.00,\"currency\":\"USD\",\"asset\":\"CASH\"}",
            "{\"fromAccountId\":\"ACC-BANKBETA-BOND\",\"toAccountId\":\"ACC-BANKALPHA-BOND\",\"amount\":5.00,\"currency\":\"UNITS\",\"asset\":\"BOND-US10Y\"}",
            "REQ-DVP"
        );

        final DvPSettlement initiated = JsonUtil.fromJson(settlementJson, DvPSettlement.class);
        assertThatThrownBy(() -> contract.ExecuteDvP(context, initiated.getSettlementId(), "REQ-DVP-EXEC"))
            .isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Insufficient balance");

        assertThat(account("ACC-BANKALPHA-USD").getBalance()).isEqualByComparingTo("1000.00");
        assertThat(account("ACC-BANKBETA-USD").getBalance()).isEqualByComparingTo("1000.00");
        assertThat(account("ACC-BANKALPHA-BOND").getBalance()).isEqualByComparingTo("20.00");
        assertThat(account("ACC-BANKBETA-BOND").getBalance()).isEqualByComparingTo("2.00");
        assertThat(settlement(initiated.getSettlementId()).getStatus()).isEqualTo("PENDING");
        assertThat(state.keySet()).noneMatch(key -> key.startsWith("TX#"));
    }

    @Test
    void initiateDvPRejectsIncorrectAccountTypes() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "1000.00", "CASH", "", "REQ-1");
        contract.CreateAccount(context, "ACC-BANKBETA-USD", "BankBeta", "USD", "1000.00", "CASH", "", "REQ-2");
        contract.CreateAccount(context, "ACC-BANKBETA-USD-2", "BankBeta", "USD", "500.00", "CASH", "", "REQ-3");
        contract.CreateAccount(context, "ACC-BANKALPHA-USD-2", "BankAlpha", "USD", "500.00", "CASH", "", "REQ-4");

        assertThatThrownBy(() -> contract.InitiateDvP(
            context,
            "{\"fromAccountId\":\"ACC-BANKALPHA-USD\",\"toAccountId\":\"ACC-BANKBETA-USD\",\"amount\":100.00,\"currency\":\"USD\",\"asset\":\"CASH\"}",
            "{\"fromAccountId\":\"ACC-BANKBETA-USD-2\",\"toAccountId\":\"ACC-BANKALPHA-USD-2\",\"amount\":5.00,\"currency\":\"USD\",\"asset\":\"BOND-US10Y\"}",
            "REQ-DVP"
        )).isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("must be of type ASSET");
    }

    @Test
    void batchTransferIsAtomicWhenAnyLegFails() {
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", "100.00", "CASH", "", "REQ-A");
        contract.CreateAccount(context, "ACC-BANKBETA-USD", "BankBeta", "USD", "100.00", "CASH", "", "REQ-B");
        contract.CreateAccount(context, "ACC-CLEARING-USD", "ClearingHouse", "USD", "100.00", "CASH", "", "REQ-C");

        final String transfersJson = """
            [
              {
                "fromAccountId":"ACC-BANKALPHA-USD",
                "toAccountId":"ACC-BANKBETA-USD",
                "amount":"50.00",
                "currency":"USD",
                "metadataJson":"{}",
                "clientRequestId":"REQ-BATCH-1"
              },
              {
                "fromAccountId":"ACC-BANKALPHA-USD",
                "toAccountId":"ACC-CLEARING-USD",
                "amount":"75.00",
                "currency":"USD",
                "metadataJson":"{}",
                "clientRequestId":"REQ-BATCH-2"
              }
            ]
            """;

        assertThatThrownBy(() -> contract.BatchTransfer(context, transfersJson, "REQ-BATCH"))
            .isInstanceOf(ChaincodeException.class)
            .hasMessageContaining("Insufficient balance");

        assertThat(account("ACC-BANKALPHA-USD").getBalance()).isEqualByComparingTo("100.00");
        assertThat(account("ACC-BANKBETA-USD").getBalance()).isEqualByComparingTo("100.00");
        assertThat(account("ACC-CLEARING-USD").getBalance()).isEqualByComparingTo("100.00");
        assertThat(state.keySet()).noneMatch(key -> key.startsWith("TX#"));
    }

    @Test
    void dateRangeQueryUsesSelector() {
        when(stub.getQueryResult(anyString())).thenAnswer(invocation -> {
            final String selector = invocation.getArgument(0, String.class);
            assertThat(selector).contains("2026-03-18T00:00:00Z", "2026-03-18T23:59:59Z");
            final TokenTransaction transaction = new TokenTransaction();
            transaction.setTxId("TXN-1");
            transaction.setAmount(new BigDecimal("10.00"));
            transaction.setCurrency("USD");
            transaction.setStatus("SETTLED");
            return new SimpleQueryResultsIterator(List.of(new SimpleKeyValue("TX#TXN-1", JsonUtil.toJson(transaction))));
        });

        final String payload = contract.GetTransactionsByDateRange(
            context,
            "2026-03-18T00:00:00Z",
            "2026-03-18T23:59:59Z"
        );

        assertThat(payload).contains("TXN-1");
    }

    @Test
    void settlementVolumeFiltersByOrgAndDate() {
        when(stub.getQueryResult(anyString())).thenAnswer(invocation -> {
            final String selector = invocation.getArgument(0, String.class);
            assertThat(selector).contains("\"$regex\":\"^2026-03-18\"");

            final TokenTransaction matchingOutgoing = new TokenTransaction();
            matchingOutgoing.setTxId("TXN-OUT");
            matchingOutgoing.setFromOrg("BankAlpha");
            matchingOutgoing.setToOrg("BankBeta");
            matchingOutgoing.setAmount(new BigDecimal("25.00"));
            matchingOutgoing.setCurrency("USD");
            matchingOutgoing.setStatus("SETTLED");

            final TokenTransaction matchingIncoming = new TokenTransaction();
            matchingIncoming.setTxId("TXN-IN");
            matchingIncoming.setFromOrg("ClearingHouse");
            matchingIncoming.setToOrg("BankAlpha");
            matchingIncoming.setAmount(new BigDecimal("10.00"));
            matchingIncoming.setCurrency("USD");
            matchingIncoming.setStatus("SETTLED");

            final TokenTransaction differentOrg = new TokenTransaction();
            differentOrg.setTxId("TXN-OTHER");
            differentOrg.setFromOrg("BankBeta");
            differentOrg.setToOrg("ClearingHouse");
            differentOrg.setAmount(new BigDecimal("99.00"));
            differentOrg.setCurrency("USD");
            differentOrg.setStatus("SETTLED");

            return new SimpleQueryResultsIterator(List.of(
                new SimpleKeyValue("TX#TXN-OUT", JsonUtil.toJson(matchingOutgoing)),
                new SimpleKeyValue("TX#TXN-IN", JsonUtil.toJson(matchingIncoming)),
                new SimpleKeyValue("TX#TXN-OTHER", JsonUtil.toJson(differentOrg))
            ));
        });

        final String payload = contract.GetSettlementVolume(context, "BankAlpha", "2026-03-18");
        assertThat(payload).contains("\"volume\":35.00");
    }

    private void seedDvPAccounts(
        final String alphaCashBalance,
        final String betaCashBalance,
        final String betaAssetBalance,
        final String alphaAssetBalance
    ) {
        when(identity.getMSPID()).thenReturn("ClearingHouseMSP");
        contract.CreateAccount(context, "ACC-BANKALPHA-USD", "BankAlpha", "USD", alphaCashBalance, "CASH", "", "REQ-1");
        contract.CreateAccount(context, "ACC-BANKBETA-USD", "BankBeta", "USD", betaCashBalance, "CASH", "", "REQ-2");
        contract.CreateAccount(context, "ACC-BANKBETA-BOND", "BankBeta", "UNITS", betaAssetBalance, "ASSET", "BOND-US10Y", "REQ-3");
        contract.CreateAccount(context, "ACC-BANKALPHA-BOND", "BankAlpha", "UNITS", alphaAssetBalance, "ASSET", "BOND-US10Y", "REQ-4");
    }

    private TokenAccount account(final String accountId) {
        return JsonUtil.fromJson(state.get("ACCOUNT#" + accountId), TokenAccount.class);
    }

    private DvPSettlement settlement(final String settlementId) {
        return JsonUtil.fromJson(state.get("DVP#" + settlementId), DvPSettlement.class);
    }

    private record SimpleKeyValue(String key, String stringValue) implements KeyValue {
        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getStringValue() {
            return stringValue;
        }

        @Override
        public byte[] getValue() {
            return stringValue.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class SimpleQueryResultsIterator implements QueryResultsIterator<KeyValue> {
        private final List<KeyValue> values;

        private SimpleQueryResultsIterator(final List<KeyValue> values) {
            this.values = values;
        }

        @Override
        public Iterator<KeyValue> iterator() {
            return values.iterator();
        }

        @Override
        public void close() {
        }
    }
}
