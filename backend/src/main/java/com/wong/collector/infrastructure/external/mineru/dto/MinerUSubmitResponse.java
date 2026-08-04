package com.wong.collector.infrastructure.external.mineru.dto;

import lombok.Data;

@Data
public class MinerUSubmitResponse {
    private int code;
    private String msg;
    private DataBody data;

    @lombok.Data
    public static class DataBody {
        private String taskId;
    }
}
