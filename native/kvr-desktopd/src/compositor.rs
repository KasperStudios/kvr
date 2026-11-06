//! The hidden compositor: a Wayland server with no outputs on any screen. Apps launched by the mod
//! connect to it (WAYLAND_DISPLAY=kvr-desktop-<pid>), draw into wl_shm buffers, and each xdg_toplevel
//! becomes a KVR stream. Nothing is ever shown on the real desktop: we have no backend at all.
//!
//! Scope after D2 (honest list):
//! * wl_shm only (no linux-dmabuf, D4): GTK/Qt/Firefox/foot fall back to software rendering.
//! * toplevel + subsurfaces + popups (menus, tooltips, combo boxes, context menus) composited into
//!   one frame per window (surfaces.rs). Popups are kept inside the window (xdg_positioner
//!   constraint = the window geometry), so the frame size never jumps when a menu opens.
//! * popup grabs: keyboard goes to the open menu; a click outside the menus closes them (and is eaten,
//!   like on a desktop).
//! * the app's cursor (wl_pointer.set_cursor surface or a named shape) goes to the mod (CURSOR).
//! * clipboard text both ways (clipboard.rs).
//! * pointer, buttons, wheel, keyboard (xkb us+ru, Alt+Shift switches like a desktop).
//!
//! Smithay API written against 0.6 / smallvil from memory: lines marked VERIFY are the likely spots
//! for compile errors. Send the exact `cargo build` output.

use crate::clipboard;
use crate::ipc::{token_eq, Conn};
use crate::launch::Launches;
use crate::protocol::{Msg, Rect, CAP_CLIPBOARD, CAP_CURSOR, CAP_HOSTED, CAP_POPUPS, CURSOR_HIDDEN, CURSOR_IMAGE, CURSOR_NAMED, FORMAT_BGRA, MAX_RECTS, VERSION};
use crate::shm::{self, ShmRing, SLOTS};
use crate::surfaces::{self, IRect, Layer};
use anyhow::Context;
use std::os::fd::OwnedFd;
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tracing::{info, warn};

use smithay::backend::input::{Axis, AxisSource, ButtonState, KeyState};
use smithay::input::keyboard::{FilterResult, XkbConfig};
use smithay::input::pointer::{AxisFrame, ButtonEvent, CursorImageStatus, CursorImageSurfaceData, MotionEvent};
use smithay::input::{Seat, SeatHandler, SeatState};
use smithay::output::{Mode as OutputMode, Output, PhysicalProperties, Scale, Subpixel};
use smithay::reexports::calloop::generic::Generic;
use smithay::reexports::calloop::{EventLoop, Interest, Mode, PostAction};
use smithay::reexports::wayland_protocols::xdg::decoration::zv1::server::zxdg_toplevel_decoration_v1::Mode as DecorationMode;
use smithay::reexports::wayland_protocols::xdg::shell::server::xdg_toplevel;
use smithay::reexports::wayland_server::backend::{ClientData, ClientId, DisconnectReason};
use smithay::reexports::wayland_server::protocol::{wl_buffer::WlBuffer, wl_callback::WlCallback, wl_seat::WlSeat, wl_surface::WlSurface};
use smithay::reexports::wayland_server::{Client, Display, DisplayHandle, Resource};
use smithay::utils::{Logical, Rectangle, Serial, Transform, SERIAL_COUNTER};
use smithay::wayland::buffer::BufferHandler;
use smithay::wayland::compositor::{with_states, CompositorClientState, CompositorHandler, CompositorState};
use smithay::wayland::output::{OutputHandler, OutputManagerState};
use smithay::wayland::selection::data_device::{
    request_data_device_client_selection, set_data_device_focus, set_data_device_selection, ClientDndGrabHandler, DataDeviceHandler, DataDeviceState,
    ServerDndGrabHandler,
};
use smithay::wayland::selection::{SelectionHandler, SelectionSource, SelectionTarget};
use smithay::wayland::shell::xdg::decoration::{XdgDecorationHandler, XdgDecorationState};
use smithay::wayland::shell::xdg::{PopupSurface, PositionerState, ToplevelSurface, XdgPopupSurfaceData, XdgShellHandler, XdgShellState, XdgToplevelSurfaceData};
use smithay::wayland::shm::{ShmHandler, ShmState};
use smithay::wayland::socket::ListeningSocketSource;
use smithay::{delegate_compositor, delegate_data_device, delegate_output, delegate_seat, delegate_shm, delegate_xdg_decoration, delegate_xdg_shell};

static STOP: AtomicBool = AtomicBool::new(false);

extern "C" fn on_term(_: libc::c_int) {
    STOP.store(true, Ordering::Relaxed);
}

pub struct Stream {
    id: u32,
    toplevel: ToplevelSurface,
    ring: Option<ShmRing>,
    next_generation: u16,
    callbacks: Vec<WlCallback>,
    fps: u8,
    visible: bool,
    last_publish: Instant,
    seq: u32,
    title: String,
    app_id: String,
    /// something changed since the last published frame
    dirty: bool,
    /// changed rectangles in frame coordinates since the last published frame; None = everything
    damage: Option<Vec<Rect>>,
    last_size: (u16, u16),
}

impl Stream {
    fn add_damage(&mut self, rects: impl IntoIterator<Item = Rect>) {
        if let Some(d) = self.damage.as_mut() {
            d.extend(rects);
            if d.len() > MAX_RECTS {
                self.damage = None;
            }
        }
    }

    fn damage_all(&mut self) {
        self.dirty = true;
        self.damage = None;
    }
}

/// An xdg_popup (menu, tooltip, combo box list). `rel` = its window geometry relative to the parent's
/// window geometry, exactly as configured (already constrained into the window).
struct Popup {
    surface: PopupSurface,
    parent: Option<WlSurface>,
    stream: u32,
    rel: IRect,
    grabbed: bool,
    dismissed: bool,
}

