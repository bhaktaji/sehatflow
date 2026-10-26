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
 * E2E-02: Priority override.
 * Add three tokens, move the third to the front with a reason, verify order
 * and audit record, and verify the public page discloses no priority info.
 */
test.describe("E2E-02 priority override", () => {
  test("priority reorder with reason is audited and hidden from patients", async ({ page }) => {
    resetE2eDate();

    const ownerApi = await loginApi(DEMO.owner.username, DEMO.owner.password);
    const doctorId = await firstDoctorId(ownerApi);
    const { sessionId } = await createSession(ownerApi, doctorId);

    const receptionApi = await loginApi(DEMO.reception.username, DEMO.reception.password);
    const responses = [];
    for (let i = 0; i < 3; i++) {
      const res = await receptionApi.post(`/queue-sessions/${sessionId}/tokens`, {
        displayName: `Patient ${i + 1}`,
      });
      responses.push(await res.json());
    }
    await ownerApi.dispose();

    // Log in as receptionist in the UI.
    await page.goto("/login");
    await page.getByLabel("Username").fill(DEMO.reception.username);
    await page.getByLabel("Password").fill(DEMO.reception.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    // Wait for the authenticated shell before navigating (a goto mid-login aborts the request).
    await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible({ timeout: 20_000 });
    await page.goto(`/?date=${E2E_DATE}`);
    await expect(page.getByTestId("token-table")).toBeVisible();

    // Move the third token to position 1 with a reason.
    await page.getByTestId("token-row-A3").getByRole("button", { name: "Reorder" }).click();
    await page.getByRole("dialog").getByLabel(/Reason/).fill("Approved by clinician");
    await page.getByRole("dialog").getByRole("button", { name: "Confirm" }).click();

    // Order changed: A3 moves to the front (retried until the refresh lands).
    await expect(page.locator('[data-testid^="token-row-"]').first()).toHaveAttribute(
      "data-testid",
      "token-row-A3",
    );

    // Audit shows actor, reason and action.
    await page.getByRole("button", { name: "Show audit" }).click();
    const auditTable = page.getByTestId("audit-table");
    await expect(auditTable).toContainText("TOKEN_REORDERED");
    await expect(auditTable).toContainText("Approved by clinician");
    await receptionApi.dispose();

    // Public page discloses no priority flag, reason or other patient details.
    const patientContext = await page.context().browser()!.newContext();
    const patientPage = await patientContext.newPage();
    await patientPage.goto(`/p/${responses[2].patientUrl.split("/p/")[1]}`);
    await expect(patientPage.getByTestId("patient-token")).toHaveText("A3");
    const bodyText = (await patientPage.locator("body").innerText()).toLowerCase();
    expect(bodyText).not.toContain("priority");
    expect(bodyText).not.toContain("approved by clinician");
    expect(bodyText).not.toContain("patient 1");
    expect(bodyText).not.toContain("patient 2");
    await patientContext.close();
  });
});
