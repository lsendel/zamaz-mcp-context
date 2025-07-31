# 🧪 Testing Guide for Zamaz MCP with Real LLMs

## Overview
This guide explains how to test the Zamaz MCP system with real LLM integration using production-ready code and Playwright for end-to-end testing.

## Key Features
- ✅ **No hardcoded responses** - All responses come from real Gemini models
- ✅ **Production-ready code** - Uses Google Cloud AI Platform SDK properly
- ✅ **Environment-based configuration** - No hardcoded credentials or settings
- ✅ **Playwright E2E tests** - Automated browser testing with screenshots/videos
- ✅ **Comprehensive error handling** - Graceful fallbacks and user-friendly errors

## Setup Instructions

### 1. Google Cloud Configuration

```bash
# Set up authentication
export GOOGLE_CLOUD_PROJECT=zamaz-authentication
gcloud auth application-default login

# Or use service account
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json
```

### 2. Environment Variables

Create a `.env` file:
```env
GOOGLE_CLOUD_PROJECT=zamaz-authentication
GCP_LOCATION=us-central1
GCP_MODEL=gemini-1.5-flash
```

### 3. Start the Server

```bash
# Run with Maven
mvn spring-boot:run

# Or use the Makefile
make run-server
```

### 4. Run E2E Tests

```bash
# Install Playwright browsers (first time only)
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install"

# Run the tests
./run-e2e-tests.sh

# Or run specific test
mvn test -Dtest=ChatConsoleE2ETest
```

## Test Scenarios

### 1. Basic Chat Test
```javascript
// Open console
http://localhost:8080/console.html

// Test prompts:
"What tools are available?"
"Analyze this code: function add(a, b) { return a + b; }"
"Create a plan for implementing user authentication"
```

### 2. MCP Commands Test
```javascript
// Context management
"Store this context: Project name is Zamaz MCP"
"Retrieve my stored context"
"Clear all contexts"

// Tool usage
"List available tools"
"Use the calculator tool to compute 15% of 1200"

// Vector search
"Find similar products to wireless headphones"
```

### 3. Multi-Agent Test
```javascript
// Switch between agents and test their specializations
1. General Assistant: "What is the weather like?"
2. Code Analyzer: "Review this Python function for best practices"
3. Data Processor: "Convert this CSV to JSON format"
4. Planning Agent: "Create a roadmap for Q1 2025"
5. Quality Checker: "Validate this API response format"
```

## Verification Checklist

### ✅ Real LLM Integration
- [ ] Responses are dynamic and contextual
- [ ] No "demo mode" or hardcoded messages
- [ ] Responses vary based on input
- [ ] Multi-turn conversations work correctly

### ✅ Authentication Working
- [ ] Google Cloud credentials are loaded
- [ ] API calls authenticate successfully
- [ ] No 401/403 errors in logs

### ✅ Error Handling
- [ ] Network failures show user-friendly messages
- [ ] Invalid inputs don't crash the system
- [ ] Timeout errors are handled gracefully
- [ ] Rate limits are respected

### ✅ Performance
- [ ] Responses arrive within 2-5 seconds
- [ ] UI remains responsive during API calls
- [ ] Multiple concurrent requests work

## Monitoring

### Check Server Logs
```bash
tail -f server.log | grep -E "LLM|API|ERROR"
```

### Check API Health
```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/test-llm
```

### View Test Results
After running E2E tests:
- Screenshots: `target/screenshots/`
- Videos: `target/videos/`
- Reports: `target/surefire-reports/`

## Troubleshooting

### Issue: "Failed to connect to LLM service"
```bash
# Check credentials
gcloud auth application-default print-access-token

# Verify project
echo $GOOGLE_CLOUD_PROJECT

# Test API directly
curl -X POST \
  https://us-central1-aiplatform.googleapis.com/v1/projects/${GOOGLE_CLOUD_PROJECT}/locations/us-central1/publishers/google/models/gemini-1.5-flash:generateContent \
  -H "Authorization: Bearer $(gcloud auth application-default print-access-token)" \
  -H "Content-Type: application/json" \
  -d '{"contents": [{"parts": [{"text": "Hello"}]}]}'
```

### Issue: "Port 8080 already in use"
```bash
# Find and kill process
lsof -ti:8080 | xargs kill -9
```

### Issue: "Playwright tests failing"
```bash
# Run in headed mode for debugging
CI="" mvn test -Dtest=ChatConsoleE2ETest

# Check browser console
# Screenshots are saved automatically on failure
```

## Production Deployment

### 1. Use Service Account
```bash
# Create and configure service account
gcloud iam service-accounts create zamaz-mcp-prod
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:zamaz-mcp-prod@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

### 2. Configure Spring Profile
```bash
# Run with production profile
java -jar target/context-engine-mcp.jar \
  --spring.profiles.active=production \
  --gcp.project-id=$PROJECT_ID
```

### 3. Enable Monitoring
- Set up Cloud Logging
- Configure alerts for API errors
- Monitor token usage and costs

## Cost Optimization

### Model Selection
- **gemini-1.5-flash**: Use for general queries (cheapest)
- **gemini-1.5-pro**: Use for complex analysis (more expensive)

### Token Management
- Implement response caching
- Limit max tokens per request
- Batch similar requests

### Monitoring Usage
```bash
# Check Vertex AI usage
gcloud ai models list-operations --region=us-central1
```

## Next Steps

1. ✅ Server is running with real LLMs
2. ✅ E2E tests are passing
3. ✅ No hardcoded responses
4. ✅ Production-ready architecture

Ready for deployment! 🚀