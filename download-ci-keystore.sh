#!/bin/bash
# download-ci-keystore.sh - 从 GitHub Actions 下载 debug.keystore
#
# 解决签名不一致问题

set -e

REPO="XenoAmess-bot/qr_code_simple"
LOCAL_KEYSTORE="$HOME/.android/debug.keystore"
TEMP_DIR=$(mktemp -d)

echo "=========================================="
echo "下载 GitHub Actions 的 debug.keystore"
echo "=========================================="
echo ""

# 检查 gh CLI
if ! command -v gh &> /dev/null; then
    echo "错误: 需要安装 GitHub CLI (gh)"
    echo "安装: https://cli.github.com/"
    exit 1
fi

# 检查登录状态
if ! gh auth status &> /dev/null; then
    echo "错误: 请先登录 GitHub CLI"
    echo "运行: gh auth login"
    exit 1
fi

echo "获取最新的 workflow run..."
RUN_ID=$(gh run list --repo "$REPO" --workflow="Build APK" --limit 1 --json databaseId -q '.[0].databaseId')

if [ -z "$RUN_ID" ]; then
    echo "错误: 未找到 workflow run"
    exit 1
fi

echo "找到最新的 workflow run: $RUN_ID"
echo ""

# 下载 keystore artifact
echo "下载 debug-keystore artifact..."
cd "$TEMP_DIR"
gh run download "$RUN_ID" --repo "$REPO" --name "debug-keystore" || {
    echo "错误: 下载失败，请检查是否有权限访问该仓库"
    rm -rf "$TEMP_DIR"
    exit 1
}

if [ ! -f "debug.keystore" ]; then
    echo "错误: 下载的文件中未找到 debug.keystore"
    rm -rf "$TEMP_DIR"
    exit 1
fi

echo "下载成功!"
echo ""

# 备份本地 keystore
if [ -f "$LOCAL_KEYSTORE" ]; then
    BACKUP="$LOCAL_KEYSTORE.backup.$(date +%Y%m%d%H%M%S)"
    echo "备份本地 keystore 到: $BACKUP"
    cp "$LOCAL_KEYSTORE" "$BACKUP"
fi

# 安装新 keystore
echo "安装 CI keystore 到: $LOCAL_KEYSTORE"
mkdir -p "$HOME/.android"
cp "$TEMP_DIR/debug.keystore" "$LOCAL_KEYSTORE"

# 清理
cd /
rm -rf "$TEMP_DIR"

echo ""
echo "=========================================="
echo "✅ 安装完成!"
echo "=========================================="
echo ""
echo "现在你可以本地构建 APK，签名将与 CI 一致。"
echo ""
echo "验证签名:"
echo "  keytool -list -v -keystore ~/.android/debug.keystore -storepass android"
echo ""
