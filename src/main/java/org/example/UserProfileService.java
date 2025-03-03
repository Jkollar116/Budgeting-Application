package org.example;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class UserProfileService {
    private static final String COLLECTION_NAME = "user_profiles";

    /**
     * Creates or updates a user profile
     */
    public void saveUserProfile(String userId, Map<String, Object> profileData) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);

        // Set with merge option to update fields without overwriting the entire document
        ApiFuture<WriteResult> result = docRef.set(profileData, SetOptions.merge());

        // Wait for the operation to complete
        result.get();
    }

    /**
     * Retrieves a user profile by user ID
     */
    public Map<String, Object> getUserProfile(String userId) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);
        ApiFuture<DocumentSnapshot> future = docRef.get();

        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return document.getData();
        } else {
            return new HashMap<>(); // Return empty map if profile doesn't exist
        }
    }

    /**
     * Updates specific fields in a user profile
     */
    public void updateUserProfile(String userId, Map<String, Object> updates) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);

        ApiFuture<WriteResult> result = docRef.update(updates);
        result.get();
    }

    /**
     * Deletes a user profile
     */
    public void deleteUserProfile(String userId) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        DocumentReference docRef = db.collection(COLLECTION_NAME).document(userId);

        ApiFuture<WriteResult> result = docRef.delete();
        result.get();
    }
}
