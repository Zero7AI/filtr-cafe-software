import {
  KitchenCountingModeId,
  KitchenInventoryEntry,
  KitchenInventoryItem,
  KitchenSectionId,
  KitchenStockStatus,
} from './types';

export interface KitchenSectionMeta {
  id: KitchenSectionId;
  title: string;
  shortTitle: string;
  iconEmoji: string;
  orderIndex: number;
}

export const KITCHEN_SECTIONS: KitchenSectionMeta[] = [
  {
    id: 'DEEP_FREEZER',
    title: 'Deep Freezer',
    shortTitle: 'Deep Freezer',
    iconEmoji: '❄️',
    orderIndex: 1,
  },
  {
    id: 'REFRIGERATOR_FREEZER',
    title: 'Refrigerator / Freezer',
    shortTitle: 'Fridge',
    iconEmoji: '🧊',
    orderIndex: 2,
  },
  {
    id: 'OUTSIDE_FREEZER',
    title: 'Outside Freezer',
    shortTitle: 'Outside Freezer',
    iconEmoji: '🌡️',
    orderIndex: 3,
  },
  {
    id: 'NORMAL_TEMPERATURE',
    title: 'Normal Temperature Storage',
    shortTitle: 'Normal Storage',
    iconEmoji: '🧺',
    orderIndex: 4,
  },
];

export const KITCHEN_COUNTING_MODES: Array<{
  id: KitchenCountingModeId;
  label: string;
  usesSealedOpened: boolean;
}> = [
  { id: 'SEALED_AND_PERCENTAGE', label: 'Sealed + Opened (% left)', usesSealedOpened: true },
  { id: 'SEALED_AND_GRAMS', label: 'Sealed + Opened (grams)', usesSealedOpened: true },
  { id: 'SEALED_AND_ML', label: 'Sealed + Opened (ml)', usesSealedOpened: true },
  { id: 'SEALED_AND_PIECES', label: 'Sealed + Opened (pcs)', usesSealedOpened: true },
  { id: 'DIRECT_GRAMS', label: 'Total Remaining (grams)', usesSealedOpened: false },
  { id: 'DIRECT_ML', label: 'Total Remaining (ml)', usesSealedOpened: false },
  { id: 'DIRECT_PIECES', label: 'Total Pieces', usesSealedOpened: false },
];

export function usesSealedOpenedMode(mode: string): boolean {
  return (
    mode === 'SEALED_AND_PERCENTAGE' ||
    mode === 'SEALED_AND_GRAMS' ||
    mode === 'SEALED_AND_ML' ||
    mode === 'SEALED_AND_PIECES'
  );
}

export function buildShiftId(dateKey: string, shiftType: 'OPENING' | 'CLOSING'): string {
  return `${dateKey}_${shiftType}`;
}

export function round2(v: number): number {
  return Math.round(v * 100) / 100;
}

export function calculateTotalUsableQty(
  countingMode: string,
  packSize: number,
  sealedQty: number,
  openedQty: number,
  openedRemainingQty: number
): number {
  const safePack = packSize > 0 ? packSize : 1.0;
  const safeSealed = Math.max(0, sealedQty);
  const safeOpened = Math.max(0, openedQty);
  const safeRem = Math.max(0, openedRemainingQty);

  let raw = safeRem;
  if (countingMode === 'SEALED_AND_PERCENTAGE') {
    const pctFraction = Math.min(100, Math.max(0, safeRem)) / 100.0;
    const openedPart = safeOpened > 0 ? safeOpened * safePack * pctFraction : 0;
    raw = safeSealed * safePack + openedPart;
  } else if (
    countingMode === 'SEALED_AND_GRAMS' ||
    countingMode === 'SEALED_AND_ML' ||
    countingMode === 'SEALED_AND_PIECES'
  ) {
    const openedPart = safeOpened > 0 ? safeRem : 0;
    raw = safeSealed * safePack + openedPart;
  }
  return round2(raw);
}

export function evaluateStockStatus(
  totalUsableQty: number,
  minStockThreshold: number
): KitchenStockStatus {
  const usable = Math.max(0, totalUsableQty);
  const minReq = Math.max(0, minStockThreshold);
  if (usable <= 0.0001) return 'RED';
  if (minReq > 0 && usable < minReq) return 'ORANGE';
  return 'GREEN';
}

export interface KitchenReorderRecommendation {
  item: KitchenInventoryItem;
  currentStock: number;
  minimumRequiredStock: number;
  suggestedPurchaseQty: number;
  suggestedPacksToOrder: number;
  status: KitchenStockStatus;
  orderStatusLabel: string;
}

