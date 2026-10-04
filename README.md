# TaczHacker

一个 Minecraft 1.20.1 Forge 客户端模组，为 [Tacz（Timeless and Classics Zero）](https://modrinth.com/mod/timeless-and-classics-zero) 枪械模组提供作弊功能，**仅限娱乐使用**。

> 本模组**没有任何反作弊绕过能力**，仅适用于无反作弊的私人服务器/单机游戏。

## 功能一览

| 功能 | 默认按键 | 说明 |
|------|---------|------|
| 开火静默自瞄 | 开火自动触发 | 开火瞬间自动瞄准附近目标，本地视角无感 |
| 追踪弹 / 穿墙子弹 | 配置项开关 | 仅单机/局域网有效 |
| 低头转圈 | H | 他人视角中角色低头转圈，本地视角正常 |
| 视角锁定自瞄 | V（按住） | 自动平滑锁定目标头部/身体 |
| 透视 X-ray | X | 跳过所有方块渲染，可透视见实体 |
| 飞行挂 | G | 自由飞行，支持开关/按住两种模式 |
| 全亮（Fullbright） | B | 强制最大亮度，关闭时自动恢复 |
| ParCool 长滑铲 | 起滑 C；退出 跳跃键 / Z | 滑铲不自动结束、方向跟随视角、不消耗体力（需 ParCool） |
| 玩家 ESP | J | 从屏幕准心向目标头顶画线，可附带显示血量 |
| 实体信息牌 | K | 目标头顶显示名字 / 血量 / 距离 / 血条 |

注：都可以在cloth config api的模组设置中关闭

## 前置依赖

首次构建时自动下载到 `libs/`：

- [Tacz 1.20.1-1.1.8-hotfix](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)
- [Embeddium 0.3.31+mc1.20.1](https://www.curseforge.com/minecraft/mc-mods/embeddium)
- [Cloth Config API 11.1.136-forge](https://www.curseforge.com/minecraft/mc-mods/cloth-config)
- [ParCool 1.20.1-3.4.3.3](https://modrinth.com/mod/parcool)（功能7 长滑铲用；既是编译期依赖，也会被 `installToMods` 一并装进 mods）

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
- 功能7 长滑铲要装的 **ParCool 本体联机时必须两端都装**（ParCool 自己没写 `displayTest`，Forge 会拒绝连接）
- 无限体力、长滑铲判定都在客户端跑，**本 mod 不用装在服务端**
- 功能8 玩家 ESP 是纯客户端渲染（只画线，不发包），服务端装不装都一样
- 飞行挂请在无反作弊服务器使用

## 致谢与来源说明

功能 8（玩家 ESP）和功能 9（实体信息牌）在设计与实现时参考了
[FDPClient](https://github.com/SkidderMC/FDPClient)（GPL-3.0）的做法：

- **玩家 ESP**：从渲染管线直接取 view / projection 矩阵，而不是自己拼相机旋转
- **实体信息牌**：标签内容的组织方式（名字 / 血量 / 血条 / 距离）
- **实体血量**：FDPClient 提供的 `HealthFromScoreboard` 只对玩家生效
  （原版 `health` criteria 本身就不追踪生物），因此没有采用

**代码为独立编写的 Java 实现，未复制 FDPClient 的源码。**
FDPClient 是 Kotlin 项目、走 GLU 风格的 `project()`；本项目的对应实现是 Java，
用 `Vector4f.mul(Matrix4f)` 自己分段处理，两者的实现路径不同。
本项目的其余功能与 FDPClient 无关。

本项目采用 MIT 许可证，见 [LICENSE](LICENSE)。

> 本节，以及上述功能 8、功能 9 的实现，由 **deepseekv4.1flash** 编写。