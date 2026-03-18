const API_BASE = window.CHAINSETTLE_API_BASE || "http://localhost:8080";
const HEALTH_URL = `${API_BASE}/api/v1/network/health`;
const FEED_URL = `${API_BASE}/api/v1/analytics/perspective-feed`;
const BALANCE_URL = `${API_BASE}/api/v1/analytics/balances`;

const txSchema = {
    txId: "string",
    txType: "string",
    fromAccountId: "string",
    toAccountId: "string",
    fromOrg: "string",
    toOrg: "string",
    amount: "float",
    currency: "string",
    status: "string",
    timestamp: "datetime",
    timestampBucket: "datetime"
};

const balanceSchema = {
    accountId: "string",
    orgName: "string",
    currency: "string",
    accountType: "string",
    assetType: "string",
    balance: "float",
    snapshotTime: "datetime"
};

let txTable;
let balanceTable;

async function initializeDashboard() {
    const worker = window.perspective.worker();
    txTable = await worker.table(txSchema, { index: "txId" });
    balanceTable = await worker.table(balanceSchema, { index: "accountId" });

    await Promise.all([
        configureTransactionsViewer(document.getElementById("tx-feed")),
        configureBalanceViewer(document.getElementById("balance-overview")),
        configureVolumeViewer(document.getElementById("volume-over-time")),
        configureHeatmapViewer(document.getElementById("network-heatmap"))
    ]);

    await Promise.all([loadInitialTransactions(), loadInitialBalances(), refreshHealth()]);
    connectWebSocket();
    setInterval(refreshHealth, 15000);
}

async function configureTransactionsViewer(viewer) {
    await viewer.load(txTable);
    await viewer.restore({
        theme: "Pro Dark",
        plugin: "Datagrid",
        columns: ["txId", "txType", "fromOrg", "toOrg", "amount", "currency", "status", "timestamp"],
        sort: [["timestamp", "desc"]]
    });
}

async function configureBalanceViewer(viewer) {
    await viewer.load(balanceTable);
    await viewer.restore({
        theme: "Pro Dark",
        plugin: "Y Bar",
        group_by: ["orgName"],
        split_by: ["currency"],
        columns: ["balance"],
        aggregates: { balance: "last" }
    });
}

async function configureVolumeViewer(viewer) {
    await viewer.load(txTable);
    await viewer.restore({
        theme: "Pro Dark",
        plugin: "Y Line",
        group_by: ["timestampBucket"],
        split_by: ["fromOrg"],
        columns: ["amount"],
        aggregates: { amount: "sum" },
        sort: [["timestampBucket", "asc"]]
    });
}

async function configureHeatmapViewer(viewer) {
    await viewer.load(txTable);
    await viewer.restore({
        theme: "Pro Dark",
        plugin: "Heatmap",
        group_by: ["fromOrg"],
        split_by: ["toOrg"],
        columns: ["amount"],
        aggregates: { amount: "sum" }
    });
}

async function loadInitialTransactions() {
    const payload = await fetchJson(FEED_URL);
    if (Array.isArray(payload) && payload.length > 0) {
        await txTable.update(payload.map(normalizeTransaction));
    }
}

async function loadInitialBalances() {
    const payload = await fetchJson(BALANCE_URL);
    if (Array.isArray(payload) && payload.length > 0) {
        await balanceTable.update(payload.map(normalizeBalance));
    }
}

function connectWebSocket() {
    const socket = new SockJS(`${API_BASE}/ws`);
    const client = Stomp.over(socket);
    client.debug = () => {};
    client.connect({}, () => {
        client.subscribe("/topic/transactions", async frame => {
            const message = JSON.parse(frame.body);
            await txTable.update([normalizeTransaction(message)]);
        });
        client.subscribe("/topic/balances", async frame => {
            const message = JSON.parse(frame.body);
            if (Array.isArray(message) && message.length > 0) {
                await balanceTable.update(message.map(normalizeBalance));
            }
        });
        client.subscribe("/topic/events", frame => {
            const message = JSON.parse(frame.body);
            if (message && message.event) {
                setNetworkStatus(`Live: ${message.event}`, true);
            }
        });
        setNetworkStatus("Live stream connected", true);
    }, () => {
        setNetworkStatus("WebSocket disconnected", false);
        setTimeout(connectWebSocket, 5000);
    });
}

async function refreshHealth() {
    try {
        const payload = await fetchJson(HEALTH_URL);
        setNetworkStatus(payload.status === "UP" ? "Network healthy" : payload.message || payload.status, payload.status === "UP");
    } catch (error) {
        setNetworkStatus("Backend unavailable", false);
    }
}

async function fetchJson(url) {
    const response = await fetch(url, { headers: { Accept: "application/json" } });
    if (!response.ok) {
        throw new Error(`Failed to fetch ${url}: ${response.status}`);
    }
    return response.json();
}

function normalizeTransaction(record) {
    const timestamp = record.timestamp || new Date().toISOString();
    return {
        txId: record.txId,
        txType: record.txType || "TRANSFER",
        fromAccountId: record.fromAccountId || "",
        toAccountId: record.toAccountId || "",
        fromOrg: record.fromOrg || inferOrg(record.fromAccountId),
        toOrg: record.toOrg || inferOrg(record.toAccountId),
        amount: Number(record.amount || 0),
        currency: record.currency || "USD",
        status: record.status || "SETTLED",
        timestamp,
        timestampBucket: new Date(Math.floor(new Date(timestamp).getTime() / 60000) * 60000).toISOString()
    };
}

function normalizeBalance(record) {
    return {
        accountId: record.accountId,
        orgName: record.orgName,
        currency: record.currency,
        accountType: record.accountType,
        assetType: record.assetType || "",
        balance: Number(record.balance || 0),
        snapshotTime: record.snapshotTime || new Date().toISOString()
    };
}

function inferOrg(accountId = "") {
    if (accountId.includes("BANKALPHA")) {
        return "BankAlpha";
    }
    if (accountId.includes("BANKBETA")) {
        return "BankBeta";
    }
    if (accountId.includes("CLEARINGHOUSE")) {
        return "ClearingHouse";
    }
    return "Unknown";
}

function setNetworkStatus(label, online) {
    document.getElementById("network-status").textContent = label;
    const dot = document.getElementById("status-dot");
    dot.classList.toggle("online", Boolean(online));
}

window.addEventListener("DOMContentLoaded", initializeDashboard);

