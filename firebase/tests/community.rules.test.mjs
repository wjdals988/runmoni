import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {before, beforeEach, after, test} from 'node:test';
import {initializeTestEnvironment, assertSucceeds, assertFails} from '@firebase/rules-unit-testing';
import {collection, doc, getDoc, getDocs, query, where, orderBy, limit, setDoc, updateDoc,
  deleteDoc, writeBatch, arrayUnion, arrayRemove, increment, serverTimestamp, Timestamp, runTransaction} from 'firebase/firestore';

let env;
const RID='A'.repeat(20), PID='B'.repeat(20);
const zero=Timestamp.fromMillis(0);
const db=(uid)=>uid ? env.authenticatedContext(uid,{firebase:{sign_in_provider:'anonymous'}}).firestore() : env.unauthenticatedContext().firestore();
const profile=(d,u)=>doc(d,'communityProfiles',u);
const room=(d,id=RID)=>doc(d,'communityRooms',id);
const member=(d,u,id=RID)=>doc(d,'communityRooms',id,'members',u);
const message=(d,id='M'.repeat(20),rid=RID)=>doc(d,'communityRooms',rid,'messages',id);
before(async()=>{
  const endpoint=process.env.FIRESTORE_EMULATOR_HOST??'127.0.0.1:8080';
  assert.match(endpoint,/^(127\.0\.0\.1|localhost):\d+$/);
  const [host,port]=endpoint.split(':');
  env=await initializeTestEnvironment({projectId:'demo-runmoni',firestore:{host,port:Number(port),rules:await readFile(new URL('../firestore.rules',import.meta.url),'utf8')}});
});
beforeEach(async()=>env.clearFirestore());
after(async()=>env?.cleanup());
async function connect(u){
  const d=db(u);
  await setDoc(profile(d,u),{nickname:u,createdAt:serverTimestamp(),updatedAt:serverTimestamp(),joinedRoomIds:[],lastRoomId:'',createdRoomCount:0,lastRoomCreatedAt:zero});
  return d;
}
function roomData(u,overrides={}){
 return {kind:'crew',visibility:'open',ownerUid:u,title:'공개 러닝 방',description:'안전한 테스트',region:'서울',paceLabel:'자유 페이스',capacity:100,memberCount:1,
   createdAt:serverTimestamp(),updatedAt:serverTimestamp(),crewId:'',partyKind:'',startsAtMillis:0,meetingLabel:'',distanceKm:0,status:'active',...overrides};
}
function memberData(u,role='member'){
 return {uid:u,nickname:u,role,joinedAt:serverTimestamp(),lastMessageAt:zero,lastMessageId:''};
}
async function create(d,u,id=RID,overrides={}){
 const b=writeBatch(d);
 b.set(room(d,id),roomData(u,overrides));
 b.set(member(d,u,id),memberData(u,'owner'));
 b.update(profile(d,u),{joinedRoomIds:arrayUnion(id),lastRoomId:id,updatedAt:serverTimestamp(),createdRoomCount:increment(1),lastRoomCreatedAt:serverTimestamp()});
 return b.commit();
}
async function join(d,u,id=RID){
 return runTransaction(d,async tx=>{
  const r=await tx.get(room(d,id));
  tx.set(member(d,u,id),memberData(u));
  tx.update(room(d,id),{memberCount:r.data().memberCount+1,updatedAt:serverTimestamp()});
  tx.update(profile(d,u),{joinedRoomIds:arrayUnion(id),lastRoomId:id,updatedAt:serverTimestamp()});
 });
}
async function leave(d,u,id=RID){
 const b=writeBatch(d);
 b.delete(member(d,u,id));
 b.update(room(d,id),{memberCount:increment(-1),updatedAt:serverTimestamp()});
 b.update(profile(d,u),{joinedRoomIds:arrayRemove(id),lastRoomId:id,updatedAt:serverTimestamp()});
 return b.commit();
}
async function send(d,u,{id='M'.repeat(20),notice=false,body='함께 달려요',rid=RID,nickname=u}={}){
 const b=writeBatch(d);
 b.set(message(d,id,rid),{authorUid:u,nickname,body,kind:notice?'announcement':'chat',createdAt:serverTimestamp()});
 b.update(member(d,u,rid),{nickname,lastMessageAt:serverTimestamp(),lastMessageId:id});
 return b.commit();
}
async function basic(){const a=await connect('alice'),b=await connect('bobby');await create(a,'alice');return {a,b};}

