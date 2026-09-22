"""拿真实 CLDR 数据核对 langs.py 的复数档位表 —— 本工具链**唯一的外部对照物**。

为什么非要有它：复数档位是本项目里**唯一一处「写错了不报错、只是译文语法不对」**的东西。
少一档时 Android 会静默回落 `other`，于是俄语的 5 显示成 «5 товаров»（应为 «5 товара»）——
编译、门禁、跑起来全绿，只有懂俄语的人看得出。而 `langs.plurals` 是我按 CLDR 记忆手写的，
生成器与校验器都用这一份，**自证不了**：它们只能证明「生成的和表里写的一致」，
证明不了「表是对的」。

所以这里引入一个真正独立的数据源（`babel` 打包的 CLDR）来对账。

**依赖是可选的**：babel 装不上（离线、无网）时本脚本**跳过而不是失败** ——
它是一次性的核对工具，不是 CI 门禁。门禁在 Kotlin 侧（`StringsParityTest`），
那份表是手抄的第二遍，两边一致即是交叉验证。

用法：
  python3 -m pip install --target=/tmp/cldr babel
  PYTHONPATH=/tmp/cldr python3 cldr_check.py
"""

import sys

import langs

#: CLDR 的 other 是**兜底档**，babel 的 `plural_form.rules` 里不含它
#: （ja 的 rules 是空集，而 ja 仍有 other 档）。比对时要补上。
IMPLICIT = {"other"}


def cldr_categories(tag: str):
    """该 BCP-47 的 CLDR 基数类别。地区变体回退到语言级规则。"""
    from babel import Locale
    from babel.core import UnknownLocaleError

    for cand in (tag.replace("-", "_"), tag.split("-")[0]):
        try:
            return set(Locale.parse(cand).plural_form.rules) | IMPLICIT
        except (UnknownLocaleError, ValueError):
            continue
    return None


def main() -> None:
    try:
        import babel  # noqa: F401
    except ImportError:
        print("跳过：没装 babel（Python 侧没有 CLDR 数据）。")
        print("  python3 -m pip install --target=/tmp/cldr babel")
        print("  PYTHONPATH=/tmp/cldr python3 cldr_check.py")
        return

    # 两个方向的**后果完全不同**，别搞反（第一版就搞反了，输出看着像全部语言都缺 other）：
    #   lacks = CLDR 要、langs.py 没有 → 少一档 → 静默回落 other → 可见的语法错，必须拦
    #   spare = langs.py 有、CLDR 没有 → 多一档 → 死条目，无害，只报告
    lacks, spare, unknown = [], [], []
    for x in langs.LANGS:
        got = cldr_categories(x.bcp47)
        if got is None:
            unknown.append(x.bcp47)
            continue
        want = set(x.plurals)
        if got - want:
            lacks.append((x.bcp47, sorted(got - want)))
        if want - got:
            spare.append((x.bcp47, sorted(want - got)))

    if unknown:
        print(f"⚠ babel 不认识这些 tag，没能核对：{unknown}")

    if lacks:
        print(f"\n✗ CLDR 要求、langs.py 里漏了 —— **会静默回落 other，是真 bug**：")
        for tag, cats in lacks:
            print(f"    {tag:8s} 缺 {cats}")

    if spare:
        print(f"\n· langs.py 里写了、CLDR 这一版没有（无害，见下）：")
        for tag, cats in spare:
            print(f"    {tag:8s} 多 {cats}")

    if lacks:
        raise SystemExit(1)

    print(f"\n✓ {len(langs.LANGS) - len(unknown)} 种语言的复数档位与 CLDR 一致，无遗漏")
    if spare:
        print(
            "  上面那些「多余」档位**有意保留**：CLDR 会把某些类别删掉（希伯来语的 many\n"
            "  就在 CLDR 30 前后被移除），而设备上的 ICU 版本新旧不一 —— 老 ICU 认为该语言\n"
            "  有 many 时，多写一档能兜住；多出来的档位在 Android 上是**死条目，无害**。\n"
            "  反过来漏一档才是可见的语法错误，所以只拦漏、不拦多。"
        )


if __name__ == "__main__":
    main()
