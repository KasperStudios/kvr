//! Starting apps inside our hidden compositor — and only there.
//!
//! * `WAYLAND_DISPLAY` = our socket, `DISPLAY` removed: an X11-only app fails instead of opening a
//!   window on the real desktop (Xwayland inside KVR is D3). Toolkit hints force Wayland.
//! * If `dbus-run-session` exists, the app gets a private session bus: single-instance apps
//!   (GApplication, Firefox remote) cannot hand the request to an instance on the real desktop, and
//!   portal dialogs (file chooser) fall back to in-app dialogs, which open inside KVR.
//! * No shell: argv comes from the mod as a list. Children get PR_SET_PDEATHSIG, so they go when we go.

use std::collections::VecDeque;
use std::io;
use std::os::unix::process::CommandExt;
use std::path::PathBuf;
use std::process::{Child, Command, Stdio};
use std::time::{Duration, Instant};

pub struct Launches {
    pending: VecDeque<(u32, u32, Instant)>, // (pid, req, when)
    children: Vec<Child>,
}

impl Launches {
    pub fn new() -> Self {
        Self { pending: VecDeque::new(), children: Vec::new() }
    }

    pub fn spawn(&mut self, req: u32, argv: &[String], wayland_display: &str) -> io::Result<()> {
        if argv.is_empty() {
            return Err(io::Error::new(io::ErrorKind::InvalidInput, "empty command"));
        }
        let mut cmd = match which("dbus-run-session") {
            Some(dbus) => {
                let mut c = Command::new(dbus);
                c.arg("--").args(argv);
                c
            }
            None => {
                let mut c = Command::new(&argv[0]);
                c.args(&argv[1..]);
                c
            }
        };
        cmd.env("WAYLAND_DISPLAY", wayland_display)
            .env_remove("DISPLAY")
            .env_remove("WAYLAND_SOCKET")
            .env("XDG_SESSION_TYPE", "wayland")
            .env("GDK_BACKEND", "wayland")
            .env("QT_QPA_PLATFORM", "wayland")
            .env("SDL_VIDEODRIVER", "wayland")
            .env("CLUTTER_BACKEND", "wayland")
            .env("MOZ_ENABLE_WAYLAND", "1")
            .env("ELECTRON_OZONE_PLATFORM_HINT", "wayland")
            .env("_JAVA_AWT_WM_NONREPARENTING", "1")
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .process_group(0);
        // SAFETY: prctl is async-signal-safe; nothing else runs between fork and exec
        unsafe {
            cmd.pre_exec(|| {
                libc::prctl(libc::PR_SET_PDEATHSIG, libc::SIGTERM);
                Ok(())
            });
        }
        let child = cmd.spawn()?;
        tracing::info!(req, pid = child.id(), ?argv, "launched");
        self.pending.push_back((child.id(), req, Instant::now()));
        self.children.push(child);
        Ok(())
    }

    /// Which LAUNCH a new toplevel belongs to: the client's pid or one of its ancestors was launched
    /// by us (dbus-run-session, launcher scripts); else the oldest launch younger than 30 s. 0 = none.
    pub fn claim(&mut self, client_pid: Option<i32>) -> u32 {
        self.pending.retain(|(_, _, t)| t.elapsed() < Duration::from_secs(30));
        if let Some(pid) = client_pid {
            if let Some(i) = self.pending.iter().position(|(p, _, _)| is_self_or_descendant(pid, *p as i32)) {
                return self.pending.remove(i).map(|(_, r, _)| r).unwrap_or(0);
            }
        }
        self.pending.pop_front().map(|(_, r, _)| r).unwrap_or(0)
    }

    /// Reap exited children (no zombies).
    pub fn reap(&mut self) {
        self.children.retain_mut(|c| matches!(c.try_wait(), Ok(None)));
    }

    /// Session over: ask every app (whole process group) to quit.
    pub fn terminate_all(&mut self) {
        for c in &self.children {
            // SAFETY: plain kill(2) on the group we created with process_group(0)
            unsafe {
                libc::kill(-(c.id() as i32), libc::SIGTERM);
            }
        }
        self.children.clear();
    }
}

fn which(name: &str) -> Option<PathBuf> {
    std::env::var_os("PATH").and_then(|paths| std::env::split_paths(&paths).map(|d| d.join(name)).find(|p| p.is_file()))
}

/// Walks /proc/<pid>/stat parents (≤ 16 levels).
fn is_self_or_descendant(mut pid: i32, ancestor: i32) -> bool {
    for _ in 0..16 {
        if pid == ancestor {
            return true;
        }
        if pid <= 1 {
            return false;
        }
        let Ok(stat) = std::fs::read_to_string(format!("/proc/{pid}/stat")) else { return false };
        // "pid (comm) state ppid ..." — comm may contain spaces, so split after the last ')'
        let Some(rest) = stat.rsplit_once(')').map(|(_, r)| r) else { return false };
        let Some(ppid) = rest.split_whitespace().nth(1).and_then(|s| s.parse().ok()) else { return false };
        pid = ppid;
    }
    false
}
