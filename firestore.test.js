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

// --- Unauthenticated (Guest Mode) tests ---
test("Unauthenticated guest: can read active alerts read-only, blocked from resolved or unfiltered queries", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminSeedDb = context.firestore();
    await adminSeedDb.collection("alerts").doc("alert_active_1").set({
      type: "pozo",
      latitude: -35.5,
      longitude: -58.0,
      reporterUid: ALICE_UID,
      status: "active",
      createdAt: new Date(),
    });
    await adminSeedDb.collection("alerts").doc("alert_resolved_1").set({
      type: "control_radar",
      latitude: -35.6,
      longitude: -58.1,
      reporterUid: ALICE_UID,
      status: "resolved",
      createdAt: new Date(),
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertSucceeds(unauthDb.collection("alerts").where("status", "==", "active").get());
  await assertSucceeds(unauthDb.collection("alerts").doc("alert_active_1").get());
  await assertFails(unauthDb.collection("alerts").get());
  await assertFails(unauthDb.collection("alerts").where("status", "==", "resolved").get());
  await assertFails(unauthDb.collection("alerts").doc("alert_resolved_1").get());
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

// --- Referral Codes & Referrals rules ---
test("Referral code directory: single-doc lookup allowed for signed-in users, list blocked, ownership enforced", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates her referral code
  await assertSucceeds(
    aliceDb.collection("referral_codes").doc("VIA-ALICE1").set({
      code: "VIA-ALICE1",
      ownerUid: ALICE_UID,
      ownerName: "Alice",
      createdAt: new Date(),
    })
  );

  // Alice cannot spoof Bob's ownerUid
  await assertFails(
    aliceDb.collection("referral_codes").doc("VIA-FAKE01").set({
      code: "VIA-FAKE01",
      ownerUid: BOB_UID,
      ownerName: "Bob",
      createdAt: new Date(),
    })
  );

  // Bob can look up a single code doc, but CANNOT list/scrape all codes
  await assertSucceeds(bobDb.collection("referral_codes").doc("VIA-ALICE1").get());
  await assertFails(bobDb.collection("referral_codes").get());
});

test("Referrals attribution: referee redeems once, blocks self-referral & hijack, referrer claims pending reward", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext("charlie_789").firestore();

  await assertSucceeds(
    aliceDb.collection("referral_codes").doc("VIA-ALICE1").set({
      code: "VIA-ALICE1",
      ownerUid: ALICE_UID,
      ownerName: "Alice",
      createdAt: new Date(),
    })
  );

  // Self-referral must fail
  await assertFails(
    aliceDb.collection("referrals").doc(ALICE_UID).set({
      refereeUid: ALICE_UID,
      refereeName: "Alice",
      referrerUid: ALICE_UID,
      code: "VIA-ALICE1",
      status: "pending",
      rewardGrantedToReferee: true,
      rewardGrantedToReferrer: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Bob redeems Alice's code validly
  await assertSucceeds(
    bobDb.collection("referrals").doc(BOB_UID).set({
      refereeUid: BOB_UID,
      refereeName: "Bob",
      referrerUid: ALICE_UID,
      code: "VIA-ALICE1",
      status: "pending",
      rewardGrantedToReferee: true,
      rewardGrantedToReferrer: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Stranger Charlie cannot hijack or overwrite Bob's referral record
  await assertFails(
    charlieDb.collection("referrals").doc(BOB_UID).update({
      rewardGrantedToReferrer: true,
      status: "claimed",
    })
  );

  // Referrer Alice can claim her pending reward once
  await assertSucceeds(
    aliceDb.collection("referrals").doc(BOB_UID).update({
      rewardGrantedToReferrer: true,
      status: "claimed",
      updatedAt: new Date(),
    })
  );

  // Duplicate claim after rewardGrantedToReferrer is true must fail for non-admin
  await assertFails(
    aliceDb.collection("referrals").doc(BOB_UID).update({
      rewardGrantedToReferrer: true,
      status: "claimed",
    })
  );
});

// --- App Billing Config rules ---
test("App billing config: admin can write, signed-in user can read doc, non-admin cannot write", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const adminDb = testEnv.authenticatedContext("owner_uid", { email: "redmond262@gmail.com" }).firestore();

  await assertFails(
    bobDb.collection("app_config").doc("billing").set({
      mercadoPagoAlias: "fake.alias",
      proPriceArs: 1,
    })
  );

  await assertSucceeds(
    adminDb.collection("app_config").doc("billing").set({
      mercadoPagoAlias: "vialerta.oficial.mp",
      proPriceArs: 5000,
      paymentLinkUrl: "https://mpago.la/viaalerta",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(bobDb.collection("app_config").doc("billing").get());
  await assertFails(bobDb.collection("app_config").get());
});

// --- Feature Flags (app_config/features) & Per-User Beta Testers rules ---
test("Feature flags config: guest & users can get features doc, cannot list or write; admin controls modes", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const adminDb = testEnv.authenticatedContext("owner_uid", { email: "redmond262@gmail.com" }).firestore();

  // Non-admin cannot create or update app_config/features
  await assertFails(
    bobDb.collection("app_config").doc("features").set({
      aventonMode: "public",
      baulVialMode: "public",
    })
  );

  // Admin rejects invalid mode string or extra unauthorized field
  await assertFails(
    adminDb.collection("app_config").doc("features").set({
      aventonMode: "invalid_mode",
      baulVialMode: "beta",
    })
  );
  await assertFails(
    adminDb.collection("app_config").doc("features").set({
      aventonMode: "beta",
      baulVialMode: "beta",
      betaEmails: ["leak@example.com"],
    })
  );

  // Admin writes valid feature rollout modes
  await assertSucceeds(
    adminDb.collection("app_config").doc("features").set({
      aventonMode: "beta",
      baulVialMode: "hidden",
      reclamosMode: "public",
      proConversionMode: "beta",
      referralsMode: "public",
      preTripChecklistMode: "public",
      updatedAt: new Date(),
    })
  );

  // Unauthenticated Guest Mode and normal users can get app_config/features (zero PII)
  await assertSucceeds(unauthDb.collection("app_config").doc("features").get());
  await assertSucceeds(bobDb.collection("app_config").doc("features").get());

  // Neither guest nor Bob can list app_config (so app_config/billing stays private from guests)
  await assertFails(unauthDb.collection("app_config").get());
  await assertFails(unauthDb.collection("app_config").doc("billing").get());
  await assertFails(bobDb.collection("app_config").get());
});

test("App update config: guest & users can get app_update, only admin can write valid version payload", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const adminDb = testEnv.authenticatedContext("owner_uid", { email: "redmond262@gmail.com" }).firestore();

  // Non-admin cannot create or modify app_config/app_update
  await assertFails(
    bobDb.collection("app_config").doc("app_update").set({
      enabled: true,
      mandatory: false,
      latestVersionCode: 4,
      latestVersionName: "2.3",
      message: "Hijacked update",
      downloadUrl: "https://evil.example.com/app.apk",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin rejects invalid versionCode (< 1), unexpected keys, or tampered future timestamp
  await assertFails(
    adminDb.collection("app_config").doc("app_update").set({
      enabled: true,
      mandatory: false,
      latestVersionCode: 0,
      latestVersionName: "2.3",
      downloadUrl: "https://drive.google.com/file/d/abc/view",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    adminDb.collection("app_config").doc("app_update").set({
      enabled: true,
      mandatory: false,
      latestVersionCode: 4,
      latestVersionName: "2.3",
      downloadUrl: "https://drive.google.com/file/d/abc/view",
      unexpectedField: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    adminDb.collection("app_config").doc("app_update").set({
      enabled: true,
      mandatory: false,
      latestVersionCode: 4,
      latestVersionName: "2.3",
      downloadUrl: "https://drive.google.com/file/d/abc/view",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Admin writes valid Kotlin-mirrored app_update payload
  await assertSucceeds(
    adminDb.collection("app_config").doc("app_update").set({
      enabled: true,
      mandatory: false,
      latestVersionCode: 4,
      latestVersionName: "2.3",
      message: "Mejoras en rutas y comandos por voz.",
      downloadUrl: "https://drive.google.com/file/d/12345/view",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Guest Mode and signed-in drivers can read app_config/app_update
  await assertSucceeds(unauthDb.collection("app_config").doc("app_update").get());
  await assertSucceeds(bobDb.collection("app_config").doc("app_update").get());

  // Admin can disable update notice cleanly
  await assertSucceeds(
    adminDb.collection("app_config").doc("app_update").set({
      enabled: false,
      mandatory: false,
      latestVersionCode: 4,
      latestVersionName: "2.3",
      message: "",
      downloadUrl: "",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("User isBetaTester flag: blocks self-escalation on create/update, allows admin toggle and normal profile edits", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const adminDb = testEnv.authenticatedContext("owner_uid", { email: "redmond262@gmail.com" }).firestore();

  // Non-admin cannot create profile with isBetaTester: true
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      name: "Bob Hack",
      email: "bob@example.com",
      isBetaTester: true,
      createdAt: new Date(),
    })
  );

  // Non-admin creates normal profile (isBetaTester: false)
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      name: "Bob Conductor",
      email: "bob@example.com",
      isBetaTester: false,
      createdAt: new Date(),
    })
  );

  // Non-admin cannot self-promote to beta tester
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).update({
      isBetaTester: true,
    })
  );

  // Non-admin can update allowed profile fields when keeping isBetaTester unchanged
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).update({
      name: "Bob Ruta 2",
      isBetaTester: false,
      updatedAt: new Date(),
    })
  );

  // Admin can promote & demote user beta status
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      isBetaTester: true,
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(BOB_UID).update({
      isBetaTester: false,
      updatedAt: new Date(),
    })
  );
});

// --- Etapa 2: Road Levels, Author Precision Bonus, Temporary Alert TTL ---
test("Etapa 2 alerts: allows expiresAtMillis & reporterLevel 1-6, protects authorBonusAwarded from third parties", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const futureExpiry = Date.now() + 3 * 60 * 60 * 1000;

  await assertSucceeds(
    aliceDb.collection("alerts").doc("alert_temp_1").set({
      type: "neblina",
      latitude: -35.8,
      longitude: -58.2,
      reporterUid: ALICE_UID,
      reporterLevel: 5,
      expiresAtMillis: futureExpiry,
      authorBonusAwarded: false,
      confirmations: 1,
      notThereVotes: 0,
      status: "active",
      createdAt: new Date(),
    })
  );

  // Invalid reporterLevel > 6 must fail
  await assertFails(
    aliceDb.collection("alerts").doc("alert_bad_lvl").set({
      type: "pozo",
      latitude: -35.8,
      longitude: -58.2,
      reporterUid: ALICE_UID,
      reporterLevel: 9,
      status: "active",
      createdAt: new Date(),
    })
  );

  // Bob can confirm & renew temporary expiry on Alice's alert
  await assertSucceeds(
    bobDb.collection("alerts").doc("alert_temp_1").update({
      confirmations: 2,
      expiresAtMillis: futureExpiry + 3600000,
    })
  );

  // Bob CANNOT hijack or flip authorBonusAwarded to true on Alice's alert
  await assertFails(
    bobDb.collection("alerts").doc("alert_temp_1").update({
      authorBonusAwarded: true,
    })
  );

  // Original author Alice CAN list her own alerts by reporterUid (to claim +25 confirmation bonus)
  await assertSucceeds(
    aliceDb.collection("alerts").where("reporterUid", "==", ALICE_UID).get()
  );

  // Third-party Bob CANNOT list all alerts by reporterUid without status filter
  await assertFails(
    bobDb.collection("alerts").where("reporterUid", "==", ALICE_UID).get()
  );

  // Original author Alice CAN mark authorBonusAwarded = true once confirmed
  await assertSucceeds(
    aliceDb.collection("alerts").doc("alert_temp_1").update({
      authorBonusAwarded: true,
    })
  );
});

test("Etapa 2 user profile: allows impact counters & monthly level PRO claims while validating ranges", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice Centinela",
      email: "alice@example.com",
      xpTotal: 6200,
      pointsRuta: 1200,
      alertsCreatedCount: 40,
      alertsResolvedCount: 15,
      confirmedImpactCount: 12,
      lastWeekendProClaimMonth: "2026-11",
      createdAt: new Date(),
    })
  );

  // Negative confirmedImpactCount or invalid claim month format must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      confirmedImpactCount: -3,
    })
  );
});

// --- Seguime (Tiempo Real) tests ---
test("Seguime sessions: private link get allowed, public list denied, ownership & breadcrumbs enforced", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com" }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com" }).firestore();
  const secretId = "seguime_AbCdEf1234567890";
  const created = new Date(Date.now() - 2000);
  const expiresAt = Date.now() + 24 * 60 * 60 * 1000;

  // Unauthenticated cannot create or list seguime_sessions
  await assertFails(unauthDb.collection("seguime_sessions").get());
  await assertFails(
    unauthDb.collection("seguime_sessions").doc(secretId).set({
      ownerUid: ALICE_UID,
      driverName: "Alice",
      status: "active",
      latitude: -35.5,
      longitude: -58.0,
      expiresAtMillis: expiresAt,
      createdAt: created,
    })
  );

  // Short guessable ID (<16 chars) must be rejected
  await assertFails(
    aliceDb.collection("seguime_sessions").doc("short_1").set({
      ownerUid: ALICE_UID,
      driverName: "Alice",
      status: "active",
      latitude: -35.5,
      longitude: -58.0,
      expiresAtMillis: expiresAt,
      createdAt: created,
    })
  );

  // Valid unguessable session created by Alice succeeds
  await assertSucceeds(
    aliceDb.collection("seguime_sessions").doc(secretId).set({
      ownerUid: ALICE_UID,
      driverName: "Alice Conductora",
      vehicleSummary: "Fiat Cronos · AF 123 CD",
      destinationTitle: "Mar del Plata",
      originName: "Autovía 2 Km 90",
      status: "active",
      latitude: -35.5,
      longitude: -58.0,
      speedKmH: 98.5,
      heading: 145.0,
      offlineBuffered: false,
      recentPoints: [{ lat: -35.5, lng: -58.0, ts: Date.now() }],
      expiresAtMillis: expiresAt,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Anyone with the exact unguessable link can GET (view seguime.html?s=...) without sign-in
  await assertSucceeds(unauthDb.collection("seguime_sessions").doc(secretId).get());

  // Third party Bob CANNOT list all active Seguime sessions or overwrite Alice's session
  await assertFails(bobDb.collection("seguime_sessions").get());
  await assertFails(bobDb.collection("seguime_sessions").where("ownerUid", "==", ALICE_UID).get());
  await assertFails(
    bobDb.collection("seguime_sessions").doc(secretId).update({
      status: "cancelled",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Owner Alice can list her own sessions and update GPS / finish with status arrived
  await assertSucceeds(aliceDb.collection("seguime_sessions").where("ownerUid", "==", ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("seguime_sessions").doc(secretId).update({
      latitude: -36.2,
      longitude: -57.8,
      speedKmH: 0.0,
      status: "arrived",
      offlineBuffered: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});


