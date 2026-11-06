//! kvr-desktopd — runs PC apps inside a hidden Wayland compositor and streams their windows into
//! KVR (Minecraft). The apps never get a window on the real desktop.
//!
//! Started by the mod (after the player's consent) as:
//!     kvr-desktopd --socket $XDG_RUNTIME_DIR/kvr/desktopd.sock --shm-dir $XDG_RUNTIME_DIR/kvr
//! with the session token in the environment (KVR_DESKTOPD_TOKEN). Exits with the game.

mod protocol;

#[cfg(target_os = "linux")]
mod clipboard;
#[cfg(target_os = "linux")]
mod compositor;
#[cfg(target_os = "linux")]
mod ipc;
#[cfg(target_os = "linux")]
mod launch;
#[cfg(target_os = "linux")]
mod shm;
#[cfg(target_os = "linux")]
mod surfaces;

#[cfg(not(target_os = "linux"))]
fn main() {
    if std::env::args().any(|a| a == "--version") {
        println!("kvr-desktopd {} protocol {} (no backend for this OS yet)", env!("CARGO_PKG_VERSION"), protocol::VERSION);
        return;
    }
    eprintln!("kvr-desktopd: no backend for this OS yet — Windows (hidden desktop) arrives in D5");
    std::process::exit(2);
}

#[cfg(target_os = "linux")]
fn main() -> anyhow::Result<()> {
    use anyhow::{bail, Context};
    use tracing_subscriber::EnvFilter;

    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")))
        .with_writer(std::io::stderr)
        .with_ansi(false)
        .init();

    let mut socket = None;
    let mut shm_dir = None;
    let mut args = std::env::args().skip(1);
    while let Some(a) = args.next() {
        match a.as_str() {
            "--socket" => socket = args.next(),
            "--shm-dir" => shm_dir = args.next(),
            "--version" => {
                println!("kvr-desktopd {} protocol {}", env!("CARGO_PKG_VERSION"), protocol::VERSION);
                return Ok(());
            }
            other => bail!("unknown argument {other} (expected --socket <path> --shm-dir <dir>)"),
        }
    }
    let socket = std::path::PathBuf::from(socket.context("--socket <path> is required")?);
    let shm_dir = std::path::PathBuf::from(shm_dir.context("--shm-dir <dir> is required")?);
    let token = std::env::var("KVR_DESKTOPD_TOKEN").context("KVR_DESKTOPD_TOKEN is not set (the mod passes it)")?;
    if token.len() < 32 {
        bail!("KVR_DESKTOPD_TOKEN too short");
    }
    // apps must not inherit the token
    std::env::remove_var("KVR_DESKTOPD_TOKEN");
    // die with Minecraft even if it crashes without running its shutdown hook
    // SAFETY: plain prctl call
    unsafe {
        libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM);
    }
    compositor::run(socket, shm_dir, token)
}