#[derive(Default)]
pub struct ClientState {
    compositor_state: CompositorClientState,
}

impl ClientData for ClientState {
    fn initialized(&self, _client_id: ClientId) {}
    fn disconnected(&self, _client_id: ClientId, _reason: DisconnectReason) {}
}

pub struct State {
    dh: DisplayHandle,
    compositor_state: CompositorState,
    xdg_shell_state: XdgShellState,
    shm_state: ShmState,
    seat_state: SeatState<State>,
    data_device_state: DataDeviceState,
    seat: Seat<State>,
    _decoration_state: XdgDecorationState,
    _output_state: OutputManagerState,
    _output: Output,
    wayland_display: String,
    shm_dir: PathBuf,
    token: String,
    conn: Option<Conn>,
    ever_connected: bool,
    started: Instant,
    streams: Vec<Stream>,
    popups: Vec<Popup>,
    launches: Launches,
    next_stream: u32,
    running: bool,
    /// stream under the mod's pointer (gets the CURSOR messages) and its last position (frame px)
    pointer_stream: u32,
    pointer_pos: (f64, f64),
    /// stream with the mod's keyboard capture (0 = none)
    kbd_stream: u32,
    /// presses that only closed a menu: their releases are eaten too
    swallowed: Vec<u32>,
    cursor_surface: Option<WlSurface>,
    clip_request: Option<String>,
    clip_reader: Option<clipboard::Reader>,
    clip_writers: Vec<clipboard::Writer>,
}

pub fn run(socket: PathBuf, shm_dir: PathBuf, token: String) -> anyhow::Result<()> {
    // SAFETY: the handler only stores an atomic flag (async-signal-safe)
    unsafe {
        libc::signal(libc::SIGTERM, on_term as libc::sighandler_t);
        libc::signal(libc::SIGINT, on_term as libc::sighandler_t);
    }
    std::fs::create_dir_all(&shm_dir).context("shm dir")?;
    shm::remove_stale(&shm_dir);

    let mut event_loop: EventLoop<State> = EventLoop::try_new().context("event loop")?;
    let display: Display<State> = Display::new().context("wayland display")?;
    let dh = display.handle();

    let compositor_state = CompositorState::new::<State>(&dh);
    let xdg_shell_state = XdgShellState::new::<State>(&dh);
    let shm_state = ShmState::new::<State>(&dh, vec![]);
    let decoration_state = XdgDecorationState::new::<State>(&dh);
    let output_state = OutputManagerState::new_with_xdg_output::<State>(&dh);
    let data_device_state = DataDeviceState::new::<State>(&dh);
    let mut seat_state = SeatState::new();
    let mut seat = seat_state.new_wl_seat(&dh, "kvr");
    // us+ru, Alt+Shift switches the layout inside the app, like on a desktop
    seat.add_keyboard(XkbConfig { layout: "us,ru", options: Some("grp:alt_shift_toggle".to_string()), ..XkbConfig::default() }, 300, 30)
        .map_err(|e| anyhow::anyhow!("xkb keymap: {e:?}"))?;
    seat.add_pointer();
    let output = virtual_output(&dh);

    // the apps' Wayland socket ($XDG_RUNTIME_DIR/kvr-desktop-<pid>)
    let listening = ListeningSocketSource::with_name(&format!("kvr-desktop-{}", std::process::id())).context("wayland socket")?;
    let wayland_display = listening.socket_name().to_string_lossy().into_owned();
    event_loop
        .handle()
        .insert_source(listening, |client, _, state: &mut State| {
            if let Err(e) = state.dh.insert_client(client, Arc::new(ClientState::default())) {
                warn!("wayland client rejected: {e}");
            }
        })
        .map_err(|e| anyhow::anyhow!("socket source: {e}"))?;
    event_loop
        .handle()
        .insert_source(Generic::new(display, Interest::READ, Mode::Level), |_, display, state: &mut State| {
            // SAFETY: the display is not dropped while the loop runs (smallvil pattern) — VERIFY get_mut on NoIoDrop
            unsafe {
                if let Err(e) = display.get_mut().dispatch_clients(state) {
                    warn!("dispatch: {e}");
                }
            }
            Ok(PostAction::Continue)
        })
        .map_err(|e| anyhow::anyhow!("display source: {e}"))?;

    // the mod's control socket
    let _ = std::fs::remove_file(&socket);
    let listener = UnixListener::bind(&socket).with_context(|| format!("bind {}", socket.display()))?;
    std::fs::set_permissions(&socket, std::fs::Permissions::from_mode(0o600))?;
    listener.set_nonblocking(true)?;
    event_loop
        .handle()
        .insert_source(Generic::new(listener, Interest::READ, Mode::Level), |_, l, state: &mut State| {
            loop {
                match l.accept() {
                    Ok((s, _)) => state.accept_mod(s),
                    Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => break,
                    Err(e) => {
                        warn!("accept: {e}");
                        break;
                    }
                }
            }
            Ok(PostAction::Continue)
        })
        .map_err(|e| anyhow::anyhow!("control source: {e}"))?;

    let mut state = State {
        dh,
        compositor_state,
        xdg_shell_state,
        shm_state,
        seat_state,
        data_device_state,
        seat,
        _decoration_state: decoration_state,
        _output_state: output_state,
        _output: output,
        wayland_display,
        shm_dir,
        token,
        conn: None,
        ever_connected: false,
        started: Instant::now(),
        streams: Vec::new(),
        popups: Vec::new(),
        launches: Launches::new(),
        next_stream: 1,
        running: true,
        pointer_stream: 0,
        pointer_pos: (0.0, 0.0),
        kbd_stream: 0,
        swallowed: Vec::new(),
        cursor_surface: None,
        clip_request: None,
        clip_reader: None,
        clip_writers: Vec::new(),
    };
    info!(wayland_display = %state.wayland_display, control = %socket.display(), "kvr-desktopd ready");

    while state.running && !STOP.load(Ordering::Relaxed) {
        event_loop.dispatch(Some(Duration::from_millis(4)), &mut state).context("event loop")?;
        state.tick();
        let _ = state.dh.flush_clients();
    }
    state.shutdown();
    let _ = state.dh.flush_clients();
    let _ = std::fs::remove_file(&socket);
    info!("kvr-desktopd stopped");
    Ok(())
}

