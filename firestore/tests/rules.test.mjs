// Firestore security-rules tests. They run ONLY against the local emulator (project "demo-giro"),
// never against production.   Run:  npm install && npm test
// Test another rules file:      RULES=/path/to/other.rules npm test
//
// [SEC]    = an attack that must be DENIED
// [BUG]    = legitimate behaviour that the old rules rejected by mistake
// [COMPAT] = writes done by the published Android app (same field names) that must keep working
import { test, before, after, beforeEach } from 'node:test';
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import {
  doc, getDoc, getDocs, setDoc, updateDoc, deleteDoc, addDoc, collection, query, where, limit,
  arrayUnion, arrayRemove, increment, deleteField,
} from 'firebase/firestore';

const RULES = process.env.RULES || new URL('../firestore.rules', import.meta.url);
const CODE = 'ABC123';
let env;

const as = (uid, email) => env.authenticatedContext(uid, { email: email ?? `${uid}@example.com`, email_verified: true }).firestore();
const anon = () => env.unauthenticatedContext().firestore();

before(async () => {
  env = await initializeTestEnvironment({ projectId: 'demo-giro', firestore: { rules: readFileSync(RULES, 'utf8') } });
});
after(() => env.cleanup());

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    // a calendar created before `openCount` existed
    await setDoc(doc(db, 'calendars', CODE), {
      code: CODE, name: 'MTB Test', description: '', ownerUid: 'owner', ownerEmail: 'ow***@example.com',
      listed: true, editors: ['owner', 'editor'], createdAtMillis: 1,
    });
    await setDoc(doc(db, 'calendars', CODE, 'private', 'owner'), { email: 'owner@example.com' });
    await setDoc(doc(db, 'calendars', CODE, 'events', 'e1'), { titolo: 'Giro', partecipanti: ['Anna', 'Bruno'], forse: [] });
    await setDoc(doc(db, 'emailIndex', 'victim@example.com'), { uid: 'victim' });
    await setDoc(doc(db, 'emailIndex', 'other@example.com'), { uid: 'other' });
    await setDoc(doc(db, 'admins', 'owner'), { since: 1, code: CODE });
    await setDoc(doc(db, 'admins', 'legacy'), { since: 1 });                 // pre-fix entry, never verified
    await setDoc(doc(db, 'admins', 'exowner'), { since: 1, code: 'GONE01' }); // its calendar was deleted
    await setDoc(doc(db, 'trails', 't1'), {
      name: 'Flow', zone: 'Rive Rosse', status: 'open', difficulty: 's1', createdByUid: 'victim',
      createdAtMillis: 1, lastUpdateAtMillis: 0, lastUpdateByName: '', path: 'abc', lat: 45.6, lon: 8.2,
    });
    await setDoc(doc(db, 'trailLog', 'l1'), { action: 'create', trailName: 'Flow', userUid: 'victim', userEmail: 'victim@example.com', createdAtMillis: 1 });
    await setDoc(doc(db, 'pois', 'p1'), { name: 'Fontana', desc: '', type: 'altro', lat: 45.6, lon: 8.2, createdByUid: 'victim' });
    await setDoc(doc(db, 'trailZones', 'rive-rosse'), { zone: 'Rive Rosse', polygon: 'abc', createdByUid: 'victim' });
  });
});

// ───────────────────────── emailIndex ─────────────────────────
test('[SEC] a logged-in stranger cannot dump every user email by listing emailIndex', async () => {
  await assertFails(getDocs(collection(as('mallory'), 'emailIndex')));
});
test('[SEC] emailIndex entry cannot carry arbitrary extra data', async () => {
  await assertFails(setDoc(doc(as('mallory'), 'emailIndex', 'mallory@example.com'), { uid: 'mallory', junk: 'x'.repeat(100000) }));
});
test('[SEC] cannot register someone else\'s email in emailIndex', async () => {
  await assertFails(setDoc(doc(as('mallory'), 'emailIndex', 'victim@example.com'), { uid: 'mallory' }));
});
test('[COMPAT] login registers own emailIndex entry; invite-by-email lookup (get) works', async () => {
  await assertSucceeds(setDoc(doc(as('alice'), 'emailIndex', 'alice@example.com'), { uid: 'alice' }));
  await assertSucceeds(getDoc(doc(as('owner'), 'emailIndex', 'victim@example.com')));
});
test('[BUG] a user can delete their own emailIndex entry (account deletion / GDPR erasure)', async () => {
  await assertSucceeds(deleteDoc(doc(as('victim'), 'emailIndex', 'victim@example.com')));
  await assertFails(deleteDoc(doc(as('mallory'), 'emailIndex', 'other@example.com')));
});

