// DAVINCI CODE 웹 클라이언트. 게임 서버가 이 파일을 직접 보내 주고, 같은 주소의 /ws 로 WebSocket 연결해서 PC 앱과 함께 플레이한다.
import * as P from './proto.js';

// ================================================================ 작은 도우미

const $ = sel => document.querySelector(sel);

/** 요소 만들기. 사용자가 쓴 글자는 반드시 text(=textContent)로 넣는다 (HTML로 해석되지 않게) */
function h(tag, props = {}, ...kids) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props || {})) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') e.className = v;
    else if (k === 'text') e.textContent = v;
    else if (k === 'html') e.innerHTML = v; // 코드 안의 고정 SVG에만 쓴다
    else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
    else if (k === 'style') for (const [p, val] of Object.entries(v)) { if (p.startsWith('--')) e.style.setProperty(p, val); else e.style[p] = val; }
    else if (k === 'dataset') Object.assign(e.dataset, v);
    else e.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) if (kid !== null && kid !== undefined && kid !== false) e.append(kid instanceof Node ? kid : String(kid));
  return e;
}

const IC = {
  back: '<path d="M15 5l-7 7 7 7"/>',
  close: '<path d="M6 6l12 12M18 6L6 18"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  send: '<path d="M21 3L3 10.5l7.5 3L13.5 21z"/><path d="M21 3L10.5 13.5"/>',
  users: '<circle cx="8.5" cy="8.5" r="3.5"/><path d="M2 20c0-4 3-6.5 6.5-6.5S15 16 15 20"/><path d="M16 5.5a3 3 0 0 1 0 6M18 14c2 .8 3.5 2.7 3.5 6"/>',
  lock: '<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
  crown: '<path d="M3 8l4.5 4.5L12 5l4.5 7.5L21 8l-2 11H5z"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  gear: '<circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="7"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9L7 7M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"/>',
  chat: '<path d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-8l-5 4v-4H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/>',
  trophy: '<path d="M7 4h10v5a5 5 0 0 1-10 0z"/><path d="M7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4M12 14v4M8 21h8"/>',
  bot: '<rect x="4.5" y="8" width="15" height="12" rx="3"/><path d="M12 4.5V8"/><circle cx="9.3" cy="13.8" r="1.2"/><circle cx="14.7" cy="13.8" r="1.2"/>',
  help: '<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6"/><path d="M12 17h.01"/>',
  exit: '<path d="M13 4H5v16h8"/><path d="M10 12h11M17 8l4 4-4 4"/>',
  check: '<path d="M5 12.5l5 5L19 7"/>',
  star: '<path d="M12 3l2.6 5.6 6.1.7-4.5 4.2 1.2 6L12 16.6 6.6 19.5l1.2-6L3.3 9.3l6.1-.7z"/>',
  zap: '<path d="M13 2L4 14h7l-1 8 10-12.5h-7z"/>',
  sparkle: '<path d="M12 2c.6 5.4 4.6 9.4 10 10-5.4.6-9.4 4.6-10 10-.6-5.4-4.6-9.4-10-10 5.4-.6 9.4-4.6 10-10z"/>',
  flag: '<path d="M5 21V4M5 4h13l-3 4 3 4H5"/>',
  block: '<circle cx="12" cy="12" r="9"/><path d="M6 6l12 12"/>',
  play: '<path d="M8 5l11 7-11 7z"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.7"/><path d="M20 4v5h-5"/>',
  timer: '<circle cx="12" cy="14" r="8"/><path d="M12 14v-4M10 2.5h4M12 2.5V6"/>',
  wifi: '<path d="M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"/><path d="M12 19.5h.01"/>',
  server: '<rect x="3" y="3.5" width="18" height="7" rx="2"/><rect x="3" y="13.5" width="18" height="7" rx="2"/><path d="M7 7h.01M7 17h.01"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5M12 8h.01"/>',
  pencil: '<path d="M15 4l5 5L9 20H4v-5z"/>',
};
const FILLED = new Set(['crown', 'star', 'zap', 'sparkle', 'play']);
const icon = name => `<svg class="ic${FILLED.has(name) ? ' fill' : ''}" viewBox="0 0 24 24" aria-hidden="true">${IC[name] || ''}</svg>`;
const iconEl = name => { const t = document.createElement('template'); t.innerHTML = icon(name); return t.content.firstChild; };
/** 아이콘 + 글자 (버튼 안에 넣는다). label은 코드 안의 고정 글자만 쓴다 */
const iconBtn = (name, label) => { const t = document.createElement('template'); t.innerHTML = icon(name) + (label ? `<span>${label}</span>` : ''); return t.content; };

// ---------------------------------------------------------------- 캐릭터 (PC 앱 Art.avatar와 같은 얼굴)
const AV = ['#FF7A59', '#FFC15E', '#3DD6B0', '#5EA8FF', '#A98BFF', '#FF7EB6', '#A6E35A', '#3CC7D9'];
function avatarSvg(i, bot, size) {
  const v = ((i % 8) + 8) % 8, c = AV[v], ink = '#1B1F2C';
  let s = `<svg class="avatar" width="${size}" height="${size}" viewBox="-4 -8 108 110" aria-hidden="true">`;
  if (bot) {
    s += `<path d="M50 14V3" stroke="${ink}" stroke-width="5" stroke-linecap="round"/><circle cx="50" cy="2" r="6" fill="${ink}"/>`;
    s += `<rect x="6" y="12" width="88" height="82" rx="26" fill="${c}"/><rect x="6" y="12" width="88" height="40" rx="22" fill="#fff" opacity=".18"/>`;
    s += `<rect x="20" y="36" width="60" height="26" rx="12" fill="${ink}"/><rect x="28" y="42" width="13" height="11" rx="5" fill="#fff" opacity=".75"/><rect x="59" y="42" width="13" height="11" rx="5" fill="#fff" opacity=".75"/>`;
    return s + '</svg>';
  }
  s += `<circle cx="50" cy="50" r="50" fill="${c}"/><ellipse cx="50" cy="30" rx="40" ry="25" fill="#fff" opacity=".16"/>`;
  const eye = (x) => `<ellipse cx="${x}" cy="47" rx="6.5" ry="7.8" fill="${ink}"/>`;
  const arc = (x) => `<path d="M${x - 9} 50 Q${x} 39 ${x + 9} 50" stroke="${ink}" stroke-width="4.6" fill="none" stroke-linecap="round"/>`;
  switch (v) {
    case 1: s += arc(35) + arc(65); break;
    case 3: s += eye(35) + arc(65); break;
    case 5: s += `<ellipse cx="35" cy="47" rx="9" ry="10.5" fill="${ink}"/><ellipse cx="65" cy="47" rx="9" ry="10.5" fill="${ink}"/><circle cx="32" cy="43" r="3.4" fill="#fff"/><circle cx="62" cy="43" r="3.4" fill="#fff"/>`; break;
    case 6: s += `<path d="M26 47H44M56 47H74" stroke="${ink}" stroke-width="4.6" stroke-linecap="round"/>`; break;
    default: s += eye(35) + eye(65);
  }
  s += `<ellipse cx="24.5" cy="60.5" rx="7.5" ry="4.5" fill="#FF788C" opacity=".38"/><ellipse cx="75.5" cy="60.5" rx="7.5" ry="4.5" fill="#FF788C" opacity=".38"/>`;
  switch (v) {
    case 2: s += `<circle cx="50" cy="66" r="5" stroke="${ink}" stroke-width="4.4" fill="none"/>`; break;
    case 4: s += `<path d="M39 64 Q44.5 71 50 64 Q55.5 71 61 64" stroke="${ink}" stroke-width="4.4" fill="none" stroke-linecap="round"/>`; break;
    case 7: s += `<path d="M38 61 Q50 72 62 61" stroke="${ink}" stroke-width="4.4" fill="none" stroke-linecap="round"/><rect x="46.5" y="66" width="7" height="8" rx="3.5" fill="#FF6F8E"/>`; break;
    default: s += `<path d="M40 62 Q50 72 60 62" stroke="${ink}" stroke-width="4.4" fill="none" stroke-linecap="round"/>`;
  }
  return s + '</svg>';
}
const avatarEl = (i, bot, size) => { const t = document.createElement('template'); t.innerHTML = avatarSvg(i, bot, size); return t.content.firstChild; };

// ---------------------------------------------------------------- 타일
const JOKER_SVG = '<svg class="jk" viewBox="0 0 60 60" aria-hidden="true"><rect x="9" y="26" width="42" height="8" rx="4" fill="currentColor"/><path d="M46 7l2 5 5 2-5 2-2 5-2-5-5-2 5-2z" fill="#FFC15E"/><path d="M15 40l1.5 3.5 3.5 1.5-3.5 1.5L15 50l-1.5-3.5L10 45l3.5-1.5z" fill="#FFC15E"/></svg>';
function tileEl(t, o = {}) {
  const back = o.back ?? !P.isKnown(t);
  const e = h('div', { class: `tile ${t.color === 'B' ? 'b' : 'w'}${back ? ' back' : ''}${o.cls ? ' ' + o.cls : ''}` });
  if (t.uid !== undefined && t.uid >= 0) e.dataset.uid = t.uid;
  if (!back) {
    if (t.value === P.JOKER) e.insertAdjacentHTML('beforeend', JOKER_SVG);
    else e.append(h('span', { class: 'f' + (t.value === 6 || t.value === 9 ? ' u' : ''), text: String(t.value) }));
    if (o.mine && t.revealed) e.append(h('span', { class: 'bdg open', text: '공개' }));
  }
  if (o.note) e.append(h('span', { class: 'note', text: o.note }));
  return e;
}
const fakeTile = (color, value) => ({ color, value: value === '?' ? P.UNKNOWN : value === 'J' ? P.JOKER : value, revealed: false, uid: -1 });

// ================================================================ 설정 (이 브라우저에만 저장)

const store = {
  get(k, d) {
    try {
      let v = localStorage.getItem('dc.' + k);
      if (v === null) v = localStorage.getItem('hc.' + k); // 예전 이름(HIDDEN CODE) 때 저장한 값
      return v === null ? d : JSON.parse(v);
    } catch { return d; }
  },
  set(k, v) { try { localStorage.setItem('dc.' + k, JSON.stringify(v)); } catch { /* 저장 불가 (사생활 보호 모드 등) */ } },
};
function uuid() {
  const b = new Uint8Array(16);
  crypto.getRandomValues(b);
  b[6] = (b[6] & 0x0f) | 0x40; b[8] = (b[8] & 0x3f) | 0x80;
  const x = [...b].map(n => n.toString(16).padStart(2, '0')).join('');
  return `${x.slice(0, 8)}-${x.slice(8, 12)}-${x.slice(12, 16)}-${x.slice(16, 20)}-${x.slice(20)}`;
}
const prefs = {
  deviceId: store.get('device', '') || (() => { const id = 'web-' + uuid(); store.set('device', id); return id; })(),
  name: store.get('name', ''), avatar: store.get('avatar', Math.floor(Math.random() * 8)), agreed: store.get('agreed', false),
  sound: store.get('sound', true), volume: store.get('volume', 70), chatSound: store.get('chatSound', true),
  reduce: store.get('reduce', false), blocked: store.get('blocked', []), seenHow: store.get('seenHow', false),
  save() { for (const k of ['name', 'avatar', 'agreed', 'sound', 'volume', 'chatSound', 'reduce', 'blocked', 'seenHow']) store.set(k, this[k]); },
  isBlocked(n) { return this.blocked.includes(n); },
  block(n, on) { this.blocked = this.blocked.filter(x => x !== n); if (on) this.blocked.push(n); this.save(); },
};
document.body.classList.toggle('reduce', prefs.reduce);

// ================================================================ 효과음 (Web Audio로 합성, 파일 없음)