fn virtual_output(dh: &DisplayHandle) -> Output {
    // one invisible 1080p "monitor": some toolkits refuse to map windows without any wl_output
    // VERIFY: newer smithay may add `serial_number` to PhysicalProperties
    let output = Output::new(
        "KVR-1".to_string(),
        PhysicalProperties { size: (0, 0).into(), subpixel: Subpixel::Unknown, make: "KasperStudios".into(), model: "KVR Spatial".into() },
    );
    let _global = output.create_global::<State>(dh);
    let mode = OutputMode { size: (1920, 1080).into(), refresh: 60_000 };
    output.change_current_state(Some(mode), Some(Transform::Normal), Some(Scale::Integer(1)), Some((0, 0).into()));
    output.set_preferred(mode);
    output
}

impl State {
    fn now_ms(&self) -> u32 {
        self.started.elapsed().as_millis() as u32
    }

    fn send(&mut self, m: Msg) {
        if let Some(c) = self.conn.as_mut() {
            if c.authed || matches!(m, Msg::HelloOk { .. }) {
                if let Err(e) = c.queue(&m) {
                    warn!("mod connection: {e}");
                    self.mod_gone();
                }
            }
        }
    }

    // --- mod connection ------------------------------------------------------------------------

    fn accept_mod(&mut self, s: UnixStream) {
        if self.conn.is_some() {
            warn!("second control connection refused");
            return;
        }
        match Conn::new(s) {
            Ok(c) => {
                self.conn = Some(c);
                self.ever_connected = true;
            }
            Err(e) => warn!("control connection: {e}"),
        }
    }

    /// The mod launched us for this game session: when it goes, we go (and take the apps along).
    fn mod_gone(&mut self) {
        self.conn = None;
        self.running = false;
    }

    fn tick(&mut self) {
        if self.conn.is_none() && !self.ever_connected && self.started.elapsed() > Duration::from_secs(15) {
            warn!("no mod connected within 15 s, exiting");
            self.running = false;
            return;
        }
        self.poll_mod();
        self.reap_popups();
        self.poll_clipboard();
        self.publish_frames();
        self.launches.reap();
        if let Some(c) = self.conn.as_mut() {
            if let Err(e) = c.flush() {
                warn!("mod write: {e}");
                self.mod_gone();
            }
        }
    }

    fn poll_mod(&mut self) {
        let Some(conn) = self.conn.as_mut() else { return };
        match conn.read_available() {
            Ok(true) => {}
            Ok(false) => {
                info!("mod disconnected");
                self.mod_gone();
                return;
            }
            Err(e) => {
                warn!("mod read: {e}");
                self.mod_gone();
                return;
            }
        }
        loop {
            let next = match self.conn.as_mut() {
                Some(c) => c.next(),
                None => return,
            };
            match next {
                Ok(Some(m)) => self.handle(m),
                Ok(None) => return,
                Err(e) => {
                    warn!("bad message from mod: {e}");
                    self.mod_gone();
                    return;
                }
            }
        }
    }

    fn handle(&mut self, m: Msg) {
        let authed = self.conn.as_ref().is_some_and(|c| c.authed);
        if !authed {
            match m {
                Msg::Hello { version, token } if version == VERSION && token_eq(&token, &self.token) => {
                    if let Some(c) = self.conn.as_mut() {
                        c.authed = true;
                    }
                    self.send(Msg::HelloOk { version: VERSION, caps: CAP_HOSTED | CAP_CLIPBOARD | CAP_CURSOR | CAP_POPUPS });
                    info!("mod authenticated");
                }
                _ => {
                    warn!("unauthenticated or wrong-version HELLO (daemon speaks v{VERSION}), dropping connection");
                    self.conn = None; // a stranger (or an older mod): drop it, but keep waiting for the real mod
                }
            }
            return;
        }
        match m {
            Msg::Launch { req, argv } => {
                let display = self.wayland_display.clone();
                if let Err(e) = self.launches.spawn(req, &argv, &display) {
                    self.send(Msg::Error { req, message: format!("{}: {e}", argv.first().cloned().unwrap_or_default()) });
                }
            }
            Msg::Configure { stream, width, height, fps, visible } => self.configure(stream, width, height, fps, visible),
            Msg::Ack { stream, generation, slot } => {
                if let Some(s) = self.streams.iter_mut().find(|s| s.id == stream) {
                    if let Some(r) = s.ring.as_mut() {
                        r.ack(generation, slot as usize);
                    }
                }
            }
            Msg::Pointer { stream, x, y } => self.pointer_motion(stream, x as f64, y as f64),
            Msg::Button { stream, button, pressed } => self.pointer_button(stream, button, pressed),
            Msg::Axis { stream, dx, dy } => self.pointer_axis(stream, dx, dy),
            Msg::Key { stream, scancode, pressed, .. } => self.key(stream, scancode, pressed),
            Msg::Focus { stream, focused } => self.focus(stream, focused),
            Msg::Close { stream } => {
                if let Some(s) = self.streams.iter().find(|s| s.id == stream) {
                    s.toplevel.send_close();
                }
            }
            Msg::ClipboardSet { text } => {
                // we own the clipboard now; an app pasting is served by send_selection
                set_data_device_selection(&self.dh, &self.seat, clipboard::offered_mimes(), Arc::new(text)); // VERIFY signature (0.6)
            }
            other => warn!("unexpected message from mod: {other:?}"),
        }
    }

