'use client';

import React, { useEffect, useState, useCallback } from 'react';
import { Flame, Check, RotateCcw, User } from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import { useToast } from '@/components/admin/ToastContext';
import { formatIstTime } from '@/lib/domain/ist';
import { KdsQueueUnitItem, StaffRosterMember } from '@/lib/domain/types';

export default function AdminKdsPage() {
  const { isDark, operatorName, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [queue, setQueue] = useState<KdsQueueUnitItem[]>([]);
  const [roster, setRoster] = useState<StaffRosterMember[]>([]);
  const [selectedStaff, setSelectedStaff] = useState<string>('');
  const [paidOnly, setPaidOnly] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadKds = useCallback(async () => {
    try {
      const res = await fetch(`/api/admin/kds?paidOnly=${paidOnly}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        const data = await res.json();
        setQueue(data.queue || []);
        const activeRoster: StaffRosterMember[] = data.roster || [];
        setRoster(activeRoster);
        setSelectedStaff((prev) => prev || activeRoster[0]?.name || operatorName);
      }
    } finally {
      setLoading(false);
    }
  }, [paidOnly, operatorName]);

  useEffect(() => {
    loadKds();
    const timer = setInterval(loadKds, 4000);
    return () => clearInterval(timer);
  }, [loadKds, refreshTick]);

  const sendAction = async (
    unit: KdsQueueUnitItem,
    action: 'CLAIM' | 'COMPLETE' | 'RELEASE'
  ) => {
    const res = await fetch('/api/admin/kds', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action,
        orderId: unit.orderId,
        itemId: unit.itemId,
        itemName: unit.itemName,
        unitIndex: unit.unitIndex,
        staffName: selectedStaff || operatorName,
      }),
    });
    if (res.ok) {
      pushToast(
        action === 'CLAIM'
          ? `Claimed ${unit.itemName} (#${unit.shortOrderNumber})`
          : action === 'COMPLETE'
            ? `Completed ${unit.itemName} (#${unit.shortOrderNumber})`
            : `Released ${unit.itemName}`,
        'success'
      );
      loadKds();
    } else {
      pushToast('Failed to update KDS unit', 'error');
    }
  };

  return (
    <div className="space-y-4">
      <div
        className={`rounded-2xl border p-4 flex flex-wrap items-center justify-between gap-3 ${
          isDark ? 'border-zinc-800 bg-zinc-900' : 'border-zinc-200 bg-white shadow-sm'
        }`}
      >
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-500/15 text-amber-500">
            <Flame className="h-5 w-5" />
          </div>
          <div>
            <h3 className="text-sm font-black">Live FIFO Barista Queue</h3>
            <p className="text-xs text-zinc-400">
              {queue.length} unit(s) waiting or making • Auto-refreshes every 4s
            </p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="flex items-center gap-1.5">
            <User className="h-3.5 w-3.5 text-amber-500" />
            <select
              value={selectedStaff}
              onChange={(e) => setSelectedStaff(e.target.value)}
              className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
                isDark
                  ? 'bg-zinc-950 border-zinc-800 text-white'
                  : 'bg-white border-zinc-200 text-zinc-900'
              }`}
            >
              {roster.map((m) => (
                <option key={m.id} value={m.name}>
                  {m.name} ({m.roleTitle})
                </option>
              ))}
              <option value={operatorName}>{operatorName} (Admin)</option>
            </select>
          </div>

          <button
            type="button"
            onClick={() => setPaidOnly(!paidOnly)}
            className={`rounded-xl border px-3 py-1.5 text-xs font-bold transition ${
              paidOnly
                ? 'bg-emerald-500 text-zinc-950 border-emerald-500'
                : isDark
                  ? 'bg-zinc-950 border-zinc-800 text-zinc-400'
                  : 'bg-white border-zinc-200 text-zinc-600'
            }`}
          >
            Paid Only: {paidOnly ? 'ON' : 'OFF'}
          </button>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading queue...</div>
      ) : queue.length === 0 ? (
        <div
          className={`rounded-2xl border p-12 text-center ${
            isDark ? 'border-zinc-800 bg-zinc-900/60' : 'border-zinc-200 bg-white'
          }`}
        >
          <Check className="h-10 w-10 text-emerald-500 mx-auto mb-2" />
          <p className="text-sm font-bold">Kitchen queue is clear!</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-3.5">
          {queue.map((unit) => {
            const isMaking = unit.stage === 'MAKING';
            return (
              <div
                key={unit.claimKey}
                style={
                  isMaking && unit.claimedColorHex
                    ? { borderColor: unit.claimedColorHex }
                    : undefined
                }
                className={`rounded-2xl border-2 p-4 flex flex-col justify-between space-y-3 ${
                  isDark ? 'bg-zinc-900 border-zinc-800' : 'bg-white border-zinc-200 shadow-sm'
                }`}
              >
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="rounded-lg bg-amber-500 text-zinc-950 px-2 py-0.5 text-xs font-black">
                        #{unit.queuePosition}
                      </span>
                      <span className="text-xs font-mono font-bold text-zinc-400">
                        Token #{unit.shortOrderNumber}
                      </span>
                    </div>
                    <span
                      className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                        unit.isPaid
                          ? 'bg-emerald-500/15 text-emerald-500'
                          : 'bg-rose-500/15 text-rose-500'
                      }`}
                    >
                      {unit.isPaid ? unit.paymentMode : 'UNPAID'}
                    </span>
                  </div>

                  <h4 className="mt-2.5 text-base font-black leading-tight">
                    {unit.itemName}
                  </h4>
                  <p className="text-xs text-zinc-400 mt-0.5">
                    Cup {unit.unitIndex} of {unit.totalUnits} • {unit.customerName} •{' '}
                    {formatIstTime(unit.orderTimestamp)}
                  </p>

                  {unit.orderNotes && (
                    <p className="mt-2 rounded-lg bg-amber-500/10 border border-amber-500/20 px-2.5 py-1.5 text-xs text-amber-400">
                      {unit.orderNotes}
                    </p>
                  )}

                  {isMaking && (
                    <div
                      className="mt-2 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-black text-white"
                      style={{ backgroundColor: unit.claimedColorHex || '#16A34A' }}
                    >
                      <span>Making: {unit.claimedByStaff}</span>
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-2 pt-2 border-t border-zinc-200 dark:border-zinc-800">
                  {!isMaking ? (
                    <button
                      type="button"
                      onClick={() => sendAction(unit, 'CLAIM')}
                      className="flex-1 rounded-xl bg-amber-500 py-2 text-xs font-black text-zinc-950 hover:bg-amber-400 transition"
                    >
                      Start Making
                    </button>
                  ) : (
                    <>
                      <button
                        type="button"
                        onClick={() => sendAction(unit, 'COMPLETE')}
                        className="flex-1 rounded-xl bg-emerald-500 py-2 text-xs font-black text-zinc-950 hover:bg-emerald-400 transition"
                      >
                        Mark Ready ✓
                      </button>
                      <button
                        type="button"
                        onClick={() => sendAction(unit, 'RELEASE')}
                        title="Release claim"
                        className="rounded-xl border border-zinc-700 p-2 text-zinc-400 hover:text-white"
                      >
                        <RotateCcw className="h-4 w-4" />
                      </button>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
