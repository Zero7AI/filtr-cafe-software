'use client';

import React, { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import {
  Button,
  Card,
  Chip,
  Input,
  Label,
  Skeleton,
  Switch,
  Tabs,
  TextField,
} from '@heroui/react';
import {
  KeyRound,
  Monitor,
  Plus,
  QrCode,
  Receipt,
  RefreshCw,
  Settings,
  Smartphone,
  Trash2,
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
  const { refreshTick } = useAdminContext();
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
  const [newUpi, setNewUpi] = useState({
    vpa: '',
    payee_name: 'FILTR Coffee Store',
    is_default: false,
  });

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
    min_version_code: 23,
    min_version_name: '2.5.4',
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

  return (
    <div className="space-y-5">
      <Tabs
        selectedKey={tab}
        onSelectionChange={(k) => setTab(String(k) as typeof tab)}
        variant="secondary"
        className="w-auto"
      >
        <Tabs.ListContainer>
          <Tabs.List aria-label="Settings sections">
            <Tabs.Tab id="DEVICES" className="text-xs px-3.5">
              <span className="flex items-center gap-1.5">
                <Smartphone className="h-3.5 w-3.5" />
                Devices & Access ({devices.length})
              </span>
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="UPI" className="text-xs px-3.5">
              <span className="flex items-center gap-1.5">
                <QrCode className="h-3.5 w-3.5" />
                UPI Accounts ({upiAccounts.length})
              </span>
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="RECEIPT" className="text-xs px-3.5">
              <span className="flex items-center gap-1.5">
                <Receipt className="h-3.5 w-3.5" />
                Receipt Design
              </span>
              <Tabs.Indicator />
            </Tabs.Tab>
            <Tabs.Tab id="SYSTEM" className="text-xs px-3.5">
              <span className="flex items-center gap-1.5">
                <Settings className="h-3.5 w-3.5" />
                Sheets, OTA & POS
              </span>
              <Tabs.Indicator />
            </Tabs.Tab>
          </Tabs.List>
        </Tabs.ListContainer>
      </Tabs>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
      ) : tab === 'DEVICES' ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <div className="lg:col-span-8 space-y-3">
            {devices.map((dev) => (
              <Card key={dev.registryKey} className="border border-border/80">
                <Card.Content className="p-4 space-y-3">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-bold">{dev.operator_name}</span>
                        <Chip
                          size="sm"
                          variant="soft"
                          color={dev.app_role === 'ADMIN' ? 'accent' : 'default'}
                        >
                          {dev.app_role}
                        </Chip>
                        <Chip
                          size="sm"
                          variant="soft"
                          color={
                            dev.status === 'ACTIVE'
                              ? 'success'
                              : dev.status === 'PENDING'
                                ? 'warning'
                                : 'danger'
                          }
                        >
                          {dev.status}
                        </Chip>
                      </div>
                      <p className="text-xs text-muted mt-0.5">
                        {dev.device_model} • v{dev.app_version_name || '—'} • Seen{' '}
                        {formatIstDateTime(dev.last_seen_ms)}
                      </p>
                      {dev.linked_member_name ? (
                        <p className="text-xs text-success font-medium mt-0.5">
                          Linked to Roster: {dev.linked_member_name}
                        </p>
                      ) : null}
                    </div>

                    <div className="flex flex-wrap gap-1.5">
                      {dev.status !== 'ACTIVE' && (
                        <Button
                          size="sm"
                          variant="primary"
                          onPress={() =>
                            postAction(
                              {
                                action: 'updateDeviceStatus',
                                registryKey: dev.registryKey,
                                status: 'ACTIVE',
                              },
                              `Approved ${dev.operator_name}`
                            )
                          }
                        >
                          Approve / Restore
                        </Button>
                      )}
                      {dev.status === 'ACTIVE' && (
                        <Button
                          size="sm"
                          variant="danger-soft"
                          onPress={() =>
                            postAction(
                              {
                                action: 'updateDeviceStatus',
                                registryKey: dev.registryKey,
                                status: 'REVOKED',
                              },
                              `Revoked ${dev.operator_name}`
                            )
                          }
                        >
                          Revoke
                        </Button>
                      )}
                      <Button
                        size="sm"
                        variant="outline"
                        onPress={() =>
                          postAction(
                            {
                              action: 'updateDeviceRole',
                              registryKey: dev.registryKey,
                              role: dev.app_role === 'ADMIN' ? 'STAFF' : 'ADMIN',
                            },
                            `Updated role for ${dev.operator_name}`
                          )
                        }
                      >
                        Make {dev.app_role === 'ADMIN' ? 'STAFF' : 'ADMIN'}
                      </Button>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 pt-2 border-t border-separator">
                    <span className="text-xs text-muted">Link Roster:</span>
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
                      className="rounded-xl border border-border bg-surface-secondary px-2.5 py-1 text-xs text-foreground"
                    >
                      <option value="">-- Unlinked --</option>
                      {roster.map((r) => (
                        <option key={r.id} value={r.id}>
                          {r.name} ({r.orgRole})
                        </option>
                      ))}
                    </select>
                  </div>
                </Card.Content>
              </Card>
            ))}
          </div>

          <Card className="lg:col-span-4 border border-border/80 h-fit">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold flex items-center gap-2">
                <KeyRound className="h-4 w-4 text-accent" />
                Passcode Policy (Epoch #{policy.passcode_epoch})
              </Card.Title>
            </Card.Header>
            <Card.Content className="px-5 pb-5 space-y-3">
              <div className="space-y-1">
                <Label className="text-xs font-medium">Admin Passcode</Label>
                <Input
                  type="text"
                  variant="secondary"
                  fullWidth
                  value={policy.admin_passcode}
                  onChange={(e) => setPolicy({ ...policy, admin_passcode: e.target.value })}
                  className="font-mono text-xs"
                />
              </div>

              <div className="space-y-1">
                <Label className="text-xs font-medium">Staff POS PIN</Label>
                <Input
                  type="text"
                  variant="secondary"
                  fullWidth
                  value={policy.staff_passcode}
                  onChange={(e) => setPolicy({ ...policy, staff_passcode: e.target.value })}
                  className="font-mono text-xs"
                />
              </div>

              <Switch
                isSelected={policy.require_owner_approval_for_admin}
                onChange={(v) =>
                  setPolicy({ ...policy, require_owner_approval_for_admin: v })
                }
                size="sm"
              >
                <Switch.Content>
                  <Switch.Control>
                    <Switch.Thumb />
                  </Switch.Control>
                  <Label className="text-xs">Require Owner approval for Admin devices</Label>
                </Switch.Content>
              </Switch>

              <Switch isSelected={forceReverify} onChange={setForceReverify} size="sm">
                <Switch.Content>
                  <Switch.Control>
                    <Switch.Thumb />
                  </Switch.Control>
                  <Label className="text-xs text-warning">
                    Force re-verify all sessions (bump epoch)
                  </Label>
                </Switch.Content>
              </Switch>

              <Button
                variant="primary"
                fullWidth
                onPress={() =>
                  postAction(
                    { action: 'saveAccessPolicy', ...policy, forceReverifyAll: forceReverify },
                    'Saved access policy'
                  )
                }
              >
                Save Access Policy
              </Button>
            </Card.Content>
          </Card>
        </div>
      ) : tab === 'UPI' ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <div className="space-y-3">
            {upiAccounts.map((acc) => (
              <Card key={acc.id} className="border border-border/80">
                <Card.Content className="p-4 flex items-center justify-between gap-3">
                  <div>
                    <div className="flex items-center gap-2">
                      <p className="font-mono text-sm font-bold text-accent">{acc.vpa}</p>
                      {acc.is_default ? (
                        <Chip size="sm" variant="soft" color="success">
                          DEFAULT
                        </Chip>
                      ) : null}
                    </div>
                    <p className="text-xs text-muted mt-0.5">{acc.payee_name}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    {!acc.is_default && (
                      <Button
                        size="sm"
                        variant="outline"
                        onPress={() =>
                          postAction(
                            { action: 'upsertUpiAccount', ...acc, is_default: true },
                            `Set ${acc.vpa} as default`
                          )
                        }
                      >
                        Set Default
                      </Button>
                    )}
                    <Button
                      isIconOnly
                      size="sm"
                      variant="danger-soft"
                      onPress={() =>
                        postAction(
                          { action: 'deleteUpiAccount', id: acc.id },
                          'Deleted UPI account'
                        )
                      }
                      aria-label={`Delete ${acc.vpa}`}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </Card.Content>
              </Card>
            ))}
          </div>

          <Card className="border border-border/80 h-fit">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold flex items-center gap-2">
                <Plus className="h-4 w-4 text-accent" />
                Add UPI Account
              </Card.Title>
            </Card.Header>
            <Card.Content className="px-5 pb-5 space-y-3">
              <TextField
                value={newUpi.vpa}
                onChange={(v) => setNewUpi({ ...newUpi, vpa: v })}
                fullWidth
              >
                <Label className="text-xs font-medium">UPI VPA</Label>
                <Input variant="secondary" placeholder="e.g. 9907839311@ybl" />
              </TextField>

              <TextField
                value={newUpi.payee_name}
                onChange={(v) => setNewUpi({ ...newUpi, payee_name: v })}
                fullWidth
              >
                <Label className="text-xs font-medium">Payee Name</Label>
                <Input variant="secondary" placeholder="FILTR Coffee Store" />
              </TextField>

              <Switch
                isSelected={newUpi.is_default}
                onChange={(v) => setNewUpi({ ...newUpi, is_default: v })}
                size="sm"
              >
                <Switch.Content>
                  <Switch.Control>
                    <Switch.Thumb />
                  </Switch.Control>
                  <Label className="text-xs">Set as Default QR Account</Label>
                </Switch.Content>
              </Switch>

              <Button
                variant="primary"
                fullWidth
                onPress={() =>
                  postAction({ action: 'upsertUpiAccount', ...newUpi }, 'Added UPI account')
                }
              >
                Add UPI Account
              </Button>
            </Card.Content>
          </Card>
        </div>
      ) : tab === 'RECEIPT' ? (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
          <Card className="lg:col-span-7 border border-border/80">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold">Receipt Design Configuration</Card.Title>
              <Card.Description className="text-xs">
                Synced with 58mm / 80mm thermal printers and Android POS
              </Card.Description>
            </Card.Header>
            <Card.Content className="px-5 pb-5 space-y-3">
              {(
                [
                  ['headerSubtitle', 'Header Subtitle'],
                  ['tagline', 'Tagline'],
                  ['footerLine1', 'Footer Line 1'],
                  ['footerLine2', 'Footer Line 2'],
                  ['footerLine3', 'Footer Line 3'],
                ] as const
              ).map(([field, label]) => (
                <TextField
                  key={field}
                  value={receipt[field]}
                  onChange={(v) => setReceipt({ ...receipt, [field]: v })}
                  fullWidth
                >
                  <Label className="text-xs font-medium">{label}</Label>
                  <Input variant="secondary" />
                </TextField>
              ))}

              <Switch
                isSelected={receipt.isStoryEnabled}
                onChange={(v) => setReceipt({ ...receipt, isStoryEnabled: v })}
                size="sm"
              >
                <Switch.Content>
                  <Switch.Control>
                    <Switch.Thumb />
                  </Switch.Control>
                  <Label className="text-xs">Enable Receipt Story QR / Photo Footer</Label>
                </Switch.Content>
              </Switch>

              <Button
                variant="primary"
                onPress={() =>
                  postAction({ action: 'saveReceiptConfig', ...receipt }, 'Saved receipt design')
                }
              >
                Save Receipt Design
              </Button>
            </Card.Content>
          </Card>

          {/* Live 80mm Thermal Paper Preview */}
          <Card className="lg:col-span-5 border border-border/80 h-fit">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold">Live 80mm Receipt Preview</Card.Title>
              <Card.Description className="text-xs">
                Real-time formatting check (48-col paper)
              </Card.Description>
            </Card.Header>
            <Card.Content className="px-5 pb-5">
              <div className="rounded-xl bg-white text-zinc-900 p-4 font-mono text-[11px] leading-relaxed shadow-inner border border-zinc-200 max-w-[320px] mx-auto">
                <div className="text-center font-bold text-sm">FILTR COFFEE</div>
                {receipt.headerSubtitle ? (
                  <div className="text-center text-[10px]">{receipt.headerSubtitle}</div>
                ) : null}
                {receipt.tagline ? (
                  <div className="text-center text-[10px] italic">{receipt.tagline}</div>
                ) : null}
                <div className="border-t border-dashed border-zinc-400 my-2" />
                <div className="flex justify-between">
                  <span>BILL: FLT-SAMPLE-0042</span>
                  <span>TOKEN #42</span>
                </div>
                <div className="border-t border-dashed border-zinc-400 my-2" />
                <div className="flex justify-between">
                  <span>1x Iced Pour Over</span>
                  <span>180.00</span>
                </div>
                <div className="flex justify-between">
                  <span>1x Cranberry Espresso</span>
                  <span>210.00</span>
                </div>
                <div className="border-t border-dashed border-zinc-400 my-2" />
                <div className="flex justify-between font-bold text-xs">
                  <span>TOTAL (INCL. 5% GST)</span>
                  <span>Rs 390.00</span>
                </div>
                <div className="border-t border-dashed border-zinc-400 my-2" />
                {receipt.footerLine1 ? <div className="text-center">{receipt.footerLine1}</div> : null}
                {receipt.footerLine2 ? <div className="text-center">{receipt.footerLine2}</div> : null}
                {receipt.footerLine3 ? <div className="text-center">{receipt.footerLine3}</div> : null}
              </div>
            </Card.Content>
          </Card>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
          <Card className="border border-border/80">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold">
                Google Sheets & Price Tracking
              </Card.Title>
            </Card.Header>
            <Card.Content className="px-5 pb-5 space-y-3">
              <TextField value={sheetsUrl} onChange={setSheetsUrl} fullWidth>
                <Label className="text-xs font-medium">Google Sheets URL</Label>
                <Input type="url" variant="secondary" />
              </TextField>
              <TextField value={sheetsWebhookUrl} onChange={setSheetsWebhookUrl} fullWidth>
                <Label className="text-xs font-medium">Apps Script Webhook URL</Label>
                <Input type="url" variant="secondary" />
              </TextField>
              <div className="space-y-1">
                <Label className="text-xs font-medium">Price Hike Alert Threshold (%)</Label>
                <Input
                  type="number"
                  step="0.5"
                  variant="secondary"
                  fullWidth
                  value={String(thresholdPct)}
                  onChange={(e) => setThresholdPct(Number(e.target.value))}
                />
              </div>
              <div className="flex gap-2 pt-1">
                <Button
                  variant="primary"
                  fullWidth
                  onPress={() =>
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
                >
                  Save Config
                </Button>
                <Button
                  variant="outline"
                  onPress={() =>
                    postAction({ action: 'syncTodayToSheets' }, 'Synced today orders to Sheets')
                  }
                >
                  <RefreshCw className="h-3.5 w-3.5 text-success" />
                  <span>Sync Today</span>
                </Button>
              </div>
            </Card.Content>
          </Card>

          <div className="space-y-5">
            <Card className="border border-border/80">
              <Card.Header className="px-5 pt-5 pb-2">
                <Card.Title className="text-sm font-bold">
                  Android App OTA Update Policy
                </Card.Title>
                <Card.Description className="text-xs">
                  Latest tablet release target: v2.5.4 (versionCode 23)
                </Card.Description>
              </Card.Header>
              <Card.Content className="px-5 pb-5 space-y-3">
                <div className="grid grid-cols-2 gap-3">
                  <div className="space-y-1">
                    <Label className="text-xs font-medium">Min Version Code</Label>
                    <Input
                      type="number"
                      variant="secondary"
                      fullWidth
                      value={String(otaPolicy.min_version_code)}
                      onChange={(e) =>
                        setOtaPolicy({
                          ...otaPolicy,
                          min_version_code: Number(e.target.value),
                        })
                      }
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs font-medium">Min Version Name</Label>
                    <Input
                      type="text"
                      variant="secondary"
                      fullWidth
                      value={otaPolicy.min_version_name}
                      onChange={(e) =>
                        setOtaPolicy({ ...otaPolicy, min_version_name: e.target.value })
                      }
                    />
                  </div>
                </div>
                <TextField
                  value={otaPolicy.apk_download_url}
                  onChange={(v) => setOtaPolicy({ ...otaPolicy, apk_download_url: v })}
                  fullWidth
                >
                  <Label className="text-xs font-medium">APK Download URL</Label>
                  <Input type="url" variant="secondary" />
                </TextField>

                <Switch
                  isSelected={otaPolicy.force_update}
                  onChange={(v) => setOtaPolicy({ ...otaPolicy, force_update: v })}
                  size="sm"
                >
                  <Switch.Content>
                    <Switch.Control>
                      <Switch.Thumb />
                    </Switch.Control>
                    <Label className="text-xs">Force Mandatory Update on Android Devices</Label>
                  </Switch.Content>
                </Switch>

                <Button
                  variant="primary"
                  fullWidth
                  onPress={() =>
                    postAction({ action: 'saveOtaPolicy', ...otaPolicy }, 'Saved OTA policy')
                  }
                >
                  Save OTA Policy
                </Button>
              </Card.Content>
            </Card>

            <Card className="border border-border/80">
              <Card.Content className="p-4 flex items-center justify-between gap-3">
                <div>
                  <p className="text-sm font-bold">Counter Tablet POS Terminal</p>
                  <p className="text-xs text-muted">
                    Legacy web counter order-punching view moved to /pos
                  </p>
                </div>
                <Link href="/pos">
                  <Button size="sm" variant="outline">
                    <Monitor className="h-3.5 w-3.5 text-accent" />
                    <span>Open /pos</span>
                  </Button>
                </Link>
              </Card.Content>
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
