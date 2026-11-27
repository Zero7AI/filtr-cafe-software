'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Button, Card, Chip, Skeleton, Table, Tabs } from '@heroui/react';
import {
  ArrowUpRight,
  BarChart3,
  Download,
  Flame,
  IndianRupee,
  Package,
  Refrigerator,
  ShoppingBag,
  TrendingUp,
  Users,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import {
  CategoryBarChart,
  PaymentDonutChart,
  RevenueAreaChart,
} from '@/components/admin/Charts';
import DateRangeBar from '@/components/admin/DateRangeBar';
import KpiCard from '@/components/admin/KpiCard';
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
    revenueDeltaPct: number | null;
    ordersDeltaPct: number | null;
  };
  pnlSnapshot: {
    netSales: number;
    recipeCogs: number;
    grossProfit: number;
    grossMarginPct: number;
    proratedOverhead: number;
    netOperatingProfit: number;
    netMarginPct: number;
    breakEvenDailySales: number;
    currentDailySalesRunRate: number;
  };
  purchasePulse: {
    trackedSkus: number;
    priceHikeCount: number;
    unitAnomalyCount: number;
    pendingWhatsappCount: number;
    topPriceHikes: Array<{
      skuName: string;
      unitWeightLabel: string;
      latestPrice: number;
      firstPrice: number;
      priceChangeVsFirstPct: number | null;
      isPriceHike: boolean;
      hasUnitAnomaly: boolean;
      billsCount: number;
    }>;
  };
  topUsage: Array<{
    inventoryItemId: number;
    itemName: string;
    totalQuantity: number;
    unit: string;
    exactUsageLabel: string;
    category: string;
    estimatedCost: number;
  }>;
  revenueSeries: Array<{ label: string; revenue: number; orders: number }>;
  topItems: Array<{ name: string; qty: number; revenue: number; category: string }>;
  categories: Array<{ category: string; qty: number; revenue: number }>;
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
    reorderCount: number;
  };
  recentOrders: OrderWithItems[];
}

