import { NextRequest, NextResponse } from 'next/server';
import { getIstCompactDate } from '@/lib/domain/ist';
import { applyOrderStockDeltaServer } from '@/lib/domain/stock-sync';
import {
  DEFAULT_RECEIPT_CONFIG,
  fetchAccessPolicyConfig,
  fetchReceiptDesignConfigServer,
  getSupabaseAdmin,
  mapOrderRowToOrderWithItems,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  try {
    const action = request.nextUrl.searchParams.get('action') || '';
    const supabase = getSupabaseAdmin();

    if (action === 'menu') {
      const { data, error } = await supabase
        .from('menu_items')
        .select('*')
        .eq('is_available', true)
        .order('category', { ascending: true })
        .order('name', { ascending: true });

      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({ items: data || [] });
    }

    if (action === 'orders') {
      const filter = request.nextUrl.searchParams.get('filter') || 'UNCOMPLETED';
      const limit = Math.min(500, Math.max(1, Number(request.nextUrl.searchParams.get('limit') || 200)));
      const onlyToday = request.nextUrl.searchParams.get('onlyToday') !== 'false';

      let query = supabase
        .from('orders')
        .select('*, order_items(*)')
        .order('timestamp', { ascending: false })
        .limit(limit);

      if (onlyToday) {
        const todayDate = getIstCompactDate();
        query = query.like('order_id', `FLT-${todayDate}-%`);
      }

      if (filter === 'UNCOMPLETED') {
        query = query.or('is_delivered.eq.false,is_made.eq.false');
      } else if (filter === 'COMPLETED') {
        query = query.eq('is_delivered', true).eq('is_made', true);
      }

      const { data, error } = await query;
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      const orders = (data || []).map((r) =>
        mapOrderRowToOrderWithItems(r as Record<string, unknown>)
      );
      return NextResponse.json({ orders });
    }

    if (action === 'nextOrderId') {
      const datePart = getIstCompactDate();
      const prefix = `FLT-${datePart}-`;

      // Try atomic RPC first (same as Android app)
      const { data: rpcSeq, error: rpcErr } = await supabase.rpc(
        'get_next_order_sequence',
        { p_date_part: datePart }
      );
      if (!rpcErr && typeof rpcSeq === 'number' && rpcSeq > 0) {
        return NextResponse.json({ orderId: `${prefix}${rpcSeq}` });
      }

      const { data, error } = await supabase
        .from('orders')
        .select('order_id')
        .like('order_id', `${prefix}%`)
        .order('order_id', { ascending: false })
        .limit(1);

      if (error || !data || data.length === 0) {
        return NextResponse.json({ orderId: `${prefix}1001` });
      }
      const lastId = String(data[0].order_id || '');
      const lastSeq = parseInt(lastId.split('-').pop() || '1000', 10);
      const nextSeq = Number.isNaN(lastSeq) ? 1001 : lastSeq + 1;
      return NextResponse.json({ orderId: `${prefix}${nextSeq}` });
    }

    if (action === 'receiptConfig') {
      const config = await fetchReceiptDesignConfigServer().catch(
        () => DEFAULT_RECEIPT_CONFIG
      );
      return NextResponse.json({ config });
    }

    if (action === 'upiAccounts') {
      const { data, error } = await supabase
        .from('upi_accounts')
        .select('*')
        .order('is_default', { ascending: false });

      if (error || !data || data.length === 0) {
        return NextResponse.json({
          accounts: [
            {
              vpa: '9907839311@ybl',
              payee_name: 'FILTR Coffee Store',
              is_default: true,
              note: 'FILTR Coffee Payment',
            },
          ],
        });
      }
      return NextResponse.json({ accounts: data });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Server error' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const action = String(body?.action || '');
    const supabase = getSupabaseAdmin();

    if (action === 'verifyPin') {
      const pin = String(body?.pin || '').trim();
      const policy = await fetchAccessPolicyConfig();
      const envPin = process.env.NEXT_PUBLIC_POS_PIN || '1234';
      const valid = pin === policy.staff_passcode || pin === envPin;
      return NextResponse.json({ valid });
    }

    if (action === 'createOrder') {
      const order = body?.order;
      const items = Array.isArray(body?.items) ? body.items : [];
      if (!order?.order_id) {
        return NextResponse.json({ success: false, error: 'Missing order_id' }, { status: 400 });
      }

      const { error: orderError } = await supabase.from('orders').insert({
        order_id: order.order_id,
        customer_name: order.customer_name || 'Walk-in',
        contact_number: order.contact_number || '',
        total_amount: order.total_amount,
        timestamp: order.timestamp,
        is_made: Boolean(order.is_made),
        is_paid: Boolean(order.is_paid),
        is_delivered: Boolean(order.is_delivered),
        sync_status: 'SYNCED',
        notes: order.notes || '',
        payment_mode: order.payment_mode || 'CASH',
        completed_timestamp: order.completed_timestamp || 0,
      });

      if (orderError) {
        return NextResponse.json({ success: false, error: orderError.message }, { status: 500 });
      }

      if (items.length > 0) {
        const itemsPayload = items.map((item: Record<string, unknown>) => ({
          order_id: order.order_id,
          menu_item_id: item.menu_item_id,
          item_name: item.item_name,
          item_price: item.item_price,
          quantity: item.quantity,
          subtotal: item.subtotal,
          completed_quantity: item.completed_quantity || 0,
          completed_timestamp: item.completed_timestamp || 0,
        }));

        const { error: itemsError } = await supabase
          .from('order_items')
          .insert(itemsPayload);

        if (itemsError) {
          return NextResponse.json({ success: false, error: itemsError.message }, { status: 500 });
        }
      }

      const stockUpdates = await applyOrderStockDeltaServer(
        items.map((it: Record<string, unknown>) => ({
          menu_item_id: Number(it.menu_item_id || 0),
          item_name: String(it.item_name || ''),
          quantity: Number(it.quantity || 0),
        })),
        'DEDUCT'
      ).catch(() => []);

      return NextResponse.json({ success: true, stockUpdates });
    }

    if (action === 'updateOrderStatus') {
      const orderId = String(body?.orderId || '');
      const updates = body?.updates || {};
      if (!orderId) {
        return NextResponse.json({ success: false, error: 'Missing orderId' }, { status: 400 });
      }
      const { error } = await supabase
        .from('orders')
        .update(updates)
        .eq('order_id', orderId);
      return NextResponse.json({ success: !error, error: error?.message });
    }

    if (action === 'updateOrderItemCompletion') {
      const itemId = Number(body?.itemId || 0);
      const completedQty = Number(body?.completedQty || 0);
      if (!itemId) {
        return NextResponse.json({ success: false }, { status: 400 });
      }
      const { error } = await supabase
        .from('order_items')
        .update({
          completed_quantity: completedQty,
          completed_timestamp: Date.now(),
        })
        .eq('id', itemId);
      return NextResponse.json({ success: !error });
    }

    if (action === 'deleteOrder') {
      const orderId = String(body?.orderId || '');
      if (!orderId) {
        return NextResponse.json({ success: false }, { status: 400 });
      }
      const { data: existingItems } = await supabase
        .from('order_items')
        .select('menu_item_id, item_name, quantity')
        .eq('order_id', orderId);

      await supabase.from('order_items').delete().eq('order_id', orderId);
      const { error } = await supabase.from('orders').delete().eq('order_id', orderId);

      if (!error && existingItems && existingItems.length > 0) {
        await applyOrderStockDeltaServer(
          existingItems.map((it) => ({
            menu_item_id: Number(it.menu_item_id || 0),
            item_name: String(it.item_name || ''),
            quantity: Number(it.quantity || 0),
          })),
          'RESTORE'
        ).catch(() => []);
      }

      return NextResponse.json({ success: !error });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { success: false, error: err instanceof Error ? err.message : 'Server error' },
      { status: 500 }
    );
  }
}
