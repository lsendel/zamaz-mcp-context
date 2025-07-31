#!/usr/bin/env node
/**
 * Google ADK Workflow MCP Server
 * Exposes all workflow, agent, and context features via MCP
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequest,
  ErrorCode,
  ListResourcesRequest,
  ListToolsRequest,
  McpError,
  ReadResourceRequest,
} from "@modelcontextprotocol/sdk/types.js";
import axios from 'axios';

const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8080/api/v1';

// Initialize MCP Server
const server = new Server(
  {
    name: "google-adk-workflow",
    version: "1.0.0",
  },
  {
    capabilities: {
      resources: {},
      tools: {},
    },
  }
);

// Define MCP Tools for all features with tenant support
const TOOLS = [
  // ========== WORKFLOW TOOLS ==========
  {
    name: "create_workflow",
    description: "Create a new workflow with nodes and edges using Google Vertex AI (tenant-aware)",
    inputSchema: {
      type: "object",
      properties: {
        organization_id: { type: "string", description: "Organization ID (required)" },
        project_id: { type: "string", description: "Project ID (optional)" },
        subproject_id: { type: "string", description: "Subproject ID (optional)" },
        name: { type: "string", description: "Workflow name" },
        nodes: {
          type: "array",
          items: {
            type: "object",
            properties: {
              id: { type: "string" },
              type: { type: "string" },
              model: { type: "string", enum: ["gemini-1.5-flash-001", "gemini-1.5-pro-001"] },
              config: { type: "object" }
            }
          }
        },
        edges: {
          type: "array",
          items: {
            type: "object",
            properties: {
              from: { type: "string" },
              to: { type: "string" },
              condition: { type: "string" }
            }
          }
        }
      },
      required: ["organization_id", "name", "nodes", "edges"]
    }
  },
  {
    name: "execute_workflow",
    description: "Execute a workflow with initial state (tenant-aware)",
    inputSchema: {
      type: "object",
      properties: {
        organization_id: { type: "string", description: "Organization ID (required)" },
        project_id: { type: "string", description: "Project ID (optional)" },
        subproject_id: { type: "string", description: "Subproject ID (optional)" },
        workflow_id: { type: "string" },
        initial_state: { type: "object" },
        start_node: { type: "string", default: "start" }
      },
      required: ["organization_id", "workflow_id", "initial_state"]
    }
  },

  // ========== AGENT TOOLS ==========
  {
    name: "orchestrate_agents",
    description: "Orchestrate multiple specialized agents for complex tasks (tenant-aware)",
    inputSchema: {
      type: "object",
      properties: {
        organization_id: { type: "string", description: "Organization ID (required)" },
        project_id: { type: "string", description: "Project ID (optional)" },
        subproject_id: { type: "string", description: "Subproject ID (optional)" },
        description: { type: "string", description: "Task description" },
        preferred_agents: {
          type: "array",
          items: {
            type: "string",
            enum: ["CODE_ANALYZER", "DOCUMENT_WRITER", "DATA_PROCESSOR", "SEARCH_AGENT", "PLANNING_AGENT", "QUALITY_CHECKER"]
          }
        },
        context: { type: "object", default: {} }
      },
      required: ["organization_id", "description"]
    }
  },
  {
    name: "get_agent_info",
    description: "Get information about a specific agent",
    inputSchema: {
      type: "object",
      properties: {
        agent_type: {
          type: "string",
          enum: ["CODE_ANALYZER", "DOCUMENT_WRITER", "DATA_PROCESSOR", "SEARCH_AGENT", "PLANNING_AGENT", "QUALITY_CHECKER"]
        }
      },
      required: ["agent_type"]
    }
  },
  {
    name: "clear_agent_context",
    description: "Clear the context window for a specific agent",
    inputSchema: {
      type: "object",
      properties: {
        agent_type: {
          type: "string",
          enum: ["CODE_ANALYZER", "DOCUMENT_WRITER", "DATA_PROCESSOR", "SEARCH_AGENT", "PLANNING_AGENT", "QUALITY_CHECKER"]
        }
      },
      required: ["agent_type"]
    }
  },

  // ========== TOOL SELECTION ==========
  {
    name: "select_tools",
    description: "Dynamically select tools based on query using embeddings",
    inputSchema: {
      type: "object",
      properties: {
        query: { type: "string" },
        max_tools: { type: "number", default: 5 },
        min_similarity: { type: "number", default: 0.7 },
        categories: { type: "array", items: { type: "string" } }
      },
      required: ["query"]
    }
  },
  {
    name: "index_tool",
    description: "Index a new tool with embeddings for dynamic selection",
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string" },
        description: { type: "string" },
        categories: { type: "array", items: { type: "string" } },
        input_schema: { type: "object" },
        metadata: { type: "object" }
      },
      required: ["name", "description"]
    }
  },

  // ========== MEMORY MANAGEMENT ==========
  {
    name: "store_context",
    description: "Store context in persistent memory with automatic offloading",
    inputSchema: {
      type: "object",
      properties: {
        session_id: { type: "string" },
        content: { type: "string" },
        metadata: { type: "object", default: {} }
      },
      required: ["session_id", "content"]
    }
  },
  {
    name: "retrieve_context",
    description: "Retrieve relevant context from memory using semantic search",
    inputSchema: {
      type: "object",
      properties: {
        session_id: { type: "string" },
        query: { type: "string" },
        max_entries: { type: "number", default: 10 },
        filters: { type: "object" }
      },
      required: ["session_id", "query"]
    }
  },

  // ========== CONTEXT VALIDATION ==========
  {
    name: "validate_context",
    description: "Detect context failures (poisoning, distraction, confusion, clash)",
    inputSchema: {
      type: "object",
      properties: {
        content: { type: "string" }
      },
      required: ["content"]
    }
  },
  {
    name: "mitigate_context",
    description: "Fix context issues using AI-powered mitigation",
    inputSchema: {
      type: "object",
      properties: {
        content: { type: "string" },
        issues: { type: "array", items: { type: "string" } }
      },
      required: ["content", "issues"]
    }
  },

  // ========== VECTOR STORE ==========
  {
    name: "vector_search",
    description: "Search documents using Vertex AI Vector Search",
    inputSchema: {
      type: "object",
      properties: {
        query: { type: "string" },
        limit: { type: "number", default: 10 },
        filters: { type: "object" }
      },
      required: ["query"]
    }
  },
  {
    name: "index_document",
    description: "Index a document with embeddings in Vertex AI Vector Search",
    inputSchema: {
      type: "object",
      properties: {
        content: { type: "string" },
        metadata: { type: "object", default: {} }
      },
      required: ["content"]
    }
  },

  // ========== PERFORMANCE ANALYSIS ==========
  {
    name: "analyze_performance",
    description: "Analyze workflow or agent performance metrics",
    inputSchema: {
      type: "object",
      properties: {
        entity_type: { type: "string", enum: ["workflow", "agent", "tool"] },
        entity_id: { type: "string" },
        time_range: { type: "string", default: "last_hour" }
      },
      required: ["entity_type", "entity_id"]
    }
  }
];

// Define MCP Resources
const RESOURCES = [
  {
    uri: "adk://workflows/list",
    name: "Available Workflows",
    description: "List of all created workflows",
    mimeType: "application/json"
  },
  {
    uri: "adk://agents/status",
    name: "Agent Status",
    description: "Current status of all agents",
    mimeType: "application/json"
  },
  {
    uri: "adk://tools/catalog",
    name: "Tool Catalog",
    description: "All indexed tools with embeddings",
    mimeType: "application/json"
  },
  {
    uri: "adk://memory/stats",
    name: "Memory Statistics",
    description: "Memory usage and performance stats",
    mimeType: "application/json"
  },
  {
    uri: "adk://performance/metrics",
    name: "Performance Metrics",
    description: "Real-time performance metrics",
    mimeType: "application/json"
  }
];

// Tool handlers
server.setRequestHandler(ListToolsRequest, async () => ({
  tools: TOOLS
}));

// Resource handlers
server.setRequestHandler(ListResourcesRequest, async () => ({
  resources: RESOURCES
}));

// Handle tool calls
server.setRequestHandler(CallToolRequest, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    let response;

    switch (name) {
      // ========== WORKFLOW OPERATIONS ==========
      case "create_workflow":
        const createPath = buildTenantPath(args, "workflow/create");
        response = await axios.post(`${API_BASE_URL}${createPath}`, {
          definition: {
            name: args.name,
            nodes: args.nodes,
            edges: args.edges.map((e: any) => ({
              from_node: e.from,
              to_node: e.to,
              condition: e.condition || "true"
            }))
          }
        });
        break;

      case "execute_workflow":
        const executePath = buildTenantPath(args, "workflow/execute");
        response = await axios.post(`${API_BASE_URL}${executePath}`, {
          workflow_id: args.workflow_id,
          initial_state: args.initial_state,
          start_node: args.start_node || "start"
        });
        break;

      // ========== AGENT OPERATIONS ==========
      case "orchestrate_agents":
        const orchestratePath = buildTenantPath(args, "agents/orchestrate");
        response = await axios.post(`${API_BASE_URL}${orchestratePath}`, {
          request_id: `mcp-${Date.now()}`,
          description: args.description,
          preferred_agents: args.preferred_agents || [],
          context: args.context || {}
        });
        break;

      case "get_agent_info":
        response = await axios.get(`${API_BASE_URL}/agents/${args.agent_type}/info`);
        break;

      case "clear_agent_context":
        response = await axios.post(`${API_BASE_URL}/agents/${args.agent_type}/clear-context`);
        break;

      // ========== TOOL SELECTION ==========
      case "select_tools":
        response = await axios.post(`${API_BASE_URL}/tools/select`, {
          query: args.query,
          max_tools: args.max_tools || 5,
          min_similarity: args.min_similarity || 0.7,
          categories: args.categories || []
        });
        break;

      case "index_tool":
        response = await axios.post(`${API_BASE_URL}/tools/index`, {
          tool: {
            name: args.name,
            description: args.description,
            categories: args.categories || [],
            input_schema: args.input_schema || {},
            metadata: args.metadata || {}
          }
        });
        break;

      // ========== MEMORY OPERATIONS ==========
      case "store_context":
        response = await axios.post(`${API_BASE_URL}/memory/store`, {
          session_id: args.session_id,
          content: args.content,
          metadata: args.metadata || {}
        });
        break;

      case "retrieve_context":
        response = await axios.post(`${API_BASE_URL}/memory/retrieve`, {
          session_id: args.session_id,
          query: args.query,
          max_entries: args.max_entries || 10,
          filter: args.filters || {}
        });
        break;

      // ========== CONTEXT VALIDATION ==========
      case "validate_context":
        response = await axios.post(`${API_BASE_URL}/context/validate`, {
          content: args.content
        });
        break;

      case "mitigate_context":
        response = await axios.post(`${API_BASE_URL}/context/mitigate`, {
          content: args.content,
          issues: args.issues
        });
        break;

      // ========== VECTOR OPERATIONS ==========
      case "vector_search":
        response = await axios.post(`${API_BASE_URL}/vectors/search`, {
          query: args.query,
          limit: args.limit || 10,
          filters: args.filters || {}
        });
        break;

      case "index_document":
        response = await axios.post(`${API_BASE_URL}/vectors/index`, {
          content: args.content,
          metadata: args.metadata || {}
        });
        break;

      // ========== PERFORMANCE ANALYSIS ==========
      case "analyze_performance":
        response = await axios.get(`${API_BASE_URL}/performance/${args.entity_type}/${args.entity_id}`, {
          params: { time_range: args.time_range || "last_hour" }
        });
        break;

      default:
        throw new McpError(
          ErrorCode.MethodNotFound,
          `Unknown tool: ${name}`
        );
    }

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(response.data, null, 2)
        }
      ]
    };

  } catch (error: any) {
    // Handle API errors gracefully
    const errorMessage = error.response?.data?.message || error.message;
    const errorDetails = {
      error: errorMessage,
      status: error.response?.status,
      tool: name,
      timestamp: new Date().toISOString()
    };

    return {
      content: [
        {
          type: "text",
          text: JSON.stringify(errorDetails, null, 2)
        }
      ]
    };
  }
});

// Handle resource reads
server.setRequestHandler(ReadResourceRequest, async (request) => {
  const { uri } = request.params;

  try {
    let data;

    switch (uri) {
      case "adk://workflows/list":
        const workflows = await axios.get(`${API_BASE_URL}/workflows`);
        data = workflows.data;
        break;

      case "adk://agents/status":
        const agents = await axios.get(`${API_BASE_URL}/agents/status`);
        data = agents.data;
        break;

      case "adk://tools/catalog":
        const tools = await axios.get(`${API_BASE_URL}/tools`);
        data = tools.data;
        break;

      case "adk://memory/stats":
        const memory = await axios.get(`${API_BASE_URL}/memory/stats`);
        data = memory.data;
        break;

      case "adk://performance/metrics":
        const metrics = await axios.get(`${API_BASE_URL}/performance/metrics`);
        data = metrics.data;
        break;

      default:
        throw new McpError(
          ErrorCode.InvalidRequest,
          `Unknown resource: ${uri}`
        );
    }

    return {
      contents: [
        {
          uri,
          mimeType: "application/json",
          text: JSON.stringify(data, null, 2)
        }
      ]
    };

  } catch (error: any) {
    throw new McpError(
      ErrorCode.InternalError,
      `Failed to read resource: ${error.message}`
    );
  }
});

// Start the server
async function main() {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  
  console.error("Google ADK Workflow MCP Server started");
  console.error(`API endpoint: ${API_BASE_URL}`);
  console.error(`Available tools: ${TOOLS.length}`);
  console.error(`Available resources: ${RESOURCES.length}`);
}

// Helper function to build tenant path
function buildTenantPath(args: any, endpoint: string): string {
  let path = "/org/" + args.organization_id;
  
  if (args.project_id) {
    path += "/project/" + args.project_id;
    
    if (args.subproject_id) {
      path += "/subproject/" + args.subproject_id;
    }
  }
  
  path += "/" + endpoint;
  return path;
}

main().catch((error) => {
  console.error("Server error:", error);
  process.exit(1);
});