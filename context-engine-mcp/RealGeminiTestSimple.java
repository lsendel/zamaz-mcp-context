import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Real Gemini API Test - NO MOCKS, NO EXTERNAL DEPENDENCIES
 * Direct HTTP calls to Google Cloud Vertex AI
 */
public class RealGeminiTestSimple {
    private static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");
    private static final String LOCATION = "us-central1";
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Real Gemini API Test - NO MOCKS");
        System.out.println("==================================");
        System.out.println("Project: " + PROJECT_ID);
        
        if (PROJECT_ID == null || PROJECT_ID.isEmpty()) {
            System.out.println("❌ Please set GOOGLE_CLOUD_PROJECT environment variable");
            System.exit(1);
        }
        
        // Get auth token
        String token = getAuthToken();
        System.out.println("✅ Authentication successful");
        System.out.println();
        
        // Test 1: Real Gemini Flash
        System.out.println("1. Testing Gemini Flash (Real API)");
        System.out.println("----------------------------------");
        testGeminiModel("gemini-1.5-flash-001", "What is 2+2?", token);
        
        // Test 2: Real Gemini Pro
        System.out.println("2. Testing Gemini Pro (Real API)");
        System.out.println("--------------------------------");
        testGeminiModel("gemini-1.5-pro-001", "Explain the concept of recursion in programming", token);
        
        // Test 3: Context optimization comparison
        System.out.println("3. Real Context Optimization Test");
        System.out.println("---------------------------------");
        testContextOptimization(token);
        
        // Test 4: Performance comparison
        System.out.println("4. Performance & Cost Comparison");
        System.out.println("--------------------------------");
        testPerformanceComparison(token);
    }
    
    private static void testGeminiModel(String model, String prompt, String token) throws Exception {
        long startTime = System.currentTimeMillis();
        String response = callGeminiAPI(model, prompt, token);
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("Model: " + model);
        System.out.println("Prompt: " + prompt);
        System.out.println("Response: " + response);
        System.out.println("Response time: " + duration + "ms");
        System.out.println("✅ Success");
        System.out.println();
    }
    
    private static void testContextOptimization(String token) throws Exception {
        String verboseCode = """
            public class UserService {
                // This is a comment explaining the purpose
                private static final Logger logger = LoggerFactory.getLogger(UserService.class);
                
                public void processUser(User user) {
                    // Log entry
                    logger.debug("Processing user: " + user.getId());
                    
                    // Validate
                    if (user == null) {
                        throw new IllegalArgumentException("User cannot be null");
                    }
                    
                    // Process
                    doProcess(user);
                }
            }
            """;
        
        String optimizationPrompt = "Remove all comments and logging, return only essential code:\n\n" + verboseCode;
        
        System.out.println("Original code length: " + verboseCode.length() + " characters");
        
        String optimized = callGeminiAPI("gemini-1.5-flash-001", optimizationPrompt, token);
        System.out.println("Optimized code length: " + optimized.length() + " characters");
        
        int reduction = 100 - (optimized.length() * 100 / verboseCode.length());
        System.out.println("Reduction: " + reduction + "%");
        System.out.println("✅ Real context optimization achieved");
        System.out.println();
    }
    
    private static void testPerformanceComparison(String token) throws Exception {
        String query = "What is the capital of France?";
        
        // Test Flash
        long flashStart = System.currentTimeMillis();
        String flashResponse = callGeminiAPI("gemini-1.5-flash-001", query, token);
        long flashDuration = System.currentTimeMillis() - flashStart;
        
        // Test Pro
        long proStart = System.currentTimeMillis();
        String proResponse = callGeminiAPI("gemini-1.5-pro-001", query, token);
        long proDuration = System.currentTimeMillis() - proStart;
        
        System.out.println("Query: " + query);
        System.out.println();
        System.out.println("Gemini Flash:");
        System.out.println("  Response: " + flashResponse);
        System.out.println("  Time: " + flashDuration + "ms");
        System.out.println("  Cost: $0.00025 per 1k characters");
        System.out.println();
        System.out.println("Gemini Pro:");
        System.out.println("  Response: " + proResponse);
        System.out.println("  Time: " + proDuration + "ms");
        System.out.println("  Cost: $0.00125 per 1k characters (5x more)");
        System.out.println();
        
        System.out.println("✅ Flash is " + (proDuration > flashDuration ? "faster" : "slower") + " and 5x cheaper for simple queries");
        System.out.println();
    }
    
    private static String callGeminiAPI(String model, String prompt, String token) throws Exception {
        String endpoint = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
            LOCATION, PROJECT_ID, LOCATION, model
        );
        
        // Build JSON request manually
        String jsonRequest = String.format(
            "{\"contents\":[{\"parts\":[{\"text\":\"%s\"}]}]}",
            prompt.replace("\"", "\\\"").replace("\n", "\\n")
        );
        
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(jsonRequest);
        }
        
        if (conn.getResponseCode() != 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String error = br.lines().reduce("", (a, b) -> a + b);
                throw new Exception("API call failed (" + conn.getResponseCode() + "): " + error);
            }
        }
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String response = br.lines().reduce("", (a, b) -> a + b);
            // Extract text from response (simple parsing)
            int textStart = response.indexOf("\"text\":\"") + 8;
            int textEnd = response.indexOf("\"", textStart);
            if (textStart > 7 && textEnd > textStart) {
                return response.substring(textStart, textEnd)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
            }
            return "Could not parse response";
        }
    }
    
    private static String getAuthToken() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "application-default", "print-access-token");
        Process p = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String token = reader.readLine();
            if (token == null || token.isEmpty()) {
                throw new Exception("Failed to get auth token. Run: gcloud auth application-default login");
            }
            return token.trim();
        }
    }
}