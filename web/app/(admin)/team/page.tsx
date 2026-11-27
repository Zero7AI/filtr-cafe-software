'use client';

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Checkbox,
  Chip,
  Input,
  Label,
  Skeleton,
  Switch,
  Table,
  Tabs,
  TextField,
} from '@heroui/react';
import {
  CheckCircle2,
  Clock,
  MapPin,
  Plus,
  Trash2,
  Users,
} from 'lucide-react';
import AdminModal from '@/components/admin/AdminModal';
import { useAdminContext } from '@/components/admin/AdminShell';
import KpiCard from '@/components/admin/KpiCard';
import { useToast } from '@/components/admin/ToastContext';
import {
  formatDayOfWeekShort,
  formatWorkedDuration,
  MonthlyAttendanceSummary,
} from '@/lib/domain/attendance';
import { formatIstTime, getIstDateKey, getIstMonthKey } from '@/lib/domain/ist';
import { KDS_BARISTA_COLORS } from '@/lib/domain/kds';
import {
  AttendanceLog,
  ChecklistCompletion,
  ChecklistTask,
  StaffRosterMember,
  StoreGeofenceConfig,
} from '@/lib/domain/types';

interface TeamSummaryRow {
  member: StaffRosterMember;
  monthlySummary: MonthlyAttendanceSummary;
  todayLog: AttendanceLog | null;
  todayStatus: string;
  linkedDevicesCount: number;
}

