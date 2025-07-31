package com.zamaz.adk.memory;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.*;
import com.google.cloud.storage.*;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.vectors.AdvancedVectorMetadataStore;
import com.zamaz.adk.memory.AgentMemoryPool.MemoryEntry;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Persistent Memory with Embeddings - Long-term memory storage with semantic search
 * Combines structured memory with vector embeddings for intelligent retrieval
 */
public class PersistentMemoryEmbeddings {
    private final PredictionServiceClient predictionClient;
    private final Firestore firestore;
    private final Storage storage;
    private final String projectId;
    private final String location;
    private final String bucketName;
    private final AdvancedVectorMetadataStore vectorStore;
    
    // Memory management
    private final Map<String, MemoryChunk> memoryChunks = new ConcurrentHashMap<>();
    private final Map<String, MemoryContext> contextCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // Configuration
    private static final String EMBEDDING_MODEL = "textembedding-gecko@003";
    private static final int CHUNK_SIZE = 512; // tokens
    private static final int MAX_MEMORY_SIZE = 100_000; // chunks
    private static final long MEMORY_TTL = TimeUnit.DAYS.toMillis(90);
    
    public PersistentMemoryEmbeddings(String projectId, String location,
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
        
        // Initialize vector store for memory search
        this.vectorStore = new AdvancedVectorMetadataStore(
            projectId, location, firestore, storage, bucketName);
        
        // Schedule maintenance tasks
        scheduler.scheduleAtFixedRate(this::consolidateMemories, 
            0, 6, TimeUnit.HOURS);
        scheduler.scheduleAtFixedRate(this::cleanupExpiredMemories,
            0, 24, TimeUnit.HOURS);
    }
    
    /**
     * Memory chunk with embedding
     */
    public static class MemoryChunk {
        private final String chunkId;
        private final String memoryId;
        private final String content;
        private final float[] embedding;
        private final MemoryType type;
        private final Map<String, Object> metadata;
        private final long timestamp;
        private final String sourceAgent;
        private final Set<String> relatedChunks;
        private double importance;
        private int accessCount;
        private long lastAccessed;
        
        public enum MemoryType {
            EPISODIC,      // Specific events/experiences
            SEMANTIC,      // General knowledge/facts
            PROCEDURAL,    // How-to knowledge
            WORKING,       // Temporary active memory
            CONTEXTUAL,    // Context-specific information
            EMOTIONAL      // Emotionally significant memories
        }
        
        public MemoryChunk(String chunkId, String memoryId, String content,
                         float[] embedding, MemoryType type,
                         Map<String, Object> metadata, String sourceAgent) {
            this.chunkId = chunkId;
            this.memoryId = memoryId;
            this.content = content;
            this.embedding = embedding;
            this.type = type;
            this.metadata = metadata;
            this.sourceAgent = sourceAgent;
            this.timestamp = System.currentTimeMillis();
            this.relatedChunks = new HashSet<>();
            this.importance = calculateInitialImportance();
            this.accessCount = 0;
            this.lastAccessed = timestamp;
        }
        
        private double calculateInitialImportance() {
            // Base importance on type
            double baseImportance = switch (type) {
                case EMOTIONAL -> 0.9;
                case EPISODIC -> 0.7;
                case SEMANTIC -> 0.6;
                case PROCEDURAL -> 0.8;
                case CONTEXTUAL -> 0.5;
                case WORKING -> 0.3;
            };
            
            // Adjust based on metadata
            if (metadata.containsKey("priority")) {
                baseImportance *= (Double) metadata.get("priority");
            }
            
            return Math.min(1.0, baseImportance);
        }
        
        public void recordAccess() {
            accessCount++;
            lastAccessed = System.currentTimeMillis();
            
            // Increase importance with access
            importance = Math.min(1.0, importance + 0.01);
        }
        
        public void decay() {
            // Memory decay over time
            long age = System.currentTimeMillis() - timestamp;
            double decayFactor = Math.exp(-age / (30.0 * 24 * 60 * 60 * 1000)); // 30 day half-life
            
            // Decay less for frequently accessed memories
            double accessBoost = Math.min(1.0, accessCount * 0.1);
            
            importance = importance * decayFactor + accessBoost * (1 - decayFactor);
        }
        
