// KAV (Kasper AntiVirus / PonoScan 3000) — reference demo from CONCEPT §3, single-file 5a version.
// Exercises timers, live widget updates, style switch, shake, resize and sounds.
import { Window, Text, Button, Slider } from 'kvr';

const calm = { background: 'glass' };
const danger = { background: 'linear(#7f1d1d, #f43f5e)' };
const FILES = ['mods/', 'saves/world/region/r.0.0.mca', 'dirom_brain.bin', 'trusiki_feroxa.zip', 'kvr_apps/notes.vrapp', 'system32/ponos.dll'];

const win = new Window({ title: 'KAV \u2014 PonoScan 3000', width: 300, height: 190, style: calm });
const status = new Text('\u0413\u043e\u0442\u043e\u0432 \u043a \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0435');
const bar = new Slider({ min: 0, max: 100, value: 0, readOnly: true, glow: true });
const ticker = new Text('', { color: '#94a3b8' });
let scanning = false;

const scanButton = new Button('\u041f\u041e\u041b\u041d\u041e\u0415 \u0421\u041a\u0410\u041d\u0418\u0420\u041e\u0412\u0410\u041d\u0418\u0415 \u0421\u0418\u0421\u0422\u0415\u041c\u042b', () => {
  if (scanning) return;
  scanning = true;
  let p = 0;
  let i = 0;
  status.setText('\u0421\u043a\u0430\u043d\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435\u2026');
  const id = setInterval(() => {
    p = Math.min(99, p + Math.random() * 6);
    bar.setValue(p);
    ticker.setText('\u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430: ' + FILES[i++ % FILES.length]);
    if (p >= 99) {
      clearInterval(id);
      status.setText('\u0410\u043d\u0430\u043b\u0438\u0437\u2026 99%');
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
  win.replace(new Text('\u041a\u0420\u0418\u0422\u0418\u0427\u0415\u0421\u041a\u0410\u042f \u0423\u0413\u0420\u041e\u0417\u0410! \u041e\u0411\u041d\u0410\u0420\u0423\u0416\u0415\u041d\u041e 4,294,967,295 \u041f\u041e\u041d\u041e\u0421\u041d\u042b\u0425 \u0422\u0420\u041e\u042f\u041d\u041e\u0412!', { weight: 700, glitch: true }));
  setTimeout(() => {
    kvr.sound.play('minecraft:entity.generic.explode');
    kvr.window.resize({ width: 420, height: 240 });
    win.replace(
      new Text('\u2620 \u0421\u0418\u0421\u0422\u0415\u041c\u0410 \u0417\u0410\u0425\u0412\u0410\u0427\u0415\u041d\u0410', { weight: 700, glitch: true }),
      new Text('\u0422\u0440\u043e\u044f\u043d\u044b \u043f\u0440\u043e\u0431\u0440\u0430\u043b\u0438\u0441\u044c \u0434\u0430\u0436\u0435 \u0432 dirom_brain.bin.'),
      new Button('\u041f\u041e\u041a\u0410\u041a\u0410\u0422\u042c \u0418 \u041f\u0415\u0420\u0415\u0417\u0410\u0413\u0420\u0423\u0417\u0418\u0422\u042c', reboot, { variant: 'danger' }),
    );
  }, 2200);
}

function reboot() {
  scanning = false;
  bar.setValue(0);
  ticker.setText('');
  status.setText('\u0413\u043e\u0442\u043e\u0432 \u043a \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0435');
  kvr.window.resize({ width: 300, height: 190 });
  win.setStyle(calm);
  win.replace(scanButton, status, bar, ticker);
}
