//! Control connection to the mod: one client, non-blocking Unix socket, polled from the main loop.

use crate::protocol::{next_message, Msg};
use std::io::{self, Read, Write};
use std::os::unix::net::UnixStream;

/// A mod that stops reading must not make us buffer frames forever.
const MAX_OUT: usize = 4 << 20;

pub struct Conn {
    stream: UnixStream,
    inbuf: Vec<u8>,
    out: Vec<u8>,
    pub authed: bool,
}

impl Conn {
    pub fn new(stream: UnixStream) -> io::Result<Self> {
        stream.set_nonblocking(true)?;
        Ok(Self { stream, inbuf: Vec::with_capacity(4096), out: Vec::with_capacity(4096), authed: false })
    }

    /// Reads whatever is available. `Ok(false)` = the mod closed the connection.
    pub fn read_available(&mut self) -> io::Result<bool> {
        let mut tmp = [0u8; 16 * 1024];
        loop {
            match self.stream.read(&mut tmp) {
                Ok(0) => return Ok(false),
                Ok(n) => self.inbuf.extend_from_slice(&tmp[..n]),
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => return Ok(true),
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                Err(e) => return Err(e),
            }
        }
    }

    pub fn next(&mut self) -> io::Result<Option<Msg>> {
        next_message(&mut self.inbuf)
    }

    pub fn queue(&mut self, m: &Msg) -> io::Result<()> {
        if self.out.len() > MAX_OUT {
            return Err(io::Error::new(io::ErrorKind::Other, "mod is not reading"));
        }
        self.out.extend(m.encode());
        Ok(())
    }

    pub fn flush(&mut self) -> io::Result<()> {
        while !self.out.is_empty() {
            match self.stream.write(&self.out) {
                Ok(0) => return Err(io::Error::new(io::ErrorKind::WriteZero, "closed")),
                Ok(n) => {
                    self.out.drain(..n);
                }
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => return Ok(()),
                Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                Err(e) => return Err(e),
            }
        }
        Ok(())
    }
}

/// Constant-time token comparison.
pub fn token_eq(a: &str, b: &str) -> bool {
    let (a, b) = (a.as_bytes(), b.as_bytes());
    if a.len() != b.len() {
        return false;
    }
    a.iter().zip(b).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}
