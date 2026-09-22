"""把 todo.json 摊成翻译代理直接吃的纯文本，并生成每语言的复数档位表。

产物（out/bundle/）：
  source.tsv           328 条待译：key \t 英文 \t 中文
  source-plurals.tsv   11 条复数：key \t 档位 \t 英文 \t 中文
  plural-categories.tsv  语言 tag \t BCP-47 \t 该语言必须产出的档位

为什么不用 JSON 直接给：JSON 要 52K，TSV 只要 35K；62 个代理各读一遍，
省下的是实打实的 token。而且 TSV 一行一条、**不转义**（值里没有制表符），
代理复制粘贴时不会把 `\\"` 带进去 —— 这是本项目最容易出错的地方
（见 xmlio.py 的说明：转义只该由脚本做一次）。

用法：python3 bundle.py
"""

import json
from pathlib import Path

import langs

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
BUNDLE = OUT / "bundle"


def cell(value: str) -> str:
    """TSV 单元格：制表符会破坏列，换行会破坏行 —— 出现即报错而不是悄悄改。"""
    if "\t" in value:
        raise ValueError(f"值里有制表符，TSV 装不下：{value!r}")
    return value.replace("\n", "\\n")


def main() -> None:
    BUNDLE.mkdir(parents=True, exist_ok=True)
    todo = json.loads((OUT / "todo.json").read_text(encoding="utf-8"))

    singles, plurals = [], []
    for key, rec in todo.items():
        if rec["kind"] == "plurals":
            en, zh = rec["en"], rec["zh"]
            for q in sorted(set(en) | set(zh)):
                plurals.append(
                    (key, q, cell(en.get(q, "")), cell(zh.get(q, "")))
                )
        else:
            singles.append((key, cell(rec["en"]), cell(rec["zh"])))

    singles.sort()
    (BUNDLE / "source.tsv").write_text(
        "# key\t英文（权威 UI 文案）\t中文（仅用于消歧义，不要照字面译）\n"
        + "".join(f"{k}\t{e}\t{z}\n" for k, e, z in singles),
        encoding="utf-8",
    )
    (BUNDLE / "source-plurals.tsv").write_text(
        "# key\t英文档位\t英文\t中文\n"
        + "".join(f"{k}\t{q}\t{e}\t{z}\n" for k, q, e, z in plurals),
        encoding="utf-8",
    )
    (BUNDLE / "plural-categories.tsv").write_text(
        "# 语言\tBCP-47\t必须产出的档位（逗号分隔）\t该语言的全部复数 key\n"
        + "".join(
            f"{x.endonym}\t{x.bcp47}\t{','.join(x.plurals)}\t"
            f"{','.join(sorted(k for k, r in todo.items() if r['kind'] == 'plurals'))}\n"
            for x in langs.to_create()
        ),
        encoding="utf-8",
    )

    print(f"单条待译   {len(singles)} 条 → out/bundle/source.tsv")
    print(f"复数待译   {len(set(k for k, _, _, _ in plurals))} 条 key / {len(plurals)} 行"
          f" → out/bundle/source-plurals.tsv")
    print(f"复数档位表 {len(langs.to_create())} 种语言 → out/bundle/plural-categories.tsv")
    groups = sorted({tuple(x.plurals) for x in langs.to_create()})
    print(f"\n{len(groups)} 种档位组合，代理按自己语言那一行产出即可：")
    for g in groups:
        who = [x.bcp47 for x in langs.to_create() if tuple(x.plurals) == g]
        print(f"  {'/'.join(g):32s} {len(who):2d} 种  {', '.join(who[:8])}"
              + (" …" if len(who) > 8 else ""))


if __name__ == "__main__":
    main()
