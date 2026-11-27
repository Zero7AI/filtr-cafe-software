import { getDaysInMonthKey, getIstDayOfWeek } from './ist';
import { AttendanceLog, ChecklistTask } from './types';

export type DayAttendanceStatus =
  | 'PRESENT'
  | 'ON_DUTY'
  | 'ABSENT'
  | 'WEEKLY_OFF'
  | 'NOT_JOINED'
  | 'UPCOMING';

export interface AttendanceCalendarDay {
  dateKey: string;
  dayOfMonth: number;
  status: DayAttendanceStatus;
  log: AttendanceLog | null;
}

export interface MonthlyAttendanceSummary {
  monthKey: string;
  staffName: string;
  days: AttendanceCalendarDay[];
  presentDays: number;
  absentDays: number;
  completedShifts: number;
  totalWorkedMinutes: number;
  attendancePercent: number;
}

export function normalizeStaffKey(staffName: string): string {
  const cleaned = (staffName || '')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
  return cleaned || 'staff';
}

export function buildLogId(dateKey: string, staffName: string): string {
  return `${dateKey}_${normalizeStaffKey(staffName)}`;
}

export function calculateWorkedMinutes(checkInMs: number, checkOutMs: number): number {
  if (!checkInMs || !checkOutMs || checkOutMs <= checkInMs) return 0;
  return Math.floor((checkOutMs - checkInMs) / 60000);
}

export function formatWorkedDuration(minutes: number): string {
  const safe = Math.max(0, Math.floor(minutes));
  const h = Math.floor(safe / 60);
  const m = safe % 60;
  if (h > 0 && m > 0) return `${h}h ${m}m`;
  if (h > 0) return `${h}h`;
  return `${m}m`;
}

export function formatDayOfWeekShort(dayOfWeek: number): string {
  switch (dayOfWeek) {
    case 1:
      return 'Sun';
    case 2:
      return 'Mon';
    case 3:
      return 'Tue';
    case 4:
      return 'Wed';
    case 5:
      return 'Thu';
    case 6:
      return 'Fri';
    case 7:
      return 'Sat';
    default:
      return 'None';
  }
}

export function buildMonthlySummary(
  monthKey: string,
  staffName: string,
  logs: AttendanceLog[],
  trackingStartDateKey: string | null | undefined,
  todayDateKey: string,
  weeklyOffDayOfWeek: number = 0
): MonthlyAttendanceSummary {
  const daysInMonth = getDaysInMonthKey(monthKey);
  const targetKey = normalizeStaffKey(staffName);

  const logsByDate = new Map<string, AttendanceLog>();
  for (const log of logs) {
    if (log.monthKey !== monthKey) continue;
    if (normalizeStaffKey(log.staffName) !== targetKey) continue;
    const existing = logsByDate.get(log.dateKey);
    if (!existing || log.updatedAt > existing.updatedAt) {
      logsByDate.set(log.dateKey, log);
    }
  }

  const monthStartKey = `${monthKey}-01`;
  const todayMonthKey = todayDateKey.slice(0, 7);
  const effectiveStartKey =
    !trackingStartDateKey || !trackingStartDateKey.trim()
      ? monthStartKey
      : monthKey < todayMonthKey
        ? monthStartKey
        : trackingStartDateKey > monthStartKey
          ? trackingStartDateKey
          : monthStartKey;

  let presentDays = 0;
  let absentDays = 0;
  let completedShifts = 0;
  let totalWorkedMinutes = 0;
  const days: AttendanceCalendarDay[] = [];

  for (let day = 1; day <= daysInMonth; day++) {
    const dateKey = `${monthKey}-${String(day).padStart(2, '0')}`;
    const log = logsByDate.get(dateKey) || null;
    const dow = getIstDayOfWeek(dateKey);

    let status: DayAttendanceStatus;
    if (dateKey > todayDateKey) {
      status = 'UPCOMING';
    } else if (log && log.checkOutTimestamp > 0) {
      presentDays++;
      completedShifts++;
      const worked =
        log.workedMinutes > 0
          ? log.workedMinutes
          : calculateWorkedMinutes(log.checkInTimestamp, log.checkOutTimestamp);
      totalWorkedMinutes += worked;
      status = 'PRESENT';
    } else if (log && log.checkInTimestamp > 0) {
      presentDays++;
      status = 'ON_DUTY';
    } else if (dateKey < effectiveStartKey) {
      status = 'NOT_JOINED';
    } else if (weeklyOffDayOfWeek >= 1 && weeklyOffDayOfWeek <= 7 && dow === weeklyOffDayOfWeek) {
      status = 'WEEKLY_OFF';
    } else {
      absentDays++;
      status = 'ABSENT';
    }

    days.push({ dateKey, dayOfMonth: day, status, log });
  }

  const totalEval = presentDays + absentDays;
  const attendancePercent = totalEval === 0 ? 0 : Math.floor((presentDays * 100) / totalEval);

  return {
    monthKey,
    staffName,
    days,
    presentDays,
    absentDays,
    completedShifts,
    totalWorkedMinutes,
    attendancePercent,
  };
}

