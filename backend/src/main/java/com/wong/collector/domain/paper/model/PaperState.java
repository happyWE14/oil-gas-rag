package com.wong.collector.domain.paper.model;

/**
 * 论文的生命周期状态。
 */
public enum PaperState {
    // 搜索阶段
    DISCOVERED("DISCOVERED", "已发现", "等待相关性预分析"),

    // 预分析阶段
    PRE_ANALYZING("PRE_ANALYZING", "预分析中", "根据标题和摘要进行AI预判"),
    PRE_RELEVANT("PRE_RELEVANT", "预判相关", "预分析认为相关，等待下载"),
    IRRELEVANT("IRRELEVANT", "不相关", "预分析判定不相关，直接结束"),

    // 下载阶段
    DOWNLOADING("DOWNLOADING", "下载中", "正在下载原始PDF"),
    DOWNLOADED("DOWNLOADED", "已下载", "完成下载，等待转换"),

    // 转换阶段
    TRANSFORMING("TRANSFORMING", "转换中", "PDF 转 Markdown"),
    TRANSFORMED("TRANSFORMED", "已转换", "转换完成"),

    // 向量化阶段
    EMBEDDING("EMBEDDING", "向量化中", "分块生成向量"),
    EMBEDDING_COMPLETED("EMBEDDING_COMPLETED", "向量化完成", "向量生成完成"),

    // 材料提取阶段
    EXTRACTING("EXTRACTING", "提取中", "提取材料信息"),
    MATERIAL_EXTRACTED("MATERIAL_EXTRACTED", "提取完成", "材料信息已提取"),

    // 终态
    COMPLETED("COMPLETED", "已完成", "流程结束"),
    FAILED("FAILED", "失败", "流程失败，需人工处理或放弃"),
    ABANDONED("ABANDONED", "已放弃", "人工放弃处理");

    private final String code;
    private final String title;
    private final String description;

    PaperState(String code, String title, String description) {
        this.code = code;
        this.title = title;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFinalState() {
        return this == COMPLETED || this == ABANDONED || this == FAILED;
    }

    public boolean isProcessingState() {
        return switch (this) {
            case PRE_ANALYZING, DOWNLOADING, TRANSFORMING, EMBEDDING, EXTRACTING -> true;
            default -> false;
        };
    }

    /**
     * 判断当前状态是否已达到或超过目标状态（基于流水线顺序）。
     * 用于任务处理器判断是否可跳过已完成的阶段。
     */
    public boolean isAtLeast(PaperState target) {
        return this.pipelineOrder() >= target.pipelineOrder();
    }

    /**
     * 流水线阶段顺序值。
     * 终态和分支状态返回特殊值，不参与常规比较。
     */
    private int pipelineOrder() {
        return switch (this) {
            case DISCOVERED -> 0;
            case PRE_ANALYZING -> 1;
            case PRE_RELEVANT -> 2;
            case DOWNLOADING -> 3;
            case DOWNLOADED -> 4;
            case TRANSFORMING -> 5;
            case TRANSFORMED -> 6;
            case EMBEDDING -> 7;
            case EMBEDDING_COMPLETED -> 8;
            case EXTRACTING -> 9;
            case MATERIAL_EXTRACTED -> 10;
            case COMPLETED -> 11;
            // 终态/分支态：不参与流水线比较
            case IRRELEVANT, FAILED, ABANDONED -> -1;
        };
    }
}
