import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import { getIstDateKey } from '@/lib/domain/ist';
import {
  buildShiftId,
  calculateTotalUsableQty,
  computeReorderRecommendation,
  computeVarianceRows,
  evaluateStockStatus,
  KITCHEN_SEED_CATALOG,
} from '@/lib/domain/kitchen';
import {
  KitchenInventoryEntry,
  KitchenInventoryItem,
  KitchenInventoryShift,
} from '@/lib/domain/types';
import { getSyncMetadataJson, setSyncMetadata } from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

async function loadCatalog(): Promise<KitchenInventoryItem[]> {
  const cloud = await getSyncMetadataJson<KitchenInventoryItem[]>(
    'kitchen_inv_catalog',
    []
  );
  if (Array.isArray(cloud) && cloud.length > 0) return cloud;
  return KITCHEN_SEED_CATALOG;
}

export async function GET(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const dateKey =
      request.nextUrl.searchParams.get('date') || getIstDateKey();

    const openingId = buildShiftId(dateKey, 'OPENING');
    const closingId = buildShiftId(dateKey, 'CLOSING');

    const [catalog, shiftIndex, openingShift, closingShift] = await Promise.all([
      loadCatalog(),
      getSyncMetadataJson<string[]>('kitchen_inv_shift_index', []),
      getSyncMetadataJson<KitchenInventoryShift | null>(
        `kitchen_inv_shift_${openingId}`,
        null
      ),
      getSyncMetadataJson<KitchenInventoryShift | null>(
        `kitchen_inv_shift_${closingId}`,
        null
      ),
    ]);

    const openingEntries = openingShift?.entries || [];
    const closingEntries = closingShift?.entries || [];
    const varianceRows = computeVarianceRows(
      catalog,
      openingEntries,
      closingEntries
    );

    const latestByItem = new Map<string, KitchenInventoryEntry>();
    for (const e of openingEntries) latestByItem.set(e.itemId, e);
    for (const e of closingEntries) latestByItem.set(e.itemId, e);

    const reorderRecommendations = catalog
      .filter((i) => i.isActive)
      .map((item) => {
        const entry = latestByItem.get(item.id);
        if (!entry) return null;
        return computeReorderRecommendation(item, entry.totalUsableQty);
      })
      .filter((x): x is NonNullable<typeof x> => x !== null);

    return NextResponse.json({
      dateKey,
      catalog,
      shiftIndex,
      openingShift,
      closingShift,
      varianceRows,
      reorderRecommendations,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load kitchen inventory' },
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

    if (action === 'upsertCatalogItem') {
      const catalog = await loadCatalog();
      const incoming = body?.item as Partial<KitchenInventoryItem>;
      if (!incoming?.name?.trim()) {
        return NextResponse.json({ error: 'Item name required' }, { status: 400 });
      }
      const id =
        incoming.id?.trim() ||
        `custom_${incoming.name
          .trim()
          .toLowerCase()
          .replace(/[^a-z0-9]+/g, '_')}_${Date.now().toString().slice(-4)}`;

      const updatedItem: KitchenInventoryItem = {
        id,
        name: incoming.name.trim(),
        iconEmoji: incoming.iconEmoji?.trim() || '📦',
        sectionId: incoming.sectionId || 'NORMAL_TEMPERATURE',
        countingMode: incoming.countingMode || 'DIRECT_PIECES',
        unitLabel: incoming.unitLabel?.trim() || 'pcs',
        containerLabel: incoming.containerLabel?.trim() || '',
        packSize: Math.max(0.01, Number(incoming.packSize || 1)),
        minStockThreshold: Math.max(0, Number(incoming.minStockThreshold ?? 1)),
        targetStockLevel: Math.max(0, Number(incoming.targetStockLevel ?? 2)),
        orderIndex: Number(incoming.orderIndex ?? catalog.length + 1),
        isDefault: Boolean(incoming.isDefault),
        isActive: incoming.isActive !== false,
        updatedAt: Date.now(),
      };

      const nextCatalog = catalog.some((i) => i.id === id)
        ? catalog.map((i) => (i.id === id ? updatedItem : i))
        : [...catalog, updatedItem];

      await setSyncMetadata('kitchen_inv_catalog', nextCatalog);
      return NextResponse.json({ success: true, item: updatedItem });
    }

    if (action === 'saveShift') {
      const dateKey = String(body?.dateKey || getIstDateKey());
      const shiftType = body?.shiftType === 'CLOSING' ? 'CLOSING' : 'OPENING';
      const shiftId = buildShiftId(dateKey, shiftType);
      const status = body?.status === 'SUBMITTED' ? 'SUBMITTED' : 'IN_PROGRESS';
      const staffName =
        String(body?.staffName || auth.session.operatorName || 'Admin').trim();
      const notes = String(body?.notes || '').trim();
      const catalog = await loadCatalog();
      const catalogById = new Map(catalog.map((c) => [c.id, c]));

      const rawEntries = Array.isArray(body?.entries) ? body.entries : [];
      const now = Date.now();
      const entries: KitchenInventoryEntry[] = rawEntries.map(
        (raw: Record<string, unknown>) => {
          const itemId = String(raw.itemId || '');
          const master = catalogById.get(itemId);
          const mode = String(
            raw.countingModeSnapshot || master?.countingMode || 'DIRECT_PIECES'
          );
          const packSize = Number(
            raw.packSizeSnapshot || master?.packSize || 1
          );
          const sealedQty = Math.max(0, Number(raw.sealedQty || 0));
          const openedQty = Math.max(0, Number(raw.openedQty || 0));
          const openedRemainingQty = Math.max(
            0,
            Number(raw.openedRemainingQty || 0)
          );
          const totalUsableQty = calculateTotalUsableQty(
            mode,
            packSize,
            sealedQty,
            openedQty,
            openedRemainingQty
          );
          const minThresh = master?.minStockThreshold ?? 1;
          const stockStatus = evaluateStockStatus(totalUsableQty, minThresh);

          return {
            shiftId,
            dateKey,
            shiftType,
            itemId,
            itemNameSnapshot: String(
              raw.itemNameSnapshot || master?.name || itemId
            ),
            sectionIdSnapshot: String(
              raw.sectionIdSnapshot || master?.sectionId || 'NORMAL_TEMPERATURE'
            ),
            unitLabelSnapshot: String(
              raw.unitLabelSnapshot || master?.unitLabel || 'pcs'
            ),
            countingModeSnapshot: mode,
            packSizeSnapshot: packSize,
            sealedQty,
            openedQty,
            openedRemainingQty,
            totalUsableQty,
            wastageQty: Math.max(0, Number(raw.wastageQty || 0)),
            wastageReason: String(raw.wastageReason || ''),
            stockStatus,
            isChecked: Boolean(raw.isChecked ?? true),
            checkedBy: String(raw.checkedBy || staffName),
            checkedAt: Number(raw.checkedAt || now),
          };
        }
      );

      const existingShift = await getSyncMetadataJson<KitchenInventoryShift | null>(
        `kitchen_inv_shift_${shiftId}`,
        null
      );

      const shiftPayload: KitchenInventoryShift = {
        id: shiftId,
        dateKey,
        shiftType,
        status,
        staffName,
        startedAt: existingShift?.startedAt || now,
        submittedAt: status === 'SUBMITTED' ? now : existingShift?.submittedAt || 0,
        lastUpdatedAt: now,
        notes,
        entries,
      };

      await setSyncMetadata(`kitchen_inv_shift_${shiftId}`, shiftPayload);
      const index = await getSyncMetadataJson<string[]>(
        'kitchen_inv_shift_index',
        []
      );
      const nextIndex = [shiftId, ...index.filter((id) => id !== shiftId)].slice(
        0,
        30
      );
      await setSyncMetadata('kitchen_inv_shift_index', nextIndex);

      return NextResponse.json({ success: true, shift: shiftPayload });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Kitchen mutation failed' },
      { status: 500 }
    );
  }
}
