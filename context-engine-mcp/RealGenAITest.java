import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Real Google Generative AI Test - NO MOCKS
 * Using the generativelanguage API directly
 */
public class RealGenAITest {
    // You need to set this API key
    private static final String API_KEY = System.getenv("GOOGLE_API_KEY");
    
    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Real Google Generative AI Test - NO MOCKS");
        System.out.println("===========================================");
        
        if (API_KEY == null || API_KEY.isEmpty()) {
            System.out.println("⚠️  GOOGLE_API_KEY environment variable not set");
            System.out.println();
            System.out.println("To get an API key:");
            System.out.println("1. Go to https://makersuite.google.com/app/apikey");
            System.out.println("2. Create a new API key");
            System.out.println("3. Run: export GOOGLE_API_KEY='your-key-here'");
            System.out.println();
            System.out.println("Alternatively, we can use OpenAI API if you have OPENAI_API_KEY set");
            
            testOpenAI();
            return;
        }
        
        // Test with Gemini Pro via Generative AI API
        testGeminiPro();
    }
    
    private static void testGeminiPro() throws Exception {
        System.out.println("Testing Gemini Pro via Generative AI API");
        System.out.println("----------------------------------------");
        
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=" + API_KEY;
        
        String jsonRequest = """
            {
              "contents": [{
                "parts":[{
                  "text": "What is 2+2? Answer with just the number."
                }]
              }]
            }
            """;
        
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
            writer.write(jsonRequest);
        }
        
        int responseCode = conn.getResponseCode();
        InputStream inputStream = (responseCode == 200) ? conn.getInputStream() : conn.getErrorStream();
        
        try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
            String response = br.lines().reduce("", (a, b) -> a + b);
            System.out.println("Response: " + response);
            
            if (responseCode == 200) {
                System.out.println("✅ Gemini Pro API working!");
            } else {
                System.out.println("❌ API call failed: " + responseCode);
            }
        }
    }
    
    private static void testOpenAI() {
        String openAIKey = System.getenv("OPENAI_API_KEY");
        if (openAIKey != null && !openAIKey.isEmpty()) {
            System.out.println();
            System.out.println("OpenAI API key detected. You can test with OpenAI instead.");
            System.out.println("Create a similar test using https://api.openai.com/v1/chat/completions");
        }
    }
}