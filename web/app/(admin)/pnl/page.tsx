'use client';

import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Chip,
  Input,
  Label,
  Skeleton,
  Table,
  TextField,
} from '@heroui/react';
import {
  Download,
  IndianRupee,
  Plus,
  Settings,
  Trash2,
  TrendingUp,
} from 'lucide-react';
import AdminModal from '@/components/admin/AdminModal';
import { useAdminContext } from '@/components/admin/AdminShell';
import DateRangeBar from '@/components/admin/DateRangeBar';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import { DateRangePreset, formatInr, getIstMonthKey } from '@/lib/domain/ist';
import { PnlReport, getFixedCostForMonthKey } from '@/lib/domain/pnl';
import { FixedCostRecord, FixedCostStaffMember } from '@/lib/domain/types';

export default function AdminPnlPage() {
  const { refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [monthKey, setMonthKey] = useState(getIstMonthKey());
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');
  const [report, setReport] = useState<PnlReport | null>(null);
  const [fixedCostsMap, setFixedCostsMap] = useState<Record<string, FixedCostRecord>>({});
  const [loading, setLoading] = useState(true);

  const [costModalOpen, setCostModalOpen] = useState(false);
  const [editMonthKey, setEditMonthKey] = useState(getIstMonthKey());
  const [rent, setRent] = useState('9000');
  const [electricity, setElectricity] = useState('6000');
  const [maidSalary, setMaidSalary] = useState('2000');
  const [miscellaneous, setMiscellaneous] = useState('5000');
  const [notes, setNotes] = useState('');
  const [staffRows, setStaffRows] = useState<FixedCostStaffMember[]>([]);
  const [savingCost, setSavingCost] = useState(false);

  const loadPnl = useCallback(async () => {
    try {
      const qs = new URLSearchParams({ range, monthKey });
      if (range === 'CUSTOM' && customStart) {
        qs.set('start', customStart);
        qs.set('end', customEnd || customStart);
      }
      const res = await fetch(`/api/admin/pnl?${qs.toString()}`, { cache: 'no-store' });
      if (res.ok) {
        const data = await res.json();
        setReport(data.report);
        setFixedCostsMap(data.fixedCostsMap || {});
      }
    } finally {
      setLoading(false);
    }
  }, [range, monthKey, customStart, customEnd]);

  useEffect(() => {
    loadPnl();
  }, [loadPnl, refreshTick]);

  const populateModalForMonth = (mk: string, map: Record<string, FixedCostRecord>) => {
    const { record } = getFixedCostForMonthKey(mk, map);
    setEditMonthKey(mk);
    setRent(String(record.rent));
    setElectricity(String(record.electricity));
    setMaidSalary(String(record.maidSalary));
    setMiscellaneous(String(record.miscellaneous));
    setNotes(record.notes || '');
    setStaffRows(
      record.staffMembers.length > 0
        ? record.staffMembers
        : [
            { id: `staff_${mk}_1`, name: 'Barista 1', salary: 15000 },
            { id: `staff_${mk}_2`, name: 'Barista 2', salary: 15000 },
          ]
    );
  };

  const handleOpenFixedCosts = () => {
    populateModalForMonth(monthKey, fixedCostsMap);
    setCostModalOpen(true);
  };

  const handleSaveFixedCosts = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingCost(true);
    try {
      const res = await fetch('/api/admin/pnl', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          monthKey: editMonthKey,
          rent: Number(rent || 0),
          electricity: Number(electricity || 0),
          maidSalary: Number(maidSalary || 0),
          miscellaneous: Number(miscellaneous || 0),
          notes,
          staffMembers: staffRows,
        }),
      });
      if (res.ok) {
        pushToast(`Saved fixed overheads for ${editMonthKey}`, 'success');
        setCostModalOpen(false);
        loadPnl();
      } else {
        pushToast('Failed to save fixed costs', 'error');
      }
    } finally {
      setSavingCost(false);
    }
  };

  const exportPnlCsv = () => {
    if (!report) return;
    const rows = [
      ['FILTR COFFEE P&L STATEMENT', report.proratedOverhead.monthKeyDescription],
      ['Orders Processed', String(report.ordersCount)],
      ['Gross Sales', report.grossSales.toFixed(2)],
      ['5% Inclusive GST', report.gstAmount.toFixed(2)],
      ['Net Operating Revenue', report.netSales.toFixed(2)],
      ['Recipe COGS', report.recipeCogs.toFixed(2)],
      ['Gross Profit', report.grossProfit.toFixed(2)],
      ['Gross Margin %', `${report.grossMarginPct.toFixed(1)}%`],
      ['Prorated Fixed Overheads', report.proratedOverhead.total.toFixed(2)],
      ['Net Operating Profit (EBITDA)', report.netOperatingProfit.toFixed(2)],
      ['Net Margin %', `${report.netMarginPct.toFixed(1)}%`],
    ];
    const csv = '\uFEFF' + rows.map((r) => r.join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `filtr-pnl-${report.range.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

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
          <>
            <Button size="sm" variant="primary" onPress={handleOpenFixedCosts}>
              <Settings className="h-3.5 w-3.5" />
              <span>Set Fixed Costs</span>
            </Button>
            <Button size="sm" variant="outline" onPress={exportPnlCsv} isDisabled={!report}>
              <Download className="h-3.5 w-3.5 text-accent" />
              <span>CSV</span>
            </Button>
          </>
        }
      />

      {loading && !report ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-28 rounded-2xl" />
          ))}
        </div>
      ) : report ? (
        <>
          {/* Hero KPIs */}
          <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
            <KpiCard
              label="Net Operating Profit (EBITDA)"
              value={formatInr(report.netOperatingProfit)}
              subtitle={`${report.ordersCount} orders processed`}
              badgeText={`${report.netMarginPct >= 0 ? '+' : ''}${report.netMarginPct.toFixed(1)}% Margin`}
              badgeColor={report.netOperatingProfit >= 0 ? 'success' : 'danger'}
              icon={<TrendingUp className="h-4 w-4" />}
            />
            <KpiCard
              label="Net Revenue (Excl. 5% GST)"
              value={formatInr(report.netSales)}
              subtitle={`Gross: ${formatInr(report.grossSales)} • GST: ${formatInr(report.gstAmount)}`}
              icon={<IndianRupee className="h-4 w-4" />}
            />
            <KpiCard
              label="Recipe Ingredients (COGS)"
              value={formatInr(report.recipeCogs)}
              subtitle={`Gross Profit: ${formatInr(report.grossProfit)} (${report.grossMarginPct.toFixed(1)}%)`}
              badgeText={
                report.unconfiguredRecipeOrdersCount > 0
                  ? `${report.unconfiguredRecipeOrdersCount} 35% Est`
                  : '100% Recipe'
              }
              badgeColor={report.unconfiguredRecipeOrdersCount > 0 ? 'warning' : 'success'}
            />
            <KpiCard
              label="Prorated Fixed Overheads"
              value={formatInr(report.proratedOverhead.total)}
              subtitle={`${formatInr(report.dailyFixedCost)}/day × ${report.proratedOverhead.daysCount} day(s)`}
            />
          </div>

          {/* 12-Col Waterfall + Break-even / Overhead Breakdown */}
          <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
            <Card className="xl:col-span-7 border border-border/80">
              <Card.Header className="flex flex-row items-center justify-between px-5 pt-5 pb-2">
                <div>
                  <Card.Title className="text-sm font-bold">
                    P&L Waterfall Statement
                  </Card.Title>
                  <Card.Description className="text-xs">
                    {report.proratedOverhead.monthKeyDescription} • 5% inclusive GST deduction
                  </Card.Description>
                </div>
                <Chip
                  size="sm"
                  variant="soft"
                  color={report.netOperatingProfit >= 0 ? 'success' : 'danger'}
                >
                  {report.netOperatingProfit >= 0 ? 'Profitable' : 'Operating Loss'}
                </Chip>
              </Card.Header>
              <Card.Content className="px-4 pb-4">
                <Table variant="secondary">
                  <Table.ScrollContainer>
                    <Table.Content aria-label="P&L waterfall statement">
                      <Table.Header>
                        <Table.Column isRowHeader>Line Item</Table.Column>
                        <Table.Column>Rule / Formula</Table.Column>
                        <Table.Column className="text-right">Amount</Table.Column>
                      </Table.Header>
                      <Table.Body>
                        <Table.Row id="gross">
                          <Table.Cell className="font-semibold">[+] Gross Customer Billings</Table.Cell>
                          <Table.Cell className="text-xs text-muted">{report.ordersCount} orders</Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums">
                            {formatInr(report.grossSales)}
                          </Table.Cell>
                        </Table.Row>
                        <Table.Row id="gst">
                          <Table.Cell className="text-danger">[-] 5% GST Tax Remittance</Table.Cell>
                          <Table.Cell className="text-xs text-muted">Gross × 5 / 105</Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums text-danger">
                            -{formatInr(report.gstAmount)}
                          </Table.Cell>
                        </Table.Row>
                        <Table.Row id="net">
                          <Table.Cell className="font-bold">[=] Net Operating Revenue</Table.Cell>
                          <Table.Cell className="text-xs text-muted">Excl. GST</Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums">
                            {formatInr(report.netSales)}
                          </Table.Cell>
                        </Table.Row>
                        <Table.Row id="cogs">
                          <Table.Cell className="text-warning">[-] Recipe Ingredients (COGS)</Table.Cell>
                          <Table.Cell className="text-xs text-muted">Recipe Master / 35% fallback</Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums text-warning">
                            -{formatInr(report.recipeCogs)}
                          </Table.Cell>
                        </Table.Row>
                        <Table.Row id="gp">
                          <Table.Cell className="font-bold text-success">
                            [=] Gross Profit ({report.grossMarginPct.toFixed(1)}%)
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted">Net Rev − COGS</Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums text-success">
                            {formatInr(report.grossProfit)}
                          </Table.Cell>
                        </Table.Row>
                        <Table.Row id="overheads">
                          <Table.Cell className="text-muted">[-] Prorated Fixed Overheads</Table.Cell>
                          <Table.Cell className="text-xs text-muted">
                            {report.proratedOverhead.daysCount} day(s) prorated
                          </Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums text-muted">
                            -{formatInr(report.proratedOverhead.total)}
                          </Table.Cell>
                        </Table.Row>
                        <Table.Row id="ebitda">
                          <Table.Cell
                            className={`text-sm font-bold ${
                              report.netOperatingProfit >= 0 ? 'text-success' : 'text-danger'
                            }`}
                          >
                            NET OPERATING PROFIT (EBITDA)
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted">
                            {report.netMarginPct.toFixed(1)}% Net Margin
                          </Table.Cell>
                          <Table.Cell
                            className={`text-right text-base font-bold tabular-nums ${
                              report.netOperatingProfit >= 0 ? 'text-success' : 'text-danger'
                            }`}
                          >
                            {formatInr(report.netOperatingProfit)}
                          </Table.Cell>
                        </Table.Row>
                      </Table.Body>
                    </Table.Content>
                  </Table.ScrollContainer>
                </Table>
              </Card.Content>
            </Card>

            <div className="xl:col-span-5 space-y-5">
              <Card className="border border-border/80">
                <Card.Header className="px-5 pt-5 pb-2">
                  <Card.Title className="text-sm font-bold">
                    Break-Even Daily Run Rate
                  </Card.Title>
                  <Card.Description className="text-xs">
                    Daily revenue required to cover prorated overheads & COGS
                  </Card.Description>
                </Card.Header>
                <Card.Content className="px-5 pb-5 space-y-3">
                  <div className="flex justify-between text-xs">
                    <div>
                      <span className="text-muted block">Current Daily Sales</span>
                      <span className="text-lg font-bold tabular-nums">
                        {formatInr(report.currentDailySalesRunRate)}/day
                      </span>
                    </div>
                    <div className="text-right">
                      <span className="text-muted block">Break-Even Target</span>
                      <span className="text-lg font-bold text-accent tabular-nums">
                        {formatInr(report.breakEvenDailySales)}/day
                      </span>
                    </div>
                  </div>
                  <div className="h-3 rounded-full bg-surface-tertiary overflow-hidden">
                    <div
                      className={`h-full rounded-full ${
                        report.currentDailySalesRunRate >= report.breakEvenDailySales
                          ? 'bg-success'
                          : 'bg-accent'
                      }`}
                      style={{
                        width: `${Math.min(100, Math.round(report.breakEvenProgress * 100))}%`,
                      }}
                    />
                  </div>
                  <p className="text-xs text-muted tabular-nums">
                    {Math.round(report.breakEvenProgress * 100)}% of daily break-even target achieved
                  </p>
                </Card.Content>
              </Card>

              <Card className="border border-border/80">
                <Card.Header className="flex flex-row items-center justify-between px-5 pt-5 pb-2">
                  <div>
                    <Card.Title className="text-sm font-bold">
                      Prorated Overhead Breakdown
                    </Card.Title>
                    <Card.Description className="text-xs">
                      {report.proratedOverhead.daysCount} day(s) •{' '}
                      {report.proratedOverhead.isConfiguredInDb ? 'Saved in Cloud' : 'Using defaults'}
                    </Card.Description>
                  </div>
                  <Button size="sm" variant="outline" onPress={handleOpenFixedCosts}>
                    Edit
                  </Button>
                </Card.Header>
                <Card.Content className="px-5 pb-5 space-y-2.5 text-xs">
                  {[
                    ['Shop Rent', report.proratedOverhead.rent],
                    ['Electricity', report.proratedOverhead.electricity],
                    [
                      `Staff Salaries (${report.proratedOverhead.staffCount})`,
                      report.proratedOverhead.staffSalaries,
                    ],
                    ['Maid / Cleaning', report.proratedOverhead.maidSalary],
                    ['Misc & Maintenance', report.proratedOverhead.miscellaneous],
                  ].map(([lbl, val]) => (
                    <div key={String(lbl)} className="flex justify-between py-1 border-b border-separator/50 last:border-b-0">
                      <span className="text-muted">{lbl}</span>
                      <span className="font-semibold tabular-nums">{formatInr(Number(val))}</span>
                    </div>
                  ))}
                </Card.Content>
              </Card>
            </div>
          </div>

          {/* Top COGS Ingredients Table */}
          {report.topIngredients.length > 0 && (
            <Card className="border border-border/80">
              <Card.Header className="px-5 pt-5 pb-2">
                <Card.Title className="text-sm font-bold">
                  Top Ingredients Consumed (COGS)
                </Card.Title>
                <Card.Description className="text-xs">
                  Highest cost raw ingredients consumed across active orders
                </Card.Description>
              </Card.Header>
              <Card.Content className="px-4 pb-4">
                <Table variant="secondary">
                  <Table.ScrollContainer>
                    <Table.Content aria-label="Top COGS ingredients">
                      <Table.Header>
                        <Table.Column isRowHeader>Ingredient</Table.Column>
                        <Table.Column>Consumed Volume / Qty</Table.Column>
                        <Table.Column className="text-right">Cost</Table.Column>
                        <Table.Column className="text-right">% of Recipe COGS</Table.Column>
                      </Table.Header>
                      <Table.Body>
                        {report.topIngredients.map((ing) => {
                          const sharePct =
                            report.recipeCogs > 0
                              ? (ing.cost / report.recipeCogs) * 100
                              : 0;
                          return (
                            <Table.Row key={String(ing.itemId)} id={String(ing.itemId)}>
                              <Table.Cell className="font-semibold">{ing.name}</Table.Cell>
                              <Table.Cell className="text-xs text-muted">{ing.usageDetail}</Table.Cell>
                              <Table.Cell className="text-right font-bold tabular-nums text-accent">
                                {formatInr(ing.cost, 2)}
                              </Table.Cell>
                              <Table.Cell className="text-right text-xs tabular-nums">
                                {sharePct.toFixed(1)}%
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
        </>
      ) : null}

      <AdminModal
        isOpen={costModalOpen}
        onClose={() => setCostModalOpen(false)}
        title="Monthly Fixed Overheads"
        subtitle="Prorated day-by-day across P&L reports"
        size="lg"
      >
        <form onSubmit={handleSaveFixedCosts} className="space-y-4">
          <div className="space-y-1">
            <Label className="text-xs font-medium">Month (YYYY-MM)</Label>
            <Input
              type="month"
              variant="secondary"
              fullWidth
              value={editMonthKey}
              onChange={(e) => populateModalForMonth(e.target.value, fixedCostsMap)}
            />
          </div>

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-medium">Staff Members & Monthly Salaries</Label>
              <Button
                size="sm"
                variant="outline"
                onPress={() =>
                  setStaffRows((prev) => [
                    ...prev,
                    {
                      id: `staff_${Date.now()}`,
                      name: `Staff ${prev.length + 1}`,
                      salary: 15000,
                    },
                  ])
                }
              >
                <Plus className="h-3.5 w-3.5" /> Add Staff
              </Button>
            </div>
            {staffRows.map((s, idx) => (
              <div key={s.id} className="flex items-center gap-2">
                <Input
                  type="text"
                  variant="secondary"
                  fullWidth
                  value={s.name}
                  onChange={(e) =>
                    setStaffRows((prev) =>
                      prev.map((row, i) => (i === idx ? { ...row, name: e.target.value } : row))
                    )
                  }
                  placeholder="Name"
                  className="text-xs"
                />
                <Input
                  type="number"
                  variant="secondary"
                  value={String(s.salary)}
                  onChange={(e) =>
                    setStaffRows((prev) =>
                      prev.map((row, i) =>
                        i === idx ? { ...row, salary: Number(e.target.value || 0) } : row
                      )
                    )
                  }
                  className="w-32 text-xs tabular-nums"
                />
                {staffRows.length > 1 && (
                  <Button
                    isIconOnly
                    size="sm"
                    variant="danger-soft"
                    onPress={() => setStaffRows((prev) => prev.filter((_, i) => i !== idx))}
                    aria-label="Remove staff row"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                )}
              </div>
            ))}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-medium">Shop Rent (₹/mo)</Label>
              <Input
                type="number"
                variant="secondary"
                fullWidth
                value={rent}
                onChange={(e) => setRent(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Electricity (₹/mo)</Label>
              <Input
                type="number"
                variant="secondary"
                fullWidth
                value={electricity}
                onChange={(e) => setElectricity(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Maid / Cleaning (₹/mo)</Label>
              <Input
                type="number"
                variant="secondary"
                fullWidth
                value={maidSalary}
                onChange={(e) => setMaidSalary(e.target.value)}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Misc (₹/mo)</Label>
              <Input
                type="number"
                variant="secondary"
                fullWidth
                value={miscellaneous}
                onChange={(e) => setMiscellaneous(e.target.value)}
              />
            </div>
          </div>

          <TextField value={notes} onChange={setNotes} fullWidth>
            <Label className="text-xs font-medium">Notes</Label>
            <Input variant="secondary" placeholder="Optional overhead notes" />
          </TextField>

          <Button type="submit" variant="primary" fullWidth isDisabled={savingCost}>
            {savingCost ? 'Saving...' : `Save Fixed Costs for ${editMonthKey}`}
          </Button>
        </form>
      </AdminModal>
    </div>
  );
}
