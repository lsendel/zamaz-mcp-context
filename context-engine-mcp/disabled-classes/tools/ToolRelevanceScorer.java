package com.zamaz.adk.tools;

import com.google.cloud.aiplatform.v1.*;
import com.zamaz.adk.tools.ToolEmbeddingIndex.*;
import com.zamaz.adk.workflow.WorkflowEngine.VertexAIClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tool Relevance Scorer - Advanced scoring engine for tool selection
 * Combines multiple signals to determine the best tools for a given task
 */
public class ToolRelevanceScorer {
    private final VertexAIClient aiClient;
    private final Map<String, UsageStatistics> usageStats = new ConcurrentHashMap<>();
    private final Map<String, ContextualHistory> contextHistory = new ConcurrentHashMap<>();
    private final Map<String, ToolRelationship> toolRelationships = new ConcurrentHashMap<>();
    
    // Scoring weights (configurable)
    private final ScoringWeights weights;
    
    public ToolRelevanceScorer(VertexAIClient aiClient) {
        this(aiClient, ScoringWeights.defaultWeights());
    }
    
    public ToolRelevanceScorer(VertexAIClient aiClient, ScoringWeights weights) {
        this.aiClient = aiClient;
        this.weights = weights;
        
        // Load historical data
        loadHistoricalData();
    }
    
    /**
     * Scoring weights configuration
     */
    public static class ScoringWeights {
        private final double semanticSimilarity;
        private final double contextualRelevance;
        private final double historicalSuccess;
        private final double coOccurrence;
        private final double userPreference;
        private final double taskComplexity;
        private final double performanceMetrics;
        
        public ScoringWeights(double semanticSimilarity, double contextualRelevance,
                            double historicalSuccess, double coOccurrence,
                            double userPreference, double taskComplexity,
                            double performanceMetrics) {
            this.semanticSimilarity = semanticSimilarity;
            this.contextualRelevance = contextualRelevance;
            this.historicalSuccess = historicalSuccess;
            this.coOccurrence = coOccurrence;
            this.userPreference = userPreference;
            this.taskComplexity = taskComplexity;
            this.performanceMetrics = performanceMetrics;
        }
        
        public static ScoringWeights defaultWeights() {
            return new ScoringWeights(0.3, 0.2, 0.15, 0.1, 0.1, 0.1, 0.05);
        }
        
        public static ScoringWeights precisionWeights() {
            return new ScoringWeights(0.4, 0.25, 0.2, 0.05, 0.05, 0.03, 0.02);
        }
        
        public static ScoringWeights explorationWeights() {
            return new ScoringWeights(0.2, 0.15, 0.1, 0.2, 0.15, 0.15, 0.05);
        }
        
        // Getters
        public double getSemanticSimilarity() { return semanticSimilarity; }
        public double getContextualRelevance() { return contextualRelevance; }
        public double getHistoricalSuccess() { return historicalSuccess; }
        public double getCoOccurrence() { return coOccurrence; }
        public double getUserPreference() { return userPreference; }
        public double getTaskComplexity() { return taskComplexity; }
        public double getPerformanceMetrics() { return performanceMetrics; }
    }
    
    /**
     * Enhanced scoring context
     */
    public static class ScoringContext {
        private final String query;
        private final String userId;
        private final String workflowId;
        private final Map<String, Object> currentState;
        private final List<String> previousTools;
        private final TaskCharacteristics taskCharacteristics;
        private final Map<String, Object> constraints;
        
        public ScoringContext(String query, String userId, String workflowId,
                            Map<String, Object> currentState, List<String> previousTools,
                            TaskCharacteristics taskCharacteristics,
                            Map<String, Object> constraints) {
            this.query = query;
            this.userId = userId;
            this.workflowId = workflowId;
            this.currentState = currentState;
            this.previousTools = previousTools;
            this.taskCharacteristics = taskCharacteristics;
            this.constraints = constraints;
        }
        
        // Getters
        public String getQuery() { return query; }
        public String getUserId() { return userId; }
        public String getWorkflowId() { return workflowId; }
        public Map<String, Object> getCurrentState() { return currentState; }
        public List<String> getPreviousTools() { return previousTools; }
        public TaskCharacteristics getTaskCharacteristics() { return taskCharacteristics; }
        public Map<String, Object> getConstraints() { return constraints; }
    }
    
