import { NextRequest, NextResponse } from 'next/server';
import {
  callGeminiReceiptParser,
  createDatedInventoryOrderServer,
  fetchWhatsappMediaBase64,
  isAuthorizedWhatsappSender,
  pushSkuPriceHistoryToWebhook,
  reprocessPendingWhatsappMessages,
  resolveServerGeminiKeys,
  sendWhatsappTextReply,
} from '@/lib/domain/whatsapp-ai';
import { InventoryItem } from '@/lib/domain/types';
import { getSupabaseAdmin, getSyncMetadata } from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

export async function GET(request: NextRequest) {
  const sp = request.nextUrl.searchParams;
  const action = sp.get('action') || '';

  // 1. Meta WhatsApp Webhook Verification Handshake
  const mode = sp.get('hub.mode');
  const token = sp.get('hub.verify_token');
  const challenge = sp.get('hub.challenge');
  if (mode === 'subscribe') {
    const expectedToken = process.env.WA_VERIFY_TOKEN || 'FILTR_WA_VERIFY_2026_SECURE';
    if (token === expectedToken) {
      return new NextResponse(challenge || '', { status: 200 });
    }
    return new NextResponse('Forbidden', { status: 403 });
  }

  // 2. Diagnostic Health Check (never exposes keys)
  if (action === 'health' || action === 'diag') {
    try {
      const supabase = getSupabaseAdmin();
      const [keys, pendingRes, sheetWebhook] = await Promise.all([
        resolveServerGeminiKeys(),
        supabase
          .from('whatsapp_messages')
          .select('id', { count: 'exact', head: true })
          .in('status', ['retry_pending', 'config_error']),
        getSyncMetadata('google_sheets_webhook_url'),
      ]);

      let geminiStatus = 0;
      let geminiModel = process.env.GEMINI_MODEL || 'gemini-flash-lite-latest';
      if (keys.length > 0) {
        const probe = await fetch(
          `https://generativelanguage.googleapis.com/v1beta/models/${geminiModel}:generateContent?key=${keys[0]}`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              contents: [{ parts: [{ text: 'Return JSON: {"ok":true}' }] }],
              generationConfig: { temperature: 0, responseMimeType: 'application/json' },
            }),
          }
        );
        geminiStatus = probe.status;
      }

      return NextResponse.json({
        status: 'ok',
        geminiConfigured: keys.length > 0,
        geminiStatus,
        geminiModel,
        pendingCount: pendingRes.count || 0,
        googleSheetsWebhookConfigured: Boolean((sheetWebhook || '').trim()),
      });
    } catch (err) {
      return NextResponse.json(
        { status: 'error', error: err instanceof Error ? err.message : 'Health failed' },
        { status: 500 }
      );
    }
  }

  // 3. Reprocess Queued Pending WhatsApp Bills (also used by Vercel daily Cron)
  if (action === 'sync_pending' || action === 'retry_pending') {
    try {
      const result = await reprocessPendingWhatsappMessages();
      return NextResponse.json({ status: 'ok', ...result });
    } catch (err) {
      return NextResponse.json(
        { status: 'error', error: err instanceof Error ? err.message : 'Retry failed' },
        { status: 500 }
      );
    }
  }

  return NextResponse.json({ status: 'FILTR WhatsApp AI Webhook Ready' });
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    if (body?.action === 'sync_pending' || body?.action === 'retry_pending') {
      const result = await reprocessPendingWhatsappMessages();
      return NextResponse.json({ status: 'ok', ...result });
    }

    const change = body?.entry?.[0]?.changes?.[0];
    if (!change || change.field !== 'messages') {
      return NextResponse.json({ status: 'ignored' });
    }

    const value = change.value;
    const message = value?.messages?.[0];
    if (!message) {
      return NextResponse.json({ status: 'no_message' });
    }

    const fromNumber = String(message.from || '');
    const messageId = String(message.id || '');
    const messageType = String(message.type || '');
    const phoneId = String(value?.metadata?.phone_number_id || '');

    if (!isAuthorizedWhatsappSender(fromNumber)) {
      return NextResponse.json({ status: 'unauthorized_sender' });
    }

    const supabase = getSupabaseAdmin();
    const { data: existing } = await supabase
      .from('whatsapp_messages')
      .select('id, status')
      .eq('message_id', messageId)
      .maybeSingle();

    if (existing?.status === 'saved') {
      return NextResponse.json({ status: 'duplicate_skipped' });
    }

    const mediaId =
      messageType === 'image'
        ? message.image?.id
        : messageType === 'document'
          ? message.document?.id
          : undefined;
    const mimeType =
      messageType === 'image'
        ? message.image?.mime_type || 'image/jpeg'
        : messageType === 'document'
          ? message.document?.mime_type || 'application/pdf'
          : 'text/plain';
    const messageBody =
      messageType === 'text'
        ? message.text?.body || ''
        : messageType === 'image'
          ? message.image?.caption || '[Image received]'
          : `[Document: ${message.document?.filename || 'invoice'}]`;

    if (!existing) {
      await supabase.from('whatsapp_messages').insert({
        message_id: messageId,
        from_number: fromNumber,
        message_type: messageType,
        message_body: messageBody,
        status: 'received',
        parsed_result: mediaId ? { media_id: mediaId, mime_type: mimeType } : null,
      });
    }

    try {
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

      let b64: string | null = null;
      let actualMime = mimeType;
      if (messageType === 'image' || messageType === 'document') {
        const media = await fetchWhatsappMediaBase64(mediaId || '');
        if (!media) throw new Error('Could not download WhatsApp media');
        b64 = media.base64;
        actualMime = media.mimeType || mimeType;
      }

      const parsed = await callGeminiReceiptParser(
        `Extract cafe bill supplier, bill_date (YYYY-MM-DD), items, quantities, units, unit_price, subtotal.\nCATALOG:\n${catalogText}\nINPUT:\n${messageBody}`,
        b64,
        actualMime
      );

      if (parsed.items && parsed.items.length > 0) {
        const created = await createDatedInventoryOrderServer(parsed, catalog, Date.now());
        await supabase
          .from('whatsapp_messages')
          .update({
            status: 'saved',
            parsed_result: { ...parsed, ...(mediaId ? { media_id: mediaId, mime_type: actualMime } : {}) },
            inventory_order_id: created.orderId,
            error_message: null,
          })
          .eq('message_id', messageId);

        await pushSkuPriceHistoryToWebhook(true).catch(() => {});
        await sendWhatsappTextReply(
          fromNumber,
          phoneId,
          `✅ *Bill ${created.orderId} logged & synced to Excel Sheet!*\n📦 Supplier: ${parsed.supplier_name || 'WhatsApp Order'}\n💰 Total: ₹${created.totalAmount.toLocaleString('en-IN')}`
        );
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : String(err);
      await supabase
        .from('whatsapp_messages')
        .update({ status: 'retry_pending', error_message: msg.slice(0, 300) })
        .eq('message_id', messageId);
    }

    return NextResponse.json({ status: 'ok' });
  } catch (err) {
    return NextResponse.json(
      { status: 'error', error: err instanceof Error ? err.message : 'Webhook failed' },
      { status: 500 }
    );
  }
}
