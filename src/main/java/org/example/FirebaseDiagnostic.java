package org.example;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Diagnostic tool for Firebase authentication issues
 */
public class FirebaseDiagnostic {
    
    public static void testFirebaseConnection() {
        System.out.println("============ FIREBASE DIAGNOSTIC TEST ============");
        
        try {
            // Step 1: Check if we have the credentials file
            System.out.println("Step 1: Checking credential files...");
            checkCredentialFiles();
            
            // Step 2: Try to load credentials
            System.out.println("\nStep 2: Trying to load credentials...");
            GoogleCredentials credentials = loadCredentials();
            
            if (credentials == null) {
                System.out.println("FAILED: Could not load any valid credentials");
                return;
            }
            
            System.out.println("SUCCESS: Credentials loaded successfully");
            
            // Step 3: Initialize Firebase
            System.out.println("\nStep 3: Initializing Firebase...");
            initializeFirebase(credentials);
            
            // Step 4: Test Firestore operations
            System.out.println("\nStep 4: Testing Firestore operations...");
            testFirestoreOperations();
            
            System.out.println("\n============ DIAGNOSTIC TEST COMPLETE ============");
        } catch (Exception e) {
            System.out.println("FAILED: Unexpected error during diagnostic test: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void checkCredentialFiles() {
        String[] possiblePaths = {
            "firebase-credentials.json", 
            "serviceAccountKey.json",
            "src/main/resources/serviceAccountKey.json",
            "./serviceAccountKey.json"
        };
        
        boolean found = false;
        
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                System.out.println("  ✓ Found credential file at: " + file.getAbsolutePath());
                System.out.println("    File size: " + file.length() + " bytes");
                System.out.println("    Last modified: " + new java.util.Date(file.lastModified()));
                found = true;
            } else {
                System.out.println("  ✗ No credential file at: " + path);
            }
        }
        
        if (!found) {
            System.out.println("FAILED: No credential files found!");
        }
        
        // Check environment variable
        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null) {
            System.out.println("  GOOGLE_APPLICATION_CREDENTIALS environment variable: " + envPath);
            File envFile = new File(envPath);
            if (envFile.exists()) {
                System.out.println("  ✓ File exists at path specified by environment variable");
            } else {
                System.out.println("  ✗ File DOES NOT exist at path specified by environment variable");
            }
        } else {
            System.out.println("  ✗ GOOGLE_APPLICATION_CREDENTIALS environment variable not set");
        }
    }
    
    private static GoogleCredentials loadCredentials() {
        GoogleCredentials credentials = null;
        
        // Method 1: Try environment variable GOOGLE_APPLICATION_CREDENTIALS
        try {
            String credPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (credPath != null && !credPath.isEmpty()) {
                System.out.println("  Trying to load credentials from environment variable: " + credPath);
                File file = new File(credPath);
                if (file.exists()) {
                    try (FileInputStream serviceAccount = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount);
                        System.out.println("  ✓ Successfully loaded credentials from environment variable");
                        return credentials;
                    } catch (IOException e) {
                        System.out.println("  ✗ Failed to load credentials from environment variable: " + e.getMessage());
                    }
                } else {
                    System.out.println("  ✗ Credentials file specified in environment variable does not exist: " + credPath);
                }
            }
        } catch (Exception e) {
            System.out.println("  ✗ Error checking environment variable: " + e.getMessage());
        }
        
        // Method 2: Try explicit file paths
        String[] possiblePaths = {
            "firebase-credentials.json", 
            "serviceAccountKey.json",
            "src/main/resources/serviceAccountKey.json",
            "./serviceAccountKey.json"
        };
        
        for (String path : possiblePaths) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    System.out.println("  Trying credentials file at: " + file.getAbsolutePath());
                    try (FileInputStream serviceAccount = new FileInputStream(file)) {
                        credentials = GoogleCredentials.fromStream(serviceAccount);
                        System.out.println("  ✓ Successfully loaded credentials from: " + path);
                        
                        // Print some info about the credentials
                        System.out.println("  - Credentials type: " + credentials.getClass().getName());
                        
                        return credentials;
                    } catch (IOException e) {
                        System.out.println("  ✗ Failed to load credentials from " + path + ": " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.out.println("  ✗ Error checking path " + path + ": " + e.getMessage());
            }
        }
        
        // Method 3: Try application default credentials
        try {
            System.out.println("  Trying application default credentials");
            credentials = GoogleCredentials.getApplicationDefault();
            System.out.println("  ✓ Successfully loaded application default credentials");
            return credentials;
        } catch (IOException e) {
            System.out.println("  ✗ Failed to load application default credentials: " + e.getMessage());
        }
        
        return null;
    }
    
    private static void initializeFirebase(GoogleCredentials credentials) {
        try {
            // Only initialize if not already initialized
            if (FirebaseApp.getApps().isEmpty()) {
                System.out.println("  Initializing Firebase with credentials");
                
                String projectId = "cashclimb-d162c"; // Your project ID
                String databaseUrl = "https://" + projectId + ".firebaseio.com";
                
                // Create FirebaseOptions with explicit project ID and credentials
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .setDatabaseUrl(databaseUrl)
                        .setProjectId(projectId)
                        .build();
                
                // Initialize the Firebase app
                FirebaseApp app = FirebaseApp.initializeApp(options);
                System.out.println("  ✓ Firebase app initialized with name: " + app.getName());
            } else {
                System.out.println("  Firebase already initialized with " + FirebaseApp.getApps().size() + " apps");
            }
        } catch (Exception e) {
            System.out.println("  ✗ Failed to initialize Firebase: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testFirestoreOperations() {
        try {
            // Get Firestore instance
            Firestore db = FirestoreClient.getFirestore();
            System.out.println("  ✓ Got Firestore instance successfully");
            
            // Try a simple write to a test document
            String testId = "test-" + UUID.randomUUID().toString().substring(0, 8);
            DocumentReference docRef = db.collection("system").document(testId);
            Map<String, Object> testData = new HashMap<>();
            testData.put("timestamp", FieldValue.serverTimestamp());
            testData.put("message", "Diagnostic test at " + new java.util.Date());
            
            System.out.println("  Attempting to write to Firestore...");
            WriteResult writeResult = docRef.set(testData).get();
            System.out.println("  ✓ Test document written at: " + writeResult.getUpdateTime());
            
            // Try to read the document back
            System.out.println("  Attempting to read from Firestore...");
            DocumentSnapshot snapshot = docRef.get().get();
            if (snapshot.exists()) {
                System.out.println("  ✓ Successfully read test document");
                
                // Try to delete the document (cleanup)
                System.out.println("  Cleaning up test document...");
                docRef.delete().get();
                System.out.println("  ✓ Test document deleted");
            } else {
                System.out.println("  ✗ Test document not found after writing");
            }
            
        } catch (ExecutionException e) {
            System.out.println("  ✗ Error during Firestore operations: " + e.getMessage());
            if (e.getCause() != null) {
                System.out.println("  ✗ Cause: " + e.getCause().getMessage());
                e.getCause().printStackTrace();
            }
        } catch (Exception e) {
            System.out.println("  ✗ Unexpected error during Firestore operations: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
