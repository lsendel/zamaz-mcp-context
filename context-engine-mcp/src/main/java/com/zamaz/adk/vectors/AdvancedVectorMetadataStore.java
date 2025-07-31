package com.zamaz.adk.vectors;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.*;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.vectors.TenantAwareVectorStore.VectorDocument;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.function.Predicate;

/**
 * Advanced Vector Metadata Store - Enhanced vector search with rich metadata
 * Supports complex filtering, hybrid search, and performance optimizations
 */
public class AdvancedVectorMetadataStore {
    private final PredictionServiceClient predictionClient;
    private final Firestore firestore;
    private final Storage storage;
    private final String projectId;
    private final String location;
    private final String bucketName;
    
    // Metadata indices for fast filtering
    private final Map<String, MetadataIndex> metadataIndices = new ConcurrentHashMap<>();
    private final Map<String, FilterIndex> filterIndices = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();
    
    // Performance optimization
    private final LoadingCache<String, float[]> embeddingCache;
    private final ExecutorService searchExecutor = Executors.newWorkStealingPool();
    
    // Configuration
    private static final String EMBEDDING_MODEL = "textembedding-gecko@003";
    private static final int MAX_RESULTS = 100;
    private static final int CACHE_SIZE = 10000;
    private static final double DEFAULT_ALPHA = 0.7; // Weight for vector similarity
    
    public AdvancedVectorMetadataStore(String projectId, String location,
                                     Firestore firestore, Storage storage,
                                     String bucketName) {
        this.projectId = projectId;
        this.location = location;
        this.firestore = firestore;
        this.storage = storage;
        this.bucketName = bucketName;
        
        try {
            this.predictionClient = PredictionServiceClient.create();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create prediction client", e);
        }
        
        // Initialize embedding cache
        this.embeddingCache = CacheBuilder.newBuilder()
            .maximumSize(CACHE_SIZE)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .recordStats()
            .build(new CacheLoader<String, float[]>() {
                @Override
                public float[] load(String key) throws Exception {
                    return loadEmbedding(key);
                }
            });
        
        // Initialize indices
        initializeIndices();
    }
    
    /**
     * Enhanced vector document with rich metadata
     */
    public static class EnhancedVectorDocument extends VectorDocument {
        private final Map<String, Object> structuredMetadata;
        private final Map<String, Double> numericMetadata;
        private final Map<String, List<String>> arrayMetadata;
        private final Map<String, Map<String, Object>> nestedMetadata;
        private final Set<String> tags;
        private final long version;
        private final Map<String, Float> scores;
        
        public EnhancedVectorDocument(String id, String content, float[] embedding,
                                    Map<String, String> metadata, TenantContext tenantContext,
                                    Map<String, Object> structuredMetadata,
                                    Map<String, Double> numericMetadata,
                                    Map<String, List<String>> arrayMetadata,
                                    Map<String, Map<String, Object>> nestedMetadata,
                                    Set<String> tags, long version) {
            super(id, content, embedding, metadata, tenantContext);
            this.structuredMetadata = structuredMetadata;
            this.numericMetadata = numericMetadata;
            this.arrayMetadata = arrayMetadata;
            this.nestedMetadata = nestedMetadata;
            this.tags = tags;
            this.version = version;
            this.scores = new HashMap<>();
        }
        
        // Builder pattern for complex construction
        public static class Builder {
            private String id;
            private String content;
            private float[] embedding;
            private Map<String, String> metadata = new HashMap<>();
            private TenantContext tenantContext;
            private Map<String, Object> structuredMetadata = new HashMap<>();
            private Map<String, Double> numericMetadata = new HashMap<>();
            private Map<String, List<String>> arrayMetadata = new HashMap<>();
            private Map<String, Map<String, Object>> nestedMetadata = new HashMap<>();
            private Set<String> tags = new HashSet<>();
            private long version = 1;
            
            public Builder id(String id) {
                this.id = id;
                return this;
            }
            
            public Builder content(String content) {
                this.content = content;
                return this;
            }
            
            public Builder embedding(float[] embedding) {
                this.embedding = embedding;
                return this;
            }
            
            public Builder metadata(String key, String value) {
                this.metadata.put(key, value);
                return this;
            }
            
            public Builder structuredMetadata(String key, Object value) {
                this.structuredMetadata.put(key, value);
                return this;
            }
            
            public Builder numericMetadata(String key, Double value) {
                this.numericMetadata.put(key, value);
                return this;
            }
            
            public Builder arrayMetadata(String key, List<String> values) {
                this.arrayMetadata.put(key, values);
                return this;
            }
            
            public Builder nestedMetadata(String key, Map<String, Object> value) {
                this.nestedMetadata.put(key, value);
                return this;
            }
            
