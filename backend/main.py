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
BASE_NAME = os.getenv("BASE_NAME", "VitalEchoUser")

# Logging setup
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("VitalEcho")

# Constants
HEARTBEAT_TIMEOUT = 30  # seconds
CHECK_INTERVAL = 10     # seconds
SEPARATOR = " - "

# Status Constants (Chinese)
STATUS_ACTION = "行动模式"
STATUS_STANDBY = "待机模式"
STATUS_OFFLINE = "离线模式"

# Global State
class SystemState:
    last_heartbeat: float = 0
    current_status: str = STATUS_OFFLINE
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
            first_name = BASE_NAME
            last_name = "MockStatus"
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
    # Logic: BASE_NAME - Status
    # We overwrite the Last Name with the " - Status" part,
    # OR we overwrite the whole First/Last name combo?
    # User said: "write corresponding name in variable, then directly overwrite existing name according to format Name - Mode"
    # This implies we control the WHOLE name structure.
    # To be clean, let's set First Name = BASE_NAME, Last Name = " - Mode".
    # Or First Name = "BASE_NAME - Mode", Last Name = Empty.
    # Telegram First Name is mandatory, Last Name is optional.
    # A common style is First: "Name", Last: "- Mode".
    # Let's try that.

    # Wait, if BASE_NAME is long?
    # Let's use First Name = BASE_NAME.
    # Last Name = "- Mode".

    # Construct the mode string
    # The status comes in as "行动模式", "MOBILE" (legacy?), etc.
    # We map them just in case, but Android will send Chinese.

    mode_text = status
    if status == "MOBILE": mode_text = STATUS_ACTION
    elif status == "WIFI": mode_text = STATUS_STANDBY
    elif status == "OFFLINE": mode_text = STATUS_OFFLINE

    # Format: " - 行动模式"
    suffix = f"{SEPARATOR}{mode_text}"

    # Check if update is needed
    if state.last_updated_username == mode_text:
        return

    try:
        # We enforce First Name = BASE_NAME, Last Name = suffix
        await client(functions.account.UpdateProfileRequest(
            first_name=BASE_NAME,
            last_name=suffix
        ))
        logger.info(f"Updated Telegram profile to: {BASE_NAME} {suffix}")
        state.last_updated_username = mode_text

    except Exception as e:
        logger.error(f"Failed to update Telegram profile: {e}")

async def check_offline_status():
    """Background task to check for heartbeat timeout."""
    while True:
        await asyncio.sleep(CHECK_INTERVAL)
        now = time.time()
        if state.current_status != STATUS_OFFLINE and (now - state.last_heartbeat > HEARTBEAT_TIMEOUT):
            logger.info("Heartbeat timeout. Switching to OFFLINE.")
            state.current_status = STATUS_OFFLINE
            await update_telegram_username(STATUS_OFFLINE)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    await client.start()
    state.last_heartbeat = time.time()
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

    # User sends Chinese string directly now?
    # Or "MOBILE"/"WIFI"?
    # The plan says "Android app and reporting use Chinese".
    # I will accept both to be robust, but map to the Chinese constant.

    raw_status = update.network_type
    new_status = raw_status

    if raw_status == "MOBILE": new_status = STATUS_ACTION
    elif raw_status == "WIFI": new_status = STATUS_STANDBY
    elif raw_status == "OFFLINE": new_status = STATUS_OFFLINE
    # Else assume it is already Chinese or valid

    if state.current_status != new_status:
        state.current_status = new_status
        logger.info(f"Network status changed to: {new_status}")
        await update_telegram_username(new_status)

    return {"status": "updated", "mode": state.current_status}

@app.get("/")
async def root():
    return {"message": "VitalEcho Server Running", "current_mode": state.current_status}
