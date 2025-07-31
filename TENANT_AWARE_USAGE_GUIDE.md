# Tenant-Aware Google ADK Usage Guide

This guide demonstrates how to use the multi-tenant features of the Zamaz MCP Context Engine with organization, project, and subproject hierarchy.

## Overview

The system supports three levels of tenant isolation:
- **Organization**: Top-level tenant (e.g., "zamaz-enterprise")
- **Project**: Projects within an organization (e.g., "fba-optimization")
- **Subproject**: Subprojects within a project (e.g., "electronics-category")

## Claude/Claude Code Sample Prompts

### 1. Organization-Level Workflow Creation

```
Create a workflow for organization "zamaz-enterprise" that analyzes FBA inventory health across all warehouses. The workflow should:
1. Extract current inventory data
2. Analyze storage fees and age
3. Recommend actions for slow-moving items
```

Claude will use:
```typescript
create_workflow({
  organization_id: "zamaz-enterprise",
  name: "fba-inventory-health-analyzer",
  nodes: [...],
  edges: [...]
})
```

### 2. Project-Level Agent Orchestration

```
For the "fba-optimization" project under "zamaz-enterprise", orchestrate agents to:
- Analyze Q4 sales forecasts
- Calculate optimal reorder quantities
- Consider Prime Day and Black Friday demand spikes
- Generate a comprehensive inventory plan
```

Claude will use:
```typescript
orchestrate_agents({
  organization_id: "zamaz-enterprise",
  project_id: "fba-optimization",
  description: "Q4 inventory planning with seasonal analysis",
  preferred_agents: ["DATA_PROCESSOR", "PLANNING_AGENT"],
  context: { quarter: "Q4", events: ["Prime Day", "Black Friday"] }
})
```

### 3. Subproject-Level Tool Selection

```
In the "electronics-category" subproject, find tools that can help with:
- Competitor price monitoring
- Buy Box optimization
- Dynamic repricing strategies
```

Claude will use:
```typescript
select_tools({
  organization_id: "zamaz-enterprise",
  project_id: "fba-optimization",
  subproject_id: "electronics-category",
  query: "competitor pricing buy box optimization",
  categories: ["pricing", "amazon", "optimization"]
})
```

### 4. Complex E-commerce Workflow

```
Create a comprehensive FBA optimization workflow for Zamaz that:
1. Monitors inventory levels daily
2. Calculates reorder points based on lead times
3. Analyzes competitor pricing
4. Adjusts prices to maintain Buy Box
5. Generates removal orders for aged inventory
6. Produces weekly performance reports

This should run at the organization level but be configurable per project.
```

### 5. Cross-Tenant Data Analysis

```
Compare performance metrics across all projects under "zamaz-enterprise":
- Show inventory turnover by project
- Identify which projects have the highest storage fees
- Recommend inventory redistribution strategies
```

### 6. Memory Management with Context

```
Store this strategic planning session for the FBA optimization project:
"Q4 2024 focus: Electronics category represents 40% of revenue. Key risks include tariff changes and shipping delays. Opportunities: Prime Day in October, Black Friday expansion. Target: $10M revenue with 25% margin."

Then later: "What were the key risks we identified for Q4 in electronics?"
```

### 7. Tenant Migration Request

```
Create a new project under "zamaz-enterprise" called "eu-expansion" for managing European FBA operations. Set it up with workflows for:
- VAT calculation
- Multi-country inventory distribution
- Currency hedging analysis
```

## REST API Usage Examples

### Organization Level
```bash
# Create workflow at org level
curl -X POST http://localhost:8080/api/v1/org/zamaz-enterprise/workflow/create \
  -H "Content-Type: application/json" \
  -d '{
    "definition": {
      "name": "inventory-analyzer",
      "nodes": [...],
      "edges": [...]
    }
  }'

# List all workflows for organization
curl http://localhost:8080/api/v1/org/zamaz-enterprise/workflows
```

### Project Level
```bash
# Execute workflow at project level
curl -X POST http://localhost:8080/api/v1/org/zamaz-enterprise/project/fba-optimization/workflow/execute \
  -H "Content-Type: application/json" \
  -d '{
    "workflow_id": "zamaz-enterprise_fba-optimization_inventory-analyzer_abc123",
    "initial_state": {
      "analysis_type": "comprehensive"
    }
  }'
```

