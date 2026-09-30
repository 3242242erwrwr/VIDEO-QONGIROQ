import json
import asyncio
from typing import Dict
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

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
        "active_users_count": online_count,
        "total_users_count": len(registered_users)
    }

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
