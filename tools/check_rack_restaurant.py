#!/usr/bin/env python3
"""Check Carry On/Structurize integration without starting Minecraft.

Usage: python tools/check_rack_restaurant.py /path/to/minecraft/mods
The third-party jars are test inputs only and are never bundled with NetLab.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]


def run(command,**kwargs):
    result=subprocess.run(command,cwd=ROOT,**kwargs)
    if result.returncode:raise SystemExit(result.returncode)
    return result


def main():
    if len(sys.argv)!=2:raise SystemExit(__doc__)
    mods=Path(sys.argv[1]); jars=[]
    for pattern in ['carryon-neoforge-*.jar','structurize-*.jar','blockui-*.jar']:
        found=list(mods.glob(pattern))
        if len(found)!=1:raise SystemExit(f'Expected exactly one {pattern} in {mods}')
        jars.append(found[0].resolve())
    output=ROOT/'build/rack-restaurant-check';output.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='rack-check-') as scratch:
        init=Path(scratch)/'classpath.gradle'
        init.write_text("allprojects { tasks.register('printRackCheckClasspath') { doLast { println('NETLAB_RACK_CP=' + sourceSets.automation.runtimeClasspath.asPath) } } }\n")
        result=run(['bash','./dev.sh','--offline','--no-configuration-cache','-I',str(init),'automationClasses','printRackCheckClasspath'],stdout=subprocess.PIPE,text=True)
        marker='NETLAB_RACK_CP='
        cp=next(line[len(marker):] for line in result.stdout.splitlines() if line.startswith(marker))
    cp+=os.pathsep+os.pathsep.join(map(str,jars))
    java_dir=ROOT/'.tools/jdk-21/bin'
    javac=str(java_dir/'javac') if (java_dir/'javac').exists() else shutil.which('javac')
    java=str(java_dir/'java') if (java_dir/'java').exists() else shutil.which('java')
    if not java or not javac:raise SystemExit('Java 21 JDK is required')
    run([javac,'-proc:none','-cp',cp,'-d',str(output),str(ROOT/'tools/Ae2AutomationRuntimeCheck.java'),str(ROOT/'tools/RackRestaurantRuntimeCheck.java')])
    services=output/'META-INF/services';services.mkdir(parents=True,exist_ok=True)
    (services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('Ae2AutomationRuntimeCheck$Properties\n')
    run([java,'-Xmx1G','-cp',str(output)+os.pathsep+cp,'RackRestaurantRuntimeCheck'])


if __name__=='__main__':main()
