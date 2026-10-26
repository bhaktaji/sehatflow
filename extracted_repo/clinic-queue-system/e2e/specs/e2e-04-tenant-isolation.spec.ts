import { expect, test } from "@playwright/test";
import {
  DEMO,
  E2E_DATE_B,
  createSession,
  firstDoctorId,
  loginApi,
  resetE2eDate,
  seedClinicB,
} from "../helpers";

/**
 * E2E-04: Tenant isolation.
 * Clinic B is seeded separately (SQL). Using Clinic A credentials, cross-clinic
 * GETs and POSTs must fail with no data leakage and no state mutation in
 * Clinic B.
 */
test.describe("E2E-04 tenant isolation", () => {
  test("cross-clinic access is denied with no leakage or mutation", async ({ page }) => {
    resetE2eDate();
    const clinicB = seedClinicB();

    const ownerApi = await loginApi(DEMO.owner.username, DEMO.owner.password);
    const doctorId = await firstDoctorId(ownerApi);
    const { sessionId } = await createSession(ownerApi, doctorId);
    const tokenA = await (
      await ownerApi.post(`/queue-sessions/${sessionId}/tokens`, { displayName: "Clinic A Patient" })
    ).json();

    // Clinic B logs in with its own credentials (seeded via SQL).
    const clinicBApi = await loginApi(clinicB.username, clinicB.password);
    const bDoctors = await clinicBApi.json<Array<{ id: string }>>("/doctors");
    const bDoctorId = bDoctors[0].id;
    const bSession = await (
      await clinicBApi.post("/queue-sessions", { doctorId: bDoctorId, businessDate: E2E_DATE_B })
    ).json();
    expect(bSession.id, "clinic B session must be created").toBeTruthy();
    const tokenB = await (
      await clinicBApi.post(`/queue-sessions/${bSession.id}/tokens`, { displayName: "Clinic B Patient" })
    ).json();
    await clinicBApi.dispose();

    // Clinic A (owner) attempts to read and mutate Clinic B resources.
    const crossQueue = await ownerApi.get(`/queue-sessions/${bSession.id}/queue`);
    expect(crossQueue.status()).toBe(404);
    expect(await crossQueue.text()).not.toContain("Clinic B Patient");

    const crossCall = await ownerApi.post(`/queue-sessions/${bSession.id}/call-next`);
    expect(crossCall.status()).toBe(403);
    expect(await crossCall.text()).toContain("FORBIDDEN_TENANT_SCOPE");

    const crossComplete = await ownerApi.post(`/tokens/${tokenB.id}/complete`);
    expect(crossComplete.status()).toBe(404);

    const crossDoctors = await ownerApi.json<Array<{ id: string }>>("/doctors");
    expect(crossDoctors.map((d) => d.id)).not.toContain(bDoctorId);

    // Clinic B state is unchanged.
    const clinicBVerify = await loginApi(clinicB.username, clinicB.password);
    const bQueue = await clinicBVerify.json<{
      peopleWaiting: number;
      tokens: Array<{ id: string; displayName: string }>;
    }>(`/queue-sessions/${bSession.id}/queue`);
    expect(bQueue.peopleWaiting).toBe(1);
    expect(bQueue.tokens[0].displayName).toBe("Clinic B Patient");
    await clinicBVerify.dispose();

    // Clinic A's own data is untouched by the attempts.
    const aQueue = await ownerApi.json<{ tokens: Array<{ id: string }> }>(
      `/queue-sessions/${sessionId}/queue`,
    );
    expect(aQueue.tokens[0].id).toBe(tokenA.id);
    await ownerApi.dispose();

    // The app UI is still reachable.
    await page.goto("/login");
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible();
  });
});
