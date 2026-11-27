import { createClient, SupabaseClient } from '@supabase/supabase-js';
import {
  AccessPolicyConfig,
  DeviceRegistration,
  OrderWithItems,
  ReceiptDesignConfig,
  StaffRosterMember,
} from './domain/types';

const BLOCKED_METADATA_KEYS = new Set(['deepgram_config', 'gemini_config']);

let cachedClient: SupabaseClient | null = null;

export function getSupabaseServerUrl(): string {
  return (
    process.env.SUPABASE_URL ||
    process.env.NEXT_PUBLIC_SUPABASE_URL ||
    'https://bpeqfynknwuaqfjjqmlh.supabase.co'
  );
}

export function getSupabaseServerKey(): string {
  return (
    process.env.SUPABASE_SERVICE_ROLE_KEY ||
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ||
    ''
  );
}

export function getSupabaseAdmin(): SupabaseClient {
  if (cachedClient) return cachedClient;
  const url = getSupabaseServerUrl();
  const key = getSupabaseServerKey();
  if (!url || !key) {
    throw new Error('Supabase server environment variables are not configured.');
  }
  cachedClient = createClient(url, key, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });
  return cachedClient;
}

export function isBlockedMetadataKey(key: string): boolean {
  return BLOCKED_METADATA_KEYS.has(key.trim().toLowerCase());
}

export async function getSyncMetadata(key: string): Promise<string | null> {
  if (isBlockedMetadataKey(key)) return null;
  const supabase = getSupabaseAdmin();
  const { data, error } = await supabase
    .from('sync_metadata')
    .select('value')
    .eq('key', key)
    .limit(1);

  if (error || !data || data.length === 0) return null;
  const raw = data[0].value;
  if (raw === null || raw === undefined) return null;
  return typeof raw === 'string' ? raw : JSON.stringify(raw);
}

