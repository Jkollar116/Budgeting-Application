// AWS Configuration and Initialization
const initializeAWS = async () => {
    try {
        // Fetch AWS configuration from backend
        const response = await fetch('/aws-config');
        if (!response.ok) {
            throw new Error(`Failed to load AWS config: ${response.status}`);
        }

        const awsConfig = await response.json();

        // Check if AWS SDK is loaded
        if (typeof AWS !== 'undefined') {
            // Configure AWS SDK
            AWS.config.region = awsConfig.region;

            // Return configuration for use in application
            return awsConfig;
        } else {
            console.warn('AWS SDK not loaded. Make sure to include the AWS SDK script.');
            return null;
        }
    } catch (error) {
        console.error('Error initializing AWS:', error);
        return null;
    }
};

// Export the initialize function and configuration
window.awsInitialize = initializeAWS;
