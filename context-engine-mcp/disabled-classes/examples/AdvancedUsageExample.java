package com.zamaz.adk.examples;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.zamaz.adk.core.TenantContext;
import com.zamaz.adk.integration.*;
import com.zamaz.adk.agents.MultiAgentOrchestrator;
import com.zamaz.adk.workflow.*;
import com.zamaz.adk.context.*;
import com.zamaz.adk.memory.*;
import com.zamaz.adk.tools.*;

import java.util.*;
import java.util.concurrent.*;

/**
 * Advanced Usage Example - Demonstrates all features of the Unified Context Engine
 * This example shows how to build sophisticated AI applications using the ADK
 */
public class AdvancedUsageExample {
    
    public static void main(String[] args) {
        // Initialize Google Cloud clients
        String projectId = System.getenv("GOOGLE_CLOUD_PROJECT");
        String bucketName = System.getenv("STORAGE_BUCKET");
        
        Firestore firestore = FirestoreOptions.getDefaultInstance().getService();
        Storage storage = StorageOptions.getDefaultInstance().getService();
        
        // Configure the Unified Context Engine
        UnifiedContextEngine.EngineConfiguration config = 
            new UnifiedContextEngine.EngineConfiguration.Builder()
                .projectId(projectId)
                .location("us-central1")
                .bucketName(bucketName)
                .enableDebug(true)
                .enableStreaming(true)
                .maxConcurrentWorkflows(50)
                .modelConfig("gemini-1.5-pro", Map.of(
                    "temperature", 0.7,
                    "maxOutputTokens", 2048
                ))
                .modelConfig("gemini-1.5-flash", Map.of(
                    "temperature", 0.5,
                    "maxOutputTokens", 1024
                ))
                .serviceConfig("memory", Map.of(
                    "max_pool_size", 10000,
                    "ttl_hours", 24
                ))
                .build();
        
        // Create the engine
        UnifiedContextEngine engine = new UnifiedContextEngine(config, firestore, storage);
        
        try {
            // Create tenant context
            TenantContext tenantContext = new TenantContext("zamaz-prod", "production");
            
            // Example 1: Create an Intelligent Agent Team
            System.out.println("\n=== Example 1: Creating Intelligent Agent Team ===");
            demonstrateAgentTeam(engine, tenantContext);
            
            // Example 2: Execute Advanced Workflow with Streaming
            System.out.println("\n=== Example 2: Advanced Workflow Execution ===");
            demonstrateAdvancedWorkflow(engine, tenantContext);
            
            // Example 3: Context-Aware Search with Quality Scoring
            System.out.println("\n=== Example 3: Context-Aware Search ===");
            demonstrateContextAwareSearch(engine, tenantContext);
            
            // Example 4: Cross-Workflow Memory Sharing
            System.out.println("\n=== Example 4: Cross-Workflow Memory ===");
            demonstrateCrossWorkflowMemory(engine, tenantContext);
            
            // Example 5: Workflow Debugging and Replay
            System.out.println("\n=== Example 5: Workflow Debugging ===");
            demonstrateWorkflowDebugging(engine, tenantContext);
            
            // Example 6: Tool Discovery and Enrichment
            System.out.println("\n=== Example 6: Tool Discovery ===");
            demonstrateToolDiscovery(engine, tenantContext);
            
            // Example 7: Persistent Memory with Semantic Search
            System.out.println("\n=== Example 7: Persistent Memory ===");
            demonstratePersistentMemory(engine, tenantContext);
            
            // Example 8: Real-time Context Quality Monitoring
            System.out.println("\n=== Example 8: Context Quality Monitoring ===");
            demonstrateContextQuality(engine, tenantContext);
            
            // Show system statistics
            System.out.println("\n=== System Statistics ===");
            showSystemStatistics(engine);
            
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Shutdown the engine
            engine.shutdown();
        }
    }
    
