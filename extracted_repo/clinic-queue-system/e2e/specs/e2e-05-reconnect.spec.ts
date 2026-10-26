import { expect, test } from "@playwright/test";
import {
  DEMO,
  E2E_DATE,
  createSession,
  firstDoctorId,
  loginApi,
  resetE2eDate,
} from "../helpers";

/**
 * E2E-05: Reconnect.
 * Load the staff dashboard, interrupt the network, change the queue through
 * another session, restore the network, and verify the snapshot refresh
 * converges to the authoritative server state.
 */
test.describe("E2E-05 reconnect", () => {
  test("snapshot refresh converges after network interruption", async ({ page, context }) => {
    resetE2eDate();

    const ownerApi = await loginApi(DEMO.owner.username, DEMO.owner.password);
    const doctorId = await firstDoctorId(ownerApi);
    const { sessionId } = await createSession(ownerApi, doctorId);
    await ownerApi.dispose();

    // Receptionist logs in.
    await page.goto("/login");
    await page.getByLabel("Username").fill(DEMO.reception.username);
    await page.getByLabel("Password").fill(DEMO.reception.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    // Wait for the authenticated shell before navigating (a goto mid-login aborts the request).
    await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible({ timeout: 20_000 });
    await page.goto(`/?date=${E2E_DATE}`);
    await expect(page.getByTestId("token-table")).toBeVisible({ timeout: 20_000 });

    await page.getByTestId("add-patient").click();
    await expect(page.getByTestId("token-row-A1")).toBeVisible();

    // Go offline: mutations must be disabled.
    await context.setOffline(true);
    await page.evaluate(() => window.dispatchEvent(new Event("offline")));
    await expect(page.getByTestId("offline-banner")).toBeVisible();
    await expect(page.getByTestId("add-patient")).toBeDisabled();
    await expect(page.getByTestId("call-next")).toBeDisabled();

    // Another authorized session mutates the queue while we are offline.
    const otherApi = await loginApi(DEMO.owner.username, DEMO.owner.password);
    await otherApi.post(`/queue-sessions/${sessionId}/tokens`, { displayName: "Offline Change" });
    await otherApi.dispose();

    // Restore the network: the client refetches and converges to server state.
    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await expect(page.getByTestId("offline-banner")).toBeHidden();
    await expect(page.getByTestId("token-row-A2")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId("add-patient")).toBeEnabled();
  });
});