    /**
     * Task characteristics for complexity scoring
     */
    public static class TaskCharacteristics {
        private final ComplexityLevel complexity;
        private final List<String> requiredCapabilities;
        private final Map<String, Object> dataRequirements;
        private final boolean requiresExternalAPI;
        private final boolean requiresStateManagement;
        private final double estimatedDuration;
        
        public enum ComplexityLevel {
            SIMPLE(0.2), MODERATE(0.5), COMPLEX(0.8), EXPERT(1.0);
            
            private final double score;
            
            ComplexityLevel(double score) {
                this.score = score;
            }
            
            public double getScore() { return score; }
        }
        
        public TaskCharacteristics(ComplexityLevel complexity,
                                 List<String> requiredCapabilities,
                                 Map<String, Object> dataRequirements,
                                 boolean requiresExternalAPI,
                                 boolean requiresStateManagement,
                                 double estimatedDuration) {
            this.complexity = complexity;
            this.requiredCapabilities = requiredCapabilities;
            this.dataRequirements = dataRequirements;
            this.requiresExternalAPI = requiresExternalAPI;
            this.requiresStateManagement = requiresStateManagement;
            this.estimatedDuration = estimatedDuration;
        }
        
        // Getters
        public ComplexityLevel getComplexity() { return complexity; }
        public List<String> getRequiredCapabilities() { return requiredCapabilities; }
        public Map<String, Object> getDataRequirements() { return dataRequirements; }
        public boolean isRequiresExternalAPI() { return requiresExternalAPI; }
        public boolean isRequiresStateManagement() { return requiresStateManagement; }
        public double getEstimatedDuration() { return estimatedDuration; }
    }
    
    /**
     * Enhanced tool match with detailed scoring
     */
    public static class EnhancedToolMatch extends ToolMatch {
        private final Map<String, Double> signalScores;
        private final List<String> strengths;
        private final List<String> weaknesses;
        private final double confidenceScore;
        private final Map<String, Object> recommendations;
        
        public EnhancedToolMatch(EnrichedTool tool, double similarityScore,
                               double relevanceScore, Map<String, Double> scoreBreakdown,
                               String explanation, Map<String, Double> signalScores,
                               List<String> strengths, List<String> weaknesses,
                               double confidenceScore, Map<String, Object> recommendations) {
            super(tool, similarityScore, relevanceScore, scoreBreakdown, explanation);
            this.signalScores = signalScores;
            this.strengths = strengths;
            this.weaknesses = weaknesses;
            this.confidenceScore = confidenceScore;
            this.recommendations = recommendations;
        }
        
        // Getters
        public Map<String, Double> getSignalScores() { return signalScores; }
        public List<String> getStrengths() { return strengths; }
        public List<String> getWeaknesses() { return weaknesses; }
        public double getConfidenceScore() { return confidenceScore; }
        public Map<String, Object> getRecommendations() { return recommendations; }
    }
    
    /**
     * Usage statistics for tools
     */
    private static class UsageStatistics {
        private final String toolId;
        private int totalUses = 0;
        private int successfulUses = 0;
        private double averageExecutionTime = 0;
        private final Map<String, Integer> errorCounts = new HashMap<>();
        private final Map<String, Integer> userUsageCounts = new HashMap<>();
        private final List<Double> recentScores = new ArrayList<>();
        
        public UsageStatistics(String toolId) {
            this.toolId = toolId;
        }
        
        public void recordUsage(String userId, boolean success, double executionTime,
                              String error, double satisfactionScore) {
            totalUses++;
            if (success) {
                successfulUses++;
            } else if (error != null) {
                errorCounts.merge(error, 1, Integer::sum);
            }
            
            // Update average execution time
            averageExecutionTime = (averageExecutionTime * (totalUses - 1) + executionTime) / totalUses;
            
            // Track user usage
            userUsageCounts.merge(userId, 1, Integer::sum);
            
            // Keep last 100 satisfaction scores
            recentScores.add(satisfactionScore);
            if (recentScores.size() > 100) {
                recentScores.remove(0);
            }
        }
        
        public double getSuccessRate() {
            return totalUses > 0 ? (double) successfulUses / totalUses : 0.5;
        }
        
        public double getRecentSatisfaction() {
            return recentScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.7);
        }
        
        public int getUserCount() {
            return userUsageCounts.size();
        }
        
