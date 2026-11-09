#!/usr/bin/env python3
"""Actual Media registries, recipes, picking, memory cards and voice API. Never starts Minecraft."""
from pathlib import Path
import os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
def run(args,**kw):
 r=subprocess.run(args,cwd=ROOT,**kw)
 if r.returncode:raise SystemExit(r.returncode)
 return r
with tempfile.TemporaryDirectory(prefix='netlab-media-') as temp:
 init=Path(temp)/'classpath.gradle'
 init.write_text("allprojects { tasks.register('printMediaCheckClasspath') { doLast { println('MEDIA_CHECK_CP=' + sourceSets.media.runtimeClasspath.asPath + File.pathSeparator + sourceSets.mobile.output.asPath + File.pathSeparator + sourceSets.automation.output.asPath + File.pathSeparator + files('.tools/sputnik/de.keksuccino.rinku-neoforge-3.0.4-1.21.1-mod.jar', '.tools/media/voicechat-api-2.6.0.jar').asPath) } } }\n")
 result=run(['bash','./dev.sh','--offline','--no-configuration-cache','-I',str(init),'mediaClasses','mobileClasses','automationClasses','printMediaCheckClasspath'],stdout=subprocess.PIPE,text=True)
 cp=next(line[len('MEDIA_CHECK_CP='):] for line in result.stdout.splitlines() if line.startswith('MEDIA_CHECK_CP='))
 out=ROOT/'build/media-runtime-check';out.mkdir(parents=True,exist_ok=True)
 files=['FabricationRuntimeCheck','MobileRuntimeCheck','FluidRuntimeCheck','ServerThermalRuntimeCheck','SputnikRuntimeCheck','RecipeCoverageRuntimeCheck','MediaRuntimeCheck','MediaVoiceRuntimeCheck','Ae2AutomationRuntimeCheck']
 run([str(ROOT/'.tools/jdk-21/bin/javac'),'-proc:none','-cp',cp,'-d',str(out),*[str(ROOT/'tools'/f'{name}.java') for name in files]])
 services=out/'META-INF/services';services.mkdir(parents=True,exist_ok=True);(services/'org.spongepowered.asm.service.IGlobalPropertyService').write_text('Ae2AutomationRuntimeCheck$Properties\n')
 for main in ['MediaRuntimeCheck','RecipeCoverageRuntimeCheck']:
  run([str(ROOT/'.tools/jdk-21/bin/java'),'-Xmx1G','-cp',str(out)+os.pathsep+cp,main])
