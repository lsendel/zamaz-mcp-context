#\!/bin/bash

# Set up environment for testing
export GOOGLE_CLOUD_PROJECT=zamaz-authentication
export GOOGLE_APPLICATION_CREDENTIALS=~/.config/gcloud/application_default_credentials.json

echo "Environment configured:"
echo "PROJECT: $GOOGLE_CLOUD_PROJECT"
echo "CREDENTIALS: $GOOGLE_APPLICATION_CREDENTIALS"

# Enable required APIs
echo ""
echo "Enabling required APIs..."
gcloud services enable aiplatform.googleapis.com storage-component.googleapis.com

echo ""
echo "Ready to run tests\!"
