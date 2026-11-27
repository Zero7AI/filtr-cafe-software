export const IST_OFFSET_MS = 5.5 * 60 * 60 * 1000;

export type DateRangePreset =
  | 'TODAY'
  | 'YESTERDAY'
  | 'LAST_7_DAYS'
  | 'THIS_MONTH'
  | 'MONTH'
  | 'CUSTOM';

function toIstShiftedDate(ms: number = Date.now()): Date {
  return new Date(ms + IST_OFFSET_MS);
}

export function getIstDateKey(ms: number = Date.now()): string {
  const d = toIstShiftedDate(ms);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function getIstCompactDate(ms: number = Date.now()): string {
  const d = toIstShiftedDate(ms);
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, '0');
  const day = String(d.getUTCDate()).padStart(2, '0');
  return `${y}${m}${day}`;
}

export function getIstMonthKey(ms: number = Date.now()): string {
  return getIstDateKey(ms).slice(0, 7);
}

export function getDaysInMonthKey(monthKey: string): number {
  const [yStr, mStr] = monthKey.split('-');
  const y = Number(yStr);
  const m = Number(mStr);
  if (!Number.isFinite(y) || !Number.isFinite(m) || m < 1 || m > 12) {
    return 30;
  }
  return new Date(Date.UTC(y, m, 0)).getUTCDate();
}

