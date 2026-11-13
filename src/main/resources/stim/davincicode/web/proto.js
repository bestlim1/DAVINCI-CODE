// DAVINCI CODE 웹 클라이언트 — 프로토콜, 게임 상태 해석, 판 위 숫자 계산
// Java의 common 패키지(Proto, Msg, GameView, Deduction)를 그대로 옮겼다. 서버와 PC 앱과 같은 규칙을 쓴다.

export const FS = '\t', RS = '\u001E', US = '\u001F';
export const VERSION = 3;
export const APP_VERSION = '1.3.1';
export const UNKNOWN = -2, JOKER = -1, PICK_HIDDEN = -9;
export const CHAT_MAX = 200, NAME_MAX = 10, TITLE_MAX = 24, AVATARS = 8;

/** 명령어와 필드를 탭으로 이어 한 줄로 만든다 */
export function encode(cmd, ...fields) {
  return [cmd, ...fields.map(f => (f === null || f === undefined) ? '' : String(f))].join(FS);
}

/** 사용자 입력에서 제어 문자(구분자 포함)를 지우고 길이를 자른다 */
export function clean(s, max) {
  let r = String(s ?? '').replace(/[\u0000-\u001F\u007F]/g, ' ').trim().replace(/ {2,}/g, ' ');
  if (r.length > max) r = r.slice(0, max).trim();
  return r;
}

/** 받은 한 줄 */
export class Msg {
  constructor(line) {
    const p = String(line).split(FS);
    this.cmd = p[0].trim().toUpperCase();
    this.f = p.slice(1);
  }
  s(i) { return this.f[i] ?? ''; }
  i(i, d = 0) { const n = parseInt(this.s(i), 10); return Number.isNaN(n) ? d : n; }
  b(i) { return this.s(i) === '1'; }
  list(i) { const v = this.s(i); return v ? v.split(RS).map(r => r.split(US)) : []; }
}

const num = (r, i, d = 0) => { const n = parseInt(r[i] ?? '', 10); return Number.isNaN(n) ? d : n; };
const flag = (r, i) => r[i] === '1';
const str = (r, i) => r[i] ?? '';

/** 닉네임 규칙 (서버에서 한 번 더 검사한다). 문제가 없으면 null */
export function checkName(name) {
  const n = String(name ?? '').trim();
  if (!n) return '닉네임을 입력해 주세요.';
  if (n.length < 2 || n.length > NAME_MAX) return '닉네임은 2~10글자로 정해 주세요.';
  if (!/^[가-힣a-zA-Z0-9_]+$/.test(n)) return '닉네임에는 한글, 영문, 숫자, _만 쓸 수 있어요.';
  const lower = n.toLowerCase();
  if (lower.startsWith('ai') || lower.includes('admin') || n.includes('운영자') || n.includes('관리자')) return 'AI, 운영자처럼 보이는 닉네임은 쓸 수 없어요.';
  return null;
}

// ------------------------------------------------------------------ 타일과 게임 상태

/** 타일 표기 [uid][색][숫자|J|?][*][!] → 객체 */
export function parseTile(code) {
  if (!code) return null;
  const m = /^(\d*)([BW])(\?|J|\d+)([*!]*)$/.exec(code);
  if (!m) return null;
  return {
    uid: m[1] ? Number(m[1]) : -1,
    color: m[2],
    value: m[3] === '?' ? UNKNOWN : m[3] === 'J' ? JOKER : Number(m[3]),
    revealed: m[4].includes('*'),
  };
}

export const isKnown = t => t.value !== UNKNOWN;
export const faceOf = t => t.value === JOKER ? '-' : t.value === UNKNOWN ? '?' : String(t.value);

const PHASES = ['SETUP', 'DRAW', 'JOKER', 'GUESS', 'CONTINUE', 'REVEAL_SELF', 'OVER'];

