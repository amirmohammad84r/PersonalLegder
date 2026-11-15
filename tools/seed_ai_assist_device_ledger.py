#!/usr/bin/env python3
"""
Idempotent AI-assistant test seed merger for PersonalLedger ledger.json.

- Does NOT delete or rewrite existing user records.
- Only inserts entities whose ids start with SEED_PREFIX.
- Re-running is a no-op once those ids already exist.
"""

from __future__ import annotations

import argparse
import calendar
import json
import random
from collections import Counter, defaultdict
from copy import deepcopy
from datetime import date, timedelta
from pathlib import Path

SEED_PREFIX = "seed-aiassist-v1"
SEED_MARKER = "[SEED-AI]"


def epoch_days(d: date) -> int:
    return (d - date(1970, 1, 1)).days


def ms_for_day(d: date, salt: int) -> int:
    # Stable synthetic timestamps within the day (no wall-clock dependency).
    return calendar.timegm(d.timetuple()) * 1000 + (salt % 86_400_000)


def already_seeded(snapshot: dict) -> bool:
    ids = []
    for key in ("transactions", "goals", "goalAllocations", "accounts", "categories"):
        for item in snapshot.get(key, []):
            ids.append(str(item.get("id", "")))
    return any(i.startswith(SEED_PREFIX) for i in ids)


def pick_cat(cats: list[dict], typ: str, name_contains: str | None = None) -> dict:
    pool = [c for c in cats if c["type"] == typ]
    if name_contains:
        hit = [c for c in pool if name_contains in c["name"]]
        if hit:
            return hit[0]
    if not pool:
        raise SystemExit(f"No category of type {typ}")
    return pool[0]


