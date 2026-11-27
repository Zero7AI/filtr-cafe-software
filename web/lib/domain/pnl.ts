import { calculatePacksAndCost } from './inventory';
import {
  DateRangePreset,
  formatMonthDisplay,
  getDaysInMonthKey,
  getIstMonthKey,
  getIstRangeBoundsMs,
} from './ist';
import {
  FixedCostRecord,
  FixedCostStaffMember,
  InventoryItem,
  InventoryUsage,
  OrderWithItems,
} from './types';

export function buildDefaultFixedCost(monthKey: string): FixedCostRecord {
  const staffMembers: FixedCostStaffMember[] = [
    { id: `staff_${monthKey}_1`, name: 'Barista 1', salary: 15000 },
    { id: `staff_${monthKey}_2`, name: 'Barista 2', salary: 15000 },
  ];
  return {
    monthKey,
    rent: 9000,
    electricity: 6000,
    staffSalaries: 30000,
    staffCount: 2,
    maidSalary: 2000,
    miscellaneous: 5000,
    notes: `Default fixed overheads for ${monthKey}`,
    staffMembers,
    updatedAt: 0,
  };
}

export function getFixedCostForMonthKey(
  monthKey: string,
  fixedCostsMap: Record<string, FixedCostRecord>
): { record: FixedCostRecord; isConfiguredInDb: boolean } {
  if (fixedCostsMap[monthKey]) {
    return { record: fixedCostsMap[monthKey], isConfiguredInDb: true };
  }
  const earlierKeys = Object.keys(fixedCostsMap)
    .filter((k) => k < monthKey)
    .sort();
  if (earlierKeys.length > 0) {
    const latestEarlier = fixedCostsMap[earlierKeys[earlierKeys.length - 1]];
    return {
      record: {
        ...latestEarlier,
        monthKey,
        notes: `Carried forward from ${latestEarlier.monthKey}`,
      },
      isConfiguredInDb: false,
    };
  }
  return { record: buildDefaultFixedCost(monthKey), isConfiguredInDb: false };
}

export interface ProratedFixedCostBreakdown {
  total: number;
  rent: number;
  electricity: number;
  staffSalaries: number;
  staffCount: number;
  maidSalary: number;
  miscellaneous: number;
  daysCount: number;
  isConfiguredInDb: boolean;
  monthKeyDescription: string;
  dailyBurden: number;
  staffMembers: FixedCostStaffMember[];
}

export interface TopIngredientCogs {
  itemId: number;
  name: string;
  cost: number;
  quantity: number;
  unit: string;
  usageDetail: string;
}

export interface PnlReport {
  range: DateRangePreset;
  startMs: number;
  endMs: number;
  ordersCount: number;
  grossSales: number;
  gstAmount: number;
  netSales: number;
  recipeCogs: number;
  grossProfit: number;
  grossMarginPct: number;
  proratedOverhead: ProratedFixedCostBreakdown;
  netOperatingProfit: number;
  netMarginPct: number;
  dailyFixedCost: number;
  breakEvenDailySales: number;
  currentDailySalesRunRate: number;
  breakEvenProgress: number;
  topIngredients: TopIngredientCogs[];
  unconfiguredRecipeOrdersCount: number;
}

