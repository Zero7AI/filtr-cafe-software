'use client';

import React, { useState, useMemo } from 'react';
import { MenuItem, CartItem, OrderWithItems } from '@/lib/types';
import { getNextOrderId, createOrder, updateOrderStatus, ReceiptDesignConfig, defaultReceiptConfig } from '@/lib/supabase';
import OrderBillModal from './OrderBillModal';
import {
  Search,
  Plus,
  Minus,
  Trash2,
  Printer,
  CheckCircle,
  IndianRupee,
  Sparkles,
  User,
  Phone,
  FileText,
  QrCode,
  Receipt,
} from 'lucide-react';

interface PosOrderViewProps {
  menuItems: MenuItem[];
  autoPrint: boolean;
  onOrderCompleted: (orderWithItems: OrderWithItems, shouldPrint: boolean) => void;
  theme?: 'dark' | 'light';
  receiptConfig?: ReceiptDesignConfig;
}

export default function PosOrderView({
  menuItems,
  autoPrint,
  onOrderCompleted,
  theme = 'dark',
  receiptConfig = defaultReceiptConfig,
}: PosOrderViewProps) {
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [cart, setCart] = useState<CartItem[]>([]);
  const [customerName, setCustomerName] = useState<string>('');
  const [contactNumber, setContactNumber] = useState<string>('');
  const [notes, setNotes] = useState<string>('');
  const [paymentMode, setPaymentMode] = useState<'UNPAID' | 'CASH' | 'UPI'>('UNPAID');
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [lastPlacedOrder, setLastPlacedOrder] = useState<{ id: string; token: string; total: number } | null>(null);
  const [activeBillModalOrder, setActiveBillModalOrder] = useState<OrderWithItems | null>(null);

  const isDark = theme === 'dark';

  // Extract unique categories from menu items
  const categories = useMemo(() => {
    const set = new Set<string>();
    menuItems.forEach((m) => {
      if (m.category) set.add(m.category.trim());
    });
    return ['ALL', ...Array.from(set).sort()];
  }, [menuItems]);

  // Filter menu items by category and search
  const filteredItems = useMemo(() => {
    return menuItems.filter((item) => {
      const matchesCategory =
        selectedCategory === 'ALL' || item.category.toLowerCase() === selectedCategory.toLowerCase();
      const matchesSearch =
        !searchQuery ||
        item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        item.category.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }, [menuItems, selectedCategory, searchQuery]);

  // Cart operations
  const addToCart = (item: MenuItem) => {
    setCart((prev) => {
      const idx = prev.findIndex((ci) => ci.item.id === item.id);
      if (idx >= 0) {
        const next = [...prev];
        next[idx] = { ...next[idx], quantity: next[idx].quantity + 1 };
        return next;
      }
      return [...prev, { item, quantity: 1 }];
    });
  };

  const updateQuantity = (itemId: number, delta: number) => {
    setCart((prev) => {
      return prev
        .map((ci) => {
          if (ci.item.id === itemId) {
            const newQty = ci.quantity + delta;
            return newQty > 0 ? { ...ci, quantity: newQty } : null;
          }
          return ci;
        })
        .filter(Boolean) as CartItem[];
    });
  };

  const removeFromCart = (itemId: number) => {
    setCart((prev) => prev.filter((ci) => ci.item.id !== itemId));
  };

  const clearCart = () => {
    setCart([]);
    setCustomerName('');
    setContactNumber('');
    setNotes('');
  };

  // Cart Totals
  const totalItemsCount = useMemo(() => cart.reduce((acc, ci) => acc + ci.quantity, 0), [cart]);
  const grandTotal = useMemo(() => cart.reduce((acc, ci) => acc + ci.quantity * ci.item.price, 0), [cart]);

  // Submit Order handler
  const handlePlaceOrder = async (shouldPrint: boolean) => {
    if (cart.length === 0 || isSubmitting) return;

    setIsSubmitting(true);
    try {
      const orderId = await getNextOrderId();
      const token = orderId.split('-').pop() || '0000';
      const now = Date.now();

      const isPaid = paymentMode !== 'UNPAID';

      const newOrder = {
        order_id: orderId,
        customer_name: customerName.trim() || 'Walk-in Customer',
        contact_number: contactNumber.trim(),
        total_amount: grandTotal,
        timestamp: now,
        is_made: false,
        is_paid: isPaid,
        is_delivered: false,
        sync_status: 'SYNCED',
        notes: notes.trim(),
        payment_mode: paymentMode,
        completed_timestamp: 0,
      };

      const orderItems = cart.map((ci) => ({
        order_id: orderId,
        menu_item_id: ci.item.id,
        item_name: ci.item.name,
        item_price: ci.item.price,
        quantity: ci.quantity,
        subtotal: ci.quantity * ci.item.price,
        completed_quantity: 0,
        completed_timestamp: 0,
      }));

      const res = await createOrder(newOrder, orderItems);
      if (!res.success) {
        alert(`Failed to save order: ${res.error}`);
        setIsSubmitting(false);
        return;
      }

      const orderWithItems: OrderWithItems = {
        order: newOrder,
        items: orderItems,
      };

      setLastPlacedOrder({ id: orderId, token, total: grandTotal });
      clearCart();
      setPaymentMode('UNPAID');

      // Immediately display Bill Receipt Modal with dynamic UPI QR Code
      setActiveBillModalOrder(orderWithItems);

      // Notify parent app (increments count & background Bluetooth print if connected)
      onOrderCompleted(orderWithItems, shouldPrint);

      setTimeout(() => {
        setLastPlacedOrder(null);
      }, 4000);
    } catch (err: any) {
      console.error('Order creation error:', err);
      alert('Error creating order. Please check connection.');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="flex h-[calc(100vh-4.5rem)] w-full overflow-hidden">
      {/* LEFT: Menu Selection Grid (65% width on tablet) */}
      <div
        className={`flex flex-1 flex-col overflow-hidden border-r transition-colors ${
          isDark ? 'border-zinc-800 bg-zinc-950' : 'border-[#E4E4E7] bg-[#FAFAFA]'
        }`}
      >
        {/* Top Controls: Search + Categories */}
        <div
          className={`border-b p-4 space-y-3 shrink-0 transition-colors ${
            isDark ? 'border-zinc-800 bg-zinc-950/40' : 'border-[#E4E4E7] bg-white shadow-sm'
          }`}
        >
          {/* Search Box */}
          <div className="relative w-full">
            <Search className="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-zinc-400" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search coffee, tea, food items..."
              className={`w-full rounded-2xl border pl-10 pr-4 py-2.5 text-sm focus:outline-none focus:border-amber-500 transition ${
                isDark
                  ? 'bg-zinc-900 border-zinc-800 text-white placeholder-zinc-500'
                  : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
              }`}
            />
          </div>

          {/* Category Pills (Horizontal scrollable) */}
          <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
            {categories.map((cat) => {
              const isSelected = selectedCategory === cat;
              return (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setSelectedCategory(cat)}
                  className={`whitespace-nowrap rounded-xl px-4 py-2 text-xs font-bold transition-all ${
                    isSelected
                      ? 'bg-amber-500 text-zinc-950 shadow-md shadow-amber-500/20 scale-105'
                      : isDark
                      ? 'bg-zinc-800/80 text-zinc-300 hover:bg-zinc-700 hover:text-white border border-zinc-700/50'
                      : 'bg-zinc-100 text-zinc-700 hover:bg-zinc-200 border border-zinc-200'
                  }`}
                >
                  {cat}
                </button>
              );
            })}
          </div>
        </div>

        {/* Menu Grid */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5">
          {filteredItems.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-64 text-center text-zinc-500">
              <Sparkles className="h-10 w-10 text-zinc-400 mb-2" />
              <p className="text-sm font-semibold">No menu items found</p>
              <p className="text-xs text-zinc-400 mt-0.5">Try searching for another keyword or select All</p>
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-3 sm:gap-4">
              {filteredItems.map((item) => {
                const cartEntry = cart.find((ci) => ci.item.id === item.id);
                const countInCart = cartEntry ? cartEntry.quantity : 0;

                return (
                  <div
                    key={item.id}
                    onClick={() => addToCart(item)}
                    className={`relative flex flex-col justify-between rounded-2xl border p-4 transition-all duration-150 cursor-pointer active:scale-95 select-none ${
                      countInCart > 0
                        ? isDark
                          ? 'bg-zinc-900 border-amber-500/60 shadow-lg shadow-amber-500/10'
                          : 'bg-white border-amber-500 shadow-md'
                        : isDark
                        ? 'bg-zinc-900/60 border-zinc-800/80 hover:border-zinc-700 hover:bg-zinc-800/50'
                        : 'bg-white border-[#E4E4E7] hover:border-zinc-300 hover:bg-zinc-50/50 shadow-sm'
                    }`}
                  >
                    {countInCart > 0 && (
                      <span className="absolute top-2.5 right-2.5 flex h-6 min-w-6 items-center justify-center rounded-full bg-amber-500 text-zinc-950 text-xs font-black shadow-md">
                        {countInCart}
                      </span>
                    )}

                    <div>
                      <span className="text-[10px] font-bold uppercase tracking-wider text-amber-500 block mb-1">
                        {item.category}
                      </span>
                      <h3
                        className={`text-sm font-bold leading-tight line-clamp-2 ${
                          isDark ? 'text-white' : 'text-[#09090B]'
                        }`}
                      >
                        {item.name}
                      </h3>
                      {item.description && (
                        <p
                          className={`text-[11px] mt-1 line-clamp-1 ${
                            isDark ? 'text-zinc-400' : 'text-zinc-500'
                          }`}
                        >
                          {item.description}
                        </p>
                      )}
                    </div>

                    <div
                      className={`mt-4 flex items-center justify-between pt-2 border-t ${
                        isDark ? 'border-zinc-800/60' : 'border-zinc-100'
                      }`}
                    >
                      <span className={`text-base font-black ${isDark ? 'text-white' : 'text-[#09090B]'}`}>
                        ₹{item.price}
                      </span>
                      <button
                        type="button"
                        className="flex h-8 w-8 items-center justify-center rounded-xl bg-amber-500 text-zinc-950 shadow-sm active:scale-90 transition"
                      >
                        <Plus className="h-4 w-4 stroke-[3]" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* RIGHT: Cart & Checkout Panel (35% width on tablet) */}
      <div
        className={`flex w-96 flex-col shrink-0 border-l transition-colors duration-150 ${
          isDark
            ? 'border-zinc-800 bg-zinc-900/90 text-white'
            : 'border-[#E4E4E7] bg-white text-[#09090B] shadow-sm'
        }`}
      >
        {/* Cart Header */}
        <div
          className={`flex items-center justify-between border-b px-5 py-4 ${
            isDark ? 'border-zinc-800' : 'border-[#E4E4E7]'
          }`}
        >
          <div>
            <h2 className="text-base font-black tracking-tight flex items-center gap-2">
              <span>Order Summary</span>
              {totalItemsCount > 0 && (
                <span className="rounded-full bg-amber-500 text-zinc-950 px-2 py-0.5 text-xs font-black">
                  {totalItemsCount}
                </span>
              )}
            </h2>
            <p className={`text-[11px] ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Review items before placing
            </p>
          </div>

          {cart.length > 0 && (
            <button
              type="button"
              onClick={clearCart}
              className={`flex items-center gap-1 rounded-xl px-2.5 py-1 text-xs font-medium transition ${
                isDark
                  ? 'text-zinc-400 hover:text-rose-400 hover:bg-zinc-800'
                  : 'text-zinc-500 hover:text-rose-600 hover:bg-rose-50'
              }`}
            >
              <Trash2 className="h-3.5 w-3.5" />
              <span>Clear</span>
            </button>
          )}
        </div>

        {/* Cart Items List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2.5">
          {cart.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full text-center py-12">
              <div
                className={`mb-3 flex h-14 w-14 items-center justify-center rounded-2xl ${
                  isDark ? 'bg-zinc-800/80 text-zinc-500' : 'bg-zinc-100 text-zinc-400'
                }`}
              >
                <Plus className="h-7 w-7" />
              </div>
              <p className={`text-sm font-bold ${isDark ? 'text-zinc-400' : 'text-zinc-700'}`}>
                Cart is empty
              </p>
              <p className={`text-xs mt-1 max-w-[200px] ${isDark ? 'text-zinc-500' : 'text-zinc-400'}`}>
                Tap any menu item on the left to start this order
              </p>
            </div>
          ) : (
            cart.map((ci) => (
              <div
                key={ci.item.id}
                className={`flex items-center justify-between rounded-2xl border p-3 transition ${
                  isDark
                    ? 'border-zinc-800/60 bg-zinc-950/60'
                    : 'border-[#E4E4E7] bg-[#F4F4F5]'
                }`}
              >
                <div className="flex-1 min-w-0 pr-2">
                  <h4 className="text-xs font-bold truncate leading-tight">
                    {ci.item.name}
                  </h4>
                  <p className={`text-[11px] font-semibold mt-0.5 ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                    ₹{ci.item.price} each
                  </p>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <div className="flex items-center gap-1.5 rounded-xl border p-1 bg-opacity-50">
                    <button
                      type="button"
                      onClick={() => updateQuantity(ci.item.id, -1)}
                      className={`flex h-7 w-7 items-center justify-center rounded-lg transition active:scale-90 ${
                        isDark
                          ? 'bg-zinc-800 text-zinc-200 hover:bg-zinc-700'
                          : 'bg-white text-zinc-800 hover:bg-zinc-200 border border-zinc-200 shadow-sm'
                      }`}
                    >
                      <Minus className="h-3 w-3" />
                    </button>
                    <span className="w-5 text-center text-xs font-black">
                      {ci.quantity}
                    </span>
                    <button
                      type="button"
                      onClick={() => updateQuantity(ci.item.id, 1)}
                      className={`flex h-7 w-7 items-center justify-center rounded-lg transition active:scale-90 ${
                        isDark
                          ? 'bg-zinc-800 text-zinc-200 hover:bg-zinc-700'
                          : 'bg-white text-zinc-800 hover:bg-zinc-200 border border-zinc-200 shadow-sm'
                      }`}
                    >
                      <Plus className="h-3 w-3" />
                    </button>
                  </div>

                  <span className="w-14 text-right text-xs font-black">
                    ₹{ci.quantity * ci.item.price}
                  </span>
                </div>
              </div>
            ))
          )}
        </div>

        {/* Customer Details & Actions */}
        <div
          className={`border-t p-4 space-y-3 shrink-0 ${
            isDark ? 'border-zinc-800 bg-zinc-950/60' : 'border-[#E4E4E7] bg-white'
          }`}
        >
          {/* Customer Inputs */}
          <div className="grid grid-cols-2 gap-2">
            <div className="relative">
              <User className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-400" />
              <input
                type="text"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="Walk-in Customer"
                className={`w-full rounded-xl border pl-8 pr-2.5 py-1.5 text-xs focus:outline-none focus:border-amber-500 ${
                  isDark
                    ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-500'
                    : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
                }`}
              />
            </div>

            <div className="relative">
              <Phone className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-400" />
              <input
                type="tel"
                value={contactNumber}
                onChange={(e) => setContactNumber(e.target.value)}
                placeholder="Phone (optional)"
                className={`w-full rounded-xl border pl-8 pr-2.5 py-1.5 text-xs focus:outline-none focus:border-amber-500 ${
                  isDark
                    ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-500'
                    : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
                }`}
              />
            </div>
          </div>

          {/* Notes */}
          <div className="relative">
            <FileText className="absolute left-2.5 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-400" />
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Notes: less ice, strong, etc."
              className={`w-full rounded-xl border pl-8 pr-2.5 py-1.5 text-xs focus:outline-none focus:border-amber-500 ${
                isDark
                  ? 'bg-zinc-950 border-zinc-800 text-white placeholder-zinc-500'
                  : 'bg-[#FAFAFA] border-[#E4E4E7] text-[#09090B] placeholder-zinc-400'
              }`}
            />
          </div>

          {/* Payment Mode Selector (Matches Android App Options) */}
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setPaymentMode('UNPAID')}
              className={`flex-1 rounded-xl py-2 text-xs font-bold border transition active:scale-95 ${
                paymentMode === 'UNPAID'
                  ? 'border-rose-500 bg-rose-500/15 text-rose-500 shadow-sm'
                  : isDark
                  ? 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-white'
                  : 'border-[#E4E4E7] bg-[#F4F4F5] text-zinc-600 hover:text-zinc-900'
              }`}
            >
              ⏳ UNPAID
            </button>
            <button
              type="button"
              onClick={() => setPaymentMode('CASH')}
              className={`flex-1 rounded-xl py-2 text-xs font-bold border transition active:scale-95 ${
                paymentMode === 'CASH'
                  ? 'border-emerald-500 bg-emerald-500/15 text-emerald-500 shadow-sm'
                  : isDark
                  ? 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-white'
                  : 'border-[#E4E4E7] bg-[#F4F4F5] text-zinc-600 hover:text-zinc-900'
              }`}
            >
              💵 CASH
            </button>
            <button
              type="button"
              onClick={() => setPaymentMode('UPI')}
              className={`flex-1 rounded-xl py-2 text-xs font-bold border transition active:scale-95 ${
                paymentMode === 'UPI'
                  ? 'border-blue-500 bg-blue-500/15 text-blue-500 shadow-sm'
                  : isDark
                  ? 'border-zinc-800 bg-zinc-950 text-zinc-400 hover:text-white'
                  : 'border-[#E4E4E7] bg-[#F4F4F5] text-zinc-600 hover:text-zinc-900'
              }`}
            >
              📱 UPI
            </button>
          </div>

          {/* Grand Total Display */}
          <div
            className={`flex items-baseline justify-between rounded-2xl border p-3.5 ${
              isDark
                ? 'border-zinc-800/80 bg-zinc-950/60'
                : 'border-[#E4E4E7] bg-[#F4F4F5]'
            }`}
          >
            <div>
              <span className={`text-[11px] font-bold uppercase tracking-wider block ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                Grand Total
              </span>
              <span className={`text-[10px] ${isDark ? 'text-zinc-500' : 'text-zinc-400'}`}>
                Incl. taxes (5%)
              </span>
            </div>
            <div className="flex items-baseline text-2xl font-black text-amber-500">
              <span className="text-lg mr-0.5">₹</span>
              <span>{grandTotal}</span>
            </div>
          </div>

          {/* Last Placed Notification */}
          {lastPlacedOrder && (
            <div className="flex items-center gap-2 rounded-xl border border-emerald-500/40 bg-emerald-500/10 p-2.5 text-xs font-medium text-emerald-500">
              <CheckCircle className="h-4 w-4 shrink-0" />
              <span>
                Placed <strong>#{lastPlacedOrder.token}</strong> (₹{lastPlacedOrder.total})
              </span>
            </div>
          )}

          {/* Checkout Buttons */}
          <div className="grid grid-cols-2 gap-2 pt-1">
            <button
              type="button"
              disabled={cart.length === 0 || isSubmitting}
              onClick={() => handlePlaceOrder(false)}
              className={`flex items-center justify-center gap-1.5 rounded-2xl border py-3 text-xs font-bold transition disabled:opacity-40 disabled:cursor-not-allowed active:scale-95 ${
                isDark
                  ? 'border-zinc-800 bg-zinc-800/80 text-zinc-300 hover:bg-zinc-700'
                  : 'border-zinc-200 bg-zinc-100 text-zinc-700 hover:bg-zinc-200'
              }`}
            >
              <QrCode className="h-4 w-4 text-amber-500" />
              <span>{isSubmitting ? 'Placing...' : 'Punch & QR Bill'}</span>
            </button>

            <button
              type="button"
              disabled={cart.length === 0 || isSubmitting}
              onClick={() => handlePlaceOrder(true)}
              className="flex items-center justify-center gap-1.5 rounded-2xl bg-amber-500 py-3 text-xs font-black text-zinc-950 shadow-lg shadow-amber-500/20 hover:bg-amber-400 disabled:opacity-40 disabled:cursor-not-allowed active:scale-95 transition"
            >
              <Printer className="h-4 w-4" />
              <span>{isSubmitting ? 'Placing...' : 'Save & Print'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Bill & QR Code Pop-up Modal */}
      <OrderBillModal
        orderData={activeBillModalOrder}
        isOpen={!!activeBillModalOrder}
        onClose={() => setActiveBillModalOrder(null)}
        onPrint={(ord) => onOrderCompleted(ord, true)}
        onMarkPaid={async (orderId, mode) => {
          const isPaid = mode !== 'UNPAID';
          await updateOrderStatus(orderId, { is_paid: isPaid, payment_mode: mode });
          if (activeBillModalOrder && activeBillModalOrder.order.order_id === orderId) {
            setActiveBillModalOrder({
              ...activeBillModalOrder,
              order: { ...activeBillModalOrder.order, is_paid: isPaid, payment_mode: mode },
            });
          }
        }}
        onMarkComplete={async (orderId) => {
          await updateOrderStatus(orderId, {
            is_made: true,
            is_delivered: true,
            completed_timestamp: Date.now(),
          });
          if (activeBillModalOrder && activeBillModalOrder.order.order_id === orderId) {
            setActiveBillModalOrder({
              ...activeBillModalOrder,
              order: {
                ...activeBillModalOrder.order,
                is_made: true,
                is_delivered: true,
                completed_timestamp: Date.now(),
              },
            });
          }
        }}
        receiptConfig={receiptConfig}
        theme={theme}
      />
    </div>
  );
}
