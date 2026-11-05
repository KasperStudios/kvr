// KVR Notes (CONCEPT 1.3 / 4): text notes in /home/*.txt, shared with every app
// (Messenger can send them). "Coords" inserts the current position, "Pin" floats the note
// at the right shoulder.
import { Window, Text, Button, Stack, TextInput } from 'kvr';

const RU = kvr.lang.startsWith('ru');
const T = RU ? {
  title: 'Заметки', empty: 'Пока пусто. Создай первую заметку.', fresh: '+ Новая', back: '< Назад',
  save: 'Сохранить', coords: 'Координаты', pin: 'Закрепить', del: 'Удалить', sure: 'Точно удалить?',
  name: 'имя файла (латиница)', body: 'Текст заметки...', saved: 'Сохранено', deleted: 'Удалено',
  loading: 'Загрузка...', page: 'стр.', error: 'Ошибка', full: 'Диск очков заполнен',
} : {
  title: 'Notes', empty: 'Nothing here yet. Create your first note.', fresh: '+ New', back: '< Back',
  save: 'Save', coords: 'Coords', pin: 'Pin', del: 'Delete', sure: 'Really delete?',
  name: 'file name (latin)', body: 'Note text...', saved: 'Saved', deleted: 'Deleted',
  loading: 'Loading...', page: 'page', error: 'Error', full: 'Glasses storage is full',
};

const DIR = '/home';
const PER_PAGE = 6;

const win = new Window({ title: T.title, width: 300, height: 270 });
const status = new Text('', { color: '#94a3b8' });
let notes = [];
let page = 0;
let current = null; // file name being edited, or null for a new note
let confirmDelete = false;

// --- file names: VFS segments are [A-Za-z0-9._-], so Cyrillic is transliterated --------------
const TR = { а:'a',б:'b',в:'v',г:'g',д:'d',е:'e',ё:'e',ж:'zh',з:'z',и:'i',й:'y',к:'k',л:'l',м:'m',н:'n',о:'o',п:'p',р:'r',с:'s',т:'t',у:'u',ф:'f',х:'h',ц:'c',ч:'ch',ш:'sh',щ:'sch',ъ:'',ы:'y',ь:'',э:'e',ю:'yu',я:'ya' };
const fileName = (raw) => {
  let s = String(raw).trim().toLowerCase().split('').map((c) => (TR[c] !== undefined ? TR[c] : c)).join('');
  s = s.replace(/\.txt$/, '').replace(/[^a-z0-9._-]+/g, '_').replace(/^[._]+|_+$/g, '').slice(0, 36);
  return (s || 'note-' + Date.now().toString(36)) + '.txt';
};
const label = (name) => name.replace(/\.txt$/, '');

const fail = (e) => {
  status.set({ text: (e && e.code && e.code.startsWith('QUOTA') ? T.full : T.error + ': ' + (e && (e.code || e.message))), color: '#f43f5e' });
};
const ok = (text) => status.set({ text, color: '#22c55e' });

// --- list view ------------------------------------------------------------------------------
function showList() {
  current = null;
  confirmDelete = false;
  status.set({ text: T.loading, color: '#94a3b8' });
  renderList();
  kvr.fs.list(DIR)
    .then((entries) => {
      notes = entries.filter((e) => !e.dir && e.name.endsWith('.txt')).map((e) => e.name).sort();
      page = Math.min(page, Math.max(0, Math.ceil(notes.length / PER_PAGE) - 1));
      status.set({ text: notes.length ? notes.length + ' .txt' : T.empty, color: '#94a3b8' });
      renderList();
    })
    .catch((e) => {
      notes = [];
      if (e.code === 'NOT_FOUND') status.set({ text: T.empty, color: '#94a3b8' });
      else fail(e);
      renderList();
    });
}

function renderList() {
  win.setTitle(T.title);
  const pages = Math.max(1, Math.ceil(notes.length / PER_PAGE));
  const items = notes.slice(page * PER_PAGE, page * PER_PAGE + PER_PAGE).map((n) => new Button(label(n), () => openNote(n)));
  win.replace(
    new Stack({ dir: 'row' }).add(
      new Button(T.fresh, () => edit(null, ''), { variant: 'success' }),
      new Button('<', () => { if (page > 0) { page--; renderList(); } }),
      new Text(T.page + ' ' + (page + 1) + '/' + pages, { color: '#94a3b8' }),
      new Button('>', () => { if (page < pages - 1) { page++; renderList(); } }),
    ),
    ...items,
    status,
  );
}

function openNote(name) {
  status.set({ text: T.loading, color: '#94a3b8' });
  kvr.fs.read(DIR + '/' + name).then((text) => edit(name, text)).catch(fail);
}

// --- editor ---------------------------------------------------------------------------------
const nameInput = new TextInput({ placeholder: T.name, maxLength: 36 });
const bodyInput = new TextInput({ placeholder: T.body, multiline: true, rows: 8, maxLength: 4000 });
const deleteBtn = new Button(T.del, () => removeNote(), { variant: 'danger' });

function edit(name, text) {
  current = name;
  confirmDelete = false;
  nameInput.setValue(name ? label(name) : '');
  bodyInput.setValue(text);
  deleteBtn.setLabel(T.del);
  status.set({ text: name ? DIR + '/' + name : '', color: '#94a3b8' });
  win.setTitle(T.title + (name ? ' - ' + label(name) : ''));
  win.replace(
    nameInput,
    bodyInput,
    new Stack({ dir: 'row' }).add(
      new Button(T.save, save, { variant: 'success' }),
      new Button(T.coords, insertCoords),
      new Button(T.pin, () => kvr.pin(nameInput.value || T.title, bodyInput.value)),
    ),
    new Stack({ dir: 'row' }).add(new Button(T.back, showList), deleteBtn),
    status,
  );
}

function save() {
  const name = fileName(nameInput.value || bodyInput.value.split('\n')[0]);
  const old = current;
  kvr.fs.write(DIR + '/' + name, bodyInput.value)
    .then(() => {
      current = name;
      nameInput.setValue(label(name));
      win.setTitle(T.title + ' - ' + label(name));
      ok(T.saved + ': ' + DIR + '/' + name);
      // renamed: the old file goes away so the list has no stale twin
      if (old && old !== name) return kvr.fs.remove(DIR + '/' + old).catch(() => {});
    })
    .catch(fail);
}

function insertCoords() {
  const c = kvr.player.coords();
  const dim = String(c.dimension || '').replace(/^minecraft:/, '');
  const line = 'X ' + c.x + '  Y ' + c.y + '  Z ' + c.z + (dim ? '  (' + dim + ')' : '');
  const v = bodyInput.value;
  bodyInput.setValue(v + (v && !v.endsWith('\n') ? '\n' : '') + line + '\n');
}

function removeNote() {
  if (!current) { showList(); return; }
  if (!confirmDelete) {
    confirmDelete = true;
    deleteBtn.setLabel(T.sure);
    return;
  }
  kvr.fs.remove(DIR + '/' + current)
    .then(() => { kvr.toast({ title: T.title, text: T.deleted + ': ' + current }); showList(); } )
    .catch(fail);
}

win.show();
showList();