        public double getReliabilityScore() {
            if (totalUses < 10) return 0.5; // Not enough data
            
            double successRate = getSuccessRate();
            double errorDiversity = 1.0 - (errorCounts.size() * 0.1); // Penalize many error types
            double performanceScore = averageExecutionTime < 1000 ? 1.0 : 
                                    averageExecutionTime < 5000 ? 0.8 : 0.5;
            
            return (successRate * 0.5 + errorDiversity * 0.3 + performanceScore * 0.2);
        }
    }
    
    /**
     * Contextual history for pattern detection
     */
    private static class ContextualHistory {
        private final Map<String, List<ToolSequence>> workflowPatterns = new HashMap<>();
        private final Map<String, Map<String, Integer>> contextToolFrequency = new HashMap<>();
        
        public void recordToolUsage(String workflowId, String contextKey, 
                                  String toolId, boolean success) {
            // Record in workflow patterns
            workflowPatterns.computeIfAbsent(workflowId, k -> new ArrayList<>())
                .add(new ToolSequence(toolId, success, System.currentTimeMillis()));
            
            // Record context-tool frequency
            contextToolFrequency.computeIfAbsent(contextKey, k -> new HashMap<>())
                .merge(toolId, 1, Integer::sum);
        }
        
        public List<String> getPredictedNextTools(String workflowId, String lastTool) {
            List<ToolSequence> patterns = workflowPatterns.get(workflowId);
            if (patterns == null || patterns.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Find sequences where lastTool appeared and what followed
            Map<String, Integer> nextToolCounts = new HashMap<>();
            
            for (int i = 0; i < patterns.size() - 1; i++) {
                if (patterns.get(i).toolId.equals(lastTool) && patterns.get(i).success) {
                    String nextTool = patterns.get(i + 1).toolId;
                    nextToolCounts.merge(nextTool, 1, Integer::sum);
                }
            }
            
            // Return top 3 most frequent next tools
            return nextToolCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        }
        
        public double getContextRelevance(String contextKey, String toolId) {
            Map<String, Integer> toolFrequency = contextToolFrequency.get(contextKey);
            if (toolFrequency == null) {
                return 0.5; // No history
            }
            
            int toolCount = toolFrequency.getOrDefault(toolId, 0);
            int totalCount = toolFrequency.values().stream().mapToInt(Integer::intValue).sum();
            
            return totalCount > 0 ? (double) toolCount / totalCount : 0.0;
        }
    }
    
    /**
     * Tool sequence for pattern detection
     */
    private static class ToolSequence {
        private final String toolId;
        private final boolean success;
        private final long timestamp;
        
        public ToolSequence(String toolId, boolean success, long timestamp) {
            this.toolId = toolId;
            this.success = success;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Tool relationship graph
     */
    private static class ToolRelationship {
        private final Map<String, Map<String, Double>> coOccurrenceMatrix = new HashMap<>();
        private final Map<String, Set<String>> complementaryTools = new HashMap<>();
        private final Map<String, Set<String>> substitutableTools = new HashMap<>();
        
        public void recordCoOccurrence(String tool1, String tool2, boolean sameWorkflow) {
            double weight = sameWorkflow ? 1.0 : 0.3;
            
            coOccurrenceMatrix.computeIfAbsent(tool1, k -> new HashMap<>())
                .merge(tool2, weight, Double::sum);
            
            coOccurrenceMatrix.computeIfAbsent(tool2, k -> new HashMap<>())
                .merge(tool1, weight, Double::sum);
        }
        
        public void markComplementary(String tool1, String tool2) {
            complementaryTools.computeIfAbsent(tool1, k -> new HashSet<>()).add(tool2);
            complementaryTools.computeIfAbsent(tool2, k -> new HashSet<>()).add(tool1);
        }
        
        public void markSubstitutable(String tool1, String tool2) {
            substitutableTools.computeIfAbsent(tool1, k -> new HashSet<>()).add(tool2);
            substitutableTools.computeIfAbsent(tool2, k -> new HashSet<>()).add(tool1);
        }
        
        public double getCoOccurrenceScore(String tool1, String tool2) {
            return coOccurrenceMatrix.getOrDefault(tool1, Collections.emptyMap())
                .getOrDefault(tool2, 0.0);
        }
        
        public boolean areComplementary(String tool1, String tool2) {
            return complementaryTools.getOrDefault(tool1, Collections.emptySet())
                .contains(tool2);
        }
        
        public Set<String> getSubstitutes(String tool) {
            return substitutableTools.getOrDefault(tool, Collections.emptySet());
        }
    }
    
    /**
     * Score tools with enhanced relevance calculation
     */
    public List<EnhancedToolMatch> scoreTools(List<ToolMatch> candidates,
                                             ScoringContext context) {
        List<EnhancedToolMatch> enhancedMatches = new ArrayList<>();
        
        // Analyze task characteristics if not provided
        TaskCharacteristics taskChar = context.getTaskCharacteristics() != null ?
            context.getTaskCharacteristics() : analyzeTask(context.getQuery());
        
        for (ToolMatch candidate : candidates) {
            Map<String, Double> signalScores = calculateSignalScores(
                candidate, context, taskChar);
            
            // Calculate weighted relevance score
            double weightedScore = calculateWeightedScore(signalScores);
            
            // Analyze strengths and weaknesses
            List<String> strengths = identifyStrengths(candidate, signalScores);
            List<String> weaknesses = identifyWeaknesses(candidate, signalScores);
            
            // Calculate confidence
            double confidence = calculateConfidence(signalScores, candidate);
            
            // Generate recommendations
            Map<String, Object> recommendations = generateRecommendations(
                candidate, context, signalScores);
            
            // Create enhanced explanation
            String enhancedExplanation = generateEnhancedExplanation(
                candidate, signalScores, strengths, weaknesses);
            
            enhancedMatches.add(new EnhancedToolMatch(
                candidate.getTool(),
                candidate.getSimilarityScore(),
                weightedScore,
                candidate.getScoreBreakdown(),
                enhancedExplanation,
                signalScores,
                strengths,
                weaknesses,
                confidence,
                recommendations
            ));
        }
        
        // Sort by weighted score
        enhancedMatches.sort((a, b) -> 
            Double.compare(b.getRelevanceScore(), a.getRelevanceScore()));
        
        // Apply post-processing rules
        enhancedMatches = applyPostProcessingRules(enhancedMatches, context);
        
        return enhancedMatches;
    }
    
    /**
     * Calculate all scoring signals
     */
    private Map<String, Double> calculateSignalScores(ToolMatch candidate,
                                                    ScoringContext context,
                                                    TaskCharacteristics taskChar) {
        Map<String, Double> scores = new HashMap<>();
        
        // 1. Semantic similarity (from base match)
        scores.put("semantic_similarity", candidate.getSimilarityScore());
        
        // 2. Contextual relevance
        double contextScore = calculateContextualRelevance(candidate.getTool(), context);
        scores.put("contextual_relevance", contextScore);
        
        // 3. Historical success
        double historyScore = calculateHistoricalSuccess(candidate.getTool(), context);
        scores.put("historical_success", historyScore);
        
        // 4. Co-occurrence score
        double coOccurrenceScore = calculateCoOccurrence(candidate.getTool(), context);
        scores.put("co_occurrence", coOccurrenceScore);
        
        // 5. User preference
        double preferenceScore = calculateUserPreference(candidate.getTool(), context);
        scores.put("user_preference", preferenceScore);
        
        // 6. Task complexity match
        double complexityScore = calculateTaskComplexityMatch(candidate.getTool(), taskChar);
        scores.put("task_complexity", complexityScore);
        
        // 7. Performance metrics
        double performanceScore = calculatePerformanceScore(candidate.getTool());
        scores.put("performance_metrics", performanceScore);
        
        // Additional signals
        scores.put("capability_match", calculateCapabilityMatch(candidate.getTool(), taskChar));
        scores.put("data_compatibility", calculateDataCompatibility(candidate.getTool(), context));
        scores.put("constraint_satisfaction", calculateConstraintSatisfaction(candidate.getTool(), context));
        
        return scores;
    }
    
    /**
     * Calculate contextual relevance
     */
    private double calculateContextualRelevance(EnrichedTool tool, ScoringContext context) {
        String contextKey = generateContextKey(context);
        ContextualHistory history = contextHistory.get(context.getUserId());
        
        if (history == null) {
            return 0.5; // No history
        }
        
        // Base relevance from historical usage in similar contexts
        double baseRelevance = history.getContextRelevance(contextKey, tool.getId());
        
        // Boost if tool was recently used successfully
        if (context.getPreviousTools().contains(tool.getId())) {
            baseRelevance *= 0.8; // Slight penalty for repetition
        }
        
        // Check if tool is predicted as next in sequence
        List<String> predicted = history.getPredictedNextTools(
            context.getWorkflowId(), 
            context.getPreviousTools().isEmpty() ? "" : 
                context.getPreviousTools().get(context.getPreviousTools().size() - 1)
        );
        
        if (predicted.contains(tool.getId())) {
            baseRelevance = Math.min(1.0, baseRelevance + 0.3);
        }
        
        return baseRelevance;
    }
    
    /**
     * Calculate historical success rate
     */
    private double calculateHistoricalSuccess(EnrichedTool tool, ScoringContext context) {
        UsageStatistics stats = usageStats.get(tool.getId());
        if (stats == null) {
            return 0.7; // Default for new tools
        }
        
        // Combine multiple factors
        double successRate = stats.getSuccessRate();
        double satisfaction = stats.getRecentSatisfaction();
        double reliability = stats.getReliabilityScore();
        double userDiversity = Math.min(1.0, stats.getUserCount() / 10.0);
        
        // Weighted combination
        return successRate * 0.4 + satisfaction * 0.3 + 
               reliability * 0.2 + userDiversity * 0.1;
    }
    
    /**
     * Calculate co-occurrence score
     */
    private double calculateCoOccurrence(EnrichedTool tool, ScoringContext context) {
        if (context.getPreviousTools().isEmpty()) {
            return 0.5;
        }
        
        ToolRelationship relationships = toolRelationships.get("global");
        if (relationships == null) {
            return 0.5;
        }
        
        // Average co-occurrence with previous tools
        double totalScore = 0;
        int count = 0;
        
        for (String previousTool : context.getPreviousTools()) {
            double score = relationships.getCoOccurrenceScore(previousTool, tool.getId());
            
            // Boost if tools are complementary
            if (relationships.areComplementary(previousTool, tool.getId())) {
                score = Math.min(1.0, score + 0.3);
            }
            
            totalScore += score;
            count++;
        }
        
        return count > 0 ? totalScore / count : 0.5;
    }
    
    /**
     * Calculate user preference
     */
    private double calculateUserPreference(EnrichedTool tool, ScoringContext context) {
        UsageStatistics stats = usageStats.get(tool.getId());
        if (stats == null || stats.userUsageCounts.get(context.getUserId()) == null) {
            return 0.5; // No user history
        }
        
        // User's personal usage rate
        int userUses = stats.userUsageCounts.get(context.getUserId());
        int totalUserTools = usageStats.values().stream()
            .mapToInt(s -> s.userUsageCounts.getOrDefault(context.getUserId(), 0))
            .sum();
        
        if (totalUserTools == 0) {
            return 0.5;
        }
        
        double usageRate = (double) userUses / totalUserTools;
        
        // Normalize to 0-1 range (assuming most tools have <10% usage)
        return Math.min(1.0, usageRate * 10);
    }
    
    /**
     * Calculate task complexity match
     */
    private double calculateTaskComplexityMatch(EnrichedTool tool, 
                                              TaskCharacteristics taskChar) {
        // Simple tools for simple tasks, complex tools for complex tasks
        double toolComplexity = estimateToolComplexity(tool);
        double taskComplexity = taskChar.getComplexity().getScore();
        
        // Best match when complexities are similar
        double difference = Math.abs(toolComplexity - taskComplexity);
        
        // Convert difference to score (0 difference = 1.0 score)
        return Math.max(0, 1.0 - difference);
    }
    
    /**
     * Calculate performance score
     */
    private double calculatePerformanceScore(EnrichedTool tool) {
        UsageStatistics stats = usageStats.get(tool.getId());
        if (stats == null) {
            return 0.7; // Default
        }
        
        // Performance based on execution time
        double timeScore = stats.averageExecutionTime < 500 ? 1.0 :
                          stats.averageExecutionTime < 2000 ? 0.8 :
                          stats.averageExecutionTime < 5000 ? 0.6 : 0.3;
        
        // Adjust for reliability
        return timeScore * stats.getReliabilityScore();
    }
    
    /**
     * Calculate capability match
     */
    private double calculateCapabilityMatch(EnrichedTool tool, 
                                          TaskCharacteristics taskChar) {
        List<String> required = taskChar.getRequiredCapabilities();
        if (required.isEmpty()) {
            return 1.0; // No specific requirements
        }
        
        // Check tool categories and keywords
        Set<String> toolCapabilities = new HashSet<>();
        toolCapabilities.addAll(tool.getCategories());
        toolCapabilities.addAll(tool.getKeywords());
        
        long matchCount = required.stream()
            .filter(cap -> toolCapabilities.stream()
                .anyMatch(tc -> tc.toLowerCase().contains(cap.toLowerCase())))
            .count();
        
        return (double) matchCount / required.size();
    }
    
    /**
     * Calculate data compatibility
     */
    private double calculateDataCompatibility(EnrichedTool tool, ScoringContext context) {
        // Check if tool can handle the current state data
        Map<String, Object> currentData = context.getCurrentState();
        Map<String, Object> inputSchema = tool.getInputSchema();
        
        if (inputSchema.isEmpty()) {
            return 1.0; // No schema restrictions
        }
        
        // Simple compatibility check
        Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
        if (properties == null) {
            return 1.0;
        }
        
        // Check required fields
        List<String> required = (List<String>) inputSchema.getOrDefault("required", 
            Collections.emptyList());
        
        long matchingFields = required.stream()
            .filter(currentData::containsKey)
            .count();
        
        return required.isEmpty() ? 1.0 : (double) matchingFields / required.size();
    }
    
    /**
     * Calculate constraint satisfaction
     */
    private double calculateConstraintSatisfaction(EnrichedTool tool, 
                                                 ScoringContext context) {
        Map<String, Object> constraints = context.getConstraints();
        if (constraints.isEmpty()) {
            return 1.0;
        }
        
        double score = 1.0;
        
        // Check execution time constraint
        if (constraints.containsKey("max_execution_time")) {
            double maxTime = ((Number) constraints.get("max_execution_time")).doubleValue();
            UsageStatistics stats = usageStats.get(tool.getId());
            if (stats != null && stats.averageExecutionTime > maxTime) {
                score *= 0.5;
            }
        }
        
        // Check API constraint
        if (constraints.containsKey("no_external_api") && 
            (Boolean) constraints.get("no_external_api")) {
            Boolean usesApi = (Boolean) tool.getMetadata().getOrDefault("uses_external_api", false);
            if (usesApi) {
                score *= 0.1;
            }
        }
        
        return score;
    }
    
    /**
     * Calculate weighted score
     */
    private double calculateWeightedScore(Map<String, Double> signalScores) {
        double weightedSum = 0;
        double totalWeight = 0;
        
        // Apply configured weights
        weightedSum += signalScores.getOrDefault("semantic_similarity", 0.0) * 
            weights.getSemanticSimilarity();
        totalWeight += weights.getSemanticSimilarity();
        
        weightedSum += signalScores.getOrDefault("contextual_relevance", 0.0) * 
            weights.getContextualRelevance();
        totalWeight += weights.getContextualRelevance();
        
        weightedSum += signalScores.getOrDefault("historical_success", 0.0) * 
            weights.getHistoricalSuccess();
        totalWeight += weights.getHistoricalSuccess();
        
        weightedSum += signalScores.getOrDefault("co_occurrence", 0.0) * 
            weights.getCoOccurrence();
        totalWeight += weights.getCoOccurrence();
        
        weightedSum += signalScores.getOrDefault("user_preference", 0.0) * 
            weights.getUserPreference();
        totalWeight += weights.getUserPreference();
        
        weightedSum += signalScores.getOrDefault("task_complexity", 0.0) * 
            weights.getTaskComplexity();
        totalWeight += weights.getTaskComplexity();
        
        weightedSum += signalScores.getOrDefault("performance_metrics", 0.0) * 
            weights.getPerformanceMetrics();
        totalWeight += weights.getPerformanceMetrics();
        
        // Additional signals with default weights
        weightedSum += signalScores.getOrDefault("capability_match", 0.0) * 0.15;
        totalWeight += 0.15;
        
        weightedSum += signalScores.getOrDefault("data_compatibility", 0.0) * 0.1;
        totalWeight += 0.1;
        
        weightedSum += signalScores.getOrDefault("constraint_satisfaction", 0.0) * 0.1;
        totalWeight += 0.1;
        
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }
    
    /**
     * Identify tool strengths
     */
    private List<String> identifyStrengths(ToolMatch candidate, 
                                         Map<String, Double> signalScores) {
        List<String> strengths = new ArrayList<>();
        
        if (signalScores.get("semantic_similarity") > 0.8) {
            strengths.add("Excellent semantic match to query");
        }
        
        if (signalScores.get("historical_success") > 0.85) {
            strengths.add("Proven track record of success");
        }
        
        if (signalScores.get("performance_metrics") > 0.9) {
            strengths.add("Fast and reliable execution");
        }
        
        if (signalScores.get("user_preference") > 0.7) {
            strengths.add("Frequently used by you");
        }
        
        if (signalScores.get("capability_match") > 0.9) {
            strengths.add("Perfect capability match");
        }
        
        return strengths;
    }
    
    /**
     * Identify tool weaknesses  
     */
    private List<String> identifyWeaknesses(ToolMatch candidate,
                                          Map<String, Double> signalScores) {
        List<String> weaknesses = new ArrayList<>();
        
        if (signalScores.get("semantic_similarity") < 0.5) {
            weaknesses.add("Lower semantic relevance");
        }
        
        if (signalScores.get("historical_success") < 0.6) {
            weaknesses.add("Mixed success history");
        }
        
        if (signalScores.get("performance_metrics") < 0.5) {
            weaknesses.add("Slower execution time");
        }
        
        if (signalScores.get("data_compatibility") < 0.7) {
            weaknesses.add("May require data transformation");
        }
        
        if (signalScores.get("constraint_satisfaction") < 0.8) {
            weaknesses.add("Some constraints not fully met");
        }
        
        return weaknesses;
    }
    
    /**
     * Calculate confidence score
     */
    private double calculateConfidence(Map<String, Double> signalScores,
                                     ToolMatch candidate) {
        // Confidence based on signal agreement and data availability
        double signalVariance = calculateVariance(signalScores.values());
        double dataCompleteness = signalScores.size() / 10.0; // Expected 10 signals
        
        // High confidence when signals agree (low variance) and data is complete
        double varianceScore = 1.0 - Math.min(1.0, signalVariance * 2);
        
        return (varianceScore * 0.6 + dataCompleteness * 0.4) * 
               candidate.getSimilarityScore();
    }
    
    /**
     * Generate recommendations
     */
    private Map<String, Object> generateRecommendations(ToolMatch candidate,
                                                      ScoringContext context,
                                                      Map<String, Double> signalScores) {
        Map<String, Object> recommendations = new HashMap<>();
        
        // Parameter recommendations
        if (signalScores.get("task_complexity") < 0.7) {
            recommendations.put("adjust_parameters", 
                "Consider adjusting tool parameters for task complexity");
        }
        
        // Alternative tools
        ToolRelationship relationships = toolRelationships.get("global");
        if (relationships != null) {
            Set<String> alternatives = relationships.getSubstitutes(candidate.getTool().getId());
            if (!alternatives.isEmpty()) {
                recommendations.put("alternatives", new ArrayList<>(alternatives));
            }
        }
        
        // Complementary tools
        if (signalScores.get("capability_match") < 0.8) {
            recommendations.put("combine_with", 
                "Consider combining with specialized tools for missing capabilities");
        }
        
        return recommendations;
    }
    
    /**
     * Generate enhanced explanation
     */
    private String generateEnhancedExplanation(ToolMatch candidate,
                                             Map<String, Double> signalScores,
                                             List<String> strengths,
                                             List<String> weaknesses) {
        StringBuilder explanation = new StringBuilder();
        
        explanation.append(candidate.getExplanation()).append(" ");
        
        // Add signal-based insights
        if (signalScores.get("contextual_relevance") > 0.7) {
            explanation.append("Highly relevant in current context. ");
        }
        
        if (signalScores.get("co_occurrence") > 0.6) {
            explanation.append("Works well with previously used tools. ");
        }
        
        // Add strengths summary
        if (!strengths.isEmpty()) {
            explanation.append("Key strengths: ")
                      .append(strengths.get(0));
            if (strengths.size() > 1) {
                explanation.append(" and ").append(strengths.size() - 1)
                          .append(" more. ");
            }
        }
        
        return explanation.toString().trim();
    }
    
    /**
     * Apply post-processing rules
     */
    private List<EnhancedToolMatch> applyPostProcessingRules(
            List<EnhancedToolMatch> matches, ScoringContext context) {
        
        // Rule 1: Boost recently successful tools
        for (EnhancedToolMatch match : matches) {
            if (wasRecentlySuccessful(match.getTool().getId(), context.getUserId())) {
                match.getSignalScores().put("recent_success_boost", 0.1);
                // Recalculate score would happen here
            }
        }
        
        // Rule 2: Ensure diversity in top results
        if (matches.size() > 5) {
            Set<String> seenCategories = new HashSet<>();
            List<EnhancedToolMatch> diverseMatches = new ArrayList<>();
            
            for (EnhancedToolMatch match : matches) {
                boolean isNovel = match.getTool().getCategories().stream()
                    .anyMatch(cat -> !seenCategories.contains(cat));
                
                if (isNovel || diverseMatches.size() < 3) {
                    diverseMatches.add(match);
                    seenCategories.addAll(match.getTool().getCategories());
                }
                
                if (diverseMatches.size() >= 5) {
                    break;
                }
            }
            
            // Add remaining high-scoring tools
            for (EnhancedToolMatch match : matches) {
                if (!diverseMatches.contains(match) && diverseMatches.size() < 10) {
                    diverseMatches.add(match);
                }
            }
            
            return diverseMatches;
        }
        
        return matches;
    }
    
    /**
     * Helper methods
     */
    
    private String generateContextKey(ScoringContext context) {
        // Generate a key representing the context type
        return context.getCurrentState().keySet().stream()
            .sorted()
            .collect(Collectors.joining("_"));
    }
    
    private TaskCharacteristics analyzeTask(String query) {
        // Use AI to analyze task complexity
        String prompt = "Analyze this task and determine its complexity level " +
                       "(SIMPLE/MODERATE/COMPLEX/EXPERT): " + query;
        
        String response = aiClient.generateContent("gemini-1.5-flash", prompt,
            Map.of("temperature", 0.3, "maxOutputTokens", 100));
        
        // Parse response (simplified)
        TaskCharacteristics.ComplexityLevel complexity = 
            response.toLowerCase().contains("simple") ? TaskCharacteristics.ComplexityLevel.SIMPLE :
            response.toLowerCase().contains("moderate") ? TaskCharacteristics.ComplexityLevel.MODERATE :
            response.toLowerCase().contains("expert") ? TaskCharacteristics.ComplexityLevel.EXPERT :
            TaskCharacteristics.ComplexityLevel.COMPLEX;
        
        return new TaskCharacteristics(
            complexity,
            Collections.emptyList(),
            new HashMap<>(),
            false,
            false,
            complexity.getScore() * 5000 // Estimated duration
        );
    }
    
    private double estimateToolComplexity(EnrichedTool tool) {
        // Estimate based on input schema complexity and categories
        Map<String, Object> schema = tool.getInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        
        int fieldCount = properties != null ? properties.size() : 0;
        int categoryCount = tool.getCategories().size();
        
        // More fields and categories = more complex
        double schemaComplexity = Math.min(1.0, fieldCount / 10.0);
        double categoryComplexity = Math.min(1.0, categoryCount / 5.0);
        
        return (schemaComplexity + categoryComplexity) / 2;
    }
    
    private double calculateVariance(Collection<Double> values) {
        if (values.isEmpty()) return 0;
        
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        return values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0);
    }
    
    private boolean wasRecentlySuccessful(String toolId, String userId) {
        UsageStatistics stats = usageStats.get(toolId);
        if (stats == null || stats.recentScores.isEmpty()) {
            return false;
        }
        
        // Check last 5 uses
        int checkCount = Math.min(5, stats.recentScores.size());
        double recentAvg = stats.recentScores.subList(
            stats.recentScores.size() - checkCount,
            stats.recentScores.size()
        ).stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        return recentAvg > 0.8;
    }
    
    /**
     * Load historical data
     */
    private void loadHistoricalData() {
        // In production, load from persistent storage
        // Initialize with empty data structures for now
        toolRelationships.put("global", new ToolRelationship());
    }
    
    /**
     * Record tool usage for learning
     */
    public void recordToolUsage(String toolId, String userId, String workflowId,
                              String contextKey, boolean success, 
                              double executionTime, String error,
                              double satisfactionScore) {
        // Update usage statistics
        usageStats.computeIfAbsent(toolId, k -> new UsageStatistics(k))
            .recordUsage(userId, success, executionTime, error, satisfactionScore);
        
        // Update contextual history
        contextHistory.computeIfAbsent(userId, k -> new ContextualHistory())
            .recordToolUsage(workflowId, contextKey, toolId, success);
    }
    
    /**
     * Record tool relationships
     */
    public void recordToolRelationship(String tool1, String tool2, 
                                     String relationshipType,
                                     boolean sameWorkflow) {
        ToolRelationship relationships = toolRelationships.get("global");
        
        switch (relationshipType) {
            case "co_occurrence":
                relationships.recordCoOccurrence(tool1, tool2, sameWorkflow);
                break;
            case "complementary":
                relationships.markComplementary(tool1, tool2);
                break;
            case "substitutable":
                relationships.markSubstitutable(tool1, tool2);
                break;
        }
    }
}