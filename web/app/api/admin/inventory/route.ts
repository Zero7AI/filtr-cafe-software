import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import {
  buildBillLedger,
  buildSkuPriceHistoryMatrix,
  formatBillCell,
  matchCatalogItem,
  resolvePackUnitPrice,
} from '@/lib/domain/inventory';
import { getIstCompactDate } from '@/lib/domain/ist';
import {
  InventoryItem,
  InventoryOrder,
  InventoryOrderItem,
  InventoryOrderWithItems,
} from '@/lib/domain/types';
import {
  getSupabaseAdmin,
  getSupabaseServerUrl,
  getSyncMetadata,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const DEFAULT_SHEET_URL =
  'https://docs.google.com/spreadsheets/d/1UsUM0SLdDf4uHXhJuao96Twkd-Eq5Yxl4_VHM_sFw90/edit?gid=0#gid=0';

async function fetchAllInventoryState() {
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

async function pushSkuPriceHistoryToWebhook(includeDateInCells: boolean = true) {
  const webhookUrl = ((await getSyncMetadata('google_sheets_webhook_url')) || '').trim();
  if (!webhookUrl) {
    return { synced: false, reason: 'Google Sheets webhook URL is not configured yet.' };
  }

  const { items, orders } = await fetchAllInventoryState();
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

export async function GET() {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const supabase = getSupabaseAdmin();
    const [{ items, orders }, waRes, sheetUrlRaw, webhookUrlRaw] = await Promise.all([
      fetchAllInventoryState(),
      supabase
        .from('whatsapp_messages')
        .select('*')
        .order('created_at', { ascending: false })
        .limit(60),
      getSyncMetadata('google_sheets_url'),
      getSyncMetadata('google_sheets_webhook_url'),
    ]);

    const skuMatrix = buildSkuPriceHistoryMatrix(items, orders);
    const billLedger = buildBillLedger(items, orders).reverse(); // newest first for UI ledger
    const whatsappMessages = waRes.data || [];
    const pendingWhatsappCount = whatsappMessages.filter(
      (m: { status?: string }) => m.status === 'retry_pending' || m.status === 'config_error'
    ).length;

    return NextResponse.json({
      items,
      skuMatrix,
      billLedger,
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
      const supaUrl = getSupabaseServerUrl();
      const resp = await fetch(`${supaUrl}/functions/v1/whatsapp-webhook?action=retry_pending`, {
        method: 'GET',
      });
      const json = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        return NextResponse.json(
          { error: json?.error || `Webhook returned HTTP ${resp.status}` },
          { status: 502 }
        );
      }
      return NextResponse.json({ success: true, result: json });
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
      for (const line of linePayloads) {
        const matched = matchCatalogItem(line.item_name, catalog);
        if (matched) {
          const packRate = resolvePackUnitPrice(line, matched);
          if (packRate > 0) {
            await supabase
              .from('inventory_items')
              .update({ default_price: packRate })
              .eq('id', matched.id);
          }
        }
      }

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

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Inventory mutation failed' },
      { status: 500 }
    );
  }
}