/** STATE 메시지 → 내가 볼 수 있는 게임 상태 */
export function parseState(m, viewer) {
  const v = {
    phase: PHASES.includes(m.s(0)) ? m.s(0) : 'OVER',
    turnPid: m.i(1), remainingMs: m.i(2, -1), deckB: m.i(3), deckW: m.i(4),
    pending: parseTile(m.s(5)), pendingPos: m.i(6, -1), streak: m.i(7),
    seats: [], turnSec: m.i(9), handSize: m.i(10, 4), viewer,
  };
  for (const r of m.list(8)) {
    // pick: 시작 패로 고른 검정 수. 안 골랐으면 -1, 다른 사람이 골랐지만 아직 비밀이면 PICK_HIDDEN
    const pk = str(r, 4);
    const pick = pk === '' ? -1 : pk === '?' ? PICK_HIDDEN : num(r, 4, -1);
    v.seats.push({
      pid: num(r, 0), alive: flag(r, 1), connected: flag(r, 2),
      tiles: str(r, 3).split(',').filter(Boolean).map(parseTile),
      pick, picked: pick !== -1,
    });
  }
  return v;
}

export const seatOf = (v, pid) => v.seats.find(s => s.pid === pid) || null;
export const meOf = v => seatOf(v, v.viewer);
export const isMyTurn = v => v.turnPid === v.viewer && v.phase !== 'OVER';
export const hiddenCount = s => s.tiles.filter(t => !t.revealed).length;
export const deckTotal = v => v.deckB + v.deckW;

export function findTile(v, uid) {
  for (const s of v.seats) for (const t of s.tiles) if (t.uid === uid) return t;
  if (v.pending && v.pending.uid === uid) return v.pending;
  return null;
}

export function ownerOf(v, uid) {
  return v.seats.find(s => s.tiles.some(t => t.uid === uid)) || null;
}

// ------------------------------------------------------------------ 판 위 숫자 계산 (Deduction.java와 같은 계산. 웹에서는 숫자 고르기 창에서 이미 보이는 숫자를 막는 데 쓴다)

const ci = t => (t.color === 'W' ? 1 : 0);

/**
 * 보이는 정보만으로 상대의 숨은 타일마다 들어갈 수 있는 숫자를 계산한다.
 * key = 숫자*2 + (흰색이면 1)은 한 줄 안에서 엄격하게 커지므로, 앞뒤로 훑어 가능한 범위를 정확히 구한다.
 * 숨은 조커는 정렬을 따르지 않아서 조커가 어느 자리에 있을지 경우를 나눠 계산한 뒤 합친다.
 */
export function analyze(v) {
  const r = {
    known: [new Array(12).fill(false), new Array(12).fill(false)],
    jokerKnown: [false, false],
    cands: new Map(),
  };
  const mark = t => {
    if (!t || !isKnown(t)) return;
    if (t.value === JOKER) r.jokerKnown[ci(t)] = true;
    else r.known[ci(t)][t.value] = true;
  };
  v.seats.forEach(s => s.tiles.forEach(mark));
  mark(v.pending);
  const assumed = [new Array(12).fill(-1), new Array(12).fill(-1)];
  for (let round = 0; round < 4; round++) {
    r.cands.clear();
    let changed = false;
    for (const s of v.seats) if (s.pid !== v.viewer) analyzeRow(s, r, assumed);
    for (const [uid, c] of r.cands) {
      if (c.length === 1 && c[0] >= 0) {
        const col = ci(findTile(v, uid));
        if (assumed[col][c[0]] === -1) { assumed[col][c[0]] = uid; changed = true; }
      }
    }
    if (!changed) break;
  }
  r.unknown = (color, value) => {
    const c = color === 'W' ? 1 : 0;
    if (value === JOKER) return !r.jokerKnown[c];
    return !r.known[c][value];
  };
  return r;
}

