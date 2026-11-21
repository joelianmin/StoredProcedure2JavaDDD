CREATE OR REPLACE PROCEDURE hitung_denda_kredit (
    p_no_rekening IN VARCHAR2,
    p_tgl_proses  IN DATE,
    p_denda       OUT NUMBER
) AS
    v_tunggakan      NUMBER;
    v_hari_telat     NUMBER;
    v_kolektibilitas NUMBER := 1;
    v_tarif          NUMBER := 0.001;
    v_maks_denda     NUMBER := 5000000;
BEGIN
    SELECT tunggakan_pokok, hari_tunggakan
      INTO v_tunggakan, v_hari_telat
      FROM pinjaman
     WHERE no_rekening = p_no_rekening;

    IF v_hari_telat <= 0 THEN
        p_denda := 0;
    ELSIF v_hari_telat <= 90 THEN
        p_denda := v_tunggakan * v_tarif * v_hari_telat;
        v_kolektibilitas := 2;
    ELSE
        p_denda := v_tunggakan * v_tarif * 90 + 50000;
        v_kolektibilitas := 3;
    END IF;

    IF p_denda > v_maks_denda THEN
        p_denda := v_maks_denda;
    END IF;

    UPDATE pinjaman
       SET denda = p_denda,
           kolektibilitas = v_kolektibilitas,
           tgl_hitung_denda = p_tgl_proses
     WHERE no_rekening = p_no_rekening;

    COMMIT;
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        p_denda := -1;
END;
/