const Sound = {
  ctx: null,
  ensure() {
    if (!this.ctx) { try { this.ctx = new (window.AudioContext || window.webkitAudioContext)(); } catch { this.ctx = null; } }
    if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume();
  },
  tone(freq, dur, type = 'sine', gain = 0.25, delay = 0, to = null) {
    const c = this.ctx, t0 = c.currentTime + delay, o = c.createOscillator(), g = c.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, t0);
    if (to) o.frequency.exponentialRampToValueAtTime(to, t0 + dur);
    const peak = Math.max(0.0002, gain * prefs.volume / 100);
    g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(peak, t0 + 0.006); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
    o.connect(g).connect(c.destination); o.start(t0); o.stop(t0 + dur + 0.03);
  },
  play(name) {
    if (!prefs.sound || !this.ctx || prefs.volume === 0) return;
    try {
      const t = (...a) => this.tone(...a);
      switch (name) {
        case 'click': t(1700, 0.04, 'sine', 0.12); break;
        case 'draw': t(520, 0.08, 'triangle', 0.25); t(300, 0.06, 'sine', 0.15, 0.02); break;
        case 'place': t(260, 0.09, 'sine', 0.4); t(520, 0.06, 'sine', 0.18); break;
        case 'correct': t(1046, 0.13, 'triangle', 0.3); t(1318, 0.13, 'triangle', 0.3, 0.075); t(1568, 0.25, 'triangle', 0.32, 0.15); break;
        case 'wrong': t(233, 0.15, 'square', 0.12); t(175, 0.24, 'square', 0.12, 0.13); break;
        case 'turn': t(784, 0.3, 'sine', 0.3); t(1046, 0.45, 'sine', 0.32, 0.11); break;
        case 'chat': t(880, 0.06, 'sine', 0.18); break;
        case 'tick': t(1250, 0.03, 'sine', 0.2); break;
        case 'win': [523, 659, 784].forEach((f, i) => t(f, 0.15, 'triangle', 0.3, i * 0.11)); [1046, 1318, 1568].forEach(f => t(f, 0.7, 'sine', 0.2, 0.33)); break;
        case 'lose': [392, 330, 262].forEach((f, i) => t(f, i === 2 ? 0.45 : 0.2, 'triangle', 0.3, i * 0.17)); break;
        default:
      }
    } catch { /* 소리 장치 없음 */ }
  },
};

// ================================================================ 알림, 대화상자, 메뉴

const TOAST_C = { info: 'var(--sky)', success: 'var(--mint)', error: 'var(--red)', warn: 'var(--amber)' };
let lastToast = { text: '', at: 0 };
function toast(text, kind = 'info') {
  if (!text) return;
  const now = Date.now();
  if (text === lastToast.text && now - lastToast.at < 1500) return;
  lastToast = { text, at: now };
  const box = $('#toasts');
  const t = h('div', { class: 'toast', style: { '--c': TOAST_C[kind] } }, iconEl(kind === 'success' ? 'check' : 'info'), h('span', { text }));
  box.append(t);
  while (box.children.length > 3) box.firstChild.remove();
  setTimeout(() => t.remove(), 3300);
}

const modalStack = [];
function modal({ title, sub, body, actions = [], width = 460, dismissable = true, onClose }) {
  const bd = h('div', { class: 'backdrop' });
  const card = h('div', { class: 'card modal', role: 'dialog', 'aria-modal': 'true', style: { '--w': width + 'px' } });
  const head = h('div', { class: 'head' }, h('div', { style: { flex: '1' } }, h('h3', { text: title }), sub ? h('p', { text: sub }) : null));
  const api = { card, closed: false, dismissable };
  api.close = () => {
    if (api.closed) return;
    api.closed = true;
    bd.remove();
    const i = modalStack.indexOf(api);
    if (i >= 0) modalStack.splice(i, 1);
    onClose?.();
  };
  if (dismissable) {
    head.append(h('button', { class: 'icon-btn', type: 'button', title: '닫기', html: icon('close'), onclick: api.close }));
    bd.addEventListener('pointerdown', e => { if (e.target === bd) api.close(); });
  }
  card.append(head);
  if (body) { api.body = h('div', { class: 'body' }, body); card.append(api.body); }
  if (actions.length) {
    const foot = h('div', { class: 'foot' });
    for (const a of actions) {
      const b = h('button', { class: `btn ${a.kind || ''}`, type: 'button', onclick: () => a.onClick ? a.onClick(api) : api.close() });
      if (a.icon) b.insertAdjacentHTML('beforeend', icon(a.icon));
      b.append(h('span', { text: a.label }));
      foot.append(b);
      if (a.ref) a.ref(b);
    }
    card.append(foot);
  }
  bd.append(card);
  $('#modals').append(bd);
  modalStack.push(api);
  setTimeout(() => { const f = card.querySelector('input:not([type=checkbox]):not([type=range])'); if (f) f.focus(); }, 30);
  return api;
}
const closeAllModals = () => [...modalStack].forEach(m => m.close());
const alertBox = (title, msg) => modal({ title, sub: msg, actions: [{ label: '확인', kind: 'primary' }] });
function confirmBox(title, msg, okText, danger) {
  return new Promise(res => {
    let done = false;
    modal({
      title, sub: msg, onClose: () => { if (!done) res(false); },
      actions: [{ label: '취소' }, { label: okText, kind: danger ? 'danger' : 'primary', onClick: api => { done = true; api.close(); res(true); } }],
    });
  });
}
function promptBox(title, msg, password) {
  return new Promise(res => {
    let done = false;
    const input = h('input', { class: 'input', type: password ? 'password' : 'text', maxlength: password ? 12 : 40, placeholder: password ? '비밀번호' : '' });
    const ok = api => { done = true; api.close(); res(input.value); };
    const m = modal({ title, sub: msg, body: input, onClose: () => { if (!done) res(null); }, actions: [{ label: '취소' }, { label: '확인', kind: 'primary', onClick: ok }] });
    input.addEventListener('keydown', e => { if (e.key === 'Enter') ok(m); });
  });
}

let openMenu = null;
function popmenu(x, y, content) {
  closeMenu();
  const m = h('div', { class: 'popmenu' }, content);
  document.body.append(m);
  const w = m.offsetWidth, hh = m.offsetHeight;
  m.style.left = Math.max(8, Math.min(x, innerWidth - w - 8)) + 'px';
  m.style.top = Math.max(8, Math.min(y, innerHeight - hh - 8)) + 'px';
  openMenu = m;
  setTimeout(() => document.addEventListener('pointerdown', outside, true), 0);
  function outside(e) { if (!m.contains(e.target)) { closeMenu(); } }
  m._off = () => document.removeEventListener('pointerdown', outside, true);
}
function closeMenu() { if (openMenu) { openMenu._off(); openMenu.remove(); openMenu = null; } }
const menuItems = items => items.map(it => h('button', { type: 'button', html: icon(it.icon), onclick: () => { closeMenu(); it.onClick(); } }, h('span', { text: it.label })));

function seg(options, selected, onChange, cls = '') {
  const e = h('div', { class: 'seg ' + cls, role: 'radiogroup' });
  const btns = options.map((o, i) => h('button', { type: 'button', class: i === selected ? 'on' : '', text: o, onclick: () => { if (e._dis) return; set(i); onChange?.(i); } }));
  const set = i => { e._v = i; btns.forEach((b, j) => b.classList.toggle('on', j === i)); };
  e.append(...btns);
  e._v = selected;
  e.set = set;
  e.value = () => e._v;
  e.disable = d => { e._dis = d; btns.forEach(b => { b.disabled = d; }); };
  return e;
}
function switchEl(checked, onChange) {
  const input = h('input', { type: 'checkbox', role: 'switch' });
  input.checked = checked;
  input.addEventListener('change', () => onChange?.(input.checked));
  const e = h('label', { class: 'switch' }, input, h('span'));
  e.input = input;
  return e;
}
const row = (title, desc, right) => h('div', { class: 'row' }, h('div', { class: 't' }, h('b', { text: title }), desc ? h('small', { text: desc }) : null), right);

function busy(text, cancellable) {
  hideBusy();
  const actions = cancellable ? [{ label: '취소', onClick: api => { api.close(); disconnect(); goStart(); } }] : [];
  S.busy = modal({ title: '잠시만요', body: h('div', { class: 'overlay-msg' }, h('div', { class: 'spinner' }), h('p', { text })), actions, dismissable: false, width: 380 });
}
function hideBusy() { if (S.busy) { S.busy.close(); S.busy = null; } }

// ================================================================ 상태

const S = {
  screen: 'start', myId: 0, myName: '', serverName: '', pickSent: -1, dealAt: -1,
  lobby: { rooms: [], users: [] }, filter: 0, room: null, game: null, ded: null,
  who: new Map(), memos: new Map(), stateAt: 0, totalMs: 1, lastTick: -1, lastOver: false,
  picker: null, boardShown: false, resultPending: false, resultModal: null,
  anim: { flip: new Map(), pop: new Map(), shake: new Map(), ok: new Map() }, elim: new Map(),
  busy: null, rtt: -1, unread: 0, profileReply: null,
};
const nameOf = pid => (pid === S.myId ? '나' : (S.who.get(pid)?.name ?? '?'));
const subj = pid => (pid === S.myId ? '내가' : nameOf(pid) + '님이');

// ================================================================ 연결

