'use client';

import React from 'react';
import { OrderWithItems } from '@/lib/types';
import { ReceiptDesignConfig, defaultReceiptConfig } from '@/lib/supabase';

interface ReceiptProps {
  orderData: OrderWithItems | null;
  paperWidth?: '80mm' | '58mm';
  receiptConfig?: ReceiptDesignConfig;
}

export default function Receipt({
  orderData,
  paperWidth = '80mm',
  receiptConfig = defaultReceiptConfig,
}: ReceiptProps) {
  if (!orderData) return null;

  const { order, items } = orderData;
  const token = order.order_id.split('-').pop() || '0000';
  const dateStr = new Date(order.timestamp).toLocaleString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });

  return (
    <div id="thermal-receipt" className="font-mono text-black bg-white">
      {/* HEADER */}
      <div className="text-center mb-2">
        <h1 className="text-xl font-black tracking-wider uppercase">FILTR COFFEE</h1>
        {receiptConfig.headerSubtitle && (
          <p className="text-xs italic text-gray-700">{receiptConfig.headerSubtitle}</p>
        )}
        {receiptConfig.tagline && (
          <p className="text-[10px] text-gray-600">{receiptConfig.tagline}</p>
        )}
        <div className="border-b-2 border-black my-1" />
      </div>

      {/* TOKEN & METADATA */}
      <div className="text-center mb-2">
        <div className="text-2xl font-black border-2 border-black inline-block px-3 py-1 my-1">
          TOKEN #{token}
        </div>
        <p className="text-xs font-bold mt-1">Order ID: {order.order_id}</p>
        <p className="text-xs">{dateStr}</p>
        {order.customer_name && order.customer_name !== 'Walk-in' && (
          <p className="text-xs font-semibold">Customer: {order.customer_name}</p>
        )}
        {order.contact_number && (
          <p className="text-xs">Phone: {order.contact_number}</p>
        )}
        <div className="border-b border-dashed border-black my-1" />
      </div>

      {/* ITEMS TABLE */}
      <table className="w-full text-xs mb-2">
        <thead>
          <tr className="border-b border-black text-left">
            <th className="py-1">Item</th>
            <th className="py-1 text-center">Qty</th>
            <th className="py-1 text-right">Rate</th>
            <th className="py-1 text-right">Amt</th>
          </tr>
        </thead>
        <tbody>
          {items.map((it, idx) => (
            <tr key={idx} className="border-b border-gray-300">
              <td className="py-1 font-semibold pr-1">{it.item_name}</td>
              <td className="py-1 text-center">x{it.quantity}</td>
              <td className="py-1 text-right">₹{it.item_price}</td>
              <td className="py-1 text-right font-bold">₹{it.subtotal}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* TOTALS & PAYMENT */}
      <div className="border-t-2 border-black pt-1 mb-2">
        <div className="flex justify-between text-base font-black">
          <span>GRAND TOTAL:</span>
          <span>₹{order.total_amount}</span>
        </div>
        <div className="flex justify-between text-xs mt-1">
          <span>Payment Mode:</span>
          <span className="font-bold">{order.payment_mode}</span>
        </div>
        <div className="flex justify-between text-xs">
          <span>Payment Status:</span>
          <span className="font-bold">{order.is_paid ? 'PAID' : 'UNPAID'}</span>
        </div>
        {order.notes && (
          <div className="text-xs mt-1 text-gray-800">
            <span className="font-semibold">Notes:</span> {order.notes}
          </div>
        )}
      </div>

      {/* FOOTER */}
      <div className="text-center text-xs border-t border-black pt-2 mt-2 space-y-0.5">
        {receiptConfig.footerLine1 && (
          <p className="font-semibold">{receiptConfig.footerLine1}</p>
        )}
        {receiptConfig.footerLine2 && (
          <p className="text-[10px] text-gray-700">{receiptConfig.footerLine2}</p>
        )}
        {receiptConfig.footerLine3 && (
          <p className="text-[9px] text-gray-600 italic">{receiptConfig.footerLine3}</p>
        )}
      </div>
    </div>
  );
}
