#!/usr/bin/env python3
"""Compiles every black hole shader the way ShaderLibrary assembles it, for every quality level.

With glslangValidator (Khronos, `apt install glslang-tools` / `brew install glslang`) the shaders are
checked as `#version 300 es`, exactly as devices compile them. Without it the NDK's glslc is used: it
only targets SPIR-V, so it checks `#version 310 es` (a superset of 300 es) and can miss a 3.10-only feature.
Either way this catches syntax, type and undeclared-name errors, not driver quirks.

The quality defines (K_MAX, STAR_LAYERS) are read from QualityLevel in BlackHoleParams.kt.

Usage: scripts/validate_blackhole_shaders.py   (exit code 1 on errors)
With no compiler found it skips with exit code 0, unless WALLPER_REQUIRE_SHADER_COMPILER is set (CI).
"""
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'core', 'blackhole', 'renderer', 'src', 'main', 'assets', 'blackhole')
PARAMS = os.path.join(ROOT, 'core', 'blackhole', 'physics', 'src', 'main', 'kotlin', 'com', 'onthecrow', 'wallper',
                      'core', 'blackhole', 'physics', 'BlackHoleParams.kt')
STAGES = {'.vert': 'vert', '.frag': 'frag'}
# LensMath.PHI_MAX and PHI_END_MAX as BlackHoleRenderer prints them; their values don't change what compiles.
LENS_DEFINES = {'PHI_MAX': '9.42477796', 'PHI_END_MAX': '25.13274123'}


def find_glslang_validator():
    return os.environ.get('GLSLANG_VALIDATOR') or shutil.which('glslangValidator')


def find_glslc():
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or os.path.expanduser('~/Library/Android/sdk')
    candidates = sorted(glob.glob(os.path.join(sdk, 'ndk', '*', 'shader-tools', '*', 'glslc')))
    return candidates[-1] if candidates else None


def quality_defines():
    """(K_MAX, STAR_LAYERS) of every QualityLevel, from its constructor arguments."""
    with open(PARAMS) as source:
        text = source.read()
    header = re.search(r'enum class QualityLevel\((.*?)\)\s*\{(.*?);', text, re.S)
    if header is None:
        sys.exit('QualityLevel not found in BlackHoleParams.kt')
    names = re.findall(r'val (\w+):', header.group(1))
    levels = re.findall(r'^\s*([A-Z]+)\(([^)]*)\)', header.group(2), re.M)
    if not levels:
        sys.exit('No QualityLevel entries found in BlackHoleParams.kt')
    combos = set()
    for _, args in levels:
        values = [value.strip() for value in args.split(',')]
        combos.add((values[names.index('diskCrossings')], values[names.index('starLayers')]))
    return sorted(combos)


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


def compile_command(validator, glslc, stage, path):
    if validator:
        return [validator, path]
    return [glslc, f'-fshader-stage={stage}', '--target-env=opengl', '-fauto-map-locations',
            '-fauto-bind-uniforms', path, '-o', os.devnull]


def main():
    validator = find_glslang_validator()
    glslc = None if validator else find_glslc()
    if not validator and not glslc:
        message = 'shaders: no glslangValidator or NDK glslc found'
        if os.environ.get('WALLPER_REQUIRE_SHADER_COMPILER'):
            sys.exit(message)
        print(message + ', skipped')
        return
    version = '#version 300 es' if validator else '#version 310 es'
    if not validator:
        print('shaders: glslangValidator not found, checking with glslc as 310 es')

    failed = False
    for path in sorted(glob.glob(os.path.join(ASSETS, '*'))):
        name = os.path.basename(path)
        stage = STAGES.get(os.path.splitext(name)[1])
        if stage is None:
            continue
        for k_max, star_layers in quality_defines():
            for hdr in (1, 0):
                defines = {'K_MAX': k_max, 'STAR_LAYERS': star_layers, **LENS_DEFINES, 'HDR_TARGET': hdr}
                source = version + '\n' + ''.join(f'#define {k} {v}\n' for k, v in defines.items()) + resolve(name, set())
                with tempfile.NamedTemporaryFile('w', suffix='.' + stage, delete=False) as out:
                    out.write(source)
                result = subprocess.run(compile_command(validator, glslc, stage, out.name), capture_output=True, text=True)
                os.unlink(out.name)
                output = (result.stdout + result.stderr).strip()
                if result.returncode != 0 or 'ERROR' in output or 'error' in result.stderr:
                    failed = True
                    print(f'{name} (K_MAX={k_max}, STAR_LAYERS={star_layers}, HDR_TARGET={hdr}):\n{output}')
    print('shaders: FAILED' if failed else 'shaders: OK')
    sys.exit(1 if failed else 0)


if __name__ == '__main__':
    main()
