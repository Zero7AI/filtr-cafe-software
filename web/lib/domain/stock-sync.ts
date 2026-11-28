import {
  calculatePacksAndCost,
  matchCatalogItem,
  resolvePackUnitPrice,
} from './inventory';
import {
  InventoryItem,
  InventoryOrderItem,
  InventoryUsage,
} from './types';
import { getSupabaseAdmin } from '../supabase-server';

export function round4(n: number): number {
  return Math.round(n * 10000) / 10000;
}

export interface StockDeltaResult {
  inventoryItemId: number;
  itemName: string;
  deltaPacks: number;
  newStock: number;
}

/**
 * Applies POS order recipe consumption against live `inventory_items.current_stock`.
 * Looks up recipes by `menu_item_id` first, falling back to case-insensitive `menu_item_name` trim match.
 */
export async function applyOrderStockDeltaServer(
  orderItems: Array<{ menu_item_id?: number; item_name?: string; quantity: number }>,
  direction: 'DEDUCT' | 'RESTORE'
): Promise<StockDeltaResult[]> {
  const validItems = orderItems.filter((oi) => Number(oi.quantity || 0) > 0);
  if (validItems.length === 0) return [];

  const supabase = getSupabaseAdmin();
  const [usagesRes, itemsRes] = await Promise.all([
    supabase.from('inventory_usage').select('*'),
    supabase.from('inventory_items').select('*'),
  ]);

  const usages = (usagesRes.data || []) as InventoryUsage[];
  const items = (itemsRes.data || []) as InventoryItem[];
  if (usages.length === 0 || items.length === 0) return [];

  const invById = new Map<number, InventoryItem>(items.map((i) => [i.id, i]));
  const usagesByMenuId = new Map<number, InventoryUsage[]>();
  const usagesByMenuName = new Map<string, InventoryUsage[]>();

  for (const u of usages) {
    if (u.menu_item_id > 0) {
      const list = usagesByMenuId.get(u.menu_item_id) || [];
      list.push(u);
      usagesByMenuId.set(u.menu_item_id, list);
    }
    const cleanName = (u.menu_item_name || '').trim().toLowerCase();
    if (cleanName) {
      const list = usagesByMenuName.get(cleanName) || [];
      list.push(u);
      usagesByMenuName.set(cleanName, list);
    }
  }

  const packsDeltaByItem = new Map<number, number>();
  for (const oi of validItems) {
    const qty = Number(oi.quantity || 0);
    const menuId = Number(oi.menu_item_id || 0);
    const cleanName = (oi.item_name || '').trim().toLowerCase();

    const matchedUsages =
      (menuId > 0 ? usagesByMenuId.get(menuId) : undefined) ||
      (cleanName ? usagesByMenuName.get(cleanName) : undefined) ||
      [];

    for (const usage of matchedUsages) {
      const consumedQty = Number(usage.quantity_used || 0) * qty;
      if (consumedQty <= 0) continue;
      const master = invById.get(usage.inventory_item_id);
      if (!master) continue;
      const { packs } = calculatePacksAndCost(consumedQty, usage.unit, master);
      if (packs > 0) {
        packsDeltaByItem.set(
          master.id,
          (packsDeltaByItem.get(master.id) || 0) + packs
        );
      }
    }
  }

  const results: StockDeltaResult[] = [];
  for (const [invId, packs] of packsDeltaByItem.entries()) {
    const master = invById.get(invId);
    if (!master) continue;
    const current = Number(master.current_stock || 0);
    const nextStock =
      direction === 'DEDUCT'
        ? Math.max(0, round4(current - packs))
        : round4(current + packs);

    const { error } = await supabase
      .from('inventory_items')
      .update({ current_stock: nextStock })
      .eq('id', invId);

    if (!error) {
      results.push({
        inventoryItemId: invId,
        itemName: master.name,
        deltaPacks: round4(direction === 'DEDUCT' ? -packs : packs),
        newStock: nextStock,
      });
    }
  }

  return results;
}

