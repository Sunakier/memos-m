# 自动 CI 与正式发布

参考 PaperWorld 的版本账本与校验后发布流程，适用于我们的 `main` 自用分支。

## 日常 push

- 每次向 `main` push 都触发 CI，包括文档、测试和工作流变更。
- 运行 3 个现有定向测试类：编辑提交、写入账号隔离、无版本 API 兼容；共 20 个现有用例。
- 成功后以正式签名和 R8 构建 `arm64-v8a`、`armeabi-v7a`，自动发布 `ci-<编号>` 预览 Release。
- 预览版名为 `<versionName>-ci-<编号>`，包名仍为 `top.lazyerpaper.memosm`，可以覆盖同包名的较低编号安装包。
- Release 附件包含两个 APK、R8 mapping、SHA-256 校验和、`release-manifest.json`。发布前核对签名、包名、版本、ABI、许可资源及远端附件大小/哈希。
- 常规 `CI` 的 PR 和非 main 手动运行使用本地回退版本编号，生成保留 1 天的 Debug Artifact。
- CI 的 JVM、构建及静态校验不代表设备交互验收。

`dev` 继续保持上游镜像。`feature/*` 继续用于上游贡献；这里的分发工作流与个人包名配置只放在 main。

## QuickEdit 分支构建

`QuickEdit Build CI` 手动运行时可以选择分支。Fork 的 Actions Artifacts 分别提供 Debug APK 和签名的 Release APK，Release 产物包含 ARM64、ARMv7、R8 mapping 与 SHA-256 校验和。

Release 包名为 `top.lazyerpaper.memosm`，版本名带有 `quickedit` 和提交短哈希。版本编号读取当前已发布的编号，分支构建只上传到 Actions Artifacts。

## 正式发布

1. 修改根 `version.properties` 的 `versionName`，合入并推送 main。
2. 在已进入 main 历史的目标提交创建并推送严格匹配的 `vX.Y.Z` 标签。例：文件版本为 `1.0.4` 时推送 `v1.0.4`。
3. Release 工作流自动领取比之前预览/正式发布更高的编号，以标签固定源码构建、校验并发布正式版。

预览不会修改 GitHub 的 Latest 正式版入口。现有稳定版本、旧标签和附件保持原样；正式标签不能移动，已发布附件不能覆盖。

## 版本编号与重试

- 预览和正式版共用 `automation/release-state` 分支的账本，以及 `memosm-distribution` 并发组。
- `version.properties` 中的 `versionCode=103` 是冻结的本地回退值/迁移下限，日常不递增；CI 通过 Gradle 的 `versionCodeOverride`、`versionNameOverride` 注入分发版本。
- 账本只允许普通快进写入，不合入 main/dev，不删除、重置或强推。备份仓库时保留账本和标签。
- 预览按 Actions run ID、正式按标签分配身份；重跑相同任务复用编号。失败可以留下空号，编号不依赖提交数量。
- 旧源码的排队任务只验证，不分发过时预览。GitHub 并发组可能替换尚未运行的等待项，运行中的分发不会自动取消。
- 部分发布后的重试优先恢复已封存的 APK、mapping 和校验和。已有附件与哈希不符时失败，保留旧内容；修正源码后发新提交/新版本。
- 已完成任务重跑不重新构建、不覆盖 Release。预览转正式需要后续正式版的编号更高，不能绕过 Android 的升级顺序。

## 签名与入口

沿用现有 `KEYSTORE`、`RELEASE_KEYSTORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD` Secrets，以及可选 `GRADLE_CACHE_KEY`。分发缺少签名或签名不匹配时直接失败。

当前发布证书 SHA-256：`17a7f4feee874fd644d60616d1938365467f07023acd30c9f71b8a8be49b7022`。

旧 Canary 工作流保留为手动入口，包名带 `.canary`；Insider 标签工作流与 nightly API 集成测试保留。
