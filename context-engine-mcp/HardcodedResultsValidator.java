import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Validator to check for hardcoded results that should be dynamic
 */
public class HardcodedResultsValidator {
    
    private static final String[] SUSPICIOUS_PATTERNS = {
        // Hardcoded status responses
        "\"status\"\\s*,\\s*\"healthy\"",
        "\"status\"\\s*:\\s*\"healthy\"",
        
        // Hardcoded metrics
        "\"total\"\\s*,\\s*[0-9]+",
        "\"active\"\\s*,\\s*[0-9]+",
        "\"requests\"\\s*,\\s*[0-9]+",
        
        // Hardcoded model names (not in configuration)
        "\"gemini-1\\.[0-9]-(pro|flash)\"",
        
        // Hardcoded test results
        "return\\s+\"[A-Z]+[_\\s]+[A-Z]+\"", // Like "TEST_PASSED"
        
        // Mock-like patterns
        "Mock[A-Z]\\w+",
        "@Mock",
        "when\\(.*\\).thenReturn",
        
        // Always-true conditions
        "return\\s+true;",
        "return\\s+false;"  // Sometimes these should be dynamic
    };
    
    private static final String[] WHITELIST_PATTERNS = {
        // Acceptable hardcoded values
        "application\\.yml",
        "test-credentials\\.json",
        "ConfigurationValidationTest",
        "ExceptionHandlingValidationTest",
        "toString\\(\\)",
        "enum\\s+\\w+",
        "static\\s+final"
    };
    
    public static void main(String[] args) {
        System.out.println("🔍 Hardcoded Results Validator");
        System.out.println("==============================");
        System.out.println();
        
        Map<String, List<String>> findings = new HashMap<>();
        
        // Scan main source code
        scanDirectory("src/main/java", findings);
        
        // Report findings
        if (findings.isEmpty()) {
            System.out.println("✅ NO HARDCODED RESULTS FOUND!");
            System.out.println("All responses appear to be dynamic.");
        } else {
            System.out.println("⚠️  HARDCODED RESULTS FOUND:");
            System.out.println("============================");
            
            for (Map.Entry<String, List<String>> entry : findings.entrySet()) {
                System.out.println();
                System.out.println("📄 " + entry.getKey());
                System.out.println("---" + "-".repeat(entry.getKey().length()));
                
                for (String issue : entry.getValue()) {
                    System.out.println("  ⚠️  " + issue);
                }
            }
            
            System.out.println();
            System.out.println("🔧 RECOMMENDATIONS:");
            System.out.println("===================");
            System.out.println("1. Replace hardcoded health status with actual health checks");
            System.out.println("2. Replace hardcoded metrics with real data from storage");
            System.out.println("3. Use ModelConfigurationService for dynamic model selection");
            System.out.println("4. Replace static responses with calculated values");
        }
        
        System.out.println();
        System.out.println("📊 VALIDATION COMPLETE");
        System.out.println("=====================");
    }
    
    private static void scanDirectory(String dirPath, Map<String, List<String>> findings) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            System.out.println("Directory not found: " + dirPath);
            return;
        }
        
        scanDirectoryRecursive(dir, findings);
    }
    
    private static void scanDirectoryRecursive(File dir, Map<String, List<String>> findings) {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectoryRecursive(file, findings);
            } else if (file.getName().endsWith(".java")) {
                scanFile(file, findings);
            }
        }
    }
    
    private static void scanFile(File file, Map<String, List<String>> findings) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            int lineNumber = 0;
            List<String> fileIssues = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                
                // Skip whitelisted patterns
                if (isWhitelisted(line, file.getName())) {
                    continue;
                }
                
                // Check for suspicious patterns
                for (String pattern : SUSPICIOUS_PATTERNS) {
                    Pattern p = Pattern.compile(pattern);
                    Matcher m = p.matcher(line);
                    
                    if (m.find()) {
                        String issue = String.format("Line %d: %s (Pattern: %s)", 
                            lineNumber, line.trim(), pattern);
                        fileIssues.add(issue);
                    }
                }
            }
            
            reader.close();
            
            if (!fileIssues.isEmpty()) {
                findings.put(file.getPath(), fileIssues);
            }
            
        } catch (IOException e) {
            System.err.println("Error reading file: " + file.getPath() + " - " + e.getMessage());
        }
    }
    
    private static boolean isWhitelisted(String line, String fileName) {
        for (String pattern : WHITELIST_PATTERNS) {
            if (line.contains(pattern) || fileName.contains(pattern)) {
                return true;
            }
        }
        return false;
    }
}