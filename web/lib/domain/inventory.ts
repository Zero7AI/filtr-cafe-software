import {
  InventoryItem,
  InventoryOrderItem,
  InventoryOrderWithItems,
  InventoryUsage,
  OrderWithItems,
} from './types';

const CONTAINER_UNITS = new Set([
  'packet', 'packets', 'pack', 'packs', 'can', 'cans', 'tin', 'tins',
  'box', 'boxes', 'bottle', 'bottles', 'btl', 'btls', 'jar', 'jars',
  'pcs', 'pc', 'piece', 'pieces', 'nos', 'no', 'unit', 'units',
  'case', 'cases', 'bag', 'bags', 'pouch', 'pouches', 'carton', 'cartons', 'bill',
]);
const KG_UNITS = new Set(['kg', 'kgs', 'kilo', 'kilos', 'kilogram', 'kilograms']);
const G_UNITS = new Set(['g', 'gm', 'gms', 'gram', 'grams']);
const LITER_UNITS = new Set(['l', 'lt', 'ltr', 'ltrs', 'liter', 'liters', 'litre', 'litres']);
const ML_UNITS = new Set(['ml', 'mls', 'milliliter', 'milliliters', 'millilitre', 'millilitres']);
const PCS_UNITS = new Set(['pcs', 'pc', 'piece', 'pieces']);

const NON_INGREDIENT_HARDWARE = new Set([
  'stand', 'dispenser', 'cover', 'machine', 'basket', 'repair', 'repairs',
  'rent', 'electricity', 'wifi', 'wi-fi', 'rapido', 'courier', 'paint',
  'table', 'chair', 'shelf', 'rack', 'stool', 'signage', 'board', 'banner',
  'delivery', 'transport', 'freight',
]);

const GENERIC_SINGLE_TOKENS = new Set([
  'water', 'ice', 'milk', 'sugar', 'cup', 'cups', 'sauce', 'syrup', 'base', 'powder',
]);

const SKU_ALIASES: Array<[RegExp, string]> = [
  [/\b(tht|tnt|tkt)\s*(vanilla|vancia)\b/gi, 'vanilla base ice cream'],
  [/\bgallon\s+vanilla\b/gi, 'vanilla base ice cream'],
  [/\bvanilla\s*5\s*(l|lt|ltr)\b/gi, 'vanilla base ice cream'],
  [/\bperi\s*[-]?\s*peri\b/gi, 'peri peri seasoning'],
  [/\b(pama|pamac)\b/gi, 'caramel sauce pamac'],
  [/\bdoodh\b/gi, 'milk'],
  [/\bcheeni\b/gi, 'sugar'],
  [/\bmonin\s+caramel(\s+syrup)?\b/gi, 'monin caramel syrup'],
];

const PACK_SUFFIX_REGEX =
  /\b\d+(?:\.\d+)?\s*(?:kgs?|kilograms?|gms?|grams?|g|ltrs?|litres?|liters?|lt|l|mls?|ml|pcs?|pieces?|nos?|pkts?|packs?|packets?|btls?|bottles?|tins?|cans?|jars?|boxes?)\b/gi;

function round2(v: number): number {
  return Math.round(v * 100) / 100;
}

export function formatCompactNumber(v: number): string {
  const r = round2(v);
  return Math.abs(r - Math.round(r)) < 0.005 ? String(Math.round(r)) : r.toFixed(2);
}

export function isLikelyNonIngredientHardware(rawName: string): boolean {
  const tokens = (rawName || '')
    .trim()
    .toLowerCase()
    .split(/[^a-z0-9-]+/)
    .filter(Boolean);
  return tokens.some((t) => NON_INGREDIENT_HARDWARE.has(t));
}

