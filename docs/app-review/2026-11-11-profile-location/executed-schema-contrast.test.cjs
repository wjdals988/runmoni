const assert = require('node:assert/strict');
const { test } = require('node:test');
const { readFileSync } = require('node:fs');
const { createRequire } = require('node:module');
const requireFixture = createRequire('/workspace/runmoni/firebase/package.json');
const { initializeTestEnvironment } = requireFixture('@firebase/rules-unit-testing');
const { doc, getDoc, writeBatch, serverTimestamp, Timestamp } = requireFixture('firebase/firestore');
const endpoint = process.env.FIRESTORE_EMULATOR_HOST;
assert.equal(endpoint, '127.0.0.1:8080');
const projectId = 'demo-runmoni';
function fields() { return { nickname:'MigrationReview', nicknameKey:'migrationreview',
  createdAt:serverTimestamp(),updatedAt:serverTimestamp(),joinedRoomIds:[],lastRoomId:'',
  createdRoomCount:0,lastRoomCreatedAt:Timestamp.fromMillis(0) }; }
async function scenario(path, legacy) {
  const env = await initializeTestEnvironment({projectId, firestore:{host:'127.0.0.1',port:8080,rules:readFileSync(path,'utf8')}});
  try {
    await env.clearFirestore();
    const database = env.authenticatedContext('schema-review-owner',{firebase:{sign_in_provider:'anonymous'}}).firestore();
    const profile=doc(database,'communityProfiles','schema-review-owner');
    const claim=doc(database,'communityNicknames','migrationreview');
    if (legacy) await assert.rejects(getDoc(claim),error=>error.code==='permission-denied');
    else assert.equal((await getDoc(claim)).exists(),false);
    const batch=writeBatch(database);batch.set(profile,fields());
    batch.set(claim,{uid:'schema-review-owner',nickname:'MigrationReview',createdAt:serverTimestamp(),updatedAt:serverTimestamp()});
    if (legacy) await assert.rejects(batch.commit(),error=>error.code==='permission-denied');
    else {
      await batch.commit();assert.equal((await getDoc(profile)).data().nicknameKey,'migrationreview');
      assert.equal((await getDoc(claim)).data().uid,'schema-review-owner');
    }
  } finally { await env.cleanup(); }
}
test('exact pre-reservation policy denies current nickname claim read and atomic registration',async()=>{
  await scenario('/workspace/runmoni/work/profile-location/pre-reservation.rules',true);
});
test('current policy accepts the same owner-bound registration without relaxing private ownership',async()=>{
  await scenario('/workspace/runmoni/firebase/firestore.rules',false);
});
