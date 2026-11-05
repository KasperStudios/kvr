// Smallest KVR app: a counter, a live clock from the CPU-tier tick, a toast button.
import { Window, Text, Button, Slider } from 'kvr';

const win = new Window({ title: 'Hello KVR', width: 240, height: 150 });
const clock = new Text('0.0 s');
let clicks = 0;
const counter = new Text('Clicks: 0');
const bar = new Slider({ min: 0, max: 10, value: 0, readOnly: true });

win.add(
  clock,
  counter,
  bar,
  new Button('Click me', () => {
    clicks++;
    counter.setText('Clicks: ' + clicks);
    bar.setValue(clicks % 11);
    if (clicks % 5 === 0) kvr.toast({ title: 'Hello KVR', text: clicks + ' clicks!' });
  }, { variant: 'success' }),
);
win.show();

setInterval(() => clock.setText((kvr.now / 1000).toFixed(1) + ' s'), 100);
