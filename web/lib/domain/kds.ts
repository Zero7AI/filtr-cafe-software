import { normalizeStaffKey } from './attendance';
import {
  KdsItemClaim,
  KdsQueueUnitItem,
  OrderWithItems,
  StaffRosterMember,
} from './types';

export const KDS_BARISTA_COLORS = [
  '#16A34A', // Green
  '#DC2626', // Red
  '#2563EB', // Blue
  '#D97706', // Amber
  '#7C3AED', // Violet
  '#0D9488', // Teal
  '#E11D48', // Rose
];

export function resolveBaristaColorHex(
  staffName: string,
  customColorHex: string = ''
): string {
  const trimmed = (customColorHex || '').trim();
  if (/^#[0-9A-Fa-f]{6}$/.test(trimmed)) {
    return trimmed.toUpperCase();
  }
  const clean = (staffName || '').trim().toLowerCase();
  if (!clean) return KDS_BARISTA_COLORS[0];
  let hash = 0;
  for (let i = 0; i < clean.length; i++) {
    hash = (hash * 31 + clean.charCodeAt(i)) | 0;
  }
  const index = Math.abs(hash) % KDS_BARISTA_COLORS.length;
  return KDS_BARISTA_COLORS[index];
}

export function buildClaimKey(
  orderId: string,
  itemId: number,
  unitIndex: number
): string {
  return `${orderId}_${itemId}_${unitIndex}`;
}

export function extractShortOrderNumber(orderId: string): string {
  const idx = orderId.lastIndexOf('-');
  return idx >= 0 && idx < orderId.length - 1
    ? orderId.substring(idx + 1)
    : orderId;
}

export function buildFifoPendingQueue(params: {
  orders: OrderWithItems[];
  claims: KdsItemClaim[];
  roster: StaffRosterMember[];
  paidOnlyFilter?: boolean;
  minTimestampMs?: number | null;
}): KdsQueueUnitItem[] {
  const {
    orders,
    claims,
    roster,
    paidOnlyFilter = false,
    minTimestampMs = null,
  } = params;

  const claimsByKey = new Map(claims.map((c) => [c.key, c]));
  const rosterByNorm = new Map<string, StaffRosterMember>();
  for (const m of roster) {
    rosterByNorm.set(normalizeStaffKey(m.name), m);
  }

  const activeOrders = orders
    .filter((o) => !o.order.is_made && !o.order.is_delivered)
    .filter((o) => minTimestampMs === null || o.order.timestamp >= minTimestampMs)
    .filter((o) => !paidOnlyFilter || o.order.is_paid)
    .sort((a, b) => {
      const tsDiff = a.order.timestamp - b.order.timestamp;
      if (tsDiff !== 0) return tsDiff;
      return a.order.order_id.localeCompare(b.order.order_id);
    });

  const units: KdsQueueUnitItem[] = [];

  for (const owi of activeOrders) {
    const order = owi.order;
    const shortOrderNumber = extractShortOrderNumber(order.order_id);

    for (const item of owi.items) {
      const itemId = item.id ?? item.menu_item_id;
      for (let unitIndex = 1; unitIndex <= item.quantity; unitIndex++) {
        const claimKey = buildClaimKey(order.order_id, itemId, unitIndex);
        const claim = claimsByKey.get(claimKey);
        const isDone =
          unitIndex <= (item.completed_quantity || 0) ||
          claim?.status === 'MADE';
        if (isDone) continue;

        const isMaking = claim?.status === 'MAKING';
        const claimedByStaff = isMaking ? claim?.staffName || '' : '';
        let claimedColorHex = '';
        if (isMaking) {
          const member = rosterByNorm.get(normalizeStaffKey(claimedByStaff));
          claimedColorHex = resolveBaristaColorHex(
            claimedByStaff,
            claim?.colorHex || member?.colorHex || ''
          );
        }

        units.push({
          claimKey,
          queuePosition: units.length + 1,
          orderId: order.order_id,
          shortOrderNumber,
          orderTimestamp: order.timestamp,
          customerName: order.customer_name || 'Walk-in',
          orderNotes: order.notes || '',
          isPaid: order.is_paid,
          paymentMode: order.payment_mode || 'UNPAID',
          itemId,
          itemName: item.item_name,
          unitIndex,
          totalUnits: item.quantity,
          stage: isMaking ? 'MAKING' : 'PENDING',
          claimedByStaff,
          claimedColorHex,
        });
      }
    }
  }

  return units;
}
