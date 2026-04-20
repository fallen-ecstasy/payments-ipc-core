# Idempotent Payment Core

A high-throughput, thread-safe payment ledger engine built with **Java 21** and **Spring Boot**.

Designed to mirror production-grade financial infrastructure, this system implements **double-entry accounting**, **pessimistic row-level locking** for concurrency, and a **Redis-backed idempotency layer** to prevent duplicate charges during network failures.

## 🚀 Key Features

* **Double-Entry Ledger:** Mathematical guarantee that funds are never created or destroyed; every transaction balances to zero.
* **Concurrency Control:** Utilizes PostgreSQL `SELECT ... FOR UPDATE` (Pessimistic Locking) to prevent race conditions during simultaneous transfer requests.
* **Strict Idempotency:** Custom Redis Interceptor ensures that retried network requests (with the same `Idempotency-Key`) return the cached result rather than executing twice.
* **Fail-Safe Transactions:** Fully bounded `@Transactional` operations ensure partial failures trigger a complete database rollback.

## 🛠️ Tech Stack

* **Backend:** Java 21, Spring Boot 3.2, Spring Data JPA
* **Database:** PostgreSQL (Core Ledger), Flyway (Schema Migration)
* **Caching & Locking:** Redis, Spring Data Redis
* **Testing:** JUnit 5, Java ExecutorService (Concurrency testing)
* **DevOps:** Docker, Docker Compose, GitHub Actions

## 🚦 Quick Start (Local Development)

You do not need Java installed to run this. Simply use Docker:

1. Clone the repository
2. Spin up the infrastructure and application:
   ```bash
   docker-compose up --build -d
   ```
3. The API is now running on `http://localhost:8080`.


## 📖 API Documentation
1. Initiate a Transfer
   Moves funds from Account A to Account B. Must include the `Idempotency-Key` header.

Endpoint: `POST` `/api/v1/transfers`

Headers:
`Idempotency-Key: <UUID>`

### Payload:

```JSON
{
"sourceAccountId": "123e4567-e89b-12d3-a456-426614174000",
"destinationAccountId": "123e4567-e89b-12d3-a456-426614174001",
"amount": 50.00
}
```
### Responses:

`201 Created` - Transfer successful.

`409 Conflict` - Request with this Idempotency-Key has already been processed.

`422 Unprocessable Entity` - Insufficient funds or business rule violation.

## 🧪 Testing Concurrency
This project includes a dedicated LedgerConcurrencyTest. It uses a CountDownLatch and a 10-thread ExecutorService to unleash simultaneous requests against the exact same database row to mathematically prove the pessimistic locks hold under pressure.

Run the tests using:


```bash
mvn test
```
