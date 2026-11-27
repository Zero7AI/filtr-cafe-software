'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { Refrigerator, AlertTriangle, Plus, CheckCircle2 } from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import Modal from '@/components/admin/Modal';
import { useToast } from '@/components/admin/ToastContext';
import { getIstDateKey, formatIstTime } from '@/lib/domain/ist';
import {
  KITCHEN_COUNTING_MODES,
  KITCHEN_SECTIONS,
  KitchenReorderRecommendation,
  KitchenVarianceRow,
} from '@/lib/domain/kitchen';
import {
  KitchenInventoryItem,
  KitchenInventoryShift,
  KitchenSectionId,
} from '@/lib/domain/types';

export default function AdminKitchenPage() {
  const { isDark, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [dateKey, setDateKey] = useState(getIstDateKey());
  const [tab, setTab] = useState<'VARIANCE' | 'REORDER' | 'CATALOG'>('VARIANCE');
  const [sectionFilter, setSectionFilter] = useState<'ALL' | KitchenSectionId>('ALL');
  const [catalog, setCatalog] = useState<KitchenInventoryItem[]>([]);
  const [openingShift, setOpeningShift] = useState<KitchenInventoryShift | null>(null);
  const [closingShift, setClosingShift] = useState<KitchenInventoryShift | null>(null);
  const [varianceRows, setVarianceRows] = useState<KitchenVarianceRow[]>([]);
  const [reorders, setReorders] = useState<KitchenReorderRecommendation[]>([]);
  const [loading, setLoading] = useState(true);

  const [itemModal, setItemModal] = useState(false);
  const [editItem, setEditItem] = useState<Partial<KitchenInventoryItem>>({});

  const loadKitchen = useCallback(async () => {
    try {
      const res = await fetch(`/api/admin/kitchen?date=${dateKey}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        const d = await res.json();
        setCatalog(d.catalog || []);
        setOpeningShift(d.openingShift || null);
        setClosingShift(d.closingShift || null);
        setVarianceRows(d.varianceRows || []);
        setReorders(d.reorderRecommendations || []);
      }
    } finally {
      setLoading(false);
    }
  }, [dateKey]);

  useEffect(() => {
    loadKitchen();
  }, [loadKitchen, refreshTick]);

  const filteredVariance = useMemo(() => {
    if (sectionFilter === 'ALL') return varianceRows;
    return varianceRows.filter((r) => r.item.sectionId === sectionFilter);
  }, [varianceRows, sectionFilter]);

  const filteredCatalog = useMemo(() => {
    if (sectionFilter === 'ALL') return catalog;
    return catalog.filter((c) => c.sectionId === sectionFilter);
  }, [catalog, sectionFilter]);

  const saveCatalogItem = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/kitchen', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'upsertCatalogItem', item: editItem }),
    });
    if (res.ok) {
      pushToast('Saved kitchen catalog item', 'success');
      setItemModal(false);
      loadKitchen();
    } else {
      pushToast('Failed to save item', 'error');
    }
  };

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          <button
            type="button"
            onClick={() => setTab('VARIANCE')}
            className={`rounded-xl border px-3.5 py-2 text-xs font-black ${
              tab === 'VARIANCE'
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark
                  ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                  : 'bg-white border-zinc-200'
            }`}
          >
            Opening vs Closing Variance
          </button>
          <button
            type="button"
            onClick={() => setTab('REORDER')}
            className={`rounded-xl border px-3.5 py-2 text-xs font-black ${
              tab === 'REORDER'
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark
                  ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                  : 'bg-white border-zinc-200'
            }`}
          >
            Reorder Alerts ({reorders.length})
          </button>
          <button
            type="button"
            onClick={() => setTab('CATALOG')}
            className={`rounded-xl border px-3.5 py-2 text-xs font-black ${
              tab === 'CATALOG'
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark
                  ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                  : 'bg-white border-zinc-200'
            }`}
          >
            Zone Catalog ({catalog.length})
          </button>
        </div>

        <div className="flex items-center gap-2">
          <input
            type="date"
            value={dateKey}
            onChange={(e) => setDateKey(e.target.value)}
            className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
              isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
            }`}
          />
          <button
            type="button"
            onClick={() => {
              setEditItem({
                name: '',
                iconEmoji: '📦',
                sectionId: 'NORMAL_TEMPERATURE',
                countingMode: 'DIRECT_PIECES',
                unitLabel: 'pcs',
                containerLabel: '',
                packSize: 1,
                minStockThreshold: 10,
                targetStockLevel: 30,
                isActive: true,
              });
              setItemModal(true);
            }}
            className="flex items-center gap-1 rounded-xl bg-amber-500 px-3 py-2 text-xs font-black text-zinc-950"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Kitchen Item</span>
          </button>
        </div>
      </div>

      {/* Shift Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className={`rounded-2xl border p-4 ${cardSurface}`}>
          <div className="flex items-center justify-between">
            <span className="text-xs font-black uppercase text-sky-400">
              Opening Shift ({dateKey})
            </span>
            <span className="rounded-md bg-zinc-800 px-2 py-0.5 text-[10px] font-bold">
              {openingShift?.status || 'NOT STARTED'}
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            {openingShift
              ? `By ${openingShift.staffName} • ${openingShift.entries.length} items • Updated ${formatIstTime(openingShift.lastUpdatedAt)}`
              : 'Waiting for staff opening check on Android Kitchen app'}
          </p>
        </div>

        <div className={`rounded-2xl border p-4 ${cardSurface}`}>
          <div className="flex items-center justify-between">
            <span className="text-xs font-black uppercase text-amber-400">
              Closing Shift ({dateKey})
            </span>
            <span className="rounded-md bg-zinc-800 px-2 py-0.5 text-[10px] font-bold">
              {closingShift?.status || 'NOT STARTED'}
            </span>
          </div>
          <p className="text-xs text-zinc-400 mt-1">
            {closingShift
              ? `By ${closingShift.staffName} • ${closingShift.entries.length} items • Updated ${formatIstTime(closingShift.lastUpdatedAt)}`
              : 'Waiting for staff closing check'}
          </p>
        </div>
      </div>

      {/* Storage Zone Pills */}
      <div className="flex flex-wrap gap-1.5">
        <button
          type="button"
          onClick={() => setSectionFilter('ALL')}
          className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
            sectionFilter === 'ALL'
              ? 'bg-amber-500 text-zinc-950 border-amber-500'
              : isDark ? 'bg-zinc-900 border-zinc-800 text-zinc-400' : 'bg-white border-zinc-200'
          }`}
        >
          All Zones
        </button>
        {KITCHEN_SECTIONS.map((s) => (
          <button
            key={s.id}
            type="button"
            onClick={() => setSectionFilter(s.id)}
            className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
              sectionFilter === s.id
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark ? 'bg-zinc-900 border-zinc-800 text-zinc-400' : 'bg-white border-zinc-200'
            }`}
          >
            {s.iconEmoji} {s.shortTitle}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading kitchen data...</div>
      ) : tab === 'VARIANCE' ? (
        <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[10px] uppercase text-zinc-500">
                  <th className="py-3 px-4">Item</th>
                  <th className="py-3 px-4">Opening</th>
                  <th className="py-3 px-4">Closing</th>
                  <th className="py-3 px-4">Consumed</th>
                  <th className="py-3 px-4">Wastage</th>
                  <th className="py-3 px-4">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {filteredVariance.map((r) => (
                  <tr key={r.item.id}>
                    <td className="py-2.5 px-4 font-bold">
                      {r.item.iconEmoji} {r.item.name}
                    </td>
                    <td className="py-2.5 px-4">
                      {r.openingQty !== null ? `${r.openingQty} ${r.item.unitLabel}` : '—'}
                    </td>
                    <td className="py-2.5 px-4">
                      {r.closingQty !== null ? `${r.closingQty} ${r.item.unitLabel}` : '—'}
                    </td>
                    <td className="py-2.5 px-4 font-bold text-amber-500">
                      {r.consumedQty !== null ? `${r.consumedQty} ${r.item.unitLabel}` : '—'}
                    </td>
                    <td className="py-2.5 px-4 text-rose-400">
                      {r.wastageQty > 0 ? `${r.wastageQty} ${r.item.unitLabel}` : '0'}
                    </td>
                    <td className="py-2.5 px-4">
                      <span
                        className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                          r.latestStatus === 'RED'
                            ? 'bg-rose-500/20 text-rose-400'
                            : r.latestStatus === 'ORANGE'
                              ? 'bg-amber-500/20 text-amber-400'
                              : 'bg-emerald-500/15 text-emerald-500'
                        }`}
                      >
                        {r.latestStatus}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : tab === 'REORDER' ? (
        reorders.length === 0 ? (
          <div className={`rounded-2xl border p-10 text-center ${cardSurface}`}>
            <CheckCircle2 className="h-10 w-10 text-emerald-500 mx-auto mb-2" />
            <p className="text-sm font-bold">No kitchen items below threshold for {dateKey}</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
            {reorders.map((rec) => (
              <div key={rec.item.id} className={`rounded-2xl border p-4 space-y-2 ${cardSurface}`}>
                <div className="flex items-center justify-between">
                  <span className="font-bold text-sm">
                    {rec.item.iconEmoji} {rec.item.name}
                  </span>
                  <AlertTriangle
                    className={`h-4 w-4 ${
                      rec.status === 'RED' ? 'text-rose-500' : 'text-amber-500'
                    }`}
                  />
                </div>
                <p className="text-xs text-zinc-400">
                  Current: <strong>{rec.currentStock}</strong> / Min:{' '}
                  <strong>{rec.minimumRequiredStock} {rec.item.unitLabel}</strong>
                </p>
                <p className="text-xs font-black text-amber-500">
                  Suggested Order: {rec.suggestedPurchaseQty} {rec.item.unitLabel}
                  {rec.suggestedPacksToOrder > 0 ? ` (${rec.suggestedPacksToOrder} packs)` : ''}
                </p>
              </div>
            ))}
          </div>
        )
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {filteredCatalog.map((item) => (
            <div
              key={item.id}
              onClick={() => {
                setEditItem(item);
                setItemModal(true);
              }}
              className={`rounded-2xl border p-4 cursor-pointer hover:border-amber-500/50 transition ${cardSurface}`}
            >
              <div className="flex items-center justify-between">
                <span className="font-bold text-sm">
                  {item.iconEmoji} {item.name}
                </span>
                <Refrigerator className="h-4 w-4 text-sky-400" />
              </div>
              <p className="text-[11px] text-zinc-400 mt-1">
                {item.sectionId} • {item.countingMode}
              </p>
              <p className="text-xs mt-1.5">
                Pack: {item.packSize} {item.unitLabel} • Min: {item.minStockThreshold} • Target:{' '}
                {item.targetStockLevel}
              </p>
            </div>
          ))}
        </div>
      )}

      <Modal
        open={itemModal}
        onClose={() => setItemModal(false)}
        title="Kitchen Catalog Item"
        isDark={isDark}
      >
        <form onSubmit={saveCatalogItem} className="space-y-3">
          <div className="grid grid-cols-4 gap-2">
            <div className="col-span-3">
              <label className="block text-xs font-bold mb-1">Item Name</label>
              <input
                type="text"
                value={editItem.name || ''}
                onChange={(e) => setEditItem({ ...editItem, name: e.target.value })}
                required
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Emoji</label>
              <input
                type="text"
                value={editItem.iconEmoji || '📦'}
                onChange={(e) => setEditItem({ ...editItem, iconEmoji: e.target.value })}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white text-center"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold mb-1">Storage Zone</label>
              <select
                value={editItem.sectionId || 'NORMAL_TEMPERATURE'}
                onChange={(e) =>
                  setEditItem({ ...editItem, sectionId: e.target.value as KitchenSectionId })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              >
                {KITCHEN_SECTIONS.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.title}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Counting Mode</label>
              <select
                value={editItem.countingMode || 'DIRECT_PIECES'}
                onChange={(e) =>
                  setEditItem({
                    ...editItem,
                    countingMode: e.target.value as KitchenInventoryItem['countingMode'],
                  })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              >
                {KITCHEN_COUNTING_MODES.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="block text-xs font-bold mb-1">Pack Size</label>
              <input
                type="number"
                step="0.01"
                value={editItem.packSize ?? 1}
                onChange={(e) => setEditItem({ ...editItem, packSize: Number(e.target.value) })}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Min Threshold</label>
              <input
                type="number"
                step="0.01"
                value={editItem.minStockThreshold ?? 1}
                onChange={(e) =>
                  setEditItem({ ...editItem, minStockThreshold: Number(e.target.value) })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Target Level</label>
              <input
                type="number"
                step="0.01"
                value={editItem.targetStockLevel ?? 2}
                onChange={(e) =>
                  setEditItem({ ...editItem, targetStockLevel: Number(e.target.value) })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-xs text-white"
              />
            </div>
          </div>

          <button
            type="submit"
            className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
          >
            Save Kitchen Item
          </button>
        </form>
      </Modal>
    </div>
  );
}
