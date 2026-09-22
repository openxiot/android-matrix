"""独立校验 63 个语言目录 —— 与 Kotlin 侧的门禁测试互为交叉验证。

为什么要有第二份：Kotlin 门禁跑在 `./gradlew testDebugUnitTest` 里，而 Gradle 的
`UP-TO-DATE` 会让它在资源没被当成输入时**静默不执行**（"BUILD SUCCESSFUL" 什么都没证明）。
本脚本直接读文件、当场给数，是那层门禁的对照物。

检查项：
  结构   目录齐备 / 15 个文件名与 values/ 完全一致 / total 551 条
  键     key 集合与 values/（剔除 translatable="false"）完全相等
  占位符 每个 key 的格式说明符**多重集**与英文相等（%1$s 不许变 %s，不许增减）
  复数   该语言 CLDR 要求的档位齐备，other 必须存在（缺档 Android 会静默回落）
  残留   非中文语言不得与中文原文逐字相同；不得出现汉字
  繁体   zh-rTW / zh-rHK 不得整份等于简体
  比例   与英文逐字相同的条目占比须低于阈值（照抄英文会当场炸）

用法：python3 validate.py [language ...]     # 不给参数就全查
"""

import re
import sys
from pathlib import Path

import langs
import xmlio
from xmlio import SPEC, parse_dir

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
RES = ROOT / "app/src/main/res"

#: 与英文逐字相同占比的上限。web 自己的词典因专有名词有 8–16% 相同，属正常；
#: 真正要抓的是「整份照抄英文」。定在 50% 有一倍余量，又离「照抄」很远。
MAX_IDENTICAL_RATIO = 0.50

#: 汉字。日语正用汉字、韩语偶有汉字，故这两者只查「与中文原文相同」不查汉字。
CJK = re.compile(r"[㐀-䶿一-鿿豈-﫿]")
HAN_OK = {"zh", "zh-TW", "zh-HK", "ja", "ko"}


def specs(text: str) -> list[str]:
    """占位符多重集 —— 排序后比较，顺序无关、个数有关。

    取 `group(0)` 而不是 `findall`：`SPEC` 带捕获组，`findall` 只返回组内内容，
    `%1$s`→`1$`、`%s`→空串，于是 `%s` 与 `%d` 在比对里区分不开 ——
    「%s 被译成了 %d」会静默通过，真机上抛 IllegalFormatConversionException。
    """
    return sorted(m.group(0) for m in SPEC.finditer(text))


def flat(res) -> dict:
    """把 plurals 按档位摊平，供**逐条比对值/占位符**用。

    注意摊平后的条数（英文侧 562）**大于**条目数（551）：551 是 key 的个数
    （540 string + 11 plurals），摊平会按档位展开 plurals。两个数都用，别混。
    """
    out = dict(res.strings)
    for k, items in res.plurals.items():
        for q, v in items.items():
            out[f"{k}[{q}]"] = v
    return out


def has_nothing_to_translate(text: str) -> bool:
    """纯格式壳（去掉占位符后不含任何字母）—— 没有可译的词，不必查是否抄了原文。

    与 Kotlin 门禁 `StringsParityTest.hasNothingToTranslate` 逐字对齐：
    把 `%1$s`/`%s`/`%2$d` 这类占位符剥掉后，剩余只有冒号/空格/括号/数字时，
    该 key 是「格式模板」而非「文案」。例：`modbus_alarm_condition` 的 `%1$s %2$s`
    在英文与中文里本来就是同一串，各语言自然也相同 —— 这不是没翻。
    """
    return not any(c for c in re.sub(r"%(\d+\$)?[sd]", "", text) if c.isalpha())


def names(res) -> set[str]:
    """key 集合 —— **不带**档位。复数档位由 CLDR 规则单独查，不混进这里：
    同一批 plurals 在英文侧是 one+other、在中文侧只有 other，拿摊平后的集合比会全线误报。
    """
    return set(res.strings) | set(res.plurals)


