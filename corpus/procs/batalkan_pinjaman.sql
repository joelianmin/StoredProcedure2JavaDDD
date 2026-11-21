-- Meniru LoanCancellationService + skenario Gherkin "dana dikembalikan ke masing-masing lender".
CREATE OR REPLACE PROCEDURE batalkan_pinjaman(p_loan_id text)
LANGUAGE plpgsql AS $$
DECLARE
    v_amount      numeric;
    v_status      text;
    v_borrower_id text;
    r             record;
BEGIN
    SELECT amount, status, borrower_id
      INTO v_amount, v_status, v_borrower_id
      FROM loan WHERE id = p_loan_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Loan tidak ditemukan: %', p_loan_id;
    END IF;

    IF v_status = 'DISBURSED' OR v_status = 'REPAID' THEN
        RAISE EXCEPTION 'Pinjaman tidak dapat dibatalkan setelah dicairkan';
    END IF;

    FOR r IN SELECT id, lender_id, amount FROM funding WHERE loan_id = p_loan_id ORDER BY id LOOP
        UPDATE lender SET balance = balance + r.amount WHERE id = r.lender_id;
    END LOOP;

    DELETE FROM funding WHERE loan_id = p_loan_id;

    UPDATE borrower SET loan_limit = loan_limit + v_amount WHERE id = v_borrower_id;
    UPDATE loan SET status = 'CANCELLED', collected_amount = 0 WHERE id = p_loan_id;
END;
$$;
