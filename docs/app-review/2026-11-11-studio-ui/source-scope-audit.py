from pathlib import Path
import subprocess,json,re,hashlib,datetime
R=Path('/workspace/runmoni');E=R/'docs/app-review/2026-11-11-studio-ui';MAPS=('productSources','testSources','resources','rendererAssets','approvedSourceCopies','configurationSources')
sha=lambda x:hashlib.sha256(x if isinstance(x,bytes) else x.read_bytes()).hexdigest()
records=[('android-review1.json','f0c6601',False,53),('android-review2.json','8a50228',True,13),('native1.json','8a50228',False,9),('android-review3.json','030261a',True,2),('native2.json','030261a',True,4)]
method_observations={};observed=[]
for name,revision,overall,count in records:
 d=json.loads((E/name).read_text());b=json.loads((E/d['binaryReference']).read_text());C=subprocess.check_output(['git','rev-parse',revision],cwd=R,text=True).strip();classes={}
 for path,expected in b['testSources'].items():
  raw=subprocess.check_output(['git','show',C+':'+path],cwd=R);assert sha(raw)==expected,(name,path)
  s=raw.decode();starts=list(re.finditer(r'^class (\w+)\b',s,re.M))
  package=re.search(r'^package\s+([\w.]+)',s,re.M)[1]
  for i,m in enumerate(starts):classes[package+'.'+m[1]]=re.findall(r'@Test\s+fun\s+(\w+)',s[m.start():starts[i+1].start() if i+1<len(starts) else len(s)])
 assert d['completed'] and d['passed'] is overall and d['actualPassedProductMethods']==count
 assert sha(E/d['binaryReference'])==d['binaryReferenceSha256']
 expected_installed=[{'package':p,'sha256':entry['sha256']} for p,entry in zip(('com.runmoni.app','com.runmoni.app.test'),b['binaries'])]
 assert sorted(d['installed'],key=lambda x:x['package'])==sorted(expected_installed,key=lambda x:x['package'])
 fields=('sourceMapsBefore',) if name=='android-review1.json' else ('sourceMapsBefore','sourceMapsAfter','sourceMapsAfterRestoration')
 for field in fields:
  assert set(d[field])==set(MAPS)
  for k in MAPS:assert d[field][k]=={'fileCount':len(b[k]),'matches':True,'mismatches':[]}
 n=0
 for g in d['groups']:
  if not g['passed']:continue
  raw=(E/g['raw']).read_bytes();assert sha(raw)==g['rawSha256'] and re.search(r'OK \('+str(g['actual'])+r' tests?\)',raw.decode()) and 'FAILURES!!!' not in raw.decode()
  ids=[]
  for selector in g['classes']:
   clazz,*specific=selector.split('#');names=specific or classes[clazz]
   if specific:assert all(m in classes[clazz] for m in names),(name,selector,classes[clazz])
   ids.extend(clazz+'#'+m for m in names)
  assert len(ids)==g['expected']==g['actual'];n+=len(ids)
  for method in ids:method_observations[method]={'proof':name,'group':g['name']}
 assert n==count
 observed.append({'proof':name,'sha256':sha(E/name),'binaryReference':d['binaryReference'],'matchingTestBlobCommit':C,'allFrozenTestSourceBlobsMatched':True,'sourceSnapshotFieldsActuallyChecked':list(fields),'installedPairMatches':True,'passedGroupMethods':count,'parentOverallPassed':overall})
scope=json.loads((E/'android-scope-summary.json').read_text());rows=scope['methods'];assert len(rows)==len(method_observations)==70 and len({r['method'] for r in rows})==70
for row in rows:assert method_observations[row['method']]=={'proof':row['proof'],'group':row['group']}
assert sum(r['freshOnFinalLocalBinary'] for r in rows)==6 and scope['retainedEarlierAndroid']['total']==64
post=json.loads((E/'source1-post-review-observation.json').read_text());assert post['allSixSourceMapsMatchFrozenInput'] and post['parentProof']['sha256']==sha(E/'android-review1.json')
script=E/'source-scope-audit.py';assert not script.exists();script.write_bytes(Path(__file__).read_bytes())
out=E/'scope-root-audit.json';assert not out.exists();out.write_text(json.dumps({'observedAtUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'source':script.name,'sourceSha256':sha(script),'scope':{'file':'android-scope-summary.json','sha256':sha(E/'android-scope-summary.json')},'actualSelectorsAndMatchingFrozenSourceMethodsCrossChecked':True,'originalScopeNotRewritten':True,'freshFinal':6,'retainedEarlier':64,'distinct':70,'observations':observed,'source1MissingAfterSnapshotFabricated':False,'source1SeparatePostObservationPreserved':True,'physicalDeviceVerified':False,'googleLoginVerified':False},ensure_ascii=False,indent=2)+'\n');print('Verified6/64/70 against exact frozen test blobs, installed APK pairs and actual source snapshots.')
