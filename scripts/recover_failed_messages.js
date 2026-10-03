/**
 * Recover Failed WhatsApp Messages Script
 * Safely parses and backfills inventory orders that failed due to Gemini Free Tier 503 spikes.
 * Handles deduplication of re-sent messages.
 */

const SUPABASE_URL = "https://bpeqfynknwuaqfjjqmlh.supabase.co";
const SERVICE_ROLE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM";

const headers = {
  "apikey": SERVICE_ROLE_KEY,
  "Authorization": `Bearer ${SERVICE_ROLE_KEY}`,
  "Content-Type": "application/json",
  "Prefer": "return=representation"
};

async function runRecovery(dryRun = true) {
  console.log(`=== WHATSAPP FAILED MESSAGES RECOVERY (Dry Run: ${dryRun}) ===\n`);

  // 1. Fetch current inventory catalog
  const invRes = await fetch(`${SUPABASE_URL}/rest/v1/inventory_items?is_active=eq.true`, { headers });
  const inventoryItems = await invRes.json();
  console.log(`Loaded ${inventoryItems.length} active inventory catalog items.\n`);

  // 2. Fetch candidates: error or received
  const msgRes = await fetch(`${SUPABASE_URL}/rest/v1/whatsapp_messages?status=in.(error,received)&order=id.asc`, { headers });
  const messages = await msgRes.json();
  console.log(`Found ${messages.length} messages to process.\n`);

  const processedOrders = [];
  const nonInventory = [];
  const duplicates = [];

  // Track seen texts within 2 hours to avoid duplicate order creation
  const seenTexts = new Map();

  for (const msg of messages) {
    const text = (msg.message_body || "").trim();

    // Check if this text is an exact duplicate of a recently sent message (for text messages only)
    if (msg.message_type === "text" && text.length > 0) {
      const normText = text.toLowerCase().replace(/\s+/g, " ");
      if (seenTexts.has(normText)) {
        duplicates.push({ msg, originalId: seenTexts.get(normText) });
        continue;
      }
      seenTexts.set(normText, msg.id);
    }

    // Parse specific failed messages
    let parsed = null;

    if (text === "Ice - 90/-") {
      parsed = {
        is_inventory: true,
        supplier_name: "",
        items: [{ item_name: "Ice", quantity: 1, unit: "pack", unit_price: 90, subtotal: 90 }],
        total_amount: 90,
        notes: "Recovered from message ID " + msg.id
      };
    } else if (text.includes("Amrapura - 2510rs")) {
      parsed = {
        is_inventory: true,
        supplier_name: "Amrapura",
        items: [{ item_name: "Milk / Dairy (Amrapura)", quantity: 1, unit: "order", unit_price: 2510, subtotal: 2510 }],
        total_amount: 2510,
        notes: "Recovered from message ID " + msg.id
      };
    } else if (text.includes("ganesh chanda")) {
      nonInventory.push({ msg, reason: "Festival donation / operational expense" });
      continue;
    } else if (text.includes("Ice - 450rs") && text.includes("5 packet")) {
      parsed = {
        is_inventory: true,
        supplier_name: "",
        items: [{ item_name: "Ice", quantity: 5, unit: "packet", unit_price: 90, subtotal: 450 }],
        total_amount: 450,
        notes: "Recovered from message ID " + msg.id
      };
    } else if (text.includes("Rent") && text.includes("Electricity bill")) {
      nonInventory.push({ msg, reason: "Rent & electricity operational expenses" });
      continue;
    } else if (text.includes("Pandian")) {
      parsed = {
        is_inventory: true,
        supplier_name: "Pandian",
        items: [
          { item_name: "Milk jug", quantity: 1, unit: "pcs", unit_price: 160, subtotal: 160 },
          { item_name: "Salt & pepper jar", quantity: 2, unit: "pcs", unit_price: 50, subtotal: 100 },
          { item_name: "Salt jar", quantity: 1, unit: "pcs", unit_price: 75, subtotal: 75 },
          { item_name: "Butter spoon", quantity: 1, unit: "pcs", unit_price: 30, subtotal: 30 }
        ],
        total_amount: 365,
        notes: "Recovered from message ID " + msg.id
      };
    } else if (text === "Bread - 130rs") {
      parsed = {
        is_inventory: true,
        supplier_name: "",
        items: [{ item_name: "Bread", quantity: 1, unit: "pack", unit_price: 130, subtotal: 130 }],
        total_amount: 130,
        notes: "Recovered from message ID " + msg.id
      };
    } else if (text.includes("Ice cream 2*405 - 810rs")) {
      parsed = {
        is_inventory: true,
        supplier_name: "",
        items: [{ item_name: "Ice cream", quantity: 2, unit: "pack", unit_price: 405, subtotal: 810 }],
        total_amount: 810,
        notes: "Recovered from message ID " + msg.id
      };
    } else if (text.includes("Fries - 280rs") && text.includes("Peri peri - 130rs")) {
      parsed = {
        is_inventory: true,
        supplier_name: "",
        items: [
          { item_name: "Fries", quantity: 1, unit: "pack", unit_price: 280, subtotal: 280 },
          { item_name: "Peri peri", quantity: 1, unit: "pack", unit_price: 130, subtotal: 130 }
        ],
        total_amount: 410,
        notes: "Recovered from message ID " + msg.id
      };
    }

    if (parsed) {
      processedOrders.push({ msg, parsed });
    }
  }

  console.log("=== SUMMARY OF RECOVERY PLAN ===");
  console.log(`Orders to create: ${processedOrders.length}`);
  for (const { msg, parsed } of processedOrders) {
    console.log(`  - [Msg #${msg.id} | ${msg.created_at.substring(0, 10)}] ${parsed.items.map(i => `${i.item_name} (₹${i.subtotal})`).join(", ")} | Total: ₹${parsed.total_amount}`);
  }

  console.log(`\nNon-inventory items to mark parse_failed: ${nonInventory.length}`);
  for (const { msg, reason } of nonInventory) {
    console.log(`  - [Msg #${msg.id}] "${msg.message_body.replace(/\n/g, " ")}" -> ${reason}`);
  }

  console.log(`\nDuplicate re-sent messages to resolve: ${duplicates.length}`);
  for (const { msg, originalId } of duplicates) {
    console.log(`  - [Msg #${msg.id}] "${msg.message_body.replace(/\n/g, " ")}" (duplicate of #${originalId})`);
  }

  if (dryRun) {
    console.log("\n[DRY RUN COMPLETE] No database changes made. Set dryRun = false to apply.");
    return;
  }

  // APPLY CHANGES
  console.log("\n=== APPLYING RECOVERY TO DATABASE ===");

  for (const { msg, parsed } of processedOrders) {
    // Generate order ID for the date of the message
    const msgDate = new Date(msg.created_at);
    const datePart = msgDate.toISOString().slice(0, 10).replace(/-/g, "");
    const prefix = `INV-${datePart}-`;

    const seqRes = await fetch(`${SUPABASE_URL}/rest/v1/inventory_orders?select=order_id&order_id=like.${prefix}*&order=order_id.desc&limit=1`, { headers });
    const existingOrders = await seqRes.json();
    let nextSeq = 1;
    if (existingOrders && existingOrders.length > 0) {
      const lastId = existingOrders[0].order_id;
      const lastSeq = parseInt(lastId.replace(prefix, ""), 10);
      if (!isNaN(lastSeq)) nextSeq = lastSeq + 1;
    }
    const orderId = `${prefix}${String(nextSeq).padStart(3, "0")}`;

    // Insert order
    await fetch(`${SUPABASE_URL}/rest/v1/inventory_orders`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        order_id: orderId,
        timestamp: msgDate.getTime(),
        supplier_name: parsed.supplier_name || "WhatsApp Order",
        total_amount: parsed.total_amount,
        notes: `Via WhatsApp (Recovered) | ${parsed.notes || ""}`.trim(),
        sync_status: "SYNCED"
      })
    });

    // Insert items
    const orderItems = parsed.items.map(item => ({
      order_id: orderId,
      item_name: item.item_name,
      quantity: item.quantity,
      unit: item.unit,
      unit_price: item.unit_price,
      subtotal: item.subtotal
    }));

    await fetch(`${SUPABASE_URL}/rest/v1/inventory_order_items`, {
      method: "POST",
      headers,
      body: JSON.stringify(orderItems)
    });

    // Update message
    await fetch(`${SUPABASE_URL}/rest/v1/whatsapp_messages?id=eq.${msg.id}`, {
      method: "PATCH",
      headers,
      body: JSON.stringify({
        status: "saved",
        parsed_result: parsed,
        inventory_order_id: orderId,
        error_message: null
      })
    });

    console.log(`✅ Created Order ${orderId} for Message #${msg.id}`);
  }

  // Update non-inventory
  for (const { msg, reason } of nonInventory) {
    await fetch(`${SUPABASE_URL}/rest/v1/whatsapp_messages?id=eq.${msg.id}`, {
      method: "PATCH",
      headers,
      body: JSON.stringify({
        status: "parse_failed",
        parsed_result: { is_inventory: false, items: [], notes: reason },
        error_message: null
      })
    });
    console.log(`Updated non-inventory message #${msg.id}`);
  }

  // Update duplicates
  for (const { msg, originalId } of duplicates) {
    await fetch(`${SUPABASE_URL}/rest/v1/whatsapp_messages?id=eq.${msg.id}`, {
      method: "PATCH",
      headers,
      body: JSON.stringify({
        status: "saved",
        error_message: `Duplicate of message #${originalId}`
      })
    });
    console.log(`Marked duplicate message #${msg.id}`);
  }

  console.log("\n=== RECOVERY COMPLETE ===");
}

const isExecute = process.argv.includes("--execute");
runRecovery(!isExecute).catch(console.error);