            public Builder tag(String tag) {
                this.tags.add(tag);
                return this;
            }
            
            public Builder tags(Collection<String> tags) {
                this.tags.addAll(tags);
                return this;
            }
            
            public Builder tenantContext(TenantContext context) {
                this.tenantContext = context;
                return this;
            }
            
            public Builder version(long version) {
                this.version = version;
                return this;
            }
            
            public EnhancedVectorDocument build() {
                return new EnhancedVectorDocument(id, content, embedding, metadata,
                    tenantContext, structuredMetadata, numericMetadata,
                    arrayMetadata, nestedMetadata, tags, version);
            }
        }
        
        // Getters
        public Map<String, Object> getStructuredMetadata() { return structuredMetadata; }
        public Map<String, Double> getNumericMetadata() { return numericMetadata; }
        public Map<String, List<String>> getArrayMetadata() { return arrayMetadata; }
        public Map<String, Map<String, Object>> getNestedMetadata() { return nestedMetadata; }
        public Set<String> getTags() { return tags; }
        public long getVersion() { return version; }
        public Map<String, Float> getScores() { return scores; }
        
        public void addScore(String scoreName, float score) {
            scores.put(scoreName, score);
        }
    }
    
    /**
     * Metadata index for efficient filtering
     */
    private static class MetadataIndex {
        private final Map<String, Set<String>> stringIndex = new ConcurrentHashMap<>();
        private final Map<String, TreeMap<Double, Set<String>>> numericIndex = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> arrayIndex = new ConcurrentHashMap<>();
        private final Map<String, Set<String>> tagIndex = new ConcurrentHashMap<>();
        
        public void indexDocument(EnhancedVectorDocument doc) {
            String docId = doc.getId();
            
            // Index string metadata
            doc.getStructuredMetadata().forEach((key, value) -> {
                if (value instanceof String) {
                    stringIndex.computeIfAbsent(key + ":" + value, k -> new HashSet<>()).add(docId);
                }
            });
            
            // Index numeric metadata
            doc.getNumericMetadata().forEach((key, value) -> {
                numericIndex.computeIfAbsent(key, k -> new TreeMap<>())
                    .computeIfAbsent(value, k -> new HashSet<>()).add(docId);
            });
            
            // Index array metadata
            doc.getArrayMetadata().forEach((key, values) -> {
                values.forEach(value -> 
                    arrayIndex.computeIfAbsent(key + ":" + value, k -> new HashSet<>()).add(docId)
                );
            });
            
            // Index tags
            doc.getTags().forEach(tag -> 
                tagIndex.computeIfAbsent(tag, k -> new HashSet<>()).add(docId)
            );
        }
        
        public Set<String> findByStringMetadata(String key, String value) {
            return stringIndex.getOrDefault(key + ":" + value, Collections.emptySet());
        }
        
        public Set<String> findByNumericRange(String key, Double min, Double max) {
            TreeMap<Double, Set<String>> keyIndex = numericIndex.get(key);
            if (keyIndex == null) return Collections.emptySet();
            
            Set<String> results = new HashSet<>();
            keyIndex.subMap(min, max).values().forEach(results::addAll);
            return results;
        }
        
        public Set<String> findByArrayContains(String key, String value) {
            return arrayIndex.getOrDefault(key + ":" + value, Collections.emptySet());
        }
        
        public Set<String> findByTag(String tag) {
            return tagIndex.getOrDefault(tag, Collections.emptySet());
        }
    }
    
    /**
     * Advanced search request
     */
    public static class AdvancedSearchRequest {
        private final String query;
        private final Map<String, FilterCondition> metadataFilters;
        private final List<String> requiredTags;
        private final List<String> excludedTags;
        private final TenantContext tenantContext;
        private final SearchMode searchMode;
        private final double alpha; // Vector weight (1-alpha for keyword)
        private final int maxResults;
        private final boolean includeMetadata;
        private final List<String> returnFields;
        private final Map<String, SortOrder> sortBy;
        
        public enum SearchMode {
            VECTOR_ONLY,      // Pure vector search
            KEYWORD_ONLY,     // Pure keyword search
            HYBRID,           // Combined vector + keyword
            FILTERED_VECTOR,  // Vector search with filters
            SEMANTIC_KEYWORD  // Keyword expanded with synonyms
        }
        
        public enum SortOrder {
            ASC, DESC
        }
        
        // Builder pattern implementation
        public static class Builder {
            private String query;
            private Map<String, FilterCondition> metadataFilters = new HashMap<>();
            private List<String> requiredTags = new ArrayList<>();
            private List<String> excludedTags = new ArrayList<>();
            private TenantContext tenantContext;
            private SearchMode searchMode = SearchMode.HYBRID;
            private double alpha = DEFAULT_ALPHA;
            private int maxResults = 10;
            private boolean includeMetadata = true;
            private List<String> returnFields = new ArrayList<>();
            private Map<String, SortOrder> sortBy = new LinkedHashMap<>();
            
