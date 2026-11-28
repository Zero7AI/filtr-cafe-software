import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import {
  applyLatestPurchasePrices,
  buildSkuPriceHistoryMatrix,
  calculatePacksAndCost,
  computeIngredientUsage,
} from '@/lib/domain/inventory';
import { persistLatestItemPricesServer } from '@/lib/domain/stock-sync';
import {
  DateRangePreset,
  getIstDateKey,
  getIstMonthKey,
  getIstRangeBoundsMs,
  IST_OFFSET_MS,
} from '@/lib/domain/ist';
import {
  buildShiftId,
  computeReorderRecommendation,
  KITCHEN_SEED_CATALOG,
} from '@/lib/domain/kitchen';
import { computePnlReport } from '@/lib/domain/pnl';
import {
  AttendanceLog,
  FixedCostRecord,
  InventoryItem,
  InventoryOrder,
  InventoryOrderItem,
  InventoryOrderWithItems,
  InventoryUsage,
  KitchenInventoryEntry,
  KitchenInventoryItem,
  KitchenInventoryShift,
  StaffRosterMember,
} from '@/lib/domain/types';
import {
  fetchOrdersInRangeServer,
  getSupabaseAdmin,
  getSyncMetadataJson,
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

    const { startMs, endMs, dateKeys } = getIstRangeBoundsMs(range, {
      monthKey,
      startDate,
      endDate,
    });

    const spanMs = Math.max(1, endMs - startMs + 1);
    const prevEndMs = startMs - 1;
    const prevStartMs = prevEndMs - spanMs + 1;

    const todayKey = getIstDateKey();
    const todayMonthKey = todayKey.slice(0, 7);
    const supabase = getSupabaseAdmin();

    const [
      orders,
      prevOrders,
      menuRes,
      invItemsRes,
      invOrdersRes,
      invUsageRes,
      fixedCostsMap,
      waPendingRes,
      roster,
      monthLogs,
      kitchenCatalogCloud,
      openingShift,
      closingShift,
    ] = await Promise.all([
      fetchOrdersInRangeServer(startMs, endMs),
      fetchOrdersInRangeServer(prevStartMs, prevEndMs),
      supabase.from('menu_items').select('id, name, category'),
      supabase.from('inventory_items').select('*'),
      supabase
        .from('inventory_orders')
        .select('*, inventory_order_items(*)')
        .order('timestamp', { ascending: true })
        .limit(1000),
      supabase.from('inventory_usage').select('*'),
      getSyncMetadataJson<Record<string, FixedCostRecord>>('fixed_costs_config', {}),
      supabase
        .from('whatsapp_messages')
        .select('id', { count: 'exact', head: true })
        .in('status', ['retry_pending', 'config_error']),
      getSyncMetadataJson<StaffRosterMember[]>('attendance_staff_roster', []),
      getSyncMetadataJson<AttendanceLog[]>(`attendance_month_${todayMonthKey}`, []),
      getSyncMetadataJson<KitchenInventoryItem[]>('kitchen_inv_catalog', []),
      getSyncMetadataJson<KitchenInventoryShift | null>(
        `kitchen_inv_shift_${buildShiftId(todayKey, 'OPENING')}`,
        null
      ),
      getSyncMetadataJson<KitchenInventoryShift | null>(
        `kitchen_inv_shift_${buildShiftId(todayKey, 'CLOSING')}`,
        null
      ),
    ]);

    const categoryByMenuId = new Map<number, string>();
    const categoryByMenuName = new Map<string, string>();
    for (const m of menuRes.data || []) {
      categoryByMenuId.set(Number(m.id), String(m.category || 'Other'));
      categoryByMenuName.set(
        String(m.name || '').trim().toLowerCase(),
        String(m.category || 'Other')
      );
    }

    let grossRevenue = 0;
    let paidRevenue = 0;
    let cashRevenue = 0;
    let upiRevenue = 0;
    let unpaidCount = 0;
    let activeKdsCount = 0;

    const itemStats = new Map<string, { qty: number; revenue: number; category: string }>();
    const categoryStats = new Map<string, { qty: number; revenue: number }>();
    const bucketRevenue = new Map<string, { label: string; revenue: number; orders: number }>();

    const singleDay = dateKeys.length === 1;
    if (singleDay) {
      for (let h = 7; h <= 23; h++) {
        const label = `${h > 12 ? h - 12 : h}${h >= 12 ? 'p' : 'a'}`;
        bucketRevenue.set(String(h).padStart(2, '0'), {
          label,
          revenue: 0,
          orders: 0,
        });
      }
    } else {
      for (const dk of dateKeys) {
        bucketRevenue.set(dk, {
          label: dk.slice(5),
          revenue: 0,
          orders: 0,
        });
      }
    }

    for (const owi of orders) {
      const o = owi.order;
      const amt = o.total_amount || 0;
      grossRevenue += amt;
      if (o.is_paid) {
        paidRevenue += amt;
        if (o.payment_mode === 'UPI') upiRevenue += amt;
        else cashRevenue += amt;
      } else {
        unpaidCount++;
      }
      if (!o.is_made && !o.is_delivered) {
        activeKdsCount++;
      }

      const istDate = new Date(o.timestamp + IST_OFFSET_MS);
      if (singleDay) {
        const h = String(istDate.getUTCHours()).padStart(2, '0');
        const b = bucketRevenue.get(h);
        if (b) {
          b.revenue += amt;
          b.orders += 1;
        }
      } else {
        const dk = getIstDateKey(o.timestamp);
        const b = bucketRevenue.get(dk);
        if (b) {
          b.revenue += amt;
          b.orders += 1;
        }
      }

      for (const it of owi.items) {
        const cat =
          categoryByMenuId.get(it.menu_item_id) ||
          categoryByMenuName.get(it.item_name.trim().toLowerCase()) ||
          'Other';
        const lineRev = it.subtotal || it.item_price * it.quantity;

        const prevItem = itemStats.get(it.item_name) || {
          qty: 0,
          revenue: 0,
          category: cat,
        };
        prevItem.qty += it.quantity;
        prevItem.revenue += lineRev;
        itemStats.set(it.item_name, prevItem);

        const prevCat = categoryStats.get(cat) || { qty: 0, revenue: 0 };
        prevCat.qty += it.quantity;
        prevCat.revenue += lineRev;
        categoryStats.set(cat, prevCat);
      }
    }

    const prevGrossRevenue = prevOrders.reduce(
      (s, o) => s + (o.order.total_amount || 0),
      0
    );
    const revenueDeltaPct =
      prevGrossRevenue > 0
        ? Math.round(((grossRevenue - prevGrossRevenue) / prevGrossRevenue) * 1000) / 10
        : null;
    const ordersDeltaPct =
      prevOrders.length > 0
        ? Math.round(((orders.length - prevOrders.length) / prevOrders.length) * 1000) / 10
        : null;

    const totalOrders = orders.length;
    const unpaidRevenue = Math.max(0, grossRevenue - paidRevenue);
    const avgOrderValue = totalOrders > 0 ? grossRevenue / totalOrders : 0;

    const topItems = Array.from(itemStats.entries())
      .map(([name, st]) => ({ name, ...st }))
      .sort((a, b) => b.revenue - a.revenue)
      .slice(0, 10);

    const categories = Array.from(categoryStats.entries())
      .map(([category, st]) => ({ category, ...st }))
      .sort((a, b) => b.revenue - a.revenue);

    // v2.5.4 Purchase Tracking Matrix + Exact Ingredient Usage + P&L Snapshot
    const rawInvItems = (invItemsRes.data || []) as InventoryItem[];
    const invUsage = (invUsageRes.data || []) as InventoryUsage[];
    const invOrders: InventoryOrderWithItems[] = (invOrdersRes.data || []).map(
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

    const rawMatrix = buildSkuPriceHistoryMatrix(rawInvItems, invOrders);
    const { items: invItems, updates: priceUpdates, matrix: skuMatrix } =
      applyLatestPurchasePrices(rawInvItems, invOrders, rawMatrix);
    if (priceUpdates.length > 0) {
      persistLatestItemPricesServer(priceUpdates).catch(() => {});
    }
    const invById = new Map(invItems.map((i) => [i.id, i]));
    const topPriceHikes = skuMatrix.rows
      .filter((r) => r.isPriceHike || r.hasUnitAnomaly)
      .slice(0, 8)
      .map((r) => ({
        skuName: r.skuName,
        unitWeightLabel: r.unitWeightLabel,
        latestPrice: r.latestPrice,
        firstPrice: r.firstPrice,
        priceChangeVsFirstPct: r.priceChangeVsFirstPct,
        isPriceHike: r.isPriceHike,
        hasUnitAnomaly: r.hasUnitAnomaly,
        billsCount: r.bills.length,
      }));

    const usageReport = computeIngredientUsage(orders, invItems, invUsage);
    const topUsage = usageReport.items.slice(0, 8).map((u) => {
      const master = invById.get(u.inventoryItemId);
      const estimatedCost = master
        ? Math.round(calculatePacksAndCost(u.totalQuantity, u.unit, master).cost * 100) / 100
        : 0;
      return {
        ...u,
        category: master?.category || 'General',
        estimatedCost,
      };
    });

    const pnlReport = computePnlReport({
      range,
      monthKey,
      startDate,
      endDate,
      orders,
      inventoryItems: invItems,
      usages: invUsage,
      fixedCostsMap,
    });

    // Kitchen Shift & Reorder Pulse
    const kitchenCatalog =
      Array.isArray(kitchenCatalogCloud) && kitchenCatalogCloud.length > 0
        ? kitchenCatalogCloud
        : KITCHEN_SEED_CATALOG;
    const latestByItem = new Map<string, KitchenInventoryEntry>();
    for (const e of openingShift?.entries || []) latestByItem.set(e.itemId, e);
    for (const e of closingShift?.entries || []) latestByItem.set(e.itemId, e);
    const kitchenReorders = kitchenCatalog
      .filter((i) => i.isActive)
      .map((item) => {
        const entry = latestByItem.get(item.id);
        return entry ? computeReorderRecommendation(item, entry.totalUsableQty) : null;
      })
      .filter((x): x is NonNullable<typeof x> => x !== null);

    // Staff attendance summary for today
    const activeRoster = roster.filter((r) => r.isActive);
    const todayLogs = monthLogs.filter((l) => l.dateKey === todayKey);
    const onDutyStaff = todayLogs
      .filter((l) => l.checkInTimestamp > 0 && !l.checkOutTimestamp)
      .map((l) => ({ staffName: l.staffName, checkInTimestamp: l.checkInTimestamp }));
    const checkedOutStaff = todayLogs.filter(
      (l) => l.checkInTimestamp > 0 && l.checkOutTimestamp > 0
    ).length;

    return NextResponse.json({
      range,
      startMs,
      endMs,
      todayKey,
      kpis: {
        grossRevenue,
        paidRevenue,
        unpaidRevenue,
        cashRevenue,
        upiRevenue,
        totalOrders,
        unpaidCount,
        avgOrderValue,
        activeKdsCount,
        revenueDeltaPct,
        ordersDeltaPct,
      },
      pnlSnapshot: {
        netSales: pnlReport.netSales,
        recipeCogs: pnlReport.recipeCogs,
        grossProfit: pnlReport.grossProfit,
        grossMarginPct: pnlReport.grossMarginPct,
        proratedOverhead: pnlReport.proratedOverhead.total,
        netOperatingProfit: pnlReport.netOperatingProfit,
        netMarginPct: pnlReport.netMarginPct,
        breakEvenDailySales: pnlReport.breakEvenDailySales,
        currentDailySalesRunRate: pnlReport.currentDailySalesRunRate,
      },
      purchasePulse: {
        trackedSkus: skuMatrix.rows.length,
        priceHikeCount: skuMatrix.rows.filter((r) => r.isPriceHike).length,
        unitAnomalyCount: skuMatrix.rows.filter((r) => r.hasUnitAnomaly).length,
        pendingWhatsappCount: waPendingRes.count || 0,
        topPriceHikes,
      },
      topUsage,
      revenueSeries: Array.from(bucketRevenue.values()),
      topItems,
      categories,
      team: {
        activeRosterCount: activeRoster.length,
        onDutyCount: onDutyStaff.length,
        checkedOutCount: checkedOutStaff,
        onDutyStaff,
      },
      kitchen: {
        openingStatus: openingShift?.status || 'NOT_STARTED',
        openingBy: openingShift?.staffName || '',
        closingStatus: closingShift?.status || 'NOT_STARTED',
        closingBy: closingShift?.staffName || '',
        reorderCount: kitchenReorders.length,
        topReorders: kitchenReorders.slice(0, 5),
      },
      recentOrders: orders.slice(0, 15),
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load overview' },
      { status: 500 }
    );
  }
}