let ws = null, userClosing = false, welcomed = false, kicked = false, retry = 0, pingTimer = null;
const wsUrl = () => (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws';

function connect() {
  let sock;
  try { sock = new WebSocket(wsUrl()); } catch { onClosed(); return; }
  ws = sock;
  userClosing = false;
  sock.onopen = () => { if (sock === ws) send('HELLO', P.VERSION, prefs.deviceId, prefs.name, prefs.avatar); };
  sock.onmessage = e => { if (sock === ws) onMessage(new P.Msg(e.data)); };
  sock.onclose = () => { if (sock === ws) { ws = null; onClosed(); } };
  clearInterval(pingTimer);
  pingTimer = setInterval(() => send('PING', performance.now().toFixed(1)), 10000);
}
function send(cmd, ...f) { if (ws && ws.readyState === 1) ws.send(P.encode(cmd, ...f)); }
function disconnect() {
  userClosing = true;
  welcomed = false;
  clearInterval(pingTimer);
  if (ws) { const w = ws; ws = null; try { w.close(1000); } catch { /* 이미 닫힘 */ } }
}

function onClosed() {
  clearInterval(pingTimer);
  if (userClosing) { userClosing = false; return; }
  if (kicked) return;
  if (welcomed && retry < 6) {
    retry++;
    busy(`연결이 끊겼어요. 다시 연결하는 중… (${retry}/6)`, true);
    setTimeout(() => { if (S.busy) connect(); }, Math.min(5000, 800 * retry));
    return;
  }
  const wasIn = welcomed;
  welcomed = false;
  hideBusy();
  goStart();
  if (wasIn) alertBox('다시 연결하지 못했어요', '서버가 꺼졌거나 인터넷 연결이 끊겼어요. 잠시 후 다시 입장해 주세요.');
  else setStartError('서버에 연결할 수 없어요. 주소가 맞는지, 서버가 켜져 있는지 확인해 주세요.');
}

function onMessage(m) {
  switch (m.cmd) {
    case 'WELCOME': {
      welcomed = true; retry = 0; hideBusy();
      S.myId = m.i(0); S.myName = m.s(1); S.serverName = m.s(2);
      if (m.b(3)) { toast('다시 연결됐어요. 하던 게임을 이어서 해요.', 'success'); return; }
      S.room = null; S.game = null;
      show('lobby');
      renderLobby();
      if (!prefs.seenHow) { prefs.seenHow = true; prefs.save(); howTo(); }
      break;
    }
    case 'PONG': S.rtt = Math.round(performance.now() - parseFloat(m.s(0))); updatePing(); break;
    case 'ERROR': {
      const code = m.s(0), text = m.s(1);
      if (!welcomed) { setStartError(text); disconnect(); return; }
      if (S.profileReply && ['BAD_NAME', 'NAME_TAKEN', 'IN_ROOM', 'PROFILE_WAIT'].includes(code)) {
        const done = S.profileReply; S.profileReply = null; done(text); return; // 프로필 창에 이유를 보여 준다
      }
      toast(text, code === 'BAD_MOVE' ? 'warn' : 'error');
      if (code === 'BAD_MOVE' && S.game) renderBoard();
      break;
    }
    case 'NOTICE': toast(m.s(0), 'info'); break;
    case 'KICKED':
      kicked = true; disconnect(); closeAllModals(); goStart();
      alertBox('연결이 종료됐어요', m.s(0));
      break;
    case 'LOBBY': {
      const l = P.parseLobby(m);
      S.lobby = l; S.serverName = l.serverName || S.serverName;
      renderLobby();
      break;
    }
    case 'ROOM': {
      const r = P.parseRoom(m);
      const entering = !S.room || S.room.id !== r.id || S.screen !== 'room';
      S.room = r;
      for (const x of r.members) S.who.set(x.id, { name: x.name, avatar: x.avatar });
      if (entering) enterRoom();
      updateRoom();
      break;
    }
    case 'LEFT':
      S.room = null; S.game = null; closeAllModals();
      show('lobby'); renderLobby();
      if (m.s(0)) toast(m.s(0), 'warn');
      break;
    case 'STATE': onState(P.parseState(m, S.myId)); break;
    case 'EVENT': onEvent(m); break;
    case 'RESULT': onResult(m); break;
    case 'CHAT': {
      if (prefs.isBlocked(m.s(2))) break;
      const msg = { id: m.i(1), name: m.s(2), avatar: m.i(3), text: m.s(4), spectator: m.s(5) === 'S' };
      if (m.s(0) === 'L') lobbyChat.addChat(msg); else roomChat.addChat(msg);
      if (msg.id !== S.myId && prefs.chatSound) Sound.play('chat');
      break;
    }
    case 'SYS': roomChat.addSys(m.s(1)); break;
    case 'WHISPER': {
      if (prefs.isBlocked(m.s(0))) break;
      (S.screen === 'room' ? roomChat : lobbyChat).addWhisper(m.s(0), m.s(1), m.i(2), m.s(3));
      if (m.s(0) !== S.myName && prefs.chatSound) Sound.play('chat');
      break;
    }
    case 'REACT': onReact(m.i(0), m.i(1)); break;
    case 'PROFILE': {
      // 서버가 닉네임·캐릭터 변경을 받아들였다
      S.myName = m.s(0);
      prefs.name = S.myName; prefs.avatar = m.i(1, prefs.avatar); prefs.save();
      $('#nick').value = prefs.name;
      renderLobby();
      toast('프로필을 바꿨어요.', 'success');
      const done = S.profileReply; S.profileReply = null; done?.(null);
      break;
    }
    default: // 모르는 메시지는 무시 (새 버전 서버와 호환)
  }
}

function updatePing() {
  const t = S.rtt < 0 ? '연결 확인 중' : S.rtt + 'ms';
  const c = S.rtt < 0 ? 'var(--text3)' : S.rtt < 80 ? 'var(--mint)' : S.rtt < 200 ? 'var(--amber)' : 'var(--red)';
  for (const id of ['#lobby-ping', '#room-ping']) {
    const p = $(id);
    p.replaceChildren(iconEl('wifi'), h('i', { class: 'dot', style: { '--c': c } }), h('span', { text: t }));
  }
}

// ================================================================ 화면 전환

function show(name) {
  S.screen = name;
  for (const s of ['start', 'lobby', 'room']) $('#screen-' + s).hidden = s !== name;
  closeMenu();
  if (name !== 'room') S.picker = null;
}
function goStart() {
  S.room = null; S.game = null; S.myId = 0;
  show('start');
  const b = $('#enter');
  b.disabled = false; b.textContent = '입장하기';
}
function setStartError(t) {
  $('#start-err').textContent = t || '';
  const b = $('#enter');
  b.disabled = false; b.textContent = '입장하기';
}

// ================================================================ 첫 화면

function initStart() {
  const lt = $('#logo-tiles');
  lt.append(tileEl(fakeTile('B', '?')), tileEl(fakeTile('W', 7)), tileEl(fakeTile('W', '?')), tileEl(fakeTile('B', 'J')));
  const fl = $('#floaters');
  for (let i = 0; i < 18; i++) {
    const t = tileEl(fakeTile(Math.random() < 0.5 ? 'B' : 'W', Math.random() < 0.6 ? '?' : Math.floor(Math.random() * 12)));
    t.style.setProperty('--tw', (22 + Math.random() * 24) + 'px');
    t.style.left = (Math.random() * 100) + 'vw';
    t.style.setProperty('--r0', (Math.random() * 360) + 'deg');
    t.style.setProperty('--r1', (Math.random() * 360 + 180) + 'deg');
    const dur = 26 + Math.random() * 30;
    t.style.animationDuration = dur + 's';
    t.style.animationDelay = (-Math.random() * dur) + 's';
    fl.append(t);
  }
  const pick = $('#avatar-pick');
  const draw = () => {
    pick.replaceChildren(...Array.from({ length: 8 }, (_, i) => {
      const b = h('button', { type: 'button', class: i === prefs.avatar ? 'on' : '', 'aria-label': '캐릭터 ' + (i + 1), html: avatarSvg(i, false, 38), onclick: () => { prefs.avatar = i; draw(); } });
      return b;
    }));
  };
  draw();
  $('#nick').value = prefs.name;
  $('#agree-row').hidden = prefs.agreed;
  $('#server-host').textContent = location.host || '(알 수 없음)';
  $('#privacy-link').addEventListener('click', e => { e.preventDefault(); privacy(); });
  $('#how-start').addEventListener('click', howTo);
  $('#start-form').addEventListener('submit', e => {
    e.preventDefault();
    const name = $('#nick').value.trim();
    const why = P.checkName(name);
    $('#nick').classList.toggle('bad', !!why);
    if (why) { setStartError(why); return; }
    if (!prefs.agreed && !$('#agree').checked) { setStartError('이용 규칙과 개인정보 처리방침에 동의해야 해요.'); return; }
    prefs.name = name; prefs.agreed = true; prefs.save();
    $('#agree-row').hidden = true;
    Sound.ensure();
    $('#start-err').textContent = '';
    const b = $('#enter');
    b.disabled = true; b.textContent = '접속 중…';
    kicked = false; welcomed = false; retry = 0;
    connect();
  });
  if (location.protocol === 'file:') setStartError('게임 서버 주소로 열어야 해요. (예: http://192.168.0.5:30000)');
}

// ================================================================ 채팅 (로비·방 공용)

const PHRASES = ['안녕하세요! 잘 부탁해요', '나이스!', '아깝다…', '잠시만요!', '한 판 더 할까요?', 'GG! 즐거웠어요'];
const REACTIONS = ['굿!', 'ㅋㅋㅋ', '헉!', '아깝다', '고마워', 'GG'];
const REACT_C = ['var(--mint)', 'var(--amber)', 'var(--sky)', 'var(--violet)', '#FF7EB6', 'var(--coral)'];

function makeChat(root, { room, onIncoming }) {
  const wrap = h('div', { class: 'chat' });
  const tabs = h('div', { class: 'chat-tabs' });
  const t1 = h('button', { type: 'button', class: 'on', text: room ? '채팅' : '로비 채팅' });
  const logDot = h('i', { class: 'dot', hidden: true });
  const t2 = room ? h('button', { type: 'button' }, '게임 기록', logDot) : null;
  tabs.append(t1);
  if (t2) tabs.append(t2);
  const list = h('div', { class: 'chat-list' });
  const logList = h('div', { class: 'log-list', hidden: true });
  const empty = h('div', { class: 'chat-empty', text: '아직 대화가 없어요. 먼저 인사해 보세요!' });
  list.append(empty);
  t1.onclick = () => { t1.classList.add('on'); t2?.classList.remove('on'); list.hidden = false; logList.hidden = true; };
  if (t2) t2.onclick = () => { t2.classList.add('on'); t1.classList.remove('on'); list.hidden = true; logList.hidden = false; logDot.hidden = true; logList.scrollTop = logList.scrollHeight; };

  const input = h('input', { class: 'input', maxlength: P.CHAT_MAX, placeholder: '메시지 입력', title: '귓속말: /w 닉네임 내용', enterkeyhint: 'send' });
  const submit = () => {
    const text = input.value.trim();
    if (!text) return;
    if (/^\/(w|귓) /.test(text)) {
      const p = text.split(' ');
      if (p.length < 3 || !p.slice(2).join(' ').trim()) { toast("귓속말은 '/w 닉네임 내용' 처럼 써 주세요.", 'warn'); return; }
      send('WHISPER', p[1], p.slice(2).join(' '));
    } else {
      send('CHAT', P.clean(text, P.CHAT_MAX));
    }
    input.value = '';
  };
  input.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.isComposing) { e.preventDefault(); submit(); } });
  const quick = h('button', { class: 'icon-btn', type: 'button', title: '빠른 채팅', html: icon('zap') });
  quick.onclick = () => {
    const r = quick.getBoundingClientRect();
    popmenu(r.left, r.top - 290, menuItems(PHRASES.map(p => ({ icon: 'chat', label: p, onClick: () => send('CHAT', p) }))));
  };
  const sendBtn = h('button', { class: 'btn primary send', type: 'button', title: '보내기', html: icon('send'), onclick: submit });
  const bottom = [];
  if (room) {
    bottom.push(h('div', { class: 'reacts' }, REACTIONS.map((r, i) => h('button', { class: 'btn outline tiny', type: 'button', text: r, style: { '--c': REACT_C[i] }, title: '리액션 보내기', onclick: () => send('REACT', i) }))));
  }
  bottom.push(h('div', { class: 'chat-input' }, quick, input, sendBtn));
  wrap.append(tabs, list, logList, ...bottom);
  root.replaceChildren(wrap);

  const near = el => el.scrollHeight - el.scrollTop - el.clientHeight < 70;
  const push = (node, mine) => {
    empty.remove();
    const stick = near(list) || mine;
    list.append(node);
    while (list.children.length > 300) list.firstChild.remove();
    if (stick) list.scrollTop = list.scrollHeight;
    if (!mine) onIncoming?.();
  };
  const userMenu = (name, x, y) => popmenu(x, y, menuItems([
    { icon: 'chat', label: name + '님에게 귓속말', onClick: () => api.whisperTo(name) },
    { icon: 'block', label: prefs.isBlocked(name) ? '차단 풀기' : '차단하기', onClick: () => toggleBlock(name) },
    { icon: 'flag', label: '신고하기', onClick: () => report(name) },
  ]));
  const attachMenu = (node, name) => {
    node.addEventListener('contextmenu', e => { e.preventDefault(); userMenu(name, e.clientX, e.clientY); });
    let timer = null;
    node.addEventListener('pointerdown', e => { if (e.pointerType !== 'mouse') timer = setTimeout(() => userMenu(name, e.clientX, e.clientY), 550); });
    for (const ev of ['pointerup', 'pointerleave', 'pointercancel']) node.addEventListener(ev, () => clearTimeout(timer));
  };

  const api = {
    addChat({ id, name, avatar, text, spectator }) {
      const mine = id === S.myId;
      const node = h('div', { class: 'msg ' + (mine ? 'mine' : 'other') });
      if (!mine) node.append(avatarEl(avatar, false, 30));
      node.append(h('div', { class: 'body' }, mine ? null : h('span', { class: 'name', text: name + (spectator ? ' · 관전' : ''), style: { '--nc': AV[((avatar % 8) + 8) % 8] } }), h('div', { class: 'bubble', text })));
      if (!mine) attachMenu(node, name);
      push(node, mine);
    },
    addSys(text) { push(h('div', { class: 'sys', text }), true); },
    addWhisper(from, to, avatar, text) {
      const mine = from === S.myName;
      const node = h('div', { class: 'msg whisper ' + (mine ? 'mine' : 'other') });
      if (!mine) node.append(avatarEl(avatar, false, 30));
      node.append(h('div', { class: 'body' }, h('span', { class: 'name', text: mine ? '귓속말 → ' + to : from + ' → 나 (귓속말)' }), h('div', { class: 'bubble', text })));
      if (!mine) attachMenu(node, from);
      push(node, mine);
    },
    addLog(text, color = 'var(--text3)') {
      const t = new Date();
      const stick = near(logList);
      logList.append(h('div', { class: 'log', style: { '--c': color } }, h('i'), h('time', { text: `${String(t.getHours()).padStart(2, '0')}:${String(t.getMinutes()).padStart(2, '0')}` }), h('span', { text })));
      while (logList.children.length > 400) logList.firstChild.remove();
      if (stick) logList.scrollTop = logList.scrollHeight;
      if (logList.hidden && t2) logDot.hidden = false;
    },
    clear() { list.replaceChildren(empty); logList.replaceChildren(); logDot.hidden = true; t1.click(); },
    whisperTo(name) { input.value = '/w ' + name + ' '; input.focus(); },
    userMenu,
  };
  return api;
}

