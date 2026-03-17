# Add Back Button to Create Strategy Page Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development to implement this plan. Steps use checkbox syntax for tracking.

**Goal:** Add a top navigation bar with back button to the create strategy page for better mobile usability, and fix incorrect navigation paths.

**Architecture:** Wrap the existing form content with the `MobileLayout` component that's already used by other pages (StrategyDetail). This adds the top navigation bar with back button automatically while maintaining consistency with existing design. Fix two incorrect navigation paths that still use the wrong `/m/` prefix.

**Tech Stack:** Vue 3 Composition API, Vue Router, existing MobileLayout component

---

### Task 1: Add failing test for navigation path fixes

**Files:**
- Modify: `tests/views/gridtrading/Home.test.js` (add tests)
- No new files needed

- [ ] **Step 1: Add two new test cases to existing test file**

```javascript
it('after successful creation should navigate to /grid/strategy/:id', () => {
  // Tests that the redirect path uses correct /grid/ prefix
});

it('after successful OCR import should navigate to /grid/strategy/:id', () => {
  // Tests that the OCR redirect path uses correct /grid/ prefix
});
```

- [ ] **Step 2: Run tests to confirm current implementation fails**

Run:
```bash
cd frontend && npx vitest run tests/views/gridtrading/Home.test.js
```
Expected: Tests will fail because `StrategyCreate` still uses `/m/strategy/{id}`

---

### Task 2: Wrap StrategyCreate with MobileLayout component

**Files:**
- Modify: `src/views/gridtrading/StrategyCreate.vue`

- [ ] **Step 1: Import MobileLayout component and update template**

Change template from:
```vue
<template>
  <div class="mobile-create">
    <!-- form content -->
  </div>
</template>
```

To:
```vue
<template>
  <MobileLayout title="创建策略" :show-back="true" :show-tab-bar="false">
    <div class="mobile-create">
      <!-- existing form content remains exactly the same -->
    </div>
  </MobileLayout>
</template>
```

Add import in script:
```javascript
import MobileLayout from './Layout.vue'
```

- [ ] **Step 3: Run tests again**

Existing tests should still pass (the navigation path tests will still fail, which is expected)

---

### Task 3: Fix incorrect navigation paths after creation

**Files:**
- Modify: `src/views/gridtrading\StrategyCreate.vue:237, 277`

- [ ] **Step 1: Fix the two router.replace paths**

Change from:
```javascript
router.replace(`/m/strategy/${strategyId}`)
```

To:
```javascript
router.replace(`/grid/strategy/${strategyId}`)
```

- [ ] **Step 2: Run tests to verify all tests now pass**

Run:
```bash
cd frontend && npx vitest run tests/views/gridtrading/Home.test.js
```
Expected: All 5 tests pass

---

### Task 4: Verify and commit

**Files:**
- `src/views/gridtrading/StrategyCreate.vue`
- `tests/views/gridtrading/Home.test.js`
- `docs/superpowers/specs/...`
- `docs/superpowers/plans/...`

- [ ] **Step 1: Verify all tests pass**

- [ ] **Step 2: Commit changes**

```bash
git add src/views/gridtrading/StrategyCreate.vue tests/views/gridtrading/Home.test.js docs/superpowers/specs/2026-03-17-add-back-button-to-create-strategy-page-design.md docs/superpowers/plans/2026-03-17-add-back-button-to-create-strategy-page-plan.md
git commit -m "feat: add back button to create strategy page + fix navigation paths"
```
