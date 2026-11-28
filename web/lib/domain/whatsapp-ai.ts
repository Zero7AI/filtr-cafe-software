import {
  buildBillLedger,
  buildSkuPriceHistoryMatrix,
  formatBillCell,
  matchCatalogItem,
  resolvePackUnitPrice,
} from './inventory';
import { addPurchasedStockForLinesServer } from './stock-sync';
import { getIstCompactDate, IST_OFFSET_MS } from './ist';
import {
  InventoryItem,
  InventoryOrder,
  InventoryOrderItem,
  InventoryOrderWithItems,
} from './types';
import {
  getSupabaseAdmin,
  getSupabaseServerUrl,
  getSyncMetadata,
} from '../supabase-server';

const FALLBACK_GEMINI_MODELS = [
  'gemini-flash-lite-latest',
  'gemini-3-flash-preview',
  'gemini-2.5-flash-lite',
  'gemini-3.1-flash-lite-preview',
  'gemini-flash-latest',
];

const GRAPH_API_VERSION = 'v21.0';
const SOMESH_PHONE = '919907839311';

export function normalizePhoneNumber(raw: string): string {
  return String(raw || '').replace(/[^\d]/g, '').replace(/^0+/, '');
}

export function isAuthorizedWhatsappSender(raw: string): boolean {
  const n = normalizePhoneNumber(raw);
  return n === SOMESH_PHONE || n === '9907839311';
}

export async function fetchAllInventoryStateServer(): Promise<{
  items: InventoryItem[];
  orders: InventoryOrderWithItems[];
}> {
  const supabase = getSupabaseAdmin();
  const [itemsRes, ordersRes] = await Promise.all([
    supabase.from('inventory_items').select('*').order('name'),
    supabase
      .from('inventory_orders')
      .select('*, inventory_order_items(*)')
      .order('timestamp', { ascending: true })
      .limit(1000),
  ]);

  const items = (itemsRes.data || []) as InventoryItem[];
  const orders: InventoryOrderWithItems[] = (ordersRes.data || []).map(
    (row: Record<string, unknown>) => ({
      order: {
        order_id: String(row.order_id || ''),
        timestamp: Number(row.timestamp || 0),
        supplier_name: String(row.supplier_name || ''),
        total_amount: Number(row.total_amount || 0),
        notes: String(row.notes || ''),
        sync_status: String(row.sync_status || 'SYNCED'),
      } as InventoryOrder,
      items: Array.isArray(row.inventory_order_items)
        ? (row.inventory_order_items as InventoryOrderItem[])
        : [],
    })
  );

  return { items, orders };
}

export async function pushSkuPriceHistoryToWebhook(
  includeDateInCells: boolean = true
): Promise<{ synced: boolean; count?: number; reason?: string }> {
  const webhookUrl = ((await getSyncMetadata('google_sheets_webhook_url')) || '').trim();
  if (!webhookUrl) {
    return { synced: false, reason: 'Google Sheets webhook URL is not configured yet.' };
  }

  const { items, orders } = await fetchAllInventoryStateServer();
  const matrix = buildSkuPriceHistoryMatrix(items, orders);
  const ledger = buildBillLedger(items, orders);

  const payload = {
    action: 'syncSkuPriceHistory',
    includeDateInCells,
    maxBillColumns: matrix.maxBillColumns,
    rows: matrix.rows.map((r) => ({
      skuName: r.skuName,
      unitWeightLabel: r.unitWeightLabel,
      latestPrice: r.latestPrice,
      priceChangeVsFirstPct: r.priceChangeVsFirstPct ?? 0,
      hasChangePct: r.priceChangeVsFirstPct !== null,
      isPriceHike: r.isPriceHike,
      hasUnitAnomaly: r.hasUnitAnomaly,
      bills: r.bills.map((b) => ({
        billIndex: b.billIndex,
        price: b.price,
        dateLabel: b.dateLabel,
        cellText: formatBillCell(b, includeDateInCells),
        isUnitAnomaly: b.isUnitAnomaly,
        orderId: b.orderId,
        supplierName: b.supplierName,
      })),
    })),
    ledger: ledger.map((l) => ({
      orderId: l.orderId,
      dateFormatted: l.dateFormatted,
      supplierName: l.supplierName,
      rawItemName: l.rawItemName,
      matchedSkuName: l.matchedSkuName,
      quantity: l.quantity,
      unit: l.unit,
      unitPrice: l.unitPrice,
      subtotal: l.subtotal,
      normalizedPrice: l.normalizedPrice,
      statusLabel: l.statusLabel,
      notes: l.notes,
    })),
  };

  const sep = webhookUrl.includes('?') ? '&' : '?';
  const res = await fetch(`${webhookUrl}${sep}action=syncSkuPriceHistory`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    redirect: 'follow',
  });

  if (!res.ok) {
    const errText = await res.text();
    return { synced: false, reason: `HTTP ${res.status}: ${errText.slice(0, 120)}` };
  }
  return { synced: true, count: matrix.rows.length };
}