// ───────────────────────── event participants (no login needed) ─────────────────────────
const ev = (db) => doc(db, 'calendars', CODE, 'events', 'e1');
test('[SEC] anonymous visitor cannot bloat an event with a huge "participant"', async () => {
  await assertFails(updateDoc(ev(anon()), { partecipanti: arrayUnion('x'.repeat(200000)) }));
});
test('[SEC] anonymous visitor cannot push a non-string participant', async () => {
  await assertFails(updateDoc(ev(anon()), { partecipanti: arrayUnion({ html: '<img src=x onerror=alert(1)>' }) }));
  await assertFails(updateDoc(ev(anon()), { forse: arrayUnion(12345) }));
});
test('[COMPAT] anonymous visitor can add / move / remove a name', async () => {
  await assertSucceeds(updateDoc(ev(anon()), { partecipanti: arrayUnion('Carla') }));
  await assertSucceeds(updateDoc(ev(anon()), { partecipanti: arrayRemove('Carla'), forse: arrayUnion('Carla') }));
  await assertSucceeds(updateDoc(ev(anon()), { forse: arrayRemove('Carla') }));
});
test('[SEC] anonymous visitor still cannot rewrite the list or touch other fields', async () => {
  await assertFails(updateDoc(ev(anon()), { partecipanti: [] }));
  await assertFails(updateDoc(ev(anon()), { titolo: 'hacked' }));
});

// ───────────────────────── openCount ─────────────────────────
test('[BUG] openCount can be incremented on a calendar that does not have the field yet', async () => {
  await assertSucceeds(updateDoc(doc(anon(), 'calendars', CODE), { openCount: increment(1) }));
  await assertSucceeds(updateDoc(doc(anon(), 'calendars', CODE), { openCount: increment(1) }));
});
test('[SEC] openCount cannot be set to an arbitrary value', async () => {
  await assertFails(updateDoc(doc(anon(), 'calendars', CODE), { openCount: 999999 }));
});

// ───────────────────────── trail admin ─────────────────────────
test('[SEC] legacy admins/{uid} entry (no calendar behind it) grants no admin power', async () => {
  await assertFails(deleteDoc(doc(as('legacy'), 'trails', 't1')));
  await assertFails(getDocs(collection(as('legacy'), 'trailLog')));
});
test('[SEC] admin power is revoked when the calendar is deleted / ownership is lost', async () => {
  await assertFails(deleteDoc(doc(as('exowner'), 'trails', 't1')));
  await assertFails(getDocs(collection(as('exowner'), 'trailLog')));
});
test('[SEC] cannot self-register as admin for a calendar you do not own', async () => {
  await assertFails(setDoc(doc(as('mallory'), 'admins', 'mallory'), { since: 1, code: CODE }));
});
test('[COMPAT] a current calendar owner is trail admin (delete trail, read log)', async () => {
  await assertSucceeds(getDocs(collection(as('owner'), 'trailLog')));
  await assertSucceeds(deleteDoc(doc(as('owner'), 'trails', 't1')));
});
test('[COMPAT] create calendar + admins entry + private/owner (CalendarRepository.createCalendar)', async () => {
  const db = as('alice');
  await assertSucceeds(setDoc(doc(db, 'calendars', 'NEW001'), {
    code: 'NEW001', name: 'Nuovo', description: '', ownerUid: 'alice', ownerEmail: 'al***@example.com',
    listed: true, editors: ['alice'], createdAtMillis: 5, logoColor: '', logoData: '',
  }));
  await assertSucceeds(setDoc(doc(db, 'admins', 'alice'), { since: 5, code: 'NEW001' }));
  await assertSucceeds(setDoc(doc(db, 'calendars', 'NEW001', 'private', 'owner'), { email: 'alice@example.com' }));
  await assertSucceeds(getDocs(collection(db, 'trailLog')));
});

