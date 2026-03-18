package com.chainsettle.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account_snapshots")
public class AccountSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "org_name", nullable = false, length = 80)
    private String orgName;

    @Column(nullable = false, length = 20)
    private String currency;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "asset_type", length = 80)
    private String assetType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "snapshot_time", nullable = false)
    private Instant snapshotTime;

    @PrePersist
    public void prePersist() {
        if (snapshotTime == null) {
            snapshotTime = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(final String accountId) {
        this.accountId = accountId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(final String orgName) {
        this.orgName = orgName;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(final String accountType) {
        this.accountType = accountType;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(final String assetType) {
        this.assetType = assetType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(final BigDecimal balance) {
        this.balance = balance;
    }

    public Instant getSnapshotTime() {
        return snapshotTime;
    }

    public void setSnapshotTime(final Instant snapshotTime) {
        this.snapshotTime = snapshotTime;
    }
}

