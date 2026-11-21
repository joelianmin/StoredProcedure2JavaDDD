package com.p2p.snap;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cek header wajib SNAP (keberadaan dan format, TANPA verifikasi kriptografi) lalu idempotensi X-EXTERNAL-ID.
 * Urutan cek: Content-Type, Authorization, X-TIMESTAMP, X-SIGNATURE, X-PARTNER-ID, X-EXTERNAL-ID, CHANNEL-ID.
 */
public class SnapHeaderFilter extends OncePerRequestFilter {
    private static final Pattern TIMESTAMP = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[+-]\\d{2}:\\d{2}$");
    private static final Pattern BASE64 = Pattern.compile("^[A-Za-z0-9+/]+={0,2}$");
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    private final SnapRoutes routes;
    private final ExternalIdRegistry registry;
    private final Clock clock;
    private final ObjectMapper json;

    public SnapHeaderFilter(SnapRoutes routes, ExternalIdRegistry registry, Clock clock, ObjectMapper json) {
        this.routes = routes;
        this.registry = registry;
        this.clock = clock;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> serviceCode = routes.serviceCodeFor(req.getRequestURI());
        if (serviceCode.isEmpty()) {
            chain.doFilter(req, res);
            return;
        }
        try {
            validate(req);
            String partner = req.getHeader("X-PARTNER-ID");
            String external = req.getHeader("X-EXTERNAL-ID");
            if (!registry.register(partner, external, LocalDate.now(clock))) {
                throw new SnapRequestException(409, "00", "Conflict");
            }
        } catch (SnapRequestException e) {
            res.setStatus(e.getHttp());
            res.setContentType("application/json");
            json.writeValue(res.getOutputStream(), SnapErrors.fromRequest(e, serviceCode.get(), null).getBody());
            return;
        }
        chain.doFilter(req, res);
    }

    private static String required(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        if (v == null || v.isBlank()) throw new SnapRequestException(400, "02", "Invalid Mandatory Field {" + name + "}");
        return v;
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new SnapRequestException(400, "01", "Invalid Field Format {" + name + "}");
    }

    static void validate(HttpServletRequest req) {
        check(required(req, "Content-Type").toLowerCase().startsWith("application/json"), "Content-Type");
        String auth = required(req, "Authorization");
        check(auth.startsWith("Bearer ") && auth.length() > 7, "Authorization");
        String ts = required(req, "X-TIMESTAMP");
        boolean tsOk = TIMESTAMP.matcher(ts).matches();
        if (tsOk) {
            try {
                OffsetDateTime.parse(ts);
            } catch (RuntimeException e) {
                tsOk = false;
            }
        }
        check(tsOk, "X-TIMESTAMP");
        check(BASE64.matcher(required(req, "X-SIGNATURE")).matches(), "X-SIGNATURE");
        check(required(req, "X-PARTNER-ID").length() <= 36, "X-PARTNER-ID");
        String ext = required(req, "X-EXTERNAL-ID");
        check(ext.length() <= 36 && DIGITS.matcher(ext).matches(), "X-EXTERNAL-ID");
        String ch = required(req, "CHANNEL-ID");
        check(ch.length() <= 5 && DIGITS.matcher(ch).matches(), "CHANNEL-ID");
    }
}
