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

function validSavePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    heroName: "Link de Eldoria",
    level: 1,
    xp: 0,
    coins: 25,
    hp: 100,
    maxHp: 100,
    baseAttack: 12,
    baseDefense: 4,
    potions: 2,
    currentZone: "meadow",
    monstersSlain: 0,
    chestsOpened: 0,
    equippedWeaponId: "starter_sword",
    openedChestIds: [],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validItemPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    templateId: "emerald_blade",
    name: "Espada da Clareira",
    slot: "weapon",
    rarity: "rare",
    attackBonus: 18,
    defenseBonus: 0,
    hpBonus: 10,
    critBonus: 8,
    coinBonusPct: 0,
    sellPrice: 45,
    colorHex: "#10B981",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user: cannot read or write hero saves or items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("hero_saves").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("hero_saves").doc("primary").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("hero_items").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("hero_items").doc("item_1").set(validItemPayload(ALICE_UID)));
});

test("Authenticated owner: can create, read, update hero save and manage items", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const saveRef = aliceDb.collection("users").doc(ALICE_UID).collection("hero_saves").doc("primary");
  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("hero_items").doc("sword_1");

  await assertSucceeds(saveRef.set(validSavePayload(ALICE_UID)));
  await assertSucceeds(saveRef.get());
  await assertSucceeds(
    saveRef.update({
      coins: 120,
      level: 2,
      xp: 40,
      openedChestIds: ["meadow_chest_1"],
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(itemRef.set(validItemPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("hero_items").get());
  await assertSucceeds(itemRef.delete());
});

test("Cross-user isolation: Bob cannot read, create, update, or delete Alice's save or items", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("hero_saves").doc("primary").set(validSavePayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("hero_items").doc("sword_1").set(validItemPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceSaves = bobDb.collection("users").doc(ALICE_UID).collection("hero_saves");
  const aliceItems = bobDb.collection("users").doc(ALICE_UID).collection("hero_items");

  await assertFails(aliceSaves.get());
  await assertFails(aliceSaves.doc("primary").get());
  await assertFails(aliceSaves.doc("primary").update({ coins: 9999, updatedAt: new Date(Date.now() - 100) }));
  await assertFails(aliceItems.get());
  await assertFails(aliceItems.doc("sword_1").delete());
  await assertFails(aliceItems.doc("hack").set(validItemPayload(BOB_UID)));
});

test("Schema hardening: rejects ghost fields, invalid enums, spoofed UIDs, and immutable createdAt tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const saves = aliceDb.collection("users").doc(ALICE_UID).collection("hero_saves");
  const items = aliceDb.collection("users").doc(ALICE_UID).collection("hero_items");

  await assertFails(saves.doc("ghost").set(validSavePayload(ALICE_UID, { isGodMode: true })));
  await assertFails(saves.doc("bad_zone").set(validSavePayload(ALICE_UID, { currentZone: "space" })));
  await assertFails(saves.doc("spoof").set(validSavePayload(BOB_UID)));

  await assertFails(items.doc("bad_rarity").set(validItemPayload(ALICE_UID, { rarity: "mythical" })));
  await assertFails(items.doc("bad_color").set(validItemPayload(ALICE_UID, { colorHex: "red" })));
  await assertFails(items.doc("ghost_item").set(validItemPayload(ALICE_UID, { hacked: 1 })));

  const saveRef = saves.doc("primary");
  await assertSucceeds(saveRef.set(validSavePayload(ALICE_UID)));
  await assertFails(
    saveRef.update({
      createdAt: new Date(Date.now() - 50),
      updatedAt: new Date(Date.now() - 50),
    })
  );
  await assertFails(
    saveRef.update({
      userId: BOB_UID,
      updatedAt: new Date(Date.now() - 50),
    })
  );
});
