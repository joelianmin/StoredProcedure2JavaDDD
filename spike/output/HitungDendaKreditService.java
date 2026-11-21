package id.bank.modernisasi;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Hasil generate otomatis dari PL/SQL procedure `hitung_denda_kredit`. */
@Service
public class HitungDendaKreditService {

    public record HitungDendaKreditResult(BigDecimal denda) {}

    private final HitungDendaKreditRepository repository;

    public HitungDendaKreditService(HitungDendaKreditRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public HitungDendaKreditResult execute(String noRekening, LocalDate tglProses) {
        BigDecimal denda = null;
        BigDecimal tunggakan = null;
        BigDecimal hariTelat = null;
        BigDecimal kolektibilitas = BigDecimal.ONE;
        BigDecimal tarif = new BigDecimal("0.001");
        BigDecimal maksDenda = new BigDecimal("5000000");

        try {
            var pinjamanRow = repository.findPinjaman(noRekening);
            tunggakan = pinjamanRow.tunggakanPokok();
            hariTelat = pinjamanRow.hariTunggakan();
            if (hariTelat.compareTo(BigDecimal.ZERO) <= 0) {
                denda = BigDecimal.ZERO;
            } else if (hariTelat.compareTo(new BigDecimal("90")) <= 0) {
                denda = tunggakan.multiply(tarif).multiply(hariTelat);
                kolektibilitas = new BigDecimal("2");
            } else {
                denda = tunggakan.multiply(tarif).multiply(new BigDecimal("90")).add(new BigDecimal("50000"));
                kolektibilitas = new BigDecimal("3");
            }
            if (denda.compareTo(maksDenda) > 0) {
                denda = maksDenda;
            }
            repository.updatePinjaman(noRekening, denda, kolektibilitas, tglProses);
            // COMMIT -> ditangani @Transactional
        } catch (EmptyResultDataAccessException e) {
            denda = new BigDecimal("-1");
        }
        return new HitungDendaKreditResult(denda);
    }
}