export async function getSyncMetadataJson<T>(key: string, fallback: T): Promise<T> {
  const raw = await getSyncMetadata(key);
  if (!raw) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

export async function getSyncMetadataByKeys(
  keys: string[]
): Promise<Record<string, string>> {
  const safeKeys = keys.filter((k) => !isBlockedMetadataKey(k));
  if (safeKeys.length === 0) return {};
  const supabase = getSupabaseAdmin();
  const { data, error } = await supabase
    .from('sync_metadata')
    .select('key, value')
    .in('key', safeKeys);

  if (error || !data) return {};
  const map: Record<string, string> = {};
  for (const row of data) {
    if (!isBlockedMetadataKey(row.key) && row.value !== null && row.value !== undefined) {
      map[row.key] =
        typeof row.value === 'string' ? row.value : JSON.stringify(row.value);
    }
  }
  return map;
}

export async function getSyncMetadataByPrefix(
  prefix: string
): Promise<Array<{ key: string; value: string; updated_at: string | null }>> {
  const supabase = getSupabaseAdmin();
  const { data, error } = await supabase
    .from('sync_metadata')
    .select('key, value, updated_at')
    .like('key', `${prefix}%`)
    .order('updated_at', { ascending: false })
    .limit(500);

  if (error || !data) return [];
  return data
    .filter((row) => !isBlockedMetadataKey(row.key))
    .map((row) => ({
      key: row.key,
      value:
        typeof row.value === 'string'
          ? row.value
          : row.value !== null && row.value !== undefined
            ? JSON.stringify(row.value)
            : '',
      updated_at: row.updated_at || null,
    }));
}

export async function setSyncMetadata(
  key: string,
  value: unknown
): Promise<{ success: boolean; error?: string }> {
  if (isBlockedMetadataKey(key)) {
    return { success: false, error: 'Restricted metadata key' };
  }
  const supabase = getSupabaseAdmin();
  const serialized = typeof value === 'string' ? value : JSON.stringify(value);
  const { error } = await supabase.from('sync_metadata').upsert(
    {
      key,
      value: serialized,
      updated_at: new Date().toISOString(),
    },
    { onConflict: 'key' }
  );
  if (error) {
    return { success: false, error: error.message };
  }
  return { success: true };
}

export const DEFAULT_ACCESS_POLICY: AccessPolicyConfig = {
  admin_passcode: 'FILTR007',
  staff_passcode: '1234',
  passcode_epoch: 1,
  require_owner_approval_for_admin: false,
};

export async function fetchAccessPolicyConfig(): Promise<AccessPolicyConfig> {
  const parsed = await getSyncMetadataJson<Partial<AccessPolicyConfig>>(
    'access_policy_config',
    {}
  );
  return {
    admin_passcode:
      typeof parsed.admin_passcode === 'string' && parsed.admin_passcode.trim()
        ? parsed.admin_passcode.trim()
        : DEFAULT_ACCESS_POLICY.admin_passcode,
    staff_passcode:
      typeof parsed.staff_passcode === 'string' && parsed.staff_passcode.trim()
        ? parsed.staff_passcode.trim()
        : DEFAULT_ACCESS_POLICY.staff_passcode,
    passcode_epoch:
      typeof parsed.passcode_epoch === 'number' && parsed.passcode_epoch > 0
        ? parsed.passcode_epoch
        : 1,
    require_owner_approval_for_admin: Boolean(
      parsed.require_owner_approval_for_admin
    ),
  };
}

export function parseDeviceRegistration(
  key: string,
  rawValue: string
): DeviceRegistration | null {
  try {
    const j = JSON.parse(rawValue);
    const devId =
      typeof j.device_id === 'string' && j.device_id.trim()
        ? j.device_id.trim()
        : key.replace(/^device_reg_/, '').split('_').pop() || key;
    const rawRole = String(j.app_role || 'STAFF').toUpperCase();
    const rawStatus = String(j.status || 'ACTIVE').toUpperCase();
    return {
      registryKey: key,
      device_id: devId,
      app_role: rawRole === 'ADMIN' ? 'ADMIN' : 'STAFF',
      operator_name: String(j.operator_name || 'Unknown Device'),
      device_model: String(j.device_model || 'Android'),
      android_version: String(j.android_version || ''),
      app_version_name: String(j.app_version_name || ''),
      app_version_code: Number(j.app_version_code || 0),
      status:
        rawStatus === 'REVOKED'
          ? 'REVOKED'
          : rawStatus === 'PENDING'
            ? 'PENDING'
            : 'ACTIVE',
      passcode_epoch: Number(j.passcode_epoch || 1),
      first_seen_ms: Number(j.first_seen_ms || Date.now()),
      last_seen_ms: Number(j.last_seen_ms || Date.now()),
      role_updated_at_ms: Number(j.role_updated_at_ms || 0),
      linked_member_id: String(j.linked_member_id || ''),
      linked_member_name: String(j.linked_member_name || ''),
      member_link_updated_at_ms: Number(j.member_link_updated_at_ms || 0),
    };
  } catch {
    return null;
  }
}

export function serializeDeviceRegistration(reg: DeviceRegistration): string {
  const { registryKey: _ignore, ...payload } = reg;
  void _ignore;
  return JSON.stringify(payload);
}

export async function fetchAllRegisteredDevices(): Promise<DeviceRegistration[]> {
  const rows = await getSyncMetadataByPrefix('device_reg_');
  const map = new Map<string, DeviceRegistration>();
  for (const row of rows) {
    const parsed = parseDeviceRegistration(row.key, row.value);
    if (!parsed) continue;
    const dedupId = parsed.device_id || row.key;
    const existing = map.get(dedupId);
    if (!existing || parsed.last_seen_ms > existing.last_seen_ms) {
      map.set(dedupId, parsed);
    }
  }
  return Array.from(map.values()).sort(
    (a, b) => b.last_seen_ms - a.last_seen_ms
  );
}

export async function cascadeMemberPolicyToLinkedDevices(
  member: StaffRosterMember
): Promise<number> {
  const devices = await fetchAllRegisteredDevices();
  let updatedCount = 0;
  const now = Date.now();
  const normalizedRole: 'ADMIN' | 'STAFF' =
    member.orgRole === 'ADMIN' ? 'ADMIN' : 'STAFF';

  for (const dev of devices) {
    if (dev.linked_member_id === member.id) {
      const updated: DeviceRegistration = !member.isActive
        ? {
            ...dev,
            status: 'REVOKED',
            app_role: 'STAFF',
            role_updated_at_ms: now,
          }
        : {
            ...dev,
            app_role: normalizedRole,
            linked_member_name: member.name,
            role_updated_at_ms: now,
          };
      const res = await setSyncMetadata(
        dev.registryKey,
        serializeDeviceRegistration(updated)
      );
      if (res.success) updatedCount++;
    }
  }
  return updatedCount;
}

export const DEFAULT_RECEIPT_CONFIG: ReceiptDesignConfig = {
  tagline: 'We are working for you <3',
  headerSubtitle: 'where coffee meets vibe',
  footerLine1: 'Thank you for hanging out with us!',
  footerLine2: 'Tag your cup on Instagram @filtr.coffeee',
  footerLine3: 'Made fresh - Crafted with obsession',
  isStoryEnabled: true,
};

export async function fetchReceiptDesignConfigServer(): Promise<ReceiptDesignConfig> {
  const parsed = await getSyncMetadataJson<Partial<ReceiptDesignConfig>>(
    'receipt_design_config',
    {}
  );
  return {
    tagline: parsed.tagline || DEFAULT_RECEIPT_CONFIG.tagline,
    headerSubtitle:
      parsed.headerSubtitle || DEFAULT_RECEIPT_CONFIG.headerSubtitle,
    footerLine1: parsed.footerLine1 || DEFAULT_RECEIPT_CONFIG.footerLine1,
    footerLine2: parsed.footerLine2 || DEFAULT_RECEIPT_CONFIG.footerLine2,
    footerLine3: parsed.footerLine3 || DEFAULT_RECEIPT_CONFIG.footerLine3,
    isStoryEnabled:
      parsed.isStoryEnabled ?? DEFAULT_RECEIPT_CONFIG.isStoryEnabled,
    metadataMap: parsed.metadataMap,
  };
}

export function mapOrderRowToOrderWithItems(row: Record<string, unknown>): OrderWithItems {
  const rawItems = Array.isArray(row.order_items)
    ? (row.order_items as Array<Record<string, unknown>>)
    : [];
  return {
    order: {
      order_id: String(row.order_id || ''),
      customer_name: String(row.customer_name || 'Walk-in'),
      contact_number: String(row.contact_number || ''),
      total_amount: Number(row.total_amount || 0),
      timestamp: Number(row.timestamp || 0),
      is_made: Boolean(row.is_made),
      is_paid: Boolean(row.is_paid),
      is_delivered: Boolean(row.is_delivered),
      sync_status: String(row.sync_status || 'SYNCED'),
      notes: String(row.notes || ''),
      payment_mode: String(row.payment_mode || 'UNPAID'),
      completed_timestamp: Number(row.completed_timestamp || 0),
    },
    items: rawItems.map((it) => ({
      id: it.id !== undefined ? Number(it.id) : undefined,
      order_id: String(it.order_id || row.order_id || ''),
      menu_item_id: Number(it.menu_item_id || 0),
      item_name: String(it.item_name || ''),
      item_price: Number(it.item_price || 0),
      quantity: Number(it.quantity || 1),
      subtotal: Number(it.subtotal || 0),
      completed_quantity: Number(it.completed_quantity || 0),
      completed_timestamp: Number(it.completed_timestamp || 0),
    })),
  };
}

export async function fetchOrdersInRangeServer(
  startMs: number,
  endMs: number,
  maxRows: number = 5000
): Promise<OrderWithItems[]> {
  const supabase = getSupabaseAdmin();
  const pageSize = 500;
  const results: OrderWithItems[] = [];
  let offset = 0;

  while (offset < maxRows) {
    const { data, error } = await supabase
      .from('orders')
      .select('*, order_items(*)')
      .gte('timestamp', startMs)
      .lte('timestamp', endMs)
      .order('timestamp', { ascending: false })
      .range(offset, offset + pageSize - 1);

    if (error || !data) break;
    for (const row of data) {
      results.push(mapOrderRowToOrderWithItems(row as Record<string, unknown>));
    }
    if (data.length < pageSize) break;
    offset += pageSize;
  }

  return results;
}

export async function triggerGoogleSheetsWebhook(
  action: string,
  payload: Record<string, unknown>
): Promise<{ sent: boolean; ok: boolean; message?: string }> {
  try {
    const webhookUrl = await getSyncMetadata('google_sheets_webhook_url');
    if (!webhookUrl || !webhookUrl.startsWith('http')) {
      return { sent: false, ok: false, message: 'No Google Sheets webhook URL configured' };
    }
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 7000);
    const resp = await fetch(webhookUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action, ...payload }),
      signal: controller.signal,
    });
    clearTimeout(timer);
    return {
      sent: true,
      ok: resp.ok,
      message: resp.ok ? 'Synced to Google Sheets' : `HTTP ${resp.status}`,
    };
  } catch (err) {
    return {
      sent: true,
      ok: false,
      message: err instanceof Error ? err.message : 'Sheets sync error',
    };
  }
}
