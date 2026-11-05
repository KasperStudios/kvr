//! Wire format between kvr-desktopd and the mod. Mirror of
//! `src/main/java/com/kasperstudios/kvr/desktop/DesktopProtocol.java`; both test the same golden bytes.
//!
//! message = u16 type, u16 flags (0), u32 payload length, payload   (little-endian)
//! str     = u16 byte length + UTF-8 (<= 4096 bytes)
//! Pixels never go through the socket: they live in the stream's shared-memory file (see shm.rs).

use std::io;

pub const VERSION: u16 = 1;
pub const HEADER: usize = 8;
pub const MAX_PAYLOAD: usize = 64 * 1024;
pub const MAX_STRING: usize = 4096;
pub const MAX_RECTS: usize = 16;
pub const SHM_MAGIC: u32 = 0x5352_564B;
pub const SHM_HEADER: usize = 64;
/// Bytes B, G, R, A per pixel (wl_shm ARGB8888 / XRGB8888 in memory; alpha forced to 255 for X).
pub const FORMAT_BGRA: u8 = 0;
/// Capability bits in HELLO_OK.
pub const CAP_HOSTED: u32 = 1;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Rect {
    pub x: u16,
    pub y: u16,
    pub w: u16,
    pub h: u16,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Msg {
    // mod -> daemon
    Hello { version: u16, token: String },
    Launch { req: u32, argv: Vec<String> },
    Configure { stream: u32, width: u16, height: u16, fps: u8, visible: bool },
    Ack { stream: u32, generation: u16, slot: u8 },
    Pointer { stream: u32, x: f32, y: f32 },
    Button { stream: u32, button: u32, pressed: bool },
    Axis { stream: u32, dx: f32, dy: f32 },
    Key { stream: u32, scancode: u32, glfw_key: i32, pressed: bool },
    Focus { stream: u32, focused: bool },
    Close { stream: u32 },
    // daemon -> mod
    HelloOk { version: u16, caps: u32 },
    Stream { stream: u32, req: u32, title: String, app_id: String },
    Buffer { stream: u32, generation: u16, path: String, slots: u8, slot_size: u32 },
    Frame { stream: u32, generation: u16, slot: u8, seq: u32, width: u16, height: u16, stride: u32, format: u8, damage: Vec<Rect> },
    Title { stream: u32, title: String },
    Closed { stream: u32, reason: String },
    Error { req: u32, message: String },
}

impl Msg {
    pub fn kind(&self) -> u16 {
        match self {
            Msg::Hello { .. } => 1,
            Msg::Launch { .. } => 2,
            Msg::Configure { .. } => 3,
            Msg::Ack { .. } => 4,
            Msg::Pointer { .. } => 5,
            Msg::Button { .. } => 6,
            Msg::Axis { .. } => 7,
            Msg::Key { .. } => 8,
            Msg::Focus { .. } => 9,
            Msg::Close { .. } => 10,
            Msg::HelloOk { .. } => 101,
            Msg::Stream { .. } => 102,
            Msg::Buffer { .. } => 103,
            Msg::Frame { .. } => 104,
            Msg::Title { .. } => 105,
            Msg::Closed { .. } => 106,
            Msg::Error { .. } => 107,
        }
    }

    /// Header + payload, ready to write.
    pub fn encode(&self) -> Vec<u8> {
        let mut p = W(Vec::with_capacity(64));
        match self {
            Msg::Hello { version, token } => {
                p.u16(*version);
                p.str(token);
            }
            Msg::Launch { req, argv } => {
                p.u32(*req);
                p.u16(argv.len() as u16);
                for a in argv {
                    p.str(a);
                }
            }
            Msg::Configure { stream, width, height, fps, visible } => {
                p.u32(*stream);
                p.u16(*width);
                p.u16(*height);
                p.u8(*fps);
                p.u8(*visible as u8);
            }
            Msg::Ack { stream, generation, slot } => {
                p.u32(*stream);
                p.u16(*generation);
                p.u8(*slot);
            }
            Msg::Pointer { stream, x, y } => {
                p.u32(*stream);
                p.f32(*x);
                p.f32(*y);
            }
            Msg::Button { stream, button, pressed } => {
                p.u32(*stream);
                p.u32(*button);
                p.u8(*pressed as u8);
            }
            Msg::Axis { stream, dx, dy } => {
                p.u32(*stream);
                p.f32(*dx);
                p.f32(*dy);
            }
            Msg::Key { stream, scancode, glfw_key, pressed } => {
                p.u32(*stream);
                p.u32(*scancode);
                p.u32(*glfw_key as u32);
                p.u8(*pressed as u8);
            }
            Msg::Focus { stream, focused } => {
                p.u32(*stream);
                p.u8(*focused as u8);
            }
            Msg::Close { stream } => p.u32(*stream),
            Msg::HelloOk { version, caps } => {
                p.u16(*version);
                p.u32(*caps);
            }
            Msg::Stream { stream, req, title, app_id } => {
                p.u32(*stream);
                p.u32(*req);
                p.str(title);
                p.str(app_id);
            }
            Msg::Buffer { stream, generation, path, slots, slot_size } => {
                p.u32(*stream);
                p.u16(*generation);
                p.str(path);
                p.u8(*slots);
                p.u32(*slot_size);
            }
            Msg::Frame { stream, generation, slot, seq, width, height, stride, format, damage } => {
                p.u32(*stream);
                p.u16(*generation);
                p.u8(*slot);
                p.u32(*seq);
                p.u16(*width);
                p.u16(*height);
                p.u32(*stride);
                p.u8(*format);
                let n = damage.len().min(MAX_RECTS);
                p.u8(n as u8);
                for r in &damage[..n] {
                    p.u16(r.x);
                    p.u16(r.y);
                    p.u16(r.w);
                    p.u16(r.h);
                }
            }
            Msg::Title { stream, title } => {
                p.u32(*stream);
                p.str(title);
            }
            Msg::Closed { stream, reason } => {
                p.u32(*stream);
                p.str(reason);
            }
            Msg::Error { req, message } => {
                p.u32(*req);
                p.str(message);
            }
        }
        let mut out = Vec::with_capacity(HEADER + p.0.len());
        out.extend_from_slice(&self.kind().to_le_bytes());
        out.extend_from_slice(&0u16.to_le_bytes());
        out.extend_from_slice(&(p.0.len() as u32).to_le_bytes());
        out.extend_from_slice(&p.0);
        out
    }

    pub fn decode(kind: u16, payload: &[u8]) -> io::Result<Msg> {
        let mut r = R { b: payload, pos: 0 };
        let m = match kind {
            1 => Msg::Hello { version: r.u16()?, token: r.str()? },
            2 => {
                let req = r.u32()?;
                let n = r.u16()? as usize;
                if n > 64 {
                    return Err(bad("argv too long"));
                }
                let mut argv = Vec::with_capacity(n);
                for _ in 0..n {
                    argv.push(r.str()?);
                }
                Msg::Launch { req, argv }
            }
            3 => Msg::Configure { stream: r.u32()?, width: r.u16()?, height: r.u16()?, fps: r.u8()?, visible: r.u8()? != 0 },
            4 => Msg::Ack { stream: r.u32()?, generation: r.u16()?, slot: r.u8()? },
            5 => Msg::Pointer { stream: r.u32()?, x: r.f32()?, y: r.f32()? },
            6 => Msg::Button { stream: r.u32()?, button: r.u32()?, pressed: r.u8()? != 0 },
            7 => Msg::Axis { stream: r.u32()?, dx: r.f32()?, dy: r.f32()? },
            8 => Msg::Key { stream: r.u32()?, scancode: r.u32()?, glfw_key: r.u32()? as i32, pressed: r.u8()? != 0 },
            9 => Msg::Focus { stream: r.u32()?, focused: r.u8()? != 0 },
            10 => Msg::Close { stream: r.u32()? },
            101 => Msg::HelloOk { version: r.u16()?, caps: r.u32()? },
            102 => Msg::Stream { stream: r.u32()?, req: r.u32()?, title: r.str()?, app_id: r.str()? },
            103 => Msg::Buffer { stream: r.u32()?, generation: r.u16()?, path: r.str()?, slots: r.u8()?, slot_size: r.u32()? },
            104 => {
                let (stream, generation, slot, seq) = (r.u32()?, r.u16()?, r.u8()?, r.u32()?);
                let (width, height, stride, format) = (r.u16()?, r.u16()?, r.u32()?, r.u8()?);
                let n = (r.u8()? as usize).min(MAX_RECTS);
                let mut damage = Vec::with_capacity(n);
                for _ in 0..n {
                    damage.push(Rect { x: r.u16()?, y: r.u16()?, w: r.u16()?, h: r.u16()? });
                }
                Msg::Frame { stream, generation, slot, seq, width, height, stride, format, damage }
            }
            105 => Msg::Title { stream: r.u32()?, title: r.str()? },
            106 => Msg::Closed { stream: r.u32()?, reason: r.str()? },
            107 => Msg::Error { req: r.u32()?, message: r.str()? },
            other => return Err(bad(&format!("unknown message type {other}"))),
        };
        Ok(m)
    }
}

fn bad(why: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, why.to_string())
}

