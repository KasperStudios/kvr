// KVR Messenger (CONCEPT 1.4 "Zero Discord Needed"): chat with players who wear KVR glasses,
// send a note from /home as a file (it lands in their /home/inbox/), open and pin received files.
// History lives in /data/messenger/history.json (private to this app, inside the glasses quota).
import { Window, Text, Button, Stack, TextInput } from 'kvr';

const RU = kvr.lang.startsWith('ru');
const T = RU ? {
  title: 'Мессенджер', contacts: 'Контакты', online: 'в сети', none: 'Никого нет в сети',
  back: '< Назад', send: 'Отправить', attach: 'Файл', detach: 'Без файла', type: 'Сообщение...',
  me: 'я', sent: 'Отправлено', choose: 'Выбери файл из /home', noFiles: 'В /home нет .txt',
  open: 'Открыть', pin: 'Закрепить', file: 'файл', refresh: 'Обновить',
  err: {
    OFFLINE: 'Игрок не в сети', NO_GLASSES: 'Надень очки KVR', RECIPIENT_GLASSES_OFF: 'У получателя очки выключены',
    RECIPIENT_FULL: 'У получателя заполнен диск', RATE_LIMIT: 'Слишком часто, подожди',
  },
} : {
  title: 'Messenger', contacts: 'Contacts', online: 'online', none: 'Nobody online',
  back: '< Back', send: 'Send', attach: 'File', detach: 'No file', type: 'Message...',
  me: 'me', sent: 'Sent', choose: 'Pick a file from /home', noFiles: 'No .txt in /home',
  open: 'Open', pin: 'Pin', file: 'file', refresh: 'Refresh',
  err: {
    OFFLINE: 'Player is offline', NO_GLASSES: 'Put on KVR glasses', RECIPIENT_GLASSES_OFF: 'Their glasses are off',
    RECIPIENT_FULL: 'Their storage is full', RATE_LIMIT: 'Too fast, wait a moment',
  },
};

const HISTORY = '/data/messenger/history.json';
const KEEP = 40;      // messages kept per contact
const SHOWN = 6;      // messages visible in the chat view
const CONTACTS = 7;   // contact buttons per screen

const win = new Window({ title: T.title, width: 300, height: 290 });
const status = new Text('', { color: '#94a3b8' });
let history = {};     // name -> [{ me, text, file, t }]
let unread = {};      // name -> count
let view = 'contacts';
let peer = null;
let attach = '';

const clip = (s, n) => (s.length > n ? s.slice(0, n - 1) + '\u2026' : s);
const baseName = (p) => p.slice(p.lastIndexOf('/') + 1);
const say = (text, color) => status.set({ text, color: color || '#94a3b8' });

// --- history persistence (debounced: at most one write per 2 s) ----------------------------
let saveTimer = 0;
function persist() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = 0;
    kvr.fs.write(HISTORY, JSON.stringify(history)).catch((e) => kvr.log('history save failed', e.code));
  }, 2000);
}

function push(name, entry) {
  const list = history[name] || (history[name] = []);
  list.push(Object.assign({ t: Date.now() }, entry));
  if (list.length > KEEP) list.splice(0, list.length - KEEP);
  persist();
}

function receive(m) {
  push(m.from, { me: false, text: m.text, file: m.file || '' });
  if (view === 'chat' && peer === m.from) renderChat();
  else {
    unread[m.from] = (unread[m.from] || 0) + 1;
    if (view === 'contacts') renderContacts();
  }
}

// --- contacts -------------------------------------------------------------------------------
function renderContacts() {
  view = 'contacts';
  peer = null;
  win.setTitle(T.title);
  const online = kvr.players.online();
  const known = Object.keys(history);
  const names = Array.from(new Set(online.concat(known))).sort((a, b) => (unread[b] || 0) - (unread[a] || 0) || a.localeCompare(b));
  const buttons = names.slice(0, CONTACTS).map((n) => {
    const u = unread[n] ? '  (' + unread[n] + ')' : '';
    const dot = online.includes(n) ? '\u25cf ' : '\u25cb ';
    return new Button(dot + n + u, () => openChat(n), unread[n] ? { variant: 'success' } : {});
  });
  say(online.length ? online.length + ' ' + T.online : T.none);
  win.replace(
    new Stack({ dir: 'row' }).add(new Text(T.contacts, { weight: 700 }), new Button(T.refresh, renderContacts)),
    ...buttons,
    status,
  );
}

