// WhatsApp Inventory Webhook — Supabase Edge Function
// Handles: Meta webhook verification, incoming messages, multi-model Gemini AI parsing,
// backoff retries, persistent auto-retry queue, document/PDF invoice support, inventory order creation, and WhatsApp reply confirmations.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

// ==================== CONFIGURATION ====================
const VERIFY_TOKEN = Deno.env.get("WA_VERIFY_TOKEN") || "FILTR_WA_VERIFY_2026_SECURE";
const WA_ACCESS_TOKEN = Deno.env.get("WA_ACCESS_TOKEN") || "";
const WA_PHONE_NUMBER_ID = Deno.env.get("WA_PHONE_NUMBER_ID") || "";
const WA_APP_SECRET = Deno.env.get("WA_APP_SECRET") || "";
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY") || "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL") || "https://bpeqfynknwuaqfjjqmlh.supabase.co";
const SUPABASE_SERVICE_ROLE_KEY =
  Deno.env.get("SB_SERVICE_ROLE_KEY") ||
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ||
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJwZXFmeW5rbnd1YXFmampxbWxoIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4Njk0MTcxMiwiZXhwIjoyMTAyNTE3NzEyfQ.FpXVlPZsro_Lvsw3UFKho_LLEH2Kqy4JOstP2VVCJAM";

const GRAPH_API_VERSION = "v21.0";

// Verified active Gemini models for Google AI Studio API
const PRIMARY_GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") || "gemini-3.1-flash-lite";
const FALLBACK_GEMINI_MODELS = [
  "gemini-3.1-flash-lite",
  "gemini-3.5-flash-lite",
  "gemini-3.6-flash",
  "gemini-flash-lite-latest",
  "gemini-3-flash-preview",
];

// Phone number normalization & strict single-number whitelist: Somesh (+919907839311) ONLY
function normalizePhoneNumber(raw: string): string {
  return (raw || "").replace(/[^\d]/g, "").replace(/^0+/, "");
}

// STRICT AUTHORIZATION: Only Somesh (+919907839311) is permitted to trigger the inventory automation
const SOMESH_PHONE_NUMBER = "919907839311";

function isNumberAllowed(rawNumber: string): boolean {
  const normalized = normalizePhoneNumber(rawNumber);
  return normalized === SOMESH_PHONE_NUMBER || normalized === "9907839311";
}

