'use client';

import React, { useEffect, useState, useCallback } from 'react';
import {
  TrendingUp,
  Settings,
  Download,
  Plus,
  Trash2,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import Modal from '@/components/admin/Modal';
import StatCard from '@/components/admin/StatCard';
import { useToast } from '@/components/admin/ToastContext';
import {
  DateRangePreset,
  formatInr,
  formatMonthDisplay,
  getIstMonthKey,
  getNextMonthKey,
  getPreviousMonthKey,
} from '@/lib/domain/ist';
import { PnlReport, getFixedCostForMonthKey } from '@/lib/domain/pnl';
import { FixedCostRecord, FixedCostStaffMember } from '@/lib/domain/types';

export default function AdminPnlPage() {
  const { isDark, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [monthKey, setMonthKey] = useState(getIstMonthKey());
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [report, setReport] = useState<PnlReport | null>(null);
  const [fixedCostsMap, setFixedCostsMap] = useState<Record<string, FixedCostRecord>>({});
  const [loading, setLoading] = useState(true);

  const [costModalOpen, setCostModalOpen] = useState(false);
  const [editMonthKey, setEditMonthKey] = useState(getIstMonthKey());
  const [rent, setRent] = useState('9000');
  const [electricity, setElectricity] = useState('6000');
  const [maidSalary, setMaidSalary] = useState('2000');
  const [miscellaneous, setMiscellaneous] = useState('5000');
  const [notes, setNotes] = useState('');
  const [staffRows, setStaffRows] = useState<FixedCostStaffMember[]>([]);
  const [savingCost, setSavingCost] = useState(false);

  const loadPnl = useCallback(async () => {
    try {
      const qs = new URLSearchParams({ range, monthKey });
      if (range === 'CUSTOM' && customStart) {
        qs.set('start', customStart);
        qs.set('end', customEnd || customStart);
      }
      const res = await fetch(`/api/admin/pnl?${qs.toString()}`, { cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        setReport(data.report);
        setFixedCostsMap(data.fixedCostsMap || {});
      }
    } finally {
      setLoading(false);
    }
  }, [range, monthKey, customStart, customEnd]);

  useEffect(() => {
    loadPnl();
  }, [loadPnl, refreshTick]);

  const populateModalForMonth = (mk: string, map: Record<string, FixedCostRecord>) => {
    const { record } = getFixedCostForMonthKey(mk, map);
    setEditMonthKey(mk);
    setRent(String(record.rent));
    setElectricity(String(record.electricity));
    setMaidSalary(String(record.maidSalary));
    setMiscellaneous(String(record.miscellaneous));
    setNotes(record.notes || '');
    setStaffRows(record.staffMembers.length > 0 ? record.staffMembers : [
      { id: `staff_${mk}_1`, name: 'Barista 1', salary: 15000 },
      { id: `staff_${mk}_2`, name: 'Barista 2', salary: 15000 },
    ]);
  };

  const handleOpenFixedCosts = () => {
    populateModalForMonth(monthKey, fixedCostsMap);
    setCostModalOpen(true);
  };

  const handleSaveFixedCosts = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingCost(true);
    try {
      const res = await fetch('/api/admin/pnl', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          monthKey: editMonthKey,
          rent: Number(rent || 0),
          electricity: Number(electricity || 0),
          maidSalary: Number(maidSalary || 0),
          miscellaneous: Number(miscellaneous || 0),
          notes,
          staffMembers: staffRows,
        }),
      });
      if (res.ok) {
        pushToast(`Saved fixed overheads for ${editMonthKey}`, 'success');
        setCostModalOpen(false);
        loadPnl();
      } else {
        pushToast('Failed to save fixed costs', 'error');
      }
    } finally {
      setSavingCost(false);
    }
  };

  const exportPnlCsv = () => {
    if (!report) return;
    const rows = [
      ['FILTR COFFEE P&L STATEMENT', report.proratedOverhead.monthKeyDescription],
      ['Orders Processed', String(report.ordersCount)],
      ['Gross Sales', report.grossSales.toFixed(2)],
      ['5% Inclusive GST', report.gstAmount.toFixed(2)],
      ['Net Operating Revenue', report.netSales.toFixed(2)],
      ['Recipe COGS', report.recipeCogs.toFixed(2)],
      ['Gross Profit', report.grossProfit.toFixed(2)],
      ['Gross Margin %', `${report.grossMarginPct.toFixed(1)}%`],
      ['Prorated Fixed Overheads', report.proratedOverhead.total.toFixed(2)],
      ['Net Operating Profit (EBITDA)', report.netOperatingProfit.toFixed(2)],
      ['Net Margin %', `${report.netMarginPct.toFixed(1)}%`],
    ];
    const csv = '\uFEFF' + rows.map((r) => r.join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `filtr-pnl-${report.range.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-6">
      {/* Header & Controls */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-1.5">
          {(
            [
              ['TODAY', 'Today'],
              ['YESTERDAY', 'Yesterday'],
              ['LAST_7_DAYS', 'Last 7 Days'],
              ['THIS_MONTH', 'This Month (MTD)'],
              ['MONTH', 'Select Month'],
              ['CUSTOM', 'Custom'],
            ] as Array<[DateRangePreset, string]>
          ).map(([k, lbl]) => (
            <button
              key={k}
              type="button"
              onClick={() => setRange(k)}
              className={`rounded-xl border px-3.5 py-2 text-xs font-bold transition ${
                range === k
                  ? 'bg-amber-500 text-zinc-950 border-amber-500'
                  : isDark
                    ? 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white'
                    : 'bg-white border-zinc-200 text-zinc-600'
              }`}
            >
              {lbl}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          {range === 'MONTH' && (
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setMonthKey(getPreviousMonthKey(monthKey))}
                className="rounded-lg border border-zinc-700 px-2 py-1 text-xs font-bold"
              >
                ←
              </button>
              <span className="text-xs font-black px-2">{formatMonthDisplay(monthKey)}</span>
              <button
                type="button"
                onClick={() => setMonthKey(getNextMonthKey(monthKey))}
                className="rounded-lg border border-zinc-700 px-2 py-1 text-xs font-bold"
              >
                →
              </button>
            </div>
          )}
          {range === 'CUSTOM' && (
            <div className="flex items-center gap-1.5">
              <input
                type="date"
                value={customStart}
                onChange={(e) => setCustomStart(e.target.value)}
                className="rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-1.5 text-xs"
              />
              <input
                type="date"
                value={customEnd}
                onChange={(e) => setCustomEnd(e.target.value)}
                className="rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-1.5 text-xs"
              />
            </div>
          )}

          <button
            type="button"
            onClick={handleOpenFixedCosts}
            className="flex items-center gap-1.5 rounded-xl border border-amber-500/40 bg-amber-500/15 px-3 py-2 text-xs font-bold text-amber-500 hover:bg-amber-500/25"
          >
            <Settings className="h-3.5 w-3.5" />
            <span>Set Fixed Costs</span>
          </button>

          <button
            type="button"
            onClick={exportPnlCsv}
            disabled={!report}
            className={`flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-bold ${
              isDark ? 'border-zinc-800 bg-zinc-900 text-zinc-200' : 'border-zinc-200 bg-white'
            }`}
          >
            <Download className="h-3.5 w-3.5" />
            <span>CSV</span>
          </button>
        </div>
      </div>

      {loading && !report ? (
        <div className="py-12 text-center text-xs text-zinc-500">Computing P&L...</div>
      ) : report ? (
        <>
          {/* Hero KPIs */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            <StatCard
              label="Net Operating Profit (EBITDA)"
              value={formatInr(report.netOperatingProfit)}
              subtitle={`${report.netMarginPct >= 0 ? '+' : ''}${report.netMarginPct.toFixed(1)}% Net Margin`}
              accent={report.netOperatingProfit >= 0 ? 'emerald' : 'rose'}
              icon={<TrendingUp className="h-4 w-4" />}
              isDark={isDark}
            />
            <StatCard
              label="Net Revenue (Excl. 5% GST)"
              value={formatInr(report.netSales)}
              subtitle={`Gross: ${formatInr(report.grossSales)} (${report.ordersCount} orders)`}
              accent="amber"
              isDark={isDark}
            />
            <StatCard
              label="Recipe COGS"
              value={formatInr(report.recipeCogs)}
              subtitle={`Gross Profit: ${formatInr(report.grossProfit)} (${report.grossMarginPct.toFixed(1)}%)`}
              accent="blue"
              isDark={isDark}
            />
            <StatCard
              label="Prorated Overheads"
              value={formatInr(report.proratedOverhead.total)}
              subtitle={`${formatInr(report.dailyFixedCost)}/day × ${report.proratedOverhead.daysCount}d`}
              accent="purple"
              isDark={isDark}
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
            {/* P&L Waterfall */}
            <div className={`rounded-2xl border p-5 space-y-3 ${cardSurface}`}>
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-black">P&L Waterfall Statement</h3>
                <span className="text-xs text-zinc-400">
                  {report.proratedOverhead.monthKeyDescription}
                </span>
              </div>
              <div className="divide-y divide-zinc-200 dark:divide-zinc-800 text-xs">
                <div className="py-2.5 flex justify-between">
                  <span>[+] Gross Customer Billings</span>
                  <span className="font-bold">{formatInr(report.grossSales)}</span>
                </div>
                <div className="py-2.5 flex justify-between text-rose-400">
                  <span>[-] 5% GST Tax Remittance (Gross × 5/105)</span>
                  <span className="font-bold">-{formatInr(report.gstAmount)}</span>
                </div>
                <div className="py-2.5 flex justify-between font-black">
                  <span>[=] Net Cafe Operating Revenue</span>
                  <span>{formatInr(report.netSales)}</span>
                </div>
                <div className="py-2.5 flex justify-between text-amber-400">
                  <span>[-] Recipe Ingredients (COGS)</span>
                  <span className="font-bold">-{formatInr(report.recipeCogs)}</span>
                </div>
                <div className="py-2.5 flex justify-between font-black text-emerald-500">
                  <span>[=] Gross Margin ({report.grossMarginPct.toFixed(1)}%)</span>
                  <span>{formatInr(report.grossProfit)}</span>
                </div>
                <div className="py-2.5 flex justify-between text-purple-400">
                  <span>
                    [-] Prorated Fixed Overheads ({report.proratedOverhead.daysCount}d)
                  </span>
                  <span className="font-bold">
                    -{formatInr(report.proratedOverhead.total)}
                  </span>
                </div>
                <div
                  className={`pt-3 flex justify-between text-base font-black ${
                    report.netOperatingProfit >= 0 ? 'text-emerald-500' : 'text-rose-500'
                  }`}
                >
                  <span>NET OPERATING PROFIT</span>
                  <span>{formatInr(report.netOperatingProfit)}</span>
                </div>
              </div>
            </div>

            {/* Overheads & Break-Even */}
            <div className="space-y-5">
              <div className={`rounded-2xl border p-5 space-y-3 ${cardSurface}`}>
                <h3 className="text-sm font-black">Break-Even Daily Run Rate</h3>
                <div className="flex justify-between text-xs">
                  <div>
                    <span className="text-zinc-400 block">Current Daily Sales</span>
                    <span className="text-base font-black">
                      {formatInr(report.currentDailySalesRunRate)}/day
                    </span>
                  </div>
                  <div className="text-right">
                    <span className="text-zinc-400 block">Break-Even Target</span>
                    <span className="text-base font-black text-amber-500">
                      {formatInr(report.breakEvenDailySales)}/day
                    </span>
                  </div>
                </div>
                <div className="h-3 rounded-full bg-zinc-200 dark:bg-zinc-800 overflow-hidden">
                  <div
                    className={`h-full rounded-full ${
                      report.currentDailySalesRunRate >= report.breakEvenDailySales
                        ? 'bg-emerald-500'
                        : 'bg-amber-500'
                    }`}
                    style={{
                      width: `${Math.min(100, Math.round(report.breakEvenProgress * 100))}%`,
                    }}
                  />
                </div>
              </div>

              <div className={`rounded-2xl border p-5 space-y-2.5 ${cardSurface}`}>
                <h3 className="text-sm font-black">Prorated Overhead Breakdown</h3>
                <div className="text-xs space-y-1.5">
                  <div className="flex justify-between">
                    <span className="text-zinc-400">Shop Rent</span>
                    <span className="font-bold">{formatInr(report.proratedOverhead.rent)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-400">Electricity</span>
                    <span className="font-bold">
                      {formatInr(report.proratedOverhead.electricity)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-400">
                      Staff Salaries ({report.proratedOverhead.staffCount})
                    </span>
                    <span className="font-bold">
                      {formatInr(report.proratedOverhead.staffSalaries)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-400">Maid / Cleaning</span>
                    <span className="font-bold">
                      {formatInr(report.proratedOverhead.maidSalary)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-zinc-400">Misc & Maintenance</span>
                    <span className="font-bold">
                      {formatInr(report.proratedOverhead.miscellaneous)}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Top Ingredients Consumed */}
          {report.topIngredients.length > 0 && (
            <div className={`rounded-2xl border p-5 ${cardSurface}`}>
              <h3 className="text-sm font-black mb-3">Top Ingredients Consumed (COGS)</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                {report.topIngredients.map((ing) => (
                  <div
                    key={ing.itemId}
                    className={`rounded-xl border p-3 ${
                      isDark ? 'border-zinc-800 bg-zinc-950/50' : 'border-zinc-200 bg-zinc-50'
                    }`}
                  >
                    <p className="text-xs font-bold truncate">{ing.name}</p>
                    <p className="text-[11px] text-zinc-400 mt-0.5">{ing.usageDetail}</p>
                    <p className="text-sm font-black text-amber-500 mt-1">
                      {formatInr(ing.cost)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      ) : null}

      {/* Fixed Costs Modal */}
      <Modal
        open={costModalOpen}
        onClose={() => setCostModalOpen(false)}
        title="Monthly Fixed Overheads"
        subtitle="Prorated day-by-day across P&L reports"
        isDark={isDark}
      >
        <form onSubmit={handleSaveFixedCosts} className="space-y-4">
          <div>
            <label className="block text-xs font-bold mb-1">Month (YYYY-MM)</label>
            <input
              type="month"
              value={editMonthKey}
              onChange={(e) => populateModalForMonth(e.target.value, fixedCostsMap)}
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white"
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold">Staff Members & Monthly Salaries</label>
              <button
                type="button"
                onClick={() =>
                  setStaffRows((prev) => [
                    ...prev,
                    {
                      id: `staff_${Date.now()}`,
                      name: `Staff ${prev.length + 1}`,
                      salary: 15000,
                    },
                  ])
                }
                className="flex items-center gap-1 text-xs font-bold text-amber-500"
              >
                <Plus className="h-3.5 w-3.5" /> Add Staff
              </button>
            </div>
            {staffRows.map((s, idx) => (
              <div key={s.id} className="flex items-center gap-2">
                <input
                  type="text"
                  value={s.name}
                  onChange={(e) =>
                    setStaffRows((prev) =>
                      prev.map((row, i) => (i === idx ? { ...row, name: e.target.value } : row))
                    )
                  }
                  placeholder="Name"
                  className="flex-1 rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
                />
                <input
                  type="number"
                  value={s.salary}
                  onChange={(e) =>
                    setStaffRows((prev) =>
                      prev.map((row, i) =>
                        i === idx ? { ...row, salary: Number(e.target.value || 0) } : row
                      )
                    )
                  }
                  className="w-28 rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
                />
                {staffRows.length > 1 && (
                  <button
                    type="button"
                    onClick={() =>
                      setStaffRows((prev) => prev.filter((_, i) => i !== idx))
                    }
                    className="p-1.5 text-rose-400"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                )}
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold mb-1">Shop Rent (₹/mo)</label>
              <input
                type="number"
                value={rent}
                onChange={(e) => setRent(e.target.value)}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Electricity (₹/mo)</label>
              <input
                type="number"
                value={electricity}
                onChange={(e) => setElectricity(e.target.value)}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Maid / Cleaning (₹/mo)</label>
              <input
                type="number"
                value={maidSalary}
                onChange={(e) => setMaidSalary(e.target.value)}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Misc (₹/mo)</label>
              <input
                type="number"
                value={miscellaneous}
                onChange={(e) => setMiscellaneous(e.target.value)}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold mb-1">Notes</label>
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
            />
          </div>

          <button
            type="submit"
            disabled={savingCost}
            className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
          >
            {savingCost ? 'Saving...' : `Save Fixed Costs for ${editMonthKey}`}
          </button>
        </form>
      </Modal>
    </div>
  );
}