/**
 * Resolves valid Gemini API keys from env var first, then Supabase `sync_metadata.gemini_config`.
 */
export async function resolveServerGeminiKeys(): Promise<string[]> {
  const keys: string[] = [];
  const envKey = (process.env.GEMINI_API_KEY || '').trim();
  if ((envKey.startsWith('AIza') || envKey.startsWith('AQ.')) && envKey.length >= 30) {
    keys.push(envKey);
  }

  try {
    const supabase = getSupabaseAdmin();
    const { data } = await supabase
      .from('sync_metadata')
      .select('value')
      .eq('key', 'gemini_config')
      .maybeSingle();

    if (data?.value) {
      const rawText = typeof data.value === 'string' ? data.value : JSON.stringify(data.value);
      const matches = rawText.match(/(?:AIza|AQ\.)[A-Za-z0-9_\-.]{30,}/g) || [];
      for (const m of matches) {
        if (!keys.includes(m)) keys.push(m);
      }
    }
  } catch {
    // ignore metadata read errors
  }

  return keys;
}

export interface ParsedReceiptPayload {
  category: 'inventory' | 'store_expense' | 'utility_expense' | 'unitemized_vendor' | 'greeting_or_query';
  is_inventory: boolean;
  supplier_name: string;
  bill_date?: string | null; // YYYY-MM-DD
  items: Array<{
    item_name: string;
    catalog_item_id?: number | null;
    quantity: number;
    unit: string;
    unit_price: number;
    subtotal: number;
  }>;
  total_amount: number;
  notes: string;
}

