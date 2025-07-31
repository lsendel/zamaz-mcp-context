# Context Engine MCP - Installation Guide

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Configuration for Claude Desktop](#configuration-for-claude-desktop)
4. [Configuration for Claude Code](#configuration-for-claude-code)
5. [Testing the Integration](#testing-the-integration)
6. [Sample Prompts](#sample-prompts)
7. [Troubleshooting](#troubleshooting)

## Prerequisites

- Node.js 16+ installed
- npm or yarn package manager
- Claude Desktop app (for Claude integration)
- VS Code with Claude Code extension (for Claude Code integration)
- Google Cloud credentials (optional, for real API calls)

## Installation

### Step 1: Clone the Repository
```bash
git clone https://github.com/zamaz/context-engine-mcp.git
cd context-engine-mcp
```

### Step 2: Install Dependencies
```bash
npm install
```

### Step 3: Build the MCP Server
```bash
npm run build
```

### Step 4: Test the Server Locally
```bash
npm run dev
```

## Configuration for Claude Desktop

### Step 1: Locate Claude Configuration
Claude Desktop stores its configuration in:
- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`
- **Linux**: `~/.config/Claude/claude_desktop_config.json`

### Step 2: Edit Configuration
Open the configuration file and add the Context Engine MCP server:

```json
{
  "mcpServers": {
    "context-engine": {
      "command": "node",
      "args": [
        "/absolute/path/to/context-engine-mcp/build/mcp-server.js"
      ],
      "env": {
        "GOOGLE_CLOUD_PROJECT": "your-project-id",
        "NODE_ENV": "production"
      }
    }
  }
}
```

### Step 3: Restart Claude Desktop
1. Quit Claude Desktop completely
2. Restart the application
3. The Context Engine MCP should now be available

### Step 4: Verify Installation
In Claude, type:
```
What MCP tools are available?
```

You should see tools like:
- `classify_code`
- `prune_code`
- `detect_dependencies`
- `summarize_code`
- `optimize_context`
- `estimate_cost`

## Configuration for Claude Code

### Step 1: Install Claude Code Extension
1. Open VS Code
2. Go to Extensions (Cmd/Ctrl + Shift + X)
3. Search for "Claude Code"
4. Install the official extension

### Step 2: Configure MCP in VS Code
1. Open VS Code settings (Cmd/Ctrl + ,)
2. Search for "Claude MCP"
3. Add the Context Engine configuration:

```json
{
  "claude.mcp.servers": {
    "context-engine": {
      "command": "node",
      "args": [
        "/absolute/path/to/context-engine-mcp/build/mcp-server.js"
      ],
      "env": {
        "GOOGLE_CLOUD_PROJECT": "your-project-id"
      }
    }
  }
}
```

### Step 3: Reload VS Code Window
- Press Cmd/Ctrl + Shift + P
- Run "Developer: Reload Window"

## Testing the Integration

### Basic Test
Ask Claude:
```
Use the classify_code tool to analyze this code:
public class UserService implements IUserService { 
    void createUser(User u) { db.save(u); } 
}
```

### Expected Response
```json
{
  "type": "service",
  "purpose": "user management",
  "complexity": "medium",
  "dependencies": ["IUserService", "User", "db"],
  "model_used": "gemini-flash",
  "cost": "$0.000013",
  "confidence": 0.92
}
```

## Sample Prompts

### 1. Code Classification
```
Classify these Java classes and tell me their purposes:
1. @RestController public class UserController { }
2. public interface IRepository<T> { T findById(Long id); }
3. @Service public class EmailService { }
```

### 2. Code Pruning
```
I have this verbose Java code. Can you prune it to keep only the logic for user authentication?

[paste your code]
```

### 3. Dependency Analysis
```
Analyze the dependencies in this OrderService class and identify what other services it needs.

[paste your code]
```

### 4. Smart Summarization
```
Summarize what this complex algorithm does. Use the two-tier summarization to ensure accuracy.

[paste complex code]
```

### 5. Context Optimization
```
I need to send this code to an LLM but it's too long. Optimize it to achieve 70% reduction while keeping the important parts.

[paste large code block]
```

### 6. Cost Estimation
```
Estimate the cost of processing 10,000 Java files (average 500 chars each) for:
- Classification
- Dependency detection
- Summarization

Compare costs between Gemini Flash and Claude.
```

### 7. Batch Operations
```
Process these files:
1. Classify UserService.java
2. Prune OrderController.java for order processing logic
3. Detect dependencies in PaymentService.java
4. Summarize ComplexAlgorithm.java
```

### 8. Real-World Scenario
```
I'm analyzing a large Java codebase. For each service class:
1. Classify its type and purpose
2. Extract its dependencies
3. Create a brief summary
4. Estimate the cost if I process 1000 similar files

Start with this UserService class: [paste code]
```

## Advanced Usage

### Working with Projects
```
Analyze my entire project structure:
1. Read all Java files in src/main/java
2. Classify each file
3. Build a dependency graph
4. Identify the most complex classes
5. Estimate total processing cost
```

### Optimization Strategies
```
I have a 10KB Java file that I need to send to Claude. 
1. First, prune obvious boilerplate
2. Then optimize the context for 70% reduction
3. Show me the cost savings compared to sending the full file
```

### Integration with Development Workflow
```
Create a code review assistant that:
1. Classifies incoming code changes
2. Detects new dependencies
3. Summarizes what changed
4. Estimates the complexity increase
```

## Troubleshooting

### MCP Server Not Found
```bash
# Verify the server is built
ls -la /path/to/context-engine-mcp/build/mcp-server.js

# Test manually
node /path/to/context-engine-mcp/build/mcp-server.js
```

### Tools Not Available in Claude
1. Check Claude's MCP connection status
2. Restart Claude Desktop
3. Check logs in: `~/Library/Logs/Claude/` (macOS)

### Permission Errors
```bash
# Make sure the script is executable
chmod +x /path/to/context-engine-mcp/build/mcp-server.js
```

### Debugging MCP Communication
```bash
# Run server in debug mode
DEBUG=mcp:* node /path/to/context-engine-mcp/build/mcp-server.js
```

## Environment Variables

Optional environment variables for enhanced functionality:

```bash
# Google Cloud project for real API calls
export GOOGLE_CLOUD_PROJECT=your-project-id

# API keys (if not using gcloud auth)
export GOOGLE_API_KEY=your-api-key
export OPENAI_API_KEY=your-openai-key

# Cost limits
export MAX_DAILY_COST=10.00
export COST_ALERT_THRESHOLD=5.00

# Performance settings
export PARALLEL_OPERATIONS=true
export MAX_BATCH_SIZE=100
```

## Next Steps

1. **Explore Resources**: Ask Claude to "show me the available MCP resources"
2. **Check Examples**: Use `read_resource` with `context://examples/classification`
3. **Monitor Usage**: Check `context://stats/usage` for cost tracking
4. **Customize Models**: Modify `context://config/models` for your needs

## Support

For issues or questions:
- GitHub Issues: https://github.com/zamaz/context-engine-mcp/issues
- Documentation: https://docs.zamaz.com/context-engine
- MCP Protocol Docs: https://modelcontextprotocol.io