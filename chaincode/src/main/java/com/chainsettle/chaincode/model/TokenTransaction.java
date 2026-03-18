package com.chainsettle.chaincode.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class TokenTransaction {
    private String docType = "tokenTransaction";
    private String txId;
    private String clientRequestId;
    private String txType;
    private String fromAccountId;
    private String toAccountId;
    private String fromOrg;
    private String toOrg;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String initiatedBy;
    private List<String> endorsedBy;
    private String timestamp;
    private String settlementRef;
    private Map<String, Object> metadata;

    public String getDocType() {
        return docType;
    }

    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(final String txId) {
        this.txId = txId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(final String clientRequestId) {
        this.clientRequestId = clientRequestId;
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

    public String getInitiatedBy() {
        return initiatedBy;
    }

    public void setInitiatedBy(final String initiatedBy) {
        this.initiatedBy = initiatedBy;
    }

    public List<String> getEndorsedBy() {
        return endorsedBy;
    }

    public void setEndorsedBy(final List<String> endorsedBy) {
        this.endorsedBy = endorsedBy;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final String timestamp) {
        this.timestamp = timestamp;
    }

    public String getSettlementRef() {
        return settlementRef;
    }

    public void setSettlementRef(final String settlementRef) {
        this.settlementRef = settlementRef;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(final Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}

