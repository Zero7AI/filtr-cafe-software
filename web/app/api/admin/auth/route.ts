import { NextRequest, NextResponse } from 'next/server';
import {
  buildAdminSessionCookieOptions,
  createAdminSessionPayload,
  getVerifiedAdminSession,
  signAdminSessionToken,
  timingSafeCompare,
} from '@/lib/admin-auth';
import {
  fetchAccessPolicyConfig,
  parseDeviceRegistration,
  serializeDeviceRegistration,
  getSyncMetadata,
  setSyncMetadata,
} from '@/lib/supabase-server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const session = await getVerifiedAdminSession();
    if (!session) {
      return NextResponse.json({ authenticated: false }, { status: 200 });
    }
    return NextResponse.json({
      authenticated: true,
      operatorName: session.operatorName,
      issuedAt: session.issuedAt,
      expiresAt: session.expiresAt,
    });
  } catch (err) {
    return NextResponse.json(
      {
        authenticated: false,
        error: err instanceof Error ? err.message : 'Session check failed',
      },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const passcode = String(body?.passcode || '').trim();
    const operatorName = String(body?.operatorName || 'Web Admin').trim() || 'Web Admin';

    if (!passcode) {
      return NextResponse.json(
        { error: 'Admin passcode is required' },
        { status: 400 }
      );
    }

    const policy = await fetchAccessPolicyConfig();
    const isValid = timingSafeCompare(passcode, policy.admin_passcode);
    if (!isValid) {
      return NextResponse.json(
        { error: 'Invalid Admin passcode' },
        { status: 401 }
      );
    }

    const payload = createAdminSessionPayload(operatorName, policy.passcode_epoch);
    const token = signAdminSessionToken(payload);

    // Best-effort register/update web admin heartbeat in sync_metadata
    try {
      const key = 'device_reg_web_admin';
      const existingRaw = await getSyncMetadata(key);
      const existing = existingRaw ? parseDeviceRegistration(key, existingRaw) : null;
      const now = Date.now();
      const updatedReg = {
        registryKey: key,
        device_id: 'web_admin',
        app_role: 'ADMIN' as const,
        operator_name: operatorName,
        device_model: 'Web Admin Portal',
        android_version: 'Browser / PWA',
        app_version_name: 'web-1.0.0',
        app_version_code: 100,
        status: 'ACTIVE' as const,
        passcode_epoch: policy.passcode_epoch,
        first_seen_ms: existing?.first_seen_ms || now,
        last_seen_ms: now,
        role_updated_at_ms: existing?.role_updated_at_ms || now,
        linked_member_id: existing?.linked_member_id || '',
        linked_member_name: existing?.linked_member_name || '',
        member_link_updated_at_ms: existing?.member_link_updated_at_ms || 0,
      };
      await setSyncMetadata(key, serializeDeviceRegistration(updatedReg));
    } catch {
      // Never block admin login on heartbeat
    }

    const res = NextResponse.json({
      authenticated: true,
      operatorName: payload.operatorName,
    });
    res.cookies.set({
      ...buildAdminSessionCookieOptions(),
      value: token,
    });
    return res;
  } catch (err) {
    return NextResponse.json(
      { error: err instanceof Error ? err.message : 'Login error' },
      { status: 500 }
    );
  }
}

export async function DELETE() {
  const res = NextResponse.json({ authenticated: false });
  res.cookies.set({
    ...buildAdminSessionCookieOptions(0),
    value: '',
  });
  return res;
}
