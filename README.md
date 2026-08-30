# TaczHacker

一个 Minecraft 1.20.1 Forge 客户端模组，为 [Tacz（Timeless and Classics Zero）](https://modrinth.com/mod/timeless-and-classics-zero) 枪械模组提供作弊功能，**仅限娱乐使用**。

> 本模组**没有任何反作弊绕过能力**，仅适用于无反作弊的私人服务器/单机游戏。

## 功能一览

| 功能 | 默认按键 | 说明 |
|------|---------|------|
| 开火静默自瞄 | 开火自动触发 | 开火瞬间自动瞄准附近目标，本地视角无感 |
| 低头转圈 | H | 他人视角中角色低头转圈，本地视角正常 |
| 视角锁定自瞄 | V（按住） | 自动平滑锁定目标头部/身体 |
| 透视 X-ray | X | 半透明/经典全隐藏模式，支持自定义方块列表 |
| 飞行挂 | G | 自由飞行，支持开关/按住两种模式 |
| 全亮（Fullbright） | B | 强制最大亮度，关闭时自动恢复 |

注：都可以在cloth config api的模组设置中关闭

## 前置依赖

首次构建时自动从 CurseForge CDN 下载：

- [Tacz 1.20.1-1.1.8-hotfix](https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero)
- [Embeddium 0.3.31+mc1.20.1](https://www.curseforge.com/minecraft/mc-mods/embeddium)
- [Cloth Config API 11.1.136-forge](https://www.curseforge.com/minecraft/mc-mods/cloth-config)

你可以在build后把项目根目录/libs/下的这三个mod连同本mod(build/libs/)一起复制到*1.20.1 Forge*游戏的mods目录中

## 构建

```bash
gradle build
```

构建产物在 `build/libs/` 目录下。

## 配置

所有功能参数在游戏内调整：**Mods 列表 → TaczHacker → 配置**

- 使用 Cloth Config API
- 支持按键绑定修改

## 注意事项

- 部分功能（追踪弹、穿墙子弹）**仅单机/双端都装mod有效**
- 飞行挂请在无反作弊服务器使用