test('public bounded discovery works; private profiles/members/chats and unbounded lists do not',async()=>{
 const {a}=await basic();const publicDb=db();
 await assertSucceeds(getDocs(query(collection(publicDb,'communityRooms'),where('visibility','==','open'),where('kind','==','crew'),orderBy('createdAt','desc'),limit(50))));
 await assertFails(getDocs(collection(publicDb,'communityRooms')));
 await assertFails(getDocs(query(collection(publicDb,'communityRooms'),limit(51))));
 for(const ref of [profile(publicDb,'alice'),member(publicDb,'alice'),message(publicDb)]) await assertFails(getDoc(ref));
 await assertFails(getDocs(query(collection(a,'communityProfiles'),limit(1))));
 await assertFails(getDoc(profile(db('bobby'),'alice')));
 for(const ref of [room(publicDb),profile(publicDb,'alice'),member(publicDb,'alice'),message(publicDb)]) await assertFails(setDoc(ref,{attempted:true}));
});
test('create, join and leave preserve metadata/member/private-link counts atomically',async()=>{
 const {a,b}=await basic();await assertSucceeds(join(b,'bobby'));
 assert.equal((await getDoc(room(a))).data().memberCount,2);
 assert.deepEqual((await getDoc(profile(b,'bobby'))).data().joinedRoomIds,[RID]);
 await assertSucceeds(leave(b,'bobby'));
 assert.equal((await getDoc(room(a))).data().memberCount,1);
 assert.deepEqual((await getDoc(profile(b,'bobby'))).data().joinedRoomIds,[]);
 await assertFails(getDocs(query(collection(b,'communityRooms',RID,'messages'),limit(50))));
});
test('owner cannot leave or delete a room; members cannot forge owner or counters',async()=>{
 const {a,b}=await basic();await assertFails(leave(a,'alice'));
 await assertFails(deleteDoc(room(a)));
 await assertFails(setDoc(member(b,'bobby'),memberData('bobby','owner')));
 await assertFails(updateDoc(room(b),{memberCount:2,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(profile(b,'bobby'),{joinedRoomIds:[RID],lastRoomId:RID,updatedAt:serverTimestamp()}));
});
test('incomplete membership batches cannot change capacity/counts or bypass private links',async()=>{
 const {a,b}=await basic();
 const batch=writeBatch(b);batch.set(member(b,'bobby'),memberData('bobby'));batch.update(room(b),{memberCount:2,updatedAt:serverTimestamp()});
 await assertFails(batch.commit());
 await join(b,'bobby');
 const leaveBatch=writeBatch(b);leaveBatch.update(room(b),{memberCount:1,updatedAt:serverTimestamp()});leaveBatch.update(profile(b,'bobby'),{joinedRoomIds:[],lastRoomId:RID,updatedAt:serverTimestamp()});
 await assertFails(leaveBatch.commit());
 await assertFails(updateDoc(room(a),{capacity:200,updatedAt:serverTimestamp()}));
});
test('parallel attempts for the last party slot cannot overfill it',async()=>{
 const a=await connect('alice'),b=await connect('bobby'),c=await connect('carol');
 await create(a,'alice',PID,{kind:'party',partyKind:'remote',startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:2});
 const results=await Promise.allSettled([join(b,'bobby',PID),join(c,'carol',PID)]);
 assert.equal(results.filter(r=>r.status==='fulfilled').length,1);
 assert.equal((await getDoc(room(a,PID))).data().memberCount,2);
});
test('crew-linked parties require membership; cancellation prevents new joins',async()=>{
 const {a,b}=await basic();
 await assertFails(create(b,'bobby',PID,{kind:'party',partyKind:'remote',crewId:RID,startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3}));
 await join(b,'bobby');
 await assertSucceeds(create(b,'bobby',PID,{kind:'party',partyKind:'remote',crewId:RID,startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3}));
 await assertSucceeds(updateDoc(room(a,PID),{status:'cancelled',updatedAt:serverTimestamp()}));
 await assertFails(join(a,'alice',PID));
});
test('only a room manager can assign DJ; roles do not grant owner/wallet authority',async()=>{
 const {a,b}=await basic();await join(b,'bobby');
 await assertFails(updateDoc(member(b,'bobby'),{role:'dj'}));
 await assertFails(updateDoc(member(a,'bobby'),{role:'owner'}));
 await assertSucceeds(updateDoc(member(a,'bobby'),{role:'dj'}));
 await assertFails(updateDoc(member(a,'alice'),{role:'member'}));
 await assertFails(setDoc(doc(b,'wallets','bobby'),{balance:999999}));
});
test('member messages arrive only with atomic throttle update and matching author',async()=>{
 const {a,b}=await basic();await join(b,'bobby');
 await assertSucceeds(send(b,'bobby'));
 await assertSucceeds(getDocs(query(collection(a,'communityRooms',RID,'messages'),orderBy('createdAt','desc'),limit(50))));
 await assertFails(send(b,'bobby',{id:'N'.repeat(20)}));
 await assertFails(setDoc(message(b,'Q'.repeat(20)),{authorUid:'bobby',nickname:'bobby',body:'bypass throttle',kind:'chat',createdAt:serverTimestamp()}));
 await assertFails(send(a,'alice',{nickname:'bobby'}));
 await assertFails(send(b,'alice'));
 await assertFails(updateDoc(message(b),{body:'rewrite'}));
 await assertFails(deleteDoc(message(a)));
});
test('member cannot post announcements; owner and assigned DJ can',async()=>{
 const {a,b}=await basic();await join(b,'bobby');
 await assertFails(send(b,'bobby',{notice:true}));
 await assertSucceeds(send(a,'alice',{notice:true}));
 await updateDoc(member(a,'bobby'),{role:'dj'});
 await assertSucceeds(send(b,'bobby',{id:'N'.repeat(20),notice:true}));
});
test('members cannot falsify nickname or reset throttle without a corresponding message',async()=>{
 const {a,b}=await basic();await join(b,'bobby');
 await assertFails(updateDoc(member(b,'bobby'),{nickname:'alice'}));
 await assertFails(updateDoc(member(b,'bobby'),{lastMessageAt:serverTimestamp(),lastMessageId:'Z'.repeat(20)}));
 await updateDoc(profile(b,'bobby'),{nickname:'새이름',updatedAt:serverTimestamp()});
 await assertSucceeds(send(b,'bobby',{nickname:'새이름'}));
});
for(const [name,override] of [ ['extra wallet field',{mileage:99999}],['private visibility',{visibility:'private'}],['owner spoof',{ownerUid:'bobby'}],
 ['invalid capacity',{capacity:101}],['invalid count',{memberCount:3}],['unknown type',{kind:'other'}],['infinite distance',{distanceKm:Infinity}],
 ['extra GPS',{latitude:37.5}],['stale timestamp',{updatedAt:zero}] ]) {
 test(`invalid room data rejected: ${name}`,async()=>{const a=await connect('alice');await assertFails(create(a,'alice',RID,override));});
}
test('profile fields are bounded and cannot claim wallet, routes or someone else identity',async()=>{
 const a=await connect('alice');
 await assertFails(updateDoc(profile(a,'alice'),{wallet:99999,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(profile(a,'alice'),{joinedRoomIds:Array(101).fill(RID),updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(profile(a,'alice'),{createdRoomCount:1,lastRoomCreatedAt:serverTimestamp(),updatedAt:serverTimestamp()}));
 await assertFails(setDoc(profile(db('bobby'),'alice'),{nickname:'bobby'}));
 await assertFails(deleteDoc(profile(a,'alice')));
});
test('profile creation budget and interval are enforced by rules',async()=>{
 const a=await connect('alice');await create(a,'alice');
 await assertFails(create(a,'alice',PID));
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(profile(ctx.firestore(),'alice'),{createdRoomCount:20,lastRoomCreatedAt:zero}));
 await assertFails(create(a,'alice',PID));
});
