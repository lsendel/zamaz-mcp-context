package com.zamaz.adk.agents;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.*;
import com.zamaz.adk.config.ADKConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Multi-Agent Architecture with Context Isolation using Google ADK
 * Each agent has its own context window and specialized capabilities
 * Now uses Spring dependency injection
 */
@Service
public class MultiAgentOrchestrator {
    private final Map<AgentType, Agent> agents = new ConcurrentHashMap<>();
    private final SupervisorAgent supervisor;
    private final ContextIsolator contextIsolator;
    private volatile Publisher eventPublisher;
    private volatile ExecutorService agentExecutor;
    
    // Resource management
    private volatile boolean isShutdown = false;
    private final Object shutdownLock = new Object();
    
    public enum AgentType {
        CODE_ANALYZER("gemini-1.5-pro", "Specialized in code analysis and review"),
        DOCUMENT_WRITER("gemini-1.5-pro", "Creates technical documentation"),
        DATA_PROCESSOR("gemini-1.5-flash", "Processes and transforms data"),
        SEARCH_AGENT("gemini-1.5-flash", "Searches and retrieves information"),
        PLANNING_AGENT("gemini-1.5-pro", "Creates execution plans and strategies"),
        QUALITY_CHECKER("gemini-1.5-flash", "Validates outputs and checks quality");
        
        private final String defaultModel;
        private final String description;
        
        AgentType(String defaultModel, String description) {
            this.defaultModel = defaultModel;
            this.description = description;
        }
    }
    
    private final ADKConfigurationProperties config;
    
    @Value("${google.cloud.project}")
    private String projectId;
    
    @Value("${google.cloud.location:us-central1}")
    private String location;
    
    @Value("${google.cloud.pubsub.topic:agent-events}")
    private String topicName;
    
    @Autowired
    public MultiAgentOrchestrator(ADKConfigurationProperties config,
                                Firestore firestore, 
                                ContextIsolator contextIsolator,
                                SupervisorAgent supervisor) {
        this.config = config;
        this.contextIsolator = contextIsolator;
        this.supervisor = supervisor;
    }
    
    @PostConstruct
    public void initialize() {
        try {
            this.agentExecutor = Executors.newWorkStealingPool();
            this.eventPublisher = Publisher.newBuilder(
                TopicName.of(projectId, topicName)).build();
            
            // Initialize specialized agents
            initializeAgents(projectId, location);
            
            // Add shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(this::emergencyShutdown));
            
        } catch (Exception e) {
            // Clean up on initialization failure
            emergencyShutdown();
            throw new AgentOrchestrationException(
                AgentOrchestrationException.AgentErrorCode.AGENT_INITIALIZATION_FAILED,
                "MultiAgentOrchestrator",
                null,
                "Failed to initialize: " + e.getMessage()
            );
        }
    }
    
    /**
     * Specialized Agent with isolated context
     */
    public static class Agent {
        private final String id;
        private final AgentType type;
        private final ContextWindow isolatedContext;
        private final List<Tool> availableTools;
        private final VertexAIEndpoint endpoint;
        private final Map<String, Object> capabilities;
        
        public Agent(String id, AgentType type, String projectId, String location) {
            this.id = id;
            this.type = type;
            this.isolatedContext = new ContextWindow(id, 8192); // 8k context
            this.availableTools = loadToolsForType(type);
            this.endpoint = new VertexAIEndpoint(projectId, location, type.defaultModel);
            this.capabilities = defineCapabilities(type);
        }
        
        public CompletableFuture<AgentResponse> process(AgentRequest request) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Add request to isolated context
                    isolatedContext.addMessage(request.toMessage());
                    
                    // Prepare prompt with context
                    String prompt = buildPrompt(request);
                    
                    // Call Vertex AI with appropriate model
                    String response = endpoint.generateContent(prompt, 
                        Map.of(
                            "temperature", getTemperature(),
                            "maxOutputTokens", getMaxTokens(),
                            "topK", 40,
                            "topP", 0.95
                        ));
                    
                    // Add response to context
                    isolatedContext.addMessage(new Message("assistant", response));
                    
