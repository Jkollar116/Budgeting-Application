package org.example;

/**
 * Main class to test Firebase connectivity
 */
public class FirebaseTest {
    public static void main(String[] args) {
        System.out.println("Starting Firebase Diagnostic Test");
        
        // Run diagnostic tests
        FirebaseDiagnostic.testFirebaseConnection();
        
        System.out.println("Test completed");
    }
}
