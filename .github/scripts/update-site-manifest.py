#!/usr/bin/env python3
"""把本次发版的版本信息写进官网仓库的 static/data/apps/android.json。

被 .github/workflows/build-release.yml 调用，参数：

    update-site-manifest.py <android.json> <版本号> <发布日期> <大小> <下载地址> <sha256> <提交说明> [notes-dir]

**读改写，不是套模板整份覆盖。** 那个文件里 `minOs` 等字段不归本仓库管，必须原样
留着；只有本仓库产出的字段（version / releasedAt / size / url / sha256 / notes /
_comment）才动。整份覆盖会把官网同学手写的字段抹掉，而 diff 看起来只是「一次正常的
发版」。

`_comment` 写的是本次编译那个 commit 的提交说明（`git log -1 --pretty=%s`），好让
日后从官网那份 JSON 反查是哪次提交发的版。

更新说明从 [notes-dir] 下的 zh.txt / en.txt 读，一行一条 —— 这两个文件永远只描述
「下一版要发什么」，发完就该为下一版重写，所以文件里**没有**版本号。空行与 `#` 开头
的行会被丢掉（`#` 是留给维护者写提醒的，不会出现在官网上）。

单独成文件而不是写在 workflow 的 heredoc 里：YAML 的块标量会把每一行整体缩进，
`<<'PY'` 不做去缩进，Python 直接 IndentationError。提出来还有个好处——可以本地跑。
"""

import json
import os
import re
import sys


def read_lines(notes_dir, name):
    """读 [notes_dir]/[name] 的每一行（已 strip）。读不到返回空列表。

    用 utf-8-sig：Windows 上的编辑器可能给文件塞 BOM，而 BOM 会粘在第一行文字上被
    当成内容发到官网。splitlines 而不是迭代文件对象：末尾有没有换行都一个结果。
    """
    try:
        with open(os.path.join(notes_dir, name), encoding="utf-8-sig") as f:
            return [line.strip() for line in f.read().splitlines()]
    except OSError:
        # 不存在（多半是路径写错）与不可读都归到「这版没写说明」，由调用方决定怎么办。
        # 这里不能抛：发版流程要能继续，官网继续显示上一版的说明。
        return []


def clean(lines):
    """丢掉空行与 `#` 注释行。"""
    return [line for line in lines if line and not line.startswith("#")]


def main() -> int:
    if len(sys.argv) < 8:
        print(__doc__, file=sys.stderr)
        return 2

    manifest_path, version, released_at, size, url, sha, comment = sys.argv[1:8]
    notes_dir = sys.argv[8] if len(sys.argv) > 8 else None

    # 八个位置参数全是字符串，写错顺序不会报错、只会把错值静默写进官网。挑几个一眼能
    # 看出串位的做格式断言 —— 真实取值（tag 推导、sha256sum、date -u）都稳定满足这些
    # 形状，不会误伤。提交说明是自由文本，没法做形状断言，只能靠别的不串位。
    for label, value, pattern in (
        ("版本号", version, r"^\d+\.\d+\.\d+$"),
        ("发布日期", released_at, r"^\d{4}-\d{2}-\d{2}$"),
        ("大小", size, r"^\d+(\.\d+)? MB$"),
        ("下载地址", url, r"^https://"),
        ("sha256", sha, r"^[0-9a-f]{64}$"),
    ):
        if not re.match(pattern, value):
            print(f"::error::{label} 取值不合法：{value!r}", file=sys.stderr)
            return 2

    with open(manifest_path, encoding="utf-8") as f:
        data = json.load(f)

    # 同版本重跑（Rerun 一次 tag 触发的 run）时不刷新发布日期：那一天是**首次**发布的
    # 日子，重跑改掉它既不准，也会平白多出一个提交和一次站点重建。
    same_version = data.get("version") == version
    prev_notes = data.get("notes")

    data["version"] = version
    data["size"] = size
    data["url"] = url
    data["sha256"] = sha
    # 记下这份包是哪个 commit 编出来的。值由 workflow 用 `git log -1 --pretty=%s` 取
    # ——workspace 里检出来的那个 commit（真正被编译的源码）的提交说明。
    #
    # 注意这个字段是**公网可见**的：它随站点一起发布，刚才那句「字段说明见 README」也
    # 一样是匿名可读的。提交说明若写了客户名、内网地址、工单号，等于把它们发到公网上。
    data["_comment"] = comment.strip()
    if not same_version:
        data["releasedAt"] = released_at

    zh = clean(read_lines(notes_dir, "zh.txt")) if notes_dir else []
    en = clean(read_lines(notes_dir, "en.txt")) if notes_dir else []

    if not zh:
        # 中文是主说明（App 只认中文，官网也以它为先）。这里**不动**已有的 notes：
        # 官网继续显示上一版的说明，总好过显示一片空白。
        note_state = "kept"
        print(
            f"::warning::{notes_dir}/zh.txt 是空的或读不到，官网仍显示上一版的更新说明。",
            file=sys.stderr,
        )
    else:
        notes = {"zh": zh, "en": en}
        data["notes"] = notes
        if notes == prev_notes:
            # 这两个文件没有版本号可查，判断不了「这一版写了没有」，只能跟官网上现有的
            # 比：一字不差 + 版本号是新的 = 多半是发版时忘了改文件。重跑同一版时说明
            # 本来就该一样，那种情况不算。
            note_state = "used" if same_version else "unchanged"
            if note_state == "unchanged":
                print(
                    f"::warning::{notes_dir} 里的说明与官网上现有的**一字不差** —— "
                    f"发新版却忘了改它？确认无误可忽略。",
                    file=sys.stderr,
                )
        else:
            note_state = "used"

    # 写英文留空数组而不是复制中文：官网那边 en 为空会回退到 zh（src/data/apps/index.ts
    # 的 normalize），留空才是「真的没写英文」这个事实；复制一份会让官网误以为有英文版。
    #
    # ensure_ascii=False 让中文原样落盘，diff 才是一行行看得懂的；末尾补换行是因为
    # json.dump 不写，而原文件有——不补的话每次发版都会多一行 "\ No newline" 噪声。
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # 只打印状态给 shell 取；警告走 stderr，不会被 $() 吞掉
    print(f"NOTES={note_state}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