            public Builder query(String query) {
                this.query = query;
                return this;
            }
            
            public Builder filter(String field, FilterCondition condition) {
                this.metadataFilters.put(field, condition);
                return this;
            }
            
            public Builder requireTag(String tag) {
                this.requiredTags.add(tag);
                return this;
            }
            
            public Builder excludeTag(String tag) {
                this.excludedTags.add(tag);
                return this;
            }
            
            public Builder tenantContext(TenantContext context) {
                this.tenantContext = context;
                return this;
            }
            
            public Builder searchMode(SearchMode mode) {
                this.searchMode = mode;
                return this;
            }
            
            public Builder alpha(double alpha) {
                this.alpha = Math.max(0, Math.min(1, alpha));
                return this;
            }
            
            public Builder maxResults(int max) {
                this.maxResults = max;
                return this;
            }
            
            public Builder includeMetadata(boolean include) {
                this.includeMetadata = include;
                return this;
            }
            
            public Builder returnField(String field) {
                this.returnFields.add(field);
                return this;
            }
            
            public Builder sortBy(String field, SortOrder order) {
                this.sortBy.put(field, order);
                return this;
            }
            
            public AdvancedSearchRequest build() {
                return new AdvancedSearchRequest(query, metadataFilters, requiredTags,
                    excludedTags, tenantContext, searchMode, alpha, maxResults,
                    includeMetadata, returnFields, sortBy);
            }
        }
        
        private AdvancedSearchRequest(String query, Map<String, FilterCondition> metadataFilters,
                                    List<String> requiredTags, List<String> excludedTags,
                                    TenantContext tenantContext, SearchMode searchMode,
                                    double alpha, int maxResults, boolean includeMetadata,
                                    List<String> returnFields, Map<String, SortOrder> sortBy) {
            this.query = query;
            this.metadataFilters = metadataFilters;
            this.requiredTags = requiredTags;
            this.excludedTags = excludedTags;
            this.tenantContext = tenantContext;
            this.searchMode = searchMode;
            this.alpha = alpha;
            this.maxResults = maxResults;
            this.includeMetadata = includeMetadata;
            this.returnFields = returnFields;
            this.sortBy = sortBy;
        }
        
        // Getters
        public String getQuery() { return query; }
        public Map<String, FilterCondition> getMetadataFilters() { return metadataFilters; }
        public List<String> getRequiredTags() { return requiredTags; }
        public List<String> getExcludedTags() { return excludedTags; }
        public TenantContext getTenantContext() { return tenantContext; }
        public SearchMode getSearchMode() { return searchMode; }
        public double getAlpha() { return alpha; }
        public int getMaxResults() { return maxResults; }
        public boolean isIncludeMetadata() { return includeMetadata; }
        public List<String> getReturnFields() { return returnFields; }
        public Map<String, SortOrder> getSortBy() { return sortBy; }
    }
    
    /**
     * Filter condition for metadata
     */
    public static class FilterCondition {
        private final FilterType type;
        private final Object value;
        private final Object secondValue; // For range queries
        
        public enum FilterType {
            EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN,
            GREATER_EQUAL, LESS_EQUAL, BETWEEN, IN, NOT_IN,
            CONTAINS, STARTS_WITH, ENDS_WITH, REGEX
        }
        
        public FilterCondition(FilterType type, Object value) {
            this.type = type;
            this.value = value;
            this.secondValue = null;
        }
        
        public FilterCondition(FilterType type, Object value, Object secondValue) {
            this.type = type;
            this.value = value;
            this.secondValue = secondValue;
        }
        
        public boolean matches(Object fieldValue) {
            if (fieldValue == null) return false;
            
            switch (type) {
                case EQUALS:
                    return fieldValue.equals(value);
                case NOT_EQUALS:
                    return !fieldValue.equals(value);
                case GREATER_THAN:
                    return compare(fieldValue, value) > 0;
                case LESS_THAN:
                    return compare(fieldValue, value) < 0;
                case GREATER_EQUAL:
                    return compare(fieldValue, value) >= 0;
                case LESS_EQUAL:
                    return compare(fieldValue, value) <= 0;
                case BETWEEN:
                    return compare(fieldValue, value) >= 0 && 
                           compare(fieldValue, secondValue) <= 0;
                case IN:
                    return ((Collection<?>) value).contains(fieldValue);
                case NOT_IN:
                    return !((Collection<?>) value).contains(fieldValue);
                case CONTAINS:
                    return fieldValue.toString().contains(value.toString());
                case STARTS_WITH:
                    return fieldValue.toString().startsWith(value.toString());
                case ENDS_WITH:
                    return fieldValue.toString().endsWith(value.toString());
                case REGEX:
                    return fieldValue.toString().matches(value.toString());
                default:
                    return false;
            }
        }
        
