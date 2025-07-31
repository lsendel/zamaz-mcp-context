# 🧪 MCP Test Prompts Guide

## Overview
This guide provides test prompts for validating MCP (Model Context Protocol) functionality through the ADK Agent Console.

## 🎯 Test Categories

### 1. **Context Management Tests**

#### Basic Context Operations
```
"Store this context: User preferences include dark mode, language=English, timezone=EST"

"Retrieve my stored preferences"

"Update my timezone preference to PST"

"Clear all my stored contexts"
```

#### Multi-Tenant Context
```
"Switch to tenant: zamaz-enterprise"

"Store product catalog for current tenant"

"List all contexts for organization zamaz-dev"

"Migrate contexts from tenant A to tenant B"
```

### 2. **Tool Usage Tests**

#### Tool Discovery
```
"What tools are available?"

"Show me tools for data processing"

"Describe the capabilities of the code analyzer tool"

"Find tools that can help with inventory management"
```

#### Tool Execution
```
"Use the calculator tool to compute 15% of 1200"

"Analyze this code snippet for performance issues: [paste code]"

"Generate a sales forecast using historical data"

"Transform this CSV data to JSON format"
```

### 3. **Agent Orchestration Tests**

#### Single Agent Tasks
```
"Analyze the code quality of our checkout module"

"Process this customer data and identify trends"

"Create a plan for implementing a new feature"

"Check the quality of this API response"
```

#### Multi-Agent Workflows
```
"Coordinate code analysis and documentation generation for module X"

"Plan, implement, and test a data validation feature"

"Analyze sales data, generate insights, and create a report"

"Review code, suggest improvements, and update documentation"
```

### 4. **Memory and Retrieval Tests**

#### Short-term Memory
```
"Remember that our primary database is PostgreSQL"

"What database did I just mention?"

"Forget the last piece of information"

"Show my conversation history"
```

#### Long-term Memory
```
"Store this as permanent knowledge: Our API rate limit is 1000 req/min"

"What are our API rate limits?"

"Search my stored knowledge about rate limits"

"Update the stored API rate limit to 2000 req/min"
```

### 5. **Vector Search Tests**

#### Semantic Search
```
"Find similar products to 'wireless bluetooth headphones'"

"Search for documents about inventory forecasting"

"Find code examples similar to async data processing"

"Locate all contexts related to customer preferences"
```

#### Embedding Operations
```
"Generate embeddings for this product description"

"Compare similarity between these two text snippets"

"Index this document for semantic search"

"Find the most relevant context for this query"
```

### 6. **Workflow Engine Tests**

#### Workflow Creation
```
"Create a workflow for processing customer orders"

"Define a data validation pipeline"

"Set up an automated code review process"

"Build a multi-step analysis workflow"
```

#### Workflow Execution
```
"Execute the order processing workflow for order #12345"

"Run the data validation pipeline on uploaded CSV"

"Trigger the code review workflow for PR #456"

"Start the analysis workflow with custom parameters"
```

### 7. **Error Handling Tests**

#### Invalid Inputs
```
"Process this data: [intentionally malformed JSON]"

"Analyze code in unsupported language: COBOL"

"Search for non-existent context ID: xyz123"

"Execute undefined workflow: ghost-workflow"
```

#### Resource Limits
```
"Store 1GB of context data"

"Process 10000 items simultaneously"

"Create 1000 concurrent workflows"

"Search across 1 million vectors"
```

### 8. **Integration Tests**

#### External Service Integration
```
"Fetch current inventory from Zamaz API"

"Sync with Google Cloud Storage"

"Query Firestore for tenant data"

"Send results to Pub/Sub topic"
```

#### Cross-Agent Communication
```
"Have the code analyzer share results with documentation agent"

"Coordinate between data processor and quality checker"

"Pass planning output to implementation agent"

"Aggregate results from all active agents"
```

### 9. **Performance Tests**

#### Latency Tests
```
"Measure response time for simple query"

"Benchmark vector search with 1000 items"

"Time workflow execution end-to-end"

"Profile memory usage during operation"
```

#### Throughput Tests
```
"Process 100 requests in parallel"

"Handle sustained load of 50 req/sec"

"Batch process 1000 items"

"Stream real-time data for 5 minutes"
```

### 10. **Security and Access Control Tests**

#### Authentication
```
"Access restricted resource without auth"

"Validate my credentials"

"Refresh expired token"

"Switch user context"
```

#### Authorization
```
"Try to access another tenant's data"

"List my accessible resources"

"Request elevated permissions"

"Audit access logs"
```

## 📝 Example Test Scenarios

### Scenario 1: E-commerce Context Management
```
1. "Switch to tenant: zamaz-enterprise"
2. "Store product catalog with 1000 items"
3. "Find products similar to 'organic coffee beans'"
4. "Generate inventory forecast for next quarter"
5. "Create workflow for low-stock alerts"
```

### Scenario 2: Code Analysis Pipeline
```
1. "Analyze Python code in src/main.py"
2. "Identify performance bottlenecks"
3. "Suggest optimizations"
4. "Generate updated documentation"
5. "Create PR with improvements"
```

### Scenario 3: Data Processing Workflow
```
1. "Upload sales data CSV"
2. "Validate data format and integrity"
3. "Transform to normalized format"
4. "Calculate key metrics"
5. "Generate executive summary"
```

## 🔍 Validation Checklist

When testing, verify:
- [ ] Correct response format
- [ ] Appropriate error messages
- [ ] Response time within limits
- [ ] Context properly stored/retrieved
- [ ] Multi-tenant isolation working
- [ ] Tools executing correctly
- [ ] Agents collaborating effectively
- [ ] Memory persistence functioning
- [ ] Vector search returning relevant results
- [ ] Workflows completing successfully

## 🚀 Advanced Testing

### Load Testing
```python
# Simulate concurrent users
for i in range(100):
    "Process order #{i} with standard workflow"
```

### Stress Testing
```
"Generate and index 10000 random documents"
"Perform 1000 similarity searches"
"Create nested workflow with 50 steps"
```

### Chaos Testing
```
"Interrupt workflow execution at step 3"
"Simulate network timeout during API call"
"Force agent communication failure"
"Trigger out-of-memory condition"
```

## 💡 Tips for Effective Testing

1. **Start Simple**: Begin with basic operations before complex workflows
2. **Test Isolation**: Verify multi-tenant separation thoroughly
3. **Monitor Resources**: Watch memory and CPU during tests
4. **Document Issues**: Keep track of any unexpected behaviors
5. **Incremental Complexity**: Gradually increase test complexity
6. **Real-World Scenarios**: Test with actual use cases from Zamaz

## 🛠️ Debugging Commands

```
"Show debug info for last operation"
"Enable verbose logging"
"Trace workflow execution"
"Display system metrics"
"Export diagnostic report"
```

---

Remember: The current implementation has simulated responses. Once fully integrated with the MCP backend, these prompts will trigger actual AI processing, tool execution, and workflow management.