# Electronic Life Status Feedback Device (电子生命状态反馈器)

> *Linking organic pulse to the digital void.*

## Overview

The **Electronic Life Status Feedback Device** (电子生命状态反馈器) is a cybernetic link system designed to synchronize your physical connectivity status with your digital projection in the Telegram network. It serves as a heartbeat monitor for your electronic existence, broadcasting your operational state to the ether.

## System Architecture

The system operates via a dual-node configuration:

### 1. The Sensor Node (Android Terminal)
Located at the root of your repository (`/app`), this neural interface resides on your mobile device.
*   **Function**: Continuously scans local network flux (WiFi vs. Cellular).
*   **Transmission**: Emits "heartbeat" pulses to the central relay.
*   **Directives**: Detects "Action Mode" (Mobile Data) and "Standby Mode" (WiFi).

### 2. The Central Core (Backend Relay)
Located in the `/backend` sector.
*   **Function**: Receives telemetry from the Sensor Node.
*   **Analysis**: Monitors pulse continuity. If the pulse ceases, it declares "Offline Mode" (Critical Disconnection).
*   **Execution**: Manipulates the Telegram user profile via the Userbot protocol to reflect the current status suffix.

## Operational Modes

*   **Action Mode (行动模式)**: Engaged when the entity is mobile, utilizing cellular data pathways.
*   **Standby Mode (待机模式)**: Engaged when the entity is stationary, tethered to a stable WiFi grid.
*   **Offline Mode (离线模式)**: Engaged when signal is lost or the biological unit ceases transmission.

## Deployment Protocols

### Backend Core Initialization
1.  Navigate to the `backend/` sector.
2.  Install neural dependencies: `pip install -r requirements.txt`.
3.  Calibrate environment variables (`API_ID`, `API_HASH`, etc.) in `.env`.
4.  Ignite the core: Run the server.

### Android Terminal Integration
1.  Open the repository root in your Android Studio IDE.
2.  The system will recognize the `app` module and root `build.gradle`.
3.  Compile the APK and fuse it with your mobile device.
4.  Grant necessary permissions for background network scanning and accessibility services to ensure the link remains unbroken.
