# Add Back Button to Create Strategy Page Design

## Problem
The `/grid/create` (create strategy) page does not have a top navigation bar with a back button. Users cannot easily navigate back to the strategy list page. While there is a "Cancel" button at the bottom, it's not visible without scrolling and not intuitive for mobile users.

## Solution
Wrap the entire page with the existing `MobileLayout` component, which automatically:
- Adds a top navigation bar
- Shows a back arrow icon in the top-left corner
- Displays the page title "创建策略" (Create Strategy)
- Follows the same design pattern as other pages (StrategyDetail, etc.)
- Maintains consistency with the rest of the mobile UI

## Scope
- Change only the `StrategyCreate.vue` page
- Fix existing incorrect navigation paths that still use `/m/` prefix
- No other changes to the layout or functionality

## Design Details

### Component Structure
```
StrategyCreate.vue
└── MobileLayout (title="创建策略", show-back="true")
    └── existing form content remains unchanged
```

### Navigation Behavior
- Back arrow click → `router.back()` (same behavior as existing "Cancel" button)
- After successful creation → navigate to `grid/strategy/{id}` (fix the wrong `/m/` prefix)
- After successful OCR import → navigate to `grid/strategy/{id}` (fix the wrong `/m/` prefix)

## Acceptance Criteria
1. Top navigation bar appears with "创建策略" title
2. Back arrow icon visible in top-left corner
3. Clicking back arrow returns to strategy list
4. All existing form functionality works unchanged
5. After creation success navigates correctly to strategy detail
6. After OCR import success navigates correctly to strategy detail

## Trade-offs Considered
- **Option A (chosen):** Use existing `MobileLayout` → consistent design, minimal code change, leverages existing component
- **Option B:** Add custom back button → inconsistent, more code, breaks design pattern
- **Option C:** Only improve cancel button → still not discoverable for mobile users

## Related Files
- `src/views/gridtrading/StrategyCreate.vue` - page to modify
- `src/views/gridtrading/Layout.vue` - existing MobileLayout component