    // --- streams -------------------------------------------------------------------------------

    fn configure(&mut self, id: u32, width: u16, height: u16, fps: u8, visible: bool) {
        let Some(s) = self.streams.iter_mut().find(|s| s.id == id) else { return };
        s.fps = fps.min(120);
        s.visible = visible;
        if width > 0 && height > 0 {
            s.toplevel.with_pending_state(|st| {
                st.size = Some((width as i32, height as i32).into());
                st.states.set(xdg_toplevel::State::Activated);
            });
            let sent = initial_configure_sent_toplevel(s.toplevel.wl_surface());
            if sent {
                s.toplevel.send_pending_configure(); // VERIFY: exists since smithay 0.4
            }
        }
    }

    /// Composes and publishes dirty streams at their fps and paces the apps with frame callbacks
    /// (hidden / fps 0 = no callbacks = the app stops drawing, zero CPU).
    fn publish_frames(&mut self) {
        let now = Instant::now();
        let time = self.now_ms();
        let mut out = Vec::new();
        for idx in 0..self.streams.len() {
            {
                let s = &self.streams[idx];
                if !s.visible || s.fps == 0 {
                    continue;
                }
                let interval = Duration::from_micros(1_000_000 / s.fps as u64);
                if now.duration_since(s.last_publish) < interval {
                    continue;
                }
            }
            if self.streams[idx].dirty {
                let (frame, layers) = self.frame_layers(idx);
                let s = &mut self.streams[idx];
                match publish(s, frame, &layers, &self.shm_dir) {
                    Ok(Some(mut msgs)) => {
                        out.append(&mut msgs);
                        s.dirty = false;
                        s.damage = Some(Vec::new());
                    }
                    Ok(None) => continue, // every slot is with the mod: try again next loop
                    Err(e) => {
                        warn!(stream = s.id, "frame dropped: {e}");
                        s.dirty = false;
                    }
                }
            }
            let s = &mut self.streams[idx];
            for cb in s.callbacks.drain(..) {
                cb.done(time);
            }
            s.last_publish = now;
        }
        for m in out {
            self.send(m);
        }
    }

    /// Frame size and the layers of a stream (toplevel tree, then its popups), in frame coordinates.
    fn frame_layers(&self, idx: usize) -> ((i32, i32), Vec<Layer>) {
        let s = &self.streams[idx];
        let root = s.toplevel.wl_surface();
        let f = surfaces::frame_rect(root);
        let mut layers = Vec::new();
        surfaces::layers(root, (-f.x, -f.y), &mut layers);
        for p in self.popups.iter().filter(|p| p.stream == s.id && !p.dismissed && p.surface.alive()) {
            if let Some(origin) = self.popup_buffer_origin(p) {
                surfaces::layers(p.surface.wl_surface(), origin, &mut layers);
            }
        }
        ((f.w, f.h), layers)
    }

    fn stream_index_of(&self, surface: &WlSurface) -> Option<usize> {
        self.streams.iter().position(|s| s.toplevel.wl_surface() == surface)
    }

    /// Stream a toplevel or popup surface belongs to.
    fn stream_of(&self, surface: &WlSurface) -> Option<u32> {
        if let Some(i) = self.stream_index_of(surface) {
            return Some(self.streams[i].id);
        }
        self.popups.iter().find(|p| p.surface.wl_surface() == surface).map(|p| p.stream).filter(|id| *id != 0)
    }

    /// Frame position of the window-geometry origin of a toplevel ((0, 0)) or popup (recursive).
    fn geometry_origin(&self, surface: &WlSurface, depth: u8) -> Option<(i32, i32)> {
        if depth > 16 {
            return None;
        }
        if self.stream_index_of(surface).is_some() {
            return Some((0, 0));
        }
        let p = self.popups.iter().find(|p| p.surface.wl_surface() == surface)?;
        let (px, py) = self.geometry_origin(p.parent.as_ref()?, depth + 1)?;
        Some((px + p.rel.x, py + p.rel.y))
    }

    /// Frame position of the popup's buffer (its window geometry may start inside the buffer).
    fn popup_buffer_origin(&self, p: &Popup) -> Option<(i32, i32)> {
        let (gx, gy) = self.geometry_origin(p.surface.wl_surface(), 0)?;
        let own = surfaces::window_geometry(p.surface.wl_surface()).map(|g| (g.x, g.y)).unwrap_or((0, 0));
        Some((gx - own.0, gy - own.1))
    }

    /// Where a popup of `parent` may go: the whole window, relative to the parent's window geometry.
    fn constraint_target(&self, parent: &WlSurface, stream: u32) -> Option<Rectangle<i32, Logical>> {
        let s = self.streams.iter().find(|s| s.id == stream)?;
        let f = surfaces::frame_rect(s.toplevel.wl_surface());
        if f.w <= 0 || f.h <= 0 {
            return None;
        }
        let (px, py) = self.geometry_origin(parent, 0)?;
        // VERIFY: smithay 0.6 may prefer Rectangle::new(loc.into(), size.into()) (from_loc_and_size deprecated)
        Some(Rectangle::from_loc_and_size((-px, -py), (f.w, f.h)))
    }

    fn mark_full(&mut self, stream: u32) {
        if let Some(s) = self.streams.iter_mut().find(|s| s.id == stream) {
            s.damage_all();
        }
    }

