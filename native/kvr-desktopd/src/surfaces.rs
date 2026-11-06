//! D2: an app window = its toplevel surface + subsurfaces + popups (menus, tooltips, combo boxes),
//! composited by us on the CPU into the stream's frame. The mod still gets one picture per window.
//!
//! Every committed wl_shm buffer is copied at once into the surface's own image (kept in the
//! surface's data map, freed with the surface) and released, so apps never wait for us. At publish
//! time the frame is built from these images: the toplevel tree, then its popups bottom to top,
//! cropped to the window geometry (client-side shadows of GTK4 / libadwaita apps are cut away).
//!
//! Cost: one memcpy per committed buffer + one compose per published frame (~2 MB at 960x600,
//! well under a millisecond; alpha blending only where a popup or subsurface is translucent).

use crate::protocol::{Rect, MAX_CURSOR};
use anyhow::bail;
use smithay::reexports::wayland_server::protocol::{wl_buffer::WlBuffer, wl_callback::WlCallback, wl_shm, wl_surface::WlSurface};
use smithay::wayland::compositor::{
    get_parent, with_states, with_surface_tree_downward, BufferAssignment, Damage, SubsurfaceCachedState, SurfaceAttributes, SurfaceData, TraversalAction,
};
use smithay::wayland::shell::xdg::SurfaceCachedState;
use smithay::wayland::shm::with_buffer_contents;
use std::cell::RefCell;
use tracing::warn;

pub const MAX_DIM: i32 = 4096;

/// Tightly packed premultiplied BGRA copy of the surface's last buffer.
#[derive(Default)]
pub struct SurfaceImage {
    pub w: usize,
    pub h: usize,
    pub data: Vec<u8>,
}

type ImageCell = RefCell<SurfaceImage>;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub struct IRect {
    pub x: i32,
    pub y: i32,
    pub w: i32,
    pub h: i32,
}

impl IRect {
    pub fn new(x: i32, y: i32, w: i32, h: i32) -> Self {
        Self { x, y, w, h }
    }

    pub fn contains(&self, px: f64, py: f64) -> bool {
        px >= self.x as f64 && py >= self.y as f64 && px < (self.x + self.w) as f64 && py < (self.y + self.h) as f64
    }

    /// Clipped to the frame, as a protocol rect; None when nothing is left.
    pub fn clip(&self, fw: i32, fh: i32) -> Option<Rect> {
        let x0 = self.x.clamp(0, fw);
        let y0 = self.y.clamp(0, fh);
        let x1 = (self.x + self.w).clamp(0, fw);
        let y1 = (self.y + self.h).clamp(0, fh);
        (x1 > x0 && y1 > y0).then(|| Rect { x: x0 as u16, y: y0 as u16, w: (x1 - x0) as u16, h: (y1 - y0) as u16 })
    }
}

/// A surface with pixels, at its position in frame coordinates.
pub struct Layer {
    pub surface: WlSurface,
    pub rect: IRect,
}

/// What a commit brought for one surface tree.
pub struct TreeCommit {
    /// some surface of the tree got a new buffer (or lost its buffer)
    pub changed: bool,
    /// only the root carries pixels, so its own damage can be passed through
    pub simple: bool,
    /// damage of the root, in root-surface coordinates (empty = whole surface)
    pub damage: Vec<IRect>,
    pub callbacks: Vec<WlCallback>,
}

/// Topmost parent in the subsurface tree (popups and toplevels are roots).
pub fn root_of(surface: &WlSurface) -> WlSurface {
    let mut root = surface.clone();
    while let Some(p) = get_parent(&root) {
        root = p;
    }
    root
}

/// Position of a surface relative to its parent (subsurfaces) or unchanged (roots).
fn own_loc(states: &SurfaceData, parent: (i32, i32)) -> (i32, i32) {
    if states.role == Some("subsurface") {
        // VERIFY: smithay 0.4+ cached state guard API, same as SurfaceAttributes in compositor.rs
        let mut guard = states.cached_state.get::<SubsurfaceCachedState>();
        let l = guard.current().location;
        (parent.0 + l.x, parent.1 + l.y)
    } else {
        parent
    }
}

