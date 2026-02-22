# ParkHere - Community Parking Spot Finder

A real-time community-driven parking spot finder app for Android.

## Features
- 🗺️ Google Maps integration
- 📍 Add available parking spots with one tap
- ❌ Mark spots as taken
- 🔄 Real-time sync across all users
- 🧭 Navigate to spots via Google Maps

## Setup Instructions

### 1. Get Google Maps API Key
1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable **Maps SDK for Android**
4. Go to Credentials → Create API Key
5. Copy the API key

### 2. Add API Key to App
Edit `app/src/main/AndroidManifest.xml`:
```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_ACTUAL_API_KEY_HERE" />
```

### 3. Set Up Firebase
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create new project "ParkHere"
3. Add Android app with package `com.sal.parkingfinder`
4. Download `google-services.json`
5. Place it in `app/` folder
6. Enable **Realtime Database** in Firebase Console
7. Set database rules (for testing):
```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

### 4. Build & Run
Open project in Android Studio and run on device/emulator.

## Project Structure
```
app/
├── src/main/java/com/sal/parkingfinder/
│   ├── MainActivity.kt      # Map UI and user interaction
│   ├── ParkingSpot.kt        # Data model
│   └── ParkingRepository.kt  # Firebase operations
└── src/main/res/
    ├── layout/activity_main.xml
    ├── values/colors.xml, strings.xml, themes.xml
    └── drawable/icons and backgrounds
```

## Security Note
For production, update Firebase rules to require authentication:
```json
{
  "rules": {
    "parking_spots": {
      ".read": true,
      ".write": "auth != null"
    }
  }
}
```
