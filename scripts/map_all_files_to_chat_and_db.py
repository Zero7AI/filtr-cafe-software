import os
import sys
import json
import re
import urllib.request
from collections import defaultdict

sys.stdout.reconfigure(encoding='utf-8')

SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co"
KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM"
headers = {"apikey": KEY, "Authorization": f"Bearer {KEY}"}

def fetch_supabase(endpoint):
    req = urllib.request.Request(f"{SUPABASE_URL}/rest/v1/{endpoint}", headers=headers)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

# 1. Parse Chat Transcript
transcript_path = 'extracted_whatsapp_chat/WhatsApp Chat with Motes by Dropper Logistics.txt'
with open(transcript_path, 'r', encoding='utf-8') as f:
    text = f.read()

pattern = re.compile(r'^(\d{1,2}/\d{1,2}/\d{2,4},\s\d{1,2}:\d{2})\s-\s([^:]+):\s(.*)$', re.MULTILINE)
matches = list(pattern.finditer(text))

messages = []
for i, match in enumerate(matches):
    start_pos = match.end()
    end_pos = matches[i+1].start() if i + 1 < len(matches) else len(text)
    full_content = (match.group(3) + text[start_pos:end_pos]).strip()
    messages.append({
        'index': i + 1,
        'timestamp': match.group(1),
        'sender': match.group(2).strip(),
        'content': full_content
    })

# Group into turns
turns = []
current_somesh = None
current_replies = []
for m in messages:
    if 'somesh' in m['sender'].lower():
        if current_somesh:
            turns.append({'somesh': current_somesh, 'replies': current_replies})
        current_somesh = m
        current_replies = []
    elif 'motes' in m['sender'].lower():
        if current_somesh:
            current_replies.append(m)

if current_somesh:
    turns.append({'somesh': current_somesh, 'replies': current_replies})

# 2. Fetch DB orders and items
db_orders = fetch_supabase("inventory_orders?order=order_id.asc")
db_items = fetch_supabase("inventory_order_items?limit=5000")
items_by_order = defaultdict(list)
for it in db_items:
    items_by_order[it['order_id']].append(it)

# 3. All media files in extracted_whatsapp_chat
media_files = sorted([f for f in os.listdir('extracted_whatsapp_chat') if f.endswith('.jpg') or f.endswith('.pdf')])

print(f"Total media files in chat export: {len(media_files)}")
print(f"Total Somesh turns: {len(turns)}")
print(f"Total DB orders: {len(db_orders)}")

# Cross-reference each media file
media_audit = []
for mf in media_files:
    # find which turns mention this file
    matched_turns = []
    for t in turns:
        c = t['somesh']['content']
        # WhatsApp export replaces filenames or keeps them:
        # e.g. "IMG-20260902-WA0015.jpg (file attached)"
        # or partial match
        if mf in c or mf.replace('.jpg', '') in c or (mf.startswith('Somesh') and 'R26 725' in c):
            matched_turns.append(t)
    
    media_audit.append({
        'file': mf,
        'turns': matched_turns
    })

for item in media_audit:
    mf = item['file']
    ts = item['turns']
    print(f"\n📁 FILE: {mf}")
    if not ts:
        print("   ⚠️ Not explicitly named in transcript (search by date/time)")
    for t in ts:
        s = t['somesh']
        replies = t['replies']
        rep_texts = " | ".join([r['content'].replace('\n', ' ')[:100] for r in replies]) if replies else "NO REPLY"
        print(f"   Turn #{s['index']} ({s['timestamp']}): Somesh: {s['content'][:60]} --> Bot: {rep_texts}")

