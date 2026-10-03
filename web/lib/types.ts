export interface MenuItem {
  id: number;
  name: string;
  category: string;
  price: number;
  description?: string;
  is_available: boolean;
}

export interface Order {
  order_id: string;
  customer_name: string;
  contact_number: string;
  total_amount: number;
  timestamp: number;
  is_made: boolean;
  is_paid: boolean;
  is_delivered: boolean;
  sync_status: string;
  notes: string;
  payment_mode: string; // 'CASH' | 'UPI' | 'UNPAID'
  completed_timestamp: number;
}

export interface OrderItem {
  id?: number;
  order_id: string;
  menu_item_id: number;
  item_name: string;
  item_price: number;
  quantity: number;
  subtotal: number;
  completed_quantity: number;
  completed_timestamp: number;
}

export interface OrderWithItems {
  order: Order;
  items: OrderItem[];
}

export interface CartItem {
  item: MenuItem;
  quantity: number;
  notes?: string;
}

export interface UpiAccount {
  id?: number;
  vpa: string;
  payee_name: string;
  is_default: boolean;
  note?: string;
}
