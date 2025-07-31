package com.zamaz.adk;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * REST-Assured tests for all tenant-aware API endpoints
 * Tests real API interactions with various scenarios
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "google.cloud.project=zamaz-authentication",
    "google.cloud.location=us-central1"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantAwareRestAssuredTest {
    
    @LocalServerPort
    private int port;
    
    private static final String BASE_PATH = "/api/v1";
    
    // Test organizations
    private static final String ORG_ZAMAZ = "zamaz-enterprise";
    private static final String ORG_TEST = "test-organization";
    
    // Test projects
    private static final String PROJECT_FBA = "fba-optimization";
    private static final String PROJECT_SUPPLY = "supply-chain";
    
    // Test subprojects
    private static final String SUBPROJECT_ELECTRONICS = "electronics-category";
    private static final String SUBPROJECT_HOME = "home-goods";
    
    // Track created resources
    private static final List<String> createdWorkflowIds = new ArrayList<>();
    private static final Map<String, Object> testContext = new HashMap<>();
    
    @BeforeAll
    static void setUpClass() {
        System.out.println("🚀 REST-Assured Test Suite for Tenant-Aware APIs");
        System.out.println("===============================================");
    }
    
    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = BASE_PATH;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
    
    // ==================== WORKFLOW TESTS ====================
    
    @Test
    @Order(1)
    @DisplayName("Create Workflow - Organization Level")
    void testCreateWorkflowOrganizationLevel() {
        Map<String, Object> workflowDefinition = createWorkflowDefinition(
            "inventory-health-analyzer",
            Arrays.asList(
                createNode("extract", "DATA_PROCESSOR", "Extract inventory data from all warehouses"),
                createNode("analyze", "PLANNING_AGENT", "Analyze storage fees and inventory age"),
                createNode("recommend", "PLANNING_AGENT", "Generate recommendations for slow-moving items")
            ),
            Arrays.asList(
                createEdge("extract", "analyze", "true"),
                createEdge("analyze", "recommend", "result.issues_found == true")
            )
        );
        
        Response response = given()
            .contentType(ContentType.JSON)
            .body(Map.of("definition", workflowDefinition))
            .when()
            .post("/org/{orgId}/workflow/create", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("workflow_id", notNullValue())
            .body("status", equalTo("created"))
            .body("workflow_id", containsString(ORG_ZAMAZ))
            .extract()
            .response();
        
        String workflowId = response.jsonPath().getString("workflow_id");
        createdWorkflowIds.add(workflowId);
        testContext.put("org_workflow_id", workflowId);
        
        System.out.println("Created organization workflow: " + workflowId);
    }
    
    @Test
    @Order(2)
    @DisplayName("Create Workflow - Project Level with Complex Definition")
    void testCreateWorkflowProjectLevel() {
        Map<String, Object> workflowDefinition = createWorkflowDefinition(
            "fba-inventory-optimizer",
            Arrays.asList(
                createNode("sales_analysis", "DATA_PROCESSOR", 
                    "Analyze FBA sales velocity and patterns"),
                createNode("demand_forecast", "PLANNING_AGENT", 
                    "Forecast demand for next 90 days with seasonality"),
                createNode("reorder_calc", "PLANNING_AGENT", 
                    "Calculate optimal reorder points and quantities"),
                createNode("cost_optimization", "PLANNING_AGENT", 
                    "Optimize for storage fees vs stockout costs")
            ),
            Arrays.asList(
                createEdge("sales_analysis", "demand_forecast", "true"),
                createEdge("demand_forecast", "reorder_calc", "result.confidence > 0.8"),
                createEdge("reorder_calc", "cost_optimization", "true")
            )
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("definition", workflowDefinition))
            .when()
            .post("/org/{orgId}/project/{projectId}/workflow/create", 
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .body("workflow_id", containsString(PROJECT_FBA))
            .body("status", equalTo("created"))
            .extract()
            .response()
            .jsonPath()
            .getString("workflow_id")
            .also(workflowId -> {
                createdWorkflowIds.add(workflowId);
                testContext.put("project_workflow_id", workflowId);
            });
    }
    
    @Test
    @Order(3)
    @DisplayName("Execute Workflow with Initial State")
    void testExecuteWorkflow() {
        String workflowId = (String) testContext.get("org_workflow_id");
        assertNotNull(workflowId, "Workflow ID should exist from previous test");
        
        Map<String, Object> initialState = Map.of(
            "warehouse_locations", Arrays.asList("PHX7", "LAS1", "ONT8"),
            "analysis_depth", "comprehensive",
            "include_forecasting", true,
            "time_horizon_days", 90
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "workflow_id", workflowId,
                "initial_state", initialState,
                "start_node", "extract"
            ))
            .when()
            .post("/org/{orgId}/workflow/execute", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("execution_id", notNullValue())
            .body("status.status", equalTo("COMPLETED"))
            .body("execution_time_ms", greaterThan(0))
            .body("final_state.workflow_id", equalTo(workflowId))
            .body("final_state.execution_path", hasSize(greaterThan(0)));
    }
    
    @Test
    @Order(4)
    @DisplayName("List Workflows - Hierarchical Access")
    void testListWorkflowsHierarchical() {
        // Organization can see all workflows
        given()
            .when()
            .get("/org/{orgId}/workflows", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("tenant", equalTo(ORG_ZAMAZ))
            .body("workflows", hasSize(greaterThan(0)))
            .body("workflows[0].id", notNullValue())
            .body("workflows[0].name", notNullValue());
        
        // Project sees only project workflows
        given()
            .when()
            .get("/org/{orgId}/project/{projectId}/workflows", 
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .body("tenant", equalTo(ORG_ZAMAZ + "/" + PROJECT_FBA))
            .body("workflows.findAll { it.id.contains('" + PROJECT_FBA + "') }", 
                  hasSize(greaterThan(0)));
    }
    
    // ==================== MULTI-AGENT TESTS ====================
    
    @Test
    @Order(5)
    @DisplayName("Agent Orchestration - Complex E-commerce Scenario")
    void testAgentOrchestrationComplexScenario() {
        Map<String, Object> orchestrationRequest = Map.of(
            "request_id", "ecommerce-optimization-" + System.currentTimeMillis(),
            "description", "Analyze Q4 2024 FBA inventory: identify slow-moving SKUs, " +
                          "calculate optimal reorder quantities for Black Friday, " +
                          "suggest pricing strategies to maintain Buy Box",
            "context", Map.of(
                "current_inventory", Map.of(
                    "total_skus", 2500,
                    "inventory_value", 2500000,
                    "warehouse_locations", Arrays.asList("PHX7", "LAS1", "ONT8")
                ),
                "performance_metrics", Map.of(
                    "ipi_score", 487,
                    "storage_fees_monthly", 45000,
                    "sell_through_rate", 0.65
                ),
                "business_goals", Map.of(
                    "target_revenue_q4", 12000000,
                    "target_margin", 0.28,
                    "max_storage_fees", 40000
                )
            ),
            "preferred_agents", Arrays.asList(
                "DATA_PROCESSOR", "PLANNING_AGENT", "QUALITY_CHECKER"
            )
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(orchestrationRequest)
            .when()
            .post("/org/{orgId}/project/{projectId}/agents/orchestrate",
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .body("request_id", equalTo(orchestrationRequest.get("request_id")))
            .body("final_response", notNullValue())
            .body("confidence_score", greaterThan(0.5))
            .body("agent_responses", aMapWithSize(greaterThan(0)))
            .body("agent_responses.values().agent_type", 
                  everyItem(in(Arrays.asList("DATA_PROCESSOR", "PLANNING_AGENT", "QUALITY_CHECKER"))))
            .time(lessThan(60000L)); // Should complete within 60 seconds
    }
    
    @Test
    @Order(6)
    @DisplayName("Agent Orchestration - Subproject Level with Constraints")
    void testAgentOrchestrationSubprojectLevel() {
        Map<String, Object> request = Map.of(
            "request_id", "electronics-pricing-" + System.currentTimeMillis(),
            "description", "Analyze electronics category pricing against competitors, " +
                          "recommend repricing strategy for top 50 ASINs",
            "context", Map.of(
                "category", "Electronics",
                "competitor_count", 15,
                "current_buy_box_percentage", 0.65,
                "target_buy_box_percentage", 0.85
            ),
            "preferred_agents", Arrays.asList("SEARCH_AGENT", "PLANNING_AGENT"),
            "constraints", Map.of(
                "max_latency", "30000",
                "priority", "high",
                "output_format", "detailed_action_plan"
            )
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/org/{orgId}/project/{projectId}/subproject/{subprojectId}/agents/orchestrate",
                  ORG_ZAMAZ, PROJECT_FBA, SUBPROJECT_ELECTRONICS)
            .then()
            .statusCode(200)
            .body("final_response", containsString("pricing"))
            .body("agent_responses.size()", greaterThanOrEqualTo(2));
    }
    
    // ==================== TOOL SELECTION TESTS ====================
    
    @Test
    @Order(7)
    @DisplayName("Tool Selection - Query-based Selection")
    void testToolSelection() {
        // First, index some test tools
        indexTestTools();
        
        // Now test tool selection
        Map<String, Object> selectionRequest = Map.of(
            "query", "I need tools to analyze Amazon FBA inventory and optimize pricing",
            "max_tools", 5,
            "categories", Arrays.asList("analytics", "optimization", "amazon"),
            "min_similarity", 0.7
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(selectionRequest)
            .when()
            .post("/org/{orgId}/tools/select", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("matched_tools", hasSize(greaterThan(0)))
            .body("matched_tools[0].tool.name", notNullValue())
            .body("matched_tools[0].similarity_score", greaterThan(0.7))
            .body("selection_time_ms", greaterThan(0));
    }
    
    @Test
    @Order(8)
    @DisplayName("Tool Indexing - Project-specific Tools")
    void testToolIndexing() {
        Map<String, Object> tool = Map.of(
            "tool", Map.of(
                "name", "fba_profitability_calculator",
                "description", "Calculates true profitability for FBA products including all fees",
                "categories", Arrays.asList("finance", "fba", "analytics"),
                "input_schema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "asin", Map.of("type", "string"),
                        "cost", Map.of("type", "number"),
                        "price", Map.of("type", "number"),
                        "monthly_units", Map.of("type", "number")
                    )
                ),
                "metadata", Map.of(
                    "version", "2.0",
                    "last_updated", "2024-01-15",
                    "accuracy", "98%"
                )
            )
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(tool)
            .when()
            .post("/org/{orgId}/project/{projectId}/tools/index",
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .body("tool_id", notNullValue())
            .body("success", equalTo(true))
            .body("message", containsString("indexed"));
    }
    
    // ==================== MEMORY MANAGEMENT TESTS ====================
    
    @Test
    @Order(9)
    @DisplayName("Memory Store and Retrieve - Session Management")
    void testMemoryManagement() {
        String sessionId = "planning-session-" + System.currentTimeMillis();
        
        // Store strategic planning context
        Map<String, Object> storeRequest = Map.of(
            "session_id", sessionId,
            "content", "Q4 2024 Strategy: Focus on electronics category (40% of revenue). " +
                      "Key products: wireless earbuds (ASIN: B08XYZ123), smart home devices. " +
                      "Risks: tariff changes, shipping delays. " +
                      "Opportunities: Prime Day October, Black Friday expansion.",
            "metadata", Map.of(
                "type", "strategic_planning",
                "participants", Arrays.asList("CEO", "Head of FBA", "Supply Chain Manager"),
                "priority", "high",
                "quarter", "Q4-2024"
            )
        );
        
        // Store memory
        String entryId = given()
            .contentType(ContentType.JSON)
            .body(storeRequest)
            .when()
            .post("/org/{orgId}/memory/store", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("entry_id", notNullValue())
            .body("success", equalTo(true))
            .body("storage_type", equalTo("firestore"))
            .extract()
            .jsonPath()
            .getString("entry_id");
        
        // Retrieve memory
        Map<String, Object> retrieveRequest = Map.of(
            "session_id", sessionId,
            "query", "What were the risks for Q4?",
            "max_entries", 5,
            "filter", Map.of("type", "strategic_planning")
        );
        
        given()
            .contentType(ContentType.JSON)
            .body(retrieveRequest)
            .when()
            .post("/org/{orgId}/memory/retrieve", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("memory.session_id", equalTo(sessionId))
            .body("memory.entries", hasSize(greaterThan(0)))
            .body("memory.entries[0].content", containsString("tariff changes"))
            .body("success", equalTo(true));
    }
    
    // ==================== HEALTH & METRICS TESTS ====================
    
    @Test
    @Order(10)
    @DisplayName("Tenant Health Check - All Levels")
    void testTenantHealthCheck() {
        // Organization level
        given()
            .when()
            .get("/org/{orgId}/health", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .body("tenant", equalTo(ORG_ZAMAZ))
            .body("timestamp", notNullValue());
        
        // Project level
        given()
            .when()
            .get("/org/{orgId}/project/{projectId}/health",
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .body("tenant", equalTo(ORG_ZAMAZ + "/" + PROJECT_FBA));
        
        // Subproject level
        given()
            .when()
            .get("/org/{orgId}/project/{projectId}/subproject/{subprojectId}/health",
                  ORG_ZAMAZ, PROJECT_FBA, SUBPROJECT_ELECTRONICS)
            .then()
            .statusCode(200)
            .body("status", equalTo("healthy"))
            .body("tenant", equalTo(ORG_ZAMAZ + "/" + PROJECT_FBA + "/" + SUBPROJECT_ELECTRONICS));
    }
    
    @Test
    @Order(11)
    @DisplayName("Tenant Metrics - Time-based Queries")
    void testTenantMetrics() {
        String[] timeRanges = {"1h", "24h", "7d", "30d"};
        
        for (String timeRange : timeRanges) {
            given()
                .queryParam("time_range", timeRange)
                .when()
                .get("/org/{orgId}/metrics", ORG_ZAMAZ)
                .then()
                .statusCode(200)
                .body("tenant", equalTo(ORG_ZAMAZ))
                .body("time_range", equalTo(timeRange))
                .body("workflows", notNullValue())
                .body("workflows.total", greaterThanOrEqualTo(0))
                .body("agents", notNullValue())
                .body("agents.requests", greaterThanOrEqualTo(0));
        }
    }
    
    // ==================== ERROR HANDLING TESTS ====================
    
    @Test
    @Order(12)
    @DisplayName("Error Handling - Invalid Tenant Access")
    void testInvalidTenantAccess() {
        // Try to access another org's resources
        given()
            .when()
            .get("/org/{orgId}/workflows", "unauthorized-org")
            .then()
            .statusCode(anyOf(equalTo(403), equalTo(404)));
        
        // Try to execute non-existent workflow
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "workflow_id", "non-existent-workflow",
                "initial_state", Map.of()
            ))
            .when()
            .post("/org/{orgId}/workflow/execute", ORG_ZAMAZ)
            .then()
            .statusCode(anyOf(equalTo(400), equalTo(404)));
    }
    
    @Test
    @Order(13)
    @DisplayName("Error Handling - Malformed Requests")
    void testMalformedRequests() {
        // Missing required fields
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("definition", Map.of()))  // Empty definition
            .when()
            .post("/org/{orgId}/workflow/create", ORG_ZAMAZ)
            .then()
            .statusCode(400);
        
        // Invalid agent type
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "request_id", "test",
                "description", "test",
                "preferred_agents", Arrays.asList("INVALID_AGENT")
            ))
            .when()
            .post("/org/{orgId}/agents/orchestrate", ORG_ZAMAZ)
            .then()
            .statusCode(anyOf(equalTo(400), equalTo(422)));
    }
    
    // ==================== PERFORMANCE TESTS ====================
    
    @Test
    @Order(14)
    @DisplayName("Performance - Concurrent Requests")
    void testConcurrentRequests() throws Exception {
        int concurrentRequests = 10;
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);
        List<Long> latencies = Collections.synchronizedList(new ArrayList<>());
        
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    
                    given()
                        .when()
                        .get("/org/{orgId}/health", ORG_ZAMAZ)
                        .then()
                        .statusCode(200);
                    
                    latencies.add(System.currentTimeMillis() - start);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All requests should complete");
        
        // Calculate statistics
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long maxLatency = latencies.stream().mapToLong(Long::longValue).max().orElse(0);
        
        System.out.println("Concurrent Request Performance:");
        System.out.println("Average latency: " + avgLatency + "ms");
        System.out.println("Max latency: " + maxLatency + "ms");
        
        assertTrue(avgLatency < 1000, "Average latency should be under 1 second");
        executor.shutdown();
    }
    
    // ==================== INTEGRATION SCENARIO TESTS ====================
    
    @Test
    @Order(15)
    @DisplayName("End-to-End Scenario - Complete FBA Optimization Flow")
    void testEndToEndFBAOptimization() {
        // Step 1: Create optimization workflow
        String workflowId = createFBAOptimizationWorkflow();
        
        // Step 2: Store context about current inventory
        String sessionId = storeInventoryContext();
        
        // Step 3: Execute workflow with context
        Map<String, Object> executionResult = executeOptimizationWorkflow(workflowId, sessionId);
        
        // Step 4: Orchestrate agents for detailed analysis
        Map<String, Object> agentAnalysis = orchestrateDetailedAnalysis(executionResult);
        
        // Step 5: Select tools for implementation
        List<Map<String, Object>> selectedTools = selectImplementationTools(agentAnalysis);
        
        // Step 6: Retrieve all context and verify
        verifyOptimizationResults(sessionId, workflowId);
        
        System.out.println("End-to-end FBA optimization completed successfully");
    }
    
    // ==================== HELPER METHODS ====================
    
    private Map<String, Object> createWorkflowDefinition(String name, 
                                                       List<Map<String, Object>> nodes,
                                                       List<Map<String, Object>> edges) {
        return Map.of(
            "name", name,
            "nodes", nodes,
            "edges", edges,
            "metadata", Map.of(
                "created_by", "rest-assured-test",
                "version", "1.0"
            )
        );
    }
    
    private Map<String, Object> createNode(String id, String type, String prompt) {
        return Map.of(
            "id", id,
            "type", type,
            "model", "gemini-1.5-flash-001",
            "config", Map.of("prompt", prompt)
        );
    }
    
    private Map<String, Object> createEdge(String from, String to, String condition) {
        return Map.of(
            "from_node", from,
            "to_node", to,
            "condition", condition
        );
    }
    
    private void indexTestTools() {
        List<Map<String, Object>> tools = Arrays.asList(
            Map.of(
                "name", "inventory_analyzer",
                "description", "Analyzes FBA inventory health and metrics",
                "categories", Arrays.asList("analytics", "inventory", "amazon")
            ),
            Map.of(
                "name", "pricing_optimizer",
                "description", "Optimizes product pricing for Buy Box",
                "categories", Arrays.asList("optimization", "pricing", "amazon")
            ),
            Map.of(
                "name", "competitor_tracker",
                "description", "Tracks competitor pricing and inventory",
                "categories", Arrays.asList("analytics", "competition", "monitoring")
            )
        );
        
        tools.forEach(tool -> {
            given()
                .contentType(ContentType.JSON)
                .body(Map.of("tool", tool))
                .when()
                .post("/org/{orgId}/tools/index", ORG_ZAMAZ)
                .then()
                .statusCode(200);
        });
    }
    
    private String createFBAOptimizationWorkflow() {
        // Implementation for end-to-end test
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of("definition", createWorkflowDefinition(
                "fba-complete-optimization",
                Arrays.asList(
                    createNode("analyze", "DATA_PROCESSOR", "Analyze current FBA metrics"),
                    createNode("optimize", "PLANNING_AGENT", "Generate optimization plan"),
                    createNode("validate", "QUALITY_CHECKER", "Validate recommendations")
                ),
                Arrays.asList(
                    createEdge("analyze", "optimize", "true"),
                    createEdge("optimize", "validate", "true")
                )
            )))
            .when()
            .post("/org/{orgId}/project/{projectId}/workflow/create", 
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getString("workflow_id");
    }
    
    private String storeInventoryContext() {
        String sessionId = "e2e-test-" + System.currentTimeMillis();
        
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "session_id", sessionId,
                "content", "Current inventory: 2500 SKUs, $2.5M value, IPI: 487",
                "metadata", Map.of("type", "inventory_snapshot")
            ))
            .when()
            .post("/org/{orgId}/memory/store", ORG_ZAMAZ)
            .then()
            .statusCode(200);
        
        return sessionId;
    }
    
    private Map<String, Object> executeOptimizationWorkflow(String workflowId, String sessionId) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "workflow_id", workflowId,
                "initial_state", Map.of(
                    "session_id", sessionId,
                    "optimization_target", "reduce_storage_fees"
                ),
                "start_node", "analyze"
            ))
            .when()
            .post("/org/{orgId}/project/{projectId}/workflow/execute",
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap(".");
    }
    
    private Map<String, Object> orchestrateDetailedAnalysis(Map<String, Object> workflowResult) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "request_id", "e2e-analysis",
                "description", "Detailed analysis based on workflow results",
                "context", workflowResult,
                "preferred_agents", Arrays.asList("DATA_PROCESSOR", "PLANNING_AGENT")
            ))
            .when()
            .post("/org/{orgId}/project/{projectId}/agents/orchestrate",
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getMap(".");
    }
    
    private List<Map<String, Object>> selectImplementationTools(Map<String, Object> analysis) {
        return given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "query", "tools for implementing FBA optimization recommendations",
                "max_tools", 3,
                "min_similarity", 0.6
            ))
            .when()
            .post("/org/{orgId}/tools/select", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .extract()
            .jsonPath()
            .getList("matched_tools");
    }
    
    private void verifyOptimizationResults(String sessionId, String workflowId) {
        // Verify memory contains optimization results
        given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "session_id", sessionId,
                "query", "optimization results",
                "max_entries", 10
            ))
            .when()
            .post("/org/{orgId}/memory/retrieve", ORG_ZAMAZ)
            .then()
            .statusCode(200)
            .body("memory.entries", hasSize(greaterThan(0)));
        
        // Verify workflow appears in listings
        given()
            .when()
            .get("/org/{orgId}/project/{projectId}/workflows",
                  ORG_ZAMAZ, PROJECT_FBA)
            .then()
            .statusCode(200)
            .body("workflows.find { it.id == '" + workflowId + "' }", notNullValue());
    }
    
    @AfterAll
    static void tearDown() {
        System.out.println("\n========================================");
        System.out.println("REST-Assured Test Suite Complete");
        System.out.println("Created workflows: " + createdWorkflowIds.size());
        System.out.println("========================================");
    }
}