function toggleBlock(name) {
  const on = !prefs.isBlocked(name);
  if (!on) { prefs.block(name, false); toast(name + '님 차단을 풀었어요.'); renderLobby(); return; }
  confirmBox(name + '님을 차단할까요?', '차단하면 이 사람의 채팅과 귓속말이 보이지 않아요. 설정에서 언제든 풀 수 있어요.', '차단', true).then(ok => {
    if (ok) { prefs.block(name, true); toast(name + '님을 차단했어요.'); renderLobby(); }
  });
}

let lobbyChat, roomChat;

// ================================================================ 로비

function initLobby() {
  $('#btn-how').innerHTML = icon('help'); $('#btn-how').onclick = howTo;
  $('#btn-settings').innerHTML = icon('gear'); $('#btn-settings').onclick = settings;
  $('#btn-exit').innerHTML = icon('exit'); $('#btn-exit').onclick = () => { disconnect(); goStart(); };
  const me = $('#lobby-me');
  me.addEventListener('click', profileOnline);
  me.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); profileOnline(); } });
  $('#btn-quick').append(iconBtn('zap', '빠른 시작'));
  $('#btn-quick').onclick = () => send('QUICK');
  $('#btn-create').append(iconBtn('plus', '방 만들기'));
  $('#btn-create').onclick = createRoom;
  $('#room-filter').replaceWith(Object.assign(seg(['전체', '대기 중', '빈자리'], 0, i => { S.filter = i; renderRooms(); }, 'small'), { id: 'room-filter' }));
  const tabs = $('#lobby-tabs');
  tabs.addEventListener('click', e => {
    const b = e.target.closest('button');
    if (!b) return;
    tabs.querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b));
    $('.lobby-body').dataset.tab = b.dataset.tab;
    if (b.dataset.tab === 'chat') tabs.querySelector('.dot').hidden = true;
  });
  lobbyChat = makeChat($('#lobby-chat'), {
    room: false,
    onIncoming: () => { if ($('.lobby-body').dataset.tab !== 'chat' && matchMedia('(max-width: 900px)').matches) tabs.querySelector('.dot').hidden = false; },
  });
}

function renderLobby() {
  if (S.screen !== 'lobby') return;
  $('#lobby-server').replaceChildren(iconEl('server'), h('i', { class: 'dot' }), h('span', { text: S.serverName || '서버' }));
  updatePing();
  $('#lobby-me').replaceChildren(avatarEl(prefs.avatar, false, 34), h('div', { class: 'who' }, h('b', { text: S.myName }), h('small', { text: '프로필 바꾸기' })), iconEl('pencil'));
  renderRooms();
  const ul = $('#user-list');
  $('#user-count').textContent = `접속자 ${S.lobby.users.length}명`;
  ul.replaceChildren(...S.lobby.users.map(u => {
    const me = u.id === S.myId;
    const where = u.where === 'L' ? ['로비', 'var(--mint)'] : u.where === 'W' ? ['대기실', 'var(--sky)'] : ['게임 중', 'var(--amber)'];
    const row = h('div', { class: 'user' + (me ? ' me' : '') }, avatarEl(u.avatar, false, 28),
      h('span', { class: 'n', text: u.name + (me ? ' (나)' : ''), style: { color: prefs.isBlocked(u.name) ? 'var(--text3)' : '' } }),
      h('span', { class: 'w', text: where[0], style: { color: where[1] } }));
    if (!me) row.addEventListener('click', e => lobbyChat.userMenu(u.name, e.clientX, e.clientY));
    return row;
  }));
}

function renderRooms() {
  const list = $('#room-list');
  const rooms = S.lobby.rooms.filter(r => S.filter === 0 || (S.filter === 1 && !r.playing) || (S.filter === 2 && !r.playing && r.count < r.max));
  $('#room-count').textContent = S.lobby.rooms.length ? S.lobby.rooms.length + '개' : '';
  if (!rooms.length) {
    list.replaceChildren(h('div', { class: 'empty' },
      h('div', { class: 'tiles' }, tileEl(fakeTile('B', '?')), tileEl(fakeTile('W', '?')), tileEl(fakeTile('B', '?'))),
      h('b', { text: '열려 있는 방이 없어요' }),
      h('span', { text: "방을 만들거나 '빠른 시작'을 눌러 보세요. 친구가 들어오면 바로 시작할 수 있어요." })));
    return;
  }
  list.replaceChildren(...rooms.map(r => {
    const full = !r.playing && r.count >= r.max;
    const st = r.playing ? ['게임 중', 'var(--amber)'] : full ? ['꽉 참', 'var(--text3)'] : ['대기 중', 'var(--mint)'];
    const dots = h('span', { class: 'dots' }, `${r.count}/${r.max}`, ...Array.from({ length: r.max }, (_, i) => h('i', { class: i < r.count ? 'on' : '' })));
    const chips = [];
    chips.push(h('span', { class: 'chip', style: { '--c': 'var(--sky)' } }, iconEl('timer'), P.turnText(r.turnSec)));
    return h('button', { class: 'room-card', type: 'button', onclick: () => joinRoom(r), title: r.playing ? '관전하기' : full ? '자리 없음' : '입장하기' },
      h('div', { class: 'top' }, h('span', { class: 'rid', text: '#' + r.id }), r.locked ? h('span', { html: icon('lock'), style: { color: 'var(--amber)', display: 'inline-flex' } }) : null,
        h('span', { class: 'ttl', text: r.title }), h('span', { class: 'status', text: st[0], style: { '--c': st[1] } })),
      h('div', { class: 'host', text: '방장 ' + r.host + (r.spectators ? `  ·  관전 ${r.spectators}명` : '') }),
      h('div', { class: 'bottom' }, ...chips, dots));
  }));
}

async function joinRoom(r) {
  if (!r.playing && r.count >= r.max) { toast('방이 꽉 찼어요. 다른 방을 골라 보세요.', 'warn'); return; }
  let pw = '';
  if (r.locked) {
    pw = await promptBox('비밀번호가 있는 방이에요', `'${r.title}' 방의 비밀번호를 입력해 주세요.`, true);
    if (pw === null) return;
  }
  send(r.playing ? 'SPECTATE' : 'JOIN', r.id, pw);
}

// ================================================================ 방

function initRoom() {
  $('#btn-leave').append(iconBtn('back', '나가기'));
  $('#btn-leave').onclick = leaveRoom;
  $('#btn-room-how').innerHTML = icon('help'); $('#btn-room-how').onclick = howTo;
  $('#btn-room-settings').innerHTML = icon('gear'); $('#btn-room-settings').onclick = settings;
  const side = $('#room-chat');
  const fab = $('#chat-fab');
  roomChat = makeChat(side, {
    room: true,
    onIncoming: () => {
      if (!side.classList.contains('open') && matchMedia('(max-width: 900px)').matches) {
        S.unread++;
        const b = fab.querySelector('.badge');
        b.hidden = false; b.textContent = S.unread > 9 ? '9+' : S.unread;
      }
    },
  });
  fab.onclick = () => setDrawer(!side.classList.contains('open'));
  // 휴대폰에서 채팅 서랍이 열려 있을 때 게임판을 누르면 닫는다
  $('.room-main').addEventListener('pointerdown', () => { if (side.classList.contains('open')) setDrawer(false); });
  const main = $('.room-main');
  S.fx = h('div', { class: 'fx-layer' });
  main.append(S.fx);
  initBoardEvents();
}

/** 휴대폰 화면의 채팅 서랍 열고 닫기 */
function setDrawer(open) {
  const side = $('#room-chat'), fab = $('#chat-fab');
  side.classList.toggle('open', open);
  $('#screen-room').classList.toggle('chat-open', open);
  fab.firstChild.textContent = open ? '닫기 ' : '채팅 ';
  if (open) { S.unread = 0; fab.querySelector('.badge').hidden = true; }
}

function enterRoom() {
  roomChat.clear();
  S.game = null; S.picker = null; S.resultPending = false;
  S.memos.clear(); S.elim.clear();
  for (const m of Object.values(S.anim)) m.clear();
  if (S.resultModal) S.resultModal.close();
  buildWaiting();
  showBoard(false);
  show('room');
  setDrawer(false);
}

async function leaveRoom() {
  const r = S.room;
  const meSeat = S.game ? P.meOf(S.game) : null;
  if (r && r.playing && meSeat && meSeat.alive && S.game.phase !== 'OVER') {
    const ok = await confirmBox('게임을 나갈까요?', '게임 중에 나가면 기권으로 처리되고 남은 타일이 모두 공개돼요.', '나가기', true);
    if (!ok) return;
  }
  send('LEAVE');
}

function showBoard(on) {
  S.boardShown = on;
  $('#waiting').hidden = on;
  $('#board').hidden = !on;
}

function updateRoom() {
  const r = S.room;
  $('#room-title').textContent = r.title;
  $('#room-id').textContent = '#' + r.id;
  $('#room-info').textContent = `${r.members.length}/${r.max}명 · ${P.turnText(r.turnSec)}`;
  const sp = $('#room-specs');
  sp.hidden = !r.spectators.length;
  sp.replaceChildren(iconEl('eye'), h('span', { text: '관전 ' + r.spectators.length }));
  updatePing();
  updateWaiting();
  if (r.playing && !S.boardShown && S.game) showBoard(true);
  if (!r.playing && S.boardShown && !S.resultPending && !(S.resultModal && !S.resultModal.closed)) showBoard(false);
}

// ---------------------------------------------------------------- 대기실

const W = {};
function buildWaiting() {
  const box = $('#waiting');
  W.hint = h('p', { class: 'hint' });
  W.specs = h('div', { class: 'specs' });
  W.seats = h('div', { class: 'seats' });
  const send2 = withText => sendSettings(withText);
  W.max = seg(['2명', '3명', '4명'], 2, () => send2(false));
  W.time = seg(['30초', '45초', '60초', '90초', '무제한'], 2, () => send2(false));
  W.title = h('input', { class: 'input', maxlength: P.TITLE_MAX, placeholder: '방 제목' });
  W.pass = h('input', { class: 'input', type: 'password', maxlength: 12, placeholder: '비밀번호 (선택)', autocomplete: 'new-password' });
  W.apply = h('button', { class: 'btn small apply', type: 'button', text: '이름·비밀번호 적용', onclick: () => send2(true) });
  W.title.addEventListener('keydown', e => { if (e.key === 'Enter') send2(true); });
  W.action = h('button', { class: 'btn primary big', type: 'button', onclick: waitingAction });
  const lbl = t => h('label', { class: 'lbl', text: t });
  const settingsCard = h('div', { class: 'card settings-card' },
    h('div', { class: 'col' }, lbl('최대 인원'), W.max, lbl('한 번에 쓸 수 있는 시간'), W.time),
    h('div', { class: 'col' }, lbl('방 이름 · 비밀번호'), h('div', { class: 'names' }, W.title, W.pass), W.apply));
  box.replaceChildren(h('div', {}, h('h2', { text: '대기실' }), W.hint), W.specs, W.seats, settingsCard, h('div', { class: 'action' }, W.action));
}

