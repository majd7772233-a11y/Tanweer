import { describe, expect, it } from 'vitest';
import { Env } from '../src/env';
import {
  MAX_ACTIVE_DEVICES,
  createDeviceSession,
  handleGetMyDevices,
  handleRevokeMyDevice,
} from '../src/services/devices';
import { UserContext } from '../src/env';

type SessionRow = {
  id: string;
  user_id: string;
  device_id: string;
  refresh_token_hash: string;
  expires_at: number;
  created_at: number;
};

type DeviceRow = {
  device_id: string;
  user_id: string;
  platform: string;
  app_version: string;
  push_token: string | null;
  last_seen: number;
};

class FakeStatement {
  private params: unknown[] = [];

  constructor(private readonly db: FakeDeviceDb, private readonly sql: string) {}

  bind(...params: unknown[]) {
    this.params = params;
    return this;
  }

  async run() {
    return this.db.execute(this.sql, this.params);
  }

  async first() {
    return this.db.first(this.sql, this.params);
  }

  async all() {
    return this.db.all(this.sql, this.params);
  }
}

class FakeDeviceDb {
  sessions: SessionRow[] = [];
  devices: DeviceRow[] = [];

  prepare(sql: string) {
    return new FakeStatement(this, sql);
  }

  async batch(statements: FakeStatement[]) {
    const results = [];
    for (const statement of statements) results.push(await statement.run());
    return results;
  }

  async execute(rawSql: string, params: unknown[]) {
    const sql = rawSql.replace(/\s+/g, ' ').trim();

    if (/^DELETE FROM sessions WHERE user_id = \? AND \(expires_at <= \? OR device_id = \?\)$/i.test(sql)) {
      const [userId, now, deviceId] = params as [string, number, string];
      const before = this.sessions.length;
      this.sessions = this.sessions.filter(row =>
        !(row.user_id === userId && (row.expires_at <= now || row.device_id === deviceId))
      );
      return { success: true, meta: { changes: before - this.sessions.length } };
    }

    if (/^INSERT INTO sessions \(id, user_id, device_id, refresh_token_hash, expires_at, created_at\) SELECT/i.test(sql)) {
      const [id, userId, deviceId, tokenHash, expiresAt, createdAt, countUserId, now, limit] = params as [
        string, string, string, string, number, number, string, number, number
      ];
      const active = new Set(
        this.sessions
          .filter(row => row.user_id === countUserId && row.expires_at > now)
          .map(row => row.device_id)
      );
      if (active.size >= limit) return { success: true, meta: { changes: 0 } };
      this.sessions.push({
        id,
        user_id: userId,
        device_id: deviceId,
        refresh_token_hash: tokenHash,
        expires_at: expiresAt,
        created_at: createdAt,
      });
      return { success: true, meta: { changes: 1 } };
    }

    if (/^INSERT INTO devices \(device_id, user_id, platform, app_version, push_token, last_seen\) SELECT/i.test(sql)) {
      const [deviceId, userId, platform, appVersion, lastSeen, sessionId, expectedUser, tokenHash] = params as [
        string, string, string, string, number, string, string, string
      ];
      const session = this.sessions.find(row =>
        row.id === sessionId &&
        row.user_id === expectedUser &&
        row.refresh_token_hash === tokenHash
      );
      if (!session) return { success: true, meta: { changes: 0 } };
      const existing = this.devices.find(row => row.device_id === deviceId);
      if (existing) {
        Object.assign(existing, { user_id: userId, platform, app_version: appVersion, last_seen: lastSeen });
      } else {
        this.devices.push({
          device_id: deviceId,
          user_id: userId,
          platform,
          app_version: appVersion,
          push_token: null,
          last_seen: lastSeen,
        });
      }
      return { success: true, meta: { changes: 1 } };
    }

    if (/^DELETE FROM devices WHERE user_id = \? AND NOT EXISTS/i.test(sql)) {
      const [userId, now] = params as [string, number];
      const before = this.devices.length;
      this.devices = this.devices.filter(device =>
        device.user_id !== userId ||
        this.sessions.some(session =>
          session.user_id === userId && session.device_id === device.device_id && session.expires_at > now
        )
      );
      return { success: true, meta: { changes: before - this.devices.length } };
    }

    if (/^DELETE FROM sessions WHERE user_id = \? AND device_id = \?$/i.test(sql)) {
      const [userId, deviceId] = params as [string, string];
      const before = this.sessions.length;
      this.sessions = this.sessions.filter(row => !(row.user_id === userId && row.device_id === deviceId));
      return { success: true, meta: { changes: before - this.sessions.length } };
    }

    if (/^DELETE FROM devices WHERE user_id = \? AND device_id = \?$/i.test(sql)) {
      const [userId, deviceId] = params as [string, string];
      const before = this.devices.length;
      this.devices = this.devices.filter(row => !(row.user_id === userId && row.device_id === deviceId));
      return { success: true, meta: { changes: before - this.devices.length } };
    }

    return { success: true, meta: { changes: 0 } };
  }

  async first(rawSql: string, params: unknown[]) {
    const sql = rawSql.replace(/\s+/g, ' ').trim();
    if (/SELECT 1 FROM sessions WHERE user_id = \? AND device_id = \? AND expires_at > \?/i.test(sql)) {
      const [userId, deviceId, now] = params as [string, string, number];
      return this.sessions.some(row => row.user_id === userId && row.device_id === deviceId && row.expires_at > now)
        ? { 1: 1 }
        : null;
    }
    return null;
  }

