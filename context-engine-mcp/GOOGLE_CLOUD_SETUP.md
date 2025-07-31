# Google Cloud Setup for Real LLM Integration

## Prerequisites

1. Google Cloud Project with billing enabled
2. Google Cloud CLI (`gcloud`) installed
3. Vertex AI API enabled

## Setup Steps

### 1. Enable Vertex AI API

```bash
gcloud services enable aiplatform.googleapis.com
```

### 2. Set up Authentication

#### Option A: Application Default Credentials (Recommended for Development)

```bash
# Login to Google Cloud
gcloud auth login

# Set application default credentials
gcloud auth application-default login

# Set your project
gcloud config set project YOUR_PROJECT_ID
```

#### Option B: Service Account (Recommended for Production)

```bash
# Create a service account
gcloud iam service-accounts create zamaz-mcp-service \
    --display-name="Zamaz MCP Service Account"

# Grant necessary permissions
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
    --member="serviceAccount:zamaz-mcp-service@YOUR_PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/aiplatform.user"

# Create and download key
gcloud iam service-accounts keys create ~/zamaz-mcp-key.json \
    --iam-account=zamaz-mcp-service@YOUR_PROJECT_ID.iam.gserviceaccount.com

# Set environment variable
export GOOGLE_APPLICATION_CREDENTIALS=~/zamaz-mcp-key.json
```

### 3. Set Environment Variables

Add to your `.bashrc`, `.zshrc`, or `.env` file:

```bash
export GOOGLE_CLOUD_PROJECT=YOUR_PROJECT_ID
export GOOGLE_APPLICATION_CREDENTIALS=~/zamaz-mcp-key.json  # If using service account
```

### 4. Test the Setup

```bash
# Test with gcloud
gcloud ai models list --region=us-central1

# Test the API endpoint
curl http://localhost:8080/api/test-llm
```

## Available Models

- **gemini-1.5-flash-001**: Fast, cost-effective model for simple tasks
- **gemini-1.5-pro-001**: More capable model for complex tasks

## Cost Optimization

The service is configured to use:
- Flash model for general queries (cost-effective)
- Pro model for code analysis and data processing (when accuracy is critical)

## Troubleshooting

### 1. Authentication Issues
```bash
# Check current authentication
gcloud auth list

# Verify application default credentials
gcloud auth application-default print-access-token
```

### 2. API Not Enabled
```bash
# Enable required APIs
gcloud services enable aiplatform.googleapis.com
gcloud services enable compute.googleapis.com
```

### 3. Quota Issues
- Check your quotas in the Google Cloud Console
- Vertex AI has regional quotas - try different regions if needed

### 4. Connection Test
```bash
# Test the LLM connection from the application
curl http://localhost:8080/api/test-llm
```

Expected response:
```json
{
    "connected": true,
    "message": "Successfully connected to LLM service!"
}
```

## Environment Variables Summary

| Variable | Description | Example |
|----------|-------------|---------|
| GOOGLE_CLOUD_PROJECT | Your GCP project ID | my-project-123 |
| GOOGLE_APPLICATION_CREDENTIALS | Path to service account key | ~/zamaz-mcp-key.json |
| GOOGLE_CLOUD_LOCATION | Vertex AI location | us-central1 |

## Next Steps

1. Restart the Spring Boot application
2. Access the console at http://localhost:8080/console.html
3. Test real LLM responses with various prompts
4. Monitor costs in the Google Cloud Console