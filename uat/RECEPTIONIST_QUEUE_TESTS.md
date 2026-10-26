# SehatFlow UAT Report: Receptionist Queue Visibility & Scrolling Defect Fix

## 1. Executive Summary & Defect Overview
During initial field trials, clinic receptionists managing long OPD queues reported being **unable to scroll to the bottom of the live queue**, preventing visibility of the last patient and blocking critical receptionist actions (marking priority, reordering, and marking no-shows).

This report documents the **root cause analysis**, **Compose architectural fix**, and **35-patient verification test** in `ReceptionDashboardScreen.kt`.

---

## 2. Root Cause Analysis (DEF-004)

### A. Layout Constraint Issue
- **Previous Code**:
  ```kotlin
  Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      // Header items (~240dp total):
      OfflineBanner(...)
      SessionCard(...)
      MetricCardsRow(...)
      TabRow(...)

      // Live Queue List:
      LazyColumn(
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
          modifier = Modifier.fillMaxSize().testTag("reception_queue_list")
      ) { ... }
  }
  ```
- **Failure Mechanism**:
  1. `Modifier.fillMaxSize()` on `LazyColumn` placed inside an unconstrained vertical `Column` below ~240dp of fixed headers resulted in unbounded measurement. The total scrollable extent extended below the Android viewport, causing items at the bottom of large lists (e.g. patients #30–#35) to be cut off and unreachable.
  2. The `ExtendedFloatingActionButton` ("Walk-in Patient") is anchored at the bottom-right of the screen. With only `bottom = 80.dp` content padding, the FAB physically overlapped and blocked access to the 3 action buttons on the last token (`priority_action_btn`, `reorder_action_btn`, `no_show_action_btn`).

---

## 3. Engineering Fix & Verification

### A. Remediation Applied to `ReceptionDashboardScreen.kt`
1. Replaced `Modifier.fillMaxSize()` on `LazyColumn` with:
   ```kotlin
   Modifier.weight(1f).fillMaxWidth().testTag("reception_queue_list")
   ```
   This strictly bounds the `LazyColumn` measurement pass to the remaining viewport height between the tabs and the bottom window insets.
2. Increased bottom padding to `120.dp`:
   ```kotlin
   contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)
   ```
   This guarantees that when the list is scrolled to the very end, the 35th patient card scrolls completely clear of the Extended FAB.
3. Added a dedicated UAT Test Button (`btn_populate_35_uat`) to seed a 35-patient queue instantly for QA validation.

---

## 4. Test Execution & Verification

### Test Steps:
1. Seeded 35 synthetic patient tokens into the live queue (`DemoDataProvider.populateLargeQueueForTesting(35)`).
2. Opened `ReceptionDashboardScreen`.
3. Scrolled smoothly through all 35 patients from top to bottom.
4. Verified patient #35 (`label = "T-35"`, `queuePosition = 34`).
5. Verified that the bottom-most card scrolls cleanly above the Extended FAB.
6. Executed all 3 primary receptionist actions on Patient #35:
   - **Emergency Priority Toggle**: Tapped `priority_action_btn` on #35 -> status updated to Emergency with red badge.
   - **Queue Reorder**: Tapped `reorder_action_btn` on #35 -> reordered from #35 to #2.
   - **No-Show Marking**: Tapped `no_show_action_btn` on token -> successfully rejected/marked with audit reason.

---

## 5. Automated Regression Verification
- **Automated Test**: `SehatFlowUatRegressionTest.testReceptionistQueue_35PatientsReachabilityAndActionExecution` — **PASSED**
