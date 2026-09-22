"""把「搬运来的 + 新译的」合并成 62 个 values-* 目录的 XML。

输入（都在 out/）：
  skeleton.json      每个 strings_*.xml 的 key 顺序 —— 决定文件切分与行序
  source.json        551 条 en/zh 对照 —— 提供英文兜底与残缺检查
  reused/<tag>.json  从 web 词典搬来的部分（223 条）
  translated/<tag>.json  新译的部分（328 条），由翻译代理产出

合并规则是**逐 key**的：新译优先，缺了才用搬运的。两者都没有 → 该 key 报缺。

产出：app/src/main/res/values-*/strings*.xml

用法：
  python3 build_xml.py                 # 全部语言，写进真 res 目录
  python3 build_xml.py ja de ru        # 只做指定语言
  python3 build_xml.py --out /tmp/x    # 写到别处（先看再搬）
  python3 build_xml.py --allow-missing # 允许缺 key（只为看进度，正式生成别加）
"""

import json
import shutil
import sys
from pathlib import Path

import langs
import xmlio
from xmlio import Res, parse_dir

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
ROOT = HERE.parents[1]
RES = ROOT / "app/src/main/res"

HEADER = "本文件由 tools/i18n/build_xml.py 生成，请勿手改；改文案请改 out/ 下的源包后重跑。"

#: 逐语言可能缺的档位提示语 —— 复数缺档是**静默**回落，必须在生成期就炸出来
def check_plural(key: str, items: dict, lang: langs.Lang) -> list[str]:
    want = set(lang.plurals)
    got = set(items)
    errs = []
    if "other" not in got:
        errs.append(f"{key}: 缺 other（Android 强制要求）")
    missing = want - got
    if missing:
        errs.append(f"{key}: 缺 {'/'.join(sorted(missing))}（该语言 CLDR 需要，缺了会静默回落 other）")
    extra = got - want
    if extra:
        errs.append(f"{key}: 多出 {'/'.join(sorted(extra))}（该语言 CLDR 无此档位）")
    return errs


def load_translated(tag: str) -> dict:
    """新译结果。文件不存在就当作「一条都没译」—— 让缺 key 报错去说。"""
    p = OUT / "translated" / f"{tag}.json"
    if not p.exists():
        return {}
    data = json.loads(p.read_text(encoding="utf-8"))
    # 容忍两种形状：扁平 {key: 译文} 与 {strings:{}, plurals:{}}
    if "strings" in data or "plurals" in data:
        flat = dict(data.get("strings") or {})
        flat.update(data.get("plurals") or {})
        return flat
    return data


def load_reused(tag: str) -> dict:
    p = OUT / "reused" / f"{tag}.json"
    if not p.exists():
        return {}
    data = json.loads(p.read_text(encoding="utf-8"))
    flat = dict(data.get("strings") or {})
    flat.update(data.get("plurals") or {})
    return flat


def build_one(lang: langs.Lang, skeleton: dict, source: dict, allow_missing: bool) -> tuple[dict, list[str]]:
    """返回 (文件名 -> XML 文本, 错误列表)。"""
    merged = load_reused(lang.bcp47)
    merged.update(load_translated(lang.bcp47))

    res = Res()
    errors: list[str] = []
    for key, rec in source.items():
        if key not in merged:
            if not allow_missing:
                errors.append(f"{key}: 既没搬运也没新译")
            continue
        value = merged[key]
        if rec["kind"] == "plurals":
            if not isinstance(value, dict):
                errors.append(f"{key}: 是 plurals，译文须为 {{档位: 文本}}，实为 {type(value).__name__}")
                continue
            errors += check_plural(key, value, lang)
            res.plurals[key] = value
        else:
            if isinstance(value, dict):
                errors.append(f"{key}: 是 string，译文须为文本，实为 dict")
                continue
            res.strings[key] = value

    files = {fname: (order, Res()) for fname, order in skeleton.items()}
    return xmlio.render(files, res, HEADER), errors