    /**
     * Example 1: Create and coordinate an intelligent agent team
     */
    private static void demonstrateAgentTeam(UnifiedContextEngine engine, 
                                           TenantContext tenantContext) throws Exception {
        // Define agent specifications
        List<UnifiedContextEngine.AgentTeamRequest.AgentSpec> agentSpecs = Arrays.asList(
            // Research Agent
            new UnifiedContextEngine.AgentTeamRequest.AgentSpec(
                MultiAgentOrchestrator.AgentType.RESEARCHER,
                "Market Research Specialist",
                Map.of(
                    "domains", Arrays.asList("e-commerce", "amazon-fba", "retail"),
                    "skills", Arrays.asList("data-analysis", "trend-detection", "competitor-analysis")
                ),
                Map.of("memory_type", "persistent", "memory_size", "large")
            ),
            
            // Analysis Agent
            new UnifiedContextEngine.AgentTeamRequest.AgentSpec(
                MultiAgentOrchestrator.AgentType.ANALYST,
                "Business Intelligence Analyst",
                Map.of(
                    "expertise", Arrays.asList("financial-modeling", "forecasting", "optimization"),
                    "tools", Arrays.asList("spreadsheet", "visualization", "statistical-analysis")
                ),
                Map.of("memory_type", "working", "cache_results", true)
            ),
            
            // Decision Agent
            new UnifiedContextEngine.AgentTeamRequest.AgentSpec(
                MultiAgentOrchestrator.AgentType.PLANNER,
                "Strategic Decision Maker",
                Map.of(
                    "decision_framework", "data-driven",
                    "risk_tolerance", "moderate",
                    "optimization_goals", Arrays.asList("profit", "efficiency", "growth")
                ),
                Map.of("memory_type", "contextual", "decision_history", true)
            )
        );
        
        // Create the agent team
        UnifiedContextEngine.AgentTeamRequest teamRequest = 
            new UnifiedContextEngine.AgentTeamRequest(
                "market-intelligence-team",
                "Market Intelligence Team",
                tenantContext,
                agentSpecs,
                UnifiedContextEngine.AgentTeamRequest.CoordinationStrategy.COLLABORATIVE
            );
        
        UnifiedContextEngine.AgentTeam team = engine.createAgentTeam(teamRequest).get();
        
        System.out.println("Created agent team: " + team.getTeamName());
        System.out.println("Team ID: " + team.getTeamId());
        System.out.println("Communication Channel: " + team.getChannelId());
        
        // Simulate team collaboration
        String analysisTask = "Analyze the current market trends for kitchen appliances on Amazon, " +
                            "identify top competitors, and recommend optimal pricing strategy for Q4.";
        
        // Send task to team channel
        engine.getCommunicationBus().publishToChannel(
            team.getChannelId(),
            new AgentCommunicationBus.MemoryMessage(
                "coordinator",
                team.getChannelId(),
                AgentCommunicationBus.MemoryMessage.MessageType.REQUEST,
                Map.of("task", analysisTask, "priority", "high"),
                Map.of("deadline", "2hours"),
                8
            )
        );
        
        System.out.println("Task assigned to team: " + analysisTask);
    }
    
    /**
     * Example 2: Execute advanced workflow with conditional routing and streaming
     */
    private static void demonstrateAdvancedWorkflow(UnifiedContextEngine engine,
                                                  TenantContext tenantContext) throws Exception {
        // Build workflow execution request
        UnifiedContextEngine.WorkflowExecutionRequest request =
            new UnifiedContextEngine.WorkflowExecutionRequest.Builder()
                .workflowId("inventory-optimization-workflow")
                .tenantContext(tenantContext)
                .parameter("product_category", "kitchen-appliances")
                .parameter("optimization_goal", "minimize-stockouts")
                .parameter("budget_constraint", 100000)
                .context("season", "Q4-holiday")
                .context("market_conditions", "high-demand")
                .enableDebug(true)
                .enableStreaming(true)
                .streamType(UnifiedContextEngine.WorkflowExecutionRequest.StreamType.SSE)
                .requireCapability("forecasting")
                .requireCapability("optimization")
                .memoryConfig(Map.of(
                    "share_memory", true,
                    "memory_scope", "family",
                    "persist_results", true
                ))
                .build();
        
        // Subscribe to streaming events
        String executionId = UUID.randomUUID().toString();
        engine.getStreamingService().startStream(executionId, false);
        
        // Add event listener
        engine.getStreamingService().addEventListener(executionId, event -> {
            System.out.println("Workflow Event: " + event.getEventType() + 
                             " - Node: " + event.getNodeId());
        });
        
        // Execute workflow
        CompletableFuture<UnifiedContextEngine.WorkflowExecutionResult> future =
            engine.executeWorkflow(request);
        
        // Monitor execution progress
        while (!future.isDone()) {
            WorkflowStreamingService.StreamStatistics stats = 
                engine.getStreamingService().getStreamStatistics(executionId);
            System.out.println("Events processed: " + stats.getEventsProcessed());
            Thread.sleep(1000);
        }
        
        UnifiedContextEngine.WorkflowExecutionResult result = future.get();
        
        System.out.println("\nWorkflow completed with status: " + result.getStatus());
        System.out.println("Total duration: " + result.getMetrics().getTotalDuration() + "ms");
        System.out.println("Nodes executed: " + result.getMetrics().getTotalNodes());
        System.out.println("Context quality score: " + result.getMetrics().getContextQualityScore());
        
        // Show outputs
        System.out.println("\nWorkflow outputs:");
        result.getOutputs().forEach((key, value) -> 
            System.out.println("  " + key + ": " + value));
    }
    
