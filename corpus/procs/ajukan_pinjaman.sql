-- Meniru LoanSubmissionService (repo semester lalu).
CREATE OR REPLACE PROCEDURE ajukan_pinjaman(p_loan_id text, p_borrower_id text, p_jumlah numeric, p_tenor integer)
LANGUAGE plpgsql AS $$
DECLARE
    v_kyc    boolean;
    v_income numeric;
    v_limit  numeric;
BEGIN
    SELECT kyc_verified, income, loan_limit
      INTO v_kyc, v_income, v_limit
      FROM borrower WHERE id = p_borrower_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Borrower tidak ditemukan: %', p_borrower_id;
    END IF;

    IF NOT v_kyc THEN
        RAISE EXCEPTION 'Borrower belum verifikasi KYC';
    END IF;

    -- Sengaja: income NULL membuat kondisi bernilai NULL sehingga TIDAK ditolak (jebakan J1).
    IF v_income <= 3000000 THEN
        RAISE EXCEPTION 'Penghasilan borrower tidak memenuhi syarat';
    END IF;

    IF p_jumlah > v_limit THEN
        RAISE EXCEPTION 'Limit pinjaman borrower tidak mencukupi';
    END IF;

    INSERT INTO loan (id, borrower_id, amount, collected_amount, tenor_months, interest_rate, status)
    VALUES (p_loan_id, p_borrower_id, p_jumlah, 0, p_tenor, 0.1000, 'PENDING');

    UPDATE borrower SET loan_limit = loan_limit - p_jumlah WHERE id = p_borrower_id;
END;
$$;