function sendSettings(withText) {
  const r = S.room;
  if (!r) return;
  const pw = W.pass.value;
  let sendPw = withText ? pw : '*';
  if (withText && !pw && r.locked) sendPw = '';
  const turn = [30, 45, 60, 90, 0][W.time.value()];
  send('SETTINGS', withText ? W.title.value : r.title, W.max.value() + 2, sendPw, turn);
  if (withText) { W.pass.value = ''; toast(pw ? '방 이름과 비밀번호를 적용했어요.' : '방 이름을 바꿨어요.' + (r.locked ? ' 비밀번호도 없앴어요.' : ''), 'success'); }
}

function waitingAction() {
  const r = S.room;
  if (!r) return;
  if (r.spectators.some(s => s.id === S.myId)) send('JOIN', r.id, '');
  else if (r.hostId === S.myId) send('START');
  else { const me = r.members.find(m => m.id === S.myId); send('READY', me && me.ready ? 0 : 1); }
}

function updateWaiting() {
  const r = S.room;
  if (!W.seats) return;
  const host = r.hostId === S.myId;
  const spectator = r.spectators.some(s => s.id === S.myId);
  for (const c of [W.max, W.time]) c.disable(!host);
  for (const c of [W.title, W.pass, W.apply]) c.disabled = !host;
  W.max.set(r.max - 2);
  W.time.set(Math.max(0, [30, 45, 60, 90, 0].indexOf(r.turnSec)));
  if (document.activeElement !== W.title) W.title.value = r.title;

  W.seats.replaceChildren(...Array.from({ length: r.max }, (_, i) => {
    const m = r.members[i];
    if (!m) {
      return h('div', { class: 'seat-card empty' }, h('span', { class: 'ic', html: icon('users') }), h('b', { text: '빈자리' }), h('small', { text: '친구를 기다리는 중' }));
    }
    const me = m.id === S.myId, isHost = m.id === r.hostId;
    const st = !m.connected ? ['연결 끊김', 'var(--red)'] : isHost ? ['방장', 'var(--amber)'] : m.ready ? ['준비 완료', 'var(--mint)'] : ['준비 중', 'var(--text3)'];
    const card = h('div', { class: 'seat-card' + (me ? ' me' : '') }, avatarEl(m.avatar, false, 84),
      h('div', { class: 'nm', text: m.name + (me ? ' (나)' : '') }),
      h('span', { class: 'status', text: st[0], style: { '--c': st[1] } }));
    if (isHost) card.append(h('span', { class: 'crown', html: icon('crown') }));
    if (host && !me) {
      card.append(h('button', { class: 'kick', type: 'button', title: '내보내기', html: icon('close'), onclick: async () => {
        if (await confirmBox(m.name + '님을 내보낼까요?', '내보낸 사람은 이 방에 다시 들어올 수 없어요.', '내보내기', true)) send('KICK', m.id);
      } }));
    }
    return card;
  }));
  W.specs.textContent = r.spectators.length ? '관전: ' + r.spectators.map(s => s.name).join(', ') : '';

  const a = W.action;
  a.disabled = false;
  if (spectator) {
    const can = r.members.length < r.max && !r.playing;
    a.className = 'btn big'; a.textContent = can ? '참가하기' : '관전 중'; a.disabled = !can;
    W.hint.textContent = '관전 중이에요. 자리가 있으면 게임에 참가할 수 있어요.';
  } else if (host) {
    const allReady = r.members.every(m => m.id === r.hostId || m.ready);
    a.className = 'btn primary big';
    a.replaceChildren(iconEl('play'), h('span', { text: '게임 시작' }));
    a.disabled = r.members.length < 2 || !allReady;
    W.hint.textContent = r.members.length < 2 ? '친구가 들어오기를 기다리는 중이에요. 2명부터 시작할 수 있어요.' : allReady ? '모두 준비됐어요. 게임을 시작하세요!' : '모두 준비하면 시작할 수 있어요.';
  } else {
    const me = r.members.find(m => m.id === S.myId);
    const ready = me && me.ready;
    a.className = 'btn big ' + (ready ? '' : 'mint');
    a.replaceChildren(iconEl(ready ? 'close' : 'check'), h('span', { text: ready ? '준비 취소' : '준비하기' }));
    W.hint.textContent = ready ? '방장이 시작하기를 기다리는 중이에요.' : '준비를 누르면 방장이 게임을 시작할 수 있어요.';
  }
}

// ================================================================ 게임판

function onState(v) {
  const newGame = !S.boardShown || !S.game || (S.lastOver && v.phase !== 'OVER');
  if (newGame) {
    S.memos.clear(); S.elim.clear(); S.picker = null; S.pickSent = -1; S.dealAt = -1;
    for (const m of Object.values(S.anim)) m.clear();
    roomChat.addLog('새 게임이 시작됐어요.', 'var(--coral)');
    if (S.resultModal) S.resultModal.close();
    S.resultPending = false;
  }
  S.lastOver = v.phase === 'OVER';
  S.game = v;
  S.stateAt = performance.now();
  S.totalMs = Math.max(1, v.remainingMs);
  S.lastTick = -1;
  S.ded = P.analyze(v);
  if (v.phase !== 'SETUP') S.pickSent = -1;
  if (!P.isMyTurn(v) || (v.phase !== 'GUESS' && v.phase !== 'CONTINUE')) S.picker = null;
  if (S.picker) { const t = P.findTile(v, S.picker.uid); if (!t || t.revealed) S.picker = null; }
  if (!S.boardShown) showBoard(true);
  renderBoard();
  startRing();
}

function initBoardEvents() {
  const board = $('#board');
  board.addEventListener('click', e => {
    if (e.target.closest('.picker')) return;
    const pile = e.target.closest('.pile.can');
    if (pile) { send('DRAW', pile.dataset.color); return; }
    const slot = e.target.closest('.slot');
    if (slot) { send('JOKER', slot.dataset.pos); return; }
    const tile = e.target.closest('.tile.target');
    if (tile) { onTileClick(tile); return; }
    if (S.picker) { S.picker = null; renderBoard(); }
  });
  board.addEventListener('contextmenu', e => {
    const tile = e.target.closest('.seat:not(.mine) .tile.back');
    if (!tile) return;
    e.preventDefault();
    memoMenu(+tile.dataset.uid, e.clientX, e.clientY);
  });
  let lp = null;
  board.addEventListener('pointerdown', e => {
    if (e.pointerType === 'mouse') return;
    const tile = e.target.closest('.seat:not(.mine) .tile.back');
    if (tile) lp = setTimeout(() => { lp = 'done'; memoMenu(+tile.dataset.uid, e.clientX, e.clientY); }, 600);
  });
  for (const ev of ['pointerup', 'pointercancel', 'pointerleave']) board.addEventListener(ev, () => { if (lp && lp !== 'done') clearTimeout(lp); lp = null; });
  addEventListener('resize', () => { if (S.boardShown && S.game) fitTiles(); });
}

async function onTileClick(el) {
  const v = S.game;
  const uid = +el.dataset.uid;
  const seat = el.closest('.seat');
  const pid = +seat.dataset.pid;
  if (pid === S.myId) {
    if (v.phase === 'REVEAL_SELF' && await confirmBox('이 타일을 공개할까요?', '틀렸고 더미가 비어 있어서 내 숨은 타일 하나를 공개해야 해요.', '공개하기', true)) send('REVEAL', uid);
    return;
  }
  S.picker = { pid, uid };
  Sound.play('click');
  renderBoard();
}

function memoMenu(uid, x, y) {
  const grid = h('div', { class: 'grid' }, ...Array.from({ length: 12 }, (_, n) => h('button', { type: 'button', text: String(n), onclick: () => { S.memos.set(uid, String(n)); closeMenu(); renderBoard(); } })));
  popmenu(x, y, [h('div', { class: 'title', text: '메모 (나만 보여요)' }), grid,
    h('button', { type: 'button', text: '메모 지우기', onclick: () => { S.memos.delete(uid); closeMenu(); renderBoard(); } })]);
}

function seatOrder(v) {
  const i = v.seats.findIndex(s => s.pid === S.myId);
  if (i < 0) return { bottom: v.seats[0], others: v.seats.slice(1), spectator: true };
  const others = [];
  for (let k = 1; k < v.seats.length; k++) others.push(v.seats[(i + k) % v.seats.length]);
  return { bottom: v.seats[i], others, spectator: false };
}

function renderBoard() {
  const v = S.game;
  const board = $('#board');
  if (!v) return;
  const keep = board.scrollTop;
  const { bottom, others, spectator } = seatOrder(v);
  const opps = h('div', { class: 'opps' }, others.map(s => seatEl(v, s, false)));
  const center = centerEl(v);
  const mineRow = h('div', { class: 'mine-row' }, seatEl(v, bottom, !spectator));
  if (!spectator && v.phase !== 'SETUP') mineRow.append(actionsEl(v));
  const parts = [opps];
  parts.push(center, mineRow);
  board.replaceChildren(...parts);
  fitTiles();
  if (S.picker) placePicker(v);
  board.scrollTop = keep;
}

function fitTiles() {
  for (const row of document.querySelectorAll('#board .seat-tiles')) {
    const n = row.querySelectorAll('.tile').length || 1;
    const slots = row.querySelectorAll('.slot').length;
    const mine = row.closest('.seat').classList.contains('mine');
    const small = innerWidth < 600;
    const gap = small ? 4 : 8;
    const max = mine ? (small ? 52 : 74) : (small ? 42 : 58);
    const avail = row.clientWidth - slots * 10;
    const tw = Math.max(20, Math.min(max, (avail - gap * (n - 1)) / n));
    row.style.setProperty('--gap', gap + 'px');
    row.querySelectorAll('.tile').forEach(t => t.style.setProperty('--tw', tw + 'px'));
  }
}

function seatEl(v, s, mine) {
  const w = S.who.get(s.pid) || { name: '?', avatar: 0 };
  const turn = v.turnPid === s.pid && v.phase !== 'OVER';
  const seat = h('div', { class: `seat${mine ? ' mine' : ''}${turn ? ' turn' : ''}${!s.alive ? ' out' : ''}`, dataset: { pid: s.pid } });
  let sub, subCls = '';
  if (!s.alive) { sub = '탈락'; subCls = 'out'; }
  else if (!s.connected) { sub = '연결 끊김 · 기다리는 중'; subCls = 'bad'; }
  else if (v.phase === 'SETUP') {
    const done = s.picked || (mine && S.pickSent >= 0);
    sub = done ? '시작 패를 골랐어요' : '시작 패 고르는 중…'; subCls = done ? 'ok' : '';
  } else sub = '숨은 타일 ' + P.hiddenCount(s) + '개';
  const info = h('div', { class: 'seat-info' },
    h('div', { class: 'who' }, avatarEl(w.avatar, false, mine ? 46 : 40),
      h('div', { style: { minWidth: '0' } }, h('div', { class: 'nm', text: w.name + (mine ? ' (나)' : '') }), h('div', { class: 'sub ' + subCls, text: sub }))),
    turn ? h('span', { class: 'turn-tag', text: '차례' }) : null);
  if (v.phase === 'SETUP') {
    seat.append(info, mine ? pickEl(v, s) : pickWaitEl(s));
    return seat;
  }
  const tiles = h('div', { class: 'seat-tiles' });
  const myT = P.isMyTurn(v);
  const canTarget = !mine && myT && s.alive && (v.phase === 'GUESS' || v.phase === 'CONTINUE');
  const canSlot = mine && myT && v.phase === 'JOKER';
  const canReveal = mine && myT && v.phase === 'REVEAL_SELF';
  const now = performance.now();
  s.tiles.forEach((t, i) => {
    if (canSlot) tiles.append(h('button', { class: 'slot', type: 'button', title: '여기에 조커 놓기', dataset: { pos: i } }));
    let cls = '';
    if ((canTarget || canReveal) && !t.revealed) cls += ' target';
    if (canReveal && !t.revealed) cls += ' danger';
    if (S.picker && S.picker.uid === t.uid) cls += ' sel';
    const te = tileEl(t, { mine, cls: cls.trim(), note: !mine && !P.isKnown(t) ? S.memos.get(t.uid) : null });
    playAnim(te, t.uid, now);
    if (S.dealAt >= 0 && now - S.dealAt < 1400) {
      // 시작 패: 왼쪽부터 하나씩 놓인다 (다시 그려도 이어서 재생되도록 시작 시각을 기준으로 지연을 준다)
      te.classList.add('deal');
      te.style.animationDelay = (S.dealAt + i * 90 - now) + 'ms';
    }
    const cell = h('div', { class: 'tcell' }, te);
    if (mine && !t.revealed && v.phase !== 'OVER') cell.append(h('span', { class: 'lock', html: icon('lock') }));
    tiles.append(cell);
  });
  if (canSlot) tiles.append(h('button', { class: 'slot', type: 'button', title: '여기에 조커 놓기', dataset: { pos: s.tiles.length } }));
  seat.append(info, tiles);
  if (!s.alive) {
    const st = h('span', { class: 'stamp', text: '탈락' });
    const t0 = S.elim.get(s.pid);
    if (t0 !== undefined && now - t0 < 450) st.style.animationDelay = (t0 - now) + 'ms';
    else st.style.animation = 'none';
    seat.append(st);
  }
  return seat;
}