export function getPreviousMonthKey(monthKey: string): string {
  const [yStr, mStr] = monthKey.split('-');
  const y = Number(yStr);
  const m = Number(mStr);
  if (!Number.isFinite(y) || !Number.isFinite(m)) return getIstMonthKey();
  const d = new Date(Date.UTC(y, m - 2, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

export function getNextMonthKey(monthKey: string): string {
  const [yStr, mStr] = monthKey.split('-');
  const y = Number(yStr);
  const m = Number(mStr);
  if (!Number.isFinite(y) || !Number.isFinite(m)) return getIstMonthKey();
  const d = new Date(Date.UTC(y, m, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
}

const MONTH_NAMES = [
  'January',
  'February',
  'March',
  'April',
  'May',
  'June',
  'July',
  'August',
  'September',
  'October',
  'November',
  'December',
];

const MONTH_SHORT_NAMES = [
  'Jan',
  'Feb',
  'Mar',
  'Apr',
  'May',
  'Jun',
  'Jul',
  'Aug',
  'Sep',
  'Oct',
  'Nov',
  'Dec',
];

export function formatMonthDisplay(monthKey: string): string {
  const [yStr, mStr] = monthKey.split('-');
  const m = Number(mStr);
  if (!yStr || !Number.isFinite(m) || m < 1 || m > 12) return monthKey;
  return `${MONTH_NAMES[m - 1]} ${yStr}`;
}

export function formatMonthShort(monthKey: string): string {
  const [yStr, mStr] = monthKey.split('-');
  const m = Number(mStr);
  if (!yStr || !Number.isFinite(m) || m < 1 || m > 12) return monthKey;
  return `${MONTH_SHORT_NAMES[m - 1]} ${yStr.slice(-2)}`;
}

export function getIstDayBoundsMs(dateKey: string): { startMs: number; endMs: number } {
  const parts = dateKey.split('-').map(Number);
  if (parts.length !== 3 || parts.some((n) => !Number.isFinite(n))) {
    const fallback = getIstDateKey();
    return getIstDayBoundsMs(fallback);
  }
  const [y, m, d] = parts;
  const startUtc = Date.UTC(y, m - 1, d, 0, 0, 0, 0) - IST_OFFSET_MS;
  const endUtc = Date.UTC(y, m - 1, d, 23, 59, 59, 999) - IST_OFFSET_MS;
  return { startMs: startUtc, endMs: endUtc };
}

export function addDaysToDateKey(dateKey: string, deltaDays: number): string {
  const [y, m, d] = dateKey.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d + deltaDays));
  const yy = dt.getUTCFullYear();
  const mm = String(dt.getUTCMonth() + 1).padStart(2, '0');
  const dd = String(dt.getUTCDate()).padStart(2, '0');
  return `${yy}-${mm}-${dd}`;
}

export function getIstDayOfWeek(dateKey: string): number {
  // Returns 1=Sunday .. 7=Saturday (matching java.util.Calendar.SUNDAY..SATURDAY)
  const [y, m, d] = dateKey.split('-').map(Number);
  const dt = new Date(Date.UTC(y, m - 1, d));
  return dt.getUTCDay() + 1;
}

export function enumerateDateKeys(startDateKey: string, endDateKey: string): string[] {
  const keys: string[] = [];
  let cur = startDateKey;
  let guard = 0;
  while (cur <= endDateKey && guard < 400) {
    keys.push(cur);
    cur = addDaysToDateKey(cur, 1);
    guard++;
  }
  return keys;
}

export function getIstRangeBoundsMs(
  range: DateRangePreset,
  opts?: { monthKey?: string; startDate?: string; endDate?: string }
): { startMs: number; endMs: number; daysCount: number; dateKeys: string[] } {
  const todayKey = getIstDateKey();

  switch (range) {
    case 'TODAY': {
      const { startMs, endMs } = getIstDayBoundsMs(todayKey);
      return { startMs, endMs, daysCount: 1, dateKeys: [todayKey] };
    }
    case 'YESTERDAY': {
      const yKey = addDaysToDateKey(todayKey, -1);
      const { startMs, endMs } = getIstDayBoundsMs(yKey);
      return { startMs, endMs, daysCount: 1, dateKeys: [yKey] };
    }
    case 'LAST_7_DAYS': {
      const startKey = addDaysToDateKey(todayKey, -6);
      const { startMs } = getIstDayBoundsMs(startKey);
      const { endMs } = getIstDayBoundsMs(todayKey);
      const dateKeys = enumerateDateKeys(startKey, todayKey);
      return { startMs, endMs, daysCount: dateKeys.length, dateKeys };
    }
    case 'THIS_MONTH': {
      const monthKey = todayKey.slice(0, 7);
      const startKey = `${monthKey}-01`;
      const { startMs } = getIstDayBoundsMs(startKey);
      const { endMs } = getIstDayBoundsMs(todayKey);
      const dateKeys = enumerateDateKeys(startKey, todayKey);
      return { startMs, endMs, daysCount: dateKeys.length, dateKeys };
    }
    case 'MONTH': {
      const monthKey = opts?.monthKey && /^\d{4}-\d{2}$/.test(opts.monthKey)
        ? opts.monthKey
        : todayKey.slice(0, 7);
      const daysInMonth = getDaysInMonthKey(monthKey);
      const startKey = `${monthKey}-01`;
      const endKey = `${monthKey}-${String(daysInMonth).padStart(2, '0')}`;
      const { startMs } = getIstDayBoundsMs(startKey);
      const { endMs } = getIstDayBoundsMs(endKey);
      const dateKeys = enumerateDateKeys(startKey, endKey);
      return { startMs, endMs, daysCount: daysInMonth, dateKeys };
    }
    case 'CUSTOM': {
      const rawStart = opts?.startDate && /^\d{4}-\d{2}-\d{2}$/.test(opts.startDate)
        ? opts.startDate
        : todayKey;
      const rawEnd = opts?.endDate && /^\d{4}-\d{2}-\d{2}$/.test(opts.endDate)
        ? opts.endDate
        : rawStart;
      const startKey = rawStart <= rawEnd ? rawStart : rawEnd;
      const endKey = rawStart <= rawEnd ? rawEnd : rawStart;
      const { startMs } = getIstDayBoundsMs(startKey);
      const { endMs } = getIstDayBoundsMs(endKey);
      // For overhead proration when endKey extends into future dates, clamp overhead end to todayKey
      const overheadEndKey = endKey > todayKey && startKey <= todayKey ? todayKey : endKey;
      const dateKeys = enumerateDateKeys(startKey, overheadEndKey);
      return { startMs, endMs, daysCount: Math.max(1, dateKeys.length), dateKeys };
    }
  }
}

export function formatIstTime(ms: number): string {
  if (!ms) return '—';
  return new Date(ms).toLocaleTimeString('en-IN', {
    timeZone: 'Asia/Kolkata',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

export function formatIstDateTime(ms: number): string {
  if (!ms) return '—';
  return new Date(ms).toLocaleString('en-IN', {
    timeZone: 'Asia/Kolkata',
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: true,
  });
}

export function formatInr(amount: number, decimals: number = 0): string {
  const safe = Number.isFinite(amount) ? amount : 0;
  const sign = safe < 0 ? '-' : '';
  const abs = Math.abs(safe);
  return `${sign}₹${abs.toLocaleString('en-IN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}`;
}
