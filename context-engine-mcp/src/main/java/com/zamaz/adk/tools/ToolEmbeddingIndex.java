package com.zamaz.adk.tools;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.*;
import com.zamaz.adk.core.TenantContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Tool Embedding Index - Vector-based tool discovery and selection
 * Uses embeddings to find semantically similar tools based on natural language queries
 */
public class ToolEmbeddingIndex {
    private final PredictionServiceClient predictionClient;
    private final Firestore firestore;
    private final Storage storage;
    private final String projectId;
    private final String location;
    private final String bucketName;
    
    // In-memory index for fast retrieval
    private final Map<String, ToolEmbedding> embeddingCache = new ConcurrentHashMap<>();
    private final Map<String, List<ToolEmbedding>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<String, List<ToolEmbedding>> tenantIndex = new ConcurrentHashMap<>();
    
    // Embedding model configuration
    private static final String EMBEDDING_MODEL = "textembedding-gecko@003";
    private static final int EMBEDDING_DIMENSION = 768;
    private static final int MAX_BATCH_SIZE = 100;
    
    public ToolEmbeddingIndex(String projectId, String location, 
                            Firestore firestore, Storage storage, String bucketName) {
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
        
        // Load existing embeddings
        loadExistingEmbeddings();
    }
    
    /**
     * Tool with rich metadata for embedding
     */
    public static class EnrichedTool {
        private final String id;
        private final String name;
        private final String description;
        private final List<String> categories;
        private final Map<String, Object> inputSchema;
        private final Map<String, Object> outputSchema;
        private final List<String> keywords;
        private final List<String> examples;
        private final Map<String, Object> metadata;
        private final TenantContext tenantContext;
        private final double popularity;
        private final double successRate;
        
        public EnrichedTool(String id, String name, String description,
                          List<String> categories, Map<String, Object> inputSchema,
                          Map<String, Object> outputSchema, List<String> keywords,
                          List<String> examples, Map<String, Object> metadata,
                          TenantContext tenantContext) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.categories = categories;
            this.inputSchema = inputSchema;
            this.outputSchema = outputSchema;
            this.keywords = keywords;
            this.examples = examples;
            this.metadata = metadata;
            this.tenantContext = tenantContext;
            this.popularity = (Double) metadata.getOrDefault("popularity", 0.5);
            this.successRate = (Double) metadata.getOrDefault("success_rate", 0.8);
        }
        
        /**
         * Generate rich text representation for embedding
         */
        public String toEmbeddingText() {
            StringBuilder text = new StringBuilder();
            
            // Tool name and description
            text.append("Tool: ").append(name).append("\n");
            text.append("Description: ").append(description).append("\n");
            
            // Categories
            if (!categories.isEmpty()) {
                text.append("Categories: ").append(String.join(", ", categories)).append("\n");
            }
            
            // Keywords
            if (!keywords.isEmpty()) {
                text.append("Keywords: ").append(String.join(", ", keywords)).append("\n");
            }
            
            // Input/Output information
            text.append("Input: ").append(describeSchema(inputSchema)).append("\n");
            text.append("Output: ").append(describeSchema(outputSchema)).append("\n");
            
            // Examples
            if (!examples.isEmpty()) {
                text.append("Examples:\n");
                for (String example : examples) {
                    text.append("- ").append(example).append("\n");
                }
            }
            
            // Additional metadata
            if (metadata.containsKey("use_cases")) {
                text.append("Use cases: ").append(metadata.get("use_cases")).append("\n");
            }
            
            return text.toString();
        }
        
