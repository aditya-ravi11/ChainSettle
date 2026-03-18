package com.chainsettle.repository;

import com.chainsettle.model.entity.SettlementRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, Long> {
    Optional<SettlementRecord> findBySettlementId(String settlementId);
}