        private int compare(Object a, Object b) {
            if (a instanceof Comparable && b instanceof Comparable) {
                return ((Comparable) a).compareTo(b);
            }
            return a.toString().compareTo(b.toString());
        }
        
        // Getters
        public FilterType getType() { return type; }
        public Object getValue() { return value; }
        public Object getSecondValue() { return secondValue; }
    }
    
    /**
     * Filter index for optimized filtering
     */
    private static class FilterIndex {
        private final String fieldName;
        private final Map<Object, Set<String>> exactMatchIndex = new ConcurrentHashMap<>();
        private final TreeMap<Comparable, Set<String>> rangeIndex = new TreeMap<>();
        
        public FilterIndex(String fieldName) {
            this.fieldName = fieldName;
        }
        
        public void addValue(Object value, String documentId) {
            exactMatchIndex.computeIfAbsent(value, k -> new HashSet<>()).add(documentId);
            
            if (value instanceof Comparable) {
                rangeIndex.computeIfAbsent((Comparable) value, k -> new HashSet<>()).add(documentId);
            }
        }
        
        public Set<String> findExact(Object value) {
            return exactMatchIndex.getOrDefault(value, Collections.emptySet());
        }
        
        public Set<String> findRange(Comparable min, Comparable max) {
            Set<String> results = new HashSet<>();
            rangeIndex.subMap(min, true, max, true).values().forEach(results::addAll);
            return results;
        }
    }
    
