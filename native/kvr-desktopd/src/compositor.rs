//! The hidden compositor: a Wayland server with no outputs on any screen. Apps launched by the mod
//! connect to it (WAYLAND_DISPLAY=kvr-desktop-<pid>), draw into wl_shm buffers, and each xdg_toplevel
//! becomes a KVR stream. Nothing is ever shown on the real desktop: we have no backend at all.
//!
//! D1 scope (honest list):
//! * wl_shm only (no linux-dmabuf): GTK/Qt/Firefox/foot fall back to software rendering — fine for
//!   terminals, file managers, editors, browsers at 30–60 fps.
//! * toplevels only: popups (menus) and subsurfaces are configured and kept alive, but not yet
//!   composited into the frame (D2). Server-side decorations are requested: KVR draws the title bar.
//! * pointer, buttons, wheel, keyboard (xkb us+ru, Alt+Shift switches like a desktop).
//!
//! Smithay API written against 0.6 / smallvil from memory: lines marked VERIFY are the likely spots
//! for compile errors. Send the exact `cargo build` output.

use crate::ipc::{token_eq, Conn};
use crate::launch::Launches;
use crate::protocol::{Msg, Rect, CAP_HOSTED, FORMAT_BGRA, MAX_RECTS, VERSION};
use crate::shm::{self, ShmRing, SLOTS};
use anyhow::{bail, Context};
use std::os::unix::fs::PermissionsExt;
use std::os::unix::net::{UnixListener, UnixStream};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tracing::{info, warn};

use smithay::backend::input::{Axis, AxisSource, ButtonState, KeyState};
use smithay::input::keyboard::{FilterResult, XkbConfig};
use smithay::input::pointer::{AxisFrame, ButtonEvent, MotionEvent};
use smithay::input::{Seat, SeatHandler, SeatState};
use smithay::output::{Mode as OutputMode, Output, PhysicalProperties, Scale, Subpixel};
use smithay::reexports::calloop::generic::Generic;
use smithay::reexports::calloop::{EventLoop, Interest, Mode, PostAction};
use smithay::reexports::wayland_protocols::xdg::decoration::zv1::server::zxdg_toplevel_decoration_v1::Mode as DecorationMode;
use smithay::reexports::wayland_protocols::xdg::shell::server::xdg_toplevel;
use smithay::reexports::wayland_server::backend::{ClientData, ClientId, DisconnectReason};
use smithay::reexports::wayland_server::protocol::{wl_buffer::WlBuffer, wl_callback::WlCallback, wl_seat::WlSeat, wl_shm, wl_surface::WlSurface};
use smithay::reexports::wayland_server::{Client, Display, DisplayHandle, Resource};
use smithay::utils::{Serial, Transform, SERIAL_COUNTER};
use smithay::wayland::buffer::BufferHandler;
use smithay::wayland::compositor::{with_states, BufferAssignment, CompositorClientState, CompositorHandler, CompositorState, Damage, SurfaceAttributes};
use smithay::wayland::output::{OutputHandler, OutputManagerState};
use smithay::wayland::shell::xdg::decoration::{XdgDecorationHandler, XdgDecorationState};
use smithay::wayland::shell::xdg::{PopupSurface, PositionerState, ToplevelSurface, XdgPopupSurfaceData, XdgShellHandler, XdgShellState, XdgToplevelSurfaceData};
use smithay::wayland::shm::{with_buffer_contents, ShmHandler, ShmState};
use smithay::wayland::socket::ListeningSocketSource;
use smithay::{delegate_compositor, delegate_output, delegate_seat, delegate_shm, delegate_xdg_decoration, delegate_xdg_shell};

static STOP: AtomicBool = AtomicBool::new(false);

extern "C" fn on_term(_: libc::c_int) {
    STOP.store(true, Ordering::Relaxed);
}

/// A committed buffer we hold until it is copied into the ring (the app double-buffers meanwhile).
struct Pending {
    buffer: WlBuffer,
    /// None = everything changed
    damage: Option<Vec<Rect>>,
}

