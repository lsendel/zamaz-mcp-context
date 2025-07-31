#!/bin/bash

# API Testing Script for Context Engine MCP

echo "🔍 Context Engine MCP - API Testing"
echo "==================================="
echo ""

# Set defaults
BASE_URL=${BASE_URL:-"http://localhost:8080"}
PROJECT_ID=${GOOGLE_CLOUD_PROJECT:-"zamaz-authentication"}

# Function to test endpoint
test_endpoint() {
    local method=$1
    local endpoint=$2
    local data=$3
    local description=$4
    
    echo -n "Testing $description... "
    
    if [ -z "$data" ]; then
        response=$(curl -s -X $method "$BASE_URL$endpoint" -w "\n%{http_code}")
    else
        response=$(curl -s -X $method "$BASE_URL$endpoint" \
            -H "Content-Type: application/json" \
            -d "$data" \
            -w "\n%{http_code}")
    fi
    
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | head -n -1)
    
    if [[ $http_code -ge 200 && $http_code -lt 300 ]]; then
        echo "✅ Success ($http_code)"
        if [ ! -z "$body" ]; then
            echo "Response: $body" | head -n 3
        fi
    else
        echo "❌ Failed ($http_code)"
        if [ ! -z "$body" ]; then
            echo "Error: $body"
        fi
    fi
    echo ""
}

# Check if server is running
echo "Checking if server is running at $BASE_URL..."
if curl -s -f "$BASE_URL/actuator/health" > /dev/null 2>&1; then
    echo "✅ Server is running"
else
    echo "❌ Server is not running"
    echo ""
    echo "Start the server with: make run-server"
    echo "Or: cd context-engine-mcp && mvn spring-boot:run"
    exit 1
fi
echo ""

# Test health endpoints
echo "=== Health & Status ==="
test_endpoint "GET" "/actuator/health" "" "Health check"
test_endpoint "GET" "/actuator/info" "" "Application info"

# Test MCP endpoints
echo "=== MCP Endpoints ==="
test_endpoint "GET" "/mcp/status" "" "MCP status"
test_endpoint "GET" "/mcp/capabilities" "" "MCP capabilities"

# Test LLM endpoints
echo "=== LLM Provider Endpoints ==="
test_endpoint "GET" "/api/llm/providers" "" "List LLM providers"
test_endpoint "GET" "/api/llm/models" "" "List available models"

# Test context optimization
echo "=== Context Optimization ==="
optimize_data='{
  "content": "public class Example { /* Long comment */ public void test() { System.out.println(\"test\"); } }",
  "taskType": "code-review",
  "profile": "balanced"
}'
test_endpoint "POST" "/api/context/optimize" "$optimize_data" "Context optimization"

# Test completion
echo "=== LLM Completion ==="
completion_data='{
  "prompt": "What is 2+2?",
  "model": "gemini-flash",
  "temperature": 0.7,
  "maxTokens": 50
}'
test_endpoint "POST" "/api/llm/complete" "$completion_data" "LLM completion"

# Test embeddings
echo "=== Embeddings ==="
embedding_data='{
  "text": "public void sendEmail(String to, String subject)",
  "model": "text-embedding-004"
}'
test_endpoint "POST" "/api/embeddings/generate" "$embedding_data" "Generate embeddings"

# Test vector search
echo "=== Vector Search ==="
search_data='{
  "query": "function to send notifications",
  "limit": 5,
  "projectId": "'$PROJECT_ID'"
}'
test_endpoint "POST" "/api/search/semantic" "$search_data" "Semantic search"

# Test multi-tenant
echo "=== Multi-Tenant ==="
test_endpoint "GET" "/api/organizations" "" "List organizations"
test_endpoint "GET" "/api/projects?orgId=test-org" "" "List projects"

# Test metrics
echo "=== Metrics & Monitoring ==="
test_endpoint "GET" "/actuator/metrics" "" "Available metrics"
test_endpoint "GET" "/actuator/metrics/http.server.requests" "" "HTTP request metrics"

echo "==================================="
echo "API testing complete!"