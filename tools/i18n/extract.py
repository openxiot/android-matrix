"""从现有 values/ 与 values-zh/ 抽出待翻译的条目，并算出哪些能从 web 词典直接搬。

产物（都在 tools/i18n/out/，可随时重跑覆盖）：
  skeleton.json  每个 strings_*.xml 里 key 的**顺序** —— 生成时照它切分文件
  source.json    全部 551 条的 en / zh 对照，带 reusable 标记
  todo.json      **需要新译**的条目（328 条），交给逐语言的翻译
  reused/<tag>.json  该语言可以从 web 词典搬过来的条目（223 条里能对上的）

「能搬」的判据是**中文原文逐字相同**：web 的 i18n key 就是中文本身
（`"拾取标识": "Pick ID"`），所以拿 Android 的 values-zh 值去 web 的 zh.json 里查
key，命中即可取该语言文件里的同 key 值。这是纯确定性的，不涉及任何语义判断。
"""

import json
from pathlib import Path

import langs
import xmlio
from xmlio import parse_file

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
WEB_I18N = Path("/Users/ouyang/works/openxiot/service/webapp-matrix/public/i18n")
OUT = Path(__file__).resolve().parent / "out"


def load_web(tag: str) -> dict:
    p = WEB_I18N / f"{tag}.json"
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else {}


def main() -> None:
    OUT.mkdir(exist_ok=True)
    (OUT / "reused").mkdir(exist_ok=True)

    en_files = sorted(f.name for f in (RES / "values").glob(xmlio.FILE_GLOB))
    zh_files = sorted(f.name for f in (RES / "values-zh").glob(xmlio.FILE_GLOB))
    assert en_files == zh_files, f"两个目录文件不成对：{set(en_files) ^ set(zh_files)}"

    web_zh = load_web("zh")
    web_keys = {k.strip() for k in web_zh}

    # ── 抽源：按文件走，保住 key 顺序 ──
    skeleton: dict[str, list[str]] = {}
    source: dict[str, dict] = {}
    todo: dict[str, dict] = {}

    for fname in en_files:
        order, en = parse_file(RES / "values" / fname)
        _, zh = parse_file(RES / "values-zh" / fname)
        skeleton[fname] = order

        for key in order:
            if key in en.plurals:
                # 复数：逐档判断能不能搬（zh 侧只有 other 一档）
                en_items = en.plurals[key]
                zh_items = zh.plurals.get(key, {})
                hit = {
                    q: v.strip() in web_keys for q, v in zh_items.items() if v.strip()
                }
                source[key] = {
                    "file": fname,
                    "kind": "plurals",
                    "en": en_items,
                    "zh": zh_items,
                    "reusable": bool(hit) and all(hit.values()),
                }
            else:
                en_v = en.strings.get(key, "")
                zh_v = zh.strings.get(key, "")
                source[key] = {
                    "file": fname,
                    "kind": "string",
                    "en": en_v,
                    "zh": zh_v,
                    "reusable": zh_v.strip() in web_keys,
                }

    # ── todo：需要新译的 ──
    for key, rec in source.items():
        if rec["reusable"]:
            continue
        if rec["kind"] == "plurals":
            todo[key] = {
                "file": rec["file"],
                "kind": "plurals",
                "en": rec["en"],
                "zh": rec["zh"],
            }
        else:
            todo[key] = {"file": rec["file"], "kind": "string", "en": rec["en"], "zh": rec["zh"]}

    # ── reused/<tag>.json：每个语言能搬的部分 ──
    stats = []
    for lang in langs.to_create():
        web = load_web(lang.web)
        got = {"tag": lang.bcp47, "web": lang.web, "strings": {}, "plurals": {}}
        for key, rec in source.items():
            if not rec["reusable"]:
                continue
            zh_v = (rec["zh"] if rec["kind"] == "string" else rec["zh"].get("other", "")).strip()
            val = web.get(zh_v)
            if val is None:
                continue
            if rec["kind"] == "plurals":
                got["plurals"][key] = {"other": val}
            else:
                got["strings"][key] = val
        (OUT / "reused" / f"{lang.bcp47}.json").write_text(
            json.dumps(got, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
        )
        stats.append((lang.bcp47, len(got["strings"]) + len(got["plurals"])))

    (OUT / "skeleton.json").write_text(
        json.dumps(skeleton, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    (OUT / "source.json").write_text(
        json.dumps(source, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )
    (OUT / "todo.json").write_text(
        json.dumps(todo, ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )

    total = len(source)
    reusable = sum(1 for r in source.values() if r["reusable"])
    print(f"条目总数        {total}")
    print(f"可从 web 搬运   {reusable}")
    print(f"需要新译        {len(todo)}")
    print(f"待建目录        {len(stats)} 个")
    print(f"每语言需新译    {len(todo)} 条 → 合计 {len(todo) * len(stats):,} 条")
    lo = min(s for _, s in stats)
    hi = max(s for _, s in stats)
    print(f"每语言可搬条数  {lo}…{hi}（不一致说明某些语言的 web 词典缺键）")
    files = sorted(set(r["file"] for r in todo.values()))
    print(f"涉及文件        {len(files)} 个：" + ", ".join(files))


if __name__ == "__main__":
    main()