function buildReceiptParsePrompt(
  userMessage: string,
  inventoryCatalog: string,
  inputType: 'text' | 'image' | 'pdf_document'
): string {
  return `You are an intelligent AI procurement & accounting assistant for FILTR Coffee, a café in India. Parse incoming procurement bills, receipt photos, or PDF invoices and extract structured JSON.

## CURRENT INVENTORY MASTER CATALOG:
${inventoryCatalog || 'No inventory items configured yet.'}

## INPUT (${inputType}):
"${userMessage}"

## CLASSIFICATION:
1. "inventory": Raw cafe consumable ingredients, syrups, dairy/milk, coffee beans, sauces, ice cream bases, frozen items, and packaging (cups, lids, straws, tissues, tray plates).
2. "store_expense": Non-consumable hardware, dispensers, stands, utensils, equipment, baskets, containers, weighing machines, appliances, paint, repairs.
3. "utility_expense": Rent, electricity, Wi-Fi, transport/delivery (Rapido/courier).
4. "unitemized_vendor": A supplier and total amount without line items (e.g. "Kalpana traders - 780rs").
5. "greeting_or_query": Casual greetings, questions, non-bill text.

## IMPORTANT EXTRACTION RULES:
- Extract "bill_date" strictly in "YYYY-MM-DD" format whenever a date is printed or handwritten on the invoice/receipt (convert Indian formats like 29/10/26 or 13-11-2026 -> "2026-10-29", "2026-11-13"). Set null if no date is present.
- Indian Shorthand Text Bills ("<AMOUNT> - <items>"): When a text bill starts with a number followed by "-" (e.g. "1520 - condense milk", "1041 - 9 peach iced, 1 lemon iced"), that leading number is ALWAYS total_amount in INR — NEVER treat it as a quantity or invoice number; estimate/split item subtotals so they sum to that exact total_amount.
- For "items": extract item_name, quantity (positive number), unit (kg, g, L, ml, pcs, pack), unit_price (INR per billed unit/pack), subtotal.
- Do NOT confuse pack size inside an item's name (like "250g" or "500ml" or "24 pcs") with quantity or rate: if 1 pack costs ₹140, quantity=1, unit="pack", unit_price=140, subtotal=140.
- Container Count Units: When an invoice bills a multi-kg or multi-L gallon, tub, carton, or box by count (e.g. "6 Nos" of "GALLON VANILLA 5LT @ 405"), output unit: "pack" (NOT "kg" or "L").
- Always return strictly valid JSON matching this schema:
{
  "category": "inventory",
  "is_inventory": true,
  "supplier_name": "Supplier name or empty string",
  "bill_date": "YYYY-MM-DD or null",
  "items": [
    {
      "item_name": "Item name",
      "catalog_item_id": null,
      "quantity": 1,
      "unit": "pack",
      "unit_price": 140,
      "subtotal": 140
    }
  ],
  "total_amount": 140,
  "notes": "Invoice number or concise note"
}`;
}

