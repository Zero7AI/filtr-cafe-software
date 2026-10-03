import urllib.request
import json
import sys
import base64

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def call_action(action, params=""):
    url = f"{SUPABASE_URL}/functions/v1/whatsapp-webhook?action={action}{params}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except Exception as e:
        print(f"Error calling {action}: {e}")
        if hasattr(e, 'read'):
            print("Response:", e.read().decode())
        return None

print("=== 1. LIST MODELS ===")
models_data = call_action("list_models")
if models_data and "models" in models_data:
    print(f"Total available models: {len(models_data['models'])}")
    for m in models_data['models']:
        name = m.get('name')
        methods = m.get('supportedGenerationMethods', [])
        if "generateContent" in methods:
            print(f"  - {name} ({m.get('displayName')})")
else:
    print("Models response:", models_data)

print("\n=== 2. META INFO & PHONE NUMBER ID ===")
meta_info = call_action("get_meta_info")
print(json.dumps(meta_info, indent=2))

print("\n=== 3. DOWNLOAD MEDIA FOR MSG 121 (media_id 1387005036424564) ===")
media_res = call_action("get_media", "&media_id=1387005036424564")
if media_res and "base64" in media_res:
    b64 = media_res.pop("base64")
    print(f"Downloaded media: len={len(b64)}, mime={media_res.get('mimeType')}")
    # Save to disk as msg_121_receipt.jpg
    with open("scripts/msg_121_receipt.jpg", "wb") as f:
        f.write(base64.b64decode(b64))
    print("Saved to scripts/msg_121_receipt.jpg!")
else:
    print("Media res:", media_res)