struct W(Vec<u8>);

impl W {
    fn u8(&mut self, v: u8) {
        self.0.push(v);
    }
    fn u16(&mut self, v: u16) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn u32(&mut self, v: u32) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn f32(&mut self, v: f32) {
        self.0.extend_from_slice(&v.to_le_bytes());
    }
    fn str(&mut self, s: &str) {
        let mut b = s.as_bytes();
        if b.len() > MAX_STRING {
            // cut on a char boundary
            let mut end = MAX_STRING;
            while !s.is_char_boundary(end) {
                end -= 1;
            }
            b = &b[..end];
        }
        self.u16(b.len() as u16);
        self.0.extend_from_slice(b);
    }
}

struct R<'a> {
    b: &'a [u8],
    pos: usize,
}

impl R<'_> {
    fn take(&mut self, n: usize) -> io::Result<&[u8]> {
        if self.pos + n > self.b.len() {
            return Err(bad("truncated message"));
        }
        let s = &self.b[self.pos..self.pos + n];
        self.pos += n;
        Ok(s)
    }
    fn u8(&mut self) -> io::Result<u8> {
        Ok(self.take(1)?[0])
    }
    fn u16(&mut self) -> io::Result<u16> {
        Ok(u16::from_le_bytes(self.take(2)?.try_into().unwrap()))
    }
    fn u32(&mut self) -> io::Result<u32> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }
    fn f32(&mut self) -> io::Result<f32> {
        Ok(f32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }
    fn str(&mut self) -> io::Result<String> {
        let n = self.u16()? as usize;
        if n > MAX_STRING {
            return Err(bad("string too long"));
        }
        String::from_utf8(self.take(n)?.to_vec()).map_err(|_| bad("bad UTF-8"))
    }
}

