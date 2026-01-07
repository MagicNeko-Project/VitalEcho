# VitalEcho

VitalEcho is an Android application and backend system designed to dynamically update a Telegram username based on the device's network connection status.

## Architecture

The system consists of two main components:

1.  **Android Application**:
    *   Monitors network connectivity (WiFi vs. Mobile Data).
    *   Sends heartbeats to the backend.
    *   Reports network status changes.

2.  **Backend (Relay Server + Userbot)**:
    *   Receives status updates and heartbeats.
    *   Monitors for heartbeat timeouts (Offline mode).
    *   Connects to Telegram using a Userbot to update the username.

## Features

*   **Action Mode**: Telegram username updated when using Mobile Data.
*   **Standby Mode**: Telegram username updated when using WiFi.
*   **Offline Mode**: Telegram username updated when device stops sending heartbeats.

## Setup

### Backend
1.  Navigate to `backend/`.
2.  Install dependencies: `pip install -r requirements.txt`.
3.  Configure environment variables (API_ID, API_HASH, etc.).
4.  Run the server.

### Android
1.  Open `android/` in Android Studio.
2.  Build and deploy to device.
