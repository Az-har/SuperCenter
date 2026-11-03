#!/usr/bin/env python3
"""
PennyWise to Super Center Profile Extractor
------------------------------------------
Reads a PennyWise JSON backup file, extracts the user's hyper-personalized
configuration (Accounts, Display Names, Aliases, Categories, Merchant Mappings,
SMS Patterns, and Debts), and outputs a clean markdown profile that can be
emailed or pasted directly to Antigravity to hyper-personalize Super Center.
"""

import sys
import json
import os
from collections import Counter
from datetime import datetime

def parse_pennywise_backup(json_path):
    if not os.path.exists(json_path):
        print(f"Error: File not found at {json_path}")
        sys.exit(1)

    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    db = data.get("database", {})
    metadata = data.get("metadata", {})
    prefs = data.get("preferences", {})

    accounts = db.get("accounts", [])
    merchant_aliases = db.get("merchant_aliases", [])
    merchant_mappings = db.get("merchant_mappings", [])
    transactions = db.get("transactions", [])
    loans = db.get("loans", [])
    rules = db.get("rules", [])
    subscriptions = db.get("subscriptions", [])
    bank_notifications = db.get("bank_notifications", [])

    # Calculate statistics
    total_tx = len(transactions)
    income_tx = [t for t in transactions if str(t.get("transactionType", "")).upper() == "INCOME"]
    expense_tx = [t for t in transactions if str(t.get("transactionType", "")).upper() in ("EXPENSE", "CREDIT")]

    total_income = sum(float(t.get("amount", 0)) for t in income_tx)
    total_expense = sum(float(t.get("amount", 0)) for t in expense_tx)

    # Top merchants by frequency and spend
    merchant_spend = Counter()
    merchant_count = Counter()
    bank_sms_senders = Counter()
    bank_names = Counter()

    for t in transactions:
        m = t.get("merchantName", "Unknown").strip()
        amt = float(t.get("amount", 0))
        merchant_spend[m] += amt
        merchant_count[m] += 1
        
        sender = t.get("smsSender")
        if sender:
            bank_sms_senders[sender] += 1
            
        bank = t.get("bankName")
        if bank:
            bank_names[bank] += 1

    # Format Markdown Profile
    lines = []
    lines.append("# 👤 My Personal Financial Profile (PennyWise -> Super Center)")
    lines.append(f"*Extracted on {datetime.now().strftime('%Y-%m-%d %H:%M:%S')} from `{os.path.basename(json_path)}`*\n")

    lines.append("## 🏦 1. Accounts, Cards & Custom Display Names")
    if not accounts:
        lines.append("_No accounts recorded in backup._\n")
    else:
        lines.append("| Bank Name | Last 4 | Account Type | Is Credit Card | User Alias / Display Name |")
        lines.append("|---|---|---|---|---|")
        for acc in accounts:
            bank = acc.get("bankName", "Unknown")
            last4 = acc.get("accountLast4", "N/A")
            acc_type = acc.get("accountType", "SAVINGS")
            is_cc = "Yes" if acc.get("isCreditCard") else "No"
            alias = acc.get("alias") or f"{bank} ••{last4}"
            lines.append(f"| **{bank}** | `{last4}` | {acc_type} | {is_cc} | **{alias}** |")
        lines.append("")

    lines.append("## 🏷️ 2. Merchant Aliases & Display Names")
    if not merchant_aliases:
        lines.append("_No custom merchant aliases recorded._\n")
    else:
        lines.append(f"*Total Custom Aliases: {len(merchant_aliases)}*\n")
        lines.append("| Raw Merchant SMS Text | Custom Clean Display Name |")
        lines.append("|---|---|")
        for ma in sorted(merchant_aliases, key=lambda x: x.get("merchantName", "")):
            raw = ma.get("merchantName", "")
            alias = ma.get("alias", "")
            lines.append(f"| `{raw}` | **{alias}** |")
        lines.append("")

    lines.append("## 📂 3. Category Mappings & Spending Rules")
    if not merchant_mappings:
        lines.append("_No custom category mappings recorded._\n")
    else:
        lines.append(f"*Total Mappings: {len(merchant_mappings)}*\n")
        lines.append("| Merchant | Assigned Category |")
        lines.append("|---|---|")
        for mm in sorted(merchant_mappings, key=lambda x: x.get("category", "")):
            merchant = mm.get("merchantName", "")
            cat = mm.get("category", "")
            lines.append(f"| {merchant} | `{cat}` |")
        lines.append("")

    lines.append("## 📲 4. Detected Banks & SMS Sender IDs")
    lines.append(f"- **Primary Banks Identified**: {', '.join(b for b, _ in bank_names.most_common(10)) or 'None'}")
    lines.append(f"- **SMS Sender Headers**: {', '.join(f'`{s}` ({c})' for s, c in bank_sms_senders.most_common(15)) or 'None'}\n")

    lines.append("## 💳 5. Top Merchants & Recurring Spending")
    lines.append("| Merchant | Transaction Count | Total Spend |")
    lines.append("|---|---|---|")
    for m, spend in merchant_spend.most_common(15):
        cnt = merchant_count[m]
        lines.append(f"| **{m}** | {cnt} txns | ₹{spend:,.2f} |")
    lines.append("")

    lines.append("## 🤝 6. Active Debts & Loans from PennyWise")
    if not loans:
        lines.append("_No loans or debt records found in backup._\n")
    else:
        lines.append("| Person / Counterparty | Initial Amount | Remaining | Type | Notes |")
        lines.append("|---|---|---|---|---|")
        for l in loans:
            person = l.get("personName", "Unknown")
            init_amt = float(l.get("initialAmount", 0))
            rem_amt = float(l.get("remainingAmount", 0))
            ltype = "Owed to Me (Lent)" if l.get("loanType") == "LENT" else "I Owe (Borrowed)"
            notes = l.get("notes") or "-"
            lines.append(f"| **{person}** | ₹{init_amt:,.2f} | ₹{rem_amt:,.2f} | {ltype} | {notes} |")
        lines.append("")

    lines.append("## 📊 7. Overall Summary")
    lines.append(f"- **Total Transactions**: {total_tx:,}")
    lines.append(f"- **Total Income Ingested**: ₹{total_income:,.2f}")
    lines.append(f"- **Total Expenses Ingested**: ₹{total_expense:,.2f}")
    lines.append(f"- **Active Subscriptions**: {len(subscriptions)}")
    lines.append(f"- **Custom Rules**: {len(rules)}")
    lines.append("")

    lines.append("---")
    lines.append("> 💡 **Next Step**: Share or email this file to your development assistant so it can hyper-tailor the Super Center SMS auto-reader, default categories, and account cards to your exact financial footprint!")

    output_text = "\n".join(lines)
    return output_text

def main():
    if len(sys.argv) < 2:
        print("Usage: python extract_pennywise_profile.py <path_to_pennywise_backup.json> [output_profile.md]")
        sys.exit(1)

    json_path = sys.argv[1]
    out_path = sys.argv[2] if len(sys.argv) > 2 else "MY_FINANCIAL_PROFILE.md"

    print(f"Parsing PennyWise backup from: {json_path}")
    md_content = parse_pennywise_backup(json_path)

    with open(out_path, "w", encoding="utf-8") as f:
        f.write(md_content)

    print(f"✅ Success! Profile extracted and saved to: {out_path}")
    print(f"You can now review `{out_path}`, email it, or paste it directly into your Antigravity conversation!")

if __name__ == "__main__":
    main()
