import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import {
  buildBillLedger,
  buildSkuPriceHistoryMatrix,
} from '@/lib/domain/inventory';
import { addPurchasedStockForLinesServer } from '@/lib/domain/stock-sync';
import { getIstCompactDate } from '@/lib/domain/ist';
import {
  InventoryItem,
  InventoryOrderItem,
} from '@/lib/domain/types';
import {
  fetchAllInventoryStateServer,
  parseUploadedReceiptAndSave,
  pushSkuPriceHistoryToWebhook,
  reprocessPendingWhatsappMessages,
} from '@/lib/domain/whatsapp-ai';
import {
  getSupabaseAdmin,
  getSyncMetadata,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

const DEFAULT_SHEET_URL =
  'https://docs.google.com/spreadsheets/d/1UsUM0SLdDf4uHXhJuao96Twkd-Eq5Yxl4_VHM_sFw90/edit?gid=0#gid=0';

export async function GET() {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const supabase = getSupabaseAdmin();
    const [{ items, orders }, waRes, sheetUrlRaw, webhookUrlRaw] = await Promise.all([
      fetchAllInventoryStateServer(),
      supabase
        .from('whatsapp_messages')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(80),
      getSyncMetadata('google_sheets_url'),
      getSyncMetadata('google_sheets_webhook_url'),
    ]);

    const skuMatrix = buildSkuPriceHistoryMatrix(items, orders);
    const billLedger = buildBillLedger(items, orders).reverse(); // newest first for UI ledger
    const whatsappMessages = waRes.data || [];
    const pendingWhatsappCount = whatsappMessages.filter(
      (m: { status?: string }) => m.status === 'retry_pending' || m.status === 'config_error'
    ).length;

    const totalBillSpend = Math.round(
      orders.reduce((s, o) => s + Number(o.order.total_amount || 0), 0) * 100
    ) / 100;
    const uniqueSuppliersCount = new Set(
      orders.map((o) => (o.order.supplier_name || '').trim().toLowerCase()).filter(Boolean)
    ).size;

    return NextResponse.json({
      items,
      skuMatrix,
      billLedger,
      totalBillSpend,
      billsCount: orders.length,
      uniqueSuppliersCount,
      googleSheetsUrl: (sheetUrlRaw || '').trim() || DEFAULT_SHEET_URL,
      googleSheetsWebhookConfigured: Boolean((webhookUrlRaw || '').trim()),
      whatsappMessages,
      pendingWhatsappCount,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load purchase tracking' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const body = await request.json();
    const action = String(body?.action || '');
    const supabase = getSupabaseAdmin();

    if (action === 'syncGoogleSheet') {
      const includeDates = body?.includeDateInCells !== false;
      const result = await pushSkuPriceHistoryToWebhook(includeDates);
      if (!result.synced) {
        return NextResponse.json({ error: result.reason }, { status: 400 });
      }
      return NextResponse.json({ success: true, count: result.count });
    }

    if (action === 'retryWhatsappPending') {
      const result = await reprocessPendingWhatsappMessages();
      return NextResponse.json({ success: true, ...result });
    }

    if (action === 'scanUploadReceipt') {
      const base64 = String(body?.base64 || '').trim();
      const mimeType = String(body?.mimeType || 'image/jpeg').trim();
      const filename = String(body?.filename || 'receipt').trim();
      if (!base64) {
        return NextResponse.json({ error: 'Uploaded file content is empty' }, { status: 400 });
      }
      const result = await parseUploadedReceiptAndSave({ base64, mimeType, filename });
      return NextResponse.json({ success: true, ...result });
    }

    if (action === 'createPurchaseBill') {
      const supplierName = String(body?.supplier_name || 'Supplier').trim() || 'Supplier';
      const notes = String(body?.notes || '').trim();
      const rawLines = Array.isArray(body?.items) ? body.items : [];
      if (rawLines.length === 0) {
        return NextResponse.json({ error: 'At least one item is required' }, { status: 400 });
      }

      const datePrefix = getIstCompactDate();
      const likePrefix = `INV-${datePrefix}-`;
      const { data: existingOrders } = await supabase
        .from('inventory_orders')
        .select('order_id')
        .like('order_id', `${likePrefix}%`);

      let maxSeq = 0;
      for (const row of existingOrders || []) {
        const seq = parseInt(String(row.order_id || '').split('-').pop() || '0', 10);
        if (!Number.isNaN(seq) && seq > maxSeq) maxSeq = seq;
      }
      const orderId = `${likePrefix}${String(maxSeq + 1).padStart(3, '0')}`;
      const timestamp = Number(body?.timestamp || Date.now());

      const linePayloads: InventoryOrderItem[] = rawLines.map(
        (l: Record<string, unknown>) => {
          const qty = Math.max(0.01, Number(l.quantity || 1));
          const unitPrice = Math.max(0, Number(l.unit_price || 0));
          const subtotal = Number(l.subtotal ?? qty * unitPrice);
          return {
            order_id: orderId,
            item_name: String(l.item_name || '').trim(),
            quantity: qty,
            unit: String(l.unit || 'kg').trim() || 'kg',
            unit_price: unitPrice,
            subtotal,
          };
        }
      );

      const totalAmount = linePayloads.reduce((s, l) => s + l.subtotal, 0);

      const { error: ordErr } = await supabase.from('inventory_orders').insert({
        order_id: orderId,
        timestamp,
        supplier_name: supplierName,
        total_amount: totalAmount,
        notes,
        sync_status: 'SYNCED',
      });
      if (ordErr) return NextResponse.json({ error: ordErr.message }, { status: 500 });

      const { error: linesErr } = await supabase
        .from('inventory_order_items')
        .insert(linePayloads);
      if (linesErr) return NextResponse.json({ error: linesErr.message }, { status: 500 });

      const { data: catalogData } = await supabase.from('inventory_items').select('*');
      const catalog = (catalogData || []) as InventoryItem[];
      await addPurchasedStockForLinesServer(linePayloads, catalog).catch(() => []);

      pushSkuPriceHistoryToWebhook(true).catch(() => {});
      return NextResponse.json({ success: true, orderId });
    }

    if (action === 'deletePurchaseBill') {
      const orderId = String(body?.orderId || '').trim();
      if (!orderId) return NextResponse.json({ error: 'orderId required' }, { status: 400 });
      await supabase.from('inventory_order_items').delete().eq('order_id', orderId);
      const { error } = await supabase
        .from('inventory_orders')
        .delete()
        .eq('order_id', orderId);
      if (error) return NextResponse.json({ error: error.message }, { status: 500 });

      pushSkuPriceHistoryToWebhook(true).catch(() => {});
      return NextResponse.json({ success: true });
    }

    if (action === 'upsertInventoryItem') {
      const id = Number(body?.id || 0);
      const name = String(body?.name || '').trim();
      const category = String(body?.category || 'General').trim() || 'General';
      const unit = String(body?.unit || 'kg').trim() || 'kg';
      const standardQuantity = Math.max(0.01, Number(body?.standard_quantity || 1));
      const defaultPrice = Math.max(0, Number(body?.default_price || 0));
      const currentStock = Math.max(0, Number(body?.current_stock || 0));
      const isActive = body?.is_active !== false;

      if (!name) {
        return NextResponse.json({ error: 'Ingredient name is required' }, { status: 400 });
      }

      const payload = {
        name,
        category,
        unit,
        standard_quantity: standardQuantity,
        default_price: defaultPrice,
        current_stock: currentStock,
        is_active: isActive,
      };

      if (id > 0) {
        const { data, error } = await supabase
          .from('inventory_items')
          .update(payload)
          .eq('id', id)
          .select('*')
          .single();
        if (error) return NextResponse.json({ error: error.message }, { status: 500 });
        return NextResponse.json({ success: true, item: data });
      } else {
        const { data, error } = await supabase
          .from('inventory_items')
          .insert(payload)
          .select('*')
          .single();
        if (error) return NextResponse.json({ error: error.message }, { status: 500 });
        return NextResponse.json({ success: true, item: data });
      }
    }

    if (action === 'toggleInventoryItem') {
      const id = Number(body?.id || 0);
      const isActive = Boolean(body?.is_active);
      if (!id) return NextResponse.json({ error: 'id required' }, { status: 400 });
      const { error } = await supabase
        .from('inventory_items')
        .update({ is_active: isActive })
        .eq('id', id);
      if (error) return NextResponse.json({ error: error.message }, { status: 500 });
      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Inventory mutation failed' },
      { status: 500 }
    );
  }
}
