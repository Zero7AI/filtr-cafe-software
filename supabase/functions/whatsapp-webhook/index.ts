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
const PRIMARY_GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") || "gemini-flash-lite-latest";
const FALLBACK_GEMINI_MODELS = [
  "gemini-flash-lite-latest",
  "gemini-3-flash-preview",
  "gemini-2.5-flash-lite",
  "gemini-3.1-flash-lite-preview",
  "gemini-flash-latest",
];

async function resolveGeminiKeys(supabase: any): Promise<string[]> {
  const keys: string[] = [];
  const envKey = (GEMINI_API_KEY || "").trim();
  if (envKey.length >= 30 && envKey.startsWith("AIza")) {
    keys.push(envKey);
  }
  try {
    const { data } = await supabase
      .from("sync_metadata")
      .select("value")
      .eq("key", "gemini_config")
      .maybeSingle();
    const raw = data?.value;
    if (raw) {
      const text = typeof raw === "string" ? raw : JSON.stringify(raw);
      const matches = text.match(/AIza[A-Za-z0-9_\-]{30,}/g) || [];
      for (const m of matches) {
        if (!keys.includes(m)) keys.push(m);
      }
    }
  } catch (e: any) {
    console.warn("Could not read gemini_config from sync_metadata:", e.message);
  }
  return keys;
}

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

    // Manual/Cron Google Sheet SKU Price History backfill
    if (action === "sync_sheets") {
      const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
      const syncResult = await syncSkuPriceHistoryToGoogleSheet(supabase);
      return new Response(JSON.stringify(syncResult), {
        status: syncResult.synced ? 200 : 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    // Diagnostic: List all active Gemini models
    if (action === "list_models") {
      try {
        const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
        const keys = await resolveGeminiKeys(supabase);
        const keyToUse = keys[0] || GEMINI_API_KEY;
        const resp = await fetch(`https://generativelanguage.googleapis.com/v1beta/models?key=${keyToUse}`);
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
        const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
        const keys = await resolveGeminiKeys(supabase);
        const keyToUse = keys[0] || GEMINI_API_KEY;
        const testModel = url.searchParams.get("model") || PRIMARY_GEMINI_MODEL;
        const testResp = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${testModel}:generateContent?key=${keyToUse}`,
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
        const { data: sheetMeta } = await supabase
          .from("sync_metadata")
          .select("value")
          .eq("key", "google_sheets_webhook_url")
          .maybeSingle();
        let metaStatus: any = null;
        let phoneNumbers: any = null;
        if (WA_ACCESS_TOKEN) {
          try {
            const metaCheck = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/debug_token?input_token=${WA_ACCESS_TOKEN}&access_token=${WA_ACCESS_TOKEN}`);
            metaStatus = await metaCheck.json();

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
            geminiKeyCount: keys.length,
            activeModels: FALLBACK_GEMINI_MODELS,
            googleSheetsWebhookConfigured: Boolean(sheetMeta?.value && String(sheetMeta.value).trim()),
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
    const errMsg = err?.message || String(err);
    const isAuthError =
      errMsg.includes("(401)") ||
      errMsg.includes("(403)") ||
      errMsg.includes("UNAUTHENTICATED") ||
      errMsg.includes("API key not valid") ||
      errMsg.includes("No valid Gemini API key");

    await supabase.from("whatsapp_messages").update({
      status: isAuthError ? "config_error" : "retry_pending",
      error_message: isAuthError
        ? `AUTH_ERROR: Gemini API key invalid or missing. Queued for retry once key is updated: ${errMsg}`
        : `Queued for auto-retry: ${errMsg}`,
    }).eq("message_id", messageId);

    const userNotice = isAuthError
      ? "⚠️ *Inventory Bill Saved to Queue (Action Needed)*\n\nOur Gemini AI key needs renewal, so your bill could not be parsed automatically right now.\n\n✅ Your bill is safely queued and will auto-process once Admin updates the AI key."
      : "⏳ *Inventory Bill Received*\n\nOur AI parser is momentarily busy due to high demand. We've queued your bill for *automatic background retry*.\n\n✅ *No need to resend!* You will receive your confirmation message as soon as it's processed.";

    await sendWhatsAppReply(fromNumber, activePhoneNumberId, userNotice);
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
    parsedResult = await parseTextMessage(supabase, messageBody, inventoryCatalog);
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
      parsedResult = await parseMediaMessage(supabase, downloaded.base64, actualMime, messageBody, inventoryCatalog);
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
    .in("status", ["retry_pending", "config_error"])
    .order("id", { ascending: true })
    .limit(25);

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

async function parseTextMessage(supabase: any, text: string, inventoryCatalog: string): Promise<any> {
  const prompt = buildInventoryParsePrompt(text, inventoryCatalog, "text");
  return callGeminiAPI(supabase, prompt, null, "text/plain");
}

async function parseMediaMessage(
  supabase: any,
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
  return callGeminiAPI(supabase, prompt, mediaBase64, mimeType);
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
- Indian Shorthand Text Bills ("<AMOUNT> - <items>"): When a text bill starts with a number followed by "-" (e.g., "1520 - condense milk", "1041 - 9 peach iced, 1 lemon iced"), the leading number is ALWAYS total_amount in INR — NEVER treat it as a quantity or invoice number; split item subtotals so they sum to that exact total.
- Container Count Units: When an invoice bills a multi-kg/multi-L gallon, tub, carton, or box by count (e.g., "6 Nos" of "GALLON VANILLA 5LT @ 405"), output unit: "pack" (NOT "kg" or "L").

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
  supabase: any,
  prompt: string,
  mediaBase64: string | null,
  mimeType: string = "image/jpeg"
): Promise<any> {
  const keys = await resolveGeminiKeys(supabase);
  if (keys.length === 0) {
    throw new Error("No valid Gemini API key configured (API key not valid)");
  }

  let lastError: any = null;

  for (const apiKey of keys) {
    for (const model of FALLBACK_GEMINI_MODELS) {
      for (let attempt = 1; attempt <= 2; attempt++) {
        try {
          console.log(`🤖 Invoking Gemini [${model}] (attempt ${attempt}/2)...`);
          const result = await callGeminiSingle(apiKey, model, prompt, mediaBase64, mimeType);
          console.log(`✅ Gemini [${model}] successfully parsed response!`);
          return result;
        } catch (err: any) {
          lastError = err;
          const errMsg = err.message || String(err);
          const isAuth =
            errMsg.includes("(401)") ||
            errMsg.includes("(403)") ||
            errMsg.includes("API key not valid") ||
            errMsg.includes("UNAUTHENTICATED");
          if (isAuth) {
            console.warn(`🔑 Key rejected (${errMsg.slice(0, 120)}); trying next key if available...`);
            break;
          }
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
            break;
          }
        }
      }
      if (
        lastError?.message &&
        (lastError.message.includes("(401)") ||
          lastError.message.includes("(403)") ||
          lastError.message.includes("API key not valid"))
      ) {
        break;
      }
    }
  }

  throw new Error(`All Gemini models exhausted. Last error: ${lastError?.message || lastError}`);
}

async function callGeminiSingle(
  apiKey: string,
  modelName: string,
  prompt: string,
  mediaBase64: string | null,
  mimeType: string = "image/jpeg"
): Promise<any> {
  const apiUrl = `https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent?key=${apiKey}`;

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

  // Automatically sync updated SKU Price History to live Google Sheet without blocking confirmation
  syncSkuPriceHistoryToGoogleSheet(supabase).catch((e) =>
    console.warn("Google Sheet auto-sync skipped:", e?.message || e)
  );

  return { orderId, isDuplicate: false };
}

// ==================== LIVE GOOGLE SHEET SKU PRICE HISTORY SYNC ====================

const HARDWARE_TERMS = new Set([
  "stand", "dispenser", "cover", "machine", "basket", "repair", "repairs",
  "rent", "electricity", "wifi", "wi-fi", "rapido", "courier", "paint",
  "table", "chair", "shelf", "rack", "stool", "signage", "board", "banner",
  "delivery", "transport", "freight",
]);

function normalizeSkuForSheet(raw: string): string {
  let s = (raw || "").trim().toLowerCase();
  s = s
    .replace(/\b(tht|tnt|tkt)\s*(vanilla|vancia)\b/gi, "vanilla base ice cream")
    .replace(/\bgallon\s+vanilla\b/gi, "vanilla base ice cream")
    .replace(/\bvanilla\s*5\s*(l|lt|ltr)\b/gi, "vanilla base ice cream")
    .replace(/\bperi\s*[-]?\s*peri\b/gi, "peri peri seasoning")
    .replace(/\b(pama|pamac)\b/gi, "caramel sauce pamac")
    .replace(/\bdoodh\b/gi, "milk")
    .replace(/\bcheeni\b/gi, "sugar")
    .replace(/\bmonin\s+caramel(\s+syrup)?\b/gi, "monin caramel syrup")
    .replace(/\b\d+(?:\.\d+)?\s*(?:kgs?|kilograms?|gms?|grams?|g|ltrs?|litres?|liters?|lt|l|mls?|ml|pcs?|pieces?|nos?|pkts?|packs?|packets?|btls?|bottles?|tins?|cans?|jars?|boxes?)\b/gi, " ")
    .replace(/[^a-z0-9\s]+/g, " ")
    .replace(/\s+/g, " ")
    .trim();
  return s;
}

function matchMasterSkuForSheet(rawName: string, catalog: any[]): any | null {
  const clean = (rawName || "").trim();
  if (!clean || !catalog?.length) return null;
  const exact = catalog.find((i: any) => i.name.trim().toLowerCase() === clean.toLowerCase());
  if (exact) return exact;
  const rawTokens = clean.toLowerCase().split(/[^a-z0-9-]+/).filter(Boolean);
  if (rawTokens.some((t) => HARDWARE_TERMS.has(t))) return null;

  const cleanNorm = normalizeSkuForSheet(clean);
  if (!cleanNorm) return null;
  const cleanWords = cleanNorm.split(" ").filter((w) => w.length >= 3);
  let best: any = null;
  let bestScore = 0;
  for (const cand of catalog) {
    const candNorm = normalizeSkuForSheet(cand.name);
    if (!candNorm) continue;
    if (candNorm === cleanNorm) return cand;
    const candWords = candNorm.split(" ").filter((w) => w.length >= 3);
    const overlap = cleanWords.filter((w) => candWords.includes(w));
    const minLen = Math.max(1, Math.min(cleanWords.length, candWords.length));
    const cov = overlap.length / minLen;
    let score = 0;
    if (overlap.length === 1 && ["water", "ice", "milk", "sugar", "cup", "cups"].includes(overlap[0]) && cleanWords.length >= 4) {
      score = 0;
    } else if (candNorm.includes(cleanNorm) || cleanNorm.includes(candNorm)) {
      score = 120 + overlap.length * 5;
    } else if (cov >= 0.6) {
      score = 60 + cov * 30;
    }
    if (score > bestScore) {
      bestScore = score;
      best = cand;
    }
  }
  return bestScore > 0 ? best : null;
}

function normalizeUnitPriceForSheet(line: any, master: any): number {
  const qty = Number(line.quantity || 0);
  const unitPrice = Number(line.unit_price || 0);
  const subtotal = Number(line.subtotal || 0);
  if (qty <= 0) return Math.round(Math.max(0, unitPrice) * 100) / 100;
  const total = subtotal > 0 ? subtotal : qty * unitPrice;
  if (total <= 0) return 0;
  const rawPerUnit = total / qty;
  if (!master) return Math.round(rawPerUnit * 100) / 100;

  const bUnit = String(line.unit || "").trim().toLowerCase();
  const mUnit = String(master.unit || "").trim().toLowerCase();
  const packSize = Number(master.standard_quantity || 1) > 0 ? Number(master.standard_quantity) : 1;

  const isG = ["g", "gm", "gms", "gram", "grams"].includes(bUnit);
  const isKg = ["kg", "kgs", "kilo", "kilos", "kilogram", "kilograms"].includes(bUnit);
  const mIsG = ["g", "gm", "gms", "gram", "grams"].includes(mUnit);
  const mIsKg = ["kg", "kgs", "kilo", "kilos", "kilogram", "kilograms"].includes(mUnit);

  const isMl = ["ml", "mls", "milliliter", "milliliters"].includes(bUnit);
  const isL = ["l", "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres"].includes(bUnit);
  const mIsMl = ["ml", "mls", "milliliter", "milliliters"].includes(mUnit);
  const mIsL = ["l", "lt", "ltr", "ltrs", "liter", "liters", "litre", "litres"].includes(mUnit);

  if ((isG || isKg) && (mIsG || mIsKg)) {
    const conv = isG && mIsKg ? qty / 1000 : isKg && mIsG ? qty * 1000 : qty;
    const packs = conv / packSize;
    return packs > 0 ? Math.round((total / packs) * 100) / 100 : Math.round(rawPerUnit * 100) / 100;
  }
  if ((isMl || isL) && (mIsMl || mIsL)) {
    const conv = isMl && mIsL ? qty / 1000 : isL && mIsMl ? qty * 1000 : qty;
    const packs = conv / packSize;
    return packs > 0 ? Math.round((total / packs) * 100) / 100 : Math.round(rawPerUnit * 100) / 100;
  }
  return Math.round(rawPerUnit * 100) / 100;
}

async function syncSkuPriceHistoryToGoogleSheet(supabase: any): Promise<{ synced: boolean; count?: number; error?: string }> {
  const { data: metaRow } = await supabase
    .from("sync_metadata")
    .select("value")
    .eq("key", "google_sheets_webhook_url")
    .maybeSingle();

  const webhookUrl = String(metaRow?.value || "").trim();
  if (!webhookUrl) {
    return { synced: false, error: "google_sheets_webhook_url not set" };
  }

  const [{ data: items }, { data: orders }] = await Promise.all([
    supabase.from("inventory_items").select("*").order("name"),
    supabase.from("inventory_orders").select("*, inventory_order_items(*)").order("timestamp", { ascending: true }).limit(1000),
  ]);

  const catalog = items || [];
  const shortMonths = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  const entriesByKey = new Map<string, any[]>();
  const ledger: any[] = [];
  const lastValidLedger = new Map<string, number>();

  for (const ord of orders || []) {
    const ts = Number(ord.timestamp || Date.now());
    const dIst = new Date(ts + 5.5 * 60 * 60 * 1000);
    const shortDate = `${String(dIst.getUTCDate()).padStart(2, "0")} ${shortMonths[dIst.getUTCMonth()]}`;
    const fullDate = new Date(ts).toLocaleString("en-IN", { timeZone: "Asia/Kolkata" });

    for (const line of ord.inventory_order_items || []) {
      if (!line.item_name || line.quantity <= 0) continue;
      const isHw = String(line.item_name).toLowerCase().split(/[^a-z0-9-]+/).some((t: string) => HARDWARE_TERMS.has(t));
      const matched = isHw ? null : matchMasterSkuForSheet(line.item_name, catalog);
      const skuName = matched ? matched.name : (isHw ? "Non-Ingredient / Hardware" : line.item_name.trim());
      const rate = normalizeUnitPriceForSheet(line, matched);
      const key = skuName.toLowerCase();
      const prevL = lastValidLedger.get(key);
      const isAnom = !isHw && prevL !== undefined && rate > 0 && (rate > prevL * 3.0 || rate < prevL * 0.3);
      if (!isAnom && !isHw && rate > 0) lastValidLedger.set(key, rate);

      ledger.push({
        orderId: ord.order_id,
        dateFormatted: fullDate,
        supplierName: ord.supplier_name || "Supplier",
        rawItemName: line.item_name,
        matchedSkuName: skuName,
        quantity: line.quantity,
        unit: line.unit || "pcs",
        unitPrice: line.unit_price || 0,
        subtotal: line.subtotal || 0,
        normalizedPrice: rate,
        statusLabel: isHw ? "HARDWARE / EXCLUDED" : isAnom ? "CHECK UNIT" : "OK",
        notes: ord.notes || "",
      });

      if (isHw || rate <= 0) continue;
      const arr = entriesByKey.get(key) || [];
      arr.push({
        rate,
        dateLabel: shortDate,
        orderId: ord.order_id,
        supplierName: ord.supplier_name || "Supplier",
        master: matched,
        fallbackUnit: line.unit || "pcs",
        skuName,
      });
      entriesByKey.set(key, arr);
    }
  }

  let maxBills = 0;
  const rows: any[] = [];
  for (const list of entriesByKey.values()) {
    let lastValid: number | null = null;
    const bills = list.map((e: any, idx: number) => {
      const anom = lastValid !== null && (e.rate > lastValid * 3.0 || e.rate < lastValid * 0.3);
      if (!anom) lastValid = e.rate;
      const numStr = Math.abs(e.rate - Math.round(e.rate)) < 0.005 ? String(Math.round(e.rate)) : e.rate.toFixed(2);
      return {
        billIndex: idx + 1,
        price: e.rate,
        dateLabel: e.dateLabel,
        cellText: anom ? `CHECK UNIT (${numStr}, ${e.dateLabel})` : `${numStr} (${e.dateLabel})`,
        isUnitAnomaly: anom,
        orderId: e.orderId,
        supplierName: e.supplierName,
      };
    });
    if (bills.length > maxBills) maxBills = bills.length;
    const valid = bills.filter((b: any) => !b.isUnitAnomaly);
    const first = valid.length ? valid[0].price : bills[0].price;
    const latest = valid.length ? valid[valid.length - 1].price : bills[bills.length - 1].price;
    const hasPct = valid.length >= 2 && first > 0;
    const pct = hasPct ? Math.round(((latest - first) / first) * 10000) / 100 : 0;

    const sample = list[0];
    const m = sample.master;
    const uWeight = m
      ? (Number(m.standard_quantity || 1) === 1 ? `per ${m.unit}` : `per ${m.standard_quantity}${m.unit} pack`)
      : `per ${sample.fallbackUnit}`;

    rows.push({
      skuName: sample.skuName,
      unitWeightLabel: uWeight,
      latestPrice: latest,
      priceChangeVsFirstPct: pct,
      hasChangePct: hasPct,
      isPriceHike: pct > 0,
      hasUnitAnomaly: bills.some((b: any) => b.isUnitAnomaly),
      bills,
    });
  }

  rows.sort((a, b) => {
    const tA = a.isPriceHike ? 0 : a.hasUnitAnomaly ? 1 : 2;
    const tB = b.isPriceHike ? 0 : b.hasUnitAnomaly ? 1 : 2;
    if (tA !== tB) return tA - tB;
    return (b.priceChangeVsFirstPct || 0) - (a.priceChangeVsFirstPct || 0);
  });

  const sep = webhookUrl.includes("?") ? "&" : "?";
  const resp = await fetch(`${webhookUrl}${sep}action=syncSkuPriceHistory`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      action: "syncSkuPriceHistory",
      includeDateInCells: true,
      maxBillColumns: maxBills,
      rows,
      ledger,
    }),
    redirect: "follow",
  });
  return { synced: resp.ok, count: rows.length };
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
