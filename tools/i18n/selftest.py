"""端到端彩排：造一份假译文，跑完整条 build + validate 链路。

Batch 3 要一次性灌 62 个语言、930 个文件，**没有逐条人审的可能**。所以在灌真数据之前，
先用一份**确定性的假译文**把链路跑通：搬运（223）+ 新译（328）+ 复数档位 + 渲染 +
校验，全部走一遍。哪一环不通，现在就知道，而不是在 2 万条译文里找。

假译文用 `DE:` 前缀 + 英文，这样：
  - 与英文不同 → 不触发「照抄英文」比例规则
  - 占位符原样保留 → 触发不了占位符错误（占位符那条由 xmlio 的自检负责）
  - 只覆盖德语（one/other，最简单），机制验证与语言复杂度无关

同时**故意注入三种坏数据**，确认校验真的抓得住（只跑通不叫验证，能抓住错才叫）：

用法：python3 selftest.py
"""

import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
RES = HERE.parents[1] / "app/src/main/res"
TAG = "de"


def fake_translations() -> dict:
    """把 328 条 todo 造出假译文。复数按德语要求的 one/other 两档都产。"""
    todo = json.loads((OUT / "todo.json").read_text(encoding="utf-8"))
    out = {}
    for key, rec in todo.items():
        if rec["kind"] == "plurals":
            # 德语 one/other：英文侧有哪档就产哪档，再补齐 other
            items = {q: "DE:" + v for q, v in rec["en"].items() if v}
            items.setdefault("other", "DE:" + (rec["en"].get("other") or rec["en"].get("one") or key))
            out[key] = items
        else:
            out[key] = "DE:" + rec["en"]
    return out


def write_translated(path: Path) -> None:
    (OUT / "translated").mkdir(exist_ok=True)
    path.write_text(
        json.dumps(fake_translations(), ensure_ascii=False, indent=1) + "\n", encoding="utf-8"
    )


def run(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, *args], cwd=HERE, capture_output=True, text=True
    )


def main() -> None:
    fake = OUT / "translated" / f"{TAG}.json"
    backup = fake.read_bytes() if fake.exists() else None
    tmp = Path(tempfile.mkdtemp(prefix="i18n-selftest-"))
    failures = []
    try:
        # ── 1. 正常路径：应生成成功 ──
        write_translated(fake)
        r = run("build_xml.py", TAG, "--out", str(tmp))
        if r.returncode != 0:
            print("✗ 正常路径：build 失败")
            print(r.stdout[-3000:] or r.stderr[-3000:])
            raise SystemExit(1)
        print("✓ 正常路径：build 成功")

        # ── 2. 生成结果应通过校验 ──
        # validate.py 只认 app/src/main/res，故把假语言临时搬进去再撤走
        target = RES / "values-de"
        if target.exists():
            print(f"✗ {target} 已存在，先让开再跑彩排")
            raise SystemExit(1)
        shutil.copytree(tmp / "values-de", target)
        try:
            r = run("validate.py", TAG)
            if r.returncode != 0:
                print("✗ 正常路径：validate 不通过")
                print(r.stdout[-2000:])
                failures.append("validate 对合法数据报错")
            else:
                line = [x for x in r.stdout.splitlines() if x.strip().startswith(TAG)]
                print(f"✓ 正常路径：validate 通过  {line[0].strip() if line else ''}")
        finally:
            shutil.rmtree(target)

        # ── 3. 注入坏数据：缺复数档位，必须被 build 抓住 ──
        bad = fake_translations()
        plural_key = next(k for k, v in bad.items() if isinstance(v, dict))
        del bad[plural_key]["one"]
        fake.write_text(json.dumps(bad, ensure_ascii=False) + "\n", encoding="utf-8")
        r = run("build_xml.py", TAG, "--out", str(tmp / "x"))
        if r.returncode == 0:
            failures.append(f"build 没抓住「{plural_key} 缺 one」")
            print(f"✗ 坏数据 1：缺 {plural_key}[one]，build 竟然放行")
        else:
            print(f"✓ 坏数据 1：缺 {plural_key}[one]，build 拦下")

        # ── 4. 注入坏数据：某语言漏了 other ──
        bad = fake_translations()
        bad[plural_key] = {q: v for q, v in bad[plural_key].items() if q != "other"}
        fake.write_text(json.dumps(bad, ensure_ascii=False) + "\n", encoding="utf-8")
        r = run("build_xml.py", TAG, "--out", str(tmp / "y"))
        if r.returncode == 0:
            failures.append("build 没抓住缺 other")
            print(f"✗ 坏数据 2：缺 {plural_key}[other]，build 竟然放行")
        else:
            print(f"✓ 坏数据 2：缺 {plural_key}[other]，build 拦下")

        # ── 5. 注入坏数据：漏译整条 key，且不许静默补英文 ──
        bad = fake_translations()
        dropped = sorted(k for k, v in bad.items() if isinstance(v, str))[0]
        del bad[dropped]
        fake.write_text(json.dumps(bad, ensure_ascii=False) + "\n", encoding="utf-8")
        r = run("build_xml.py", TAG, "--out", str(tmp / "z"))
        if r.returncode == 0:
            failures.append(f"build 没抓住「{dropped} 漏译」")
            print(f"✗ 坏数据 3：{dropped} 漏译，build 竟然放行（会静默补英文吗？）")
        else:
            print(f"✓ 坏数据 3：{dropped} 漏译，build 拦下")

    finally:
        if backup is None:
            fake.unlink(missing_ok=True)
        else:
            fake.write_bytes(backup)
        shutil.rmtree(tmp, ignore_errors=True)

    if failures:
        print(f"\n✗ 彩排失败 {len(failures)} 项：")
        for f in failures:
            print(f"  - {f}")
        raise SystemExit(1)
    print("\n✓ 彩排通过：搬运+新译+复数+渲染+校验 全链路闭合，且三类坏数据都能拦住")


if __name__ == "__main__":
    main()
