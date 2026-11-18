#!/usr/bin/env python3
"""ROOT ONLY, future execution: run real Node checks bound to a prepared staged tree.
Import performs no operation. Never fills a future commit/count/time by default.
"""
from pathlib import Path
import argparse,datetime,hashlib,json,os,re,subprocess
R=Path('/workspace/runmoni');W=R/'work/expressive-refresh';E=R/'docs/app-review/2026-11-17-expressive-refresh';B=Path('/workspace/mydashboard')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def utc():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def git(*args):return subprocess.run(['git',*args],cwd=B,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True).stdout.decode().strip()
def run(a):
 assert re.fullmatch(r'node-tests[1-9][0-9]*\.json',a.output)
 out=E/a.output;raw=E/(out.stem+'.txt');assert not out.exists() and not raw.exists()
 plan=a.plan.resolve(strict=True);assert plan.is_relative_to(E.resolve());p=json.loads(plan.read_text());assert p['mode']=='apply' and p['dashboardWritten'] is True
 assert git('diff','--name-only')=='' and git('ls-files','--others','--exclude-standard')=='','Stage all prepared dashboard changes before testing'
 head,tree=git('rev-parse','HEAD'),git('write-tree');assert re.fullmatch('[0-9a-f]{40}',head) and re.fullmatch('[0-9a-f]{40}',tree)
 project=next(x for x in json.loads((B/'src/lib/projects.json').read_text()) if x['slug']=='runmoni');assert project==p['project']
 paths=['src/lib/projects.json','scripts/update-project-apk.mjs','package.json','package-lock.json']+['public/'+s['src'].lstrip('/') for s in project['screenshots']]
 before={x:sha(B/x) for x in paths};plan_sha=sha(plan)
 value={'command':['npm','test'],'cwd':str(B),'startedAtUtc':utc(),'testedHeadRevision':head,'testedIndexTreeOidBefore':tree,'preparedPlan':{'file':plan.name,'sha256':plan_sha},'catalogSha256':before['src/lib/projects.json'],'updaterSha256':before['scripts/update-project-apk.mjs'],'gallerySha256':{x:before[x] for x in paths if x.startswith('public/')},'inputFileHashes':before,'nodeVersion':subprocess.run(['node','--version'],cwd=B,capture_output=True,text=True,check=True).stdout.strip(),'executedDriverSha256':sha(Path(__file__))}
 with raw.open('x') as f:
  proc=subprocess.run(value['command'],cwd=B,stdin=subprocess.DEVNULL,stdout=f,stderr=subprocess.STDOUT);f.flush();os.fsync(f.fileno())
 value.update(exitCode=proc.returncode,finishedAtUtc=utc(),rawLog=raw.name,rawSha256=sha(raw),testedIndexTreeOidAfter=git('write-tree'))
 value['testedFilesMatchIndex']=(git('diff','--name-only')=='' and git('ls-files','--others','--exclude-standard')=='' and git('rev-parse','HEAD')==head and value['testedIndexTreeOidAfter']==tree and all(sha(B/x)==h for x,h in before.items()) and sha(plan)==plan_sha)
 text=raw.read_text()
 for dest,key in [('tests','tests'),('passed','pass'),('failed','fail'),('skipped','skipped')]:
  matches=re.findall(r'^(?:#|ℹ) '+key+r' (\d+)\s*$',text,re.M);assert len(matches)==1,(key,'exact actual TAP or Node spec summary required');value[dest]=int(matches[0])
 with out.open('x') as f:json.dump(value,f,ensure_ascii=False,indent=2);f.write('\n')
 assert value['exitCode']==0 and value['tests']==value['passed']>0 and value['failed']==value['skipped']==0 and value['testedFilesMatchIndex']
 print(json.dumps({'file':out.name,'tests':value['tests'],'passed':value['passed'],'testedTree':tree,'treeUnchanged':True}))
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',type=Path,required=True);p.add_argument('--output',required=True);run(p.parse_args())
