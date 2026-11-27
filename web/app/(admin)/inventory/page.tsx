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
  Switch,
  Table,
  Tabs,
  TextField,
} from '@heroui/react';
import {
  AlertTriangle,
  Download,
  Edit2,
  ExternalLink,
  IndianRupee,
  MessageSquare,
  Package,
  Plus,
  Receipt,
  RefreshCw,
  Trash2,
  TrendingUp,
  Upload,
} from 'lucide-react';
import AdminModal from '@/components/admin/AdminModal';
import { useAdminContext } from '@/components/admin/AdminShell';
import { SkuPriceLineChart } from '@/components/admin/Charts';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import {
  BillLedgerRow,
  buildSkuPriceHistoryCsv,
  formatBillCell,
  SkuPriceHistoryMatrix,
  SkuPriceHistoryRow,
} from '@/lib/domain/inventory';
import { formatInr } from '@/lib/domain/ist';
import { InventoryItem, WhatsappMessage } from '@/lib/domain/types';

export default function AdminInventoryPage() {
  const { refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [tab, setTab] = useState<'MATRIX' | 'LEDGER' | 'WHATSAPP' | 'MASTER'>('MATRIX');
  const [matrixFilter, setMatrixFilter] = useState<'ALL' | 'HIKES' | 'ANOMALIES'>('ALL');
  const [items, setItems] = useState<InventoryItem[]>([]);
  const [matrix, setMatrix] = useState<SkuPriceHistoryMatrix>({
    maxBillColumns: 0,
    rows: [],
  });
  const [ledger, setLedger] = useState<BillLedgerRow[]>([]);
  const [totalBillSpend, setTotalBillSpend] = useState(0);
  const [billsCount, setBillsCount] = useState(0);
  const [uniqueSuppliersCount, setUniqueSuppliersCount] = useState(0);
  const [googleSheetsUrl, setGoogleSheetsUrl] = useState(
    'https://docs.google.com/spreadsheets/d/1UsUM0SLdDf4uHXhJuao96Twkd-Eq5Yxl4_VHM_sFw90/edit?gid=0#gid=0'
  );
  const [webhookConfigured, setWebhookConfigured] = useState(false);
  const [waMessages, setWaMessages] = useState<WhatsappMessage[]>([]);
  const [pendingWaCount, setPendingWaCount] = useState(0);
  const [includeDates, setIncludeDates] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [syncingSheet, setSyncingSheet] = useState(false);
  const [retryingWa, setRetryingWa] = useState(false);
  const [scanningReceipt, setScanningReceipt] = useState(false);

  // SKU Drill-down Modal
  const [selectedSku, setSelectedSku] = useState<SkuPriceHistoryRow | null>(null);

  // New Purchase Bill Modal
  const [billModal, setBillModal] = useState(false);
  const [supplierName, setSupplierName] = useState('');
  const [billNotes, setBillNotes] = useState('');
  const [billLines, setBillLines] = useState<
    Array<{ item_name: string; quantity: number; unit: string; unit_price: number }>
  >([{ item_name: '', quantity: 1, unit: 'kg', unit_price: 0 }]);

  // Ingredient Master Modal
  const [masterModal, setMasterModal] = useState(false);
  const [editMaster, setEditMaster] = useState<Partial<InventoryItem>>({});

  const loadData = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/inventory', { cache: 'no-store' });
      if (res.ok) {
        const d = await res.json();
        setItems(d.items || []);
        setMatrix(d.skuMatrix || { maxBillColumns: 0, rows: [] });
        setLedger(d.billLedger || []);
        setTotalBillSpend(Number(d.totalBillSpend || 0));
        setBillsCount(Number(d.billsCount || 0));
        setUniqueSuppliersCount(Number(d.uniqueSuppliersCount || 0));
        if (d.googleSheetsUrl) setGoogleSheetsUrl(d.googleSheetsUrl);
        setWebhookConfigured(Boolean(d.googleSheetsWebhookConfigured));
        setWaMessages(d.whatsappMessages || []);
        setPendingWaCount(Number(d.pendingWhatsappCount || 0));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData, refreshTick]);

  const filteredMatrixRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return matrix.rows.filter((r) => {
      if (matrixFilter === 'HIKES' && !r.isPriceHike) return false;
      if (matrixFilter === 'ANOMALIES' && !r.hasUnitAnomaly) return false;
      if (!q) return true;
      return (
        r.skuName.toLowerCase().includes(q) ||
        r.bills.some((b) => b.supplierName.toLowerCase().includes(q))
      );
    });
  }, [matrix.rows, matrixFilter, searchQuery]);

  const filteredLedgerRows = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return ledger;
    return ledger.filter(
      (l) =>
        l.orderId.toLowerCase().includes(q) ||
        l.supplierName.toLowerCase().includes(q) ||
        l.matchedSkuName.toLowerCase().includes(q) ||
        l.rawItemName.toLowerCase().includes(q)
    );
  }, [ledger, searchQuery]);

  const filteredMasterItems = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (it) =>
        it.name.toLowerCase().includes(q) ||
        it.category.toLowerCase().includes(q)
    );
  }, [items, searchQuery]);

  const syncGoogleSheetNow = async () => {
    setSyncingSheet(true);
    try {
      const res = await fetch('/api/admin/inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'syncGoogleSheet',
          includeDateInCells: includeDates,
        }),
      });
      const d = await res.json().catch(() => ({}));
      if (res.ok) {
        pushToast(`Synced ${d.count || matrix.rows.length} SKUs to live Google Sheet`, 'success');
      } else {
        pushToast(d.error || 'Sheet sync failed', 'error');
      }
    } finally {
      setSyncingSheet(false);
    }
  };

  const retryWhatsappPending = async () => {
    setRetryingWa(true);
    try {
      const res = await fetch('/api/admin/inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ action: 'retryWhatsappPending' }),
      });
      const d = await res.json().catch(() => ({}));
      if (res.ok) {
        pushToast(
          `Processed ${d.processedCount || 0} pending bill(s): ${d.savedCount || 0} saved!`,
          'success'
        );
        loadData();
      } else {
        pushToast(d.error || 'Retry trigger failed', 'error');
      }
    } finally {
      setRetryingWa(false);
    }
  };

  const handleReceiptUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setScanningReceipt(true);
    try {
      const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result || ''));
        reader.onerror = () => reject(new Error('Failed to read file'));
        reader.readAsDataURL(file);
      });
      const base64 = dataUrl.includes(',') ? dataUrl.split(',')[1] : dataUrl;
      const res = await fetch('/api/admin/inventory', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          action: 'scanUploadReceipt',
          base64,
          mimeType: file.type || 'image/jpeg',
          filename: file.name,
        }),
      });
      const d = await res.json().catch(() => ({}));
      if (res.ok) {
        pushToast(
          `AI scanned ${file.name} -> ${d.orderId || 'Saved'}${d.isDuplicate ? ' (duplicate skipped)' : ''}`,
          'success'
        );
        loadData();
      } else {
        pushToast(d.error || 'AI scan failed', 'error');
      }
    } catch (err) {
      pushToast(err instanceof Error ? err.message : 'Upload failed', 'error');
    } finally {
      setScanningReceipt(false);
    }
  };

  const downloadCsv = () => {
    const csv = buildSkuPriceHistoryCsv(matrix, includeDates);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'FILTR_SKU_Price_History.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const createPurchaseBill = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/inventory', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'createPurchaseBill',
        supplier_name: supplierName,
        notes: billNotes,
        items: billLines.map((l) => ({
          ...l,
          subtotal: l.quantity * l.unit_price,
        })),
      }),
    });
    if (res.ok) {
      pushToast('Purchase bill saved & SKU price history updated', 'success');
      setBillModal(false);
      setSupplierName('');
      setBillNotes('');
      setBillLines([{ item_name: '', quantity: 1, unit: 'kg', unit_price: 0 }]);
      loadData();
    } else {
      pushToast('Failed to save bill', 'error');
    }
  };

  const deleteBill = async (orderId: string) => {
    if (!confirm(`Delete purchase bill ${orderId}?`)) return;
    const res = await fetch('/api/admin/inventory', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'deletePurchaseBill', orderId }),
    });
    if (res.ok) {
      pushToast(`Deleted ${orderId}`, 'success');
      loadData();
    }
  };

  const saveMasterItem = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/inventory', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'upsertInventoryItem', ...editMaster }),
    });
    if (res.ok) {
      pushToast(`Saved ${editMaster.name}`, 'success');
      setMasterModal(false);
      loadData();
    } else {
      const d = await res.json().catch(() => ({}));
      pushToast(d.error || 'Failed to save ingredient', 'error');
    }
  };

  const toggleMasterActive = async (item: InventoryItem, isActive: boolean) => {
    const res = await fetch('/api/admin/inventory', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'toggleInventoryItem',
        id: item.id,
        is_active: isActive,
      }),
    });
    if (res.ok) {
      pushToast(`${item.name}: ${isActive ? 'Active' : 'Inactive'}`, 'success');
      loadData();
    }
  };

  const billColsCount = Math.max(1, matrix.maxBillColumns);
  const hikeCount = matrix.rows.filter((r) => r.isPriceHike).length;
  const anomalyCount = matrix.rows.filter((r) => r.hasUnitAnomaly).length;

  return (
    <div className="space-y-5">
      {/* 4 Summary KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          label="Total Logged Procurement"
          value={formatInr(totalBillSpend)}
          subtitle={`${billsCount} bills • ${uniqueSuppliersCount} suppliers`}
          icon={<IndianRupee className="h-4 w-4" />}
        />
        <KpiCard
          label="Tracked SKUs & Price Hikes"
          value={`${matrix.rows.length} SKUs`}
          subtitle={`${hikeCount} SKUs up vs first bill`}
          badgeText={hikeCount > 0 ? `${hikeCount} Hikes` : 'Prices Stable'}
          badgeColor={hikeCount > 0 ? 'danger' : 'success'}
          icon={<TrendingUp className="h-4 w-4" />}
        />
        <KpiCard
          label="CHECK UNIT Flags"
          value={String(anomalyCount)}
          subtitle=">3x / <0.3x unit jump excluded from %"
          badgeColor={anomalyCount > 0 ? 'warning' : 'success'}
          icon={<AlertTriangle className="h-4 w-4" />}
        />
        <KpiCard
          label="WhatsApp Receipt Bot"
          value={pendingWaCount > 0 ? `${pendingWaCount} Pending` : 'Healthy'}
          subtitle={`${waMessages.length} recent webhook logs`}
          badgeColor={pendingWaCount > 0 ? 'danger' : 'success'}
          icon={<MessageSquare className="h-4 w-4" />}
        />
      </div>

      {/* Action Banner Card */}
      <Card className="border border-success/30">
        <Card.Content className="p-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-success">
              Purchase Tracking • Live Google Sheets & Excel Matrix
            </p>
            <p className="text-xs text-muted mt-0.5">
              Click any SKU row in the matrix to inspect its full bill-by-bill rate trajectory.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <a href={googleSheetsUrl} target="_blank" rel="noopener noreferrer">
              <Button size="sm" variant="primary">
                <ExternalLink className="h-3.5 w-3.5" />
                <span>Open Live Sheet</span>
              </Button>
            </a>

            <a href={`/api/admin/inventory/export-xlsx?includeDates=${includeDates}`}>
              <Button size="sm" variant="outline">
                <Download className="h-3.5 w-3.5 text-success" />
                <span>Excel (.xlsx)</span>
              </Button>
            </a>

            <Button size="sm" variant="outline" onPress={downloadCsv}>
              <Download className="h-3.5 w-3.5" />
              <span>CSV</span>
            </Button>

            <Button
              size="sm"
              variant="outline"
              isDisabled={!webhookConfigured || syncingSheet}
              onPress={syncGoogleSheetNow}
            >
              <RefreshCw className={`h-3.5 w-3.5 ${syncingSheet ? 'animate-spin' : ''}`} />
              <span>{syncingSheet ? 'Syncing...' : 'Sync Sheet'}</span>
            </Button>

            <label className="inline-flex items-center gap-1.5 rounded-xl border border-amber-500/40 bg-amber-500/15 px-3 py-1.5 text-xs font-bold text-amber-400 cursor-pointer hover:bg-amber-500/25 transition">
              <Upload className="h-3.5 w-3.5" />
              <span>{scanningReceipt ? 'AI Scanning...' : 'Scan Bill (Photo / PDF)'}</span>
              <input
                type="file"
                accept="image/*,application/pdf"
                disabled={scanningReceipt}
                onChange={handleReceiptUpload}
                className="hidden"
              />
            </label>

            <Button size="sm" variant="primary" onPress={() => setBillModal(true)}>
              <Plus className="h-3.5 w-3.5" />
              <span>Add Bill</span>
            </Button>
          </div>
        </Card.Content>
      </Card>

      {/* Tabs + Search + Controls */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs
          selectedKey={tab}
          onSelectionChange={(k) => setTab(String(k) as typeof tab)}
          variant="secondary"
          className="w-auto"
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Purchase tracking tabs">
              <Tabs.Tab id="MATRIX" className="text-xs px-3.5">
                SKU Price History ({matrix.rows.length})
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="LEDGER" className="text-xs px-3.5">
                Bill Ledger ({ledger.length})
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="WHATSAPP" className="text-xs px-3.5">
                WhatsApp Bot{pendingWaCount > 0 ? ` (${pendingWaCount})` : ''}
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="MASTER" className="text-xs px-3.5">
                Ingredient Master ({items.length})
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        <div className="flex flex-wrap items-center gap-3">
          {tab === 'MATRIX' && (
            <>
              <div className="flex items-center gap-1 rounded-xl border border-border bg-surface p-0.5">
                {(
                  [
                    ['ALL', 'All'],
                    ['HIKES', `Hikes (${hikeCount})`],
                    ['ANOMALIES', `Check Unit (${anomalyCount})`],
                  ] as const
                ).map(([k, lbl]) => (
                  <Button
                    key={k}
                    size="sm"
                    variant={matrixFilter === k ? 'primary' : 'ghost'}
                    onPress={() => setMatrixFilter(k)}
                  >
                    {lbl}
                  </Button>
                ))}
              </div>
              <Switch isSelected={includeDates} onChange={setIncludeDates} size="sm">
                <Switch.Content>
                  <Switch.Control>
                    <Switch.Thumb />
                  </Switch.Control>
                  <Label className="text-xs font-medium">Dates in Cells</Label>
                </Switch.Content>
              </Switch>
            </>
          )}

          {tab === 'MASTER' && (
            <Button
              size="sm"
              variant="outline"
              onPress={() => {
                setEditMaster({
                  name: '',
                  category: 'Dairy & Liquids',
                  unit: 'kg',
                  standard_quantity: 1,
                  default_price: 0,
                  current_stock: 0,
                  is_active: true,
                });
                setMasterModal(true);
              }}
            >
              <Plus className="h-3.5 w-3.5 text-accent" />
              <span>Add Master SKU</span>
            </Button>
          )}

          <SearchField
            value={searchQuery}
            onChange={setSearchQuery}
            variant="secondary"
            className="w-full sm:w-72"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input placeholder="Search SKU or supplier..." />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 rounded-2xl" />
          ))}
        </div>
      ) : tab === 'MATRIX' ? (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="SKU Price History matrix">
                  <Table.Header>
                    <Table.Column isRowHeader>SKU Name</Table.Column>
                    <Table.Column>Unit / Weight</Table.Column>
                    <Table.Column className="text-right">Latest Price</Table.Column>
                    <Table.Column>vs First %</Table.Column>
                    {Array.from({ length: billColsCount }, (_, i) => (
                      <Table.Column key={i}>Bill {i + 1}</Table.Column>
                    ))}
                  </Table.Header>
                  <Table.Body
                    renderEmptyState={() => (
                      <div className="py-10 text-center text-xs text-muted">
                        No SKU pricing entries match this filter
                      </div>
                    )}
                  >
                    {filteredMatrixRows.map((r) => {
                      const pct = r.priceChangeVsFirstPct;
                      const pctText =
                        pct !== null ? `${pct > 0 ? '+' : ''}${pct.toFixed(1)}%` : '0.0%';
                      return (
                        <Table.Row
                          key={r.skuName}
                          id={r.skuName}
                          className="cursor-pointer"
                          onAction={() => setSelectedSku(r)}
                        >
                          <Table.Cell className="font-semibold whitespace-nowrap">
                            {r.skuName}
                          </Table.Cell>
                          <Table.Cell className="text-xs text-muted whitespace-nowrap">
                            {r.unitWeightLabel}
                          </Table.Cell>
                          <Table.Cell className="text-right font-bold tabular-nums whitespace-nowrap">
                            {formatInr(r.latestPrice, 2)}
                          </Table.Cell>
                          <Table.Cell className="whitespace-nowrap">
                            <Chip
                              size="sm"
                              variant="soft"
                              color={
                                pct !== null && pct > 0
                                  ? 'danger'
                                  : pct !== null && pct < 0
                                    ? 'success'
                                    : 'default'
                              }
                            >
                              {pctText}
                            </Chip>
                          </Table.Cell>
                          {Array.from({ length: billColsCount }, (_, idx) => {
                            const b = r.bills[idx];
                            return (
                              <Table.Cell
                                key={idx}
                                className={`font-mono text-xs whitespace-nowrap ${
                                  b?.isUnitAnomaly ? 'text-warning font-bold' : 'text-muted'
                                }`}
                              >
                                {b ? formatBillCell(b, includeDates) : '—'}
                              </Table.Cell>
                            );
                          })}
                        </Table.Row>
                      );
                    })}
                  </Table.Body>
                </Table.Content>
              </Table.ScrollContainer>
            </Table>
          </Card.Content>
        </Card>
      ) : tab === 'LEDGER' ? (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="Purchase bill ledger">
                  <Table.Header>
                    <Table.Column isRowHeader>Bill ID</Table.Column>
                    <Table.Column>Date</Table.Column>
                    <Table.Column>Supplier</Table.Column>
                    <Table.Column>Raw Item</Table.Column>
                    <Table.Column>Matched SKU</Table.Column>
                    <Table.Column>Qty</Table.Column>
                    <Table.Column className="text-right">Total</Table.Column>
                    <Table.Column className="text-right">Normalized</Table.Column>
                    <Table.Column>Status</Table.Column>
                    <Table.Column className="text-right">Action</Table.Column>
                  </Table.Header>
                  <Table.Body>
                    {filteredLedgerRows.map((row, idx) => (
                      <Table.Row key={`${row.orderId}-${idx}`} id={`${row.orderId}-${idx}`}>
                        <Table.Cell className="font-mono text-xs font-bold text-accent whitespace-nowrap">
                          {row.orderId}
                        </Table.Cell>
                        <Table.Cell className="text-xs text-muted whitespace-nowrap">
                          {row.dateFormatted}
                        </Table.Cell>
                        <Table.Cell className="text-xs whitespace-nowrap">{row.supplierName}</Table.Cell>
                        <Table.Cell className="text-xs text-muted">{row.rawItemName}</Table.Cell>
                        <Table.Cell className="text-xs font-semibold">{row.matchedSkuName}</Table.Cell>
                        <Table.Cell className="text-xs whitespace-nowrap tabular-nums">
                          {row.quantity} {row.unit}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs font-semibold tabular-nums whitespace-nowrap">
                          {formatInr(row.subtotal, 2)}
                        </Table.Cell>
                        <Table.Cell className="text-right text-xs font-bold tabular-nums whitespace-nowrap">
                          {formatInr(row.normalizedPrice, 2)}
                        </Table.Cell>
                        <Table.Cell className="whitespace-nowrap">
                          <Chip
                            size="sm"
                            variant="soft"
                            color={
                              row.statusLabel === 'CHECK UNIT'
                                ? 'warning'
                                : row.statusLabel === 'HARDWARE / EXCLUDED'
                                  ? 'default'
                                  : 'success'
                            }
                          >
                            {row.statusLabel}
                          </Chip>
                        </Table.Cell>
                        <Table.Cell className="text-right">
                          <Button
                            isIconOnly
                            size="sm"
                            variant="danger-soft"
                            onPress={() => deleteBill(row.orderId)}
                            aria-label="Delete purchase bill"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
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
      ) : tab === 'WHATSAPP' ? (
        <div className="space-y-4">
          {pendingWaCount > 0 && (
            <Card className="border border-warning/40 bg-warning-soft">
              <Card.Content className="p-4 flex flex-wrap items-center justify-between gap-3">
                <div>
                  <p className="text-xs font-bold text-warning">
                    {pendingWaCount} WhatsApp Bill(s) Waiting for Retry
                  </p>
                  <p className="text-xs text-muted">
                    Once Gemini API access is healthy, trigger automatic reprocessing for queued bills.
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="primary"
                  isDisabled={retryingWa}
                  onPress={retryWhatsappPending}
                >
                  {retryingWa ? 'Retrying...' : 'Retry Pending Bills'}
                </Button>
              </Card.Content>
            </Card>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3.5">
            {waMessages.map((m) => (
              <Card key={m.id} className="border border-border/80">
                <Card.Content className="p-4 text-xs space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold">
                      {m.from_number} • {m.message_type}
                    </span>
                    <Chip
                      size="sm"
                      variant="soft"
                      color={
                        m.status === 'saved' || m.status === 'PROCESSED'
                          ? 'success'
                          : m.status === 'retry_pending' || m.status === 'config_error'
                            ? 'danger'
                            : 'warning'
                      }
                    >
                      {m.status}
                    </Chip>
                  </div>
                  <p className="text-muted">{m.message_body || '(Media attachment)'}</p>
                  {m.inventory_order_id ? (
                    <p className="text-success font-mono font-semibold">
                      Linked Bill: {m.inventory_order_id}
                    </p>
                  ) : null}
                  {m.error_message ? (
                    <p className="text-danger">{m.error_message}</p>
                  ) : null}
                </Card.Content>
              </Card>
            ))}
          </div>
        </div>
      ) : (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="Ingredient master catalog">
                  <Table.Header>
                    <Table.Column isRowHeader>Ingredient Name</Table.Column>
                    <Table.Column>Category</Table.Column>
                    <Table.Column>Standard Pack</Table.Column>
                    <Table.Column className="text-right">Default Rate</Table.Column>
                    <Table.Column>Active</Table.Column>
                    <Table.Column className="text-right">Edit</Table.Column>
                  </Table.Header>
                  <Table.Body>
                    {filteredMasterItems.map((it) => (
                      <Table.Row key={it.id} id={String(it.id)}>
                        <Table.Cell className="font-semibold">{it.name}</Table.Cell>
                        <Table.Cell className="text-xs text-muted">{it.category}</Table.Cell>
                        <Table.Cell className="text-xs tabular-nums">
                          {it.standard_quantity} {it.unit}
                        </Table.Cell>
                        <Table.Cell className="text-right font-bold tabular-nums text-accent">
                          {formatInr(it.default_price, 2)}
                        </Table.Cell>
                        <Table.Cell>
                          <Switch
                            isSelected={it.is_active}
                            onChange={(val) => toggleMasterActive(it, val)}
                            size="sm"
                            aria-label={`Toggle ${it.name}`}
                          >
                            <Switch.Content>
                              <Switch.Control>
                                <Switch.Thumb />
                              </Switch.Control>
                            </Switch.Content>
                          </Switch>
                        </Table.Cell>
                        <Table.Cell className="text-right">
                          <Button
                            isIconOnly
                            size="sm"
                            variant="ghost"
                            onPress={() => {
                              setEditMaster(it);
                              setMasterModal(true);
                            }}
                            aria-label={`Edit ${it.name}`}
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

      {/* SKU Drill-down Price Trajectory Modal */}
      <AdminModal
        isOpen={!!selectedSku}
        onClose={() => setSelectedSku(null)}
        title={selectedSku ? `SKU Trajectory — ${selectedSku.skuName}` : 'SKU Trajectory'}
        subtitle={
          selectedSku
            ? `${selectedSku.unitWeightLabel} • Latest: ${formatInr(selectedSku.latestPrice, 2)}`
            : ''
        }
        size="lg"
      >
        {selectedSku ? (
          <div className="space-y-4">
            <SkuPriceLineChart
              data={selectedSku.bills
                .filter((b) => !b.isUnitAnomaly)
                .map((b) => ({
                  label: `B${b.billIndex} (${b.dateLabel})`,
                  price: b.price,
                }))}
              height={220}
            />

            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="SKU bill points">
                  <Table.Header>
                    <Table.Column isRowHeader>Bill #</Table.Column>
                    <Table.Column>Date</Table.Column>
                    <Table.Column>Supplier</Table.Column>
                    <Table.Column>Order ID</Table.Column>
                    <Table.Column className="text-right">Normalized Rate</Table.Column>
                  </Table.Header>
                  <Table.Body>
                    {selectedSku.bills.map((b) => (
                      <Table.Row key={b.billIndex} id={String(b.billIndex)}>
                        <Table.Cell className="font-semibold">Bill {b.billIndex}</Table.Cell>
                        <Table.Cell className="text-xs text-muted">{b.dateLabel}</Table.Cell>
                        <Table.Cell className="text-xs">{b.supplierName}</Table.Cell>
                        <Table.Cell className="font-mono text-xs text-muted">{b.orderId}</Table.Cell>
                        <Table.Cell className="text-right font-bold tabular-nums">
                          {b.isUnitAnomaly ? (
                            <Chip size="sm" variant="soft" color="warning">
                              CHECK UNIT ({formatInr(b.price, 2)})
                            </Chip>
                          ) : (
                            formatInr(b.price, 2)
                          )}
                        </Table.Cell>
                      </Table.Row>
                    ))}
                  </Table.Body>
                </Table.Content>
              </Table.ScrollContainer>
            </Table>
          </div>
        ) : null}
      </AdminModal>

      {/* New Purchase Bill Modal */}
      <AdminModal
        isOpen={billModal}
        onClose={() => setBillModal(false)}
        title="Record Purchase Bill"
        subtitle="Updates Bill Ledger and recalculates SKU Price History"
        size="lg"
      >
        <form onSubmit={createPurchaseBill} className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <TextField value={supplierName} onChange={setSupplierName} isRequired fullWidth>
              <Label className="text-xs font-medium">Supplier Name</Label>
              <Input variant="secondary" placeholder="e.g. Amrapur Dairy" />
            </TextField>
            <TextField value={billNotes} onChange={setBillNotes} fullWidth>
              <Label className="text-xs font-medium">Notes / Invoice #</Label>
              <Input variant="secondary" placeholder="Bill # / Date" />
            </TextField>
          </div>

          <div className="space-y-2">
            <div className="flex justify-between items-center">
              <Label className="text-xs font-medium">Bill Line Items</Label>
              <Button
                size="sm"
                variant="outline"
                onPress={() =>
                  setBillLines((prev) => [
                    ...prev,
                    { item_name: '', quantity: 1, unit: 'kg', unit_price: 0 },
                  ])
                }
              >
                <Plus className="h-3.5 w-3.5" /> Add Line
              </Button>
            </div>

            {billLines.map((line, idx) => (
              <div key={idx} className="grid grid-cols-12 gap-2 items-center">
                <Input
                  type="text"
                  list="inv-names"
                  variant="secondary"
                  value={line.item_name}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) => (i === idx ? { ...l, item_name: e.target.value } : l))
                    )
                  }
                  placeholder="Ingredient"
                  required
                  className="col-span-5 text-xs"
                />
                <Input
                  type="number"
                  step="0.01"
                  variant="secondary"
                  value={String(line.quantity)}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) =>
                        i === idx ? { ...l, quantity: Number(e.target.value) } : l
                      )
                    )
                  }
                  className="col-span-2 text-xs tabular-nums"
                />
                <Input
                  type="text"
                  variant="secondary"
                  value={line.unit}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) => (i === idx ? { ...l, unit: e.target.value } : l))
                    )
                  }
                  className="col-span-2 text-xs"
                />
                <Input
                  type="number"
                  step="0.01"
                  variant="secondary"
                  value={String(line.unit_price)}
                  onChange={(e) =>
                    setBillLines((prev) =>
                      prev.map((l, i) =>
                        i === idx ? { ...l, unit_price: Number(e.target.value) } : l
                      )
                    )
                  }
                  placeholder="Rate"
                  className="col-span-2 text-xs tabular-nums"
                />
                <Button
                  isIconOnly
                  size="sm"
                  variant="danger-soft"
                  isDisabled={billLines.length <= 1}
                  onPress={() =>
                    setBillLines((prev) => prev.filter((_, i) => i !== idx))
                  }
                  aria-label="Remove line"
                  className="col-span-1"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))}
            <datalist id="inv-names">
              {items.map((it) => (
                <option key={it.id} value={it.name} />
              ))}
            </datalist>
          </div>

          <Button type="submit" variant="primary" fullWidth>
            Save Purchase Bill (Total:{' '}
            {formatInr(
              billLines.reduce((s, l) => s + l.quantity * l.unit_price, 0)
            )}
            )
          </Button>
        </form>
      </AdminModal>

      {/* Master SKU Modal */}
      <AdminModal
        isOpen={masterModal}
        onClose={() => setMasterModal(false)}
        title={editMaster.id ? 'Edit Ingredient Master SKU' : 'New Ingredient Master SKU'}
      >
        <form onSubmit={saveMasterItem} className="space-y-3">
          <TextField
            value={editMaster.name || ''}
            onChange={(v) => setEditMaster({ ...editMaster, name: v })}
            isRequired
            fullWidth
          >
            <Label className="text-xs font-medium">Ingredient Name</Label>
            <Input variant="secondary" />
          </TextField>

          <div className="grid grid-cols-2 gap-3">
            <TextField
              value={editMaster.category || 'General'}
              onChange={(v) => setEditMaster({ ...editMaster, category: v })}
              fullWidth
            >
              <Label className="text-xs font-medium">Category</Label>
              <Input variant="secondary" />
            </TextField>
            <TextField
              value={editMaster.unit || 'kg'}
              onChange={(v) => setEditMaster({ ...editMaster, unit: v })}
              fullWidth
            >
              <Label className="text-xs font-medium">Unit</Label>
              <Input variant="secondary" placeholder="kg, g, L, ml, pcs" />
            </TextField>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-medium">Standard Pack Size</Label>
              <Input
                type="number"
                step="0.01"
                variant="secondary"
                fullWidth
                value={String(editMaster.standard_quantity ?? 1)}
                onChange={(e) =>
                  setEditMaster({ ...editMaster, standard_quantity: Number(e.target.value) })
                }
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Default Pack Rate (₹)</Label>
              <Input
                type="number"
                step="0.01"
                variant="secondary"
                fullWidth
                value={String(editMaster.default_price ?? 0)}
                onChange={(e) =>
                  setEditMaster({ ...editMaster, default_price: Number(e.target.value) })
                }
              />
            </div>
          </div>

          <Switch
            isSelected={editMaster.is_active !== false}
            onChange={(v) => setEditMaster({ ...editMaster, is_active: v })}
            size="sm"
          >
            <Switch.Content>
              <Switch.Control>
                <Switch.Thumb />
              </Switch.Control>
              <Label className="text-xs font-medium">Active Ingredient</Label>
            </Switch.Content>
          </Switch>

          <Button type="submit" variant="primary" fullWidth>
            Save Master SKU
          </Button>
        </form>
      </AdminModal>
    </div>
  );
}