export function computeReorderRecommendation(
  item: KitchenInventoryItem,
  currentStock: number
): KitchenReorderRecommendation | null {
  const status = evaluateStockStatus(currentStock, item.minStockThreshold);
  if (status === 'GREEN') return null;

  const target = Math.max(item.targetStockLevel, item.minStockThreshold * 1.5);
  const deficit = Math.max(target - Math.max(0, currentStock), item.minStockThreshold);
  const safePack = item.packSize > 0 ? item.packSize : 1.0;
  const packsNeeded = usesSealedOpenedMode(item.countingMode)
    ? Math.max(1, Math.ceil(deficit / safePack))
    : 0;
  const suggestedQty =
    packsNeeded > 0 ? round2(packsNeeded * safePack) : round2(deficit);

  return {
    item,
    currentStock: round2(Math.max(0, currentStock)),
    minimumRequiredStock: round2(item.minStockThreshold),
    suggestedPurchaseQty: suggestedQty,
    suggestedPacksToOrder: packsNeeded,
    status,
    orderStatusLabel:
      status === 'RED' ? 'URGENT — OUT OF STOCK' : 'REORDER SOON',
  };
}

export interface KitchenVarianceRow {
  item: KitchenInventoryItem;
  openingQty: number | null;
  closingQty: number | null;
  wastageQty: number;
  wastageReason: string;
  consumedQty: number | null;
  latestStatus: KitchenStockStatus;
}

export function computeVarianceRows(
  items: KitchenInventoryItem[],
  openingEntries: KitchenInventoryEntry[],
  closingEntries: KitchenInventoryEntry[]
): KitchenVarianceRow[] {
  const openingByItem = new Map(openingEntries.map((e) => [e.itemId, e]));
  const closingByItem = new Map(closingEntries.map((e) => [e.itemId, e]));

  return items
    .filter((i) => i.isActive)
    .map((item) => {
      const openEntry = openingByItem.get(item.id);
      const closeEntry = closingByItem.get(item.id);
      const openQty = openEntry ? openEntry.totalUsableQty : null;
      const closeQty = closeEntry ? closeEntry.totalUsableQty : null;
      const waste = closeEntry ? closeEntry.wastageQty : 0;
      const consumed =
        openQty !== null && closeQty !== null
          ? round2(Math.max(0, openQty - closeQty - waste))
          : null;
      const latestStatus: KitchenStockStatus = closeEntry
        ? closeEntry.stockStatus
        : openEntry
          ? openEntry.stockStatus
          : 'GREEN';

      return {
        item,
        openingQty: openQty,
        closingQty: closeQty,
        wastageQty: round2(waste),
        wastageReason: closeEntry?.wastageReason || '',
        consumedQty: consumed,
        latestStatus,
      };
    });
}

function seedItem(
  id: string,
  name: string,
  iconEmoji: string,
  sectionId: KitchenSectionId,
  countingMode: KitchenCountingModeId,
  unitLabel: string,
  containerLabel: string,
  packSize: number,
  minStockThreshold: number,
  targetStockLevel: number,
  orderIndex: number
): KitchenInventoryItem {
  return {
    id,
    name,
    iconEmoji,
    sectionId,
    countingMode,
    unitLabel,
    containerLabel,
    packSize,
    minStockThreshold,
    targetStockLevel,
    orderIndex,
    isDefault: true,
    isActive: true,
    updatedAt: 0,
  };
}

