"""模拟 ReceivableLinkingService 在真实 CSV 上的行为，定位未配对的主记录。"""
import csv
from collections import defaultdict
from decimal import Decimal

CROSS_ACCOUNT = {"房贷", "车贷", "信贷", "利息"}
PATH = "moze-data.txt"

rows = []
with open(PATH, encoding="utf-8") as f:
    # TSV, header row 1
    reader = csv.reader(f, delimiter="\t")
    headers = next(reader)
    # Column order per CLAUDE.md: 账户 币种 记录类型 主类别 子类别 金额 手续费 折扣 名称 商家 日期 时间 项目 描述 标签 对象
    col = {h: i for i, h in enumerate(headers)}
    for r in reader:
        if len(r) < 12:
            continue
        rt = r[col["记录类型"]]
        if rt not in ("应收款项", "应付款项"):
            continue
        try:
            amt = Decimal(r[col["金额"]].replace(",", ""))
        except Exception:
            continue
        rows.append({
            "account": r[col["账户"]],
            "recordType": rt,
            "subCategory": r[col["子类别"]],
            "amount": amt,
            "name": r[col["名称"]],
            "date": r[col["日期"]],
            "time": r[col["时间"]] if len(r) > col["时间"] else "",
        })

print(f"Total 应收应付 records: {len(rows)}")
# Debug: list records for group (empty name, 借入, 应付款项)
target = [r for r in rows if r["name"] == "" and r["subCategory"] == "借入" and r["recordType"] == "应付款项"]
print(f"DEBUG empty-name 借入 应付款项 count: {len(target)}")
for r in target:
    print(f"  {r['date']} {r['time']} {r['account']} amt={r['amount']}")

def is_parent(r):
    if r["recordType"] == "应收款项":
        return r["amount"] < 0
    return r["amount"] > 0

def group_key(r):
    return (r["subCategory"], r["name"], r["recordType"])

def fallback_key(r):
    return (r["subCategory"], r["recordType"])

# Parse date for sorting
def parse_date(s):
    parts = s.split("/")
    return (int(parts[0]), int(parts[1]), int(parts[2]))

def parse_time(s):
    if not s:
        return (0, 0, 0)
    parts = s.split(":")
    return tuple(int(p) for p in parts + ["0"] * (3 - len(parts)))

# Group
groups = defaultdict(list)
for r in rows:
    groups[group_key(r)].append(r)

total_linked = 0
unpaired_parents = []  # list of [record, remaining]
orphan_children = []

# Pass 1: primary FIFO by (subCategory, name, recordType)
def simulate(group, strategy):
    """返回 (paired_child_to_parent: dict id->parent_id, leftover_queue_remaining: list of (record, remaining), orphans)"""
    queue = []
    pair = {}
    orphans = []
    for r in group:
        if is_parent(r):
            queue.append([r, r["amount"].copy_abs()])
            continue
        if not queue:
            orphans.append(r)
            continue
        child_abs = r["amount"].copy_abs()
        # Exact match first (anywhere in queue)
        exact_idx = None
        if strategy == "lifo":
            rng = range(len(queue) - 1, -1, -1)
        else:
            rng = range(len(queue))
        for i in rng:
            if queue[i][1] == child_abs:
                exact_idx = i
                break
        if exact_idx is not None:
            pick = exact_idx
        elif strategy == "lifo":
            pick = len(queue) - 1
        else:  # fifo
            pick = 0
        head = queue[pick]
        pair[id(r)] = head[0]
        if strategy == "fifo":
            # FIFO allows overflow cascade
            remaining = child_abs
            cur_pick = pick
            while remaining > 0 and queue:
                h = queue[cur_pick]
                take = min(h[1], remaining)
                h[1] -= take
                remaining -= take
                if h[1] <= 0:
                    queue.pop(cur_pick)
                    if cur_pick >= len(queue):
                        break
                else:
                    break
        else:
            head[1] -= child_abs
            if head[1] <= 0:
                queue.pop(pick)
    return pair, queue, orphans

