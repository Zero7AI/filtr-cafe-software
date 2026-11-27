import { MenuItem, Order, OrderItem, OrderWithItems, ReceiptDesignConfig, UpiAccount } from './types';

// Re-export ReceiptDesignConfig & defaultReceiptConfig for existing POS components
export type { ReceiptDesignConfig };

export const defaultReceiptConfig: ReceiptDesignConfig = {
  tagline: 'We are working for you <3',
  headerSubtitle: 'where coffee meets vibe',
  footerLine1: 'Thank you for hanging out with us!',
  footerLine2: 'Tag your cup on Instagram @filtr.coffeee',
  footerLine3: 'Made fresh - Crafted with obsession',
  isStoryEnabled: true,
};

// Format date to YYYYMMDD in IST
export function getTodayDatePart(): string {
  const now = new Date();
  const istOffset = 5.5 * 60 * 60 * 1000;
  const istDate = new Date(now.getTime() + istOffset);
  const year = istDate.getUTCFullYear();
  const month = String(istDate.getUTCMonth() + 1).padStart(2, '0');
  const day = String(istDate.getUTCDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}

export async function fetchMenuItems(): Promise<MenuItem[]> {
  try {
    const res = await fetch('/api/pos?action=menu', { cache: 'no-store' });
    if (!res.ok) return [];
    const data = await res.json();
    return data.items || [];
  } catch (err) {
    console.error('Error fetching menu items:', err);
    return [];
  }
}

export async function getNextOrderId(): Promise<string> {
  const datePart = getTodayDatePart();
  const prefix = `FLT-${datePart}-`;
  try {
    const res = await fetch('/api/pos?action=nextOrderId', { cache: 'no-store' });
    if (!res.ok) return `${prefix}1001`;
    const data = await res.json();
    return data.orderId || `${prefix}1001`;
  } catch (err) {
    console.error('Error computing next order ID:', err);
    return `${prefix}${Date.now().toString().slice(-4)}`;
  }
}

export async function createOrder(
  order: Order,
  items: OrderItem[]
): Promise<{ success: boolean; error?: string }> {
  try {
    const res = await fetch('/api/pos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'createOrder', order, items }),
    });
    const data = await res.json();
    return { success: Boolean(data.success), error: data.error };
  } catch (err: unknown) {
    return {
      success: false,
      error: err instanceof Error ? err.message : 'Network error',
    };
  }
}

export async function fetchOrders(
  filter: 'UNCOMPLETED' | 'ALL' | 'COMPLETED' = 'UNCOMPLETED',
  limit: number = 200,
  onlyToday: boolean = true
): Promise<OrderWithItems[]> {
  try {
    const params = new URLSearchParams({
      action: 'orders',
      filter,
      limit: String(limit),
      onlyToday: String(onlyToday),
    });
    const res = await fetch(`/api/pos?${params.toString()}`, { cache: 'no-store' });
    if (!res.ok) return [];
    const data = await res.json();
    return data.orders || [];
  } catch (err) {
    console.error('fetchOrders error:', err);
    return [];
  }
}

export async function updateOrderStatus(
  orderId: string,
  updates: Partial<Order>
): Promise<boolean> {
  try {
    const res = await fetch('/api/pos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'updateOrderStatus', orderId, updates }),
    });
    const data = await res.json();
    return Boolean(data.success);
  } catch (err) {
    console.error('updateOrderStatus error:', err);
    return false;
  }
}

export async function deleteOrder(orderId: string): Promise<boolean> {
  try {
    const res = await fetch('/api/pos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'deleteOrder', orderId }),
    });
    const data = await res.json();
    return Boolean(data.success);
  } catch (err) {
    console.error('deleteOrder error:', err);
    return false;
  }
}

export async function fetchReceiptConfig(): Promise<ReceiptDesignConfig> {
  try {
    const res = await fetch('/api/pos?action=receiptConfig', { cache: 'no-store' });
    if (!res.ok) return defaultReceiptConfig;
    const data = await res.json();
    return data.config || defaultReceiptConfig;
  } catch (err) {
    console.error('fetchReceiptConfig error:', err);
    return defaultReceiptConfig;
  }
}

export async function fetchUpiAccounts(): Promise<UpiAccount[]> {
  try {
    const res = await fetch('/api/pos?action=upiAccounts', { cache: 'no-store' });
    if (!res.ok) {
      return [
        {
          vpa: '9907839311@ybl',
          payee_name: 'FILTR Coffee Store',
          is_default: true,
          note: 'FILTR Coffee Payment',
        },
      ];
    }
    const data = await res.json();
    return data.accounts || [];
  } catch (err) {
    console.error('fetchUpiAccounts error:', err);
    return [
      {
        vpa: '9907839311@ybl',
        payee_name: 'FILTR Coffee Store',
        is_default: true,
        note: 'FILTR Coffee Payment',
      },
    ];
  }
}

export async function updateOrderItemCompletion(
  itemId: number,
  completedQty: number
): Promise<boolean> {
  try {
    const res = await fetch('/api/pos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'updateOrderItemCompletion',
        itemId,
        completedQty,
      }),
    });
    const data = await res.json();
    return Boolean(data.success);
  } catch (err) {
    console.error('updateOrderItemCompletion error:', err);
    return false;
  }
}
