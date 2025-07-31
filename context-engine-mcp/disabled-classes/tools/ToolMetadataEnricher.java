package com.zamaz.adk.tools;

import com.google.cloud.aiplatform.v1.*;
import com.zamaz.adk.tools.ToolEmbeddingIndex.EnrichedTool;
import com.zamaz.adk.workflow.WorkflowEngine.VertexAIClient;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tool Metadata Enricher - Automatically generates rich metadata for tools
 * Uses AI to create descriptions, examples, and categorizations
 */
public class ToolMetadataEnricher {
    private final VertexAIClient aiClient;
    private final Map<String, EnrichmentCache> enrichmentCache = new ConcurrentHashMap<>();
    private final Map<String, Pattern> patternLibrary = new HashMap<>();
    
    public ToolMetadataEnricher(VertexAIClient aiClient) {
        this.aiClient = aiClient;
        initializePatternLibrary();
    }
    
    /**
     * Tool definition for enrichment
     */
    public static class ToolDefinition {
        private final String id;
        private final String name;
        private final String basicDescription;
        private final Map<String, Object> inputSchema;
        private final Map<String, Object> outputSchema;
        private final String implementation; // Optional code snippet
        private final Map<String, Object> existingMetadata;
        
        public ToolDefinition(String id, String name, String basicDescription,
                            Map<String, Object> inputSchema, Map<String, Object> outputSchema,
                            String implementation, Map<String, Object> existingMetadata) {
            this.id = id;
            this.name = name;
            this.basicDescription = basicDescription;
            this.inputSchema = inputSchema;
            this.outputSchema = outputSchema;
            this.implementation = implementation;
            this.existingMetadata = existingMetadata;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getBasicDescription() { return basicDescription; }
        public Map<String, Object> getInputSchema() { return inputSchema; }
        public Map<String, Object> getOutputSchema() { return outputSchema; }
        public String getImplementation() { return implementation; }
        public Map<String, Object> getExistingMetadata() { return existingMetadata; }
    }
    
    /**
     * Enrichment result
     */
    public static class EnrichmentResult {
        private final String enhancedDescription;
        private final List<String> categories;
        private final List<String> keywords;
        private final List<String> examples;
        private final Map<String, Object> capabilities;
        private final Map<String, Object> limitations;
        private final Map<String, Object> useCases;
        private final double confidence;
        
        public EnrichmentResult(String enhancedDescription, List<String> categories,
                              List<String> keywords, List<String> examples,
                              Map<String, Object> capabilities, Map<String, Object> limitations,
                              Map<String, Object> useCases, double confidence) {
            this.enhancedDescription = enhancedDescription;
            this.categories = categories;
            this.keywords = keywords;
            this.examples = examples;
            this.capabilities = capabilities;
            this.limitations = limitations;
            this.useCases = useCases;
            this.confidence = confidence;
        }
        
        // Getters
        public String getEnhancedDescription() { return enhancedDescription; }
        public List<String> getCategories() { return categories; }
        public List<String> getKeywords() { return keywords; }
        public List<String> getExamples() { return examples; }
        public Map<String, Object> getCapabilities() { return capabilities; }
        public Map<String, Object> getLimitations() { return limitations; }
        public Map<String, Object> getUseCases() { return useCases; }
        public double getConfidence() { return confidence; }
    }
    
    /**
     * Enrichment cache entry
     */
    private static class EnrichmentCache {
        private final EnrichmentResult result;
        private final long timestamp;
        private final String toolVersion;
        
        public EnrichmentCache(EnrichmentResult result, String toolVersion) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
            this.toolVersion = toolVersion;
        }
        
        public boolean isValid(String currentVersion) {
            // Cache valid for 7 days and same version
            return toolVersion.equals(currentVersion) &&
                   (System.currentTimeMillis() - timestamp) < 7 * 24 * 60 * 60 * 1000;
        }
    }
    