function analyzeRow(s, r, assumed) {
  const row = s.tiles, n = row.length;
  const keys = row.map(t => {
    if (isKnown(t)) return t.value === JOKER ? null : [t.value * 2 + ci(t)];
    const c = ci(t), ks = [];
    for (let x = 0; x < 12; x++) {
      if (!r.known[c][x] && (assumed[c][x] === -1 || assumed[c][x] === t.uid)) ks.push(x * 2 + c);
    }
    return ks;
  });
  const jb = [-1], jw = [-1];
  row.forEach((t, i) => {
    if (isKnown(t)) return;
    const c = ci(t);
    if (!r.jokerKnown[c]) (c === 0 ? jb : jw).push(i);
  });
  const union = row.map(() => new Set());
  let any = false;
  for (const b of jb) {
    for (const w of jw) {
      const res = chain(keys, n, b, w);
      if (!res) continue;
      any = true;
      row.forEach((t, i) => {
        if (isKnown(t)) return;
        if (i === b || i === w) union[i].add(JOKER);
        else res[i].forEach(k => union[i].add(Math.floor(k / 2)));
      });
    }
  }
  row.forEach((t, i) => {
    if (isKnown(t)) return;
    const vals = union[i];
    if (!any || vals.size === 0) {
      keys[i].forEach(k => vals.add(Math.floor(k / 2)));
      if (!r.jokerKnown[ci(t)]) vals.add(JOKER);
    }
    r.cands.set(t.uid, [...vals].sort((a, b) => a - b));
  });
}

function chain(keys, n, s1, s2) {
  const skip = keys.map((k, i) => k === null || i === s1 || i === s2);
  const lo = new Array(n), hi = new Array(n);
  let prev = -1;
  for (let i = 0; i < n; i++) {
    if (skip[i]) continue;
    let best = Infinity;
    for (const k of keys[i]) if (k > prev && k < best) best = k;
    if (best === Infinity) return null;
    prev = lo[i] = best;
  }
  let next = 24;
  for (let i = n - 1; i >= 0; i--) {
    if (skip[i]) continue;
    let best = -Infinity;
    for (const k of keys[i]) if (k < next && k > best) best = k;
    if (best === -Infinity) return null;
    next = hi[i] = best;
  }
  const out = new Array(n);
  for (let i = 0; i < n; i++) {
    if (skip[i]) continue;
    let left = -1, right = 24;
    for (let j = i - 1; j >= 0; j--) if (!skip[j]) { left = lo[j]; break; }
    for (let j = i + 1; j < n; j++) if (!skip[j]) { right = hi[j]; break; }
    out[i] = keys[i].filter(k => k > left && k < right);
  }
  return out;
}

// ------------------------------------------------------------------ 로비·방 정보

export function parseRoom(m) {
  const r = {
    id: m.i(0), title: m.s(1), hostId: m.i(2), playing: m.s(3) === 'P', max: m.i(4, 4),
    locked: m.b(5), turnSec: m.i(6, 60),
    members: [], spectators: [],
  };
  for (const x of m.list(7)) {
    r.members.push({ id: num(x, 0), name: str(x, 1), avatar: num(x, 2), ready: flag(x, 3), connected: flag(x, 4) });
  }
  for (const x of m.list(8)) r.spectators.push({ id: num(x, 0), name: str(x, 1), avatar: num(x, 2) });
  return r;
}

export function parseLobby(m) {
  const rooms = m.list(0).map(x => ({
    id: num(x, 0), title: str(x, 1), playing: str(x, 2) === 'P', count: num(x, 3), max: num(x, 4, 4),
    locked: flag(x, 5), turnSec: num(x, 6, 60), host: str(x, 7), spectators: num(x, 8),
  }));
  const users = m.list(1).map(x => ({ id: num(x, 0), name: str(x, 1), avatar: num(x, 2), where: str(x, 3) }));
  return { rooms, users, serverName: m.s(2) };
}

export const turnText = sec => (sec === 0 ? '시간 무제한' : sec + '초');
