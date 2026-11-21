-- Skema platform P2P lending (versi legacy). Hanya PK satu kolom, NOT NULL, dan tipe dasar (spec §6.6).
CREATE TABLE borrower (
    id           text PRIMARY KEY,
    name         text NOT NULL,
    kyc_verified boolean NOT NULL,
    income       numeric(15,2),            -- nullable: sumber jebakan J1
    balance      numeric(15,2) NOT NULL,
    loan_limit   numeric(15,2) NOT NULL
);

CREATE TABLE lender (
    id      text PRIMARY KEY,
    name    text NOT NULL,
    balance numeric(15,2) NOT NULL
);

CREATE TABLE loan (
    id               text PRIMARY KEY,
    borrower_id      text NOT NULL,
    amount           numeric(15,2) NOT NULL,
    collected_amount numeric(15,2) NOT NULL,
    tenor_months     integer NOT NULL,
    interest_rate    numeric(5,4) NOT NULL, -- 0.1000 = 10% per tahun
    status           text NOT NULL
);

CREATE TABLE funding (
    id        text PRIMARY KEY,
    loan_id   text NOT NULL,
    lender_id text NOT NULL,
    amount    numeric(15,2) NOT NULL
);
