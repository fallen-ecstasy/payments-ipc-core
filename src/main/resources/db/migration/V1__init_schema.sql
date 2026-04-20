-- V1__init_schema.sql

CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          name VARCHAR(255) NOT NULL,
    -- DECIMAL(19,4) prevents floating-point rounding errors in financial data
    -- The CHECK constraint is our final defense against negative balances
                          balance DECIMAL(19, 4) NOT NULL CHECK (balance >= 0),
                          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              idempotency_key VARCHAR(255) UNIQUE NOT NULL,
                              status VARCHAR(50) NOT NULL, -- PENDING, COMPLETED, FAILED
                              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,
                                transaction_id UUID NOT NULL REFERENCES transactions(id),
                                account_id UUID NOT NULL REFERENCES accounts(id),
                                amount DECIMAL(19, 4) NOT NULL,
                                direction VARCHAR(10) NOT NULL, -- 'CREDIT', 'DEBIT'
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast idempotency lookups at the database level
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);