  async all(rawSql: string, params: unknown[]) {
    const sql = rawSql.replace(/\s+/g, ' ').trim();
    if (/FROM sessions s LEFT JOIN devices d/i.test(sql)) {
      const [userId, now] = params as [string, number];
      const active = this.sessions.filter(row => row.user_id === userId && row.expires_at > now);
      const uniqueDevices = [...new Set(active.map(row => row.device_id))];
      return {
        results: uniqueDevices.map(deviceId => {
          const sessions = active.filter(row => row.device_id === deviceId);
          const latest = sessions.reduce((a, b) => a.created_at > b.created_at ? a : b);
          const device = this.devices.find(row => row.user_id === userId && row.device_id === deviceId);
          return {
            deviceId,
            platform: device?.platform ?? 'Android',
            appVersion: device?.app_version ?? '',
            lastSeen: latest.created_at,
            connectedAt: latest.created_at,
          };
        }),
      };
    }
    return { results: [] };
  }
}

const user: UserContext = {
  userId: 'user_1',
  phoneNumber: '777123456',
  fullName: 'مستخدم تنوير',
  gradeId: 10,
  sectionId: 'A',
  role: 'STUDENT',
  deviceId: 'device-current',
  defaultGroupId: 'class_10_A',
};

function createEnv(db: FakeDeviceDb): Env {
  return { DB: db } as unknown as Env;
}

async function registerFakeDevice(db: FakeDeviceDb, deviceId: string, now: number) {
  return createDeviceSession(createEnv(db), {
    userId: user.userId,
    deviceId,
    sessionId: `session-${deviceId}-${now}`,
    tokenHash: `hash-${deviceId}-${now}`,
    expiresAt: now + 90 * 24 * 60 * 60 * 1000,
    platform: 'Android',
    appVersion: '1.2.0-staging',
    now,
  });
}

describe('Three active device limit', () => {
  it('allows three distinct devices and rejects a fourth', async () => {
    const db = new FakeDeviceDb();
    const now = 1_800_000_000_000;

    expect(await registerFakeDevice(db, 'device-001', now)).toBe(true);
    expect(await registerFakeDevice(db, 'device-002', now + 1)).toBe(true);
    expect(await registerFakeDevice(db, 'device-003', now + 2)).toBe(true);
    expect(await registerFakeDevice(db, 'device-004', now + 3)).toBe(false);

    const active = new Set(db.sessions.map(row => row.device_id));
    expect(active.size).toBe(MAX_ACTIVE_DEVICES);
    expect(db.devices).toHaveLength(MAX_ACTIVE_DEVICES);
  });

  it('replaces a session for the same installation without consuming another slot', async () => {
    const db = new FakeDeviceDb();
    const now = 1_800_000_000_000;

    await registerFakeDevice(db, 'device-001', now);
    await registerFakeDevice(db, 'device-002', now + 1);
    await registerFakeDevice(db, 'device-003', now + 2);

    expect(await registerFakeDevice(db, 'device-002', now + 100)).toBe(true);
    expect(new Set(db.sessions.map(row => row.device_id)).size).toBe(3);
    expect(db.sessions.filter(row => row.device_id === 'device-002')).toHaveLength(1);
  });

  it('purges expired sessions before calculating active device slots', async () => {
    const db = new FakeDeviceDb();
    const now = 1_800_000_000_000;
    db.sessions.push({
      id: 'expired-session',
      user_id: user.userId,
      device_id: 'expired-device',
      refresh_token_hash: 'expired-hash',
      expires_at: now - 1,
      created_at: now - 100,
    });
    db.devices.push({
      device_id: 'expired-device',
      user_id: user.userId,
      platform: 'Android',
      app_version: 'old',
      push_token: null,
      last_seen: now - 100,
    });

    expect(await registerFakeDevice(db, 'device-001', now)).toBe(true);
    expect(db.sessions.some(row => row.device_id === 'expired-device')).toBe(false);
    expect(db.devices.some(row => row.device_id === 'expired-device')).toBe(false);
  });

  it('lists only active devices and identifies the current installation', async () => {
    const db = new FakeDeviceDb();
    const now = 1_800_000_000_000;
    await registerFakeDevice(db, 'device-current', now);
    await registerFakeDevice(db, 'device-old', now + 1);

    const response = await handleGetMyDevices(user, createEnv(db));
    const body = await response.json() as any;

    expect(response.status).toBe(200);
    expect(body.maxActiveDevices).toBe(3);
    expect(body.activeDevicesCount).toBe(2);
    expect(body.devices.find((item: any) => item.deviceId === 'device-current').isCurrent).toBe(true);
    expect(body.devices.find((item: any) => item.deviceId === 'device-old').isCurrent).toBe(false);
  });

  it('removes only another device and refuses to revoke the current installation', async () => {
    const db = new FakeDeviceDb();
    const now = 1_800_000_000_000;
    await registerFakeDevice(db, 'device-current', now);
    await registerFakeDevice(db, 'device-old', now + 1);

    const revokeOld = await handleRevokeMyDevice('device-old', user, createEnv(db));
    const revokeBody = await revokeOld.json() as any;
    expect(revokeOld.status).toBe(200);
    expect(revokeBody.success).toBe(true);
    expect(db.sessions.some(row => row.device_id === 'device-old')).toBe(false);
    expect(db.devices.some(row => row.device_id === 'device-old')).toBe(false);

    const revokeCurrent = await handleRevokeMyDevice('device-current', user, createEnv(db));
    expect(revokeCurrent.status).toBe(409);
    expect(db.sessions.some(row => row.device_id === 'device-current')).toBe(true);
  });
});