// ==================== MAIN HTTP HANDLER ====================
Deno.serve(async (req: Request) => {
  const url = new URL(req.url);

  // === GET: Verification or Manual Retry Trigger ===
  if (req.method === "GET") {
    const action = url.searchParams.get("action");

    // Manual/Cron retry trigger
    if (action === "retry_pending" || action === "retry") {
      const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
      const results = await processPendingRetries(supabase, WA_PHONE_NUMBER_ID);
      return new Response(JSON.stringify({ status: "ok", retried: results }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Diagnostic: List all active Gemini models
    if (action === "list_models") {
      try {
        const resp = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${GEMINI_API_KEY}`);
        const data = await resp.json();
        return new Response(JSON.stringify(data), { status: 200, headers: { "Content-Type": "application/json" } });
      } catch (e: any) {
        return new Response(JSON.stringify({ error: e.message }), { status: 500, headers: { "Content-Type": "application/json" } });
      }
    }

    // Diagnostic: Inspect Meta WhatsApp Business Accounts & Phone numbers
    if (action === "get_meta_info") {
      try {
        const results: any = {};
        const metaHeaders = { Authorization: `Bearer ${WA_ACCESS_TOKEN}` };

        // 1. me
        const meResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/me?fields=id,name`, { headers: metaHeaders });
        results.me = await meResp.json();

        // 2. debug token
        const debugResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/debug_token?input_token=${WA_ACCESS_TOKEN}&access_token=${WA_ACCESS_TOKEN}`);
        results.debug_token = await debugResp.json();

        // 3. user businesses
        const uBizResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/122137573767095281/businesses`, { headers: metaHeaders });
        results.user_businesses = await uBizResp.json();

        // 4. app
        const appResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/794984353144205?fields=id,name`, { headers: metaHeaders });
        results.app = await appResp.json();

        // 5. search for phone numbers via common edges
        try {
          const pResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/122137573767095281/phone_numbers`, { headers: metaHeaders });
          results.phone_numbers = await pResp.json();
        } catch (e: any) { results.phone_numbers_err = e.message; }

        return new Response(JSON.stringify(results), { status: 200, headers: { "Content-Type": "application/json" } });
      } catch (e: any) {
        return new Response(JSON.stringify({ error: e.message }), { status: 500, headers: { "Content-Type": "application/json" } });
      }
    }

    // Diagnostic: Download WhatsApp media by ID
    if (action === "get_media") {
      const mediaId = url.searchParams.get("media_id") || "1387005036424564";
      const downloaded = await downloadWhatsAppMedia(mediaId);
      if (!downloaded) {
        return new Response(JSON.stringify({ error: "Download failed" }), { status: 400, headers: { "Content-Type": "application/json" } });
      }
      return new Response(JSON.stringify({
        media_id: mediaId,
        mimeType: downloaded.mimeType,
        base64Length: downloaded.base64.length,
        base64: downloaded.base64,
      }), { status: 200, headers: { "Content-Type": "application/json" } });
    }

    // Health check & diagnostic check
    if (action === "diag" || action === "health") {
      try {
        const testModel = url.searchParams.get("model") || "gemini-3.6-flash";
        const testResp = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${testModel}:generateContent?key=${GEMINI_API_KEY}`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              contents: [{ parts: [{ text: "Parse: Milk 5L Rs 280. Return JSON: {\"item\": \"Milk\", \"qty\": 5}" }] }],
              generationConfig: { temperature: 0.1, responseMimeType: "application/json" },
            }),
          }
        );
        const testData = await testResp.json();
        // Meta token validation & phone number lookup
        let metaStatus: any = null;
        let phoneNumbers: any = null;
        if (WA_ACCESS_TOKEN) {
          try {
            const metaCheck = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/debug_token?input_token=${WA_ACCESS_TOKEN}&access_token=${WA_ACCESS_TOKEN}`);
            metaStatus = await metaCheck.json();

            // Try to find business accounts and phone numbers
            const wabasResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/794984353144205/whatsapp_business_accounts`, {
              headers: { Authorization: `Bearer ${WA_ACCESS_TOKEN}` }
            });
            phoneNumbers = await wabasResp.json();
          } catch (e: any) {
            metaStatus = { error: e.message };
          }
        }

        return new Response(
          JSON.stringify({
            status: "ok",
            model: testModel,
            apiStatus: testResp.status,
            activeModels: FALLBACK_GEMINI_MODELS,
            waTokenConfigured: !!WA_ACCESS_TOKEN,
            waPhoneIdConfigured: !!WA_PHONE_NUMBER_ID,
            metaStatus,
            phoneNumbers,
            testData,
          }),
          {
            status: 200,
            headers: { "Content-Type": "application/json" },
          }
        );
      } catch (err: any) {
        return new Response(JSON.stringify({ error: err.message }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        });
      }
    }

    // Meta Webhook Verification
    const mode = url.searchParams.get("hub.mode");
    const token = url.searchParams.get("hub.verify_token");
    const challenge = url.searchParams.get("hub.challenge");

    if (mode === "subscribe" && token === VERIFY_TOKEN) {
      console.log("✅ Webhook verified successfully");
      return new Response(challenge, { status: 200 });
    }
    console.log("❌ Webhook verification failed: token mismatch");
    return new Response("Forbidden", { status: 403 });
  }

  // === POST: Incoming Messages or Retry Actions ===
  if (req.method === "POST") {
    try {
      const body = await req.json();

      // Manual retry endpoint via POST
      if (body?.action === "retry_pending" || url.searchParams.get("action") === "retry_pending") {
        const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
        const results = await processPendingRetries(supabase, WA_PHONE_NUMBER_ID);
        return new Response(JSON.stringify({ status: "ok", retried: results }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        });
      }

      // Process in background asynchronously with Edge isolate protection
      const processPromise = processWebhookEvent(body).catch((err) =>
        console.error("Background processing error:", err)
      );

      if (typeof (globalThis as any).EdgeRuntime?.waitUntil === "function") {
        (globalThis as any).EdgeRuntime.waitUntil(processPromise);
      }

      // Return 200 immediately (Meta requires < 5s acknowledgment)
      return new Response(JSON.stringify({ status: "ok" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    } catch (err) {
      console.error("Error parsing webhook body:", err);
      return new Response("OK", { status: 200 });
    }
  }

  return new Response("Method Not Allowed", { status: 405 });
});

// ==================== MESSAGE PROCESSING ====================
async function processWebhookEvent(body: any) {
  const entry = body?.entry?.[0];
  if (!entry) return;

  const changes = entry?.changes?.[0];
  if (!changes || changes.field !== "messages") return;

  const value = changes.value;
  const messages = value?.messages;
  if (!messages || messages.length === 0) return;

  const message = messages[0];
  const fromNumber = message.from;
  const messageId = message.id;
  const messageType = message.type; // 'text', 'image', 'document'

  // Auto-extract phone_number_id from webhook metadata (fallback if env not set)
  const metadataPhoneId = value?.metadata?.phone_number_id || "";
  const activePhoneNumberId = WA_PHONE_NUMBER_ID || metadataPhoneId;
  if (metadataPhoneId && !WA_PHONE_NUMBER_ID) {
    console.log(`📋 Auto-detected phone_number_id: ${metadataPhoneId}`);
  }

  // Whitelist check: ONLY Somesh (+919907839311)
  if (!isNumberAllowed(fromNumber)) {
    console.log(`🚫 Ignoring message from unauthorized number: ${fromNumber}`);
    return;
  }

  console.log(`📩 Message from Somesh (${fromNumber}): type=${messageType}, id=${messageId}`);

  // Initialize Supabase client
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);

  // Check for duplicate (idempotency)
  const { data: existing } = await supabase
    .from("whatsapp_messages")
    .select("id, status, parsed_result")
    .eq("message_id", messageId)
    .maybeSingle();

  if (existing && existing.status === "saved") {
    console.log("⏭️ Duplicate message already processed, skipping:", messageId);
    return;
  }

  // Extract media info for images or forwarded documents/PDFs
  let mediaId: string | undefined = undefined;
  let mimeType: string = "image/jpeg";
  let filename: string = "";

  if (messageType === "image") {
    mediaId = message.image?.id;
    mimeType = message.image?.mime_type || "image/jpeg";
  } else if (messageType === "document") {
    mediaId = message.document?.id;
    mimeType = message.document?.mime_type || "application/pdf";
    filename = message.document?.filename || "";
  }

  // Log or update the raw message
  let messageBody = "";
  if (messageType === "text") {
    messageBody = message.text?.body || "";
  } else if (messageType === "image") {
    messageBody = message.image?.caption || "[Image received]";
  } else if (messageType === "document") {
    messageBody = message.document?.caption || `[Document received: ${filename || "invoice"}]`;
  }

  // Persist message record with media_id so auto-retries can always re-fetch the media
  if (!existing) {
    await supabase.from("whatsapp_messages").insert({
      message_id: messageId,
      from_number: fromNumber,
      message_type: messageType,
      message_body: messageBody,
      status: "received",
      parsed_result: mediaId ? { media_id: mediaId, mime_type: mimeType, filename: filename } : null,
    });
  } else if (mediaId && (!existing.parsed_result || !existing.parsed_result.media_id)) {
    // Backfill media_id if existing record was missing it
    await supabase.from("whatsapp_messages").update({
      parsed_result: { ...(existing.parsed_result || {}), media_id: mediaId, mime_type: mimeType, filename: filename },
    }).eq("message_id", messageId);
  }

  // Also opportunistically trigger any earlier pending retries in background
  processPendingRetries(supabase, activePhoneNumberId).catch((e) =>
    console.warn("Background auto-retry sweep encountered error:", e.message)
  );

  try {
    // Process message through AI with multi-model fallback & backoff
    await processSingleMessage(
      supabase,
      messageId,
      fromNumber,
      messageType,
      messageBody,
      mediaId,
      mimeType,
      activePhoneNumberId,
      false
    );
  } catch (err: any) {
    console.error("Error processing message:", err);

    // Queue for automatic background retry instead of failing completely!
    await supabase.from("whatsapp_messages").update({
      status: "retry_pending",
      error_message: `AI service busy. Queued for auto-retry: ${err.message || String(err)}`,
    }).eq("message_id", messageId);

    // Reassure Somesh on WhatsApp
    await sendWhatsAppReply(
      fromNumber,
      activePhoneNumberId,
      "⏳ *Inventory Bill Received*\n\nOur AI parser is momentarily busy due to high demand. We've queued your bill for *automatic background retry*.\n\n✅ *No need to resend!* You will receive your confirmation message as soon as it's processed."
    );
  }
}

// ==================== CORE MESSAGE PROCESSOR ====================
async function processSingleMessage(
  supabase: any,
  messageId: string,
  fromNumber: string,
  messageType: string,
  messageBody: string,
  mediaId: string | undefined,
  mimeType: string | undefined,
  activePhoneNumberId: string,
  isRetry: boolean
): Promise<boolean> {
  // Fetch current inventory master items for AI context
  const { data: inventoryItems } = await supabase
    .from("inventory_items")
    .select("id, name, category, unit, standard_quantity, default_price, current_stock, is_active")
    .eq("is_active", true);

  const inventoryCatalog = (inventoryItems || [])
    .map((item: any) => `- ${item.name} (${item.category}) | Unit: ${item.unit} | Pack: ${item.standard_quantity}${item.unit} | Default Price: ₹${item.default_price}/pack | Stock: ${item.current_stock} packs`)
    .join("\n");

  let parsedResult: any = null;

  if (messageType === "text") {
    parsedResult = await parseTextMessage(messageBody, inventoryCatalog);
  } else if (messageType === "image" || messageType === "document") {
    if (!mediaId) {
      console.warn(`Message ID ${messageId} is missing mediaId. Cannot download media without mediaId.`);
      await supabase.from("whatsapp_messages").update({
        status: "media_expired",
        error_message: "Media ID was not stored. Please re-send or re-forward this bill.",
      }).eq("message_id", messageId);
      return false;
    }

    const downloaded = await downloadWhatsAppMedia(mediaId);
    if (downloaded) {
      const actualMime = downloaded.mimeType || mimeType || (messageType === "document" ? "application/pdf" : "image/jpeg");
      parsedResult = await parseMediaMessage(downloaded.base64, actualMime, messageBody, inventoryCatalog);
    } else {
      throw new Error("Could not download WhatsApp media from Meta");
    }
  } else {
    // Unsupported message type (audio, location, sticker)
    await sendWhatsAppReply(
      fromNumber,
      activePhoneNumberId,
      "❌ Sorry, I can only process text messages, photos, and PDF invoices. Please send your inventory bill as text, photo, or PDF."
    );
    await supabase.from("whatsapp_messages").update({ status: "unsupported" }).eq("message_id", messageId);
    return false;
  }

  // 1. Non-Inventory Expenses (Store supplies, equipment, rent, utilities, capex)
  const isNonInventory =
    parsedResult?.category === "store_expense" ||
    parsedResult?.category === "utility_expense" ||
    (parsedResult?.is_inventory === false && (parsedResult?.total_amount > 0 || (parsedResult?.notes && parsedResult?.notes.length > 5)));

  if (isNonInventory) {
    console.log("ℹ️ Message parsed as store / operational expense:", parsedResult.notes);
    await supabase.from("whatsapp_messages").update({
      status: "logged_as_expense",
      parsed_result: parsedResult,
      error_message: null,
    }).eq("message_id", messageId);

    const categoryTitle = parsedResult?.category === "utility_expense"
      ? "Rent & Utility Operating Expense"
      : "Store Supplies & Equipment Expense";

    let replyMsg = `🧾 *${categoryTitle} Logged!*\n\n`;
    if (parsedResult?.supplier_name && parsedResult?.supplier_name.trim()) {
      replyMsg += `📦 *Vendor / Recipient*: ${parsedResult.supplier_name.trim()}\n`;
    }
    if (parsedResult?.total_amount > 0) {
      replyMsg += `💰 *Amount*: *₹${parsedResult.total_amount.toLocaleString("en-IN")}*\n`;
    }
    if (parsedResult?.notes && parsedResult?.notes.trim()) {
      replyMsg += `📝 *Details*: ${parsedResult.notes.trim()}\n`;
    }
    replyMsg += `\nℹ️ _Recorded as operational expense. Raw ingredient stock was not altered._`;

    await sendWhatsAppReply(fromNumber, activePhoneNumberId, replyMsg);
    return true;
  }

  // 2. Unitemized Supplier Bills (e.g. "Kalpana traders - 780rs")
  const isUnitemizedVendor =
    parsedResult?.category === "unitemized_vendor" ||
    ((!parsedResult?.items || parsedResult.items.length === 0) &&
      (parsedResult?.total_amount > 0) &&
      (parsedResult?.supplier_name && parsedResult.supplier_name.trim().length > 0));

  if (isUnitemizedVendor) {
    const suppName = parsedResult.supplier_name.trim();
    const totalAmt = parsedResult.total_amount;

    // Create an unitemized procurement order so this expense is never lost
    const orderResult = await createInventoryOrder(supabase, {
      ...parsedResult,
      items: [{
        item_name: `Procurement - ${suppName}`,
        quantity: 1,
        unit: "bill",
        unit_price: totalAmt,
        subtotal: totalAmt
      }]
    }, inventoryItems || []);

    const { orderId, isDuplicate } = orderResult;
    if (isDuplicate) {
      await supabase.from("whatsapp_messages").update({
        status: "saved",
        parsed_result: parsedResult,
        inventory_order_id: orderId,
        error_message: `Duplicate of ${orderId}`,
      }).eq("message_id", messageId);

      const dupMsg = `ℹ️ *Bill Already Logged!*\n\nThis bill of *₹${totalAmt.toLocaleString("en-IN")}* for *${suppName}* was already recorded as Order *${orderId}*.\n\n✅ *No duplicate entry made.*`;
      await sendWhatsAppReply(fromNumber, activePhoneNumberId, dupMsg);
      return true;
    }

    await supabase.from("whatsapp_messages").update({
      status: "saved",
      parsed_result: parsedResult,
      inventory_order_id: orderId,
      error_message: null,
    }).eq("message_id", messageId);

    const replyMsg = `📝 *Supplier Bill ${orderId} Logged!*\n\n📦 *Supplier*: ${suppName}\n💰 *Total Amount*: *₹${totalAmt.toLocaleString("en-IN")}*\n\n✅ _Recorded as general supplier bill in FILTR._\n💡 _Tip: To track individual ingredients in inventory, send item names and quantities (e.g. "Hazelnut Syrup 2 btl ₹780")._`;
    await sendWhatsAppReply(fromNumber, activePhoneNumberId, replyMsg);
    return true;
  }

  // 3. Greeting, query, or unrecognizable message
  if (!parsedResult || !parsedResult.items || parsedResult.items.length === 0) {
    if (parsedResult?.category === "greeting_or_query" || !messageBody || messageBody.trim().length <= 4) {
      await sendWhatsAppReply(
        fromNumber,
        activePhoneNumberId,
        "👋 *Hello Somesh!*\n\nSend me your café procurement bills anytime:\n📸 *Photos of receipts / handwritten slips*\n📄 *PDF invoices*\n📝 *Text messages* (e.g. _Milk 5L ₹280, Ice 2pkt ₹180_)\n\nAll bills are automatically logged into FILTR billing!"
      );
    } else {
      await sendWhatsAppReply(
        fromNumber,
        activePhoneNumberId,
        "🤔 I couldn't detect specific items or amounts in your message.\n\nPlease send items with quantity and price:\n_Example: Milk 5L ₹280, Ice 2pkt ₹180, Bread 1pkt ₹130_"
      );
    }
    await supabase.from("whatsapp_messages").update({
      status: "parse_failed",
      parsed_result: parsedResult,
    }).eq("message_id", messageId);
    return false;
  }

  // Create inventory order in Supabase & update stock (with duplicate bill prevention)
  const orderResult = await createInventoryOrder(supabase, parsedResult, inventoryItems || []);
  const { orderId, isDuplicate } = orderResult;

  if (isDuplicate) {
    await supabase.from("whatsapp_messages").update({
      status: "saved",
      parsed_result: parsedResult,
      inventory_order_id: orderId,
      error_message: `Duplicate of ${orderId}`,
    }).eq("message_id", messageId);

    const dupMsg = `ℹ️ *Bill Already Logged!*\n\nThis bill of *₹${(parsedResult.total_amount || 0).toLocaleString("en-IN")}* was already recorded as Order *${orderId}*.\n\n✅ *No duplicate entry made.* Stock remains accurate.`;
    await sendWhatsAppReply(fromNumber, activePhoneNumberId, dupMsg);
    console.log(`ℹ️ Duplicate bill suppressed. Linked to existing order ${orderId}`);
    return true;
  }

  // Update the message record
  await supabase.from("whatsapp_messages").update({
    status: "saved",
    parsed_result: parsedResult,
    inventory_order_id: orderId,
    error_message: null,
  }).eq("message_id", messageId);

  // Send confirmation reply
  const confirmationMsg = formatConfirmationMessage(orderId, parsedResult, isRetry);
  await sendWhatsAppReply(fromNumber, activePhoneNumberId, confirmationMsg);

  console.log(`✅ Inventory order ${orderId} logged (isRetry: ${isRetry})`);
  return true;
}

// ==================== BACKGROUND AUTO-RETRY SWEEPER ====================
async function processPendingRetries(supabase: any, activePhoneNumberId: string): Promise<any[]> {
  const { data: pendingMessages, error } = await supabase
    .from("whatsapp_messages")
    .select("*")
    .eq("status", "retry_pending")
    .order("id", { ascending: true })
    .limit(10);

  if (error || !pendingMessages || pendingMessages.length === 0) {
    return [];
  }

  console.log(`🔄 Auto-retry sweep: found ${pendingMessages.length} pending message(s) to process...`);
  const results = [];

  for (const msg of pendingMessages) {
    if (!isNumberAllowed(msg.from_number)) {
      console.log(`🚫 Skipping retry for unauthorized sender: ${msg.from_number}`);
      continue;
    }
    try {
      console.log(`🔁 Auto-retrying message ID ${msg.id} (${msg.from_number}, type=${msg.message_type})...`);
      const mediaId = msg.parsed_result?.media_id;
      const mimeType = msg.parsed_result?.mime_type || (msg.message_type === "document" ? "application/pdf" : "image/jpeg");

      const success = await processSingleMessage(
        supabase,
        msg.message_id,
        msg.from_number,
        msg.message_type,
        msg.message_body,
        mediaId,
        mimeType,
        activePhoneNumberId,
        true // isRetry = true
      );
      results.push({ id: msg.id, success });
    } catch (err: any) {
      console.warn(`⚠️ Auto-retry failed for message ID ${msg.id}: ${err.message}`);
      results.push({ id: msg.id, success: false, error: err.message });
    }
  }

  return results;
}

// ==================== GEMINI AI PARSING & FALLBACKS ====================

async function parseTextMessage(text: string, inventoryCatalog: string): Promise<any> {
  const prompt = buildInventoryParsePrompt(text, inventoryCatalog, "text");
  return callGeminiAPI(prompt, null, "text/plain");
}

async function parseMediaMessage(
  mediaBase64: string,
  mimeType: string,
  caption: string,
  inventoryCatalog: string
): Promise<any> {
  const isPdf = mimeType.includes("pdf");
  const prompt = buildInventoryParsePrompt(
    caption || `Extract all inventory items from this ${isPdf ? "PDF invoice document" : "receipt photo"}`,
    inventoryCatalog,
    isPdf ? "pdf_document" : "image"
  );
  return callGeminiAPI(prompt, mediaBase64, mimeType);
}

function buildInventoryParsePrompt(userMessage: string, inventoryCatalog: string, inputType: string): string {
  return `You are an intelligent AI procurement & accounting assistant for FILTR Coffee, a café in India. Your job is to parse incoming procurement/bill messages, receipt photos, or PDF invoices and extract structured data.

## CURRENT INVENTORY MASTER CATALOG:
${inventoryCatalog || "No inventory items configured yet."}

## USER MESSAGE (${inputType}):
"${userMessage}"

## YOUR TASK:
Classify the message and extract data into one of the following categories:
1. "inventory": Raw cafe consumable ingredients, syrups, dairy/milk, coffee beans, sauces, frozen items, and packaging (cups, lids, straws, tissues, tray plates).
2. "store_expense": Non-consumable cafe hardware, utensils, equipment, baskets, storage containers, weighing machines, dispenser covers, appliances, paint, repairs, or maintenance.
3. "utility_expense": Store operating expenses like shop rent, electricity bills, Wi-Fi recharge, transportation/delivery (Rapido/courier).
4. "unitemized_vendor": A supplier and total amount is mentioned (e.g., "Kalpana traders - 780rs") without an itemized list of goods.
5. "greeting_or_query": Greetings ("hi", "hello", "good morning"), status questions, or casual chat.

For items in "inventory":
- Match to existing catalog items using fuzzy matching (e.g. "doodh" -> "Milk", "coffee powder" -> "Roasted Coffee Beans", "cheeni" -> "Sugar", "tnt vanilla" -> "Vanilla Base Ice Cream", "pama" -> "Caramel Sauce PAMAC (500ml)").
- Extract: item_name, quantity, unit, unit_price, subtotal.
- Infer supplier name if visible on the receipt header or mentioned in text (e.g. "SHRI-AMRAPUR", "KE ESTIMATE 25", "The Beanster Company", "D-Mart", "Pandian").
- Infer receipt/slip date if written or printed (e.g. "20/09/26").

## RULES:
- Always return strictly valid JSON
- Quantities must be positive numbers
- Prices should be in Indian Rupees (₹)
- Round subtotals and totals to 2 decimal places

## OUTPUT FORMAT (STRICT JSON ONLY):
{
  "category": "inventory",
  "is_inventory": true,
  "supplier_name": "Supplier or Vendor name or empty string",
  "items": [
    {
      "item_name": "Item name",
      "catalog_item_id": null,
      "quantity": 5.0,
      "unit": "L",
      "unit_price": 56.0,
      "subtotal": 280.0
    }
  ],
  "total_amount": 280.0,
  "notes": "Concise summary including invoice number or date if visible"
}`;
}

// ==================== RESILIENT GEMINI CALLER ====================

async function callGeminiAPI(
  prompt: string,
  mediaBase64: string | null,
  mimeType: string = "image/jpeg"
): Promise<any> {
  let lastError: any = null;

  for (const model of FALLBACK_GEMINI_MODELS) {
    for (let attempt = 1; attempt <= 2; attempt++) {
      try {
        console.log(`🤖 Invoking Gemini [${model}] (attempt ${attempt}/2)...`);
        const result = await callGeminiSingle(model, prompt, mediaBase64, mimeType);
        console.log(`✅ Gemini [${model}] successfully parsed response!`);
        return result;
      } catch (err: any) {
        lastError = err;
        const errMsg = err.message || String(err);
        const isTransient =
          errMsg.includes("503") ||
          errMsg.includes("429") ||
          errMsg.includes("500") ||
          errMsg.includes("502") ||
          errMsg.includes("504") ||
          errMsg.includes("high demand") ||
          errMsg.includes("UNAVAILABLE") ||
          errMsg.includes("RESOURCE_EXHAUSTED");

        console.warn(`⚠️ Model [${model}] attempt ${attempt} failed: ${errMsg}`);

        if (isTransient) {
          const delayMs = attempt * 1500 + Math.floor(Math.random() * 800);
          console.log(`⏳ Backoff: waiting ${delayMs}ms before next retry...`);
          await new Promise((resolve) => setTimeout(resolve, delayMs));
        } else {
          // If 404 or other non-transient error, immediately cascade to next model
          break;
        }
      }
    }
  }

  throw new Error(`All Gemini models exhausted. Last error: ${lastError?.message || lastError}`);
}

async function callGeminiSingle(
  modelName: string,
  prompt: string,
  mediaBase64: string | null,
  mimeType: string = "image/jpeg"
): Promise<any> {
  const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${GEMINI_API_KEY}`;

  const parts: any[] = [];

  if (mediaBase64) {
    parts.push({
      inlineData: {
        mimeType: mimeType,
        data: mediaBase64,
      },
    });
  }

  parts.push({ text: prompt });

  const requestBody = {
    contents: [{ parts }],
    generationConfig: {
      temperature: 0.1,
      responseMimeType: "application/json",
    },
  };

  const response = await fetch(apiUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(requestBody),
  });

  if (!response.ok) {
    const errText = await response.text();
    throw new Error(`Gemini API error (${response.status}): ${errText}`);
  }

  const data = await response.json();
  const text = data?.candidates?.[0]?.content?.parts?.[0]?.text;

  if (!text) {
    throw new Error("Empty Gemini response");
  }

  try {
    return JSON.parse(text);
  } catch {
    const jsonMatch = text.match(/```json\s*([\s\S]*?)```/) || text.match(/\{[\s\S]*\}/);
    if (jsonMatch) {
      return JSON.parse(jsonMatch[1] || jsonMatch[0]);
    }
    throw new Error(`Failed to parse Gemini response as JSON: ${text.substring(0, 200)}`);
  }
}

// ==================== WHATSAPP MEDIA DOWNLOAD ====================

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const len = bytes.byteLength;
  const chunkSize = 8192;
  for (let i = 0; i < len; i += chunkSize) {
    const chunk = bytes.subarray(i, Math.min(i + chunkSize, len));
    binary += String.fromCharCode.apply(null, chunk as unknown as number[]);
  }
  return btoa(binary);
}