        private String describeSchema(Map<String, Object> schema) {
            if (schema == null || schema.isEmpty()) {
                return "No schema defined";
            }
            
            List<String> fields = new ArrayList<>();
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();
                    String type = (String) fieldDef.get("type");
                    fields.add(entry.getKey() + " (" + type + ")");
                }
            }
            
            return fields.isEmpty() ? "No fields" : String.join(", ", fields);
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public List<String> getCategories() { return categories; }
        public Map<String, Object> getInputSchema() { return inputSchema; }
        public Map<String, Object> getOutputSchema() { return outputSchema; }
        public List<String> getKeywords() { return keywords; }
        public List<String> getExamples() { return examples; }
        public Map<String, Object> getMetadata() { return metadata; }
        public TenantContext getTenantContext() { return tenantContext; }
        public double getPopularity() { return popularity; }
        public double getSuccessRate() { return successRate; }
    }
    
    /**
     * Tool embedding with metadata
     */
    public static class ToolEmbedding {
        private final String toolId;
        private final EnrichedTool tool;
        private final float[] embedding;
        private final long timestamp;
        private final String embeddingText;
        
        public ToolEmbedding(String toolId, EnrichedTool tool, float[] embedding,
                           String embeddingText) {
            this.toolId = toolId;
            this.tool = tool;
            this.embedding = embedding;
            this.embeddingText = embeddingText;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters
        public String getToolId() { return toolId; }
        public EnrichedTool getTool() { return tool; }
        public float[] getEmbedding() { return embedding; }
        public long getTimestamp() { return timestamp; }
        public String getEmbeddingText() { return embeddingText; }
    }
    
    /**
     * Tool match result
     */
    public static class ToolMatch {
        private final EnrichedTool tool;
        private final double similarityScore;
        private final double relevanceScore;
        private final Map<String, Double> scoreBreakdown;
        private final String explanation;
        
        public ToolMatch(EnrichedTool tool, double similarityScore,
                       double relevanceScore, Map<String, Double> scoreBreakdown,
                       String explanation) {
            this.tool = tool;
            this.similarityScore = similarityScore;
            this.relevanceScore = relevanceScore;
            this.scoreBreakdown = scoreBreakdown;
            this.explanation = explanation;
        }
        
        // Getters
        public EnrichedTool getTool() { return tool; }
        public double getSimilarityScore() { return similarityScore; }
        public double getRelevanceScore() { return relevanceScore; }
        public Map<String, Double> getScoreBreakdown() { return scoreBreakdown; }
        public String getExplanation() { return explanation; }
    }
    
    /**
     * Index a tool with embeddings
     */
    public CompletableFuture<String> indexTool(EnrichedTool tool) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate embedding text
                String embeddingText = tool.toEmbeddingText();
                
                // Generate embedding
                float[] embedding = generateEmbedding(embeddingText);
                
                // Create tool embedding
                ToolEmbedding toolEmbedding = new ToolEmbedding(
                    tool.getId(), tool, embedding, embeddingText);
                
                // Store in cache
                embeddingCache.put(tool.getId(), toolEmbedding);
                
                // Update category index
                for (String category : tool.getCategories()) {
                    categoryIndex.computeIfAbsent(category.toLowerCase(), 
                        k -> new ArrayList<>()).add(toolEmbedding);
                }
                
                // Update tenant index
                if (tool.getTenantContext() != null) {
                    tenantIndex.computeIfAbsent(tool.getTenantContext().getTenantPath(),
                        k -> new ArrayList<>()).add(toolEmbedding);
                }
                
                // Persist to storage
                persistEmbedding(toolEmbedding);
                
                return tool.getId();
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to index tool: " + tool.getName(), e);
            }
        });
    }
    
    /**
     * Index multiple tools in batch
     */
    public CompletableFuture<Map<String, Boolean>> indexToolsBatch(List<EnrichedTool> tools) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Boolean> results = new HashMap<>();
            
            // Process in batches for efficiency
            for (int i = 0; i < tools.size(); i += MAX_BATCH_SIZE) {
                List<EnrichedTool> batch = tools.subList(i, 
                    Math.min(i + MAX_BATCH_SIZE, tools.size()));
                
                // Generate embeddings for batch
                List<String> texts = batch.stream()
                    .map(EnrichedTool::toEmbeddingText)
                    .collect(Collectors.toList());
                
                List<float[]> embeddings = generateEmbeddingsBatch(texts);
                
                // Index each tool
                for (int j = 0; j < batch.size(); j++) {
                    EnrichedTool tool = batch.get(j);
                    float[] embedding = embeddings.get(j);
                    
                    ToolEmbedding toolEmbedding = new ToolEmbedding(
                        tool.getId(), tool, embedding, texts.get(j));
                    
                    // Update indices
                    embeddingCache.put(tool.getId(), toolEmbedding);
                    updateIndices(toolEmbedding);
                    
                    results.put(tool.getId(), true);
                }
                
                // Persist batch
                persistEmbeddingsBatch(batch, embeddings, texts);
            }
            
            return results;
        });
    }
    
    /**
     * Search for tools using natural language query
     */
    public List<ToolMatch> searchTools(String query, SearchOptions options) {
        // Generate query embedding
        float[] queryEmbedding = generateEmbedding(query);
        
        // Get candidate tools based on filters
        List<ToolEmbedding> candidates = getCandidates(options);
        
        // Calculate similarities
        List<ToolMatch> matches = new ArrayList<>();
        
        for (ToolEmbedding candidate : candidates) {
            // Calculate cosine similarity
            double similarity = calculateCosineSimilarity(queryEmbedding, 
                candidate.getEmbedding());
            
            // Skip if below threshold
            if (similarity < options.getMinSimilarity()) {
                continue;
            }
            
            // Calculate relevance score
            Map<String, Double> scoreBreakdown = new HashMap<>();
            scoreBreakdown.put("similarity", similarity);
            
            // Boost score based on categories match
            double categoryBoost = calculateCategoryBoost(query, 
                candidate.getTool().getCategories());
            scoreBreakdown.put("category_boost", categoryBoost);
            
            // Boost based on keywords match
            double keywordBoost = calculateKeywordBoost(query, 
                candidate.getTool().getKeywords());
            scoreBreakdown.put("keyword_boost", keywordBoost);
            
            // Factor in popularity and success rate
            double popularityFactor = candidate.getTool().getPopularity() * 0.1;
            double successFactor = candidate.getTool().getSuccessRate() * 0.1;
            scoreBreakdown.put("popularity", popularityFactor);
            scoreBreakdown.put("success_rate", successFactor);
            
            // Calculate final relevance score
            double relevanceScore = similarity * 0.6 + 
                                  categoryBoost * 0.2 + 
                                  keywordBoost * 0.1 +
                                  popularityFactor + 
                                  successFactor;
            
            // Generate explanation
            String explanation = generateExplanation(candidate.getTool(), 
                scoreBreakdown, query);
            
            matches.add(new ToolMatch(candidate.getTool(), similarity, 
                relevanceScore, scoreBreakdown, explanation));
        }
        
        // Sort by relevance score
        matches.sort((a, b) -> Double.compare(b.getRelevanceScore(), 
            a.getRelevanceScore()));
        
        // Apply limit
        if (options.getMaxResults() > 0 && matches.size() > options.getMaxResults()) {
            matches = matches.subList(0, options.getMaxResults());
        }
        
        // Record search for analytics
        recordSearch(query, matches, options);
        
        return matches;
    }
    
    /**
     * Search options
     */
    public static class SearchOptions {
        private final List<String> categories;
        private final TenantContext tenantContext;
        private final double minSimilarity;
        private final int maxResults;
        private final boolean includeExamples;
        private final Map<String, Object> filters;
        
        public SearchOptions(List<String> categories, TenantContext tenantContext,
                           double minSimilarity, int maxResults,
                           boolean includeExamples, Map<String, Object> filters) {
            this.categories = categories;
            this.tenantContext = tenantContext;
            this.minSimilarity = minSimilarity;
            this.maxResults = maxResults;
            this.includeExamples = includeExamples;
            this.filters = filters;
        }
        
        public static SearchOptions defaultOptions() {
            return new SearchOptions(Collections.emptyList(), null, 
                0.5, 10, true, new HashMap<>());
        }
        
        // Getters
        public List<String> getCategories() { return categories; }
        public TenantContext getTenantContext() { return tenantContext; }
        public double getMinSimilarity() { return minSimilarity; }
        public int getMaxResults() { return maxResults; }
        public boolean isIncludeExamples() { return includeExamples; }
        public Map<String, Object> getFilters() { return filters; }
    }
    
    /**
     * Get related tools
     */
    public List<ToolMatch> getRelatedTools(String toolId, int maxResults) {
        ToolEmbedding sourceEmbedding = embeddingCache.get(toolId);
        if (sourceEmbedding == null) {
            return Collections.emptyList();
        }
        
        List<ToolMatch> matches = new ArrayList<>();
        
        for (ToolEmbedding candidate : embeddingCache.values()) {
            if (candidate.getToolId().equals(toolId)) {
                continue;
            }
            
            double similarity = calculateCosineSimilarity(
                sourceEmbedding.getEmbedding(), 
                candidate.getEmbedding());
            
            if (similarity > 0.7) {
                matches.add(new ToolMatch(candidate.getTool(), similarity, 
                    similarity, Map.of("similarity", similarity), 
                    "Related based on functionality"));
            }
        }
        
        matches.sort((a, b) -> Double.compare(b.getSimilarityScore(), 
            a.getSimilarityScore()));
        
        if (maxResults > 0 && matches.size() > maxResults) {
            matches = matches.subList(0, maxResults);
        }
        
        return matches;
    }
    
    /**
     * Update tool popularity based on usage
     */
    public void updateToolPopularity(String toolId, boolean wasSuccessful) {
        ToolEmbedding embedding = embeddingCache.get(toolId);
        if (embedding == null) {
            return;
        }
        
        EnrichedTool tool = embedding.getTool();
        Map<String, Object> metadata = tool.getMetadata();
        
        // Update popularity (simple exponential moving average)
        double currentPopularity = tool.getPopularity();
        double newPopularity = currentPopularity * 0.95 + 0.05;
        metadata.put("popularity", Math.min(1.0, newPopularity));
        
        // Update success rate
        if (wasSuccessful) {
            double currentSuccess = tool.getSuccessRate();
            double newSuccess = currentSuccess * 0.9 + 0.1;
            metadata.put("success_rate", newSuccess);
        } else {
            double currentSuccess = tool.getSuccessRate();
            double newSuccess = currentSuccess * 0.9;
            metadata.put("success_rate", newSuccess);
        }
        
        // Update in Firestore
        firestore.collection("tool_metadata")
            .document(toolId)
            .update(metadata);
    }
    
    /**
     * Generate embedding for text
     */
    private float[] generateEmbedding(String text) {
        try {
            EndpointName endpointName = EndpointName.of(projectId, location, EMBEDDING_MODEL);
            
            Value instance = Value.newBuilder()
                .setStructValue(Struct.newBuilder()
                    .putFields("content", Value.newBuilder()
                        .setStringValue(text)
                        .build())
                    .build())
                .build();
            
            PredictRequest request = PredictRequest.newBuilder()
                .setEndpoint(endpointName.toString())
                .addInstances(instance)
                .build();
            
            PredictResponse response = predictionClient.predict(request);
            
            // Extract embedding from response
            List<Value> predictions = response.getPredictionsList();
            if (!predictions.isEmpty()) {
                List<Value> embeddingValues = predictions.get(0)
                    .getStructValue()
                    .getFieldsOrThrow("embeddings")
                    .getStructValue()
                    .getFieldsOrThrow("values")
                    .getListValue()
                    .getValuesList();
                
                float[] embedding = new float[embeddingValues.size()];
                for (int i = 0; i < embeddingValues.size(); i++) {
                    embedding[i] = (float) embeddingValues.get(i).getNumberValue();
                }
                
                return embedding;
            }
            
            throw new RuntimeException("No embedding returned");
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }
    
    /**
     * Generate embeddings for batch of texts
     */
    private List<float[]> generateEmbeddingsBatch(List<String> texts) {
        try {
            EndpointName endpointName = EndpointName.of(projectId, location, EMBEDDING_MODEL);
            
            // Create instances
            List<Value> instances = texts.stream()
                .map(text -> Value.newBuilder()
                    .setStructValue(Struct.newBuilder()
                        .putFields("content", Value.newBuilder()
                            .setStringValue(text)
                            .build())
                        .build())
                    .build())
                .collect(Collectors.toList());
            
            PredictRequest request = PredictRequest.newBuilder()
                .setEndpoint(endpointName.toString())
                .addAllInstances(instances)
                .build();
            
            PredictResponse response = predictionClient.predict(request);
            
            // Extract embeddings
            List<float[]> embeddings = new ArrayList<>();
            for (Value prediction : response.getPredictionsList()) {
                List<Value> embeddingValues = prediction
                    .getStructValue()
                    .getFieldsOrThrow("embeddings")
                    .getStructValue()
                    .getFieldsOrThrow("values")
                    .getListValue()
                    .getValuesList();
                
                float[] embedding = new float[embeddingValues.size()];
                for (int i = 0; i < embeddingValues.size(); i++) {
                    embedding[i] = (float) embeddingValues.get(i).getNumberValue();
                }
                
                embeddings.add(embedding);
            }
            
            return embeddings;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate batch embeddings", e);
        }
    }
    
    /**
     * Calculate cosine similarity between embeddings
     */
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
    
    /**
     * Get candidate tools based on filters
     */
    private List<ToolEmbedding> getCandidates(SearchOptions options) {
        List<ToolEmbedding> candidates = new ArrayList<>();
        
        // Filter by tenant if specified
        if (options.getTenantContext() != null) {
            List<ToolEmbedding> tenantTools = tenantIndex.get(
                options.getTenantContext().getTenantPath());
            if (tenantTools != null) {
                candidates.addAll(tenantTools);
            }
        } else {
            candidates.addAll(embeddingCache.values());
        }
        
        // Filter by categories if specified
        if (!options.getCategories().isEmpty()) {
            Set<String> targetCategories = new HashSet<>(options.getCategories());
            candidates = candidates.stream()
                .filter(e -> !Collections.disjoint(
                    e.getTool().getCategories(), targetCategories))
                .collect(Collectors.toList());
        }
        
        // Apply custom filters
        for (Map.Entry<String, Object> filter : options.getFilters().entrySet()) {
            String key = filter.getKey();
            Object value = filter.getValue();
            
            candidates = candidates.stream()
                .filter(e -> {
                    Object toolValue = e.getTool().getMetadata().get(key);
                    return Objects.equals(toolValue, value);
                })
                .collect(Collectors.toList());
        }
        
        return candidates;
    }
    
    /**
     * Calculate category boost
     */
    private double calculateCategoryBoost(String query, List<String> categories) {
        String queryLower = query.toLowerCase();
        
        for (String category : categories) {
            if (queryLower.contains(category.toLowerCase())) {
                return 0.3;
            }
        }
        
        return 0.0;
    }
    
    /**
     * Calculate keyword boost
     */
    private double calculateKeywordBoost(String query, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0.0;
        }
        
        String queryLower = query.toLowerCase();
        long matchCount = keywords.stream()
            .filter(keyword -> queryLower.contains(keyword.toLowerCase()))
            .count();
        
        return Math.min(0.3, matchCount * 0.1);
    }
    
    /**
     * Generate explanation for tool match
     */
    private String generateExplanation(EnrichedTool tool, 
                                     Map<String, Double> scoreBreakdown,
                                     String query) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append("Tool '").append(tool.getName())
                  .append("' matches your query");
        
        if (scoreBreakdown.get("similarity") > 0.8) {
            explanation.append(" with high semantic similarity");
        }
        
        if (scoreBreakdown.get("category_boost") > 0) {
            explanation.append(", matching category intent");
        }
        
        if (scoreBreakdown.get("keyword_boost") > 0) {
            explanation.append(", contains relevant keywords");
        }
        
        if (tool.getPopularity() > 0.7) {
            explanation.append(", frequently used by others");
        }
        
        explanation.append(".");
        
        return explanation.toString();
    }
    
    /**
     * Update indices for a tool embedding
     */
    private void updateIndices(ToolEmbedding embedding) {
        // Update category index
        for (String category : embedding.getTool().getCategories()) {
            categoryIndex.computeIfAbsent(category.toLowerCase(), 
                k -> new ArrayList<>()).add(embedding);
        }
        
        // Update tenant index
        if (embedding.getTool().getTenantContext() != null) {
            tenantIndex.computeIfAbsent(
                embedding.getTool().getTenantContext().getTenantPath(),
                k -> new ArrayList<>()).add(embedding);
        }
    }
    
    /**
     * Persist embedding to storage
     */
    private void persistEmbedding(ToolEmbedding embedding) {
        try {
            // Store embedding vector in Cloud Storage
            String blobName = String.format("tool-embeddings/%s.bin", 
                embedding.getToolId());
            
            BlobId blobId = BlobId.of(bucketName, blobName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
            
            // Serialize embedding
            byte[] data = serializeEmbedding(embedding.getEmbedding());
            storage.create(blobInfo, data);
            
            // Store metadata in Firestore
            Map<String, Object> doc = new HashMap<>();
            doc.put("tool_id", embedding.getToolId());
            doc.put("tool_name", embedding.getTool().getName());
            doc.put("embedding_text", embedding.getEmbeddingText());
            doc.put("embedding_location", blobName);
            doc.put("timestamp", embedding.getTimestamp());
            doc.put("categories", embedding.getTool().getCategories());
            doc.put("tenant", embedding.getTool().getTenantContext() != null ?
                embedding.getTool().getTenantContext().getTenantPath() : null);
            
            firestore.collection("tool_embeddings")
                .document(embedding.getToolId())
                .set(doc);
                
        } catch (Exception e) {
            System.err.println("Failed to persist embedding: " + e.getMessage());
        }
    }
    
    /**
     * Persist batch of embeddings
     */
    private void persistEmbeddingsBatch(List<EnrichedTool> tools, 
                                       List<float[]> embeddings,
                                       List<String> texts) {
        // Batch write to Firestore
        var batch = firestore.batch();
        
        for (int i = 0; i < tools.size(); i++) {
            EnrichedTool tool = tools.get(i);
            float[] embedding = embeddings.get(i);
            String text = texts.get(i);
            
            // Store in Cloud Storage
            String blobName = String.format("tool-embeddings/%s.bin", tool.getId());
            
            try {
                BlobId blobId = BlobId.of(bucketName, blobName);
                BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
                byte[] data = serializeEmbedding(embedding);
                storage.create(blobInfo, data);
            } catch (Exception e) {
                System.err.println("Failed to store embedding for " + tool.getId());
                continue;
            }
            
            // Add to batch
            Map<String, Object> doc = new HashMap<>();
            doc.put("tool_id", tool.getId());
            doc.put("tool_name", tool.getName());
            doc.put("embedding_text", text);
            doc.put("embedding_location", blobName);
            doc.put("timestamp", System.currentTimeMillis());
            doc.put("categories", tool.getCategories());
            
            batch.set(firestore.collection("tool_embeddings")
                .document(tool.getId()), doc);
        }
        
        // Commit batch
        try {
            batch.commit().get();
        } catch (Exception e) {
            System.err.println("Failed to commit batch: " + e.getMessage());
        }
    }
    
    /**
     * Load existing embeddings from storage
     */
    private void loadExistingEmbeddings() {
        try {
            firestore.collection("tool_embeddings")
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> {
                    try {
                        String toolId = doc.getString("tool_id");
                        String blobName = doc.getString("embedding_location");
                        
                        // Load embedding from Cloud Storage
                        Blob blob = storage.get(bucketName, blobName);
                        if (blob != null) {
                            float[] embedding = deserializeEmbedding(blob.getContent());
                            
                            // Reconstruct tool (simplified - would load full details)
                            EnrichedTool tool = new EnrichedTool(
                                toolId,
                                doc.getString("tool_name"),
                                "", // Would load full description
                                (List<String>) doc.get("categories"),
                                new HashMap<>(),
                                new HashMap<>(),
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new HashMap<>(),
                                null // Would load tenant context
                            );
                            
                            ToolEmbedding toolEmbedding = new ToolEmbedding(
                                toolId, tool, embedding, doc.getString("embedding_text"));
                            
                            embeddingCache.put(toolId, toolEmbedding);
                            updateIndices(toolEmbedding);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to load embedding: " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            System.err.println("Failed to load embeddings: " + e.getMessage());
        }
    }
    
    /**
     * Record search for analytics
     */
    private void recordSearch(String query, List<ToolMatch> results, 
                            SearchOptions options) {
        Map<String, Object> searchRecord = new HashMap<>();
        searchRecord.put("query", query);
        searchRecord.put("timestamp", System.currentTimeMillis());
        searchRecord.put("result_count", results.size());
        searchRecord.put("top_result", results.isEmpty() ? null : 
            results.get(0).getTool().getName());
        searchRecord.put("options", options);
        
        firestore.collection("tool_searches")
            .add(searchRecord);
    }
    
    /**
     * Serialize embedding to bytes
     */
    private byte[] serializeEmbedding(float[] embedding) {
        byte[] bytes = new byte[embedding.length * 4];
        int idx = 0;
        
        for (float value : embedding) {
            int bits = Float.floatToIntBits(value);
            bytes[idx++] = (byte) (bits >> 24);
            bytes[idx++] = (byte) (bits >> 16);
            bytes[idx++] = (byte) (bits >> 8);
            bytes[idx++] = (byte) bits;
        }
        
        return bytes;
    }
    
    /**
     * Deserialize embedding from bytes
     */
    private float[] deserializeEmbedding(byte[] bytes) {
        float[] embedding = new float[bytes.length / 4];
        int idx = 0;
        
        for (int i = 0; i < embedding.length; i++) {
            int bits = ((bytes[idx++] & 0xFF) << 24) |
                      ((bytes[idx++] & 0xFF) << 16) |
                      ((bytes[idx++] & 0xFF) << 8) |
                      (bytes[idx++] & 0xFF);
            embedding[i] = Float.intBitsToFloat(bits);
        }
        
        return embedding;
    }
    
    public void shutdown() {
        try {
            predictionClient.close();
        } catch (Exception e) {
            System.err.println("Error shutting down: " + e.getMessage());
        }
    }
}