export const DEFAULT_CHECKLIST_TASKS: ChecklistTask[] = [
  // 01_OPENING (9)
  { id: 'task_open_1', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Switch ON counter lights & verify all lights are working', description: 'Check all bulbs, strip lights, and outdoor brand signage', isDefault: true, orderIndex: 1, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_2', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Check refrigerator and deep freezer are ON & working', description: 'Verify cooling temperature display and power supply indicator', isDefault: true, orderIndex: 2, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_3', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Deep Freezer Check: Ice cream, French fries, Ice, Mozzarella cheese, Whipped cream, Sweet corn', description: 'Ensure packaging is intact and required stock is readily accessible', isDefault: true, orderIndex: 3, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_4', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Refrigerator Check: Milk, Butter, Paneer, Cucumber, Onion', description: 'Take out butter from refrigerator as per daily required quantity', isDefault: true, orderIndex: 4, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_5', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Prepare milk mixture (2 Litres milk + 500g whipped cream)', description: 'Follow exact recipe ratio. Mix thoroughly and store cold', isDefault: true, orderIndex: 5, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_6', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Prepare coffee extract (1kg coffee + 5 Litres water)', description: 'Brew as per standard extraction SOP', isDefault: true, orderIndex: 6, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_7', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Prepare coffee recipe mixture ready for billing rush', description: 'Check consistency and label containers with date/time', isDefault: true, orderIndex: 7, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_8', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Fill all sauce dispenser bottles & seasonings', description: 'Chocolate, caramel, peri-peri, mayo and mustard bottles filled', isDefault: true, orderIndex: 8, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_open_9', sectionId: '01_OPENING', sectionName: '01. Opening & Daily Preparation', title: 'Check cucumber and onion cutting / availability', description: 'Freshly sliced vegetables ready for sandwich assembly', isDefault: true, orderIndex: 9, isWeeklyMondayOnly: false, createdAt: 0 },

  // 02_CLEANING (8)
  { id: 'task_clean_1', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Clean main kitchen counter & sandwich preparation area', description: 'Sanitize chopping boards, slicers, and prep surfaces', isDefault: true, orderIndex: 1, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_2', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Clean all shelves & keep all ingredients in proper positions', description: 'Organize syrup bottles, coffee beans, cups and lids', isDefault: true, orderIndex: 2, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_3', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Clean stove, burners, fryer & surrounding area', description: 'Wipe off oil splatters and clean drip trays', isDefault: true, orderIndex: 3, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_4', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Keep dustbins clean, emptied if full & replace liner', description: 'Maintain separate wet and dry waste bins', isDefault: true, orderIndex: 4, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_5', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Sweep & mop the kitchen floor — keep completely dry', description: 'Prevent slip hazards around the prep and billing stations', isDefault: true, orderIndex: 5, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_6', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Outside cleaning: Sweep floor (Jhadu), clean tables & chairs', description: 'Sanitize customer dining tables and outside seating area', isDefault: true, orderIndex: 6, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_7', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Check front entrance / road: Pick up plastics & waste', description: 'Keep store perimeter clean and welcoming', isDefault: true, orderIndex: 7, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_clean_8', sectionId: '02_CLEANING', sectionName: '02. Cleaning & Store Hygiene', title: 'Clean all fans & ventilation filters (Every Monday)', description: 'Weekly deep clean of wall fans, exhaust fans, and AC vents', isDefault: true, orderIndex: 8, isWeeklyMondayOnly: true, createdAt: 0 },

  // 03_ROUTINE (6)
  { id: 'task_routine_1', sectionId: '03_ROUTINE', sectionName: '03. Routine Store Checks', title: '12:00 PM Check — Kitchen, counter & dining tables clean', description: 'Afternoon peak preparation: wipe counters and clear plates', isDefault: true, orderIndex: 1, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_routine_2', sectionId: '03_ROUTINE', sectionName: '03. Routine Store Checks', title: '2:00 PM Check — Check milk/ice stock & sweep dining area', description: 'Mid-day replenishment check and dustbin clearance', isDefault: true, orderIndex: 2, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_routine_3', sectionId: '03_ROUTINE', sectionName: '03. Routine Store Checks', title: '5:00 PM Check — Evening peak preparation & sauce fill', description: 'Prep counter for evening coffee orders', isDefault: true, orderIndex: 3, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_routine_4', sectionId: '03_ROUTINE', sectionName: '03. Routine Store Checks', title: '7:00 PM Check — Wipe tables & pick up outer road waste', description: 'Maintain clean customer entrance and outdoor sitting area', isDefault: true, orderIndex: 4, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_routine_5', sectionId: '03_ROUTINE', sectionName: '03. Routine Store Checks', title: '8:30 PM Check — Kitchen clean & stock levels review', description: 'Pre-closing prep and inventory check for night rush', isDefault: true, orderIndex: 5, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_routine_6', sectionId: '03_ROUTINE', sectionName: '03. Routine Store Checks', title: '9:00 PM Check — Final evening hygiene & prep stations', description: 'Clear used cups, wipe coffee machines and steamer wands', isDefault: true, orderIndex: 6, isWeeklyMondayOnly: false, createdAt: 0 },

  // 04_CLOSING (5)
  { id: 'task_close_1', sectionId: '04_CLOSING', sectionName: '04. Closing Checklist', title: 'Store all refrigerator & deep freezer items safely inside', description: 'Ice cream, fries, cheese, cream, milk, paneer, butter properly stored', isDefault: true, orderIndex: 1, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_close_2', sectionId: '04_CLOSING', sectionName: '04. Closing Checklist', title: 'CRITICAL: Refrigerator and deep freezer switches remain ON', description: 'DO NOT switch off cooling appliances overnight', isDefault: true, orderIndex: 2, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_close_3', sectionId: '04_CLOSING', sectionName: '04. Closing Checklist', title: 'Switch OFF all counter lights, display signage, fans & machines', description: 'Espresso grinder, sandwich press, induction stove OFF', isDefault: true, orderIndex: 3, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_close_4', sectionId: '04_CLOSING', sectionName: '04. Closing Checklist', title: 'Kitchen deep cleaned, dustbins emptied & floor mopped dry', description: 'Cover all opened sauce containers and dry all utensils', isDefault: true, orderIndex: 4, isWeeklyMondayOnly: false, createdAt: 0 },
  { id: 'task_close_5', sectionId: '04_CLOSING', sectionName: '04. Closing Checklist', title: 'Water taps tightly closed & main entrance shutter/door locked', description: 'Verify plumbing is safe and cash drawer / premises secured', isDefault: true, orderIndex: 5, isWeeklyMondayOnly: false, createdAt: 0 },
];
