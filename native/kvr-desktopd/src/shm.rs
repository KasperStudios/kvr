//! Shared-memory frame ring of one stream. The mod maps the same file read-only
//! (`FileChannel.map`) and uploads a slot straight to the GPU, then ACKs it.
//!
//! file = 64-byte header (u32 magic 'KVRS', u32 version, u32 slots, u32 slot_size, zeros)
//!        + SLOTS × slot_size bytes of tightly packed BGRA rows
//! Ownership of a slot is decided by messages only (FRAME hands it to the mod, ACK gives it back),
//! so no atomics live in the file. A bigger app surface = a new file with the next generation.

use crate::protocol::{SHM_HEADER, SHM_MAGIC, VERSION};
use memmap2::MmapMut;
use std::fs::{self, OpenOptions};
use std::io;
use std::os::unix::fs::OpenOptionsExt;
use std::path::{Path, PathBuf};

pub const SLOTS: usize = 3;
const ROUND: usize = 1 << 20;

pub struct ShmRing {
    pub path: PathBuf,
    map: MmapMut,
    pub slot_size: usize,
    pub generation: u16,
    busy: [bool; SLOTS],
}

impl ShmRing {
    pub fn create(dir: &Path, stream: u32, generation: u16, min_slot: usize) -> io::Result<Self> {
        let slot_size = min_slot.max(ROUND).div_ceil(ROUND) * ROUND;
        let path = dir.join(format!("stream-{stream}-{generation}.buf"));
        let file = OpenOptions::new().read(true).write(true).create(true).truncate(true).mode(0o600).open(&path)?;
        file.set_len((SHM_HEADER + SLOTS * slot_size) as u64)?;
        // SAFETY: the file is ours (0600, private dir); the mod only reads it
        let mut map = unsafe { MmapMut::map_mut(&file)? };
        map[0..4].copy_from_slice(&SHM_MAGIC.to_le_bytes());
        map[4..8].copy_from_slice(&(VERSION as u32).to_le_bytes());
        map[8..12].copy_from_slice(&(SLOTS as u32).to_le_bytes());
        map[12..16].copy_from_slice(&(slot_size as u32).to_le_bytes());
        Ok(Self { path, map, slot_size, generation, busy: [false; SLOTS] })
    }

    pub fn free_slot(&self) -> Option<usize> {
        self.busy.iter().position(|b| !b)
    }

    pub fn slot_mut(&mut self, i: usize) -> &mut [u8] {
        let start = SHM_HEADER + i * self.slot_size;
        &mut self.map[start..start + self.slot_size]
    }

    pub fn mark_busy(&mut self, i: usize) {
        self.busy[i] = true;
    }

    pub fn ack(&mut self, generation: u16, i: usize) {
        if generation == self.generation && i < SLOTS {
            self.busy[i] = false;
        }
    }
}

impl Drop for ShmRing {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
    }
}

/// Leftovers of a crashed run (the mod's runtime dir is private, so these are ours).
pub fn remove_stale(dir: &Path) {
    if let Ok(entries) = fs::read_dir(dir) {
        for e in entries.flatten() {
            let name = e.file_name();
            let name = name.to_string_lossy();
            if name.starts_with("stream-") && name.ends_with(".buf") {
                let _ = fs::remove_file(e.path());
            }
        }
    }
}
