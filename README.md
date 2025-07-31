# Context Engine MCP - Small LLM Integration

A Model Context Protocol (MCP) server that provides cost-effective code analysis using small LLMs (Gemini Flash) for initial processing, with intelligent routing to larger models only when necessary.

## 🚀 Features

- **Code Classification**: Instant classification of code types and purposes using Gemini Flash (40x cheaper than Claude)
- **Smart Code Pruning**: Remove boilerplate while preserving essential logic
- **Dependency Detection**: Fast extraction of code dependencies
- **Two-Tier Summarization**: Gemini for simple code, Claude for complex scenarios
- **Context Optimization**: Achieve 70%+ token reduction for LLM processing
- **Cost Tracking**: Real-time cost estimation and optimization

## 💰 Cost Savings

| Operation | Using Claude | Using Gemini Flash | Savings |
|-----------|--------------|-------------------|---------|
| Code Classification | $0.00052 | $0.000013 | 40x |
| Dependency Detection | $0.00076 | $0.000019 | 40x |
| Code Pruning | $0.00104 | $0.000026 | 40x |
| Daily Operations (26k) | $45.00 | $13.50 | 70% |

## 🛠️ Quick Start

### Installation

```bash
# Clone repository
git clone https://github.com/zamaz/context-engine-mcp.git
cd context-engine-mcp

# Install dependencies
npm install

# Build MCP server
npm run build

# Test locally
npm run dev
```

### Configure for Claude Desktop

Add to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "context-engine": {
      "command": "node",
      "args": ["/path/to/context-engine-mcp/build/mcp-server.js"]
    }
  }
}
```

### Configure for Claude Code (VS Code)

Add to VS Code settings:

```json
{
  "claude.mcp.servers": {
    "context-engine": {
      "command": "node",
      "args": ["/path/to/context-engine-mcp/build/mcp-server.js"]
    }
  }
}
```

## 📝 Usage Examples

### Via MCP (in Claude)

```
Classify this code:
public class UserService implements IUserService { 
    void createUser(User u) { db.save(u); } 
}
```

### Via REST API

```bash
curl -X POST http://localhost:8080/api/code/classify \
  -H "Content-Type: application/json" \
  -d '{
    "code": "public class UserService { }",
    "model": "gemini-flash"
  }'
```

## 🧪 Sample Prompts

### Basic Classification
```
What type of code is this and what's its purpose?
[paste your code]
```

### Smart Pruning
```
Remove all boilerplate from this code, keeping only the payment processing logic:
[paste verbose code]
```

### Dependency Analysis
```
List all the services and classes this code depends on:
[paste your service class]
```

### Cost-Aware Processing
```
I need to analyze 10,000 Java files. What's the most cost-effective approach?
Show me the cost breakdown for different operations.
```

## 🏗️ Architecture

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────┐
│   Claude/User   │────▶│  MCP Server  │────▶│ Gemini Flash│
└─────────────────┘     └──────┬───────┘     └─────────────┘
                               │                      │
                               │ Complex?             │ 99% of requests
                               ▼                      │
                        ┌──────────────┐              │
                        │   Claude-3   │◀─────────────┘
                        └──────────────┘
                          1% of requests
```

## 📊 Available Tools

| Tool | Description | Cost |
|------|-------------|------|
| `classify_code` | Classify code type and purpose | $0.000013 |
| `prune_code` | Remove boilerplate, keep essential logic | $0.000026 |
| `detect_dependencies` | Extract code dependencies | $0.000019 |
| `summarize_code` | Two-tier intelligent summarization | $0.000008-$0.000158 |
| `optimize_context` | Reduce tokens by 70%+ | $0.000020 |
| `estimate_cost` | Calculate processing costs | Free |

## 🔧 Advanced Features

### Batch Processing
Process multiple files efficiently:
```
Process these 5 files: classify each, detect dependencies, and estimate total cost
```

### Context Optimization
Reduce token usage dramatically:
```
Optimize this 10KB file to fit in a 3KB context window
```

### Model Routing
Automatic selection of the most cost-effective model:
```
Process this code with the best model for my budget of $0.001
```

## 📈 Performance

- **Classification**: 50ms average response time
- **Pruning**: 75% average size reduction
- **Dependencies**: 99% accuracy on standard Java code
- **Context Optimization**: 70-85% token reduction
- **Cost**: 40x cheaper than using Claude for everything

## 🤝 Contributing

We welcome contributions! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## 📄 License

MIT License - see [LICENSE](LICENSE) for details.

## 🔗 Links

- [Installation Guide](INSTALLATION_GUIDE.md)
- [API Documentation](docs/API.md)
- [MCP Protocol Spec](https://modelcontextprotocol.io)
- [Google Cloud Setup](docs/GOOGLE_CLOUD_SETUP.md)

## 💡 Pro Tips

1. **Start with Gemini Flash** for all initial processing
2. **Use batch operations** to reduce API calls
3. **Monitor costs** with the `estimate_cost` tool
4. **Cache classifications** for repeated code patterns
5. **Combine operations** for efficiency (classify + prune + summarize)

---

Built with ❤️ by Zamaz for the developer community