package com.zamaz.adk.workflow;

import com.google.cloud.aiplatform.v1.*;
import com.zamaz.adk.workflow.WorkflowEngine.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Advanced Conditional Router with AI-powered decision making,
 * backtracking support, and parallel execution capabilities
 */
public class ConditionalRouter {
    private final VertexAIClient aiClient;
    private final Map<String, RoutingHistory> routingHistories = new ConcurrentHashMap<>();
    private final Map<String, BacktrackPoint> backtrackPoints = new ConcurrentHashMap<>();
    
    public ConditionalRouter(VertexAIClient aiClient) {
        this.aiClient = aiClient;
    }
    
    /**
     * Enhanced edge with advanced routing capabilities
     */
    public static class SmartEdge extends Edge {
        private final RoutingStrategy strategy;
        private final Map<String, Object> metadata;
        private final List<RoutingCondition> conditions;
        private double priority = 1.0;
        private boolean allowBacktrack = true;
        
        public enum RoutingStrategy {
            SIMPLE,          // Basic condition evaluation
            AI_ASSISTED,     // Use AI for complex decisions
            PROBABILISTIC,   // Route based on probability
            WEIGHTED,        // Use weighted scoring
            PARALLEL,        // Can execute in parallel
            EXCLUSIVE        // Mutually exclusive with other edges
        }
        
        public SmartEdge(String from, String to, RoutingStrategy strategy) {
            super(from, to, state -> true);
            this.strategy = strategy;
            this.metadata = new HashMap<>();
            this.conditions = new ArrayList<>();
        }
        
        public SmartEdge withCondition(String name, Predicate<State> condition, double weight) {
            conditions.add(new RoutingCondition(name, condition, weight));
            return this;
        }
        
        public SmartEdge withPriority(double priority) {
            this.priority = priority;
            return this;
        }
        
        public SmartEdge withMetadata(String key, Object value) {
            metadata.put(key, value);
            return this;
        }
        
        public SmartEdge disableBacktrack() {
            this.allowBacktrack = false;
            return this;
        }
        
        public double evaluateScore(State state) {
            if (conditions.isEmpty()) {
                return priority;
            }
            
            double totalScore = 0;
            double totalWeight = 0;
            
            for (RoutingCondition condition : conditions) {
                if (condition.getPredicate().test(state)) {
                    totalScore += condition.getWeight();
                }
                totalWeight += condition.getWeight();
            }
            
            return totalWeight > 0 ? (totalScore / totalWeight) * priority : 0;
        }
        
        // Getters
        public RoutingStrategy getStrategy() { return strategy; }
        public Map<String, Object> getMetadata() { return metadata; }
        public List<RoutingCondition> getConditions() { return conditions; }
        public double getPriority() { return priority; }
        public boolean isBacktrackAllowed() { return allowBacktrack; }
    }
    
    /**
     * Routing condition with weight
     */
    public static class RoutingCondition {
        private final String name;
        private final Predicate<State> predicate;
        private final double weight;
        
        public RoutingCondition(String name, Predicate<State> predicate, double weight) {
            this.name = name;
            this.predicate = predicate;
            this.weight = weight;
        }
        
        // Getters
        public String getName() { return name; }
        public Predicate<State> getPredicate() { return predicate; }
        public double getWeight() { return weight; }
    }
    
    /**
     * Routing decision result
     */
    public static class RoutingDecision {
        private final String selectedNode;
        private final double confidence;
        private final String reasoning;
        private final Map<String, Double> alternativeScores;
        private final boolean requiresBacktrackPoint;
        private final List<String> parallelNodes;
        
        public RoutingDecision(String selectedNode, double confidence, String reasoning) {
            this.selectedNode = selectedNode;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.alternativeScores = new HashMap<>();
            this.requiresBacktrackPoint = false;
            this.parallelNodes = new ArrayList<>();
        }
        
        public RoutingDecision(String selectedNode, double confidence, String reasoning,
                             Map<String, Double> alternativeScores, 
                             boolean requiresBacktrackPoint,
                             List<String> parallelNodes) {
            this.selectedNode = selectedNode;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.alternativeScores = alternativeScores;
            this.requiresBacktrackPoint = requiresBacktrackPoint;
            this.parallelNodes = parallelNodes;
        }
        
        // Getters
        public String getSelectedNode() { return selectedNode; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public Map<String, Double> getAlternativeScores() { return alternativeScores; }
        public boolean requiresBacktrackPoint() { return requiresBacktrackPoint; }
        public List<String> getParallelNodes() { return parallelNodes; }
    }
    
    /**
     * Backtrack point for recovery
     */
    public static class BacktrackPoint {
        private final String nodeId;
        private final State savedState;
        private final Map<String, Double> alternativeScores;
        private final List<String> triedPaths;
        private final long timestamp;
        