    /**
     * Example 3: Perform context-aware search with quality scoring
     */
    private static void demonstrateContextAwareSearch(UnifiedContextEngine engine,
                                                    TenantContext tenantContext) throws Exception {
        // Create search request
        UnifiedContextEngine.ContextualSearchRequest searchRequest =
            new UnifiedContextEngine.ContextualSearchRequest(
                "Find all inventory forecasting models suitable for seasonal products with high volatility",
                tenantContext,
                UnifiedContextEngine.ContextualSearchRequest.SearchType.HYBRID,
                Map.of(
                    "category", "forecasting",
                    "complexity", "high",
                    "data_requirements", "time-series"
                ),
                Arrays.asList("model_name", "accuracy", "implementation_time"),
                10,
                true,
                Map.of("boost_recent", true, "include_examples", true)
            );
        
        // Perform search
        UnifiedContextEngine.SearchResults results = 
            engine.searchWithContext(searchRequest).get();
        
        System.out.println("Search query: " + results.getQuery());
        System.out.println("Context quality: " + results.getQualityScore().getQualityLevel() + 
                         " (" + results.getQualityScore().getOverallScore() + ")");
        
        // Check for context failures
        if (!results.getDetectedFailures().isEmpty()) {
            System.out.println("\nDetected context issues:");
            results.getDetectedFailures().forEach(failure ->
                System.out.println("  - " + failure.getMode() + ": " + failure.getDescription())
            );
        }
        
        // Show results
        System.out.println("\nSearch results:");
        for (int i = 0; i < results.getResults().size(); i++) {
            UnifiedContextEngine.SearchResult result = results.getResults().get(i);
            System.out.println((i + 1) + ". " + result.getContent());
            System.out.println("   Score: " + result.getScore());
            System.out.println("   Metadata: " + result.getMetadata());
        }
    }
    
    /**
     * Example 4: Demonstrate cross-workflow memory sharing
     */
    private static void demonstrateCrossWorkflowMemory(UnifiedContextEngine engine,
                                                      TenantContext tenantContext) throws Exception {
        CrossWorkflowMemory crossMemory = engine.getCrossWorkflowMemory();
        
        // Create memory space for workflow family
        CrossWorkflowMemory.WorkflowMemorySpace memorySpace = 
            crossMemory.createMemorySpace("inventory-workflows", tenantContext);
        
        System.out.println("Created memory space: " + memorySpace.getSpaceId());
        
        // Share memory from one workflow
        CrossWorkflowMemory.MemorySharingRequest sharingRequest =
            new CrossWorkflowMemory.MemorySharingRequest(
                "forecast-workflow-001",
                "inventory-workflows/*",
                CrossWorkflowMemory.SharedMemorySegment.MemoryScope.FAMILY,
                Map.of(
                    "forecast_results", Map.of(
                        "Q4_demand", 15000,
                        "confidence", 0.87,
                        "peak_dates", Arrays.asList("2024-11-24", "2024-12-15")
                    ),
                    "model_parameters", Map.of(
                        "seasonality", "multiplicative",
                        "trend", "linear",
                        "holidays_included", true
                    )
                ),
                Map.of("data_type", "forecast", "version", "2.1"),
                Set.of("forecast", "q4", "high-priority"),
                TimeUnit.DAYS.toMillis(30),
                true
            );
        
        String segmentId = crossMemory.shareMemory(sharingRequest).get();
        System.out.println("Shared memory segment: " + segmentId);
        
        // Access shared memory from another workflow
        Map<String, Object> sharedData = crossMemory.accessMemory(
            "optimization-workflow-002",
            memorySpace.getSpaceId(),
            segmentId
        ).get();
        
        System.out.println("\nAccessed shared memory:");
        sharedData.forEach((key, value) -> 
            System.out.println("  " + key + ": " + value));
        
        // Search for relevant shared memories
        List<CrossWorkflowMemory.SharedMemorySegment> segments = 
            crossMemory.searchMemory(
                "planning-workflow-003",
                "inventory-workflows/*",
                Set.of("forecast"),
                Map.of("data_type", "forecast")
            ).get();
        
        System.out.println("\nFound " + segments.size() + " relevant memory segments");
    }
    