    /// Popups the app destroyed (menu closed): repaint the window, give the keyboard back.
    fn reap_popups(&mut self) {
        let mut gone = Vec::new();
        self.popups.retain(|p| {
            let alive = p.surface.alive();
            if !alive && p.stream != 0 {
                gone.push(p.stream);
            }
            alive
        });
        for id in gone {
            self.mark_full(id);
            if self.kbd_stream == id {
                self.refocus_keyboard(id);
            }
        }
    }

    // --- input ---------------------------------------------------------------------------------

    /// Topmost surface with pixels under a frame point (popups first), with its frame position.
    fn hit(&self, id: u32, x: f64, y: f64) -> Option<(WlSurface, (i32, i32))> {
        let idx = self.streams.iter().position(|s| s.id == id)?;
        let (_, layers) = self.frame_layers(idx);
        if let Some(l) = layers.iter().rev().find(|l| l.rect.contains(x, y)) {
            return Some((l.surface.clone(), (l.rect.x, l.rect.y)));
        }
        // outside every surface (e.g. not drawn yet): the toplevel still gets motion
        let root = self.streams[idx].toplevel.wl_surface().clone();
        let f = surfaces::frame_rect(&root);
        Some((root, (-f.x, -f.y)))
    }

    fn pointer_motion(&mut self, id: u32, x: f64, y: f64) {
        let Some((surface, (ox, oy))) = self.hit(id, x, y) else { return };
        let Some(ptr) = self.seat.get_pointer() else { return };
        self.pointer_stream = id;
        self.pointer_pos = (x, y);
        let time = self.now_ms();
        // global space = this stream's frame; the focused surface sits at its frame position
        ptr.motion(self, Some((surface, (ox as f64, oy as f64).into())), &MotionEvent { location: (x, y).into(), serial: SERIAL_COUNTER.next_serial(), time });
        ptr.frame(self);
    }

    fn pointer_button(&mut self, id: u32, button: u32, pressed: bool) {
        let Some(idx) = self.streams.iter().position(|s| s.id == id) else { return };
        let Some(ptr) = self.seat.get_pointer() else { return };
        let serial = SERIAL_COUNTER.next_serial();
        let time = self.now_ms();
        if pressed {
            let open_menu = self.popups.iter().any(|p| p.stream == id && p.grabbed && !p.dismissed);
            if open_menu {
                let (x, y) = self.pointer_pos;
                let on_menu = self
                    .hit(id, x, y)
                    .map(|(s, _)| surfaces::root_of(&s))
                    .is_some_and(|root| self.popups.iter().any(|p| p.surface.wl_surface() == &root));
                if !on_menu {
                    // click beside an open menu closes it and is eaten, like on a desktop
                    self.dismiss_popups(id);
                    self.swallowed.push(button);
                    return;
                }
            } else {
                // a click gives the app keyboard focus, like on any desktop
                let top = self.streams[idx].toplevel.wl_surface().clone();
                if let Some(kbd) = self.seat.get_keyboard() {
                    kbd.set_focus(self, Some(top), serial);
                }
            }
        } else if let Some(i) = self.swallowed.iter().position(|b| *b == button) {
            self.swallowed.remove(i);
            return;
        }
        let state = if pressed { ButtonState::Pressed } else { ButtonState::Released };
        ptr.button(self, &ButtonEvent { serial, time, button, state });
        ptr.frame(self);
    }

    fn dismiss_popups(&mut self, id: u32) {
        for p in self.popups.iter_mut().rev().filter(|p| p.stream == id && !p.dismissed) {
            p.surface.send_popup_done();
            p.dismissed = true;
        }
        self.mark_full(id);
        if self.kbd_stream == id {
            self.refocus_keyboard(id);
        }
    }

    fn pointer_axis(&mut self, id: u32, dx: f32, dy: f32) {
        if !self.streams.iter().any(|s| s.id == id) {
            return;
        }
        let Some(ptr) = self.seat.get_pointer() else { return };
        let time = self.now_ms();
        // mod: +1 = one notch "up"; Wayland: positive vertical = down, 15 px and 120 v120 units per notch
        let mut frame = AxisFrame::new(time).source(AxisSource::Wheel);
        if dy != 0.0 {
            frame = frame.value(Axis::Vertical, -(dy as f64) * 15.0).v120(Axis::Vertical, (-dy * 120.0) as i32);
        }
        if dx != 0.0 {
            frame = frame.value(Axis::Horizontal, dx as f64 * 15.0).v120(Axis::Horizontal, (dx * 120.0) as i32);
        }
        ptr.axis(self, frame);
        ptr.frame(self);
    }

    fn key(&mut self, id: u32, scancode: u32, pressed: bool) {
        if !self.streams.iter().any(|s| s.id == id) || scancode < 8 {
            return;
        }
        let Some(kbd) = self.seat.get_keyboard() else { return };
        let time = self.now_ms();
        let state = if pressed { KeyState::Pressed } else { KeyState::Released };
        // GLFW scancode on Linux = X keycode = evdev + 8 = xkb keycode.
        // VERIFY: recent smithay takes xkb Keycode (u32.into()); 0.3 took the raw evdev code (scancode - 8).
        kbd.input::<(), _>(self, scancode.into(), state, SERIAL_COUNTER.next_serial(), time, |_, _, _| FilterResult::Forward);
    }

    fn focus(&mut self, id: u32, focused: bool) {
        if focused {
            if !self.streams.iter().any(|s| s.id == id) {
                return;
            }
            self.kbd_stream = id;
            self.refocus_keyboard(id);
        } else {
            if self.kbd_stream == id {
                self.kbd_stream = 0;
            }
            let Some(kbd) = self.seat.get_keyboard() else { return };
            kbd.set_focus(self, None, SERIAL_COUNTER.next_serial());
        }
    }

