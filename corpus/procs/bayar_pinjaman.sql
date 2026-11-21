-- Meniru RepaymentService + FixedInterestStrategy (repo semester lalu), tapi dengan numeric, bukan double.
CREATE OR REPLACE PROCEDURE bayar_pinjaman(p_loan_id text)
LANGUAGE plpgsql AS $$
DECLARE
    v_amount      numeric;
    v_tenor       integer;
    v_rate        numeric;
    v_status      text;
    v_borrower_id text;
    v_bunga       numeric;
    v_total       numeric;
    v_saldo       numeric;
BEGIN
    SELECT amount, tenor_months, interest_rate, status, borrower_id
      INTO v_amount, v_tenor, v_rate, v_status, v_borrower_id
      FROM loan WHERE id = p_loan_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Loan tidak ditemukan: %', p_loan_id;
    END IF;

    IF v_status <> 'DISBURSED' THEN
        RAISE EXCEPTION 'Loan belum dicairkan';
    END IF;

    v_bunga := round(v_amount * v_rate * v_tenor / 12, 2);
    v_total := v_amount + v_bunga;

    SELECT balance INTO v_saldo FROM borrower WHERE id = v_borrower_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Borrower tidak ditemukan: %', v_borrower_id;
    END IF;

    IF v_saldo < v_total THEN
        RAISE EXCEPTION 'Saldo borrower tidak mencukupi';
    END IF;

    UPDATE borrower SET balance = balance - v_total WHERE id = v_borrower_id;
    UPDATE loan SET status = 'REPAID' WHERE id = p_loan_id;
END;
$$;
