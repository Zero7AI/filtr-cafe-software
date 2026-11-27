import { NextRequest, NextResponse } from 'next/server';
import { requireAdmin } from '@/lib/admin-auth';
import {
  buildLogId,
  buildMonthlySummary,
  calculateWorkedMinutes,
  DEFAULT_CHECKLIST_TASKS,
  normalizeStaffKey,
} from '@/lib/domain/attendance';
import { getIstDateKey, getIstMonthKey } from '@/lib/domain/ist';
import {
  AttendanceLog,
  ChecklistCompletion,
  ChecklistTask,
  StaffRosterMember,
  StoreGeofenceConfig,
} from '@/lib/domain/types';
import {
  cascadeMemberPolicyToLinkedDevices,
  fetchAllRegisteredDevices,
  getSyncMetadataJson,
  setSyncMetadata,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  const auth = await requireAdmin();
  if (!auth.ok) return auth.response;

  try {
    const sp = request.nextUrl.searchParams;
    const todayKey = getIstDateKey();
    const monthKey = sp.get('month') || getIstMonthKey();
    const dateKey = sp.get('date') || todayKey;

    const [
      roster,
      monthLogs,
      storeConfig,
      customTasks,
      completions,
      devices,
    ] = await Promise.all([
      getSyncMetadataJson<StaffRosterMember[]>('attendance_staff_roster', []),
      getSyncMetadataJson<AttendanceLog[]>(`attendance_month_${monthKey}`, []),
      getSyncMetadataJson<StoreGeofenceConfig>('attendance_store_config', {
        latitude: 0,
        longitude: 0,
        radiusMeters: 100,
        storeLabel: 'FILTR Coffee',
        updatedAt: 0,
      }),
      getSyncMetadataJson<ChecklistTask[]>('checklist_custom_tasks', []),
      getSyncMetadataJson<ChecklistCompletion[]>(`checklist_state_${dateKey}`, []),
      fetchAllRegisteredDevices(),
    ]);

    const summaries = roster.map((member) => {
      const monthlySummary = buildMonthlySummary(
        monthKey,
        member.name,
        monthLogs,
        member.joinedDateKey,
        todayKey,
        member.weeklyOffDayOfWeek
      );
      const todayDay = monthlySummary.days.find((d) => d.dateKey === dateKey);
      const linkedDevices = devices.filter(
        (d) =>
          d.linked_member_id === member.id ||
          (!d.linked_member_id &&
            normalizeStaffKey(d.operator_name) === normalizeStaffKey(member.name))
      );
      return {
        member,
        monthlySummary,
        todayLog: todayDay?.log || null,
        todayStatus: todayDay?.status || 'ABSENT',
        linkedDevicesCount: linkedDevices.length,
      };
    });

    const allTasks: ChecklistTask[] = [
      ...DEFAULT_CHECKLIST_TASKS,
      ...customTasks.map((t) => ({ ...t, isDefault: false })),
    ];

    return NextResponse.json({
      todayKey,
      monthKey,
      dateKey,
      roster,
      summaries,
      monthLogs,
      storeConfig,
      checklistTasks: allTasks,
      checklistCompletions: completions,
    });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Failed to load team data' },
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

    if (action === 'upsertRosterMember') {
      const m = body?.member as Partial<StaffRosterMember>;
      const cleanName = String(m?.name || '').trim();
      if (!cleanName) {
        return NextResponse.json({ error: 'Staff name required' }, { status: 400 });
      }
      const id = m?.id?.trim() || normalizeStaffKey(cleanName);
      const orgRole: 'STAFF' | 'ADMIN' = m?.orgRole === 'ADMIN' ? 'ADMIN' : 'STAFF';
      const updatedMember: StaffRosterMember = {
        id,
        name: cleanName,
        roleTitle: String(m?.roleTitle || 'Barista').trim() || 'Barista',
        shiftLabel: String(m?.shiftLabel || 'Full Day').trim() || 'Full Day',
        weeklyOffDayOfWeek: Number(m?.weeklyOffDayOfWeek || 0),
        joinedDateKey: String(m?.joinedDateKey || getIstDateKey()).trim(),
        isActive: m?.isActive !== false,
        updatedAt: Date.now(),
        colorHex: String(m?.colorHex || '').trim(),
        orgRole,
      };

      const roster = await getSyncMetadataJson<StaffRosterMember[]>(
        'attendance_staff_roster',
        []
      );
      const nextRoster = roster.some((r) => r.id === id)
        ? roster.map((r) => (r.id === id ? updatedMember : r))
        : [...roster, updatedMember];

      await setSyncMetadata('attendance_staff_roster', nextRoster);
      const cascaded = await cascadeMemberPolicyToLinkedDevices(updatedMember);
      return NextResponse.json({
        success: true,
        member: updatedMember,
        cascadedDevices: cascaded,
      });
    }

    if (action === 'adminAttendanceOverride') {
      const staffName = String(body?.staffName || '').trim();
      const dateKey = String(body?.dateKey || getIstDateKey()).trim();
      const mode = String(body?.mode || 'CHECK_IN'); // CHECK_IN | CHECK_OUT | CLEAR
      const notes = String(body?.notes || 'Admin web override').trim();
      if (!staffName || !/^\d{4}-\d{2}-\d{2}$/.test(dateKey)) {
        return NextResponse.json(
          { error: 'staffName and dateKey required' },
          { status: 400 }
        );
      }

      const monthKey = dateKey.slice(0, 7);
      const logs = await getSyncMetadataJson<AttendanceLog[]>(
        `attendance_month_${monthKey}`,
        []
      );
      const logId = buildLogId(dateKey, staffName);
      const now = Date.now();
      const checkInTs = Number(body?.checkInTimestamp || now);
      const checkOutTs = Number(body?.checkOutTimestamp || now);

      let nextLogs = logs.filter((l) => l.id !== logId);
      const existing = logs.find((l) => l.id === logId);

      if (mode === 'CHECK_IN') {
        const entry: AttendanceLog = {
          id: logId,
          dateKey,
          monthKey,
          staffName,
          deviceId: existing?.deviceId || 'web_admin',
          checkInTimestamp: existing?.checkInTimestamp || checkInTs,
          checkInLat: existing?.checkInLat || 0,
          checkInLng: existing?.checkInLng || 0,
          checkInDistanceMeters: existing?.checkInDistanceMeters || 0,
          checkOutTimestamp: 0,
          checkOutLat: 0,
          checkOutLng: 0,
          checkOutDistanceMeters: 0,
          workedMinutes: 0,
          notes,
          updatedAt: now,
        };
        nextLogs = [...nextLogs, entry];
      } else if (mode === 'CHECK_OUT') {
        const startTs = existing?.checkInTimestamp || checkInTs;
        const endTs = Math.max(startTs + 60_000, checkOutTs);
        const entry: AttendanceLog = {
          id: logId,
          dateKey,
          monthKey,
          staffName,
          deviceId: existing?.deviceId || 'web_admin',
          checkInTimestamp: startTs,
          checkInLat: existing?.checkInLat || 0,
          checkInLng: existing?.checkInLng || 0,
          checkInDistanceMeters: existing?.checkInDistanceMeters || 0,
          checkOutTimestamp: endTs,
          checkOutLat: 0,
          checkOutLng: 0,
          checkOutDistanceMeters: 0,
          workedMinutes: calculateWorkedMinutes(startTs, endTs),
          notes,
          updatedAt: now,
        };
        nextLogs = [...nextLogs, entry];
      }

      await setSyncMetadata(`attendance_month_${monthKey}`, nextLogs);
      return NextResponse.json({ success: true });
    }

    if (action === 'saveStoreConfig') {
      const cfg: StoreGeofenceConfig = {
        latitude: Number(body?.latitude || 0),
        longitude: Number(body?.longitude || 0),
        radiusMeters: Math.max(10, Number(body?.radiusMeters || 100)),
        storeLabel: String(body?.storeLabel || 'FILTR Coffee').trim() || 'FILTR Coffee',
        updatedAt: Date.now(),
      };
      await setSyncMetadata('attendance_store_config', cfg);
      return NextResponse.json({ success: true, storeConfig: cfg });
    }

    if (action === 'toggleChecklistCompletion') {
      const dateKey = String(body?.dateKey || getIstDateKey()).trim();
      const taskId = String(body?.taskId || '').trim();
      const isCompleted = Boolean(body?.isCompleted);
      const isOutOfStock = Boolean(body?.isOutOfStock);
      const completedBy =
        String(body?.completedBy || auth.session.operatorName || 'Admin').trim();

      if (!taskId) {
        return NextResponse.json({ error: 'taskId required' }, { status: 400 });
      }
      const completions = await getSyncMetadataJson<ChecklistCompletion[]>(
        `checklist_state_${dateKey}`,
        []
      );
      const now = Date.now();
      const updatedItem: ChecklistCompletion = {
        taskId,
        isCompleted,
        completedTimestamp: isCompleted ? now : 0,
        updatedAt: now,
        completedBy,
        isOutOfStock,
      };
      const next = [
        ...completions.filter((c) => c.taskId !== taskId),
        updatedItem,
      ];
      await setSyncMetadata(`checklist_state_${dateKey}`, next);
      return NextResponse.json({ success: true });
    }

    if (action === 'addCustomTask') {
      const title = String(body?.title || '').trim();
      if (!title) {
        return NextResponse.json({ error: 'Title required' }, { status: 400 });
      }
      const tasks = await getSyncMetadataJson<ChecklistTask[]>(
        'checklist_custom_tasks',
        []
      );
      const newTask: ChecklistTask = {
        id: `custom_task_${Date.now()}`,
        sectionId: String(body?.sectionId || '01_OPENING'),
        sectionName: String(
          body?.sectionName || '01. Opening & Daily Preparation'
        ),
        title,
        description: String(body?.description || '').trim(),
        isDefault: false,
        orderIndex: tasks.length + 100,
        isWeeklyMondayOnly: Boolean(body?.isWeeklyMondayOnly),
        createdAt: Date.now(),
      };
      await setSyncMetadata('checklist_custom_tasks', [...tasks, newTask]);
      return NextResponse.json({ success: true, task: newTask });
    }

    if (action === 'deleteCustomTask') {
      const taskId = String(body?.taskId || '').trim();
      const tasks = await getSyncMetadataJson<ChecklistTask[]>(
        'checklist_custom_tasks',
        []
      );
      await setSyncMetadata(
        'checklist_custom_tasks',
        tasks.filter((t) => t.id !== taskId)
      );
      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: 'Unknown action' }, { status: 400 });
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Team mutation failed' },
      { status: 500 }
    );
  }
}
