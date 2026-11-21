package com.p2p.snap;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/** JSON rusak -> 400xx01; SnapRequestException dari controller -> kode SNAP-nya. */
@RestControllerAdvice
public class SnapExceptionHandler {
    private final SnapRoutes routes;

    public SnapExceptionHandler(SnapRoutes routes) {
        this.routes = routes;
    }

    private String serviceCode(HttpServletRequest req) {
        return routes.serviceCodeFor(req.getRequestURI()).orElse("00");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<SnapResponse> unreadable(HttpServletRequest req) {
        return SnapErrors.fromRequest(new SnapRequestException(400, "01", "Invalid Field Format"), serviceCode(req), null);
    }

    @ExceptionHandler(SnapRequestException.class)
    public ResponseEntity<SnapResponse> invalid(SnapRequestException e, HttpServletRequest req) {
        return SnapErrors.fromRequest(e, serviceCode(req), null);
    }
}