export default function AdminOverviewPage() {
  const { refreshTick } = useAdminContext();
  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [monthKey, setMonthKey] = useState<string>(getIstMonthKey());
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [chartMetric, setChartMetric] = useState<'revenue' | 'orders'>('revenue');
  const [opsTab, setOpsTab] = useState<'HIKES' | 'USAGE'>('HIKES');
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
      ['Net Operating Profit (EBITDA)', data.pnlSnapshot.netOperatingProfit.toFixed(2)],
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

  const formatDelta = (delta: number | null) => {
    if (delta === null) return undefined;
    return `${delta >= 0 ? '+' : ''}${delta.toFixed(1)}% vs prev`;
  };

  const maxTopRevenue = data?.topItems[0]?.revenue || 1;

  return (
    <div className="space-y-6">
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
          <Button size="sm" variant="outline" onPress={exportCsv} isDisabled={!data}>
            <Download className="h-3.5 w-3.5 text-accent" />
            <span>Export CSV</span>
          </Button>
        }
      />

      {loading && !data ? (
        <div className="space-y-6">
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            {[1, 2, 3, 4].map((i) => (
              <Skeleton key={i} className="h-28 rounded-2xl" />
            ))}
          </div>
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
            <Skeleton className="xl:col-span-8 h-80 rounded-2xl" />
            <Skeleton className="xl:col-span-4 h-80 rounded-2xl" />
          </div>
        </div>
      ) : data ? (
        <>
          {/* 4-Card Analytical KPI Grid */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            <KpiCard
              label="Gross Revenue"
              value={formatInr(data.kpis.grossRevenue)}
              subtitle={`Paid: ${formatInr(data.kpis.paidRevenue)} • AOV: ${formatInr(data.kpis.avgOrderValue)}`}
              badgeText={formatDelta(data.kpis.revenueDeltaPct)}
              badgeColor={
                (data.kpis.revenueDeltaPct ?? 0) >= 0 ? 'success' : 'danger'
              }
              icon={<IndianRupee className="h-4 w-4" />}
            />

            <KpiCard
              label="Est. Net Profit (EBITDA)"
              value={formatInr(data.pnlSnapshot.netOperatingProfit)}
              subtitle={`Gross Margin: ${data.pnlSnapshot.grossMarginPct.toFixed(1)}% • COGS: ${formatInr(data.pnlSnapshot.recipeCogs)}`}
              badgeText={`${data.pnlSnapshot.netMarginPct >= 0 ? '+' : ''}${data.pnlSnapshot.netMarginPct.toFixed(1)}%`}
              badgeColor={data.pnlSnapshot.netOperatingProfit >= 0 ? 'success' : 'danger'}
              icon={<TrendingUp className="h-4 w-4" />}
            />

            <KpiCard
              label="Orders & Kitchen Queue"
              value={`${data.kpis.totalOrders} Orders`}
              subtitle={`${data.kpis.activeKdsCount} active KDS • ${data.kpis.unpaidCount} unpaid (${formatInr(data.kpis.unpaidRevenue)})`}
              badgeText={formatDelta(data.kpis.ordersDeltaPct) || `${data.kpis.activeKdsCount} Active`}
              badgeColor={data.kpis.activeKdsCount > 0 ? 'warning' : 'success'}
              icon={<ShoppingBag className="h-4 w-4" />}
            />

            <KpiCard
              label="Purchase & Stock Watch (v2.5.4)"
              value={`${data.purchasePulse.trackedSkus} SKUs`}
              subtitle={`${data.purchasePulse.priceHikeCount} hikes • ${data.purchasePulse.unitAnomalyCount} CHECK UNIT • ${data.kitchen.reorderCount} reorders`}
              badgeText={
                data.purchasePulse.pendingWhatsappCount > 0
                  ? `${data.purchasePulse.pendingWhatsappCount} WA Retry`
                  : data.purchasePulse.priceHikeCount > 0
                    ? `${data.purchasePulse.priceHikeCount} Hikes`
                    : 'Stable'
              }
              badgeColor={
                data.purchasePulse.pendingWhatsappCount > 0 ||
                data.purchasePulse.priceHikeCount > 0
                  ? 'danger'
                  : 'success'
              }
              icon={<Package className="h-4 w-4" />}
            />
          </div>

          {/* Main 12-Col Analytics Row */}
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
            <Card className="xl:col-span-8 border border-border/80">
              <Card.Header className="flex flex-row items-center justify-between gap-3 px-5 pt-5 pb-2">
                <div>
                  <Card.Title className="text-sm font-bold">
                    Revenue & Order Throughput
                  </Card.Title>
                  <Card.Description className="text-xs">
                    IST breakdown across selected period • Daily run rate:{' '}
                    <span className="font-semibold text-foreground tabular-nums">
                      {formatInr(data.pnlSnapshot.currentDailySalesRunRate)}/day
                    </span>
                  </Card.Description>
                </div>
                <div className="flex items-center gap-2">
                  <Tabs
                    selectedKey={chartMetric}
                    onSelectionChange={(k) =>
                      setChartMetric(String(k) as 'revenue' | 'orders')
                    }
                    className="w-auto"
                  >
                    <Tabs.ListContainer>
                      <Tabs.List aria-label="Chart metric">
                        <Tabs.Tab id="revenue" className="text-xs px-3 h-7">
                          Revenue
                          <Tabs.Indicator />
                        </Tabs.Tab>
                        <Tabs.Tab id="orders" className="text-xs px-3 h-7">
                          Orders
                          <Tabs.Indicator />
                        </Tabs.Tab>
                      </Tabs.List>
                    </Tabs.ListContainer>
                  </Tabs>
                  <Link
                    href="/pnl"
                    className="inline-flex items-center gap-1 text-xs font-semibold text-accent hover:underline"
                  >
                    <span>Full P&L</span>
                    <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </Card.Header>
              <Card.Content className="px-4 pb-4">
                <RevenueAreaChart data={data.revenueSeries} metric={chartMetric} height={270} />
              </Card.Content>
            </Card>

            <Card className="xl:col-span-4 border border-border/80">
              <Card.Header className="px-5 pt-5 pb-2">
                <Card.Title className="text-sm font-bold">
                  Collection Split & Live Operations
                </Card.Title>
                <Card.Description className="text-xs">
                  Payment tender mix, shift attendance & kitchen status
                </Card.Description>
              </Card.Header>
              <Card.Content className="px-5 pb-5 space-y-4">
                <PaymentDonutChart
                  upiRevenue={data.kpis.upiRevenue}
                  cashRevenue={data.kpis.cashRevenue}
                  unpaidRevenue={data.kpis.unpaidRevenue}
                  height={165}
                />

                <div className="grid grid-cols-3 gap-2 text-center text-xs rounded-xl bg-surface-secondary p-2.5">
                  <div>
                    <span className="text-[10px] text-muted block">UPI</span>
                    <span className="font-bold text-success tabular-nums">
                      {formatInr(data.kpis.upiRevenue)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted block">Cash</span>
                    <span className="font-bold text-accent tabular-nums">
                      {formatInr(data.kpis.cashRevenue)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-muted block">Unpaid</span>
                    <span className="font-bold text-danger tabular-nums">
                      {formatInr(data.kpis.unpaidRevenue)}
                    </span>
                  </div>
                </div>

                <div className="rounded-xl border border-separator bg-surface-secondary/50 p-3 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold flex items-center gap-1.5">
                      <Users className="h-3.5 w-3.5 text-success" />
                      Staff ({data.todayKey})
                    </span>
                    <Link href="/team" className="text-[11px] font-semibold text-accent hover:underline">
                      Team →
                    </Link>
                  </div>
                  <p className="text-xs text-muted">
                    <strong className="text-foreground">{data.team.onDutyCount}</strong> on duty •{' '}
                    <strong className="text-foreground">{data.team.checkedOutCount}</strong> completed •{' '}
                    {data.team.activeRosterCount} active
                  </p>
                  {data.team.onDutyStaff.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      {data.team.onDutyStaff.map((s) => (
                        <Chip key={s.staffName} size="sm" variant="soft" color="success">
                          {s.staffName} ({formatIstTime(s.checkInTimestamp)})
                        </Chip>
                      ))}
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-2 gap-2">
                  <Link href="/kds">
                    <Button fullWidth size="sm" variant="primary">
                      <Flame className="h-3.5 w-3.5" />
                      <span>KDS Queue ({data.kpis.activeKdsCount})</span>
                    </Button>
                  </Link>
                  <Link href="/kitchen">
                    <Button fullWidth size="sm" variant="outline">
                      <Refrigerator className="h-3.5 w-3.5 text-accent" />
                      <span>Kitchen ({data.kitchen.openingStatus})</span>
                    </Button>
                  </Link>
                </div>
              </Card.Content>
            </Card>
          </div>

          {/* 3-Column Deep Insights Row */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
            {/* Top Selling Menu Items */}
            <Card className="border border-border/80">
              <Card.Header className="flex flex-row items-center justify-between px-5 pt-5 pb-2">
                <div>
                  <Card.Title className="text-sm font-bold">Top Selling Items</Card.Title>
                  <Card.Description className="text-xs">By item revenue in period</Card.Description>
                </div>
                <Link href="/menu" className="text-xs font-semibold text-accent hover:underline">
                  Menu →
                </Link>
              </Card.Header>
              <Card.Content className="px-5 pb-5">
                {data.topItems.length === 0 ? (
                  <p className="text-xs text-muted py-6 text-center">No items sold in this period.</p>
                ) : (
                  <div className="space-y-3">
                    {data.topItems.slice(0, 7).map((it, idx) => {
                      const pct = Math.round((it.revenue / maxTopRevenue) * 100);
                      return (
                        <div key={it.name} className="space-y-1">
                          <div className="flex items-center justify-between text-xs gap-2">
                            <span className="truncate font-medium">
                              <span className="text-accent font-bold mr-1.5 tabular-nums">
                                {idx + 1}.
                              </span>
                              {it.name}
                            </span>
                            <span className="font-bold tabular-nums shrink-0">
                              {formatInr(it.revenue)}
                              <span className="text-muted font-normal ml-1">({it.qty})</span>
                            </span>
                          </div>
                          <div className="h-1.5 rounded-full bg-surface-tertiary overflow-hidden">
                            <div
                              className="h-full rounded-full bg-accent"
                              style={{ width: `${Math.min(100, pct)}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </Card.Content>
            </Card>

            {/* Sales by Category Chart */}
            <Card className="border border-border/80">
              <Card.Header className="px-5 pt-5 pb-2">
                <Card.Title className="text-sm font-bold">Revenue by Category</Card.Title>
                <Card.Description className="text-xs">
                  Menu category contribution mix
                </Card.Description>
              </Card.Header>
              <Card.Content className="px-3 pb-4">
                <CategoryBarChart data={data.categories} height={250} />
              </Card.Content>
            </Card>

            {/* v2.5.4 Purchase Tracking & Exact Usage Switchable Card */}
            <Card className="border border-border/80">
              <Card.Header className="flex flex-row items-center justify-between gap-2 px-5 pt-5 pb-2">
                <Tabs
                  selectedKey={opsTab}
                  onSelectionChange={(k) => setOpsTab(String(k) as 'HIKES' | 'USAGE')}
                  variant="secondary"
                  className="w-auto"
                >
                  <Tabs.ListContainer>
                    <Tabs.List aria-label="Operations insights">
                      <Tabs.Tab id="HIKES" className="text-xs px-2.5">
                        SKU Price Hikes
                        <Tabs.Indicator />
                      </Tabs.Tab>
                      <Tabs.Tab id="USAGE" className="text-xs px-2.5">
                        Exact Usage
                        <Tabs.Indicator />
                      </Tabs.Tab>
                    </Tabs.List>
                  </Tabs.ListContainer>
                </Tabs>
                <Link
                  href={opsTab === 'HIKES' ? '/inventory' : '/usage'}
                  className="text-xs font-semibold text-accent hover:underline shrink-0"
                >
                  Open →
                </Link>
              </Card.Header>
              <Card.Content className="px-5 pb-5">
                {opsTab === 'HIKES' ? (
                  data.purchasePulse.topPriceHikes.length === 0 ? (
                    <div className="py-8 text-center text-xs text-success font-medium">
                      No SKU price hikes or CHECK UNIT anomalies detected
                    </div>
                  ) : (
                    <div className="space-y-2">
                      {data.purchasePulse.topPriceHikes.slice(0, 6).map((row) => (
                        <div
                          key={row.skuName}
                          className="flex items-center justify-between gap-2 rounded-xl border border-separator bg-surface-secondary/40 px-3 py-2 text-xs"
                        >
                          <div className="min-w-0">
                            <p className="font-semibold truncate">{row.skuName}</p>
                            <p className="text-[11px] text-muted">
                              Latest {formatInr(row.latestPrice, 2)} ({row.unitWeightLabel})
                            </p>
                          </div>
                          <div className="flex items-center gap-1.5 shrink-0">
                            {row.hasUnitAnomaly ? (
                              <Chip size="sm" variant="soft" color="warning">
                                CHECK UNIT
                              </Chip>
                            ) : null}
                            {row.priceChangeVsFirstPct !== null && row.isPriceHike ? (
                              <Chip size="sm" variant="soft" color="danger">
                                +{row.priceChangeVsFirstPct.toFixed(1)}%
                              </Chip>
                            ) : null}
                          </div>
                        </div>
                      ))}
                    </div>
                  )
                ) : data.topUsage.length === 0 ? (
                  <div className="py-8 text-center text-xs text-muted flex flex-col items-center gap-2">
                    <BarChart3 className="h-6 w-6 opacity-50" />
                    <span>No Recipe Master consumption matched orders in this period</span>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {data.topUsage.slice(0, 6).map((u) => (
                      <div
                        key={u.inventoryItemId}
                        className="flex items-center justify-between gap-2 rounded-xl border border-separator bg-surface-secondary/40 px-3 py-2 text-xs"
                      >
                        <div className="min-w-0">
                          <p className="font-semibold truncate">{u.itemName}</p>
                          <p className="text-[11px] text-muted">{u.category}</p>
                        </div>
                        <div className="text-right shrink-0 tabular-nums">
                          <p className="font-bold text-accent">{u.exactUsageLabel}</p>
                          {u.estimatedCost > 0 ? (
                            <p className="text-[10px] text-muted">
                              ~{formatInr(u.estimatedCost, 0)}
                            </p>
                          ) : null}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </Card.Content>
            </Card>
          </div>

          {/* Recent Orders HeroUI Table */}
          <Card className="border border-border/80">
            <Card.Header className="flex flex-row items-center justify-between px-5 pt-5 pb-2">
              <div>
                <Card.Title className="text-sm font-bold">Recent Orders</Card.Title>
                <Card.Description className="text-xs">
                  Latest bills synced from Counter POS & Android tablets
                </Card.Description>
              </div>
              <Link href="/orders" className="text-xs font-semibold text-accent hover:underline">
                Full Orders Ledger →
              </Link>
            </Card.Header>
            <Card.Content className="px-4 pb-4">
              <Table variant="secondary">
                <Table.ScrollContainer>
                  <Table.Content aria-label="Recent orders table">
                    <Table.Header>
                      <Table.Column isRowHeader>Order ID</Table.Column>
                      <Table.Column>Time (IST)</Table.Column>
                      <Table.Column>Customer</Table.Column>
                      <Table.Column>Items</Table.Column>
                      <Table.Column>Payment</Table.Column>
                      <Table.Column>Status</Table.Column>
                      <Table.Column className="text-right">Amount</Table.Column>
                    </Table.Header>
                    <Table.Body
                      renderEmptyState={() => (
                        <div className="py-8 text-center text-xs text-muted">
                          No orders recorded in this range
                        </div>
                      )}
                    >
                      {data.recentOrders.map(({ order, items }) => (
                        <Table.Row key={order.order_id} id={order.order_id}>
                          <Table.Cell className="font-mono text-xs font-semibold whitespace-nowrap">
                            {order.order_id}
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted whitespace-nowrap tabular-nums">
                            {formatIstTime(order.timestamp)}
                          </Table.Cell>
                          <Table.Cell className="text-xs font-medium whitespace-nowrap">
                            {order.customer_name}
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted max-w-xs truncate">
                            {items.map((i) => `${i.quantity}x ${i.item_name}`).join(', ')}
                          </Table.Cell>
                          <Table.Cell>
                            <Chip
                              size="sm"
                              variant="soft"
                              color={order.is_paid ? 'success' : 'danger'}
                            >
                              {order.is_paid ? order.payment_mode : 'UNPAID'}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell>
                            <Chip
                              size="sm"
                              variant="soft"
                              color={
                                order.is_delivered
                                  ? 'success'
                                  : order.is_made
                                    ? 'accent'
                                    : 'default'
                              }
                            >
                              {order.is_delivered
                                ? 'Delivered'
                                : order.is_made
                                  ? 'Ready'
                                  : 'Preparing'}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums text-accent whitespace-nowrap">
                            {formatInr(order.total_amount)}
                          </Table.Cell>
                        </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Content>
                </Table.ScrollContainer>
              </Table>
            </Card.Content>
          </Card>
        </>
      ) : null}
    </div>
  );
}