def self_check() -> None:
    """用**已有且已核实**的 values-zh 反向验整条生成链路。

    拿 source.json 里的中文当「译文」喂进 build 的渲染路径，生成到临时目录，
    再把生成结果解析回来与真的 values-zh 逐条比。能对上就说明
    skeleton 切分、key 顺序、escape/render 这一串在真实数据上是闭合的 ——
    比任何构造的用例都有说服力，因为 551 条是真实文案（含 `%1$s`、`&`、`'`、全角标点）。

    中文侧只有 other 一档，英文侧 plural 有 one+other，故只比中文侧存在的档位。
    """
    import tempfile

    skeleton = json.loads((OUT / "skeleton.json").read_text(encoding="utf-8"))
    source = json.loads((OUT / "source.json").read_text(encoding="utf-8"))

    res = Res()
    for key, rec in source.items():
        if rec["kind"] == "plurals":
            res.plurals[key] = {q: v for q, v in rec["zh"].items() if v}
        else:
            res.strings[key] = rec["zh"]

    files = {fname: (order, Res()) for fname, order in skeleton.items()}
    rendered = xmlio.render(files, res, HEADER)

    real = RES / "values-zh"
    with tempfile.TemporaryDirectory() as tmp:
        d = Path(tmp)
        for fname, text in rendered.items():
            (d / fname).write_text(text, encoding="utf-8")
        got = parse_dir(d)
        ref = parse_dir(real)

    problems = []
    for key, value in ref.strings.items():
        if got.strings.get(key) != value:
            problems.append(f"{key}\n      真: {value!r}\n      生成: {got.strings.get(key)!r}")
    for key, items in ref.plurals.items():
        for q, value in items.items():
            if got.plurals.get(key, {}).get(q) != value:
                problems.append(
                    f"{key}[{q}]\n      真: {value!r}\n      生成: {got.plurals.get(key, {}).get(q)!r}"
                )
    extra = (set(got.strings) | set(got.plurals)) - (set(ref.strings) | set(ref.plurals))
    if extra:
        problems.append(f"多出条目：{sorted(extra)[:6]}")

    if problems:
        print(f"✗ 生成链路与 values-zh 不符，{len(problems)} 条：")
        for p in problems[:10]:
            print(f"    {p}")
        raise SystemExit(1)
    n = len(ref.strings) + sum(len(v) for v in ref.plurals.values())
    print(f"✓ 生成链路闭合：用 source.json 重渲染出 {n} 条，与 values-zh 逐条相同")
    print(f"  （{len(rendered)} 个文件，含 %1$s 占位符、&amp; 实体、全角标点）")


def main() -> None:
    argv = sys.argv[1:]
    if "--selfcheck" in argv:
        self_check()
        return
    allow_missing = "--allow-missing" in argv
    argv = [a for a in argv if a != "--allow-missing"]
    out_root = RES
    if "--out" in argv:
        i = argv.index("--out")
        out_root = Path(argv[i + 1])
        del argv[i : i + 2]

    skeleton = json.loads((OUT / "skeleton.json").read_text(encoding="utf-8"))
    source = json.loads((OUT / "source.json").read_text(encoding="utf-8"))

    targets = langs.to_create()
    if argv:
        want = set(argv)
        unknown = want - {x.bcp47 for x in langs.LANGS}
        if unknown:
            raise SystemExit(f"不认识的语言：{sorted(unknown)}")
        targets = [x for x in targets if x.bcp47 in want]

    total_written = 0
    failed = []
    for lang in targets:
        files, errors = build_one(lang, skeleton, source, allow_missing)
        if errors:
            failed.append((lang, errors))
            continue
        d = out_root / lang.dir
        if d.exists():
            shutil.rmtree(d)  # 重跑要干净，否则删掉的 key 会留下残骸
        d.mkdir(parents=True)
        for fname, text in files.items():
            (d / fname).write_text(text, encoding="utf-8")
        total_written += 1

    if failed:
        print(f"✗ {len(failed)} 种语言没通过，未落盘：")
        for lang, errs in failed[:5]:
            print(f"\n  【{lang.bcp47}】{lang.endonym}  {len(errs)} 条问题")
            for e in errs[:12]:
                print(f"    - {e}")
            if len(errs) > 12:
                print(f"    … 另有 {len(errs) - 12} 条")
        if len(failed) > 5:
            print(f"\n  … 另有 {len(failed) - 5} 种语言有问题")
        raise SystemExit(1)

    where = "已落盘" if total_written else "（无目标）"
    print(f"✓ {total_written} 个目录 {where} → {out_root}")
    if out_root == RES:
        print("  下一步：python3 validate.py")


if __name__ == "__main__":
    main()
