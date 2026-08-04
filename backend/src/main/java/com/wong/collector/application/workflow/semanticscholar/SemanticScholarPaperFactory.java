package com.wong.collector.application.workflow.semanticscholar;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.wong.collector.domain.paper.model.Authors;
import com.wong.collector.domain.paper.model.DOI;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperSource;
import com.wong.collector.domain.paper.model.Title;
import com.wong.collector.domain.paper.model.YearPublished;
import com.wong.collector.infrastructure.external.semanticscholar.dto.SemanticScholarPaperInfo;

@Component
public class SemanticScholarPaperFactory {

    public Paper toPaper(SemanticScholarPaperInfo info) {
        if (info == null) {
            throw new IllegalArgumentException("Paper info is null");
        }
        String doiText = Optional.ofNullable(info.getExternalIds())
            .map(ids -> ids.get("DOI"))
            .map(Object::toString)
            .filter(v -> !v.isBlank())
            .orElse(null);

        String semanticScholarId = normalizeSemanticScholarId(info.getPaperId());
        if (semanticScholarId == null) {
            throw new IllegalArgumentException("paperId is missing");
        }
        PaperId paperId = PaperId.of(semanticScholarId);

        Title title = new Title(info.getTitle() == null || info.getTitle().isBlank() ? "Untitled" : info.getTitle());
        List<String> authors = info.getAuthors() == null
            ? List.of()
            : info.getAuthors().stream()
                .map(SemanticScholarPaperInfo.AuthorInfo::getName)
                .filter(a -> a != null && !a.isBlank())
                .collect(Collectors.toList());
        if (authors.isEmpty()) {
            authors = List.of("Unknown");
        }

        DOI doi = doiText == null ? null : new DOI(doiText);
        YearPublished year = info.getYear() == null ? null : new YearPublished(info.getYear());

        String abstractText = info.getAbstractText();
        String downloadUrl = info.getOpenAccessPdf() == null ? null : info.getOpenAccessPdf().getUrl();
        if (downloadUrl == null || downloadUrl.isBlank()) {
            throw new IllegalArgumentException("openAccessPdf.url is missing");
        }

        Integer citations = info.getCitationCount();
        return Paper.create(
            paperId,
            title,
            Authors.of(authors),
            doi,
            year,
            PaperSource.SEMANTIC_SCHOLAR,
            abstractText,
            downloadUrl,
            citations
        );
    }

    private String normalizeSemanticScholarId(String rawId) {
        if (rawId == null) {
            return null;
        }
        String trimmed = rawId.trim();
        if (trimmed.isBlank() || trimmed.length() > 64 || trimmed.contains("/")) {
            return null;
        }
        return trimmed;
    }
}
