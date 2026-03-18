package com.chainsettle.repository;

import com.chainsettle.model.entity.AccountSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {
    Optional<AccountSnapshot> findTopByAccountIdOrderBySnapshotTimeDesc(String accountId);

    List<AccountSnapshot> findBySnapshotTimeAfterOrderBySnapshotTimeDesc(java.time.Instant snapshotTime);
}

