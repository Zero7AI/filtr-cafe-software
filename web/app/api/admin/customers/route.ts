import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import { getSupabaseAdmin, mapOrderRowToOrderWithItems } from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const supabase = getSupabaseAdmin();
    const customerName = request.nextUrl.searchParams.get('customerName')?.trim();

    if (customerName) {
      const { data, error } = await supabase
        .from('orders')
        .select('*, order_items(*)')
        .ilike('customer_name', customerName)
        .order('timestamp', { ascending: false })
        .limit(100);
      if (error) return NextResponse.json({ error: error.message }, { status: 500 });
      return NextResponse.json({
        orders: (data || []).map((r) =>
          mapOrderRowToOrderWithItems(r as Record<string, unknown>)
        ),
      });
    }

    const { data, error } = await supabase
      .from('customers')
      .select('*')
      .order('total_spent', { ascending: false });

    if (error) return NextResponse.json({ error: error.message }, { status: 500 });
    return NextResponse.json({ customers: data || [] });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load customers' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const body = await request.json();
    const id = Number(body?.id || 0);
    const name = String(body?.name || '').trim();
    if (!name) {
      return NextResponse.json({ error: 'Customer name required' }, { status: 400 });
    }
    const payload = {
      name,
      contact_number: String(body?.contact_number || '').trim(),
      total_orders: Math.max(0, Number(body?.total_orders || 0)),
      total_spent: Math.max(0, Number(body?.total_spent || 0)),
      last_order_timestamp: Number(body?.last_order_timestamp || 0),
      created_at: Number(body?.created_at || Date.now()),
    };

    const supabase = getSupabaseAdmin();
    if (id > 0) {
      const { error } = await supabase
        .from('customers')
        .update(payload)
        .eq('id', id);
      if (error) return NextResponse.json({ error: error.message }, { status: 500 });
    } else {
      const { error } = await supabase.from('customers').insert(payload);
      if (error) return NextResponse.json({ error: error.message }, { status: 500 });
    }
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Customer save failed' },
      { status: 500 }
    );
  }
}

export async function DELETE(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const id = Number(request.nextUrl.searchParams.get('id') || 0);
    if (!id) return NextResponse.json({ error: 'id required' }, { status: 400 });
    const supabase = getSupabaseAdmin();
    const { error } = await supabase.from('customers').delete().eq('id', id);
    if (error) return NextResponse.json({ error: error.message }, { status: 500 });
    return NextResponse.json({ success: true });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Delete failed' },
      { status: 500 }
    );
  }
}
