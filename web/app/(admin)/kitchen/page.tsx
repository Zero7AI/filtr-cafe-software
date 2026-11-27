'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Chip,
  Input,
  Label,
  Skeleton,
  Table,
  Tabs,
  TextField,
} from '@heroui/react';
import { AlertTriangle, CheckCircle2, Edit2, Plus, Refrigerator } from 'lucide-react';
import AdminModal from '@/components/admin/AdminModal';
import { useAdminContext } from '@/components/admin/AdminShell';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import { formatIstTime, getIstDateKey } from '@/lib/domain/ist';
import {
  KITCHEN_COUNTING_MODES,
  KITCHEN_SECTIONS,
  KitchenReorderRecommendation,
  KitchenVarianceRow,
} from '@/lib/domain/kitchen';
import {
  KitchenInventoryItem,
  KitchenInventoryShift,
  KitchenSectionId,
} from '@/lib/domain/types';

export default function AdminKitchenPage() {
  const { refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [dateKey, setDateKey] = useState(getIstDateKey());
  const [tab, setTab] = useState<'VARIANCE' | 'REORDER' | 'CATALOG'>('VARIANCE');
  const [sectionFilter, setSectionFilter] = useState<'ALL' | KitchenSectionId>('ALL');
  const [catalog, setCatalog] = useState<KitchenInventoryItem[]>([]);
  const [openingShift, setOpeningShift] = useState<KitchenInventoryShift | null>(null);
  const [closingShift, setClosingShift] = useState<KitchenInventoryShift | null>(null);
  const [varianceRows, setVarianceRows] = useState<KitchenVarianceRow[]>([]);
  const [reorders, setReorders] = useState<KitchenReorderRecommendation[]>([]);
  const [loading, setLoading] = useState(true);

  const [itemModal, setItemModal] = useState(false);
  const [editItem, setEditItem] = useState<Partial<KitchenInventoryItem>>({});

  const loadKitchen = useCallback(async () => {
    try {
      const res = await fetch(`/api/admin/kitchen?date=${dateKey}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        const d = await res.json();
        setCatalog(d.catalog || []);
        setOpeningShift(d.openingShift || null);
        setClosingShift(d.closingShift || null);
        setVarianceRows(d.varianceRows || []);
        setReorders(d.reorderRecommendations || []);
      }
    } finally {
      setLoading(false);
    }
  }, [dateKey]);

  useEffect(() => {
    loadKitchen();
  }, [loadKitchen, refreshTick]);

  const filteredVariance = useMemo(() => {
    if (sectionFilter === 'ALL') return varianceRows;
    return varianceRows.filter((r) => r.item.sectionId === sectionFilter);
  }, [varianceRows, sectionFilter]);

  const filteredCatalog = useMemo(() => {
    if (sectionFilter === 'ALL') return catalog;
    return catalog.filter((c) => c.sectionId === sectionFilter);
  }, [catalog, sectionFilter]);

  const filteredReorders = useMemo(() => {
    if (sectionFilter === 'ALL') return reorders;
    return reorders.filter((r) => r.item.sectionId === sectionFilter);
  }, [reorders, sectionFilter]);

  const saveCatalogItem = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/kitchen', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'upsertCatalogItem', item: editItem }),
    });
    if (res.ok) {
      pushToast('Saved kitchen catalog item', 'success');
      setItemModal(false);
      loadKitchen();
    } else {
      pushToast('Failed to save item', 'error');
    }
  };

  return (
    <div className="space-y-5">
      {/* Shift & Reorder Summary */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          label={`Opening Shift (${dateKey})`}
          value={openingShift?.status || 'NOT STARTED'}
          subtitle={
            openingShift
              ? `By ${openingShift.staffName} • ${openingShift.entries.length} checked`
              : 'Waiting for Android Kitchen check'
          }
          badgeText={openingShift ? formatIstTime(openingShift.lastUpdatedAt) : undefined}
          badgeColor={openingShift?.status === 'SUBMITTED' ? 'success' : 'warning'}
        />
        <KpiCard
          label={`Closing Shift (${dateKey})`}
          value={closingShift?.status || 'NOT STARTED'}
          subtitle={
            closingShift
              ? `By ${closingShift.staffName} • ${closingShift.entries.length} checked`
              : 'Waiting for closing check'
          }
          badgeText={closingShift ? formatIstTime(closingShift.lastUpdatedAt) : undefined}
          badgeColor={closingShift?.status === 'SUBMITTED' ? 'success' : 'default'}
        />
        <KpiCard
          label="Reorder Alerts"
          value={String(reorders.length)}
          subtitle="Items below zone threshold"
          badgeColor={reorders.length > 0 ? 'danger' : 'success'}
          icon={<AlertTriangle className="h-4 w-4" />}
        />
        <KpiCard
          label="Zone Catalog SKUs"
          value={String(catalog.length)}
          subtitle="Across 4 temperature storage zones"
          icon={<Refrigerator className="h-4 w-4" />}
        />
      </div>

      {/* Tabs + Date + Add */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs
          selectedKey={tab}
          onSelectionChange={(k) => setTab(String(k) as typeof tab)}
          variant="secondary"
          className="w-auto"
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Kitchen inventory tabs">
              <Tabs.Tab id="VARIANCE" className="text-xs px-3.5">
                Opening vs Closing Variance
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="REORDER" className="text-xs px-3.5">
                Reorder Alerts ({reorders.length})
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="CATALOG" className="text-xs px-3.5">
                Zone Catalog ({catalog.length})
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        <div className="flex items-center gap-2">
          <Input
            type="date"
            variant="secondary"
            value={dateKey}
            onChange={(e) => setDateKey(e.target.value)}
            aria-label="Kitchen shift date"
            className="text-xs h-9"
          />
          <Button
            size="sm"
            variant="primary"
            onPress={() => {
              setEditItem({
                name: '',
                iconEmoji: '📦',
                sectionId: 'NORMAL_TEMPERATURE',
                countingMode: 'DIRECT_PIECES',
                unitLabel: 'pcs',
                containerLabel: '',
                packSize: 1,
                minStockThreshold: 10,
                targetStockLevel: 30,
                isActive: true,
              });
              setItemModal(true);
            }}
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Kitchen Item</span>
          </Button>
        </div>
      </div>

      {/* Storage Zone Pills */}
      <div className="flex flex-wrap gap-1.5">
        <Button
          size="sm"
          variant={sectionFilter === 'ALL' ? 'primary' : 'outline'}
          onPress={() => setSectionFilter('ALL')}
        >
          All Zones
        </Button>
        {KITCHEN_SECTIONS.map((s) => (
          <Button
            key={s.id}
            size="sm"
            variant={sectionFilter === s.id ? 'primary' : 'outline'}
            onPress={() => setSectionFilter(s.id)}
          >
            {s.iconEmoji} {s.shortTitle}
          </Button>
        ))}
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 rounded-2xl" />
          ))}
        </div>
      ) : tab === 'VARIANCE' ? (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="Kitchen shift variance">
                  <Table.Header>
                    <Table.Column isRowHeader>Item</Table.Column>
                    <Table.Column>Zone</Table.Column>
                    <Table.Column className="text-right">Opening</Table.Column>
                    <Table.Column className="text-right">Closing</Table.Column>
                    <Table.Column className="text-right">Consumed</Table.Column>
                    <Table.Column className="text-right">Wastage</Table.Column>
                    <Table.Column>Status</Table.Column>
                  </Table.Header>
                  <Table.Body>
                    {filteredVariance.map((r) => (
                      <Table.Row key={r.item.id} id={r.item.id}>
                        <Table.Cell className="font-semibold">
                          {r.item.iconEmoji} {r.item.name}
                        </Table.Cell>
                        <Table.Cell className="text-xs text-muted">{r.item.sectionId}</Table.Cell>
                        <Table.Cell className="text-right text-xs tabular-nums">
                          {r.openingQty !== null ? `${r.openingQty} ${r.item.unitLabel}` : '—'}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs tabular-nums">
                          {r.closingQty !== null ? `${r.closingQty} ${r.item.unitLabel}` : '—'}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs font-bold text-accent tabular-nums">
                          {r.consumedQty !== null ? `${r.consumedQty} ${r.item.unitLabel}` : '—'}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs text-danger tabular-nums">
                          {r.wastageQty > 0 ? `${r.wastageQty} ${r.item.unitLabel}` : '0'}
                        </Table.Cell>
                        <Table.Cell>
                          <Chip
                            size="sm"
                            variant="soft"
                            color={
                              r.latestStatus === 'RED'
                                ? 'danger'
                                : r.latestStatus === 'ORANGE'
                                  ? 'warning'
                                  : 'success'
                            }
                          >
                            {r.latestStatus}
                          </Chip>
                        </Table.Cell>
                      </Table.Row>
                    ))}
                  </Table.Body>
                </Table.Content>
              </Table.ScrollContainer>
            </Table>
          </Card.Content>
        </Card>
      ) : tab === 'REORDER' ? (
        filteredReorders.length === 0 ? (
          <Card className="border border-border/80">
            <Card.Content className="py-12 text-center flex flex-col items-center gap-2">
              <CheckCircle2 className="h-10 w-10 text-success" />
              <p className="text-sm font-bold">
                No kitchen items below threshold for {dateKey}
              </p>
            </Card.Content>
          </Card>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
            {filteredReorders.map((rec) => (
              <Card key={rec.item.id} className="border border-border/80">
                <Card.Content className="p-4 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="font-bold text-sm">
                      {rec.item.iconEmoji} {rec.item.name}
                    </span>
                    <Chip
                      size="sm"
                      variant="soft"
                      color={rec.status === 'RED' ? 'danger' : 'warning'}
                    >
                      {rec.status === 'RED' ? 'URGENT' : 'REORDER'}
                    </Chip>
                  </div>
                  <p className="text-xs text-muted tabular-nums">
                    Current: <strong className="text-foreground">{rec.currentStock}</strong> / Min:{' '}
                    <strong className="text-foreground">
                      {rec.minimumRequiredStock} {rec.item.unitLabel}
                    </strong>
                  </p>
                  <p className="text-xs font-bold text-accent tabular-nums">
                    Suggested Order: {rec.suggestedPurchaseQty} {rec.item.unitLabel}
                    {rec.suggestedPacksToOrder > 0
                      ? ` (${rec.suggestedPacksToOrder} packs)`
                      : ''}
                  </p>
                </Card.Content>
              </Card>
            ))}
          </div>
        )
      ) : (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="Kitchen zone catalog">
                  <Table.Header>
                    <Table.Column isRowHeader>Item</Table.Column>
                    <Table.Column>Zone</Table.Column>
                    <Table.Column>Counting Mode</Table.Column>
                    <Table.Column className="text-right">Pack Size</Table.Column>
                    <Table.Column className="text-right">Min</Table.Column>
                    <Table.Column className="text-right">Target</Table.Column>
                    <Table.Column className="text-right">Edit</Table.Column>
                  </Table.Header>
                  <Table.Body>
                    {filteredCatalog.map((item) => (
                      <Table.Row key={item.id} id={item.id}>
                        <Table.Cell className="font-semibold">
                          {item.iconEmoji} {item.name}
                        </Table.Cell>
                        <Table.Cell className="text-xs text-muted">{item.sectionId}</Table.Cell>
                        <Table.Cell className="text-xs text-muted">{item.countingMode}</Table.Cell>
                        <Table.Cell className="text-right text-xs tabular-nums">
                          {item.packSize} {item.unitLabel}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs tabular-nums">
                          {item.minStockThreshold}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs tabular-nums">
                          {item.targetStockLevel}
                        </Table.Cell>
                        <Table.Cell className="text-right">
                          <Button
                            isIconOnly
                            size="sm"
                            variant="ghost"
                            onPress={() => {
                              setEditItem(item);
                              setItemModal(true);
                            }}
                            aria-label={`Edit ${item.name}`}
                          >
                            <Edit2 className="h-3.5 w-3.5" />
                          </Button>
                        </Table.Cell>
                      </Table.Row>
                    ))}
                  </Table.Body>
                </Table.Content>
              </Table.ScrollContainer>
            </Table>
          </Card.Content>
        </Card>
      )}

      <AdminModal
        isOpen={itemModal}
        onClose={() => setItemModal(false)}
        title="Kitchen Catalog Item"
      >
        <form onSubmit={saveCatalogItem} className="space-y-3">
          <div className="grid grid-cols-4 gap-2">
            <div className="col-span-3">
              <TextField
                value={editItem.name || ''}
                onChange={(v) => setEditItem({ ...editItem, name: v })}
                isRequired
                fullWidth
              >
                <Label className="text-xs font-medium">Item Name</Label>
                <Input variant="secondary" />
              </TextField>
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Emoji</Label>
              <Input
                variant="secondary"
                value={editItem.iconEmoji || '📦'}
                onChange={(e) => setEditItem({ ...editItem, iconEmoji: e.target.value })}
                className="text-center"
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-medium">Storage Zone</Label>
              <select
                value={editItem.sectionId || 'NORMAL_TEMPERATURE'}
                onChange={(e) =>
                  setEditItem({ ...editItem, sectionId: e.target.value as KitchenSectionId })
                }
                className="w-full rounded-xl border border-border bg-surface-secondary px-3 py-2 text-xs text-foreground"
              >
                {KITCHEN_SECTIONS.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.title}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Counting Mode</Label>
              <select
                value={editItem.countingMode || 'DIRECT_PIECES'}
                onChange={(e) =>
                  setEditItem({
                    ...editItem,
                    countingMode: e.target.value as KitchenInventoryItem['countingMode'],
                  })
                }
                className="w-full rounded-xl border border-border bg-surface-secondary px-3 py-2 text-xs text-foreground"
              >
                {KITCHEN_COUNTING_MODES.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.label}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div className="space-y-1">
              <Label className="text-xs font-medium">Pack Size</Label>
              <Input
                type="number"
                step="0.01"
                variant="secondary"
                value={String(editItem.packSize ?? 1)}
                onChange={(e) => setEditItem({ ...editItem, packSize: Number(e.target.value) })}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Min Threshold</Label>
              <Input
                type="number"
                step="0.01"
                variant="secondary"
                value={String(editItem.minStockThreshold ?? 1)}
                onChange={(e) =>
                  setEditItem({ ...editItem, minStockThreshold: Number(e.target.value) })
                }
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Target Level</Label>
              <Input
                type="number"
                step="0.01"
                variant="secondary"
                value={String(editItem.targetStockLevel ?? 2)}
                onChange={(e) =>
                  setEditItem({ ...editItem, targetStockLevel: Number(e.target.value) })
                }
              />
            </div>
          </div>

          <Button type="submit" variant="primary" fullWidth>
            Save Kitchen Item
          </Button>
        </form>
      </AdminModal>
    </div>
  );
}
