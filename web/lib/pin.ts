const PIN_STORAGE_KEY = 'filtr_pos_pin';
const DEFAULT_PIN = process.env.NEXT_PUBLIC_POS_PIN || '1234';

export function getStaffPin(): string {
  if (typeof window === 'undefined') return DEFAULT_PIN;
  try {
    const stored = localStorage.getItem(PIN_STORAGE_KEY);
    if (stored && /^\d{4}$/.test(stored)) {
      return stored;
    }
  } catch (e) {
    console.error('Failed to read staff PIN from localStorage:', e);
  }
  return DEFAULT_PIN;
}

export function setStaffPin(newPin: string): boolean {
  if (!/^\d{4}$/.test(newPin)) return false;
  try {
    localStorage.setItem(PIN_STORAGE_KEY, newPin);
    return true;
  } catch (e) {
    console.error('Failed to save staff PIN to localStorage:', e);
    return false;
  }
}

export function verifyStaffPin(inputPin: string): boolean {
  return inputPin === getStaffPin();
}