    /// Keyboard to the topmost grabbing menu of the stream, else to the window itself.
    fn refocus_keyboard(&mut self, id: u32) {
        let target = self
            .popups
            .iter()
            .rev()
            .find(|p| p.stream == id && p.grabbed && !p.dismissed && p.surface.alive())
            .map(|p| p.surface.wl_surface().clone())
            .or_else(|| self.streams.iter().find(|s| s.id == id).map(|s| s.toplevel.wl_surface().clone()));
        let Some(kbd) = self.seat.get_keyboard() else { return };
        kbd.set_focus(self, target, SERIAL_COUNTER.next_serial());
    }

    // --- cursor --------------------------------------------------------------------------------

    fn set_cursor(&mut self, image: CursorImageStatus) {
        let stream = self.pointer_stream;
        // VERIFY: smithay 0.6 CursorImageStatus = Hidden | Named(CursorIcon) | Surface(WlSurface)
        match image {
            CursorImageStatus::Hidden => {
                self.cursor_surface = None;
                self.send(Msg::Cursor { stream, shape: CURSOR_HIDDEN, name: String::new(), hot_x: 0, hot_y: 0, width: 0, height: 0, pixels: Vec::new() });
            }
            CursorImageStatus::Named(icon) => {
                self.cursor_surface = None;
                self.send(Msg::Cursor { stream, shape: CURSOR_NAMED, name: icon.name().to_string(), hot_x: 0, hot_y: 0, width: 0, height: 0, pixels: Vec::new() });
            }
            CursorImageStatus::Surface(surface) => {
                self.cursor_surface = Some(surface);
                self.send_cursor_image();
            }
        }
    }

    /// The cursor surface got pixels (or became the cursor): send them with the hotspot.
    fn send_cursor_image(&mut self) {
        let Some(surface) = self.cursor_surface.clone() else { return };
        let Some((w, h, pixels, div)) = surfaces::cursor_pixels(&surface) else { return };
        // VERIFY: CursorImageSurfaceData = Mutex<CursorImageAttributes { hotspot, .. }> (smithay 0.4+)
        let hotspot = with_states(&surface, |states| {
            states.data_map.get::<CursorImageSurfaceData>().map(|d| d.lock().unwrap().hotspot).unwrap_or_default()
        });
        let c = |v: i32| (v.max(0) as usize / div).min(u16::MAX as usize) as u16;
        self.send(Msg::Cursor {
            stream: self.pointer_stream,
            shape: CURSOR_IMAGE,
            name: String::new(),
            hot_x: c(hotspot.x),
            hot_y: c(hotspot.y),
            width: w as u16,
            height: h as u16,
            pixels,
        });
    }

    // --- clipboard -----------------------------------------------------------------------------

    fn poll_clipboard(&mut self) {
        if let Some(mime) = self.clip_request.take() {
            match clipboard::pipe() {
                // VERIFY signature (0.6): request_data_device_client_selection(&Seat<D>, String, OwnedFd)
                Ok((read, write)) => match request_data_device_client_selection(&self.seat, mime, write) {
                    Ok(()) => self.clip_reader = Some(clipboard::Reader::new(read)),
                    Err(e) => warn!("clipboard request: {e:?}"),
                },
                Err(e) => warn!("clipboard pipe: {e}"),
            }
        }
        if let Some(r) = self.clip_reader.as_mut() {
            match r.poll() {
                clipboard::Poll::Pending => {}
                clipboard::Poll::Done(text) => {
                    self.clip_reader = None;
                    if !text.is_empty() {
                        self.send(Msg::Clipboard { text });
                    }
                }
                clipboard::Poll::Failed => {
                    self.clip_reader = None;
                    warn!("clipboard: the app did not deliver its selection");
                }
            }
        }
        self.clip_writers.retain_mut(|w| !w.poll());
    }

    fn shutdown(&mut self) {
        for s in &self.streams {
            s.toplevel.send_close();
        }
        self.launches.terminate_all();
        self.streams.clear(); // drops the rings = removes the shm files
    }
}

/// Composes the stream into a free slot (recreating the ring if the frame grew).
/// `Ok(None)` = no free slot right now.
fn publish(s: &mut Stream, frame: (i32, i32), layers: &[Layer], dir: &Path) -> anyhow::Result<Option<Vec<Msg>>> {
    let mut msgs = Vec::new();
    let (fw, fh) = frame;
    if fw <= 0 || fh <= 0 || layers.is_empty() {
        return Ok(Some(msgs)); // nothing drawn yet
    }
    let (w, h) = (fw as usize, fh as usize);
    let need = w * h * 4;
    let mut recreated = false;
    if s.ring.as_ref().map_or(true, |r| r.slot_size < need) {
        let generation = s.next_generation;
        s.next_generation = s.next_generation.wrapping_add(1);
        let ring = ShmRing::create(dir, s.id, generation, need)?;
        msgs.push(Msg::Buffer {
            stream: s.id,
            generation,
            path: ring.path.to_string_lossy().into_owned(),
            slots: SLOTS as u8,
            slot_size: ring.slot_size as u32,
        });
        s.ring = Some(ring);
        recreated = true;
    }
    let ring = s.ring.as_mut().expect("ring");
    let Some(slot) = ring.free_slot() else { return Ok(None) };
    surfaces::compose(layers, ring.slot_mut(slot), w, h);
    ring.mark_busy(slot);
    s.seq = s.seq.wrapping_add(1);
    let size = (w as u16, h as u16);
    // empty damage = full repaint: new buffer file, new size, or "everything changed"
    let damage = if recreated || size != s.last_size {
        Vec::new()
    } else {
        s.damage.as_deref().map(|d| d.to_vec()).unwrap_or_default()
    };
    s.last_size = size;
    msgs.push(Msg::Frame {
        stream: s.id,
        generation: ring.generation,
        slot: slot as u8,
        seq: s.seq,
        width: size.0,
        height: size.1,
        stride: (w * 4) as u32,
        format: FORMAT_BGRA,
        damage,
    });
    Ok(Some(msgs))
}

