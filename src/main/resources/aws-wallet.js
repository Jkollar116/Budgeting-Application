// AWS Wallet Management Utility
const awsWalletService = {
    // List all wallets
    async listWallets(userId) {
        try {
            const response = await fetch(`/aws-wallets?userId=${userId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error listing wallets:', error);
            throw error;
        }
    },

    // Get a single wallet
    async getWallet(userId, walletId) {
        try {
            const response = await fetch(`/aws-wallets/${walletId}?userId=${userId}`);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error fetching wallet:', error);
            throw error;
        }
    },

    // Add a new wallet
    async addWallet(userId, walletData) {
        try {
            const response = await fetch(`/aws-wallets?userId=${userId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(walletData)
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error adding wallet:', error);
            throw error;
        }
    },

    // Refresh wallet data
    async refreshWallet(userId, walletId) {
        try {
            const response = await fetch(`/aws-wallets/${walletId}/refresh?userId=${userId}`, {
                method: 'POST'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error refreshing wallet:', error);
            throw error;
        }
    },

    // Delete a wallet
    async deleteWallet(userId, walletId) {
        try {
            const response = await fetch(`/aws-wallets/${walletId}?userId=${userId}`, {
                method: 'DELETE'
            });

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return await response.json();
        } catch (error) {
            console.error('Error deleting wallet:', error);
            throw error;
        }
    }
};

// Make available globally
window.awsWalletService = awsWalletService;