// ───────────────────────── trails / trailLog / pois / zones ─────────────────────────
test('[SEC] a random logged-in user cannot stuff arbitrary fields / blobs into someone else\'s trail', async () => {
  await assertFails(updateDoc(doc(as('mallory'), 'trails', 't1'), { evil: 'x'.repeat(500000) }));
  await assertFails(updateDoc(doc(as('mallory'), 'trails', 't1'), { status: '<script>' }));
  await assertFails(updateDoc(doc(as('mallory'), 'trails', 't1'), { createdByUid: 'mallory' }));
});
test('[SEC] trailLog entry cannot carry arbitrary fields or a forged identity', async () => {
  const base = { action: 'create', trailName: 'x', detail: '', userUid: 'mallory', userName: 'M', userEmail: 'mallory@example.com', createdAtMillis: 1 };
  await assertFails(addDoc(collection(as('mallory'), 'trailLog'), { ...base, junk: 'x'.repeat(500000) }));
  await assertFails(addDoc(collection(as('mallory'), 'trailLog'), { ...base, userEmail: 'victim@example.com' }));
});
test('[SEC] POI / zone / trail comment cannot carry arbitrary fields or oversized text', async () => {
  await assertFails(updateDoc(doc(as('mallory'), 'pois', 'p1'), { junk: 'x'.repeat(500000) }));
  await assertFails(updateDoc(doc(as('mallory'), 'trailZones', 'rive-rosse'), { junk: 'x'.repeat(500000) }));
  await assertFails(addDoc(collection(as('mallory'), 'trails', 't1', 'comments'),
    { authorUid: 'mallory', authorName: 'M', text: 'x'.repeat(500000), newStatus: '', createdAtMillis: 1 }));
});
test('[COMPAT] trail lifecycle exactly as TrailRepository writes it', async () => {
  const db = as('alice');
  const t = doc(db, 'trails', 't2');
  await assertSucceeds(setDoc(t, { name: 'Nuovo', zone: 'Rive Rosse', status: 'open', difficulty: 's2', createdByUid: 'alice', createdAtMillis: 9, lastUpdateAtMillis: 0, lastUpdateByName: '' }));
  await assertSucceeds(updateDoc(t, { difficulty: 's3' }));
  await assertSucceeds(updateDoc(t, { path: 'enc', lat: 45.1, lon: 8.1 }));
  await assertSucceeds(updateDoc(t, { lat: 45.2, lon: 8.2, path: deleteField() }));
  await assertSucceeds(updateDoc(t, { name: 'Rinominato' }));
  // another logged-in user reports a status change on it (wiki model)
  const bob = as('bob');
  await assertSucceeds(addDoc(collection(bob, 'trails', 't2', 'comments'), { authorUid: 'bob', authorName: 'Bob', text: 'albero caduto', newStatus: 'caution', createdAtMillis: 10 }));
  await assertSucceeds(updateDoc(doc(bob, 'trails', 't2'), { status: 'caution', lastUpdateAtMillis: 10, lastUpdateByName: 'Bob' }));
  await assertSucceeds(addDoc(collection(bob, 'trailLog'), { action: 'status', trailName: 'Rinominato', detail: '', userUid: 'bob', userName: 'Bob', userEmail: 'bob@example.com', createdAtMillis: 10, trailId: 't2', undo: { kind: 'fields', before: { status: 'open' }, after: { status: 'caution' } } }));
  await assertSucceeds(deleteDoc(t));
});
test('[COMPAT] POI and zone writes exactly as TrailRepository writes them', async () => {
  const db = as('alice');
  await assertSucceeds(addDoc(collection(db, 'pois'), { name: 'Vetta', desc: 'bella', type: 'vetta', lat: 45, lon: 8, createdByUid: 'alice', createdByName: 'Alice', createdAtMillis: 1 }));
  await assertSucceeds(setDoc(doc(db, 'pois', 'p1'), { name: 'Fontana', desc: 'acqua', type: 'altro', lat: 45.6, lon: 8.2, updatedByName: 'Alice', updatedAtMillis: 2 }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'trailZones', 'rive-rosse'), { zone: 'Rive Rosse', polygon: 'xyz', createdByUid: 'victim', updatedAtMillis: 2 }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'trailZones', 'rive-rosse'), { zone: 'Rive Rosse', updatedAtMillis: 3, builders: [{ name: 'Crew', logo: 'data:image/webp;base64,AAAA', url: 'https://example.com' }] }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'trailZones', 'nuova'), { zone: 'Nuova', updatedAtMillis: 3, builders: [], polygon: '', createdByUid: 'alice' }, { merge: true }));
});

