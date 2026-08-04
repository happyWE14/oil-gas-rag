package com.wong.collector.infrastructure.external.download.dto;

import lombok.Data;

@Data
public class TokenResponse {
    private int code;
    private String msg;
    private String data;
}
