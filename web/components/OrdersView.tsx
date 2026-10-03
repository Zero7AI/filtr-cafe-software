'use client';

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { OrderWithItems } from '@/lib/types';
import {
  fetchOrders,
  updateOrderStatus,
  updateOrderItemCompletion,
  deleteOrder,
  ReceiptDesignConfig,
  defaultReceiptConfig,
} from '@/lib/supabase';
import OrderBillModal from './OrderBillModal';
import {
  Clock,
  CheckCircle2,
  Printer,
  RefreshCw,
  Search,
  Check,
  AlertCircle,
  Trash2,
  IndianRupee,
  User,
  Coffee,
  QrCode,
  CreditCard,
  DollarSign,
  Receipt,
  Calendar,
  Layers,
  X,
} from 'lucide-react';

interface OrdersViewProps {
  onPrintOrder: (order: OrderWithItems) => void;
  onRefreshOrdersCount?: (count: number) => void;
  theme?: 'dark' | 'light';
  receiptConfig?: ReceiptDesignConfig;
}

export default function OrdersView({
  onPrintOrder,
  onRefreshOrdersCount,
  theme = 'dark',
  receiptConfig = defaultReceiptConfig,
}: OrdersViewProps) {
  // Filter tabs matching Android app: PENDING | ALL | MADE | UNPAID | DELIVERED
  const [filter, setFilter] = useState<'PENDING' | 'ALL' | 'MADE' | 'UNPAID' | 'DELIVERED'>('PENDING');
  const [allTodayOrders, setAllTodayOrders] = useState<OrderWithItems[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [processingId, setProcessingId] = useState<string | null>(null);

  // Modal dialog states
  const [selectedBillOrder, setSelectedBillOrder] = useState<OrderWithItems | null>(null);
  const [paymentDialogOrder, setPaymentDialogOrder] = useState<OrderWithItems | null>(null);
  const [orderToDelete, setOrderToDelete] = useState<string | null>(null);

  const isDark = theme === 'dark';

  // Load all today's orders
  const loadOrders = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchOrders('ALL', 300, true);
      setAllTodayOrders(data);

      const uncompleted = data.filter((o) => !o.order.is_delivered).length;
      if (onRefreshOrdersCount) {
        onRefreshOrdersCount(uncompleted);
      }
    } catch (e) {
      console.error('loadOrders error:', e);
    } finally {
      setLoading(false);
    }
  }, [onRefreshOrdersCount]);

  useEffect(() => {
    loadOrders();
    // Auto-refresh orders every 8 seconds for real-time kitchen tablet display
    const interval = setInterval(() => {
      fetchOrders('ALL', 300, true).then((data) => {
        setAllTodayOrders(data);
        const uncompleted = data.filter((o) => !o.order.is_delivered).length;
        if (onRefreshOrdersCount) {
          onRefreshOrdersCount(uncompleted);
        }
      });
    }, 8000);
    return () => clearInterval(interval);
  }, [loadOrders, onRefreshOrdersCount]);

  // Summary Metrics for Today (Banner calculation matching Android OrdersScreen.kt)
  const totalDayRevenue = useMemo(() => {
    return allTodayOrders.reduce((sum, o) => sum + (o.order.total_amount || 0), 0);
  }, [allTodayOrders]);

  const upiTotal = useMemo(() => {
    return allTodayOrders
      .filter((o) => o.order.is_paid && o.order.payment_mode === 'UPI')
      .reduce((sum, o) => sum + (o.order.total_amount || 0), 0);
  }, [allTodayOrders]);

  const cashTotal = useMemo(() => {
    return allTodayOrders
      .filter((o) => o.order.is_paid && o.order.payment_mode === 'CASH')
      .reduce((sum, o) => sum + (o.order.total_amount || 0), 0);
  }, [allTodayOrders]);

  const processedCount = useMemo(() => {
    return allTodayOrders.filter((o) => o.order.is_made || o.order.is_delivered).length;
  }, [allTodayOrders]);

  const deliveredCount = useMemo(() => {
    return allTodayOrders.filter((o) => o.order.is_delivered).length;
  }, [allTodayOrders]);

  const unpaidOrders = useMemo(() => {
    return allTodayOrders.filter((o) => !o.order.is_paid);
  }, [allTodayOrders]);

  const unpaidTotal = useMemo(() => {
    return unpaidOrders.reduce((sum, o) => sum + (o.order.total_amount || 0), 0);
  }, [unpaidOrders]);

  // Filtered orders according to active status tab
  const statusFilteredOrders = useMemo(() => {
    switch (filter) {
      case 'PENDING':
        return allTodayOrders.filter((o) => !o.order.is_delivered);
      case 'MADE':
        return allTodayOrders.filter((o) => o.order.is_made);
      case 'UNPAID':
        return allTodayOrders.filter((o) => !o.order.is_paid);
      case 'DELIVERED':
        return allTodayOrders.filter((o) => o.order.is_delivered);
      case 'ALL':
      default:
        return allTodayOrders;
    }
  }, [allTodayOrders, filter]);

  // Filter by search query
  const displayOrders = useMemo(() => {
    if (!searchQuery.trim()) return statusFilteredOrders;
    const q = searchQuery.toLowerCase().trim();
    return statusFilteredOrders.filter(
      (o) =>
        o.order.order_id.toLowerCase().includes(q) ||
        o.order.customer_name.toLowerCase().includes(q) ||
        o.order.notes.toLowerCase().includes(q) ||
        o.items.some((it) => it.item_name.toLowerCase().includes(q))
    );
  }, [statusFilteredOrders, searchQuery]);

  // Action: Toggle Made Status
  const handleToggleMade = async (order: OrderWithItems) => {
    const nextMade = !order.order.is_made;
    setProcessingId(order.order.order_id);

    // Optimistically update local state
    setAllTodayOrders((prev) =>
      prev.map((o) =>
        o.order.order_id === order.order.order_id
          ? {
              ...o,
              order: { ...o.order, is_made: nextMade },
              items: o.items.map((it) => ({
                ...it,
                completed_quantity: nextMade ? it.quantity : 0,
              })),
            }
          : o
      )
    );

    await updateOrderStatus(order.order.order_id, { is_made: nextMade });
    setProcessingId(null);
  };

  // Action: Toggle Delivered Status
  const handleToggleDelivered = async (order: OrderWithItems) => {
    const nextDelivered = !order.order.is_delivered;
    setProcessingId(order.order.order_id);

    const updates = {
      is_delivered: nextDelivered,
      is_made: nextDelivered ? true : order.order.is_made,
      completed_timestamp: nextDelivered ? Date.now() : 0,
    };

    setAllTodayOrders((prev) =>
      prev.map((o) =>
        o.order.order_id === order.order.order_id
          ? {
              ...o,
              order: { ...o.order, ...updates },
            }
          : o
      )
    );

    await updateOrderStatus(order.order.order_id, updates);
    setProcessingId(null);
  };

  // Action: Select Payment Mode (CASH, UPI, UNPAID)
  const handleSelectPaymentMode = async (orderId: string, mode: 'CASH' | 'UPI' | 'UNPAID') => {
    setProcessingId(orderId);
    const updates = {
      is_paid: mode !== 'UNPAID',
      payment_mode: mode === 'UNPAID' ? 'UNPAID' : mode,
    };

    setAllTodayOrders((prev) =>
      prev.map((o) =>
        o.order.order_id === orderId
          ? {
              ...o,
              order: { ...o.order, ...updates },
            }
          : o
      )
    );

    await updateOrderStatus(orderId, updates);
    setPaymentDialogOrder(null);
    setProcessingId(null);
  };

  // Action: Toggle Individual Item Completion
  const handleToggleItemComplete = async (
    orderId: string,
    itemId: number | undefined,
    currentQty: number,
    totalQty: number
  ) => {
    const nextQty = currentQty >= totalQty ? 0 : currentQty + 1;

    setAllTodayOrders((prev) =>
      prev.map((ord) => {
        if (ord.order.order_id !== orderId) return ord;
        const updatedItems = ord.items.map((it) =>
          it.id === itemId || (!it.id && it.order_id === orderId)
            ? { ...it, completed_quantity: nextQty }
            : it
        );
        const allItemsDone = updatedItems.every(
          (it) => it.completed_quantity >= it.quantity
        );
        return {
          ...ord,
          order: { ...ord.order, is_made: allItemsDone ? true : ord.order.is_made },
          items: updatedItems,
        };
      })
    );

    if (itemId) {
      await updateOrderItemCompletion(itemId, nextQty);
    }
  };

  // Action: Confirm Delete Order
  const handleConfirmDelete = async () => {
    if (!orderToDelete) return;
    setProcessingId(orderToDelete);
    const success = await deleteOrder(orderToDelete);
    if (success) {
      setAllTodayOrders((prev) => prev.filter((o) => o.order.order_id !== orderToDelete));
    } else {
      alert('Failed to delete order. Please check network.');
    }
    setOrderToDelete(null);
    setProcessingId(null);
  };

  return (
    <div
      className={`flex flex-col h-[calc(100vh-4.5rem)] overflow-hidden p-3 sm:p-5 space-y-3.5 transition-colors ${
        isDark ? 'bg-zinc-950 text-white' : 'bg-[#FAFAFA] text-[#09090B]'
      }`}
    >
      {/* TOP TOTAL REVENUE & ORDERS BANNER (Matching Android OrdersScreen.kt) */}
      <div
        className={`rounded-2xl border p-3.5 sm:p-4 shadow-sm transition-colors ${
          isDark
            ? 'bg-zinc-900 border-zinc-800 text-white'
            : 'bg-[#09090B] border-[#09090B] text-white shadow-md'
        }`}
      >
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          {/* Left Stats: Total Bill + Total Orders */}
          <div className="flex items-center gap-4 sm:gap-6 flex-wrap">
            <div>
              <span className="text-[10px] font-black uppercase tracking-wider text-zinc-400 block">
                TOTAL BILL (TODAY)
              </span>
              <span className="text-xl sm:text-2xl font-black text-white">
                ₹{Number(totalDayRevenue).toFixed(0)}
              </span>
            </div>

            <div className="h-8 w-px bg-zinc-700/60" />

            <div>
              <span className="text-[10px] font-black uppercase tracking-wider text-zinc-400 block">
                ORDERS (TODAY)
              </span>
              <div className="flex items-center gap-1.5">
                <span className="text-xl sm:text-2xl font-black text-white">
                  {allTodayOrders.length}
                </span>
                {allTodayOrders.length > 0 && (
                  <span className="text-xs font-bold text-amber-400">
                    ({allTodayOrders.filter((o) => !o.order.is_delivered).length} active)
                  </span>
                )}
              </div>
            </div>

            <div className="h-8 w-px bg-zinc-700/60 hidden sm:block" />

            {/* UPI & Cash Split pill */}
            <div className="flex items-center gap-2 rounded-xl bg-white/10 px-3 py-1.5 text-xs font-bold text-zinc-200">
              <span className="flex items-center gap-1">
                <QrCode className="h-3 w-3 text-amber-400" />
                <span>₹{Number(upiTotal).toFixed(0)}</span>
              </span>
              <span className="text-zinc-500">•</span>
              <span className="flex items-center gap-1">
                <DollarSign className="h-3 w-3 text-emerald-400" />
                <span>₹{Number(cashTotal).toFixed(0)}</span>
              </span>
            </div>

            {/* Unpaid Balance pill */}
            {unpaidOrders.length > 0 ? (
              <button
                type="button"
                onClick={() => setFilter('UNPAID')}
                title="Click to view all unpaid orders"
                className={`flex items-center gap-1.5 rounded-xl px-3 py-1.5 text-xs font-bold transition active:scale-95 ${
                  filter === 'UNPAID'
                    ? 'bg-rose-500 text-white shadow-md shadow-rose-500/30 ring-2 ring-white/30'
                    : 'bg-rose-500/20 border border-rose-500/40 text-rose-300 hover:bg-rose-500/30'
                }`}
              >
                <span>⚠️ {unpaidOrders.length} Unpaid</span>
                <span>(₹{Number(unpaidTotal).toFixed(0)})</span>
              </button>
            ) : (
              <div className="flex items-center gap-1 rounded-xl bg-emerald-500/15 border border-emerald-500/30 px-2.5 py-1.5 text-xs font-bold text-emerald-400">
                <span>✓ All Paid</span>
              </div>
            )}
          </div>

          {/* Right Status Badge: Kitchen Progress */}
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1.5 rounded-xl bg-emerald-500/20 border border-emerald-500/40 px-3 py-1.5 text-xs font-bold text-emerald-400">
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>
                Done: {processedCount}/{allTodayOrders.length} (Del: {deliveredCount})
              </span>
            </div>

            <button
              type="button"
              onClick={loadOrders}
              disabled={loading}
              title="Refresh Orders"
              className="flex h-9 w-9 items-center justify-center rounded-xl bg-white/10 hover:bg-white/20 text-white transition active:scale-95"
            >
              <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin text-amber-400' : ''}`} />
            </button>
          </div>
        </div>
      </div>

      {/* FILTER TABS & SEARCH BAR */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
        {/* Filter Pills: PENDING | ALL | MADE | UNPAID | DELIVERED */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 no-scrollbar">
          {(['PENDING', 'ALL', 'MADE', 'UNPAID', 'DELIVERED'] as const).map((tab) => {
            const isSel = filter === tab;
            const count =
              tab === 'PENDING'
                ? allTodayOrders.filter((o) => !o.order.is_delivered).length
                : tab === 'MADE'
                ? allTodayOrders.filter((o) => o.order.is_made).length
                : tab === 'UNPAID'
                ? allTodayOrders.filter((o) => !o.order.is_paid).length
                : tab === 'DELIVERED'
                ? allTodayOrders.filter((o) => o.order.is_delivered).length
                : allTodayOrders.length;

            return (
              <button
                key={tab}
                type="button"
                onClick={() => setFilter(tab)}
                className={`whitespace-nowrap flex items-center gap-1.5 rounded-xl px-3.5 py-1.5 text-xs font-black transition active:scale-95 ${
                  isSel
                    ? tab === 'UNPAID'
                      ? 'bg-rose-600 text-white shadow-md shadow-rose-600/30 scale-105'
                      : 'bg-amber-500 text-zinc-950 shadow-md shadow-amber-500/20 scale-105'
                    : tab === 'UNPAID' && count > 0
                    ? 'bg-rose-500/15 border border-rose-500/40 text-rose-400 hover:bg-rose-500/25'
                    : isDark
                    ? 'bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-white'
                    : 'bg-white border border-[#E4E4E7] text-zinc-600 hover:text-zinc-900 shadow-sm'
                }`}
              >
                <span>{tab}</span>
                <span
                  className={`rounded-full px-1.5 py-0.2 text-[10px] font-bold ${
                    isSel
                      ? 'bg-zinc-950 text-white'
                      : tab === 'UNPAID' && count > 0
                      ? 'bg-rose-500 text-white'
                      : isDark
                      ? 'bg-zinc-800 text-zinc-400'
                      : 'bg-zinc-200 text-zinc-700'
                  }`}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Search Input */}
        <div className="relative w-full sm:w-64">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-400" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search token, ID, customer..."
            className={`w-full rounded-xl border pl-9 pr-3 py-1.5 text-xs focus:outline-none focus:border-amber-500 transition ${
              isDark
                ? 'bg-zinc-900 border-zinc-800 text-white placeholder-zinc-500'
                : 'bg-white border-[#E4E4E7] text-[#09090B] placeholder-zinc-400 shadow-sm'
            }`}
          />
        </div>
      </div>

      {/* ORDERS CARDS GRID */}
      <div className="flex-1 overflow-y-auto">
        {displayOrders.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-72 text-center text-zinc-500">
            <CheckCircle2 className={`h-12 w-12 mb-2 ${filter === 'UNPAID' ? 'text-emerald-500' : 'text-zinc-400'}`} />
            <p className="text-base font-bold">
              {filter === 'PENDING'
                ? 'All caught up! No active orders for today.'
                : filter === 'UNPAID'
                ? '🎉 Great job! All orders are fully paid.'
                : 'No orders found matching this filter.'}
            </p>
            <p className={`text-xs mt-1 max-w-sm ${isDark ? 'text-zinc-500' : 'text-zinc-400'}`}>
              {filter === 'UNPAID'
                ? 'When an order is placed as UNPAID, it will appear here until settled.'
                : 'New orders punched from POS will automatically show here in real-time.'}
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 pb-6">
            {displayOrders.map((ordWithItems) => {
              const { order, items } = ordWithItems;
              const token = order.order_id.split('-').pop() || '0000';
              const isCompleted = order.is_made && order.is_delivered;
              const timeStr = new Date(order.timestamp).toLocaleTimeString([], {
                hour: '2-digit',
                minute: '2-digit',
              });

              // SKU counters
              const totalUnits = items.reduce((acc, ci) => acc + ci.quantity, 0);
              const completedUnits = items.reduce(
                (acc, ci) => acc + Math.min(ci.completed_quantity || 0, ci.quantity),
                0
              );
              const allSkusDone = totalUnits > 0 && completedUnits === totalUnits;

              return (
                <div
                  key={order.order_id}
                  className={`flex flex-col justify-between rounded-2xl border p-4 transition shadow-md select-none ${
                    isCompleted
                      ? isDark
                        ? 'bg-zinc-900/50 border-zinc-800/80 opacity-80'
                        : 'bg-white/80 border-[#E4E4E7] opacity-80'
                      : isDark
                      ? 'bg-zinc-900 border-amber-500/40 shadow-amber-500/5'
                      : 'bg-white border-amber-500/40 shadow-sm'
                  }`}
                >
                  <div>
                    {/* Header Row: Token, Order ID, Amount & Delete */}
                    <div className="flex items-start justify-between">
                      <div
                        onClick={() => setSelectedBillOrder(ordWithItems)}
                        className="cursor-pointer group"
                      >
                        <div className="flex items-center gap-2">
                          <span
                            className={`text-xl font-black px-2.5 py-0.5 rounded-lg border transition group-hover:scale-105 ${
                              isDark
                                ? 'text-white bg-zinc-800 border-zinc-700'
                                : 'text-[#09090B] bg-zinc-100 border-[#E4E4E7]'
                            }`}
                          >
                            #{token}
                          </span>
                          <span className={`text-xs font-mono ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                            {order.order_id}
                          </span>
                        </div>
                        <p className={`text-[11px] mt-1 ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                          {timeStr}
                        </p>
                      </div>

                      <div className="flex items-center gap-1.5">
                        <span className="text-lg font-black text-amber-500">
                          ₹{Number(order.total_amount).toFixed(2)}
                        </span>

                        <button
                          type="button"
                          onClick={() => setOrderToDelete(order.order_id)}
                          title="Delete Order"
                          className={`rounded-lg p-1.5 transition ${
                            isDark
                              ? 'text-zinc-500 hover:text-rose-400 hover:bg-zinc-800'
                              : 'text-zinc-400 hover:text-rose-600 hover:bg-zinc-100'
                          }`}
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      </div>
                    </div>

                    {/* Customer Details */}
                    {order.customer_name && (
                      <div
                        className={`mt-2 flex items-center gap-1.5 text-xs font-semibold ${
                          isDark ? 'text-zinc-300' : 'text-zinc-700'
                        }`}
                      >
                        <User className="h-3.5 w-3.5 text-zinc-400" />
                        <span>{order.customer_name}</span>
                        {order.contact_number && (
                          <span className="text-[11px] font-normal text-zinc-500">
                            ({order.contact_number})
                          </span>
                        )}
                      </div>
                    )}

                    {/* Kitchen SKU Checklist (Matching Android OrdersScreen.kt) */}
                    <div
                      className={`my-3 rounded-xl border p-2.5 transition-colors ${
                        isDark ? 'border-zinc-800/80 bg-zinc-950/40' : 'border-[#E4E4E7] bg-[#F8F9FA]'
                      }`}
                    >
                      <div className="flex items-center justify-between pb-1.5 border-b border-zinc-200 dark:border-zinc-800 mb-2">
                        <span className="text-[10px] font-black uppercase tracking-wider text-zinc-500">
                          KITCHEN ITEMS & SKUs
                        </span>
                        <span
                          className={`text-[10px] font-bold px-2 py-0.5 rounded-md ${
                            allSkusDone
                              ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30'
                              : 'bg-amber-500/15 text-amber-500 border border-amber-500/30'
                          }`}
                        >
                          {allSkusDone
                            ? `ALL ${totalUnits} SKUs READY ✓`
                            : `${completedUnits}/${totalUnits} Prepared`}
                        </span>
                      </div>

                      <div className="space-y-1.5">
                        {items.map((it, idx) => {
                          const isItemMade = (it.completed_quantity || 0) >= it.quantity;
                          return (
                            <div
                              key={idx}
                              className={`flex items-center justify-between rounded-lg p-1.5 border text-xs transition ${
                                isItemMade
                                  ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-500'
                                  : isDark
                                  ? 'bg-zinc-900 border-zinc-800 text-zinc-200'
                                  : 'bg-white border-[#E4E4E7] text-zinc-800'
                              }`}
                            >
                              <div className="flex items-center gap-2">
                                <span className="font-black text-amber-500">
                                  x{it.quantity}
                                </span>
                                <span className="font-semibold">{it.item_name}</span>
                              </div>

                              <button
                                type="button"
                                onClick={() =>
                                  handleToggleItemComplete(
                                    order.order_id,
                                    it.id,
                                    it.completed_quantity || 0,
                                    it.quantity
                                  )
                                }
                                className={`rounded-md px-2 py-0.5 text-[10px] font-bold transition active:scale-95 ${
                                  isItemMade
                                    ? 'bg-emerald-500 text-zinc-950'
                                    : isDark
                                    ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
                                    : 'bg-zinc-100 text-zinc-700 hover:bg-zinc-200 border border-zinc-200'
                                }`}
                              >
                                {isItemMade ? 'Ready ✓' : 'Mark Made'}
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    </div>

                    {/* Order Notes */}
                    {order.notes && (
                      <div
                        className={`rounded-xl border p-2 text-xs mb-3 ${
                          isDark
                            ? 'border-amber-500/20 bg-amber-500/5 text-amber-300'
                            : 'border-amber-200 bg-amber-50 text-amber-900'
                        }`}
                      >
                        <span className="font-bold">Note: </span>
                        <span>{order.notes}</span>
                      </div>
                    )}
                  </div>

                  {/* BOTTOM: 4 INTERACTIVE STATUS BADGES ROW (Matches Android) */}
                  <div className="pt-2 border-t border-zinc-200 dark:border-zinc-800/80 space-y-2.5">
                    <div className="grid grid-cols-3 gap-1.5">
                      {/* 1. Coffee Ready / Making Toggle Button */}
                      <button
                        type="button"
                        onClick={() => handleToggleMade(ordWithItems)}
                        className={`flex items-center justify-center gap-1 rounded-xl py-1.5 px-1 text-[11px] font-black border transition active:scale-95 ${
                          order.is_made
                            ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                            : isDark
                            ? 'bg-zinc-800/60 border-zinc-700 text-zinc-400 hover:text-white'
                            : 'bg-zinc-100 border-[#E4E4E7] text-zinc-600 hover:text-zinc-900'
                        }`}
                      >
                        <Coffee className="h-3 w-3" />
                        <span>{order.is_made ? 'Ready ✓' : 'Making'}</span>
                      </button>

                      {/* 2. Paid (CASH/UPI) / Unpaid Button (Click opens Payment Selection Dialog) */}
                      <button
                        type="button"
                        onClick={() => setPaymentDialogOrder(ordWithItems)}
                        className={`flex items-center justify-center gap-1 rounded-xl py-1.5 px-1 text-[11px] font-black border transition active:scale-95 ${
                          order.is_paid
                            ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                            : 'bg-rose-500/15 border-rose-500/40 text-rose-500'
                        }`}
                      >
                        <CreditCard className="h-3 w-3" />
                        <span>
                          {order.is_paid
                            ? `Paid (${order.payment_mode || 'CASH'})`
                            : 'Unpaid'}
                        </span>
                      </button>

                      {/* 3. Delivered / Preparing Toggle Button */}
                      <button
                        type="button"
                        onClick={() => handleToggleDelivered(ordWithItems)}
                        className={`flex items-center justify-center gap-1 rounded-xl py-1.5 px-1 text-[11px] font-black border transition active:scale-95 ${
                          order.is_delivered
                            ? 'bg-emerald-500/15 border-emerald-500/40 text-emerald-500'
                            : isDark
                            ? 'bg-zinc-800/60 border-zinc-700 text-zinc-400 hover:text-white'
                            : 'bg-zinc-100 border-[#E4E4E7] text-zinc-600 hover:text-zinc-900'
                        }`}
                      >
                        <Check className="h-3 w-3 stroke-[3]" />
                        <span>{order.is_delivered ? 'Delivered' : 'Preparing'}</span>
                      </button>
                    </div>

                    {/* Primary Bill Receipt & Print Buttons */}
                    <div className="grid grid-cols-2 gap-2">
                      {/* Bill Receipt Button (Opens popup modal with dynamic UPI QR code) */}
                      <button
                        type="button"
                        onClick={() => setSelectedBillOrder(ordWithItems)}
                        className={`flex items-center justify-center gap-1.5 rounded-xl border py-2.5 text-xs font-bold transition active:scale-95 ${
                          isDark
                            ? 'border-zinc-800 bg-zinc-800/80 text-zinc-200 hover:bg-zinc-700 hover:text-white'
                            : 'border-zinc-300 bg-zinc-100 text-zinc-800 hover:bg-zinc-200'
                        }`}
                      >
                        <Receipt className="h-4 w-4 text-amber-500" />
                        <span>Bill Receipt & QR</span>
                      </button>

                      {/* Print Bill Button */}
                      <button
                        type="button"
                        onClick={() => onPrintOrder(ordWithItems)}
                        className="flex items-center justify-center gap-1.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-zinc-950 py-2.5 text-xs font-black shadow-md shadow-amber-500/20 active:scale-95 transition"
                      >
                        <Printer className="h-4 w-4" />
                        <span>Print Bill</span>
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* POP-UP MODAL 1: BILL RECEIPT WITH DYNAMIC UPI QR CODE */}
      <OrderBillModal
        orderData={selectedBillOrder}
        isOpen={!!selectedBillOrder}
        onClose={() => setSelectedBillOrder(null)}
        onPrint={(ord) => onPrintOrder(ord)}
        onMarkPaid={async (orderId, mode) => {
          await handleSelectPaymentMode(orderId, mode);
          if (selectedBillOrder && selectedBillOrder.order.order_id === orderId) {
            setSelectedBillOrder({
              ...selectedBillOrder,
              order: { ...selectedBillOrder.order, is_paid: true, payment_mode: mode },
            });
          }
        }}
        onMarkComplete={async (orderId) => {
          const target = allTodayOrders.find((o) => o.order.order_id === orderId);
          if (target) {
            await handleToggleDelivered(target);
            if (selectedBillOrder && selectedBillOrder.order.order_id === orderId) {
              setSelectedBillOrder({
                ...selectedBillOrder,
                order: {
                  ...selectedBillOrder.order,
                  is_made: true,
                  is_delivered: true,
                  completed_timestamp: Date.now(),
                },
              });
            }
          }
        }}
        receiptConfig={receiptConfig}
        theme={theme}
      />

      {/* POP-UP MODAL 2: PAYMENT MODE SELECTION DIALOG (Matching Android OrdersScreen.kt) */}
      {paymentDialogOrder && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70"
          onClick={() => setPaymentDialogOrder(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className={`w-full max-w-sm rounded-2xl border p-5 shadow-2xl transition-all ${
              isDark
                ? 'bg-zinc-950 border-zinc-800 text-white'
                : 'bg-white border-[#E4E4E7] text-[#09090B]'
            }`}
          >
            <div className="flex items-center justify-between pb-3 border-b border-zinc-200 dark:border-zinc-800">
              <div className="flex items-center gap-2">
                <CreditCard className="h-5 w-5 text-amber-500" />
                <h3 className="text-base font-black">Select Payment Mode</h3>
              </div>
              <button
                type="button"
                onClick={() => setPaymentDialogOrder(null)}
                className="rounded-lg p-1 text-zinc-400 hover:text-white"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div className="py-4 text-center">
              <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                Order #{paymentDialogOrder.order.order_id} • {paymentDialogOrder.order.customer_name}
              </p>
              <p className="text-2xl font-black text-amber-500 mt-1">
                ₹{Number(paymentDialogOrder.order.total_amount).toFixed(2)}
              </p>
            </div>

            <div className="space-y-2.5">
              {/* CASH OPTION */}
              <button
                type="button"
                onClick={() =>
                  handleSelectPaymentMode(paymentDialogOrder.order.order_id, 'CASH')
                }
                className={`w-full flex items-center justify-center gap-2 rounded-xl py-3.5 text-xs font-black shadow-md active:scale-95 transition ${
                  paymentDialogOrder.order.is_paid && paymentDialogOrder.order.payment_mode === 'CASH'
                    ? 'bg-emerald-600 text-white shadow-emerald-600/30 ring-2 ring-emerald-400'
                    : 'bg-emerald-600/80 hover:bg-emerald-600 text-white'
                }`}
              >
                <DollarSign className="h-4 w-4" />
                <span>
                  {paymentDialogOrder.order.is_paid && paymentDialogOrder.order.payment_mode === 'CASH'
                    ? '💵 CASH PAYMENT ✓'
                    : '💵 CASH PAYMENT'}
                </span>
              </button>

              {/* UPI OPTION */}
              <button
                type="button"
                onClick={() =>
                  handleSelectPaymentMode(paymentDialogOrder.order.order_id, 'UPI')
                }
                className={`w-full flex items-center justify-center gap-2 rounded-xl py-3.5 text-xs font-black shadow-md active:scale-95 transition ${
                  paymentDialogOrder.order.is_paid && paymentDialogOrder.order.payment_mode === 'UPI'
                    ? 'bg-blue-600 text-white shadow-blue-600/30 ring-2 ring-blue-400'
                    : 'bg-blue-600/80 hover:bg-blue-600 text-white'
                }`}
              >
                <QrCode className="h-4 w-4" />
                <span>
                  {paymentDialogOrder.order.is_paid && paymentDialogOrder.order.payment_mode === 'UPI'
                    ? '📱 UPI PAYMENT ✓'
                    : '📱 UPI PAYMENT'}
                </span>
              </button>

              {/* UNMARK PAID (SET UNPAID) */}
              {paymentDialogOrder.order.is_paid && (
                <button
                  type="button"
                  onClick={() =>
                    handleSelectPaymentMode(paymentDialogOrder.order.order_id, 'UNPAID')
                  }
                  className="w-full rounded-xl py-2.5 text-xs font-bold text-rose-500 hover:bg-rose-500/10 border border-rose-500/20 transition active:scale-95"
                >
                  Unmark Paid (Set to Unpaid)
                </button>
              )}
            </div>

            <div className="mt-4 pt-3 border-t border-zinc-200 dark:border-zinc-800 text-center">
              <button
                type="button"
                onClick={() => setPaymentDialogOrder(null)}
                className="text-xs font-bold text-zinc-400 hover:text-zinc-200"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* POP-UP MODAL 3: DELETE CONFIRMATION */}
      {orderToDelete && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70"
          onClick={() => setOrderToDelete(null)}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className={`w-full max-w-sm rounded-2xl border p-5 shadow-2xl transition-all ${
              isDark
                ? 'bg-zinc-950 border-zinc-800 text-white'
                : 'bg-white border-[#E4E4E7] text-[#09090B]'
            }`}
          >
            <div className="flex items-center gap-2 text-rose-500 mb-2">
              <AlertCircle className="h-5 w-5" />
              <h3 className="text-base font-black">Cancel & Delete Order?</h3>
            </div>
            <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-600'}`}>
              Are you sure you want to permanently cancel and delete order{' '}
              <strong>{orderToDelete}</strong>? This action cannot be undone.
            </p>

            <div className="mt-5 flex items-center gap-2 justify-end">
              <button
                type="button"
                onClick={() => setOrderToDelete(null)}
                className={`rounded-xl px-4 py-2 text-xs font-bold border transition ${
                  isDark
                    ? 'border-zinc-800 bg-zinc-900 text-zinc-300 hover:bg-zinc-800'
                    : 'border-[#E4E4E7] bg-zinc-100 text-zinc-700 hover:bg-zinc-200'
                }`}
              >
                Keep Order
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                className="rounded-xl bg-rose-600 hover:bg-rose-500 text-white px-4 py-2 text-xs font-black shadow-md shadow-rose-600/20 active:scale-95 transition"
              >
                Yes, Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
