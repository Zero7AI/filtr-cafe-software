'use client';

import React, { useEffect, useState, useMemo, useCallback } from 'react';
import {
  Plus,
  Edit2,
  Trash2,
  ArrowUp,
  ArrowDown,
  RefreshCw,
  UtensilsCrossed,
  BookOpen,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import Modal from '@/components/admin/Modal';
import { useToast } from '@/components/admin/ToastContext';
import { calculatePacksAndCost } from '@/lib/domain/inventory';
import { formatInr } from '@/lib/domain/ist';
import { InventoryItem, InventoryUsage, MenuItem } from '@/lib/domain/types';

export default function AdminMenuPage() {
  const { isDark, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [tab, setTab] = useState<'MENU' | 'RECIPES'>('MENU');
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [usages, setUsages] = useState<InventoryUsage[]>([]);
  const [inventoryItems, setInventoryItems] = useState<InventoryItem[]>([]);
  const [itemOrder, setItemOrder] = useState<string[]>([]);
  const [categoryFilter, setCategoryFilter] = useState('ALL');
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

  const categories = useMemo(() => {
    const s = new Set(menuItems.map((m) => m.category || 'General'));
    return ['ALL', ...Array.from(s).sort()];
  }, [menuItems]);

  const visibleMenu = useMemo(() => {
    if (categoryFilter === 'ALL') return sortedMenu;
    return sortedMenu.filter((m) => m.category === categoryFilter);
  }, [sortedMenu, categoryFilter]);

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

  const toggleAvailability = async (item: MenuItem) => {
    const res = await fetch('/api/admin/menu', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'toggleAvailability',
        id: item.id,
        is_available: !item.is_available,
      }),
    });
    if (res.ok) {
      pushToast(`${item.name}: ${!item.is_available ? 'Available' : 'Disabled'}`, 'success');
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
    pushToast(d.message || (d.success ? 'Synced to Sheets' : 'Sheets sync failed'), d.success ? 'success' : 'error');
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
      if (inv) {
        total += calculatePacksAndCost(r.quantity_used, r.unit, inv).cost;
      }
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

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setTab('MENU')}
            className={`flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-black border ${
              tab === 'MENU'
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark
                  ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                  : 'bg-white border-zinc-200 text-zinc-600'
            }`}
          >
            <UtensilsCrossed className="h-3.5 w-3.5" />
            <span>Menu Master ({menuItems.length})</span>
          </button>
          <button
            type="button"
            onClick={() => setTab('RECIPES')}
            className={`flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-black border ${
              tab === 'RECIPES'
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark
                  ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                  : 'bg-white border-zinc-200 text-zinc-600'
            }`}
          >
            <BookOpen className="h-3.5 w-3.5" />
            <span>Recipe Master & COGS</span>
          </button>
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={syncSheets}
            className={`flex items-center gap-1.5 rounded-xl border px-3 py-2 text-xs font-bold ${
              isDark ? 'border-zinc-800 bg-zinc-900 text-zinc-200' : 'border-zinc-200 bg-white'
            }`}
          >
            <RefreshCw className="h-3.5 w-3.5 text-emerald-500" />
            <span>Sync to Sheets</span>
          </button>
          <button
            type="button"
            onClick={() => openModal()}
            className="flex items-center gap-1.5 rounded-xl bg-amber-500 px-3.5 py-2 text-xs font-black text-zinc-950"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Menu Item</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading menu...</div>
      ) : tab === 'MENU' ? (
        <div className="space-y-3">
          <div className="flex flex-wrap gap-1.5">
            {categories.map((cat) => (
              <button
                key={cat}
                type="button"
                onClick={() => setCategoryFilter(cat)}
                className={`rounded-xl px-3 py-1.5 text-xs font-bold border ${
                  categoryFilter === cat
                    ? 'bg-amber-500 text-zinc-950 border-amber-500'
                    : isDark
                      ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                      : 'bg-white border-zinc-200 text-zinc-600'
                }`}
              >
                {cat}
              </button>
            ))}
          </div>

          <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
            <div className="overflow-x-auto">
              <table className="w-full text-xs text-left">
                <thead>
                  <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[10px] uppercase text-zinc-500">
                    <th className="py-3 px-4">Order</th>
                    <th className="py-3 px-4">Item Name</th>
                    <th className="py-3 px-4">Category</th>
                    <th className="py-3 px-4">Price</th>
                    <th className="py-3 px-4">Status</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
                  {visibleMenu.map((item) => (
                    <tr key={item.id}>
                      <td className="py-2.5 px-4">
                        <div className="flex items-center gap-1">
                          <button
                            type="button"
                            onClick={() => moveItem(item.name, -1)}
                            className="p-1 rounded hover:bg-zinc-800 text-zinc-400"
                          >
                            <ArrowUp className="h-3.5 w-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={() => moveItem(item.name, 1)}
                            className="p-1 rounded hover:bg-zinc-800 text-zinc-400"
                          >
                            <ArrowDown className="h-3.5 w-3.5" />
                          </button>
                        </div>
                      </td>
                      <td className="py-2.5 px-4">
                        <p className="font-bold">{item.name}</p>
                        {item.description && (
                          <p className="text-[10px] text-zinc-400">{item.description}</p>
                        )}
                      </td>
                      <td className="py-2.5 px-4 font-semibold text-amber-500">
                        {item.category}
                      </td>
                      <td className="py-2.5 px-4 font-black">{formatInr(item.price)}</td>
                      <td className="py-2.5 px-4">
                        <button
                          type="button"
                          onClick={() => toggleAvailability(item)}
                          className={`rounded-full px-2.5 py-0.5 text-[10px] font-black ${
                            item.is_available
                              ? 'bg-emerald-500/15 text-emerald-500'
                              : 'bg-zinc-700 text-zinc-400'
                          }`}
                        >
                          {item.is_available ? 'Available' : 'Unavailable'}
                        </button>
                      </td>
                      <td className="py-2.5 px-4 text-right space-x-1">
                        <button
                          type="button"
                          onClick={() => openModal(item)}
                          className="p-1.5 text-zinc-400 hover:text-amber-500"
                        >
                          <Edit2 className="h-3.5 w-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => deleteItem(item)}
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
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div className={`rounded-2xl border p-4 space-y-2 max-h-[70vh] overflow-y-auto ${cardSurface}`}>
            <h3 className="text-xs font-black uppercase text-zinc-400 mb-2">
              Select Menu Item
            </h3>
            {sortedMenu.map((m) => (
              <button
                key={m.id}
                type="button"
                onClick={() => setSelectedMenuId(m.id)}
                className={`w-full text-left rounded-xl border p-3 text-xs transition ${
                  selectedMenuId === m.id
                    ? 'border-amber-500 bg-amber-500/15 font-bold'
                    : isDark
                      ? 'border-zinc-800 bg-zinc-950/50 hover:bg-zinc-800'
                      : 'border-zinc-200 bg-zinc-50 hover:bg-zinc-100'
                }`}
              >
                <div className="flex justify-between">
                  <span>{m.name}</span>
                  <span className="text-amber-500 font-black">{formatInr(m.price)}</span>
                </div>
                <span className="text-[10px] text-zinc-400">{m.category}</span>
              </button>
            ))}
          </div>

          <div className={`lg:col-span-2 rounded-2xl border p-5 space-y-4 ${cardSurface}`}>
            {selectedMenuItem ? (
              <>
                <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-zinc-200 dark:border-zinc-800">
                  <div>
                    <h3 className="text-base font-black">{selectedMenuItem.name}</h3>
                    <p className="text-xs text-zinc-400">
                      Selling Price: {formatInr(selectedMenuItem.price)} • Recipe COGS:{' '}
                      <strong className="text-amber-500">
                        {formatInr(selectedRecipeCogs, 2)}
                      </strong>{' '}
                      • Margin:{' '}
                      <strong className="text-emerald-500">
                        {selectedMenuItem.price > 0
                          ? (
                              ((selectedMenuItem.price - selectedRecipeCogs) /
                                selectedMenuItem.price) *
                              100
                            ).toFixed(1)
                          : '0.0'}
                        %
                      </strong>
                    </p>
                  </div>
                </div>

                <div className="space-y-2">
                  {selectedMenuRecipes.length === 0 ? (
                    <p className="text-xs text-zinc-500">
                      No recipe ingredients linked yet (uses 35% COGS fallback in P&L).
                    </p>
                  ) : (
                    selectedMenuRecipes.map((r) => {
                      const inv = inventoryItems.find(
                        (i) => i.id === r.inventory_item_id
                      );
                      const cost = inv
                        ? calculatePacksAndCost(r.quantity_used, r.unit, inv).cost
                        : 0;
                      return (
                        <div
                          key={r.id}
                          className={`flex items-center justify-between rounded-xl border px-3 py-2 text-xs ${
                            isDark
                              ? 'border-zinc-800 bg-zinc-950/50'
                              : 'border-zinc-200 bg-zinc-50'
                          }`}
                        >
                          <div>
                            <span className="font-bold">{r.inventory_item_name}</span>
                            <span className="text-zinc-400 ml-2">
                              {r.quantity_used} {r.unit}
                            </span>
                          </div>
                          <div className="flex items-center gap-3">
                            <span className="font-bold text-amber-500">
                              {formatInr(cost, 2)}
                            </span>
                            <button
                              type="button"
                              onClick={() => deleteRecipeRow(r.id)}
                              className="text-rose-400 hover:text-rose-300"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>

                <form
                  onSubmit={addRecipeIngredient}
                  className="pt-3 border-t border-zinc-200 dark:border-zinc-800 grid grid-cols-1 sm:grid-cols-4 gap-2"
                >
                  <select
                    value={recipeInvId}
                    onChange={(e) => setRecipeInvId(Number(e.target.value))}
                    className="sm:col-span-2 rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
                  >
                    {inventoryItems.map((inv) => (
                      <option key={inv.id} value={inv.id}>
                        {inv.name} ({inv.unit})
                      </option>
                    ))}
                  </select>
                  <div className="flex gap-1.5">
                    <input
                      type="number"
                      step="0.01"
                      value={recipeQty}
                      onChange={(e) => setRecipeQty(e.target.value)}
                      placeholder="Qty"
                      required
                      className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-2.5 py-2 text-xs text-white"
                    />
                    <select
                      value={recipeUnit}
                      onChange={(e) => setRecipeUnit(e.target.value)}
                      className="rounded-xl border border-zinc-700 bg-zinc-900 px-2 py-2 text-xs text-white"
                    >
                      {['g', 'ml', 'kg', 'L', 'pcs', 'pack'].map((u) => (
                        <option key={u} value={u}>
                          {u}
                        </option>
                      ))}
                    </select>
                  </div>
                  <button
                    type="submit"
                    className="rounded-xl bg-amber-500 py-2 text-xs font-black text-zinc-950"
                  >
                    + Add Ingredient
                  </button>
                </form>
              </>
            ) : null}
          </div>
        </div>
      )}

      <Modal
        open={itemModal}
        onClose={() => setItemModal(false)}
        title={editId ? 'Edit Menu Item' : 'New Menu Item'}
        isDark={isDark}
      >
        <form onSubmit={saveMenuItem} className="space-y-3">
          <div>
            <label className="block text-xs font-bold mb-1">Item Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold mb-1">Category</label>
              <input
                type="text"
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                required
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Price (₹)</label>
              <input
                type="number"
                value={price}
                onChange={(e) => setPrice(e.target.value)}
                required
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white"
              />
            </div>
          </div>
          <div>
            <label className="block text-xs font-bold mb-1">Description</label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-sm text-white"
            />
          </div>
          <label className="flex items-center gap-2 text-xs font-bold">
            <input
              type="checkbox"
              checked={isAvailable}
              onChange={(e) => setIsAvailable(e.target.checked)}
            />
            Available on Counter POS
          </label>
          <button
            type="submit"
            className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
          >
            Save Menu Item
          </button>
        </form>
      </Modal>
    </div>
  );
}
