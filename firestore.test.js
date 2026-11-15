const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

// --- Unauthenticated tests ---
test("Unauthenticated user: cannot read alerts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("alerts").get());
});

test("Unauthenticated user: cannot create alert", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("alerts").doc("alert_1").set({
      type: "pozo",
      latitude: -34.6037,
      longitude: -58.3816,
      reporterUid: ALICE_UID,
      status: "active",
      createdAt: new Date(),
    })
  );
});

// --- Authenticated alert tests ---
test("Authenticated user: can create valid road alert", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("alerts").doc("alert_1").set({
      type: "pozo",
      depthCm: 15,
      roadName: "Ruta 2 Km 120",
      description: "Pozo cortante en carril rápido",
      latitude: -35.5,
      longitude: -58.0,
      reporterUid: ALICE_UID,
      reporterName: "Alice Conductora",
      status: "active",
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: cannot spoof reporterUid", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("alerts").doc("alert_spoofed").set({
      type: "pozo",
      latitude: -35.5,
      longitude: -58.0,
      reporterUid: BOB_UID, // Spoofing Bob
      status: "active",
      createdAt: new Date(),
    })
  );
});

test("Community user: can update confirmations count", async () => {
  // Alice creates alert
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const alertDoc = {
    type: "ruta_detonada",
    latitude: -36.0,
    longitude: -59.0,
    reporterUid: ALICE_UID,
    status: "active",
    confirmations: 1,
    createdAt: new Date(),
  };
  await aliceDb.collection("alerts").doc("alert_2").set(alertDoc);

  // Bob confirms
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("alerts").doc("alert_2").update({
      confirmations: 2,
    })
  );

  // Community user can vote alert as no longer there and auto-resolve when threshold met
  await assertSucceeds(
    bobDb.collection("alerts").doc("alert_2").update({
      notThereVotes: 3,
      status: "resolved",
    })
  );
});

// --- SOS Request tests ---
test("Authenticated user: can create SOS request for vehicle", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("sos_requests").doc("sos_1").set({
      ownerUid: ALICE_UID,
      ownerName: "Alice Chofer",
      ownerDni: "35123456",
      dniVerified: true,
      vehicleModel: "Fiat Cronos Rojo",
      licensePlate: "AF 123 CD",
      assistanceType: "pinchadura",
      latitude: -37.0,
      longitude: -57.5,
      routeDescription: "Ruta 2 km 200",
      status: "active",
      createdAt: new Date(),
    })
  );
});

test("User can mark their own SOS as resolved", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await aliceDb.collection("sos_requests").doc("sos_1").set({
    ownerUid: ALICE_UID,
    ownerName: "Alice Chofer",
    ownerDni: "35123456",
    vehicleModel: "Fiat Cronos Rojo",
    licensePlate: "AF 123 CD",
    assistanceType: "bateria",
    latitude: -37.0,
    longitude: -57.5,
    status: "active",
    createdAt: new Date(),
  });

  await assertSucceeds(
    aliceDb.collection("sos_requests").doc("sos_1").update({
      status: "resolved",
    })
  );
});

// --- Hitchhike tests ---
test("Verified hitchhiker can create request and driver can pair", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("hitchhike_requests").doc("ride_1").set({
      userUid: ALICE_UID,
      userName: "Alice Docente",
      userDni: "34987654",
      roleCategory: "docente",
      pickupPoint: "Garita Rotonda Chascomús",
      destination: "Castelli",
      latitude: -35.58,
      longitude: -58.01,
      status: "waiting",
      createdAt: new Date(),
    })
  );

  // Bob driver pairs license plate and sets picked_up
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("hitchhike_requests").doc("ride_1").update({
      pairedPlate: "AD 456 EF",
      status: "picked_up",
    })
  );
});

// --- Baúl Vial (Lost & Found with ownership verification) tests ---
test("Unauthenticated user: cannot read or create baul_vial items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("baul_vial").get());
  await assertFails(
    unauthDb.collection("baul_vial").doc("item_1").set({
      type: "encontrado",
      category: "patente",
      title: "Patente encontrada",
      roadName: "Ruta 41 km 115",
      publisherUid: ALICE_UID,
      verificationQuestion: "¿Cuáles son las letras iniciales?",
      claimStatus: "sin_reclamo",
      status: "activo",
      createdAt: new Date(),
    })
  );
});

test("Baul Vial: full claim & approval flow, blocks hijacking pending claims", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("baul_vial").doc("item_patente").set({
      type: "encontrado",
      category: "patente",
      title: "Patente de auto en banquina",
      publicDescription: "Terminada en 89",
      roadName: "Ruta 41 km 115",
      latitude: -35.0,
      longitude: -59.2,
      publisherUid: ALICE_UID,
      publisherName: "Alice Conductora",
      verificationQuestion: "¿Qué letras empieza la patente?",
      claimStatus: "sin_reclamo",
      status: "activo",
      createdAt: new Date(),
    })
  );

  // Bob submits ownership proof
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("baul_vial").doc("item_patente").update({
      claimantUid: BOB_UID,
      claimantName: "Bob Conductor",
      claimantAnswer: "AF 432",
      claimantContact: "+5491144556677",
      claimStatus: "pendiente",
    })
  );

  // Stranger Charlie cannot overwrite Bob's pending claim
  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();
  await assertFails(
    charlieDb.collection("baul_vial").doc("item_patente").update({
      claimantUid: "charlie_789",
      claimantName: "Charlie",
      claimantAnswer: "Fake",
      claimStatus: "pendiente",
    })
  );

  // Bob cannot self-approve his own claim
  await assertFails(
    bobDb.collection("baul_vial").doc("item_patente").update({
      claimStatus: "aprobado",
    })
  );

  // Publisher Alice approves and releases her contact
  await assertSucceeds(
    aliceDb.collection("baul_vial").doc("item_patente").update({
      claimStatus: "aprobado",
      publisherContact: "+5491199887766",
    })
  );

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("baul_vial").doc("item_patente").delete());

  // Publisher Alice can mark as devuelto or delete
  await assertSucceeds(
    aliceDb.collection("baul_vial").doc("item_patente").update({
      status: "devuelto",
    })
  );
});

// --- Owner Admin Dashboard rules ---
test("Admin redmond262@gmail.com can list & update user PRO status while non-admin cannot", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Conductora",
      email: "alice@example.com",
      isProUser: false,
      createdAt: new Date(),
    })
  );

  // Stranger Bob cannot read or update Alice's profile
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ isProUser: true }));

  // Owner Admin can read & toggle PRO status
  const adminDb = testEnv.authenticatedContext("owner_uid", { email: "redmond262@gmail.com" }).firestore();
  await assertSucceeds(adminDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(adminDb.collection("users").doc(ALICE_UID).update({ isProUser: true }));

  // Owner Admin can list alerts, sos_requests, and hitchhike_requests without status filter
  // while non-admin unfiltered list queries must be rejected
  await assertFails(bobDb.collection("alerts").get());
  await assertFails(bobDb.collection("sos_requests").get());
  await assertFails(bobDb.collection("hitchhike_requests").get());

  await assertSucceeds(adminDb.collection("alerts").get());
  await assertSucceeds(adminDb.collection("sos_requests").get());
  await assertSucceeds(adminDb.collection("hitchhike_requests").get());
});

