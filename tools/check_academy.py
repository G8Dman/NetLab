#!/usr/bin/env python3
"""Conditional courses, actual advancement codecs and guide highlighting; never starts the game."""
from pathlib import Path
import argparse,os,shutil,subprocess,tempfile
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--online",action="store_true",help="Allow Gradle to fill a fresh runtime dependency cache; never launches the game")
options=parser.parse_args()
ROOT=Path(__file__).resolve().parents[1]
def java_tool(name):
 suffix='.exe' if os.name=='nt' else ''
 candidates=[ROOT/'.tools/jdk-21/bin'/(name+suffix)]
 if os.environ.get('JAVA_HOME'):candidates.append(Path(os.environ['JAVA_HOME'])/'bin'/(name+suffix))
 found=shutil.which(name)
 if found:candidates.append(Path(found))
 for candidate in candidates:
  if candidate.is_file():return str(candidate)
 raise SystemExit('Java 21 JDK required: missing '+name)

def run(args,**kw):
 r=subprocess.run(args,cwd=ROOT,**kw)
 if r.returncode:
  if r.stdout:print(r.stdout)
  raise SystemExit(r.returncode)
 return r
with tempfile.TemporaryDirectory(prefix='netlab-academy-') as temp:
 init=Path(temp)/'classpath.gradle'
 init.write_text("allprojects { tasks.register('printAcademyClasspath') { doLast { println('ACADEMY_CP=' + sourceSets.academy.runtimeClasspath.asPath) } } }\n")
 result=run(['bash','./dev.sh',*([] if options.online else ['--offline']),'--no-configuration-cache','-I',str(init),'academyClasses','printAcademyClasspath'],stdout=subprocess.PIPE,text=True)
 cp=next(x[len('ACADEMY_CP='):] for x in result.stdout.splitlines() if x.startswith('ACADEMY_CP='))
 # Runtime dependencies include Minecraft resources used by the real codecs. A fresh
 # cache may need --online even after compile-only tasks; no launcher is started.
 # No optional addon output, JCEF/Rinku or voice API is present.
 if any(f'/classes/java/{name}' in cp for name in ('automation','mobile','sputnik','media')):raise SystemExit('Optional classes leaked into Academy runtime')
 out=ROOT/'build/academy-runtime-check';out.mkdir(parents=True,exist_ok=True)
 run([java_tool('javac'),'-proc:none','-cp',cp,'-d',str(out),str(ROOT/'tools/AcademyRuntimeCheck.java')])
 run([java_tool('java'),'-Xmx1G','-cp',str(out)+os.pathsep+cp,'AcademyRuntimeCheck'])
