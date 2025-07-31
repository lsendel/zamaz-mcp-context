import java.io.*;
import java.net.*;

public class CheckAvailableModels {
    public static void main(String[] args) throws Exception {
        System.out.println("🔍 Checking Available Vertex AI Models");
        System.out.println("=====================================");
        
        String project = System.getenv("GOOGLE_CLOUD_PROJECT");
        if (project == null) project = "zamaz-authentication";
        
        System.out.println("Project: " + project);
        
        // Get auth token
        ProcessBuilder pb = new ProcessBuilder("gcloud", "auth", "application-default", "print-access-token");
        Process p = pb.start();
        String token;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            token = reader.readLine().trim();
        }
        
        // Try different model variations
        String[] models = {
            "gemini-1.5-flash",
            "gemini-1.5-flash-001", 
            "gemini-1.5-flash-002",
            "gemini-1.5-pro",
            "gemini-1.5-pro-001",
            "gemini-1.5-pro-002",
            "gemini-1.0-pro",
            "gemini-1.0-pro-001",
            "gemini-1.0-pro-002",
            "gemini-pro",
            "text-bison",
            "text-bison-001"
        };
        
        for (String model : models) {
            System.out.print("Testing " + model + "... ");
            
            String endpoint = String.format(
                "https://us-central1-aiplatform.googleapis.com/v1/projects/%s/locations/us-central1/publishers/google/models/%s:generateContent",
                project, model
            );
            
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                
                String json = "{\"contents\":[{\"parts\":[{\"text\":\"Hi\"}]}]}";
                try (OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream())) {
                    writer.write(json);
                }
                
                int code = conn.getResponseCode();
                if (code == 200) {
                    System.out.println("✅ Available");
                } else if (code == 404) {
                    System.out.println("❌ Not found");
                } else {
                    System.out.println("⚠️  Status: " + code);
                }
                
                conn.disconnect();
            } catch (Exception e) {
                System.out.println("❌ Error: " + e.getMessage());
            }
        }
    }
}