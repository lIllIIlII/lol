# 吸析At (XiXiAt)

> 网盘分享链接解析 · 高速下载 · iOS 风格液态玻璃界面
> 基于开源项目 [YunX](https://github.com/CYQawa/YunX)（AGPL-3.0）优化增强 · 完全免费，禁止倒卖

---

## 应用简介

吸析At 是一款安卓网盘工具：粘贴分享链接即可解析并高速下载文件，无需打开网页、无需等待广告。

- **支持平台**：夸克网盘 · UC 网盘 · 迅雷网盘 · 百度网盘 · 139 邮箱网盘 · 123 云盘 · 蓝奏云 · 蓝奏云优享版（ilanzou.com） · 奶牛快传 · 小飞机网盘
- **免登录解析**：蓝奏云 / 蓝奏云优享版 / 奶牛快传 / 小飞机无需账号即可解析下载；其余平台登录后可解锁高速直链
- **防 DNS 污染**：蓝奏云家族与优享版域名智能 DNS（阿里 DoH 真实 IP 优先 + 系统 DNS 兜底），应对运营商网络屏蔽
- **多线程下载**：OkHttp 分片并发（最高 512 线程）+ 断点续传 + 限速/重试可调
- **取链即删**：转存取链后自动清理临时目录，不在网盘留垃圾
- **本地加密**：Cookie 凭证 AES 加密落库，仅存本机；支持加密备份/恢复
- **界面**：iOS 风格弹窗 + 液态玻璃（预模糊壁纸采样，零逐帧开销）

## 下载安装

| 渠道 | 地址 |
| --- | --- |
| GitHub Release（推荐） | https://github.com/lIllIIlII/lol/releases/latest |
| 国内加速直链 | https://gh-proxy.com/https://github.com/lIllIIlII/lol/releases/latest |

- 安装包名：`com.ccat.pan`，更新安装不会丢数据（同签名覆盖升级）
- 应用内 **设置 → 通用 → 检查更新** 会自动读取本仓库 `updated.json`，新版本静默提醒、非强制更新

## 使用说明

1. **解析**：首页粘贴分享链接（可带提取码文案，自动识别），点击「开始解析」
   - 支持的链接形态：`pan.quark.cn/s/xx`、`drive.uc.cn/s/xx`、`pan.xunlei.com/s/xx`、`pan.baidu.com/s/xx`、`caiyun.139.com/m/i?xx`、`123pan.com/s/xx`、`*.lanzou*.com/xx`（含 wwbll.lanzoul.com / wwapg.lanzoub.com 等变体）、`ilanzou.com/s/xx`（蓝奏云优享版）、`cowtransfer.com/s/xx`、`share.feijipan.com/s/xx`
2. **浏览**：解析后进入文件列表，可进入子文件夹、多选、搜索
3. **下载**：点击文件即取直链并加入下载队列（下载页查看进度，完成后点击「打开」）
4. **网盘**：登录各平台账号（WebView 抓取 Cookie，本地 AES 加密保存），可浏览云盘、转存、创建分享
5. **设置**：
   - `下载线程数`：按平台调整并发分片数（默认 16）
   - `下载保存目录`：默认系统下载目录，可自选 SAF 目录
   - `反馈联系`：扫微信 / QQ 二维码加开发者好友直接反馈问题（需要详细日志时可先「导出日志」发给好友）
   - `开源网址`：本仓库地址

## 反馈联系方式

应用内「设置 → 反馈联系（原汇报日志）」展示开发者微信好友码与 QQ 好友码：
- 扫码加好友后直接描述问题（附分享链接 / 报错提示 / 操作步骤最佳）
- 需要详细排查时：`设置 → 导出日志` 导出已脱敏日志文件后发给开发者


## 更新发布（开发者）

新版本发布三步：

1. 改 `app/build.gradle.kts` 的 `versionCode` / `versionName`，构建 `assembleRelease`
2. GitHub 网页创建 Release（tag 如 `v1.4.0`），上传 APK 资产（建议 ASCII 文件名如 `XiXiAt_1.4.0.apk`）
3. 更新仓库根目录 `updated.json` 的 `version` / `url` / `notes`（`url` 填 gh-proxy 加速链，`mirror` 填直链）

老版本 APP 会在启动时静默检测并提示更新（可忽略本次）。

## 常见问题

- **解析失败**：多为网络或链接失效；蓝奏云偶发反爬，重试即可；账号登录后更稳定
- **下载速度慢**：调大「下载线程数」；百度非会员大文件有官方限速（应用会提示）
- **WebView 登录页空白**：官网脚本较大，等待数秒；自动重试一次；仍空白请切换网络
- **更新下载慢**：应用会先探测 GitHub 直链，国内网络自动切 gh-proxy 镜像

## 构建

```bash
# Android Studio 打开直接 Run；命令行：
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk（R8 + 资源压缩，约 5MB）
```

要求：JDK 17+、Android SDK Platform 36。

## 隐私与安全

- 凭证仅存本机（AES 加密），不上传任何服务器
- 日志导出/汇报前自动脱敏（Cookie / token 等自动打码）
- 无任何统计 SDK、无广告、无启动页

## 开源协议

本项目基于 YunX（AGPL-3.0）修改，按协议同样以 **AGPL-3.0** 开源。
感谢 [CYQawa](https://github.com/CYQawa) 与所有 YunX 贡献者。
