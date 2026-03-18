package com.chainsettle.chaincode.model;

import java.math.BigDecimal;

public class DvPSettlement {
    private String docType = "dvpSettlement";
    private String settlementId;
    private String clientRequestId;
    private SettlementLeg leg1;
    private SettlementLeg leg2;
    private String status;
    private String initiatedBy;
    private String createdAt;
    private String completedAt;
    private String failureReason;

    public String getDocType() {
        return docType;
    }

    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String getSettlementId() {
        return settlementId;
    }

    public void setSettlementId(final String settlementId) {
        this.settlementId = settlementId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(final String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public SettlementLeg getLeg1() {
        return leg1;
    }

    public void setLeg1(final SettlementLeg leg1) {
        this.leg1 = leg1;
    }

    public SettlementLeg getLeg2() {
        return leg2;
    }

    public void setLeg2(final SettlementLeg leg2) {
        this.leg2 = leg2;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(final String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(final String completedAt) {
        this.completedAt = completedAt;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(final String failureReason) {
        this.failureReason = failureReason;
    }

    public static class SettlementLeg {
        private String fromAccountId;
        private String toAccountId;
        private BigDecimal amount;
        private String currency;
        private String asset;

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

        public String getAsset() {
            return asset;
        }

        public void setAsset(final String asset) {
            this.asset = asset;
        }
    }
}

