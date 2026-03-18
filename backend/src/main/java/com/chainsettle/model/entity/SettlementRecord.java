package com.chainsettle.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "settlement_records")
public class SettlementRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_id", nullable = false, unique = true, length = 100)
    private String settlementId;

    @Column(name = "settlement_type", nullable = false, length = 20)
    private String settlementType;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "cash_leg_json", nullable = false, columnDefinition = "jsonb")
    private String cashLegJson;

    @Column(name = "asset_leg_json", nullable = false, columnDefinition = "jsonb")
    private String assetLegJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    public String getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(final String settlementId) {
        this.settlementId = settlementId;
    }

    public String getSettlementType() {
        return settlementType;
    }

    public void setSettlementType(final String settlementType) {
        this.settlementType = settlementType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getCashLegJson() {
        return cashLegJson;
    }

    public void setCashLegJson(final String cashLegJson) {
        this.cashLegJson = cashLegJson;
    }

    public String getAssetLegJson() {
        return assetLegJson;
    }

    public void setAssetLegJson(final String assetLegJson) {
        this.assetLegJson = assetLegJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(final Instant completedAt) {
        this.completedAt = completedAt;
    }
}