export const KITCHEN_SEED_CATALOG: KitchenInventoryItem[] = [
  // 1. DEEP FREEZER (6 items)
  seedItem('df_french_fries', 'French Fries', '🍟', 'DEEP_FREEZER', 'SEALED_AND_GRAMS', 'g', 'packets', 2500, 2500, 7500, 1),
  seedItem('df_ice_cream', 'Ice Cream', '🍨', 'DEEP_FREEZER', 'SEALED_AND_PERCENTAGE', 'tubs', 'tubs', 1, 1.5, 4, 2),
  seedItem('df_whipped_cream', 'Whipped Cream', '🥛', 'DEEP_FREEZER', 'SEALED_AND_ML', 'ml', 'packets', 1000, 1000, 3000, 3),
  seedItem('df_mozzarella_cheese', 'Mozzarella Cheese', '🧀', 'DEEP_FREEZER', 'SEALED_AND_GRAMS', 'g', 'packets', 1000, 1000, 2000, 4),
  seedItem('df_ice', 'Ice', '🧊', 'DEEP_FREEZER', 'DIRECT_GRAMS', 'g', '', 1, 2000, 5000, 5),
  seedItem('df_sweet_corn', 'Sweet Corn', '🌽', 'DEEP_FREEZER', 'SEALED_AND_GRAMS', 'g', 'packets', 1000, 1000, 2000, 6),

  // 2. REFRIGERATOR / FREEZER (8 items)
  seedItem('rf_paneer', 'Paneer', '🧈', 'REFRIGERATOR_FREEZER', 'DIRECT_GRAMS', 'g', '', 1, 500, 1500, 1),
  seedItem('rf_thousand_island_sauce', 'Thousand Island Sauce', '🥣', 'REFRIGERATOR_FREEZER', 'SEALED_AND_ML', 'ml', 'bottles', 1000, 500, 2000, 2),
  seedItem('rf_melted_cheese_sauce', 'Melted Cheese Sauce', '🧀', 'REFRIGERATOR_FREEZER', 'SEALED_AND_ML', 'ml', 'bottles', 1000, 500, 2000, 3),
  seedItem('rf_butter', 'Butter', '🧈', 'REFRIGERATOR_FREEZER', 'SEALED_AND_GRAMS', 'g', 'packets', 500, 500, 1500, 4),
  seedItem('rf_cucumber', 'Cucumber', '🥒', 'REFRIGERATOR_FREEZER', 'DIRECT_GRAMS', 'g', '', 1, 500, 1500, 5),
  seedItem('rf_shimla_mirch', 'Shimla Mirch', '🫑', 'REFRIGERATOR_FREEZER', 'DIRECT_GRAMS', 'g', '', 1, 400, 1000, 6),
  seedItem('rf_olives', 'Olives', '🫒', 'REFRIGERATOR_FREEZER', 'SEALED_AND_GRAMS', 'g', 'jars', 450, 300, 900, 7),
  seedItem('rf_jalapeno', 'Jalapeno', '🌶️', 'REFRIGERATOR_FREEZER', 'SEALED_AND_GRAMS', 'g', 'jars', 450, 300, 900, 8),

  // 4. NORMAL TEMPERATURE STORAGE (33 items)
  seedItem('nt_onion', 'Onion', '🧅', 'NORMAL_TEMPERATURE', 'DIRECT_GRAMS', 'g', '', 1, 1000, 3000, 1),
  seedItem('nt_peanut_butter', 'Peanut Butter', '🥜', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'jars', 1000, 500, 2000, 2),
  seedItem('nt_nutella_spread', 'Nutella Spread', '🍫', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'jars', 750, 400, 1500, 3),
  seedItem('nt_lotus_biscoff_spread', 'Lotus Biscoff Spread', '🍪', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'jars', 400, 300, 800, 4),
  seedItem('nt_honey', 'Honey', '🍯', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 500, 300, 1000, 5),
  seedItem('nt_peach_ice_tea', 'Peach Ice Tea', '🍑', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 500, 500, 1500, 6),
  seedItem('nt_lemon_ice_tea', 'Lemon Ice Tea', '🍋', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 500, 500, 1500, 7),
  seedItem('nt_chaat_masala', 'Chaat Masala', '🧂', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 100, 100, 300, 8),
  seedItem('nt_hot_cups', 'Hot Cups', '☕', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 50, 200, 9),
  seedItem('nt_cold_cups', 'Cold Cups', '🥤', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 50, 200, 10),
  seedItem('nt_cinnamon', 'Cinnamon', '🪵', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 100, 100, 300, 11),
  seedItem('nt_condensed_milk', 'Condensed Milk', '🥛', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 400, 800, 2000, 12),
  seedItem('nt_dustbin_bags', 'Dustbin Bags', '🗑️', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 15, 50, 13),
  seedItem('nt_bread', 'Bread', '🍞', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 20, 60, 14),
  seedItem('nt_coffee_packets', 'Coffee Packets', '☕', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 1000, 1000, 3000, 15),
  seedItem('nt_tissue_paper', 'Tissue Paper', '🧻', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 100, 500, 16),
  seedItem('nt_sandwich_boxes', 'Sandwich Boxes', '🥪', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 30, 100, 17),
  seedItem('nt_fries_boxes', 'Fries Boxes', '🍟', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 30, 100, 18),
  seedItem('nt_hand_wash', 'Hand Wash', '🧼', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 500, 300, 1000, 19),
  seedItem('nt_hand_sanitizer', 'Hand Sanitizer', '🧴', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 500, 300, 1000, 20),
  seedItem('nt_cranberry_juice', 'Cranberry Juice', '🧃', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 1000, 1000, 3000, 21),
  seedItem('nt_peri_peri_masala', 'Peri Peri Masala', '🌶️', 'NORMAL_TEMPERATURE', 'SEALED_AND_GRAMS', 'g', 'packets', 250, 200, 500, 22),
  seedItem('nt_hazelnut_monin_syrup', 'Hazelnut Monin Syrup', '🌰', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 700, 500, 1400, 23),
  seedItem('nt_vanilla_monin_syrup', 'Vanilla Monin Syrup', '🌼', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 700, 500, 1400, 24),
  seedItem('nt_caramel_monin_syrup', 'Caramel Monin Syrup', '🍮', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'bottles', 700, 500, 1400, 25),
  seedItem('nt_tomato_sauce_sachets', 'Tomato Sauce Sachets', '🍅', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 50, 200, 26),
  seedItem('nt_sugar_sachets', 'Sugar Sachets', '🍬', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 50, 200, 27),
  seedItem('nt_oil_packets', 'Oil Packets', '🫗', 'NORMAL_TEMPERATURE', 'SEALED_AND_ML', 'ml', 'packets', 1000, 1000, 3000, 28),
  seedItem('nt_banana', 'Banana', '🍌', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 6, 18, 29),
  seedItem('nt_gloves', 'Gloves', '🧤', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 20, 100, 30),
  seedItem('nt_shower_caps', 'Shower Caps', '🧢', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 20, 100, 31),
  seedItem('nt_straws', 'Straws', '🥤', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 50, 200, 32),
  seedItem('nt_spoons', 'Spoons', '🥄', 'NORMAL_TEMPERATURE', 'DIRECT_PIECES', 'pcs', '', 1, 50, 200, 33),
];
