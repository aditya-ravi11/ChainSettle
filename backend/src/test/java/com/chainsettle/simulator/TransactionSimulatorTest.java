package com.chainsettle.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainsettle.service.AccountService;
import com.chainsettle.service.SettlementService;
import com.chainsettle.service.TransferService;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TransactionSimulatorTest {
    @Mock
    private AccountService accountService;

    @Mock
    private TransferService transferService;

    @Mock
    private SettlementService settlementService;

    private TransactionSimulator simulator;

    @BeforeEach
    void setUp() {
        simulator = new TransactionSimulator(accountService, transferService, settlementService);
        ReflectionTestUtils.setField(simulator, "batchProbability", 0.10d);
        ReflectionTestUtils.setField(simulator, "dvpProbability", 0.20d);
    }

    @Test
    void selectActionMatchesConfiguredDistribution() {
        final long batchCount = IntStream.range(0, 100)
            .mapToObj(index -> simulator.selectAction((index + 0.5d) / 100.0d))
            .filter(action -> action == TransactionSimulator.SimulationAction.BATCH)
            .count();
        final long dvpCount = IntStream.range(0, 100)
            .mapToObj(index -> simulator.selectAction((index + 0.5d) / 100.0d))
            .filter(action -> action == TransactionSimulator.SimulationAction.DVP)
            .count();
        final long transferCount = IntStream.range(0, 100)
            .mapToObj(index -> simulator.selectAction((index + 0.5d) / 100.0d))
            .filter(action -> action == TransactionSimulator.SimulationAction.TRANSFER)
            .count();

        assertThat(batchCount).isEqualTo(10);
        assertThat(dvpCount).isEqualTo(20);
        assertThat(transferCount).isEqualTo(70);
    }
}
