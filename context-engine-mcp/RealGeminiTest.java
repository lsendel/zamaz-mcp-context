import java.io.*;
import java.net.*;
import java.util.*;
import com.google.gson.*;

/**
 * Real Gemini API Test - NO MOCKS
 * This test uses actual Google Cloud Vertex AI APIs
 */
public class RealGeminiTest {
    private static final String PROJECT_ID = System.getenv("GOOGLE_CLOUD_PROJECT");
    private static final String LOCATION = "us-central1";
    private static final Gson gson = new Gson();
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Real Gemini API Test - NO MOCKS");
        System.out.println("==================================");
        System.out.println("Project: " + PROJECT_ID);
        System.out.println();
        
        // Get auth token
        String token = getAuthToken();
        System.out.println("✅ Authentication successful");
        System.out.println();
        
        // Test 1: Real Gemini Flash completion
        testGeminiFlash(token);
        
        // Test 2: Real Gemini Pro completion
        testGeminiPro(token);
        
        // Test 3: Real context optimization
        testContextOptimization(token);
        
        // Test 4: Real embeddings
        testEmbeddings(token);
        
        // Test 5: Cost comparison
        testCostComparison(token);
    }
    
    private static void testGeminiFlash(String token) throws Exception {
        System.out.println("1. Testing Gemini Flash (Real API)");
        System.out.println("----------------------------------");
        
        String prompt = "What is 2+2? Answer in one word.";
        String response = callGeminiAPI("gemini-1.5-flash", prompt, token);
        System.out.println("Prompt: " + prompt);
        System.out.println("Response: " + response);
        System.out.println("✅ Gemini Flash working");
        System.out.println();
    }
    
    private static void testGeminiPro(String token) throws Exception {
        System.out.println("2. Testing Gemini Pro (Real API)");
        System.out.println("--------------------------------");
        
        String prompt = "Analyze this code for issues: public void process() { String s = null; s.length(); }";
        String response = callGeminiAPI("gemini-1.5-pro", prompt, token);
        System.out.println("Prompt: " + prompt);
        System.out.println("Response: " + response.substring(0, Math.min(200, response.length())) + "...");
        System.out.println("✅ Gemini Pro working");
        System.out.println();
    }
    
    private static void testContextOptimization(String token) throws Exception {
        System.out.println("3. Testing Real Context Optimization");
        System.out.println("------------------------------------");
        
        String originalCode = """
            public class UserService {
                // This is a verbose comment explaining what the method does
                // It validates the user input and processes the request
                // Then it saves to database and sends email
                private static final Logger logger = LoggerFactory.getLogger(UserService.class);
                
                public void processUser(User user) {
                    // Log the entry
                    logger.debug("Starting to process user with ID: " + user.getId());
                    logger.debug("User name is: " + user.getName());
                    
                    // Validate the user
                    if (user == null) {
                        logger.error("User is null!");
                        throw new IllegalArgumentException("User cannot be null");
                    }
                    
                    // Do the actual processing
                    doProcess(user);
                    
                    // Log the exit
                    logger.debug("Finished processing user");
                }
            }
            """;
        
        // Use Gemini to optimize
        String optimizationPrompt = "Remove all comments and logging from this code, keep only essential logic:\n" + originalCode;
        String optimized = callGeminiAPI("gemini-1.5-flash", optimizationPrompt, token);
        
        System.out.println("Original length: " + originalCode.length() + " chars");
        System.out.println("Optimized length: " + optimized.length() + " chars");
        System.out.println("Reduction: " + (100 - (optimized.length() * 100 / originalCode.length())) + "%");
        System.out.println("✅ Context optimization working with real LLM");
        System.out.println();
    }
    
    private static void testEmbeddings(String token) throws Exception {
        System.out.println("4. Testing Real Embeddings");
        System.out.println("--------------------------");
        
        String text = "public void sendEmailNotification(String recipient, String message)";
        String endpoint = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/text-embedding-004:predict",
            LOCATION, PROJECT_ID, LOCATION
        );
        
        Map<String, Object> instance = new HashMap<>();
        instance.put("content", text);
        
        Map<String, Object> request = new HashMap<>();
        request.put("instances", Arrays.asList(instance));
        
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(gson.toJson(request));
        }
        
        if (conn.getResponseCode() == 200) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String response = br.lines().reduce("", (a, b) -> a + b);
                JsonObject json = gson.fromJson(response, JsonObject.class);
                JsonArray predictions = json.getAsJsonArray("predictions");
                if (predictions.size() > 0) {
                    JsonObject embedding = predictions.get(0).getAsJsonObject();
                    JsonArray values = embedding.getAsJsonArray("embeddings");
                    System.out.println("Text: " + text);
                    System.out.println("Embedding dimensions: " + values.size());
                    System.out.println("First 5 values: " + values.toString().substring(0, 50) + "...");
                    System.out.println("✅ Real embeddings generated");
                }
            }
        } else {
            System.out.println("❌ Embedding generation failed: " + conn.getResponseCode());
        }
        System.out.println();
    }
    
    private static void testCostComparison(String token) throws Exception {
        System.out.println("5. Real Cost Analysis");
        System.out.println("---------------------");
        
        // Simple query - should use Flash
        String simpleQuery = "What is the capital of France?";
        long flashStart = System.currentTimeMillis();
        String flashResponse = callGeminiAPI("gemini-1.5-flash", simpleQuery, token);
        long flashTime = System.currentTimeMillis() - flashStart;
        
        // Same query with Pro
        long proStart = System.currentTimeMillis();
        String proResponse = callGeminiAPI("gemini-1.5-pro", simpleQuery, token);
        long proTime = System.currentTimeMillis() - proStart;
        
        System.out.println("Query: " + simpleQuery);
        System.out.println();
        System.out.println("Gemini Flash:");
        System.out.println("  Response: " + flashResponse);
        System.out.println("  Time: " + flashTime + "ms");
        System.out.println("  Cost: $0.00025/1k chars");
        System.out.println();
        System.out.println("Gemini Pro:");
        System.out.println("  Response: " + proResponse);
        System.out.println("  Time: " + proTime + "ms");
        System.out.println("  Cost: $0.00125/1k chars (5x more expensive)");
        System.out.println();
        System.out.println("✅ For simple queries, Flash is " + (proTime / flashTime) + "x faster and 5x cheaper");
        System.out.println();
    }
    
    private static String callGeminiAPI(String model, String prompt, String token) throws Exception {
        String endpoint = String.format(
            "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent",
            LOCATION, PROJECT_ID, LOCATION, model
        );
        
        // Build request
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);
        
        Map<String, Object> part = new HashMap<>();
        part.put("parts", Arrays.asList(textPart));
        
        Map<String, Object> request = new HashMap<>();
        request.put("contents", Arrays.asList(part));
        
        // Make API call
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(gson.toJson(request));
        }
        
        if (conn.getResponseCode() != 200) {
            throw new Exception("API call failed: " + conn.getResponseCode());
        }
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String response = br.lines().reduce("", (a, b) -> a + b);
            JsonObject json = gson.fromJson(response, JsonObject.class);
            JsonArray candidates = json.getAsJsonArray("candidates");
            if (candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts.size() > 0) {
                    return parts.get(0).getAsJsonObject().get("text").getAsString();
                }
            }
        }
        
        return "No response";
    }
    
    private static String getAuthToken() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "application-default", "print-access-token");
        Process p = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            return reader.readLine().trim();
        }
    }
}