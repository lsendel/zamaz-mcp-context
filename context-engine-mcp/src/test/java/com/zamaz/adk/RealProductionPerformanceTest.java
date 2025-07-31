package com.zamaz.adk;

import com.google.cloud.aiplatform.v1.*;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.zamaz.adk.agents.*;
import com.zamaz.adk.api.*;
import com.zamaz.adk.proto.*;
import com.zamaz.adk.workflow.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;

/**
 * Real Production Performance Tests - NO MOCKS
 * Tests actual performance with Google Vertex AI
 */
@SpringBootTest
@TestPropertySource(properties = {
    "google.cloud.project=zamaz-authentication",
    "google.cloud.location=us-central1"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RealProductionPerformanceTest {
    
    private static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");
    private static final String LOCATION = "us-central1";
    
    private static WorkflowEngine workflowEngine;
    private static MultiAgentOrchestrator agentOrchestrator;
    private static DynamicToolSelector toolSelector;
    private static Firestore firestore;
    
    // Performance metrics
    private static final List<Long> workflowLatencies = new CopyOnWriteArrayList<>();
    private static final List<Long> agentLatencies = new CopyOnWriteArrayList<>();
    private static final List<Long> toolSelectionLatencies = new CopyOnWriteArrayList<>();
    private static final AtomicInteger successfulRequests = new AtomicInteger(0);
    private static final AtomicInteger failedRequests = new AtomicInteger(0);
    
    @BeforeAll
    static void setUp() {
        System.out.println("🚀 Real Production Performance Test - Google ADK");
        System.out.println("================================================");
        System.out.println("Project: " + PROJECT_ID);
        System.out.println("Location: " + LOCATION);
        System.out.println();
        
        // Initialize Firestore
        firestore = FirestoreOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .build()
            .getService();
        
        // Initialize components
        workflowEngine = new WorkflowEngine(firestore, PROJECT_ID, LOCATION);
        agentOrchestrator = new MultiAgentOrchestrator(PROJECT_ID, LOCATION, firestore, "perf-test-events");
        toolSelector = new DynamicToolSelector(PROJECT_ID, LOCATION);
    }
    
    @Test
    @Order(1)
    @DisplayName("1. Workflow Performance - Sequential Execution")
    void testWorkflowSequentialPerformance() throws Exception {
        System.out.println("📊 Testing Workflow Sequential Performance");
        System.out.println("-----------------------------------------");
        
        // Create a simple workflow
        WorkflowEngine workflow = createTestWorkflow();
        
        // Run 10 sequential executions
        for (int i = 0; i < 10; i++) {
            long startTime = System.currentTimeMillis();
            
            WorkflowEngine.State initialState = new WorkflowEngine.State("perf-test-" + i);
            initialState.put("input", "Process this text: Hello world " + i);
            
            CompletableFuture<WorkflowEngine.State> future = workflow.execute("start", initialState);
            WorkflowEngine.State result = future.get(30, TimeUnit.SECONDS);
            
            long latency = System.currentTimeMillis() - startTime;
            workflowLatencies.add(latency);
            
            System.out.printf("Execution %d: %dms\n", i + 1, latency);
            
            if (result.get("final_result") != null) {
                successfulRequests.incrementAndGet();
            } else {
                failedRequests.incrementAndGet();
            }
        }
        
        printWorkflowStats();
    }
    
    @Test
    @Order(2)
    @DisplayName("2. Workflow Performance - Parallel Execution")
    void testWorkflowParallelPerformance() throws Exception {
        System.out.println("\n📊 Testing Workflow Parallel Performance");
        System.out.println("----------------------------------------");
        
        WorkflowEngine workflow = createTestWorkflow();
        int parallelRequests = 20;
        CountDownLatch latch = new CountDownLatch(parallelRequests);
        
        long startTime = System.currentTimeMillis();
        
        // Submit parallel requests
        IntStream.range(0, parallelRequests).parallel().forEach(i -> {
            try {
                WorkflowEngine.State initialState = new WorkflowEngine.State("parallel-" + i);
                initialState.put("input", "Parallel process " + i);
                
                workflow.execute("start", initialState).whenComplete((result, error) -> {
                    if (error == null) {
                        successfulRequests.incrementAndGet();
                    } else {
                        failedRequests.incrementAndGet();
                        System.err.println("Request " + i + " failed: " + error.getMessage());
                    }
                    latch.countDown();
                });
            } catch (Exception e) {
                failedRequests.incrementAndGet();
                latch.countDown();
            }
        });
        
        // Wait for all to complete
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("\nParallel Execution Results:");
        System.out.println("Total requests: " + parallelRequests);
        System.out.println("Successful: " + successfulRequests.get());
        System.out.println("Failed: " + failedRequests.get());
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Throughput: " + String.format("%.2f", parallelRequests * 1000.0 / totalTime) + " req/s");
        
        Assertions.assertTrue(completed, "All requests should complete within timeout");
        Assertions.assertTrue(successfulRequests.get() > parallelRequests * 0.8, 
            "At least 80% success rate expected");
    }
    
    @Test
    @Order(3)
    @DisplayName("3. Multi-Agent Performance - Complex Orchestration")
    void testMultiAgentPerformance() throws Exception {
        System.out.println("\n🤖 Testing Multi-Agent Performance");
        System.out.println("----------------------------------");
        
        // Create complex request requiring multiple agents
        for (int i = 0; i < 5; i++) {
            long startTime = System.currentTimeMillis();
            
            ComplexRequest request = new ComplexRequest(
                "perf-test-" + i,
                "Analyze this code, write documentation, and check quality: " +
                "public class Example { void process() { System.out.println(\"test\"); } }",
                Map.of("iteration", i),
                Arrays.asList(AgentType.CODE_ANALYZER, AgentType.DOCUMENT_WRITER, AgentType.QUALITY_CHECKER)
            );
            
            CompletableFuture<FinalResponse> future = agentOrchestrator.orchestrate(request);
            FinalResponse response = future.get(45, TimeUnit.SECONDS);
            
            long latency = System.currentTimeMillis() - startTime;
            agentLatencies.add(latency);
            
            System.out.printf("Multi-agent request %d: %dms, Agents used: %d\n", 
                i + 1, latency, response.getAgentResponses().size());
            
            // Print individual agent latencies
            response.getAgentResponses().forEach((id, agentResp) -> {
                System.out.printf("  - %s: %dms\n", agentResp.getAgentType(), agentResp.getLatencyMs());
            });
        }
        
        printAgentStats();
    }
    
    @Test
    @Order(4)
    @DisplayName("4. Dynamic Tool Selection Performance")
    void testToolSelectionPerformance() throws Exception {
        System.out.println("\n🔧 Testing Dynamic Tool Selection Performance");
        System.out.println("---------------------------------------------");
        
        // First, index some tools
        indexTestTools();
        
        // Test tool selection with various queries
        String[] queries = {
            "analyze code complexity",
            "search for security vulnerabilities",
            "generate unit tests",
            "optimize database queries",
            "create API documentation"
        };
        
        for (String query : queries) {
            long startTime = System.currentTimeMillis();
            
            List<DynamicToolSelector.ToolMatch> matches = toolSelector.selectTools(
                query, 3, Collections.emptyList(), 0.7
            );
            
            long latency = System.currentTimeMillis() - startTime;
            toolSelectionLatencies.add(latency);
            
            System.out.printf("Query: '%s' - %dms, Found %d tools\n", 
                query, latency, matches.size());
            
            matches.forEach(match -> {
                System.out.printf("  - %s (%.2f similarity)\n", 
                    match.getTool().getName(), match.getSimilarity());
            });
        }
        
        printToolSelectionStats();
    }
    
    @Test
    @Order(5)
    @DisplayName("5. Load Test - 100 Concurrent Requests")
    void testHighLoad() throws Exception {
        System.out.println("\n🔥 Load Test - 100 Concurrent Requests");
        System.out.println("--------------------------------------");
        
        int totalRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger completed = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Submit all requests
        for (int i = 0; i < totalRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    // Randomly choose operation type
                    int opType = requestId % 3;
                    
                    switch (opType) {
                        case 0:
                            // Workflow execution
                            WorkflowEngine.State state = new WorkflowEngine.State("load-" + requestId);
                            state.put("data", "Load test " + requestId);
                            workflowEngine.execute("start", state).get(30, TimeUnit.SECONDS);
                            break;
                            
                        case 1:
                            // Tool selection
                            toolSelector.selectTools("find tools for " + requestId, 2, 
                                Collections.emptyList(), 0.5);
                            break;
                            
                        case 2:
                            // Simple Gemini call
                            callGemini("What is " + requestId + "?", "gemini-1.5-flash");
                            break;
                    }
                    
                    completed.incrementAndGet();
                    
                } catch (Exception e) {
                    System.err.println("Request " + requestId + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for completion
        boolean finished = latch.await(120, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;
        
        executor.shutdown();
        
        System.out.println("\nLoad Test Results:");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Completed: " + completed.get());
        System.out.println("Failed: " + (totalRequests - completed.get()));
        System.out.println("Total time: " + totalTime + "ms");
        System.out.println("Throughput: " + String.format("%.2f", totalRequests * 1000.0 / totalTime) + " req/s");
        System.out.println("Average latency: " + (totalTime / totalRequests) + "ms");
        
        Assertions.assertTrue(finished, "All requests should complete");
        Assertions.assertTrue(completed.get() > totalRequests * 0.9, 
            "At least 90% success rate expected");
    }
    
    @Test
    @Order(6)
    @DisplayName("6. Context Window Performance - Large Documents")
    void testContextWindowPerformance() throws Exception {
        System.out.println("\n📄 Testing Context Window Performance");
        System.out.println("------------------------------------");
        
        // Generate large documents of varying sizes
        int[] docSizes = {1000, 5000, 10000, 20000, 50000}; // characters
        
        for (int size : docSizes) {
            String document = generateDocument(size);
            
            long startTime = System.currentTimeMillis();
            
            // Test context optimization
            String optimized = optimizeContext(document);
            
            long optimizationTime = System.currentTimeMillis() - startTime;
            
            // Test with Gemini
            startTime = System.currentTimeMillis();
            String summary = callGemini(
                "Summarize this document: " + optimized, 
                "gemini-1.5-flash"
            );
            long processingTime = System.currentTimeMillis() - startTime;
            
            double reduction = 100.0 * (1 - (double)optimized.length() / document.length());
            
            System.out.printf("Document size: %d chars\n", size);
            System.out.printf("  Optimization: %dms, Reduction: %.1f%%\n", optimizationTime, reduction);
            System.out.printf("  Processing: %dms\n", processingTime);
            System.out.printf("  Total: %dms\n\n", optimizationTime + processingTime);
        }
    }
    
    @Test
    @Order(7)
    @DisplayName("7. Model Comparison - Gemini Flash vs Pro")
    void testModelPerformance() throws Exception {
        System.out.println("\n⚡ Model Performance Comparison");
        System.out.println("-------------------------------");
        
        String[] prompts = {
            "What is 2+2?",
            "Explain recursion in 50 words",
            "Write a function to sort an array",
            "Analyze the complexity of quicksort",
            "Design a microservices architecture for e-commerce"
        };
        
        for (String prompt : prompts) {
            System.out.println("\nPrompt: " + prompt);
            
            // Test Gemini Flash
            long flashStart = System.currentTimeMillis();
            String flashResponse = callGemini(prompt, "gemini-1.5-flash-001");
            long flashTime = System.currentTimeMillis() - flashStart;
            
            // Test Gemini Pro
            long proStart = System.currentTimeMillis();
            String proResponse = callGemini(prompt, "gemini-1.5-pro-001");
            long proTime = System.currentTimeMillis() - proStart;
            
            System.out.printf("  Gemini Flash: %dms (%.2f tokens/s)\n", 
                flashTime, estimateTokensPerSecond(flashResponse, flashTime));
            System.out.printf("  Gemini Pro: %dms (%.2f tokens/s)\n", 
                proTime, estimateTokensPerSecond(proResponse, proTime));
            System.out.printf("  Speed ratio: %.2fx\n", (double)proTime / flashTime);
        }
    }
    
    // ==================== HELPER METHODS ====================
    
    private WorkflowEngine createTestWorkflow() {
        return new WorkflowEngine.Builder(firestore, PROJECT_ID, LOCATION)
            .addNode(new TestWorkflowNode("start", "gemini-1.5-flash-001"))
            .addNode(new TestWorkflowNode("process", "gemini-1.5-flash-001"))
            .addNode(new TestWorkflowNode("finalize", "gemini-1.5-flash-001"))
            .addEdge("start", "process")
            .addEdge("process", "finalize")
            .build();
    }
    
    private void indexTestTools() {
        String[][] tools = {
            {"code_analyzer", "Analyzes code for quality and security issues"},
            {"test_generator", "Generates unit tests for code"},
            {"doc_writer", "Creates technical documentation"},
            {"perf_optimizer", "Optimizes code performance"},
            {"security_scanner", "Scans for security vulnerabilities"},
            {"api_designer", "Designs REST APIs"},
            {"db_optimizer", "Optimizes database queries"}
        };
        
        for (String[] tool : tools) {
            toolSelector.indexTool(tool[0], tool[1], 
                Arrays.asList("development", "automation"),
                Map.of("type", "tool"),
                Map.of("category", "development"));
        }
    }
    
    private String callGemini(String prompt, String model) {
        try {
            EndpointName endpointName = EndpointName.of(PROJECT_ID, LOCATION, model);
            
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
                .putParameters("temperature", Value.newBuilder().setNumberValue(0.7).build())
                .putParameters("maxOutputTokens", Value.newBuilder().setNumberValue(1024).build())
                .build();
            
            try (PredictionServiceClient client = PredictionServiceClient.create()) {
                PredictResponse response = client.predict(predictRequest);
                return response.getPredictions(0)
                    .getStructValue()
                    .getFieldsOrThrow("content")
                    .getStringValue();
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    private String generateDocument(int size) {
        StringBuilder doc = new StringBuilder();
        String[] words = {"data", "process", "analyze", "compute", "optimize", 
                         "transform", "aggregate", "filter", "map", "reduce"};
        
        Random random = new Random();
        while (doc.length() < size) {
            doc.append(words[random.nextInt(words.length)]).append(" ");
        }
        
        return doc.toString();
    }
    
    private String optimizeContext(String content) {
        // Real context optimization using Gemini
        String prompt = "Remove redundancy and compress this text while keeping key information: " + content;
        return callGemini(prompt, "gemini-1.5-flash-001");
    }
    
    private double estimateTokensPerSecond(String text, long milliseconds) {
        int estimatedTokens = text.length() / 4; // Rough estimate
        return (estimatedTokens * 1000.0) / milliseconds;
    }
    
    private void printWorkflowStats() {
        System.out.println("\nWorkflow Performance Statistics:");
        System.out.println("Average latency: " + calculateAverage(workflowLatencies) + "ms");
        System.out.println("Min latency: " + Collections.min(workflowLatencies) + "ms");
        System.out.println("Max latency: " + Collections.max(workflowLatencies) + "ms");
        System.out.println("Success rate: " + 
            (100.0 * successfulRequests.get() / (successfulRequests.get() + failedRequests.get())) + "%");
    }
    
    private void printAgentStats() {
        System.out.println("\nMulti-Agent Performance Statistics:");
        System.out.println("Average latency: " + calculateAverage(agentLatencies) + "ms");
        System.out.println("Min latency: " + Collections.min(agentLatencies) + "ms");
        System.out.println("Max latency: " + Collections.max(agentLatencies) + "ms");
    }
    
    private void printToolSelectionStats() {
        System.out.println("\nTool Selection Performance Statistics:");
        System.out.println("Average latency: " + calculateAverage(toolSelectionLatencies) + "ms");
        System.out.println("Min latency: " + Collections.min(toolSelectionLatencies) + "ms");
        System.out.println("Max latency: " + Collections.max(toolSelectionLatencies) + "ms");
    }
    
    private long calculateAverage(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).sum() / values.size();
    }
    
    @AfterAll
    static void tearDown() {
        System.out.println("\n========================================");
        System.out.println("Production Performance Test Complete");
        System.out.println("Total successful requests: " + successfulRequests.get());
        System.out.println("Total failed requests: " + failedRequests.get());
        System.out.println("========================================");
    }
    
    /**
     * Test workflow node that calls Vertex AI
     */
    private static class TestWorkflowNode extends WorkflowEngine.WorkflowNode {
        public TestWorkflowNode(String nodeId, String modelId) {
            super(nodeId, modelId);
        }
        
        @Override
        public CompletableFuture<WorkflowEngine.State> process(
                WorkflowEngine.State input, 
                WorkflowEngine.VertexAIClient client) {
            return CompletableFuture.supplyAsync(() -> {
                String prompt = "Process this in node " + nodeId + ": " + 
                               input.get("input");
                
                String response = client.generateContent(modelId, prompt,
                    Map.of("temperature", 0.7, "maxOutputTokens", 512));
                
                WorkflowEngine.State newState = input.derive();
                newState.put(nodeId + "_result", response);
                
                if (nodeId.equals("finalize")) {
                    newState.put("final_result", response);
                }
                
                return newState;
            });
        }
    }
}