    /**
     * Enrich tool metadata
     */
    public CompletableFuture<EnrichmentResult> enrichTool(ToolDefinition tool) {
        return CompletableFuture.supplyAsync(() -> {
            // Check cache
            String cacheKey = tool.getId();
            String version = (String) tool.getExistingMetadata().getOrDefault("version", "1.0");
            
            EnrichmentCache cached = enrichmentCache.get(cacheKey);
            if (cached != null && cached.isValid(version)) {
                return cached.result;
            }
            
            // Generate enriched metadata
            String enhancedDescription = generateEnhancedDescription(tool);
            List<String> categories = generateCategories(tool);
            List<String> keywords = extractKeywords(tool, enhancedDescription);
            List<String> examples = generateExamples(tool);
            Map<String, Object> capabilities = analyzeCapabilities(tool);
            Map<String, Object> limitations = analyzeLimitations(tool);
            Map<String, Object> useCases = generateUseCases(tool);
            
            // Calculate confidence based on available information
            double confidence = calculateEnrichmentConfidence(tool);
            
            EnrichmentResult result = new EnrichmentResult(
                enhancedDescription, categories, keywords, examples,
                capabilities, limitations, useCases, confidence
            );
            
            // Cache result
            enrichmentCache.put(cacheKey, new EnrichmentCache(result, version));
            
            return result;
        });
    }
    
