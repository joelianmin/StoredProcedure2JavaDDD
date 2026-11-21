package com.p2p.snap;

/** Request tidak valid menurut konvensi SNAP (header/body). */
public class SnapRequestException extends RuntimeException {
    private final int http;
    private final String caseCode;

    public SnapRequestException(int http, String caseCode, String message) {
        super(message);
        this.http = http;
        this.caseCode = caseCode;
    }

    public int getHttp() {
        return http;
    }

    public String getCaseCode() {
        return caseCode;
    }
}
