package com.zamaz.adk;

import com.zamaz.adk.exceptions.*;
import com.zamaz.adk.integration.UnifiedContextEngine;
import com.zamaz.adk.agents.MultiAgentOrchestrator;
import com.zamaz.adk.core.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for async flow improvements and non-blocking operations
 */
@SpringBootTest
@TestPropertySource(properties = {
    "google.cloud.project=zamaz-test",
    "google.cloud.location=us-central1",
    "debug.enabled=true"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AsyncFlowValidationTest {

    @Autowired
    private UnifiedContextEngine contextEngine;
    
    @Autowired
    private MultiAgentOrchestrator agentOrchestrator;
    
    private TenantContext testTenant;
    
    @BeforeEach
    void setUp() {
        testTenant = TenantContext.builder()
            .organizationId("zamaz-test")
            .projectId("async-validation")
            .build();
    }
    
    @Test
    @Order(1)
    @DisplayName("Async Workflow Execution - No Blocking Calls")
    void testAsyncWorkflowExecution() {
        // Test that workflow execution is truly async
        UnifiedContextEngine.WorkflowExecutionRequest request = 
            new UnifiedContextEngine.WorkflowExecutionRequest.Builder()
                .workflowId("async-test-workflow")
                .tenantContext(testTenant)
                .parameter("test_mode", true)
                .context("execution_type", "async_validation")
                .build();
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<UnifiedContextEngine.WorkflowExecutionResult> future = 
            contextEngine.executeWorkflow(request);
        
        // Should return immediately (non-blocking)
        long immediateReturn = System.currentTimeMillis() - startTime;
        assertTrue(immediateReturn < 100, "Method should return immediately (non-blocking)");
        
        // Future should complete within reasonable time
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            UnifiedContextEngine.WorkflowExecutionResult result = future.get();
            assertNotNull(result);
            assertNotNull(result.getExecutionId());
            return result;
        });
    }
    
    @Test
    @Order(2)
    @DisplayName("Concurrent Async Operations - Scalability Test")
    void testConcurrentAsyncOperations() throws Exception {
        int concurrentRequests = 20;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<CompletableFuture<UnifiedContextEngine.WorkflowExecutionResult>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();
        
        // Submit multiple concurrent requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestId = i;
            
            UnifiedContextEngine.WorkflowExecutionRequest request = 
                new UnifiedContextEngine.WorkflowExecutionRequest.Builder()
                    .workflowId("concurrent-test-workflow-" + requestId)
                    .tenantContext(testTenant)
                    .parameter("request_id", requestId)
                    .context("batch_execution", true)
                    .build();
            
            CompletableFuture<UnifiedContextEngine.WorkflowExecutionResult> future = 
                contextEngine.executeWorkflow(request)
                    .whenComplete((result, throwable) -> {
                        if (throwable == null) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                            System.err.println("Request " + requestId + " failed: " + throwable.getMessage());
                        }
                    });
            
            futures.add(future);
        }
        
        // Wait for all to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            allFutures.get();
            return null;
        });
        
        // Verify results
        System.out.println("Concurrent execution results:");
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + errorCount.get());
        
        assertTrue(successCount.get() > concurrentRequests * 0.8, 
            "At least 80% of requests should succeed");
        
        executor.shutdown();
    }
    
    @Test
    @Order(3)
    @DisplayName("Async Chain Operations - Context Search")
    void testAsyncContextSearchChain() {
        UnifiedContextEngine.ContextualSearchRequest searchRequest = 
            new UnifiedContextEngine.ContextualSearchRequest(
                "find optimization opportunities",
                testTenant,
                UnifiedContextEngine.ContextualSearchRequest.SearchType.HYBRID,
                Map.of("domain", "fba"),
                Arrays.asList("content", "metadata"),
                10,
                true,
                Map.of("enable_async", true)
            );
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<UnifiedContextEngine.SearchResults> searchFuture = 
            contextEngine.searchWithContext(searchRequest);
        
        // Should return immediately
        long immediateReturn = System.currentTimeMillis() - startTime;
        assertTrue(immediateReturn < 100, "Search should start immediately");
        
        // Chain additional operations
        CompletableFuture<String> analysisResult = searchFuture
            .thenCompose(results -> {
                // Simulate additional async processing
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(100); // Simulate processing
                        return "Analysis complete for " + results.getResults().size() + " results";
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            })
            .thenApply(analysis -> {
                return "Final: " + analysis + " (Quality: " + 
                       searchFuture.join().getQualityScore().getOverallScore() + ")";
            });
        
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            String result = analysisResult.get();
            assertNotNull(result);
            assertTrue(result.contains("Analysis complete"));
            return result;
        });
    }
    
    @Test
    @Order(4)
    @DisplayName("Error Propagation in Async Chains")
    void testAsyncErrorPropagation() {
        // Test that errors are properly propagated through async chains
        UnifiedContextEngine.WorkflowExecutionRequest invalidRequest = 
            new UnifiedContextEngine.WorkflowExecutionRequest.Builder()
                .workflowId("") // Invalid workflow ID
                .tenantContext(testTenant)
                .build();
        
        CompletableFuture<UnifiedContextEngine.WorkflowExecutionResult> future = 
            contextEngine.executeWorkflow(invalidRequest);
        
        // Should complete exceptionally
        assertThrows(ExecutionException.class, () -> {
            future.get(10, TimeUnit.SECONDS);
        });
        
        // Verify exception type
        assertTrue(future.isCompletedExceptionally());
        
        future.handle((result, throwable) -> {
            assertNull(result);
            assertNotNull(throwable);
            assertTrue(throwable instanceof WorkflowExecutionException ||
                      throwable.getCause() instanceof WorkflowExecutionException,
                "Should propagate WorkflowExecutionException");
            return null;
        }).join();
    }
    
    @Test
    @Order(5)
    @DisplayName("Agent Orchestration Async Flow")
    void testAgentOrchestrationAsyncFlow() {
        MultiAgentOrchestrator.ComplexRequest complexRequest = 
            new MultiAgentOrchestrator.ComplexRequest(
                "test-orchestration-" + System.currentTimeMillis(),
                "Analyze async orchestration performance",
                Map.of("test_mode", true, "async_validation", true),
                Arrays.asList("DATA_PROCESSOR", "QUALITY_CHECKER")
            );
        
        assertFalse(agentOrchestrator.isShutdown(), "Orchestrator should be running");
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<MultiAgentOrchestrator.FinalResponse> orchestrationFuture = 
            agentOrchestrator.orchestrate(complexRequest);
        
        // Should return immediately
        long immediateReturn = System.currentTimeMillis() - startTime;
        assertTrue(immediateReturn < 100, "Orchestration should start immediately");
        
        // Test chaining with timeout
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            MultiAgentOrchestrator.FinalResponse response = orchestrationFuture.get();
            assertNotNull(response);
            assertNotNull(response.getContent());
            assertTrue(response.getConfidence() >= 0.0);
            return response;
        });
    }
    
    @Test
    @Order(6)
    @DisplayName("Resource Cleanup During Async Operations")
    void testResourceCleanupDuringAsyncOps() throws Exception {
        List<CompletableFuture<Void>> backgroundTasks = new ArrayList<>();
        
        // Start several long-running async operations
        for (int i = 0; i < 5; i++) {
            CompletableFuture<Void> task = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(2000); // 2 second task
                    return "completed";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted", e);
                }
            }).thenAccept(result -> {
                // Process result
                assertNotNull(result);
            });
            
            backgroundTasks.add(task);
        }
        
        // Wait a bit then test that engine is still responsive
        Thread.sleep(500);
        
        // Engine should still be responsive during background operations
        assertFalse(contextEngine.isShutdown(), "Engine should not be shutdown");
        
        // All tasks should complete successfully
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            backgroundTasks.toArray(new CompletableFuture[0])
        );
        
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            allTasks.get();
            return null;
        });
    }
    
    @Test
    @Order(7)
    @DisplayName("Memory Pressure Under Async Load")
    void testMemoryPressureUnderAsyncLoad() throws Exception {
        int heavyRequestCount = 50;
        List<CompletableFuture<String>> memoryTasks = new ArrayList<>();
        
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Create memory-intensive async operations
        for (int i = 0; i < heavyRequestCount; i++) {
            final int taskId = i;
            
            CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
                // Simulate memory-intensive operation
                List<String> largeData = new ArrayList<>();
                for (int j = 0; j < 1000; j++) {
                    largeData.add("Task-" + taskId + "-Data-" + j + "-" + UUID.randomUUID());
                }
                
                // Process data
                return largeData.stream()
                    .filter(s -> s.contains("Task-" + taskId))
                    .findFirst()
                    .orElse("Not found");
            }).thenApply(result -> {
                // Additional processing
                return "Processed: " + result;
            });
            
            memoryTasks.add(task);
        }
        
        // Complete all tasks
        CompletableFuture<Void> allMemoryTasks = CompletableFuture.allOf(
            memoryTasks.toArray(new CompletableFuture[0])
        );
        
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            allMemoryTasks.get();
            return null;
        });
        
        // Force garbage collection
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        System.out.println("Memory pressure test:");
        System.out.println("Initial memory: " + (initialMemory / 1024 / 1024) + " MB");
        System.out.println("Final memory: " + (finalMemory / 1024 / 1024) + " MB");
        System.out.println("Memory increase: " + (memoryIncrease / 1024 / 1024) + " MB");
        
        // Memory increase should be reasonable (less than 500MB for this test)
        assertTrue(memoryIncrease < 500 * 1024 * 1024, 
            "Memory increase should be reasonable after GC");
    }
    
    @AfterEach
    void cleanup() {
        // Ensure any test-specific cleanup
        System.gc(); // Suggest garbage collection after each test
    }
}