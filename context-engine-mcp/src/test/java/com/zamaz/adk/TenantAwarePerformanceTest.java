package com.zamaz.adk;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.zamaz.adk.agents.*;
import com.zamaz.adk.core.*;
import com.zamaz.adk.proto.*;
import com.zamaz.adk.workflow.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;

/**
 * Performance tests for tenant-aware services
 * Measures latency, throughput, and resource usage
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TenantAwarePerformanceTest {
    
    private static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");
    private static final String LOCATION = "us-central1";
    
    private static TenantAwareWorkflowEngine workflowEngine;
    private static TenantAwareMultiAgentOrchestrator agentOrchestrator;
    private static ExecutorService testExecutor;
    
    // Performance metrics
    private static final Map<String, List<Long>> latencyMetrics = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> throughputMetrics = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    
    @BeforeAll
    static void setUp() {
        System.out.println("🚀 Tenant-Aware Performance Test Suite");
        System.out.println("=====================================");
        System.out.println("Project: " + PROJECT_ID);
        System.out.println("Location: " + LOCATION);
        System.out.println();
        
        Firestore firestore = FirestoreOptions.newBuilder()
            .setProjectId(PROJECT_ID)
            .build()
            .getService();
        
        workflowEngine = new TenantAwareWorkflowEngine(firestore, PROJECT_ID, LOCATION);
        agentOrchestrator = new TenantAwareMultiAgentOrchestrator(firestore, PROJECT_ID, LOCATION);
        testExecutor = Executors.newWorkStealingPool();
    }
    
    @Test
    @Order(1)
    @DisplayName("1. Single Tenant Performance Baseline")
    void testSingleTenantBaseline() throws Exception {
        System.out.println("📊 Single Tenant Performance Baseline");
        System.out.println("------------------------------------");
        
        TenantContext tenant = TenantContext.builder()
            .organizationId("perf-test-org")
            .projectId("baseline-project")
            .build();
        
        // Create test workflow
        String workflowId = createPerformanceWorkflow(tenant, "baseline-workflow");
        
        // Warm up
        for (int i = 0; i < 5; i++) {
            executeWorkflowMeasured(tenant, workflowId, "warmup");
        }
        
        // Measure baseline
        List<Long> latencies = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            long latency = executeWorkflowMeasured(tenant, workflowId, "baseline");
            latencies.add(latency);
        }
        
        printLatencyStats("Single Tenant Baseline", latencies);
        latencyMetrics.put("single_tenant_baseline", latencies);
    }
    
    @Test
    @Order(2)
    @DisplayName("2. Multi-Tenant Concurrent Performance")
    void testMultiTenantConcurrent() throws Exception {
        System.out.println("\n📊 Multi-Tenant Concurrent Performance");
        System.out.println("--------------------------------------");
        
        int numTenants = 10;
        int requestsPerTenant = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(numTenants * requestsPerTenant);
        
        List<TenantContext> tenants = new ArrayList<>();
        Map<String, String> tenantWorkflows = new HashMap<>();
        
        // Create tenants and workflows
        for (int i = 0; i < numTenants; i++) {
            TenantContext tenant = TenantContext.builder()
                .organizationId("org-" + i)
                .projectId("project-" + i)
                .build();
            tenants.add(tenant);
            
            String workflowId = createPerformanceWorkflow(tenant, "concurrent-wf-" + i);
            tenantWorkflows.put(tenant.getTenantPath(), workflowId);
        }
        
        // Concurrent execution
        List<Long> allLatencies = Collections.synchronizedList(new ArrayList<>());
        AtomicLong totalRequests = new AtomicLong(0);
        AtomicLong successfulRequests = new AtomicLong(0);
        long startTime = System.currentTimeMillis();
        
        // Submit all tasks
        for (int t = 0; t < numTenants; t++) {
            final TenantContext tenant = tenants.get(t);
            final String workflowId = tenantWorkflows.get(tenant.getTenantPath());
            
            for (int r = 0; r < requestsPerTenant; r++) {
                final int requestId = r;
                testExecutor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for simultaneous start
                        
                        long latency = executeWorkflowMeasured(
                            tenant, workflowId, "concurrent-" + requestId);
                        
                        allLatencies.add(latency);
                        successfulRequests.incrementAndGet();
                    } catch (Exception e) {
                        errorCounts.computeIfAbsent("multi_tenant_concurrent", 
                            k -> new AtomicLong()).incrementAndGet();
                    } finally {
                        totalRequests.incrementAndGet();
                        completeLatch.countDown();
                    }
                });
            }
        }
        
        // Start all requests simultaneously
        startLatch.countDown();
        
        // Wait for completion
        boolean completed = completeLatch.await(120, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        
        assertTrue(completed, "All requests should complete");
        
        System.out.println("\nMulti-Tenant Concurrent Results:");
        System.out.println("Total tenants: " + numTenants);
        System.out.println("Requests per tenant: " + requestsPerTenant);
        System.out.println("Total requests: " + totalRequests.get());
        System.out.println("Successful: " + successfulRequests.get());
        System.out.println("Failed: " + (totalRequests.get() - successfulRequests.get()));
        System.out.println("Duration: " + duration + "ms");
        System.out.println("Throughput: " + String.format("%.2f", 
            totalRequests.get() * 1000.0 / duration) + " req/s");
        
        printLatencyStats("Multi-Tenant Concurrent", allLatencies);
        latencyMetrics.put("multi_tenant_concurrent", allLatencies);
    }
    
    @Test
    @Order(3)
    @DisplayName("3. Tenant Isolation Overhead")
    void testTenantIsolationOverhead() throws Exception {
        System.out.println("\n📊 Tenant Isolation Overhead");
        System.out.println("----------------------------");
        
        // Test with increasing hierarchy depth
        TenantContext orgOnly = TenantContext.builder()
            .organizationId("perf-org")
            .build();
        
        TenantContext orgProject = TenantContext.builder()
            .organizationId("perf-org")
            .projectId("perf-project")
            .build();
        
        TenantContext fullHierarchy = TenantContext.builder()
            .organizationId("perf-org")
            .projectId("perf-project")
            .subprojectId("perf-subproject")
            .build();
        
        // Create workflows for each level
        String orgWorkflowId = createPerformanceWorkflow(orgOnly, "org-workflow");
        String projectWorkflowId = createPerformanceWorkflow(orgProject, "project-workflow");
        String subprojectWorkflowId = createPerformanceWorkflow(fullHierarchy, "subproject-workflow");
        
        // Measure overhead at each level
        Map<String, List<Long>> hierarchyLatencies = new HashMap<>();
        
        for (int i = 0; i < 20; i++) {
            hierarchyLatencies.computeIfAbsent("org_only", k -> new ArrayList<>())
                .add(executeWorkflowMeasured(orgOnly, orgWorkflowId, "hierarchy-" + i));
            
            hierarchyLatencies.computeIfAbsent("org_project", k -> new ArrayList<>())
                .add(executeWorkflowMeasured(orgProject, projectWorkflowId, "hierarchy-" + i));
            
            hierarchyLatencies.computeIfAbsent("full_hierarchy", k -> new ArrayList<>())
                .add(executeWorkflowMeasured(fullHierarchy, subprojectWorkflowId, "hierarchy-" + i));
        }
        
        System.out.println("\nHierarchy Level Performance:");
        hierarchyLatencies.forEach((level, latencies) -> {
            System.out.println("\n" + level + ":");
            printLatencyStats(level, latencies);
        });
        
        // Calculate overhead
        double orgAvg = calculateAverage(hierarchyLatencies.get("org_only"));
        double projectAvg = calculateAverage(hierarchyLatencies.get("org_project"));
        double fullAvg = calculateAverage(hierarchyLatencies.get("full_hierarchy"));
        
        System.out.println("\nIsolation Overhead:");
        System.out.println("Project vs Org: " + 
            String.format("%.2f%%", (projectAvg - orgAvg) / orgAvg * 100));
        System.out.println("Subproject vs Org: " + 
            String.format("%.2f%%", (fullAvg - orgAvg) / orgAvg * 100));
    }
    
    @Test
    @Order(4)
    @DisplayName("4. Agent Orchestration Performance")
    void testAgentOrchestrationPerformance() throws Exception {
        System.out.println("\n🤖 Agent Orchestration Performance");
        System.out.println("----------------------------------");
        
        TenantContext tenant = TenantContext.builder()
            .organizationId("agent-perf-org")
            .projectId("agent-perf-project")
            .build();
        
        // Test with different numbers of agents
        int[] agentCounts = {1, 3, 5};
        Map<Integer, List<Long>> agentLatencies = new HashMap<>();
        
        for (int agentCount : agentCounts) {
            List<Long> latencies = new ArrayList<>();
            
            for (int i = 0; i < 10; i++) {
                List<AgentType> agents = Arrays.asList(AgentType.values())
                    .subList(0, agentCount);
                
                TenantComplexRequest request = new TenantComplexRequest(
                    tenant,
                    "agent-perf-" + i,
                    "Performance test with " + agentCount + " agents",
                    Map.of("iteration", i),
                    agents
                );
                
                long startTime = System.currentTimeMillis();
                CompletableFuture<TenantFinalResponse> future = 
                    agentOrchestrator.orchestrate(tenant, request);
                TenantFinalResponse response = future.get(60, TimeUnit.SECONDS);
                long latency = System.currentTimeMillis() - startTime;
                
                latencies.add(latency);
                
                System.out.printf("Agents: %d, Request: %d, Latency: %dms\n", 
                    agentCount, i, latency);
            }
            
            agentLatencies.put(agentCount, latencies);
        }
        
        System.out.println("\nAgent Count Performance Impact:");
        agentLatencies.forEach((count, latencies) -> {
            System.out.println("\n" + count + " agents:");
            printLatencyStats("agents_" + count, latencies);
        });
    }
    
    @Test
    @Order(5)
    @DisplayName("5. Resource Contention Test")
    void testResourceContention() throws Exception {
        System.out.println("\n🔥 Resource Contention Test");
        System.out.println("---------------------------");
        
        // Create shared resource scenario
        TenantContext sharedTenant = TenantContext.builder()
            .organizationId("shared-org")
            .build();
        
        String sharedWorkflowId = createPerformanceWorkflow(sharedTenant, "shared-workflow");
        
        // Test with increasing concurrency
        int[] concurrencyLevels = {10, 50, 100};
        
        for (int concurrency : concurrencyLevels) {
            System.out.println("\nConcurrency Level: " + concurrency);
            
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completeLatch = new CountDownLatch(concurrency);
            List<Long> contentionLatencies = Collections.synchronizedList(new ArrayList<>());
            AtomicLong contentionErrors = new AtomicLong(0);
            
            long testStart = System.currentTimeMillis();
            
            // Submit concurrent requests to same tenant
            IntStream.range(0, concurrency).forEach(i -> {
                testExecutor.submit(() -> {
                    try {
                        startLatch.await();
                        
                        long latency = executeWorkflowMeasured(
                            sharedTenant, sharedWorkflowId, "contention-" + i);
                        contentionLatencies.add(latency);
                    } catch (Exception e) {
                        contentionErrors.incrementAndGet();
                    } finally {
                        completeLatch.countDown();
                    }
                });
            });
            
            startLatch.countDown();
            completeLatch.await(120, TimeUnit.SECONDS);
            
            long testDuration = System.currentTimeMillis() - testStart;
            
            System.out.println("Completed: " + contentionLatencies.size());
            System.out.println("Errors: " + contentionErrors.get());
            System.out.println("Total duration: " + testDuration + "ms");
            System.out.println("Throughput: " + 
                String.format("%.2f", concurrency * 1000.0 / testDuration) + " req/s");
            
            if (!contentionLatencies.isEmpty()) {
                printLatencyStats("contention_" + concurrency, contentionLatencies);
            }
        }
    }
    
    @Test
    @Order(6)
    @DisplayName("6. Quota Enforcement Performance Impact")
    void testQuotaEnforcementPerformance() throws Exception {
        System.out.println("\n📊 Quota Enforcement Performance Impact");
        System.out.println("--------------------------------------");
        
        // Create tenants with different quota tiers
        TenantContext freeTenant = TenantContext.builder()
            .organizationId("free-tier-org")
            .build();
        
        TenantContext enterpriseTenant = TenantContext.builder()
            .organizationId("enterprise-tier-org")
            .build();
        
        // In real test, would configure tiers in Firestore
        
        String freeWorkflowId = createPerformanceWorkflow(freeTenant, "free-workflow");
        String enterpriseWorkflowId = createPerformanceWorkflow(enterpriseTenant, "enterprise-workflow");
        
        // Measure quota check overhead
        List<Long> freeLatencies = new ArrayList<>();
        List<Long> enterpriseLatencies = new ArrayList<>();
        
        for (int i = 0; i < 20; i++) {
            freeLatencies.add(executeWorkflowMeasured(
                freeTenant, freeWorkflowId, "quota-free-" + i));
            enterpriseLatencies.add(executeWorkflowMeasured(
                enterpriseTenant, enterpriseWorkflowId, "quota-enterprise-" + i));
        }
        
        System.out.println("\nFree Tier:");
        printLatencyStats("quota_free", freeLatencies);
        
        System.out.println("\nEnterprise Tier:");
        printLatencyStats("quota_enterprise", enterpriseLatencies);
        
        double freeAvg = calculateAverage(freeLatencies);
        double enterpriseAvg = calculateAverage(enterpriseLatencies);
        
        System.out.println("\nQuota Check Overhead: " + 
            String.format("%.2f%%", Math.abs(freeAvg - enterpriseAvg) / enterpriseAvg * 100));
    }
    
    @Test
    @Order(7)
    @DisplayName("7. Memory and Resource Usage")
    void testMemoryUsage() throws Exception {
        System.out.println("\n💾 Memory and Resource Usage Test");
        System.out.println("---------------------------------");
        
        Runtime runtime = Runtime.getRuntime();
        
        // Baseline memory
        System.gc();
        Thread.sleep(1000);
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create many tenants
        int numTenants = 100;
        List<TenantContext> tenants = new ArrayList<>();
        Map<String, String> workflows = new HashMap<>();
        
        for (int i = 0; i < numTenants; i++) {
            TenantContext tenant = TenantContext.builder()
                .organizationId("mem-test-org-" + i)
                .projectId("mem-test-project-" + i)
                .build();
            tenants.add(tenant);
            
            String workflowId = createPerformanceWorkflow(tenant, "mem-workflow-" + i);
            workflows.put(tenant.getTenantPath(), workflowId);
        }
        
        // Memory after tenant creation
        System.gc();
        Thread.sleep(1000);
        long afterCreationMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Execute workflows to populate caches
        for (int i = 0; i < Math.min(10, numTenants); i++) {
            TenantContext tenant = tenants.get(i);
            String workflowId = workflows.get(tenant.getTenantPath());
            executeWorkflowMeasured(tenant, workflowId, "memory-test-" + i);
        }
        
        // Memory after execution
        System.gc();
        Thread.sleep(1000);
        long afterExecutionMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.println("\nMemory Usage:");
        System.out.println("Baseline: " + formatBytes(baselineMemory));
        System.out.println("After creating " + numTenants + " tenants: " + 
            formatBytes(afterCreationMemory));
        System.out.println("After execution: " + formatBytes(afterExecutionMemory));
        System.out.println("Per-tenant overhead: " + 
            formatBytes((afterCreationMemory - baselineMemory) / numTenants));
        System.out.println("Execution cache overhead: " + 
            formatBytes(afterExecutionMemory - afterCreationMemory));
    }
    
    // ==================== HELPER METHODS ====================
    
    private String createPerformanceWorkflow(TenantContext tenant, String name) {
        TenantAwareWorkflowEngine.Builder builder = 
            new TenantAwareWorkflowEngine.Builder(tenant, workflowEngine, name);
        
        // Simple workflow for performance testing
        builder.addNode(new PerformanceTestNode("start", "gemini-1.5-flash-001"));
        builder.addNode(new PerformanceTestNode("process", "gemini-1.5-flash-001"));
        builder.addNode(new PerformanceTestNode("end", "gemini-1.5-flash-001"));
        
        builder.addEdge("start", "process", state -> true);
        builder.addEdge("process", "end", state -> true);
        
        return builder.build();
    }
    
    private long executeWorkflowMeasured(TenantContext tenant, String workflowId, 
                                       String executionId) throws Exception {
        TenantAwareWorkflowEngine.State initialState = 
            new TenantAwareWorkflowEngine.State(executionId);
        initialState.put("test_data", "Performance test data");
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<TenantAwareWorkflowEngine.State> future = 
            workflowEngine.execute(tenant, workflowId, "start", initialState);
        
        TenantAwareWorkflowEngine.State result = future.get(60, TimeUnit.SECONDS);
        
        long latency = System.currentTimeMillis() - startTime;
        
        if (!"completed".equals(result.getStatus())) {
            throw new RuntimeException("Workflow failed: " + result.getError());
        }
        
        return latency;
    }
    
    private void printLatencyStats(String label, List<Long> latencies) {
        if (latencies.isEmpty()) {
            System.out.println(label + ": No data");
            return;
        }
        
        Collections.sort(latencies);
        
        double avg = calculateAverage(latencies);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        long p50 = latencies.get(latencies.size() / 2);
        long p95 = latencies.get((int)(latencies.size() * 0.95));
        long p99 = latencies.get((int)(latencies.size() * 0.99));
        
        System.out.println(label + " Latency Statistics:");
        System.out.println("  Samples: " + latencies.size());
        System.out.println("  Average: " + String.format("%.2f", avg) + "ms");
        System.out.println("  Min: " + min + "ms");
        System.out.println("  Max: " + max + "ms");
        System.out.println("  P50: " + p50 + "ms");
        System.out.println("  P95: " + p95 + "ms");
        System.out.println("  P99: " + p99 + "ms");
    }
    
    private double calculateAverage(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }
    
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    /**
     * Lightweight workflow node for performance testing
     */
    private static class PerformanceTestNode extends WorkflowEngine.WorkflowNode {
        public PerformanceTestNode(String nodeId, String modelId) {
            super(nodeId, modelId);
        }
        
        @Override
        public CompletableFuture<WorkflowEngine.State> process(
                WorkflowEngine.State input, 
                WorkflowEngine.VertexAIClient client) {
            return CompletableFuture.supplyAsync(() -> {
                // Minimal processing for performance testing
                WorkflowEngine.State newState = input.derive();
                newState.put(nodeId + "_timestamp", System.currentTimeMillis());
                
                // Only call AI on start node
                if ("start".equals(nodeId)) {
                    String response = client.generateContent(modelId, 
                        "Return SUCCESS", 
                        Map.of("temperature", 0.1, "maxOutputTokens", 10));
                    newState.put("ai_response", response);
                }
                
                return newState;
            });
        }
    }
    
    @AfterAll
    static void tearDown() {
        System.out.println("\n========================================");
        System.out.println("Performance Test Summary");
        System.out.println("========================================");
        
        System.out.println("\nLatency Metrics:");
        latencyMetrics.forEach((metric, latencies) -> {
            if (!latencies.isEmpty()) {
                System.out.println("\n" + metric + ":");
                printLatencyStats(metric, latencies);
            }
        });
        
        System.out.println("\nError Counts:");
        errorCounts.forEach((operation, count) -> {
            System.out.println(operation + ": " + count.get() + " errors");
        });
        
        workflowEngine.shutdown();
        agentOrchestrator.shutdown();
        testExecutor.shutdown();
    }
}