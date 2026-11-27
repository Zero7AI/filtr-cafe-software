import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import { DateRangePreset, getIstRangeBoundsMs } from '@/lib/domain/ist';
import { KdsItemClaim } from '@/lib/domain/types';
import {
  fetchOrdersInRangeServer,
  getSupabaseAdmin,
  getSyncMetadataJson,
  triggerGoogleSheetsWebhook,
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

    const { startMs, endMs } = getIstRangeBoundsMs(range, {
      monthKey: sp.get('monthKey') || undefined,
      startDate: sp.get('start') || undefined,
      endDate: sp.get('end') || undefined,
    });

    const supabase = getSupabaseAdmin();
    const [orders, claims, menuRes] = await Promise.all([
      fetchOrdersInRangeServer(startMs, endMs),
      getSyncMetadataJson<KdsItemClaim[]>('kds_claims_active', []),
      supabase.from('menu_items').select('*').order('category').order('name'),
    ]);

    return NextResponse.json({
      orders,
      claims,
      menuItems: menuRes.data || [],
      startMs,
      endMs,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load orders' },
      { status: 500 }
    );
  }
}

export async function PATCH(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const body = await request.json();
    const action = String(body?.action || 'updateOrder');
    const supabase = getSupabaseAdmin();

    if (action === 'updateOrder') {
      const orderId = String(body?.orderId || '').trim();
      const updates = body?.updates || {};
      if (!orderId) {
        return NextResponse.json({ error: 'orderId required' }, { status: 400 });
      }
      const { error } = await supabase
        .from('orders')
        .update(updates)
        .eq('order_id', orderId);
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({ success: true });
    }

    if (action === 'updateItemCompletion') {
      const itemId = Number(body?.itemId || 0);
      const completedQty = Number(body?.completedQty || 0);
      const orderId = String(body?.orderId || '').trim();
      if (!itemId) {
        return NextResponse.json({ error: 'itemId required' }, { status: 400 });
      }
      const { error } = await supabase
        .from('order_items')
        .update({
          completed_quantity: completedQty,
          completed_timestamp: Date.now(),
        })
        .eq('id', itemId);
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      if (orderId && typeof body?.markOrderMade === 'boolean') {
        await supabase
          .from('orders')
          .update({ is_made: body.markOrderMade })
          .eq('order_id', orderId);
      }
      return NextResponse.json({ success: true });
    }

    if (action === 'updateCustomerDetails') {
      const orderId = String(body?.orderId || '').trim();
      if (!orderId) {
        return NextResponse.json({ error: 'orderId required' }, { status: 400 });
      }
      const { error } = await supabase
        .from('orders')
        .update({
          customer_name: String(body?.customerName || 'Walk-in').trim() || 'Walk-in',
          contact_number: String(body?.contactNumber || '').trim(),
          notes: String(body?.notes || '').trim(),
        })
        .eq('order_id', orderId);
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Update failed' },
      { status: 500 }
    );
  }
}

export async function DELETE(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const orderId = request.nextUrl.searchParams.get('orderId')?.trim() || '';
    if (!orderId) {
      return NextResponse.json({ error: 'orderId required' }, { status: 400 });
    }
    const supabase = getSupabaseAdmin();
    await supabase.from('order_items').delete().eq('order_id', orderId);
    const { error } = await supabase.from('orders').delete().eq('order_id', orderId);
    if (error) {
      return NextResponse.json({ error: error.message }, { status: 500 });
    }
    const sheetsResult = await triggerGoogleSheetsWebhook('deleteOrder', { orderId });
    return NextResponse.json({ success: true, sheetsResult });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Delete failed' },
      { status: 500 }
    );
  }
}