export function normalizeSkuMatchText(raw: string): string {
  let normalized = (raw || '').trim().toLowerCase();
  for (const [pattern, replacement] of SKU_ALIASES) {
    normalized = normalized.replace(pattern, replacement);
  }
  return normalized
    .replace(PACK_SUFFIX_REGEX, ' ')
    .replace(/[^a-z0-9\s]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function normalizeUnit(rawUnit: string): string {
  const u = (rawUnit || '').trim().toLowerCase();
  if (KG_UNITS.has(u)) return 'kg';
  if (LITER_UNITS.has(u)) return 'L';
  if (G_UNITS.has(u)) return 'g';
  if (ML_UNITS.has(u)) return 'ml';
  if (['pack', 'packs', 'pkt', 'pkts', 'packet', 'packets', 'pouch', 'pouches', 'box', 'boxes', 'bag', 'bags', 'tin', 'tins', 'can', 'cans', 'case', 'carton', 'crate'].includes(u)) return 'pack';
  return 'pcs';
}

export function calculatePacksAndCost(
  quantity: number,
  consumedUnit: string,
  item: InventoryItem
): { packs: number; cost: number } {
  if (quantity <= 0) return { packs: 0, cost: 0 };

  const u1 = (consumedUnit || '').trim().toLowerCase();
  const u2 = (item.unit || '').trim().toLowerCase();
  const packSize = item.standard_quantity > 0 ? item.standard_quantity : 1.0;
  const packUnits = ['packet', 'packets', 'pack', 'packs', 'can', 'cans', 'tin', 'tins', 'box', 'boxes', 'bottle', 'bottles'];
  const isPackItem = packUnits.includes(u2);

  let packs: number;
  if (packUnits.includes(u1)) {
    packs = quantity;
  } else if (
    u1 === u2 ||
    (KG_UNITS.has(u1) && KG_UNITS.has(u2)) ||
    (G_UNITS.has(u1) && G_UNITS.has(u2)) ||
    (LITER_UNITS.has(u1) && LITER_UNITS.has(u2)) ||
    (ML_UNITS.has(u1) && ML_UNITS.has(u2)) ||
    (KG_UNITS.has(u1) && LITER_UNITS.has(u2)) ||
    (LITER_UNITS.has(u1) && KG_UNITS.has(u2)) ||
    (G_UNITS.has(u1) && ML_UNITS.has(u2)) ||
    (ML_UNITS.has(u1) && G_UNITS.has(u2)) ||
    (PCS_UNITS.has(u1) && PCS_UNITS.has(u2))
  ) {
    packs = quantity / packSize;
  } else if (
    (G_UNITS.has(u1) && (KG_UNITS.has(u2) || LITER_UNITS.has(u2))) ||
    (ML_UNITS.has(u1) && (KG_UNITS.has(u2) || LITER_UNITS.has(u2)))
  ) {
    packs = quantity / 1000 / packSize;
  } else if (
    ((KG_UNITS.has(u1) || LITER_UNITS.has(u1)) && (G_UNITS.has(u2) || ML_UNITS.has(u2)))
  ) {
    packs = (quantity * 1000) / packSize;
  } else if (isPackItem && (KG_UNITS.has(u1) || LITER_UNITS.has(u1))) {
    packs = packSize > 10.0 ? (quantity * 1000) / packSize : quantity / packSize;
  } else if (isPackItem && (G_UNITS.has(u1) || ML_UNITS.has(u1))) {
    packs = packSize > 10.0 ? quantity / packSize : quantity / 1000 / packSize;
  } else {
    packs = quantity / packSize;
  }

  return { packs, cost: packs * (item.default_price || 0) };
}

export function matchCatalogItem(
  rawName: string,
  items: InventoryItem[]
): InventoryItem | undefined {
  const clean = (rawName || '').trim();
  if (!clean || items.length === 0) return undefined;

  const exact = items.find((i) => i.name.trim().toLowerCase() === clean.toLowerCase());
  if (exact) return exact;

  if (isLikelyNonIngredientHardware(clean)) return undefined;

  const cleanNorm = normalizeSkuMatchText(clean);
  if (!cleanNorm) return undefined;

  const cleanTokens = cleanNorm.split(' ').filter((t) => t.length >= 2);
  const meaningfulClean = new Set(cleanTokens.filter((t) => t.length >= 3));

  let best: { item: InventoryItem; score: number } | undefined;
  for (const candidate of items) {
    const candNorm = normalizeSkuMatchText(candidate.name);
    if (!candNorm) continue;
    if (candNorm === cleanNorm) return candidate;

    const candTokens = candNorm.split(' ').filter((t) => t.length >= 2);
    const meaningfulCand = new Set(candTokens.filter((t) => t.length >= 3));
    const overlap = [...meaningfulClean].filter((t) => meaningfulCand.has(t));

    const minLen = Math.max(1, Math.min(meaningfulClean.size, meaningfulCand.size));
    const maxLen = Math.max(1, Math.max(meaningfulClean.size, meaningfulCand.size));
    const shorterCoverage = overlap.length / minLen;
    const unionSize = Math.max(1, meaningfulClean.size + meaningfulCand.size - overlap.length);
    const jaccard = overlap.length / unionSize;

    const isSingleGenericOverlap =
      overlap.length === 1 &&
      GENERIC_SINGLE_TOKENS.has(overlap[0]) &&
      meaningfulClean.size >= 4;

    let score = 0;
    if (isSingleGenericOverlap) {
      score = 0;
    } else if (
      (candNorm.includes(cleanNorm) || cleanNorm.includes(candNorm)) &&
      (Math.min(cleanTokens.length, candTokens.length) >= 2 || maxLen <= 3)
    ) {
      score = 120 + overlap.length * 5;
    } else if (shorterCoverage >= 0.6 && jaccard >= 0.35) {
      score = 60 + shorterCoverage * 30 + jaccard * 20;
    }

    if (score > 0 && (!best || score > best.score)) {
      best = { item: candidate, score };
    }
  }

  return best?.item;
}

export function resolvePackUnitPrice(
  orderItem: InventoryOrderItem,
  masterItem?: InventoryItem
): number {
  if (orderItem.quantity <= 0) return round2(Math.max(0, orderItem.unit_price));
  const effectiveTotal =
    orderItem.subtotal > 0
      ? orderItem.subtotal
      : orderItem.quantity * orderItem.unit_price;
  if (effectiveTotal <= 0) return 0;

  const rawPerBilledUnit = effectiveTotal / orderItem.quantity;
  if (!masterItem) return round2(rawPerBilledUnit);

  const bUnit = (orderItem.unit || '').trim().toLowerCase();
  const mUnit = (masterItem.unit || '').trim().toLowerCase();
  const packSize = masterItem.standard_quantity > 0 ? masterItem.standard_quantity : 1.0;

  const bIsWeight = G_UNITS.has(bUnit) || KG_UNITS.has(bUnit);
  const mIsWeight = G_UNITS.has(mUnit) || KG_UNITS.has(mUnit);
  const bIsVol = ML_UNITS.has(bUnit) || LITER_UNITS.has(bUnit);
  const mIsVol = ML_UNITS.has(mUnit) || LITER_UNITS.has(mUnit);
  const bIsCount = bUnit === '' || CONTAINER_UNITS.has(bUnit);
  const mIsPcs = PCS_UNITS.has(mUnit);

  if (bIsWeight && mIsWeight) {
    const converted =
      G_UNITS.has(bUnit) && KG_UNITS.has(mUnit)
        ? orderItem.quantity / 1000
        : KG_UNITS.has(bUnit) && G_UNITS.has(mUnit)
          ? orderItem.quantity * 1000
          : orderItem.quantity;
    const packs = converted / packSize;
    return packs > 0 ? round2(effectiveTotal / packs) : round2(rawPerBilledUnit);
  }

  if (bIsVol && mIsVol) {
    const converted =
      ML_UNITS.has(bUnit) && LITER_UNITS.has(mUnit)
        ? orderItem.quantity / 1000
        : LITER_UNITS.has(bUnit) && ML_UNITS.has(mUnit)
          ? orderItem.quantity * 1000
          : orderItem.quantity;
    const packs = converted / packSize;
    return packs > 0 ? round2(effectiveTotal / packs) : round2(rawPerBilledUnit);
  }

  // Case C: Container/pcs bill unit mapped to weight/volume pack (e.g. Peri Peri 1 pcs = ₹140)
  if (bIsCount && (mIsWeight || mIsVol)) {
    return round2(rawPerBilledUnit);
  }

  // Case D: Count/pcs bill unit mapped to multi-pcs pack (e.g. Water 1 pcs = ₹130)
  if (bIsCount && mIsPcs && packSize > 1) {
    const defaultPackRate = masterItem.default_price || 0;
    const explicitContainer = ['pack', 'packs', 'pkt', 'pkts', 'box', 'boxes', 'case', 'cases', 'carton', 'cartons'].includes(bUnit);
    if (!explicitContainer && defaultPackRate > 0 && rawPerBilledUnit < defaultPackRate * 0.4) {
      const packs = orderItem.quantity / packSize;
      if (packs > 0) return round2(effectiveTotal / packs);
    }
    return round2(rawPerBilledUnit);
  }

  return round2(rawPerBilledUnit);
}

export function formatUnitWeightLabel(masterItem: InventoryItem | undefined, fallbackUnit: string): string {
  if (!masterItem) {
    return `per ${normalizeUnit(fallbackUnit || 'pcs')}`;
  }
  const mUnit = (masterItem.unit || '').trim().toLowerCase();
  const qty = masterItem.standard_quantity > 0 ? masterItem.standard_quantity : 1.0;
  const qtyStr = formatCompactNumber(qty);

  if (KG_UNITS.has(mUnit)) return Math.abs(qty - 1) < 0.01 ? 'per kg' : `per ${qtyStr}kg pack`;
  if (G_UNITS.has(mUnit)) return Math.abs(qty - 1000) < 0.01 ? 'per kg' : `per ${qtyStr}g pack`;
  if (LITER_UNITS.has(mUnit)) return Math.abs(qty - 1) < 0.01 ? 'per L' : `per ${qtyStr}L pack`;
  if (ML_UNITS.has(mUnit)) return Math.abs(qty - 1000) < 0.01 ? 'per L' : `per ${qtyStr}ml pack`;
  if (PCS_UNITS.has(mUnit)) return Math.abs(qty - 1) < 0.01 ? 'per pcs' : `per ${qtyStr}-pack`;
  return Math.abs(qty - 1) < 0.01 ? `per ${masterItem.unit}` : `per ${qtyStr} ${masterItem.unit}`;
}

export interface SkuBillPoint {
  billIndex: number;
  price: number;
  timestamp: number;
  dateLabel: string;
  orderId: string;
  supplierName: string;
  isUnitAnomaly: boolean;
  anomalyReason: string;
}

export interface SkuPriceHistoryRow {
  skuName: string;
  unitWeightLabel: string;
  latestPrice: number;
  firstPrice: number;
  priceChangeVsFirstPct: number | null;
  isPriceHike: boolean;
  hasUnitAnomaly: boolean;
  bills: SkuBillPoint[];
}

export interface SkuPriceHistoryMatrix {
  maxBillColumns: number;
  rows: SkuPriceHistoryRow[];
}

export interface BillLedgerRow {
  orderId: string;
  timestamp: number;
  dateFormatted: string;
  supplierName: string;
  rawItemName: string;
  matchedSkuName: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  subtotal: number;
  normalizedPrice: number;
  statusLabel: string;
  notes: string;
}

export function formatBillCell(bill: SkuBillPoint, includeDateInCells: boolean): string {
  const compact = formatCompactNumber(bill.price);
  if (bill.isUnitAnomaly) {
    return includeDateInCells ? `CHECK UNIT (${compact}, ${bill.dateLabel})` : 'CHECK UNIT';
  }
  return includeDateInCells ? `${compact} (${bill.dateLabel})` : compact;
}

const SHORT_MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function formatShortIstDate(ms: number): string {
  const d = new Date(ms + 5.5 * 60 * 60 * 1000);
  const day = String(d.getUTCDate()).padStart(2, '0');
  const mon = SHORT_MONTHS[d.getUTCMonth()] || 'Jan';
  return `${day} ${mon}`;
}

function formatFullIstDate(ms: number): string {
  return new Date(ms).toLocaleString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function titleCaseClean(s: string): string {
  return s
    .split(' ')
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}

export function buildSkuPriceHistoryMatrix(
  inventoryItems: InventoryItem[],
  inventoryOrders: InventoryOrderWithItems[]
): SkuPriceHistoryMatrix {
  const sortedOrders = [...inventoryOrders].sort(
    (a, b) => a.order.timestamp - b.order.timestamp
  );

  type RawEntry = {
    rate: number;
    timestamp: number;
    dateLabel: string;
    orderId: string;
    supplier: string;
    master?: InventoryItem;
    fallbackUnit: string;
    canonicalName: string;
  };

  const entriesByKey = new Map<string, RawEntry[]>();

  for (const owi of sortedOrders) {
    const ts = owi.order.timestamp > 0 ? owi.order.timestamp : Date.now();
    const dateLabel = formatShortIstDate(ts);
    for (const line of owi.items) {
      if (!line.item_name.trim() || line.quantity <= 0) continue;
      if (isLikelyNonIngredientHardware(line.item_name)) continue;

      const matched = matchCatalogItem(line.item_name, inventoryItems);
      const canonicalName = matched
        ? matched.name
        : titleCaseClean(normalizeSkuMatchText(line.item_name)) || line.item_name.trim();
      const key = canonicalName.trim().toLowerCase();
      const rate = resolvePackUnitPrice(line, matched);
      if (rate <= 0) continue;

      const arr = entriesByKey.get(key) || [];
      arr.push({
        rate,
        timestamp: ts,
        dateLabel,
        orderId: owi.order.order_id,
        supplier: owi.order.supplier_name || 'Supplier',
        master: matched,
        fallbackUnit: line.unit || 'pcs',
        canonicalName,
      });
      entriesByKey.set(key, arr);
    }
  }

  const rows: SkuPriceHistoryRow[] = [];
  let maxBillColumns = 0;

  for (const rawList of entriesByKey.values()) {
    if (rawList.length === 0) continue;
    const sample = rawList[0];
    const billPoints: SkuBillPoint[] = [];
    let lastValidPrice: number | null = null;
    let hasUnitAnomaly = false;

    rawList.forEach((entry, idx) => {
      const isAnomaly =
        lastValidPrice !== null &&
        (entry.rate > lastValidPrice * 3.0 || entry.rate < lastValidPrice * 0.3);
      if (isAnomaly) {
        hasUnitAnomaly = true;
      } else {
        lastValidPrice = entry.rate;
      }
      billPoints.push({
        billIndex: idx + 1,
        price: entry.rate,
        timestamp: entry.timestamp,
        dateLabel: entry.dateLabel,
        orderId: entry.orderId,
        supplierName: entry.supplier,
        isUnitAnomaly: isAnomaly,
        anomalyReason: isAnomaly ? 'CHECK UNIT' : '',
      });
    });

    if (billPoints.length > maxBillColumns) maxBillColumns = billPoints.length;

    const validBills = billPoints.filter((b) => !b.isUnitAnomaly);
    const firstValid = validBills.length > 0 ? validBills[0].price : billPoints[0].price;
    const latestValid =
      validBills.length > 0
        ? validBills[validBills.length - 1].price
        : billPoints[billPoints.length - 1].price;
    const pctChange =
      validBills.length >= 2 && firstValid > 0
        ? round2(((latestValid - firstValid) / firstValid) * 100)
        : null;

    rows.push({
      skuName: sample.canonicalName,
      unitWeightLabel: formatUnitWeightLabel(sample.master, sample.fallbackUnit),
      latestPrice: round2(latestValid),
      firstPrice: round2(firstValid),
      priceChangeVsFirstPct: pctChange,
      isPriceHike: (pctChange ?? 0) > 0,
      hasUnitAnomaly,
      bills: billPoints,
    });
  }

  rows.sort((a, b) => {
    const aTier = a.isPriceHike ? 0 : a.hasUnitAnomaly ? 1 : 2;
    const bTier = b.isPriceHike ? 0 : b.hasUnitAnomaly ? 1 : 2;
    if (aTier !== bTier) return aTier - bTier;
    const pctDiff = (b.priceChangeVsFirstPct ?? 0) - (a.priceChangeVsFirstPct ?? 0);
    if (Math.abs(pctDiff) > 0.001) return pctDiff;
    return a.skuName.localeCompare(b.skuName, undefined, { sensitivity: 'base' });
  });

  return { maxBillColumns, rows };
}

export function buildBillLedger(
  inventoryItems: InventoryItem[],
  inventoryOrders: InventoryOrderWithItems[]
): BillLedgerRow[] {
  const sortedOrders = [...inventoryOrders].sort(
    (a, b) => a.order.timestamp - b.order.timestamp
  );
  const rows: BillLedgerRow[] = [];
  const lastValidByKey = new Map<string, number>();

  for (const owi of sortedOrders) {
    const ts = owi.order.timestamp > 0 ? owi.order.timestamp : Date.now();
    const dateFormatted = formatFullIstDate(ts);
    for (const line of owi.items) {
      if (!line.item_name.trim() || line.quantity <= 0) continue;
      const isHardware = isLikelyNonIngredientHardware(line.item_name);
      const matched = isHardware ? undefined : matchCatalogItem(line.item_name, inventoryItems);
      const matchedName = matched
        ? matched.name
        : isHardware
          ? 'Non-Ingredient / Hardware'
          : line.item_name.trim();
      const rate = resolvePackUnitPrice(line, matched);
      const key = matchedName.toLowerCase();
      const prev = lastValidByKey.get(key);
      const isAnomaly =
        !isHardware && prev !== undefined && rate > 0 && (rate > prev * 3.0 || rate < prev * 0.3);
      if (!isAnomaly && !isHardware && rate > 0) {
        lastValidByKey.set(key, rate);
      }

      rows.push({
        orderId: owi.order.order_id,
        timestamp: ts,
        dateFormatted,
        supplierName: owi.order.supplier_name || 'Supplier',
        rawItemName: line.item_name,
        matchedSkuName: matchedName,
        quantity: line.quantity,
        unit: line.unit || 'pcs',
        unitPrice: line.unit_price,
        subtotal: line.subtotal,
        normalizedPrice: rate,
        statusLabel: isHardware ? 'HARDWARE / EXCLUDED' : isAnomaly ? 'CHECK UNIT' : 'OK',
        notes: owi.order.notes || '',
      });
    }
  }
  return rows;
}

function escapeCsv(raw: string): string {
  const cleaned = String(raw ?? '').replace(/[\r\n]+/g, ' ');
  if (cleaned.includes(',') || cleaned.includes('"')) {
    return `"${cleaned.replace(/"/g, '""')}"`;
  }
  return cleaned;
}

export function buildSkuPriceHistoryCsv(
  matrix: SkuPriceHistoryMatrix,
  includeDateInCells: boolean = true
): string {
  const headers = ['SKU Name', 'Unit / Weight', 'Latest Price (INR)', 'Price Change vs First (%)'];
  for (let b = 1; b <= Math.max(1, matrix.maxBillColumns); b++) {
    headers.push(`Bill ${b}`);
  }
  const lines: string[] = [headers.map(escapeCsv).join(',')];

  for (const row of matrix.rows) {
    const pctStr =
      row.priceChangeVsFirstPct !== null
        ? `${row.priceChangeVsFirstPct > 0 ? '+' : ''}${row.priceChangeVsFirstPct.toFixed(1)}%`
        : '0.0%';
    const cols = [
      escapeCsv(row.skuName),
      escapeCsv(row.unitWeightLabel),
      formatCompactNumber(row.latestPrice),
      escapeCsv(pctStr),
    ];
    for (let i = 0; i < Math.max(1, matrix.maxBillColumns); i++) {
      const b = row.bills[i];
      cols.push(escapeCsv(b ? formatBillCell(b, includeDateInCells) : ''));
    }
    lines.push(cols.join(','));
  }

  return '\uFEFF' + lines.join('\r\n');
}

export interface CleanIngredientUsageRow {
  inventoryItemId: number;
  itemName: string;
  totalQuantity: number;
  unit: string;
  exactUsageLabel: string;
}

export interface CleanInventoryUsageReport {
  ordersCount: number;
  items: CleanIngredientUsageRow[];
}

export function computeIngredientUsage(
  orders: OrderWithItems[],
  inventoryItems: InventoryItem[],
  usages: InventoryUsage[]
): CleanInventoryUsageReport {
  const activeOrders = orders.filter((o) => o.order.sync_status !== 'CANCELLED');
  const invById = new Map<number, InventoryItem>();
  inventoryItems.forEach((i) => invById.set(i.id, i));

  const toBase = (qty: number, unitRaw: string): { kind: string; baseQty: number; unit: string } => {
    const u = (unitRaw || '').trim().toLowerCase();
    if (KG_UNITS.has(u)) return { kind: 'WEIGHT', baseQty: qty * 1000, unit: 'g' };
    if (G_UNITS.has(u)) return { kind: 'WEIGHT', baseQty: qty, unit: 'g' };
    if (LITER_UNITS.has(u)) return { kind: 'VOLUME', baseQty: qty * 1000, unit: 'ml' };
    if (ML_UNITS.has(u)) return { kind: 'VOLUME', baseQty: qty, unit: 'ml' };
    return { kind: `COUNT:${u || 'pcs'}`, baseQty: qty, unit: u || 'pcs' };
  };

  const fromBase = (baseQty: number, kind: string, fallbackUnit: string): { qty: number; unit: string } => {
    if (kind === 'WEIGHT') {
      return baseQty >= 1000 ? { qty: baseQty / 1000, unit: 'kg' } : { qty: baseQty, unit: 'g' };
    }
    if (kind === 'VOLUME') {
      return baseQty >= 1000 ? { qty: baseQty / 1000, unit: 'L' } : { qty: baseQty, unit: 'ml' };
    }
    return { qty: baseQty, unit: fallbackUnit };
  };

  const accByInvId = new Map<
    number,
    { baseQty: number; kind: string; fallbackUnit: string; fallbackName: string }
  >();

  for (const owi of activeOrders) {
    for (const orderItem of owi.items) {
      if (orderItem.quantity <= 0) continue;
      const recipeRows = usages.filter(
        (u) =>
          (orderItem.menu_item_id > 0 && u.menu_item_id === orderItem.menu_item_id) ||
          u.menu_item_name.trim().toLowerCase() === orderItem.item_name.trim().toLowerCase()
      );

      if (recipeRows.length > 0) {
        for (const recipe of recipeRows) {
          const qty = recipe.quantity_used * orderItem.quantity;
          if (qty <= 0) continue;
          const b = toBase(qty, recipe.unit);
          const existing = accByInvId.get(recipe.inventory_item_id);
          if (!existing) {
            accByInvId.set(recipe.inventory_item_id, {
              baseQty: b.baseQty,
              kind: b.kind,
              fallbackUnit: b.unit,
              fallbackName: recipe.inventory_item_name,
            });
          } else {
            existing.baseQty += existing.kind === b.kind ? b.baseQty : qty;
          }
        }
      } else {
        const directInv = inventoryItems.find(
          (i) => i.name.trim().toLowerCase() === orderItem.item_name.trim().toLowerCase()
        );
        if (directInv) {
          const b = toBase(orderItem.quantity, directInv.unit);
          const existing = accByInvId.get(directInv.id);
          if (!existing) {
            accByInvId.set(directInv.id, {
              baseQty: b.baseQty,
              kind: b.kind,
              fallbackUnit: b.unit,
              fallbackName: directInv.name,
            });
          } else {
            existing.baseQty += b.baseQty;
          }
        }
      }
    }
  }

  const rows: CleanIngredientUsageRow[] = [];
  for (const [invId, acc] of accByInvId.entries()) {
    if (acc.baseQty <= 0) continue;
    const itemName = invById.get(invId)?.name || acc.fallbackName;
    const display = fromBase(acc.baseQty, acc.kind, acc.fallbackUnit);
    const numStr = formatCompactNumber(display.qty);
    rows.push({
      inventoryItemId: invId,
      itemName,
      totalQuantity: round2(display.qty),
      unit: display.unit,
      exactUsageLabel: `${numStr} ${display.unit}`,
    });
  }

  rows.sort((a, b) => a.itemName.localeCompare(b.itemName, undefined, { sensitivity: 'base' }));
  return { ordersCount: activeOrders.length, items: rows };
}

// Legacy compatibility types/exports so other routes/analytics stay intact
export type PriceTrend = 'INCREASED' | 'DECREASED' | 'UNCHANGED' | 'FIRST_ENTRY';
export type StockAlertLevel = 'OUT_OF_STOCK' | 'CRITICAL_REORDER' | 'LOW_STOCK' | 'HEALTHY';
export type PriceAlertLevel = 'HIGH_HIKE' | 'MINOR_UP' | 'PRICE_DROP' | 'STABLE';

export interface IngredientPriceSummary {
  itemId: number;
  itemName: string;
  category: string;
  unit: string;
  packSize: number;
  currentStock: number;
  latestRate: number;
  previousRate: number | null;
  priceDelta: number;
  priceChangePercent: number;
  trend: PriceTrend;
  alertLabel: string;
  lastSupplier: string;
  lastPurchasedTimestamp: number;
  totalPurchasesCount: number;
}

export interface InventoryAlertRow {
  itemId: number;
  itemName: string;
  category: string;
  unit: string;
  packSize: number;
  currentStock: number;
  estimatedOrdersLeft: number | null;
  stockAlertLevel: StockAlertLevel;
  stockAlertLabel: string;
  suggestedOrderPacks: number;
  suggestedOrderLabel: string;
  latestRate: number;
  previousRate: number | null;
  priceDelta: number;
  priceChangePercent: number;
  priceAlertLevel: PriceAlertLevel;
  priceAlertLabel: string;
  lastSupplier: string;
  lastPurchasedTimestamp: number;
}

export interface IngredientPriceMatrixRow {
  itemName: string;
  category: string;
  unit: string;
  currentStock: number;
  stockAlertLevel: StockAlertLevel;
  stockAlertLabel: string;
  suggestedOrderLabel: string;
  latestRate: number;
  priceChangePercent: number;
  priceAlertLevel: PriceAlertLevel;
  priceAlertLabel: string;
  trend: PriceTrend;
  ratesByDate: Record<string, number>;
}

export interface IngredientPriceMatrix {
  dates: string[];
  rows: IngredientPriceMatrixRow[];
}

export function buildIngredientPricingSummaries(
  inventoryItems: InventoryItem[],
  inventoryOrders: InventoryOrderWithItems[]
): IngredientPriceSummary[] {
  const skuMatrix = buildSkuPriceHistoryMatrix(inventoryItems, inventoryOrders);
  return skuMatrix.rows.map((r) => {
    const validBills = r.bills.filter((b) => !b.isUnitAnomaly);
    const prevRate = validBills.length >= 2 ? validBills[0].price : null;
    const delta = prevRate !== null ? round2(r.latestPrice - prevRate) : 0;
    const pct = r.priceChangeVsFirstPct ?? 0;
    const trend: PriceTrend =
      prevRate === null
        ? 'FIRST_ENTRY'
        : delta > 0.01
          ? 'INCREASED'
          : delta < -0.01
            ? 'DECREASED'
            : 'UNCHANGED';
    return {
      itemId: 0,
      itemName: r.skuName,
      category: 'Purchase History',
      unit: r.unitWeightLabel,
      packSize: 1,
      currentStock: 0,
      latestRate: r.latestPrice,
      previousRate: prevRate,
      priceDelta: delta,
      priceChangePercent: pct,
      trend,
      alertLabel:
        trend === 'INCREASED'
          ? `+${pct.toFixed(1)}%`
          : trend === 'DECREASED'
            ? `${pct.toFixed(1)}%`
            : 'SAME',
      lastSupplier: r.bills[r.bills.length - 1]?.supplierName || '',
      lastPurchasedTimestamp: r.bills[r.bills.length - 1]?.timestamp || 0,
      totalPurchasesCount: r.bills.length,
    };
  });
}

export function evaluateInventoryAlerts(
  _inventoryItems: InventoryItem[] = [],
  _inventoryOrders: InventoryOrderWithItems[] = [],
  _usages: InventoryUsage[] = [],
  _priceAlertThresholdPct: number = 5.0
): InventoryAlertRow[] {
  return [];
}

export function buildIngredientPriceMatrix(
  _inventoryItems: InventoryItem[] = [],
  _inventoryOrders: InventoryOrderWithItems[] = [],
  _usages: InventoryUsage[] = [],
  _priceAlertThresholdPct: number = 5.0
): IngredientPriceMatrix {
  return { dates: [], rows: [] };
}

export function buildIngredientPricingCsv(
  inventoryItems: InventoryItem[],
  inventoryOrders: InventoryOrderWithItems[],
  _usages: InventoryUsage[] = [],
  _priceAlertThresholdPct: number = 5.0
): string {
  return buildSkuPriceHistoryCsv(buildSkuPriceHistoryMatrix(inventoryItems, inventoryOrders), true);
}
