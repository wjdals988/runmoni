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
async function cooldown(u){
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(profile(ctx.firestore(),u),{lastRoomCreatedAt:zero}));
}
async function linkedParty(){
 const {a,b}=await basic();await join(b,'bobby');await cooldown('alice');
 await create(a,'alice',PID,{kind:'party',partyKind:'remote',crewId:RID,startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3});
 return {a,b};
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
 await create(a,'alice');await join(b,'bobby');await join(c,'carol');await cooldown('alice');
 await create(a,'alice',PID,{kind:'party',partyKind:'remote',crewId:RID,startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:2});
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
test('creation interval stays enforced after more than twenty lifetime creations',async()=>{
 const a=await connect('alice');await create(a,'alice');
 await assertFails(create(a,'alice',PID));
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(profile(ctx.firestore(),'alice'),{createdRoomCount:20,lastRoomCreatedAt:zero}));
 await assertSucceeds(create(a,'alice',PID));
 assert.equal((await getDoc(profile(a,'alice'))).data().createdRoomCount,21);
});

test('new parties cannot exist outside an active crew or use a party as their parent',async()=>{
 const a=await connect('alice');
 const fields={kind:'party',partyKind:'remote',startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3};
 await assertFails(create(a,'alice',PID,fields));
 await assertFails(create(a,'alice',PID,{...fields,crewId:'Z'.repeat(20)}));
 await create(a,'alice');await cooldown('alice');
 await create(a,'alice',PID,{...fields,crewId:RID});await cooldown('alice');
 await assertFails(create(a,'alice','C'.repeat(20),{...fields,crewId:PID}));
});
test('party access is lost immediately after leaving its parent crew even for organizer or DJ',async()=>{
 const {a,b}=await basic();await join(b,'bobby');
 await create(b,'bobby',PID,{kind:'party',partyKind:'remote',crewId:RID,startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3});
 await send(b,'bobby',{rid:PID});await leave(b,'bobby');
 await assertFails(getDocs(query(collection(b,'communityRooms',PID,'messages'),limit(50))));
 await assertFails(getDoc(message(b,'M'.repeat(20),PID)));
 await assertFails(send(b,'bobby',{rid:PID,id:'N'.repeat(20)}));
 await assertFails(getDocs(query(collection(b,'communityRooms',PID,'members'),limit(100))));
 // Own membership is still readable for cleanup, and the former organizer can delete it.
 await assertSucceeds(getDoc(member(b,'bobby',PID)));
 await assertSucceeds(updateDoc(room(b,PID),{status:'deleted',updatedAt:serverTimestamp()}));
 await assertSucceeds(leave(b,'bobby',PID));
});
test('only organizer or parent crew owner may delete a party; deleted conversations are retained but inaccessible',async()=>{
 const {a,b}=await linkedParty();await join(b,'bobby',PID);
 const c=await connect('carol');await create(c,'carol','C'.repeat(20));
 await send(a,'alice',{rid:PID});
 await assertFails(updateDoc(room(b,PID),{status:'deleted',updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(room(c,PID),{status:'deleted',updatedAt:serverTimestamp()}));
 await assertSucceeds(updateDoc(room(a,PID),{status:'deleted',updatedAt:serverTimestamp()}));
 await assertFails(getDoc(message(a,'M'.repeat(20),PID)));
 await assertFails(getDocs(query(collection(b,'communityRooms',PID,'messages'),limit(50))));
 await assertFails(send(b,'bobby',{rid:PID}));
 await assertFails(updateDoc(member(a,'bobby',PID),{role:'dj'}));
 await assertFails(updateDoc(room(a,PID),{status:'active',updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(room(a,PID),{title:'revive',status:'active',updatedAt:serverTimestamp()}));
 await assertFails(deleteDoc(room(a,PID)));
 await env.withSecurityRulesDisabled(async ctx=>{
  assert.equal((await getDoc(message(ctx.firestore(),'M'.repeat(20),PID))).data().body,'함께 달려요');
 });
});
test('parent crew owner can delete a cancelled party without being a party member',async()=>{
 const {a,b}=await basic();await join(b,'bobby');
 await create(b,'bobby',PID,{kind:'party',partyKind:'remote',crewId:RID,startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3});
 await assertSucceeds(updateDoc(room(a,PID),{status:'cancelled',updatedAt:serverTimestamp()}));
 await assertSucceeds(updateDoc(room(a,PID),{status:'deleted',updatedAt:serverTimestamp()}));
 await assertFails(join(a,'alice',PID));
 await assertFails(updateDoc(room(a),{status:'deleted',updatedAt:serverTimestamp()}));
});
test('deleted party cleanup frees private links for organizer and members including the last member',async()=>{
 const {a,b}=await linkedParty();await join(b,'bobby',PID);
 await assertFails(leave(a,'alice',PID));
 await updateDoc(room(a,PID),{status:'deleted',updatedAt:serverTimestamp()});
 await assertFails(updateDoc(room(a,PID),{memberCount:1,updatedAt:serverTimestamp()}));
 await assertFails(updateDoc(profile(a,'alice'),{joinedRoomIds:[RID],lastRoomId:PID,updatedAt:serverTimestamp()}));
 const missingProfile=writeBatch(a);missingProfile.delete(member(a,'alice',PID));missingProfile.update(room(a,PID),{memberCount:1,updatedAt:serverTimestamp()});
 await assertFails(missingProfile.commit());
 await assertSucceeds(leave(a,'alice',PID));await assertSucceeds(leave(b,'bobby',PID));
 assert.equal((await getDoc(room(a,PID))).data().memberCount,0);
 assert.deepEqual((await getDoc(profile(a,'alice'))).data().joinedRoomIds,[RID]);
 assert.deepEqual((await getDoc(profile(b,'bobby'))).data().joinedRoomIds,[RID]);
 await assertFails(updateDoc(room(a,PID),{memberCount:-1,updatedAt:serverTimestamp()}));
 await cooldown('alice');await assertSucceeds(create(a,'alice','D'.repeat(20)));
});
test('cleanup at the hundred-membership bound allows the next creation without fake counters',async()=>{
 const {a}=await linkedParty();
 const old=Array.from({length:98},(_,i)=>String(i).padStart(20,'0'));
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(profile(ctx.firestore(),'alice'),{joinedRoomIds:[RID,PID,...old],lastRoomCreatedAt:zero}));
 await assertFails(create(a,'alice','D'.repeat(20)));
 await updateDoc(room(a,PID),{status:'deleted',updatedAt:serverTimestamp()});
 await assertSucceeds(leave(a,'alice',PID));
 await assertSucceeds(create(a,'alice','D'.repeat(20)));
 assert.equal((await getDoc(profile(a,'alice'))).data().joinedRoomIds.length,100);
});
test('legacy standalone party cannot accept new members or messages but its owner may archive and cleanup',async()=>{
 const a=await connect('alice'),b=await connect('bobby');
 await env.withSecurityRulesDisabled(async ctx=>{
  const d=ctx.firestore();await setDoc(room(d,PID),roomData('alice',{kind:'party',partyKind:'remote',startsAtMillis:Date.now()+3600000,distanceKm:5,capacity:3}));
  await setDoc(member(d,'alice',PID),memberData('alice','owner'));
  await updateDoc(profile(d,'alice'),{joinedRoomIds:[PID]});
 });
 await assertFails(join(b,'bobby',PID));
 await assertFails(send(a,'alice',{rid:PID}));
 await assertSucceeds(updateDoc(room(a,PID),{status:'deleted',updatedAt:serverTimestamp()}));
 await assertSucceeds(leave(a,'alice',PID));
});
test('a former crew member loses party DJ read and announcement privileges',async()=>{
 const {a,b}=await linkedParty();await join(b,'bobby',PID);
 await updateDoc(member(a,'bobby',PID),{role:'dj'});
 await assertSucceeds(send(b,'bobby',{rid:PID,notice:true}));
 await leave(b,'bobby');
 await assertFails(getDoc(message(b,'M'.repeat(20),PID)));
 await assertFails(send(b,'bobby',{rid:PID,id:'N'.repeat(20),notice:true}));
 await assertFails(updateDoc(member(b,'bobby',PID),{role:'owner'}));
});
test('cumulative creation counter remains bounded and cannot be reset or incremented without creation',async()=>{
 const a=await connect('alice');await create(a,'alice');
 await assertFails(updateDoc(profile(a,'alice'),{createdRoomCount:0,updatedAt:serverTimestamp()}));
 await env.withSecurityRulesDisabled(async ctx=>updateDoc(profile(ctx.firestore(),'alice'),{createdRoomCount:1000000,lastRoomCreatedAt:zero}));
 await assertFails(create(a,'alice',PID));
});
