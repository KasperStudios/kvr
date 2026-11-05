// KVR OS prelude — evaluated before every app (QuickJS, ES2020).
// Host bridge: __kvr_host(op, payload) -> result. The engine adapter passes payload as JSON and
// JSON.parse()s the host's JSON return value, so getters below receive plain JS values.
// Entry points called by Java: __kvr_tick(ms), __kvr_event(event).
(function (global) {
  'use strict';

  const host = (op, payload) => __kvr_host(op, payload === undefined ? null : payload);
  let now = 0; // ms since app start

  // --- timers (driven by the CPU-tier tick, SPEC: kvr.timers) -----------------------------
  let timerSeq = 1;
  const timers = new Map();
  global.setTimeout = (fn, ms = 0, ...args) => {
    const id = timerSeq++;
    timers.set(id, { fn, at: now + Math.max(0, Number(ms) || 0), every: 0, args });
    return id;
  };
  global.setInterval = (fn, ms = 0, ...args) => {
    const id = timerSeq++;
    const every = Math.max(1, Number(ms) || 1);
    timers.set(id, { fn, at: now + every, every, args });
    return id;
  };
  global.clearTimeout = global.clearInterval = (id) => { timers.delete(id); };
  let frames = [];
  global.requestAnimationFrame = (fn) => { frames.push(fn); return frames.length; };

  // --- local event bus (kvr.on / kvr.emit; the Rednet bridge joins in 5g) -----------------
  const listeners = new Map();
  const on = (type, fn) => {
    if (!listeners.has(type)) listeners.set(type, []);
    listeners.get(type).push(fn);
  };
  const off = (type, fn) => {
    const l = listeners.get(type);
    if (l) listeners.set(type, l.filter((f) => f !== fn));
  };
  const emitLocal = (type, data) => (listeners.get(type) || []).slice().forEach((f) => f(data));

  // --- retained widget tree (SPEC 5.2 example API) ----------------------------------------
  let widgetSeq = 1;
  let live = new Map();
  let root = null;
  let dirty = false;
  const markDirty = () => { dirty = true; };

  class Widget {
    constructor(type, props) {
      this.id = widgetSeq++;
      this.type = type;
      this.props = Object.assign({}, props);
    }
    set(p) { Object.assign(this.props, p); markDirty(); return this; }
    toJSON() { live.set(this.id, this); return Object.assign({ id: this.id, type: this.type }, this.props); }
  }
  class Text extends Widget {
    constructor(text = '', props) { super('text', Object.assign({ text: String(text) }, props)); }
    setText(t) { return this.set({ text: String(t) }); }
  }
  class Button extends Widget {
    constructor(label = '', onClick = null, props) {
      super('button', Object.assign({ label: String(label) }, props));
      this.onClick = onClick;
    }
    setLabel(l) { return this.set({ label: String(l) }); }
  }
  class Slider extends Widget {
    constructor(props = {}) {
      const p = Object.assign({ min: 0, max: 100, value: 0 }, props);
      const onChange = p.onChange || null;
      delete p.onChange;
      super('slider', p);
      this.onChange = onChange;
    }
    setValue(v) { return this.set({ value: Number(v) }); }
    get value() { return this.props.value; }
  }
  // Text field: click focuses it (keyboard captured until Enter/Esc; multiline: Ctrl+Enter).
  // props: value, placeholder, multiline, rows, maxLength, readOnly, onInput(v), onSubmit(v)
  class TextInput extends Widget {
    constructor(props = {}) {
      const p = Object.assign({ value: '', placeholder: '', multiline: false }, props);
      const onInput = p.onInput || null;
      const onSubmit = p.onSubmit || null;
      delete p.onInput;
      delete p.onSubmit;
      p.value = String(p.value);
      super('input', p);
      this.onInput = onInput;
      this.onSubmit = onSubmit;
    }
    setValue(v) { return this.set({ value: String(v) }); }
    get value() { return this.props.value; }
  }
  class Stack extends Widget {
    constructor(props) { super('stack', Object.assign({ dir: 'column' }, props)); this.children = []; }
    add(...ws) { this.children.push(...ws); markDirty(); return this; }
    replace(...ws) { this.children = ws; markDirty(); return this; }
    clear() { this.children = []; markDirty(); return this; }
    toJSON() { return Object.assign(super.toJSON(), { children: this.children.map((c) => c.toJSON()) }); }
  }
  class Window extends Stack {
    constructor(opts = {}) {
      super({ dir: 'column' });
      this.opts = Object.assign({ title: 'App', width: 300, height: 200, style: null }, opts);
    }
    setTitle(t) { this.opts.title = String(t); markDirty(); return this; }
    setStyle(style) { this.opts.style = style; markDirty(); return this; }
    show() { root = this; markDirty(); return this; }
    toJSON() {
      live.set(this.id, this);
      return Object.assign({ id: this.id, type: 'window' }, this.opts, { children: this.children.map((c) => c.toJSON()) });
    }
  }

  const commit = () => {
    if (!dirty || !root) return;
    live = new Map();
    host('ui.commit', root.toJSON());
    dirty = false;
  };

  const kvr = {
    Window, Text, Button, Slider, Stack, TextInput,
    fs: null,
    toast: (o) => host('toast', o || {}),
    log: (...a) => host('log', a.map(String).join(' ')),
    on, off,
    emit: (type, data) => emitLocal(type, data),
    window: {
      shake: (o) => host('window.shake', o || {}),
      resize: (o) => host('window.resize', o || {}),
      setTitle: (t) => root && root.setTitle(t),
      // same switches as the gear menu (the user can always change them back there)
      lock: (on = true) => host('window.option', { key: 'lock', value: String(!!on) }),
      setPublic: (on = true) => host('window.option', { key: 'public', value: String(!!on) }),
      anchor: (mode) => host('window.option', { key: 'anchor', value: mode === 'body' ? 'body' : 'world' }),
    },
    sound: { play: (id) => host('sound.play', String(id)) },
    // note window (coords etc.): opens in front of the player, Body anchor; gear menu + × like any window
    pin: (title, text) => host('window.pin', { title: String(title), text: String(text === undefined ? '' : text) }),
    player: Object.freeze({
      coords: () => host('player.coords'), // {x, y, z, dimension}
      name: () => host('player.name'),
    }),
    players: Object.freeze({
      online: () => host('players.online') || [], // other players on the server, sorted
    }),
    msg: null,
    get lang() { return host('client.lang') || 'en_us'; }, // e.g. 'ru_ru'
    get now() { return now; },
  };
  Object.freeze(kvr.window);

  // --- kvr.fs: server VFS bound to the glasses (async; Promise + optional node-style callback) ---
  // Paths: /home (shared user files), /tmp, /data/<this app>, /apps (read-only). Errors: err.code.
  let fsSeq = 1;
  const fsPending = new Map();
  const fsCall = (op, path, text, cb) => {
    const req = fsSeq++;
    const p = new Promise((resolve, reject) => fsPending.set(req, { op, resolve, reject, cb }));
    host('fs', { req, op, path: String(path), text: text === undefined ? null : String(text) });
    if (typeof cb === 'function') p.catch(() => {}); // callback style: errors go to cb
    return p;
  };
  kvr.fs = Object.freeze({
    read: (path, cb) => fsCall('READ', path, undefined, cb),
    write: (path, text, cb) => fsCall('WRITE', path, text, cb),
    list: (path, cb) => fsCall('LIST', path, undefined, cb),
    remove: (path, cb) => fsCall('DELETE', path, undefined, cb),
    usage: (cb) => fsCall('USAGE', '/', undefined, cb),
  });
  const fsReply = (e) => {
    const p = fsPending.get(e.req);
    if (!p) return;
    fsPending.delete(e.req);
    let data = e.data;
    if (e.ok && (p.op === 'LIST' || p.op === 'USAGE')) data = JSON.parse(data);
    const err = e.ok ? null : Object.assign(new Error(e.data || e.error), { code: e.error });
    if (typeof p.cb === 'function') p.cb(err, e.ok ? data : undefined);
    if (err) p.reject(err); else p.resolve(data);
  };
  global.kvr = kvr;

  // --- kvr.msg: KVR Messenger (server relay, glasses required on both ends) ---------------------
  // send(to, text, {attach:'/home/x.txt'}) -> Promise; errors: OFFLINE, NO_GLASSES,
  // RECIPIENT_GLASSES_OFF, RECIPIENT_FULL, RATE_LIMIT, or a VFS code for the attachment (NOT_FOUND,
  // DENIED, ...). Incoming: kvr.msg.on(m => m.from/text/file).
  let msgSeq = 1;
  const msgPending = new Map();
  kvr.msg = Object.freeze({
    send: (to, text, opts = {}) => {
      const req = msgSeq++;
      const p = new Promise((resolve, reject) => msgPending.set(req, { resolve, reject }));
      host('msg.send', { req, to: String(to), text: String(text === undefined ? '' : text), attach: opts.attach ? String(opts.attach) : '' });
      return p;
    },
    pending: () => host('msg.pending') || [], // messages that arrived while the app was closed
    on: (fn) => on('msg', fn),
  });
  const msgAck = (e) => {
    const p = msgPending.get(e.req);
    if (!p) return;
    msgPending.delete(e.req);
    if (e.ok) p.resolve(true);
    else p.reject(Object.assign(new Error(e.error), { code: e.error }));
  };
  global.console = { log: kvr.log, warn: kvr.log, error: kvr.log };

  global.__kvr_tick = (ms) => {
    now = ms;
    for (const [id, t] of Array.from(timers)) {
      if (t.at > now) continue;
      if (t.every) t.at = now + t.every;
      else timers.delete(id);
      t.fn(...t.args);
    }
    const due = frames;
    frames = [];
    due.forEach((f) => f(now));
    commit();
  };

  global.__kvr_event = (e) => {
    if (e.type === 'fs') { fsReply(e); commit(); return; }
    if (e.type === 'msg_ack') { msgAck(e); commit(); return; }
    if (e.type === 'msg') { emitLocal('msg', e); commit(); return; }
    // Alt-mode keyboard (config.json "keyboard": true): kvr.on('key', e => e.key/scancode/action/mods),
    // kvr.on('char', e => e.ch), kvr.on('keyboard', e => e.focused)
    if (e.type === 'key' || e.type === 'char' || e.type === 'keyboard') { emitLocal(e.type, e); commit(); return; }
    const w = live.get(e.id);
    if (e.type === 'click' && w && typeof w.onClick === 'function') w.onClick(e);
    if (e.type === 'change' && w) {
      w.props.value = e.value;
      markDirty();
      if (typeof w.onChange === 'function') w.onChange(e.value);
    }
    if ((e.type === 'input' || e.type === 'submit') && w) {
      w.props.value = String(e.value);
      markDirty();
      if (e.type === 'input' && typeof w.onInput === 'function') w.onInput(w.props.value);
      if (e.type === 'submit' && typeof w.onSubmit === 'function') w.onSubmit(w.props.value);
    }
    emitLocal('ui:' + e.type, e);
    commit();
  };
})(globalThis);
