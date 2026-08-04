package com.wong.collector.application.workflow.search;

import com.wong.collector.domain.search.model.SearchProvider;
import com.wong.collector.infrastructure.config.properties.SearchQueryPlanProperties;
import com.wong.collector.infrastructure.config.properties.SearchQueryPlanProperties.Limit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SearchQueryBuilder {

    private static final String DEFAULT_TEMPLATE = "{anchor} {subject} {boost}";
    private static final String ROLE_ANCHOR = "anchor";
    private static final String ROLE_SUBJECT = "subject";
    private static final String ROLE_BOOST = "boost";

    private static final String PLACEHOLDER_ANCHOR = "{anchor}";
    private static final String PLACEHOLDER_SUBJECT = "{subject}";
    private static final String PLACEHOLDER_BOOST = "{boost}";

    private static final String AND_BOUNDARY = " AND ";
    private static final String OR_OPERATOR = " OR ";

    private final SearchQueryPlanProperties queryPlanProperties;

    public List<SearchQueryPlan> build(SearchProvider provider) {
        if (provider == null) {
            return List.of();
        }
        if (queryPlanProperties.getClusters() == null || queryPlanProperties.getClusters().isEmpty()) {
            return List.of();
        }

        String providerKey = providerKey(provider);
        String template = resolveTemplate(providerKey);
        Limit limit = resolveLimit(providerKey);
        QueryDialect dialect = QueryDialect.forProvider(provider);

        Integer maxRoleTerms = limit == null ? null : limit.getMaxOrTerms();
        List<String> anchors = pickTermsByRole(ROLE_ANCHOR, maxRoleTerms);
        List<String> subjects = pickTermsByRole(ROLE_SUBJECT, maxRoleTerms);
        List<String> boosts = pickTermsByRole(ROLE_BOOST, maxRoleTerms);

        int maxQueries = resolveMaxQueries();
        int boostChunkSize = resolveBoostChunkSize();
        boolean useBoostSlot = template.contains(PLACEHOLDER_BOOST);
        List<String> boostChunks = dialect.buildBoostChunks(useBoostSlot, boosts, boostChunkSize);

        String batchId = UUID.randomUUID().toString();
        List<SearchQueryPlan> plans = new ArrayList<>();
        Set<String> dedup = new LinkedHashSet<>();
        for (String anchor : anchors) {
            for (String subject : subjects) {
                for (String boost : boostChunks) {
                    String query = template
                        .replace(PLACEHOLDER_ANCHOR, dialect.formatTerm(anchor))
                        .replace(PLACEHOLDER_SUBJECT, dialect.formatTerm(subject))
                        .replace(PLACEHOLDER_BOOST, boost);
                    query = normalizeWhitespace(dialect.applyLimit(query, limit));
                    if (query.isBlank() || !dedup.add(query)) {
                        continue;
                    }
                    plans.add(new SearchQueryPlan(
                        query,
                        query,
                        queryPlanProperties.getClusters(),
                        provider.name(),
                        batchId
                    ));
                    if (dedup.size() >= maxQueries) {
                        return plans;
                    }
                }
            }
        }
        return plans;
    }

    private int resolveMaxQueries() {
        Integer configured = queryPlanProperties.getMaxQueriesPerProvider();
        if (configured == null || configured <= 0) {
            return 10;
        }
        return configured;
    }

    private int resolveBoostChunkSize() {
        Integer configured = queryPlanProperties.getBoostTermsPerQuery();
        if (configured == null || configured <= 0) {
            return 0;
        }
        return configured;
    }

    private List<String> pickTermsByRole(String role, Integer maxTerms) {
        return queryPlanProperties.getClusters().stream()
            .filter(c -> role.equalsIgnoreCase(c.getRole()))
            .flatMap(c -> c.getTerms().stream()
                .limit(c.getMaxTerms() == null ? c.getTerms().size() : c.getMaxTerms()))
            .limit(maxTerms == null ? Long.MAX_VALUE : maxTerms)
            .collect(Collectors.toList());
    }

    private String resolveTemplate(String providerKey) {
        if (queryPlanProperties.getTemplates() == null) {
            return DEFAULT_TEMPLATE;
        }
        return queryPlanProperties.getTemplates().getOrDefault(providerKey, DEFAULT_TEMPLATE);
    }

    private Limit resolveLimit(String providerKey) {
        if (queryPlanProperties.getLimits() == null) {
            return null;
        }
        return queryPlanProperties.getLimits().get(providerKey);
    }

    private static String providerKey(SearchProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    private static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private interface QueryDialect {

        static QueryDialect forProvider(SearchProvider provider) {
            return switch (provider) {
                case SEMANTIC_SCHOLAR -> SemanticScholarDialect.INSTANCE;
                case ARXIV -> ArxivDialect.INSTANCE;
                case CORE -> DefaultDialect.INSTANCE;
            };
        }

        String formatTerm(String raw);

        List<String> buildBoostChunks(boolean useBoostSlot, List<String> boosts, int chunkSize);

        String applyLimit(String query, Limit limit);
    }

    private static class DefaultDialect implements QueryDialect {

        private static final DefaultDialect INSTANCE = new DefaultDialect();

        @Override
        public String formatTerm(String raw) {
            return normalizeTerm(raw);
        }

        @Override
        public List<String> buildBoostChunks(boolean useBoostSlot, List<String> boosts, int chunkSize) {
            if (!useBoostSlot || boosts == null || boosts.isEmpty() || chunkSize <= 0) {
                return List.of("");
            }
            List<String> chunks = new ArrayList<>();
            for (int i = 0; i < boosts.size(); i += chunkSize) {
                List<String> sub = boosts.subList(i, Math.min(i + chunkSize, boosts.size()));
                String joined = sub.stream()
                    .map(this::formatTerm)
                    .filter(term -> !term.isBlank())
                    .collect(Collectors.joining(OR_OPERATOR));
                chunks.add("(" + joined + ")");
            }
            return chunks;
        }

        @Override
        public String applyLimit(String query, Limit limit) {
            if (limit == null) {
                return query;
            }
            int maxLength = limit.getMaxLength() == null ? Integer.MAX_VALUE : limit.getMaxLength();
            if (query.length() <= maxLength) {
                return query;
            }
            String truncated = truncateByAndBoundary(query, maxLength);
            return truncated == null ? "" : truncated;
        }
    }

    private static final class ArxivDialect extends DefaultDialect {

        private static final ArxivDialect INSTANCE = new ArxivDialect();

        @Override
        public String formatTerm(String raw) {
            return quoteIfNeeded(normalizeTerm(raw));
        }
    }

    private static final class SemanticScholarDialect extends DefaultDialect {

        private static final SemanticScholarDialect INSTANCE = new SemanticScholarDialect();

        @Override
        public String formatTerm(String raw) {
            // swagger.json: hyphenated query terms yield no matches; use whitespace instead.
            return normalizeHyphensAsSpace(normalizeTerm(raw));
        }

        @Override
        public List<String> buildBoostChunks(boolean useBoostSlot, List<String> boosts, int chunkSize) {
            if (!useBoostSlot || boosts == null || boosts.isEmpty() || chunkSize <= 0) {
                return List.of("");
            }
            List<String> chunks = new ArrayList<>();
            chunks.add("");
            for (int i = 0; i < boosts.size(); i += chunkSize) {
                List<String> sub = boosts.subList(i, Math.min(i + chunkSize, boosts.size()));
                String joined = sub.stream()
                    .map(this::formatTerm)
                    .filter(term -> !term.isBlank())
                    .collect(Collectors.joining(" "));
                if (!joined.isBlank()) {
                    chunks.add(joined);
                }
            }
            return chunks;
        }

        @Override
        public String applyLimit(String query, Limit limit) {
            if (limit == null) {
                return query;
            }
            int maxLength = limit.getMaxLength() == null ? Integer.MAX_VALUE : limit.getMaxLength();
            if (query.length() <= maxLength) {
                return query;
            }
            String truncated = truncateByAndBoundary(query, maxLength);
            if (truncated != null) {
                return truncated;
            }
            // swagger.json: /paper/search 的 query 为 plain-text；超长时按空格边界截断即可。
            int space = query.lastIndexOf(' ', maxLength);
            if (space > 0) {
                return query.substring(0, space).trim();
            }
            return query.substring(0, maxLength).trim();
        }
    }

    private static String truncateByAndBoundary(String query, int maxLength) {
        int cut = query.lastIndexOf(AND_BOUNDARY, maxLength);
        if (cut > 0) {
            return query.substring(0, cut).trim();
        }
        return null;
    }

    private static String normalizeTerm(String raw) {
        if (raw == null) {
            return "";
        }
        String term = raw.trim();
        return term.isBlank() ? "" : term;
    }

    private static String normalizeHyphensAsSpace(String term) {
        if (term == null || term.isBlank()) {
            return "";
        }
        return term.replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String quoteIfNeeded(String term) {
        if (term == null || term.isBlank()) {
            return "";
        }
        if (term.matches("[A-Za-z0-9_.-]+")) {
            return term;
        }
        String escaped = term.replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    @Data
    @AllArgsConstructor
    public static class SearchQueryPlan {
        private String query;
        private Object querySnapshot;
        private Object keywordsSnapshot;
        private String templateName;
        private String batchId;
    }
}
