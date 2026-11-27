'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Search,
  CheckCircle2,
  Coffee,
  CreditCard,
  Check,
  Receipt as ReceiptIcon,
  Printer,
  Trash2,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import { useToast } from '@/components/admin/ToastContext';
import OrderBillModal from '@/components/OrderBillModal';
import Receipt from '@/components/Receipt';
import { DateRangePreset, formatInr, formatIstDateTime } from '@/lib/domain/ist';
import { OrderWithItems } from '@/lib/domain/types';
import { defaultReceiptConfig, fetchReceiptConfig, ReceiptDesignConfig } from '@/lib/supabase';

export default function AdminOrdersPage() {
  const { isDark, theme, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [statusFilter, setStatusFilter] = useState<
    'ALL' | 'PENDING' | 'MADE' | 'UNPAID' | 'DELIVERED'
  >('ALL');
  const [search, setSearch] = useState('');
  const [orders, setOrders] = useState<OrderWithItems[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedBill, setSelectedBill] = useState<OrderWithItems | null>(null);
  const [printOrder, setPrintOrder] = useState<OrderWithItems | null>(null);
  const [receiptConfig, setReceiptConfig] =
    useState<ReceiptDesignConfig>(defaultReceiptConfig);

  const loadOrders = useCallback(async () => {
    try {
      const qs = new URLSearchParams({ range });
      if (range === 'CUSTOM' && customStart) {
        qs.set('start', customStart);
        qs.set('end', customEnd || customStart);
      }
      const res = await fetch(`/api/admin/orders?${qs.toString()}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        const data = await res.json();
        setOrders(data.orders || []);
      }
    } finally {
      setLoading(false);
    }
  }, [range, customStart, customEnd]);

  useEffect(() => {
    loadOrders();
    fetchReceiptConfig().then(setReceiptConfig);
  }, [loadOrders, refreshTick]);

  const filteredOrders = useMemo(() => {
    const byStatus = orders.filter(({ order }) => {
      if (statusFilter === 'PENDING') return !order.is_delivered;
      if (statusFilter === 'MADE') return order.is_made;
      if (statusFilter === 'UNPAID') return !order.is_paid;
      if (statusFilter === 'DELIVERED') return order.is_delivered;
      return true;
    });
    if (!search.trim()) return byStatus;
    const q = search.trim().toLowerCase();
    return byStatus.filter(
      ({ order, items }) =>
        order.order_id.toLowerCase().includes(q) ||
        order.customer_name.toLowerCase().includes(q) ||
        order.notes.toLowerCase().includes(q) ||
        items.some((i) => i.item_name.toLowerCase().includes(q))
    );
  }, [orders, statusFilter, search]);

  const totals = useMemo(() => {
    let gross = 0;
    let cash = 0;
    let upi = 0;
    let unpaid = 0;
    for (const { order } of orders) {
      gross += order.total_amount || 0;
      if (order.is_paid) {
        if (order.payment_mode === 'UPI') upi += order.total_amount || 0;
        else cash += order.total_amount || 0;
      } else {
        unpaid += order.total_amount || 0;
      }
    }
    return { gross, cash, upi, unpaid };
  }, [orders]);

  const patchOrder = async (
    orderId: string,
    updates: Record<string, unknown>,
    successMsg: string
  ) => {
    const res = await fetch('/api/admin/orders', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'updateOrder', orderId, updates }),
    });
    if (res.ok) {
      setOrders((prev) =>
        prev.map((o) =>
          o.order.order_id === orderId
            ? { ...o, order: { ...o.order, ...updates } }
            : o
        )
      );
      pushToast(successMsg, 'success');
    } else {
      pushToast('Failed to update order', 'error');
    }
  };

  const handleDelete = async (orderId: string) => {
    if (!confirm(`Delete order ${orderId} from database & Google Sheets?`)) return;
    const res = await fetch(
      `/api/admin/orders?orderId=${encodeURIComponent(orderId)}`,
      { method: 'DELETE' }
    );
    if (res.ok) {
      setOrders((prev) => prev.filter((o) => o.order.order_id !== orderId));
      pushToast(`Deleted ${orderId}`, 'success');
    } else {
      pushToast('Failed to delete order', 'error');
    }
  };

  const handlePrint = (ord: OrderWithItems) => {
    setPrintOrder(ord);
    setTimeout(() => window.print(), 300);
  };

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="no-print space-y-4">
        {/* Top Metrics Banner */}
        <div
          className={`rounded-2xl border p-4 flex flex-wrap items-center justify-between gap-4 ${
            isDark ? 'border-zinc-800 bg-zinc-900' : 'border-zinc-900 bg-zinc-950 text-white'
          }`}
        >
          <div className="flex flex-wrap items-center gap-5">
            <div>
              <span className="text-[10px] font-black uppercase text-zinc-400 block">
                Period Total
              </span>
              <span className="text-xl font-black text-amber-400">
                {formatInr(totals.gross)}
              </span>
            </div>
            <div>
              <span className="text-[10px] font-black uppercase text-zinc-400 block">
                Orders
              </span>
              <span className="text-xl font-black">{orders.length}</span>
            </div>
            <div className="text-xs font-bold space-x-3 text-zinc-300">
              <span>UPI: {formatInr(totals.upi)}</span>
              <span>•</span>
              <span>Cash: {formatInr(totals.cash)}</span>
              <span>•</span>
              <span className={totals.unpaid > 0 ? 'text-rose-400' : 'text-emerald-400'}>
                Unpaid: {formatInr(totals.unpaid)}
              </span>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-1.5">
            {(
              [
                ['TODAY', 'Today'],
                ['YESTERDAY', 'Yesterday'],
                ['LAST_7_DAYS', '7 Days'],
                ['THIS_MONTH', 'Month'],
                ['CUSTOM', 'Custom'],
              ] as Array<[DateRangePreset, string]>
            ).map(([k, lbl]) => (
              <button
                key={k}
                type="button"
                onClick={() => setRange(k)}
                className={`rounded-xl px-3 py-1.5 text-xs font-bold transition ${
                  range === k
                    ? 'bg-amber-500 text-zinc-950'
                    : 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
                }`}
              >
                {lbl}
              </button>
            ))}
          </div>
        </div>

        {range === 'CUSTOM' && (
          <div className="flex items-center gap-2">
            <input
              type="date"
              value={customStart}
              onChange={(e) => setCustomStart(e.target.value)}
              className={`rounded-xl border px-3 py-1.5 text-xs ${
                isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
              }`}
            />
            <span className="text-xs text-zinc-500">to</span>
            <input
              type="date"
              value={customEnd}
              onChange={(e) => setCustomEnd(e.target.value)}
              className={`rounded-xl border px-3 py-1.5 text-xs ${
                isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
              }`}
            />
          </div>
        )}

        {/* Filters & Search */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1">
            {(['ALL', 'PENDING', 'UNPAID', 'MADE', 'DELIVERED'] as const).map(
              (tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setStatusFilter(tab)}
                  className={`rounded-xl px-3 py-1.5 text-xs font-black border transition ${
                    statusFilter === tab
                      ? 'bg-amber-500 text-zinc-950 border-amber-500'
                      : isDark
                        ? 'bg-zinc-900 border-zinc-800 text-zinc-400 hover:text-white'
                        : 'bg-white border-zinc-200 text-zinc-600'
                  }`}
                >
                  {tab}
                </button>
              )
            )}
          </div>

          <div className="relative w-full sm:w-72">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search order ID, customer, item..."
              className={`w-full rounded-xl border pl-9 pr-3 py-2 text-xs focus:outline-none focus:border-amber-500 ${
                isDark
                  ? 'bg-zinc-900 border-zinc-800 text-white placeholder-zinc-500'
                  : 'bg-white border-zinc-200 text-zinc-900'
              }`}
            />
          </div>
        </div>

        {/* Orders Grid */}
        {loading ? (
          <div className="py-12 text-center text-xs text-zinc-500">
            Loading orders...
          </div>
        ) : filteredOrders.length === 0 ? (
          <div className={`rounded-2xl border p-10 text-center ${cardSurface}`}>
            <CheckCircle2 className="h-10 w-10 text-zinc-500 mx-auto mb-2" />
            <p className="text-sm font-bold">No orders found for this filter</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {filteredOrders.map((owi) => {
              const { order, items } = owi;
              const token = order.order_id.split('-').pop() || '0000';
              return (
                <div
                  key={order.order_id}
                  className={`rounded-2xl border p-4 flex flex-col justify-between space-y-3 ${cardSurface}`}
                >
                  <div>
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="rounded-lg bg-amber-500/15 border border-amber-500/30 px-2 py-0.5 text-sm font-black text-amber-500">
                            #{token}
                          </span>
                          <span className="text-xs font-mono text-zinc-400">
                            {order.order_id}
                          </span>
                        </div>
                        <p className="text-[11px] text-zinc-500 mt-1">
                          {formatIstDateTime(order.timestamp)} • {order.customer_name}
                        </p>
                      </div>
                      <div className="flex items-center gap-1">
                        <span className="text-base font-black text-amber-500">
                          {formatInr(order.total_amount)}
                        </span>
                        <button
                          type="button"
                          onClick={() => handleDelete(order.order_id)}
                          className="p-1.5 text-zinc-500 hover:text-rose-500 rounded-lg"
                          title="Delete Order"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>

                    <div className="mt-3 space-y-1 text-xs">
                      {items.map((it, idx) => (
                        <div
                          key={idx}
                          className={`flex items-center justify-between rounded-lg px-2.5 py-1.5 border ${
                            isDark
                              ? 'border-zinc-800 bg-zinc-950/50'
                              : 'border-zinc-200 bg-zinc-50'
                          }`}
                        >
                          <span>
                            <strong className="text-amber-500">x{it.quantity}</strong>{' '}
                            {it.item_name}
                          </span>
                          <span className="font-bold">{formatInr(it.subtotal)}</span>
                        </div>
                      ))}
                    </div>

                    {order.notes && (
                      <p className="mt-2 text-xs text-amber-400 bg-amber-500/10 border border-amber-500/20 rounded-lg px-2.5 py-1.5">
                        Note: {order.notes}
                      </p>
                    )}
                  </div>

                  <div className="pt-2 border-t border-zinc-200 dark:border-zinc-800 space-y-2">
                    <div className="grid grid-cols-3 gap-1.5">
                      <button
                        type="button"
                        onClick={() =>
                          patchOrder(
                            order.order_id,
                            { is_made: !order.is_made },
                            order.is_made ? 'Marked Making' : 'Marked Ready'
                          )
                        }
                        className={`flex items-center justify-center gap-1 rounded-xl border py-1.5 text-[11px] font-bold ${
                          order.is_made
                            ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                            : 'border-zinc-700 text-zinc-400'
                        }`}
                      >
                        <Coffee className="h-3 w-3" />
                        <span>{order.is_made ? 'Ready ✓' : 'Making'}</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => setSelectedBill(owi)}
                        className={`flex items-center justify-center gap-1 rounded-xl border py-1.5 text-[11px] font-bold ${
                          order.is_paid
                            ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                            : 'bg-rose-500/15 border-rose-500/40 text-rose-500'
                        }`}
                      >
                        <CreditCard className="h-3 w-3" />
                        <span>{order.is_paid ? order.payment_mode : 'Unpaid'}</span>
                      </button>

                      <button
                        type="button"
                        onClick={() =>
                          patchOrder(
                            order.order_id,
                            {
                              is_delivered: !order.is_delivered,
                              is_made: !order.is_delivered ? true : order.is_made,
                              completed_timestamp: !order.is_delivered ? Date.now() : 0,
                            },
                            order.is_delivered ? 'Marked Pending' : 'Marked Delivered'
                          )
                        }
                        className={`flex items-center justify-center gap-1 rounded-xl border py-1.5 text-[11px] font-bold ${
                          order.is_delivered
                            ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                            : 'border-zinc-700 text-zinc-400'
                        }`}
                      >
                        <Check className="h-3 w-3" />
                        <span>{order.is_delivered ? 'Delivered' : 'Pending'}</span>
                      </button>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => setSelectedBill(owi)}
                        className={`flex items-center justify-center gap-1.5 rounded-xl border py-2 text-xs font-bold ${
                          isDark
                            ? 'border-zinc-800 bg-zinc-800 text-zinc-200'
                            : 'border-zinc-300 bg-zinc-100 text-zinc-800'
                        }`}
                      >
                        <ReceiptIcon className="h-3.5 w-3.5 text-amber-500" />
                        <span>Bill & QR</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => handlePrint(owi)}
                        className="flex items-center justify-center gap-1.5 rounded-xl bg-amber-500 py-2 text-xs font-black text-zinc-950"
                      >
                        <Printer className="h-3.5 w-3.5" />
                        <span>Print</span>
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      <OrderBillModal
        orderData={selectedBill}
        isOpen={!!selectedBill}
        onClose={() => setSelectedBill(null)}
        onPrint={handlePrint}
        onMarkPaid={async (orderId, mode) => {
          const isPaid = mode !== 'UNPAID';
          await patchOrder(
            orderId,
            { is_paid: isPaid, payment_mode: mode },
            `Payment updated to ${mode}`
          );
          if (selectedBill?.order.order_id === orderId) {
            setSelectedBill({
              ...selectedBill,
              order: { ...selectedBill.order, is_paid: isPaid, payment_mode: mode },
            });
          }
        }}
        onMarkComplete={async (orderId) => {
          const updates = {
            is_made: true,
            is_delivered: true,
            completed_timestamp: Date.now(),
          };
          await patchOrder(orderId, updates, 'Order marked complete');
          if (selectedBill?.order.order_id === orderId) {
            setSelectedBill({
              ...selectedBill,
              order: { ...selectedBill.order, ...updates },
            });
          }
        }}
        receiptConfig={receiptConfig}
        theme={theme}
      />

      <Receipt
        orderData={printOrder}
        paperWidth="80mm"
        receiptConfig={receiptConfig}
      />
    </div>
  );
}
