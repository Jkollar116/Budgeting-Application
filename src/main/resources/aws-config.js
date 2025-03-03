// Initialize AWS configuration
(async function() {
    try {
        // Update path to match the new context
        const response = await fetch('/api/aws-config');
        if (!response.ok) {
            console.error(`Failed to fetch AWS config: ${response.status}`);
            return;
        }

        const config = await response.json();
        console.log('AWS Config loaded:', config);

        // Initialize AWS SDK with the configuration
        AWS.config.region = config.region || 'us-east-2';

        // Make config globally available
        window.awsConfig = config;

        // Log initialization success
        console.log('AWS SDK initialized successfully');
    } catch (error) {
        console.error('Error initializing AWS config:', error);
    }
})();

// Function for the storage provider dropdown
function getStorageProvider() {
    const storageSelect = document.getElementById('storage-provider');
    return storageSelect ? storageSelect.value : 'firebase';
}
