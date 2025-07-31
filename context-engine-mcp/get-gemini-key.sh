#!/bin/bash

echo "============================================"
echo "🔑 Setting up Gemini API Key"
echo "============================================"
echo ""
echo "To get real AI responses, you need a Gemini API key."
echo ""
echo "Step 1: Get your API key (takes 1 minute):"
echo "  1. Open: https://makersuite.google.com/app/apikey"
echo "  2. Click 'Create API Key'"
echo "  3. Copy the API key"
echo ""
echo "Step 2: When you have the key, paste it here:"
read -p "Enter your Gemini API key: " API_KEY

if [ -z "$API_KEY" ]; then
    echo "❌ No API key provided. Exiting."
    exit 1
fi

echo ""
echo "✅ API key received. Starting server with Gemini 2.5 Flash Lite..."
echo ""

# Export the environment variables and start the server
export GOOGLE_CLOUD_PROJECT=zamaz-authentication
export GEMINI_API_KEY="$API_KEY"

# Compile and run
echo "🔨 Building the application..."
mvn compile -q

echo "🚀 Starting server with real AI..."
java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.zamaz.adk.ContextEngineMCPApplication