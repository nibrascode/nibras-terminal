#!/usr/bin/env python3
"""Regenerates catalog/scripts/index.json and index.tsv (sha256 of every script, descriptions in 19 languages).
Run after editing a script: python3 tools/gen-script-store.py"""
import hashlib, json, os, sys
sys.path.insert(0, os.path.dirname(__file__))
from script_store_desc import LANGS, CATS, S
root = os.path.join(os.path.dirname(__file__), "..", "catalog", "scripts")
items = []
for sid, cat, desc in S:
    data = open(os.path.join(root, sid + ".sh"), "rb").read()
    items.append({"id": sid, "category": cat, "sha256": hashlib.sha256(data).hexdigest(), "size": len(data),
                  "file": sid + ".sh", "description": dict(zip(LANGS, desc.split("|")))})
idx = {"version": 1, "languages": LANGS,
       "categories": {k: dict(zip(LANGS, v.split("|"))) for k, v in CATS.items()}, "scripts": items}
json.dump(idx, open(os.path.join(root, "index.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
with open(os.path.join(root, "index.tsv"), "w", encoding="utf-8") as f:
    f.write("# id\tcategory\tsha256\t" + "\t".join(LANGS) + "\n")
    for k, v in CATS.items(): f.write("@" + k + "\t-\t-\t" + v.replace("|", "\t") + "\n")
    for it, (_, _, desc) in zip(items, S): f.write(f"{it['id']}\t{it['category']}\t{it['sha256']}\t" + desc.replace("|", "\t") + "\n")
print(len(items), "scripts")