/** 시작 패 고르기: 검정·흰색 조합 버튼들 (왼쪽은 검정이 많은 조합) */
function pickEl(v, s) {
  const hs = v.handSize;
  const chosen = s.pick >= 0 ? s.pick : S.pickSent;
  const box = h('div', { class: 'seat-pick' });
  for (let black = hs; black >= 0; black--) {
    const on = chosen === black, dim = chosen >= 0 && !on;
    const label = `검정 ${black} · 흰색 ${hs - black}`;
    const mini = h('span', { class: 'mini' }, ...Array.from({ length: hs }, (_, k) => tileEl(fakeTile(k < black ? 'B' : 'W', '?'))));
    const b = h('button', {
      class: 'pick-opt' + (on ? ' on' : '') + (dim ? ' dim' : ''), type: 'button', disabled: chosen >= 0, 'aria-label': label + ' 받기',
      onclick: () => {
        if (S.pickSent >= 0) return;
        S.pickSent = black; send('PICK', black); Sound.play('click'); renderBoard();
      },
    }, mini, h('span', { class: 'lb' }, h('b', { text: '검정 ' + black }), h('b', { text: '흰색 ' + (hs - black) })), on ? h('span', { class: 'badge', text: '선택' }) : null);
    box.append(b);
  }
  return box;
}

/** 시작 패 고르기: 다른 사람 자리에는 골랐는지만 보여 준다 (몇 개인지는 나눠 줄 때까지 비밀) */
function pickWaitEl(s) {
  if (!s.alive) return h('div', { class: 'seat-pick wait' });
  return s.picked
    ? h('div', { class: 'seat-pick wait ok' }, iconEl('check'), h('span', { text: '골랐어요' }))
    : h('div', { class: 'seat-pick wait' }, h('span', { class: 'dots3' }, h('i'), h('i'), h('i')), h('span', { text: '고르는 중' }));
}

const needPick = v => { const me = P.meOf(v); return v.phase === 'SETUP' && !!me && me.alive && !me.picked && S.pickSent < 0; };

/** 애니메이션이 이미 진행 중이면 그만큼 앞당겨서 이어 재생한다 (상태가 바뀌어 타일을 다시 그려도 끊기지 않게) */
function playAnim(el, uid, now) {
  const table = [['flip', 450], ['pop', 380], ['shake', 450], ['ok', 900]];
  for (const [cls, dur] of table) {
    const t0 = S.anim[cls].get(uid);
    if (t0 === undefined) continue;
    const age = now - t0;
    if (age > dur) { S.anim[cls].delete(uid); continue; }
    el.classList.add(cls);
    el.style.animationDelay = (-age) + 'ms';
    return;
  }
}

function centerEl(v) {
  const myDraw = P.isMyTurn(v) && v.phase === 'DRAW';
  const pile = (color, count) => {
    const can = myDraw && count > 0;
    const stack = count > 0 ? h('div', { class: 'stack' }, ...Array.from({ length: Math.min(3, count) }, () => tileEl(fakeTile(color, '?')))) : h('div', { class: 'none' });
    return h('button', { class: 'pile' + (can ? ' can' : ''), type: 'button', dataset: { color }, title: can ? (color === 'B' ? '검정' : '흰색') + ' 타일 가져오기' : null },
      stack, h('span', { text: (color === 'B' ? '검정 ' : '흰색 ') + count }));
  };
  const piles = h('div', { class: 'piles' }, pile('B', v.deckB), pile('W', v.deckW));
  const [l1, l2] = statusText(v);
  const ring = h('div', { class: 'ring', html: '<svg viewBox="0 0 64 64"><circle class="bg" cx="32" cy="32" r="27"/><circle class="fg" cx="32" cy="32" r="27"/></svg><b></b>' });
  S.ringEl = ring;
  const st = h('div', { class: 'status-text' }, h('b', { class: P.isMyTurn(v) || needPick(v) ? 'mine' : '', text: l1 }), h('span', { class: 'desc', text: l2 }),
    v.streak > 0 && v.phase === 'CONTINUE' ? h('span', { class: 'chip', html: icon('check') + `<span>${v.streak}연속 정답</span>` }) : null);
  const pb = h('div', { class: 'pending-box' });
  if (v.pending) {
    pb.append(tileEl(v.pending), h('div', {}, h('b', { text: '가져온 타일' }), h('small', { text: v.pending.value === P.JOKER ? '조커! 원하는 자리에' : P.isMyTurn(v) ? '숨긴 채 들고 있어요' : '숫자는 주인만 알아요' })));
  } else {
    pb.append(h('div', { class: 'slot-empty' }), h('div', {}, h('small', { text: P.deckTotal(v) === 0 ? '더미가 비었어요' : '가져온 타일 없음' })));
  }
  return h('div', { class: 'center' }, piles, h('div', { class: 'status-box' }, ring, st), pb);
}

function statusText(v) {
  if (v.phase === 'OVER') return ['게임이 끝났어요', '모든 타일이 공개됐어요. 결과를 확인해 보세요.'];
  if (v.phase === 'SETUP') {
    if (!P.meOf(v)) return ['관전 중 · 시작 패 고르는 중', '모두 고르면 타일을 나눠 줘요.'];
    if (needPick(v)) return ['시작 패를 고르세요', '아래에서 색 조합을 골라요. 숫자는 무작위!'];
    return ['다른 사람을 기다리는 중', '모두 고르면 고른 대로 타일을 나눠 줘요.'];
  }
  if (P.isMyTurn(v)) {
    switch (v.phase) {
      case 'DRAW': return ['타일을 가져오세요', '더미에서 검정 또는 흰색을 고르세요.'];
      case 'JOKER': return ['조커를 놓을 자리를 고르세요', '내 줄의 노란 칸 중 원하는 곳을 누르세요.'];
      case 'GUESS': return ['상대 타일을 맞혀 보세요', '숨은 타일을 눌러 숫자를 골라요 (우클릭·길게 누르기: 메모)'];
      case 'CONTINUE': return ['정답! 한 번 더 할까요?', '더 맞히면 이어서, 멈추면 가져온 타일을 숨긴 채 넣어요.'];
      case 'REVEAL_SELF': return ['아쉬워요! 내 타일 하나를 공개하세요', '더미가 비어서 내 숨은 타일 중 하나를 골라야 해요.'];
      default:
    }
  }
  const who = nameOf(v.turnPid) + '님';
  const pre = P.meOf(v) ? '' : '관전 중 · ';
  switch (v.phase) {
    case 'DRAW': return [pre + who + ' 차례', '타일을 가져오는 중이에요.'];
    case 'JOKER': return [pre + who + ' 차례', '조커를 놓을 자리를 고르는 중이에요.'];
    case 'CONTINUE': return [pre + who + '이 맞혔어요!', '더 맞힐지 고민하는 중이에요.'];
    case 'REVEAL_SELF': return [pre + who + ' 차례', '공개할 자기 타일을 고르는 중이에요.'];
    default: return [pre + who + ' 차례', '누구의 타일을 노릴지 추리하는 중이에요.'];
  }
}

function actionsEl(v) {
  const box = h('div', { class: 'actions' });
  if (P.meOf(v) && P.isMyTurn(v) && v.phase === 'CONTINUE') {
    box.append(h('button', { class: 'btn primary', type: 'button', title: '가져온 타일을 숨긴 채 넣고 차례를 넘겨요', html: icon('check') + '<span>턴 마치기</span>', onclick: () => send('STOP') }));
  }
  return box;
}

function placePicker(v) {
  const board = $('#board');
  const tile = board.querySelector(`.tile[data-uid="${S.picker.uid}"]`);
  const t = P.findTile(v, S.picker.uid);
  const owner = P.ownerOf(v, S.picker.uid);
  if (!tile || !t || !owner) { S.picker = null; return; }
  const idx = owner.tiles.findIndex(x => x.uid === t.uid) + 1;
  const numBtn = (label, value, extra = '') => {
    // 판 위에 이미 보이는 숫자(내 타일, 공개된 타일)는 이 타일일 수 없으니 막아 둔다
    const known = !S.ded.unknown(t.color, value);
    const b = h('button', { class: `n ${extra}`, type: 'button', text: label });
    b.disabled = known;
    b.onclick = () => { send('GUESS', S.picker.pid, S.picker.uid, value === P.JOKER ? 'J' : value); Sound.play('click'); S.picker = null; renderBoard(); };
    return b;
  };
  const grid = h('div', { class: 'grid' }, ...Array.from({ length: 12 }, (_, n) => numBtn(String(n), n)));
  grid.append(numBtn('조커', P.JOKER, 'jk'));
  const pk = h('div', { class: 'picker' },
    h('h4', { text: `${nameOf(owner.pid)}의 ${idx}번째 · ${t.color === 'B' ? '검정' : '흰색'}` }), h('p', { text: '어떤 숫자일까요?' }), grid,
    h('button', { class: 'cancel', type: 'button', text: '취소 (Esc)', onclick: () => { S.picker = null; renderBoard(); } }));
  board.append(pk);
  const br = board.getBoundingClientRect(), tr = tile.getBoundingClientRect();
  const pw = pk.offsetWidth, ph = pk.offsetHeight;
  const left = Math.max(8, Math.min(board.clientWidth - pw - 8, tr.left - br.left + tr.width / 2 - pw / 2));
  let top = tr.bottom - br.top + board.scrollTop + 12;
  if (top + ph > board.scrollHeight - 4 && tr.top - br.top + board.scrollTop - ph - 12 > 0) top = tr.top - br.top + board.scrollTop - ph - 12;
  pk.style.left = left + 'px';
  pk.style.top = top + 'px';
}

// ---------------------------------------------------------------- 남은 시간 링

