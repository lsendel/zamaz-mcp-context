import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Real Generative AI Test using Vertex AI - NO MOCKS
 * Tests actual Google Cloud generative models
 */
public class RealGenerativeAITest {
    private static final String PROJECT_ID = "zamaz-authentication";
    private static final String LOCATION = "us-central1";
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Real Generative AI Test - NO MOCKS");
        System.out.println("=====================================");
        System.out.println("Project: " + PROJECT_ID);
        System.out.println("Location: " + LOCATION);
        
        // Get auth token
        String token = getAuthToken();
        System.out.println("✅ Authentication successful");
        System.out.println();
        
        // Test with text-bison model (PaLM 2)
        testTextGeneration(token);
        
        // Test embeddings
        testEmbeddings(token);
        
        // Test with curl command to verify
        testWithCurl();
    }
    
    private static void testTextGeneration(String token) throws Exception {
        System.out.println("1. Testing Text Generation (PaLM 2 / text-bison)");
        System.out.println("-----------------------------------------------");
        
        String endpoint = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/text-bison:predict",
            LOCATION, PROJECT_ID, LOCATION
        );
        
        // Build request
        String prompt = "What is 2+2? Answer in one number only.";
        String jsonRequest = String.format(
            "{\"instances\":[{\"prompt\":\"%s\"}],\"parameters\":{\"temperature\":0.1,\"maxOutputTokens\":10}}",
            prompt
        );
        
        try {
            String response = makeAPICall(endpoint, jsonRequest, token);
            System.out.println("Prompt: " + prompt);
            System.out.println("Response: " + response);
            System.out.println("✅ Text generation working");
        } catch (Exception e) {
            System.out.println("❌ Text generation failed: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testEmbeddings(String token) throws Exception {
        System.out.println("2. Testing Real Embeddings (textembedding-gecko)");
        System.out.println("-----------------------------------------------");
        
        String endpoint = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/textembedding-gecko:predict",
            LOCATION, PROJECT_ID, LOCATION
        );
        
        String text = "public void sendEmailNotification()";
        String jsonRequest = String.format(
            "{\"instances\":[{\"content\":\"%s\"}]}",
            text
        );
        
        try {
            String response = makeAPICall(endpoint, jsonRequest, token);
            System.out.println("Text: " + text);
            
            // Check if response contains embeddings
            if (response.contains("embeddings")) {
                System.out.println("✅ Embeddings generated successfully");
                // Extract embedding size
                int start = response.indexOf("[");
                int end = response.indexOf("]", start);
                if (start > 0 && end > start) {
                    String embeddingStr = response.substring(start, end + 1);
                    int dimensions = embeddingStr.split(",").length;
                    System.out.println("Embedding dimensions: " + dimensions);
                }
            } else {
                System.out.println("Response: " + response);
            }
        } catch (Exception e) {
            System.out.println("❌ Embedding generation failed: " + e.getMessage());
        }
        System.out.println();
    }
    
    private static void testWithCurl() {
        System.out.println("3. Testing with curl command");
        System.out.println("----------------------------");
        System.out.println("Run this command to verify:");
        System.out.println();
        System.out.println("curl -X POST \\");
        System.out.println("  -H \"Authorization: Bearer $(gcloud auth application-default print-access-token)\" \\");
        System.out.println("  -H \"Content-Type: application/json\" \\");
        System.out.println("  https://us-central1-aiplatform.googleapis.com/v1/projects/zamaz-authentication/locations/us-central1/publishers/google/models/text-bison:predict \\");
        System.out.println("  -d '{\"instances\":[{\"prompt\":\"Hello\"}]}'");
        System.out.println();
    }
    
    private static String makeAPICall(String endpoint, String jsonRequest, String token) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(jsonRequest);
        }
        
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String response = br.lines().reduce("", (a, b) -> a + b);
            if (responseCode != 200) {
                throw new Exception("API call failed (" + responseCode + "): " + response);
            }
            return response;
        }
    }
    
    private static String getAuthToken() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "application-default", "print-access-token");
        Process p = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String token = reader.readLine();
            if (token == null || token.isEmpty()) {
                throw new Exception("Failed to get auth token");
            }
            return token.trim();
        }
    }
}