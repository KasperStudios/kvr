//! D2 clipboard bridge between the apps (Wayland data device) and KVR / the real system clipboard.
//!
//! * app copies → `SelectionHandler::new_selection` → next tick we ask the app for text through a
//!   pipe (non-blocking, polled from the main loop, 3 s timeout) → CLIPBOARD to the mod.
//! * mod sends CLIPBOARD_SET (player copied something outside, then pastes in an app) → the daemon
//!   becomes the selection owner → an app pasting gets the text written into its pipe.
//!
//! Text only. Images / files between apps of the same daemon still work natively: an app that owns
//! the selection serves other apps directly, we are not in the way.

use std::fs::File;
use std::io::{self, Read, Write};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd, RawFd};
use std::sync::Arc;
use std::time::{Duration, Instant};

/// Text MIME types, preferred first (the X11-style names are what Xwayland-era toolkits still offer).
pub const TEXT_MIMES: [&str; 5] = ["text/plain;charset=utf-8", "UTF8_STRING", "text/plain", "TEXT", "STRING"];
const MAX_READ: usize = 1 << 20;
const TIMEOUT: Duration = Duration::from_secs(3);

pub fn pick_text_mime(offered: &[String]) -> Option<String> {
    TEXT_MIMES.iter().find(|m| offered.iter().any(|o| o == *m)).map(|m| m.to_string())
}

pub fn offered_mimes() -> Vec<String> {
    TEXT_MIMES.iter().map(|m| m.to_string()).collect()
}

fn set_nonblocking(fd: RawFd) -> io::Result<()> {
    // SAFETY: plain fcntl on a descriptor we own
    unsafe {
        let flags = libc::fcntl(fd, libc::F_GETFL);
        if flags < 0 || libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK) < 0 {
            return Err(io::Error::last_os_error());
        }
    }
    Ok(())
}

/// (our non-blocking read end, the write end handed to the app)
pub fn pipe() -> io::Result<(File, OwnedFd)> {
    let mut fds: [libc::c_int; 2] = [0; 2];
    // SAFETY: fds is a valid 2-element array; both descriptors are owned right below
    if unsafe { libc::pipe2(fds.as_mut_ptr(), libc::O_CLOEXEC) } != 0 {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: fresh descriptors from pipe2, owned exactly once each
    let (read, write) = unsafe { (File::from_raw_fd(fds[0]), OwnedFd::from_raw_fd(fds[1])) };
    set_nonblocking(read.as_raw_fd())?;
    Ok((read, write))
}

pub enum Poll {
    Pending,
    Done(String),
    Failed,
}

/// App → us: reads the selection text until the app closes its end.
pub struct Reader {
    file: File,
    buf: Vec<u8>,
    started: Instant,
}

impl Reader {
    pub fn new(file: File) -> Self {
        Self { file, buf: Vec::new(), started: Instant::now() }
    }

    pub fn poll(&mut self) -> Poll {
        let mut tmp = [0u8; 16 * 1024];
        loop {
            match self.file.read(&mut tmp) {
                Ok(0) => return Poll::Done(String::from_utf8_lossy(&self.buf).into_owned()),
                Ok(n) => {
                    self.buf.extend_from_slice(&tmp[..n]);
                    if self.buf.len() >= MAX_READ {
                        return Poll::Done(String::from_utf8_lossy(&self.buf).into_owned());
                    }
                }
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => {
                    return if self.started.elapsed() > TIMEOUT { Poll::Failed } else { Poll::Pending };
                }
                Err(_) => return Poll::Failed,
            }
        }
    }
}

/// Us → app: writes the KVR clipboard text into the app's pipe, then closes it (EOF = done).
pub struct Writer {
    file: File,
    text: Arc<String>,
    pos: usize,
    started: Instant,
}

impl Writer {
    pub fn new(fd: OwnedFd, text: Arc<String>) -> io::Result<Self> {
        set_nonblocking(fd.as_raw_fd())?;
        Ok(Self { file: File::from(fd), text, pos: 0, started: Instant::now() })
    }

    /// true = finished (written, failed or timed out) and can be dropped.
    /// A reader that went away gives EPIPE, not a signal: Rust ignores SIGPIPE by default.
    pub fn poll(&mut self) -> bool {
        let bytes = self.text.as_bytes();
        while self.pos < bytes.len() {
            match self.file.write(&bytes[self.pos..]) {
                Ok(0) => return true,
                Ok(n) => self.pos += n,
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => return self.started.elapsed() > TIMEOUT,
                Err(_) => return true,
            }
        }
        true
    }
}
