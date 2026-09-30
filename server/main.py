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

# Store active websocket connections: phone -> {"websocket": ws, "name": name, "status": "AVAILABLE"}
active_connections: Dict[str, dict] = {}

async def broadcast_users():
    users_list = [
        {"phone": phone, "name": data["name"], "status": data["status"]}
        for phone, data in active_connections.items()
    ]
    message = json.dumps({"type": "user_list", "users": users_list})
    for phone, data in list(active_connections.items()):
        try:
            await data["websocket"].send_text(message)
        except Exception:
            pass

@app.get("/")
async def get_status():
    return {
        "status": "online",
        "service": "Video Qongiroq Signaling Server",
        "active_users_count": len(active_connections)
    }

@app.websocket("/ws/{phone}")
async def websocket_endpoint(websocket: WebSocket, phone: str):
    await websocket.accept()

    clean_phone = phone.replace("+", "").strip()
    name = websocket.query_params.get("name", "Abonent")

    active_connections[clean_phone] = {
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
                if clean_phone in active_connections:
                    active_connections[clean_phone]["status"] = new_status
                await broadcast_users()

            elif receiver_phone and receiver_phone in active_connections:
                target_ws = active_connections[receiver_phone]["websocket"]
                data["senderPhone"] = clean_phone
                data["senderName"] = active_connections.get(clean_phone, {}).get("name", clean_phone)
                await target_ws.send_text(json.dumps(data))

    except WebSocketDisconnect:
        print(f"User disconnected: {clean_phone}")
        if clean_phone in active_connections:
            del active_connections[clean_phone]
        await broadcast_users()
    except Exception as e:
        print(f"Error for {clean_phone}: {e}")
        if clean_phone in active_connections:
            del active_connections[clean_phone]
        await broadcast_users()
