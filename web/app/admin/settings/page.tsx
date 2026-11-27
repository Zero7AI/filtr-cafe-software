'use client';

import React, { useEffect, useState, useCallback } from 'react';
import {
  Smartphone,
  KeyRound,
  QrCode,
  Receipt,
  Settings,
  Plus,
  Trash2,
  RefreshCw,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import { useToast } from '@/components/admin/ToastContext';
import { formatIstDateTime } from '@/lib/domain/ist';
import {
  AccessPolicyConfig,
  AppUpdatePolicy,
  DeviceRegistration,
  ReceiptDesignConfig,
  StaffRosterMember,
  UpiAccount,
} from '@/lib/domain/types';

export default function AdminSettingsPage() {
  const { isDark, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [tab, setTab] = useState<'DEVICES' | 'UPI' | 'RECEIPT' | 'SYSTEM'>('DEVICES');
  const [devices, setDevices] = useState<DeviceRegistration[]>([]);
  const [roster, setRoster] = useState<StaffRosterMember[]>([]);
  const [policy, setPolicy] = useState<AccessPolicyConfig>({
    admin_passcode: 'FILTR007',
    staff_passcode: '1234',
    passcode_epoch: 1,
    require_owner_approval_for_admin: false,
  });
  const [forceReverify, setForceReverify] = useState(false);

  const [upiAccounts, setUpiAccounts] = useState<UpiAccount[]>([]);
  const [newUpi, setNewUpi] = useState({ vpa: '', payee_name: 'FILTR Coffee Store', is_default: false });

  const [receipt, setReceipt] = useState<ReceiptDesignConfig>({
    tagline: '',
    headerSubtitle: '',
    footerLine1: '',
    footerLine2: '',
    footerLine3: '',
    isStoryEnabled: true,
  });

  const [sheetsUrl, setSheetsUrl] = useState('');
  const [sheetsWebhookUrl, setSheetsWebhookUrl] = useState('');
  const [thresholdPct, setThresholdPct] = useState(5);
  const [otaPolicy, setOtaPolicy] = useState<AppUpdatePolicy>({
    force_update: false,
    min_version_code: 22,
    min_version_name: '2.5.3',
    title: 'Mandatory Update Required',
    message: '',
    apk_download_url: '',
    apk_file_name: 'filtr-billing.apk',
    apk_size_bytes: 0,
  });
  const [loading, setLoading] = useState(true);

  const loadSettings = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/settings', { cache: 'no-store' });
      if (res.ok) {
        const d = await res.json();
        setDevices(d.devices || []);
        setRoster(d.roster || []);
        if (d.accessPolicy) setPolicy(d.accessPolicy);
        setUpiAccounts(d.upiAccounts || []);
        if (d.receiptConfig) setReceipt(d.receiptConfig);
        setSheetsUrl(d.sheetsUrl || '');
        setSheetsWebhookUrl(d.sheetsWebhookUrl || '');
        setThresholdPct(d.priceAlertThresholdPct || 5);
        if (d.appUpdatePolicy) setOtaPolicy(d.appUpdatePolicy);
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSettings();
  }, [loadSettings, refreshTick]);

  const postAction = async (payload: Record<string, unknown>, msg: string) => {
    const res = await fetch('/api/admin/settings', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    const d = await res.json();
    if (res.ok) {
      pushToast(msg, 'success');
      loadSettings();
    } else {
      pushToast(d.error || 'Action failed', 'error');
    }
  };

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap gap-1.5">
        {(
          [
            ['DEVICES', `Devices & Access (${devices.length})`, Smartphone],
            ['UPI', `UPI Accounts (${upiAccounts.length})`, QrCode],
            ['RECEIPT', 'Receipt Design', Receipt],
            ['SYSTEM', 'Sheets & OTA Policy', Settings],
          ] as const
        ).map(([k, lbl, Icon]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={`flex items-center gap-1.5 rounded-xl border px-3.5 py-2 text-xs font-black ${
              tab === k
                ? 'bg-amber-500 text-zinc-950 border-amber-500'
                : isDark ? 'bg-zinc-900 border-zinc-800 text-zinc-400' : 'bg-white border-zinc-200'
            }`}
          >
            <Icon className="h-3.5 w-3.5" />
            <span>{lbl}</span>
          </button>
        ))}
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading settings...</div>
      ) : tab === 'DEVICES' ? (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-5">
          <div className="lg:col-span-2 space-y-3">
            {devices.map((dev) => (
              <div key={dev.registryKey} className={`rounded-2xl border p-4 space-y-2.5 ${cardSurface}`}>
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-black">{dev.operator_name}</span>
                      <span
                        className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                          dev.app_role === 'ADMIN'
                            ? 'bg-amber-500 text-zinc-950'
                            : 'bg-sky-500/20 text-sky-400'
                        }`}
                      >
                        {dev.app_role}
                      </span>
                      <span
                        className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                          dev.status === 'ACTIVE'
                            ? 'bg-emerald-500/15 text-emerald-500'
                            : dev.status === 'PENDING'
                              ? 'bg-amber-500/20 text-amber-400'
                              : 'bg-rose-500/20 text-rose-400'
                        }`}
                      >
                        {dev.status}
                      </span>
                    </div>
                    <p className="text-[11px] text-zinc-400 mt-0.5">
                      {dev.device_model} • v{dev.app_version_name || '—'} • Seen{' '}
                      {formatIstDateTime(dev.last_seen_ms)}
                    </p>
                    {dev.linked_member_name && (
                      <p className="text-[11px] text-emerald-400">
                        Linked to Roster: {dev.linked_member_name}
                      </p>
                    )}
                  </div>

                  <div className="flex flex-wrap gap-1.5">
                    {dev.status !== 'ACTIVE' && (
                      <button
                        type="button"
                        onClick={() =>
                          postAction(
                            { action: 'updateDeviceStatus', registryKey: dev.registryKey, status: 'ACTIVE' },
                            `Approved ${dev.operator_name}`
                          )
                        }
                        className="rounded-lg bg-emerald-500 px-2.5 py-1 text-[11px] font-black text-zinc-950"
                      >
                        Approve / Restore
                      </button>
                    )}
                    {dev.status === 'ACTIVE' && (
                      <button
                        type="button"
                        onClick={() =>
                          postAction(
                            { action: 'updateDeviceStatus', registryKey: dev.registryKey, status: 'REVOKED' },
                            `Revoked ${dev.operator_name}`
                          )
                        }
                        className="rounded-lg border border-rose-500/40 bg-rose-500/10 px-2.5 py-1 text-[11px] font-bold text-rose-400"
                      >
                        Revoke
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() =>
                        postAction(
                          {
                            action: 'updateDeviceRole',
                            registryKey: dev.registryKey,
                            role: dev.app_role === 'ADMIN' ? 'STAFF' : 'ADMIN',
                          },
                          `Updated role for ${dev.operator_name}`
                        )
                      }
                      className="rounded-lg border border-zinc-700 px-2.5 py-1 text-[11px] font-bold"
                    >
                      Make {dev.app_role === 'ADMIN' ? 'STAFF' : 'ADMIN'}
                    </button>
                  </div>
                </div>

                <div className="flex items-center gap-2 pt-2 border-t border-zinc-200 dark:border-zinc-800">
                  <span className="text-[11px] text-zinc-400">Link Roster:</span>
                  <select
                    value={dev.linked_member_id || ''}
                    onChange={(e) => {
                      const member = roster.find((r) => r.id === e.target.value);
                      if (!member) return;
                      postAction(
                        {
                          action: 'linkDeviceMember',
                          registryKey: dev.registryKey,
                          memberId: member.id,
                          memberName: member.name,
                          memberOrgRole: member.orgRole,
                        },
                        `Linked ${dev.operator_name} → ${member.name}`
                      );
                    }}
                    className="rounded-lg border border-zinc-700 bg-zinc-900 px-2 py-1 text-xs text-white"
                  >
                    <option value="">-- Unlinked --</option>
                    {roster.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.name} ({r.orgRole})
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            ))}
          </div>

          <div className={`rounded-2xl border p-5 space-y-3 h-fit ${cardSurface}`}>
            <h3 className="text-sm font-black flex items-center gap-2">
              <KeyRound className="h-4 w-4 text-amber-500" />
              Passcode Policy (Epoch #{policy.passcode_epoch})
            </h3>
            <div>
              <label className="block text-xs font-bold mb-1">Admin Passcode</label>
              <input
                type="text"
                value={policy.admin_passcode}
                onChange={(e) => setPolicy({ ...policy, admin_passcode: e.target.value })}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs font-mono text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Staff POS PIN</label>
              <input
                type="text"
                value={policy.staff_passcode}
                onChange={(e) => setPolicy({ ...policy, staff_passcode: e.target.value })}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs font-mono text-white"
              />
            </div>
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={policy.require_owner_approval_for_admin}
                onChange={(e) =>
                  setPolicy({
                    ...policy,
                    require_owner_approval_for_admin: e.target.checked,
                  })
                }
              />
              Require Owner approval for new Admin devices
            </label>
            <label className="flex items-center gap-2 text-xs text-amber-400">
              <input
                type="checkbox"
                checked={forceReverify}
                onChange={(e) => setForceReverify(e.target.checked)}
              />
              Force re-verify all sessions (bump epoch)
            </label>
            <button
              type="button"
              onClick={() =>
                postAction(
                  { action: 'saveAccessPolicy', ...policy, forceReverifyAll: forceReverify },
                  'Saved access policy'
                )
              }
              className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
            >
              Save Access Policy
            </button>
          </div>
        </div>
      ) : tab === 'UPI' ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className="space-y-3">
            {upiAccounts.map((acc) => (
              <div
                key={acc.id}
                className={`rounded-2xl border p-4 flex items-center justify-between ${cardSurface}`}
              >
                <div>
                  <p className="font-mono text-sm font-black text-amber-500">{acc.vpa}</p>
                  <p className="text-xs text-zinc-400">
                    {acc.payee_name} {acc.is_default ? '• DEFAULT' : ''}
                  </p>
                </div>
                <div className="flex gap-2">
                  {!acc.is_default && (
                    <button
                      type="button"
                      onClick={() =>
                        postAction(
                          { action: 'upsertUpiAccount', ...acc, is_default: true },
                          `Set ${acc.vpa} as default`
                        )
                      }
                      className="rounded-lg border border-zinc-700 px-2.5 py-1 text-xs font-bold"
                    >
                      Set Default
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() =>
                      postAction({ action: 'deleteUpiAccount', id: acc.id }, 'Deleted UPI account')
                    }
                    className="p-1.5 text-rose-400"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          <div className={`rounded-2xl border p-5 space-y-3 h-fit ${cardSurface}`}>
            <h3 className="text-sm font-black flex items-center gap-2">
              <Plus className="h-4 w-4 text-amber-500" /> Add UPI Account
            </h3>
            <input
              type="text"
              value={newUpi.vpa}
              onChange={(e) => setNewUpi({ ...newUpi, vpa: e.target.value })}
              placeholder="UPI VPA (e.g. 9907839311@ybl)"
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
            />
            <input
              type="text"
              value={newUpi.payee_name}
              onChange={(e) => setNewUpi({ ...newUpi, payee_name: e.target.value })}
              placeholder="Payee Name"
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
            />
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={newUpi.is_default}
                onChange={(e) => setNewUpi({ ...newUpi, is_default: e.target.checked })}
              />
              Set as Default QR Account
            </label>
            <button
              type="button"
              onClick={() =>
                postAction({ action: 'upsertUpiAccount', ...newUpi }, 'Added UPI account')
              }
              className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
            >
              Add UPI Account
            </button>
          </div>
        </div>
      ) : tab === 'RECEIPT' ? (
        <div className={`rounded-2xl border p-5 max-w-2xl space-y-3 ${cardSurface}`}>
          <h3 className="text-sm font-black">Receipt Design Configuration</h3>
          {(
            [
              ['headerSubtitle', 'Header Subtitle'],
              ['tagline', 'Tagline'],
              ['footerLine1', 'Footer Line 1'],
              ['footerLine2', 'Footer Line 2'],
              ['footerLine3', 'Footer Line 3'],
            ] as const
          ).map(([field, label]) => (
            <div key={field}>
              <label className="block text-xs font-bold mb-1">{label}</label>
              <input
                type="text"
                value={receipt[field]}
                onChange={(e) => setReceipt({ ...receipt, [field]: e.target.value })}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
          ))}
          <button
            type="button"
            onClick={() =>
              postAction({ action: 'saveReceiptConfig', ...receipt }, 'Saved receipt design')
            }
            className="rounded-xl bg-amber-500 px-5 py-2.5 text-xs font-black text-zinc-950"
          >
            Save Receipt Design
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className={`rounded-2xl border p-5 space-y-3 ${cardSurface}`}>
            <h3 className="text-sm font-black">Google Sheets & Price Alerts</h3>
            <div>
              <label className="block text-xs font-bold mb-1">Google Sheets URL</label>
              <input
                type="url"
                value={sheetsUrl}
                onChange={(e) => setSheetsUrl(e.target.value)}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Apps Script Webhook URL</label>
              <input
                type="url"
                value={sheetsWebhookUrl}
                onChange={(e) => setSheetsWebhookUrl(e.target.value)}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">
                Price Hike Alert Threshold (%)
              </label>
              <input
                type="number"
                step="0.5"
                value={thresholdPct}
                onChange={(e) => setThresholdPct(Number(e.target.value))}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() =>
                  postAction(
                    {
                      action: 'saveSheetsConfig',
                      sheetsUrl,
                      sheetsWebhookUrl,
                      priceAlertThresholdPct: thresholdPct,
                    },
                    'Saved Sheets & threshold settings'
                  )
                }
                className="flex-1 rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
              >
                Save Config
              </button>
              <button
                type="button"
                onClick={() =>
                  postAction({ action: 'syncTodayToSheets' }, 'Synced today orders to Sheets')
                }
                className="flex items-center gap-1.5 rounded-xl border border-emerald-500/40 bg-emerald-500/15 px-4 py-2.5 text-xs font-bold text-emerald-400"
              >
                <RefreshCw className="h-3.5 w-3.5" />
                Sync Today
              </button>
            </div>
          </div>

          <div className={`rounded-2xl border p-5 space-y-3 ${cardSurface}`}>
            <h3 className="text-sm font-black">Android App OTA Update Policy</h3>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold mb-1">Min Version Code</label>
                <input
                  type="number"
                  value={otaPolicy.min_version_code}
                  onChange={(e) =>
                    setOtaPolicy({ ...otaPolicy, min_version_code: Number(e.target.value) })
                  }
                  className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
                />
              </div>
              <div>
                <label className="block text-xs font-bold mb-1">Min Version Name</label>
                <input
                  type="text"
                  value={otaPolicy.min_version_name}
                  onChange={(e) =>
                    setOtaPolicy({ ...otaPolicy, min_version_name: e.target.value })
                  }
                  className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
                />
              </div>
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">APK Download URL</label>
              <input
                type="url"
                value={otaPolicy.apk_download_url}
                onChange={(e) =>
                  setOtaPolicy({ ...otaPolicy, apk_download_url: e.target.value })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={otaPolicy.force_update}
                onChange={(e) =>
                  setOtaPolicy({ ...otaPolicy, force_update: e.target.checked })
                }
              />
              Force Mandatory Update on Android Devices
            </label>
            <button
              type="button"
              onClick={() =>
                postAction({ action: 'saveOtaPolicy', ...otaPolicy }, 'Saved OTA policy')
              }
              className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
            >
              Save OTA Policy
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
