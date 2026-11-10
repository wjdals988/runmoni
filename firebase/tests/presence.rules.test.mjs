import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {before,beforeEach,after,test} from 'node:test';
import {initializeTestEnvironment,assertSucceeds,assertFails} from '@firebase/rules-unit-testing';
import {doc,collection,setDoc,getDoc,getDocs,deleteDoc,writeBatch,runTransaction,query,where,orderBy,limit,serverTimestamp,Timestamp} from 'firebase/firestore';

let env;
const token='presence-session-aaaaaaaa', nextToken='presence-session-bbbbbbbb';
const db=uid=>uid?env.authenticatedContext(uid,{firebase:{sign_in_provider:'anonymous'}}).firestore():env.unauthenticatedContext().firestore();
const marker=(d,u='alice')=>doc(d,'runnerPresence',u);
const consent=(d,u='alice')=>doc(d,'runnerConsents',u);
const avatar=()=>({hair:0,headwear:0,face:0,top:0,bottom:0,costume:0,socks:0,shoes:0,ear:0,eyewear:0,wrist:0,waist:0,hydration:0,gender:'female',colors:{top:0,bottom:1,shoes:2}});
const data=(u='alice',changes={})=>({runSessionToken:token,latitudeE3:37567,longitudeE3:126978,latBand:37,lonBand:126,nickname:u,avatar:avatar(),visibility:'approximate',adultConfirmed:true,updatedAt:serverTimestamp(),expiresAt:Timestamp.fromMillis(Date.now()+60000),...changes});
const scoped=(d,expires=Date.now()+30000,n=100)=>query(collection(d,'runnerPresence'),where('latBand','==',37),where('lonBand','==',126),where('expiresAt','>',Timestamp.fromMillis(expires)),orderBy('expiresAt'),limit(n));
before(async()=>{
 const endpoint=process.env.FIRESTORE_EMULATOR_HOST??'127.0.0.1:8080';assert.match(endpoint,/^(127\.0\.0\.1|localhost):\d+$/);const [host,port]=endpoint.split(':');
 env=await initializeTestEnvironment({projectId:'demo-runmoni',firestore:{host,port:Number(port),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async ctx=>{
  for(const u of ['alice','bobby'])await setDoc(doc(ctx.firestore(),'communityProfiles',u),{nickname:u,createdAt:Timestamp.now(),updatedAt:Timestamp.now(),joinedRoomIds:[],lastRoomId:'',createdRoomCount:0,lastRoomCreatedAt:Timestamp.fromMillis(0)});
 });
});
after(async()=>env?.cleanup());
async function enable(d,u='alice',value=token){await setDoc(consent(d,u),{runSessionToken:value,enabled:true,updatedAt:serverTimestamp()});}
async function share(d,u='alice',changes={}){await enable(d,u);await setDoc(marker(d,u),data(u,changes));}

test('approximate opt-in presence is readable only to connected profiles with a finite future-expiry query',async()=>{
 const a=db('alice'),b=db('bobby');await assertSucceeds(share(a));
 const result=await assertSucceeds(getDocs(scoped(b)));assert.equal(result.size,1);assert.equal(result.docs[0].data().latitudeE3,37567);
 await assertFails(getDocs(scoped(db())));await assertFails(getDocs(scoped(db('unconnected'))));
 await assertFails(getDocs(collection(b,'runnerPresence')));await assertFails(getDocs(scoped(b,Date.now()+30000,101)));
});
test('get and list cannot expose expired markers even when the free plan retains their physical document',async()=>{
 await env.withSecurityRulesDisabled(async ctx=>setDoc(marker(ctx.firestore()),data('alice',{updatedAt:Timestamp.fromMillis(Date.now()-90000),expiresAt:Timestamp.fromMillis(Date.now()-30000)})));
 await assertFails(getDoc(marker(db('bobby'))));await assertFails(getDocs(query(collection(db('bobby'),'runnerPresence'),limit(100))));
 assert.equal((await assertSucceeds(getDocs(scoped(db('bobby'))))).empty,true);
 await assertSucceeds(getDoc(marker(db('alice'))));await assertSucceeds(deleteDoc(marker(db('alice'))));
});
test('consent is private and owner-only; no profile cannot enable publishing',async()=>{
 const a=db('alice');await enable(a);await assertSucceeds(getDoc(consent(a)));
 await assertFails(getDoc(consent(db('bobby'))));await assertFails(getDocs(query(collection(a,'runnerConsents'),limit(1))));
 await assertFails(setDoc(consent(db('bobby')),{runSessionToken:token,enabled:true,updatedAt:serverTimestamp()}));
 await assertFails(enable(db('unconnected'),'unconnected'));
});
test('single batch may enable its own consent and publish but cannot create another UID marker',async()=>{
 const a=db('alice');const batch=writeBatch(a);batch.set(consent(a),{runSessionToken:token,enabled:true,updatedAt:serverTimestamp()});batch.set(marker(a),data());await assertSucceeds(batch.commit());
 await assertFails(setDoc(marker(a,'bobby'),data('bobby')));await assertFails(deleteDoc(marker(db('bobby'))));
});
test('revocation atomically removes marker and rejects a late old-token publish',async()=>{
 const a=db('alice');await share(a);const batch=writeBatch(a);batch.set(consent(a),{runSessionToken:token,enabled:false,updatedAt:serverTimestamp()});batch.delete(marker(a));await assertSucceeds(batch.commit());
 await assertFails(setDoc(marker(a),data()));await assertFails(setDoc(marker(a),data('alice',{runSessionToken:nextToken})));
 assert.equal((await getDoc(marker(a))).exists(),false);
});
test('a new explicit session rejects a delayed publication from the previous token',async()=>{
 const a=db('alice');await enable(a,'alice',nextToken);await assertFails(setDoc(marker(a),data()));await assertSucceeds(setDoc(marker(a),data('alice',{runSessionToken:nextToken})));
 await assertFails(setDoc(consent(a),{runSessionToken:token,enabled:false,updatedAt:serverTimestamp()}));
});
for(const [label,overrides] of [
 ['precise coordinates',{latitude:37.566535,longitude:126.978132}],['route history',{points:[]}],['wallet or ownership',{mileage:99999}],
 ['fractional cell',{latitudeE3:37567.123}],['wrong latitude band',{latBand:38}],['wrong longitude band',{lonBand:127}],
 ['out of range',{longitudeE3:180001}],['exact visibility',{visibility:'precise'}],['missing adult self-confirmation',{adultConfirmed:false}],
 ['nickname spoof',{nickname:'bobby'}],['unbounded expiry',{expiresAt:Timestamp.fromMillis(Date.now()+300000)}],
 ['already expired',{expiresAt:Timestamp.fromMillis(0)}],['nonserver timestamp',{updatedAt:Timestamp.fromMillis(0)}],
 ['unknown avatar field',{avatar:{...avatar(),points:100}}],['invalid cosmetic',{avatar:{...avatar(),hair:99}}],
 ['invalid palette',{avatar:{...avatar(),colors:{top:99,bottom:0,shoes:0}}}],
])test(`presence rejects ${label}`,async()=>{const a=db('alice');await enable(a);await assertFails(setDoc(marker(a),data('alice',overrides)));});

test('a query that includes expired documents is rejected rather than filtering private expiry client-side',async()=>{
 const a=db('alice'),b=db('bobby');await share(a);
 await assertFails(getDocs(query(collection(b,'runnerPresence'),where('latBand','==',37),where('lonBand','==',126),orderBy('expiresAt'),limit(100))));
 await assertFails(getDocs(scoped(b,0)));
});


test('revoking a prepared old enable touches the read version without disabling another token', {timeout:15000}, async()=>{
 const a=db('alice');const previous='previous-session-cccccccc';
 await setDoc(consent(a),{runSessionToken:previous,enabled:false,updatedAt:serverTimestamp()});
 let active=true,release,observed;let attempts=0;
 const seen=new Promise(resolve=>{observed=resolve;});const continueCommit=new Promise(resolve=>{release=resolve;});
 const pending=runTransaction(a,async tx=>{
  if(!active)throw new Error('locally revoked');
  await tx.get(consent(a));
  if(!active)throw new Error('locally revoked');
  tx.set(consent(a),{runSessionToken:token,enabled:true,updatedAt:serverTimestamp()});
  if(++attempts===1){observed();await continueCommit;}
 });
 pending.catch(()=>{});
 await seen;active=false;
 try {
  await assertSucceeds(setDoc(consent(a),{runSessionToken:previous,enabled:false,updatedAt:serverTimestamp()}));
 } finally { release(); }
 await assert.rejects(pending,/locally revoked/);
 const authority=(await getDoc(consent(a))).data();assert.equal(authority.runSessionToken,previous);assert.equal(authority.enabled,false);
 assert.ok(attempts===1); // The retry rejects before preparing another write.
});
