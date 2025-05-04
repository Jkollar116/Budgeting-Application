package org.example;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

/**
 * Utility class to diagnose Firebase connection issues
 * Run this to get detailed information about the Firebase configuration
 */
public class DiagnoseFirebase {
    
    public static void main(String[] args) {
        System.out.println("============== FIREBASE CONNECTION DIAGNOSTIC ==============");
        StringBuilder report = new StringBuilder();
        report.append("Firebase Connection Diagnostic Report\n");
        report.append("Generated: ").append(new Date()).append("\n\n");
        
        // Check Java version
        report.append("=== Java Environment ===\n");
        report.append("Java Version: ").append(System.getProperty("java.version")).append("\n");
        report.append("Java Home: ").append(System.getProperty("java.home")).append("\n");
        report.append("Working Directory: ").append(System.getProperty("user.dir")).append("\n\n");
        
        // Check credential files
        report.append("=== Credential Files ===\n");
        String[] possiblePaths = {
            "firebase-credentials.json", 
            "serviceAccountKey.json",
            "src/main/resources/serviceAccountKey.json",
            "src/main/resources/keys.json",
            "./serviceAccountKey.json"
        };
        
        boolean foundCredentialFile = false;
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                foundCredentialFile = true;
                report.append("✓ Found credential file: ").append(file.getAbsolutePath()).append("\n");
                report.append("  File size: ").append(file.length()).append(" bytes\n");
                report.append("  Last modified: ").append(new Date(file.lastModified())).append("\n");
                
                // Try to verify if it's a valid credential file
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    if (content.contains("\"type\": \"service_account\"")) {
                        report.append("  ✓ File contains service account configuration\n");
                        
                        // Try to load the credentials
                        try (FileInputStream serviceAccount = new FileInputStream(file)) {
                            GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccount);
                            report.append("  ✓ Successfully loaded credentials from file\n");
                        } catch (IOException e) {
                            report.append("  ✗ Failed to load credentials: ").append(e.getMessage()).append("\n");
                        }
                    } else {
                        report.append("  ✗ File does NOT contain service account configuration\n");
                    }
                } catch (IOException e) {
                    report.append("  ✗ Could not read file contents: ").append(e.getMessage()).append("\n");
                }
                report.append("\n");
            } else {
                report.append("✗ No credential file at: ").append(path).append("\n");
            }
        }
        
        if (!foundCredentialFile) {
            report.append("ERROR: No credential files found!\n\n");
        }
        
        // Check environment variable
        report.append("=== Environment Variables ===\n");
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null) {
            report.append("GOOGLE_APPLICATION_CREDENTIALS: ").append(envPath).append("\n");
            File envFile = new File(envPath);
            if (envFile.exists()) {
                report.append("✓ File exists at path specified by environment variable\n");
            } else {
                report.append("✗ File DOES NOT exist at path specified by environment variable\n");
            }
        } else {
            report.append("✗ GOOGLE_APPLICATION_CREDENTIALS environment variable not set\n");
        }
        report.append("\n");
        
        // Check network connectivity
        report.append("=== Network Connectivity ===\n");
        try {
            boolean canReachFirebase = java.net.InetAddress.getByName("firestore.googleapis.com").isReachable(5000);
            if (canReachFirebase) {
                report.append("✓ Can reach Firestore servers\n");
            } else {
                report.append("✗ Cannot reach Firestore servers\n");
            }
        } catch (Exception e) {
            report.append("✗ Network test failed: ").append(e.getMessage()).append("\n");
        }
        report.append("\n");
        
        // Recommended actions
        report.append("=== Recommended Actions ===\n");
        report.append("1. Ensure you have a valid service account key file with Firestore permissions\n");
        report.append("2. Place the file in one of the supported locations or set GOOGLE_APPLICATION_CREDENTIALS\n");
        report.append("3. Verify that your project has Firestore API enabled\n");
        report.append("4. Check that your service account has appropriate Firebase Admin permissions\n");
        
        // Print report
        System.out.println(report.toString());
        
        // Save report to file
        try {
            FileWriter writer = new FileWriter("firebase-diagnostic-report.txt");
            writer.write(report.toString());
            writer.close();
            System.out.println("\nDiagnostic report saved to firebase-diagnostic-report.txt");
        } catch (IOException e) {
            System.out.println("\nFailed to save diagnostic report: " + e.getMessage());
        }
        
        System.out.println("=============== END OF DIAGNOSTIC REPORT ===============");
    }
}
