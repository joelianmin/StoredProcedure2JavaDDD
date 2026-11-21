package com.p2p.snap;

/** Pemetaan pesan RAISE (awalan) -> HTTP + kode kasus SNAP; ditentukan tim di mapping.yaml. */
public record ErrorRule(String prefix, int http, String caseCode, String message) {}
