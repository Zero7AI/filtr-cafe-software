import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import {
  DateRangePreset,
  getIstMonthKey,
  getIstRangeBoundsMs,
} from '@/lib/domain/ist';
import { computePnlReport } from '@/lib/domain/pnl';
import {
  FixedCostRecord,
  FixedCostStaffMember,
  InventoryItem,
  InventoryUsage,
} from '@/lib/domain/types';
import {
  fetchOrdersInRangeServer,
  getSupabaseAdmin,
  getSyncMetadataJson,
  setSyncMetadata,
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
      'MONTH',
      'CUSTOM',
    ];
    const range: DateRangePreset = validRanges.includes(rangeParam)
      ? rangeParam
      : 'TODAY';
    const monthKey = sp.get('monthKey') || getIstMonthKey();
    const startDate = sp.get('start') || undefined;
    const endDate = sp.get('end') || undefined;

    const { startMs, endMs } = getIstRangeBoundsMs(range, {
      monthKey,
      startDate,
      endDate,
    });

    const supabase = getSupabaseAdmin();
    const [orders, invItemsRes, invUsageRes, fixedCostsMap] = await Promise.all([
      fetchOrdersInRangeServer(startMs, endMs),
      supabase.from('inventory_items').select('*'),
      supabase.from('inventory_usage').select('*'),
      getSyncMetadataJson<Record<string, FixedCostRecord>>('fixed_costs_config', {}),
    ]);

    const report = computePnlReport({
      range,
      monthKey,
      startDate,
      endDate,
      orders,
      inventoryItems: (invItemsRes.data || []) as InventoryItem[],
      usages: (invUsageRes.data || []) as InventoryUsage[],
      fixedCostsMap,
    });

    return NextResponse.json({
      report,
      fixedCostsMap,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to compute P&L' },
      { status: 500 }
    );
  }
}

export async function PUT(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const body = await request.json();
    const monthKey = String(body?.monthKey || '').trim();
    if (!/^\d{4}-\d{2}$/.test(monthKey)) {
      return NextResponse.json(
        { error: 'Valid monthKey (YYYY-MM) is required' },
        { status: 400 }
      );
    }

    const rawStaff = Array.isArray(body?.staffMembers) ? body.staffMembers : [];
    const staffMembers: FixedCostStaffMember[] = rawStaff.map(
      (s: Record<string, unknown>, idx: number) => ({
        id: String(s.id || `staff_${monthKey}_${idx + 1}`),
        name: String(s.name || `Staff ${idx + 1}`).trim() || `Staff ${idx + 1}`,
        salary: Math.max(0, Number(s.salary || 0)),
      })
    );
    const staffSalaries = staffMembers.reduce((sum, s) => sum + s.salary, 0);

    const record: FixedCostRecord = {
      monthKey,
      rent: Math.max(0, Number(body?.rent || 0)),
      electricity: Math.max(0, Number(body?.electricity || 0)),
      staffSalaries,
      staffCount: staffMembers.length,
      maidSalary: Math.max(0, Number(body?.maidSalary || 0)),
      miscellaneous: Math.max(0, Number(body?.miscellaneous || 0)),
      notes: String(body?.notes || ''),
      staffMembers,
      updatedAt: Date.now(),
    };

    const existingMap = await getSyncMetadataJson<Record<string, FixedCostRecord>>(
      'fixed_costs_config',
      {}
    );
    existingMap[monthKey] = record;

    const saveRes = await setSyncMetadata('fixed_costs_config', existingMap);
    if (!saveRes.success) {
      return NextResponse.json(
        { error: saveRes.error || 'Failed to save fixed costs' },
        { status: 500 }
      );
    }
    return NextResponse.json({ success: true, record, fixedCostsMap: existingMap });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to save fixed costs' },
      { status: 500 }
    );
  }
}
