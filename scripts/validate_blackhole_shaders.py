#!/usr/bin/env python3
"""Compiles every black hole shader with the NDK's glslc, the way ShaderLibrary assembles them.

glslc only targets SPIR-V, so shaders are checked as `#version 310 es` (a superset of 300 es) with
auto-assigned locations. That catches syntax, type and undeclared-name errors, not driver quirks.
Usage: scripts/validate_blackhole_shaders.py   (exit code 1 on errors)
"""
import glob
import os
import re
import subprocess
import sys
import tempfile

ASSETS = os.path.join(os.path.dirname(__file__), '..', 'core', 'blackhole', 'renderer', 'src', 'main', 'assets', 'blackhole')
STAGES = {'.vert': 'vert', '.frag': 'frag'}


def find_glslc():
    sdk = os.environ.get('ANDROID_HOME') or os.path.expanduser('~/Library/Android/sdk')
    candidates = sorted(glob.glob(os.path.join(sdk, 'ndk', '*', 'shader-tools', '*', 'glslc')))
    if not candidates:
        sys.exit('glslc not found: install an NDK or set ANDROID_HOME')
    return candidates[-1]


def resolve(name, seen):
    if name in seen:
        return ''
    seen.add(name)
    lines = []
    with open(os.path.join(ASSETS, name)) as source:
        for line in source.read().split('\n'):
            include = re.match(r'\s*#include\s+"([^"]+)"\s*$', line)
            lines.append(resolve(include.group(1), seen) if include else line)
    return '\n'.join(lines)


def main():
    glslc = find_glslc()
    failed = False
    for path in sorted(glob.glob(os.path.join(ASSETS, '*'))):
        name = os.path.basename(path)
        stage = STAGES.get(os.path.splitext(name)[1])
        if stage is None:
            continue
        for hdr in (1, 0):
            defines = {'K_MAX': 3, 'STAR_LAYERS': 3, 'PHI_MAX': '9.42477796', 'PHI_END_MAX': '25.13274123', 'HDR_TARGET': hdr}
            source = '#version 310 es\n' + ''.join(f'#define {k} {v}\n' for k, v in defines.items()) + resolve(name, set())
            with tempfile.NamedTemporaryFile('w', suffix='.' + stage, delete=False) as out:
                out.write(source)
            result = subprocess.run(
                [glslc, f'-fshader-stage={stage}', '--target-env=opengl', '-fauto-map-locations',
                 '-fauto-bind-uniforms', out.name, '-o', os.devnull],
                capture_output=True, text=True,
            )
            os.unlink(out.name)
            if result.returncode != 0 or 'error' in result.stderr:
                failed = True
                print(f'{name} (HDR_TARGET={hdr}):\n{result.stderr}')
    print('shaders: FAILED' if failed else 'shaders: OK')
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