    /**
     * Example 5: Demonstrate workflow debugging and replay
     */
    private static void demonstrateWorkflowDebugging(UnifiedContextEngine engine,
                                                   TenantContext tenantContext) throws Exception {
        WorkflowDebugger debugger = engine.getWorkflowDebugger();
        
        // Start debug session
        WorkflowDebugger.DebugSession session = debugger.startDebugSession(
            "debug-workflow-001",
            tenantContext,
            WorkflowDebugger.DebugSession.DebugMode.STEP_BY_STEP
        );
        
        System.out.println("Started debug session: " + session.getSessionId());
        
        // Set breakpoints
        WorkflowDebugger.Breakpoint breakpoint = new WorkflowDebugger.Breakpoint(
            UUID.randomUUID().toString(),
            WorkflowDebugger.Breakpoint.BreakpointType.NODE,
            "decision-node-01",
            "inventory_level < 100",
            true,
            Map.of("notify", true)
        );
        
        debugger.setBreakpoint("inventory-workflow", breakpoint);
        System.out.println("Set breakpoint at: " + breakpoint.getLocation());
        
        // Simulate stepping through execution
        WorkflowDebugger.DebugCommand stepCommand = new WorkflowDebugger.DebugCommand(
            WorkflowDebugger.DebugCommand.CommandType.STEP_OVER,
            Map.of(),
            "user-001"
        );
        
        WorkflowDebugger.DebugCommandResult stepResult = 
            debugger.executeCommand(session.getSessionId(), stepCommand).get();
        
        System.out.println("Step result: " + stepResult.getMessage());
        
        // Inspect variable
        WorkflowDebugger.DebugCommand inspectCommand = new WorkflowDebugger.DebugCommand(
            WorkflowDebugger.DebugCommand.CommandType.INSPECT_VARIABLE,
            Map.of("variableName", "inventory_level"),
            "user-001"
        );
        
        WorkflowDebugger.DebugCommandResult inspectResult = 
            debugger.executeCommand(session.getSessionId(), inspectCommand).get();
        
        System.out.println("Variable value: " + inspectResult.getData().get("value"));
        
        // Get execution analysis
        WorkflowDebugger.ExecutionAnalysis analysis = 
            debugger.analyzeExecution("debug-workflow-001");
        
        if (analysis != null) {
            System.out.println("\nExecution Analysis:");
            System.out.println("  Total duration: " + analysis.getTotalDuration() + "ms");
            System.out.println("  Critical path: " + analysis.getCriticalPath());
            System.out.println("  Bottlenecks: " + analysis.getBottlenecks());
            System.out.println("  Average node time: " + analysis.getAverageNodeExecutionTime() + "ms");
        }
        
        // Export trace for external analysis
        String exportPath = debugger.exportTrace(
            session.getSessionId(),
            WorkflowDebugger.ExportFormat.CHROME_TRACE
        ).get();
        
        System.out.println("Exported trace to: " + exportPath);
    }
    