fn initial_configure_sent_toplevel(surface: &WlSurface) -> bool {
    with_states(surface, |states| {
        states.data_map.get::<XdgToplevelSurfaceData>().map(|d| d.lock().unwrap().initial_configure_sent).unwrap_or(true)
    })
}

fn initial_configure_sent_popup(surface: &WlSurface) -> bool {
    with_states(surface, |states| {
        states.data_map.get::<XdgPopupSurfaceData>().map(|d| d.lock().unwrap().initial_configure_sent).unwrap_or(true)
    })
}

// --- smithay handlers ------------------------------------------------------------------------------

impl CompositorHandler for State {
    fn compositor_state(&mut self) -> &mut CompositorState {
        &mut self.compositor_state
    }

    fn client_compositor_state<'a>(&self, client: &'a Client) -> &'a CompositorClientState {
        &client.get_data::<ClientState>().expect("client data").compositor_state
    }

    fn commit(&mut self, surface: &WlSurface) {
        // the first commit of an xdg surface asks for its initial configure
        if let Some(s) = self.streams.iter().find(|s| s.toplevel.wl_surface() == surface) {
            if !initial_configure_sent_toplevel(surface) {
                let _ = s.toplevel.send_configure();
            }
        } else if let Some(p) = self.popups.iter().find(|p| p.surface.wl_surface() == surface) {
            if !initial_configure_sent_popup(surface) {
                let _ = p.surface.send_configure();
            }
        }

        // pick up every new buffer of this surface's tree (a synced subsurface lands with its parent)
        let root = surfaces::root_of(surface);
        let tc = surfaces::take_tree(&root);
        let time = self.now_ms();

        if let Some(idx) = self.stream_index_of(&root) {
            let has_popups = self.popups.iter().any(|p| p.stream == self.streams[idx].id && !p.dismissed);
            let f = surfaces::frame_rect(&root);
            let (title, app_id) = with_states(&root, |states| {
                states
                    .data_map
                    .get::<XdgToplevelSurfaceData>()
                    .map(|d| {
                        let d = d.lock().unwrap();
                        (d.title.clone().unwrap_or_default(), d.app_id.clone().unwrap_or_default())
                    })
                    .unwrap_or_default()
            });
            let s = &mut self.streams[idx];
            s.callbacks.extend(tc.callbacks);
            if tc.changed {
                s.dirty = true;
                if tc.simple && !has_popups && !tc.damage.is_empty() {
                    // root-surface damage → frame coordinates (window geometry may start inside the buffer)
                    let rects: Vec<Rect> = tc.damage.iter().filter_map(|r| IRect::new(r.x - f.x, r.y - f.y, r.w, r.h).clip(f.w, f.h)).collect();
                    s.add_damage(rects);
                } else {
                    s.damage = None;
                }
            }
            let changed = title != s.title || app_id != s.app_id;
            let id = s.id;
            if changed {
                s.title = title.clone();
                s.app_id = app_id;
                self.send(Msg::Title { stream: id, title });
            }
            return;
        }

        if let Some(stream) = self.popups.iter().find(|p| p.surface.wl_surface() == &root).map(|p| p.stream) {
            if let Some(s) = self.streams.iter_mut().find(|s| s.id == stream) {
                s.callbacks.extend(tc.callbacks);
                if tc.changed {
                    s.damage_all();
                }
                return;
            }
        }

        // the cursor or a surface without a role yet: never let the app stall on it
        for cb in tc.callbacks {
            cb.done(time);
        }
        if tc.changed && self.cursor_surface.as_ref() == Some(&root) {
            self.send_cursor_image();
        }
    }
}

impl BufferHandler for State {
    fn buffer_destroyed(&mut self, _buffer: &WlBuffer) {}
}

impl ShmHandler for State {
    fn shm_state(&self) -> &ShmState {
        &self.shm_state
    }
}

impl SeatHandler for State {
    type KeyboardFocus = WlSurface;
    type PointerFocus = WlSurface;
    type TouchFocus = WlSurface; // VERIFY: added in smithay 0.4

    fn seat_state(&mut self) -> &mut SeatState<Self> {
        &mut self.seat_state
    }

    fn cursor_image(&mut self, _seat: &Seat<Self>, image: CursorImageStatus) {
        self.set_cursor(image);
    }

    /// The focused app gets the clipboard offers (smallvil pattern).
    fn focus_changed(&mut self, seat: &Seat<Self>, focused: Option<&WlSurface>) {
        let client = focused.and_then(|s| self.dh.get_client(s.id()).ok());
        set_data_device_focus(&self.dh, seat, client);
    }
}

impl SelectionHandler for State {
    type SelectionUserData = Arc<String>;

    /// An app copied (or cleared) something: fetch its text on the next tick.
    fn new_selection(&mut self, ty: SelectionTarget, source: Option<SelectionSource>, _seat: Seat<Self>) {
        if !matches!(ty, SelectionTarget::Clipboard) {
            return; // primary selection (middle click) stays between the apps
        }
        // VERIFY: SelectionSource::mime_types() -> Vec<String>
        if let Some(mime) = source.and_then(|s| clipboard::pick_text_mime(&s.mime_types())) {
            self.clip_request = Some(mime);
        }
    }

