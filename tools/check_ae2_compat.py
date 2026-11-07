#!/usr/bin/env python3
"""Real optional Mixin + AE2 transfer checks; never starts Minecraft or a world.

Usage: python tools/check_ae2_compat.py /path/to/appliedenergistics2-19.2.18.jar
AE2 remains a user-supplied test input, not a build/runtime dependency of NetLab.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(command, **kwargs):
    result = subprocess.run(command, cwd=ROOT, **kwargs)
    if result.returncode:
        raise SystemExit(result.returncode)
    return result


def main():
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_file():
        raise SystemExit(__doc__)
    ae2 = Path(sys.argv[1]).resolve()
    output = ROOT / 'build/ae2-automation-check'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='ae2-check-') as scratch:
        init = Path(scratch) / 'classpath.gradle'
        init.write_text("""allprojects {
    tasks.register('printAe2CheckClasspath') {
        doLast { println('NETLAB_AE2_CP=' + sourceSets.automation.runtimeClasspath.asPath) }
    }
}
""")
        result = run(['bash', './dev.sh', '--offline', '--no-configuration-cache', '-I', str(init),
                      'automationClasses', 'printAe2CheckClasspath'], stdout=subprocess.PIPE, text=True)
        marker = 'NETLAB_AE2_CP='
        cp = next(line[len(marker):] for line in result.stdout.splitlines() if line.startswith(marker))
    java_dir = ROOT / '.tools/jdk-21/bin'
    javac = str(java_dir / 'javac') if (java_dir / 'javac').exists() else shutil.which('javac')
    java = str(java_dir / 'java') if (java_dir / 'java').exists() else shutil.which('java')
    if not java or not javac:
        raise SystemExit('Java 21 JDK is required')
    run([javac, '-proc:none', '-cp', cp, '-d', str(output), str(ROOT / 'tools/Ae2AutomationRuntimeCheck.java')])
    services = output / 'META-INF/services'
    services.mkdir(parents=True, exist_ok=True)
    (services / 'org.spongepowered.asm.service.IGlobalPropertyService').write_text('Ae2AutomationRuntimeCheck$Properties\n')
    base = str(output) + os.pathsep + cp
    run([java, '-Xmx1G', '-cp', base, 'Ae2AutomationRuntimeCheck', 'without-ae2'])
    run([java, '-Xmx1G', '-cp', base + os.pathsep + str(ae2), 'Ae2AutomationRuntimeCheck'])


if __name__ == '__main__':
    main()
