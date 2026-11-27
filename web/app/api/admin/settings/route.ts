import { NextRequest, NextResponse } from 'next/server';
import { invalidatePolicyEpochCache, requireAdmin } from '@/lib/admin-auth';
import { getIstDateKey, getIstDayBoundsMs } from '@/lib/domain/ist';
import {
  AccessPolicyConfig,
  AppUpdatePolicy,
  StaffRosterMember,
} from '@/lib/domain/types';
import {
  fetchAccessPolicyConfig,
  fetchAllRegisteredDevices,
  fetchOrdersInRangeServer,
  fetchReceiptDesignConfigServer,
  getSupabaseAdmin,
  getSupabaseServerUrl,
  getSyncMetadata,
  getSyncMetadataJson,
  parseDeviceRegistration,
  serializeDeviceRegistration,
  setSyncMetadata,
  triggerGoogleSheetsWebhook,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const supabase = getSupabaseAdmin();
    const [
      devices,
      roster,
      accessPolicy,
      upiRes,
      receiptConfig,
      sheetsUrl,
      sheetsWebhookUrl,
      thresholdRaw,
      appUpdatePolicy,
      githubRepoRaw,
      storyListRes,
    ] = await Promise.all([
      fetchAllRegisteredDevices(),
      getSyncMetadataJson<StaffRosterMember[]>('attendance_staff_roster', []),
      fetchAccessPolicyConfig(),
      supabase.from('upi_accounts').select('*').order('is_default', { ascending: false }),
      fetchReceiptDesignConfigServer(),
      getSyncMetadata('google_sheets_url'),
      getSyncMetadata('google_sheets_webhook_url'),
      getSyncMetadata('inventory_price_alert_threshold_pct'),
      getSyncMetadataJson<AppUpdatePolicy | null>('app_update_policy', null),
      getSyncMetadata('github_update_repo'),
      supabase.storage.from('receipt-stories').list('', {
        limit: 50,
        sortBy: { column: 'created_at', order: 'desc' },
      }),
    ]);

    const baseStorageUrl = `${getSupabaseServerUrl()}/storage/v1/object/public/receipt-stories`;
    const storyPhotos = (storyListRes.data || [])
      .filter((f) => f.name && !f.name.startsWith('.'))
      .map((f) => ({
        name: f.name,
        url: `${baseStorageUrl}/${encodeURIComponent(f.name)}`,
      }));

    return NextResponse.json({
      devices,
      roster,
      accessPolicy,
      upiAccounts: upiRes.data || [],
      receiptConfig,
      storyPhotos,
      sheetsUrl: sheetsUrl || '',
      sheetsWebhookUrl: sheetsWebhookUrl || '',
      priceAlertThresholdPct: Number(thresholdRaw || 5) || 5,
      appUpdatePolicy,
      githubUpdateRepo: githubRepoRaw || '',
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load settings' },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const body = await request.json();
    const action = String(body?.action || '');
    const supabase = getSupabaseAdmin();

    if (action === 'updateDeviceStatus') {
      const registryKey = String(body?.registryKey || '').trim();
      const newStatus = String(body?.status || '').toUpperCase();
      if (!registryKey || !['ACTIVE', 'PENDING', 'REVOKED'].includes(newStatus)) {
        return NextResponse.json({ error: 'Invalid device status update' }, { status: 400 });
      }
      const raw = await getSyncMetadata(registryKey);
      if (!raw) return NextResponse.json({ error: 'Device not found' }, { status: 404 });
      const dev = parseDeviceRegistration(registryKey, raw);
      if (!dev) return NextResponse.json({ error: 'Invalid device record' }, { status: 400 });

      const updated = { ...dev, status: newStatus as 'ACTIVE' | 'PENDING' | 'REVOKED' };
      await setSyncMetadata(registryKey, serializeDeviceRegistration(updated));
      return NextResponse.json({ success: true });
    }

    if (action === 'updateDeviceRole') {
      const registryKey = String(body?.registryKey || '').trim();
      const newRole = String(body?.role || '').toUpperCase();
      if (!registryKey || (newRole !== 'ADMIN' && newRole !== 'STAFF')) {
        return NextResponse.json({ error: 'Invalid role' }, { status: 400 });
      }
      const raw = await getSyncMetadata(registryKey);
      if (!raw) return NextResponse.json({ error: 'Device not found' }, { status: 404 });
      const dev = parseDeviceRegistration(registryKey, raw);
      if (!dev) return NextResponse.json({ error: 'Invalid device record' }, { status: 400 });

      const updated = {
        ...dev,
        app_role: newRole as 'ADMIN' | 'STAFF',
        role_updated_at_ms: Date.now(),
      };
      await setSyncMetadata(registryKey, serializeDeviceRegistration(updated));
      return NextResponse.json({ success: true });
    }

    if (action === 'linkDeviceMember') {
      const registryKey = String(body?.registryKey || '').trim();
      const memberId = String(body?.memberId || '').trim();
      const memberName = String(body?.memberName || '').trim();
      const memberOrgRole = String(body?.memberOrgRole || 'STAFF').toUpperCase();
      const raw = await getSyncMetadata(registryKey);
      if (!raw) return NextResponse.json({ error: 'Device not found' }, { status: 404 });
      const dev = parseDeviceRegistration(registryKey, raw);
      if (!dev) return NextResponse.json({ error: 'Invalid device record' }, { status: 400 });

      const now = Date.now();
      const updated = {
        ...dev,
        linked_member_id: memberId,
        linked_member_name: memberName,
        member_link_updated_at_ms: now,
        app_role: (memberOrgRole === 'ADMIN' ? 'ADMIN' : 'STAFF') as 'ADMIN' | 'STAFF',
        role_updated_at_ms: now,
      };
      await setSyncMetadata(registryKey, serializeDeviceRegistration(updated));
      return NextResponse.json({ success: true });
    }

    if (action === 'saveAccessPolicy') {
      const current = await fetchAccessPolicyConfig();
      const adminPasscode = String(body?.admin_passcode || '').trim();
      const staffPasscode = String(body?.staff_passcode || '').trim();
      const requireApproval = Boolean(body?.require_owner_approval_for_admin);
      const forceReverify = Boolean(body?.forceReverifyAll);
      if (!adminPasscode || !staffPasscode) {
        return NextResponse.json(
          { error: 'Both passcodes are required' },
          { status: 400 }
        );
      }
      const nextPolicy: AccessPolicyConfig = {
        admin_passcode: adminPasscode,
        staff_passcode: staffPasscode,
        passcode_epoch: forceReverify
          ? current.passcode_epoch + 1
          : current.passcode_epoch,
        require_owner_approval_for_admin: requireApproval,
      };
      await setSyncMetadata('access_policy_config', nextPolicy);
      invalidatePolicyEpochCache();
      return NextResponse.json({ success: true, accessPolicy: nextPolicy });
    }

    if (action === 'upsertUpiAccount') {
      const id = Number(body?.id || 0);
      const vpa = String(body?.vpa || '').trim();
      const payeeName = String(body?.payee_name || 'FILTR Coffee Store').trim();
      const isDefault = Boolean(body?.is_default);
      const note = String(body?.note || '').trim();
      if (!vpa) return NextResponse.json({ error: 'UPI ID (VPA) required' }, { status: 400 });

      if (isDefault) {
        await supabase.from('upi_accounts').update({ is_default: false }).neq('id', -1);
      }

      if (id > 0) {
        const { error } = await supabase
          .from('upi_accounts')
          .update({ vpa, payee_name: payeeName, is_default: isDefault, note })
          .eq('id', id);
        if (error) return NextResponse.json({ error: error.message }, { status: 500 });
      } else {
        const { error } = await supabase
          .from('upi_accounts')
          .insert({ vpa, payee_name: payeeName, is_default: isDefault, note });
        if (error) return NextResponse.json({ error: error.message }, { status: 500 });
      }
      return NextResponse.json({ success: true });
    }

    if (action === 'deleteUpiAccount') {
      const id = Number(body?.id || 0);
      if (!id) return NextResponse.json({ error: 'id required' }, { status: 400 });
      const { error } = await supabase.from('upi_accounts').delete().eq('id', id);
      if (error) return NextResponse.json({ error: error.message }, { status: 500 });
      return NextResponse.json({ success: true });
    }

    if (action === 'saveReceiptConfig') {
      const current = await fetchReceiptDesignConfigServer();
      const nextConfig = {
        tagline: String(body?.tagline ?? current.tagline),
        headerSubtitle: String(body?.headerSubtitle ?? current.headerSubtitle),
        footerLine1: String(body?.footerLine1 ?? current.footerLine1),
        footerLine2: String(body?.footerLine2 ?? current.footerLine2),
        footerLine3: String(body?.footerLine3 ?? current.footerLine3),
        isStoryEnabled: Boolean(body?.isStoryEnabled ?? current.isStoryEnabled),
        metadataMap: current.metadataMap || {},
      };
      await setSyncMetadata('receipt_design_config', nextConfig);
      return NextResponse.json({ success: true, receiptConfig: nextConfig });
    }

    if (action === 'saveSheetsConfig') {
      const sheetsUrl = String(body?.sheetsUrl || '').trim();
      const sheetsWebhookUrl = String(body?.sheetsWebhookUrl || '').trim();
      const thresholdPct = Math.max(0.1, Number(body?.priceAlertThresholdPct || 5));

      await Promise.all([
        setSyncMetadata('google_sheets_url', sheetsUrl),
        sheetsWebhookUrl
          ? setSyncMetadata('google_sheets_webhook_url', sheetsWebhookUrl)
          : Promise.resolve(),
        setSyncMetadata('inventory_price_alert_threshold_pct', String(thresholdPct)),
      ]);
      return NextResponse.json({ success: true });
    }

    if (action === 'syncTodayToSheets') {
      const todayKey = getIstDateKey();
      const { startMs, endMs } = getIstDayBoundsMs(todayKey);
      const orders = await fetchOrdersInRangeServer(startMs, endMs);
      const formatted = orders.map(({ order, items }) => ({
        orderId: order.order_id,
        timestamp: new Date(order.timestamp).toLocaleString('en-IN', {
          timeZone: 'Asia/Kolkata',
        }),
        customerName: order.customer_name,
        contactNumber: order.contact_number,
        totalAmount: order.total_amount,
        isPaid: order.is_paid ? 'PAID' : 'UNPAID',
        paymentMode: order.payment_mode,
        isMade: order.is_made ? 'READY' : 'PREPARING',
        isDelivered: order.is_delivered ? 'DELIVERED' : 'PENDING',
        notes: order.notes,
        items: items.map((it) => ({
          itemName: it.item_name,
          unitPrice: it.item_price,
          quantity: it.quantity,
          subtotal: it.subtotal,
          completedQuantity: it.completed_quantity,
        })),
      }));
      const res = await triggerGoogleSheetsWebhook('syncBatch', {
        orders: formatted,
      });
      return NextResponse.json({ success: res.ok, count: formatted.length, ...res });
    }

    if (action === 'saveOtaPolicy') {
      const policy: AppUpdatePolicy = {
        force_update: Boolean(body?.force_update),
        min_version_code: Number(body?.min_version_code || 1),
        min_version_name: String(body?.min_version_name || '').trim(),
        title: String(body?.title || 'Mandatory Update Required').trim(),
        message: String(body?.message || '').trim(),
        apk_download_url: String(body?.apk_download_url || '').trim(),
        apk_file_name: String(body?.apk_file_name || 'filtr-billing.apk').trim(),
        apk_size_bytes: Number(body?.apk_size_bytes || 0),
      };
      await setSyncMetadata('app_update_policy', policy);
      return NextResponse.json({ success: true, appUpdatePolicy: policy });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Settings update failed' },
      { status: 500 }
    );
  }
}