def build_seed(snapshot: dict, today: date) -> dict:
    out = deepcopy(snapshot)
    cats = out["categories"]
    accounts = out["accounts"]
    if not accounts:
        raise SystemExit("No accounts on device; refusing to invent primary cash account.")

    cash = accounts[0]
    # Extra synthetic account for multi-account coverage (marked).
    seed_card = {
        "id": f"{SEED_PREFIX}-acc-card",
        "name": f"{SEED_MARKER} کارت آزمایشی",
        "initialBalanceRial": 5_000_000,
    }
    if not any(a["id"] == seed_card["id"] for a in accounts):
        accounts.append(seed_card)

    cat_salary = pick_cat(cats, "INCOME", "حقوق")
    cat_gift = pick_cat(cats, "INCOME", "هدیه")
    cat_other_in = pick_cat(cats, "INCOME", "سایر")
    cat_food = pick_cat(cats, "EXPENSE", "خوراک")
    cat_transport = pick_cat(cats, "EXPENSE", "حمل")
    cat_shop = pick_cat(cats, "EXPENSE", "خرید")
    cat_health = pick_cat(cats, "EXPENSE", "سلامت")
    cat_other_ex = pick_cat(cats, "EXPENSE", "سایر")

    start = today.replace(day=1)
    # six calendar months inclusive of current month → go back 5 months from month start
    for _ in range(5):
        start = (start.replace(day=1) - timedelta(days=1)).replace(day=1)
    end = today

    rng = random.Random(20261116)  # deterministic content
    txs: list[dict] = []
    tx_i = 0

    def add_tx(
        *,
        typ: str,
        amount: int,
        title: str,
        category_id: str,
        account_id: str,
        day: date,
    ) -> None:
        nonlocal tx_i
        tx_i += 1
        tid = f"{SEED_PREFIX}-tx-{tx_i:04d}"
        created = ms_for_day(day, tx_i * 9973)
        txs.append(
            {
                "id": tid,
                "type": typ,
                "amountRial": int(amount),
                "title": title,
                "categoryId": category_id,
                "accountId": account_id,
                "dateEpochDays": epoch_days(day),
                "createdAtEpochMs": created,
                "updatedAtEpochMs": created,
            }
        )

    # Monthly salary on ~1st (or next weekday-ish) for each month in range
    cursor = start
    month_index = 0
    while cursor <= end:
        month_index += 1
        salary_day = min(cursor.replace(day=1), end)
        # keep salary near month start; if before range start clamp
        if salary_day < start:
            salary_day = start
        salary = 85_000_000 + month_index * 1_500_000
        add_tx(
            typ="INCOME",
            amount=salary,
            title="واریز حقوق ماهانه",
            category_id=cat_salary["id"],
            account_id=cash["id"],
            day=salary_day,
        )
        # occasional gift / side income
        if month_index % 2 == 0:
            gift_day = min(cursor.replace(day=min(18, 28)), end)
            if gift_day >= start:
                add_tx(
                    typ="INCOME",
                    amount=3_500_000 + month_index * 100_000,
                    title="هدیه مناسبتی",
                    category_id=cat_gift["id"],
                    account_id=seed_card["id"],
                    day=gift_day,
                )
        if month_index % 3 == 0:
            side_day = min(cursor.replace(day=min(12, 28)), end)
            if side_day >= start:
                add_tx(
                    typ="INCOME",
                    amount=7_200_000,
                    title="درآمد متفرقه پروژه",
                    category_id=cat_other_in["id"],
                    account_id=cash["id"],
                    day=side_day,
                )

        # Month end / next month boundary expenses
        if cursor.month == 12:
            next_month = date(cursor.year + 1, 1, 1)
        else:
            next_month = date(cursor.year, cursor.month + 1, 1)
        last_day = next_month - timedelta(days=1)
        if last_day > end:
            last_day = end
        if last_day >= start:
            add_tx(
                typ="EXPENSE",
                amount=2_450_000,
                title="قبض پایان ماه",
                category_id=cat_other_ex["id"],
                account_id=cash["id"],
                day=last_day,
            )

        # advance month
        cursor = next_month

    # Daily-ish expenses with gaps
    day = start
    while day <= end:
        # ~35% of days quiet
        roll = rng.randrange(100)
        if roll < 35:
            day += timedelta(days=1)
            continue
        account = cash["id"] if rng.random() < 0.72 else seed_card["id"]
        n = 1 if roll < 75 else 2
        for k in range(n):
            kind = rng.choices(
                [
                    ("food", cat_food, ["صبحانه بیرون", "ناهار اداری", "خرید مواد غذایی", "قهوه و میان‌وعده"]),
                    ("transport", cat_transport, ["کرایه تاکسی", "شارژ کارت مترو", "بنزین", "اسنپ شهری"]),
                    ("shop", cat_shop, ["خرید خانه", "لوازم مصرفی", "پوشاک سبک", "سفارش آنلاین"]),
                    ("health", cat_health, ["داروخانه", "ویزیت عمومی", "مکمل غذایی"]),
                    ("other", cat_other_ex, ["اینترنت ماهانه", "تعمیر جزئی", "هزینه متفرقه"]),
                ],
                weights=[34, 22, 18, 10, 16],
                k=1,
            )[0]
            _, cat, titles = kind
            title = rng.choice(titles)
            # amounts: small / medium / large
            band = rng.choices(["s", "m", "l"], weights=[55, 35, 10], k=1)[0]
            if band == "s":
                amount = rng.randrange(80_000, 650_000, 10_000)
            elif band == "m":
                amount = rng.randrange(700_000, 4_500_000, 50_000)
            else:
                amount = rng.randrange(5_000_000, 28_000_000, 100_000)
            # Some months intentionally spendy: inflate in months where month_index odd via day
            if ((day.month % 2) == 1) and band != "s":
                amount = int(amount * 1.15)
            add_tx(
                typ="EXPENSE",
                amount=amount,
                title=title,
                category_id=cat["id"],
                account_id=account,
                day=day,
            )
        day += timedelta(days=1)

    # Clamp to ~120-180 by trimming excess expenses from quiet middle if needed
    incomes = [t for t in txs if t["type"] == "INCOME"]
    expenses = [t for t in txs if t["type"] == "EXPENSE"]
    # Prefer keeping incomes; trim expenses from the middle of the list if too many
    target_total = 150
    if len(txs) > 180:
        keep_exp = max(0, 180 - len(incomes))
        expenses = expenses[:keep_exp]
        txs = incomes + expenses
        # re-number not needed; ids already assigned
    elif len(txs) < 120:
        # add a few more mid-range expenses near month boundaries
        extra_needed = 120 - len(txs)
        for i in range(extra_needed):
            d = start + timedelta(days=(i * 11) % max(1, (end - start).days))
            add_tx(
                typ="EXPENSE",
                amount=1_250_000 + i * 17_000,
                title="هزینه تکمیل داده آزمایشی",
                category_id=cat_other_ex["id"],
                account_id=cash["id"],
                day=d,
            )

    # Goals (5) + allocations — do not touch existing goals
    now_ms = ms_for_day(today, 42)
    goals = [
        {
            "id": f"{SEED_PREFIX}-goal-low",
            "name": f"{SEED_MARKER} صندوق اضطراری",
            "targetAmountRial": 50_000_000,
            "targetDateEpochDays": epoch_days(today + timedelta(days=180)),
            "iconKey": "savings",
            "status": "ACTIVE",
            "completedAtEpochMs": None,
            "createdAtEpochMs": now_ms - 5_000_000,
            "updatedAtEpochMs": now_ms,
        },
        {
            "id": f"{SEED_PREFIX}-goal-half",
            "name": f"{SEED_MARKER} پس‌انداز سفر",
            "targetAmountRial": 80_000_000,
            "targetDateEpochDays": epoch_days(today + timedelta(days=120)),
            "iconKey": "travel",
            "status": "ACTIVE",
            "completedAtEpochMs": None,
            "createdAtEpochMs": now_ms - 4_000_000,
            "updatedAtEpochMs": now_ms,
        },
        {
            "id": f"{SEED_PREFIX}-goal-near",
            "name": f"{SEED_MARKER} لپ‌تاپ",
            "targetAmountRial": 60_000_000,
            "targetDateEpochDays": epoch_days(today + timedelta(days=45)),
            "iconKey": "device",
            "status": "ACTIVE",
            "completedAtEpochMs": None,
            "createdAtEpochMs": now_ms - 3_000_000,
            "updatedAtEpochMs": now_ms,
        },
        {
            "id": f"{SEED_PREFIX}-goal-done",
            "name": f"{SEED_MARKER} دوره آموزشی",
            "targetAmountRial": 20_000_000,
            "targetDateEpochDays": epoch_days(today - timedelta(days=20)),
            "iconKey": "education",
            "status": "COMPLETED",
            "completedAtEpochMs": now_ms - 100_000,
            "createdAtEpochMs": now_ms - 6_000_000,
            "updatedAtEpochMs": now_ms - 100_000,
        },
        {
            "id": f"{SEED_PREFIX}-goal-future",
            "name": f"{SEED_MARKER} پیش‌پرداخت مسکن",
            "targetAmountRial": 200_000_000,
            "targetDateEpochDays": epoch_days(today + timedelta(days=365)),
            "iconKey": "home",
            "status": "ACTIVE",
            "completedAtEpochMs": None,
            "createdAtEpochMs": now_ms - 2_000_000,
            "updatedAtEpochMs": now_ms,
        },
    ]

    alloc_specs = [
        # low progress ~10%
        ("goal-low", [2_000_000, 1_500_000, 1_000_000], [20, 60, 110]),
        # half ~50%
        ("goal-half", [15_000_000, 12_000_000, 13_000_000], [30, 80, 140]),
        # near ~90%
        ("goal-near", [20_000_000, 18_000_000, 16_000_000], [25, 70, 130]),
        # completed >= 100%
        ("goal-done", [10_000_000, 10_000_000], [40, 90]),
        # future small start
        ("goal-future", [5_000_000, 3_000_000], [50, 150]),
    ]
    allocations: list[dict] = []
    ai = 0
    for goal_key, amounts, day_offsets in alloc_specs:
        gid = f"{SEED_PREFIX}-{goal_key}"
        for amount, off in zip(amounts, day_offsets):
            ai += 1
            d = start + timedelta(days=min(off, max(0, (end - start).days)))
            if d > end:
                d = end
            allocations.append(
                {
                    "id": f"{SEED_PREFIX}-alloc-{ai:03d}",
                    "goalId": gid,
                    "accountId": cash["id"] if ai % 2 else seed_card["id"],
                    "amountRial": int(amount),
                    "dateEpochDays": epoch_days(d),
                    "note": f"{SEED_MARKER} مشارکت آزمایشی",
                    "createdAtEpochMs": ms_for_day(d, ai),
                    "updatedAtEpochMs": ms_for_day(d, ai),
                }
            )

    # Merge: append only missing seed ids
    existing_tx_ids = {t["id"] for t in out.get("transactions", [])}
    existing_goal_ids = {g["id"] for g in out.get("goals", [])}
    existing_alloc_ids = {a["id"] for a in out.get("goalAllocations", [])}

    out["transactions"] = list(out.get("transactions", [])) + [
        t for t in txs if t["id"] not in existing_tx_ids
    ]
    out["goals"] = list(out.get("goals", [])) + [
        g for g in goals if g["id"] not in existing_goal_ids
    ]
    out["goalAllocations"] = list(out.get("goalAllocations", [])) + [
        a for a in allocations if a["id"] not in existing_alloc_ids
    ]
    return out


