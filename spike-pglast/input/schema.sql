CREATE TABLE lender (
    id      text PRIMARY KEY,
    name    text,
    balance numeric(15,2) NOT NULL
);

CREATE TABLE loan (
    id               text PRIMARY KEY,
    borrower_id      text NOT NULL,
    amount           numeric(15,2) NOT NULL,
    collected_amount numeric(15,2) NOT NULL,
    status           text NOT NULL
);
