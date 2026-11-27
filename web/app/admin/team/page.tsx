'use client';

import React, { useEffect, useState, useCallback } from 'react';
import {
  Users,
  CheckCircle2,
  MapPin,
  Plus,
  Trash2,
  Clock,
} from 'lucide-react';
import { useAdminContext } from '@/components/admin/AdminShell';
import Modal from '@/components/admin/Modal';
import { useToast } from '@/components/admin/ToastContext';
import {
  formatDayOfWeekShort,
  formatWorkedDuration,
  MonthlyAttendanceSummary,
} from '@/lib/domain/attendance';
import { getIstDateKey, getIstMonthKey, formatIstTime } from '@/lib/domain/ist';
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
  const { isDark, refreshTick } = useAdminContext();
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

  const toggleChecklist = async (taskId: string, currentDone: boolean, outOfStock: boolean) => {
    const res = await fetch('/api/admin/team', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        action: 'toggleChecklistCompletion',
        dateKey,
        taskId,
        isCompleted: !currentDone,
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

  const cardSurface = isDark
    ? 'border-zinc-800 bg-zinc-900/80'
    : 'border-[#E4E4E7] bg-white shadow-sm';

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          {(
            [
              ['ATTENDANCE', 'Attendance & Geofence', Clock],
              ['ROSTER', `Staff Roster (${roster.length})`, Users],
              ['CHECKLIST', 'Daily Store Checklist', CheckCircle2],
            ] as const
          ).map(([k, lbl, Icon]) => (
            <button
              key={k}
              type="button"
              onClick={() => setTab(k)}
              className={`flex items-center gap-1.5 rounded-xl border px-3.5 py-2 text-xs font-black ${
                tab === k
                  ? 'bg-amber-500 text-zinc-950 border-amber-500'
                  : isDark
                    ? 'bg-zinc-900 border-zinc-800 text-zinc-400'
                    : 'bg-white border-zinc-200'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              <span>{lbl}</span>
            </button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <input
            type="date"
            value={dateKey}
            onChange={(e) => {
              setDateKey(e.target.value);
              setMonthKey(e.target.value.slice(0, 7));
            }}
            className={`rounded-xl border px-2.5 py-1.5 text-xs font-bold ${
              isDark ? 'bg-zinc-900 border-zinc-800 text-white' : 'bg-white border-zinc-200'
            }`}
          />
          <button
            type="button"
            onClick={() => {
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
            className="flex items-center gap-1 rounded-xl bg-amber-500 px-3 py-2 text-xs font-black text-zinc-950"
          >
            <Plus className="h-3.5 w-3.5" />
            <span>Add Staff</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="py-12 text-center text-xs text-zinc-500">Loading team data...</div>
      ) : tab === 'ATTENDANCE' ? (
        <div className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {summaries.map(({ member, monthlySummary, todayLog, todayStatus, linkedDevicesCount }) => (
              <div key={member.id} className={`rounded-2xl border p-4 space-y-3 ${cardSurface}`}>
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <span
                        className="h-3 w-3 rounded-full"
                        style={{ backgroundColor: member.colorHex || '#f59e0b' }}
                      />
                      <h4 className="text-sm font-black">{member.name}</h4>
                      <span className="text-[10px] rounded bg-zinc-800 px-1.5 py-0.5 font-bold text-zinc-300">
                        {member.orgRole}
                      </span>
                    </div>
                    <p className="text-xs text-zinc-400 mt-0.5">
                      {member.roleTitle} • {member.shiftLabel} • Off:{' '}
                      {formatDayOfWeekShort(member.weeklyOffDayOfWeek)} • {linkedDevicesCount} device(s)
                    </p>
                  </div>
                  <span
                    className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                      todayStatus === 'PRESENT' || todayStatus === 'ON_DUTY'
                        ? 'bg-emerald-500/15 text-emerald-500'
                        : todayStatus === 'WEEKLY_OFF'
                          ? 'bg-sky-500/15 text-sky-400'
                          : 'bg-rose-500/15 text-rose-400'
                    }`}
                  >
                    {todayStatus}
                  </span>
                </div>

                <div className="grid grid-cols-3 gap-2 text-center text-xs rounded-xl bg-zinc-950/50 border border-zinc-800 p-2.5">
                  <div>
                    <span className="text-[10px] text-zinc-500 block">Present</span>
                    <span className="font-black text-emerald-500">
                      {monthlySummary.presentDays}d
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-zinc-500 block">Hours</span>
                    <span className="font-black">
                      {formatWorkedDuration(monthlySummary.totalWorkedMinutes)}
                    </span>
                  </div>
                  <div>
                    <span className="text-[10px] text-zinc-500 block">Attendance</span>
                    <span className="font-black text-amber-500">
                      {monthlySummary.attendancePercent}%
                    </span>
                  </div>
                </div>

                {todayLog && (
                  <p className="text-[11px] text-zinc-400">
                    In: {formatIstTime(todayLog.checkInTimestamp)}
                    {todayLog.checkOutTimestamp
                      ? ` • Out: ${formatIstTime(todayLog.checkOutTimestamp)}`
                      : ' (On Duty)'}
                  </p>
                )}

                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => overrideAttendance(member.name, 'CHECK_IN')}
                    className="flex-1 rounded-xl border border-emerald-500/40 bg-emerald-500/10 py-1.5 text-[11px] font-bold text-emerald-400"
                  >
                    Mark Check-In
                  </button>
                  <button
                    type="button"
                    onClick={() => overrideAttendance(member.name, 'CHECK_OUT')}
                    className="flex-1 rounded-xl border border-amber-500/40 bg-amber-500/10 py-1.5 text-[11px] font-bold text-amber-400"
                  >
                    Mark Check-Out
                  </button>
                  <button
                    type="button"
                    onClick={() => overrideAttendance(member.name, 'CLEAR')}
                    className="rounded-xl border border-zinc-700 px-3 py-1.5 text-[11px] font-bold text-zinc-400"
                  >
                    Clear
                  </button>
                </div>
              </div>
            ))}
          </div>

          <form onSubmit={saveGeofence} className={`rounded-2xl border p-5 space-y-3 ${cardSurface}`}>
            <h3 className="text-sm font-black flex items-center gap-2">
              <MapPin className="h-4 w-4 text-emerald-500" />
              Store Geofence Configuration
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
              <input
                type="text"
                value={storeConfig.storeLabel}
                onChange={(e) => setStoreConfig({ ...storeConfig, storeLabel: e.target.value })}
                placeholder="Store Label"
                className="rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
              <input
                type="number"
                step="0.000001"
                value={storeConfig.latitude}
                onChange={(e) =>
                  setStoreConfig({ ...storeConfig, latitude: Number(e.target.value) })
                }
                placeholder="Latitude"
                className="rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
              <input
                type="number"
                step="0.000001"
                value={storeConfig.longitude}
                onChange={(e) =>
                  setStoreConfig({ ...storeConfig, longitude: Number(e.target.value) })
                }
                placeholder="Longitude"
                className="rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
              <input
                type="number"
                value={storeConfig.radiusMeters}
                onChange={(e) =>
                  setStoreConfig({ ...storeConfig, radiusMeters: Number(e.target.value) })
                }
                placeholder="Radius (m)"
                className="rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <button
              type="submit"
              className="rounded-xl bg-amber-500 px-4 py-2 text-xs font-black text-zinc-950"
            >
              Save Geofence
            </button>
          </form>
        </div>
      ) : tab === 'ROSTER' ? (
        <div className={`rounded-2xl border overflow-hidden ${cardSurface}`}>
          <table className="w-full text-xs text-left">
            <thead>
              <tr className="border-b border-zinc-200 dark:border-zinc-800 text-[10px] uppercase text-zinc-500">
                <th className="py-3 px-4">Staff Member</th>
                <th className="py-3 px-4">Title & Shift</th>
                <th className="py-3 px-4">Weekly Off</th>
                <th className="py-3 px-4">Org Role</th>
                <th className="py-3 px-4">Status</th>
                <th className="py-3 px-4 text-right">Edit</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {roster.map((m) => (
                <tr key={m.id}>
                  <td className="py-2.5 px-4 font-bold">{m.name}</td>
                  <td className="py-2.5 px-4 text-zinc-400">
                    {m.roleTitle} • {m.shiftLabel}
                  </td>
                  <td className="py-2.5 px-4">{formatDayOfWeekShort(m.weeklyOffDayOfWeek)}</td>
                  <td className="py-2.5 px-4 font-bold">{m.orgRole}</td>
                  <td className="py-2.5 px-4">
                    <span
                      className={`rounded-md px-2 py-0.5 text-[10px] font-black ${
                        m.isActive ? 'bg-emerald-500/15 text-emerald-500' : 'bg-rose-500/15 text-rose-400'
                      }`}
                    >
                      {m.isActive ? 'ACTIVE' : 'OFFBOARDED'}
                    </span>
                  </td>
                  <td className="py-2.5 px-4 text-right">
                    <button
                      type="button"
                      onClick={() => {
                        setEditMember(m);
                        setMemberModal(true);
                      }}
                      className="text-xs font-bold text-amber-500 hover:underline"
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="space-y-4">
          <form onSubmit={addCustomTask} className="flex gap-2">
            <input
              type="text"
              value={newTaskTitle}
              onChange={(e) => setNewTaskTitle(e.target.value)}
              placeholder="Add custom daily checklist task..."
              className="flex-1 rounded-xl border border-zinc-700 bg-zinc-900 px-3.5 py-2 text-xs text-white"
            />
            <button
              type="submit"
              className="rounded-xl bg-amber-500 px-4 py-2 text-xs font-black text-zinc-950"
            >
              + Add Task
            </button>
          </form>

          <div className="space-y-2">
            {tasks.map((t) => {
              const comp = completions.find((c) => c.taskId === t.id);
              const done = Boolean(comp?.isCompleted);
              return (
                <div
                  key={t.id}
                  className={`rounded-xl border p-3 flex items-center justify-between gap-3 ${cardSurface}`}
                >
                  <label className="flex items-start gap-3 cursor-pointer flex-1">
                    <input
                      type="checkbox"
                      checked={done}
                      onChange={() => toggleChecklist(t.id, done, Boolean(comp?.isOutOfStock))}
                      className="mt-0.5 h-4 w-4 accent-amber-500"
                    />
                    <div>
                      <p className={`text-xs font-bold ${done ? 'line-through text-zinc-500' : ''}`}>
                        {t.title}
                      </p>
                      <p className="text-[10px] text-zinc-400">
                        {t.sectionName}
                        {comp?.completedBy ? ` • Done by ${comp.completedBy}` : ''}
                      </p>
                    </div>
                  </label>
                  {!t.isDefault && (
                    <button
                      type="button"
                      onClick={() => deleteCustomTask(t.id)}
                      className="text-rose-400 p-1"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </button>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      <Modal
        open={memberModal}
        onClose={() => setMemberModal(false)}
        title={editMember.id ? 'Edit Roster Member' : 'Add Staff Member'}
        isDark={isDark}
      >
        <form onSubmit={saveMember} className="space-y-3">
          <div>
            <label className="block text-xs font-bold mb-1">Full Name</label>
            <input
              type="text"
              value={editMember.name || ''}
              onChange={(e) => setEditMember({ ...editMember, name: e.target.value })}
              required
              className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold mb-1">Role Title</label>
              <input
                type="text"
                value={editMember.roleTitle || 'Barista'}
                onChange={(e) => setEditMember({ ...editMember, roleTitle: e.target.value })}
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              />
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">Org Role (RBAC)</label>
              <select
                value={editMember.orgRole || 'STAFF'}
                onChange={(e) =>
                  setEditMember({ ...editMember, orgRole: e.target.value as 'STAFF' | 'ADMIN' })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              >
                <option value="STAFF">STAFF (Locked to Staff Mode)</option>
                <option value="ADMIN">ADMIN</option>
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-bold mb-1">Weekly Off</label>
              <select
                value={editMember.weeklyOffDayOfWeek ?? 0}
                onChange={(e) =>
                  setEditMember({ ...editMember, weeklyOffDayOfWeek: Number(e.target.value) })
                }
                className="w-full rounded-xl border border-zinc-700 bg-zinc-900 px-3 py-2 text-xs text-white"
              >
                <option value={0}>None</option>
                {[1, 2, 3, 4, 5, 6, 7].map((d) => (
                  <option key={d} value={d}>
                    {formatDayOfWeekShort(d)}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold mb-1">KDS Color</label>
              <div className="flex gap-1.5 pt-1">
                {KDS_BARISTA_COLORS.map((hex) => (
                  <button
                    key={hex}
                    type="button"
                    onClick={() => setEditMember({ ...editMember, colorHex: hex })}
                    style={{ backgroundColor: hex }}
                    className={`h-6 w-6 rounded-full ${
                      editMember.colorHex === hex ? 'ring-2 ring-white' : ''
                    }`}
                  />
                ))}
              </div>
            </div>
          </div>
          <label className="flex items-center gap-2 text-xs font-bold">
            <input
              type="checkbox"
              checked={editMember.isActive !== false}
              onChange={(e) => setEditMember({ ...editMember, isActive: e.target.checked })}
            />
            Active Employee (unchecking revokes linked devices)
          </label>
          <button
            type="submit"
            className="w-full rounded-xl bg-amber-500 py-2.5 text-xs font-black text-zinc-950"
          >
            Save Roster Member
          </button>
        </form>
      </Modal>
    </div>
  );
}