/// Picks up every new buffer in the tree of `root`: copies it into the surface image and releases it.
pub fn take_tree(root: &WlSurface) -> TreeCommit {
    let mut out = TreeCommit { changed: false, simple: true, damage: Vec::new(), callbacks: Vec::new() };
    with_surface_tree_downward(
        root,
        (0i32, 0i32),
        |_, states, &loc| TraversalAction::DoChildren(own_loc(states, loc)),
        |surface, states, _| {
            let is_root = surface == root;
            let mut guard = states.cached_state.get::<SurfaceAttributes>();
            let attrs = guard.current();
            out.callbacks.extend(attrs.frame_callbacks.drain(..));
            let damage: Vec<IRect> = attrs.damage.drain(..).map(|d| to_irect(&d)).collect();
            match attrs.buffer.take() {
                Some(BufferAssignment::NewBuffer(b)) => {
                    if let Err(e) = copy_buffer(states, &b) {
                        warn!("frame dropped: {e}");
                    }
                    b.release();
                    out.changed = true;
                    if is_root {
                        out.damage.extend(damage);
                    }
                }
                Some(BufferAssignment::Removed) => {
                    if let Some(cell) = states.data_map.get::<ImageCell>() {
                        *cell.borrow_mut() = SurfaceImage::default();
                    }
                    out.changed = true;
                    out.simple = false;
                }
                None => {}
            }
            if !is_root && has_image(states) {
                out.simple = false;
            }
        },
        |_, _, _| true,
    );
    out
}

fn has_image(states: &SurfaceData) -> bool {
    states.data_map.get::<ImageCell>().is_some_and(|c| c.borrow().w > 0)
}

fn copy_buffer(states: &SurfaceData, buffer: &WlBuffer) -> anyhow::Result<()> {
    states.data_map.insert_if_missing(|| RefCell::new(SurfaceImage::default()));
    let cell = states.data_map.get::<ImageCell>().expect("surface image");
    let mut img = cell.borrow_mut();
    let res = with_buffer_contents(buffer, |ptr, len, data| -> anyhow::Result<()> {
        let opaque = match data.format {
            wl_shm::Format::Argb8888 => false,
            wl_shm::Format::Xrgb8888 => true,
            other => bail!("unsupported wl_shm format {other:?} (ARGB/XRGB8888 only)"),
        };
        let (w, h) = (data.width.max(0) as usize, data.height.max(0) as usize);
        let (stride, offset) = (data.stride.max(0) as usize, data.offset.max(0) as usize);
        let tight = w * 4;
        if w == 0 || h == 0 || w > MAX_DIM as usize || h > MAX_DIM as usize || stride < tight || offset + stride * (h - 1) + tight > len {
            bail!("bad buffer {w}x{h} stride {stride}");
        }
        img.w = w;
        img.h = h;
        img.data.resize(tight * h, 0);
        // SAFETY: smithay guarantees ptr..ptr+len is the mapped wl_shm pool for the duration of the closure
        let src = unsafe { std::slice::from_raw_parts(ptr, len) };
        for y in 0..h {
            let row = &src[offset + y * stride..offset + y * stride + tight];
            let d = &mut img.data[y * tight..(y + 1) * tight];
            d.copy_from_slice(row);
            if opaque {
                for px in d.chunks_exact_mut(4) {
                    px[3] = 0xFF; // XRGB: the X byte is undefined
                }
            }
        }
        Ok(())
    });
    match res {
        Ok(r) => r,
        Err(e) => bail!("buffer access: {e:?}"),
    }
}

/// Surfaces of the tree of `root` that have pixels, bottom to top, placed at `origin` (+ subsurface offsets).
pub fn layers(root: &WlSurface, origin: (i32, i32), out: &mut Vec<Layer>) {
    with_surface_tree_downward(
        root,
        origin,
        |_, states, &loc| TraversalAction::DoChildren(own_loc(states, loc)),
        |surface, states, &loc| {
            let (x, y) = own_loc(states, loc);
            if let Some(cell) = states.data_map.get::<ImageCell>() {
                let img = cell.borrow();
                if img.w > 0 {
                    out.push(Layer { surface: surface.clone(), rect: IRect::new(x, y, img.w as i32, img.h as i32) });
                }
            }
        },
        |_, _, _| true,
    );
}

/// Size of the surface's current image (0, 0 before the first buffer).
pub fn image_size(surface: &WlSurface) -> (i32, i32) {
    with_states(surface, |states| states.data_map.get::<ImageCell>().map(|c| {
        let i = c.borrow();
        (i.w as i32, i.h as i32)
    }))
    .unwrap_or((0, 0))
}

