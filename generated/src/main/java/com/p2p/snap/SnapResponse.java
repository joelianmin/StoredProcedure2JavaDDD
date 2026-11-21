package com.p2p.snap;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Body respons SNAP. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SnapResponse(String responseCode, String responseMessage, String partnerReferenceNo) {}
