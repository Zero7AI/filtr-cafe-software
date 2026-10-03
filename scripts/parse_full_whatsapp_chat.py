import re
import sys
import os
import json

sys.stdout.reconfigure(encoding='utf-8')

transcript_path = 'extracted_whatsapp_chat/WhatsApp Chat with Motes by Dropper Logistics.txt'

with open(transcript_path, 'r', encoding='utf-8') as f:
    text = f.read()

# WhatsApp export format: "M/D/YY, HH:MM - Sender: Message"
# Example: "8/30/26, 01:03 - Somesh: 2 pani 24 - *220/-*"
pattern = re.compile(r'^(\d{1,2}/\d{1,2}/\d{2,4},\s\d{1,2}:\d{2})\s-\s([^:]+):\s(.*)$', re.MULTILINE)

messages = []
matches = list(pattern.finditer(text))

for i, match in enumerate(matches):
    timestamp_str = match.group(1)
    sender = match.group(2).strip()
    # Content goes until the next match or end of string
    start_pos = match.end()
    end_pos = matches[i+1].start() if i + 1 < len(matches) else len(text)
    
    # First line content
    first_line = match.group(3)
    # Remaining lines before next message
    rest = text[start_pos:end_pos]
    full_content = (first_line + rest).strip()
    
    messages.append({
        'index': i + 1,
        'timestamp': timestamp_str,
        'sender': sender,
        'content': full_content
    })

print(f"Total parsed WhatsApp messages: {len(messages)}")

somesh_msgs = [m for m in messages if 'somesh' in m['sender'].lower()]
motes_msgs = [m for m in messages if 'motes' in m['sender'].lower()]
print(f"Messages from Somesh: {len(somesh_msgs)}")
print(f"Messages from Motes (Bot): {len(motes_msgs)}")

# Group by Somesh message and find bot response(s) that immediately follow
dialogues = []
current_somesh = None
current_replies = []

for m in messages:
    if 'somesh' in m['sender'].lower():
        if current_somesh:
            dialogues.append({
                'somesh': current_somesh,
                'replies': current_replies
            })
        current_somesh = m
        current_replies = []
    elif 'motes' in m['sender'].lower():
        if current_somesh:
            current_replies.append(m)
        else:
            # Bot message before first Somesh message
            dialogues.append({
                'somesh': None,
                'replies': [m]
            })

if current_somesh:
    dialogues.append({
        'somesh': current_somesh,
        'replies': current_replies
    })

print(f"\nTotal Somesh conversation turns: {len(dialogues)}")

print("\n--- Summary of Turns with Attachments or No Replies ---")
for d in dialogues:
    s = d['somesh']
    if not s: continue
    replies = d['replies']
    content = s['content']
    has_attachment = '<attached:' in content or '(file attached)' in content or '.jpg' in content or '.pdf' in content
    reply_status = "NO REPLY ❌" if not replies else f"{len(replies)} reply(ies)"
    
    # Check if reply succeeded or failed
    reply_summary = ""
    for r in replies:
        if "✅" in r['content'] or "logged!" in r['content']:
            reply_summary += " [SAVED ✅]"
        elif "⏳" in r['content'] or "queued" in r['content']:
            reply_summary += " [QUEUED ⏳]"
        elif "❌" in r['content'] or "error" in r['content'].lower():
            reply_summary += " [ERROR ❌]"
        elif "🤔" in r['content']:
            reply_summary += " [PARSE FAILED 🤔]"
        else:
            reply_summary += " [INFO]"
    
    first_line = content.split('\n')[0][:60]
    att_tag = " [FILE 📎]" if has_attachment else ""
    print(f"Turn #{s['index']:3d} | {s['timestamp']} | {reply_status:<15} {reply_summary:<25}{att_tag} -> {first_line}")

