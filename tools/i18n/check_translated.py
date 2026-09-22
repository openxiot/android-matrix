"""校验**单个**语言的译稿（`out/translated/<tag>.json`）—— 翻译代理的自检工具。

为什么要有它：2 万条译文的产出方式是「每个语言一个代理」，代理会写错的地方是**可枚举**的
——漏 key、占位符被顺手改掉（`%1$d` → `%d`）、复数只写了 `other`、把中文原文抄下来当译文、
手工做了一遍 Android 转义（`Don\'t`，脚本会再转一次变成 `Don\\'t`）。
这些全都**不会**在写的时候报错，只会在 62 个目录生成完、或者真机跑到那一屏才暴露。

所以把校验前移到代理手上：它写完就跑，报错信息直接指向第几条、差在哪，
代理当场改到过为止。**代理自己说「我检查过了」不算数**，这里的规则才是判据。

本脚本**不查**的东西（由 validate.py 在整批生成后查）：跨 key 的一致性、与英文的
相同率、繁体是不是照抄简体 —— 那些要看全量才能判断。

用法：
  python3 check_translated.py ja            # 查一个
  python3 check_translated.py ja de ru      # 查多个
  python3 check_translated.py --all         # 查现有全部（缺的只报告，不算失败）
"""

import json
import re
import sys
from pathlib import Path

import langs

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
TRANSLATED = OUT / "translated"

#: 与 xmlio.SPEC 同一套：`%1$s` `%d`。用来比对占位符**多重集**。
SPEC = re.compile(r"%(\d+\$)?[sd]")

#: 汉字。日语/中文正用汉字；韩语偶有汉字但界面文案里不该有。
#: 三段：扩展A(3400-4DBF) + 基本区(4E00-9FFF) + 兼容汉字(F900-FAFF)。
#: 第三段**必须从 F900 起** —— 若写成 8C48-FAFF，区间会连 AC00-D7A3 的
#: 한글音节一起吞掉，韩语任何一条译文都会被判「含汉字」。
CJK = re.compile(r"[㐀-䶿一-鿿豈-﫿]")
HAN_OK = {"zh-TW", "zh-HK", "ja"}

#: 手工转义的痕迹。译文里出现这些说明代理自己转了一遍 —— 脚本会再转一次。
#: `%%` 尤其隐蔽：它落到 XML 里是字面百分号，看着「也没错」，直到某天有人加了个 `%s`。
HAND_ESCAPED = [
    (re.compile(r"\\'"), "\\'（单引号不用转义，写 ' 即可）"),
    (re.compile(r'\\"'), '\\"（双引号不用转义，写 " 即可）'),
    (re.compile(r"&amp;|&lt;|&gt;|&quot;"), "XML 实体（写 & < > 即可）"),
    (re.compile(r"%%"), "%%（写单个 % 即可）"),
    (re.compile(r"\\n"), "\\n（换行请写真正的换行，或不要换行）"),
]


def read_tsv(path: Path) -> list[list[str]]:
    """读 bundle 的 TSV：跳过 `#` 开头的表头注释，按制表符切。"""
    rows = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line or line.startswith("#"):
            continue
        rows.append(line.split("\t"))
    return rows


def specs(text: str) -> list[str]:
    """占位符多重集 —— 排序后比，顺序无关、个数有关。

    必须取 `group(0)` 而不是 `findall`：正则里带捕获组时 `findall` 只返回**组内**内容，
    `%1$s` 会变成 `1$`、`%s` 会变成空串 —— 于是 `%s` 与 `%d` 在比对里**无法区分**，
    「把 %s 译成了 %d」这种错会被放过去，真机上抛 IllegalFormatConversionException。
    """
    return sorted(m.group(0) for m in SPEC.finditer(text))