export async function callGeminiReceiptParser(
  prompt: string,
  mediaBase64: string | null,
  mimeType: string = 'image/jpeg'
): Promise<ParsedReceiptPayload> {
  const keys = await resolveServerGeminiKeys();
  if (keys.length === 0) {
    throw new Error('No valid Gemini API key configured');
  }

  let lastError = 'Gemini request failed';

  for (const apiKey of keys) {
    for (const model of FALLBACK_GEMINI_MODELS) {
      for (let attempt = 1; attempt <= 2; attempt++) {
        try {
          const parts: Array<Record<string, unknown>> = [];
          if (mediaBase64) {
            parts.push({ inlineData: { mimeType, data: mediaBase64 } });
          }
          parts.push({ text: prompt });

          const resp = await fetch(
            `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent?key=${apiKey}`,
            {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                contents: [{ parts }],
                generationConfig: {
                  temperature: 0.1,
                  responseMimeType: 'application/json',
                },
              }),
            }
          );

          if (!resp.ok) {
            const errText = await resp.text();
            lastError = `Gemini API error (${resp.status}): ${errText.slice(0, 220)}`;
            if (resp.status === 401 || resp.status === 403) {
              break; // try next key
            }
            if (resp.status === 429 || resp.status >= 500) {
              await new Promise((r) => setTimeout(r, attempt * 1200));
              continue;
            }
            break;
          }

          const data = await resp.json();
          const rawText = data?.candidates?.[0]?.content?.parts?.[0]?.text;
          if (!rawText) {
            lastError = 'Empty Gemini response';
            continue;
          }

          const clean = rawText
            .trim()
            .replace(/^```json\s*/i, '')
            .replace(/^```/, '')
            .replace(/```$/, '')
            .trim();
          const jsonMatch = clean.match(/\{[\s\S]*\}/);
          return JSON.parse(jsonMatch ? jsonMatch[0] : clean) as ParsedReceiptPayload;
        } catch (err) {
          lastError = err instanceof Error ? err.message : String(err);
        }
      }
      if (lastError.includes('(401)') || lastError.includes('(403)')) break;
    }
  }

  throw new Error(lastError);
}

/**
 * Resolves the canonical bill timestamp (IST-aligned) from extracted bill_date (YYYY-MM-DD)
 * or falls back to the message/upload timestamp (never today's date when backfilling older bills).
 */
export function resolveBillTimestamp(
  extractedDateStr: string | null | undefined,
  fallbackMs: number
): number {
  const s = String(extractedDateStr || '').trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) {
    const [y, m, d] = s.split('-').map(Number);
    if (y >= 2025 && y <= 2027 && m >= 1 && m <= 12 && d >= 1 && d <= 31) {
      // Check it isn't far in the future (> +2 days)
      const noonIstUtcMs = Date.UTC(y, m - 1, d, 12, 0, 0, 0) - IST_OFFSET_MS;
      if (noonIstUtcMs <= Date.now() + 48 * 3600 * 1000) {
        return noonIstUtcMs;
      }
    }
  }
  return fallbackMs > 0 ? fallbackMs : Date.now();
}

export async function createDatedInventoryOrderServer(
  parsed: ParsedReceiptPayload,
  catalog: InventoryItem[],
  fallbackTimestampMs: number
): Promise<{ orderId: string; isDuplicate: boolean; totalAmount: number; dateKey: string }> {
  const supabase = getSupabaseAdmin();
  const billTs = resolveBillTimestamp(parsed.bill_date, fallbackTimestampMs);
  const dateCompact = getIstCompactDate(billTs); // YYYYMMDD
  const items = Array.isArray(parsed.items) ? parsed.items : [];
  const totalAmount =
    Number(parsed.total_amount || 0) > 0
      ? Number(parsed.total_amount)
      : items.reduce((s, i) => s + Number(i.subtotal || Number(i.quantity || 0) * Number(i.unit_price || 0)), 0);

  // Duplicate guard around +/- 36 hours of billTs with exact totalAmount
  const windowStart = billTs - 36 * 3600 * 1000;
  const windowEnd = billTs + 36 * 3600 * 1000;
  const { data: nearby } = await supabase
    .from('inventory_orders')
    .select('order_id, supplier_name, total_amount, timestamp, notes')
    .gte('timestamp', windowStart)
    .lte('timestamp', windowEnd)
    .eq('total_amount', totalAmount);

  if (nearby && nearby.length > 0) {
    const suppA = (parsed.supplier_name || '').trim().toLowerCase();
    for (const ro of nearby) {
      const suppB = String(ro.supplier_name || '').trim().toLowerCase();
      const sameSupplier = suppA && suppB && suppA === suppB;
      const sameDay = getIstCompactDate(Number(ro.timestamp || 0)) === dateCompact;
      const isIce = items.some((it) => it.item_name.toLowerCase().includes('ice')) && totalAmount <= 270;
      if (sameDay || (sameSupplier && !isIce)) {
        return {
          orderId: String(ro.order_id),
          isDuplicate: true,
          totalAmount,
          dateKey: dateCompact,
        };
      }
    }
  }

  const prefix = `INV-${dateCompact}-`;
  const { data: existingIds } = await supabase
    .from('inventory_orders')
    .select('order_id')
    .like('order_id', `${prefix}%`)
    .order('order_id', { ascending: false })
    .limit(10);

  let maxSeq = 0;
  for (const row of existingIds || []) {
    const seq = parseInt(String(row.order_id || '').replace(prefix, ''), 10);
    if (!Number.isNaN(seq) && seq > maxSeq) maxSeq = seq;
  }
  const orderId = `${prefix}${String(maxSeq + 1).padStart(3, '0')}`;

  const noteParts = [
    parsed.bill_date ? `Bill Date: ${parsed.bill_date}` : '',
    parsed.notes ? parsed.notes.trim() : '',
  ].filter(Boolean);

  await supabase.from('inventory_orders').insert({
    order_id: orderId,
    timestamp: billTs,
    supplier_name: (parsed.supplier_name || '').trim() || 'WhatsApp Order',
    total_amount: totalAmount,
    notes: noteParts.join(' | '),
    sync_status: 'SYNCED',
  });

  const linePayloads = items
    .filter((l) => String(l.item_name || '').trim() && Number(l.quantity || 0) > 0)
    .map((l) => {
      const qty = Number(l.quantity || 1);
      const unitPrice = Number(l.unit_price || 0);
      const sub = Number(l.subtotal || qty * unitPrice);
      return {
        order_id: orderId,
        item_name: String(l.item_name).trim(),
        quantity: qty,
        unit: String(l.unit || 'pcs').trim() || 'pcs',
        unit_price: unitPrice > 0 ? unitPrice : qty > 0 ? Math.round((sub / qty) * 100) / 100 : 0,
        subtotal: sub,
      };
    });

  if (linePayloads.length > 0) {
    await supabase.from('inventory_order_items').insert(linePayloads);
    await addPurchasedStockForLinesServer(linePayloads, catalog).catch(() => []);
  }

  return { orderId, isDuplicate: false, totalAmount, dateKey: dateCompact };
}

export async function fetchWhatsappMediaBase64(
  mediaId: string
): Promise<{ base64: string; mimeType: string } | null> {
  if (!mediaId) return null;

  // Prefer direct Meta Graph API when WA_ACCESS_TOKEN is configured
  const waToken = (process.env.WA_ACCESS_TOKEN || '').trim();
  if (waToken) {
    try {
      const metaResp = await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/${mediaId}`, {
        headers: { Authorization: `Bearer ${waToken}` },
      });
      const meta = await metaResp.json();
      if (metaResp.ok && meta?.url) {
        const binResp = await fetch(meta.url, {
          headers: { Authorization: `Bearer ${waToken}` },
        });
        if (binResp.ok) {
          const buf = Buffer.from(await binResp.arrayBuffer());
          return {
            base64: buf.toString('base64'),
            mimeType: meta.mime_type || 'image/jpeg',
          };
        }
      }
    } catch {
      // fall through to Edge Function proxy
    }
  }

  // Fallback: Edge Function proxy (which already holds WA_ACCESS_TOKEN)
  try {
    const supaUrl = getSupabaseServerUrl();
    const res = await fetch(
      `${supaUrl}/functions/v1/whatsapp-webhook?action=get_media&media_id=${encodeURIComponent(mediaId)}`,
      { cache: 'no-store' }
    );
    if (!res.ok) return null;
    const data = await res.json();
    if (data?.base64) {
      return {
        base64: String(data.base64),
        mimeType: String(data.mimeType || 'image/jpeg'),
      };
    }
  } catch {
    // ignore
  }
  return null;
}