def main() -> None:
    argv = sys.argv[1:]
    base = parse_dir(RES / "values")
    base_files = sorted(f.name for f in (RES / "values").glob(xmlio.FILE_GLOB))
    base_flat = flat(base)

    targets = langs.shipped()
    if argv:
        want = set(argv)
        unknown = want - {x.bcp47 for x in langs.LANGS}
        if unknown:
            raise SystemExit(f"不认识的语言：{sorted(unknown)}")
        targets = [x for x in targets if x.bcp47 in want]

    zh_flat = flat(parse_dir(RES / "values-zh")) if (RES / "values-zh").exists() else {}

    hard: list[str] = []
    rows: list[tuple[str, int, float, str]] = []

    for lang in targets:
        d = RES / lang.dir
        tag = lang.bcp47
        if not d.exists():
            hard.append(f"{tag}: 目录 {lang.dir} 不存在")
            continue

        file_names = sorted(f.name for f in d.glob(xmlio.FILE_GLOB))
        if file_names != base_files:
            missing = set(base_files) - set(file_names)
            extra = set(file_names) - set(base_files)
            hard.append(f"{tag}: 文件名不成对（缺 {sorted(missing)}，多 {sorted(extra)}）")
            continue

        cur = parse_dir(d)
        cur_flat = flat(cur)
        want = names(base)
        got = names(cur)
        if got != want:
            hard.append(
                f"{tag}: key 集合不符（缺 {len(want - got)} 个，多 {len(got - want)} 个）"
                + (f"，例：{sorted(want - got)[:4]}" if want - got else "")
            )
            continue
        # 摊平后的逐条集合，只取英文侧也有的档位来比对值/占位符。
        # 多出来的复数档位（CLDR 合法的 few/many/two/zero…）不在英文侧，是允许的
        # （如 ru 的 few/many、ar 的 zero/two/few/many），档位齐全度由下面
        # 的「复数档位」检查单独兜底，不能因为「比英文多档」就误报未翻译。
        keys = sorted(set(base_flat) & set(cur_flat))

        # 占位符
        bad_ph = [k for k in keys if specs(cur_flat[k]) != specs(base_flat[k])]
        if bad_ph:
            hard.append(f"{tag}: {len(bad_ph)} 条占位符与英文不符，例：{bad_ph[:4]}")
            continue

        # 复数档位
        want_q = set(lang.plurals)
        for key, items in cur.plurals.items():
            if "other" not in items:
                hard.append(f"{tag}: {key} 缺 other")
            miss = want_q - set(items)
            if miss:
                hard.append(f"{tag}: {key} 缺 {'/'.join(sorted(miss))}（CLDR 要求）")

        # 与英文逐字相同（同一 key 同一档位）
        same = [k for k in keys if cur_flat[k] == base_flat[k]]
        # 与中文原文逐字相同（= 没翻，直接抄了源）。中文侧只有 other，故按 key 比。
        if tag not in HAN_OK and zh_flat:
            copied = [
                k for k in got
                if cur_flat.get(k) is not None
                and cur_flat.get(k) == zh_flat.get(k)
                # 纯格式壳（占位符 + 冒号/括号，无字母）不算「抄了中文」：
                # en==zh、各语言也相同的模板 key 是格式而非文案（同 Kotlin 门禁）
                and not has_nothing_to_translate(cur_flat[k])
            ]
            if copied:
                hard.append(f"{tag}: {len(copied)} 条与中文原文逐字相同（未翻译），例：{copied[:4]}")

        # 汉字残留（中文/日/韩正用汉字，跳过）
        if tag not in HAN_OK:
            han = [k for k in keys if CJK.search(cur_flat[k])]
            if han:
                hard.append(f"{tag}: {len(han)} 条含汉字，例：{han[:4]}")

        # 繁体守卫
        if tag in ("zh-TW", "zh-HK") and zh_flat:
            identical = sum(1 for k in got if cur_flat.get(k) == zh_flat.get(k))
            if identical / len(got) > 0.5:
                hard.append(f"{tag}: {identical}/{len(got)} 条与简体完全相同，像是照抄了 values-zh")

        if len(got) != len(want):
            hard.append(f"{tag}: 条目数 {len(got)}，应为 {len(want)}")

        ratio = len(same) / len(keys)
        if ratio > MAX_IDENTICAL_RATIO:
            hard.append(f"{tag}: 与英文逐字相同占比 {ratio:.0%}，超过 {MAX_IDENTICAL_RATIO:.0%} 上限")
        rows.append((tag, len(got), ratio, lang.endonym))

    print(
        f"检查 {len(rows)} 个语言目录"
        f"（基准 values/：{len(names(base))} 条 key，摊平 {len(base_flat)} 条含复数档位）\n"
    )
    print(f"  {'tag':9s} {'条数':>5s} {'同英文':>7s}  语言")
    for tag, total, ratio, endo in rows:
        flag = "  ⚠" if ratio > 0.25 else ""
        print(f"  {tag:9s} {total:5d} {ratio:6.0%} {flag} {endo}")

    if hard:
        print(f"\n✗ {len(hard)} 项不通过：")
        for h in hard:
            print(f"  - {h}")
        raise SystemExit(1)
    print(f"\n✓ 全部通过（{len(rows)} 个目录 × {len(names(base))} 条）")


if __name__ == "__main__":
    main()