// --- chat -----------------------------------------------------------------------------------
const input = new TextInput({ placeholder: T.type, maxLength: 500, onSubmit: (v) => { if (v.trim()) send(); } });
const attachBtn = new Button(T.attach, () => (attach ? setAttach('') : pickFile()));

function setAttach(path) {
  attach = path;
  attachBtn.setLabel(path ? '[' + clip(baseName(path), 14) + '] x' : T.attach);
}

function openChat(name) {
  view = 'chat';
  peer = name;
  unread[name] = 0;
  setAttach('');
  say('');
  renderChat();
}

function renderChat() {
  win.setTitle(T.title + ' - ' + peer);
  const lines = [];
  for (const m of (history[peer] || []).slice(-SHOWN)) {
    const who = m.me ? T.me : peer;
    if (m.text) lines.push(new Text(who + ': ' + clip(m.text, 90), { color: m.me ? '#a78bfa' : '#f1f5f9' }));
    if (m.file) lines.push(new Button(T.open + ' ' + T.file + ': ' + clip(baseName(m.file), 22), () => openFile(m.file)));
  }
  win.replace(
    new Stack({ dir: 'row' }).add(new Button(T.back, renderContacts), attachBtn),
    ...lines,
    input,
    new Button(T.send, send, { variant: 'success' }),
    status,
  );
}

function send() {
  const text = input.value.trim();
  if (!text && !attach) return;
  const to = peer;
  const file = attach;
  say('...');
  kvr.msg.send(to, text, { attach: file })
    .then(() => {
      push(to, { me: true, text, file });
      input.setValue('');
      setAttach('');
      say(T.sent, '#22c55e');
      if (view === 'chat' && peer === to) renderChat();
    })
    .catch((e) => {
      say(T.err[e.code] || e.code || String(e), '#f43f5e');
      kvr.sound.play('minecraft:block.note_block.bass');
    });
}

// --- attachments ----------------------------------------------------------------------------
function pickFile() {
  say('...');
  kvr.fs.list('/home')
    .then((entries) => {
      const files = entries.filter((e) => !e.dir && e.name.endsWith('.txt')).map((e) => e.name).sort();
      if (!files.length) { say(T.noFiles, '#f59e0b'); return; }
      win.setTitle(T.choose);
      win.replace(
        new Button(T.back, renderChat),
        ...files.slice(0, CONTACTS).map((f) => new Button(f, () => { setAttach('/home/' + f); say(''); renderChat(); })),
        status,
      );
      say(files.length > CONTACTS ? '+' + (files.length - CONTACTS) : '');
    })
    .catch((e) => say(e.code === 'NOT_FOUND' ? T.noFiles : e.code, '#f43f5e'));
}

function openFile(path) {
  kvr.fs.read(path)
    .then((text) => {
      win.setTitle(baseName(path));
      win.replace(
        new Stack({ dir: 'row' }).add(new Button(T.back, renderChat), new Button(T.pin, () => kvr.pin(baseName(path), text))),
        new Text(clip(text, 600)),
      );
    })
    .catch((e) => say(e.code, '#f43f5e'));
}

// --- start ----------------------------------------------------------------------------------
kvr.msg.on(receive);
win.show();
renderContacts();
kvr.fs.read(HISTORY)
  .then((json) => {
    try { history = Object.assign(JSON.parse(json), history); } catch (e) { /* corrupt: start over */ }
  })
  .catch(() => {})
  .then(() => {
    for (const m of kvr.msg.pending()) receive(m); // arrived while the app was closed
    if (view === 'contacts') renderContacts();
  });