export async function sendWhatsappTextReply(to: string, phoneNumberId: string, text: string): Promise<void> {
  const waToken = (process.env.WA_ACCESS_TOKEN || '').trim();
  let phoneId = (phoneNumberId || process.env.WA_PHONE_NUMBER_ID || '').trim();
  if (!phoneId) {
    phoneId = ((await getSyncMetadata('wa_phone_number_id')) || '').trim();
  }
  if (!waToken || !phoneId) return;

  await fetch(`https://graph.facebook.com/${GRAPH_API_VERSION}/${phoneId}/messages`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${waToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      messaging_product: 'whatsapp',
      to,
      type: 'text',
      text: { body: text },
    }),
  }).catch(() => {});
}

export interface ReprocessOutcome {
  id: number;
  status: string;
  orderId?: string;
  dateKey?: string;
  error?: string;
}

async function processSingleWhatsappRecord(
  row: {
    id: number;
    message_id: string;
    message_type: string;
    message_body: string;
    created_at: string;
    parsed_result: Record<string, unknown> | null;
  },
  catalog: InventoryItem[],
  catalogText: string
): Promise<ReprocessOutcome> {
  const supabase = getSupabaseAdmin();
  const createdMs = Date.parse(row.created_at) || Date.now();
  const metaMediaId = row.parsed_result?.media_id ? String(row.parsed_result.media_id) : '';
  const metaMime = row.parsed_result?.mime_type ? String(row.parsed_result.mime_type) : '';

  let parsed: ParsedReceiptPayload;

  if (row.message_type === 'text') {
    const prompt = buildReceiptParsePrompt(row.message_body || '', catalogText, 'text');
    parsed = await callGeminiReceiptParser(prompt, null, 'text/plain');
  } else if (row.message_type === 'image' || row.message_type === 'document') {
    if (!metaMediaId) {
      await supabase
        .from('whatsapp_messages')
        .update({
          status: 'media_expired',
          error_message: 'No media_id recorded when received; ask supplier/admin to resend.',
        })
        .eq('id', row.id);
      return { id: row.id, status: 'media_expired', error: 'Missing media_id' };
    }
    const media = await fetchWhatsappMediaBase64(metaMediaId);
    if (!media) {
      await supabase
        .from('whatsapp_messages')
        .update({
          status: 'media_expired',
          error_message: 'WhatsApp media URL expired (>30 days) on Meta servers.',
          parsed_result: { ...(row.parsed_result || {}), media_id: metaMediaId, mime_type: metaMime },
        })
        .eq('id', row.id);
      return { id: row.id, status: 'media_expired', error: 'Media expired on Meta' };
    }
    const isPdf = media.mimeType.includes('pdf');
    const prompt = buildReceiptParsePrompt(
      row.message_body || `Extract all bill items and invoice date from this ${isPdf ? 'PDF' : 'receipt photo'}.`,
      catalogText,
      isPdf ? 'pdf_document' : 'image'
    );
    parsed = await callGeminiReceiptParser(prompt, media.base64, media.mimeType);
  } else {
    await supabase.from('whatsapp_messages').update({ status: 'unsupported' }).eq('id', row.id);
    return { id: row.id, status: 'unsupported' };
  }

  const enrichedParsed = {
    ...parsed,
    ...(metaMediaId ? { media_id: metaMediaId, mime_type: metaMime } : {}),
  };

  // Non-inventory operational expense
  if (
    parsed.category === 'store_expense' ||
    parsed.category === 'utility_expense' ||
    (parsed.is_inventory === false && Number(parsed.total_amount || 0) > 0)
  ) {
    await supabase
      .from('whatsapp_messages')
      .update({
        status: 'logged_as_expense',
        parsed_result: enrichedParsed,
        error_message: null,
      })
      .eq('id', row.id);
    return { id: row.id, status: 'logged_as_expense' };
  }

  // Unitemized supplier bill
  if (
    parsed.category === 'unitemized_vendor' ||
    ((!parsed.items || parsed.items.length === 0) &&
      Number(parsed.total_amount || 0) > 0 &&
      Boolean((parsed.supplier_name || '').trim()))
  ) {
    const supp = parsed.supplier_name.trim() || 'Supplier';
    const amt = Number(parsed.total_amount || 0);
    const created = await createDatedInventoryOrderServer(
      {
        ...parsed,
        items: [
          {
            item_name: `Procurement - ${supp}`,
            quantity: 1,
            unit: 'bill',
            unit_price: amt,
            subtotal: amt,
          },
        ],
      },
      catalog,
      createdMs
    );
    await supabase
      .from('whatsapp_messages')
      .update({
        status: 'saved',
        parsed_result: enrichedParsed,
        inventory_order_id: created.orderId,
        error_message: created.isDuplicate ? `Duplicate of ${created.orderId}` : null,
      })
      .eq('id', row.id);
    return { id: row.id, status: 'saved', orderId: created.orderId, dateKey: created.dateKey };
  }

  if (!parsed.items || parsed.items.length === 0) {
    await supabase
      .from('whatsapp_messages')
      .update({
        status: 'parse_failed',
        parsed_result: enrichedParsed,
        error_message: 'No bill items detected by Gemini.',
      })
      .eq('id', row.id);
    return { id: row.id, status: 'parse_failed' };
  }

  const created = await createDatedInventoryOrderServer(parsed, catalog, createdMs);
  await supabase
    .from('whatsapp_messages')
    .update({
      status: 'saved',
      parsed_result: enrichedParsed,
      inventory_order_id: created.orderId,
      error_message: created.isDuplicate ? `Duplicate of ${created.orderId}` : null,
    })
    .eq('id', row.id);

  return { id: row.id, status: 'saved', orderId: created.orderId, dateKey: created.dateKey };
}

