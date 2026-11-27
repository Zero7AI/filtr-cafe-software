import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import { normalizeStaffKey } from '@/lib/domain/attendance';
import { getIstDateKey, getIstDayBoundsMs } from '@/lib/domain/ist';
import { buildFifoPendingQueue, resolveBaristaColorHex } from '@/lib/domain/kds';
import { KdsItemClaim, StaffRosterMember } from '@/lib/domain/types';
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
    const paidOnly = request.nextUrl.searchParams.get('paidOnly') === 'true';
    const todayKey = getIstDateKey();
    const { startMs } = getIstDayBoundsMs(todayKey);
    const lookbackStart = startMs - 24 * 60 * 60 * 1000;
    const endMs = Date.now() + 60_000;

    const [orders, claims, roster] = await Promise.all([
      fetchOrdersInRangeServer(lookbackStart, endMs),
      getSyncMetadataJson<KdsItemClaim[]>('kds_claims_active', []),
      getSyncMetadataJson<StaffRosterMember[]>('attendance_staff_roster', []),
    ]);

    const activeRoster = roster.filter((m) => m.isActive);
    const queue = buildFifoPendingQueue({
      orders,
      claims,
      roster: activeRoster,
      paidOnlyFilter: paidOnly,
      minTimestampMs: startMs,
    });

    return NextResponse.json({
      queue,
      claims,
      roster: activeRoster,
      todayKey,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load KDS' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const body = await request.json();
    const action = String(body?.action || ''); // CLAIM | COMPLETE | RELEASE
    const orderId = String(body?.orderId || '').trim();
    const itemId = Number(body?.itemId || 0);
    const itemName = String(body?.itemName || '');
    const unitIndex = Number(body?.unitIndex || 1);
    const staffName = String(body?.staffName || auth.session.operatorName || 'Admin').trim();

    if (!orderId || !itemId) {
      return NextResponse.json({ error: 'Missing orderId or itemId' }, { status: 400 });
    }

    const [claims, roster] = await Promise.all([
      getSyncMetadataJson<KdsItemClaim[]>('kds_claims_active', []),
      getSyncMetadataJson<StaffRosterMember[]>('attendance_staff_roster', []),
    ]);

    const key = `${orderId}_${itemId}_${unitIndex}`;
    const now = Date.now();
    const cutoff = now - 24 * 60 * 60 * 1000;
    const filteredClaims = claims.filter(
      (c) => c.key !== key && (c.status === 'MAKING' || c.updatedAt >= cutoff)
    );

    const member = roster.find(
      (r) => normalizeStaffKey(r.name) === normalizeStaffKey(staffName)
    );
    const colorHex = resolveBaristaColorHex(staffName, member?.colorHex || '');

    if (action === 'CLAIM') {
      filteredClaims.push({
        key,
        orderId,
        itemId,
        itemName,
        unitIndex,
        status: 'MAKING',
        staffName,
        colorHex,
        updatedAt: now,
      });
      await setSyncMetadata('kds_claims_active', filteredClaims);
      return NextResponse.json({ success: true, status: 'MAKING' });
    }

    if (action === 'COMPLETE') {
      filteredClaims.push({
        key,
        orderId,
        itemId,
        itemName,
        unitIndex,
        status: 'MADE',
        staffName,
        colorHex,
        updatedAt: now,
      });
      await setSyncMetadata('kds_claims_active', filteredClaims);

      // Update order_item completed_quantity and check if entire order is now made
      const supabase = getSupabaseAdmin();
      const { data: orderItems } = await supabase
        .from('order_items')
        .select('*')
        .eq('order_id', orderId);

      if (orderItems && orderItems.length > 0) {
        const targetItem = orderItems.find(
          (it) => Number(it.id) === itemId || Number(it.menu_item_id) === itemId
        );
        if (targetItem) {
          const madeUnitsForItem = filteredClaims.filter(
            (c) =>
              c.orderId === orderId &&
              c.itemId === itemId &&
              c.status === 'MADE'
          ).length;
          const nextCompleted = Math.min(
            Number(targetItem.quantity || 1),
            Math.max(Number(targetItem.completed_quantity || 0), madeUnitsForItem)
          );
          await supabase
            .from('order_items')
            .update({
              completed_quantity: nextCompleted,
              completed_timestamp: now,
            })
            .eq('id', targetItem.id);
          targetItem.completed_quantity = nextCompleted;
        }

        const allDone = orderItems.every(
          (it) => Number(it.completed_quantity || 0) >= Number(it.quantity || 1)
        );
        if (allDone) {
          await supabase
            .from('orders')
            .update({ is_made: true })
            .eq('order_id', orderId);
        }
      }
      return NextResponse.json({ success: true, status: 'MADE' });
    }

    if (action === 'RELEASE') {
      await setSyncMetadata('kds_claims_active', filteredClaims);
      return NextResponse.json({ success: true, status: 'PENDING' });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'KDS update failed' },
      { status: 500 }
    );
  }
}
