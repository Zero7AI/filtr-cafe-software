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

export interface Customer {
  id: number;
  name: string;
  contact_number: string;
  total_orders: number;
  total_spent: number;
  last_order_timestamp: number;
  created_at: number;
}

export interface InventoryItem {
  id: number;
  name: string;
  category: string;
  unit: string;
  standard_quantity: number;
  default_price: number;
  current_stock: number;
  is_active: boolean;
}

export interface InventoryOrder {
  order_id: string;
  timestamp: number;
  supplier_name: string;
  total_amount: number;
  notes: string;
  sync_status: string;
}

export interface InventoryOrderItem {
  id?: number;
  order_id: string;
  item_name: string;
  quantity: number;
  unit: string;
  unit_price: number;
  subtotal: number;
}

export interface InventoryOrderWithItems {
  order: InventoryOrder;
  items: InventoryOrderItem[];
}

export interface InventoryUsage {
  id: number;
  menu_item_id: number;
  menu_item_name: string;
  inventory_item_id: number;
  inventory_item_name: string;
  quantity_used: number;
  unit: string;
}

export interface WhatsappMessage {
  id: number;
  message_id: string;
  from_number: string;
  message_type: string;
  message_body: string;
  parsed_result: unknown;
  inventory_order_id: string | null;
  status: string;
  error_message: string | null;
  created_at: string;
}

export interface ReceiptDesignConfig {
  tagline: string;
  headerSubtitle: string;
  footerLine1: string;
  footerLine2: string;
  footerLine3: string;
  isStoryEnabled: boolean;
  metadataMap?: Record<string, unknown>;
}

export interface AccessPolicyConfig {
  admin_passcode: string;
  staff_passcode: string;
  passcode_epoch: number;
  require_owner_approval_for_admin: boolean;
}

export interface DeviceRegistration {
  registryKey: string;
  device_id: string;
  app_role: 'ADMIN' | 'STAFF';
  operator_name: string;
  device_model: string;
  android_version: string;
  app_version_name: string;
  app_version_code: number;
  status: 'ACTIVE' | 'PENDING' | 'REVOKED';
  passcode_epoch: number;
  first_seen_ms: number;
  last_seen_ms: number;
  role_updated_at_ms: number;
  linked_member_id: string;
  linked_member_name: string;
  member_link_updated_at_ms: number;
}

export interface StaffRosterMember {
  id: string;
  name: string;
  roleTitle: string;
  shiftLabel: string;
  weeklyOffDayOfWeek: number; // 0=None, 1=Sun .. 7=Sat
  joinedDateKey: string;
  isActive: boolean;
  updatedAt: number;
  colorHex: string;
  orgRole: 'STAFF' | 'ADMIN';
}

export interface AttendanceLog {
  id: string;
  dateKey: string;
  monthKey: string;
  staffName: string;
  deviceId: string;
  checkInTimestamp: number;
  checkInLat: number;
  checkInLng: number;
  checkInDistanceMeters: number;
  checkOutTimestamp: number;
  checkOutLat: number;
  checkOutLng: number;
  checkOutDistanceMeters: number;
  workedMinutes: number;
  notes: string;
  updatedAt: number;
}

export interface StoreGeofenceConfig {
  latitude: number;
  longitude: number;
  radiusMeters: number;
  storeLabel: string;
  updatedAt: number;
}

export interface KdsItemClaim {
  key: string;
  orderId: string;
  itemId: number;
  itemName: string;
  unitIndex: number;
  status: 'PENDING' | 'MAKING' | 'MADE';
  staffName: string;
  colorHex: string;
  updatedAt: number;
}

export interface KdsQueueUnitItem {
  claimKey: string;
  queuePosition: number;
  orderId: string;
  shortOrderNumber: string;
  orderTimestamp: number;
  customerName: string;
  orderNotes: string;
  isPaid: boolean;
  paymentMode: string;
  itemId: number;
  itemName: string;
  unitIndex: number;
  totalUnits: number;
  stage: 'PENDING' | 'MAKING' | 'MADE';
  claimedByStaff: string;
  claimedColorHex: string;
}

export type KitchenSectionId =
  | 'DEEP_FREEZER'
  | 'REFRIGERATOR_FREEZER'
  | 'OUTSIDE_FREEZER'
  | 'NORMAL_TEMPERATURE';

export type KitchenCountingModeId =
  | 'SEALED_AND_PERCENTAGE'
  | 'SEALED_AND_GRAMS'
  | 'SEALED_AND_ML'
  | 'SEALED_AND_PIECES'
  | 'DIRECT_GRAMS'
  | 'DIRECT_ML'
  | 'DIRECT_PIECES';

export type KitchenStockStatus = 'GREEN' | 'ORANGE' | 'RED';

export interface KitchenInventoryItem {
  id: string;
  name: string;
  iconEmoji: string;
  sectionId: KitchenSectionId;
  countingMode: KitchenCountingModeId;
  unitLabel: string;
  containerLabel: string;
  packSize: number;
  minStockThreshold: number;
  targetStockLevel: number;
  orderIndex: number;
  isDefault: boolean;
  isActive: boolean;
  updatedAt: number;
}

export interface KitchenInventoryEntry {
  shiftId: string;
  dateKey: string;
  shiftType: 'OPENING' | 'CLOSING';
  itemId: string;
  itemNameSnapshot: string;
  sectionIdSnapshot: string;
  unitLabelSnapshot: string;
  countingModeSnapshot: string;
  packSizeSnapshot: number;
  sealedQty: number;
  openedQty: number;
  openedRemainingQty: number;
  totalUsableQty: number;
  wastageQty: number;
  wastageReason: string;
  stockStatus: KitchenStockStatus;
  isChecked: boolean;
  checkedBy: string;
  checkedAt: number;
}

export interface KitchenInventoryShift {
  id: string;
  dateKey: string;
  shiftType: 'OPENING' | 'CLOSING';
  status: 'IN_PROGRESS' | 'SUBMITTED';
  staffName: string;
  startedAt: number;
  submittedAt: number;
  lastUpdatedAt: number;
  notes: string;
  entries: KitchenInventoryEntry[];
}

export interface ChecklistTask {
  id: string;
  sectionId: string;
  sectionName: string;
  title: string;
  description: string;
  isDefault: boolean;
  orderIndex: number;
  isWeeklyMondayOnly: boolean;
  createdAt: number;
}

export interface ChecklistCompletion {
  taskId: string;
  isCompleted: boolean;
  completedTimestamp: number;
  updatedAt: number;
  completedBy: string;
  isOutOfStock: boolean;
}

export interface FixedCostStaffMember {
  id: string;
  name: string;
  salary: number;
}

export interface FixedCostRecord {
  monthKey: string;
  rent: number;
  electricity: number;
  staffSalaries: number;
  staffCount: number;
  maidSalary: number;
  miscellaneous: number;
  notes: string;
  staffMembers: FixedCostStaffMember[];
  updatedAt: number;
}

export interface AppUpdatePolicy {
  force_update: boolean;
  min_version_code: number;
  min_version_name: string;
  title: string;
  message: string;
  apk_download_url: string;
  apk_file_name: string;
  apk_size_bytes: number;
}
