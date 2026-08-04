package com.wong.collector.application.assembler;

import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;

import com.wong.collector.application.dto.response.PaperDTO;
import com.wong.collector.application.dto.response.PaperDetailDTO;
import com.wong.collector.domain.paper.model.DocumentChunk;
import com.wong.collector.domain.paper.model.MaterialExtraction;
import com.wong.collector.domain.paper.model.Paper;

@Mapper(componentModel = "spring")
public interface PaperAssembler {

    default PaperDTO toDTO(Paper paper) {
        if (paper == null) {
            return null;
        }
        return PaperDTO.builder()
            .paperId(paper.getId().getValue())
            .title(paper.getTitle().value())
            .authors(paper.getAuthors().asString())
            .state(paper.getState().name())
            .yearPublished(paper.getYearPublished() == null ? null : paper.getYearPublished().value())
            .paperSource(paper.getPaperSource() == null ? null : paper.getPaperSource().name())
            .createdAt(paper.getCreatedAt())
            .build();
    }

    default PaperDetailDTO toDetailDTO(Paper paper) {
        return toDetailDTO(paper, false);
    }

    default PaperDetailDTO toDetailDTO(Paper paper, boolean includeChunks) {
        if (paper == null) {
            return null;
        }
        return PaperDetailDTO.builder()
            .paperId(paper.getId().getValue())
            .title(paper.getTitle().value())
            .authors(paper.getAuthors().asString())
            .yearPublished(paper.getYearPublished() == null ? null : paper.getYearPublished().value())
            .doi(paper.getDoi() == null ? null : paper.getDoi().value())
            .abstractContent(paper.getAbstractContent())
            .downloadUrl(paper.getDownloadUrl())
            .citationCount(paper.getCitationCount())
            .state(paper.getState().name())
            .downloadInfo(toDownloadInfo(paper))
            .transformInfo(toTransformInfo(paper))
            .chunks(includeChunks ? mapChunks(paper.getChunks()) : List.of())
            .materials(mapMaterials(paper.getMaterials()))
            .build();
    }

    private List<PaperDetailDTO.ChunkDTO> mapChunks(List<DocumentChunk> chunks) {
        return chunks == null ? List.of() : chunks.stream()
            .map(chunk -> PaperDetailDTO.ChunkDTO.builder()
                .chunkId(chunk.getId() == null ? null : chunk.getId().toString())
                .orderNo(chunk.getOrderNo())
                .content(chunk.getContent())
                .embeddingModel(chunk.getModel() == null ? null : chunk.getModel().name())
                .build())
            .collect(Collectors.toList());
    }

    private List<PaperDetailDTO.MaterialDTO> mapMaterials(List<MaterialExtraction> materials) {
        return materials == null ? List.of() : materials.stream()
            .map(material -> PaperDetailDTO.MaterialDTO.builder()
                .material(material.getMaterial())
                .property(material.getProperty())
                .method(material.getMethod())
                .confidence(material.getConfidence())
                .detailJson(material.getDetailJson())
                .build())
            .collect(Collectors.toList());
    }

    private PaperDetailDTO.DownloadInfoDTO toDownloadInfo(Paper paper) {
        if (paper.getDownloadInfo() == null) {
            return null;
        }
        return PaperDetailDTO.DownloadInfoDTO.builder()
            .sourceUrl(paper.getDownloadInfo().getSourceUrl())
            .ossUrl(paper.getDownloadInfo().getOssUrl())
            .fileSize(paper.getDownloadInfo().getFileSize())
            .status(paper.getDownloadInfo().getStatus().name())
            .tryTimes(paper.getDownloadInfo().getTryTimes())
            .startedAt(paper.getDownloadInfo().getStartedAt())
            .completedAt(paper.getDownloadInfo().getCompletedAt())
            .build();
    }

    private PaperDetailDTO.TransformInfoDTO toTransformInfo(Paper paper) {
        if (paper.getTransformInfo() == null) {
            return null;
        }
        return PaperDetailDTO.TransformInfoDTO.builder()
            .provider(paper.getTransformInfo().getProvider().name())
            .status(paper.getTransformInfo().getStatus().name())
            .markdownPath(paper.getTransformInfo().getMarkdownPath())
            .errorMessage(paper.getTransformInfo().getErrorMessage())
            .build();
    }
}
