# Fix Price Trigger Execution Popup Design

## Problem
When user clicks the "Execute" button on the price trigger card in strategy detail page without any smart suggestion, the current code directly calls the API with only the `price` parameter. This results in:
- `gridLineId` is `null`
- Backend throws error: "网格线不存在: null"
- User cannot input required parameters (quantity, fee, tradeTime) before execution

## Solution
Add a confirmation popup for the price trigger execution scenario, so user can fill in required parameters before calling the backend API.

Reuse the existing `TradeExecuteDialog` component by adding a new mode instead of creating a new component to avoid code duplication.

## Scope
- Modify: `frontend/src/views/gridtrading/StrategyDetail.vue` - add new dialog state and open logic
- Modify: `frontend/src/views/gridtrading/components/TradeExecuteDialog.vue` - add price-trigger mode support
- No backend changes needed (validation logic already fixed)

## Design Details

### Component Changes: TradeExecuteDialog

Add new props to support two modes:

| Prop | Type | Description |
|------|------|-------------|
| `suggestion` | Object | **Smart suggestion mode** (existing behavior): suggestion from backend contains all fields including `gridLineId`, `type`, `price`, `quantity` |
| `price` | Number/String | **Price trigger mode** (new): only price is provided by user input, user needs to fill quantity |

Template logic:
- If `suggestion` exists → show existing suggestion view (unchanged)
- If `price` exists && `suggestion` is null → show price-trigger view with additional quantity input

### Price-trigger mode form fields:

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| **Trade Direction** | **Yes** | none | User selects BUY or SELL |
| Price | Read-only | from user input | |
| **Quantity** | **Yes** | user must input | |
| Fee | No | 0 | optional |
| Trade Date/Time | Yes | current date/time | user can modify |

### StrategyDetail Flow Changes

Current:
```javascript
const handleExecute = () => {
  if (currentSuggestion && currentSuggestion.type) {
    executeDialogVisible.value = true  // open dialog
  } else {
    await doExecute(price)  // direct call → bug!
  }
}
```

After change (**reuse same dialog instance**):
```javascript
const handleExecute = () => {
  const price = parseFloat(priceInput)
  if (!price || isNaN(price)) {
    ElMessage.warning('请输入有效的价格')
    return
  }

  if (currentSuggestion && currentSuggestion.type) {
    // existing logic: smart suggestion mode
    currentPriceForExecute.value = null
    executeDialogVisible.value = true
  } else {
    // new logic: price trigger mode → open dialog, user fills remaining fields
    currentPriceForExecute.value = price
    executeDialogVisible.value = true
  }
}
```

**We reuse the same dialog instance**, pass different props based on mode:
- When `suggestion` is not null → smart suggestion mode
- When `suggestion` is null but `price` is not null → price trigger mode

### Form Validation

Before emitting confirm event, the dialog must validate that all required fields are filled:
- **Trade direction**: must be selected (BUY or SELL)
- **Quantity**: must be > 0
- **Trade date/time**: must be selected

If validation fails, show error message and don't emit.

### Data emitted from dialog

For price-trigger mode, the dialog emits:
```javascript
{
  type: 'BUY' | 'SELL',    // trade direction (required)
  price: number,           // user input price
  quantity: number,       // user filled quantity (required)
  fee?: number,           // optional, defaults to 0
  tradeTime: string       // YYYY-MM-DD HH:mm:ss (required)
}
```

Backend will do automatic grid matching based on price. The emitted data format is compatible with existing `executeTick` API.

### Acceptance Criteria

1. ✅ Click "Execute" button with no smart suggestion → popup appears
2. ✅ Popup has: trade direction selector (BUY/SELL required), quantity input (required), fee input (optional), datetime picker (required, defaults to now)
3. ✅ User can't confirm unless all required fields are filled
4. ✅ Form validation shows error when required fields missing
5. ✅ After confirmation → API called with all required parameters (type, price, quantity, tradeTime, optional fee)
6. ✅ Backend doesn't throw "gridLineId 必填" or "网格线不存在: null"
7. ✅ Dialog resets all inputs when closed → clean state for next open
8. ✅ Smart suggestion mode continues to work unchanged (no regression)
9. ✅ All existing tests pass

## Trade-offs Considered

- **Option A (chosen):** Reuse `TradeExecuteDialog` with mode props → minimal new code, consistent UI, single source of truth
- **Option B:** Create new `PriceExecuteDialog` component → cleaner component API but more code duplication
- **Option C:** Inline dialog in `StrategyDetail` → messy, hard to maintain

## Related Files

- `frontend/src/views/gridtrading/StrategyDetail.vue` - add new dialog state and open logic
- `frontend/src/views/gridtrading/components/TradeExecuteDialog.vue` - modify to support price-trigger mode
- `app-gridtrading/src/main/java/.../StrategyService.java` - already fixed conditional validation