        public void addRelatedChunk(String chunkId) {
            relatedChunks.add(chunkId);
        }
        
        // Getters
        public String getChunkId() { return chunkId; }
        public String getMemoryId() { return memoryId; }
        public String getContent() { return content; }
        public float[] getEmbedding() { return embedding; }
        public MemoryType getType() { return type; }
        public Map<String, Object> getMetadata() { return metadata; }
        public long getTimestamp() { return timestamp; }
        public String getSourceAgent() { return sourceAgent; }
        public Set<String> getRelatedChunks() { return relatedChunks; }
        public double getImportance() { return importance; }
        public int getAccessCount() { return accessCount; }
        public long getLastAccessed() { return lastAccessed; }
    }
    
    /**
     * Memory context for coherent memory formation
     */
    public static class MemoryContext {
        private final String contextId;
        private final String description;
        private final List<String> chunkIds;
        private final Map<String, Double> themes;
        private final Map<String, Object> summary;
        private final long createdAt;
        private long lastUpdated;
        
        public MemoryContext(String contextId, String description) {
            this.contextId = contextId;
            this.description = description;
            this.chunkIds = new ArrayList<>();
            this.themes = new HashMap<>();
            this.summary = new HashMap<>();
            this.createdAt = System.currentTimeMillis();
            this.lastUpdated = createdAt;
        }
        
        public void addChunk(String chunkId) {
            chunkIds.add(chunkId);
            lastUpdated = System.currentTimeMillis();
        }
        
        public void updateTheme(String theme, double relevance) {
            themes.put(theme, relevance);
            lastUpdated = System.currentTimeMillis();
        }
        
        public void updateSummary(Map<String, Object> updates) {
            summary.putAll(updates);
            lastUpdated = System.currentTimeMillis();
        }
        
        // Getters
        public String getContextId() { return contextId; }
        public String getDescription() { return description; }
        public List<String> getChunkIds() { return chunkIds; }
        public Map<String, Double> getThemes() { return themes; }
        public Map<String, Object> getSummary() { return summary; }
        public long getCreatedAt() { return createdAt; }
        public long getLastUpdated() { return lastUpdated; }
    }
    
    /**
     * Memory storage request
     */
    public static class MemoryStorageRequest {
        private final String content;
        private final MemoryChunk.MemoryType type;
        private final String sourceAgent;
        private final TenantContext tenantContext;
        private final Map<String, Object> metadata;
        private final String contextId;
        private final boolean autoChunk;
        private final double importanceThreshold;
        
        public MemoryStorageRequest(String content, MemoryChunk.MemoryType type,
                                  String sourceAgent, TenantContext tenantContext,
                                  Map<String, Object> metadata, String contextId,
                                  boolean autoChunk, double importanceThreshold) {
            this.content = content;
            this.type = type;
            this.sourceAgent = sourceAgent;
            this.tenantContext = tenantContext;
            this.metadata = metadata;
            this.contextId = contextId;
            this.autoChunk = autoChunk;
            this.importanceThreshold = importanceThreshold;
        }
        
        // Getters
        public String getContent() { return content; }
        public MemoryChunk.MemoryType getType() { return type; }
        public String getSourceAgent() { return sourceAgent; }
        public TenantContext getTenantContext() { return tenantContext; }
        public Map<String, Object> getMetadata() { return metadata; }
        public String getContextId() { return contextId; }
        public boolean isAutoChunk() { return autoChunk; }
        public double getImportanceThreshold() { return importanceThreshold; }
    }
    
    /**
     * Memory retrieval request
     */
    public static class MemoryRetrievalRequest {
        private final String query;
        private final List<MemoryChunk.MemoryType> types;
        private final TenantContext tenantContext;
        private final String contextId;
        private final long timeRangeStart;
        private final long timeRangeEnd;
        private final double minImportance;
        private final int maxResults;
        private final boolean includeRelated;
        private final Map<String, Object> metadataFilters;
        
        public static class Builder {
            private String query;
            private List<MemoryChunk.MemoryType> types = new ArrayList<>();
            private TenantContext tenantContext;
            private String contextId;
            private long timeRangeStart = 0;
            private long timeRangeEnd = System.currentTimeMillis();
            private double minImportance = 0.0;
            private int maxResults = 10;
            private boolean includeRelated = true;
            private Map<String, Object> metadataFilters = new HashMap<>();
            