export async function reprocessPendingWhatsappMessages(): Promise<{
  processedCount: number;
  savedCount: number;
  outcomes: ReprocessOutcome[];
  sheetSynced: boolean;
}> {
  const supabase = getSupabaseAdmin();
  const [{ data: catalogData }, { data: pendingRows }] = await Promise.all([
    supabase.from('inventory_items').select('*').eq('is_active', true).order('name'),
    supabase
      .from('whatsapp_messages')
      .select('id, message_id, message_type, message_body, created_at, parsed_result, status')
      .in('status', ['retry_pending', 'config_error'])
      .order('id', { ascending: true })
      .limit(50),
  ]);

  const catalog = (catalogData || []) as InventoryItem[];
  const catalogText = catalog
    .map(
      (i) =>
        `- ${i.name} (${i.category}) | Unit: ${i.unit} | Pack: ${i.standard_quantity}${i.unit} | Default: ₹${i.default_price}`
    )
    .join('\n');

  const outcomes: ReprocessOutcome[] = [];
  for (const row of pendingRows || []) {
    try {
      const res = await processSingleWhatsappRecord(
        row as {
          id: number;
          message_id: string;
          message_type: string;
          message_body: string;
          created_at: string;
          parsed_result: Record<string, unknown> | null;
        },
        catalog,
        catalogText
      );
      outcomes.push(res);
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      await supabase
        .from('whatsapp_messages')
        .update({ error_message: msg.slice(0, 300) })
        .eq('id', row.id);
      outcomes.push({ id: row.id, status: 'error', error: msg });
    }
  }

  const savedCount = outcomes.filter((o) => o.status === 'saved').length;
  let sheetSynced = false;
  if (savedCount > 0) {
    const syncRes = await pushSkuPriceHistoryToWebhook(true).catch(() => ({ synced: false }));
    sheetSynced = Boolean(syncRes.synced);
  }

  return {
    processedCount: outcomes.length,
    savedCount,
    outcomes,
    sheetSynced,
  };
}

