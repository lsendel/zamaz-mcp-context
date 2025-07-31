#!/bin/bash

echo "Testing Vertex AI Connection..."
echo ""

# Test 1: List available models
echo "1. Listing Vertex AI models..."
gcloud ai models list --region=us-central1 --limit=5

# Test 2: Check if you can access Vertex AI
echo ""
echo "2. Checking Vertex AI access..."
curl -X GET \
  -H "Authorization: Bearer $(gcloud auth application-default print-access-token)" \
  -H "Content-Type: application/json" \
  "https://us-central1-aiplatform.googleapis.com/v1/projects/zamaz-authentication/locations/us-central1/models"

echo ""
echo "✅ If you see models listed above, Vertex AI is working!"