            public Builder query(String query) {
                this.query = query;
                return this;
            }
            
            public Builder type(MemoryChunk.MemoryType type) {
                this.types.add(type);
                return this;
            }
            
            public Builder types(List<MemoryChunk.MemoryType> types) {
                this.types.addAll(types);
                return this;
            }
            
            public Builder tenantContext(TenantContext context) {
                this.tenantContext = context;
                return this;
            }
            
            public Builder contextId(String contextId) {
                this.contextId = contextId;
                return this;
            }
            
            public Builder timeRange(long start, long end) {
                this.timeRangeStart = start;
                this.timeRangeEnd = end;
                return this;
            }
            
            public Builder minImportance(double importance) {
                this.minImportance = importance;
                return this;
            }
            
            public Builder maxResults(int max) {
                this.maxResults = max;
                return this;
            }
            
            public Builder includeRelated(boolean include) {
                this.includeRelated = include;
                return this;
            }
            
            public Builder metadataFilter(String key, Object value) {
                this.metadataFilters.put(key, value);
                return this;
            }
            
            public MemoryRetrievalRequest build() {
                return new MemoryRetrievalRequest(query, types, tenantContext,
                    contextId, timeRangeStart, timeRangeEnd, minImportance,
                    maxResults, includeRelated, metadataFilters);
            }
        }
        
        private MemoryRetrievalRequest(String query, List<MemoryChunk.MemoryType> types,
                                     TenantContext tenantContext, String contextId,
                                     long timeRangeStart, long timeRangeEnd,
                                     double minImportance, int maxResults,
                                     boolean includeRelated, Map<String, Object> metadataFilters) {
            this.query = query;
            this.types = types;
            this.tenantContext = tenantContext;
            this.contextId = contextId;
            this.timeRangeStart = timeRangeStart;
            this.timeRangeEnd = timeRangeEnd;
            this.minImportance = minImportance;
            this.maxResults = maxResults;
            this.includeRelated = includeRelated;
            this.metadataFilters = metadataFilters;
        }
        
        // Getters
        public String getQuery() { return query; }
        public List<MemoryChunk.MemoryType> getTypes() { return types; }
        public TenantContext getTenantContext() { return tenantContext; }
        public String getContextId() { return contextId; }
        public long getTimeRangeStart() { return timeRangeStart; }
        public long getTimeRangeEnd() { return timeRangeEnd; }
        public double getMinImportance() { return minImportance; }
        public int getMaxResults() { return maxResults; }
        public boolean isIncludeRelated() { return includeRelated; }
        public Map<String, Object> getMetadataFilters() { return metadataFilters; }
    }
    
