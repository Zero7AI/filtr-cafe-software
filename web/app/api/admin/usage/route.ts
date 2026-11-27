import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import { computeIngredientUsage } from '@/lib/domain/inventory';
import { DateRangePreset, getIstRangeBoundsMs } from '@/lib/domain/ist';
import { InventoryItem, InventoryUsage } from '@/lib/domain/types';
import {
  fetchOrdersInRangeServer,
  getSupabaseAdmin,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const sp = request.nextUrl.searchParams;
    const rangeParam = (sp.get('range') || 'TODAY').toUpperCase() as DateRangePreset;
    const validRanges: DateRangePreset[] = [
      'TODAY',
      'YESTERDAY',
      'LAST_7_DAYS',
      'THIS_MONTH',
      'CUSTOM',
    ];
    const range: DateRangePreset = validRanges.includes(rangeParam) ? rangeParam : 'TODAY';

    const { startMs, endMs } = getIstRangeBoundsMs(range, {
      startDate: sp.get('start') || undefined,
      endDate: sp.get('end') || undefined,
    });

    const supabase = getSupabaseAdmin();
    const [orders, itemsRes, usageRes] = await Promise.all([
      fetchOrdersInRangeServer(startMs, endMs),
      supabase.from('inventory_items').select('*').order('name'),
      supabase.from('inventory_usage').select('*').order('id'),
    ]);

    const items = (itemsRes.data || []) as InventoryItem[];
    const usages = (usageRes.data || []) as InventoryUsage[];
    const report = computeIngredientUsage(orders, items, usages);

    return NextResponse.json({
      ordersCount: report.ordersCount,
      items: report.items,
      range,
      startMs,
      endMs,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load inventory usage' },
      { status: 500 }
    );
  }
}
