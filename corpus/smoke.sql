BEGIN;
INSERT INTO borrower VALUES ('B1', 'Budi', true, 5000000, 0, 50000000);
INSERT INTO lender VALUES ('LD1', 'Ani', 20000000);
CALL ajukan_pinjaman('L1', 'B1', 10000000, 12);
CALL danai_pinjaman('F1', 'L1', 'LD1', 10000000);
CALL cairkan_pinjaman('L1');
SELECT status FROM loan WHERE id = 'L1';
ROLLBACK;
