# API Testing Guide for Context Engine MCP

## Prerequisites

Before testing the API, ensure the MCP server is running:

```bash
# Option 1: Using Makefile (recommended)
make run-server

# Option 2: Direct Maven command
cd context-engine-mcp
mvn spring-boot:run

# Option 3: Development mode with hot reload
make dev
```

## Testing Methods

### Method 1: Bash Script (Automated)

Run the comprehensive test script:

```bash
# From root directory
cd context-engine-mcp
./test-api.sh

# Or set custom base URL
BASE_URL=http://localhost:8080 ./test-api.sh
```

This script will test:
- Health endpoints
- MCP status and capabilities
- LLM providers and models
- Context optimization
- Completions and embeddings
- Vector search
- Multi-tenant features
- Metrics and monitoring

### Method 2: REST Client File (Interactive)

Use with VS Code REST Client extension or IntelliJ HTTP Client:

1. **VS Code**: Install "REST Client" extension by Huachao Mao
2. **IntelliJ**: Built-in HTTP client support

Open `context-engine-mcp/api-tests.http` and click "Send Request" on any test.

### Method 3: Manual cURL Commands

Test individual endpoints:

```bash
# Health check
curl http://localhost:8080/actuator/health

# MCP status
curl http://localhost:8080/mcp/status

# List LLM providers
curl http://localhost:8080/api/llm/providers

# Simple completion
curl -X POST http://localhost:8080/api/llm/complete \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "What is 2+2?",
    "model": "gemini-flash",
    "temperature": 0.7,
    "maxTokens": 50
  }'

# Context optimization
curl -X POST http://localhost:8080/api/context/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "content": "public class Example { /* Long comment */ }",
    "taskType": "code-review",
    "profile": "balanced"
  }'
```

## Quick Start Workflow

1. **Start the server**:
   ```bash
   make run-server
   ```

2. **In another terminal, run tests**:
   ```bash
   cd context-engine-mcp
   ./test-api.sh
   ```

## Expected Results

When running the test script, you should see output like:

```
🔍 Context Engine MCP - API Testing
===================================

Checking if server is running at http://localhost:8080...
✅ Server is running

=== Health & Status ===
Testing Health check... ✅ Success (200)
Response: {"status":"UP"}

Testing Application info... ✅ Success (200)

=== MCP Endpoints ===
Testing MCP status... ✅ Success (200)
Response: {"status":"operational","version":"1.0.0"}

=== LLM Provider Endpoints ===
Testing List LLM providers... ✅ Success (200)
Response: ["google-adk","openai","mock"]

...
```

## Troubleshooting

### Server Not Running
If you see "❌ Server is not running":
1. Check if port 8080 is already in use: `lsof -i :8080`
2. Start the server: `make run-server`
3. Check logs for startup errors

### Authentication Errors
If you get 401/403 errors with Google Cloud endpoints:
1. Ensure credentials are configured: `gcloud auth application-default login`
2. Set project: `export GOOGLE_CLOUD_PROJECT=zamaz-authentication`
3. Check credentials file exists: `ls ~/.config/gcloud/application_default_credentials.json`

### Connection Refused
If you get connection refused errors:
1. Verify the server is running on the expected port
2. Check firewall settings
3. Try `localhost` instead of `127.0.0.1`

## Advanced Testing

### Performance Testing
```bash
# Run concurrent requests
for i in {1..10}; do
  curl -X POST http://localhost:8080/api/llm/complete \
    -H "Content-Type: application/json" \
    -d '{"prompt": "Test '$i'", "model": "gemini-flash"}' &
done
wait
```

### Load Testing with Apache Bench
```bash
# Install ab (Apache Bench)
brew install httpd  # macOS

# Run load test
ab -n 100 -c 10 -p request.json -T application/json \
   http://localhost:8080/api/llm/complete
```

### Monitor Metrics
```bash
# View all metrics
curl http://localhost:8080/actuator/metrics

# View specific metric
curl http://localhost:8080/actuator/metrics/http.server.requests

# Prometheus format
curl http://localhost:8080/actuator/prometheus
```

## API Endpoints Reference

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Health check |
| `/mcp/status` | GET | MCP server status |
| `/mcp/capabilities` | GET | List MCP capabilities |
| `/api/llm/providers` | GET | List LLM providers |
| `/api/llm/models` | GET | List available models |
| `/api/llm/complete` | POST | Generate completion |
| `/api/context/optimize` | POST | Optimize context |
| `/api/embeddings/generate` | POST | Generate embeddings |
| `/api/search/semantic` | POST | Semantic search |
| `/api/organizations` | GET | List organizations |
| `/api/projects` | GET | List projects |
| `/api/queue/status` | GET | Queue status |
| `/actuator/metrics` | GET | Application metrics |

## Next Steps

After successful API testing:
1. Review metrics at `/actuator/metrics`
2. Check logs for any warnings
3. Test with your specific use cases
4. Monitor performance under load
5. Configure alerts for production