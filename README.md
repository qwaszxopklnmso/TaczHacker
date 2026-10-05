# TaczHacker

一个 Minecraft 1.20.1 Forge 客户端模组，为 [Tacz（Timeless and Classics Zero）](https://modrinth.com/mod/timeless-and-classics-zero) 枪械模组提供作弊功能，**仅限娱乐使用**。

> 本模组**没有任何反作弊绕过能力**，仅适用于无反作弊的私人服务器/单机游戏。

## 功能一览

| 功能 | 默认按键 | 说明 |
|------|---------|------|
| 开火静默自瞄 | N（开关切换） | 开火瞬间自动瞄准附近目标，本地视角无感，可显示 FOV 搜索圈 |
| 追踪弹 / 穿墙子弹 | 配置项开关 | 仅单机/局域网有效 |
| 低头转圈 | H | 他人视角中角色低头转圈，本地视角正常 |
| 视角锁定自瞄 | V（按住） | 自动平滑锁定目标头部/身体，可显示 FOV 搜索圈 |
| 透视 X-ray | X | 跳过所有方块渲染，可透视见实体 |
| 飞行挂 | G | 自由飞行，支持开关/按住两种模式；默认推进速度可设 0（不自动往前飘） |
| 全亮（Fullbright） | B | 强制最大亮度，关闭时自动恢复 |
| ParCool 长滑铲 | 起滑 C；退出 再按一次 C | 滑铲不自动结束、方向跟随视角、不消耗体力；滑铲中可以正常跳跃；附带「解锁全部动作」（跳过 4.0 技能树的学习要求）（需 ParCool 4.0+） |
| ESP | J | 准心连线 / 方框 / 骨骼，可在配置里分别开关 |
| 实体信息牌 | K | 目标头顶显示名字 / 血量 / 距离 / 血条 |
| 无后坐力（Tacz） | 配置项开关 | 去掉开火时画面的上跳，纯客户端 |
| 自动假开镜（Tacz） | 配置项开关 | 功能1 生效时自动进入假开镜：服务端认为你在开镜、散布按开镜档算，客户端 FOV / 灵敏度 / 移速全不变，不用按右键（需服务端也装本 mod） |

注：都可以在cloth config api的模组设置中关闭

按出来的开关状态（N / H / X / G / B / J / K）会写进配置，**重进游戏后保持**，不用每次进游戏重按一遍。
屏幕左上角那份状态里，`追踪弹` / `穿墙子弹` / `假开镜` 三行是**常驻显示**的，显示的是**实际是否生效**——联机时服务端没开（或没装本 mod）就是 OFF。

## 前置依赖

首次构建时自动下载到 `libs/`：

- [Tacz 1.20.1-1.1.8-hotfix2](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)
- [Embeddium 0.3.31+mc1.20.1](https://www.curseforge.com/minecraft/mc-mods/embeddium)
- [Cloth Config API 11.1.136-forge](https://www.curseforge.com/minecraft/mc-mods/cloth-config)
- [ParCool 1.20.1-4.0.1.0](https://modrinth.com/mod/parcool)（功能7 长滑铲用；既是编译期依赖，也会被 `installToMods` 一并装进 mods。注意它的 jar 里**没有 `zh_cn` 语言文件**，中文界面由本 mod 顺带补，见下方注意事项）

你可以在build后把项目根目录/libs/下的这几个mod连同本mod(build/libs/)一起复制到*1.20.1 Forge*游戏的mods目录中

## 构建

```bash
gradle build
```

构建产物在 `build/libs/` 目录下。

## 安装

```bash
gradle installToMods
```

会把**本 mod**和 **ParCool** 复制到游戏 mods 目录（路径在 `gradle.properties` 的 `game_mods_dir` 里改）。
其余依赖（Tacz / Embeddium / Cloth Config）不参与复制，避免和手动安装的版本重复。

## 配置

所有功能参数在游戏内调整：**Mods 列表 → TaczHacker → 配置**

- 使用 Cloth Config API
- 支持按键绑定修改

## 注意事项

- 本 mod 是**纯客户端为主**：服务器不装也能进（`displayTest` + 通道的 `acceptMissingOr` 两处都放宽了）
- 部分功能（追踪弹、穿墙子弹）**仅单机/双端都装mod有效**
- 功能7 长滑铲要装的 **ParCool 本体联机时必须两端都装**（ParCool 自己没写 `displayTest`，Forge 会拒绝连接），且要 **4.0+**（4.0 重写了动作 API，3.x 的滑铲 mixin 不兼容）
- **ParCool 4.0 的技能树默认开着，会把滑铲锁死**：`enable_skill_tree` 默认 `true`（存在世界存档的
  `saves/<存档名>/serverconfig/parcool-server.toml`），除抓钩外每个动作都要花经验等级在技能树里学习
  （快跑/爬行各 1 级、滑铲 10 级…）。没学过时 `Parkourability#permit` 恒为 false，**所有跑酷动作都起不了手**
  ——现象就是按滑铲键毫无反应，且与「长滑铲开关」无关。
  本 mod 的「解锁 ParCool 全部动作」（功能7 分类，**默认开**）直接让 ParCool 认为你已学会全部动作，
  不用花经验；技能树开关本身和每个动作的 `available` 仍然有效。
  - 起手判定在本地客户端做，所以**服务端没装本 mod 也生效**（ParCool 的动作默认 `triggeredSide = CLIENT`）；
    由服务端判定起手的动作（如落地翻滚 Breakfall）要服务端也装本 mod 才会解锁
- **ParCool 中文界面**：ParCool 4.0.1.0 的 jar 里只有 `en_us` / `ja_jp` / `ru_ru`，**没有 `zh_cn`**，
  所以它的按键名/动作名全是英文。本 mod 在自己的 `assets/taczhacker/lang/zh_cn.json` 里补了 ParCool 的
  全部 171 个键（语言文件是全局合并的，与命名空间无关），装本 mod 就顺带把 ParCool 界面变中文
- 无限体力、长滑铲判定、技能树解锁都在客户端跑，**本 mod 不用装在服务端**（`triggeredSide = SERVER` 的动作除外）
- 功能8 ESP 是纯客户端渲染（连线 / 方框 / 骨骼都不发包），服务端装不装都一样
- 无后坐力只去掉**画面**的上跳：Tacz 的后坐力是渲染层的摄像机偏移，本来就不影响弹道
- **无扩散做不到**：Tacz 的子弹散布是服务端生成子弹时算的（`ModernKineticGunItem.doBulletSpread`），
  客户端发出去的 `ClientMessagePlayerShoot` 里只有 timestamp 和 chargeProgress，连方向都不带
- **自动假开镜（Tacz）**：**功能1（开火静默自瞄）生效时自动进入假开镜**（不用按右键），
  拦掉 Tacz 的开镜包，只发本 mod 的「我在假开镜」状态包，
  服务端在散布档位判定（`InaccuracyType#getInaccuracyType`）里按 `AIM` 档取
  —— 默认散布 `AIM 0.15` vs `STAND 5.0`，所以精度按开镜算。
  - 为什么是持续开着而不是开火那一瞬间才开：服务端算散布是在收到开火包的时候，
    假开镜状态得提前送到，时序错开就有几率第一枪吃不到精度；持续挂着没有额外代价
  - **需要服务端也装本 mod**，服务端没装时功能自动失效，开镜键退回 Tacz 原版行为
  - 服务端自己的 `isAiming` / `aimingProgress` 全程保持 `false` / `0`，
    所以移速惩罚、冲刺打断、开镜动画、灵敏度同步统统不触发（这正是需求要的）
  - 因此**假开镜生效期间按右键不再进入真正的瞄准镜画面**（本地 `clientIsAiming` 一直是 false）；
    想看瞄准镜请先关掉这个开关或者关掉功能1
  - 枪包 Lua 脚本如果自己去读 `api:getAimingProgress()`，读到的仍是 0
    （只保证 `calcSpread` 拿到的 `inaccuracy` 参数是开镜档）
- 飞行挂请在无反作弊服务器使用

## 致谢与来源说明

功能 8（玩家 ESP）和功能 9（实体信息牌）在设计与实现时参考了
[FDPClient](https://github.com/SkidderMC/FDPClient)（GPL-3.0）的做法：

- **ESP 投影**：从渲染管线直接取 view / projection 矩阵，而不是自己拼相机旋转
- **实体信息牌**：标签内容的组织方式（名字 / 血量 / 血条 / 距离）
- **实体血量**：FDPClient 提供的 `HealthFromScoreboard` 只对玩家生效
  （原版 `health` criteria 本身就不追踪生物），因此没有采用

**代码为独立编写的 Java 实现，未复制 FDPClient 的源码。**
FDPClient 是 Kotlin 项目、走 GLU 风格的 `project()`；本项目的对应实现是 Java，
用 `Vector4f.mul(Matrix4f)` 自己分段处理，两者的实现路径不同。
本项目的其余功能与 FDPClient 无关。

本项目采用 MIT 许可证，见 [LICENSE](LICENSE)。

> 本项目的代码由 AI 编写，人机分工大致是：
>
> - **deepseek-flash**：实现功能、修 bug、写文档
> - **qwaszxopklnm**：提出需求、决定功能取舍与优先级、发现并报告问题、验证结果
>
> 模型版本：v1.0.1 及以前用 v4-flash，v1.1.0 及以后用 v4.1-flash。
