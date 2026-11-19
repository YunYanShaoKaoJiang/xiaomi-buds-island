#!/usr/bin/env bash
# 构建 Xiaomi Buds Island（LSPosed 模块）
#
# 依赖：
#   - JDK 8+（javac）
#   - Android build-tools 的 d8（r8.jar）与 aapt2
#   - framework-res.apk（用于 aapt2 link，从设备取：/system/framework/framework-res.apk）
#
# 用法：
#   ./build.sh /path/to/r8.jar /path/to/aapt2 /path/to/framework-res.apk [输出目录]
set -euo pipefail

R8_JAR="${1:?需要 r8.jar 路径}"
AAPT2="${2:?需要 aapt2 路径}"
FRAMEWORK="${3:?需要 framework-res.apk 路径}"
OUTDIR="${4:-build-out}"

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
rm -rf "$OUTDIR" && mkdir -p "$OUTDIR"/{stub,classes,dex,compiled}

echo "==> 编译 Xposed/Android 桩"
javac --release 8 -nowarn -d "$OUTDIR/stub" $(find stub -name '*.java')
jar cf "$OUTDIR/stub.jar" -C "$OUTDIR/stub" .

echo "==> 编译模块"
javac --release 8 -nowarn -cp "$OUTDIR/stub.jar" -d "$OUTDIR/classes" \
    $(find src -name '*.java')

echo "==> 转 dex"
java -cp "$R8_JAR" com.android.tools.r8.D8 --release --min-api 30 --no-desugaring \
    --classpath "$OUTDIR/stub.jar" --output "$OUTDIR/dex" \
    $(find "$OUTDIR/classes" -name '*.class')

echo "==> 编译资源"
"$AAPT2" compile --dir res -o "$OUTDIR/compiled"

echo "==> 链接 APK"
"$AAPT2" link -o "$OUTDIR/base.apk" --manifest AndroidManifest.xml \
    -I "$FRAMEWORK" --min-sdk-version 30 --target-sdk-version 34 \
    "$OUTDIR"/compiled/*.flat

echo "==> 打包 dex 与 xposed_init"
python3 - "$OUTDIR" <<'PY'
import sys, zipfile, os
out = sys.argv[1]
apk = os.path.join(out, 'base.apk')
z = zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED)
z.write(os.path.join(out, 'dex', 'classes.dex'), 'classes.dex')
z.writestr('assets/xposed_init', 'com.kernel.xiaomibudsisland.Hook\n')
z.close()
PY

echo "==> 对齐与签名"
KEYSTORE="${KEYSTORE:-debug.keystore}"
KS_PASS="${KS_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-island}"
zipalign -f 4 "$OUTDIR/base.apk" "$OUTDIR/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
    --out "$OUTDIR/XiaomiBudsIsland.apk" "$OUTDIR/aligned.apk"

echo "完成: $OUTDIR/XiaomiBudsIsland.apk"
ls -l "$OUTDIR/XiaomiBudsIsland.apk"
