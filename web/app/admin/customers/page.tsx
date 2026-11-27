'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import { Search, Plus, Trash2, Edit2 } from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import Modal from '@/components/admin/Modal';
import { useToast } from '@/components/admin/ToastContext';
import { formatInr, formatIstDateTime } from '@/lib/domain/ist';
import { Customer, OrderWithItems } from '@/lib/domain/types';

export default function AdminCustomersPage() {
  const { isDark, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [customers, setCustomers] = useState<Customer[]>([]);
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [editModal, setEditModal] = useState(false);
  const [editCustomer, setEditCustomer] = useState<Partial<Customer>>({});

  const [historyCustomer, setHistoryCustomer] = useState<Customer | null>(null);
  const [historyOrders, setHistoryOrders] = useState<OrderWithItems[]>([]);

  const loadCustomers = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/customers', { cache: 'no-store' });
      if (res.ok) {
        const d = await res.json();
        setCustomers(d.customers || []);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCustomers();
  }, [loadCustomers, refreshTick]);

  const filtered = useMemo(() => {
    if (!search.trim()) return customers;
    const q = search.trim().toLowerCase();
    return customers.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.contact_number.toLowerCase().includes(q)
    );
  }, [customers, search]);

  const saveCustomer = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/customers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editCustomer),
    });
    if (res.ok) {
      pushToast('Saved customer', 'success');
      setEditModal(false);
      loadCustomers();
    }
  };

  const deleteCustomer = async (c: Customer) => {
    if (!confirm(`Delete customer "${c.name}"?`)) return;
    const res = await fetch(`/api/admin/customers?id=${c.id}`, { method: 'DELETE' });
    if (res.ok) {
      pushToast(`Deleted ${c.name}`, 'success');
      loadCustomers();
    }
  };

  const openHistory = async (c: Customer) => {
    setHistoryCustomer(c);
    const res = await fetch(
      `/api/admin/customers?customerName=${encodeURIComponent(c.name)}`,
      { cache: 'no-store' }
    );
    if (res.ok) {
      const d = await res.json();
      setHistoryOrders(d.orders || []);
    }
  };

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-zinc-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search customer or phone..."
            className={`w-full rounded-xl border pl-9 pr-3 py-2 text-xs ${
              isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
            }`}
          />
        </div>
        <button
          type="button"
          onClick={() => {
            setEditCustomer({ name: '', contact_number: '' });
            setEditModal(true);
          }}
          className="flex items-center gap-1.5 rounded-xl bg-amber-500 px-3.5 py-2 text-xs font-black text-zinc-950"
        >
          <Plus className="h-3.5 w-3.5" />
          <span>Add Customer</span>
        </button>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading customers...</div>
      ) : (
        <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
          <div className="overflow-x-auto">
            <table className="w-full text-xs text-left">
              <thead>
                <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[10px] uppercase text-zinc-500">
                  <th className="py-3 px-4">Name</th>
                  <th className="py-3 px-4">Phone</th>
                  <th className="py-3 px-4">Orders</th>
                  <th className="py-3 px-4">Total Spent</th>
                  <th className="py-3 px-4">Last Visit</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                {filtered.map((c) => (
                  <tr key={c.id}>
                    <td className="py-2.5 px-4 font-bold">
                      <button
                        type="button"
                        onClick={() => openHistory(c)}
                        className="hover:text-amber-500 hover:underline"
                      >
                        {c.name}
                      </button>
                    </td>
                    <td className="py-2.5 px-4 text-zinc-400">{c.contact_number || '—'}</td>
                    <td className="py-2.5 px-4 font-bold">{c.total_orders}</td>
                    <td className="py-2.5 px-4 font-black text-amber-500">
                      {formatInr(c.total_spent)}
                    </td>
                    <td className="py-2.5 px-4 text-zinc-400">
                      {c.last_order_timestamp ? formatIstDateTime(c.last_order_timestamp) : '—'}
                    </td>
                    <td className="py-2.5 px-4 text-right space-x-1">
                      <button
                        type="button"
                        onClick={() => {
                          setEditCustomer(c);
                          setEditModal(true);
                        }}
                        className="p-1.5 text-zinc-400 hover:text-amber-500"
                      >
                        <Edit2 className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        onClick={() => deleteCustomer(c)}
                        className="p-1.5 text-zinc-400 hover:text-rose-500"
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
      )}

      <Modal
        open={editModal}
        onClose={() => setEditModal(false)}
        title={editCustomer.id ? 'Edit Customer' : 'Add Customer'}
        isDark={isDark}
      >
        <form onSubmit={saveCustomer} className="space-y-3">
          <div>
            <label className="block text-xs font-bold mb-1">Customer Name</label>
            <input
              type="text"
              value={editCustomer.name || ''}
              onChange={(e) => setEditCustomer({ ...editCustomer, name: e.target.value })}
              required
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
            />
          </div>
          <div>
            <label className="block text-xs font-bold mb-1">Phone Number</label>
            <input
              type="tel"
              value={editCustomer.contact_number || ''}
              onChange={(e) =>
                setEditCustomer({ ...editCustomer, contact_number: e.target.value })
              }
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
            />
          </div>
          <button
            type="submit"
            className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
          >
            Save Customer
          </button>
        </form>
      </Modal>

      <Modal
        open={!!historyCustomer}
        onClose={() => setHistoryCustomer(null)}
        title={`Order History — ${historyCustomer?.name || ''}`}
        maxWidthClass="max-w-xl"
        isDark={isDark}
      >
        <div className="space-y-2">
          {historyOrders.length === 0 ? (
            <p className="text-xs text-zinc-500">No orders found.</p>
          ) : (
            historyOrders.map(({ order, items }) => (
              <div
                key={order.order_id}
                className="rounded-xl border border-zinc-800 bg-zinc-900/60 p-3 text-xs space-y-1"
              >
                <div className="flex justify-between font-bold">
                  <span className="font-mono text-amber-500">{order.order_id}</span>
                  <span>{formatInr(order.total_amount)}</span>
                </div>
                <p className="text-zinc-400">{formatIstDateTime(order.timestamp)}</p>
                <p className="text-zinc-300">
                  {items.map((i) => `${i.quantity}x ${i.item_name}`).join(', ')}
                </p>
              </div>
            ))
          )}
        </div>
      </Modal>
    </div>
  );
}