export default function AdminTeamPage() {
  const { refreshTick } = useAdminContext();
  const { pushToast } = useToast();

  const [tab, setTab] = useState<'ATTENDANCE' | 'ROSTER' | 'CHECKLIST'>('ATTENDANCE');
  const [monthKey, setMonthKey] = useState(getIstMonthKey());
  const [dateKey, setDateKey] = useState(getIstDateKey());
  const [summaries, setSummaries] = useState<TeamSummaryRow[]>([]);
  const [roster, setRoster] = useState<StaffRosterMember[]>([]);
  const [storeConfig, setStoreConfig] = useState<StoreGeofenceConfig>({
    latitude: 0,
    longitude: 0,
    radiusMeters: 100,
    storeLabel: 'FILTR Coffee',
    updatedAt: 0,
  });
  const [tasks, setTasks] = useState<ChecklistTask[]>([]);
  const [completions, setCompletions] = useState<ChecklistCompletion[]>([]);
  const [loading, setLoading] = useState(true);

  const [memberModal, setMemberModal] = useState(false);
  const [editMember, setEditMember] = useState<Partial<StaffRosterMember>>({});
  const [newTaskTitle, setNewTaskTitle] = useState('');

  const loadTeam = useCallback(async () => {
    try {
      const res = await fetch(
        `/api/admin/team?month=${monthKey}&date=${dateKey}`,
        { cache: 'no-store' }
      );
      if (res.ok) {
        const d = await res.json();
        setSummaries(d.summaries || []);
        setRoster(d.roster || []);
        if (d.storeConfig) setStoreConfig(d.storeConfig);
        setTasks(d.checklistTasks || []);
        setCompletions(d.checklistCompletions || []);
      }
    } finally {
      setLoading(false);
    }
  }, [monthKey, dateKey]);

  useEffect(() => {
    loadTeam();
  }, [loadTeam, refreshTick]);

  const overrideAttendance = async (
    staffName: string,
    mode: 'CHECK_IN' | 'CHECK_OUT' | 'CLEAR'
  ) => {
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'adminAttendanceOverride',
        staffName,
        dateKey,
        mode,
      }),
    });
    if (res.ok) {
      pushToast(`Attendance updated for ${staffName}`, 'success');
      loadTeam();
    }
  };

  const saveMember = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'upsertRosterMember', member: editMember }),
    });
    if (res.ok) {
      pushToast('Saved staff roster & cascaded device policy', 'success');
      setMemberModal(false);
      loadTeam();
    } else {
      pushToast('Failed to save member', 'error');
    }
  };

  const saveGeofence = async (e: React.FormEvent) => {
    e.preventDefault();
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'saveStoreConfig', ...storeConfig }),
    });
    if (res.ok) {
      pushToast('Saved store geofence config', 'success');
    }
  };

  const toggleChecklist = async (
    taskId: string,
    nextDone: boolean,
    outOfStock: boolean
  ) => {
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'toggleChecklistCompletion',
        dateKey,
        taskId,
        isCompleted: nextDone,
        isOutOfStock: outOfStock,
      }),
    });
    if (res.ok) loadTeam();
  };

  const addCustomTask = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTaskTitle.trim()) return;
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'addCustomTask', title: newTaskTitle }),
    });
    if (res.ok) {
      setNewTaskTitle('');
      pushToast('Added custom checklist task', 'success');
      loadTeam();
    }
  };

  const deleteCustomTask = async (taskId: string) => {
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'deleteCustomTask', taskId }),
    });
    if (res.ok) loadTeam();
  };

  const teamStats = useMemo(() => {
    const active = roster.filter((r) => r.isActive).length;
    const presentToday = summaries.filter(
      (s) => s.todayStatus === 'PRESENT' || s.todayStatus === 'ON_DUTY'
    ).length;
    const doneTasks = tasks.filter((t) =>
      completions.some((c) => c.taskId === t.id && c.isCompleted)
    ).length;
    const checklistPct = tasks.length > 0 ? Math.round((doneTasks / tasks.length) * 100) : 0;
    return { active, presentToday, doneTasks, checklistPct };
  }, [roster, summaries, tasks, completions]);

  const tasksBySection = useMemo(() => {
    const map = new Map<string, ChecklistTask[]>();
    for (const t of tasks) {
      const key = t.sectionName || 'General';
      const arr = map.get(key) || [];
      arr.push(t);
      map.set(key, arr);
    }
    return Array.from(map.entries());
  }, [tasks]);

  const monthDaysCount = summaries[0]?.monthlySummary.days.length || 30;

  return (
    <div className="space-y-5">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <KpiCard
          label="Active Roster"
          value={`${teamStats.active} / ${roster.length}`}
          subtitle="Employees authorized on POS & KDS"
          icon={<Users className="h-4 w-4" />}
        />
        <KpiCard
          label={`Present / On Duty (${dateKey})`}
          value={String(teamStats.presentToday)}
          subtitle="GPS check-in or admin override"
          badgeColor="success"
          icon={<Clock className="h-4 w-4" />}
        />
        <KpiCard
          label={`Store Checklist (${dateKey})`}
          value={`${teamStats.checklistPct}%`}
          subtitle={`${teamStats.doneTasks} of ${tasks.length} tasks done`}
          badgeColor={teamStats.checklistPct >= 80 ? 'success' : 'warning'}
          icon={<CheckCircle2 className="h-4 w-4" />}
        />
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <Tabs
          selectedKey={tab}
          onSelectionChange={(k) => setTab(String(k) as typeof tab)}
          variant="secondary"
          className="w-auto"
        >
          <Tabs.ListContainer>
            <Tabs.List aria-label="Team and checklist sections">
              <Tabs.Tab id="ATTENDANCE" className="text-xs px-3.5">
                Attendance Matrix & Geofence
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="ROSTER" className="text-xs px-3.5">
                Staff Roster ({roster.length})
                <Tabs.Indicator />
              </Tabs.Tab>
              <Tabs.Tab id="CHECKLIST" className="text-xs px-3.5">
                Daily Checklist ({teamStats.doneTasks}/{tasks.length})
                <Tabs.Indicator />
              </Tabs.Tab>
            </Tabs.List>
          </Tabs.ListContainer>
        </Tabs>

        <div className="flex items-center gap-2">
          <Input
            type="date"
            variant="secondary"
            value={dateKey}
            onChange={(e) => {
              setDateKey(e.target.value);
              setMonthKey(e.target.value.slice(0, 7));
            }}
            aria-label="Attendance and checklist date"
            className="text-xs h-9"
          />
          <Button
            size="sm"
            variant="primary"
            onPress={() => {
              setEditMember({
                name: '',
                roleTitle: 'Barista',
                shiftLabel: 'Full Day',
                weeklyOffDayOfWeek: 0,
                joinedDateKey: getIstDateKey(),
                isActive: true,
                colorHex: KDS_BARISTA_COLORS[0],
                orgRole: 'STAFF',
              });
              setMemberModal(true);
            }}
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Staff</span>
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="space-y-3">
          {[1, 2, 3].map((i) => (
            <Skeleton key={i} className="h-24 rounded-2xl" />
          ))}
        </div>
      ) : tab === 'ATTENDANCE' ? (
        <div className="space-y-5">
          {/* Monthly Day-by-Day Attendance Matrix */}
          {summaries.length > 0 && (
            <Card className="border border-border/80">
              <Card.Header className="flex flex-row items-center justify-between px-5 pt-5 pb-2">
                <div>
                  <Card.Title className="text-sm font-bold">
                    Monthly Attendance Matrix ({monthKey})
                  </Card.Title>
                  <Card.Description className="text-xs">
                    P = Completed Shift • OD = On Duty • OFF = Weekly Off • A = Absent
                  </Card.Description>
                </div>
              </Card.Header>
              <Card.Content className="p-3">
                <div className="overflow-x-auto">
                  <table className="w-full text-[11px] text-left border-separate border-spacing-0">
                    <thead>
                      <tr className="border-b border-separator text-muted">
                        <th className="py-2 px-3 font-semibold sticky left-0 bg-surface z-10">
                          Staff
                        </th>
                        <th className="py-2 px-2 text-right font-semibold">%</th>
                        {Array.from({ length: monthDaysCount }, (_, idx) => (
                          <th key={idx} className="py-2 px-1 text-center font-mono">
                            {idx + 1}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {summaries.map(({ member, monthlySummary }) => (
                        <tr key={member.id} className="border-b border-separator/40">
                          <td className="py-2 px-3 font-semibold sticky left-0 bg-surface whitespace-nowrap">
                            <span
                              className="inline-block h-2 w-2 rounded-full mr-1.5"
                              style={{ backgroundColor: member.colorHex || '#f59e0b' }}
                            />
                            {member.name}
                          </td>
                          <td className="py-2 px-2 text-right font-bold tabular-nums text-accent">
                            {monthlySummary.attendancePercent}%
                          </td>
                          {monthlySummary.days.map((d) => {
                            const code =
                              d.status === 'PRESENT'
                                ? 'P'
                                : d.status === 'ON_DUTY'
                                  ? 'OD'
                                  : d.status === 'WEEKLY_OFF'
                                    ? 'O'
                                    : d.status === 'ABSENT'
                                      ? 'A'
                                      : '·';
                            const cls =
                              d.status === 'PRESENT'
                                ? 'bg-success-soft text-success'
                                : d.status === 'ON_DUTY'
                                  ? 'bg-accent-soft text-accent'
                                  : d.status === 'WEEKLY_OFF'
                                    ? 'bg-default text-muted'
                                    : d.status === 'ABSENT'
                                      ? 'bg-danger-soft text-danger'
                                      : 'text-muted/40';
                            return (
                              <td key={d.dayOfMonth} className="py-1.5 px-0.5 text-center">
                                <span
                                  className={`inline-flex h-5 w-5 items-center justify-center rounded text-[10px] font-bold ${cls}`}
                                  title={`${member.name} • ${d.dateKey}: ${d.status}`}
                                >
                                  {code}
                                </span>
                              </td>
                            );
                          })}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </Card.Content>
            </Card>
          )}

          {/* Individual Staff Override Cards */}
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {summaries.map(({ member, monthlySummary, todayLog, todayStatus, linkedDevicesCount }) => (
              <Card key={member.id} className="border border-border/80">
                <Card.Content className="p-4 space-y-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2">
                        <span
                          className="h-3 w-3 rounded-full"
                          style={{ backgroundColor: member.colorHex || '#f59e0b' }}
                        />
                        <h4 className="text-sm font-bold">{member.name}</h4>
                        <Chip size="sm" variant="soft" color="default">
                          {member.orgRole}
                        </Chip>
                      </div>
                      <p className="text-xs text-muted mt-0.5">
                        {member.roleTitle} • {member.shiftLabel} • Off:{' '}
                        {formatDayOfWeekShort(member.weeklyOffDayOfWeek)} • {linkedDevicesCount}{' '}
                        device(s)
                      </p>
                    </div>
                    <Chip
                      size="sm"
                      variant="soft"
                      color={
                        todayStatus === 'PRESENT' || todayStatus === 'ON_DUTY'
                          ? 'success'
                          : todayStatus === 'WEEKLY_OFF'
                            ? 'default'
                            : 'danger'
                      }
                    >
                      {todayStatus}
                    </Chip>
                  </div>

                  <div className="grid grid-cols-3 gap-2 text-center text-xs rounded-xl bg-surface-secondary p-2.5 tabular-nums">
                    <div>
                      <span className="text-[10px] text-muted block">Present</span>
                      <span className="font-bold text-success">
                        {monthlySummary.presentDays}d
                      </span>
                    </div>
                    <div>
                      <span className="text-[10px] text-muted block">Hours</span>
                      <span className="font-bold">
                        {formatWorkedDuration(monthlySummary.totalWorkedMinutes)}
                      </span>
                    </div>
                    <div>
                      <span className="text-[10px] text-muted block">Attendance</span>
                      <span className="font-bold text-accent">
                        {monthlySummary.attendancePercent}%
                      </span>
                    </div>
                  </div>

                  {todayLog ? (
                    <p className="text-[11px] text-muted tabular-nums">
                      In: {formatIstTime(todayLog.checkInTimestamp)}
                      {todayLog.checkOutTimestamp
                        ? ` • Out: ${formatIstTime(todayLog.checkOutTimestamp)}`
                        : ' (On Duty)'}
                    </p>
                  ) : null}

                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      fullWidth
                      onPress={() => overrideAttendance(member.name, 'CHECK_IN')}
                    >
                      Check-In
                    </Button>
                    <Button
                      size="sm"
                      variant="secondary"
                      fullWidth
                      onPress={() => overrideAttendance(member.name, 'CHECK_OUT')}
                    >
                      Check-Out
                    </Button>
                    <Button
                      size="sm"
                      variant="danger-soft"
                      onPress={() => overrideAttendance(member.name, 'CLEAR')}
                    >
                      Clear
                    </Button>
                  </div>
                </Card.Content>
              </Card>
            ))}
          </div>

          <Card className="border border-border/80">
            <Card.Header className="px-5 pt-5 pb-2">
              <Card.Title className="text-sm font-bold flex items-center gap-2">
                <MapPin className="h-4 w-4 text-success" />
                Store Geofence Configuration
              </Card.Title>
              <Card.Description className="text-xs">
                Enforced for GPS Check-In / Check-Out on staff Android tablets
              </Card.Description>
            </Card.Header>
            <Card.Content className="px-5 pb-5">
              <form onSubmit={saveGeofence} className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
                  <div className="space-y-1">
                    <Label className="text-xs font-medium">Store Label</Label>
                    <Input
                      type="text"
                      variant="secondary"
                      fullWidth
                      value={storeConfig.storeLabel}
                      onChange={(e) =>
                        setStoreConfig({ ...storeConfig, storeLabel: e.target.value })
                      }
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs font-medium">Latitude</Label>
                    <Input
                      type="number"
                      step="0.000001"
                      variant="secondary"
                      fullWidth
                      value={String(storeConfig.latitude)}
                      onChange={(e) =>
                        setStoreConfig({ ...storeConfig, latitude: Number(e.target.value) })
                      }
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs font-medium">Longitude</Label>
                    <Input
                      type="number"
                      step="0.000001"
                      variant="secondary"
                      fullWidth
                      value={String(storeConfig.longitude)}
                      onChange={(e) =>
                        setStoreConfig({ ...storeConfig, longitude: Number(e.target.value) })
                      }
                    />
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs font-medium">Radius (meters)</Label>
                    <Input
                      type="number"
                      variant="secondary"
                      fullWidth
                      value={String(storeConfig.radiusMeters)}
                      onChange={(e) =>
                        setStoreConfig({ ...storeConfig, radiusMeters: Number(e.target.value) })
                      }
                    />
                  </div>
                </div>
                <Button type="submit" variant="primary" size="sm">
                  Save Geofence
                </Button>
              </form>
            </Card.Content>
          </Card>
        </div>
      ) : tab === 'ROSTER' ? (
        <Card className="border border-border/80">
          <Card.Content className="p-3">
            <Table variant="secondary">
              <Table.ScrollContainer>
                <Table.Content aria-label="Staff roster table">
                  <Table.Header>
                    <Table.Column isRowHeader>Staff Member</Table.Column>
                    <Table.Column>Title & Shift</Table.Column>
                    <Table.Column>Weekly Off</Table.Column>
                    <Table.Column>Org Role (RBAC)</Table.Column>
                    <Table.Column>Status</Table.Column>
                    <Table.Column className="text-right">Edit</Table.Column>
                  </Table.Header>
                  <Table.Body>
                    {roster.map((m) => (
                      <Table.Row key={m.id} id={m.id}>
                        <Table.Cell className="font-semibold">
                          <span
                            className="inline-block h-2.5 w-2.5 rounded-full mr-2"
                            style={{ backgroundColor: m.colorHex || '#f59e0b' }}
                          />
                          {m.name}
                        </Table.Cell>
                        <Table.Cell className="text-xs text-muted">
                          {m.roleTitle} • {m.shiftLabel}
                        </Table.Cell>
                        <Table.Cell className="text-xs">
                          {formatDayOfWeekShort(m.weeklyOffDayOfWeek)}
                        </Table.Cell>
                        <Table.Cell>
                          <Chip
                            size="sm"
                            variant="soft"
                            color={m.orgRole === 'ADMIN' ? 'accent' : 'default'}
                          >
                            {m.orgRole}
                          </Chip>
                        </Table.Cell>
                        <Table.Cell>
                          <Chip
                            size="sm"
                            variant="soft"
                            color={m.isActive ? 'success' : 'danger'}
                          >
                            {m.isActive ? 'ACTIVE' : 'OFFBOARDED'}
                          </Chip>
                        </Table.Cell>
                        <Table.Cell className="text-right">
                          <Button
                            size="sm"
                            variant="ghost"
                            onPress={() => {
                              setEditMember(m);
                              setMemberModal(true);
                            }}
                          >
                            Edit
                          </Button>
                        </Table.Cell>
                      </Table.Row>
                    ))}
                  </Table.Body>
                </Table.Content>
              </Table.ScrollContainer>
            </Table>
          </Card.Content>
        </Card>
      ) : (
        <div className="space-y-4">
          <Card className="border border-border/80">
            <Card.Content className="p-4 space-y-3">
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs">
                <span className="font-bold">
                  Daily Progress: {teamStats.doneTasks} / {tasks.length} ({teamStats.checklistPct}%)
                </span>
              </div>
              <div className="h-2 rounded-full bg-surface-tertiary overflow-hidden">
                <div
                  className="h-full rounded-full bg-success transition-all"
                  style={{ width: `${teamStats.checklistPct}%` }}
                />
              </div>

              <form onSubmit={addCustomTask} className="flex gap-2 pt-2">
                <Input
                  type="text"
                  variant="secondary"
                  fullWidth
                  value={newTaskTitle}
                  onChange={(e) => setNewTaskTitle(e.target.value)}
                  placeholder="Add custom daily checklist task..."
                  className="text-xs"
                />
                <Button type="submit" variant="primary" size="sm">
                  + Add Task
                </Button>
              </form>
            </Card.Content>
          </Card>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            {tasksBySection.map(([sectionName, sectionTasks]) => (
              <Card key={sectionName} className="border border-border/80">
                <Card.Header className="px-5 pt-4 pb-2 border-b border-separator">
                  <Card.Title className="text-xs font-bold uppercase tracking-wider text-accent">
                    {sectionName}
                  </Card.Title>
                </Card.Header>
                <Card.Content className="p-4 space-y-2.5">
                  {sectionTasks.map((t) => {
                    const comp = completions.find((c) => c.taskId === t.id);
                    const done = Boolean(comp?.isCompleted);
                    return (
                      <div
                        key={t.id}
                        className="flex items-start justify-between gap-3 rounded-xl border border-separator bg-surface-secondary/40 p-3"
                      >
                        <Checkbox
                          isSelected={done}
                          onChange={(next) =>
                            toggleChecklist(t.id, next, Boolean(comp?.isOutOfStock))
                          }
                        >
                          <Checkbox.Control>
                            <Checkbox.Indicator />
                          </Checkbox.Control>
                          <Checkbox.Content>
                            <p className={`text-xs font-semibold ${done ? 'line-through text-muted' : ''}`}>
                              {t.title}
                            </p>
                            {comp?.completedBy ? (
                              <p className="text-[10px] text-muted">
                                Done by {comp.completedBy}
                              </p>
                            ) : null}
                          </Checkbox.Content>
                        </Checkbox>
                        {!t.isDefault && (
                          <Button
                            isIconOnly
                            size="sm"
                            variant="danger-soft"
                            onPress={() => deleteCustomTask(t.id)}
                            aria-label={`Delete task ${t.title}`}
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        )}
                      </div>
                    );
                  })}
                </Card.Content>
              </Card>
            ))}
          </div>
        </div>
      )}

      <AdminModal
        isOpen={memberModal}
        onClose={() => setMemberModal(false)}
        title={editMember.id ? 'Edit Roster Member' : 'Add Staff Member'}
      >
        <form onSubmit={saveMember} className="space-y-3">
          <TextField
            value={editMember.name || ''}
            onChange={(v) => setEditMember({ ...editMember, name: v })}
            isRequired
            fullWidth
          >
            <Label className="text-xs font-medium">Full Name</Label>
            <Input variant="secondary" />
          </TextField>

          <div className="grid grid-cols-2 gap-3">
            <TextField
              value={editMember.roleTitle || 'Barista'}
              onChange={(v) => setEditMember({ ...editMember, roleTitle: v })}
              fullWidth
            >
              <Label className="text-xs font-medium">Role Title</Label>
              <Input variant="secondary" />
            </TextField>
            <div className="space-y-1">
              <Label className="text-xs font-medium">Org Role (RBAC)</Label>
              <select
                value={editMember.orgRole || 'STAFF'}
                onChange={(e) =>
                  setEditMember({ ...editMember, orgRole: e.target.value as 'STAFF' | 'ADMIN' })
                }
                className="w-full rounded-xl border border-border bg-surface-secondary px-3 py-2 text-xs text-foreground"
              >
                <option value="STAFF">STAFF (Locked to Staff Mode)</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label className="text-xs font-medium">Weekly Off</Label>
              <select
                value={editMember.weeklyOffDayOfWeek ?? 0}
                onChange={(e) =>
                  setEditMember({ ...editMember, weeklyOffDayOfWeek: Number(e.target.value) })
                }
                className="w-full rounded-xl border border-border bg-surface-secondary px-3 py-2 text-xs text-foreground"
              >
                <option value={0}>None</option>
                {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                  <option key={d} value={d}>
                    {formatDayOfWeekShort(d)}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label className="text-xs font-medium">KDS Color</Label>
              <div className="flex gap-1.5 pt-1.5">
                {KDS_BARISTA_COLORS.map((hex) => (
                  <button
                    key={hex}
                    type="button"
                    onClick={() => setEditMember({ ...editMember, colorHex: hex })}
                    style={{ backgroundColor: hex }}
                    aria-label={`Select color ${hex}`}
                    className={`h-6 w-6 rounded-full ${
                      editMember.colorHex === hex ? 'ring-2 ring-foreground ring-offset-2 ring-offset-surface' : ''
                    }`}
                  />
                ))}
              </div>
            </div>
          </div>

          <Switch
            isSelected={editMember.isActive !== false}
            onChange={(v) => setEditMember({ ...editMember, isActive: v })}
            size="sm"
          >
            <Switch.Content>
              <Switch.Control>
                <Switch.Thumb />
              </Switch.Control>
              <Label className="text-xs font-medium">
                Active Employee (unchecking revokes linked devices)
              </Label>
            </Switch.Content>
          </Switch>

          <Button type="submit" variant="primary" fullWidth>
            Save Roster Member
          </Button>
        </form>
      </AdminModal>
    </div>
  );
}