async function downloadWhatsAppMedia(
  mediaId: string | undefined
): Promise<{ base64: string; mimeType: string } | null> {
  if (!mediaId || !WA_ACCESS_TOKEN) {
    console.warn(`Cannot download media: mediaId=${mediaId}, tokenPresent=${!!WA_ACCESS_TOKEN}`);
    return null;
  }

  try {
    const metaResp = await fetch(
      `https://graph.facebook.com/${GRAPH_API_VERSION}/${mediaId}`,
      { headers: { Authorization: `Bearer ${WA_ACCESS_TOKEN}` } }
    );
    const metaData = await metaResp.json();
    const downloadUrl = metaData.url;
    const mimeType = metaData.mime_type || "image/jpeg";

    if (!metaResp.ok || !downloadUrl) {
      console.error(`Meta media lookup failed for ID ${mediaId}: status=${metaResp.status}`, metaData);
      return null;
    }

    const mediaResp = await fetch(downloadUrl, {
      headers: { Authorization: `Bearer ${WA_ACCESS_TOKEN}` },
    });

    if (!mediaResp.ok) {
      console.error(`Meta media binary fetch failed for ID ${mediaId}: status=${mediaResp.status}`);
      return null;
    }

    const arrayBuffer = await mediaResp.arrayBuffer();
    const base64 = arrayBufferToBase64(arrayBuffer);
    console.log(`📥 Successfully downloaded media ID ${mediaId} (${mimeType}, ${arrayBuffer.byteLength} bytes)`);
    return { base64, mimeType };
  } catch (err) {
    console.error("Error downloading WhatsApp media:", err);
    return null;
  }
}

