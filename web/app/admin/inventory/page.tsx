'use client';

import React, { useEffect, useState, useCallback, useMemo } from 'react';
import {
  Plus,
  Download,
  Trash2,
  Receipt,
  MessageSquare,
  TrendingUp,
  ExternalLink,
  RefreshCw,
  Search,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import Modal from '@/components/admin/Modal';
import { useToast } from '@/components/admin/ToastContext';
import {
  BillLedgerRow,
  formatBillCell,
  SkuPriceHistoryMatrix,
} from '@/lib/domain/inventory';
import { formatInr } from '@/lib/domain/ist';
import { InventoryItem, WhatsappMessage } from '@/lib/domain/types';

export default function AdminInventoryPage() {
  const { isDark, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [tab, setTab] = useState<'MATRIX' | 'LEDGER' | 'WHATSAPP'>('MATRIX');
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [matrix, setMatrix] = useState<SkuPriceHistoryMatrix>({
    maxBillColumns: 0,
    rows: [],
  });
  const [ledger, setLedger] = useState<BillLedgerRow[]>([]);
  const [googleSheetsUrl, setGoogleSheetsUrl] = useState(
    'https://docs.google.com/spreadsheets/d/1UsUM0SLdDf4uHXhJuao96Twkd-Eq5Yxl4_VHM_sFw90/edit?gid=0#gid=0'
  );
  const [webhookConfigured, setWebhookConfigured] = useState(false);
  const [waMessages, setWaMessages] = useState<WhatsappMessage[]>([]);
  const [pendingWaCount, setPendingWaCount] = useState(0);
  const [includeDates, setIncludeDates] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [syncingSheet, setSyncingSheet] = useState(false);
  const [retryingWa, setRetryingWa] = useState(false);

  const [billModal, setBillModal] = useState(false);
  const [supplierName, setSupplierName] = useState('');
  const [billNotes, setBillNotes] = useState('');
  const [billLines, setBillLines] = useState<
    Array<{ item_name: string; quantity: number; unit: string; unit_price: number }>
  >([{ item_name: '', quantity: 1, unit: 'kg', unit_price: 0 }]);

  const loadData = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/inventory', { cache: 'no-store' });
      if (res.ok) {
        const d = await res.json();
        setItems(d.items || []);
        setMatrix(d.skuMatrix || { maxBillColumns: 0, rows: [] });
        setLedger(d.billLedger || []);
        if (d.googleSheetsUrl) setGoogleSheetsUrl(d.googleSheetsUrl);
        setWebhookConfigured(Boolean(d.googleSheetsWebhookConfigured));
        setWaMessages(d.whatsappMessages || []);
        setPendingWaCount(Number(d.pendingWhatsappCount || 0));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData, refreshTick]);

  const filteredMatrixRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return matrix.rows;
    return matrix.rows.filter((r) => r.skuName.toLowerCase().includes(q));
  }, [matrix.rows, searchQuery]);

  const filteredLedgerRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return ledger;
    return ledger.filter(
      (l) =>
        l.orderId.toLowerCase().includes(q) ||
        l.supplierName.toLowerCase().includes(q) ||
        l.matchedSkuName.toLowerCase().includes(q) ||
        l.rawItemName.toLowerCase().includes(q)
    );
  }, [ledger, searchQuery]);

  const syncGoogleSheetNow = async () => {
    setSyncingSheet(true);
    try {
      const res = await fetch('/api/admin/inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'syncGoogleSheet',
          includeDateInCells: includeDates,
        }),
      });
      const d = await res.json().catch(() => ({}));
      if (res.ok) {
        pushToast(`Synced ${d.count || matrix.rows.length} SKUs to live Google Sheet`, 'success');
      } else {
        pushToast(d.error || 'Sheet sync failed', 'error');
      }
    } finally {
      setSyncingSheet(false);
    }
  };

  const retryWhatsappPending = async () => {
    setRetryingWa(true);
    try {
      const res = await fetch('/api/admin/inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'retryWhatsappPending' }),
      });
      const d = await res.json().catch(() => ({}));
      if (res.ok) {
        pushToast('Triggered WhatsApp pending bill retry', 'success');
        loadData();
      } else {
        pushToast(d.error || 'Retry trigger failed', 'error');
      }
    } finally {
      setRetryingWa(false);
    }
  };

  const createPurchaseBill = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/inventory', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'createPurchaseBill',
        supplier_name: supplierName,
        notes: billNotes,
        items: billLines.map((l) => ({
          ...l,
          subtotal: l.quantity * l.unit_price,
        })),
      }),
    });
    if (res.ok) {
      pushToast('Purchase bill saved & SKU price history updated', 'success');
      setBillModal(false);
      setSupplierName('');
      setBillNotes('');
      setBillLines([{ item_name: '', quantity: 1, unit: 'kg', unit_price: 0 }]);
      loadData();
    } else {
      pushToast('Failed to save bill', 'error');
    }
  };

  const deleteBill = async (orderId: string) => {
    if (!confirm(`Delete purchase bill ${orderId}?`)) return;
    const res = await fetch('/api/admin/inventory', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'deletePurchaseBill', orderId }),
    });
    if (res.ok) {
      pushToast(`Deleted ${orderId}`, 'success');
      loadData();
    }
  };

  const billColsCount = Math.max(1, matrix.maxBillColumns);
  const hikeCount = matrix.rows.filter((r) => r.isPriceHike).length;
  const anomalyCount = matrix.rows.filter((r) => r.hasUnitAnomaly).length;

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      {/* Live Sheet Card (Direct Jump Link + .xlsx Export + Sync) */}
      <div className={`rounded-2xl border p-4 flex flex-wrap items-center justify-between gap-3 ${cardSurface}`}>
        <div>
          <p className="text-[11px] font-black uppercase tracking-wider text-emerald-400">
            Purchase Tracking • Live Excel & Supplier Price History
          </p>
          <p className="text-xs text-zinc-400 mt-0.5">
            {matrix.rows.length} SKUs tracked •{' '}
            <span className={hikeCount > 0 ? 'text-rose-400 font-bold' : ''}>
              {hikeCount} price hike(s)
            </span>
            {anomalyCount > 0 ? ` • ${anomalyCount} CHECK UNIT flag(s)` : ''}
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <a
            href={googleSheetsUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-1.5 rounded-xl bg-emerald-500 px-4 py-2 text-xs font-black text-zinc-950 hover:bg-emerald-400 transition"
          >
            <ExternalLink className="h-3.5 w-3.5" />
            <span>Open Live Supplier Pricing Sheet</span>
          </a>

          <a
            href={`/api/admin/inventory/export-xlsx?includeDates=${includeDates}`}
            className={`flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-bold ${
              isDark ? 'border-zinc-700 bg-zinc-900 text-emerald-400 hover:bg-zinc-800' : 'border-zinc-300 bg-white text-emerald-700'
            }`}
          >
            <Download className="h-3.5 w-3.5" />
            <span>Download Excel (.xlsx)</span>
          </a>

          <button
            type="button"
            disabled={!webhookConfigured || syncingSheet}
            onClick={syncGoogleSheetNow}
            className={`flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-bold disabled:opacity-40 ${
              isDark ? 'border-zinc-700 bg-zinc-900 text-zinc-200' : 'border-zinc-300 bg-white'
            }`}
          >
            <RefreshCw className={`h-3.5 w-3.5 ${syncingSheet ? 'animate-spin' : ''}`} />
            <span>{syncingSheet ? 'Syncing...' : 'Sync Sheet Now'}</span>
          </button>

          <button
            type="button"
            onClick={() => setBillModal(true)}
            className="flex items-center gap-1.5 rounded-xl bg-amber-500 px-3 py-2 text-xs font-black text-zinc-950"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Bill</span>
          </button>
        </div>
      </div>

      {/* Tabs + Date Display Toggle + Search */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          {(
            [
              ['MATRIX', `SKU Price History (${matrix.rows.length})`, TrendingUp],
              ['LEDGER', `Bill Ledger (${ledger.length})`, Receipt],
              ['WHATSAPP', `WhatsApp Bot${pendingWaCount > 0 ? ` (${pendingWaCount} pending)` : ''}`, MessageSquare],
            ] as const
          ).map(([k, lbl, Icon]) => (
            <button
              key={k}
              type="button"
              onClick={() => setTab(k)}
              className={`flex items-center gap-1.5 rounded-xl border px-3.5 py-2 text-xs font-black ${
                tab === k
                  ? 'bg-amber-500 text-zinc-950 border-amber-500'
                  : isDark
                    ? 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white'
                    : 'bg-white border-zinc-200 text-zinc-600'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              <span>{lbl}</span>
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="h-3.5 w-3.5 text-zinc-500 absolute left-3 top-2.5" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search SKU or supplier..."
              className={`rounded-xl border pl-8 pr-3 py-1.5 text-xs ${
                isDark ? 'border-zinc-800 bg-zinc-900 text-white' : 'border-zinc-200 bg-white'
              }`}
            />
          </div>

          {tab === 'MATRIX' && (
            <button
              type="button"
              onClick={() => setIncludeDates((v) => !v)}
              className={`rounded-xl border px-3 py-1.5 text-xs font-bold ${
                includeDates
                  ? 'border-amber-500/50 bg-amber-500/15 text-amber-400'
                  : isDark
                    ? 'border-zinc-800 bg-zinc-900 text-zinc-300'
                    : 'border-zinc-200 bg-white'
              }`}
            >
              {includeDates ? 'Format: 480 (28 Oct)' : 'Format: Pure Numbers'}
            </button>
          )}
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading SKU Price History...</div>
      ) : tab === 'MATRIX' ? (
        <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[10px] uppercase text-zinc-500">
                  <th className={`py-3 px-4 sticky left-0 ${isDark ? 'bg-zinc-900' : 'bg-white'}`}>
                    SKU Name
                  </th>
                  <th className="py-3 px-3 whitespace-nowrap">Unit / Weight</th>
                  <th className="py-3 px-3 whitespace-nowrap">Latest Price (INR)</th>
                  <th className="py-3 px-3 whitespace-nowrap">Price Change vs First (%)</th>
                  {Array.from({ length: billColsCount }, (_, i) => (
                    <th key={i} className="py-3 px-3 whitespace-nowrap">
                      Bill {i + 1}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {filteredMatrixRows.map((r) => {
                  const pct = r.priceChangeVsFirstPct;
                  const pctText =
                    pct !== null ? `${pct > 0 ? '+' : ''}${pct.toFixed(1)}%` : '0.0%';
                  return (
                    <tr key={r.skuName}>
                      <td className={`py-2.5 px-4 font-bold whitespace-nowrap sticky left-0 ${isDark ? 'bg-zinc-900/95' : 'bg-white'}`}>
                        {r.skuName}
                      </td>
                      <td className="py-2.5 px-3 whitespace-nowrap text-zinc-400">
                        {r.unitWeightLabel}
                      </td>
                      <td className="py-2.5 px-3 font-black whitespace-nowrap">
                        {formatInr(r.latestPrice, 2)}
                      </td>
                      <td className="py-2.5 px-3 whitespace-nowrap">
                        <span
                          className={`rounded-md px-2 py-0.5 text-[11px] font-black ${
                            pct !== null && pct > 0
                              ? 'bg-rose-500/20 text-rose-400'
                              : pct !== null && pct < 0
                                ? 'bg-emerald-500/15 text-emerald-400'
                                : 'text-zinc-400'
                          }`}
                        >
                          {pctText}
                        </span>
                      </td>
                      {Array.from({ length: billColsCount }, (_, idx) => {
                        const b = r.bills[idx];
                        return (
                          <td
                            key={idx}
                            className={`py-2.5 px-3 font-mono whitespace-nowrap ${
                              b?.isUnitAnomaly
                                ? 'bg-amber-500/15 text-amber-400 font-bold'
                                : 'text-zinc-300'
                            }`}
                          >
                            {b ? formatBillCell(b, includeDates) : '—'}
                          </td>
                        );
                      })}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : tab === 'LEDGER' ? (
        <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[10px] uppercase text-zinc-500">
                  <th className="py-3 px-4">Bill ID</th>
                  <th className="py-3 px-3">Date</th>
                  <th className="py-3 px-3">Supplier</th>
                  <th className="py-3 px-3">Raw Item</th>
                  <th className="py-3 px-3">Matched SKU</th>
                  <th className="py-3 px-3">Qty</th>
                  <th className="py-3 px-3">Total</th>
                  <th className="py-3 px-3">Normalized</th>
                  <th className="py-3 px-3">Status</th>
                  <th className="py-3 px-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {filteredLedgerRows.map((row, idx) => (
                  <tr key={`${row.orderId}-${idx}`}>
                    <td className="py-2.5 px-4 font-mono font-bold text-amber-500 whitespace-nowrap">
                      {row.orderId}
                    </td>
                    <td className="py-2.5 px-3 whitespace-nowrap text-zinc-400">{row.dateFormatted}</td>
                    <td className="py-2.5 px-3 whitespace-nowrap">{row.supplierName}</td>
                    <td className="py-2.5 px-3 text-zinc-300">{row.rawItemName}</td>
                    <td className="py-2.5 px-3 font-bold">{row.matchedSkuName}</td>
                    <td className="py-2.5 px-3 whitespace-nowrap">
                      {row.quantity} {row.unit}
                    </td>
                    <td className="py-2.5 px-3 font-bold whitespace-nowrap">
                      {formatInr(row.subtotal, 2)}
                    </td>
                    <td className="py-2.5 px-3 font-black whitespace-nowrap">
                      {formatInr(row.normalizedPrice, 2)}
                    </td>
                    <td className="py-2.5 px-3 whitespace-nowrap">
                      <span
                        className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                          row.statusLabel === 'CHECK UNIT'
                            ? 'bg-amber-500/20 text-amber-400'
                            : row.statusLabel === 'HARDWARE / EXCLUDED'
                              ? 'bg-zinc-700/40 text-zinc-400'
                              : 'bg-emerald-500/15 text-emerald-400'
                        }`}
                      >
                        {row.statusLabel}
                      </span>
                    </td>
                    <td className="py-2.5 px-3 text-right">
                      <button
                        type="button"
                        onClick={() => deleteBill(row.orderId)}
                        className="p-1 text-zinc-500 hover:text-rose-500"
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="space-y-3">
          {pendingWaCount > 0 && (
            <div className="rounded-2xl border border-amber-500/40 bg-amber-500/10 p-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <p className="text-xs font-black text-amber-400">
                  {pendingWaCount} WhatsApp Bill(s) Waiting for Retry
                </p>
                <p className="text-[11px] text-zinc-300">
                  Once Gemini API access is healthy, click below to reprocess queued WhatsApp bills.
                </p>
              </div>
              <button
                type="button"
                disabled={retryingWa}
                onClick={retryWhatsappPending}
                className="rounded-xl bg-amber-500 px-3.5 py-2 text-xs font-black text-zinc-950 disabled:opacity-50"
              >
                {retryingWa ? 'Retrying...' : 'Retry Pending Bills'}
              </button>
            </div>
          )}

          {waMessages.map((m) => (
            <div key={m.id} className={`rounded-2xl border p-4 text-xs space-y-1 ${cardSurface}`}>
              <div className="flex items-center justify-between">
                <span className="font-bold">
                  {m.from_number} • {m.message_type}
                </span>
                <span
                  className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                    m.status === 'saved' || m.status === 'PROCESSED'
                      ? 'bg-emerald-500/15 text-emerald-500'
                      : m.status === 'retry_pending' || m.status === 'config_error'
                        ? 'bg-rose-500/15 text-rose-400'
                        : 'bg-amber-500/15 text-amber-400'
                  }`}
                >
                  {m.status}
                </span>
              </div>
              <p className="text-zinc-300">{m.message_body || '(Media attachment)'}</p>
              {m.inventory_order_id && (
                <p className="text-emerald-400 font-mono">Linked Bill: {m.inventory_order_id}</p>
              )}
              {m.error_message && <p className="text-rose-400">{m.error_message}</p>}
            </div>
          ))}
        </div>
      )}

      {/* New Purchase Bill Modal */}
      <Modal
        open={billModal}
        onClose={() => setBillModal(false)}
        title="Record Purchase Bill"
        subtitle="Updates Bill Ledger and recalculates SKU Price History"
        maxWidthClass="max-w-2xl"
        isDark={isDark}
      >
        <form onSubmit={createPurchaseBill} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold mb-1">Supplier Name</label>
              <input
                type="text"
                value={supplierName}
                onChange={(e) => setSupplierName(e.target.value)}
                required
                placeholder="e.g. Amrapur Dairy"
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Notes</label>
              <input
                type="text"
                value={billNotes}
                onChange={(e) => setBillNotes(e.target.value)}
                placeholder="Bill # / Ref"
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <span className="text-xs font-bold">Bill Line Items</span>
              <button
                type="button"
                onClick={() =>
                  setBillLines((prev) => [
                    ...prev,
                    { item_name: '', quantity: 1, unit: 'kg', unit_price: 0 },
                  ])
                }
                className="text-xs font-bold text-amber-500"
              >
                + Add Line
              </button>
            </div>

            {billLines.map((line, idx) => (
              <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                <input
                  type="text"
                  list="inv-names"
                  value={line.item_name}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) => (i === idx ? { ...l, item_name: e.target.value } : l))
                    )
                  }
                  placeholder="Ingredient"
                  required
                  className="col-span-5 rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-xs text-white"
                />
                <input
                  type="number"
                  step="0.01"
                  value={line.quantity}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) =>
                        i === idx ? { ...l, quantity: Number(e.target.value) } : l
                      )
                    )
                  }
                  className="col-span-2 rounded-xl border border-zinc-700 bg-zinc-900 px-2 py-2 text-xs text-white"
                />
                <input
                  type="text"
                  value={line.unit}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) => (i === idx ? { ...l, unit: e.target.value } : l))
                    )
                  }
                  className="col-span-2 rounded-xl border border-zinc-700 bg-zinc-900 px-2 py-2 text-xs text-white"
                />
                <input
                  type="number"
                  step="0.01"
                  value={line.unit_price}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) =>
                        i === idx ? { ...l, unit_price: Number(e.target.value) } : l
                      )
                    )
                  }
                  placeholder="Rate"
                  className="col-span-2 rounded-xl border border-zinc-700 bg-zinc-900 px-2 py-2 text-xs text-white"
                />
                <button
                  type="button"
                  onClick={() =>
                    billLines.length > 1 &&
                    setBillLines((prev) => prev.filter((_, i) => i !== idx))
                  }
                  className="col-span-1 text-rose-400"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            ))}
            <datalist id="inv-names">
              {items.map((it) => (
                <option key={it.id} value={it.name} />
              ))}
            </datalist>
          </div>

          <button
            type="submit"
            className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
          >
            Save Purchase Bill (Total:{' '}
            {formatInr(
              billLines.reduce((s, l) => s + l.quantity * l.unit_price, 0)
            )}
            )
          </button>
        </form>
      </Modal>
    </div>
  );
}
