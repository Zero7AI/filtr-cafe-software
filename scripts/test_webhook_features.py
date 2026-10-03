import urllib.request
import json
import sys

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"

headers = {
    "apikey": KEY,
    "Authorization": f"Bearer {KEY}",
    "Content-Type": "application/json"
}

def send_test_webhook(msg_id, body_text):
    payload = {
        "object": "whatsapp_business_account",
        "entry": [{
            "id": "122137573767095281",
            "changes": [{
                "field": "messages",
                "value": {
                    "messaging_product": "whatsapp",
                    "metadata": {
                        "display_phone_number": "919907839311",
                        "phone_number_id": "100000000000000"
                    },
                    "contacts": [{
                        "profile": {"name": "Somesh"},
                        "wa_id": "919907839311"
                    }],
                    "messages": [{
                        "from": "919907839311",
                        "id": msg_id,
                        "timestamp": "1727010000",
                        "type": "text",
                        "text": {"body": body_text}
                    }]
                }
            }]
        }]
    }
    
    url = f"{SUPABASE_URL}/functions/v1/whatsapp-webhook"
    req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            print(f"Webhook HTTP status: {resp.status}")
            print(f"Response: {resp.read().decode('utf-8')}")
    except urllib.error.HTTPError as e:
        print(f"Webhook HTTP Error {e.code}: {e.read().decode('utf-8')}")

print("=== 1. Testing Unitemized Bill: 'Kalpana traders - 780rs' ===")
send_test_webhook("wamid.TEST_KALPANA_001", "Kalpana traders - 780rs")

print("\n=== 2. Testing Store Expense: 'Shop rent sept - 9000rs' ===")
send_test_webhook("wamid.TEST_RENT_001", "Shop rent sept - 9000rs")

