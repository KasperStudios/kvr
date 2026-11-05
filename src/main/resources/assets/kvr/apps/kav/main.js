// KAV (Kasper AntiVirus / PonoScan 3000) — reference demo from CONCEPT §3, single-file 5a version.
// Exercises timers, live widget updates, style switch, shake, resize and sounds.
import { Window, Text, Button, Slider } from 'kvr';

const calm = { background: 'glass' };
const danger = { background: 'linear(#7f1d1d, #f43f5e)' };
const FILES = ['mods/', 'saves/world/region/r.0.0.mca', 'dirom_brain.bin', 'trusiki_feroxa.zip', 'kvr_apps/notes.vrapp', 'system32/ponos.dll'];

const win = new Window({ title: 'KAV — PonoScan 3000', width: 300, height: 190, style: calm });
const status = new Text('Готов к проверке');
const bar = new Slider({ min: 0, max: 100, value: 0, readOnly: true, glow: true });
const ticker = new Text('', { color: '#94a3b8' });
let scanning = false;

const scanButton = new Button('ПОЛНОЕ СКАНИРОВАНИЕ СИСТЕМЫ', () => {
  if (scanning) return;
  scanning = true;
  let p = 0;
  let i = 0;
  status.setText('Сканирование…');
  const id = setInterval(() => {
    p = Math.min(99, p + Math.random() * 6);
    bar.setValue(p);
    ticker.setText('Проверка: ' + FILES[i++ % FILES.length]);
    if (p >= 99) {
      clearInterval(id);
      status.setText('Анализ… 99%');
      setTimeout(panic, 1800);
    }
  }, 120);
}, { variant: 'success' });

win.add(scanButton, status, bar, ticker);
win.show();

function panic() {
  win.setStyle(danger);
  kvr.window.shake({ intensity: 6, duration: 1500 });
  kvr.sound.play('minecraft:event.raid.horn');
  win.replace(new Text('КРИТИЧЕСКАЯ УГРОЗА! ОБНАРУЖЕНО 4,294,967,295 ПОНОСНЫХ ТРОЯНОВ!', { weight: 700, glitch: true }));
  setTimeout(() => {
    kvr.sound.play('minecraft:entity.generic.explode');
    kvr.window.resize({ width: 420, height: 240 });
    win.replace(
      new Text('☠ СИСТЕМА ЗАХВАЧЕНА', { weight: 700, glitch: true }),
      new Text('Трояны пробрались даже в dirom_brain.bin.'),
      new Button('ПОКАКАТЬ И ПЕРЕЗАГРУЗИТЬ', reboot, { variant: 'danger' }),
    );
  }, 2200);
}

function reboot() {
  scanning = false;
  bar.setValue(0);
  ticker.setText('');
  status.setText('Готов к проверке');
  kvr.window.resize({ width: 300, height: 190 });
  win.setStyle(calm);
  win.replace(scanButton, status, bar, ticker);
}
