import { Window, Text, Button, Stack, TextInput } from 'kvr';

const DEFAULT_CODE = `// KVR Live Code Editor
kvr.toast({ title: 'Live Code', text: 'Coords: ' + JSON.stringify(kvr.player.coords()) });
kvr.window.shake({ intensity: 3, duration: 500 });
kvr.sound.play('minecraft:entity.experience_orb.pickup');
`;

let currentCode = kvr.storage.get('ide_code', DEFAULT_CODE);
let outputLog = 'Ready to execute...';

const win = new Window({ title: 'KVR Code Studio', width: 320, height: 300 });
const title = new Text('KVR In-Game JS IDE', { weight: 700 });
const logText = new Text(outputLog, { color: '#94a3b8' });

const codeInput = new TextInput({
  value: currentCode,
  placeholder: '// Type JS code here...',
  multiline: true,
  rows: 8,
  maxLength: 3000,
  onInput: (val) => {
    currentCode = val;
    kvr.storage.set('ide_code', val);
  }
});

function runCode() {
  try {
    // Execute live within the JS context
    const fn = new Function('kvr', currentCode);
    const result = fn(kvr);
    outputLog = '✔ Success: ' + (result !== undefined ? String(result) : 'executed');
    logText.set({ color: '#4ade80', text: outputLog });
  } catch (err) {
    outputLog = '✖ Error: ' + err.message;
    logText.set({ color: '#f43f5e', text: outputLog });
    kvr.window.shake({ intensity: 2, duration: 400 });
  }
}

function saveFile() {
  kvr.fs.write('/home/script.js', currentCode).then(() => {
    kvr.toast({ title: 'IDE', text: 'Saved to /home/script.js' });
  }).catch((err) => {
    kvr.toast({ title: 'IDE Error', text: err.message });
  });
}

function loadFile() {
  kvr.fs.read('/home/script.js').then((content) => {
    currentCode = content;
    codeInput.setValue(content);
    kvr.storage.set('ide_code', content);
    kvr.toast({ title: 'IDE', text: 'Loaded from /home/script.js' });
  }).catch((err) => {
    kvr.toast({ title: 'IDE', text: 'File not found: ' + err.message });
  });
}

const actionRow = new Stack({ dir: 'row' })
  .add(new Button('▶ Run', runCode, { variant: 'success' }))
  .add(new Button('Save', saveFile))
  .add(new Button('Load', loadFile))
  .add(new Button('Reset', () => {
    currentCode = DEFAULT_CODE;
    codeInput.setValue(DEFAULT_CODE);
    kvr.storage.set('ide_code', DEFAULT_CODE);
  }));

win.add(title, codeInput, actionRow, logText).show();
