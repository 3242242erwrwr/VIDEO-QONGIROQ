import json
import asyncio
import os
from typing import Dict
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

app = FastAPI(title="Video Qongiroq Signaling Server")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Store registered users: phone -> {"name": name, "status": "AVAILABLE" / "OFFLINE", "websocket": ws / None}
registered_users: Dict[str, dict] = {}

# Current latest app version configuration for In-App Auto-Update over the internet
LATEST_VERSION_CODE = 2
LATEST_VERSION_NAME = "1.1"

async def broadcast_users():
    users_list = [
        {"phone": phone, "name": data["name"], "status": data["status"]}
        for phone, data in registered_users.items()
    ]
    message = json.dumps({"type": "user_list", "users": users_list})
    for phone, data in list(registered_users.items()):
        ws = data.get("websocket")
        if ws is not None:
            try:
                await ws.send_text(message)
            except Exception:
                pass

@app.get("/")
async def get_status():
    online_count = sum(1 for u in registered_users.values() if u["status"] != "OFFLINE")
    return {
        "status": "online",
        "service": "Video Qongiroq Signaling Server",
        "download_apk": "https://video-qongiroq.onrender.com/download",
        "version_check": "https://video-qongiroq.onrender.com/version",
        "active_users_count": online_count,
        "total_users_count": len(registered_users)
    }

@app.get("/call.html")
async def get_call_page():
    path = "call.html"
    if not os.path.exists(path):
        path = os.path.join("server", "call.html")
    if os.path.exists(path):
        return FileResponse(path=path, media_type="text/html")
    return {"error": "call.html not found"}

@app.get("/version")
async def get_version():
    return {
        "latestVersionCode": LATEST_VERSION_CODE,
        "versionName": LATEST_VERSION_NAME,
        "downloadUrl": "https://video-qongiroq.onrender.com/download"
    }

@app.get("/download")
async def download_apk():
    apk_path = "app-debug.apk"
    if not os.path.exists(apk_path):
        apk_path = os.path.join("server", "app-debug.apk")
    if os.path.exists(apk_path):
        return FileResponse(
            path=apk_path,
            filename="video_qongiroq_latest.apk",
            media_type="application/vnd.android.package-archive"
        )
    return {"error": "APK file not found"}

@app.websocket("/ws/{phone}")
async def websocket_endpoint(websocket: WebSocket, phone: str):
    await websocket.accept()

    clean_phone = phone.replace("+", "").strip()
    name = websocket.query_params.get("name", "Abonent")

    registered_users[clean_phone] = {
        "websocket": websocket,
        "name": name,
        "status": "AVAILABLE"
    }

    print(f"User connected: {clean_phone} ({name})")
    await broadcast_users()

    try:
        while True:
            data_text = await websocket.receive_text()
            data = json.loads(data_text)
            receiver_phone = data.get("receiverPhone", "").replace("+", "").strip()

            if data.get("type") == "status_update":
                new_status = data.get("status", "AVAILABLE")
                if clean_phone in registered_users:
                    registered_users[clean_phone]["status"] = new_status
                await broadcast_users()

            elif receiver_phone and receiver_phone in registered_users:
                target_user = registered_users[receiver_phone]
                target_ws = target_user.get("websocket")
                if target_ws is not None:
                    data["senderPhone"] = clean_phone
                    data["senderName"] = registered_users.get(clean_phone, {}).get("name", clean_phone)
                    await target_ws.send_text(json.dumps(data))

    except WebSocketDisconnect:
        print(f"User disconnected: {clean_phone}")
        if clean_phone in registered_users:
            registered_users[clean_phone]["status"] = "OFFLINE"
            registered_users[clean_phone]["websocket"] = None
        await broadcast_users()
    except Exception as e:
        print(f"Error for {clean_phone}: {e}")
        if clean_phone in registered_users:
            registered_users[clean_phone]["status"] = "OFFLINE"
            registered_users[clean_phone]["websocket"] = None
        await broadcast_users()
