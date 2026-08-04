package com.wong.collector.application.workflow.search;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.springframework.stereotype.Component;

import com.wong.collector.application.service.PaperIngestionService;
import com.wong.collector.domain.paper.model.Authors;
import com.wong.collector.domain.paper.model.DOI;
import com.wong.collector.domain.paper.model.Paper;
import com.wong.collector.domain.paper.model.PaperId;
import com.wong.collector.domain.paper.model.PaperSource;
import com.wong.collector.domain.paper.model.Title;
import com.wong.collector.domain.paper.model.YearPublished;
import com.wong.collector.domain.search.model.SearchConfig;
import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.infrastructure.config.properties.ArxivApiProperties;
import com.wong.collector.infrastructure.external.client.ArxivApiClient;
import com.wong.collector.infrastructure.external.client.support.RateLimiter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * arXiv 平台论文导入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArxivSearchExecutor implements SearchExecutor {

    private final ArxivApiClient arxivApiClient;
    private final ArxivApiProperties properties;
    private final PaperIngestionService paperIngestionService;
    private RateLimiter rateLimiter;

    private RateLimiter limiter() {
        if (rateLimiter == null) {
            rateLimiter = new RateLimiter(
                properties.getRateLimit().getIntervalMs(),
                properties.getRateLimit().getMaxConcurrency()
            );
        }
        return rateLimiter;
    }

    @Override
    public SearchProvider supports() {
        return SearchProvider.ARXIV;
    }

    @Override
    public int importPapers(SearchConfig config) {
        SearchPaging paging = SearchPaging.of(config, properties.getMaxResults());
        int pageSize = paging.pageSize();
        int start = paging.offset();
        limiter().acquire();
        try {
            String query = config.getQuery();
            String response = arxivApiClient.query(query, null, start, pageSize, "relevance", "descending");
            List<Paper> papers = parse(response);
            return persist(papers);
        } catch (DocumentException e) {
            throw new IllegalStateException("解析 arXiv 响应失败", e);
        } finally {
            limiter().release();
        }
    }

    private List<Paper> parse(String xml) throws DocumentException {
        Document document = DocumentHelper.parseText(xml);
        Map<String, String> ns = Map.of(
            "atom", "http://www.w3.org/2005/Atom",
            "arxiv", "http://arxiv.org/schemas/atom"
        );
        XPath entryXpath = DocumentHelper.createXPath("//atom:entry");
        entryXpath.setNamespaceURIs(ns);
        List<Node> entries = entryXpath.selectNodes(document);
        List<Paper> result = new ArrayList<>(entries.size());
        for (Node entry : entries) {
            String id = read(entry, "atom:id", ns);
            if (id == null) {
                continue;
            }
            String paperId = extractId(id);
            Title title = new Title(Optional.ofNullable(read(entry, "atom:title", ns)).orElse("Untitled"));
            List<String> authors = readList(entry, ns);
            String abstractText = Optional.ofNullable(read(entry, "atom:summary", ns)).orElse("");
            String doi = read(entry, "arxiv:doi", ns);
            String published = read(entry, "atom:published", ns);
            YearPublished year = parseYear(published);
            String pdfUrl = readLink(entry, "atom:link[@title='pdf']", ns);
            if (pdfUrl == null) {
                pdfUrl = readLink(entry, "atom:link[@type='text/html']", ns);
            }
            Paper paper = Paper.create(
                PaperId.of(paperId),
                title,
                Authors.of(authors.isEmpty() ? List.of("Unknown") : authors),
                doi == null ? null : new DOI(doi),
                year,
                PaperSource.ARXIV,
                abstractText,
                pdfUrl,
                null
            );
            result.add(paper);
        }
        return result;
    }

    private int persist(List<Paper> papers) {
        int imported = 0;
        for (Paper paper : papers) {
            PaperIngestionService.IngestionResult result = paperIngestionService.ingest(paper);
            if (result.createdNew()) {
                imported++;
            }
        }
        return imported;
    }

    private String read(Node node, String expr, Map<String, String> ns) {
        XPath xp = node.createXPath(expr);
        xp.setNamespaceURIs(ns);
        Node result = xp.selectSingleNode(node);
        return result == null ? null : result.getText();
    }

    private String readLink(Node node, String expr, Map<String, String> ns) {
        XPath xp = node.createXPath(expr + "/@href");
        xp.setNamespaceURIs(ns);
        Node result = xp.selectSingleNode(node);
        return result == null ? null : result.getText();
    }

    private List<String> readList(Node node, Map<String, String> ns) {
        XPath xp = node.createXPath("atom:author/atom:name");
        xp.setNamespaceURIs(ns);
        List<Node> authors = xp.selectNodes(node);
        List<String> result = new ArrayList<>();
        for (Node author : authors) {
            result.add(author.getText());
        }
        return result;
    }

    private String extractId(String id) {
        if (id.startsWith("http")) {
            int idx = id.lastIndexOf('/');
            return id.substring(idx + 1);
        }
        return id;
    }

    private YearPublished parseYear(String published) {
        if (published == null) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(published, DateTimeFormatter.ISO_DATE_TIME);
            return new YearPublished(date.getYear());
        } catch (Exception ex) {
            return null;
        }
    }
}