let ringRaf = 0;
function startRing() {
  cancelAnimationFrame(ringRaf);
  const loop = () => {
    const v = S.game, ring = S.ringEl;
    if (!v || !ring || !ring.isConnected) { ringRaf = requestAnimationFrame(loop); if (!v) cancelAnimationFrame(ringRaf); return; }
    const fg = ring.querySelector('.fg'), label = ring.querySelector('b');
    if (v.remainingMs < 0 || v.phase === 'OVER') {
      ring.classList.add('none');
      label.innerHTML = icon('timer');
      return;
    }
    const remain = Math.max(0, v.remainingMs - (performance.now() - S.stateAt));
    const frac = Math.max(0, Math.min(1, remain / S.totalMs));
    const sec = Math.ceil(remain / 1000);
    const circ = 2 * Math.PI * 27;
    fg.style.strokeDasharray = circ;
    fg.style.strokeDashoffset = circ * (1 - frac);
    ring.classList.toggle('low', sec <= 10);
    if (label.textContent !== String(sec)) label.textContent = sec;
    if ((P.isMyTurn(v) || needPick(v)) && sec <= 5 && sec > 0 && sec !== S.lastTick) { S.lastTick = sec; Sound.play('tick'); }
    if (remain > 0) ringRaf = requestAnimationFrame(loop);
  };
  ringRaf = requestAnimationFrame(loop);
}

// ---------------------------------------------------------------- 이벤트 (애니메이션, 소리, 기록)

function fxPos(el) {
  const base = S.fx.getBoundingClientRect(), r = el.getBoundingClientRect();
  return { x: r.left - base.left + r.width / 2, y: r.top - base.top };
}
function floatText(text, color, x, y, delay = 0) {
  const f = h('div', { class: 'float', text, style: { left: x + 'px', top: Math.max(26, y) + 'px', '--c': color, animationDelay: delay + 'ms' } });
  S.fx.append(f);
  setTimeout(() => f.remove(), 1350 + delay);
}
function banner(text, color, big) {
  S.fx.querySelectorAll('.banner').forEach(b => b.remove());
  const b = h('div', { class: 'banner' + (big ? ' big' : ''), text, style: { '--c': color } });
  S.fx.append(b);
  setTimeout(() => b.remove(), big ? 2700 : 1400);
}
const tileNode = uid => document.querySelector(`#board .tile[data-uid="${uid}"]`);

let titleFlash = 0;
function flashTitle() {
  if (!document.hidden) return;
  clearInterval(titleFlash);
  let on = false;
  titleFlash = setInterval(() => { on = !on; document.title = on ? '● 내 차례! — DAVINCI CODE' : 'DAVINCI CODE'; }, 900);
}
document.addEventListener('visibilitychange', () => { if (!document.hidden) { clearInterval(titleFlash); document.title = 'DAVINCI CODE'; } });

function onEvent(m) {
  const type = m.s(0);
  const now = performance.now();
  const log = (t, c) => roomChat.addLog(t, c);
  switch (type) {
    case 'SETUP': {
      const playing = !(S.room && S.room.spectators.some(x => x.id === S.myId));
      banner(playing ? '시작 패를 고르세요' : '시작 패 고르는 중', 'var(--coral)');
      if (playing) { Sound.play('turn'); flashTitle(); }
      log(`── 시작 패 고르기: 검정과 흰색을 합쳐 ${m.i(1, 4)}개 받아요 ──`);
      break;
    }
    case 'PICK': {
      const pid = m.i(1);
      log(pid === S.myId ? '시작 패를 골랐어요.' : nameOf(pid) + '님이 시작 패를 골랐어요.', 'var(--text2)');
      break;
    }
    case 'DEAL':
      S.dealAt = now;
      Sound.play('place');
      log('고른 대로 타일을 나눠 줬어요. 숫자는 무작위예요.', 'var(--mint)');
      break;
    case 'TURN': {
      const pid = m.i(1), mine = pid === S.myId;
      banner(mine ? '내 차례!' : nameOf(pid) + '님의 차례', mine ? 'var(--coral)' : 'var(--sky)');
      if (mine) { Sound.play('turn'); flashTitle(); }
      log(`── ${mine ? '내' : nameOf(pid) + '님의'} 차례 ──`);
      break;
    }
    case 'DRAW': {
      const pid = m.i(1), c = m.s(2);
      Sound.play('draw');
      log(`${subj(pid)} ${c === 'B' ? '검정' : '흰색'} 타일을 가져왔어요.`, 'var(--text2)');
      break;
    }
    case 'GUESS': {
      const pid = m.i(1), target = m.i(2), uid = m.i(3), ok = m.b(5);
      const val = m.s(4) === 'J' ? '조커' : m.s(4);
      const el = tileNode(uid);
      if (el) {
        const p = fxPos(el);
        floatText(val + '?', 'var(--text)', p.x, p.y - 4);
        floatText(ok ? '정답!' : '땡!', ok ? 'var(--mint)' : 'var(--red)', p.x, p.y - 34, 350);
      }
      S.anim[ok ? 'ok' : 'shake'].set(uid, now + 300);
      const seat = S.game ? P.seatOf(S.game, target) : null;
      const idx = seat ? seat.tiles.findIndex(t => t.uid === uid) + 1 : 0;
      Sound.play(ok ? 'correct' : 'wrong');
      log(`${nameOf(pid)} → ${nameOf(target)}의 ${idx}번째 타일을 '${val}'(으)로 추리 … ${ok ? '정답!' : '틀림'}`, ok ? 'var(--mint)' : 'var(--red)');
      break;
    }
    case 'REVEAL': S.anim.flip.set(m.i(2), now + 320); break;
    case 'PLACE': {
      const pid = m.i(1), uid = m.i(2), open = m.b(3);
      S.anim.pop.set(uid, now);
      Sound.play('place');
      log(`${subj(pid)} 가져온 타일을 ${open ? '공개한 채로' : '숨긴 채로'} 넣었어요.`, open ? 'var(--coral)' : 'var(--text2)');
      break;
    }
    case 'ELIM': {
      const pid = m.i(1);
      S.elim.set(pid, now + 400);
      log(`${nameOf(pid)}${pid === S.myId ? '는' : '님은'} 모든 타일이 공개되어 탈락했어요.`, 'var(--amber)');
      if (pid === S.myId) Sound.play('lose');
      break;
    }
    case 'TIMEOUT': {
      const pid = m.i(1);
      const av = document.querySelector(`#board .seat[data-pid="${pid}"] .avatar`);
      if (av) { const p = fxPos(av); floatText('시간 초과', 'var(--amber)', p.x, p.y); }
      log(m.s(2) === 'SETUP' ? `${nameOf(pid)} 시간 초과 — 시작 패를 임의로 정했어요.` : `${nameOf(pid)} 시간 초과 — 자동으로 진행했어요.`, 'var(--amber)');
      break;
    }
    case 'FORFEIT': log(nameOf(m.i(1)) + ' 기권', 'var(--amber)'); break;
    case 'OVER': {
      const w = m.i(1), me = w === S.myId;
      banner(me ? '승리!' : nameOf(w) + '님 승리!', me ? 'var(--amber)' : 'var(--sky)', true);
      confetti();
      const playing = S.game && P.meOf(S.game);
      Sound.play(me ? 'win' : playing ? 'lose' : 'correct');
      log(`게임 끝! ${me ? '내가' : nameOf(w) + '님이'} 이겼어요.`, 'var(--amber)');
      break;
    }
    default:
  }
}

function onReact(pid, code) {
  const c = Math.max(0, Math.min(5, code));
  roomChat.addLog(`${nameOf(pid)} : ${REACTIONS[c]}`);
  if (!S.boardShown) return;
  const av = document.querySelector(`#board .seat[data-pid="${pid}"] .avatar`);
  if (!av) return;
  const base = S.fx.getBoundingClientRect(), r = av.getBoundingClientRect();
  const b = h('div', { class: 'react', text: REACTIONS[c], style: { left: (r.right - base.left - 6) + 'px', top: (r.top - base.top + 6) + 'px', '--c': REACT_C[c] } });
  S.fx.append(b);
  setTimeout(() => b.remove(), 2600);
}

function confetti() {
  if (prefs.reduce || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const cv = $('#confetti'), ctx = cv.getContext('2d');
  cv.width = innerWidth * devicePixelRatio; cv.height = innerHeight * devicePixelRatio;
  ctx.scale(devicePixelRatio, devicePixelRatio);
  const cs = ['#FF7A59', '#FFC15E', '#3DD6B0', '#5EA8FF', '#A98BFF', '#ffffff'];
  const ps = Array.from({ length: 140 }, () => ({ x: Math.random() * innerWidth, y: -20 - Math.random() * innerHeight * 0.5, vx: (Math.random() - 0.5) * 60, vy: 120 + Math.random() * 180, r: Math.random() * 6, vr: (Math.random() - 0.5) * 8, s: 6 + Math.random() * 7, c: cs[Math.floor(Math.random() * cs.length)] }));
  const t0 = performance.now();
  const step = now => {
    const t = (now - t0) / 1000;
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    if (t > 4) return;
    ctx.globalAlpha = t > 3 ? 4 - t : 1;
    for (const p of ps) {
      const x = p.x + p.vx * t + Math.sin(t * 3 + p.r) * 14, y = p.y + p.vy * t + 40 * t * t;
      ctx.save(); ctx.translate(x, y); ctx.rotate(p.r + p.vr * t); ctx.fillStyle = p.c; ctx.fillRect(-p.s / 2, -p.s / 4, p.s, p.s / 2); ctx.restore();
    }
    requestAnimationFrame(step);
  };
  requestAnimationFrame(step);
}

function onResult(m) {
  S.resultPending = true;
  setTimeout(() => {
    S.resultPending = false;
    if (S.screen !== 'room') return;
    const ranks = m.list(0);
    const mine = ranks.find(r => +r[0] === S.myId);
    const place = mine ? +mine[3] : 0;
    const title = !place ? '게임 종료' : place === 1 ? '승리했어요!' : place + '위로 마쳤어요';
    const sub = !place ? '관전한 게임의 최종 순위예요.' : place === 1 ? '마지막까지 타일을 지켜 냈어요.' : '다시 하려면 대기실에서 준비를 눌러 주세요.';
    const medal = ['var(--amber)', '#C9D3E6', '#D9965B'];
    const list = h('div', {}, ranks.map(r => {
      const pl = +r[3];
      return h('div', { class: 'rank-row' + (pl === 1 ? ' first' : '') + (+r[0] === S.myId ? ' me' : '') },
        h('span', { class: 'medal', text: String(pl), style: { background: medal[pl - 1] || 'var(--text3)' } }), avatarEl(+r[2], false, 36),
        h('div', { class: 'nm' }, h('b', { text: r[1] })),
        pl === 1 ? h('span', { class: 'chip', text: '우승', style: { '--c': 'var(--amber)' } }) : null);
    }));
    S.resultModal = modal({
      title, sub, body: list,
      actions: [{ label: '로비로 나가기', onClick: api => { api.close(); send('LEAVE'); } }, { label: '대기실로', kind: 'primary' }],
      onClose: () => { if (S.room && !S.room.playing && S.screen === 'room') showBoard(false); },
    });
  }, 1900);
}

// ================================================================ 대화상자

/** 캐릭터 고르기 (첫 화면과 프로필 창에서 같이 쓴다) */
function avatarPicker(selected) {
  const box = h('div', { class: 'avatar-pick', role: 'radiogroup', 'aria-label': '캐릭터 고르기' });
  let value = selected;
  const draw = () => box.replaceChildren(...Array.from({ length: 8 }, (_, i) => h('button', {
    type: 'button', class: i === value ? 'on' : '', 'aria-label': '캐릭터 ' + (i + 1), html: avatarSvg(i, false, 38),
    onclick: () => { value = i; draw(); },
  })));
  draw();
  return { el: box, value: () => value };
}

/** 로비에서 닉네임·캐릭터 바꾸기. 서버가 받아들이면 다른 사람들에게도 바로 바뀐 모습이 보인다 */
function profileOnline() {
  const pick = avatarPicker(prefs.avatar);
  const nick = h('input', { class: 'input', maxlength: P.NAME_MAX, placeholder: '2~10글자 · 한글, 영문, 숫자, _', spellcheck: 'false', value: S.myName });
  const err = h('p', { class: 'err', role: 'alert' });
  let saveBtn;
  const submit = api => {
    const name = nick.value.trim();
    const why = P.checkName(name);
    nick.classList.toggle('bad', !!why);
    if (why) { err.textContent = why; return; }
    if (name === S.myName && pick.value() === prefs.avatar) { api.close(); return; }
    err.textContent = '';
    saveBtn.disabled = true; saveBtn.querySelector('span').textContent = '저장 중…';
    S.profileReply = problem => {
      if (!problem) { api.close(); return; }
      saveBtn.disabled = false; saveBtn.querySelector('span').textContent = '저장';
      err.textContent = problem; nick.classList.add('bad');
    };
    send('PROFILE', name, pick.value());
  };
  const m = modal({
    title: '프로필 바꾸기', sub: '다른 사람에게 보이는 닉네임과 캐릭터예요.', width: 480,
    body: h('div', {}, h('label', { class: 'lbl', text: '캐릭터' }), pick.el, h('label', { class: 'lbl', text: '닉네임' }), nick, err,
      h('p', { class: 'para', style: { color: 'var(--text3)', fontSize: '13px' }, text: '방에 들어가 있을 때는 바꿀 수 없어요. 로비에서 바꾸면 접속자 목록에도 바로 보여요.' })),
    actions: [{ label: '취소' }, { label: '저장', kind: 'primary', ref: b => { saveBtn = b; }, onClick: submit }],
    onClose: () => { S.profileReply = null; },
  });
  nick.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.isComposing) submit(m); });
}

