#!/usr/bin/env python3
"""Conditional courses, actual advancement codecs and guide highlighting; never starts the game."""
from pathlib import Path
import os,subprocess,tempfile
ROOT=Path(__file__).resolve().parents[1]
def run(args,**kw):
 r=subprocess.run(args,cwd=ROOT,**kw)
 if r.returncode:raise SystemExit(r.returncode)
 return r
with tempfile.TemporaryDirectory(prefix='netlab-academy-') as temp:
 init=Path(temp)/'classpath.gradle'
 init.write_text("allprojects { tasks.register('printAcademyClasspath') { doLast { println('ACADEMY_CP=' + sourceSets.academy.runtimeClasspath.asPath) } } }\n")
 result=run(['bash','./dev.sh','--offline','--no-configuration-cache','-I',str(init),'academyClasses','printAcademyClasspath'],stdout=subprocess.PIPE,text=True)
 cp=next(x[len('ACADEMY_CP='):] for x in result.stdout.splitlines() if x.startswith('ACADEMY_CP='))
 # Crucially no optional addon output, JCEF/Rinku or voice API on this classpath.
 if any(f'/classes/java/{name}' in cp for name in ('automation','mobile','sputnik','media')):raise SystemExit('Optional classes leaked into Academy runtime')
 out=ROOT/'build/academy-runtime-check';out.mkdir(parents=True,exist_ok=True)
 run([str(ROOT/'.tools/jdk-21/bin/javac'),'-proc:none','-cp',cp,'-d',str(out),str(ROOT/'tools/AcademyRuntimeCheck.java')])
 run([str(ROOT/'.tools/jdk-21/bin/java'),'-Xmx1G','-cp',str(out)+os.pathsep+cp,'AcademyRuntimeCheck'])
