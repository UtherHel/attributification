"""Machine-wide run slots: every harness of every repo shares them, so parallel sessions never run more Minecraft
servers or clients at once than the machine can take (MODTEST_SERVER_SLOTS, default 2; MODTEST_CLIENT_SLOTS, default 1).
A slot is an OS file lock, released automatically when the process dies."""
from __future__ import annotations

import contextlib
import os
import threading
import time
from pathlib import Path

from .common import HOME, WINDOWS, log

if WINDOWS:
    import msvcrt
else:
    import fcntl

LIMITS = {"server": int(os.environ.get("MODTEST_SERVER_SLOTS", "2")), "client": int(os.environ.get("MODTEST_CLIENT_SLOTS", "1"))}


def _try_lock(f) -> bool:
    try:
        if WINDOWS:
            msvcrt.locking(f.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        return True
    except OSError:
        return False


def _unlock(f) -> None:
    try:
        if WINDOWS:
            f.seek(0)
            msvcrt.locking(f.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            fcntl.flock(f, fcntl.LOCK_UN)
    except OSError:
        pass


@contextlib.contextmanager
def exclusive(name: str):
    """Machine-wide mutex `name` (e.g. one loader install): parallel runs that need the same install wait for the first
    one instead of running the installer into the same folder at the same time."""
    d = HOME / "locks"
    d.mkdir(parents=True, exist_ok=True)
    f = open(d / f"{name}.lock", "a+")
    try:
        while not _try_lock(f):
            time.sleep(1)
        yield
    finally:
        _unlock(f)
        f.close()


def _live(ticket: Path) -> bool:
    """A waiter keeps its ticket locked: a ticket that can be locked belongs to a dead process and is removed."""
    try:
        f = open(ticket, "a+")
    except OSError:
        return False
    try:
        if not _try_lock(f):
            return True
        _unlock(f)
    finally:
        f.close()
    with contextlib.suppress(OSError):
        ticket.unlink()
    return False


@contextlib.contextmanager
def slot(kind: str, label: str, timeout: float = 3600):
    """Waiters queue up first come, first served (tickets in slots/<kind>-queue): a session that runs one target
    after another cannot keep grabbing a freed slot from a session that has been waiting longer."""
    d = HOME / "slots"
    queue = d / f"{kind}-queue"
    queue.mkdir(parents=True, exist_ok=True)
    n = max(1, LIMITS.get(kind, 1))
    ticket = queue / f"{time.time_ns():020d}-{os.getpid()}-{threading.get_ident()}.t"
    tf = open(ticket, "a+")
    _try_lock(tf)
    end, said = time.time() + timeout, False
    try:
        while True:
            head = next((p for p in sorted(queue.glob("*.t")) if p == ticket or _live(p)), ticket)
            for i in range(n) if head == ticket else ():
                f = open(d / f"{kind}-{i}.lock", "a+")
                if _try_lock(f):
                    _unlock(tf)
                    tf.close()
                    with contextlib.suppress(OSError):
                        ticket.unlink()
                    try:
                        (d / f"{kind}-{i}.who").write_text(f"{os.getpid()} {label}\n")
                        yield i
                    finally:
                        _unlock(f)
                        f.close()
                    return
                f.close()
            if time.time() > end:
                raise TimeoutError(f"no free {kind} slot for {label}")
            if not said:
                log(f"     waiting for a free {kind} slot ({label})")
                said = True
            time.sleep(1)
    finally:
        if not tf.closed:
            _unlock(tf)
            tf.close()
            with contextlib.suppress(OSError):
                ticket.unlink()
