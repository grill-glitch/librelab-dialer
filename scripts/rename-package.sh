#!/bin/bash
# librelab-dialer 包名独立化:com.android.* → org.librelab.*
# 顺序很重要:先替换更长的子命名空间,再替换主命名空间
set -e
cd "$(dirname "$0")/.."

# 需要处理的文件(排除 .git)
FILES=$(grep -rlE 'com\.android\.(dialer|incallui|voicemail|contacts\.common)' . \
  --exclude-dir=.git --exclude-dir=out 2>/dev/null || true)

echo "待处理文件数: $(echo "$FILES" | grep -c . || true)"

for f in $FILES; do
  # 子命名空间(确保整词边界,避免 org.librelab.dialerxx 误伤)
  sed -i \
    -e 's/com\.android\.contacts\.common/org.librelab.contacts.common/g' \
    -e 's/com\.android\.incallui/org.librelab.incallui/g' \
    -e 's/com\.android\.voicemail/org.librelab.voicemail/g' \
    -e 's/com\.android\.dialer/org.librelab.dialer/g' \
    "$f"
done

echo "=== 剩余 org.librelab.dialer 引用(应为 0) ==="
grep -rn 'com\.android\.dialer' . --exclude-dir=.git 2>/dev/null | grep -v 'org.librelab.dialer' | head || true
grep -rc 'com\.android\.dialer' . --exclude-dir=.git 2>/dev/null | grep -v ':0' | head || echo "clean"

echo "=== 目录迁移 ==="
mkdir -p java/org/librelab
for pkg in dialer incallui voicemail contacts; do
  if [ -d "java/com/android/$pkg" ]; then
    mkdir -p "java/org/librelab/$pkg"
    # 用 cp -a 保留文件,再删旧目录
    cp -a "java/com/android/$pkg/." "java/org/librelab/$pkg/"
    rm -rf "java/com/android/$pkg"
  fi
done
# 清理空的 com/android 目录
rmdir -p java/com/android 2>/dev/null || true
echo "=== 目录迁移后 java 结构 ==="
ls java/org/librelab/
ls java/com 2>/dev/null || echo "java/com 已清空"
