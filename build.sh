#!/usr/bin/env bash
# ModRepo 构建脚本（aarch64 主机可用：aapt(apt) + javac + D8(r8.jar) + apksig 签名）
set -e
cd "$(dirname "$0")"
mkdir -p build/classes build/dex out

echo "[1/5] javac"
find app/src -name '*.java' > build/sources.txt
javac -source 8 -target 8 -nowarn -encoding UTF-8 \
  -cp tools/android-all.jar -d build/classes @build/sources.txt 2>&1 | grep -v "bootstrap" || true

echo "[2/5] D8 dex"
CLASSES=$(find build/classes -name '*.class' | tr '\n' ' ')
java -cp tools/r8.jar com.android.tools.r8.D8 --min-api 21 --no-desugaring --release \
  --lib tools/android-all.jar --output build/dex $CLASSES

echo "[3/5] aapt package"
aapt package -f -M app/AndroidManifest.xml -S app/res -A app/assets \
  -I tools/android-all.jar -F build/out.unsigned.apk

echo "[4/5] add classes.dex"
(cd build/dex && aapt add ../out.unsigned.apk classes.dex >/dev/null)

echo "[5/5] sign"
KS=${KS:-keystore/release.jks}; KP=${KP:-modrepo123}; KA=${KA:-modrepo}
java -cp tools/apksig.jar:tools/signer.jar Signer build/out.unsigned.apk out/ModRepo.apk "$KS" "$KP" "$KA"
ls -l out/ModRepo.apk
