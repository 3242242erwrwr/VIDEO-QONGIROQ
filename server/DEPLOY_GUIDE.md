# RENDER FREE SERVERGA SERVER KODINI JOYLASHTIRISH VA ISHLATISH (1 DAKIKA)

Siz tayyorlagan **`3242242erwrwr / VIDEO-QONGIROQ`** GitHub repozitariyingiz uchun Server kodlari tayyorlandi (`server/` papkasida).

---

## 1-QADAM: Kodlarni GitHub repozitariyangizga yuklash (Push)

Terminalda loyiha papkasida ushbu buyruqlarni ketma-ket bajaring:

```bash
git add .
git commit -m "Add Python WebRTC Signaling Server"
git remote add origin https://github.com/3242242erwrwr/VIDEO-QONGIROQ.git
git branch -M main
git push -u origin main
```

---

## 2-QADAM: Render.com Dashboard-da Deploy qilish

1. Render.com ekranida (skrinshotdagi oyna):
   - **Name**: `VIDEO-QONGIROQ`
   - **Language**: `Python 3`
   - **Root Directory**: `server`
   - **Build Command**: `pip install -r requirements.txt`
   - **Start Command**: `uvicorn main:app --host 0.0.0.0 --port $PORT`
2. Pastdagi **"Deploy web service"** tugmasini bosing!

---

## 3-QADAM: Telefon Ilovasida Birlashtirish

Render bepul server URL beradi (Masalan: `https://video-qongiroq.onrender.com`).
Ilovada WebSocket manzili:
`wss://video-qongiroq.onrender.com/ws/`

Ilovaning top baridagi **Cloud Server (Bulut)** belgisini bosib yangi Server URL manzilini saqlashingiz mumkin.
Endi dunyoning istalgan nuqtasidagi ikkita telefon bir-biriga telefon raqam orqali real vaqtda video qo'ng'iroq qila oladi!