pub struct Stream {
    id: u32,
    toplevel: ToplevelSurface,
    ring: Option<ShmRing>,
    next_generation: u16,
    pending: Option<Pending>,
    callbacks: Vec<WlCallback>,
    fps: u8,
    visible: bool,
    last_publish: Instant,
    seq: u32,
    title: String,
    app_id: String,
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
    popups: Vec<PopupSurface>,
    launches: Launches,
    next_stream: u32,
    running: bool,
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
        self.publish_frames();
        self.popups.retain(|p| p.alive());
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
                    self.send(Msg::HelloOk { version: VERSION, caps: CAP_HOSTED });
                    info!("mod authenticated");
                }
                _ => {
                    warn!("unauthenticated or wrong-version HELLO, dropping connection");
                    self.conn = None; // a stranger: drop it, but keep waiting for the real mod
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
            Msg::Pointer { stream, x, y } => self.pointer_motion(stream, x, y),
            Msg::Button { stream, button, pressed } => self.pointer_button(stream, button, pressed),
            Msg::Axis { stream, dx, dy } => self.pointer_axis(stream, dx, dy),
            Msg::Key { stream, scancode, pressed, .. } => self.key(stream, scancode, pressed),
            Msg::Focus { stream, focused } => self.focus(stream, focused),
            Msg::Close { stream } => {
                if let Some(s) = self.streams.iter().find(|s| s.id == stream) {
                    s.toplevel.send_close();
                }
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

    /// Copies held buffers into free ring slots at each stream's fps and paces the apps with frame
    /// callbacks (hidden / fps 0 = no callbacks = the app stops drawing, zero CPU).
    fn publish_frames(&mut self) {
        let now = Instant::now();
        let time = self.now_ms();
        let mut out = Vec::new();
        for s in &mut self.streams {
            if !s.visible || s.fps == 0 {
                continue;
            }
            let interval = Duration::from_micros(1_000_000 / s.fps as u64);
            if now.duration_since(s.last_publish) < interval {
                continue;
            }
            if let Some(p) = s.pending.take() {
                match copy_into_ring(s, &p, &self.shm_dir) {
                    Ok(Some(mut msgs)) => {
                        out.append(&mut msgs);
                        p.buffer.release();
                    }
                    Ok(None) => {
                        s.pending = Some(p); // every slot is with the mod: keep the buffer, try next loop
                        continue;
                    }
                    Err(e) => {
                        warn!(stream = s.id, "frame dropped: {e}");
                        p.buffer.release();
                    }
                }
            }
            for cb in s.callbacks.drain(..) {
                cb.done(time);
            }
            s.last_publish = now;
        }
        for m in out {
            self.send(m);
        }
    }

    fn surface_of(&self, id: u32) -> Option<WlSurface> {
        self.streams.iter().find(|s| s.id == id).map(|s| s.toplevel.wl_surface().clone())
    }

    // --- input ---------------------------------------------------------------------------------

    fn pointer_motion(&mut self, id: u32, x: f32, y: f32) {
        let Some(surface) = self.surface_of(id) else { return };
        let Some(ptr) = self.seat.get_pointer() else { return };
        let time = self.now_ms();
        // VERIFY: focus origin is Point<f64, Logical> in recent smithay ((0, 0) i32 in older ones)
        ptr.motion(self, Some((surface, (0.0, 0.0).into())), &MotionEvent { location: (x as f64, y as f64).into(), serial: SERIAL_COUNTER.next_serial(), time });
        ptr.frame(self);
    }

    fn pointer_button(&mut self, id: u32, button: u32, pressed: bool) {
        let Some(surface) = self.surface_of(id) else { return };
        let Some(ptr) = self.seat.get_pointer() else { return };
        let serial = SERIAL_COUNTER.next_serial();
        let time = self.now_ms();
        if pressed {
            // a click gives the app keyboard focus, like on any desktop
            if let Some(kbd) = self.seat.get_keyboard() {
                kbd.set_focus(self, Some(surface), serial);
            }
        }
        let state = if pressed { ButtonState::Pressed } else { ButtonState::Released };
        ptr.button(self, &ButtonEvent { serial, time, button, state });
        ptr.frame(self);
    }

    fn pointer_axis(&mut self, id: u32, dx: f32, dy: f32) {
        if self.surface_of(id).is_none() {
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
        if self.surface_of(id).is_none() || scancode < 8 {
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
        let Some(kbd) = self.seat.get_keyboard() else { return };
        let target = if focused { self.surface_of(id) } else { None };
        if focused && target.is_none() {
            return;
        }
        kbd.set_focus(self, target, SERIAL_COUNTER.next_serial());
    }

    fn shutdown(&mut self) {
        for s in &self.streams {
            s.toplevel.send_close();
        }
        self.launches.terminate_all();
        self.streams.clear(); // drops the rings = removes the shm files
    }
}

/// Copies the held buffer into a free slot (recreating the ring if the surface grew).
/// `Ok(None)` = no free slot right now.
fn copy_into_ring(s: &mut Stream, p: &Pending, dir: &std::path::Path) -> anyhow::Result<Option<Vec<Msg>>> {
    let mut msgs = Vec::new();
    let res = with_buffer_contents(&p.buffer, |ptr, len, data| -> anyhow::Result<Option<()>> {
        let opaque = match data.format {
            wl_shm::Format::Argb8888 => false,
            wl_shm::Format::Xrgb8888 => true,
            other => bail!("unsupported wl_shm format {other:?} (only ARGB/XRGB8888 in D1)"),
        };
        let (w, h) = (data.width.max(0) as usize, data.height.max(0) as usize);
        let (stride, offset) = (data.stride.max(0) as usize, data.offset.max(0) as usize);
        let tight = w * 4;
        if w == 0 || h == 0 || w > 4096 || h > 4096 || stride < tight || offset + stride * (h - 1) + tight > len {
            bail!("bad buffer {w}x{h} stride {stride}");
        }
        let need = tight * h;
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
        // SAFETY: smithay guarantees ptr..ptr+len is the mapped wl_shm pool for the duration of the closure
        let src = unsafe { std::slice::from_raw_parts(ptr, len) };
        let dst = ring.slot_mut(slot);
        for y in 0..h {
            let row = &src[offset + y * stride..offset + y * stride + tight];
            let d = &mut dst[y * tight..(y + 1) * tight];
            d.copy_from_slice(row);
            if opaque {
                for px in d.chunks_exact_mut(4) {
                    px[3] = 0xFF; // XRGB: the X byte is undefined, KVR must not see a transparent window
                }
            }
        }
        ring.mark_busy(slot);
        s.seq = s.seq.wrapping_add(1);
        let damage = if recreated { Vec::new() } else { clamp_damage(p.damage.as_deref(), w, h) };
        msgs.push(Msg::Frame {
            stream: s.id,
            generation: ring.generation,
            slot: slot as u8,
            seq: s.seq,
            width: w as u16,
            height: h as u16,
            stride: tight as u32,
            format: FORMAT_BGRA,
            damage,
        });
        Ok(Some(()))
    });
    match res {
        Ok(Ok(Some(()))) => Ok(Some(msgs)),
        Ok(Ok(None)) => Ok(None),
        Ok(Err(e)) => Err(e),
        Err(e) => bail!("buffer access: {e:?}"),
    }
}

/// Empty = full repaint (also when the app reported more rectangles than the protocol carries).
fn clamp_damage(damage: Option<&[Rect]>, w: usize, h: usize) -> Vec<Rect> {
    let Some(d) = damage else { return Vec::new() };
    if d.is_empty() || d.len() > MAX_RECTS {
        return Vec::new();
    }
    d.iter()
        .filter_map(|r| {
            let x = (r.x as usize).min(w);
            let y = (r.y as usize).min(h);
            let rw = (r.w as usize).min(w - x);
            let rh = (r.h as usize).min(h - y);
            (rw > 0 && rh > 0).then_some(Rect { x: x as u16, y: y as u16, w: rw as u16, h: rh as u16 })
        })
        .collect()
}

fn to_rect(d: &Damage) -> Rect {
    let (x, y, w, h) = match d {
        Damage::Surface(r) => (r.loc.x, r.loc.y, r.size.w, r.size.h),
        Damage::Buffer(r) => (r.loc.x, r.loc.y, r.size.w, r.size.h),
    };
    let c = |v: i32| v.clamp(0, u16::MAX as i32) as u16;
    Rect { x: c(x), y: c(y), w: c(w), h: c(h) }
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
        } else if let Some(p) = self.popups.iter().find(|p| p.wl_surface() == surface) {
            if !initial_configure_sent_popup(surface) {
                let _ = p.send_configure();
            }
        }

        // VERIFY: smithay 0.4+ cached state API (`get::<T>().current()`); 0.3 used `current::<T>()`
        let (buffer, damage, callbacks) = with_states(surface, |states| {
            let mut guard = states.cached_state.get::<SurfaceAttributes>();
            let attrs = guard.current();
            let buffer = attrs.buffer.take();
            let damage: Vec<Rect> = attrs.damage.drain(..).map(|d| to_rect(&d)).collect();
            let callbacks: Vec<WlCallback> = attrs.frame_callbacks.drain(..).collect();
            (buffer, damage, callbacks)
        });

        let time = self.now_ms();
        let Some(idx) = self.streams.iter().position(|s| s.toplevel.wl_surface() == surface) else {
            // popup / subsurface / cursor: not composited in D1 — release and tick it so the app never stalls
            if let Some(BufferAssignment::NewBuffer(b)) = buffer {
                b.release();
            }
            for cb in callbacks {
                cb.done(time);
            }
            return;
        };

        let (title, app_id) = with_states(surface, |states| {
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
        s.callbacks.extend(callbacks);
        match buffer {
            Some(BufferAssignment::NewBuffer(b)) => {
                let merged = match s.pending.take() {
                    Some(old) => {
                        if old.buffer != b {
                            old.buffer.release(); // replaced before we copied it
                        }
                        match old.damage {
                            Some(mut d) if d.len() + damage.len() <= MAX_RECTS => {
                                d.extend(damage);
                                Some(d)
                            }
                            _ => None,
                        }
                    }
                    None => Some(damage),
                };
                s.pending = Some(Pending { buffer: b, damage: merged });
            }
            Some(BufferAssignment::Removed) => {
                if let Some(old) = s.pending.take() {
                    old.buffer.release();
                }
            }
            None => {}
        }
        let changed = title != s.title || app_id != s.app_id;
        let id = s.id;
        if changed {
            s.title = title.clone();
            s.app_id = app_id;
            self.send(Msg::Title { stream: id, title });
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
}

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
            pending: None,
            callbacks: Vec::new(),
            fps: 30,
            visible: true,
            last_publish: Instant::now() - Duration::from_secs(1),
            seq: 0,
            title: String::new(),
            app_id: String::new(),
        });
        self.send(Msg::Stream { stream: id, req, title: String::new(), app_id: String::new() });
    }

    fn toplevel_destroyed(&mut self, surface: ToplevelSurface) {
        if let Some(i) = self.streams.iter().position(|s| s.toplevel == surface) {
            let s = self.streams.remove(i);
            info!(stream = s.id, "window closed");
            self.send(Msg::Closed { stream: s.id, reason: "closed".into() });
        }
    }

    fn new_popup(&mut self, surface: PopupSurface, positioner: PositionerState) {
        surface.with_pending_state(|st| st.geometry = positioner.get_geometry());
        self.popups.push(surface);
    }

    fn grab(&mut self, _surface: PopupSurface, _seat: WlSeat, _serial: Serial) {
        // popup grabs arrive with menus in D2
    }

    fn reposition_request(&mut self, surface: PopupSurface, positioner: PositionerState, token: u32) {
        surface.with_pending_state(|st| {
            st.geometry = positioner.get_geometry();
            st.positioner = positioner;
        });
        surface.send_repositioned(token);
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
delegate_xdg_shell!(State);
delegate_xdg_decoration!(State);
delegate_output!(State);