    /**
     * Example 6: Demonstrate tool discovery and enrichment
     */
    private static void demonstrateToolDiscovery(UnifiedContextEngine engine,
                                               TenantContext tenantContext) throws Exception {
        ToolEmbeddingIndex toolIndex = engine.getToolEmbeddingIndex();
        ToolMetadataEnricher enricher = engine.getMetadataEnricher();
        
        // Create a new tool
        ToolMetadataEnricher.ToolDefinition toolDef = 
            new ToolMetadataEnricher.ToolDefinition(
                "demand-forecaster-v2",
                "Advanced Demand Forecaster",
                "Forecasts product demand using machine learning with seasonal adjustments",
                Map.of(
                    "properties", Map.of(
                        "product_id", Map.of("type", "string", "description", "Product identifier"),
                        "historical_data", Map.of("type", "array", "description", "Historical sales data"),
                        "forecast_horizon", Map.of("type", "integer", "description", "Days to forecast")
                    ),
                    "required", Arrays.asList("product_id", "historical_data")
                ),
                Map.of(
                    "properties", Map.of(
                        "forecast", Map.of("type", "array", "description", "Forecasted values"),
                        "confidence_intervals", Map.of("type", "object", "description", "Upper/lower bounds"),
                        "seasonality", Map.of("type", "object", "description", "Seasonal patterns")
                    )
                ),
                null, // No implementation code needed for this example
                Map.of("version", "2.0", "accuracy", 0.92)
            );
        
        // Enrich tool metadata
        ToolMetadataEnricher.EnrichmentResult enrichment = 
            enricher.enrichTool(toolDef).get();
        
        System.out.println("Tool enrichment completed:");
        System.out.println("  Enhanced description: " + enrichment.getEnhancedDescription());
        System.out.println("  Categories: " + enrichment.getCategories());
        System.out.println("  Keywords: " + enrichment.getKeywords());
        System.out.println("  Confidence: " + enrichment.getConfidence());
        
        // Create enriched tool
        ToolEmbeddingIndex.EnrichedTool enrichedTool = 
            enricher.createEnrichedTool(toolDef, enrichment, tenantContext);
        
        // Index the tool
        String toolId = toolIndex.indexTool(enrichedTool).get();
        System.out.println("\nIndexed tool: " + toolId);
        
        // Search for tools
        List<ToolEmbeddingIndex.ToolMatch> matches = toolIndex.searchTools(
            "I need a tool to predict future sales for seasonal products",
            ToolEmbeddingIndex.SearchOptions.defaultOptions()
        );
        
        System.out.println("\nTool search results:");
        for (ToolEmbeddingIndex.ToolMatch match : matches) {
            System.out.println("- " + match.getTool().getName());
            System.out.println("  Similarity: " + match.getSimilarityScore());
            System.out.println("  Relevance: " + match.getRelevanceScore());
            System.out.println("  Explanation: " + match.getExplanation());
        }
        
        // Get related tools
        List<ToolEmbeddingIndex.ToolMatch> relatedTools = 
            toolIndex.getRelatedTools(toolId, 5);
        
        System.out.println("\nRelated tools:");
        relatedTools.forEach(related -> 
            System.out.println("- " + related.getTool().getName() + 
                             " (similarity: " + related.getSimilarityScore() + ")")
        );
    }
    
