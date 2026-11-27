'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Chip,
  SearchField,
  Skeleton,
  Table,
} from '@heroui/react';
import { BarChart3, Download, IndianRupee, Package, ShoppingBag } from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import { UsageBarChart } from '@/components/admin/Charts';
import DateRangeBar from '@/components/admin/DateRangeBar';
import KpiCard from '@/components/admin/KpiCard';
import { CleanIngredientUsageRow } from '@/lib/domain/inventory';
import { DateRangePreset, formatInr, getIstDateKey, getIstMonthKey } from '@/lib/domain/ist';

interface EnrichedUsageRow extends CleanIngredientUsageRow {
  category: string;
  estimatedCost: number;
}

export default function AdminUsagePage() {
  const { refreshTick } = useAdminContext();

  const [range, setRange] = useState<DateRangePreset>('TODAY');
  const [monthKey, setMonthKey] = useState(getIstMonthKey());
  const [customStart, setCustomStart] = useState(getIstDateKey());
  const [customEnd, setCustomEnd] = useState(getIstDateKey());
  const [search, setSearch] = useState('');
  const [unitGroup, setUnitGroup] = useState<'ALL' | 'WEIGHT' | 'VOLUME' | 'COUNT'>('ALL');
  const [ordersCount, setOrdersCount] = useState(0);
  const [totalEstimatedSpend, setTotalEstimatedSpend] = useState(0);
  const [items, setItems] = useState<EnrichedUsageRow[]>([]);
  const [loading, setLoading] = useState(true);

  const loadUsage = useCallback(async () => {
    try {
      const qs = new URLSearchParams({ range });
      if (range === 'MONTH') qs.set('monthKey', monthKey);
      if (range === 'CUSTOM' && customStart) {
        qs.set('start', customStart);
        qs.set('end', customEnd || customStart);
      }
      const res = await fetch(`/api/admin/usage?${qs.toString()}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        const d = await res.json();
        setOrdersCount(Number(d.ordersCount || 0));
        setTotalEstimatedSpend(Number(d.totalEstimatedSpend || 0));
        setItems(d.items || []);
      }
    } finally {
      setLoading(false);
    }
  }, [range, monthKey, customStart, customEnd]);

  useEffect(() => {
    loadUsage();
  }, [loadUsage, refreshTick]);

  const filteredItems = useMemo(() => {
    const q = search.trim().toLowerCase();
    return items.filter((it) => {
      const u = it.unit.toLowerCase();
      if (unitGroup === 'WEIGHT' && u !== 'kg' && u !== 'g') return false;
      if (unitGroup === 'VOLUME' && u !== 'l' && u !== 'ml') return false;
      if (unitGroup === 'COUNT' && ['kg', 'g', 'l', 'ml'].includes(u)) return false;
      if (!q) return true;
      return (
        it.itemName.toLowerCase().includes(q) ||
        it.category.toLowerCase().includes(q)
      );
    });
  }, [items, search, unitGroup]);

  const exportCsv = () => {
    const rows = [
      ['Ingredient', 'Category', 'Exact Usage', 'Quantity', 'Unit', 'Est. Cost (INR)'],
      ...filteredItems.map((it) => [
        it.itemName,
        it.category,
        it.exactUsageLabel,
        String(it.totalQuantity),
        it.unit,
        it.estimatedCost.toFixed(2),
      ]),
    ];
    const csv =
      '\uFEFF' +
      rows
        .map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(','))
        .join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `filtr-inventory-usage-${range.toLowerCase()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard
          label="Orders Processed"
          value={String(ordersCount)}
          subtitle="Non-cancelled orders in range"
          badgeColor="accent"
          icon={<ShoppingBag className="h-4 w-4" />}
        />
        <KpiCard
          label="Ingredients Consumed"
          value={String(items.length)}
          subtitle="Deterministic Recipe Master × Orders"
          icon={<Package className="h-4 w-4" />}
        />
        <KpiCard
          label="Est. Ingredient Cost"
          value={formatInr(totalEstimatedSpend)}
          subtitle="Valued using latest Ingredient Master pack rates"
          badgeColor="warning"
          icon={<IndianRupee className="h-4 w-4" />}
        />
      </div>

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
          <Button size="sm" variant="outline" onPress={exportCsv} isDisabled={items.length === 0}>
            <Download className="h-3.5 w-3.5 text-accent" />
            <span>Export CSV</span>
          </Button>
        }
      />

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1 rounded-xl border border-border bg-surface p-0.5">
          {(
            [
              ['ALL', 'All Units'],
              ['WEIGHT', 'Weight (g/kg)'],
              ['VOLUME', 'Volume (ml/L)'],
              ['COUNT', 'Count / Packs'],
            ] as const
          ).map(([k, lbl]) => (
            <Button
              key={k}
              size="sm"
              variant={unitGroup === k ? 'primary' : 'ghost'}
              onPress={() => setUnitGroup(k)}
            >
              {lbl}
            </Button>
          ))}
        </div>

        <SearchField
          value={search}
          onChange={setSearch}
          variant="secondary"
          className="w-full sm:w-72"
        >
          <SearchField.Group>
            <SearchField.SearchIcon />
            <SearchField.Input placeholder="Filter ingredients..." />
            <SearchField.ClearButton />
          </SearchField.Group>
        </SearchField>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
          <Skeleton className="xl:col-span-7 h-72 rounded-2xl" />
          <Skeleton className="xl:col-span-5 h-72 rounded-2xl" />
        </div>
      ) : filteredItems.length === 0 ? (
        <Card className="border border-border/80">
          <Card.Content className="py-12 text-center flex flex-col items-center gap-2">
            <BarChart3 className="h-9 w-9 text-muted" />
            <p className="text-sm font-semibold">
              {ordersCount === 0
                ? 'No orders recorded for this period'
                : 'No Recipe Master mappings matched these filters'}
            </p>
            <p className="text-xs text-muted">
              Map drink & food recipes under Menu & Recipes to calculate exact raw usage.
            </p>
          </Card.Content>
        </Card>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
          <Card className="xl:col-span-7 border border-border/80">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold">
                Exact Ingredient Consumption ({filteredItems.length})
              </Card.Title>
              <Card.Description className="text-xs">
                Auto-converted canonical weights (g/kg) and volumes (ml/L)
              </Card.Description>
            </Card.Header>
            <Card.Content className="p-3">
              <Table variant="secondary">
                <Table.ScrollContainer>
                  <Table.Content aria-label="Exact ingredient usage table">
                    <Table.Header>
                      <Table.Column isRowHeader>Ingredient</Table.Column>
                      <Table.Column>Category</Table.Column>
                      <Table.Column className="text-right">Exact Usage</Table.Column>
                      <Table.Column className="text-right">Est. Cost</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {filteredItems.map((row) => (
                        <Table.Row key={row.inventoryItemId} id={String(row.inventoryItemId)}>
                          <Table.Cell className="font-semibold">{row.itemName}</Table.Cell>
                          <Table.Cell className="text-xs text-muted">{row.category}</Table.Cell>
                          <Table.Cell className="text-right whitespace-nowrap">
                            <Chip size="sm" variant="soft" color="accent" className="tabular-nums font-bold">
                              {row.exactUsageLabel}
                            </Chip>
                          </Table.Cell>
                          <Table.Cell className="text-right font-semibold tabular-nums text-muted">
                            {row.estimatedCost > 0 ? formatInr(row.estimatedCost, 2) : '—'}
                          </Table.Cell>
                        </Table.Row>
                      ))}
                    </Table.Body>
                  </Table.Content>
                </Table.ScrollContainer>
              </Table>
            </Card.Content>
          </Card>

          <Card className="xl:col-span-5 border border-border/80 h-fit">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold">
                Top Cost Ingredients Chart
              </Card.Title>
              <Card.Description className="text-xs">
                Highest estimated spend ingredients in selected range
              </Card.Description>
            </Card.Header>
            <Card.Content className="px-3 pb-4">
              <UsageBarChart
                data={[...filteredItems]
                  .sort((a, b) => b.estimatedCost - a.estimatedCost)
                  .map((it) => ({ name: it.itemName, cost: it.estimatedCost }))}
                height={320}
              />
            </Card.Content>
          </Card>
        </div>
      )}
    </div>
  );
}
