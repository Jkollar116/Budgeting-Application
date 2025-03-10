package org.example;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.*;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * A standalone application to test Firebase Firestore connectivity
 * Run this directly to verify that Firebase is working correctly
 */
public class FirebaseStandaloneTest {

    public static void main(String[] args) {
        System.out.println("\n\n==========================================");
        System.out.println("FIREBASE STANDALONE TEST");
        System.out.println("==========================================\n");
        
        try {
            // Step 1: Initialize Firebase
            System.out.println("Step 1: Initializing Firebase...");
            initializeFirebase();
            
            // Step 2: Get Firestore instance
            System.out.println("\nStep 2: Getting Firestore instance...");
            Firestore db = FirestoreClient.getFirestore();
            
            // Step 3: Write a test document
            System.out.println("\nStep 3: Writing test data...");
            String testId = writeTestDocument(db);
            
            // Step 4: Read the test document
            System.out.println("\nStep 4: Reading test data...");
            readTestDocument(db, testId);
            
            // Step 5: List data in the test collection
            System.out.println("\nStep 5: Listing all test documents...");
            listTestCollection(db);
            
            System.out.println("\nTest completed successfully! Your Firebase connection is working.");
            System.out.println("Check your Firebase console at https://console.firebase.google.com/project/cashclimb-d162c/firestore/data/ to see the test data.");
            
        } catch (Exception e) {
            System.err.println("\n❌ ERROR: Test failed with exception: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n==========================================");
    }
    
    private static void initializeFirebase() throws IOException {
        // Check if Firebase is already initialized
        if (!FirebaseApp.getApps().isEmpty()) {
            System.out.println("Firebase already initialized. Closing existing apps...");
            
            for (FirebaseApp app : FirebaseApp.getApps()) {
                System.out.println("  - Found app: " + app.getName());
                try {
                    app.delete();
                    System.out.println("  - Closed app: " + app.getName());
                } catch (Exception e) {
                    System.err.println("  - Error closing app: " + e.getMessage());
                }
            }
        }
        
        // Load service account
        System.out.println("Loading service account key...");
        FileInputStream serviceAccount = 
            new FileInputStream("src/main/resources/serviceAccountKey.json");
        
        // Configure options
        String projectId = "cashclimb-d162c";
        String databaseUrl = "https://" + projectId + ".firebaseio.com";
        System.out.println("Project ID: " + projectId);
        System.out.println("Database URL: " + databaseUrl);
        
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .setDatabaseUrl(databaseUrl)
            .setProjectId(projectId)
            .build();
        
        // Initialize Firebase
        FirebaseApp app = FirebaseApp.initializeApp(options);
        System.out.println("Firebase initialized successfully. App name: " + app.getName());
    }
    
    private static String writeTestDocument(Firestore db) throws InterruptedException, ExecutionException {
        // Create a test document with a unique ID
        String testId = "test-" + UUID.randomUUID().toString();
        
        // Create a new document
        DocumentReference docRef = db.collection("test_collection").document(testId);
        
        // Create test data
        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", FieldValue.serverTimestamp());
        data.put("message", "Test data created at " + new java.util.Date());
        data.put("random_number", Math.random() * 1000);
        
        // Write data to Firestore
        System.out.println("Writing document with ID: " + testId);
        ApiFuture<WriteResult> result = docRef.set(data);
        
        // Wait for the write to complete
        WriteResult writeResult = result.get();
        System.out.println("Document written at: " + writeResult.getUpdateTime());
        
        return testId;
    }
    
    private static void readTestDocument(Firestore db, String testId) throws InterruptedException, ExecutionException {
        // Get a reference to the document
        DocumentReference docRef = db.collection("test_collection").document(testId);
        
        // Read the document
        System.out.println("Reading document with ID: " + testId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        
        // Wait for the read to complete
        DocumentSnapshot document = future.get();
        
        // Check if the document exists
        if (document.exists()) {
            System.out.println("Document data: " + document.getData());
        } else {
            System.out.println("No such document!");
        }
    }
    
    private static void listTestCollection(Firestore db) throws InterruptedException, ExecutionException {
        // Get a reference to the collection
        CollectionReference colRef = db.collection("test_collection");
        
        // List all documents
        ApiFuture<QuerySnapshot> future = colRef.get();
        
        // Wait for the query to complete
        QuerySnapshot querySnapshot = future.get();
        
        // Display all documents
        System.out.println("Listing " + querySnapshot.size() + " documents:");
        for (DocumentSnapshot document : querySnapshot.getDocuments()) {
            System.out.println("  - " + document.getId() + ": " + document.getData());
        }
    }
}
