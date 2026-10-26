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
 * E2E-03: Paused doctor.
 * Add tokens, pause the session with a generic message, verify the patient
 * page shows delay messaging and no confident ETA, verify call-next is refused
 * while paused, resume, and verify the queue continues without losing order.
 */
test.describe("E2E-03 paused doctor", () => {
  test("pause suppresses ETA and mutations; resume continues", async ({ page }) => {
    resetE2eDate();

    const ownerApi = await loginApi(DEMO.owner.username, DEMO.owner.password);
    const doctorId = await firstDoctorId(ownerApi);
    const { sessionId } = await createSession(ownerApi, doctorId);

    const receptionApi = await loginApi(DEMO.reception.username, DEMO.reception.password);
    const first = await (
      await receptionApi.post(`/queue-sessions/${sessionId}/tokens`, { displayName: "Patient One" })
    ).json();
    const second = await (
      await receptionApi.post(`/queue-sessions/${sessionId}/tokens`, { displayName: "Patient Two" })
    ).json();

    // Pause with a patient-safe message.
    await receptionApi.post(`/queue-sessions/${sessionId}/pause`, {
      message: "The doctor is temporarily delayed.",
    });

    // Patient page shows the delay and no numeric ETA.
    const patientContext = await page.context().browser()!.newContext();
    const patientPage = await patientContext.newPage();
    await patientPage.goto(`/p/${first.patientUrl.split("/p/")[1]}`);
    await expect(patientPage.getByTestId("patient-eta")).toContainText("Wait time unavailable");
    await expect(patientPage.locator("body")).not.toContainText("minutes");

    // Staff UI: pause state visible; mutations disabled.
    await page.goto("/login");
    await page.getByLabel("Username").fill(DEMO.reception.username);
    await page.getByLabel("Password").fill(DEMO.reception.password);
    await page.getByRole("button", { name: "Sign in" }).click();
    // Wait for the authenticated shell before navigating (a goto mid-login aborts the request).
    await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible({ timeout: 20_000 });
    await page.goto(`/?date=${E2E_DATE}`);
    await expect(page.getByTestId("session-status")).toContainText("PAUSED");
    await expect(page.getByTestId("call-next")).toBeDisabled();
    await expect(page.getByTestId("add-patient")).toBeDisabled();

    // Resume restores normal behavior and order is preserved.
    await page.getByRole("button", { name: "Resume" }).click();
    await expect(page.getByTestId("session-status")).toContainText("OPEN");
    await expect(page.getByTestId("call-next")).toBeEnabled();
    await page.getByTestId("call-next").click();
    await expect(page.getByTestId("now-serving")).toContainText("A1");

    // After resume + call-next, A1 is being called: the patient page tells them
    // to proceed instead of showing a numeric wait estimate.
    await expect(patientPage.getByTestId("patient-status")).toContainText("called", { timeout: 15_000 });
    expect(second.label).toBe("A2");

    await receptionApi.dispose();
    await ownerApi.dispose();
    await patientContext.close();
  });
});