    /**
     * Example 7: Demonstrate persistent memory with embeddings
     */
    private static void demonstratePersistentMemory(UnifiedContextEngine engine,
                                                  TenantContext tenantContext) throws Exception {
        PersistentMemoryEmbeddings memory = engine.getPersistentMemory();
        
        // Store complex memory
        String analysisReport = """
            Q4 2024 Market Analysis Summary:
            
            The kitchen appliances market shows strong growth potential with several key trends:
            1. Smart home integration is driving 40% of new purchases
            2. Energy efficiency ratings are becoming the top purchase factor
            3. Compact designs are preferred by 60% of urban customers
            4. Price sensitivity has decreased by 15% compared to Q3
            
            Competitive landscape:
            - Brand A maintains 35% market share with premium positioning
            - Brand B growing rapidly at 25% QoQ with value proposition
            - Our brand currently at 18% with opportunity in mid-range segment
            
            Recommendations:
            - Launch smart-enabled product line by end of Q1 2025
            - Focus marketing on energy savings (potential 20% sales uplift)
            - Develop compact variants for top 5 SKUs
            - Implement dynamic pricing to capture price-insensitive segments
            """;
        
        PersistentMemoryEmbeddings.MemoryStorageRequest storageRequest =
            new PersistentMemoryEmbeddings.MemoryStorageRequest(
                analysisReport,
                PersistentMemoryEmbeddings.MemoryChunk.MemoryType.SEMANTIC,
                "market-analysis-agent",
                tenantContext,
                Map.of(
                    "report_type", "quarterly_analysis",
                    "quarter", "Q4-2024",
                    "confidence", 0.85
                ),
                "market-analysis-context",
                true, // Auto-chunk
                0.7   // Importance threshold
            );
        
        List<String> chunkIds = memory.storeMemory(storageRequest).get();
        System.out.println("Stored memory in " + chunkIds.size() + " chunks");
        
        // Search memories
        PersistentMemoryEmbeddings.MemoryRetrievalRequest retrievalRequest =
            new PersistentMemoryEmbeddings.MemoryRetrievalRequest.Builder()
                .query("What are the recommendations for smart home products?")
                .type(PersistentMemoryEmbeddings.MemoryChunk.MemoryType.SEMANTIC)
                .tenantContext(tenantContext)
                .minImportance(0.5)
                .maxResults(5)
                .includeRelated(true)
                .build();
        
        List<PersistentMemoryEmbeddings.MemorySearchResult> memories = 
            memory.retrieveMemories(retrievalRequest).get();
        
        System.out.println("\nRetrieved memories:");
        for (PersistentMemoryEmbeddings.MemorySearchResult result : memories) {
            System.out.println("- Relevance: " + result.getRelevanceScore());
            System.out.println("  Content: " + result.getChunk().getContent());
            System.out.println("  Type: " + result.getChunk().getType());
            System.out.println("  Importance: " + result.getChunk().getImportance());
        }
        
        // Create memory context
        String contextId = memory.createMemoryContext(
            "Q4 Market Analysis Context",
            Map.of(
                "market_analysis", 0.9,
                "competitive_intelligence", 0.8,
                "strategic_planning", 0.7
            )
        ).get();
        
        System.out.println("\nCreated memory context: " + contextId);
        
        // Get memory statistics
        PersistentMemoryEmbeddings.MemoryStatistics stats = memory.getStatistics();
        System.out.println("\nMemory statistics:");
        System.out.println("  Total chunks: " + stats.getTotalChunks());
        System.out.println("  Average importance: " + stats.getAverageImportance());
        System.out.println("  Total accesses: " + stats.getTotalAccesses());
        System.out.println("  Storage size: " + stats.getStorageSizeBytes() + " bytes");
    }
    