function createRoom() {
  const title = h('input', { class: 'input', maxlength: P.TITLE_MAX, value: S.myName + '님의 방' });
  const max = seg(['2명', '3명', '4명'], 2);
  const time = seg(['30초', '45초', '60초', '90초', '무제한'], 2);
  const pass = h('input', { class: 'input', type: 'password', maxlength: 12, placeholder: '비우면 누구나 들어올 수 있어요', autocomplete: 'new-password' });
  const lbl = t => h('label', { class: 'lbl', text: t });
  const ok = api => {
    send('CREATE', title.value, max.value() + 2, pass.value, [30, 45, 60, 90, 0][time.value()]);
    api.close();
  };
  const m = modal({
    title: '방 만들기', width: 500,
    body: h('div', {}, lbl('방 제목'), title, lbl('최대 인원'), max, lbl('한 번에 쓸 수 있는 시간'), time, lbl('비밀번호 (선택)'), pass),
    actions: [{ label: '취소' }, { label: '방 만들기', kind: 'primary', icon: 'plus', onClick: ok }],
  });
  title.addEventListener('keydown', e => { if (e.key === 'Enter') ok(m); });
}

function settings() {
  const save = () => { prefs.save(); document.body.classList.toggle('reduce', prefs.reduce); };
  const vol = h('input', { type: 'range', min: 0, max: 100, value: prefs.volume, style: { width: '160px', accentColor: 'var(--coral)' } });
  vol.addEventListener('change', () => { prefs.volume = +vol.value; save(); Sound.ensure(); Sound.play('correct'); });
  const blocked = h('div', {});
  const fillBlocked = () => {
    blocked.replaceChildren(...(prefs.blocked.length ? prefs.blocked.map(n => row(n, null, h('button', { class: 'btn small', type: 'button', text: '차단 풀기', onclick: () => { prefs.block(n, false); fillBlocked(); } })))
      : [h('p', { class: 'para', text: '차단한 사람이 없어요. 채팅에서 닉네임을 우클릭(휴대폰은 길게 누르기)하면 차단할 수 있어요.' })]));
  };
  fillBlocked();
  modal({
    title: '설정', width: 520,
    body: h('div', {},
      h('div', { class: 'sec', text: '소리' }),
      row('효과음', null, switchEl(prefs.sound, v => { prefs.sound = v; save(); Sound.ensure(); Sound.play('click'); })),
      row('음량', null, vol),
      row('채팅 알림음', '새 메시지가 오면 짧게 알려요', switchEl(prefs.chatSound, v => { prefs.chatSound = v; save(); })),
      h('div', { class: 'sec', text: '게임' }),
      row('움직임 줄이기', '애니메이션과 배경 효과를 줄여요', switchEl(prefs.reduce, v => { prefs.reduce = v; save(); })),
      h('div', { class: 'sec', text: '차단한 사람' }), blocked),
    actions: [{ label: '게임 방법', kind: 'ghost', onClick: api => { api.close(); howTo(); } }, { label: '개인정보', kind: 'ghost', onClick: api => { api.close(); privacy(); } }, { label: '완료', kind: 'primary' }],
  });
}

function report(name) {
  const reasons = ['욕설·비하', '도배', '부적절한 닉네임', '게임 방해', '기타'];
  const s = seg(reasons, 0, null, 'small');
  modal({
    title: name + '님 신고', width: 540,
    body: h('div', {}, h('label', { class: 'lbl', text: '이유' }), s, h('p', { class: 'para', style: { marginTop: '12px', color: 'var(--text3)' }, text: '신고하면 최근 채팅 기록과 함께 운영진에게 전달돼요. 거짓 신고는 제재될 수 있어요.' })),
    actions: [{ label: '취소' }, { label: '신고하기', kind: 'danger', icon: 'flag', onClick: api => { send('REPORT', name, reasons[s.value()]); api.close(); } }],
  });
}

function privacy() {
  modal({
    title: '정보 · 개인정보', width: 540,
    body: h('div', {},
      h('p', { class: 'para', text: `DAVINCI CODE 웹 버전 ${P.APP_VERSION} · 만든 사람: Team STIM (고제윤, 임재윤)` }),
      h('div', { class: 'sec', text: '개인정보 처리방침 (요약)' }),
      h('p', { class: 'para', text: '• 수집하는 정보: 닉네임, 캐릭터 번호, 이 브라우저에서 만든 임의의 기기 ID(끊겼을 때 재접속용). 게임 기록은 서버에 남기지 않아요.\n• 채팅은 서버에 저장하지 않아요. 신고가 들어온 경우에만 해당 사용자의 최근 채팅이 신고 기록에 함께 남아요.\n• 위치, 연락처, 결제 정보는 수집하지 않고 광고나 외부 분석 도구도 쓰지 않아요.\n• 설정은 이 브라우저의 저장소에만 남고, 사이트 데이터를 지우면 함께 지워져요.' }),
      h('div', { class: 'sec', text: '이용 규칙' }),
      h('p', { class: 'para', text: '욕설·비하·도배·게임 방해는 금지예요. 채팅은 자동으로 걸러지고, 신고·차단 기능이 있어요.' }),
      h('div', { class: 'sec', text: '저작권' }),
      h('p', { class: 'para', text: '그림·아이콘·효과음은 모두 코드로 직접 만든 원본이에요. 숫자 타일 추리 규칙은 전통 추리 게임 방식을 바탕으로 직접 구현했어요.' })),
    actions: [{ label: '닫기', kind: 'primary' }],
  });
}

function howTo() {
  const pages = [
    ['목표', '상대가 숨겨 둔 숫자 타일을 모두 맞혀서 공개시키면 그 사람은 탈락해요. 마지막까지 내 타일을 지킨 사람이 이겨요.',
      () => [line('상대', [['B', 1], ['W', 3, 'sel'], ['W', 6], ['B', '?']]), line('나', [['W', 0], ['B', 4], ['B', 8], ['W', 11]])]],
    ['시작 패 고르기', '게임을 시작하면 처음 받을 타일을 검정 몇 개, 흰색 몇 개로 받을지 모두 동시에 골라요 (2~3명은 4개, 4명은 3개). 숫자는 무작위예요. 20초 안에 안 고르면 임의로 정해져요.',
      () => [line('', [['B', '?'], ['B', '?'], ['B', '?'], ['W', '?']]), h('small', { text: '예) 검정 3 · 흰색 1 — 색 조합만 내가 골라요', style: { color: 'var(--mint)' } })]],
    ['정렬 규칙이 열쇠', '모든 줄은 작은 숫자가 왼쪽에 놓여요. 같은 숫자면 검정이 왼쪽이에요. 공개된 타일 사이에 낀 숨은 타일은 숫자 범위가 좁아져요.',
      () => [line('', [['B', 1], ['W', 1], ['B', '?', 'sel'], ['W', 7], ['B', 9]]), h('small', { text: '가운데 검정 타일은 2~7 사이의 검정 숫자!', style: { color: 'var(--mint)' } })]],
    ['내 차례', '① 더미에서 검정이나 흰색 타일을 하나 가져와요. ② 상대의 숨은 타일 하나를 눌러 숫자를 골라요. 우클릭(휴대폰은 길게 누르기)으로 추측을 메모할 수 있어요.',
      () => [line('', [['B', '?'], ['W', '?'], null, ['B', '?', 'sel']]), h('small', { text: '① 가져오기  →  ② "5일까?"' })]],
    ['맞히면 · 틀리면', '맞히면 그 타일이 공개되고 한 번 더 도전하거나 멈출 수 있어요. 멈추면 가져온 타일을 숨긴 채 넣어요. 틀리면 가져온 타일이 모두에게 공개된 채로 들어가요.',
      () => [line('', [['W', 5, 'ok'], null, ['B', 8, 'shake', true]]), h('small', { text: '정답! 한 번 더        틀리면 가져온 타일 공개' })]],
    ['조커', "조커(—)는 아무 자리에나 놓을 수 있는 특별한 타일이에요. 숫자 정렬 규칙을 따르지 않아서 추리를 헷갈리게 해요. 맞힐 때는 숫자 대신 '조커'를 고르면 돼요. 처음 나눠 줄 때는 빠져 있다가 더미에 섞여요.",
      () => [line('', [['B', 2], ['W', 'J', 'sel'], ['W', 1], ['B', 7], ['W', 9]]), h('small', { text: '조커는 정렬 규칙과 상관없이 아무 자리에나!', style: { color: 'var(--amber)' } })]],
  ];
  function line(label, items) {
    return h('div', { class: 'line' }, label ? h('small', { text: label, style: { width: '30px' } }) : null, items.map(it => {
      if (!it) return h('span', { html: icon('back').replace('M15 5l-7 7 7 7', 'M9 5l7 7-7 7'), style: { color: 'var(--text2)', display: 'inline-flex' } });
      const t = fakeTile(it[0], it[1]);
      t.revealed = !!it[3];
      return tileEl(t, { cls: it[2] || '', mine: !!it[3] });
    }));
  }
  let i = 0;
  const art = h('div', { class: 'how-art' });
  const title = h('h3', { style: { margin: '16px 0 6px', fontSize: '19px' } });
  const text = h('p', { class: 'para' });
  const dots = h('div', { class: 'dots-nav' });
  let prevBtn, nextBtn;
  const draw = () => {
    art.replaceChildren(...pages[i][2]());
    title.textContent = pages[i][0];
    text.textContent = pages[i][1];
    dots.replaceChildren(...pages.map((_, j) => h('i', { class: j === i ? 'on' : '' })));
    prevBtn.disabled = i === 0;
    nextBtn.querySelector('span').textContent = i === pages.length - 1 ? '시작해 볼게요' : '다음';
  };
  modal({
    title: '게임 방법', width: 580, body: h('div', {}, art, title, text, dots),
    actions: [
      { label: '이전', ref: b => { prevBtn = b; }, onClick: () => { if (i > 0) { i--; draw(); } } },
      { label: '다음', kind: 'primary', ref: b => { nextBtn = b; }, onClick: api => { if (i === pages.length - 1) api.close(); else { i++; draw(); } } },
    ],
  });
  draw();
}

// ================================================================ 키보드

addEventListener('keydown', e => {
  if (e.key !== 'Escape') return;
  if (openMenu) { closeMenu(); return; }
  const top = modalStack[modalStack.length - 1];
  if (top) { if (top.dismissable) top.close(); return; }
  if (S.picker) { S.picker = null; renderBoard(); return; }
  if ($('#room-chat').classList.contains('open')) setDrawer(false);
});

// ================================================================ 시작

initStart();
initLobby();
initRoom();
updatePing();