// ==================== INVENTORY ORDER CREATION ====================

async function createInventoryOrder(
  supabase: any,
  parsedResult: any,
  inventoryItems: any[]
): Promise<{ orderId: string; isDuplicate: boolean }> {
  const totalAmount =
    parsedResult.total_amount ||
    parsedResult.items.reduce((sum: number, item: any) => sum + (item.subtotal || 0), 0);

  // === SMART DUPLICATE BILL PROTECTION ===
  // Check if an order with the exact same total amount was created within the last 48 hours
  const recentCutoff = Date.now() - (48 * 60 * 60 * 1000);
  const { data: recentOrders } = await supabase
    .from("inventory_orders")
    .select("order_id, supplier_name, total_amount, timestamp, notes")
    .gte("timestamp", recentCutoff)
    .eq("total_amount", totalAmount);

  if (recentOrders && recentOrders.length > 0) {
    for (const ro of recentOrders) {
      const timeDiffHours = Math.abs(Date.now() - (ro.timestamp || 0)) / (1000 * 60 * 60);
      const noteA = (parsedResult.notes || "").toLowerCase();
      const noteB = (ro.notes || "").toLowerCase();

      // Check date matching in note (e.g. "date: 20/09/26")
      const extractDate = (s: string) => {
        const m = s.match(/\b(\d{1,2}[\/\-]\d{1,2}[\/\-]\d{2,4})\b/);
        return m ? m[1] : null;
      };
      const dateA = extractDate(noteA);
      const dateB = extractDate(noteB);
      const isExactDateDuplicate = dateA && dateB && dateA === dateB;

      // Check supplier match
      const suppA = (parsedResult.supplier_name || "").toLowerCase().trim();
      const suppB = (ro.supplier_name || "").toLowerCase().trim();
      const sameSupplier = suppA && suppB && suppA === suppB && suppA !== "whatsapp order";

      // Small consumables like Ice (₹90, ₹180, ₹270) are routinely purchased multiple times a day
      const isFrequentConsumable =
        totalAmount <= 270 &&
        (noteA.includes("ice") ||
          (parsedResult.items &&
            parsedResult.items.some((i: any) => i.item_name.toLowerCase().includes("ice"))));

      if (isFrequentConsumable) {
        // Only suppress if within 2 hours or same bill date
        if (timeDiffHours < 2.0 || isExactDateDuplicate) {
          console.log(`⚠️ Prevented duplicate consumable order for ${ro.order_id} (amount: ₹${totalAmount})`);
          return { orderId: ro.order_id, isDuplicate: true };
        }
      } else {
        // For general bills: suppress if same bill date, same supplier, or sent within 12 hours
        if (isExactDateDuplicate || sameSupplier || timeDiffHours < 12.0) {
          console.log(`⚠️ Prevented duplicate procurement order for ${ro.order_id} (amount: ₹${totalAmount})`);
          return { orderId: ro.order_id, isDuplicate: true };
        }
      }
    }
  }

  const now = new Date();
  const datePart = now.toISOString().slice(0, 10).replace(/-/g, "");
  const prefix = `INV-${datePart}-`;

  const { data: existingOrders } = await supabase
    .from("inventory_orders")
    .select("order_id")
    .like("order_id", `${prefix}%`)
    .order("order_id", { ascending: false })
    .limit(1);

  let nextSeq = 1;
  if (existingOrders && existingOrders.length > 0) {
    const lastId = existingOrders[0].order_id;
    const lastSeq = parseInt(lastId.replace(prefix, ""), 10);
    if (!isNaN(lastSeq)) nextSeq = lastSeq + 1;
  }
  const orderId = `${prefix}${String(nextSeq).padStart(3, "0")}`;

  // Insert order header
  await supabase.from("inventory_orders").insert({
    order_id: orderId,
    timestamp: Date.now(),
    supplier_name: parsedResult.supplier_name || "WhatsApp Order",
    total_amount: totalAmount,
    notes: `Via WhatsApp | ${parsedResult.notes || ""}`.trim(),
    sync_status: "SYNCED",
  });

  // Insert order items
  const orderItems = parsedResult.items.map((item: any) => ({
    order_id: orderId,
    item_name: item.item_name,
    quantity: item.quantity || 0,
    unit: item.unit || "pcs",
    unit_price: item.unit_price || 0,
    subtotal: item.subtotal || 0,
  }));

  if (orderItems.length > 0) {
    await supabase.from("inventory_order_items").insert(orderItems);
  }

  // Update stock for matched inventory items
  for (const parsedItem of parsedResult.items) {
    if (parsedItem.catalog_item_id) {
      const masterItem = inventoryItems.find((i: any) => i.id === parsedItem.catalog_item_id);
      if (masterItem) {
        const newStock = (masterItem.current_stock || 0) + (parsedItem.quantity || 0);
        await supabase
          .from("inventory_items")
          .update({ current_stock: newStock })
          .eq("id", masterItem.id);
      }
    } else {
      const matchedItem = inventoryItems.find(
        (i: any) => i.name.toLowerCase() === parsedItem.item_name.toLowerCase()
      );
      if (matchedItem) {
        const packQty = matchedItem.standard_quantity > 0 ? matchedItem.standard_quantity : 1;
        const packsToAdd = parsedItem.quantity / packQty;
        const newStock = (matchedItem.current_stock || 0) + packsToAdd;
        await supabase
          .from("inventory_items")
          .update({ current_stock: newStock })
          .eq("id", matchedItem.id);
      }
    }
  }

  return { orderId, isDuplicate: false };
}

