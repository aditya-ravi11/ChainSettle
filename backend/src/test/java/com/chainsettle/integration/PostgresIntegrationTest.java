package com.chainsettle.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.chainsettle.model.entity.TransactionRecord;
import com.chainsettle.repository.TransactionRecordRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PostgresIntegrationTest {
    private static final String TEST_PASSWORD = UUID.randomUUID().toString();

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("chainsettle")
        .withUsername("chainsettle")
        .withPassword(TEST_PASSWORD)
        .withInitScript("init.sql");

    @DynamicPropertySource
    static void configureDatabase(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRecordRepository transactionRecordRepository;

    @Test
    void transactionRecordRepositorySavesAndReadsRecords() {
        final TransactionRecord record = new TransactionRecord();
        record.setTxId("TXN-INTEGRATION-1");
        record.setTxType("TRANSFER");
        record.setAmount(new BigDecimal("2500.00"));
        record.setCurrency("USD");
        record.setStatus("SETTLED");
        record.setOrgInitiator("BankAlpha");
        record.setFromOrg("BankAlpha");
        record.setToOrg("BankBeta");
        record.setCreatedAt(Instant.now());

        transactionRecordRepository.save(record);

        assertThat(transactionRecordRepository.findByTxId("TXN-INTEGRATION-1")).isPresent();
    }
}
