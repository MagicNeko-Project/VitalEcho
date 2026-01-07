import os
import time
import asyncio
import logging
from typing import Optional
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException, Request
from pydantic import BaseModel
from dotenv import load_dotenv
from telethon import TelegramClient, functions, types

# Load environment variables
load_dotenv()

# Configuration
API_ID = os.getenv("API_ID")
API_HASH = os.getenv("API_HASH")
SERVER_API_TOKEN = os.getenv("SERVER_API_TOKEN", "secret")

# Logging setup
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("VitalEcho")

# Constants
HEARTBEAT_TIMEOUT = 30  # seconds
CHECK_INTERVAL = 10     # seconds
SEPARATOR = " - "

# Global State
class SystemState:
    last_heartbeat: float = 0
    current_status: str = "OFFLINE" # WIFI, MOBILE, OFFLINE
    last_updated_username: str = ""

state = SystemState()

# Mock Telegram Client if credentials are missing (for testing)
class MockTelegramClient:
    def __init__(self, *args, **kwargs):
        pass

    async def start(self):
        logger.info("Mock Telegram Client started.")

    async def disconnect(self):
        logger.info("Mock Telegram Client disconnected.")

    async def get_me(self):
        # Return a mock User object
        class MockUser:
            first_name = "Mock"
            last_name = "User"
        return MockUser()

    async def __call__(self, request):
        if isinstance(request, functions.account.UpdateProfileRequest):
            logger.info(f"[MOCK] Updating profile: First='{request.first_name}', Last='{request.last_name}'")
        return True

# Initialize Telegram Client
if API_ID and API_HASH:
    client = TelegramClient('userbot', int(API_ID), API_HASH)
else:
    logger.warning("API_ID or API_HASH missing. Using Mock Telegram Client.")
    client = MockTelegramClient()

async def update_telegram_username(status: str):
    """Updates the Telegram profile name based on the status."""
    mode_name = ""
    if status == "MOBILE":
        mode_name = "Action Mode"
    elif status == "WIFI":
        mode_name = "Standby Mode"
    elif status == "OFFLINE":
        mode_name = "Offline Mode"
    else:
        return

    # Check if we actually need to update to avoid spamming the API
    # But checking internal state isn't enough if the user manually changed their name.
    # However, for performance, we can skip if state matches.
    if state.last_updated_username == mode_name:
        return

    try:
        me = await client.get_me()
        if not me:
            logger.error("Could not fetch user profile.")
            return

        current_first = me.first_name or ""
        current_last = me.last_name or ""

        new_first = current_first
        new_last = current_last

        # Helper to attach mode after separator
        def attach_mode(base, mode):
            return f"{base}{SEPARATOR}{mode}"

        # Logic:
        # 1. Check if separator exists in Last Name. If so, replace suffix.
        # 2. Else check if separator exists in First Name. If so, replace suffix.
        # 3. Else, append to Last Name (or set Last Name if empty).

        updated = False

        if SEPARATOR in current_last:
            base = current_last.split(SEPARATOR)[0]
            candidate = attach_mode(base, mode_name)
            if current_last != candidate:
                new_last = candidate
                updated = True
            # Case where it matches exactly is handled by 'updated = False' default
        elif SEPARATOR in current_first:
            base = current_first.split(SEPARATOR)[0]
            candidate = attach_mode(base, mode_name)
            if current_first != candidate:
                new_first = candidate
                updated = True
        else:
            # Separator not found in either.
            # Append to Last Name.
            # If Last Name is empty, it becomes " - Mode" (which is what we want? Or just "Mode"?)
            # User said: "ensure it only adds the corresponding character after the - symbol"
            # If I set Last Name to " - Mode", it fits the pattern.

            # Check if current_last is just empty or None
            base = current_last if current_last else ""
            candidate = attach_mode(base, mode_name)

            # If base was empty, candidate is " - Mode".
            # If base was "Doe", candidate is "Doe - Mode".

            new_last = candidate
            updated = True

        if updated:
            # Telethon's UpdateProfileRequest arguments are optional.
            # We must pass the ones we want to update.
            await client(functions.account.UpdateProfileRequest(
                first_name=new_first,
                last_name=new_last
            ))
            logger.info(f"Updated Telegram profile to: {new_first} {new_last}")
            state.last_updated_username = mode_name
        else:
            logger.info("Telegram profile already up to date.")
            state.last_updated_username = mode_name

    except Exception as e:
        logger.error(f"Failed to update Telegram profile: {e}")

async def check_offline_status():
    """Background task to check for heartbeat timeout."""
    while True:
        await asyncio.sleep(CHECK_INTERVAL)
        now = time.time()
        if state.current_status != "OFFLINE" and (now - state.last_heartbeat > HEARTBEAT_TIMEOUT):
            logger.info("Heartbeat timeout. Switching to OFFLINE.")
            state.current_status = "OFFLINE"
            await update_telegram_username("OFFLINE")

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await client.start()
    # Trigger initial update? No, wait for event or heartbeat timeout.
    state.last_heartbeat = time.time() # Reset heartbeat on start to give some grace
    asyncio.create_task(check_offline_status())
    yield
    # Shutdown
    await client.disconnect()

app = FastAPI(lifespan=lifespan)

# Data Models
class StatusUpdate(BaseModel):
    network_type: str

@app.post("/heartbeat")
async def heartbeat(request: Request, x_api_token: Optional[str] = Header(None)):
    if x_api_token != SERVER_API_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid API Token")
    state.last_heartbeat = time.time()
    return {"status": "ok", "mode": state.current_status}

@app.post("/status")
async def update_status(update: StatusUpdate, x_api_token: Optional[str] = Header(None)):
    if x_api_token != SERVER_API_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid API Token")

    state.last_heartbeat = time.time()
    new_status = update.network_type.upper()
    if new_status not in ["WIFI", "MOBILE"]:
         raise HTTPException(status_code=400, detail="Invalid network type")

    if state.current_status != new_status:
        state.current_status = new_status
        logger.info(f"Network status changed to: {new_status}")
        await update_telegram_username(new_status)

    return {"status": "updated", "mode": state.current_status}

@app.get("/")
async def root():
    return {"message": "VitalEcho Server Running", "current_mode": state.current_status}