export async function parseUploadedReceiptAndSave(params: {
  base64: string;
  mimeType: string;
  filename: string;
}): Promise<{
  parsed: ParsedReceiptPayload;
  orderId?: string;
  dateKey?: string;
  isDuplicate?: boolean;
  sheetSynced: boolean;
}> {
  const supabase = getSupabaseAdmin();
  const { data: catalogData } = await supabase
    .from('inventory_items')
    .select('*')
    .eq('is_active', true)
    .order('name');
  const catalog = (catalogData || []) as InventoryItem[];
  const catalogText = catalog
    .map(
      (i) =>
        `- ${i.name} (${i.category}) | Unit: ${i.unit} | Pack: ${i.standard_quantity}${i.unit} | Default: ₹${i.default_price}`
    )
    .join('\n');

  const isPdf = params.mimeType.includes('pdf');
  const prompt = buildReceiptParsePrompt(
    `Uploaded bill file: ${params.filename || 'receipt'}. Extract supplier, exact bill date (YYYY-MM-DD), items, quantities, units, and prices.`,
    catalogText,
    isPdf ? 'pdf_document' : 'image'
  );

  const parsed = await callGeminiReceiptParser(prompt, params.base64, params.mimeType);
  if (!parsed.items || parsed.items.length === 0) {
    throw new Error('AI could not detect bill line items in the uploaded file.');
  }

  const created = await createDatedInventoryOrderServer(parsed, catalog, Date.now());
  const syncRes = await pushSkuPriceHistoryToWebhook(true).catch(() => ({ synced: false }));

  return {
    parsed,
    orderId: created.orderId,
    dateKey: created.dateKey,
    isDuplicate: created.isDuplicate,
    sheetSynced: Boolean(syncRes.synced),
  };
}
