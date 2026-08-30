#!/usr/bin/env python3
"""Create/verify ALMI APK delta patches.

The generator is ZIP/APK aware. For every unchanged entry it copies the already-compressed payload
from the installed base APK even when local-header offsets moved. Headers, changed entries, APK
Signing Block and central directory are emitted as DATA so reconstruction is byte-identical to the
signed target APK.
"""
from __future__ import annotations

import argparse
import hashlib
import mmap
import os
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path

MAGIC = b"ALMIDLT1"
FORMAT_VERSION = 1
OP_COPY = 0
OP_DATA = 1
LOCAL_HEADER = struct.Struct("<IHHHHHIIIHH")
LOCAL_MAGIC = 0x04034B50


@dataclass
class Op:
    kind: int
    data: bytes | None = None
    offset: int = 0
    length: int = 0


def sha256(path: Path) -> bytes:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.digest()


def local_payload(mm: mmap.mmap, info: zipfile.ZipInfo) -> tuple[int, int, int]:
    start = info.header_offset
    raw = mm[start : start + LOCAL_HEADER.size]
    if len(raw) != LOCAL_HEADER.size:
        raise ValueError(f"truncated local header for {info.filename}")
    magic, _, flags, _, _, _, _, _, _, name_len, extra_len = LOCAL_HEADER.unpack(raw)
    if magic != LOCAL_MAGIC:
        raise ValueError(f"bad local header for {info.filename}")
    payload_start = start + LOCAL_HEADER.size + name_len + extra_len
    payload_end = payload_start + info.compress_size
    return start, payload_start, payload_end


def append_data(ops: list[Op], data: bytes) -> None:
    if not data:
        return
    if ops and ops[-1].kind == OP_DATA:
        ops[-1].data = (ops[-1].data or b"") + data
        ops[-1].length += len(data)
    else:
        ops.append(Op(OP_DATA, data=data, length=len(data)))


def append_copy(ops: list[Op], offset: int, length: int) -> None:
    if length <= 0:
        return
    if ops and ops[-1].kind == OP_COPY and ops[-1].offset + ops[-1].length == offset:
        ops[-1].length += length
    else:
        ops.append(Op(OP_COPY, offset=offset, length=length))


def create_patch(base_path: Path, target_path: Path, output_path: Path) -> None:
    with base_path.open("rb") as bf, target_path.open("rb") as tf:
        base_mm = mmap.mmap(bf.fileno(), 0, access=mmap.ACCESS_READ)
        target_mm = mmap.mmap(tf.fileno(), 0, access=mmap.ACCESS_READ)
        try:
            with zipfile.ZipFile(base_path) as base_zip, zipfile.ZipFile(target_path) as target_zip:
                base_infos = {i.filename: i for i in base_zip.infolist()}
                target_infos = sorted(target_zip.infolist(), key=lambda i: i.header_offset)
                ops: list[Op] = []
                cursor = 0

                for index, target_info in enumerate(target_infos):
                    t_start, t_payload_start, t_payload_end = local_payload(target_mm, target_info)
                    next_start = (
                        target_infos[index + 1].header_offset
                        if index + 1 < len(target_infos)
                        else target_zip.start_dir
                    )
                    if t_start > cursor:
                        append_data(ops, target_mm[cursor:t_start])

                    # Target local header must be exact, so always emit it as DATA.
                    append_data(ops, target_mm[t_start:t_payload_start])

                    base_info = base_infos.get(target_info.filename)
                    copied_payload = False
                    if (
                        base_info is not None
                        and base_info.CRC == target_info.CRC
                        and base_info.compress_size == target_info.compress_size
                        and base_info.file_size == target_info.file_size
                        and base_info.compress_type == target_info.compress_type
                    ):
                        _, b_payload_start, b_payload_end = local_payload(base_mm, base_info)
                        if (
                            b_payload_end - b_payload_start == t_payload_end - t_payload_start
                            and base_mm[b_payload_start:b_payload_end] == target_mm[t_payload_start:t_payload_end]
                        ):
                            append_copy(ops, b_payload_start, b_payload_end - b_payload_start)
                            copied_payload = True

                    if not copied_payload:
                        append_data(ops, target_mm[t_payload_start:t_payload_end])

                    # Data descriptor/alignment gap (and for final entry, APK Signing Block) is target data.
                    if next_start > t_payload_end:
                        append_data(ops, target_mm[t_payload_end:next_start])
                    cursor = next_start

                if cursor < len(target_mm):
                    append_data(ops, target_mm[cursor:])

                output_path.parent.mkdir(parents=True, exist_ok=True)
                with output_path.open("wb") as out:
                    out.write(MAGIC)
                    out.write(struct.pack(">i", FORMAT_VERSION))
                    out.write(struct.pack(">q", len(base_mm)))
                    out.write(struct.pack(">q", len(target_mm)))
                    out.write(sha256(base_path))
                    out.write(sha256(target_path))
                    out.write(struct.pack(">i", len(ops)))
                    for op in ops:
                        out.write(bytes([op.kind]))
                        if op.kind == OP_COPY:
                            out.write(struct.pack(">q", op.offset))
                            out.write(struct.pack(">i", op.length))
                        else:
                            data = op.data or b""
                            out.write(struct.pack(">i", len(data)))
                            out.write(data)
        finally:
            base_mm.close()
            target_mm.close()

    verify_patch(base_path, output_path, target_path)
    ratio = output_path.stat().st_size / max(1, target_path.stat().st_size)
    print(
        f"ALMI delta: {base_path.name} -> {target_path.name}: "
        f"{output_path.stat().st_size:,} bytes ({ratio:.1%} of target)"
    )