/**
 * Adds purchased bill line quantities (in master packs) to `inventory_items.current_stock`
 * and updates `default_price` using `resolvePackUnitPrice`.
 */
export async function addPurchasedStockForLinesServer(
  lines: Array<{
    item_name: string;
    quantity: number;
    unit: string;
    unit_price?: number;
    subtotal?: number;
  }>,
  providedCatalog?: InventoryItem[]
): Promise<StockDeltaResult[]> {
  if (!lines || lines.length === 0) return [];

  const supabase = getSupabaseAdmin();
  let catalog = providedCatalog;
  if (!catalog) {
    const { data } = await supabase
      .from('inventory_items')
      .select('*')
      .eq('is_active', true)
      .order('name');
    catalog = (data || []) as InventoryItem[];
  }
  if (catalog.length === 0) return [];

  const runningStock = new Map<number, number>(
    catalog.map((c) => [c.id, Number(c.current_stock || 0)])
  );
  const runningRate = new Map<number, number>(
    catalog.map((c) => [c.id, Number(c.default_price || 0)])
  );
  const addedPacks = new Map<number, number>();
  const matchedById = new Map<number, InventoryItem>();

  for (const line of lines) {
    const qty = Number(line.quantity || 0);
    if (qty <= 0 || !String(line.item_name || '').trim()) continue;
    const matched = matchCatalogItem(line.item_name, catalog);
    if (!matched) continue;

    matchedById.set(matched.id, matched);
    const { packs } = calculatePacksAndCost(qty, line.unit, matched);
    if (packs > 0) {
      runningStock.set(matched.id, round4((runningStock.get(matched.id) || 0) + packs));
      addedPacks.set(matched.id, round4((addedPacks.get(matched.id) || 0) + packs));
    }

    const rate = resolvePackUnitPrice(
      {
        order_id: '',
        item_name: line.item_name,
        quantity: qty,
        unit: line.unit,
        unit_price: Number(line.unit_price || 0),
        subtotal: Number(line.subtotal || qty * Number(line.unit_price || 0)),
      } as InventoryOrderItem,
      matched
    );
    if (rate > 0) {
      runningRate.set(matched.id, rate);
    }
  }

  const results: StockDeltaResult[] = [];
  for (const [invId, master] of matchedById.entries()) {
    const nextStock = round4(runningStock.get(invId) ?? Number(master.current_stock || 0));
    const nextRate = runningRate.get(invId) ?? Number(master.default_price || 0);
    const updatePayload: Record<string, number> = { current_stock: nextStock };
    if (nextRate > 0) updatePayload.default_price = nextRate;

    const { error } = await supabase
      .from('inventory_items')
      .update(updatePayload)
      .eq('id', invId);

    if (!error) {
      master.current_stock = nextStock;
      if (nextRate > 0) master.default_price = nextRate;
      results.push({
        inventoryItemId: invId,
        itemName: master.name,
        deltaPacks: addedPacks.get(invId) || 0,
        newStock: nextStock,
      });
    }
  }

  return results;
}

/**
 * Formats pack-based `current_stock` into a human-readable remaining quantity label.
 */
export function formatLiveStockLabel(item: InventoryItem): string {
  const packs = Number(item.current_stock || 0);
  const std = item.standard_quantity > 0 ? item.standard_quantity : 1;
  const unit = (item.unit || 'pcs').trim();
  const fmt = (v: number) =>
    Math.abs(v - Math.round(v)) < 0.01 ? String(Math.round(v)) : String(Math.round(v * 100) / 100);

  if (Math.abs(std - 1) < 0.01) {
    return `${fmt(packs)} ${unit}`;
  }
  const totalBase = packs * std;
  return `${fmt(packs)} packs (${fmt(totalBase)} ${unit})`;
}