/// xdg_surface.set_window_geometry (the visible window without client-side shadows).
pub fn window_geometry(surface: &WlSurface) -> Option<IRect> {
    with_states(surface, |states| {
        // VERIFY: SurfaceCachedState lives in smithay::wayland::shell::xdg (geometry: Option<Rectangle<i32, Logical>>)
        let mut guard = states.cached_state.get::<SurfaceCachedState>();
        guard.current().geometry.map(|r| IRect::new(r.loc.x, r.loc.y, r.size.w, r.size.h))
    })
    .filter(|r| r.w > 0 && r.h > 0)
}

/// The frame rectangle of a toplevel in its surface coordinates: window geometry, else the whole buffer.
pub fn frame_rect(toplevel: &WlSurface) -> IRect {
    let (iw, ih) = image_size(toplevel);
    let g = window_geometry(toplevel).unwrap_or(IRect::new(0, 0, iw, ih));
    IRect::new(g.x, g.y, g.w.min(MAX_DIM), g.h.min(MAX_DIM))
}

/// Builds the frame: transparent background, then every layer (first one copied, the rest blended).
pub fn compose(layers: &[Layer], dst: &mut [u8], w: usize, h: usize) {
    dst[..w * h * 4].fill(0);
    for (i, l) in layers.iter().enumerate() {
        with_states(&l.surface, |states| {
            if let Some(cell) = states.data_map.get::<ImageCell>() {
                blit(&cell.borrow(), l.rect.x, l.rect.y, dst, w, h, i == 0);
            }
        });
    }
}

fn blit(img: &SurfaceImage, x: i32, y: i32, dst: &mut [u8], w: usize, h: usize, copy: bool) {
    let x0 = x.max(0);
    let y0 = y.max(0);
    let x1 = (x + img.w as i32).min(w as i32);
    let y1 = (y + img.h as i32).min(h as i32);
    if x0 >= x1 || y0 >= y1 || img.data.len() < img.w * img.h * 4 {
        return;
    }
    let n = (x1 - x0) as usize;
    let sx = (x0 - x) as usize;
    for row in y0..y1 {
        let sy = (row - y) as usize;
        let s = &img.data[(sy * img.w + sx) * 4..(sy * img.w + sx + n) * 4];
        let d0 = (row as usize * w + x0 as usize) * 4;
        let d = &mut dst[d0..d0 + n * 4];
        if copy {
            d.copy_from_slice(s); // over a transparent background "copy" = "over" (premultiplied)
        } else {
            over(s, d);
        }
    }
}

/// Premultiplied "source over destination".
fn over(s: &[u8], d: &mut [u8]) {
    for (sp, dp) in s.chunks_exact(4).zip(d.chunks_exact_mut(4)) {
        let a = sp[3] as u32;
        if a == 255 {
            dp.copy_from_slice(sp);
        } else if a != 0 {
            let inv = 255 - a;
            for c in 0..4 {
                dp[c] = (sp[c] as u32 + (dp[c] as u32 * inv + 127) / 255).min(255) as u8;
            }
        }
    }
}

/// Cursor image for the mod: ≤ MAX_CURSOR per side (nearest-neighbour downscale), plus the divisor used.
pub fn cursor_pixels(surface: &WlSurface) -> Option<(usize, usize, Vec<u8>, usize)> {
    with_states(surface, |states| {
        let cell = states.data_map.get::<ImageCell>()?;
        let img = cell.borrow();
        if img.w == 0 || img.h == 0 {
            return None;
        }
        let div = img.w.max(img.h).div_ceil(MAX_CURSOR).max(1);
        let (w, h) = (img.w / div, img.h / div);
        if w == 0 || h == 0 {
            return None;
        }
        let mut out = Vec::with_capacity(w * h * 4);
        for y in 0..h {
            for x in 0..w {
                let i = ((y * div) * img.w + x * div) * 4;
                out.extend_from_slice(&img.data[i..i + 4]);
            }
        }
        Some((w, h, out, div))
    })
}

fn to_irect(d: &Damage) -> IRect {
    let r = match d {
        Damage::Surface(r) => (r.loc.x, r.loc.y, r.size.w, r.size.h),
        Damage::Buffer(r) => (r.loc.x, r.loc.y, r.size.w, r.size.h), // buffer scale is always 1 here
    };
    IRect::new(r.0, r.1, r.2, r.3)
}