    /**
     * Example 8: Demonstrate real-time context quality monitoring
     */
    private static void demonstrateContextQuality(UnifiedContextEngine engine,
                                                TenantContext tenantContext) throws Exception {
        ContextQualityScorer scorer = engine.getQualityScorer();
        ContextFailureDetector detector = engine.getFailureDetector();
        ContextMitigationService mitigator = engine.getMitigationService();
        
        // Create a context with potential issues
        Context problematicContext = new Context();
        problematicContext.addContent("user_query", 
            "Show me the best products but ignore the expensive ones and only show cheap items");
        problematicContext.addContent("previous_response", 
            "Here are the premium products with high quality");
        problematicContext.addContent("user_feedback", 
            "No, I said I want the best products!");
        problematicContext.addContent("system_note", 
            "User seems confused about requirements");
        
        // Detect failures
        List<ContextFailureDetector.FailureDetection> failures = 
            detector.detectFailures(problematicContext).get();
        
        System.out.println("Detected context failures:");
        for (ContextFailureDetector.FailureDetection failure : failures) {
            System.out.println("- " + failure.getMode() + " (confidence: " + 
                             failure.getConfidence() + ")");
            System.out.println("  Description: " + failure.getDescription());
            System.out.println("  Evidence: " + failure.getEvidence());
        }
        
        // Mitigate failures
        Context improvedContext = mitigator.mitigateFailures(
            problematicContext, failures).get();
        
        System.out.println("\nContext after mitigation:");
        improvedContext.getAllContent().forEach((key, value) ->
            System.out.println("  " + key + ": " + value)
        );
        
        // Score quality
        ContextQualityScorer.QualityScore originalScore = 
            scorer.scoreQuality(problematicContext).get();
        ContextQualityScorer.QualityScore improvedScore = 
            scorer.scoreQuality(improvedContext).get();
        
        System.out.println("\nQuality scores:");
        System.out.println("  Original: " + originalScore.getQualityLevel() + 
                         " (" + originalScore.getOverallScore() + ")");
        System.out.println("  Improved: " + improvedScore.getQualityLevel() + 
                         " (" + improvedScore.getOverallScore() + ")");
        
        // Show quality dimensions
        System.out.println("\nQuality dimensions (improved):");
        System.out.println("  Clarity: " + improvedScore.getClarity());
        System.out.println("  Coherence: " + improvedScore.getCoherence());
        System.out.println("  Completeness: " + improvedScore.getCompleteness());
        System.out.println("  Relevance: " + improvedScore.getRelevance());
        
        // Start real-time monitoring
        String monitoringId = "quality-monitor-001";
        scorer.startMonitoring(monitoringId);
        
        // Simulate context updates
        for (int i = 0; i < 5; i++) {
            Context update = new Context();
            update.addContent("iteration", String.valueOf(i));
            update.addContent("query", "Update " + i + " for monitoring");
            
            ContextQualityScorer.QualityScore score = scorer.scoreQuality(update).get();
            System.out.println("\nIteration " + i + " quality: " + 
                             score.getQualityLevel().getEmoji() + " " + 
                             score.getOverallScore());
            
            Thread.sleep(1000);
        }
        
        // Get monitoring statistics
        ContextQualityScorer.QualityStatistics qualityStats = 
            scorer.getStatistics(monitoringId);
        
        System.out.println("\nQuality monitoring statistics:");
        System.out.println("  Average score: " + qualityStats.getAverageScore());
        System.out.println("  Min score: " + qualityStats.getMinScore());
        System.out.println("  Max score: " + qualityStats.getMaxScore());
        System.out.println("  Total assessments: " + qualityStats.getTotalAssessments());
    }
    
    /**
     * Show system statistics
     */
    private static void showSystemStatistics(UnifiedContextEngine engine) {
        UnifiedContextEngine.SystemStatistics stats = engine.getStatistics();
        
        System.out.println("\nSystem Health: " + stats.getHealth().getStatus());
        
        System.out.println("\nComponent Statistics:");
        stats.getComponentStats().forEach((name, compStats) -> {
            System.out.println("  " + compStats.getName() + ":");
            compStats.getMetrics().forEach((metric, value) ->
                System.out.println("    " + metric + ": " + value)
            );
        });
        
        System.out.println("\nModule Statistics:");
        stats.getModuleStats().forEach((name, moduleStats) -> {
            System.out.println("  " + name + ": " + moduleStats);
        });
        
        // Get specific component stats
        AgentMemoryPool.MemoryPoolStatistics memoryStats = 
            engine.getAgentMemoryPool().getStatistics("global");
        if (memoryStats != null) {
            System.out.println("\nMemory Pool Statistics:");
            System.out.println("  Segments: " + memoryStats.getSegmentCount());
            System.out.println("  Entries: " + memoryStats.getEntryCount());
            System.out.println("  Total size: " + memoryStats.getTotalSize() + " bytes");
        }
        
        CrossWorkflowMemory.MemoryStatistics crossMemStats = 
            engine.getCrossWorkflowMemory().getStatistics();
        System.out.println("\nCross-Workflow Memory:");
        System.out.println("  Memory spaces: " + crossMemStats.getSpaceCount());
        System.out.println("  Total segments: " + crossMemStats.getSegmentCount());
        System.out.println("  Active channels: " + crossMemStats.getActiveChannels());
        
        // Show any system issues
        if (!stats.getHealth().getIssues().isEmpty()) {
            System.out.println("\nSystem Issues:");
            stats.getHealth().getIssues().forEach(issue ->
                System.out.println("  - " + issue)
            );
        }
    }
}