for key, group in groups.items():
    group.sort(key=lambda r: (parse_date(r["date"]), 0 if is_parent(r) else 1, parse_time(r["time"])))
    pair_l, q_l, orp_l = simulate(list(group), "lifo")
    pair_f, q_f, orp_f = simulate(list(group), "fifo")
    sum_l = sum(it[1] for it in q_l)
    sum_f = sum(it[1] for it in q_f)
    # Rule: default LIFO (matches Moze's per-repayment manual linking).
    # Fall back to FIFO only when FIFO fully clears the group (sum_f == 0) but
    # LIFO didn't — indicating a "single big loan, many installments" pattern
    # where children collectively cover parents.
    if sum_f == 0 and sum_l > 0:
        pair, queue, orph = pair_f, q_f, orp_f
    else:
        pair, queue, orph = pair_l, q_l, orp_l
    total_linked += len(pair)
    orphan_children.extend(orph)
    for item in queue:
        unpaired_parents.append(item)

# Pass 2: fallback — orphan children try to pair with unpaired EMPTY-NAME parents only.
fallback_queues = defaultdict(list)
named_unpaired = []  # explicitly-named parents stay as-is
for item in unpaired_parents:
    if not item[0]["name"]:
        fallback_queues[fallback_key(item[0])].append(item)
    else:
        named_unpaired.append(item)
for fk, q in fallback_queues.items():
    q.sort(key=lambda it: (parse_date(it[0]["date"]), parse_time(it[0]["time"])))

still_orphan = []
orphan_children.sort(key=lambda r: (parse_date(r["date"]), parse_time(r["time"])))
for c in orphan_children:
    q = fallback_queues.get(fallback_key(c))
    if not q:
        still_orphan.append(c)
        continue
    head = q[0]
    total_linked += 1
    head[1] -= c["amount"].copy_abs()
    if head[1] <= 0:
        q.pop(0)
orphan_children = still_orphan
unpaired_parents = named_unpaired + [item for q in fallback_queues.values() for item in q]

# Pass 3: same-date + same-abs match (catches pairs where one side has empty name
# or name-variant that primary grouping missed, like 1月房贷 / 中信银行 empty-name pair)
pass3_orphans = []
for c in orphan_children:
    c_abs = c["amount"].copy_abs()
    c_fb = fallback_key(c)
    matched_idx = None
    for i, item in enumerate(unpaired_parents):
        p = item[0]
        if fallback_key(p) != c_fb:
            continue
        if p["date"] != c["date"]:
            continue
        if item[1] == c_abs:
            matched_idx = i
            break
    if matched_idx is not None:
        total_linked += 1
        unpaired_parents[matched_idx][1] -= c_abs
    else:
        pass3_orphans.append(c)
unpaired_parents = [it for it in unpaired_parents if it[1] > 0]
orphan_children = pass3_orphans

unpaired_total = sum((rem for _, rem in unpaired_parents), Decimal(0))
# signed net: 应付 negative, 应收 positive
net = Decimal(0)
for p, rem in unpaired_parents:
    if p["recordType"] == "应付款项":
        net -= rem
    else:
        net += rem

print(f"\nGroups: {len(groups)}")
print(f"Linked children: {total_linked}")
print(f"Unpaired parents: {len(unpaired_parents)} total_abs={unpaired_total}")
print(f"Orphan children: {len(orphan_children)}")
print(f"Net summary (signed): {net}  (expect ~-37842.18)")

print("\nUnpaired parents by name+sub:")
by_name = defaultdict(lambda: [0, Decimal(0)])
for p, rem in unpaired_parents:
    k = f"{p['subCategory']}|{p['name']}|{p['recordType']}"
    by_name[k][0] += 1
    by_name[k][1] += rem
for k, (cnt, s) in sorted(by_name.items(), key=lambda x: -x[1][1])[:30]:
    print(f"  {cnt:3d}  abs={s:>12}  {k}")

print("\nFirst 20 orphan children:")
for c in orphan_children[:20]:
    print(f"  {c['date']} {c['time']} {c['account']:20} {c['subCategory']:8} {c['name']:20} amt={c['amount']}")
