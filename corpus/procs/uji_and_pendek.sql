-- Procedure uji (temuan review #3): AND dan COALESCE berhenti di argumen pertama yang menentukan.
CREATE OR REPLACE PROCEDURE uji_and_pendek(p_jumlah numeric, p_tenor integer)
LANGUAGE plpgsql AS $$
DECLARE
    v_cicilan numeric;
BEGIN
    IF p_tenor > 0 AND p_jumlah / p_tenor > 1000 THEN
        RAISE EXCEPTION 'cicilan besar';
    END IF;
    v_cicilan := coalesce(p_jumlah, p_jumlah / p_tenor);
    IF v_cicilan IS NULL THEN
        RAISE EXCEPTION 'cicilan kosong';
    END IF;
END;
$$;