                    // Parse and structure response
                    return new AgentResponse(id, type, response, extractMetadata(response));
                    
                } catch (Exception e) {
                    return new AgentResponse(id, type, 
                        "Error: " + e.getMessage(), Map.of("error", true));
                }
            });
        }
        
        private String buildPrompt(AgentRequest request) {
            StringBuilder prompt = new StringBuilder();
            
            // Add role-specific instructions
            prompt.append(getRoleInstructions()).append("\n\n");
            
            // Add relevant context
            String relevantContext = isolatedContext.getRelevantContext(
                request.getQuery(), 10);
            if (!relevantContext.isEmpty()) {
                prompt.append("Context:\n").append(relevantContext).append("\n\n");
            }
            
            // Add available tools
            if (!availableTools.isEmpty()) {
                prompt.append("Available tools:\n");
                availableTools.forEach(tool -> 
                    prompt.append("- ").append(tool.getDescription()).append("\n"));
                prompt.append("\n");
            }
            
            // Add the actual request
            prompt.append("Request: ").append(request.getQuery());
            
            return prompt.toString();
        }
        
        private String getRoleInstructions() {
            switch (type) {
                case CODE_ANALYZER:
                    return "You are a code analysis expert. Analyze code for quality, " +
                           "security issues, performance problems, and best practices.";
                case DOCUMENT_WRITER:
                    return "You are a technical documentation specialist. Create clear, " +
                           "comprehensive documentation following best practices.";
                case DATA_PROCESSOR:
                    return "You are a data processing expert. Transform, clean, and " +
                           "analyze data efficiently.";
                case SEARCH_AGENT:
                    return "You are a search specialist. Find relevant information " +
                           "quickly and accurately.";
                case PLANNING_AGENT:
                    return "You are a strategic planner. Create detailed execution plans " +
                           "and break down complex tasks.";
                case QUALITY_CHECKER:
                    return "You are a quality assurance expert. Validate outputs for " +
                           "accuracy, completeness, and consistency.";
                default:
                    return "You are a specialized AI assistant.";
            }
        }
        
        private double getTemperature() {
            // Different temperatures for different agent types
            switch (type) {
                case CODE_ANALYZER:
                case QUALITY_CHECKER:
                    return 0.1; // Low temperature for accuracy
                case DOCUMENT_WRITER:
                case PLANNING_AGENT:
                    return 0.7; // Medium temperature for creativity
                case DATA_PROCESSOR:
                case SEARCH_AGENT:
                    return 0.3; // Low-medium for consistency
                default:
                    return 0.5;
            }
        }
        
        private int getMaxTokens() {
            switch (type) {
                case DOCUMENT_WRITER:
                    return 4096; // Long form content
                case CODE_ANALYZER:
                case PLANNING_AGENT:
                    return 2048; // Medium length
                default:
                    return 1024; // Standard length
            }
        }
        
        public void clearContext() {
            isolatedContext.clear();
        }
        
        public ContextStats getContextStats() {
            return isolatedContext.getStats();
        }
    }
    
    /**
     * Supervisor Agent that coordinates other agents
     * Now a separate Spring service
     */
    @Service
    public static class SupervisorAgent {
        private final VertexAIEndpoint endpoint;
        
        @Value("${google.cloud.project}")
        private String projectId;
        
        @Value("${google.cloud.location:us-central1}")
        private String location;
        
        @PostConstruct
        public void initialize() {
            this.endpoint = new VertexAIEndpoint(projectId, location, "gemini-1.5-pro");
        }
        
        public ExecutionPlan createPlan(ComplexRequest request, 
                                      Map<AgentType, Agent> availableAgents) {
            String planningPrompt = buildPlanningPrompt(request, availableAgents);
            
            String planResponse = endpoint.generateContent(planningPrompt,
                Map.of("temperature", 0.3, "maxOutputTokens", 2048));
            
            return parsePlan(planResponse, request);
        }
        
        public FinalResponse combineResults(Map<String, AgentResponse> results, 
                                          ComplexRequest originalRequest) {
            String combinationPrompt = buildCombinationPrompt(results, originalRequest);
            
            String finalResponse = endpoint.generateContent(combinationPrompt,
                Map.of("temperature", 0.5, "maxOutputTokens", 4096));
            
            return new FinalResponse(finalResponse, results, calculateConfidence(results));
        }
        
        private String buildPlanningPrompt(ComplexRequest request, 
                                         Map<AgentType, Agent> agents) {
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are a supervisor agent coordinating specialized agents.\n\n");
            
            prompt.append("Available agents:\n");
            agents.forEach((type, agent) -> {
                prompt.append("- ").append(type.name()).append(": ")
                      .append(type.description).append("\n");
            });
            
            prompt.append("\nRequest: ").append(request.getDescription()).append("\n\n");
            prompt.append("Create an execution plan that:\n");
            prompt.append("1. Breaks down the request into subtasks\n");
            prompt.append("2. Assigns each subtask to the most appropriate agent\n");
            prompt.append("3. Defines the execution order and dependencies\n");
            prompt.append("4. Specifies how results should be combined\n");
            
            return prompt.toString();
        }
    }
    
    /**
     * Context Isolator - Prevents context contamination between agents
     * Now a separate Spring service for better testability
     */
    @Service
    public static class ContextIsolator {
        private final Map<String, ContextWindow> contexts = new ConcurrentHashMap<>();
        private final Firestore firestore;
        
        @Autowired
        public ContextIsolator(Firestore firestore) {
            this.firestore = firestore;
        }
        
        public ContextWindow getContext(String agentId) {
            return contexts.computeIfAbsent(agentId, 
                id -> new ContextWindow(id, 8192));
        }
        
        public void saveContext(String agentId) {
            ContextWindow context = contexts.get(agentId);
            if (context != null) {
                firestore.collection("agent_contexts")
                    .document(agentId)
                    .set(context.toMap());
            }
        }
        
        public void loadContext(String agentId) {
            firestore.collection("agent_contexts")
                .document(agentId)
                .get()
                .thenAccept(doc -> {
                    if (doc.exists()) {
                        ContextWindow context = ContextWindow.fromMap(
                            agentId, doc.getData());
                        contexts.put(agentId, context);
                    }
                });
        }
        
        public void saveAllContexts() {
            System.out.println("Saving all agent contexts...");
            contexts.entrySet().parallelStream().forEach(entry -> {
                try {
                    saveContext(entry.getKey());
                } catch (Exception e) {
                    System.err.println("Failed to save context for agent " + entry.getKey() + ": " + e.getMessage());
                }
            });
        }
    }
    
    /**
     * Context Window for each agent
     */
    public static class ContextWindow {
        private final String agentId;
        private final int maxTokens;
        private final LinkedList<Message> messages;
        private int currentTokens;
        
        public ContextWindow(String agentId, int maxTokens) {
            this.agentId = agentId;
            this.maxTokens = maxTokens;
            this.messages = new LinkedList<>();
            this.currentTokens = 0;
        }
        
        public synchronized void addMessage(Message message) {
            int messageTokens = estimateTokens(message.content);
            
            // Remove old messages if needed
            while (currentTokens + messageTokens > maxTokens && !messages.isEmpty()) {
                Message removed = messages.removeFirst();
                currentTokens -= estimateTokens(removed.content);
            }
            
            messages.add(message);
            currentTokens += messageTokens;
        }
        
        public String getRelevantContext(String query, int maxMessages) {
            // Use embeddings to find most relevant messages
            List<Message> relevant = messages.stream()
                .sorted((m1, m2) -> Double.compare(
                    calculateRelevance(m2.content, query),
                    calculateRelevance(m1.content, query)))
                .limit(maxMessages)
                .collect(Collectors.toList());
            
            return relevant.stream()
                .map(m -> m.role + ": " + m.content)
                .collect(Collectors.joining("\n"));
        }
        
        private double calculateRelevance(String content, String query) {
            // Simplified relevance - in production use embeddings
            Set<String> contentWords = new HashSet<>(
                Arrays.asList(content.toLowerCase().split("\\s+")));
            Set<String> queryWords = new HashSet<>(
                Arrays.asList(query.toLowerCase().split("\\s+")));
            
            contentWords.retainAll(queryWords);
            return (double) contentWords.size() / queryWords.size();
        }
        
        private int estimateTokens(String text) {
            // Rough estimate: 1 token per 4 characters
            return text.length() / 4;
        }
        
        public void clear() {
            messages.clear();
            currentTokens = 0;
        }
        
        public ContextStats getStats() {
            return new ContextStats(agentId, messages.size(), 
                currentTokens, maxTokens);
        }
        
        public Map<String, Object> toMap() {
            return Map.of(
                "agentId", agentId,
                "messages", messages.stream()
                    .map(Message::toMap)
                    .collect(Collectors.toList()),
                "currentTokens", currentTokens
            );
        }
        
        public static ContextWindow fromMap(String agentId, Map<String, Object> map) {
            ContextWindow window = new ContextWindow(agentId, 8192);
            // Restore messages from map
            return window;
        }
    }
    
    /**
     * Orchestrate complex requests across multiple agents
     */
    public CompletableFuture<FinalResponse> orchestrate(ComplexRequest request) {
        // Step 1: Supervisor creates execution plan
        ExecutionPlan plan = supervisor.createPlan(request, agents);
        
        // Step 2: Execute plan with proper coordination
        Map<String, CompletableFuture<AgentResponse>> futures = new HashMap<>();
        
        for (Task task : plan.getTasks()) {
            Agent agent = agents.get(task.getAgentType());
            if (agent == null) {
                continue;
            }
            
            // Check dependencies
            CompletableFuture<AgentResponse> taskFuture;
            if (task.getDependencies().isEmpty()) {
                taskFuture = agent.process(task.toAgentRequest());
            } else {
                // Wait for dependencies
                CompletableFuture<?>[] deps = task.getDependencies().stream()
                    .map(futures::get)
                    .filter(Objects::nonNull)
                    .toArray(CompletableFuture[]::new);
                
                taskFuture = CompletableFuture.allOf(deps)
                    .thenCompose(v -> agent.process(task.toAgentRequest()));
            }
            
            futures.put(task.getId(), taskFuture);
        }
        
        // Step 3: Combine results - now fully async
        return CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
            .thenCompose(v -> {
                Map<String, AgentResponse> results = new HashMap<>();
                
                // Collect all results without blocking
                List<CompletableFuture<Map.Entry<String, AgentResponse>>> resultFutures = 
                    futures.entrySet().stream()
                        .map(entry -> entry.getValue().thenApply(response -> 
                            Map.entry(entry.getKey(), response)))
                        .collect(Collectors.toList());
                
                return CompletableFuture.allOf(resultFutures.toArray(new CompletableFuture[0]))
                    .thenApply(unused -> {
                        resultFutures.forEach(future -> {
                            Map.Entry<String, AgentResponse> entry = future.join();
                            results.put(entry.getKey(), entry.getValue());
                        });
                        return results;
                    })
                    .thenApply(collectedResults -> supervisor.combineResults(collectedResults, request));
            });
    }
    
    private void initializeAgents(String projectId, String location) {
        for (AgentType type : AgentType.values()) {
            Agent agent = new Agent(
                type.name().toLowerCase() + "_agent",
                type,
                projectId,
                location
            );
            agents.put(type, agent);
        }
    }
    
    private static List<Tool> loadToolsForType(AgentType type) {
        // Load appropriate tools for each agent type
        switch (type) {
            case CODE_ANALYZER:
                return Arrays.asList(
                    new Tool("analyze_complexity", "Analyzes code complexity"),
                    new Tool("find_bugs", "Finds potential bugs"),
                    new Tool("check_style", "Checks code style")
                );
            case SEARCH_AGENT:
                return Arrays.asList(
                    new Tool("vector_search", "Search using embeddings"),
                    new Tool("keyword_search", "Traditional keyword search")
                );
            default:
                return Collections.emptyList();
        }
    }
    
    private static Map<String, Object> defineCapabilities(AgentType type) {
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("type", type.name());
        capabilities.put("model", type.defaultModel);
        capabilities.put("description", type.description);
        
        switch (type) {
            case CODE_ANALYZER:
                capabilities.put("languages", Arrays.asList("java", "python", "javascript"));
                capabilities.put("maxFileSize", 100000);
                break;
            case DOCUMENT_WRITER:
                capabilities.put("formats", Arrays.asList("markdown", "html", "pdf"));
                capabilities.put("maxLength", 50000);
                break;
        }
        
        return capabilities;
    }
    
    @PreDestroy
    public void shutdown() {
        synchronized (shutdownLock) {
            if (isShutdown) {
                return; // Already shut down
            }
            isShutdown = true;
        }
        
        System.out.println("Starting graceful shutdown of MultiAgentOrchestrator...");
        
        try {
            // Stop accepting new agent requests
            if (agentExecutor != null) {
                agentExecutor.shutdown();
                
                // Wait for current tasks to complete  
                if (!agentExecutor.awaitTermination(
                    config.getResources().getShutdown().getGracefulTimeoutSeconds(), 
                    TimeUnit.SECONDS)) {
                    System.out.println("Forcing shutdown of agent executor...");
                    agentExecutor.shutdownNow();
                    
                    // Wait for tasks to respond to cancellation
                    if (!agentExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                        System.err.println("Agent executor did not terminate gracefully");
                    }
                }
            }
            
            // Shutdown event publisher
            if (eventPublisher != null) {
                System.out.println("Shutting down event publisher...");
                eventPublisher.shutdown();
                
                try {
                    eventPublisher.awaitTermination(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while waiting for publisher shutdown");
                }
            }
            
            // Clear agent contexts to free memory
            agents.values().forEach(agent -> {
                try {
                    agent.clearContext();
                } catch (Exception e) {
                    System.err.println("Error clearing agent context: " + e.getMessage());
                }
            });
            
            // Save agent contexts if needed
            contextIsolator.saveAllContexts();
            
            System.out.println("MultiAgentOrchestrator shutdown completed successfully.");
            
        } catch (Exception e) {
            System.err.println("Error during MultiAgentOrchestrator shutdown: " + e.getMessage());
            emergencyShutdown();
        }
    }
    
    /**
     * Emergency shutdown for critical failures
     */
    private void emergencyShutdown() {
        if (!isShutdown) {
            System.out.println("Emergency shutdown of MultiAgentOrchestrator...");
            
            // Force immediate shutdown
            if (agentExecutor != null) {
                agentExecutor.shutdownNow();
            }
            
            if (eventPublisher != null) {
                try {
                    eventPublisher.shutdown();
                } catch (Exception e) {
                    // Ignore errors during emergency shutdown
                }
            }
            
            isShutdown = true;
        }
    }
    
    /**
     * Check if the orchestrator is shutdown
     */
    public boolean isShutdown() {
        return isShutdown;
    }
}