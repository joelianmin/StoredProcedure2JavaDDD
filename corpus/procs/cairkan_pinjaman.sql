-- Meniru DisbursementService (repo semester lalu).
CREATE OR REPLACE PROCEDURE cairkan_pinjaman(p_loan_id text)
LANGUAGE plpgsql AS $$
DECLARE
    v_amount      numeric;
    v_status      text;
    v_borrower_id text;
BEGIN
    SELECT amount, status, borrower_id
      INTO v_amount, v_status, v_borrower_id
      FROM loan WHERE id = p_loan_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Loan tidak ditemukan: %', p_loan_id;
    END IF;

    IF v_status <> 'FUNDED' THEN
        RAISE EXCEPTION 'Hanya pinjaman berstatus FUNDED yang bisa dicairkan';
    END IF;

    UPDATE borrower SET balance = balance + v_amount WHERE id = v_borrower_id;
    UPDATE loan SET status = 'DISBURSED' WHERE id = p_loan_id;
END;
$$;