export function computePnlReport(params: {
  range: DateRangePreset;
  monthKey?: string;
  startDate?: string;
  endDate?: string;
  orders: OrderWithItems[];
  inventoryItems: InventoryItem[];
  usages: InventoryUsage[];
  fixedCostsMap: Record<string, FixedCostRecord>;
}): PnlReport {
  const {
    range,
    monthKey = getIstMonthKey(),
    startDate,
    endDate,
    orders,
    inventoryItems,
    usages,
    fixedCostsMap,
  } = params;

  const { startMs, endMs, daysCount, dateKeys } = getIstRangeBoundsMs(range, {
    monthKey,
    startDate,
    endDate,
  });

  const filteredOrders = orders.filter(
    (o) => o.order.timestamp >= startMs && o.order.timestamp <= endMs
  );

  // Day-by-day proration across all dateKeys
  let totalRent = 0;
  let totalElec = 0;
  let totalStaff = 0;
  let totalMaid = 0;
  let totalMisc = 0;
  let staffCountLatest = 2;
  let staffMembersLatest: FixedCostStaffMember[] = [];
  const monthsSeen = new Set<string>();
  let anyConfigured = false;

  for (const dk of dateKeys) {
    const mk = dk.slice(0, 7);
    monthsSeen.add(mk);
    const dim = getDaysInMonthKey(mk);
    const { record, isConfiguredInDb } = getFixedCostForMonthKey(mk, fixedCostsMap);
    if (isConfiguredInDb) anyConfigured = true;
    totalRent += record.rent / dim;
    totalElec += record.electricity / dim;
    totalStaff += record.staffSalaries / dim;
    totalMaid += record.maidSalary / dim;
    totalMisc += record.miscellaneous / dim;
    staffCountLatest = record.staffCount;
    staffMembersLatest = record.staffMembers;
  }

  const totalOverhead =
    totalRent + totalElec + totalStaff + totalMaid + totalMisc;
  const safeDays = Math.max(1, daysCount);
  const dailyBurden = totalOverhead / safeDays;

  const monthList = Array.from(monthsSeen);
  const monthKeyDescription =
    range === 'MONTH'
      ? formatMonthDisplay(monthKey)
      : range === 'THIS_MONTH'
        ? `${monthKey} (MTD)`
        : monthList.join(' & ');

  const proratedOverhead: ProratedFixedCostBreakdown = {
    total: totalOverhead,
    rent: totalRent,
    electricity: totalElec,
    staffSalaries: totalStaff,
    staffCount: staffCountLatest,
    maidSalary: totalMaid,
    miscellaneous: totalMisc,
    daysCount: safeDays,
    isConfiguredInDb: anyConfigured,
    monthKeyDescription,
    dailyBurden,
    staffMembers: staffMembersLatest,
  };

  // Gross Sales & 5% inclusive GST
  const grossSales = filteredOrders.reduce(
    (sum, o) => sum + (o.order.total_amount || 0),
    0
  );
  const gstAmount = grossSales * (5.0 / 105.0);
  const netSales = grossSales - gstAmount;

  // Recipe COGS
  const rawUsageMap = new Map<number, { qty: number; unit: string }>();
  const rawCostMap = new Map<number, number>();
  let recipeCogs = 0;
  let unconfiguredRecipeOrdersCount = 0;

  for (const owi of filteredOrders) {
    let orderHadUnconfigured = false;
    for (const orderItem of owi.items) {
      const recipeUsages = usages.filter(
        (u) =>
          (orderItem.menu_item_id > 0 && u.menu_item_id === orderItem.menu_item_id) ||
          u.menu_item_name.trim().toLowerCase() ===
            orderItem.item_name.trim().toLowerCase()
      );

      if (recipeUsages.length > 0) {
        for (const u of recipeUsages) {
          const consumed = u.quantity_used * orderItem.quantity;
          const invItem = inventoryItems.find((i) => i.id === u.inventory_item_id);
          if (invItem) {
            const { cost } = calculatePacksAndCost(consumed, u.unit, invItem);
            recipeCogs += cost;
            rawCostMap.set(
              u.inventory_item_id,
              (rawCostMap.get(u.inventory_item_id) || 0) + cost
            );
            const prev = rawUsageMap.get(u.inventory_item_id);
            rawUsageMap.set(u.inventory_item_id, {
              qty: (prev?.qty || 0) + consumed,
              unit: u.unit,
            });
          }
        }
      } else {
        const directItem = inventoryItems.find(
          (i) =>
            i.name.trim().toLowerCase() ===
            orderItem.item_name.trim().toLowerCase()
        );
        if (directItem) {
          const consumed = orderItem.quantity;
          const { cost } = calculatePacksAndCost(
            consumed,
            directItem.unit,
            directItem
          );
          recipeCogs += cost;
          rawCostMap.set(
            directItem.id,
            (rawCostMap.get(directItem.id) || 0) + cost
          );
          const prev = rawUsageMap.get(directItem.id);
          rawUsageMap.set(directItem.id, {
            qty: (prev?.qty || 0) + consumed,
            unit: directItem.unit,
          });
        } else {
          // Unconfigured recipe fallback: 35% estimated COGS
          recipeCogs += orderItem.item_price * orderItem.quantity * 0.35;
          orderHadUnconfigured = true;
        }
      }
    }
    if (orderHadUnconfigured) unconfiguredRecipeOrdersCount++;
  }

  const topIngredients: TopIngredientCogs[] = Array.from(rawUsageMap.entries())
    .map(([invId, pair]) => {
      const invItem = inventoryItems.find((i) => i.id === invId);
      if (!invItem) return null;
      const cost = rawCostMap.get(invId) || 0;
      const { packs } = calculatePacksAndCost(pair.qty, pair.unit, invItem);
      const u1 = pair.unit.trim().toLowerCase();
      const u2 = invItem.unit.trim().toLowerCase();
      const qtyFmt =
        pair.qty % 1 === 0 ? String(pair.qty) : pair.qty.toFixed(1);
      let usageDetail = `${qtyFmt} ${pair.unit} (${packs.toFixed(1)} ${invItem.unit})`;
      if (u1 === u2) {
        usageDetail = `${qtyFmt} ${pair.unit}`;
      } else if (u1 === 'g' && (u2 === 'kg' || u2 === 'kgs' || u2 === 'kilogram')) {
        usageDetail = `${qtyFmt} g (${(pair.qty / 1000).toFixed(2)} kg)`;
      } else if (u1 === 'ml' && (u2 === 'l' || u2 === 'liter' || u2 === 'litre' || u2 === 'liters')) {
        usageDetail = `${qtyFmt} ml (${(pair.qty / 1000).toFixed(2)} L)`;
      }
      return {
        itemId: invId,
        name: invItem.name,
        cost,
        quantity: pair.qty,
        unit: pair.unit,
        usageDetail,
      };
    })
    .filter((x): x is TopIngredientCogs => x !== null)
    .sort((a, b) => b.cost - a.cost)
    .slice(0, 8);

  const grossProfit = netSales - recipeCogs;
  const grossMarginPct = netSales > 0 ? (grossProfit / netSales) * 100 : 0;

  const netOperatingProfit = grossProfit - proratedOverhead.total;
  const netMarginPct =
    netSales > 0 ? (netOperatingProfit / netSales) * 100 : 0;

  const dailyFixedCost = proratedOverhead.dailyBurden;
  const grossProfitRatio = grossSales > 0 ? grossProfit / grossSales : 0.65;
  const breakEvenDailySales =
    grossProfitRatio > 0.05
      ? dailyFixedCost / grossProfitRatio
      : dailyFixedCost / 0.65;
  const currentDailySalesRunRate = grossSales / safeDays;
  const breakEvenProgress =
    breakEvenDailySales > 0
      ? Math.min(2, Math.max(0, currentDailySalesRunRate / breakEvenDailySales))
      : 0;

  return {
    range,
    startMs,
    endMs,
    ordersCount: filteredOrders.length,
    grossSales,
    gstAmount,
    netSales,
    recipeCogs,
    grossProfit,
    grossMarginPct,
    proratedOverhead,
    netOperatingProfit,
    netMarginPct,
    dailyFixedCost,
    breakEvenDailySales,
    currentDailySalesRunRate,
    breakEvenProgress,
    topIngredients,
    unconfiguredRecipeOrdersCount,
  };
}
