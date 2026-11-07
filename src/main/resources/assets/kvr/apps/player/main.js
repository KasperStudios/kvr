import { Window, Text, Button, Stack } from 'kvr';

const TRACKS = [
  { name: 'Pigstep (Lena Raine)', sound: 'minecraft:music_disc.pigstep' },
  { name: 'Otherside (Lena Raine)', sound: 'minecraft:music_disc.otherside' },
  { name: 'Creator (Lena Raine)', sound: 'minecraft:music_disc.creator' },
  { name: 'Relic (Aaron Cherof)', sound: 'minecraft:music_disc.relic' },
  { name: 'Cat (C418)', sound: 'minecraft:music_disc.cat' },
  { name: 'Stal (C418)', sound: 'minecraft:music_disc.stal' }
];

let currentIndex = Number(kvr.storage.get('track_index', 0));
if (isNaN(currentIndex) || currentIndex < 0 || currentIndex >= TRACKS.length) currentIndex = 0;
let isPlaying = false;

const win = new Window({ title: 'KVR Player', width: 260, height: 260 });
const titleText = new Text('Cyber Audio Player', { weight: 700 });
const nowPlaying = new Text(`Track: ${TRACKS[currentIndex].name}`, { color: '#67e8f9' });
const statusText = new Text(isPlaying ? '● PLAYING' : '○ STOPPED', { color: isPlaying ? '#4ade80' : '#94a3b8' });

function updateStatus() {
  nowPlaying.setText(`Track: ${TRACKS[currentIndex].name}`);
  statusText.setText(isPlaying ? '● PLAYING' : '○ STOPPED');
  statusText.set({ color: isPlaying ? '#4ade80' : '#94a3b8' });
  kvr.storage.set('track_index', currentIndex);
}

function playTrack(idx) {
  currentIndex = idx;
  isPlaying = true;
  updateStatus();
  kvr.sound.play(TRACKS[currentIndex].sound);
  kvr.toast({ title: 'KVR Player', text: `Playing: ${TRACKS[currentIndex].name}` });
}

function stopTrack() {
  isPlaying = false;
  updateStatus();
  // Play UI click to acknowledge stop
  kvr.sound.play('minecraft:ui.button.click');
}

const controls = new Stack({ dir: 'row' })
  .add(new Button('◀ Prev', () => {
    let prev = (currentIndex - 1 + TRACKS.length) % TRACKS.length;
    playTrack(prev);
  }))
  .add(new Button(isPlaying ? 'Stop' : 'Play', () => {
    if (isPlaying) stopTrack();
    else playTrack(currentIndex);
  }))
  .add(new Button('Next ▶', () => {
    let next = (currentIndex + 1) % TRACKS.length;
    playTrack(next);
  }));

const trackList = new Stack({ dir: 'column' });
TRACKS.forEach((t, i) => {
  trackList.add(new Button(`${i + 1}. ${t.name}`, () => playTrack(i)));
});

win.add(titleText, nowPlaying, statusText, controls, trackList).show();
