import crypto from 'crypto';
import { cookies } from 'next/headers';
import { NextResponse } from 'next/server';
import {
  fetchAccessPolicyConfig,
  getSupabaseServerKey,
} from './supabase-server';

export const ADMIN_SESSION_COOKIE = 'filtr_admin_session';
const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days

export interface AdminSessionPayload {
  role: 'ADMIN';
  operatorName: string;
  passcodeEpoch: number;
  issuedAt: number;
  expiresAt: number;
}

let cachedPolicyCheck: { epoch: number; checkedAt: number } | null = null;

function getSessionSecret(): string {
  const explicit = process.env.ADMIN_SESSION_SECRET?.trim();
  if (explicit) return explicit;
  const fallbackSeed = getSupabaseServerKey() || 'filtr-local-dev-secret';
  return crypto
    .createHmac('sha256', 'filtr-admin-session-derivation-v1')
    .update(fallbackSeed)
    .digest('hex');
}

function sha256Buffer(value: string): Buffer {
  return crypto.createHash('sha256').update(value, 'utf8').digest();
}

export function timingSafeCompare(a: string, b: string): boolean {
  const bufA = sha256Buffer(a);
  const bufB = sha256Buffer(b);
  return crypto.timingSafeEqual(bufA, bufB);
}

export function signAdminSessionToken(payload: AdminSessionPayload): string {
  const body = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
  const sig = crypto
    .createHmac('sha256', getSessionSecret())
    .update(body)
    .digest('base64url');
  return `${body}.${sig}`;
}

export function parseAndVerifyTokenSignature(
  token: string | undefined
): AdminSessionPayload | null {
  if (!token || !token.includes('.')) return null;
  const parts = token.split('.');
  if (parts.length !== 2) return null;
  const [body, sig] = parts;
  if (!body || !sig) return null;

  const expectedSig = crypto
    .createHmac('sha256', getSessionSecret())
    .update(body)
    .digest('base64url');

  const sigBuf = Buffer.from(sig, 'utf8');
  const expectedBuf = Buffer.from(expectedSig, 'utf8');
  if (sigBuf.length !== expectedBuf.length) return null;
  if (!crypto.timingSafeEqual(sigBuf, expectedBuf)) return null;

  try {
    const parsed = JSON.parse(
      Buffer.from(body, 'base64url').toString('utf8')
    ) as AdminSessionPayload;
    if (
      parsed.role !== 'ADMIN' ||
      !parsed.expiresAt ||
      Date.now() > parsed.expiresAt
    ) {
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export async function getLivePasscodeEpoch(): Promise<number> {
  const now = Date.now();
  if (cachedPolicyCheck && now - cachedPolicyCheck.checkedAt < 30_000) {
    return cachedPolicyCheck.epoch;
  }
  const policy = await fetchAccessPolicyConfig();
  cachedPolicyCheck = { epoch: policy.passcode_epoch, checkedAt: now };
  return policy.passcode_epoch;
}

export function invalidatePolicyEpochCache(): void {
  cachedPolicyCheck = null;
}

export async function getVerifiedAdminSession(): Promise<AdminSessionPayload | null> {
  const cookieStore = await cookies();
  const token = cookieStore.get(ADMIN_SESSION_COOKIE)?.value;
  const payload = parseAndVerifyTokenSignature(token);
  if (!payload) return null;

  const liveEpoch = await getLivePasscodeEpoch();
  if (payload.passcodeEpoch < liveEpoch) {
    return null;
  }
  return payload;
}

export async function requireAdmin(): Promise<
  | { ok: true; session: AdminSessionPayload }
  | { ok: false; response: NextResponse }
> {
  const session = await getVerifiedAdminSession();
  if (!session) {
    return {
      ok: false,
      response: NextResponse.json(
        { error: 'Admin authentication required' },
        { status: 401 }
      ),
    };
  }
  return { ok: true, session };
}

export function buildAdminSessionCookieOptions(maxAgeSeconds?: number) {
  return {
    name: ADMIN_SESSION_COOKIE,
    httpOnly: true,
    sameSite: 'lax' as const,
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: maxAgeSeconds ?? Math.floor(SESSION_TTL_MS / 1000),
  };
}

export function createAdminSessionPayload(
  operatorName: string,
  passcodeEpoch: number
): AdminSessionPayload {
  const now = Date.now();
  return {
    role: 'ADMIN',
    operatorName: operatorName.trim() || 'Web Admin',
    passcodeEpoch,
    issuedAt: now,
    expiresAt: now + SESSION_TTL_MS,
  };
}
