import { describe, expect, it } from "vitest";
import { availableActions, etaDisplay, formatTime, patientUrl, statusClass } from "./format";
import type { QueueToken } from "../api/types";

function token(status: QueueToken["status"]): QueueToken {
  return {
    id: "t1",
    tokenNumber: 1,
    label: "A1",
    status,
    priority: false,
    queuePosition: 1,
    displayName: null,
    checkedInAt: "2026-10-19T04:00:00Z",
    calledAt: null,
    patientUrl: null,
  };
}

describe("availableActions", () => {
  it("mirrors the backend state machine", () => {
    expect(availableActions(token("WAITING"))).toMatchObject({ canSkip: true, canStart: false, canReorder: true });
    expect(availableActions(token("CALLED"))).toMatchObject({ canStart: true, canReturn: true, canReorder: false });
    expect(availableActions(token("IN_CONSULTATION"))).toMatchObject({ canComplete: true, canReturn: true });
    expect(availableActions(token("SKIPPED"))).toMatchObject({ canReturn: true, canComplete: false });
    expect(availableActions(token("COMPLETED"))).toMatchObject({
      canComplete: false, canSkip: false, canReturn: false, canCancel: false,
    });
    expect(availableActions(token("NO_SHOW")).canReturn).toBe(false);
    expect(availableActions(token("CANCELLED")).canCancel).toBe(false);
  });
});

describe("etaDisplay", () => {
  it("suppresses confident ETA when unavailable", () => {
    const display = etaDisplay({ estimatedWaitMinutes: null, estimateQuality: "UNAVAILABLE" });
    expect(display.primary).toBe("Wait time unavailable");
  });

  it("marks rough estimates as approximate", () => {
    const display = etaDisplay({ estimatedWaitMinutes: 25, estimateQuality: "LOW" });
    expect(display.primary).toContain("about 25 minutes");
    expect(display.primary).toContain("rough estimate");
    expect(display.secondary).toContain("approximate");
  });

  it("uses singular minute for one minute", () => {
    expect(etaDisplay({ estimatedWaitMinutes: 1, estimateQuality: "HIGH" }).primary).toContain("about 1 minute");
  });
});

describe("formatTime", () => {
  it("formats valid timestamps and tolerates invalid ones", () => {
    expect(formatTime("2026-10-19T04:05:00Z")).not.toBe("");
    expect(formatTime("not-a-date")).toBe("");
  });
});

describe("patientUrl", () => {
  it("builds a same-origin patient link", () => {
    const url = patientUrl("abc123");
    expect(url).toContain("/p/abc123");
    expect(url.startsWith("http")).toBe(true);
  });
});

describe("statusClass", () => {
  it("maps every status", () => {
    expect(statusClass("WAITING")).toBe("status-waiting");
    expect(statusClass("COMPLETED")).toBe("status-completed");
  });
});
