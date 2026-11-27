'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Chip, Label, Skeleton, Switch } from '@heroui/react';
import { Check, Clock, Flame, RotateCcw, User } from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import { formatIstTime } from '@/lib/domain/ist';
import { KdsQueueUnitItem, StaffRosterMember } from '@/lib/domain/types';

export default function AdminKdsPage() {
  const { operatorName, refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [queue, setQueue] = useState<KdsQueueUnitItem[]>([]);
  const [roster, setRoster] = useState<StaffRosterMember[]>([]);
  const [selectedStaff, setSelectedStaff] = useState<string>('');
  const [paidOnly, setPaidOnly] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadKds = useCallback(async () => {
    try {
      const res = await fetch(`/api/admin/kds?paidOnly=${paidOnly}`, {
        cache: 'no-store',
      });
      if (res.ok) {
        const data = await res.json();
        setQueue(data.queue || []);
        const activeRoster: StaffRosterMember[] = data.roster || [];
        setRoster(activeRoster);
        setSelectedStaff((prev) => prev || activeRoster[0]?.name || operatorName);
      }
    } finally {
      setLoading(false);
    }
  }, [paidOnly, operatorName]);

  useEffect(() => {
    loadKds();
    const timer = setInterval(loadKds, 4000);
    return () => clearInterval(timer);
  }, [loadKds, refreshTick]);

  const sendAction = async (
    unit: KdsQueueUnitItem,
    action: 'CLAIM' | 'COMPLETE' | 'RELEASE'
  ) => {
    const res = await fetch('/api/admin/kds', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action,
        orderId: unit.orderId,
        itemId: unit.itemId,
        itemName: unit.itemName,
        unitIndex: unit.unitIndex,
        staffName: selectedStaff || operatorName,
      }),
    });
    if (res.ok) {
      pushToast(
        action === 'CLAIM'
          ? `Claimed ${unit.itemName} (#${unit.shortOrderNumber})`
          : action === 'COMPLETE'
            ? `Completed ${unit.itemName} (#${unit.shortOrderNumber})`
            : `Released ${unit.itemName}`,
        'success'
      );
      loadKds();
    } else {
      pushToast('Failed to update KDS unit', 'error');
    }
  };

  const counts = useMemo(() => {
    const making = queue.filter((q) => q.stage === 'MAKING').length;
    const waiting = queue.length - making;
    const paid = queue.filter((q) => q.isPaid).length;
    const unpaid = queue.length - paid;
    return { making, waiting, paid, unpaid };
  }, [queue]);

  return (
    <div className="space-y-5">
      {/* Live Queue Summary */}
      <div className="grid grid-cols-2 xl:grid-cols-4 gap-4">
        <KpiCard
          label="Waiting in Queue"
          value={String(counts.waiting)}
          subtitle="Unclaimed FIFO drinks"
          badgeColor={counts.waiting > 0 ? 'warning' : 'success'}
          icon={<Clock className="h-4 w-4" />}
        />
        <KpiCard
          label="Currently Making"
          value={String(counts.making)}
          subtitle="Claimed by baristas"
          badgeColor="accent"
          icon={<Flame className="h-4 w-4" />}
        />
        <KpiCard
          label="Paid Units"
          value={String(counts.paid)}
          subtitle="Ready for immediate serve"
          badgeColor="success"
        />
        <KpiCard
          label="Unpaid Units"
          value={String(counts.unpaid)}
          subtitle="Awaiting counter payment"
          badgeColor={counts.unpaid > 0 ? 'danger' : 'default'}
        />
      </div>

      {/* Control Bar */}
      <Card className="border border-border/80">
        <Card.Content className="p-4 flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-accent-soft text-accent">
              <Flame className="h-5 w-5" />
            </div>
            <div>
              <h3 className="text-sm font-bold">Live FIFO Barista Queue</h3>
              <p className="text-xs text-muted">
                {queue.length} unit(s) active • Syncing every 4s with baristas
              </p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-4">
            <div className="flex items-center gap-2">
              <User className="h-3.5 w-3.5 text-accent" />
              <select
                value={selectedStaff}
                onChange={(e) => setSelectedStaff(e.target.value)}
                aria-label="Acting barista"
                className="rounded-xl border border-border bg-surface-secondary px-3 py-1.5 text-xs font-semibold text-foreground focus:outline-none focus:border-accent"
              >
                {roster.map((m) => (
                  <option key={m.id} value={m.name}>
                    {m.name} ({m.roleTitle})
                  </option>
                ))}
                <option value={operatorName}>{operatorName} (Admin)</option>
              </select>
            </div>

            <Switch isSelected={paidOnly} onChange={setPaidOnly} size="sm">
              <Switch.Content>
                <Switch.Control>
                  <Switch.Thumb />
                </Switch.Control>
                <Label className="text-xs font-semibold">Paid Orders Only</Label>
              </Switch.Content>
            </Switch>
          </div>
        </Card.Content>
      </Card>

      {loading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-48 rounded-2xl" />
          ))}
        </div>
      ) : queue.length === 0 ? (
        <Card className="border border-border/80">
          <Card.Content className="py-12 text-center flex flex-col items-center gap-2">
            <Check className="h-10 w-10 text-success" />
            <p className="text-sm font-bold">Kitchen queue is clear!</p>
            <p className="text-xs text-muted">New orders from POS will appear here in FIFO order.</p>
          </Card.Content>
        </Card>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          {queue.map((unit) => {
            const isMaking = unit.stage === 'MAKING';
            return (
              <Card
                key={unit.claimKey}
                className="border-2 border-border flex flex-col justify-between"
                style={
                  isMaking && unit.claimedColorHex
                    ? { borderColor: unit.claimedColorHex }
                    : undefined
                }
              >
                <Card.Content className="p-4 flex-1 flex flex-col justify-between space-y-3">
                  <div>
                    <div className="flex items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <Chip size="sm" variant="primary" color="accent">
                          #{unit.queuePosition}
                        </Chip>
                        <span className="text-xs font-mono font-semibold text-muted">
                          Token #{unit.shortOrderNumber}
                        </span>
                      </div>
                      <Chip
                        size="sm"
                        variant="soft"
                        color={unit.isPaid ? 'success' : 'danger'}
                      >
                        {unit.isPaid ? unit.paymentMode : 'UNPAID'}
                      </Chip>
                    </div>

                    <h4 className="mt-3 text-base font-bold leading-snug">{unit.itemName}</h4>
                    <p className="text-xs text-muted mt-0.5 tabular-nums">
                      Cup {unit.unitIndex} of {unit.totalUnits} • {unit.customerName} •{' '}
                      {formatIstTime(unit.orderTimestamp)}
                    </p>

                    {unit.orderNotes ? (
                      <p className="mt-2 rounded-lg bg-warning-soft px-2.5 py-1.5 text-xs text-warning">
                        {unit.orderNotes}
                      </p>
                    ) : null}

                    {isMaking ? (
                      <div
                        className="mt-2 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-bold text-white"
                        style={{ backgroundColor: unit.claimedColorHex || '#16A34A' }}
                      >
                        <span>Making: {unit.claimedByStaff}</span>
                      </div>
                    ) : null}
                  </div>

                  <div className="flex items-center gap-2 pt-3 border-t border-separator">
                    {!isMaking ? (
                      <Button
                        fullWidth
                        size="sm"
                        variant="primary"
                        onPress={() => sendAction(unit, 'CLAIM')}
                      >
                        Start Making
                      </Button>
                    ) : (
                      <>
                        <Button
                          fullWidth
                          size="sm"
                          variant="secondary"
                          onPress={() => sendAction(unit, 'COMPLETE')}
                        >
                          Mark Ready
                        </Button>
                        <Button
                          isIconOnly
                          size="sm"
                          variant="outline"
                          onPress={() => sendAction(unit, 'RELEASE')}
                          aria-label="Release claim"
                        >
                          <RotateCcw className="h-3.5 w-3.5" />
                        </Button>
                      </>
                    )}
                  </div>
                </Card.Content>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
