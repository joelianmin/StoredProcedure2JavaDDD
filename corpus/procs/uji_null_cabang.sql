-- Procedure uji (temuan review #1): variabel yang hanya diisi di dalam IF bisa tetap NULL.
CREATE OR REPLACE PROCEDURE uji_null_cabang(p_flag boolean)
LANGUAGE plpgsql AS $$
DECLARE
    v_extra numeric;
    v_s     text;
BEGIN
    IF p_flag THEN
        v_extra := 100;
        v_s := 'X';
    END IF;
    IF v_extra > 50 THEN
        RAISE EXCEPTION 'extra besar';
    END IF;
    IF 'APPROVED' <> v_s THEN
        RAISE EXCEPTION 'status beda';
    END IF;
END;
$$;
