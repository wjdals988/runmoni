import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {before, beforeEach, after, test} from 'node:test';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';
import {collection, doc, getDoc, getDocs, query, limit, setDoc, updateDoc, deleteDoc,
  writeBatch, runTransaction, serverTimestamp, Timestamp} from 'firebase/firestore';

let env;
const zero=Timestamp.fromMillis(0);
const db=uid=>uid ? env.authenticatedContext(uid,{firebase:{sign_in_provider:'anonymous'}}).firestore() : env.unauthenticatedContext().firestore();
const profile=(d,uid)=>doc(d,'communityProfiles',uid);
const claim=(d,key)=>doc(d,'communityNicknames',key);
const key=name=>name.normalize('NFC').trim().replace(/[\s\p{Z}]+/gu,' ').toLowerCase();
const fields=(nickname,overrides={})=>({nickname,createdAt:serverTimestamp(),updatedAt:serverTimestamp(),joinedRoomIds:[],lastRoomId:'',createdRoomCount:0,lastRoomCreatedAt:zero,...overrides});

before(async()=>{
 const endpoint=process.env.FIRESTORE_EMULATOR_HOST??'127.0.0.1:8080';
 assert.match(endpoint,/^(127\.0\.0\.1|localhost):\d+$/);
 const [host,port]=endpoint.split(':');
 env=await initializeTestEnvironment({projectId:'demo-runmoni',firestore:{host,port:Number(port),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
});
beforeEach(async()=>env.clearFirestore());
after(async()=>env?.cleanup());

async function connect(uid,nickname=uid){
 const d=db(uid), b=writeBatch(d), canonical=key(nickname);
 b.set(profile(d,uid),fields(nickname,{nicknameKey:canonical}));
 b.set(claim(d,canonical),{uid,nickname,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
 await b.commit();return d;
}
async function rename(d,uid,nickname,{release=true}={}){
 return runTransaction(d,async tx=>{
  const p=await tx.get(profile(d,uid)), canonical=key(nickname), next=claim(d,canonical), nextDoc=await tx.get(next);
  const previousKey=p.data().nicknameKey;
  const old=previousKey&&previousKey!==canonical ? claim(d,previousKey) : null;
  const oldDoc=old ? await tx.get(old) : null;
  if(nextDoc.exists()&&nextDoc.data().uid!==uid) throw new Error('Nickname already reserved');
  tx.update(profile(d,uid),{nickname,nicknameKey:canonical,updatedAt:serverTimestamp()});
  if(nextDoc.exists()) tx.update(next,{nickname,updatedAt:serverTimestamp()});
  else tx.set(next,{uid,nickname,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  if(release&&oldDoc?.exists()&&oldDoc.data().uid===uid) tx.delete(old);
 });
}

test('profile and normalized nickname reservation must be created in the same atomic operation',async()=>{
 const d=db('alice');
 await assertFails(setDoc(profile(d,'alice'),fields('Alice',{nicknameKey:'alice'})));
 await assertFails(setDoc(claim(d,'alice'),{uid:'alice',nickname:'Alice',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
 await assertSucceeds(connect('alice','Alice'));
 assert.equal((await getDoc(profile(d,'alice'))).data().nicknameKey,'alice');
 assert.equal((await getDoc(claim(d,'alice'))).data().uid,'alice');
});

test('availability can be read by exact authenticated key but private profile/directory reads stay denied',async()=>{
 const a=await connect('alice','Alice'), b=db('bobby'), guest=db();
 await assertSucceeds(getDoc(claim(b,'alice')));
 await assertFails(getDoc(claim(guest,'alice')));
 await assertFails(getDocs(query(collection(a,'communityNicknames'),limit(1))));
 await assertFails(getDoc(profile(b,'alice')));
});

test('two clients that observed availability cannot both reserve the same case-folded name',async()=>{
 const a=await connect('alice'), b=await connect('bobby');
 assert.equal((await getDoc(claim(a,'teamrunner'))).exists(),false);
 assert.equal((await getDoc(claim(b,'teamrunner'))).exists(),false);
 const results=await Promise.allSettled([rename(a,'alice','TeamRunner'),rename(b,'bobby','TEAMRUNNER')]);
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 const owner=(await getDoc(claim(a,'teamrunner'))).data().uid;
 assert.ok(['alice','bobby'].includes(owner));
 const other=owner==='alice'?'bobby':'alice';
 assert.equal((await getDoc(profile(db(other),other))).data().nickname,other);
});

test('rename atomically releases the old reservation and preserves UID/profile metadata',async()=>{
 const a=await connect('alice','Alice');
 const before=(await getDoc(profile(a,'alice'))).data();
 await assertFails(rename(a,'alice','새러너',{release:false}));
 assert.equal((await getDoc(profile(a,'alice'))).data().nickname,'Alice');
 await assertSucceeds(rename(a,'alice','새러너'));
 const after=(await getDoc(profile(a,'alice'))).data();
 assert.equal(after.createdAt.toMillis(),before.createdAt.toMillis());
 assert.deepEqual(after.joinedRoomIds,before.joinedRoomIds);
 assert.equal(after.createdRoomCount,before.createdRoomCount);
 assert.equal((await getDoc(claim(a,'alice'))).exists(),false);
 assert.equal((await getDoc(claim(a,'새러너'))).data().uid,'alice');
});

test('case-only display edit keeps the same reservation and immutable creation time',async()=>{
 const a=await connect('alice','Alice'), old=(await getDoc(claim(a,'alice'))).data();
 await assertSucceeds(rename(a,'alice','ALICE'));
 const current=(await getDoc(claim(a,'alice'))).data();
 assert.equal(current.uid,'alice');
 assert.equal(current.nickname,'ALICE');
 assert.equal(current.createdAt.toMillis(),old.createdAt.toMillis());
});

test('other clients cannot overwrite, steal, or release a reservation',async()=>{
 await connect('alice','Alice');const b=await connect('bobby');
 await assertFails(updateDoc(claim(b,'alice'),{uid:'bobby',updatedAt:serverTimestamp()}));
 await assertFails(deleteDoc(claim(b,'alice')));
 await assertFails(setDoc(profile(b,'alice'),fields('Bobby',{nicknameKey:'bobby'})));
 // Bypass the helper's client-side "already reserved" exception: this
 // security test must send the malicious atomic write to the rules engine.
 const steal=writeBatch(b);
 steal.update(profile(b,'bobby'),{nickname:'ALICE',nicknameKey:'alice',updatedAt:serverTimestamp()});
 steal.update(claim(b,'alice'),{uid:'bobby',nickname:'ALICE',updatedAt:serverTimestamp()});
 steal.delete(claim(b,'bobby'));
 await assertFails(steal.commit());
 assert.equal((await getDoc(profile(b,'bobby'))).data().nickname,'bobby');
 assert.equal((await getDoc(claim(b,'alice'))).data().uid,'alice');
 assert.equal((await getDoc(claim(b,'bobby'))).data().uid,'bobby');
});

test('reservation owner cannot release a live name or keep multiple reserved aliases',async()=>{
 const a=await connect('alice');
 await assertFails(deleteDoc(claim(a,'alice')));
 await assertFails(setDoc(claim(a,'second'),{uid:'alice',nickname:'Second',createdAt:serverTimestamp(),updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(profile(a,'alice'),{nickname:'Second',updatedAt:serverTimestamp()}));
});

for(const [label,nickname,canonical] of [
 ['case mismatched key','Alice','ALICE'],['leading space',' Alice','alice'],['trailing space','Alice ','alice'],
 ['doubled space','Alice  Runner','alice runner'],['uncomposed Hangul','\u1105\u1165\u1102\u1165','\u1105\u1165\u1102\u1165'],
 ['new name over sixteen','abcdefghijklmnopq','abcdefghijklmnopq'],['format character','Al\u200bice','al\u200bice'],['punctuation only','--','--']
]){
 test(`noncanonical reservation rejected: ${label}`,async()=>{
  const d=db('alice'),b=writeBatch(d);
  b.set(profile(d,'alice'),fields(nickname,{nicknameKey:canonical}));
  b.set(claim(d,canonical),{uid:'alice',nickname,createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
  await assertFails(b.commit());
 });
}

test('reservation cannot acquire wallet, location, or arbitrary identity fields',async()=>{
 const a=await connect('alice');
 for(const extra of [{mileage:99},{latitude:37.5},{admin:true}]){
  await assertFails(updateDoc(claim(a,'alice'),{...extra,updatedAt:serverTimestamp()}));
 }
 await assertFails(updateDoc(claim(a,'alice'),{createdAt:zero,updatedAt:serverTimestamp()}));
});

test('legacy long name stays readable/usable until explicit claim without changing crew ownership',async()=>{
 const uid='legacy',oldName='기존러너'.repeat(5),rid='L'.repeat(20);
 await env.withSecurityRulesDisabled(async context=>{
  const d=context.firestore();
  await setDoc(profile(d,uid),fields(oldName,{createdAt:zero,updatedAt:zero,joinedRoomIds:[rid],lastRoomId:rid,createdRoomCount:1}));
  await setDoc(doc(d,'communityRooms',rid),{kind:'crew',visibility:'open',ownerUid:uid,title:'기존 크루',description:'',region:'서울',paceLabel:'자유',capacity:100,memberCount:1,
    createdAt:zero,updatedAt:zero,crewId:'',partyKind:'',startsAtMillis:0,meetingLabel:'',distanceKm:0,status:'active'});
  await setDoc(doc(d,'communityRooms',rid,'members',uid),{uid,nickname:oldName,role:'owner'});
 });
 const d=db(uid);
 await assertSucceeds(getDoc(profile(d,uid)));
 await assertSucceeds(updateDoc(profile(d,uid),{updatedAt:serverTimestamp()}));
 await assertSucceeds(rename(d,uid,'기존러너'));
 const p=(await getDoc(profile(d,uid))).data();
 assert.deepEqual(p.joinedRoomIds,[rid]);assert.equal(p.createdRoomCount,1);
 assert.equal((await getDoc(doc(d,'communityRooms',rid))).data().ownerUid,uid);
 assert.equal((await getDoc(doc(d,'communityRooms',rid,'members',uid))).data().role,'owner');
 assert.equal((await getDoc(claim(d,'기존러너'))).data().uid,uid);
});
