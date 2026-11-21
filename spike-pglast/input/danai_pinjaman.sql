-- Versi "legacy" dari FundingService.fund() semester lalu, ditulis sebagai stored procedure.
-- Urutan debit-sebelum-validasi sengaja ditiru: di PostgreSQL ini AMAN karena RAISE me-rollback semuanya.
CREATE OR REPLACE PROCEDURE danai_pinjaman(p_loan_id text, p_lender_id text, p_jumlah numeric)
LANGUAGE plpgsql AS $$
DECLARE
    v_target     numeric;
    v_terkumpul  numeric;
    v_status     text;
    v_saldo      numeric;
BEGIN
    SELECT amount, collected_amount, status
      INTO v_target, v_terkumpul, v_status
      FROM loan WHERE id = p_loan_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Loan tidak ditemukan: %', p_loan_id;
    END IF;

    SELECT balance INTO v_saldo FROM lender WHERE id = p_lender_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Lender tidak ditemukan: %', p_lender_id;
    END IF;

    IF v_status <> 'PENDING' THEN
        RAISE EXCEPTION 'Loan % tidak dalam status PENDING', p_loan_id;
    END IF;

    -- debit dulu (seperti kode lama) ...
    UPDATE lender SET balance = balance - p_jumlah WHERE id = p_lender_id;

    -- ... baru validasi. Kalau gagal, debit di atas ikut dibatalkan oleh PostgreSQL.
    IF p_jumlah > v_target - v_terkumpul THEN
        RAISE EXCEPTION 'Pendanaan melebihi sisa target pinjaman';
    END IF;

    UPDATE loan SET collected_amount = collected_amount + p_jumlah WHERE id = p_loan_id;

    IF v_terkumpul + p_jumlah >= v_target THEN
        UPDATE loan SET status = 'FUNDED' WHERE id = p_loan_id;
    END IF;
END;
$$;
