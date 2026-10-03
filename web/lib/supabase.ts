import { createClient } from '@supabase/supabase-js';
import { MenuItem, Order, OrderItem, OrderWithItems, UpiAccount } from './types';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL || 'https://bpeqfynknwuaqfjjqmlh.supabase.co';
const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY || '';

export const supabase = createClient(supabaseUrl, supabaseKey);

// Fetch all available menu items
export async function fetchMenuItems(): Promise<MenuItem[]> {
  const { data, error } = await supabase
    .from('menu_items')
    .select('*')
    .eq('is_available', true)
    .order('category', { ascending: true })
    .order('name', { ascending: true });

  if (error) {
    console.error('Error fetching menu items:', error);
    return [];
  }
  return data || [];
}

// Format date to YYYYMMDD in IST
export function getTodayDatePart(): string {
  const now = new Date();
  // Adjust for Indian Standard Time (+05:30)
  const istOffset = 5.5 * 60 * 60 * 1000;
  const istDate = new Date(now.getTime() + istOffset);
  const year = istDate.getUTCFullYear();
  const month = String(istDate.getUTCMonth() + 1).padStart(2, '0');
  const day = String(istDate.getUTCDate()).padStart(2, '0');
  return `${year}${month}${day}`;
}

// Generate next order ID: FLT-YYYYMMDD-XXXX
export async function getNextOrderId(): Promise<string> {
  const datePart = getTodayDatePart();
  const prefix = `FLT-${datePart}-`;

  try {
    const { data, error } = await supabase
      .from('orders')
      .select('order_id')
      .like('order_id', `${prefix}%`)
      .order('order_id', { ascending: false })
      .limit(1);

    if (error || !data || data.length === 0) {
      return `${prefix}1001`;
    }

    const lastId = data[0].order_id;
    const seqStr = lastId.split('-').pop() || '1000';
    const lastSeq = parseInt(seqStr, 10);
    const nextSeq = isNaN(lastSeq) ? 1001 : lastSeq + 1;
    return `${prefix}${nextSeq}`;
  } catch (e) {
    console.error('Error computing next order ID:', e);
    return `${prefix}${Date.now().toString().slice(-4)}`;
  }
}

// Save a new order and its items
export async function createOrder(
  order: Order,
  items: OrderItem[]
): Promise<{ success: boolean; error?: string }> {
  try {
    // 1. Insert order
    const { error: orderError } = await supabase
      .from('orders')
      .insert({
        order_id: order.order_id,
        customer_name: order.customer_name || 'Walk-in',
        contact_number: order.contact_number || '',
        total_amount: order.total_amount,
        timestamp: order.timestamp,
        is_made: order.is_made,
        is_paid: order.is_paid,
        is_delivered: order.is_delivered,
        sync_status: 'SYNCED',
        notes: order.notes || '',
        payment_mode: order.payment_mode || 'CASH',
        completed_timestamp: order.completed_timestamp || 0,
      });

    if (orderError) {
      console.error('Error inserting order:', orderError);
      return { success: false, error: orderError.message };
    }

    // 2. Insert items
    if (items.length > 0) {
      const itemsPayload = items.map((item) => ({
        order_id: order.order_id,
        menu_item_id: item.menu_item_id,
        item_name: item.item_name,
        item_price: item.item_price,
        quantity: item.quantity,
        subtotal: item.subtotal,
        completed_quantity: item.completed_quantity || 0,
        completed_timestamp: item.completed_timestamp || 0,
      }));

      const { error: itemsError } = await supabase
        .from('order_items')
        .insert(itemsPayload);

      if (itemsError) {
        console.error('Error inserting order items:', itemsError);
        return { success: false, error: itemsError.message };
      }
    }

    return { success: true };
  } catch (err: any) {
    console.error('createOrder exception:', err);
    return { success: false, error: err.message };
  }
}

// Fetch orders with items. By default, orders for TODAY: onlyToday = true
export async function fetchOrders(
  filter: 'UNCOMPLETED' | 'ALL' | 'COMPLETED' = 'UNCOMPLETED',
  limit: number = 200,
  onlyToday: boolean = true
): Promise<OrderWithItems[]> {
  try {
    let query = supabase
      .from('orders')
      .select('*, order_items(*)')
      .order('timestamp', { ascending: false })
      .limit(limit);

    if (onlyToday) {
      const todayDate = getTodayDatePart();
      query = query.like('order_id', `FLT-${todayDate}-%`);
    }

    if (filter === 'UNCOMPLETED') {
      // Uncompleted means not delivered OR not made
      query = query.or('is_delivered.eq.false,is_made.eq.false');
    } else if (filter === 'COMPLETED') {
      query = query.eq('is_delivered', true).eq('is_made', true);
    }

    const { data, error } = await query;
    if (error) {
      console.error('fetchOrders error:', error);
      return [];
    }

    return (data || []).map((row: any) => ({
      order: {
        order_id: row.order_id,
        customer_name: row.customer_name || 'Walk-in',
        contact_number: row.contact_number || '',
        total_amount: row.total_amount || 0,
        timestamp: row.timestamp || 0,
        is_made: !!row.is_made,
        is_paid: !!row.is_paid,
        is_delivered: !!row.is_delivered,
        sync_status: row.sync_status || 'SYNCED',
        notes: row.notes || '',
        payment_mode: row.payment_mode || 'UNPAID',
        completed_timestamp: row.completed_timestamp || 0,
      },
      items: (row.order_items || []).map((it: any) => ({
        id: it.id,
        order_id: it.order_id,
        menu_item_id: it.menu_item_id,
        item_name: it.item_name,
        item_price: it.item_price,
        quantity: it.quantity,
        subtotal: it.subtotal,
        completed_quantity: it.completed_quantity || 0,
        completed_timestamp: it.completed_timestamp || 0,
      })),
    }));
  } catch (err) {
    console.error('fetchOrders exception:', err);
    return [];
  }
}