        public BacktrackPoint(String nodeId, State state, Map<String, Double> alternatives) {
            this.nodeId = nodeId;
            this.savedState = state.derive();
            this.alternativeScores = new HashMap<>(alternatives);
            this.triedPaths = new ArrayList<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public void markPathTried(String path) {
            triedPaths.add(path);
        }
        
        public String getNextBestPath() {
            return alternativeScores.entrySet().stream()
                .filter(e -> !triedPaths.contains(e.getKey()))
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        }
        
        // Getters
        public String getNodeId() { return nodeId; }
        public State getSavedState() { return savedState; }
        public List<String> getTriedPaths() { return triedPaths; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Routing history for learning
     */
    public static class RoutingHistory {
        private final Map<String, RoutingRecord> records = new HashMap<>();
        
        public void recordDecision(String fromNode, String toNode, 
                                 double confidence, boolean success) {
            String key = fromNode + "->" + toNode;
            records.computeIfAbsent(key, k -> new RoutingRecord())
                .addOutcome(confidence, success);
        }
        
        public double getSuccessRate(String fromNode, String toNode) {
            String key = fromNode + "->" + toNode;
            RoutingRecord record = records.get(key);
            return record != null ? record.getSuccessRate() : 0.5;
        }
        
        public double getAverageConfidence(String fromNode, String toNode) {
            String key = fromNode + "->" + toNode;
            RoutingRecord record = records.get(key);
            return record != null ? record.getAverageConfidence() : 0.5;
        }
    }
    
    /**
     * Routing record for statistics
     */
    private static class RoutingRecord {
        private int totalAttempts = 0;
        private int successfulAttempts = 0;
        private double totalConfidence = 0;
        
        public void addOutcome(double confidence, boolean success) {
            totalAttempts++;
            totalConfidence += confidence;
            if (success) {
                successfulAttempts++;
            }
        }
        
        public double getSuccessRate() {
            return totalAttempts > 0 ? (double) successfulAttempts / totalAttempts : 0;
        }
        
        public double getAverageConfidence() {
            return totalAttempts > 0 ? totalConfidence / totalAttempts : 0;
        }
    }
    
    /**
     * Make routing decision with advanced logic
     */
    public CompletableFuture<RoutingDecision> route(String currentNode, 
                                                   List<SmartEdge> edges,
                                                   State state,
                                                   String workflowId) {
        return CompletableFuture.supplyAsync(() -> {
            // Group edges by strategy
            Map<SmartEdge.RoutingStrategy, List<SmartEdge>> strategyGroups = 
                edges.stream().collect(Collectors.groupingBy(SmartEdge::getStrategy));
            
            // Handle parallel edges
            List<String> parallelNodes = strategyGroups
                .getOrDefault(SmartEdge.RoutingStrategy.PARALLEL, Collections.emptyList())
                .stream()
                .filter(edge -> edge.evaluateScore(state) > 0)
                .map(edge -> edge.to)
                .collect(Collectors.toList());
            
            // Evaluate all non-parallel edges
            Map<String, Double> scores = new HashMap<>();
            Map<String, String> reasonings = new HashMap<>();
            
            for (SmartEdge edge : edges) {
                if (edge.getStrategy() != SmartEdge.RoutingStrategy.PARALLEL) {
                    double score = evaluateEdge(edge, state, workflowId);
                    scores.put(edge.to, score);
                }
            }
            
            // Get AI assistance for complex decisions if needed
            if (strategyGroups.containsKey(SmartEdge.RoutingStrategy.AI_ASSISTED)) {
                enhanceWithAI(currentNode, edges, state, scores, reasonings);
            }
            
            // Select best path
            String selectedNode = scores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
            
            if (selectedNode == null && parallelNodes.isEmpty()) {
                throw new IllegalStateException("No valid routing path from " + currentNode);
            }
            
            double confidence = selectedNode != null ? scores.get(selectedNode) : 1.0;
            String reasoning = reasonings.getOrDefault(selectedNode, 
                "Selected based on highest score: " + confidence);
            
            // Determine if backtrack point is needed
            boolean needsBacktrack = scores.size() > 1 && 
                scores.values().stream().filter(s -> s > 0.3).count() > 1;
            
            return new RoutingDecision(
                selectedNode,
                confidence,
                reasoning,
                scores,
                needsBacktrack,
                parallelNodes
            );
        });
    }
    
    /**
     * Evaluate a single edge
     */
    private double evaluateEdge(SmartEdge edge, State state, String workflowId) {
        double baseScore = edge.evaluateScore(state);
        
        // Apply historical success rate
        RoutingHistory history = routingHistories.get(workflowId);
        if (history != null) {
            double historicalSuccess = history.getSuccessRate(edge.from, edge.to);
            baseScore = baseScore * 0.7 + historicalSuccess * 0.3;
        }
        
        // Apply strategy-specific adjustments
        switch (edge.getStrategy()) {
            case PROBABILISTIC:
                // Add some randomness
                baseScore *= (0.8 + Math.random() * 0.4);
                break;
            case WEIGHTED:
                // Already handled in base score
                break;
            case EXCLUSIVE:
                // Boost score if this is the only exclusive edge
                baseScore *= 1.2;
                break;
        }
        
        return Math.min(1.0, baseScore);
    }
    
    /**
     * Enhance routing decision with AI
     */
    private void enhanceWithAI(String currentNode, List<SmartEdge> edges, 
                              State state, Map<String, Double> scores,
                              Map<String, String> reasonings) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Given the current workflow state, help decide the best next node.\n\n");
        prompt.append("Current Node: ").append(currentNode).append("\n");
        prompt.append("State Summary: ").append(summarizeState(state)).append("\n\n");
        prompt.append("Available Paths:\n");
        
        for (SmartEdge edge : edges) {
            prompt.append("- To: ").append(edge.to).append("\n");
            prompt.append("  Conditions: ").append(edge.getConditions().stream()
                .map(RoutingCondition::getName)
                .collect(Collectors.joining(", "))).append("\n");
            prompt.append("  Current Score: ").append(scores.get(edge.to)).append("\n");
            prompt.append("  Metadata: ").append(edge.getMetadata()).append("\n\n");
        }
        
        prompt.append("Provide a routing recommendation with confidence score (0-1) and reasoning.");
        
        String aiResponse = aiClient.generateContent("gemini-1.5-flash", prompt.toString(),
            Map.of("temperature", 0.3, "maxOutputTokens", 500));
        
        // Parse AI response and update scores
        parseAIRoutingResponse(aiResponse, scores, reasonings);
    }
    
    /**
     * Create backtrack point
     */
    public void createBacktrackPoint(String nodeId, State state, 
                                   Map<String, Double> alternatives) {
        String key = state.getWorkflowId() + "_" + nodeId;
        backtrackPoints.put(key, new BacktrackPoint(nodeId, state, alternatives));
    }
    
    /**
     * Backtrack to previous decision point
     */
    public BacktrackDecision backtrack(String workflowId, String currentNode) {
        // Find most recent backtrack point
        BacktrackPoint point = backtrackPoints.entrySet().stream()
            .filter(e -> e.getKey().startsWith(workflowId))
            .map(Map.Entry::getValue)
            .max(Comparator.comparingLong(BacktrackPoint::getTimestamp))
            .orElse(null);
        
        if (point == null) {
            return new BacktrackDecision(false, null, null, 
                "No backtrack points available");
        }
        
        // Mark current path as tried
        point.markPathTried(currentNode);
        
        // Get next best path
        String nextPath = point.getNextBestPath();
        if (nextPath == null) {
            return new BacktrackDecision(false, null, null,
                "All alternative paths have been tried");
        }
        
        return new BacktrackDecision(true, point.getSavedState(), 
            nextPath, "Backtracking to try alternative path");
    }
    
    /**
     * Backtrack decision result
     */
    public static class BacktrackDecision {
        private final boolean canBacktrack;
        private final State restoredState;
        private final String nextNode;
        private final String reason;
        
        public BacktrackDecision(boolean canBacktrack, State restoredState,
                               String nextNode, String reason) {
            this.canBacktrack = canBacktrack;
            this.restoredState = restoredState;
            this.nextNode = nextNode;
            this.reason = reason;
        }
        
        // Getters
        public boolean canBacktrack() { return canBacktrack; }
        public State getRestoredState() { return restoredState; }
        public String getNextNode() { return nextNode; }
        public String getReason() { return reason; }
    }
    
    /**
     * Record routing outcome for learning
     */
    public void recordOutcome(String workflowId, String fromNode, String toNode,
                            double confidence, boolean success) {
        routingHistories.computeIfAbsent(workflowId, k -> new RoutingHistory())
            .recordDecision(fromNode, toNode, confidence, success);
    }
    
    /**
     * Clean up old backtrack points
     */
    public void cleanupOldBacktrackPoints(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        backtrackPoints.entrySet().removeIf(entry ->
            entry.getValue().getTimestamp() < cutoff);
    }
    
    private String summarizeState(State state) {
        // Create a concise summary of the state
        return String.format("Execution path: %s, Data keys: %s",
            state.getExecutionPath().size() > 3 ? 
                state.getExecutionPath().subList(
                    state.getExecutionPath().size() - 3, 
                    state.getExecutionPath().size()) :
                state.getExecutionPath(),
            state.data.keySet());
    }
    
    private void parseAIRoutingResponse(String response, Map<String, Double> scores,
                                      Map<String, String> reasonings) {
        // Parse AI response and extract recommendations
        // This is a simplified implementation
        if (response.contains("recommend")) {
            // Extract recommended node and update its score
            String[] parts = response.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].equals("node:") && scores.containsKey(parts[i + 1])) {
                    scores.put(parts[i + 1], scores.get(parts[i + 1]) * 1.2);
                }
            }
        }
        reasonings.put("ai_enhanced", response);
    }
}