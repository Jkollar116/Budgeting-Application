// User Profile Management
const userProfileService = {
    // Get the current user profile
    async getProfile(userId) {
        try {
            const response = await fetch(`/api/profile?userId=${userId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error fetching profile:', error);
            throw error;
        }
    },

    // Save or update a user profile
    async saveProfile(userId, profileData) {
        try {
            const response = await fetch(`/api/profile?userId=${userId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(profileData)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error saving profile:', error);
            throw error;
        }
    },

    // Delete a user profile
    async deleteProfile(userId) {
        try {
            const response = await fetch(`/api/profile?userId=${userId}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error deleting profile:', error);
            throw error;
        }
    }
};

// Store user ID in localStorage after login
function storeUserSession(userId, email) {
    localStorage.setItem('userId', userId);
    localStorage.setItem('userEmail', email);
    console.log('User session stored:', userId, email);
}

// Get user session info
function getUserSession() {
    return {
        userId: localStorage.getItem('userId'),
        email: localStorage.getItem('userEmail')
    };
}

// Clear user session on logout
function clearUserSession() {
    localStorage.removeItem('userId');
    localStorage.removeItem('userEmail');
}

// Make available globally
window.userProfileService = userProfileService;
window.storeUserSession = storeUserSession;
window.getUserSession = getUserSession;
window.clearUserSession = clearUserSession;
