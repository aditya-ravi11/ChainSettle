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
@Table(name = "transaction_records")
public class TransactionRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false, unique = true, length = 100)
    private String txId;

    @Column(name = "tx_type", nullable = false, length = 40)
    private String txType;

    @Column(name = "from_account_id", length = 100)
    private String fromAccountId;

    @Column(name = "to_account_id", length = 100)
    private String toAccountId;

    @Column(name = "from_org", length = 80)
    private String fromOrg;

    @Column(name = "to_org", length = 80)
    private String toOrg;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 20)
    private String currency;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "org_initiator", nullable = false, length = 80)
    private String orgInitiator;

    @Column(name = "fabric_tx_id", length = 255)
    private String fabricTxId;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "settlement_ref", length = 100)
    private String settlementRef;

    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(final String txId) {
        this.txId = txId;
    }

    public String getTxType() {
        return txType;
    }

    public void setTxType(final String txType) {
        this.txType = txType;
    }

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

    public String getFromOrg() {
        return fromOrg;
    }

    public void setFromOrg(final String fromOrg) {
        this.fromOrg = fromOrg;
    }

    public String getToOrg() {
        return toOrg;
    }

    public void setToOrg(final String toOrg) {
        this.toOrg = toOrg;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(final BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(final String currency) {
        this.currency = currency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getOrgInitiator() {
        return orgInitiator;
    }

    public void setOrgInitiator(final String orgInitiator) {
        this.orgInitiator = orgInitiator;
    }

    public String getFabricTxId() {
        return fabricTxId;
    }

    public void setFabricTxId(final String fabricTxId) {
        this.fabricTxId = fabricTxId;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public void setBlockNumber(final Long blockNumber) {
        this.blockNumber = blockNumber;
    }

    public String getSettlementRef() {
        return settlementRef;
    }

    public void setSettlementRef(final String settlementRef) {
        this.settlementRef = settlementRef;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(final String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }
}

