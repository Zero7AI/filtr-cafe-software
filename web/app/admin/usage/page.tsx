'use client';

import React, { useCallback, useEffect, useState } from 'react';
import { BarChart3 } from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import { getIstDateKey } from '@/lib/domain/ist';
import { CleanIngredientUsageRow } from '@/lib/domain/inventory';

type RangeKey = 'TODAY' | 'YESTERDAY' | 'LAST_7_DAYS' | 'THIS_MONTH' | 'CUSTOM';

export default function AdminUsagePage() {
  const { isDark, refreshTick } = useAdminContext();

  const [range, setRange] = useState<RangeKey>('TODAY');
  const [customDate, setCustomDate] = useState(getIstDateKey());
  const [ordersCount, setOrdersCount] = useState(0);
  const [items, setItems] = useState<CleanIngredientUsageRow[]>([]);
  const [loading, setLoading] = useState(true);

  const loadUsage = useCallback(async () => {
    setLoading(true);
    try {
      const qs =
        range === 'CUSTOM'
          ? `range=CUSTOM&start=${encodeURIComponent(customDate)}&end=${encodeURIComponent(customDate)}`
          : `range=${range}`;
      const res = await fetch(`/api/admin/usage?${qs}`, { cache: 'no-store' });
      if (res.ok) {
        const d = await res.json();
        setOrdersCount(Number(d.ordersCount || 0));
        setItems(d.items || []);
      }
    } finally {
      setLoading(false);
    }
  }, [range, customDate]);

  useEffect(() => {
    loadUsage();
  }, [loadUsage, refreshTick]);

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-black flex items-center gap-2">
            <BarChart3 className="h-5 w-5 text-amber-500" />
            <span>Inventory Usage</span>
          </h1>
          <p className="text-xs text-zinc-400">
            Calculated cleanly from Recipe Master × Orders Made
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <span className="rounded-xl border border-amber-500/40 bg-amber-500/15 px-3 py-1.5 text-xs font-black text-amber-400">
            {ordersCount} Orders Made
          </span>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-1.5">
        {(
          [
            ['TODAY', 'Today'],
            ['YESTERDAY', 'Yesterday'],
            ['LAST_7_DAYS', '7 Days'],
            ['THIS_MONTH', '30 Days'],
          ] as const
        ).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setRange(key)}
            className={`rounded-xl border px-3.5 py-2 text-xs font-black ${
              range === key
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark
                  ? 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white'
                  : 'bg-white border-zinc-200 text-zinc-600'
            }`}
          >
            {label}
          </button>
        ))}

        <input
          type="date"
          value={customDate}
          onChange={(e) => {
            setCustomDate(e.target.value);
            setRange('CUSTOM');
          }}
          className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
            range === 'CUSTOM'
              ? 'border-amber-500 bg-amber-500/10 text-amber-400'
              : isDark
                ? 'bg-zinc-900 border-zinc-800 text-zinc-300'
                : 'bg-white border-zinc-200 text-zinc-700'
          }`}
        />
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">
          Calculating usage from Recipe Master...
        </div>
      ) : items.length === 0 ? (
        <div className={`rounded-2xl border p-10 text-center text-xs text-zinc-400 ${cardSurface}`}>
          {ordersCount === 0
            ? 'No orders recorded for this period.'
            : 'No Recipe Master mappings matched the orders in this period.'}
        </div>
      ) : (
        <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
          <table className="w-full text-sm text-left">
            <thead>
              <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[11px] uppercase text-zinc-500">
                <th className="py-3 px-5">Ingredient ({items.length})</th>
                <th className="py-3 px-5 text-right">Exact Usage Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {items.map((row) => (
                <tr key={row.inventoryItemId}>
                  <td className="py-3.5 px-5 font-bold">{row.itemName}</td>
                  <td className="py-3.5 px-5 text-right font-black text-amber-500">
                    {row.exactUsageLabel}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
