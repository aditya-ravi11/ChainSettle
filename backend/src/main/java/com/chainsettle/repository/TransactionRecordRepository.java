package com.chainsettle.repository;

import com.chainsettle.model.entity.TransactionRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {
    Optional<TransactionRecord> findByTxId(String txId);

    List<TransactionRecord> findTop200ByOrderByCreatedAtDesc();

    List<TransactionRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
}