    /**
     * Enhanced search with advanced features
     */
    public CompletableFuture<List<EnhancedVectorDocument>> advancedSearch(
            AdvancedSearchRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get candidate documents based on filters
                Set<String> candidates = getCandidateDocuments(request);
                
                // Perform search based on mode
                List<EnhancedVectorDocument> results;
                switch (request.getSearchMode()) {
                    case VECTOR_ONLY:
                        results = vectorSearch(request, candidates);
                        break;
                    case KEYWORD_ONLY:
                        results = keywordSearch(request, candidates);
                        break;
                    case HYBRID:
                        results = hybridSearch(request, candidates);
                        break;
                    case FILTERED_VECTOR:
                        results = filteredVectorSearch(request, candidates);
                        break;
                    case SEMANTIC_KEYWORD:
                        results = semanticKeywordSearch(request, candidates);
                        break;
                    default:
                        results = hybridSearch(request, candidates);
                }
                
                // Apply sorting
                if (!request.getSortBy().isEmpty()) {
                    results = applySorting(results, request.getSortBy());
                }
                
                // Apply field filtering if specified
                if (!request.getReturnFields().isEmpty()) {
                    results = filterReturnFields(results, request.getReturnFields());
                }
                
                // Limit results
                if (results.size() > request.getMaxResults()) {
                    results = results.subList(0, request.getMaxResults());
                }
                
                return results;
                
            } catch (Exception e) {
                throw new RuntimeException("Advanced search failed", e);
            }
        }, searchExecutor);
    }
    
    /**
     * Hybrid search combining vector and keyword
     */
    private List<EnhancedVectorDocument> hybridSearch(AdvancedSearchRequest request,
                                                     Set<String> candidates) {
        // Generate query embedding
        float[] queryEmbedding = generateEmbedding(request.getQuery());
        
        // Get keyword matches
        Set<String> keywordMatches = getKeywordMatches(request.getQuery());
        
        // Score all candidates
        List<ScoredDocument> scoredDocs = new ArrayList<>();
        
        for (String docId : candidates) {
            EnhancedVectorDocument doc = loadDocument(docId);
            if (doc == null) continue;
            
            // Calculate vector similarity
            double vectorScore = calculateCosineSimilarity(queryEmbedding, doc.getEmbedding());
            
            // Calculate keyword score
            double keywordScore = calculateKeywordScore(request.getQuery(), doc);
            
            // Boost score if document contains exact keyword matches
            if (keywordMatches.contains(docId)) {
                keywordScore = Math.min(1.0, keywordScore + 0.3);
            }
            
            // Combined score
            double combinedScore = request.getAlpha() * vectorScore + 
                                 (1 - request.getAlpha()) * keywordScore;
            
            // Store individual scores
            doc.addScore("vector_score", (float) vectorScore);
            doc.addScore("keyword_score", (float) keywordScore);
            doc.addScore("combined_score", (float) combinedScore);
            
            scoredDocs.add(new ScoredDocument(doc, combinedScore));
        }
        
        // Sort by score
        scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));
        
        return scoredDocs.stream()
            .map(sd -> sd.document)
            .collect(Collectors.toList());
    }
    
    /**
     * Vector-only search
     */
    private List<EnhancedVectorDocument> vectorSearch(AdvancedSearchRequest request,
                                                     Set<String> candidates) {
        float[] queryEmbedding = generateEmbedding(request.getQuery());
        
        List<ScoredDocument> scoredDocs = candidates.parallelStream()
            .map(this::loadDocument)
            .filter(Objects::nonNull)
            .map(doc -> {
                double score = calculateCosineSimilarity(queryEmbedding, doc.getEmbedding());
                doc.addScore("vector_score", (float) score);
                return new ScoredDocument(doc, score);
            })
            .collect(Collectors.toList());
        
        scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));
        
        return scoredDocs.stream()
            .map(sd -> sd.document)
            .collect(Collectors.toList());
    }
    
    /**
     * Keyword-only search
     */
    private List<EnhancedVectorDocument> keywordSearch(AdvancedSearchRequest request,
                                                      Set<String> candidates) {
        String query = request.getQuery().toLowerCase();
        String[] queryTerms = query.split("\\s+");
        
        List<ScoredDocument> scoredDocs = candidates.parallelStream()
            .map(this::loadDocument)
            .filter(Objects::nonNull)
            .map(doc -> {
                double score = calculateKeywordScore(request.getQuery(), doc);
                doc.addScore("keyword_score", (float) score);
                return new ScoredDocument(doc, score);
            })
            .filter(sd -> sd.score > 0)
            .collect(Collectors.toList());
        
        scoredDocs.sort((a, b) -> Double.compare(b.score, a.score));
        
        return scoredDocs.stream()
            .map(sd -> sd.document)
            .collect(Collectors.toList());
    }
    
    /**
     * Filtered vector search
     */
    private List<EnhancedVectorDocument> filteredVectorSearch(
            AdvancedSearchRequest request, Set<String> candidates) {
        // Apply filters first
        Set<String> filtered = applyMetadataFilters(candidates, request);
        
        // Then perform vector search on filtered set
        request = new AdvancedSearchRequest.Builder()
            .query(request.getQuery())
            .searchMode(AdvancedSearchRequest.SearchMode.VECTOR_ONLY)
            .maxResults(request.getMaxResults())
            .build();
            
        return vectorSearch(request, filtered);
    }
    
    /**
     * Semantic keyword search with synonym expansion
     */
    private List<EnhancedVectorDocument> semanticKeywordSearch(
            AdvancedSearchRequest request, Set<String> candidates) {
        // Expand query with synonyms using AI
        String expandedQuery = expandQueryWithSynonyms(request.getQuery());
        
        // Create new request with expanded query
        AdvancedSearchRequest expandedRequest = new AdvancedSearchRequest.Builder()
            .query(expandedQuery)
            .searchMode(AdvancedSearchRequest.SearchMode.KEYWORD_ONLY)
            .maxResults(request.getMaxResults())
            .build();
        
        return keywordSearch(expandedRequest, candidates);
    }
    
    /**
     * Get candidate documents based on filters
     */
    private Set<String> getCandidateDocuments(AdvancedSearchRequest request) {
        Set<String> candidates = null;
        
        // Start with tenant filter
        if (request.getTenantContext() != null) {
            candidates = getTenantDocuments(request.getTenantContext());
        }
        
        // Apply tag filters
        if (!request.getRequiredTags().isEmpty()) {
            Set<String> tagMatches = new HashSet<>();
            for (String tag : request.getRequiredTags()) {
                Set<String> docs = metadataIndices.values().stream()
                    .flatMap(idx -> idx.findByTag(tag).stream())
                    .collect(Collectors.toSet());
                if (tagMatches.isEmpty()) {
                    tagMatches = docs;
                } else {
                    tagMatches.retainAll(docs);
                }
            }
            
            if (candidates == null) {
                candidates = tagMatches;
            } else {
                candidates.retainAll(tagMatches);
            }
        }
        
        // Apply metadata filters
        if (!request.getMetadataFilters().isEmpty()) {
            Set<String> filterMatches = applyMetadataFilters(
                candidates != null ? candidates : getAllDocumentIds(), request);
            
            if (candidates == null) {
                candidates = filterMatches;
            } else {
                candidates.retainAll(filterMatches);
            }
        }
        
        // If no filters, return all documents
        if (candidates == null) {
            candidates = getAllDocumentIds();
        }
        
        // Remove excluded tags
        if (!request.getExcludedTags().isEmpty()) {
            for (String tag : request.getExcludedTags()) {
                Set<String> excluded = metadataIndices.values().stream()
                    .flatMap(idx -> idx.findByTag(tag).stream())
                    .collect(Collectors.toSet());
                candidates.removeAll(excluded);
            }
        }
        
        return candidates;
    }
    
    /**
     * Apply metadata filters
     */
    private Set<String> applyMetadataFilters(Set<String> candidates,
                                           AdvancedSearchRequest request) {
        Set<String> filtered = new HashSet<>(candidates);
        
        for (Map.Entry<String, FilterCondition> entry : 
             request.getMetadataFilters().entrySet()) {
            String field = entry.getKey();
            FilterCondition condition = entry.getValue();
            
            Set<String> fieldMatches = new HashSet<>();
            
            // Use index if available
            FilterIndex index = filterIndices.get(field);
            if (index != null && condition.getType() == FilterCondition.FilterType.EQUALS) {
                fieldMatches = index.findExact(condition.getValue());
            } else if (index != null && condition.getType() == FilterCondition.FilterType.BETWEEN) {
                fieldMatches = index.findRange(
                    (Comparable) condition.getValue(),
                    (Comparable) condition.getSecondValue()
                );
            } else {
                // Manual filtering
                for (String docId : filtered) {
                    EnhancedVectorDocument doc = loadDocument(docId);
                    if (doc != null) {
                        Object fieldValue = getFieldValue(doc, field);
                        if (condition.matches(fieldValue)) {
                            fieldMatches.add(docId);
                        }
                    }
                }
            }
            
            filtered.retainAll(fieldMatches);
        }
        
        return filtered;
    }
    
    /**
     * Calculate keyword score
     */
    private double calculateKeywordScore(String query, EnhancedVectorDocument doc) {
        String content = doc.getContent().toLowerCase();
        String[] queryTerms = query.toLowerCase().split("\\s+");
        
        // TF-IDF style scoring
        double score = 0.0;
        int matchCount = 0;
        
        for (String term : queryTerms) {
            int termFreq = countOccurrences(content, term);
            if (termFreq > 0) {
                matchCount++;
                // Simple TF scoring
                double tf = 1 + Math.log(termFreq);
                score += tf;
            }
        }
        
        // Normalize by query length
        if (queryTerms.length > 0) {
            score = (score / queryTerms.length) * (matchCount / (double) queryTerms.length);
        }
        
        // Boost for exact phrase match
        if (content.contains(query.toLowerCase())) {
            score = Math.min(1.0, score + 0.3);
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Get keyword matches using inverted index
     */
    private Set<String> getKeywordMatches(String query) {
        String[] terms = query.toLowerCase().split("\\s+");
        Set<String> matches = null;
        
        for (String term : terms) {
            Set<String> termDocs = invertedIndex.get(term);
            if (termDocs != null) {
                if (matches == null) {
                    matches = new HashSet<>(termDocs);
                } else {
                    matches.retainAll(termDocs);
                }
            }
        }
        
        return matches != null ? matches : Collections.emptySet();
    }
    
    /**
     * Expand query with synonyms
     */
    private String expandQueryWithSynonyms(String query) {
        // Use AI to generate synonyms
        String prompt = String.format(
            "Generate synonyms and related terms for this search query: '%s'\\n" +
            "Return as a single expanded query string.", query
        );
        
        // In production, would call Vertex AI
        // For now, simple expansion
        return query + " " + query.replaceAll("\\b(\\w+)\\b", "$1s");
    }
    
    /**
     * Apply sorting to results
     */
    private List<EnhancedVectorDocument> applySorting(
            List<EnhancedVectorDocument> results,
            Map<String, AdvancedSearchRequest.SortOrder> sortBy) {
        
        List<EnhancedVectorDocument> sorted = new ArrayList<>(results);
        
        // Create comparator chain
        Comparator<EnhancedVectorDocument> comparator = null;
        
        for (Map.Entry<String, AdvancedSearchRequest.SortOrder> entry : sortBy.entrySet()) {
            String field = entry.getKey();
            AdvancedSearchRequest.SortOrder order = entry.getValue();
            
            Comparator<EnhancedVectorDocument> fieldComparator = (a, b) -> {
                Object aValue = getFieldValue(a, field);
                Object bValue = getFieldValue(b, field);
                
                if (aValue == null && bValue == null) return 0;
                if (aValue == null) return order == AdvancedSearchRequest.SortOrder.ASC ? -1 : 1;
                if (bValue == null) return order == AdvancedSearchRequest.SortOrder.ASC ? 1 : -1;
                
                int result;
                if (aValue instanceof Comparable && bValue instanceof Comparable) {
                    result = ((Comparable) aValue).compareTo(bValue);
                } else {
                    result = aValue.toString().compareTo(bValue.toString());
                }
                
                return order == AdvancedSearchRequest.SortOrder.DESC ? -result : result;
            };
            
            if (comparator == null) {
                comparator = fieldComparator;
            } else {
                comparator = comparator.thenComparing(fieldComparator);
            }
        }
        
        if (comparator != null) {
            sorted.sort(comparator);
        }
        
        return sorted;
    }
    
    /**
     * Filter return fields
     */
    private List<EnhancedVectorDocument> filterReturnFields(
            List<EnhancedVectorDocument> results, List<String> returnFields) {
        // In a real implementation, would create projected documents
        // For now, return full documents
        return results;
    }
    
    /**
     * Batch index documents
     */
    public CompletableFuture<Map<String, Boolean>> batchIndex(
            List<EnhancedVectorDocument> documents) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Boolean> results = new HashMap<>();
            
            // Process in batches
            int batchSize = 100;
            for (int i = 0; i < documents.size(); i += batchSize) {
                List<EnhancedVectorDocument> batch = documents.subList(i,
                    Math.min(i + batchSize, documents.size()));
                
                // Generate embeddings if needed
                for (EnhancedVectorDocument doc : batch) {
                    if (doc.getEmbedding() == null) {
                        float[] embedding = generateEmbedding(doc.getContent());
                        doc = new EnhancedVectorDocument.Builder()
                            .id(doc.getId())
                            .content(doc.getContent())
                            .embedding(embedding)
                            .build();
                    }
                }
                
                // Index documents
                for (EnhancedVectorDocument doc : batch) {
                    try {
                        indexDocument(doc);
                        results.put(doc.getId(), true);
                    } catch (Exception e) {
                        results.put(doc.getId(), false);
                        System.err.println("Failed to index " + doc.getId() + ": " + e.getMessage());
                    }
                }
            }
            
            return results;
        });
    }
    
    /**
     * Index a single document
     */
    private void indexDocument(EnhancedVectorDocument doc) {
        // Update metadata indices
        String tenantPath = doc.getTenantContext() != null ? 
            doc.getTenantContext().getTenantPath() : "global";
        
        MetadataIndex index = metadataIndices.computeIfAbsent(tenantPath,
            k -> new MetadataIndex());
        index.indexDocument(doc);
        
        // Update filter indices
        doc.getStructuredMetadata().forEach((key, value) -> {
            FilterIndex filterIndex = filterIndices.computeIfAbsent(key,
                k -> new FilterIndex(key));
            filterIndex.addValue(value, doc.getId());
        });
        
        // Update inverted index for keyword search
        String[] terms = doc.getContent().toLowerCase().split("\\s+");
        for (String term : terms) {
            invertedIndex.computeIfAbsent(term, k -> new HashSet<>()).add(doc.getId());
        }
        
        // Store document
        storeDocument(doc);
        
        // Store embedding
        storeEmbedding(doc.getId(), doc.getEmbedding());
    }
    
    /**
     * Update document metadata
     */
    public CompletableFuture<Boolean> updateMetadata(String documentId,
                                                   Map<String, Object> updates) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EnhancedVectorDocument doc = loadDocument(documentId);
                if (doc == null) return false;
                
                // Apply updates
                updates.forEach((key, value) -> {
                    if (value instanceof Double) {
                        doc.getNumericMetadata().put(key, (Double) value);
                    } else if (value instanceof List) {
                        doc.getArrayMetadata().put(key, (List<String>) value);
                    } else if (value instanceof Map) {
                        doc.getNestedMetadata().put(key, (Map<String, Object>) value);
                    } else {
                        doc.getStructuredMetadata().put(key, value);
                    }
                });
                
                // Re-index
                indexDocument(doc);
                
                return true;
            } catch (Exception e) {
                System.err.println("Failed to update metadata: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get search statistics
     */
    public SearchStatistics getSearchStatistics() {
        CacheStats cacheStats = embeddingCache.stats();
        
        return new SearchStatistics(
            metadataIndices.values().stream()
                .mapToInt(idx -> idx.tagIndex.size())
                .sum(),
            filterIndices.size(),
            invertedIndex.size(),
            cacheStats.hitCount(),
            cacheStats.missCount(),
            cacheStats.hitRate(),
            getAllDocumentIds().size()
        );
    }
    
    /**
     * Search statistics
     */
    public static class SearchStatistics {
        private final int totalTags;
        private final int totalFilterIndices;
        private final int invertedIndexSize;
        private final long cacheHits;
        private final long cacheMisses;
        private final double cacheHitRate;
        private final int totalDocuments;
        
        public SearchStatistics(int totalTags, int totalFilterIndices,
                              int invertedIndexSize, long cacheHits,
                              long cacheMisses, double cacheHitRate,
                              int totalDocuments) {
            this.totalTags = totalTags;
            this.totalFilterIndices = totalFilterIndices;
            this.invertedIndexSize = invertedIndexSize;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.cacheHitRate = cacheHitRate;
            this.totalDocuments = totalDocuments;
        }
        
        // Getters
        public int getTotalTags() { return totalTags; }
        public int getTotalFilterIndices() { return totalFilterIndices; }
        public int getInvertedIndexSize() { return invertedIndexSize; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public double getCacheHitRate() { return cacheHitRate; }
        public int getTotalDocuments() { return totalDocuments; }
    }
    
    /**
     * Helper classes and methods
     */
    
    private static class ScoredDocument {
        final EnhancedVectorDocument document;
        final double score;
        
        ScoredDocument(EnhancedVectorDocument document, double score) {
            this.document = document;
            this.score = score;
        }
    }
    
    private Object getFieldValue(EnhancedVectorDocument doc, String field) {
        // Check various metadata maps
        if (doc.getStructuredMetadata().containsKey(field)) {
            return doc.getStructuredMetadata().get(field);
        }
        if (doc.getNumericMetadata().containsKey(field)) {
            return doc.getNumericMetadata().get(field);
        }
        if (doc.getMetadata().containsKey(field)) {
            return doc.getMetadata().get(field);
        }
        if (doc.getScores().containsKey(field)) {
            return doc.getScores().get(field);
        }
        
        // Check nested fields
        if (field.contains(".")) {
            String[] parts = field.split("\\.", 2);
            Map<String, Object> nested = doc.getNestedMetadata().get(parts[0]);
            if (nested != null) {
                return nested.get(parts[1]);
            }
        }
        
        return null;
    }
    
    private Set<String> getTenantDocuments(TenantContext tenant) {
        // In production, would query Firestore
        return new HashSet<>();
    }
    
    private Set<String> getAllDocumentIds() {
        // In production, would query Firestore
        return new HashSet<>();
    }
    
    private EnhancedVectorDocument loadDocument(String documentId) {
        // In production, would load from Firestore
        return null;
    }
    
    private void storeDocument(EnhancedVectorDocument doc) {
        // In production, would store in Firestore
    }
    
    private void storeEmbedding(String documentId, float[] embedding) {
        // In production, would store in Cloud Storage
    }
    
    private float[] loadEmbedding(String documentId) throws Exception {
        // In production, would load from Cloud Storage
        return new float[768];
    }
    
    private float[] generateEmbedding(String text) {
        // In production, would call Vertex AI
        return new float[768];
    }
    
    private double calculateCosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    private int countOccurrences(String text, String term) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(term, index)) != -1) {
            count++;
            index += term.length();
        }
        return count;
    }
    
    private void initializeIndices() {
        // Load existing indices from Firestore
        // In production implementation
    }
    
    public void shutdown() {
        searchExecutor.shutdown();
        try {
            if (!searchExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                searchExecutor.shutdownNow();
            }
            predictionClient.close();
        } catch (Exception e) {
            searchExecutor.shutdownNow();
        }
    }
    
    // Required imports for caching
    private static class CacheBuilder {
        static <K, V> LoadingCacheBuilder<K, V> newBuilder() {
            return new LoadingCacheBuilder<>();
        }
    }
    
    private static class LoadingCacheBuilder<K, V> {
        LoadingCacheBuilder<K, V> maximumSize(long size) { return this; }
        LoadingCacheBuilder<K, V> expireAfterAccess(long duration, TimeUnit unit) { return this; }
        LoadingCacheBuilder<K, V> recordStats() { return this; }
        LoadingCache<K, V> build(CacheLoader<K, V> loader) { 
            return new LoadingCache<K, V>() {
                private final Map<K, V> cache = new ConcurrentHashMap<>();
                private final CacheStats stats = new CacheStats();
                
                @Override
                public V get(K key) throws ExecutionException {
                    V value = cache.get(key);
                    if (value == null) {
                        stats.recordMiss();
                        try {
                            value = loader.load(key);
                            cache.put(key, value);
                        } catch (Exception e) {
                            throw new ExecutionException(e);
                        }
                    } else {
                        stats.recordHit();
                    }
                    return value;
                }
                
                @Override
                public CacheStats stats() { return stats; }
            };
        }
    }
    
    private interface LoadingCache<K, V> {
        V get(K key) throws ExecutionException;
        CacheStats stats();
    }
    
    private static abstract class CacheLoader<K, V> {
        abstract V load(K key) throws Exception;
    }
    
    private static class CacheStats {
        private long hitCount = 0;
        private long missCount = 0;
        
        void recordHit() { hitCount++; }
        void recordMiss() { missCount++; }
        long hitCount() { return hitCount; }
        long missCount() { return missCount; }
        double hitRate() { 
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
    }
}