// Update order status fields
export async function updateOrderStatus(
  orderId: string,
  updates: Partial<Order>
): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('orders')
      .update(updates)
      .eq('order_id', orderId);

    if (error) {
      console.error('updateOrderStatus error:', error);
      return false;
    }
    return true;
  } catch (err) {
    console.error('updateOrderStatus exception:', err);
    return false;
  }
}

// Delete an order and its items
export async function deleteOrder(orderId: string): Promise<boolean> {
  try {
    await supabase.from('order_items').delete().eq('order_id', orderId);
    const { error } = await supabase.from('orders').delete().eq('order_id', orderId);
    return !error;
  } catch (err) {
    console.error('deleteOrder exception:', err);
    return false;
  }
}

// Receipt Design Config matching mobile app's ReceiptDesignScreen
export interface ReceiptDesignConfig {
  tagline: string;
  headerSubtitle: string;
  footerLine1: string;
  footerLine2: string;
  footerLine3: string;
  isStoryEnabled: boolean;
}

export const defaultReceiptConfig: ReceiptDesignConfig = {
  tagline: 'We are working for you <3',
  headerSubtitle: 'where coffee meets vibe',
  footerLine1: 'Thank you for hanging out with us!',
  footerLine2: 'Tag your cup on Instagram @filtr.coffeee',
  footerLine3: 'Made fresh - Crafted with obsession',
  isStoryEnabled: true,
};

// Fetch receipt design configuration from sync_metadata table (synced from mobile app)
export async function fetchReceiptConfig(): Promise<ReceiptDesignConfig> {
  try {
    const { data, error } = await supabase
      .from('sync_metadata')
      .select('value')
      .eq('key', 'receipt_design_config')
      .limit(1);

    if (error || !data || data.length === 0) {
      return defaultReceiptConfig;
    }

    const parsed = typeof data[0].value === 'string' ? JSON.parse(data[0].value) : data[0].value;
    return {
      tagline: parsed.tagline || defaultReceiptConfig.tagline,
      headerSubtitle: parsed.headerSubtitle || defaultReceiptConfig.headerSubtitle,
      footerLine1: parsed.footerLine1 || defaultReceiptConfig.footerLine1,
      footerLine2: parsed.footerLine2 || defaultReceiptConfig.footerLine2,
      footerLine3: parsed.footerLine3 || defaultReceiptConfig.footerLine3,
      isStoryEnabled: parsed.isStoryEnabled ?? defaultReceiptConfig.isStoryEnabled,
    };
  } catch (err) {
    console.error('fetchReceiptConfig error:', err);
    return defaultReceiptConfig;
  }
}

// Fetch configured UPI accounts for dynamic QR code generation
export async function fetchUpiAccounts(): Promise<UpiAccount[]> {
  try {
    const { data, error } = await supabase
      .from('upi_accounts')
      .select('*')
      .order('is_default', { ascending: false });

    if (error || !data || data.length === 0) {
      return [
        {
          vpa: '9907839311@ybl',
          payee_name: 'FILTR Coffee Store',
          is_default: true,
          note: 'FILTR Coffee Payment',
        },
      ];
    }

    return data.map((d: any) => ({
      id: d.id,
      vpa: d.vpa || '9907839311@ybl',
      payee_name: d.payee_name || 'FILTR Coffee Store',
      is_default: !!d.is_default,
      note: d.note || '',
    }));
  } catch (err) {
    console.error('fetchUpiAccounts exception:', err);
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

// Update single order item completion status in Supabase
export async function updateOrderItemCompletion(
  itemId: number,
  completedQty: number
): Promise<boolean> {
  try {
    const { error } = await supabase
      .from('order_items')
      .update({
        completed_quantity: completedQty,
        completed_timestamp: Date.now(),
      })
      .eq('id', itemId);

    return !error;
  } catch (err) {
    console.error('updateOrderItemCompletion exception:', err);
    return false;
  }
}