test('[COMPAT] web client writes (giro-web app.js): POI edit, builders, trail merge, participant with display name', async () => {
  const db = as('alice');
  await assertSucceeds(setDoc(doc(db, 'pois', 'p1'), { name: 'Fontana', desc: 'acqua', type: 'altro', updatedByName: 'Alice', updatedAtMillis: 2 }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'trailZones', 'rive-rosse'), { zone: 'Rive Rosse', builders: [], updatedAtMillis: 4 }, { merge: true }));
  await assertSucceeds(setDoc(doc(db, 'trails', 't1'), { name: 'Flow', zone: 'Rive Rosse', status: 'open', difficulty: 's1', createdByUid: 'victim',
    createdAtMillis: 1, lastUpdateAtMillis: 0, lastUpdateByName: '', path: 'abc', lat: 45.6, lon: 8.2 }, { merge: true }));
  await assertSucceeds(updateDoc(ev(as('alice')), { partecipanti: arrayUnion('Alice Rossi'), forse: arrayRemove('Alice Rossi') }));
});

// ───────────────────────── proposals ─────────────────────────
const prop = { titolo: 'Giro al lago', data: '30/11/2026', ora: '09:00', proponente: 'Alice', proposedAtMillis: 1 };
test('[SEC] one account cannot flood a calendar with proposals (random ids)', async () => {
  await assertFails(setDoc(doc(as('mallory'), 'calendars', CODE, 'proposals', 'randomId0001'), prop));
  await assertFails(addDoc(collection(as('mallory'), 'calendars', CODE, 'proposals'), prop));
});
test('[SEC] a second proposal is refused while the first one is pending', async () => {
  await assertSucceeds(setDoc(doc(as('mallory'), 'calendars', CODE, 'proposals', 'mallory'), prop));
  await assertFails(setDoc(doc(as('mallory'), 'calendars', CODE, 'proposals', 'mallory'), { ...prop, titolo: 'spam' }));
});
test('[COMPAT] propose with id = uid (CalendarRepository / web app.js), editor approves or rejects', async () => {
  await assertSucceeds(setDoc(doc(as('alice'), 'calendars', CODE, 'proposals', 'alice'), prop));
  await assertSucceeds(getDocs(collection(as('editor'), 'calendars', CODE, 'proposals')));
  await assertSucceeds(deleteDoc(doc(as('editor'), 'calendars', CODE, 'proposals', 'alice')));
  await assertSucceeds(setDoc(doc(as('alice'), 'calendars', CODE, 'proposals', 'alice'), prop));
});

// ───────────────────────── calendar document ─────────────────────────
test('[SEC] nobody but the owner can change ownerUid / editors', async () => {
  await assertFails(updateDoc(doc(as('mallory'), 'calendars', CODE), { ownerUid: 'mallory' }));
  await assertFails(updateDoc(doc(as('mallory'), 'calendars', CODE), { editors: arrayUnion('mallory') }));
  await assertFails(updateDoc(doc(as('editor'), 'calendars', CODE), { editors: arrayUnion('mallory') }));
  await assertFails(updateDoc(doc(as('editor'), 'calendars', CODE), { ownerUid: 'editor' }));
  await assertFails(setDoc(doc(as('mallory'), 'calendars', 'FAKE01'), { code: 'FAKE01', name: 'x', ownerUid: 'victim', editors: ['victim'] }));
});
test('[COMPAT] owner manages editors; editor writes events and can unlink itself', async () => {
  await assertSucceeds(updateDoc(doc(as('owner'), 'calendars', CODE), { editors: arrayUnion('newed') }));
  await assertSucceeds(setDoc(doc(as('editor'), 'calendars', CODE, 'events', 'e2'), { titolo: 'Nuova uscita', partecipanti: [] }));
  await assertSucceeds(updateDoc(doc(as('editor'), 'calendars', CODE), { editors: arrayRemove('editor') }));
});