    /// An app pastes while we own the clipboard (text from KVR).
    fn send_selection(&mut self, ty: SelectionTarget, mime_type: String, fd: OwnedFd, _seat: Seat<Self>, user_data: &Self::SelectionUserData) {
        if !matches!(ty, SelectionTarget::Clipboard) || !clipboard::TEXT_MIMES.contains(&mime_type.as_str()) {
            return; // dropping fd closes it: the app sees an empty paste
        }
        match clipboard::Writer::new(fd, user_data.clone()) {
            Ok(w) => self.clip_writers.push(w),
            Err(e) => warn!("clipboard write: {e}"),
        }
    }
}

impl DataDeviceHandler for State {
    fn data_device_state(&self) -> &DataDeviceState {
        &self.data_device_state
    }
}

/// Drag and drop between apps works inside the daemon; nothing to add.
impl ClientDndGrabHandler for State {}
impl ServerDndGrabHandler for State {}

impl XdgShellHandler for State {
    fn xdg_shell_state(&mut self) -> &mut XdgShellState {
        &mut self.xdg_shell_state
    }

    fn new_toplevel(&mut self, surface: ToplevelSurface) {
        let pid = surface.wl_surface().client().and_then(|c| c.get_credentials(&self.dh).ok()).map(|c| c.pid);
        let req = self.launches.claim(pid);
        let id = self.next_stream;
        self.next_stream += 1;
        surface.with_pending_state(|st| {
            st.states.set(xdg_toplevel::State::Activated);
            st.decoration_mode = Some(DecorationMode::ServerSide);
        });
        info!(stream = id, req, ?pid, "new window");
        self.streams.push(Stream {
            id,
            toplevel: surface,
            ring: None,
            next_generation: 0,
            callbacks: Vec::new(),
            fps: 30,
            visible: true,
            last_publish: Instant::now() - Duration::from_secs(1),
            seq: 0,
            title: String::new(),
            app_id: String::new(),
            dirty: false,
            damage: None,
            last_size: (0, 0),
        });
        self.send(Msg::Stream { stream: id, req, title: String::new(), app_id: String::new() });
    }

    fn toplevel_destroyed(&mut self, surface: ToplevelSurface) {
        if let Some(i) = self.streams.iter().position(|s| s.toplevel == surface) {
            let s = self.streams.remove(i);
            info!(stream = s.id, "window closed");
            if self.kbd_stream == s.id {
                self.kbd_stream = 0;
            }
            self.send(Msg::Closed { stream: s.id, reason: "closed".into() });
        }
    }

    fn new_popup(&mut self, surface: PopupSurface, positioner: PositionerState) {
        let parent = surface.get_parent_surface();
        let stream = parent.as_ref().and_then(|p| self.stream_of(p)).unwrap_or(0);
        // keep menus inside the window: flip / slide them like a desktop does at the screen edge
        let geo = match parent.as_ref().and_then(|p| self.constraint_target(p, stream)) {
            Some(target) => positioner.clone().get_unconstrained_geometry(target), // VERIFY: PositionerState::get_unconstrained_geometry
            None => positioner.get_geometry(),
        };
        surface.with_pending_state(|st| {
            st.geometry = geo;
            st.positioner = positioner;
        });
        self.popups.push(Popup {
            surface,
            parent,
            stream,
            rel: IRect::new(geo.loc.x, geo.loc.y, geo.size.w, geo.size.h),
            grabbed: false,
            dismissed: false,
        });
        if stream != 0 {
            self.mark_full(stream);
        }
    }

    fn grab(&mut self, surface: PopupSurface, _seat: WlSeat, _serial: Serial) {
        let Some(p) = self.popups.iter_mut().find(|p| p.surface == surface) else { return };
        p.grabbed = true;
        let stream = p.stream;
        if stream != 0 && self.kbd_stream == stream {
            self.refocus_keyboard(stream); // arrows / Enter / Esc work in the open menu
        }
    }

    fn reposition_request(&mut self, surface: PopupSurface, positioner: PositionerState, token: u32) {
        let (parent, stream) = match self.popups.iter().find(|p| p.surface == surface) {
            Some(p) => (p.parent.clone(), p.stream),
            None => (surface.get_parent_surface(), 0),
        };
        let geo = match parent.as_ref().and_then(|p| self.constraint_target(p, stream)) {
            Some(target) => positioner.clone().get_unconstrained_geometry(target),
            None => positioner.get_geometry(),
        };
        surface.with_pending_state(|st| {
            st.geometry = geo;
            st.positioner = positioner;
        });
        surface.send_repositioned(token);
        if let Some(p) = self.popups.iter_mut().find(|p| p.surface == surface) {
            p.rel = IRect::new(geo.loc.x, geo.loc.y, geo.size.w, geo.size.h);
        }
        if stream != 0 {
            self.mark_full(stream);
        }
    }
}

impl XdgDecorationHandler for State {
    fn new_decoration(&mut self, toplevel: ToplevelSurface) {
        toplevel.with_pending_state(|st| st.decoration_mode = Some(DecorationMode::ServerSide));
    }

    fn request_mode(&mut self, toplevel: ToplevelSurface, _mode: DecorationMode) {
        // KVR always draws the title bar itself
        toplevel.with_pending_state(|st| st.decoration_mode = Some(DecorationMode::ServerSide));
        if initial_configure_sent_toplevel(toplevel.wl_surface()) {
            toplevel.send_pending_configure();
        }
    }

    fn unset_mode(&mut self, toplevel: ToplevelSurface) {
        toplevel.with_pending_state(|st| st.decoration_mode = Some(DecorationMode::ServerSide));
    }
}

impl OutputHandler for State {}

delegate_compositor!(State);
delegate_shm!(State);
delegate_seat!(State);
delegate_data_device!(State);
delegate_xdg_shell!(State);
delegate_xdg_decoration!(State);
delegate_output!(State);
