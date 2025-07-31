#!/bin/bash

echo "🚀 Google Cloud Setup for Zamaz MCP"
echo "=================================="
echo ""

# Check if gcloud is installed
if ! command -v gcloud &> /dev/null; then
    echo "❌ Error: gcloud CLI is not installed."
    echo "Please install it from: https://cloud.google.com/sdk/docs/install"
    exit 1
fi

echo "✅ gcloud CLI found"

# Check current authentication
echo ""
echo "Current authentication status:"
gcloud auth list

# Set up application default credentials
echo ""
echo "Setting up application default credentials..."
read -p "Do you want to set up application default credentials? (y/n): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    gcloud auth application-default login
fi

# Get project ID
echo ""
CURRENT_PROJECT=$(gcloud config get-value project 2>/dev/null)
if [ -z "$CURRENT_PROJECT" ]; then
    echo "No project currently set."
    read -p "Enter your Google Cloud Project ID: " PROJECT_ID
    gcloud config set project $PROJECT_ID
else
    echo "Current project: $CURRENT_PROJECT"
    read -p "Use this project? (y/n): " -n 1 -r
    echo ""
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        read -p "Enter your Google Cloud Project ID: " PROJECT_ID
        gcloud config set project $PROJECT_ID
    else
        PROJECT_ID=$CURRENT_PROJECT
    fi
fi

# Enable required APIs
echo ""
echo "Enabling required APIs..."
gcloud services enable aiplatform.googleapis.com --project=$PROJECT_ID
gcloud services enable compute.googleapis.com --project=$PROJECT_ID

# Test authentication
echo ""
echo "Testing authentication..."
if gcloud auth application-default print-access-token &> /dev/null; then
    echo "✅ Authentication successful!"
else
    echo "❌ Authentication failed. Please run: gcloud auth application-default login"
fi

# Create .env file
echo ""
echo "Creating .env file..."
cat > .env << EOF
# Google Cloud Configuration
GOOGLE_CLOUD_PROJECT=$PROJECT_ID
GOOGLE_CLOUD_LOCATION=us-central1

# Optional: If using service account
# GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json
EOF

echo "✅ Created .env file with project configuration"

# Test Vertex AI access
echo ""
echo "Testing Vertex AI access..."
gcloud ai models list --region=us-central1 --project=$PROJECT_ID --limit=1 &> /dev/null
if [ $? -eq 0 ]; then
    echo "✅ Vertex AI access confirmed!"
else
    echo "⚠️  Could not access Vertex AI. Check your permissions."
fi

echo ""
echo "✅ Setup complete!"
echo ""
echo "Next steps:"
echo "1. Start the application: make run-server"
echo "2. Test LLM connection: curl http://localhost:8080/api/test-llm"
echo "3. Access the console: http://localhost:8080/console.html"
echo ""
echo "If you encounter issues, check GOOGLE_CLOUD_SETUP.md for troubleshooting."