const { initializeTestEnvironment, assertFails, assertSucceeds } = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = "demo-no-project";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: fs.readFileSync("firestore.rules", "utf8"),
      host: "127.0.0.1",
      port: 8085
    }
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

test("unauthenticated read fails on subscriptions", async () => {
  const unauthedDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthedDb.collection("subscriptions").doc("sub1").get());
});

test("user can create their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  await assertSucceeds(aliceDb.collection("users").doc("alice").set({
    userId: "alice",
    email: "alice@example.com",
    displayName: "Dr. Alice",
    role: "DOCTOR"
  }));
});

test("user cannot create profile with mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext("alice").firestore();
  await assertFails(aliceDb.collection("users").doc("bob").set({
    userId: "bob",
    email: "bob@example.com",
    displayName: "Bob",
    role: "PATIENT"
  }));
});

test("clinic admin can create clinic and subscription", async () => {
  const adminDb = testEnv.authenticatedContext("admin1").firestore();
  await assertSucceeds(adminDb.collection("clinics").doc("clinic1").set({
    clinicId: "clinic1",
    name: "Sehat Clinic Care",
    adminUid: "admin1",
    subscriptionStatus: "TRIAL"
  }));

  await assertSucceeds(adminDb.collection("subscriptions").doc("sub1").set({
    clinicId: "clinic1",
    adminUid: "admin1",
    planName: "SehatFlow Pro (₹499/mo)",
    amount: 499,
    status: "ACTIVE"
  }));
});
