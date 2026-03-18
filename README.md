# ChainSettle

ChainSettle is a permissioned token settlement simulator built on Hyperledger Fabric, Java Spring Boot, PostgreSQL, and FINOS Perspective.js to model institutional tokenized deposit transfers and atomic DvP settlement.

## Architecture

```text
┌─────────────────────────────────────────────────────────────────────┐
│                        DOCKER COMPOSE                              │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              HYPERLEDGER FABRIC NETWORK                      │   │
│  │  BankAlpha peer + CouchDB                                    │   │
│  │  BankBeta peer + CouchDB                                     │   │
│  │  ClearingHouse peer + CouchDB                                │   │
│  │  Orderer (Raft)                                              │   │
│  │  Channel: settlement-channel                                 │   │
│  │  Chaincode: token-settlement                                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              SPRING BOOT BACKEND                             │   │
│  │  REST API • Fabric Gateway • WebSocket • Analytics Cache     │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              PERSPECTIVE.JS DASHBOARD                        │   │
│  │  Live feed • balances • settlement volume • heatmap          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              POSTGRESQL ANALYTICS CACHE                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

## Motivation

Institutional token settlement platforms such as JPM Coin and Kinexys show why permissioned DLT matters for banks: deterministic counterparties, instant finality semantics, programmable settlement, and strong operational visibility. ChainSettle is a functional simulation of that model for demos, interviews, and systems design exploration. It references the concept, but is not affiliated with JP Morgan.

## Features

- 3-organization Fabric 2.5 network with BankAlpha, BankBeta, and ClearingHouse
- Java smart contract for token accounts, mint/burn, transfers, and atomic DvP settlement
- Spring Boot 3 API with Fabric Gateway integration and `X-ChainSettle-Org` institution switching
- PostgreSQL-backed analytics cache fed by chaincode events
- STOMP over WebSocket live updates for dashboard consumers
- Perspective.js single-page dashboard with transaction feed, balances, volume chart, and activity heatmap
- Transaction simulator that seeds demo accounts and generates live transfer/DvP traffic
- OpenAPI/Swagger docs, unit tests, controller tests, and PostgreSQL Testcontainers integration coverage

## Tech Stack

| Layer | Technology |
| --- | --- |
| Ledger | Hyperledger Fabric 2.5.x |
| Chaincode | Java 17, Fabric contract API |
| Backend | Spring Boot 3.3, Fabric Gateway, JPA, WebSocket |
| Database | PostgreSQL 15 |
| Frontend | FINOS Perspective.js 2.8.1, SockJS, STOMP |
| Build | Gradle Kotlin DSL |
| Containers | Docker, Docker Compose |

## Prerequisites

- Docker and `docker-compose`
- Java 17+
- `curl`, `tar`, and Git
- Node.js only if you want to iterate on the dashboard outside nginx

## Quick Start

```bash
git clone https://github.com/yourusername/chainsettle.git
cd chainsettle
cp .env.example .env
chmod +x network.sh backend/gradlew chaincode/gradlew
./network.sh up
docker-compose up -d --build
```

Open:

- Dashboard: [http://localhost:3000](http://localhost:3000)
- Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Actuator health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

## API Reference

Swagger is available at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

| Endpoint group | Examples |
| --- | --- |
| Accounts | `POST /api/v1/accounts`, `GET /api/v1/accounts/{accountId}` |
| Tokens | `POST /api/v1/tokens/mint`, `POST /api/v1/tokens/burn` |
| Transfers | `POST /api/v1/transfers`, `POST /api/v1/transfers/batch`, `GET /api/v1/transfers?account=...` |
| DvP | `POST /api/v1/settlements/dvp`, `POST /api/v1/settlements/dvp/{id}/execute` |
| Analytics | `GET /api/v1/analytics/summary`, `GET /api/v1/analytics/balances` |
| Network | `GET /api/v1/network/health`, `GET /api/v1/network/channel` |

## Dashboard Screenshots

Live screenshots are intended to be captured after the first successful end-to-end run and stored under `docs/images/generated/`. This environment generated the dashboard implementation, but did not yet produce runtime screenshots.
<!-- TODO: Add dashboard screenshot after runtime pass -->

Recommended captures after startup:

- `docs/images/generated/dashboard-live-feed.png`
- `docs/images/generated/dashboard-analytics.png`

## Testing

```bash
cd chaincode && ./gradlew test
cd ../backend && ./gradlew test
```

Fabric-backed smoke testing:

```bash
./network.sh up
docker-compose up -d --build
curl -H "X-ChainSettle-Org: BankAlpha" http://localhost:8080/api/v1/network/health
```

## Project Structure

```text
chainsettle/
├── README.md
├── docker-compose.yaml
├── docker-compose-fabric.yaml
├── network.sh
├── fabric-config/
├── chaincode/
├── backend/
├── dashboard/
└── sql/
```

## Design Decisions

- Hyperledger Fabric: permissioned membership and endorsement policies fit institutional settlement better than public chains.
- Java chaincode: keeps the smart contract layer aligned with enterprise JVM tooling and the Spring backend.
- Perspective.js: uses FINOS Perspective, a JP Morgan open-source contribution, for high-density real-time analytics.
- DvP: models a canonical capital markets settlement pattern where cash and asset movement must complete atomically.
- CouchDB: enables rich JSON queries for date ranges, organization filters, and settlement lookups.

## Future Enhancements

- Multi-currency netting engine for end-of-day optimization
- Regulatory reporting workflows for MiFID II and Dodd-Frank style outputs
- Post-quantum channel and key management experiments
- Live FX rate integration for cross-currency settlement scenarios

## Resume Bullets

- Engineered a permissioned blockchain settlement platform on Hyperledger Fabric simulating institutional tokenized deposit transfers and atomic DvP settlement across a 3-organization network.
- Built a Java Spring Boot API with Fabric Gateway integration, WebSocket event streaming, and PostgreSQL analytics persistence for high-volume simulated settlements.
- Delivered a FINOS Perspective.js monitoring dashboard with live transaction feeds, balance tracking, and inter-institution flow visualization.