// ==================== WHATSAPP REPLY ====================

async function sendWhatsAppReply(to: string, phoneNumberId: string, text: string) {
  let phoneId = phoneNumberId || WA_PHONE_NUMBER_ID;
  if (!phoneId) {
    try {
      const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
      const { data: metaRow } = await supabase
        .from("sync_metadata")
        .select("value")
        .eq("key", "wa_phone_number_id")
        .maybeSingle();
      if (metaRow?.value) phoneId = metaRow.value;
    } catch (e: any) {
      console.warn("Could not read wa_phone_number_id from sync_metadata:", e.message);
    }
  }

  if (!WA_ACCESS_TOKEN || !phoneId) {
    console.log("⚠️ WhatsApp reply skipped (missing token/phone_id):", text);
    return;
  }

  try {
    const response = await fetch(
      `https://graph.facebook.com/${GRAPH_API_VERSION}/${phoneId}/messages`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${WA_ACCESS_TOKEN}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          messaging_product: "whatsapp",
          to: to,
          type: "text",
          text: { body: text },
        }),
      }
    );

    if (!response.ok) {
      const errText = await response.text();
      console.error("WhatsApp reply failed:", errText);
    } else {
      console.log(`📱 WhatsApp reply successfully delivered to ${to} via phone ${phoneId}`);
    }
  } catch (err) {
    console.error("Error sending WhatsApp reply:", err);
  }
}