def verify_patch(base_path: Path, patch_path: Path, target_path: Path) -> None:
    with patch_path.open("rb") as patch, base_path.open("rb") as base:
        if patch.read(8) != MAGIC:
            raise ValueError("bad ALMI patch magic")
        version = struct.unpack(">i", patch.read(4))[0]
        if version != FORMAT_VERSION:
            raise ValueError("bad ALMI patch version")
        base_size = struct.unpack(">q", patch.read(8))[0]
        target_size = struct.unpack(">q", patch.read(8))[0]
        base_hash = patch.read(32)
        target_hash = patch.read(32)
        op_count = struct.unpack(">i", patch.read(4))[0]
        if base_size != base_path.stat().st_size or base_hash != sha256(base_path):
            raise ValueError("base mismatch")

        h = hashlib.sha256()
        written = 0
        for _ in range(op_count):
            kind_raw = patch.read(1)
            if not kind_raw:
                raise ValueError("truncated patch")
            kind = kind_raw[0]
            if kind == OP_COPY:
                offset = struct.unpack(">q", patch.read(8))[0]
                length = struct.unpack(">i", patch.read(4))[0]
                base.seek(offset)
                remaining = length
                while remaining:
                    chunk = base.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise ValueError("truncated base copy")
                    h.update(chunk)
                    written += len(chunk)
                    remaining -= len(chunk)
            elif kind == OP_DATA:
                length = struct.unpack(">i", patch.read(4))[0]
                remaining = length
                while remaining:
                    chunk = patch.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise ValueError("truncated patch data")
                    h.update(chunk)
                    written += len(chunk)
                    remaining -= len(chunk)
            else:
                raise ValueError(f"unknown op {kind}")

        if written != target_size:
            raise ValueError(f"target size mismatch {written} != {target_size}")
        if h.digest() != target_hash or target_hash != sha256(target_path):
            raise ValueError("target hash mismatch")


def main() -> None:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    create = sub.add_parser("create")
    create.add_argument("base", type=Path)
    create.add_argument("target", type=Path)
    create.add_argument("output", type=Path)
    verify = sub.add_parser("verify")
    verify.add_argument("base", type=Path)
    verify.add_argument("patch", type=Path)
    verify.add_argument("target", type=Path)
    args = parser.parse_args()
    if args.command == "create":
        create_patch(args.base, args.target, args.output)
    else:
        verify_patch(args.base, args.patch, args.target)
        print("ALMI delta verification: OK")


if __name__ == "__main__":
    main()
