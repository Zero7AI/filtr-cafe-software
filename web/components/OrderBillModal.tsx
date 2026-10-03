'use client';

import React, { useState, useEffect, useMemo } from 'react';
import QRCode from 'qrcode';
import { OrderWithItems, UpiAccount } from '@/lib/types';
import { ReceiptDesignConfig, defaultReceiptConfig, fetchUpiAccounts } from '@/lib/supabase';
import {
  Printer,
  Check,
  CheckCircle2,
  X,
  QrCode,
  Copy,
  CreditCard,
  Share2,
  DollarSign,
  Coffee,
} from 'lucide-react';

interface OrderBillModalProps {
  orderData: OrderWithItems | null;
  isOpen: boolean;
  onClose: () => void;
  onPrint: (order: OrderWithItems) => void;
  onMarkPaid?: (orderId: string, paymentMode: 'CASH' | 'UPI' | 'UNPAID') => Promise<void> | void;
  onMarkComplete?: (orderId: string) => Promise<void> | void;
  receiptConfig?: ReceiptDesignConfig;
  theme?: 'dark' | 'light';
}

export default function OrderBillModal({
  orderData,
  isOpen,
  onClose,
  onPrint,
  onMarkPaid,
  onMarkComplete,
  receiptConfig = defaultReceiptConfig,
  theme = 'dark',
}: OrderBillModalProps) {
  const [upiAccounts, setUpiAccounts] = useState<UpiAccount[]>([
    {
      vpa: '9907839311@ybl',
      payee_name: 'FILTR Coffee Store',
      is_default: true,
      note: 'FILTR Coffee Payment',
    },
  ]);
  const [selectedUpiAccount, setSelectedUpiAccount] = useState<UpiAccount | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string>('');
  const [copiedLink, setCopiedLink] = useState<boolean>(false);
  const [isUpdatingStatus, setIsUpdatingStatus] = useState<boolean>(false);

  const isDark = theme === 'dark';

  // Load UPI accounts from Supabase
  useEffect(() => {
    fetchUpiAccounts().then((accounts) => {
      if (accounts && accounts.length > 0) {
        setUpiAccounts(accounts);
        const defaultAcc = accounts.find((a) => a.is_default) || accounts[0];
        setSelectedUpiAccount(defaultAcc);
      }
    });
  }, []);

  // Compute active UPI account
  const activeAccount = useMemo(() => {
    return (
      selectedUpiAccount ||
      upiAccounts.find((a) => a.is_default) ||
      upiAccounts[0] || {
        vpa: '9907839311@ybl',
        payee_name: 'FILTR Coffee Store',
        is_default: true,
      }
    );
  }, [selectedUpiAccount, upiAccounts]);

  // Compute UPI Payment URL
  const upiPaymentUrl = useMemo(() => {
    if (!orderData) return '';
    const vpa = activeAccount.vpa;
    const payee = encodeURIComponent(activeAccount.payee_name || 'FILTR Coffee Store');
    const amount = Number(orderData.order.total_amount).toFixed(2);
    const note = encodeURIComponent(`Order ${orderData.order.order_id}`);
    return `upi://pay?pa=${vpa}&pn=${payee}&am=${amount}&cu=INR&tn=${note}`;
  }, [orderData, activeAccount]);

  // Generate QR code data URL whenever upiPaymentUrl changes
  useEffect(() => {
    if (!upiPaymentUrl) {
      setQrDataUrl('');
      return;
    }

    let isMounted = true;
    QRCode.toDataURL(upiPaymentUrl, {
      width: 320,
      margin: 1,
      color: {
        dark: '#000000',
        light: '#ffffff',
      },
      errorCorrectionLevel: 'M',
    })
      .then((url) => {
        if (isMounted) setQrDataUrl(url);
      })
      .catch((err) => {
        console.error('QR code generation error, falling back:', err);
        if (isMounted) {
          // Reliable fallback via QRServer API
          setQrDataUrl(
            `https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=${encodeURIComponent(
              upiPaymentUrl
            )}`
          );
        }
      });

    return () => {
      isMounted = false;
    };
  }, [upiPaymentUrl]);

  if (!isOpen || !orderData) return null;

  const { order, items } = orderData;
  const token = order.order_id.split('-').pop() || '0000';
  const dateFormatted = new Date(order.timestamp).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });

  const isCompleted = order.is_made && order.is_delivered;

  // Copy UPI link to clipboard
  const handleCopyUpiLink = () => {
    if (!upiPaymentUrl) return;
    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(upiPaymentUrl);
      } else {
        // Safe fallback for Android 5.1 / legacy Chrome
        const textArea = document.createElement('textarea');
        textArea.value = upiPaymentUrl;
        textArea.style.position = 'fixed';
        textArea.style.opacity = '0';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();
        document.execCommand('copy');
        document.body.removeChild(textArea);
      }
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2500);
    } catch (e) {
      console.error('Clipboard copy error:', e);
    }
  };

  // Quick Action: Mark Paid or Unpaid
  const handleSetPaid = async (mode: 'CASH' | 'UPI' | 'UNPAID') => {
    if (!onMarkPaid || isUpdatingStatus) return;
    setIsUpdatingStatus(true);
    try {
      await onMarkPaid(order.order_id, mode);
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  // Quick Action: Mark Complete
  const handleSetComplete = async () => {
    if (!onMarkComplete || isUpdatingStatus) return;
    setIsUpdatingStatus(true);
    try {
      await onMarkComplete(order.order_id);
    } finally {
      setIsUpdatingStatus(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-5 bg-black/75 overflow-y-auto"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className={`relative w-full max-w-xl rounded-2xl border shadow-2xl transition-all my-auto max-h-[92vh] flex flex-col ${
          isDark
            ? 'bg-zinc-950 border-zinc-800 text-white'
            : 'bg-white border-[#E4E4E7] text-[#09090B]'
        }`}
      >
        {/* Top Header */}
        <div
          className={`flex items-center justify-between px-5 py-3.5 border-b shrink-0 ${
            isDark ? 'border-zinc-800 bg-zinc-900/60' : 'border-[#E4E4E7] bg-zinc-50'
          }`}
        >
          <div className="flex items-center gap-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-amber-500 text-zinc-950 shadow-sm">
              <Coffee className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-base font-black tracking-wide">FILTR BILL RECEIPT</h2>
                <span className="rounded-md bg-amber-500/20 text-amber-500 border border-amber-500/30 px-2 py-0.5 text-xs font-black">
                  #{token}
                </span>
              </div>
              <p className={`text-[11px] font-mono ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                {order.order_id}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            className={`rounded-xl p-2 transition active:scale-95 ${
              isDark
                ? 'hover:bg-zinc-800 text-zinc-400 hover:text-white'
                : 'hover:bg-zinc-200 text-zinc-500 hover:text-zinc-900'
            }`}
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Scrollable Bill Content */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-6 space-y-4">
          {/* Printable Style Bill Preview Container */}
          <div
            className={`rounded-2xl border p-4 sm:p-5 shadow-sm transition-colors ${
              isDark
                ? 'bg-zinc-900/90 border-zinc-800'
                : 'bg-[#FAFAFA] border-[#E4E4E7]'
            }`}
          >
            {/* Store Branding */}
            <div className="text-center pb-3 border-b border-dashed border-zinc-300 dark:border-zinc-700">
              <h3 className="text-xl font-black tracking-widest uppercase">FILTR COFFEE</h3>
              {receiptConfig.headerSubtitle && (
                <p className={`text-xs italic mt-0.5 ${isDark ? 'text-zinc-400' : 'text-zinc-600'}`}>
                  {receiptConfig.headerSubtitle}
                </p>
              )}
              {receiptConfig.tagline && (
                <p className="text-[11px] text-amber-500 font-medium">{receiptConfig.tagline}</p>
              )}
            </div>

            {/* Order Metadata */}
            <div className="py-3 text-xs space-y-1 border-b border-zinc-200 dark:border-zinc-800">
              <div className="flex justify-between">
                <span className={isDark ? 'text-zinc-400' : 'text-zinc-500'}>Date & Time:</span>
                <span className="font-semibold">{dateFormatted}</span>
              </div>
              <div className="flex justify-between">
                <span className={isDark ? 'text-zinc-400' : 'text-zinc-500'}>Customer:</span>
                <span className="font-bold">
                  {order.customer_name || 'Walk-in'}
                  {order.contact_number && (
                    <span className="font-normal ml-1">({order.contact_number})</span>
                  )}
                </span>
              </div>
              <div className="flex justify-between">
                <span className={isDark ? 'text-zinc-400' : 'text-zinc-500'}>Payment Status:</span>
                <span
                  className={`font-black uppercase tracking-wider text-[11px] px-2 py-0.5 rounded-md ${
                    order.is_paid
                      ? 'bg-emerald-500/15 text-emerald-500 border border-emerald-500/30'
                      : 'bg-rose-500/15 text-rose-500 border border-rose-500/30'
                  }`}
                >
                  {order.is_paid ? `PAID (${order.payment_mode || 'CASH'})` : 'UNPAID'}
                </span>
              </div>
            </div>

            {/* Itemized Bill Table */}
            <div className="py-3">
              <div className="flex items-center justify-between text-[11px] font-bold pb-2 border-b border-zinc-300 dark:border-zinc-700 text-zinc-500 uppercase tracking-wider">
                <span className="flex-1">Item</span>
                <span className="w-12 text-center">Qty</span>
                <span className="w-16 text-right">Rate</span>
                <span className="w-16 text-right">Subtotal</span>
              </div>

              <div className="divide-y divide-zinc-200 dark:divide-zinc-800/80">
                {items.map((it, idx) => (
                  <div key={idx} className="flex items-center justify-between py-2 text-xs">
                    <div className="flex-1 pr-2">
                      <p className="font-bold">{it.item_name}</p>
                    </div>
                    <span className="w-12 text-center font-black text-amber-500">
                      x{it.quantity}
                    </span>
                    <span
                      className={`w-16 text-right ${isDark ? 'text-zinc-400' : 'text-zinc-600'}`}
                    >
                      ₹{it.item_price}
                    </span>
                    <span className="w-16 text-right font-black">₹{it.subtotal}</span>
                  </div>
                ))}
              </div>
            </div>

            {/* Notes if any */}
            {order.notes && (
              <div
                className={`rounded-xl p-2.5 my-2 text-xs border ${
                  isDark
                    ? 'border-amber-500/20 bg-amber-500/5 text-amber-300'
                    : 'border-amber-200 bg-amber-50 text-amber-900'
                }`}
              >
                <span className="font-bold">Order Note: </span>
                <span>{order.notes}</span>
              </div>
            )}

            {/* Total Section */}
            <div className="pt-3 border-t-2 border-zinc-900 dark:border-zinc-100 flex items-baseline justify-between">
              <div>
                <span className="text-xs font-bold text-zinc-400 uppercase tracking-wider">
                  Grand Total
                </span>
                <p className="text-[11px] text-zinc-500">
                  {items.reduce((acc, ci) => acc + ci.quantity, 0)} item(s) in bill
                </p>
              </div>
              <div className="text-right">
                <span className="text-2xl sm:text-3xl font-black text-amber-500">
                  ₹{Number(order.total_amount).toFixed(2)}
                </span>
              </div>
            </div>
          </div>

          {/* DYNAMIC UPI QR CODE MODULE */}
          <div
            className={`rounded-2xl border p-4 sm:p-5 text-center transition-colors ${
              isDark
                ? 'bg-zinc-900 border-zinc-800 shadow-lg'
                : 'bg-white border-[#E4E4E7] shadow-md'
            }`}
          >
            <div className="flex items-center justify-center gap-2 mb-2">
              <QrCode className="h-5 w-5 text-amber-500" />
              <h4 className="text-sm font-black tracking-wider uppercase">
                SCAN TO PAY VIA UPI
              </h4>
            </div>

            <p className={`text-xs ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
              Scan with PhonePe, Google Pay, Paytm, or any UPI app
            </p>

            {/* Account Selector if multiple accounts exist */}
            {upiAccounts.length > 1 && (
              <div className="flex items-center justify-center gap-1.5 my-3 flex-wrap">
                {upiAccounts.map((acc, index) => {
                  const isSel = acc.vpa === activeAccount.vpa;
                  return (
                    <button
                      key={index}
                      type="button"
                      onClick={() => setSelectedUpiAccount(acc)}
                      className={`rounded-xl px-3 py-1 text-xs font-bold transition active:scale-95 ${
                        isSel
                          ? 'bg-amber-500 text-zinc-950 shadow-sm'
                          : isDark
                          ? 'bg-zinc-800 text-zinc-300 hover:bg-zinc-700'
                          : 'bg-zinc-100 text-zinc-700 hover:bg-zinc-200'
                      }`}
                    >
                      {acc.vpa}
                    </button>
                  );
                })}
              </div>
            )}

            {/* QR Code Graphic Box */}
            <div className="flex flex-col items-center justify-center my-3">
              <div className="p-3 bg-white rounded-2xl border-2 border-zinc-900 dark:border-zinc-700 shadow-md inline-block">
                {qrDataUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    src={qrDataUrl}
                    alt={`UPI Payment QR for ₹${order.total_amount}`}
                    className="w-48 h-48 sm:w-56 sm:h-56 object-contain"
                  />
                ) : (
                  <div className="w-48 h-48 sm:w-56 sm:h-56 flex items-center justify-center bg-zinc-100 text-zinc-500 text-xs font-bold animate-pulse">
                    Generating QR...
                  </div>
                )}
              </div>

              <div className="mt-2.5 text-center">
                <span className="text-base font-black text-emerald-500">
                  ₹{Number(order.total_amount).toFixed(2)}
                </span>
                <p className="text-xs font-mono font-bold mt-0.5 text-blue-500">
                  {activeAccount.vpa}
                </p>
                <p className={`text-[11px] ${isDark ? 'text-zinc-400' : 'text-zinc-500'}`}>
                  Payee: {activeAccount.payee_name || 'FILTR Coffee Store'}
                </p>
              </div>

              {/* Copy UPI Link Option */}
              <button
                type="button"
                onClick={handleCopyUpiLink}
                className={`mt-2 flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-semibold transition ${
                  copiedLink
                    ? 'text-emerald-500 bg-emerald-500/10'
                    : isDark
                    ? 'text-zinc-400 hover:text-white bg-zinc-800/80 hover:bg-zinc-800'
                    : 'text-zinc-600 hover:text-zinc-900 bg-zinc-100 hover:bg-zinc-200'
                }`}
              >
                {copiedLink ? (
                  <>
                    <Check className="h-3 w-3 stroke-[3]" />
                    <span>UPI Link Copied!</span>
                  </>
                ) : (
                  <>
                    <Copy className="h-3 w-3" />
                    <span>Copy UPI Link</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* Modal Action Controls Footer */}
        <div
          className={`p-4 border-t shrink-0 flex flex-col sm:flex-row items-center justify-between gap-2.5 ${
            isDark ? 'border-zinc-800 bg-zinc-900/70' : 'border-[#E4E4E7] bg-zinc-50'
          }`}
        >
          {/* Quick Payment Marking Buttons */}
          <div className="flex items-center gap-2 w-full sm:w-auto flex-wrap">
            {!order.is_paid && onMarkPaid ? (
              <>
                <button
                  type="button"
                  disabled={isUpdatingStatus}
                  onClick={() => handleSetPaid('CASH')}
                  className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white px-3.5 py-2.5 text-xs font-bold shadow-sm active:scale-95 transition disabled:opacity-50"
                >
                  <DollarSign className="h-3.5 w-3.5" />
                  <span>Mark Cash</span>
                </button>

                <button
                  type="button"
                  disabled={isUpdatingStatus}
                  onClick={() => handleSetPaid('UPI')}
                  className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white px-3.5 py-2.5 text-xs font-bold shadow-sm active:scale-95 transition disabled:opacity-50"
                >
                  <CreditCard className="h-3.5 w-3.5" />
                  <span>Mark UPI</span>
                </button>
              </>
            ) : order.is_paid && onMarkPaid ? (
              <div className="flex items-center gap-2">
                <span className="flex items-center gap-1 px-3 py-1.5 rounded-xl bg-emerald-500/15 text-emerald-500 border border-emerald-500/30 text-xs font-bold">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  <span>Paid ({order.payment_mode || 'CASH'})</span>
                </span>
                <button
                  type="button"
                  disabled={isUpdatingStatus}
                  onClick={() => handleSetPaid('UNPAID')}
                  className="text-xs font-semibold text-rose-500 hover:text-rose-400 hover:underline px-1 py-1"
                >
                  Unmark (Set Unpaid)
                </button>
              </div>
            ) : null}

            {!isCompleted && onMarkComplete && (
              <button
                type="button"
                disabled={isUpdatingStatus}
                onClick={handleSetComplete}
                className="flex-1 sm:flex-initial flex items-center justify-center gap-1.5 rounded-xl bg-amber-500 hover:bg-amber-400 text-zinc-950 px-3.5 py-2.5 text-xs font-bold shadow-sm active:scale-95 transition disabled:opacity-50"
              >
                <Check className="h-3.5 w-3.5 stroke-[3]" />
                <span>Mark Complete</span>
              </button>
            )}
          </div>

          {/* Primary Actions: Print Receipt & Close */}
          <div className="flex items-center gap-2 w-full sm:w-auto justify-end">
            <button
              type="button"
              onClick={() => onPrint(orderData)}
              className="flex-1 sm:flex-initial flex items-center justify-center gap-2 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-zinc-950 px-5 py-2.5 text-xs font-black shadow-md shadow-emerald-500/20 active:scale-95 transition"
            >
              <Printer className="h-4 w-4" />
              <span>Print Bill</span>
            </button>

            <button
              type="button"
              onClick={onClose}
              className={`rounded-xl px-4 py-2.5 text-xs font-bold border transition active:scale-95 ${
                isDark
                  ? 'border-zinc-800 bg-zinc-900 text-zinc-300 hover:text-white hover:bg-zinc-800'
                  : 'border-[#E4E4E7] bg-white text-zinc-700 hover:text-zinc-900 hover:bg-zinc-100'
              }`}
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