/// Splits complete messages off the front of `buf`. `Ok(None)` = need more bytes.
pub fn next_message(buf: &mut Vec<u8>) -> io::Result<Option<Msg>> {
    if buf.len() < HEADER {
        return Ok(None);
    }
    let kind = u16::from_le_bytes([buf[0], buf[1]]);
    let len = u32::from_le_bytes([buf[4], buf[5], buf[6], buf[7]]) as usize;
    if len > MAX_PAYLOAD {
        return Err(bad("message too large"));
    }
    if buf.len() < HEADER + len {
        return Ok(None);
    }
    let msg = Msg::decode(kind, &buf[HEADER..HEADER + len])?;
    buf.drain(..HEADER + len);
    Ok(Some(msg))
}

#[cfg(test)]
mod tests {
    use super::*;

    // Same strings as DesktopProtocolTest.java
    const HELLO_HEX: &str = concat!("01000000", "06000000", "0100", "0200", "6162");
    const FRAME_HEX: &str = concat!(
        "68000000", "1d000000", "07000000", "0100", "02", "09000000", "0400", "0200", "10000000", "00", "01", "0000", "0000", "0400", "0200"
    );

    fn hex(s: &str) -> Vec<u8> {
        (0..s.len()).step_by(2).map(|i| u8::from_str_radix(&s[i..i + 2], 16).unwrap()).collect()
    }

    #[test]
    fn golden_bytes_match_the_mod() {
        assert_eq!(hex(HELLO_HEX), Msg::Hello { version: 1, token: "ab".into() }.encode());
        let f = Msg::Frame {
            stream: 7,
            generation: 1,
            slot: 2,
            seq: 9,
            width: 4,
            height: 2,
            stride: 16,
            format: FORMAT_BGRA,
            damage: vec![Rect { x: 0, y: 0, w: 4, h: 2 }],
        };
        assert_eq!(hex(FRAME_HEX), f.encode());
    }

    #[test]
    fn every_message_round_trips_through_the_framer() {
        let all = vec![
            Msg::Hello { version: 1, token: "токен".into() },
            Msg::Launch { req: 3, argv: vec!["foot".into(), "--title".into(), "ы".into()] },
            Msg::Configure { stream: 5, width: 960, height: 600, fps: 30, visible: true },
            Msg::Ack { stream: 5, generation: 2, slot: 1 },
            Msg::Pointer { stream: 5, x: 12.5, y: 300.0 },
            Msg::Button { stream: 5, button: 0x110, pressed: true },
            Msg::Axis { stream: 5, dx: 0.0, dy: -1.0 },
            Msg::Key { stream: 5, scancode: 38, glfw_key: -1, pressed: false },
            Msg::Focus { stream: 5, focused: true },
            Msg::Close { stream: 5 },
            Msg::HelloOk { version: 1, caps: CAP_HOSTED },
            Msg::Stream { stream: 5, req: 3, title: "foot".into(), app_id: "foot".into() },
            Msg::Buffer { stream: 5, generation: 2, path: "/run/user/1000/kvr/stream-5-2.buf".into(), slots: 3, slot_size: 2_304_000 },
            Msg::Frame { stream: 5, generation: 2, slot: 0, seq: 4_000_000_000, width: 960, height: 600, stride: 3840, format: 0, damage: vec![Rect { x: 10, y: 20, w: 30, h: 40 }] },
            Msg::Title { stream: 5, title: "~/kvr".into() },
            Msg::Closed { stream: 5, reason: "app exited".into() },
            Msg::Error { req: 3, message: "not found: foo".into() },
        ];
        let mut wire = Vec::new();
        for m in &all {
            wire.extend(m.encode());
        }
        wire.extend_from_slice(&[1, 0]); // half a header stays in the buffer
        let mut got = Vec::new();
        while let Some(m) = next_message(&mut wire).unwrap() {
            got.push(m);
        }
        assert_eq!(all, got);
        assert_eq!(wire, vec![1, 0]);
    }

    #[test]
    fn garbage_is_rejected() {
        assert!(Msg::decode(999, &[0; 4]).is_err());
        assert!(Msg::decode(1, &[1, 0, 0x88, 0x13]).is_err()); // string length 5000
        let mut huge = vec![1, 0, 0, 0, 0xff, 0xff, 0xff, 0x7f];
        assert!(next_message(&mut huge).is_err());
    }
}