def summarize(snapshot: dict, prefix: str = SEED_PREFIX) -> dict:
    txs = [t for t in snapshot.get("transactions", []) if str(t["id"]).startswith(prefix)]
    goals = [g for g in snapshot.get("goals", []) if str(g["id"]).startswith(prefix)]
    allocs = [a for a in snapshot.get("goalAllocations", []) if str(a["id"]).startswith(prefix)]
    by_month: dict[str, Counter] = defaultdict(Counter)
    income = expense = 0
    for t in txs:
        d = date(1970, 1, 1) + timedelta(days=int(t["dateEpochDays"]))
        key = f"{d.year:04d}-{d.month:02d}"
        by_month[key][t["type"]] += 1
        if t["type"] == "INCOME":
            income += int(t["amountRial"])
        else:
            expense += int(t["amountRial"])
    return {
        "seed_transactions": len(txs),
        "seed_income_count": sum(1 for t in txs if t["type"] == "INCOME"),
        "seed_expense_count": sum(1 for t in txs if t["type"] == "EXPENSE"),
        "seed_income_rial": income,
        "seed_expense_rial": expense,
        "seed_goals": len(goals),
        "seed_allocations": len(allocs),
        "seed_accounts": sum(1 for a in snapshot.get("accounts", []) if str(a["id"]).startswith(prefix)),
        "monthly": {k: dict(v) for k, v in sorted(by_month.items())},
        "total_transactions_all": len(snapshot.get("transactions", [])),
        "total_goals_all": len(snapshot.get("goals", [])),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--today", default=None, help="YYYY-MM-DD (device today)")
    args = parser.parse_args()
    snapshot = json.loads(Path(args.input).read_text(encoding="utf-8"))
    today = date.fromisoformat(args.today) if args.today else date.today()

    if already_seeded(snapshot):
        Path(args.output).write_text(
            json.dumps(snapshot, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        print(json.dumps({"status": "already_seeded", "summary": summarize(snapshot)}, ensure_ascii=False))
        return

    merged = build_seed(snapshot, today)
    Path(args.output).write_text(
        json.dumps(merged, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"status": "seeded", "summary": summarize(merged)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
