import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import {
  buildBillLedger,
  buildSkuPriceHistoryMatrix,
} from '@/lib/domain/inventory';
import { buildSkuPriceHistoryXlsx } from '@/lib/domain/xlsx';
import {
  InventoryItem,
  InventoryOrder,
  InventoryOrderItem,
  InventoryOrderWithItems,
} from '@/lib/domain/types';
import { getSupabaseAdmin } from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const includeDates = request.nextUrl.searchParams.get('includeDates') !== 'false';
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

    const matrix = buildSkuPriceHistoryMatrix(items, orders);
    const ledger = buildBillLedger(items, orders);
    const xlsxBytes = buildSkuPriceHistoryXlsx(matrix, ledger, includeDates);

    return new NextResponse(Buffer.from(xlsxBytes), {
      status: 200,
      headers: {
        'Content-Type':
          'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'Content-Disposition':
          'attachment; filename="FILTR_SKU_Price_History.xlsx"',
        'Cache-Control': 'no-store',
      },
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Export failed' },
      { status: 500 }
    );
  }
}
