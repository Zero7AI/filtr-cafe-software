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
  ArrowDown,
  ArrowUp,
  Edit2,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-react';
import AdminModal from '@/components/admin/AdminModal';
import { useAdminContext } from '@/components/admin/AdminShell';
import { useToast } from '@/components/admin/ToastContext';
import { calculatePacksAndCost } from '@/lib/domain/inventory';
import { formatInr } from '@/lib/domain/ist';
import { InventoryItem, InventoryUsage, MenuItem } from '@/lib/domain/types';

export default function AdminMenuPage() {
  const { refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [tab, setTab] = useState<'MENU' | 'RECIPES'>('MENU');
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [usages, setUsages] = useState<InventoryUsage[]>([]);
  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [itemOrder, setItemOrder] = useState<string[]>([]);
  const [categoryFilter, setCategoryFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);

  const [itemModal, setItemModal] = useState(false);
  const [editId, setEditId] = useState(0);
  const [name, setName] = useState('');
  const [category, setCategory] = useState('');
  const [price, setPrice] = useState('');
  const [description, setDescription] = useState('');
  const [isAvailable, setIsAvailable] = useState(true);

  const [selectedMenuId, setSelectedMenuId] = useState<number>(0);
  const [recipeInvId, setRecipeInvId] = useState<number>(0);
  const [recipeQty, setRecipeQty] = useState('');
  const [recipeUnit, setRecipeUnit] = useState('g');

  const loadData = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/menu', { cache: 'no-store' });
      if (res.ok) {
        const d = await res.json();
        const mItems: MenuItem[] = d.menuItems || [];
        setMenuItems(mItems);
        setUsages(d.usages || []);
        const invList: InventoryItem[] = d.inventoryItems || [];
        setInventoryItems(invList);
        setItemOrder(d.itemOrder || []);
        setSelectedMenuId((prev) => prev || mItems[0]?.id || 0);
        setRecipeInvId((prev) => prev || invList[0]?.id || 0);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData, refreshTick]);

  const sortedMenu = useMemo(() => {
    const orderIdx = new Map(itemOrder.map((n, i) => [n.toLowerCase(), i]));
    return [...menuItems].sort((a, b) => {
      const ia = orderIdx.get(a.name.toLowerCase()) ?? 9999;
      const ib = orderIdx.get(b.name.toLowerCase()) ?? 9999;
      if (ia !== ib) return ia - ib;
      return a.name.localeCompare(b.name);
    });
  }, [menuItems, itemOrder]);

  const cogsByMenuId = useMemo(() => {
    const invById = new Map(inventoryItems.map((i) => [i.id, i]));
    const out = new Map<number, { hasRecipe: boolean; cogs: number }>();
    for (const m of menuItems) {
      const rows = usages.filter(
        (u) =>
          u.menu_item_id === m.id ||
          u.menu_item_name.trim().toLowerCase() === m.name.trim().toLowerCase()
      );
      if (rows.length === 0) {
        out.set(m.id, { hasRecipe: false, cogs: m.price * 0.35 });
      } else {
        let total = 0;
        for (const r of rows) {
          const inv = invById.get(r.inventory_item_id);
          if (inv) total += calculatePacksAndCost(r.quantity_used, r.unit, inv).cost;
        }
        out.set(m.id, { hasRecipe: true, cogs: total });
      }
    }
    return out;
  }, [menuItems, usages, inventoryItems]);

  const categories = useMemo(() => {
    const s = new Set(menuItems.map((m) => m.category || 'General'));
    return ['ALL', ...Array.from(s).sort()];
  }, [menuItems]);

  const visibleMenu = useMemo(() => {
    const q = search.trim().toLowerCase();
    return sortedMenu.filter((m) => {
      if (categoryFilter !== 'ALL' && m.category !== categoryFilter) return false;
      if (!q) return true;
      return (
        m.name.toLowerCase().includes(q) ||
        m.category.toLowerCase().includes(q)
      );
    });
  }, [sortedMenu, categoryFilter, search]);

  const openModal = (item?: MenuItem) => {
    if (item) {
      setEditId(item.id);
      setName(item.name);
      setCategory(item.category);
      setPrice(String(item.price));
      setDescription(item.description || '');
      setIsAvailable(item.is_available);
    } else {
      setEditId(0);
      setName('');
      setCategory('ICED COFFEE');
      setPrice('');
      setDescription('');
      setIsAvailable(true);
    }
    setItemModal(true);
  };

  const saveMenuItem = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'upsertMenuItem',
        id: editId,
        name,
        category,
        price: Number(price || 0),
        description,
        is_available: isAvailable,
      }),
    });
    if (res.ok) {
      pushToast(editId ? 'Updated menu item' : 'Added menu item', 'success');
      setItemModal(false);
      loadData();
    } else {
      pushToast('Failed to save item', 'error');
    }
  };

  const toggleAvailability = async (item: MenuItem, nextVal: boolean) => {
    const res = await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'toggleAvailability',
        id: item.id,
        is_available: nextVal,
      }),
    });
    if (res.ok) {
      pushToast(`${item.name}: ${nextVal ? 'Available' : 'Unavailable'}`, 'success');
      loadData();
    }
  };

  const deleteItem = async (item: MenuItem) => {
    if (!confirm(`Delete or disable "${item.name}"?`)) return;
    const res = await fetch(`/api/admin/menu?id=${item.id}`, { method: 'DELETE' });
    const d = await res.json();
    if (res.ok) {
      pushToast(d.message || `Removed ${item.name}`, 'info');
      loadData();
    }
  };

  const moveItem = async (itemName: string, delta: number) => {
    const names = sortedMenu.map((m) => m.name);
    const idx = names.indexOf(itemName);
    const nextIdx = idx + delta;
    if (idx < 0 || nextIdx < 0 || nextIdx >= names.length) return;
    const copy = [...names];
    const [removed] = copy.splice(idx, 1);
    copy.splice(nextIdx, 0, removed);
    setItemOrder(copy);
    await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'saveOrder', order: copy }),
    });
  };

  const syncSheets = async () => {
    const res = await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'syncSheets' }),
    });
    const d = await res.json();
    pushToast(
      d.message || (d.success ? 'Synced to Sheets' : 'Sheets sync failed'),
      d.success ? 'success' : 'error'
    );
  };

  const selectedMenuItem = useMemo(
    () => menuItems.find((m) => m.id === selectedMenuId),
    [menuItems, selectedMenuId]
  );

  const selectedMenuRecipes = useMemo(
    () =>
      usages.filter(
        (u) =>
          u.menu_item_id === selectedMenuId ||
          (selectedMenuItem &&
            u.menu_item_name.trim().toLowerCase() === selectedMenuItem.name.trim().toLowerCase())
      ),
    [usages, selectedMenuId, selectedMenuItem]
  );

  const selectedRecipeCogs = useMemo(() => {
    let total = 0;
    for (const r of selectedMenuRecipes) {
      const inv = inventoryItems.find((i) => i.id === r.inventory_item_id);
      if (inv) total += calculatePacksAndCost(r.quantity_used, r.unit, inv).cost;
    }
    return total;
  }, [selectedMenuRecipes, inventoryItems]);

  const addRecipeIngredient = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedMenuItem || !recipeInvId) return;
    const inv = inventoryItems.find((i) => i.id === recipeInvId);
    if (!inv) return;
    const res = await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'upsertRecipeRow',
        menu_item_id: selectedMenuItem.id,
        menu_item_name: selectedMenuItem.name,
        inventory_item_id: inv.id,
        inventory_item_name: inv.name,
        quantity_used: Number(recipeQty || 1),
        unit: recipeUnit,
      }),
    });
    if (res.ok) {
      pushToast('Added ingredient to recipe', 'success');
      setRecipeQty('');
      loadData();
    }
  };

  const deleteRecipeRow = async (id: number) => {
    const res = await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'deleteRecipeRow', id }),
    });
    if (res.ok) {
      pushToast('Removed ingredient', 'success');
      loadData();
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs
          selectedKey={tab}
          onSelectionChange={(k) => setTab(String(k) as typeof tab)}
          variant="secondary"
          className="w-auto"
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Menu management sections">
              <Tabs.Tab id="MENU" className="text-xs px-3.5">
                Menu Master ({menuItems.length})
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="RECIPES" className="text-xs px-3.5">
                Recipe Master & COGS
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        <div className="flex flex-wrap items-center gap-2">
          <SearchField
            value={search}
            onChange={setSearch}
            variant="secondary"
            className="w-full sm:w-64"
          >
            <SearchField.Group>
              <SearchField.SearchIcon />
              <SearchField.Input placeholder="Search menu item..." />
              <SearchField.ClearButton />
            </SearchField.Group>
          </SearchField>
          <Button size="sm" variant="outline" onPress={syncSheets}>
            <RefreshCw className="h-3.5 w-3.5 text-success" />
            <span>Sync Sheets</span>
          </Button>
          <Button size="sm" variant="primary" onPress={() => openModal()}>
            <Plus className="h-3.5 w-3.5" />
            <span>Add Menu Item</span>
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-16 rounded-2xl" />
          ))}
        </div>
      ) : tab === 'MENU' ? (
        <div className="space-y-4">
          <div className="flex flex-wrap gap-1.5">
            {categories.map((cat) => (
              <Button
                key={cat}
                size="sm"
                variant={categoryFilter === cat ? 'primary' : 'outline'}
                onPress={() => setCategoryFilter(cat)}
              >
                {cat}
              </Button>
            ))}
          </div>

          <Card className="border border-border/80">
            <Card.Content className="p-3">
              <Table variant="secondary">
                <Table.ScrollContainer>
                  <Table.Content aria-label="Menu master table">
                    <Table.Header>
                      <Table.Column isRowHeader>Item Name</Table.Column>
                      <Table.Column>Order</Table.Column>
                      <Table.Column>Category</Table.Column>
                      <Table.Column className="text-right">Price</Table.Column>
                      <Table.Column className="text-right">Recipe COGS</Table.Column>
                      <Table.Column className="text-right">Gross Margin</Table.Column>
                      <Table.Column>POS Active</Table.Column>
                      <Table.Column className="text-right">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body>
                      {visibleMenu.map((item) => {
                        const info = cogsByMenuId.get(item.id) || {
                          hasRecipe: false,
                          cogs: item.price * 0.35,
                        };
                        const marginPct =
                          item.price > 0
                            ? ((item.price - info.cogs) / item.price) * 100
                            : 0;
                        return (
                          <Table.Row key={item.id} id={String(item.id)}>
                            <Table.Cell>
                              <p className="font-semibold">{item.name}</p>
                              {item.description ? (
                                <p className="text-[11px] text-muted">{item.description}</p>
                              ) : null}
                            </Table.Cell>
                            <Table.Cell>
                              <div className="inline-flex items-center gap-0.5">
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="ghost"
                                  onPress={() => moveItem(item.name, -1)}
                                  aria-label={`Move ${item.name} up`}
                                >
                                  <ArrowUp className="h-3.5 w-3.5" />
                                </Button>
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="ghost"
                                  onPress={() => moveItem(item.name, 1)}
                                  aria-label={`Move ${item.name} down`}
                                >
                                  <ArrowDown className="h-3.5 w-3.5" />
                                </Button>
                              </div>
                            </Table.Cell>
                            <Table.Cell>
                              <Chip size="sm" variant="soft" color="accent">
                                {item.category}
                              </Chip>
                            </Table.Cell>
                            <Table.Cell className="text-right font-bold tabular-nums">
                              {formatInr(item.price)}
                            </Table.Cell>
                            <Table.Cell className="text-right tabular-nums text-xs">
                              {formatInr(info.cogs, 2)}
                              {!info.hasRecipe ? (
                                <span className="ml-1 text-[10px] text-warning">(35% est)</span>
                              ) : null}
                            </Table.Cell>
                            <Table.Cell className="text-right tabular-nums">
                              <Chip
                                size="sm"
                                variant="soft"
                                color={marginPct >= 65 ? 'success' : marginPct >= 50 ? 'warning' : 'danger'}
                              >
                                {marginPct.toFixed(1)}%
                              </Chip>
                            </Table.Cell>
                            <Table.Cell>
                              <Switch
                                isSelected={item.is_available}
                                onChange={(val) => toggleAvailability(item, val)}
                                size="sm"
                                aria-label={`Toggle ${item.name} availability`}
                              >
                                <Switch.Content>
                                  <Switch.Control>
                                    <Switch.Thumb />
                                  </Switch.Control>
                                </Switch.Content>
                              </Switch>
                            </Table.Cell>
                            <Table.Cell className="text-right whitespace-nowrap">
                              <div className="inline-flex items-center gap-1">
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="ghost"
                                  onPress={() => openModal(item)}
                                  aria-label={`Edit ${item.name}`}
                                >
                                  <Edit2 className="h-3.5 w-3.5" />
                                </Button>
                                <Button
                                  isIconOnly
                                  size="sm"
                                  variant="danger-soft"
                                  onPress={() => deleteItem(item)}
                                  aria-label={`Delete or disable ${item.name}`}
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
        </div>
      ) : (
        <div className="grid grid-cols-1 xl:grid-cols-12 gap-5">
          <Card className="xl:col-span-4 border border-border/80">
            <Card.Header className="px-4 pt-4 pb-2">
              <Card.Title className="text-xs font-bold uppercase text-muted">
                Select Menu Item ({visibleMenu.length})
              </Card.Title>
            </Card.Header>
            <Card.Content className="px-3 pb-3 max-h-[70vh] overflow-y-auto space-y-1.5">
              {visibleMenu.map((m) => {
                const info = cogsByMenuId.get(m.id);
                const selected = selectedMenuId === m.id;
                return (
                  <button
                    key={m.id}
                    type="button"
                    onClick={() => setSelectedMenuId(m.id)}
                    className={`w-full text-left rounded-xl border p-3 text-xs transition ${
                      selected
                        ? 'border-accent bg-accent-soft font-semibold'
                        : 'border-separator bg-surface-secondary/50 hover:bg-surface-secondary'
                    }`}
                  >
                    <div className="flex justify-between items-center gap-2">
                      <span className="truncate">{m.name}</span>
                      <span className="text-accent font-bold tabular-nums shrink-0">
                        {formatInr(m.price)}
                      </span>
                    </div>
                    <div className="flex items-center justify-between text-[11px] text-muted mt-1">
                      <span>{m.category}</span>
                      <span>
                        {info?.hasRecipe ? `COGS: ${formatInr(info.cogs, 2)}` : 'No recipe (35% est)'}
                      </span>
                    </div>
                  </button>
                );
              })}
            </Card.Content>
          </Card>

          <Card className="xl:col-span-8 border border-border/80">
            <Card.Content className="p-5 space-y-5">
              {selectedMenuItem ? (
                <>
                  <div className="flex flex-wrap items-center justify-between gap-3 pb-4 border-b border-separator">
                    <div>
                      <h3 className="text-lg font-bold">{selectedMenuItem.name}</h3>
                      <p className="text-xs text-muted">
                        Category: {selectedMenuItem.category}
                      </p>
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-xs tabular-nums">
                      <Chip size="sm" variant="soft" color="default">
                        Price: {formatInr(selectedMenuItem.price)}
                      </Chip>
                      <Chip size="sm" variant="soft" color="accent">
                        COGS: {formatInr(selectedRecipeCogs, 2)}
                      </Chip>
                      <Chip size="sm" variant="soft" color="success">
                        Margin:{' '}
                        {selectedMenuItem.price > 0
                          ? (
                              ((selectedMenuItem.price - selectedRecipeCogs) /
                                selectedMenuItem.price) *
                              100
                            ).toFixed(1)
                          : '0.0'}
                        %
                      </Chip>
                    </div>
                  </div>

                  <div className="space-y-2">
                    {selectedMenuRecipes.length === 0 ? (
                      <p className="text-xs text-muted py-4">
                        No recipe ingredients linked yet (uses 35% COGS fallback in P&L).
                      </p>
                    ) : (
                      selectedMenuRecipes.map((r) => {
                        const inv = inventoryItems.find((i) => i.id === r.inventory_item_id);
                        const cost = inv
                          ? calculatePacksAndCost(r.quantity_used, r.unit, inv).cost
                          : 0;
                        return (
                          <div
                            key={r.id}
                            className="flex items-center justify-between rounded-xl border border-separator bg-surface-secondary/50 px-3.5 py-2.5 text-xs"
                          >
                            <div>
                              <span className="font-semibold">{r.inventory_item_name}</span>
                              <span className="text-muted ml-2 tabular-nums">
                                {r.quantity_used} {r.unit}
                              </span>
                            </div>
                            <div className="flex items-center gap-3">
                              <span className="font-bold text-accent tabular-nums">
                                {formatInr(cost, 2)}
                              </span>
                              <Button
                                isIconOnly
                                size="sm"
                                variant="danger-soft"
                                onPress={() => deleteRecipeRow(r.id)}
                                aria-label="Remove recipe ingredient"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </Button>
                            </div>
                          </div>
                        );
                      })
                    )}
                  </div>

                  <form
                    onSubmit={addRecipeIngredient}
                    className="pt-4 border-t border-separator grid grid-cols-1 sm:grid-cols-4 gap-2"
                  >
                    <select
                      value={recipeInvId}
                      onChange={(e) => setRecipeInvId(Number(e.target.value))}
                      className="sm:col-span-2 rounded-xl border border-border bg-surface-secondary px-3 py-2 text-xs text-foreground"
                    >
                      {inventoryItems.map((inv) => (
                        <option key={inv.id} value={inv.id}>
                          {inv.name} ({inv.unit})
                        </option>
                      ))}
                    </select>
                    <div className="flex gap-1.5">
                      <Input
                        type="number"
                        step="0.01"
                        variant="secondary"
                        value={recipeQty}
                        onChange={(e) => setRecipeQty(e.target.value)}
                        placeholder="Qty"
                        required
                        className="text-xs"
                      />
                      <select
                        value={recipeUnit}
                        onChange={(e) => setRecipeUnit(e.target.value)}
                        className="rounded-xl border border-border bg-surface-secondary px-2 py-2 text-xs text-foreground"
                      >
                        {['g', 'ml', 'kg', 'L', 'pcs', 'pack'].map((u) => (
                          <option key={u} value={u}>
                            {u}
                          </option>
                        ))}
                      </select>
                    </div>
                    <Button type="submit" variant="primary" size="sm">
                      + Add Ingredient
                    </Button>
                  </form>
                </>
              ) : null}
            </Card.Content>
          </Card>
        </div>
      )}

      <AdminModal
        isOpen={itemModal}
        onClose={() => setItemModal(false)}
        title={editId ? 'Edit Menu Item' : 'New Menu Item'}
      >
        <form onSubmit={saveMenuItem} className="space-y-3">
          <TextField value={name} onChange={setName} isRequired fullWidth>
            <Label className="text-xs font-medium">Item Name</Label>
            <Input variant="secondary" />
          </TextField>

          <div className="grid grid-cols-2 gap-3">
            <TextField value={category} onChange={setCategory} isRequired fullWidth>
              <Label className="text-xs font-medium">Category</Label>
              <Input variant="secondary" />
            </TextField>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Price (₹)</Label>
              <Input
                type="number"
                variant="secondary"
                fullWidth
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                required
              />
            </div>
          </div>

          <TextField value={description} onChange={setDescription} fullWidth>
            <Label className="text-xs font-medium">Description</Label>
            <Input variant="secondary" />
          </TextField>

          <Switch isSelected={isAvailable} onChange={setIsAvailable} size="sm">
            <Switch.Content>
              <Switch.Control>
                <Switch.Thumb />
              </Switch.Control>
              <Label className="text-xs font-medium">Available on Counter POS</Label>
            </Switch.Content>
          </Switch>

          <Button type="submit" variant="primary" fullWidth>
            Save Menu Item
          </Button>
        </form>
      </AdminModal>
    </div>
  );
}
