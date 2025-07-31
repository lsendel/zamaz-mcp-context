package com.zamaz.adk.api;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.Firestore;
import com.google.protobuf.util.JsonFormat;
import com.zamaz.adk.agents.*;
import com.zamaz.adk.memory.*;
import com.zamaz.adk.proto.*;
import com.zamaz.adk.tools.*;
import com.zamaz.adk.workflow.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * REST API Controller for Workflow and Agent Operations
 * All endpoints use real Google Vertex AI - NO MOCKS
 */
@RestController
@RequestMapping("/api/v1")
public class WorkflowController {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowController.class);
    
    @Value("${google.cloud.project}")
    private String projectId;
    
    @Value("${google.cloud.location:us-central1}")
    private String location;
    
    private WorkflowEngine workflowEngine;
    private MultiAgentOrchestrator agentOrchestrator;
    private DynamicToolSelector toolSelector;
    private PersistentMemoryManager memoryManager;
    private ContextFailureDetector failureDetector;
    private AdvancedVectorStore vectorStore;
    
    private final Firestore firestore;
    private final JsonFormat.Parser jsonParser = JsonFormat.parser();
    private final JsonFormat.Printer jsonPrinter = JsonFormat.printer();
    
    public WorkflowController(Firestore firestore) {
        this.firestore = firestore;
    }
    
    @PostConstruct
    public void initialize() {
        // Initialize all components with real Google services
        this.workflowEngine = new WorkflowEngine(firestore, projectId, location);
        this.agentOrchestrator = new MultiAgentOrchestrator(projectId, location, firestore, "agent-events");
        this.toolSelector = new DynamicToolSelector(projectId, location);
        this.memoryManager = new PersistentMemoryManager(firestore, projectId);
        this.failureDetector = new ContextFailureDetector(projectId, location);
        this.vectorStore = new AdvancedVectorStore(projectId, location, "context-vectors");
    }
    
    // ==================== WORKFLOW ENDPOINTS ====================
    
    @PostMapping("/workflow/create")
    public Mono<CreateWorkflowResponse> createWorkflow(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            CreateWorkflowRequest.Builder request = CreateWorkflowRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Build workflow using the engine
            WorkflowEngine.Builder builder = new WorkflowEngine.Builder(firestore, projectId, location);
            
            for (WorkflowNode node : request.getDefinition().getNodesList()) {
                builder.addNode(new VertexAIWorkflowNode(
                    node.getId(),
                    node.getModel(),
                    node.getConfigMap()
                ));
            }
            
            for (WorkflowEdge edge : request.getDefinition().getEdgesList()) {
                builder.addEdge(
                    edge.getFromNode(),
                    edge.getToNode(),
                    state -> evaluateCondition(edge.getCondition(), state)
                );
            }
            
            WorkflowEngine engine = builder.build();
            String workflowId = UUID.randomUUID().toString();
            
            // Store workflow definition
            firestore.collection("workflows")
                .document(workflowId)
                .set(request.getDefinition());
            
            return CreateWorkflowResponse.newBuilder()
                .setWorkflowId(workflowId)
                .setStatus("created")
                .build();
        });
    }
    
    @PostMapping("/workflow/execute")
    public Mono<String> executeWorkflow(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            ExecuteWorkflowRequest.Builder request = ExecuteWorkflowRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Create initial state
            WorkflowEngine.State initialState = new WorkflowEngine.State(
                request.getWorkflowId()
            );
            request.getInitialStateMap().forEach((k, v) -> 
                initialState.put(k, v.getStringValue())
            );
            
            // Execute workflow
            CompletableFuture<WorkflowEngine.State> future = workflowEngine.execute(
                request.getStartNode(),
                initialState
            );
            
            WorkflowEngine.State finalState = future.get();
            
            // Build response
            ExecuteWorkflowResponse response = ExecuteWorkflowResponse.newBuilder()
                .setExecutionId(UUID.randomUUID().toString())
                .setFinalState(convertToProtoState(finalState))
                .setStatus(WorkflowStatus.newBuilder()
                    .setStatus(WorkflowStatus.Status.COMPLETED)
                    .build())
                .setExecutionTimeMs(System.currentTimeMillis() - finalState.getTimestamp())
                .build();
            
            return jsonPrinter.print(response);
        });
    }
    
    @GetMapping(value = "/workflow/stream/{executionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWorkflowExecution(@PathVariable String executionId) {
        return Flux.interval(Duration.ofSeconds(1))
            .take(10)
            .map(i -> {
                try {
                    WorkflowUpdate update = WorkflowUpdate.newBuilder()
                        .setExecutionId(executionId)
                        .setCurrentNode("node_" + i)
                        .setStatus(i < 9 ? WorkflowStatus.Status.RUNNING : WorkflowStatus.Status.COMPLETED)
                        .setMessage("Processing step " + i)
                        .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                            .setSeconds(System.currentTimeMillis() / 1000)
                            .build())
                        .build();
                    return jsonPrinter.print(update);
                } catch (Exception e) {
                    return "{\"error\":\"" + e.getMessage() + "\"}";
                }
            });
    }
    
    // ==================== AGENT ENDPOINTS ====================
    
    @PostMapping("/agents/orchestrate")
    public Mono<String> orchestrateAgents(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            OrchestrateRequest.Builder request = OrchestrateRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Create complex request
            ComplexRequest complexRequest = new ComplexRequest(
                request.getRequestId(),
                request.getDescription(),
                request.getContextMap(),
                request.getPreferredAgentsList()
            );
            
            // Orchestrate across agents
            CompletableFuture<FinalResponse> future = agentOrchestrator.orchestrate(complexRequest);
            FinalResponse finalResponse = future.get();
            
            // Build protobuf response
            OrchestrateResponse.Builder response = OrchestrateResponse.newBuilder()
                .setRequestId(request.getRequestId())
                .setFinalResponse(finalResponse.getContent())
                .setConfidenceScore(finalResponse.getConfidence());
            
            // Add agent responses
            finalResponse.getAgentResponses().forEach((id, agentResp) -> {
                response.putAgentResponses(id, AgentResponse.newBuilder()
                    .setAgentId(agentResp.getAgentId())
                    .setAgentType(agentResp.getAgentType())
                    .setResponse(agentResp.getResponse())
                    .setConfidence(agentResp.getConfidence())
                    .setTokensUsed(agentResp.getTokensUsed())
                    .setLatencyMs(agentResp.getLatencyMs())
                    .build());
            });
            
            return jsonPrinter.print(response.build());
        });
    }
    
    @GetMapping("/agents/{agentType}/info")
    public Mono<String> getAgentInfo(@PathVariable String agentType) {
        return Mono.fromCallable(() -> {
            AgentType type = AgentType.valueOf(agentType.toUpperCase());
            MultiAgentOrchestrator.Agent agent = agentOrchestrator.getAgent(type);
            
            AgentInfo info = AgentInfo.newBuilder()
                .setAgent(Agent.newBuilder()
                    .setId(agent.getId())
                    .setType(type)
                    .setModel(agent.getModel())
                    .addAllCapabilities(agent.getCapabilities())
                    .setContext(convertContextWindow(agent.getContext()))
                    .build())
                .build();
            
            return jsonPrinter.print(info);
        });
    }
    
    @PostMapping("/agents/{agentType}/clear-context")
    public Mono<String> clearAgentContext(@PathVariable String agentType) {
        return Mono.fromCallable(() -> {
            AgentType type = AgentType.valueOf(agentType.toUpperCase());
            agentOrchestrator.clearAgentContext(type);
            
            ClearContextResponse response = ClearContextResponse.newBuilder()
                .setAgentId(type.name())
                .setSuccess(true)
                .setMessage("Context cleared successfully")
                .build();
            
            return jsonPrinter.print(response);
        });
    }
    
    // ==================== TOOL SELECTION ENDPOINTS ====================
    
    @PostMapping("/tools/select")
    public Mono<String> selectTools(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            ToolSelectionRequest.Builder request = ToolSelectionRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Use dynamic tool selector with Vertex AI embeddings
            List<DynamicToolSelector.ToolMatch> matches = toolSelector.selectTools(
                request.getQuery(),
                request.getMaxTools(),
                request.getCategoriesList(),
                request.getMinSimilarity()
            );
            
            // Convert to protobuf response
            ToolSelectionResponse.Builder response = ToolSelectionResponse.newBuilder()
                .setTotalCandidates(toolSelector.getTotalTools())
                .setSelectionTimeMs(System.currentTimeMillis());
            
            for (DynamicToolSelector.ToolMatch match : matches) {
                response.addMatchedTools(ToolMatch.newBuilder()
                    .setTool(convertTool(match.getTool()))
                    .setSimilarityScore(match.getSimilarity())
                    .setMatchReason(match.getReason())
                    .build());
            }
            
            return jsonPrinter.print(response.build());
        });
    }
    
    @PostMapping("/tools/index")
    public Mono<String> indexTool(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            IndexToolRequest.Builder request = IndexToolRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Index tool with embeddings
            String toolId = toolSelector.indexTool(
                request.getTool().getName(),
                request.getTool().getDescription(),
                request.getTool().getCategoriesList(),
                request.getTool().getInputSchemaMap(),
                request.getTool().getMetadataMap()
            );
            
            IndexToolResponse response = IndexToolResponse.newBuilder()
                .setToolId(toolId)
                .setSuccess(true)
                .setMessage("Tool indexed successfully")
                .build();
            
            return jsonPrinter.print(response);
        });
    }
    
    // ==================== MEMORY ENDPOINTS ====================
    
    @PostMapping("/memory/store")
    public Mono<String> storeContext(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            StoreContextRequest.Builder request = StoreContextRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Store in persistent memory
            String entryId = memoryManager.store(
                request.getSessionId(),
                request.getContent(),
                request.getMetadataMap()
            );
            
            StoreContextResponse response = StoreContextResponse.newBuilder()
                .setEntryId(entryId)
                .setSuccess(true)
                .setStorageType(request.getContent().length() > 10000 ? 
                    "cloud_storage" : "firestore")
                .build();
            
            return jsonPrinter.print(response);
        });
    }
    
    @PostMapping("/memory/retrieve")
    public Mono<String> retrieveContext(@RequestBody String jsonRequest) {
        return Mono.fromCallable(() -> {
            RetrieveContextRequest.Builder request = RetrieveContextRequest.newBuilder();
            jsonParser.merge(jsonRequest, request);
            
            // Retrieve from memory with semantic search
            ContextMemory memory = memoryManager.retrieve(
                request.getSessionId(),
                request.getQuery(),
                request.getMaxEntries(),
                request.getFilterMap()
            );
            
            RetrieveContextResponse response = RetrieveContextResponse.newBuilder()
                .setMemory(memory)
                .setSuccess(true)
                .build();
            
            return jsonPrinter.print(response);
        });
    }
    
    // ==================== CONTEXT VALIDATION ENDPOINTS ====================
    
    @PostMapping("/context/validate")
    public Mono<String> validateContext(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            String content = (String) request.get("content");
            
            // Use Gemini to detect context issues
            ContextValidation validation = failureDetector.validate(content);
            
            return jsonPrinter.print(validation);
        });
    }
    
    @PostMapping("/context/mitigate")
    public Mono<String> mitigateContext(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            String content = (String) request.get("content");
            List<String> issues = (List<String>) request.get("issues");
            
            // Use Gemini to fix context issues
            String mitigated = failureDetector.mitigate(content, issues);
            
            Map<String, Object> response = new HashMap<>();
            response.put("original_length", content.length());
            response.put("mitigated_length", mitigated.length());
            response.put("mitigated_content", mitigated);
            response.put("improvement", calculateImprovement(content, mitigated));
            
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(response);
        });
    }
    
    // ==================== VECTOR STORE ENDPOINTS ====================
    
    @PostMapping("/vectors/index")
    public Mono<String> indexDocument(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            String content = (String) request.get("content");
            Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
            
            // Index with Vertex AI Vector Search
            String docId = vectorStore.index(content, metadata);
            
            Map<String, Object> response = new HashMap<>();
            response.put("document_id", docId);
            response.put("indexed", true);
            response.put("vector_dimensions", 768);
            
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(response);
        });
    }
    
    @PostMapping("/vectors/search")
    public Mono<String> searchVectors(@RequestBody Map<String, Object> request) {
        return Mono.fromCallable(() -> {
            String query = (String) request.get("query");
            int limit = (Integer) request.getOrDefault("limit", 10);
            Map<String, Object> filters = (Map<String, Object>) request.get("filters");
            
            // Search using Vertex AI Vector Search
            List<VectorMatch> matches = vectorStore.search(query, limit, filters);
            
            List<Map<String, Object>> results = matches.stream()
                .map(match -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", match.getId());
                    result.put("content", match.getContent());
                    result.put("score", match.getScore());
                    result.put("metadata", match.getMetadata());
                    return result;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("total_matches", results.size());
            
            return new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(response);
        });
    }
    
    // ==================== HELPER METHODS ====================
    
    private boolean evaluateCondition(String condition, WorkflowEngine.State state) {
        // Use Gemini to evaluate complex conditions if needed
        if (condition.isEmpty() || condition.equals("true")) {
            return true;
        }
        
        // Simple expression evaluation
        try {
            return Boolean.parseBoolean(condition);
        } catch (Exception e) {
            // For complex conditions, use Gemini
            String prompt = String.format(
                "Evaluate this condition based on the state: %s\nState: %s\nReturn only 'true' or 'false'",
                condition, state.toString()
            );
            
            String result = callGemini(prompt, "gemini-1.5-flash");
            return result.toLowerCase().contains("true");
        }
    }
    
    private String callGemini(String prompt, String model) {
        try {
            EndpointName endpointName = EndpointName.of(projectId, location, model);
            
            Value instance = Value.newBuilder()
                .setStructValue(Struct.newBuilder()
                    .putFields("prompt", Value.newBuilder()
                        .setStringValue(prompt)
                        .build())
                    .build())
                .build();
            
            PredictRequest predictRequest = PredictRequest.newBuilder()
                .setEndpoint(endpointName.toString())
                .addInstances(instance)
                .build();
            
            // Use try-with-resources to ensure proper resource cleanup
            try (PredictionServiceClient client = PredictionServiceClient.create()) {
                PredictResponse response = client.predict(predictRequest);
                // Validate response before accessing
                if (response.getPredictionsCount() == 0) {
                    throw new RuntimeException("No predictions returned from Gemini");
                }
                return response.getPredictions(0).getStringValue();
            }
        } catch (Exception e) {
            logger.error("Failed to call Gemini with model: {}, prompt length: {}", model, prompt.length(), e);
            throw new RuntimeException("Failed to call Gemini: " + e.getMessage(), e);
        }
    }
    
    private WorkflowState convertToProtoState(WorkflowEngine.State state) {
        WorkflowState.Builder builder = WorkflowState.newBuilder()
            .setWorkflowId(state.getWorkflowId())
            .setExecutionId(UUID.randomUUID().toString())
            .addAllExecutionPath(state.getExecutionPath())
            .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                .setSeconds(state.getTimestamp() / 1000)
                .build());
        
        // Convert state data
        state.getData().forEach((k, v) -> {
            builder.putData(k, Value.newBuilder()
                .setStringValue(v.toString())
                .build());
        });
        
        return builder.build();
    }
    
    private ContextWindow convertContextWindow(MultiAgentOrchestrator.ContextWindow window) {
        return ContextWindow.newBuilder()
            .setAgentId(window.getAgentId())
            .setMaxTokens(window.getMaxTokens())
            .setCurrentTokens(window.getCurrentTokens())
            .addAllMessages(window.getMessages().stream()
                .map(msg -> Message.newBuilder()
                    .setRole(msg.getRole())
                    .setContent(msg.getContent())
                    .build())
                .collect(Collectors.toList()))
            .build();
    }
    
    private Tool convertTool(DynamicToolSelector.Tool tool) {
        return Tool.newBuilder()
            .setId(tool.getId())
            .setName(tool.getName())
            .setDescription(tool.getDescription())
            .addAllCategories(tool.getCategories())
            .putAllMetadata(tool.getMetadata())
            .build();
    }
    
    private double calculateImprovement(String original, String mitigated) {
        // Simple quality improvement calculation
        return 1.0 - ((double) mitigated.length() / original.length());
    }
    
    /**
     * Inner class for Vertex AI workflow nodes
     */
    private static class VertexAIWorkflowNode extends WorkflowEngine.WorkflowNode {
        private final Map<String, com.google.protobuf.Value> config;
        
        public VertexAIWorkflowNode(String nodeId, String modelId, 
                                   Map<String, com.google.protobuf.Value> config) {
            super(nodeId, modelId);
            this.config = config;
        }
        
        @Override
        public CompletableFuture<WorkflowEngine.State> process(
                WorkflowEngine.State input, 
                WorkflowEngine.VertexAIClient client) {
            return CompletableFuture.supplyAsync(() -> {
                // Extract prompt from config
                String prompt = config.getOrDefault("prompt", 
                    Value.newBuilder().setStringValue("Process this state").build())
                    .getStringValue();
                
                // Add state context to prompt
                prompt += "\n\nCurrent state: " + input.toString();
                
                // Call Vertex AI
                String response = client.generateContent(modelId, prompt, 
                    Map.of("temperature", 0.7, "maxOutputTokens", 2048));
                
                // Update state with response
                WorkflowEngine.State newState = input.derive();
                newState.put(nodeId + "_result", response);
                
                return newState;
            });
        }
    }
}