// ==================== FORMATTING ====================

function formatConfirmationMessage(orderId: string, parsedResult: any, isRetry = false): string {
  let msg = `✅ *Inventory Order ${orderId} logged!*\n`;
  if (isRetry) {
    msg += `_(Auto-processed after temporary service queue)_\n`;
  }
  msg += `\n`;

  for (const item of parsedResult.items) {
    const price = item.unit_price > 0 ? ` @ ₹${item.unit_price}` : "";
    const subtotal = item.subtotal > 0 ? ` = *₹${item.subtotal.toLocaleString("en-IN")}*` : "";
    msg += `• *${item.item_name}*: ${item.quantity} ${item.unit || "pcs"}${price}${subtotal}\n`;
  }

  if (parsedResult.total_amount > 0) {
    msg += `\n💰 *Total Amount: ₹${parsedResult.total_amount.toLocaleString("en-IN")}*`;
  }

  if (parsedResult.supplier_name && parsedResult.supplier_name.trim()) {
    msg += `\n📦 *Supplier*: ${parsedResult.supplier_name.trim()}`;
  }

  if (parsedResult.notes && parsedResult.notes.trim()) {
    msg += `\n📝 _Note: ${parsedResult.notes.trim()}_`;
  }

  msg += `\n\n✨ _Order saved in FILTR app & stock updated automatically._`;

  return msg;
}
