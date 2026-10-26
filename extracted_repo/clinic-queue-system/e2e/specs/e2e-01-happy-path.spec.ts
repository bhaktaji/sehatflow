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
 * E2E-01: Happy path.
 * Login as receptionist, add three walk-ins, verify labels and order, open the
 * first patient's public URL in a separate context, call next, start and
 * complete, then verify staff dashboard, public view and display converge.
 */
test.describe("E2E-01 happy path", () => {
  test("full queue workflow converges across views", async ({ page }) => {
    resetE2eDate();

    const ownerApi = await loginApi(DEMO.owner.username, DEMO.owner.password);
    const doctorId = await firstDoctorId(ownerApi);
    const { sessionId, displaySecret } = await createSession(ownerApi, doctorId);
    await ownerApi.dispose();

    // 1. Receptionist logs in through the UI.
    await page.goto("/login");
    await page.getByLabel("Username").fill(DEMO.reception.username);
    await page.getByLabel("Password").fill(DEMO.reception.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    // Wait for the authenticated shell before navigating (a goto mid-login aborts the request).
    await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible({ timeout: 20_000 });
    await page.goto(`/?date=${E2E_DATE}`);

    await expect(page.getByTestId("token-table")).toBeVisible({ timeout: 20_000 });

    // 2. Add three walk-ins.
    await page.getByTestId("add-patient").click();
    await expect(page.getByTestId("token-row-A1")).toBeVisible();
    await page.getByTestId("add-patient").click();
    await expect(page.getByTestId("token-row-A2")).toBeVisible();
    await page.getByTestId("add-patient").click();
    await expect(page.getByTestId("token-row-A3")).toBeVisible();

    // 3. Order is arrival order by default.
    const labels = await page.locator('[data-testid^="token-row-"]').evaluateAll((rows) =>
      rows.map((row) => row.getAttribute("data-testid")),
    );
    expect(labels).toEqual(["token-row-A1", "token-row-A2", "token-row-A3"]);

    // 4. Add a fourth patient via the API and open its public URL in a
    //    separate browser context (no login).
    const receptionApi = await loginApi(DEMO.reception.username, DEMO.reception.password);
    const checkIn = await receptionApi.post(`/queue-sessions/${sessionId}/tokens`, {
      displayName: "Extra Patient",
    });
    const created = await checkIn.json();

    const patientContext = await page.context().browser()!.newContext();
    const patientPage = await patientContext.newPage();
    await patientPage.goto(`/p/${created.patientUrl.split("/p/")[1]}`);
    await expect(patientPage.getByTestId("patient-token")).toHaveText("A4");
    await expect(patientPage.getByTestId("people-ahead")).toContainText("3 people ahead of you");

    // 5. Call next, start, complete via the UI.
    await page.getByTestId("call-next").click();
    await expect(page.getByTestId("now-serving")).toContainText("A1");
    await page.getByRole("button", { name: "Start", exact: true }).click();
    await expect(page.getByTestId("now-serving")).toContainText("A1");
    await page.getByRole("button", { name: "Complete", exact: true }).click();
    await expect(page.getByTestId("token-row-A1")).toContainText("Completed");

    // 6. Views converge to the same server state.
    await expect(patientPage.getByTestId("patient-token")).toHaveText("A4");
    await expect(patientPage.getByTestId("people-ahead")).toContainText("2 people ahead of you");

    const displayPage = await patientContext.newPage();
    await displayPage.goto(`/display/${displaySecret}`);
    // A1 is completed and nothing has been called since, so the display shows
    // the placeholder while A2..A4 appear in the "next" list.
    await expect(displayPage.getByTestId("display-now-serving")).toHaveText("—");
    await expect(displayPage.locator("body")).toContainText("A2");
    // Display never shows patient names.
    await expect(displayPage.locator("body")).not.toContainText("Extra Patient");

    await receptionApi.dispose();
    await patientContext.close();
  });
});