def check_one(lang: langs.Lang, src: dict, plur_src: dict) -> list[str]:
    """返回问题列表，空列表表示通过。"""
    tag = lang.bcp47
    p = TRANSLATED / f"{tag}.json"
    errs: list[str] = []
    if not p.exists():
        return [f"{tag}: {p.relative_to(HERE.parents[1])} 不存在"]

    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return [f"{tag}: JSON 解析失败 —— {e}"]

    if not isinstance(data, dict):
        return [f"{tag}: 顶层必须是对象，实为 {type(data).__name__}"]

    # 容忍扁平 {key: 译文}，但复数必须是嵌套字典，故推荐显式两段式
    if "strings" in data or "plurals" in data:
        got_strings = data.get("strings") or {}
        got_plurals = data.get("plurals") or {}
    else:
        got_plurals = {k: v for k, v in data.items() if isinstance(v, dict)}
        got_strings = {k: v for k, v in data.items() if not isinstance(v, dict)}

    # ── 键集 ──
    want_s, got_s = set(src), set(got_strings)
    if want_s != got_s:
        miss, extra = sorted(want_s - got_s), sorted(got_s - want_s)
        if miss:
            errs.append(f"{tag}: strings 缺 {len(miss)} 个：{miss[:6]}")
        if extra:
            errs.append(f"{tag}: strings 多 {len(extra)} 个（源包里没有）：{extra[:6]}")

    want_p, got_p = set(plur_src), set(got_plurals)
    if want_p != got_p:
        miss, extra = sorted(want_p - got_p), sorted(got_p - want_p)
        if miss:
            errs.append(f"{tag}: plurals 缺 {len(miss)} 个：{miss}")
        if extra:
            errs.append(f"{tag}: plurals 多 {len(extra)} 个：{extra}")

    # ── 逐条：占位符 / 空值 / 手工转义 / 汉字 ──
    def audit(where: str, text, en: str) -> None:
        """`en` 是该条英文，占位符以它为基准。"""
        if not isinstance(text, str):
            errs.append(f"{tag}: {where} 的值不是字符串，实为 {type(text).__name__}")
            return
        if not text.strip():
            errs.append(f"{tag}: {where} 是空的")
            return
        if specs(text) != specs(en):
            errs.append(
                f"{tag}: {where} 占位符不符 —— 英文 {specs(en)}，译文 {specs(text)}"
                f"\n        英文：{en}\n        译文：{text}"
            )
        for rx, why in HAND_ESCAPED:
            if rx.search(text):
                errs.append(f"{tag}: {where} 含手工转义 {why}\n        译文：{text}")
        if tag not in HAN_OK and CJK.search(text):
            errs.append(f"{tag}: {where} 含汉字（像是没翻）\n        译文：{text}")

    for key in sorted(want_s):
        if key in got_strings:
            audit(key, got_strings[key], src[key])

    # ── 复数档位 ──
    want_q = set(lang.plurals)
    for key in sorted(want_p):
        items = got_plurals.get(key)
        if not isinstance(items, dict):
            errs.append(f"{tag}: plurals.{key} 必须是 {{档位: 文本}}，实为 {type(items).__name__}")
            continue
        got_q = set(items)
        if "other" not in got_q:
            errs.append(f"{tag}: plurals.{key} 缺 other（Android 强制要求）")
        miss = want_q - got_q
        if miss:
            errs.append(
                f"{tag}: plurals.{key} 缺 {'/'.join(sorted(miss))}"
                f"（{lang.endonym}的 CLDR 要求 {sorted(want_q)}，缺了会静默回落 other → 语法错）"
            )
        spare = got_q - want_q
        if spare:
            errs.append(f"{tag}: plurals.{key} 多出 {'/'.join(sorted(spare))}（本语言无此档位）")
        # 英文的 one/other 是拆开的，别把两档写成同一句（那等于没分档）
        # 占位符以英文的 other 为基准：已核实 11 个 key 的各档占位符完全相同。
        ref = plur_src.get(key, {}).get("other", "")
        for q, text in sorted(items.items()):
            audit(f"plurals.{key}[{q}]", text, ref)

        # 「各档不许同文」这条**不算 many**：many 要么落在本 App 到不了的量级
        # （法语/西语/意语/葡语的 many 只在 1e6 以上命中，见 langs.py），要么与
        # other 在整数上同形（俄语的 other 是给小数用的）。拿它当判据会逼代理为
        # 一个永远走不到的分支编出别扭说法。真正会命中的低数档位才必须分开写。
        hit = {q: v for q, v in items.items() if q != "many" and isinstance(v, str)}
        if len(hit) > 1 and len(set(hit.values())) == 1:
            errs.append(
                f"{tag}: plurals.{key} 的 {'/'.join(sorted(hit))} 各档文本完全相同"
                f"（这些档位本 App 会真实命中，写法必须不同）\n        都是：{next(iter(hit.values()))}"
            )
    return errs


def main() -> None:
    argv = [a for a in sys.argv[1:]]
    src = {row[0]: row[1] for row in read_tsv(OUT / "bundle" / "source.tsv")}
    plur_src = {}
    for row in read_tsv(OUT / "bundle" / "source-plurals.tsv"):
        plur_src.setdefault(row[0], {})[row[1]] = row[2]

    if "--all" in argv:
        targets = [x for x in langs.to_create() if (TRANSLATED / f"{x.bcp47}.json").exists()]
        missing = [x.bcp47 for x in langs.to_create() if not (TRANSLATED / f"{x.bcp47}.json").exists()]
        if missing:
            print(f"（还没译的 {len(missing)} 种，未查：{', '.join(missing)}）\n")
    elif argv:
        names = [a for a in argv if not a.startswith("-")]
        unknown = set(names) - {x.bcp47 for x in langs.LANGS}
        if unknown:
            raise SystemExit(f"不认识的语言：{sorted(unknown)}")
        targets = [langs.by_bcp47(n) for n in names]
    else:
        raise SystemExit("用法：python3 check_translated.py ja [de ru …] | --all")

    bad = 0
    for lang in targets:
        errs = check_one(lang, src, plur_src)
        if errs:
            bad += 1
            print(f"✗ 【{lang.bcp47}】{lang.endonym} —— {len(errs)} 条问题")
            for e in errs[:15]:
                print(f"    - {e}")
            if len(errs) > 15:
                print(f"    … 另有 {len(errs) - 15} 条")
            print()
        else:
            print(f"✓ 【{lang.bcp47}】{lang.endonym}  {len(src)} string + {len(plur_src)} plurals，占位符与档位全部通过")

    if bad:
        raise SystemExit(f"\n✗ {bad}/{len(targets)} 种语言不合格")
    print(f"\n✓ {len(targets)} 种语言全部通过")


if __name__ == "__main__":
    main()
