-- Meniru FundingService.fund() (repo semester lalu), termasuk urutan debit-sebelum-validasi.
-- Di PostgreSQL urutan ini aman: RAISE membatalkan seluruh transaksi.
CREATE OR REPLACE PROCEDURE danai_pinjaman(p_funding_id text, p_loan_id text, p_lender_id text, p_jumlah numeric)
LANGUAGE plpgsql AS $$
DECLARE
    v_target    numeric;
    v_terkumpul numeric;
    v_status    text;
    v_saldo     numeric;
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

    IF v_saldo < p_jumlah THEN
        RAISE EXCEPTION 'Saldo lender tidak mencukupi';
    END IF;

    UPDATE lender SET balance = balance - p_jumlah WHERE id = p_lender_id;

    IF p_jumlah > v_target - v_terkumpul THEN
        RAISE EXCEPTION 'Pendanaan melebihi sisa target pinjaman';
    END IF;

    INSERT INTO funding (id, loan_id, lender_id, amount)
    VALUES (p_funding_id, p_loan_id, p_lender_id, p_jumlah);

    UPDATE loan SET collected_amount = collected_amount + p_jumlah WHERE id = p_loan_id;

    IF v_terkumpul + p_jumlah >= v_target THEN
        UPDATE loan SET status = 'FUNDED' WHERE id = p_loan_id;
    END IF;
END;
$$;
