import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { after, before, beforeEach, test } from 'node:test';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  collection,
  collectionGroup,
  deleteDoc,
  deleteField,
  doc,
  getDoc,
  getDocs,
  limit,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
} from 'firebase/firestore';

const PROJECT_ID = 'demo-runmoni';
const OWNER = 'alice';
const OTHER = 'bob';
const RUN_ID = 'run-20261014_01';
let environment;

function summary(overrides = {}, runId = RUN_ID) {
  return {
    schemaVersion: 1,
    runId,
    startedAtMillis: 1000000,
    finishedAtMillis: 1100000,
    activeDurationMillis: 90000,
    distanceMeters: 501.5,
    pointCount: 60,
    backupAt: serverTimestamp(),
    ...overrides,
  };
}

function userDb(uid = OWNER) {
  return environment.authenticatedContext(uid).firestore();
}

function summaryRef(db, uid = OWNER, runId = RUN_ID) {
  return doc(db, 'users', uid, 'runSummaries', runId);
}

async function seed(uid = OWNER, runId = RUN_ID, overrides = {}) {
  await environment.withSecurityRulesDisabled(async (context) => {
    await setDoc(summaryRef(context.firestore(), uid, runId), summary({
      backupAt: Timestamp.fromMillis(1000),
      ...overrides,
    }, runId));
  });
}

before(async () => {
  // Fail closed if pointed at a real project or a non-loopback emulator endpoint.
  const endpoint = process.env.FIRESTORE_EMULATOR_HOST ?? '127.0.0.1:8080';
  assert.match(endpoint, /^(127\.0\.0\.1|localhost):\d+$/);
  const [host, portText] = endpoint.split(':');
  assert.ok(PROJECT_ID.startsWith('demo-'));
  environment = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host,
      port: Number(portText),
      rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8'),
    },
  });
});

beforeEach(async () => {
  await environment.clearFirestore();
});

after(async () => {
  await environment?.cleanup();
});

test('owner creates and gets only the eight summary fields with a server backup timestamp', async () => {
  const ref = summaryRef(userDb());
  await assertSucceeds(setDoc(ref, summary()));
  const result = await assertSucceeds(getDoc(ref));
  assert.equal(result.data().runId, RUN_ID);
  assert.ok(result.data().backupAt instanceof Timestamp);
  assert.equal(Object.keys(result.data()).length, 8);
});

test('owner rebacks up the same run ID without making a second document', async () => {
  const db = userDb();
  const ref = summaryRef(db);
  await assertSucceeds(setDoc(ref, summary()));
  await assertSucceeds(setDoc(ref, summary({ distanceMeters: 502 })));
  const records = await assertSucceeds(getDocs(collection(db, 'users', OWNER, 'runSummaries')));
  assert.equal(records.size, 1);
  assert.equal(records.docs[0].data().distanceMeters, 502);
});

test('owner lists and orders only the own run summary collection', async () => {
  await seed();
  await seed(OWNER, 'run-second', { startedAtMillis: 900000, finishedAtMillis: 1000000 });
  await seed(OTHER);
  const records = await assertSucceeds(getDocs(query(
    collection(userDb(), 'users', OWNER, 'runSummaries'),
    orderBy('startedAtMillis', 'desc'),
    limit(10),
  )));
  assert.equal(records.size, 2);
  assert.equal(records.docs[0].id, RUN_ID);
});

test('owner deletes a backed up summary and may read its now-missing document', async () => {
  await seed();
  const ref = summaryRef(userDb());
  await assertSucceeds(deleteDoc(ref));
  const result = await assertSucceeds(getDoc(ref));
  assert.equal(result.exists(), false);
});

test('owner accepts inclusive zero boundaries and an ID using the allowed ASCII characters', async () => {
  const id = 'AZaz09_-';
  await assertSucceeds(setDoc(summaryRef(userDb(), OWNER, id), summary({
    startedAtMillis: 0,
    finishedAtMillis: 1,
    activeDurationMillis: 0,
    distanceMeters: 0,
    pointCount: 0,
  }, id)));
});

test('owner accepts inclusive timestamp, finite distance and point-count upper bounds and a 128-character ID', async () => {
  const id = 'A'.repeat(128);
  await assertSucceeds(setDoc(summaryRef(userDb(), OWNER, id), summary({
    startedAtMillis: 253402300699999,
    finishedAtMillis: 253402300799999,
    activeDurationMillis: 100000,
    distanceMeters: 10000000,
    pointCount: 1000000,
  }, id)));
});

for (const [actor, db] of [
  ['unauthenticated', () => environment.unauthenticatedContext().firestore()],
  ['other user', () => userDb(OTHER)],
]) {
  for (const operation of ['get', 'list', 'create', 'update', 'delete']) {
    test(`${actor} cannot ${operation} the owner's summary`, async () => {
      if (operation !== 'create') await seed();
      const firestore = db();
      const ref = summaryRef(firestore);
      const requests = {
        get: () => getDoc(ref),
        list: () => getDocs(collection(firestore, 'users', OWNER, 'runSummaries')),
        create: () => setDoc(ref, summary()),
        update: () => updateDoc(ref, { distanceMeters: 0, backupAt: serverTimestamp() }),
        delete: () => deleteDoc(ref),
      };
      await assertFails(requests[operation]());
    });
  }
}

