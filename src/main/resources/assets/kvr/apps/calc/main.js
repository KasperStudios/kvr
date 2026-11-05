// KVR Calc: a calculator that knows Minecraft. Type into the field (Enter = result) or use the
// keypad. Results are also shown as stacks of 64 and shulker boxes. No eval(): own parser.
import { Window, Text, Button, Stack, TextInput } from 'kvr';

const RU = kvr.lang.startsWith('ru');
const T = RU
  ? { title: 'Калькулятор', hint: 'например 3*64+12', stacks: 'стаков', shulkers: 'шалкеров', err: 'Ошибка' }
  : { title: 'Calc', hint: 'e.g. 3*64+12', stacks: 'stacks', shulkers: 'shulkers', err: 'Error' };

// --- parser: expr = term (('+'|'-') term)*; term = power (('*'|'/'|'%') power)*;
//             power = unary ('^' power)?; unary = '-' unary | atom; atom = number | '(' expr ')'
function evaluate(src) {
  const s = String(src).replace(/\s+/g, '').replace(/,/g, '.').replace(/[x\u00d7]/g, '*').replace(/:/g, '/');
  let i = 0;
  const peek = () => s[i];
  const expect = (c) => { if (s[i] !== c) throw new Error('"' + c + '"'); i++; };
  function expr() {
    let v = term();
    while (peek() === '+' || peek() === '-') v = s[i++] === '+' ? v + term() : v - term();
    return v;
  }
  function term() {
    let v = power();
    for (;;) {
      const c = peek();
      if (c === '*') { i++; v *= power(); }
      else if (c === '/') { i++; v /= power(); }
      else if (c === '%') { i++; v %= power(); }
      else return v;
    }
  }
  function power() {
    const b = unary();
    if (peek() === '^') { i++; return Math.pow(b, power()); }
    return b;
  }
  function unary() {
    if (peek() === '-') { i++; return -unary(); }
    if (peek() === '+') { i++; return unary(); }
    return atom();
  }
  function atom() {
    if (peek() === '(') { i++; const v = expr(); expect(')'); return v; }
    const m = /^\d*\.?\d+(e[+-]?\d+)?/i.exec(s.slice(i));
    if (!m) throw new Error(i < s.length ? '"' + s[i] + '"' : 'end');
    i += m[0].length;
    return parseFloat(m[0]);
  }
  if (!s) return null;
  const v = expr();
  if (i !== s.length) throw new Error('"' + s[i] + '"');
  if (!isFinite(v)) throw new Error('inf');
  return v;
}

const fmt = (v) => (Math.abs(v - Math.round(v)) < 1e-9 ? String(Math.round(v)) : String(+v.toFixed(8)));

const win = new Window({ title: T.title, width: 240, height: 250 });
const result = new Text('0', { weight: 700 });
const stacks = new Text('', { color: '#94a3b8' });
const input = new TextInput({ placeholder: T.hint, maxLength: 120, onInput: () => preview(), onSubmit: () => commitResult() });

function show(v) {
  result.set({ text: '= ' + fmt(v), color: '#f1f5f9' });
  const n = Math.floor(Math.abs(v));
  if (n >= 64 && Math.abs(v - Math.round(v)) < 1e-9) {
    const st = Math.floor(n / 64);
    const rest = n % 64;
    const sh = n / 1728;
    stacks.setText(st + ' ' + T.stacks + (rest ? ' + ' + rest : '') + (n >= 1728 ? '  |  ' + (+sh.toFixed(2)) + ' ' + T.shulkers : ''));
  } else {
    stacks.setText('');
  }
}

function preview() {
  try {
    const v = evaluate(input.value);
    if (v === null) { result.set({ text: '0', color: '#f1f5f9' }); stacks.setText(''); }
    else show(v);
  } catch (e) {
    result.set({ text: '...', color: '#94a3b8' }); // still typing
  }
}

function commitResult() {
  try {
    const v = evaluate(input.value);
    if (v === null) return;
    show(v);
    input.setValue(fmt(v));
  } catch (e) {
    result.set({ text: T.err + ': ' + e.message, color: '#f43f5e' });
    kvr.window.shake({ intensity: 2, duration: 250 });
  }
}

const key = (k) => new Button(k, () => { input.setValue(input.value + k); preview(); });
const row = (...ks) => new Stack({ dir: 'row' }).add(...ks);

win.add(
  input,
  result,
  stacks,
  row(key('7'), key('8'), key('9'), key('/')),
  row(key('4'), key('5'), key('6'), key('*')),
  row(key('1'), key('2'), key('3'), key('-')),
  row(key('0'), key('.'), key('+'), new Button('=', commitResult, { variant: 'success' })),
  row(key('('), key(')'), key('64'),
    new Button('C', () => { input.setValue(''); preview(); }, { variant: 'danger' }),
    new Button('<', () => { input.setValue(input.value.slice(0, -1)); preview(); })),
);
win.show();