    /**
     * Store memory with automatic chunking and embedding
     */
    public CompletableFuture<List<String>> storeMemory(MemoryStorageRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> storedChunkIds = new ArrayList<>();
            
            try {
                // Auto-chunk if requested
                List<String> chunks;
                if (request.isAutoChunk()) {
                    chunks = chunkContent(request.getContent());
                } else {
                    chunks = Collections.singletonList(request.getContent());
                }
                
                // Process each chunk
                String memoryId = UUID.randomUUID().toString();
                List<MemoryChunk> memoryChunks = new ArrayList<>();
                
                for (String chunkContent : chunks) {
                    // Generate embedding
                    float[] embedding = generateEmbedding(chunkContent);
                    
                    // Create memory chunk
                    String chunkId = memoryId + "_" + UUID.randomUUID().toString();
                    MemoryChunk chunk = new MemoryChunk(
                        chunkId, memoryId, chunkContent, embedding,
                        request.getType(), request.getMetadata(),
                        request.getSourceAgent()
                    );
                    
                    // Check importance threshold
                    if (chunk.getImportance() >= request.getImportanceThreshold()) {
                        memoryChunks.add(chunk);
                        storedChunkIds.add(chunkId);
                    }
                }
                
                // Find related memories
                for (MemoryChunk chunk : memoryChunks) {
                    List<String> related = findRelatedMemories(chunk, 5);
                    related.forEach(chunk::addRelatedChunk);
                }
                
                // Store chunks
                for (MemoryChunk chunk : memoryChunks) {
                    storeChunk(chunk, request.getTenantContext());
                }
                
                // Update context if specified
                if (request.getContextId() != null) {
                    updateMemoryContext(request.getContextId(), storedChunkIds);
                }
                
                // Trigger consolidation if needed
                if (memoryChunks.size() > 10) {
                    consolidateRelatedMemories(memoryId);
                }
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to store memory", e);
            }
            
            return storedChunkIds;
        });
    }
    
    /**
     * Retrieve memories based on semantic search
     */
    public CompletableFuture<List<MemorySearchResult>> retrieveMemories(
            MemoryRetrievalRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Build vector search request
                AdvancedVectorMetadataStore.AdvancedSearchRequest.Builder searchBuilder =
                    new AdvancedVectorMetadataStore.AdvancedSearchRequest.Builder()
                        .query(request.getQuery())
                        .tenantContext(request.getTenantContext())
                        .maxResults(request.getMaxResults() * 2) // Get extra for filtering
                        .searchMode(AdvancedVectorMetadataStore.AdvancedSearchRequest.SearchMode.HYBRID);
                
                // Add type filters
                if (!request.getTypes().isEmpty()) {
                    List<String> typeStrings = request.getTypes().stream()
                        .map(Enum::name)
                        .collect(Collectors.toList());
                    searchBuilder.filter("type", 
                        new AdvancedVectorMetadataStore.FilterCondition(
                            AdvancedVectorMetadataStore.FilterCondition.FilterType.IN, typeStrings));
                }
                
                // Add time range filter
                searchBuilder.filter("timestamp",
                    new AdvancedVectorMetadataStore.FilterCondition(
                        AdvancedVectorMetadataStore.FilterCondition.FilterType.BETWEEN,
                        request.getTimeRangeStart(), request.getTimeRangeEnd()));
                
                // Add importance filter
                searchBuilder.filter("importance",
                    new AdvancedVectorMetadataStore.FilterCondition(
                        AdvancedVectorMetadataStore.FilterCondition.FilterType.GREATER_EQUAL,
                        request.getMinImportance()));
                
                // Add metadata filters
                request.getMetadataFilters().forEach((key, value) ->
                    searchBuilder.filter("metadata." + key,
                        new AdvancedVectorMetadataStore.FilterCondition(
                            AdvancedVectorMetadataStore.FilterCondition.FilterType.EQUALS, value))
                );
                
                // Execute search
                List<AdvancedVectorMetadataStore.EnhancedVectorDocument> searchResults =
                    vectorStore.advancedSearch(searchBuilder.build()).get();
                
                // Convert to memory results
                List<MemorySearchResult> results = new ArrayList<>();
                Set<String> addedChunks = new HashSet<>();
                
                for (AdvancedVectorMetadataStore.EnhancedVectorDocument doc : searchResults) {
                    String chunkId = doc.getId();
                    if (addedChunks.contains(chunkId)) continue;
                    
                    MemoryChunk chunk = loadChunk(chunkId);
                    if (chunk == null) continue;
                    
                    // Record access
                    chunk.recordAccess();
                    
                    // Create result
                    MemorySearchResult result = new MemorySearchResult(
                        chunk,
                        doc.getScores().get("combined_score"),
                        doc.getScores()
                    );
                    
                    results.add(result);
                    addedChunks.add(chunkId);
                    
                    // Include related memories if requested
                    if (request.isIncludeRelated() && results.size() < request.getMaxResults()) {
                        for (String relatedId : chunk.getRelatedChunks()) {
                            if (!addedChunks.contains(relatedId)) {
                                MemoryChunk related = loadChunk(relatedId);
                                if (related != null && related.getImportance() >= request.getMinImportance()) {
                                    results.add(new MemorySearchResult(
                                        related,
                                        result.getRelevanceScore() * 0.7, // Reduce score for related
                                        Map.of("related_to", chunkId)
                                    ));
                                    addedChunks.add(relatedId);
                                }
                            }
                            
                            if (results.size() >= request.getMaxResults()) break;
                        }
                    }
                    
                    if (results.size() >= request.getMaxResults()) break;
                }
                
                // Filter by context if specified
                if (request.getContextId() != null) {
                    MemoryContext context = contextCache.get(request.getContextId());
                    if (context != null) {
                        Set<String> contextChunks = new HashSet<>(context.getChunkIds());
                        results = results.stream()
                            .filter(r -> contextChunks.contains(r.getChunk().getChunkId()))
                            .collect(Collectors.toList());
                    }
                }
                
                return results;
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to retrieve memories", e);
            }
        });
    }
    
    /**
     * Memory search result
     */
    public static class MemorySearchResult {
        private final MemoryChunk chunk;
        private final double relevanceScore;
        private final Map<String, Object> scores;
        
        public MemorySearchResult(MemoryChunk chunk, double relevanceScore,
                                Map<String, Object> scores) {
            this.chunk = chunk;
            this.relevanceScore = relevanceScore;
            this.scores = scores;
        }
        
        // Getters
        public MemoryChunk getChunk() { return chunk; }
        public double getRelevanceScore() { return relevanceScore; }
        public Map<String, Object> getScores() { return scores; }
    }
    
    /**
     * Create or update memory context
     */
    public CompletableFuture<String> createMemoryContext(String description,
                                                       Map<String, Double> initialThemes) {
        return CompletableFuture.supplyAsync(() -> {
            String contextId = UUID.randomUUID().toString();
            MemoryContext context = new MemoryContext(contextId, description);
            
            // Set initial themes
            initialThemes.forEach(context::updateTheme);
            
            // Store context
            contextCache.put(contextId, context);
            persistContext(context);
            
            return contextId;
        });
    }
    
    /**
     * Consolidate related memories into higher-level abstractions
     */
    private void consolidateRelatedMemories(String memoryId) {
        try {
            // Get all chunks for this memory
            List<MemoryChunk> chunks = memoryChunks.values().stream()
                .filter(chunk -> chunk.getMemoryId().equals(memoryId))
                .collect(Collectors.toList());
            
            if (chunks.size() < 5) return; // Not enough to consolidate
            
            // Group by similarity
            Map<Integer, List<MemoryChunk>> clusters = clusterMemories(chunks);
            
            // Create consolidated memories for each cluster
            for (List<MemoryChunk> cluster : clusters.values()) {
                if (cluster.size() >= 3) {
                    createConsolidatedMemory(cluster);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Failed to consolidate memories: " + e.getMessage());
        }
    }
    
    /**
     * Create consolidated memory from cluster
     */
    private void createConsolidatedMemory(List<MemoryChunk> cluster) {
        // Generate summary using AI
        StringBuilder prompt = new StringBuilder();
        prompt.append("Summarize these related memories into a coherent abstract concept:\n\n");
        
        for (MemoryChunk chunk : cluster) {
            prompt.append("- ").append(chunk.getContent()).append("\n");
        }
        
        prompt.append("\nProvide a concise summary that captures the key insights.");
        
        // In production, would call Vertex AI
        String summary = "Consolidated memory of " + cluster.size() + " related chunks";
        
        // Create new semantic memory
        MemoryStorageRequest consolidatedRequest = new MemoryStorageRequest(
            summary,
            MemoryChunk.MemoryType.SEMANTIC,
            "system_consolidation",
            null, // Use default tenant
            Map.of(
                "source_chunks", cluster.stream()
                    .map(MemoryChunk::getChunkId)
                    .collect(Collectors.toList()),
                "consolidation_time", System.currentTimeMillis()
            ),
            null,
            false,
            0.7 // Higher importance threshold
        );
        
        storeMemory(consolidatedRequest);
    }
    
    /**
     * Cluster memories by similarity
     */
    private Map<Integer, List<MemoryChunk>> clusterMemories(List<MemoryChunk> chunks) {
        // Simple clustering based on embedding similarity
        Map<Integer, List<MemoryChunk>> clusters = new HashMap<>();
        int clusterCount = 0;
        
        for (MemoryChunk chunk : chunks) {
            boolean assigned = false;
            
            // Check existing clusters
            for (Map.Entry<Integer, List<MemoryChunk>> entry : clusters.entrySet()) {
                MemoryChunk representative = entry.getValue().get(0);
                double similarity = calculateCosineSimilarity(
                    chunk.getEmbedding(), representative.getEmbedding());
                
                if (similarity > 0.8) {
                    entry.getValue().add(chunk);
                    assigned = true;
                    break;
                }
            }
            
            // Create new cluster if not assigned
            if (!assigned) {
                clusters.computeIfAbsent(clusterCount++, k -> new ArrayList<>()).add(chunk);
            }
        }
        
        return clusters;
    }
    
    /**
     * Memory decay and cleanup
     */
    private void consolidateMemories() {
        // Apply decay to all memories
        for (MemoryChunk chunk : memoryChunks.values()) {
            chunk.decay();
        }
        
        // Identify memories for consolidation
        Map<String, List<MemoryChunk>> memoryGroups = memoryChunks.values().stream()
            .collect(Collectors.groupingBy(MemoryChunk::getMemoryId));
        
        for (Map.Entry<String, List<MemoryChunk>> entry : memoryGroups.entrySet()) {
            if (entry.getValue().size() > 10) {
                consolidateRelatedMemories(entry.getKey());
            }
        }
    }
    
    /**
     * Clean up expired memories
     */
    private void cleanupExpiredMemories() {
        long cutoffTime = System.currentTimeMillis() - MEMORY_TTL;
        List<String> toRemove = new ArrayList<>();
        
        for (MemoryChunk chunk : memoryChunks.values()) {
            // Remove if too old and low importance
            if (chunk.getTimestamp() < cutoffTime && chunk.getImportance() < 0.3) {
                toRemove.add(chunk.getChunkId());
            }
            
            // Remove if never accessed and old
            if (chunk.getAccessCount() == 0 && 
                chunk.getTimestamp() < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)) {
                toRemove.add(chunk.getChunkId());
            }
        }
        
        // Remove from cache and storage
        for (String chunkId : toRemove) {
            memoryChunks.remove(chunkId);
            deleteChunk(chunkId);
        }
        
        System.out.println("Cleaned up " + toRemove.size() + " expired memories");
    }
    
    /**
     * Get memory statistics
     */
    public MemoryStatistics getStatistics() {
        Map<MemoryChunk.MemoryType, Integer> countByType = memoryChunks.values().stream()
            .collect(Collectors.groupingBy(
                MemoryChunk::getType,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
            ));
        
        double avgImportance = memoryChunks.values().stream()
            .mapToDouble(MemoryChunk::getImportance)
            .average()
            .orElse(0.0);
        
        long totalAccesses = memoryChunks.values().stream()
            .mapToLong(MemoryChunk::getAccessCount)
            .sum();
        
        return new MemoryStatistics(
            memoryChunks.size(),
            countByType,
            avgImportance,
            totalAccesses,
            contextCache.size(),
            getOldestMemoryAge(),
            getStorageSize()
        );
    }
    
    /**
     * Memory statistics
     */
    public static class MemoryStatistics {
        private final int totalChunks;
        private final Map<MemoryChunk.MemoryType, Integer> chunksByType;
        private final double averageImportance;
        private final long totalAccesses;
        private final int contextCount;
        private final long oldestMemoryAge;
        private final long storageSizeBytes;
        
        public MemoryStatistics(int totalChunks, Map<MemoryChunk.MemoryType, Integer> chunksByType,
                              double averageImportance, long totalAccesses, int contextCount,
                              long oldestMemoryAge, long storageSizeBytes) {
            this.totalChunks = totalChunks;
            this.chunksByType = chunksByType;
            this.averageImportance = averageImportance;
            this.totalAccesses = totalAccesses;
            this.contextCount = contextCount;
            this.oldestMemoryAge = oldestMemoryAge;
            this.storageSizeBytes = storageSizeBytes;
        }
        
        // Getters
        public int getTotalChunks() { return totalChunks; }
        public Map<MemoryChunk.MemoryType, Integer> getChunksByType() { return chunksByType; }
        public double getAverageImportance() { return averageImportance; }
        public long getTotalAccesses() { return totalAccesses; }
        public int getContextCount() { return contextCount; }
        public long getOldestMemoryAge() { return oldestMemoryAge; }
        public long getStorageSizeBytes() { return storageSizeBytes; }
    }
    
    /**
     * Helper methods
     */
    
    private List<String> chunkContent(String content) {
        // Simple chunking by sentences
        List<String> chunks = new ArrayList<>();
        String[] sentences = content.split("(?<=[.!?])\\s+");
        
        StringBuilder currentChunk = new StringBuilder();
        int tokenCount = 0;
        
        for (String sentence : sentences) {
            int sentenceTokens = estimateTokens(sentence);
            
            if (tokenCount + sentenceTokens > CHUNK_SIZE && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
                tokenCount = 0;
            }
            
            currentChunk.append(sentence).append(" ");
            tokenCount += sentenceTokens;
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    private int estimateTokens(String text) {
        // Rough estimation: 1 token per 4 characters
        return text.length() / 4;
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
    
    private List<String> findRelatedMemories(MemoryChunk chunk, int count) {
        // In production, would use vector search
        return new ArrayList<>();
    }
    
    private void storeChunk(MemoryChunk chunk, TenantContext tenant) {
        // Store in cache
        memoryChunks.put(chunk.getChunkId(), chunk);
        
        // Convert to vector document for storage
        AdvancedVectorMetadataStore.EnhancedVectorDocument doc =
            new AdvancedVectorMetadataStore.EnhancedVectorDocument.Builder()
                .id(chunk.getChunkId())
                .content(chunk.getContent())
                .embedding(chunk.getEmbedding())
                .metadata("memory_id", chunk.getMemoryId())
                .metadata("type", chunk.getType().name())
                .metadata("source_agent", chunk.getSourceAgent())
                .numericMetadata("importance", chunk.getImportance())
                .numericMetadata("timestamp", (double) chunk.getTimestamp())
                .structuredMetadata(chunk.getMetadata())
                .tags(Set.of(chunk.getType().name().toLowerCase(), "memory"))
                .tenantContext(tenant)
                .build();
        
        // Store in vector store
        vectorStore.batchIndex(Collections.singletonList(doc));
        
        // Persist to Firestore
        persistChunk(chunk);
    }
    
    private MemoryChunk loadChunk(String chunkId) {
        return memoryChunks.get(chunkId);
    }
    
    private void deleteChunk(String chunkId) {
        // Remove from Firestore
        firestore.collection("memory_chunks").document(chunkId).delete();
    }
    
    private void persistChunk(MemoryChunk chunk) {
        Map<String, Object> data = new HashMap<>();
        data.put("chunk_id", chunk.getChunkId());
        data.put("memory_id", chunk.getMemoryId());
        data.put("content", chunk.getContent());
        data.put("type", chunk.getType().name());
        data.put("source_agent", chunk.getSourceAgent());
        data.put("importance", chunk.getImportance());
        data.put("timestamp", chunk.getTimestamp());
        data.put("access_count", chunk.getAccessCount());
        data.put("last_accessed", chunk.getLastAccessed());
        data.put("metadata", chunk.getMetadata());
        data.put("related_chunks", new ArrayList<>(chunk.getRelatedChunks()));
        
        firestore.collection("memory_chunks")
            .document(chunk.getChunkId())
            .set(data);
    }
    
    private void persistContext(MemoryContext context) {
        Map<String, Object> data = new HashMap<>();
        data.put("context_id", context.getContextId());
        data.put("description", context.getDescription());
        data.put("chunk_ids", context.getChunkIds());
        data.put("themes", context.getThemes());
        data.put("summary", context.getSummary());
        data.put("created_at", context.getCreatedAt());
        data.put("last_updated", context.getLastUpdated());
        
        firestore.collection("memory_contexts")
            .document(context.getContextId())
            .set(data);
    }
    
    private void updateMemoryContext(String contextId, List<String> newChunkIds) {
        MemoryContext context = contextCache.get(contextId);
        if (context == null) {
            // Load from Firestore
            return;
        }
        
        newChunkIds.forEach(context::addChunk);
        persistContext(context);
    }
    
    private long getOldestMemoryAge() {
        return memoryChunks.values().stream()
            .mapToLong(MemoryChunk::getTimestamp)
            .min()
            .map(oldest -> System.currentTimeMillis() - oldest)
            .orElse(0L);
    }
    
    private long getStorageSize() {
        // Estimate storage size
        return memoryChunks.size() * 2048L; // Rough estimate
    }
    
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            
            // Persist all memories
            memoryChunks.values().forEach(this::persistChunk);
            contextCache.values().forEach(this::persistContext);
            
            vectorStore.shutdown();
            predictionClient.close();
        } catch (Exception e) {
            scheduler.shutdownNow();
        }
    }
}