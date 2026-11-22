# Omnix（奥米）

> Android 模块仓库与玩机工具箱 —— 一站管理 KernelSU / LSPosed 模块、应用与系统信息。

![version](https://img.shields.io/badge/version-2.1.0-0c8f9e)
![platform](https://img.shields.io/badge/Android-8.0%2B-3ddc84)
![license](https://img.shields.io/badge/license-MIT-blue)

## 简介

Omnix 是一个本地运行的 Android 模块管理与玩机工具，把「找模块、下模块、装模块、管理模块」和常见的系统工具整合到一个界面里。所有网络请求都在设备本地发起，不上传任何设备信息。

## 功能

### 模块仓库
- **KernelSU 官方仓库**（modules.kernelsu.org）
- **KernelSU-Next / Magisk Alt Repo / Xposed 模块组织** 等多来源聚合
- **LSPosed 官方仓库**（modules.lsposed.org，分页读取全部 1400+ 模块）
- 搜索、按热度/版本排序，每条显示来源仓库
- 应用内下载，完成后弹窗显示保存路径并直接安装 / 刷入

### 已安装模块
- Root 模块（KSU / Magisk / APatch）与 Xposed 模块分开展示
- 显示模块名、版本、大小与动态描述
- 详情页可：禁用/启用、运行 `action.sh`、卸载
- Xposed 模块识别基于 LSPosed 数据库，识别率与 LSPosed 管理器一致

### 管理器收录
- Magisk、KernelSU、KernelSU-Next、SukiSU Ultra、APatch
- 显示最新版本与本地/已装版本，一键下载安装

### 系统工具
| 功能 | 说明 |
|---|---|
| 应用冻结 | 冻结/启用应用（含系统应用），`pm disable-user` |
| 镜像分区 | 167+ 分区列表，支持导出 `.img` 与按 A/B 槽位刷入 |
| 应用备份 | 自选应用导出安装包（含 split APK） |
| 字库备份 | 关键分区 / 完整备份，带容量预估 |
| 机型伪装 | 品牌/型号/指纹一键伪装，自动备份原值可还原 |
| 刷入 LSPosed | 内置 LSPosed 模块包，可选附带 Zygisk Next |
| 应用收录 | Shizuku / Termux / MT 管理器 / Scene 等常用工具 |
| 订阅与备份 | 自定义仓库源、配置导出导入 |

### 外观
- 8 套主题配色（雾泉 / 青瓷 / 暮云 / 樱粉 / 琥珀 / 摩卡 / 石墨 / 深海）
- 日间 / 夜间 / 跟随系统
- 全站磨砂玻璃质感，浮岛式底栏（可切停靠）
- 桌面图标随主题切换（8 套自适应图标）

## 截图

| 首页 | 模块 | 多功能 | 设置 |
|---|---|---|---|
| Root 环境 + 设备信息 + 模块数 | 分来源浏览与下载 | 系统工具集合 | 主题与权限 |

## 构建

项目不依赖 Gradle，使用 aapt + javac + D8 + apksig 的最小工具链，aarch64 环境可直接构建。

```bash
# 依赖
apt install aapt openjdk-17-jdk-headless zip

# 工具 jar（放到 tools/）
#   android-all.jar  org.robolectric:android-all:14-robolectric-10818077
#   r8.jar           com.android.tools:r8
#   apksig.jar       com.android.tools.build:apksig:8.13.2

# 签名密钥
keytool -genkeypair -keystore keystore/release.jks -alias modrepo \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass modrepo123 -dname CN=Omnix

# 构建
./build.sh
# 产物：out/ModRepo.apk
```

图标与主题由脚本生成：

```bash
python3 tools/gen_themes.py      # 8 套主题 CSS
python3 tools/gen_icon_omnix.py  # 8 套主题图标（纯色底 + 玻璃质感的大写 O）
```

## 权限说明

| 权限 | 用途 |
|---|---|
| 存储 | 下载模块、导出备份 |
| Root（su） | 刷入模块、冻结应用、读写分区、伪装属性 |

应用不联网上传任何数据；订阅源与配置只保存在本机。


## 打赏与交流

### 支付宝赞赏（固定 ¥5）
纯自愿，不影响任何功能。

<img src="app/assets/www/img/pay.png" width="200" alt="支付宝赞赏码">

### QQ 交流群
群号：**1137331073** · [点此加群](https://qm.qq.com/q/moshovKCSk)

<img src="app/assets/www/img/group.png" width="200" alt="QQ 群二维码">

群内提供：问题反馈、版本内测、使用交流。提问前请先看群公告与 README。

## 免责声明

- 本应用是本地工具，不生产、不托管、不修改任何模块文件，模块内容由第三方作者负责。
- **刷入模块、写分区、冻结应用、伪装属性均属高危操作**，可能导致系统异常、数据丢失或设备无法开机，请自行备份并确认来源可信。
- 因使用本应用造成的任何直接或间接损失，由使用者自行承担。

## 协议

MIT License
