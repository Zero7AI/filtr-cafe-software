'use client';

import React, { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import {
  IndianRupee,
  ShoppingBag,
  AlertTriangle,
  Flame,
  Users,
  QrCode,
  DollarSign,
  Refrigerator,
  Download,
  ArrowUpRight,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import StatCard from '@/components/admin/StatCard';
import SimpleBarChart from '@/components/admin/SimpleBarChart';
import { DateRangePreset, formatInr, formatIstTime, getIstMonthKey } from '@/lib/domain/ist';
import { OrderWithItems } from '@/lib/domain/types';

interface OverviewResponse {
  range: DateRangePreset;
  todayKey: string;
  kpis: {
    grossRevenue: number;
    paidRevenue: number;
    unpaidRevenue: number;
    cashRevenue: number;
    upiRevenue: number;
    totalOrders: number;
    unpaidCount: number;
    avgOrderValue: number;
    activeKdsCount: number;
  };
  revenueSeries: Array<{ label: string; revenue: number; orders: number }>;
  topItems: Array<{ name: string; qty: number; revenue: number; category: string }>;
  categories: Array<{ category: string; qty: number; revenue: number }>;
  alerts: {
    outOfStockCount: number;
    lowStockCount: number;
    priceHikeCount: number;
    topAlerts: Array<{
      itemName: string;
      stockAlertLabel: string;
      priceAlertLabel: string;
      stockAlertLevel: string;
      priceAlertLevel: string;
    }>;
  };
  team: {
    activeRosterCount: number;
    onDutyCount: number;
    checkedOutCount: number;
    onDutyStaff: Array<{ staffName: string; checkInTimestamp: number }>;
  };
  kitchen: {
    openingStatus: string;
    openingBy: string;
    closingStatus: string;
    closingBy: string;
  };
  recentOrders: OrderWithItems[];
}

export default function AdminOverviewPage() {
  const { isDark, refreshTick } = useAdminContext();
  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [monthKey, setMonthKey] = useState<string>(getIstMonthKey());
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [data, setData] = useState<OverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadOverview = useCallback(async () => {
    try {
      const qs = new URLSearchParams({ range });
      if (range === 'MONTH') qs.set('monthKey', monthKey);
      if (range === 'CUSTOM' && customStart) {
        qs.set('start', customStart);
        qs.set('end', customEnd || customStart);
      }
      const res = await fetch(`/api/admin/overview?${qs.toString()}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        setData(await res.json());
      }
    } finally {
      setLoading(false);
    }
  }, [range, monthKey, customStart, customEnd]);

  useEffect(() => {
    loadOverview();
  }, [loadOverview, refreshTick]);

  const exportCsv = () => {
    if (!data) return;
    const rows = [
      ['FILTR COFFEE ANALYTICS REPORT', `Range: ${data.range}`],
      ['Gross Revenue', String(data.kpis.grossRevenue)],
      ['Paid Revenue', String(data.kpis.paidRevenue)],
      ['Cash Revenue', String(data.kpis.cashRevenue)],
      ['UPI Revenue', String(data.kpis.upiRevenue)],
      ['Unpaid Revenue', String(data.kpis.unpaidRevenue)],
      ['Total Orders', String(data.kpis.totalOrders)],
      ['Average Order Value', data.kpis.avgOrderValue.toFixed(2)],
      [],
      ['Top Items', 'Category', 'Qty', 'Revenue'],
      ...data.topItems.map((it) => [it.name, it.category, String(it.qty), String(it.revenue)]),
    ];
    const csv = '\uFEFF' + rows.map((r) => r.join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `filtr-overview-${data.range.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-6">
      {/* Range Selector & Export */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-1.5">
          {(
            [
              ['TODAY', 'Today'],
              ['YESTERDAY', 'Yesterday'],
              ['LAST_7_DAYS', 'Last 7 Days'],
              ['THIS_MONTH', 'This Month'],
              ['MONTH', 'Month'],
              ['CUSTOM', 'Custom'],
            ] as Array<[DateRangePreset, string]>
          ).map(([key, label]) => (
            <button
              key={key}
              type="button"
              onClick={() => setRange(key)}
              className={`rounded-xl px-3.5 py-2 text-xs font-bold border transition ${
                range === key
                  ? 'bg-amber-500 text-zinc-950 border-amber-500 shadow-md shadow-amber-500/20'
                  : isDark
                    ? 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white'
                    : 'bg-white border-zinc-200 text-zinc-600 hover:text-zinc-900'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          {range === 'MONTH' && (
            <input
              type="month"
              value={monthKey}
              onChange={(e) => setMonthKey(e.target.value)}
              className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
                isDark
                  ? 'bg-zinc-900 border-zinc-800 text-white'
                  : 'bg-white border-zinc-200 text-zinc-900'
              }`}
            />
          )}
          {range === 'CUSTOM' && (
            <div className="flex items-center gap-1.5">
              <input
                type="date"
                value={customStart}
                onChange={(e) => setCustomStart(e.target.value)}
                className={`rounded-xl border px-2.5 py-1.5 text-xs ${
                  isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
                }`}
              />
              <span className="text-xs text-zinc-500">to</span>
              <input
                type="date"
                value={customEnd}
                onChange={(e) => setCustomEnd(e.target.value)}
                className={`rounded-xl border px-2.5 py-1.5 text-xs ${
                  isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
                }`}
              />
            </div>
          )}

          <button
            type="button"
            onClick={exportCsv}
            disabled={!data}
            className={`flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-bold transition ${
              isDark
                ? 'border-zinc-800 bg-zinc-900 text-zinc-200 hover:bg-zinc-800'
                : 'border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-100'
            }`}
          >
            <Download className="h-3.5 w-3.5 text-amber-500" />
            <span>Export CSV</span>
          </button>
        </div>
      </div>

      {loading && !data ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div
              key={i}
              className={`h-28 rounded-2xl border animate-pulse ${cardSurface}`}
            />
          ))}
        </div>
      ) : data ? (
        <>
          {/* KPI Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            <StatCard
              label="Gross Revenue"
              value={formatInr(data.kpis.grossRevenue)}
              subtitle={`Paid: ${formatInr(data.kpis.paidRevenue)} • AOV: ${formatInr(data.kpis.avgOrderValue)}`}
              accent="amber"
              icon={<IndianRupee className="h-4 w-4" />}
              isDark={isDark}
            />
            <StatCard
              label="Orders & Active KDS"
              value={`${data.kpis.totalOrders} Orders`}
              subtitle={`${data.kpis.activeKdsCount} active in kitchen • ${data.kpis.unpaidCount} unpaid`}
              accent={data.kpis.activeKdsCount > 0 ? 'blue' : 'emerald'}
              icon={<ShoppingBag className="h-4 w-4" />}
              isDark={isDark}
            />
            <StatCard
              label="UPI vs Cash Split"
              value={formatInr(data.kpis.upiRevenue)}
              subtitle={`UPI • Cash: ${formatInr(data.kpis.cashRevenue)} • Unpaid: ${formatInr(data.kpis.unpaidRevenue)}`}
              accent="emerald"
              icon={<QrCode className="h-4 w-4" />}
              isDark={isDark}
            />
            <StatCard
              label="Inventory & Price Alerts"
              value={`${data.alerts.outOfStockCount + data.alerts.lowStockCount} Stock`}
              subtitle={`${data.alerts.outOfStockCount} out of stock • ${data.alerts.priceHikeCount} price hikes`}
              accent={
                data.alerts.outOfStockCount > 0 || data.alerts.priceHikeCount > 0
                  ? 'rose'
                  : 'emerald'
              }
              icon={<AlertTriangle className="h-4 w-4" />}
              isDark={isDark}
            />
          </div>

          {/* Chart + Live Store Pulse */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
            <div className={`lg:col-span-2 rounded-2xl border p-5 ${cardSurface}`}>
              <div className="flex items-center justify-between mb-3">
                <div>
                  <h3 className="text-sm font-black">Revenue Trend</h3>
                  <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                    IST breakdown across selected period
                  </p>
                </div>
                <Link
                  href="/admin/pnl"
                  className="flex items-center gap-1 text-xs font-bold text-amber-500 hover:underline"
                >
                  <span>Full P&L</span>
                  <ArrowUpRight className="h-3.5 w-3.5" />
                </Link>
              </div>
              <SimpleBarChart data={data.revenueSeries} isDark={isDark} height={210} />
            </div>

            <div className={`rounded-2xl border p-5 space-y-4 ${cardSurface}`}>
              <h3 className="text-sm font-black">Store Operations Pulse</h3>

              <div
                className={`rounded-xl border p-3 ${
                  isDark ? 'border-zinc-800 bg-zinc-950/60' : 'border-zinc-200 bg-zinc-50'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold flex items-center gap-1.5">
                    <Users className="h-3.5 w-3.5 text-emerald-500" />
                    Staff Attendance ({data.todayKey})
                  </span>
                  <Link href="/admin/team" className="text-[11px] font-bold text-amber-500">
                    Manage →
                  </Link>
                </div>
                <p className="mt-1.5 text-xs">
                  <strong>{data.team.onDutyCount}</strong> on duty •{' '}
                  <strong>{data.team.checkedOutCount}</strong> completed •{' '}
                  {data.team.activeRosterCount} active roster
                </p>
                {data.team.onDutyStaff.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1">
                    {data.team.onDutyStaff.map((s) => (
                      <span
                        key={s.staffName}
                        className="rounded-md bg-emerald-500/15 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-bold text-emerald-400"
                      >
                        {s.staffName} ({formatIstTime(s.checkInTimestamp)})
                      </span>
                    ))}
                  </div>
                )}
              </div>

              <div
                className={`rounded-xl border p-3 ${
                  isDark ? 'border-zinc-800 bg-zinc-950/60' : 'border-zinc-200 bg-zinc-50'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold flex items-center gap-1.5">
                    <Refrigerator className="h-3.5 w-3.5 text-sky-400" />
                    Daily Kitchen Check
                  </span>
                  <Link href="/admin/kitchen" className="text-[11px] font-bold text-amber-500">
                    Inspect →
                  </Link>
                </div>
                <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[10px] text-zinc-500 block">OPENING</span>
                    <span className="font-bold">{data.kitchen.openingStatus}</span>
                  </div>
                  <div>
                    <span className="text-[10px] text-zinc-500 block">CLOSING</span>
                    <span className="font-bold">{data.kitchen.closingStatus}</span>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <Link
                  href="/admin/kds"
                  className="flex items-center justify-center gap-1.5 rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950 hover:bg-amber-400 transition"
                >
                  <Flame className="h-3.5 w-3.5" />
                  <span>Open KDS ({data.kpis.activeKdsCount})</span>
                </Link>
                <Link
                  href="/admin/orders"
                  className={`flex items-center justify-center gap-1.5 rounded-xl border py-2.5 text-xs font-bold transition ${
                    isDark
                      ? 'border-zinc-700 bg-zinc-800 text-white hover:bg-zinc-700'
                      : 'border-zinc-300 bg-white text-zinc-800 hover:bg-zinc-100'
                  }`}
                >
                  <DollarSign className="h-3.5 w-3.5 text-emerald-500" />
                  <span>Orders ({data.kpis.totalOrders})</span>
                </Link>
              </div>
            </div>
          </div>

          {/* Top Items, Categories & Alerts */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
            <div className={`rounded-2xl border p-5 ${cardSurface}`}>
              <h3 className="text-sm font-black mb-3">Top Selling Items</h3>
              {data.topItems.length === 0 ? (
                <p className="text-xs text-zinc-500">No items sold in this period.</p>
              ) : (
                <div className="space-y-2.5">
                  {data.topItems.slice(0, 7).map((it, idx) => (
                    <div key={it.name} className="flex items-center justify-between text-xs">
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="w-5 text-center font-black text-amber-500">
                          {idx + 1}.
                        </span>
                        <div className="min-w-0">
                          <p className="font-bold truncate">{it.name}</p>
                          <p className="text-[10px] text-zinc-500">
                            {it.category} • {it.qty} sold
                          </p>
                        </div>
                      </div>
                      <span className="font-black shrink-0">{formatInr(it.revenue)}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className={`rounded-2xl border p-5 ${cardSurface}`}>
              <h3 className="text-sm font-black mb-3">Sales by Category</h3>
              {data.categories.length === 0 ? (
                <p className="text-xs text-zinc-500">No category sales yet.</p>
              ) : (
                <div className="space-y-3">
                  {data.categories.map((c) => {
                    const pct =
                      data.kpis.grossRevenue > 0
                        ? Math.round((c.revenue / data.kpis.grossRevenue) * 100)
                        : 0;
                    return (
                      <div key={c.category} className="space-y-1">
                        <div className="flex justify-between text-xs font-bold">
                          <span>{c.category}</span>
                          <span>
                            {formatInr(c.revenue)} ({pct}%)
                          </span>
                        </div>
                        <div className="h-2 rounded-full bg-zinc-200 dark:bg-zinc-800 overflow-hidden">
                          <div
                            className="h-full bg-amber-500 rounded-full"
                            style={{ width: `${Math.min(100, pct)}%` }}
                          />
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            <div className={`rounded-2xl border p-5 ${cardSurface}`}>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-black">Priority Inventory Alerts</h3>
                <Link href="/admin/inventory" className="text-xs font-bold text-amber-500">
                  View All →
                </Link>
              </div>
              {data.alerts.topAlerts.length === 0 ? (
                <p className="text-xs text-emerald-500 font-semibold">
                  All raw materials stocked & prices stable ✓
                </p>
              ) : (
                <div className="space-y-2">
                  {data.alerts.topAlerts.map((a) => (
                    <div
                      key={a.itemName}
                      className={`rounded-xl border p-2.5 text-xs ${
                        isDark ? 'border-zinc-800 bg-zinc-950/60' : 'border-zinc-200 bg-zinc-50'
                      }`}
                    >
                      <div className="flex items-center justify-between font-bold">
                        <span className="truncate pr-2">{a.itemName}</span>
                        <span
                          className={`text-[10px] px-1.5 py-0.5 rounded font-black ${
                            a.stockAlertLevel === 'OUT_OF_STOCK'
                              ? 'bg-rose-500/20 text-rose-400'
                              : a.stockAlertLevel !== 'HEALTHY'
                                ? 'bg-amber-500/20 text-amber-400'
                                : 'bg-zinc-700 text-zinc-300'
                          }`}
                        >
                          {a.stockAlertLabel}
                        </span>
                      </div>
                      {a.priceAlertLevel === 'HIGH_HIKE' && (
                        <p className="text-[10px] font-bold text-rose-400 mt-1">
                          {a.priceAlertLabel}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Recent Orders */}
          <div className={`rounded-2xl border p-5 ${cardSurface}`}>
            <div className="flex items-center justify-between mb-3">
              <h3 className="text-sm font-black">Recent Orders</h3>
              <Link href="/admin/orders" className="text-xs font-bold text-amber-500">
                Open Full Orders Ledger →
              </Link>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead>
                  <tr className="border-b border-zinc-200 dark:border-zinc-800 text-zinc-500 uppercase text-[10px]">
                    <th className="py-2 pr-3">Order ID</th>
                    <th className="py-2 pr-3">Time</th>
                    <th className="py-2 pr-3">Customer</th>
                    <th className="py-2 pr-3">Items</th>
                    <th className="py-2 pr-3">Status</th>
                    <th className="py-2 text-right">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800/70">
                  {data.recentOrders.map(({ order, items }) => (
                    <tr key={order.order_id}>
                      <td className="py-2.5 pr-3 font-mono font-bold">
                        {order.order_id}
                      </td>
                      <td className="py-2.5 pr-3 text-zinc-400">
                        {formatIstTime(order.timestamp)}
                      </td>
                      <td className="py-2.5 pr-3 font-semibold">{order.customer_name}</td>
                      <td className="py-2.5 pr-3 text-zinc-400 max-w-xs truncate">
                        {items.map((i) => `${i.quantity}x ${i.item_name}`).join(', ')}
                      </td>
                      <td className="py-2.5 pr-3">
                        <span
                          className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                            order.is_paid
                              ? 'bg-emerald-500/15 text-emerald-500'
                              : 'bg-rose-500/15 text-rose-500'
                          }`}
                        >
                          {order.is_paid ? order.payment_mode : 'UNPAID'}
                        </span>
                      </td>
                      <td className="py-2.5 text-right font-black text-amber-500">
                        {formatInr(order.total_amount)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