    /**
     * Batch enrich multiple tools
     */
    public CompletableFuture<Map<String, EnrichmentResult>> enrichToolsBatch(
            List<ToolDefinition> tools) {
        Map<String, CompletableFuture<EnrichmentResult>> futures = new HashMap<>();
        
        for (ToolDefinition tool : tools) {
            futures.put(tool.getId(), enrichTool(tool));
        }
        
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                Map<String, EnrichmentResult> results = new HashMap<>();
                futures.forEach((id, future) -> results.put(id, future.join()));
                return results;
            });
    }
    
    /**
     * Generate enhanced description
     */
    private String generateEnhancedDescription(ToolDefinition tool) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a comprehensive, SEO-friendly description for this tool:\n\n");
        prompt.append("Tool Name: ").append(tool.getName()).append("\n");
        prompt.append("Basic Description: ").append(tool.getBasicDescription()).append("\n");
        
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            prompt.append("Input Schema: ").append(describeSchema(tool.getInputSchema())).append("\n");
        }
        
        if (tool.getOutputSchema() != null && !tool.getOutputSchema().isEmpty()) {
            prompt.append("Output Schema: ").append(describeSchema(tool.getOutputSchema())).append("\n");
        }
        
        prompt.append("\nProvide a detailed description that includes:\n");
        prompt.append("1. What the tool does\n");
        prompt.append("2. When to use it\n");
        prompt.append("3. Key benefits\n");
        prompt.append("4. Technical details\n");
        
        String response = aiClient.generateContent("gemini-1.5-pro", prompt.toString(),
            Map.of("temperature", 0.7, "maxOutputTokens", 500));
        
        // Post-process to ensure quality
        return postProcessDescription(response, tool);
    }
    
    /**
     * Generate categories
     */
    private List<String> generateCategories(ToolDefinition tool) {
        // Start with pattern-based categorization
        List<String> categories = new ArrayList<>();
        
        String fullText = tool.getName() + " " + tool.getBasicDescription();
        
        // Check against pattern library
        for (Map.Entry<String, Pattern> entry : patternLibrary.entrySet()) {
            if (entry.getValue().matcher(fullText.toLowerCase()).find()) {
                categories.add(entry.getKey());
            }
        }
        
        // Use AI for additional categories
        String prompt = String.format(
            "Categorize this tool into 3-5 relevant categories:\n" +
            "Tool: %s\n" +
            "Description: %s\n" +
            "Current categories: %s\n" +
            "Add any missing important categories.",
            tool.getName(), tool.getBasicDescription(), categories
        );
        
        String response = aiClient.generateContent("gemini-1.5-flash", prompt,
            Map.of("temperature", 0.3, "maxOutputTokens", 100));
        
        // Parse AI response
        categories.addAll(parseCategories(response));
        
        // Normalize and deduplicate
        return categories.stream()
            .map(String::toLowerCase)
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Extract keywords
     */
    private List<String> extractKeywords(ToolDefinition tool, String enhancedDescription) {
        Set<String> keywords = new HashSet<>();
        
        // Extract from tool name
        keywords.addAll(Arrays.asList(tool.getName().toLowerCase().split("[_\\-\\s]+")));
        
        // Extract from schemas
        if (tool.getInputSchema() != null) {
            extractSchemaKeywords(tool.getInputSchema(), keywords);
        }
        
        if (tool.getOutputSchema() != null) {
            extractSchemaKeywords(tool.getOutputSchema(), keywords);
        }
        
        // Use AI for semantic keywords
        String prompt = String.format(
            "Extract 10-15 relevant keywords and phrases for this tool:\n" +
            "Description: %s\n" +
            "Focus on: action words, technical terms, use cases, and benefits.",
            enhancedDescription
        );
        
        String response = aiClient.generateContent("gemini-1.5-flash", prompt,
            Map.of("temperature", 0.5, "maxOutputTokens", 200));
        
        keywords.addAll(parseKeywords(response));
        
        // Filter out common words
        return keywords.stream()
            .filter(k -> k.length() > 2 && !isCommonWord(k))
            .collect(Collectors.toList());
    }
    
    /**
     * Generate examples
     */
    private List<String> generateExamples(ToolDefinition tool) {
        List<String> examples = new ArrayList<>();
        
        // If existing examples, use them
        if (tool.getExistingMetadata().containsKey("examples")) {
            examples.addAll((List<String>) tool.getExistingMetadata().get("examples"));
        }
        
        // Generate additional examples using AI
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate 3 practical usage examples for this tool:\n\n");
        prompt.append("Tool: ").append(tool.getName()).append("\n");
        prompt.append("Description: ").append(tool.getBasicDescription()).append("\n");
        
        if (tool.getInputSchema() != null) {
            prompt.append("Inputs: ").append(describeSchema(tool.getInputSchema())).append("\n");
        }
        
        prompt.append("\nProvide examples in this format:\n");
        prompt.append("1. [Scenario]: [How to use the tool]\n");
        prompt.append("Make examples specific and actionable.");
        
        String response = aiClient.generateContent("gemini-1.5-pro", prompt.toString(),
            Map.of("temperature", 0.8, "maxOutputTokens", 400));
        
        examples.addAll(parseExamples(response));
        
        return examples;
    }
    
    /**
     * Analyze capabilities
     */
    private Map<String, Object> analyzeCapabilities(ToolDefinition tool) {
        Map<String, Object> capabilities = new HashMap<>();
        
        // Analyze input/output capabilities
        if (tool.getInputSchema() != null) {
            Map<String, Object> inputCaps = analyzeSchemaCapabilities(tool.getInputSchema());
            capabilities.put("input_types", inputCaps.get("types"));
            capabilities.put("max_input_size", inputCaps.get("max_size"));
        }
        
        if (tool.getOutputSchema() != null) {
            Map<String, Object> outputCaps = analyzeSchemaCapabilities(tool.getOutputSchema());
            capabilities.put("output_types", outputCaps.get("types"));
            capabilities.put("output_formats", outputCaps.get("formats"));
        }
        
        // Analyze from implementation if available
        if (tool.getImplementation() != null) {
            capabilities.putAll(analyzeImplementationCapabilities(tool.getImplementation()));
        }
        
        // Use AI for high-level capabilities
        String prompt = String.format(
            "List the key capabilities of this tool:\n" +
            "Tool: %s\n" +
            "Description: %s\n" +
            "Format: capability_name: description",
            tool.getName(), tool.getBasicDescription()
        );
        
        String response = aiClient.generateContent("gemini-1.5-flash", prompt,
            Map.of("temperature", 0.4, "maxOutputTokens", 300));
        
        capabilities.putAll(parseCapabilities(response));
        
        return capabilities;
    }
    
    /**
     * Analyze limitations
     */
    private Map<String, Object> analyzeLimitations(ToolDefinition tool) {
        Map<String, Object> limitations = new HashMap<>();
        
        // Schema-based limitations
        if (tool.getInputSchema() != null) {
            List<String> required = (List<String>) tool.getInputSchema().get("required");
            if (required != null && !required.isEmpty()) {
                limitations.put("required_inputs", required);
            }
            
            // Check for size limitations
            Map<String, Object> properties = (Map<String, Object>) tool.getInputSchema().get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> prop : properties.entrySet()) {
                    Map<String, Object> propDef = (Map<String, Object>) prop.getValue();
                    if (propDef.containsKey("maxLength")) {
                        limitations.put("max_" + prop.getKey() + "_length", propDef.get("maxLength"));
                    }
                }
            }
        }
        
        // Use AI for other limitations
        String prompt = String.format(
            "Identify limitations and constraints of this tool:\n" +
            "Tool: %s\n" +
            "Description: %s\n" +
            "Consider: performance limits, data restrictions, use case boundaries",
            tool.getName(), tool.getBasicDescription()
        );
        
        String response = aiClient.generateContent("gemini-1.5-flash", prompt,
            Map.of("temperature", 0.5, "maxOutputTokens", 300));
        
        limitations.putAll(parseLimitations(response));
        
        return limitations;
    }
    
    /**
     * Generate use cases
     */
    private Map<String, Object> generateUseCases(ToolDefinition tool) {
        Map<String, Object> useCases = new HashMap<>();
        
        String prompt = String.format(
            "Generate specific use cases for this tool:\n" +
            "Tool: %s\n" +
            "Description: %s\n\n" +
            "Provide 4-6 use cases with:\n" +
            "- Industry/Domain\n" +
            "- Specific scenario\n" +
            "- Expected benefit\n",
            tool.getName(), tool.getBasicDescription()
        );
        
        String response = aiClient.generateContent("gemini-1.5-pro", prompt,
            Map.of("temperature", 0.7, "maxOutputTokens", 500));
        
        // Parse use cases
        List<Map<String, String>> parsedUseCases = parseUseCases(response);
        
        // Organize by domain
        for (Map<String, String> useCase : parsedUseCases) {
            String domain = useCase.get("domain");
            List<Map<String, String>> domainCases = (List<Map<String, String>>) 
                useCases.computeIfAbsent(domain, k -> new ArrayList<>());
            domainCases.add(useCase);
        }
        
        return useCases;
    }
    
    /**
     * Helper methods
     */
    
    private String describeSchema(Map<String, Object> schema) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return "No schema defined";
        }
        
        List<String> descriptions = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> prop = (Map<String, Object>) entry.getValue();
            String type = (String) prop.get("type");
            String desc = entry.getKey() + " (" + type + ")";
            
            if (prop.containsKey("description")) {
                desc += ": " + prop.get("description");
            }
            
            descriptions.add(desc);
        }
        
        return String.join(", ", descriptions);
    }
    
    private void extractSchemaKeywords(Map<String, Object> schema, Set<String> keywords) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties != null) {
            keywords.addAll(properties.keySet());
            
            // Extract from descriptions
            for (Object prop : properties.values()) {
                Map<String, Object> propDef = (Map<String, Object>) prop;
                if (propDef.containsKey("description")) {
                    String desc = (String) propDef.get("description");
                    keywords.addAll(Arrays.asList(desc.toLowerCase().split("\\s+")));
                }
            }
        }
    }
    
    private Map<String, Object> analyzeSchemaCapabilities(Map<String, Object> schema) {
        Map<String, Object> capabilities = new HashMap<>();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        
        if (properties != null) {
            // Collect types
            Set<String> types = properties.values().stream()
                .map(prop -> (String) ((Map<String, Object>) prop).get("type"))
                .collect(Collectors.toSet());
            capabilities.put("types", types);
            
            // Check for file handling
            boolean handlesFiles = types.contains("string") && properties.keySet().stream()
                .anyMatch(key -> key.toLowerCase().contains("file") || 
                                 key.toLowerCase().contains("path"));
            capabilities.put("handles_files", handlesFiles);
            
            // Check for batch processing
            boolean handlesBatch = types.contains("array");
            capabilities.put("batch_processing", handlesBatch);
        }
        
        return capabilities;
    }
    
    private Map<String, Object> analyzeImplementationCapabilities(String implementation) {
        Map<String, Object> caps = new HashMap<>();
        
        // Simple pattern matching for capabilities
        if (implementation.contains("async") || implementation.contains("await")) {
            caps.put("async_support", true);
        }
        
        if (implementation.contains("stream") || implementation.contains("Stream")) {
            caps.put("streaming_support", true);
        }
        
        if (implementation.contains("parallel") || implementation.contains("concurrent")) {
            caps.put("parallel_processing", true);
        }
        
        if (implementation.contains("cache") || implementation.contains("Cache")) {
            caps.put("caching_support", true);
        }
        
        return caps;
    }
    
    private String postProcessDescription(String description, ToolDefinition tool) {
        // Ensure description includes tool name
        if (!description.toLowerCase().contains(tool.getName().toLowerCase())) {
            description = tool.getName() + " is a tool that " + 
                         description.substring(0, 1).toLowerCase() + description.substring(1);
        }
        
        // Ensure proper formatting
        description = description.trim();
        if (!description.endsWith(".")) {
            description += ".";
        }
        
        return description;
    }
    
    private List<String> parseCategories(String response) {
        // Parse AI response for categories
        List<String> categories = new ArrayList<>();
        String[] lines = response.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("-") || line.startsWith("*") || line.matches("\\d+\\..*")) {
                String category = line.replaceAll("^[-*\\d.]+\\s*", "").trim();
                if (!category.isEmpty()) {
                    categories.add(category);
                }
            }
        }
        
        return categories;
    }
    
    private List<String> parseKeywords(String response) {
        // Extract keywords from AI response
        Set<String> keywords = new HashSet<>();
        
        // Split by common delimiters
        String[] parts = response.split("[,;\\n]");
        
        for (String part : parts) {
            String keyword = part.trim().toLowerCase()
                .replaceAll("^[-*\\d.]+\\s*", "")
                .replaceAll("[^a-z0-9\\s-]", "");
            
            if (!keyword.isEmpty() && keyword.length() > 2) {
                keywords.add(keyword);
            }
        }
        
        return new ArrayList<>(keywords);
    }
    
    private List<String> parseExamples(String response) {
        List<String> examples = new ArrayList<>();
        String[] lines = response.split("\n");
        
        StringBuilder currentExample = new StringBuilder();
        boolean inExample = false;
        
        for (String line : lines) {
            if (line.matches("^\\d+\\..*") || line.startsWith("Example")) {
                if (currentExample.length() > 0) {
                    examples.add(currentExample.toString().trim());
                    currentExample = new StringBuilder();
                }
                inExample = true;
            }
            
            if (inExample && !line.trim().isEmpty()) {
                currentExample.append(line).append(" ");
            }
        }
        
        if (currentExample.length() > 0) {
            examples.add(currentExample.toString().trim());
        }
        
        return examples;
    }
    
    private Map<String, Object> parseCapabilities(String response) {
        Map<String, Object> capabilities = new HashMap<>();
        String[] lines = response.split("\n");
        
        for (String line : lines) {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim().toLowerCase().replaceAll("\\s+", "_");
                    String value = parts[1].trim();
                    capabilities.put(key, value);
                }
            }
        }
        
        return capabilities;
    }
    
    private Map<String, Object> parseLimitations(String response) {
        Map<String, Object> limitations = new HashMap<>();
        String[] lines = response.split("\n");
        
        for (String line : lines) {
            line = line.trim();
            if (!line.isEmpty() && (line.startsWith("-") || line.contains(":"))) {
                String limitation = line.replaceAll("^[-*]\\s*", "");
                
                // Try to extract structured information
                if (limitation.toLowerCase().contains("max")) {
                    limitations.put("constraint_type", "maximum_limit");
                } else if (limitation.toLowerCase().contains("not support")) {
                    limitations.put("unsupported_features", limitation);
                } else if (limitation.toLowerCase().contains("require")) {
                    limitations.put("requirements", limitation);
                }
                
                // Add as general limitation
                List<String> generalLimitations = (List<String>) limitations
                    .computeIfAbsent("general", k -> new ArrayList<>());
                generalLimitations.add(limitation);
            }
        }
        
        return limitations;
    }
    
    private List<Map<String, String>> parseUseCases(String response) {
        List<Map<String, String>> useCases = new ArrayList<>();
        String[] sections = response.split("\n\n");
        
        for (String section : sections) {
            Map<String, String> useCase = new HashMap<>();
            String[] lines = section.split("\n");
            
            for (String line : lines) {
                if (line.toLowerCase().contains("industry") || line.toLowerCase().contains("domain")) {
                    useCase.put("domain", extractValue(line));
                } else if (line.toLowerCase().contains("scenario")) {
                    useCase.put("scenario", extractValue(line));
                } else if (line.toLowerCase().contains("benefit")) {
                    useCase.put("benefit", extractValue(line));
                }
            }
            
            if (!useCase.isEmpty()) {
                useCases.add(useCase);
            }
        }
        
        return useCases;
    }
    
    private String extractValue(String line) {
        if (line.contains(":")) {
            return line.substring(line.indexOf(":") + 1).trim();
        }
        return line.trim();
    }
    
    private boolean isCommonWord(String word) {
        Set<String> commonWords = Set.of(
            "the", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "are", "was", "were",
            "been", "be", "have", "has", "had", "do", "does", "did",
            "will", "would", "could", "should", "may", "might", "must",
            "shall", "can", "this", "that", "these", "those"
        );
        
        return commonWords.contains(word.toLowerCase());
    }
    
    private double calculateEnrichmentConfidence(ToolDefinition tool) {
        double confidence = 0.5; // Base confidence
        
        // Increase confidence based on available information
        if (tool.getBasicDescription() != null && tool.getBasicDescription().length() > 50) {
            confidence += 0.1;
        }
        
        if (tool.getInputSchema() != null && !tool.getInputSchema().isEmpty()) {
            confidence += 0.15;
        }
        
        if (tool.getOutputSchema() != null && !tool.getOutputSchema().isEmpty()) {
            confidence += 0.15;
        }
        
        if (tool.getImplementation() != null) {
            confidence += 0.1;
        }
        
        if (tool.getExistingMetadata().containsKey("examples")) {
            confidence += 0.1;
        }
        
        return Math.min(1.0, confidence);
    }
    
    private void initializePatternLibrary() {
        // Common patterns for categorization
        patternLibrary.put("data_processing", 
            Pattern.compile("process|transform|convert|parse|extract|analyze"));
        patternLibrary.put("integration", 
            Pattern.compile("api|connect|integrate|sync|import|export"));
        patternLibrary.put("automation", 
            Pattern.compile("automate|schedule|trigger|workflow|pipeline"));
        patternLibrary.put("analytics", 
            Pattern.compile("analyze|report|metric|statistic|insight|dashboard"));
        patternLibrary.put("security", 
            Pattern.compile("security|encrypt|auth|permission|access|credential"));
        patternLibrary.put("optimization", 
            Pattern.compile("optimize|improve|enhance|performance|speed|efficient"));
        patternLibrary.put("validation", 
            Pattern.compile("validate|check|verify|test|ensure|confirm"));
        patternLibrary.put("communication", 
            Pattern.compile("send|notify|email|message|alert|communicate"));
        patternLibrary.put("storage", 
            Pattern.compile("store|save|database|cache|persist|archive"));
        patternLibrary.put("search", 
            Pattern.compile("search|find|query|lookup|discover|locate"));
    }
    
    /**
     * Create enriched tool from definition and enrichment
     */
    public EnrichedTool createEnrichedTool(ToolDefinition definition, 
                                         EnrichmentResult enrichment,
                                         TenantContext tenantContext) {
        // Merge all metadata
        Map<String, Object> fullMetadata = new HashMap<>(definition.getExistingMetadata());
        fullMetadata.put("capabilities", enrichment.getCapabilities());
        fullMetadata.put("limitations", enrichment.getLimitations());
        fullMetadata.put("use_cases", enrichment.getUseCases());
        fullMetadata.put("enrichment_confidence", enrichment.getConfidence());
        fullMetadata.put("enrichment_timestamp", System.currentTimeMillis());
        
        return new EnrichedTool(
            definition.getId(),
            definition.getName(),
            enrichment.getEnhancedDescription(),
            enrichment.getCategories(),
            definition.getInputSchema(),
            definition.getOutputSchema(),
            enrichment.getKeywords(),
            enrichment.getExamples(),
            fullMetadata,
            tenantContext
        );
    }
}