# Setting up Gemini API for Real AI Responses

Since Vertex AI models are not accessible in your project, you can use Google AI Studio's Gemini API instead. This is free for testing and provides access to the latest Gemini models.

## Quick Setup Steps:

1. **Get a Gemini API Key** (takes 1 minute):
   - Go to: https://makersuite.google.com/app/apikey
   - Click "Create API Key"
   - Copy the API key

2. **Set the API Key**:
   ```bash
   export GEMINI_API_KEY="your-api-key-here"
   ```

3. **Restart the Server**:
   ```bash
   # Kill current server
   lsof -ti:8080 | xargs kill -9
   
   # Start with API key
   export GOOGLE_CLOUD_PROJECT=zamaz-authentication
   export GEMINI_API_KEY="your-api-key-here"
   java -cp "target/classes:$(mvn dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)" com.zamaz.adk.ContextEngineMCPApplication
   ```

4. **Test the Chat**:
   - Open http://localhost:8080/console.html
   - Ask any question and get real AI responses!

## Available Models:
- `gemini-1.5-flash` - Fast responses, good for most tasks
- `gemini-1.5-pro` - More capable, better for complex tasks

## Alternative: Enable Vertex AI Models

If you prefer to use Vertex AI instead:

1. Go to: https://console.cloud.google.com/vertex-ai
2. Select project: zamaz-authentication
3. Click "Model Garden" in the left menu
4. Find "Gemini" models
5. Click "Enable" or "Get Started"
6. Accept terms of service
7. Wait a few minutes for activation

## Troubleshooting:

If Vertex AI models still don't work:
- Check billing is enabled: https://console.cloud.google.com/billing
- Verify APIs are enabled: https://console.cloud.google.com/apis/library
- Contact Google Cloud support for model access

The Gemini API (via API key) is the fastest way to get real AI responses working right now!