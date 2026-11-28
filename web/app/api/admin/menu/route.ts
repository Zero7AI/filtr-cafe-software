import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import { fetchEffectiveInventoryItemsServer } from '@/lib/domain/stock-sync';
import {
  getSupabaseAdmin,
  getSyncMetadataJson,
  setSyncMetadata,
  triggerGoogleSheetsWebhook,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const supabase = getSupabaseAdmin();
    const [menuRes, usageRes, effectiveInv, itemOrder] = await Promise.all([
      supabase.from('menu_items').select('*').order('category').order('name'),
      supabase.from('inventory_usage').select('*').order('id'),
      fetchEffectiveInventoryItemsServer(),
      getSyncMetadataJson<string[]>('menu_item_order', []),
    ]);

    return NextResponse.json({
      menuItems: menuRes.data || [],
      usages: usageRes.data || [],
      inventoryItems: effectiveInv.items,
      itemOrder,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load menu' },
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

    if (action === 'upsertMenuItem') {
      const id = Number(body?.id || 0);
      const name = String(body?.name || '').trim();
      const category = String(body?.category || 'General').trim().toUpperCase();
      const price = Math.max(0, Number(body?.price || 0));
      const description = String(body?.description || '').trim();
      const isAvailable = body?.is_available !== false;

      if (!name) {
        return NextResponse.json({ error: 'Item name is required' }, { status: 400 });
      }

      if (id > 0) {
        const { data, error } = await supabase
          .from('menu_items')
          .update({ name, category, price, description, is_available: isAvailable })
          .eq('id', id)
          .select('*')
          .single();
        if (error) {
          return NextResponse.json({ error: error.message }, { status: 500 });
        }
        return NextResponse.json({ success: true, item: data });
      } else {
        const { data, error } = await supabase
          .from('menu_items')
          .insert({ name, category, price, description, is_available: isAvailable })
          .select('*')
          .single();
        if (error) {
          return NextResponse.json({ error: error.message }, { status: 500 });
        }
        return NextResponse.json({ success: true, item: data });
      }
    }

    if (action === 'toggleAvailability') {
      const id = Number(body?.id || 0);
      const isAvailable = Boolean(body?.is_available);
      if (!id) {
        return NextResponse.json({ error: 'id required' }, { status: 400 });
      }
      const { error } = await supabase
        .from('menu_items')
        .update({ is_available: isAvailable })
        .eq('id', id);
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({ success: true });
    }

    if (action === 'saveOrder') {
      const order = Array.isArray(body?.order)
        ? body.order.map((x: unknown) => String(x))
        : [];
      await setSyncMetadata('menu_item_order', order);
      return NextResponse.json({ success: true });
    }

    if (action === 'upsertRecipeRow') {
      const id = Number(body?.id || 0);
      const payload = {
        menu_item_id: Number(body?.menu_item_id || 0),
        menu_item_name: String(body?.menu_item_name || '').trim(),
        inventory_item_id: Number(body?.inventory_item_id || 0),
        inventory_item_name: String(body?.inventory_item_name || '').trim(),
        quantity_used: Math.max(0.01, Number(body?.quantity_used || 1)),
        unit: String(body?.unit || 'g').trim() || 'g',
      };
      if (!payload.menu_item_id || !payload.inventory_item_id) {
        return NextResponse.json(
          { error: 'menu_item_id and inventory_item_id required' },
          { status: 400 }
        );
      }

      if (id > 0) {
        const { error } = await supabase
          .from('inventory_usage')
          .update(payload)
          .eq('id', id);
        if (error) {
          return NextResponse.json({ error: error.message }, { status: 500 });
        }
      } else {
        const { error } = await supabase.from('inventory_usage').insert(payload);
        if (error) {
          return NextResponse.json({ error: error.message }, { status: 500 });
        }
      }
      return NextResponse.json({ success: true });
    }

    if (action === 'deleteRecipeRow') {
      const id = Number(body?.id || 0);
      if (!id) {
        return NextResponse.json({ error: 'id required' }, { status: 400 });
      }
      const { error } = await supabase.from('inventory_usage').delete().eq('id', id);
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({ success: true });
    }

    if (action === 'syncSheets') {
      const { data: menuItems } = await supabase
        .from('menu_items')
        .select('*')
        .order('category')
        .order('name');
      const mapped = (menuItems || []).map((m) => ({
        id: m.id,
        name: m.name,
        category: m.category,
        price: m.price,
        description: m.description || '',
        isAvailable: m.is_available,
      }));
      const res = await triggerGoogleSheetsWebhook('syncItemMaster', {
        menuItems: mapped,
      });
      return NextResponse.json({ success: res.ok, ...res });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Menu mutation failed' },
      { status: 500 }
    );
  }
}

export async function DELETE(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const id = Number(request.nextUrl.searchParams.get('id') || 0);
    if (!id) {
      return NextResponse.json({ error: 'id required' }, { status: 400 });
    }
    const supabase = getSupabaseAdmin();

    // Guardrail: never hard-delete menu items referenced by historical order_items
    const { data: refs } = await supabase
      .from('order_items')
      .select('id')
      .eq('menu_item_id', id)
      .limit(1);

    if (refs && refs.length > 0) {
      const { error } = await supabase
        .from('menu_items')
        .update({ is_available: false })
        .eq('id', id);
      if (error) {
        return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({
        success: true,
        softDisabled: true,
        message: 'Item has existing orders — marked unavailable to preserve order history.',
      });
    }

    await supabase.from('inventory_usage').delete().eq('menu_item_id', id);
    const { error } = await supabase.from('menu_items').delete().eq('id', id);
    if (error) {
      return NextResponse.json({ error: error.message }, { status: 500 });
    }
    return NextResponse.json({ success: true, softDisabled: false });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Delete failed' },
      { status: 500 }
    );
  }
}
