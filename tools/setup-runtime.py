#!/usr/bin/env python3
"""Prepare the pinned development renderer. Requires only Python's standard library."""

import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
import tempfile
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
LIBRARIES = ("antlr4-runtime-", "glsl-transformer-", "jcpp-")


def digest(path):
    sha = hashlib.sha512()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            sha.update(chunk)
    return sha.hexdigest()


def atomic_bytes(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_file() and path.read_bytes() == data:
        return
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as output:
        temporary = Path(output.name)
        output.write(data)
    try:
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def fetch(entry, directory):
    filename, expected, url = entry["file"], entry["sha512"], entry["url"]
    if Path(filename).name != filename or not filename.endswith(".jar"):
        raise ValueError("Lock file contains an invalid jar filename")
    if not re.fullmatch(r"[a-f0-9]{128}", expected):
        raise ValueError("Lock file contains an invalid SHA-512")
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != "cdn.modrinth.com":
        raise ValueError("Renderer downloads must use the official Modrinth CDN")
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / filename
    if target.is_file() and digest(target) == expected:
        print("Verified", filename)
        return target
    request = Request(url, headers={"User-Agent": "Frutiger-Aero-setup/2.0"})
    temporary = None
    try:
        with urlopen(request, timeout=45) as source:
            with tempfile.NamedTemporaryFile(dir=directory, delete=False) as output:
                temporary = Path(output.name)
                shutil.copyfileobj(source, output)
        if digest(temporary) != expected:
            raise ValueError("SHA-512 mismatch for " + filename)
        temporary.replace(target)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)
    print("Downloaded and verified", filename)
    return target


def extract_libraries(iris, directory):
    # Loom supplies Fabric API separately. Only Iris's non-mod Java libraries
    # belong on the development runtime classpath.
    with ZipFile(iris) as jar:
        metadata = json.loads(jar.read("fabric.mod.json"))
        entries = [item["file"] for item in metadata["jars"]]
        for prefix in LIBRARIES:
            matches = [entry for entry in entries
                       if entry.startswith("META-INF/jars/")
                       and PurePosixPath(entry).name.startswith(prefix)
                       and entry.endswith(".jar")]
            if len(matches) != 1:
                raise ValueError("Expected one bundled Iris library: " + prefix)
            entry = matches[0]
            name = PurePosixPath(entry).name
            atomic_bytes(directory / name, jar.read(entry))
            for old in directory.glob(prefix + "*.jar"):
                if old.name != name:
                    old.unlink()
            print("Prepared", name)


def install_shader(root):
    shader = root / "dist/Aero-MakeUp-shader.zip"
    if not shader.is_file():
        print("Renderer ready. Optional shader zip is absent; see docs/INSTALL.md.")
        return
    with ZipFile(shader) as archive:
        required = {"shaders/shaders.properties", "LICENSE", "Credits", "AERO-CHANGES.md"}
        if not required.issubset(archive.namelist()):
            raise ValueError("Adapted shader archive is missing its source or credits")
        if archive.testzip() is not None:
            raise ValueError("Adapted shader archive is corrupt")
    atomic_bytes(root / "run/shaderpacks/Aero-MakeUp-shader.zip", shader.read_bytes())
    configuration = root / "run/config/iris.properties"
    if not configuration.exists():
        atomic_bytes(configuration, b"enableShaders=true\nshaderPack=Aero-MakeUp-shader.zip\nmaxShadowRenderDistance=8\n")
    print("Prepared Aero-MakeUp-shader.zip; existing Iris settings preserved")


def setup(root):
    lock = json.loads((root / "dependencies/modrinth-lock.json").read_text())
    paths = [fetch(entry, root / "dependencies") for entry in lock]
    iris = [path for path in paths if path.name.startswith("iris-fabric-")]
    if len(iris) != 1:
        raise ValueError("Expected exactly one Iris jar in the renderer lock")
    extract_libraries(iris[0], root / "dependencies/iris-libs")
    install_shader(root)
    print("Ready: sh tools/gradle.sh runClient")


if __name__ == "__main__":
    try:
        setup(ROOT)
    except (OSError, ValueError, KeyError, BadZipFile) as error:
        print("Setup failed:", error, file=sys.stderr)
        sys.exit(1)
