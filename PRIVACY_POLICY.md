# Privacy Policy for ParkHere

**Effective Date:** 21st February 2026

SmallAppLabs ("we", "us", or "our") operates the ParkHere mobile application (the "Service"). This page informs you of our policies regarding the collection, use, and disclosure of personal data when you use our Service and the choices you have associated with that data.

By using the Service, you agree to the collection and use of information in accordance with this policy.

## 1. Information Collection and Use
To provide and improve our app, we may collect and process the following types of information:

### a) Location Data
ParkHere is a crowdsourced parking utility. To function properly, the app requests access to your device's precise location (GPS coordinates).
- **How we use it:** We use your location to center the map, allow you to explore parking spots near you, and to verify that any new parking spot you drop is within 100 meters of your physical location.
- **Data storage:** If you drop a parking pin, the latitude and longitude coordinates of that pin are saved to our servers (Firebase).

### b) Camera & Photos
When marking a parking spot, you have the option to attach a photograph.
- **How we use it:** To give context to other users seeking a parking spot. 
- **Privacy Protections:** The ParkHere app processes your photos on-device using ML (Machine Learning) capabilities to automatically detect and scramble text (such as license plates or street signs) before the compression stage. The blurred, compressed image is then converted to text format and uploaded to our database.
- We do not access your camera or photo gallery without your explicit, momentary permission.

### c) Device Identifiers
To prevent abuse (such as spamming the map with fake parking spots), we track user activity using a pseudonymous device identifier (Android ID or a generated UUID).
- **How we use it:** We use this ID to enforce our service limits, namely restricting accounts to creating a maximum of 3 parking spots per hour.
- We do not link this generated ID to your real name, email, phone number, or physical identity.

## 2. Third-Party Services
We employ third-party services to facilitate our Service, to provide the Service on our behalf, or to perform Service-related functions. These third parties have access to your data only to perform these tasks on our behalf:

- **Google Play Services:** Provides core location features and ML Kit vision processing.
- **Google Maps:** Renders the map interface and satellite layer.
- **Firebase Realtime Database:** Hosted by Google, used as the cloud backend to instantly sync map markers and store the compressed spot images.

## 3. Data Retention
Parking spots and their associated data (including coordinates, device ID tracker, and uploaded photos) are automatically purged from our Firebase database after **1 hour**. 

## 4. Children's Privacy
Our Service does not address anyone under the age of 13. We do not knowingly collect personally identifiable information from anyone under the age of 13.

## 5. Changes to This Privacy Policy
We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy here within the app.

## 6. Contact Us
If you have any questions about this Privacy Policy, please contact us at:
SmallAppLabs 
[Insert Contact Email]
