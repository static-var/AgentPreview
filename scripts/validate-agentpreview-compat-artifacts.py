#!/usr/bin/env python3
import csv
import json
import struct
import sys
import zlib
from pathlib import Path


def module_dir(kind):
    if kind == "android-app":
        return "app"
    if kind == "cmp-app":
        return "composeApp"
    if kind == "kmp-library":
        return "designSystem"
    raise ValueError(f"unknown project kind: {kind}")


def expected_text(kind):
    if kind == "android-app":
        return "Android app matrix"
    if kind == "cmp-app":
        return "CMP app matrix"
    if kind == "kmp-library":
        return "KMP library matrix"
    raise ValueError(f"unknown project kind: {kind}")


def walk_nodes(value):
    if isinstance(value, dict):
        yield value
        for child in value.get("children") or []:
            yield from walk_nodes(child)
    elif isinstance(value, list):
        for item in value:
            yield from walk_nodes(item)


def read_png(path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("not a PNG file")
    offset = 8
    ihdr = None
    idat = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data = data[offset + 8 : offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            ihdr = chunk_data
        elif chunk_type == b"IDAT":
            idat.extend(chunk_data)
        elif chunk_type == b"IEND":
            break
    if ihdr is None:
        raise ValueError("missing IHDR")
    width, height, bit_depth, color_type, _compression, _filter, interlace = struct.unpack(">IIBBBBB", ihdr)
    if interlace != 0:
        raise ValueError("interlaced PNGs are not supported by this validator")
    channels_by_type = {
        0: 1,
        2: 3,
        4: 2,
        6: 4,
    }
    if bit_depth != 8 or color_type not in channels_by_type:
        return width, height, None
    channels = channels_by_type[color_type]
    stride = width * channels
    raw = zlib.decompress(bytes(idat))
    rows = []
    previous = bytearray(stride)
    pos = 0
    for _row_index in range(height):
        filter_type = raw[pos]
        pos += 1
        scanline = bytearray(raw[pos : pos + stride])
        pos += stride
        recon = bytearray(stride)
        for i, value in enumerate(scanline):
            left = recon[i - channels] if i >= channels else 0
            up = previous[i]
            up_left = previous[i - channels] if i >= channels else 0
            if filter_type == 0:
                recon[i] = value
            elif filter_type == 1:
                recon[i] = (value + left) & 0xFF
            elif filter_type == 2:
                recon[i] = (value + up) & 0xFF
            elif filter_type == 3:
                recon[i] = (value + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                predictor = paeth(left, up, up_left)
                recon[i] = (value + predictor) & 0xFF
            else:
                raise ValueError(f"unsupported PNG filter type {filter_type}")
        rows.append(bytes(recon))
        previous = recon
    unique_pixels = set()
    for row in rows:
        for i in range(0, len(row), channels):
            unique_pixels.add(row[i : i + channels])
    return width, height, len(unique_pixels)


def paeth(a, b, c):
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def main(argv):
    summary = Path(argv[1]) if len(argv) > 1 else Path("build/agentpreview-compat/summary.tsv")
    rows = list(csv.DictReader(summary.open(), delimiter="\t"))
    failures = []
    warnings = []
    passes = []
    for row in rows:
        case = row["case"]
        if row["fake"] != "pass" or row["real"] != "pass":
            failures.append(f"{case}: matrix status fake={row['fake']} real={row['real']}")
            continue
        project_dir = Path(row["project_dir"])
        snapshots_root = project_dir / module_dir(row["kind"]) / "build" / "agentPreviewSnapshots"
        snapshots = sorted(snapshots_root.glob("**/snapshot.json"))
        if len(snapshots) != 1:
            failures.append(f"{case}: expected 1 snapshot under {snapshots_root}, found {len(snapshots)}")
            continue
        snapshot_path = snapshots[0]
        screenshot_path = snapshot_path.parent / "screenshot.png"
        if not screenshot_path.is_file():
            failures.append(f"{case}: missing screenshot {screenshot_path}")
            continue
        data = json.loads(snapshot_path.read_text())
        if data.get("render", {}).get("mode") != "robolectric":
            failures.append(f"{case}: render.mode={data.get('render', {}).get('mode')}")
        if "CompatPreview" not in data.get("preview", {}).get("id", ""):
            failures.append(f"{case}: unexpected preview id {data.get('preview', {}).get('id')}")
        if data.get("viewport", {}).get("name") != "preview":
            failures.append(f"{case}: unexpected viewport {data.get('viewport')}")

        nodes = list(walk_nodes(data.get("nodes", [])))
        layout_nodes = list(walk_nodes(data.get("layoutTree", [])))
        node_texts = {str(node.get("text")) for node in nodes if node.get("text") is not None}
        tags = {str(node.get("tag")) for node in nodes if node.get("tag") is not None}
        tags |= {
            str(node.get("semantics", {}).get("tag"))
            for node in layout_nodes
            if isinstance(node.get("semantics"), dict) and node.get("semantics", {}).get("tag") is not None
        }
        required_text = expected_text(row["kind"])
        if "AgentPreview" not in node_texts:
            failures.append(f"{case}: missing AgentPreview semantics text; got {sorted(node_texts)}")
        if required_text not in node_texts:
            failures.append(f"{case}: missing {required_text!r} semantics text; got {sorted(node_texts)}")
        if "compat_preview" not in tags:
            failures.append(f"{case}: missing compat_preview tag; got {sorted(tags)}")
        if not layout_nodes:
            failures.append(f"{case}: layoutTree is empty")

        layout_texts = {
            str(node.get("semantics", {}).get("text"))
            for node in layout_nodes
            if isinstance(node.get("semantics"), dict) and node.get("semantics", {}).get("text") is not None
        }
        if required_text not in layout_texts:
            warnings.append(f"{case}: layoutTree omits {required_text!r} semantics text")

        width, height, unique_pixels = read_png(screenshot_path)
        screenshot = data.get("screenshot", {})
        if (width, height) != (screenshot.get("width"), screenshot.get("height")):
            failures.append(
                f"{case}: PNG size {(width, height)} != JSON size {(screenshot.get('width'), screenshot.get('height'))}",
            )
        if width <= 0 or height <= 0:
            failures.append(f"{case}: non-positive PNG dimensions {(width, height)}")
        if unique_pixels is not None and unique_pixels < 2:
            failures.append(f"{case}: screenshot appears blank, unique_pixels={unique_pixels}")
        passes.append((case, row["kind"], width, height, unique_pixels, snapshot_path, screenshot_path))

    for case, kind, width, height, unique_pixels, snapshot_path, screenshot_path in passes:
        print(f"PASS {case} kind={kind} png={width}x{height} colors={unique_pixels or 'not-counted'}")
        print(f"  snapshot={snapshot_path}")
        print(f"  screenshot={screenshot_path}")
    if warnings:
        print("\nWARNINGS:")
        for warning in warnings:
            print(f"- {warning}")
    if failures:
        print("\nFAILURES:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print(f"Validated {len(passes)} real-render snapshots and screenshots.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