test('an authenticated user cannot use collection-group queries to cross user paths', async () => {
  await seed();
  await seed(OTHER);
  await assertFails(getDocs(collectionGroup(userDb(), 'runSummaries')));
});

test('a same-prefix UID cannot impersonate the owner', async () => {
  await seed();
  await assertFails(getDoc(summaryRef(userDb('alice-extra'))));
});

test('a 128-character authenticated UID can back up its own summary', async () => {
  const uid = 'u'.repeat(128);
  await assertSucceeds(setDoc(summaryRef(userDb(uid), uid), summary()));
});

test('a mock authenticated UID longer than 128 characters cannot access the matching path', async () => {
  const uid = 'u'.repeat(129);
  await assertFails(setDoc(summaryRef(userDb(uid), uid), summary()));
});

for (const id of ['contains.dot', 'with space', '한글', 'A'.repeat(129)]) {
  test(`owner cannot back up an invalid run path ID ${JSON.stringify(id)}`, async () => {
    await assertFails(setDoc(summaryRef(userDb(), OWNER, id), summary({}, id)));
  });
}

for (const runId of ['', 'another-run', 12, null]) {
  test(`owner cannot write a missing, mismatched or non-string runId ${JSON.stringify(runId)}`, async () => {
    await assertFails(setDoc(summaryRef(userDb()), summary({ runId })));
  });
}

const invalidValues = [
  ['schemaVersion', 2],
  ['schemaVersion', 1.5],
  ['schemaVersion', '1'],
  ['startedAtMillis', -1],
  ['startedAtMillis', 253402300800000],
  ['startedAtMillis', 0.5],
  ['startedAtMillis', '1000000'],
  ['finishedAtMillis', -1],
  ['finishedAtMillis', 0],
  ['finishedAtMillis', 253402300800000],
  ['finishedAtMillis', 999999],
  ['finishedAtMillis', 1100000.5],
  ['finishedAtMillis', '1100000'],
  ['activeDurationMillis', -1],
  ['activeDurationMillis', 100001],
  ['activeDurationMillis', 0.5],
  ['activeDurationMillis', '90000'],
  ['distanceMeters', -0.1],
  ['distanceMeters', 10000000.1],
  ['distanceMeters', Number.NaN],
  ['distanceMeters', Number.POSITIVE_INFINITY],
  ['distanceMeters', Number.NEGATIVE_INFINITY],
  ['distanceMeters', '501.5'],
  ['distanceMeters', null],
  ['pointCount', -1],
  ['pointCount', 1000001],
  ['pointCount', 0.5],
  ['pointCount', '60'],
  ['backupAt', Timestamp.fromMillis(0)],
  ['backupAt', Timestamp.fromMillis(4102444800000)],
  ['backupAt', 123],
  ['backupAt', null],
];

for (const [field, value] of invalidValues) {
  test(`owner cannot write invalid ${field}: ${String(value)}`, async () => {
    await assertFails(setDoc(summaryRef(userDb()), summary({ [field]: value })));
  });
}

for (const field of Object.keys(summary())) {
  test(`owner cannot omit required field ${field}`, async () => {
    const data = summary();
    delete data[field];
    await assertFails(setDoc(summaryRef(userDb()), data));
  });
}

for (const [field, value] of [
  ['wallet', { balance: 999999 }],
  ['mileage', 999999],
  ['rewardPoints', 999999],
  ['coordinates', [37.5, 127.0]],
  ['points', [{ latitude: 37.5, longitude: 127.0 }]],
  ['latitude', 37.5],
  ['longitude', 127.0],
  ['userId', OTHER],
  ['extraField', true],
]) {
  test(`owner cannot introduce extra field ${field} on create or update`, async () => {
    const ref = summaryRef(userDb());
    await assertFails(setDoc(ref, summary({ [field]: value })));
    await seed();
    await assertFails(updateDoc(ref, { [field]: value, backupAt: serverTimestamp() }));
  });
}

test('owner cannot delete a required field with an update', async () => {
  await seed();
  await assertFails(updateDoc(summaryRef(userDb()), {
    pointCount: deleteField(),
    backupAt: serverTimestamp(),
  }));
});

test('owner cannot update while retaining an old backup timestamp', async () => {
  await seed();
  await assertFails(updateDoc(summaryRef(userDb()), { distanceMeters: 600 }));
});

test('owner may update valid values only with a fresh server timestamp', async () => {
  await seed();
  await assertSucceeds(updateDoc(summaryRef(userDb()), {
    distanceMeters: 600,
    backupAt: serverTimestamp(),
  }));
});

for (const path of [
  'users/alice',
  'users/alice/wallet/main',
  'users/alice/runSummaries/run-extra/tracks/point-1',
  'crews/crew-1',
  'parties/party-1',
  'presence/alice',
  'wallets/alice',
  'mileage_ledger/entry-1',
  'unknown/path',
]) {
  test(`authenticated owner cannot read, write or delete excluded path ${path}`, async () => {
    await environment.withSecurityRulesDisabled(async (context) => {
      await setDoc(doc(context.firestore(), path), { seeded: true });
    });
    const ref = doc(userDb(), path);
    await assertFails(getDoc(ref));
    await assertFails(setDoc(ref, { attempted: true }));
    await assertFails(deleteDoc(ref));
  });
}
