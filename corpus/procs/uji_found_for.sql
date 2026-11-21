-- Procedure uji (temuan review #2): FOUND setelah FOR = true bila loop berjalan minimal sekali.
CREATE OR REPLACE PROCEDURE uji_found_for(p_loan_id text)
LANGUAGE plpgsql AS $$
DECLARE
    r record;
BEGIN
    FOR r IN SELECT id, lender_id, amount FROM funding WHERE loan_id = p_loan_id ORDER BY id LOOP
        UPDATE lender SET balance = balance + r.amount WHERE id = r.lender_id;
    END LOOP;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'tidak ada funding';
    END IF;
END;
$$;
