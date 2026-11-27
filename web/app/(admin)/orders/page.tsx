'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Chip,
  SearchField,
  Skeleton,
  Table,
  Tabs,
} from '@heroui/react';
import {
  Check,
  CheckCircle2,
  Coffee,
  CreditCard,
  Download,
  IndianRupee,
  LayoutGrid,
  List,
  Printer,
  QrCode,
  Receipt as ReceiptIcon,
  ShoppingBag,
  Trash2,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import DateRangeBar from '@/components/admin/DateRangeBar';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import OrderBillModal from '@/components/OrderBillModal';
import Receipt from '@/components/Receipt';
import { DateRangePreset, formatInr, formatIstDateTime, getIstMonthKey } from '@/lib/domain/ist';
import { OrderWithItems } from '@/lib/domain/types';
import { defaultReceiptConfig, fetchReceiptConfig, ReceiptDesignConfig } from '@/lib/supabase';

export default function AdminOrdersPage() {
  const { theme, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [monthKey, setMonthKey] = useState(getIstMonthKey());
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [statusFilter, setStatusFilter] = useState<
    'ALL' | 'PENDING' | 'UNPAID' | 'MADE' | 'DELIVERED'
  >('ALL');
  const [viewMode, setViewMode] = useState<'TABLE' | 'CARDS'>('TABLE');
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
      if (range === 'MONTH') qs.set('monthKey', monthKey);
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
  }, [range, monthKey, customStart, customEnd]);

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
    let unpaidCount = 0;
    for (const { order } of orders) {
      gross += order.total_amount || 0;
      if (order.is_paid) {
        if (order.payment_mode === 'UPI') upi += order.total_amount || 0;
        else cash += order.total_amount || 0;
      } else {
        unpaid += order.total_amount || 0;
        unpaidCount++;
      }
    }
    return { gross, cash, upi, unpaid, unpaidCount };
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

  const exportOrdersCsv = () => {
    const rows = [
      ['Order ID', 'IST Date & Time', 'Customer', 'Phone', 'Payment', 'Status', 'Total (INR)', 'Items'],
      ...filteredOrders.map(({ order, items }) => [
        order.order_id,
        formatIstDateTime(order.timestamp),
        order.customer_name,
        order.contact_number || '',
        order.is_paid ? order.payment_mode : 'UNPAID',
        order.is_delivered ? 'Delivered' : order.is_made ? 'Ready' : 'Preparing',
        String(order.total_amount),
        items.map((i) => `${i.quantity}x ${i.item_name}`).join('; '),
      ]),
    ];
    const csv =
      '\uFEFF' +
      rows
        .map((r) =>
          r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')
        )
        .join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `filtr-orders-${range.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-5">
      <div className="no-print space-y-5">
        {/* 4 Summary KPIs */}
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <KpiCard
            label="Period Gross Billings"
            value={formatInr(totals.gross)}
            subtitle={`${orders.length} total bills`}
            icon={<IndianRupee className="h-4 w-4" />}
          />
          <KpiCard
            label="UPI Collections"
            value={formatInr(totals.upi)}
            subtitle={`${totals.gross > 0 ? Math.round((totals.upi / totals.gross) * 100) : 0}% of total`}
            badgeColor="success"
            icon={<QrCode className="h-4 w-4" />}
          />
          <KpiCard
            label="Cash Collections"
            value={formatInr(totals.cash)}
            subtitle={`${totals.gross > 0 ? Math.round((totals.cash / totals.gross) * 100) : 0}% of total`}
            icon={<ShoppingBag className="h-4 w-4" />}
          />
          <KpiCard
            label="Unpaid Pending"
            value={formatInr(totals.unpaid)}
            subtitle={`${totals.unpaidCount} unsettled bill(s)`}
            badgeText={totals.unpaidCount > 0 ? `${totals.unpaidCount} Open` : 'All Settled'}
            badgeColor={totals.unpaidCount > 0 ? 'danger' : 'success'}
            icon={<CreditCard className="h-4 w-4" />}
          />
        </div>

        {/* Date Range + View Mode + Export */}
        <DateRangeBar
          range={range}
          onRangeChange={setRange}
          monthKey={monthKey}
          onMonthKeyChange={setMonthKey}
          customStart={customStart}
          onCustomStartChange={setCustomStart}
          customEnd={customEnd}
          onCustomEndChange={setCustomEnd}
          rightSlot={
            <>
              <div className="flex items-center rounded-xl border border-border bg-surface p-0.5">
                <Button
                  size="sm"
                  variant={viewMode === 'TABLE' ? 'primary' : 'ghost'}
                  onPress={() => setViewMode('TABLE')}
                  aria-label="Table view"
                >
                  <List className="h-3.5 w-3.5" />
                  <span>Table</span>
                </Button>
                <Button
                  size="sm"
                  variant={viewMode === 'CARDS' ? 'primary' : 'ghost'}
                  onPress={() => setViewMode('CARDS')}
                  aria-label="Cards view"
                >
                  <LayoutGrid className="h-3.5 w-3.5" />
                  <span>Cards</span>
                </Button>
              </div>
              <Button size="sm" variant="outline" onPress={exportOrdersCsv}>
                <Download className="h-3.5 w-3.5 text-accent" />
                <span>CSV</span>
              </Button>
            </>
          }
        />

        {/* Status Tabs + SearchField */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Tabs
            selectedKey={statusFilter}
            onSelectionChange={(k) =>
              setStatusFilter(String(k) as typeof statusFilter)
            }
            variant="secondary"
            className="w-auto"
          >
            <Tabs.ListContainer>
              <Tabs.List aria-label="Order status filter">
                {(['ALL', 'PENDING', 'UNPAID', 'MADE', 'DELIVERED'] as const).map((tab) => (
                  <Tabs.Tab key={tab} id={tab} className="text-xs px-3.5">
                    {tab}
                    <Tabs.Indicator />
                  </Tabs.Tab>
                ))}
              </Tabs.List>
            </Tabs.ListContainer>
          </Tabs>

          <SearchField
            value={search}
            onChange={setSearch}
            variant="secondary"
            className="w-full sm:w-80"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input placeholder="Search order ID, customer, item..." />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
        </div>

        {/* Orders Listing */}
        {loading ? (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <Skeleton key={i} className="h-16 rounded-2xl" />
            ))}
          </div>
        ) : filteredOrders.length === 0 ? (
          <Card className="border border-border/80">
            <Card.Content className="py-12 text-center flex flex-col items-center gap-2">
              <CheckCircle2 className="h-10 w-10 text-muted" />
              <p className="text-sm font-semibold">No orders found for this filter</p>
            </Card.Content>
          </Card>
        ) : viewMode === 'TABLE' ? (
          <Card className="border border-border/80">
            <Card.Content className="p-3">
              <Table variant="secondary">
                <Table.ScrollContainer>
                  <Table.Content aria-label="Orders ledger table">
                    <Table.Header>
                      <Table.Column isRowHeader>Token / Order</Table.Column>
                      <Table.Column>Date & Time</Table.Column>
                      <Table.Column>Customer</Table.Column>
                      <Table.Column>Items</Table.Column>
                      <Table.Column>Kitchen</Table.Column>
                      <Table.Column>Payment</Table.Column>
                      <Table.Column>Delivery</Table.Column>
                      <Table.Column className="text-right">Total</Table.Column>
                      <Table.Column className="text-right">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {filteredOrders.map((owi) => {
                        const { order, items } = owi;
                        const token = order.order_id.split('-').pop() || '0000';
                        return (
                          <Table.Row key={order.order_id} id={order.order_id}>
                            <Table.Cell className="whitespace-nowrap">
                              <div className="flex items-center gap-2">
                                <Chip size="sm" variant="soft" color="accent">
                                  #{token}
                                </Chip>
                                <span className="font-mono text-xs text-muted">
                                  {order.order_id}
                                </span>
                              </div>
                            </Table.Cell>
                            <Table.Cell className="text-xs text-muted whitespace-nowrap tabular-nums">
                              {formatIstDateTime(order.timestamp)}
                            </Table.Cell>
                            <Table.Cell className="text-xs font-medium whitespace-nowrap">
                              {order.customer_name}
                            </Table.Cell>
                            <Table.Cell className="text-xs max-w-sm">
                              <p className="truncate font-medium">
                                {items.map((i) => `${i.quantity}x ${i.item_name}`).join(', ')}
                              </p>
                              {order.notes ? (
                                <p className="text-[11px] text-warning truncate">
                                  Note: {order.notes}
                                </p>
                              ) : null}
                            </Table.Cell>
                            <Table.Cell>
                              <Button
                                size="sm"
                                variant={order.is_made ? 'secondary' : 'outline'}
                                onPress={() =>
                                  patchOrder(
                                    order.order_id,
                                    { is_made: !order.is_made },
                                    order.is_made ? 'Marked Making' : 'Marked Ready'
                                  )
                                }
                              >
                                <Coffee className="h-3 w-3" />
                                <span>{order.is_made ? 'Ready' : 'Making'}</span>
                              </Button>
                            </Table.Cell>
                            <Table.Cell>
                              <Button
                                size="sm"
                                variant={order.is_paid ? 'secondary' : 'danger-soft'}
                                onPress={() => setSelectedBill(owi)}
                              >
                                <CreditCard className="h-3 w-3" />
                                <span>{order.is_paid ? order.payment_mode : 'Unpaid'}</span>
                              </Button>
                            </Table.Cell>
                            <Table.Cell>
                              <Button
                                size="sm"
                                variant={order.is_delivered ? 'secondary' : 'outline'}
                                onPress={() =>
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
                              >
                                <Check className="h-3 w-3" />
                                <span>{order.is_delivered ? 'Delivered' : 'Pending'}</span>
                              </Button>
                            </Table.Cell>
                            <Table.Cell className="text-right font-bold tabular-nums text-accent whitespace-nowrap">
                              {formatInr(order.total_amount)}
                            </Table.Cell>
                            <Table.Cell className="text-right whitespace-nowrap">
                              <div className="inline-flex items-center gap-1">
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="ghost"
                                  onPress={() => setSelectedBill(owi)}
                                  aria-label="Open bill and QR"
                                >
                                  <ReceiptIcon className="h-3.5 w-3.5 text-accent" />
                                </Button>
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="ghost"
                                  onPress={() => handlePrint(owi)}
                                  aria-label="Print receipt"
                                >
                                  <Printer className="h-3.5 w-3.5" />
                                </Button>
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="danger-soft"
                                  onPress={() => handleDelete(order.order_id)}
                                  aria-label="Delete order"
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
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {filteredOrders.map((owi) => {
              const { order, items } = owi;
              const token = order.order_id.split('-').pop() || '0000';
              return (
                <Card key={order.order_id} className="border border-border/80 flex flex-col justify-between">
                  <Card.Content className="p-4 space-y-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="flex items-center gap-2">
                          <Chip size="sm" variant="soft" color="accent">
                            #{token}
                          </Chip>
                          <span className="text-xs font-mono text-muted">{order.order_id}</span>
                        </div>
                        <p className="text-xs text-muted mt-1">
                          {formatIstDateTime(order.timestamp)} • {order.customer_name}
                        </p>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <span className="text-base font-bold text-accent tabular-nums">
                          {formatInr(order.total_amount)}
                        </span>
                        <Button
                          isIconOnly
                          size="sm"
                          variant="danger-soft"
                          onPress={() => handleDelete(order.order_id)}
                          aria-label="Delete order"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </div>

                    <div className="space-y-1 text-xs">
                      {items.map((it, idx) => (
                        <div
                          key={idx}
                          className="flex items-center justify-between rounded-lg px-2.5 py-1.5 bg-surface-secondary"
                        >
                          <span>
                            <strong className="text-accent">x{it.quantity}</strong> {it.item_name}
                          </span>
                          <span className="font-semibold tabular-nums">
                            {formatInr(it.subtotal)}
                          </span>
                        </div>
                      ))}
                    </div>

                    {order.notes ? (
                      <p className="text-xs text-warning bg-warning-soft rounded-lg px-2.5 py-1.5">
                        Note: {order.notes}
                      </p>
                    ) : null}

                    <div className="grid grid-cols-3 gap-1.5 pt-2 border-t border-separator">
                      <Button
                        size="sm"
                        variant={order.is_made ? 'secondary' : 'outline'}
                        onPress={() =>
                          patchOrder(
                            order.order_id,
                            { is_made: !order.is_made },
                            order.is_made ? 'Marked Making' : 'Marked Ready'
                          )
                        }
                      >
                        <Coffee className="h-3 w-3" />
                        <span>{order.is_made ? 'Ready' : 'Making'}</span>
                      </Button>
                      <Button
                        size="sm"
                        variant={order.is_paid ? 'secondary' : 'danger-soft'}
                        onPress={() => setSelectedBill(owi)}
                      >
                        <CreditCard className="h-3 w-3" />
                        <span>{order.is_paid ? order.payment_mode : 'Unpaid'}</span>
                      </Button>
                      <Button
                        size="sm"
                        variant={order.is_delivered ? 'secondary' : 'outline'}
                        onPress={() =>
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
                      >
                        <Check className="h-3 w-3" />
                        <span>{order.is_delivered ? 'Delivered' : 'Pending'}</span>
                      </Button>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                      <Button size="sm" variant="outline" onPress={() => setSelectedBill(owi)}>
                        <ReceiptIcon className="h-3.5 w-3.5 text-accent" />
                        <span>Bill & QR</span>
                      </Button>
                      <Button size="sm" variant="primary" onPress={() => handlePrint(owi)}>
                        <Printer className="h-3.5 w-3.5" />
                        <span>Print</span>
                      </Button>
                    </div>
                  </Card.Content>
                </Card>
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
