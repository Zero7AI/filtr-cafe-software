'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Chip,
  Input,
  Label,
  SearchField,
  Skeleton,
  Table,
  TextField,
} from '@heroui/react';
import { Edit2, IndianRupee, Plus, ShoppingBag, Trash2, Users } from 'lucide-react';
import AdminModal from '@/components/admin/AdminModal';
import { useAdminContext } from '@/components/admin/AdminShell';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import { formatInr, formatIstDateTime } from '@/lib/domain/ist';
import { Customer, OrderWithItems } from '@/lib/domain/types';

export default function AdminCustomersPage() {
  const { refreshTick } = useAdminContext();
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

  const stats = useMemo(() => {
    const total = customers.length;
    const repeat = customers.filter((c) => c.total_orders >= 2).length;
    const revenue = customers.reduce((s, c) => s + Number(c.total_spent || 0), 0);
    const avgLtv = total > 0 ? revenue / total : 0;
    return { total, repeat, revenue, avgLtv };
  }, [customers]);

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
    setHistoryOrders([]);
    const res = await fetch(
      `/api/admin/customers?customerName=${encodeURIComponent(c.name)}`,
      { cache: 'no-store' }
    );
    if (res.ok) {
      const d = await res.json();
      setHistoryOrders(d.orders || []);
    }
  };

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          label="Total Customers"
          value={String(stats.total)}
          subtitle="Saved loyalty profiles"
          icon={<Users className="h-4 w-4" />}
        />
        <KpiCard
          label="Repeat Buyers (2+)"
          value={String(stats.repeat)}
          subtitle={`${stats.total > 0 ? Math.round((stats.repeat / stats.total) * 100) : 0}% retention`}
          badgeColor="success"
          icon={<ShoppingBag className="h-4 w-4" />}
        />
        <KpiCard
          label="Tracked Lifetime Revenue"
          value={formatInr(stats.revenue)}
          subtitle="Across CRM accounts"
          icon={<IndianRupee className="h-4 w-4" />}
        />
        <KpiCard
          label="Avg Lifetime Value"
          value={formatInr(stats.avgLtv)}
          subtitle="Average spend per customer"
        />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <SearchField
          value={search}
          onChange={setSearch}
          variant="secondary"
          className="w-full sm:w-80"
        >
          <SearchField.Group>
            <SearchField.SearchIcon />
            <SearchField.Input placeholder="Search customer or phone..." />
            <SearchField.ClearButton />
          </SearchField.Group>
        </SearchField>

        <Button
          size="sm"
          variant="primary"
          onPress={() => {
            setEditCustomer({ name: '', contact_number: '' });
            setEditModal(true);
          }}
        >
          <Plus className="h-3.5 w-3.5" />
          <span>Add Customer</span>
        </Button>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 rounded-2xl" />
          ))}
        </div>
      ) : (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="Customers CRM table">
                  <Table.Header>
                    <Table.Column isRowHeader>Customer Name</Table.Column>
                    <Table.Column>Phone</Table.Column>
                    <Table.Column className="text-right">Orders</Table.Column>
                    <Table.Column className="text-right">Total Spent</Table.Column>
                    <Table.Column className="text-right">Avg Ticket</Table.Column>
                    <Table.Column>Last Visit</Table.Column>
                    <Table.Column className="text-right">Actions</Table.Column>
                  </Table.Header>
                  <Table.Body
                    renderEmptyState={() => (
                      <div className="py-10 text-center text-xs text-muted">
                        No customers match your search
                      </div>
                    )}
                  >
                    {filtered.map((c) => {
                      const avgTicket =
                        c.total_orders > 0 ? c.total_spent / c.total_orders : 0;
                      return (
                        <Table.Row key={c.id} id={String(c.id)}>
                          <Table.Cell>
                            <button
                              type="button"
                              onClick={() => openHistory(c)}
                              className="font-semibold hover:text-accent hover:underline text-left"
                            >
                              {c.name}
                            </button>
                            {c.total_orders >= 3 ? (
                              <Chip size="sm" variant="soft" color="accent" className="ml-2">
                                Regular
                              </Chip>
                            ) : null}
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted font-mono">
                            {c.contact_number || '—'}
                          </Table.Cell>
                          <Table.Cell className="text-right font-semibold tabular-nums">
                            {c.total_orders}
                          </Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums text-accent">
                            {formatInr(c.total_spent)}
                          </Table.Cell>
                          <Table.Cell className="text-right text-xs text-muted tabular-nums">
                            {avgTicket > 0 ? formatInr(avgTicket) : '—'}
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted whitespace-nowrap">
                            {c.last_order_timestamp ? formatIstDateTime(c.last_order_timestamp) : '—'}
                          </Table.Cell>
                          <Table.Cell className="text-right whitespace-nowrap">
                            <div className="inline-flex items-center gap-1">
                              <Button
                                isIconOnly
                                size="sm"
                                variant="ghost"
                                onPress={() => {
                                  setEditCustomer(c);
                                  setEditModal(true);
                                }}
                                aria-label={`Edit ${c.name}`}
                              >
                                <Edit2 className="h-3.5 w-3.5" />
                              </Button>
                              <Button
                                isIconOnly
                                size="sm"
                                variant="danger-soft"
                                onPress={() => deleteCustomer(c)}
                                aria-label={`Delete ${c.name}`}
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </Button>
                            </div>
                          </Table.Cell>
                        </Table.Row>
                      );
                    })}
                  </Table.Body>
                </Table.Content>
              </Table.ScrollContainer>
            </Table>
          </Card.Content>
        </Card>
      )}

      <AdminModal
        isOpen={editModal}
        onClose={() => setEditModal(false)}
        title={editCustomer.id ? 'Edit Customer' : 'Add Customer'}
      >
        <form onSubmit={saveCustomer} className="space-y-3">
          <TextField
            value={editCustomer.name || ''}
            onChange={(v) => setEditCustomer({ ...editCustomer, name: v })}
            isRequired
            fullWidth
          >
            <Label className="text-xs font-medium">Customer Name</Label>
            <Input variant="secondary" />
          </TextField>

          <div className="space-y-1">
            <Label className="text-xs font-medium">Phone Number</Label>
            <Input
              type="tel"
              variant="secondary"
              fullWidth
              value={editCustomer.contact_number || ''}
              onChange={(e) =>
                setEditCustomer({ ...editCustomer, contact_number: e.target.value })
              }
            />
          </div>

          <Button type="submit" variant="primary" fullWidth>
            Save Customer
          </Button>
        </form>
      </AdminModal>

      <AdminModal
        isOpen={!!historyCustomer}
        onClose={() => setHistoryCustomer(null)}
        title={`Order History — ${historyCustomer?.name || ''}`}
        size="lg"
      >
        <div className="space-y-2.5">
          {historyOrders.length === 0 ? (
            <p className="text-xs text-muted py-6 text-center">No orders found.</p>
          ) : (
            historyOrders.map(({ order, items }) => (
              <Card key={order.order_id} variant="secondary" className="border border-separator">
                <Card.Content className="p-3 text-xs space-y-1">
                  <div className="flex justify-between font-bold">
                    <span className="font-mono text-accent">{order.order_id}</span>
                    <span className="tabular-nums">{formatInr(order.total_amount)}</span>
                  </div>
                  <p className="text-muted">{formatIstDateTime(order.timestamp)}</p>
                  <p>{items.map((i) => `${i.quantity}x ${i.item_name}`).join(', ')}</p>
                </Card.Content>
              </Card>
            ))
          )}
        </div>
      </AdminModal>
    </div>
  );
}