### Subproject Level
```bash
# Orchestrate agents at subproject level
curl -X POST http://localhost:8080/api/v1/org/zamaz-enterprise/project/fba-optimization/subproject/electronics-category/agents/orchestrate \
  -H "Content-Type: application/json" \
  -d '{
    "request_id": "electronics-q4-analysis",
    "description": "Analyze electronics category performance",
    "preferred_agents": ["DATA_PROCESSOR", "PLANNING_AGENT"]
  }'
```

## Tenant Access Control

### Hierarchical Access
- Organization admins can access all projects and subprojects
- Project managers can access all subprojects within their project
- Subproject users can only access their specific subproject

### Example Access Patterns
```
# Organization admin lists everything
GET /api/v1/org/zamaz-enterprise/workflows

# Project manager lists project and subproject workflows
GET /api/v1/org/zamaz-enterprise/project/fba-optimization/workflows

# Subproject user accesses only their subproject
GET /api/v1/org/zamaz-enterprise/project/fba-optimization/subproject/electronics-category/workflows
```

## Quota Management

Different tiers have different limits:

| Tier | Workflows | Agent Requests/Day | Storage |
|------|-----------|-------------------|---------|
| Free | 10 | 1,000 | 1GB |
| Standard | 100 | 10,000 | 10GB |
| Enterprise | 1,000 | 100,000 | 100GB |

## Best Practices

1. **Use Appropriate Tenant Level**
   - Organization: Company-wide processes
   - Project: Department or initiative-specific workflows
   - Subproject: Team or category-specific operations

2. **Resource Naming**
   - Include tenant context in workflow names
   - Use descriptive names that indicate purpose

3. **Context Isolation**
   - Sensitive data stays within tenant boundaries
   - Cross-tenant access requires explicit permissions

4. **Performance Optimization**
   - Resources are cached per tenant
   - Quotas prevent resource exhaustion

## Advanced Features

### Custom Project Mapping
Organizations can map to custom GCP projects:
```java
// In TenantAwareService
"zamaz-enterprise" -> "zamaz-enterprise-prod"
"zamaz-dev" -> "zamaz-dev-project"
```

### Tenant-Specific Configuration
```json
{
  "organization_id": "zamaz-enterprise",
  "tier": "enterprise",
  "config": {
    "preferred_region": "us-central1",
    "custom_model_enabled": true,
    "quota_multiplier": 2.0,
    "features": ["advanced_analytics", "custom_workflows"]
  }
}
```

### Audit Logging
All operations are logged with tenant context:
```
AUDIT: Tenant=zamaz-enterprise/fba-optimization, Operation=workflow.execute, Details=Workflow: inventory-analyzer
```

## Troubleshooting

### Common Issues

1. **Quota Exceeded**
   ```
   Error: Daily agent request limit exceeded for tenant zamaz-enterprise/fba-optimization (limit: 10000)
   ```
   Solution: Upgrade tier or wait for quota reset

2. **Access Denied**
   ```
   Error: Tenant zamaz-enterprise/project-a does not have access to resource owned by zamaz-enterprise/project-b
   ```
   Solution: Ensure correct tenant context in requests

3. **Workflow Not Found**
   ```
   Error: Workflow not found: xyz123
   ```
   Solution: Verify workflow ID includes correct tenant prefix

## Integration with Existing Systems

### With Context Engine MCP
The tenant-aware system integrates with the existing Context Engine MCP:
- Code classification uses tenant-specific models
- Cost optimization considers tenant quotas
- Memory is isolated per tenant

### With Zamaz FBA Systems
- Real-time inventory data from FBA
- Automated reorder generation
- Price optimization with Buy Box tracking
- Multi-marketplace support

## Future Enhancements

1. **Cross-Organization Federation**
   - Share workflows between partner organizations
   - Federated agent orchestration

2. **Dynamic Tenant Creation**
   - Self-service tenant provisioning
   - Automatic resource allocation

3. **Advanced Analytics**
   - Tenant-specific ML models
   - Predictive quota management

4. **Global Distribution**
   - Multi-region tenant deployment
   - Automatic failover and replication