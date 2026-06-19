# LiarsBarPaper — MC 版骗子酒馆 Paper 插件

原数据包：[JIJIFUJIJI_Liars_Bar](https://github.com/hunterkyou/JIJIFUJIJI_Liars_Bar) by **hunterkyou**  
Paper 插件移植：**Paper-Port**

支持 Paper 1.20.1 ~ 1.26.2，使用 Display Entity 实现桌面交互，Vault 处理经济。

---

## 目录

- [依赖](#依赖)
- [安装](#安装)
- [快速开始](#快速开始)
- [玩法说明](#玩法说明)
- [命令列表](#命令列表)
- [权限](#权限)
- [配置](#配置)
- [致谢](#致谢)

---

## 依赖

- **Paper 1.20.1 或更高版本**（兼容至 1.26.2）
- **[CraftEngine](https://github.com/Xiao-MoMi/craft-engine)（可选但推荐）**：自动分发资源包
- **[Vault](https://github.com/MilkBowl/Vault)（可选）**：用于饭团币 / 坤坤币模式
- 一个 Vault 经济插件（如 EssentialsX、CMIB 等）

> 如果不使用 CraftEngine，请自行托管 `resourcepack/` 下的资源包，并在 `server.properties` 中配置 `resource-pack`。

---

## 安装

1. 从 [Releases](https://github.com/Arbousier1/LiarsBarPaper/releases) 下载 `LiarsBarPaper-*.jar`。
2. 将 jar 放入服务器的 `plugins/` 文件夹。
3. （推荐）安装 CraftEngine，并把插件内嵌的 CraftEngine bundle 放到 CraftEngine 的 bundle 加载目录。
4. （可选）安装 Vault 和经济插件以启用下注模式。
5. 启动服务器。

---

## 快速开始

1. **管理员创建桌子**
   ```
   /liarbar set lobby_1
   /liarbar build lobby_1
   ```
   或者一步创建：
   ```
   /liarbar create lobby_1
   ```

2. **玩家入座**  
   走到 `lobby_1` 桌附近，右键或左键点击座位实体即可入座；也可使用：
   ```
   /liarbar join lobby_1
   ```

3. **开始游戏**  
   满 4 人自动开始，或手动：
   ```
   /liarbar start lobby_1
   ```

---

## 玩法说明

### 基本规则

- 每局 2~4 人，每轮发 5 张手牌。
- 每轮会随机指定一种**主牌**：`A`、`Q` 或 `K`。
- 玩家轮流出牌，每次最多出 3 张，可谎报牌型。
- 下家可以选择**质疑**上家出的牌。
- 被质疑后翻开牌：
  - 如果出的牌**全是主牌或万能牌**，质疑者输。
  - 如果**含有非主牌且不是恶魔牌**，出牌者输。
  - 如果**含有恶魔牌**，除出牌者外所有存活玩家都要挨一枪。
- 输家要玩一次“俄罗斯轮盘”：6 发子弹中只有 1 发实弹。
  - 实弹：玩家出局。
  - 空弹：存活，剩余子弹数 -1。
- 最后存活且仍有手牌的玩家获胜。

### 卡牌说明

| 卡牌 | 说明 |
|------|------|
| A / Q / K | 普通牌，可与主牌对应 |
| 坤（KUN） | 万能牌，可当任意主牌使用 |
| 恶魔（DEMON） | 特殊牌，只能单独出；被质疑后所有人（除出牌者）都要挨枪 |

### 下注模式

管理员可通过以下命令切换桌子模式：

```
/liarbar mode a life      # 赌命模式，无经济消耗
/liarbar mode a fantuan   # 饭团币模式，加入需扣费，胜者赢取奖池
/liarbar mode a kunkun    # 坤坤币模式
```

> 赌博模式需要在 `config.yml` 中开启 `gambling-mode: true`，并安装 Vault + 经济插件。

### 交互方式

- **座位**：右键 / 左键点击座位实体入座。
- **手牌**：右键 / 左键点击手牌实体选牌（可重复点击取消选择）。
- **出牌按钮**：点击当前玩家附近的出牌按钮执行出牌。
- **质疑按钮**：点击质疑按钮质疑上家。
- 也支持纯命令操作：`/liarbar select`、`/liarbar play`、`/liarbar challenge`。

### 回合超时

每位玩家有 30 秒出牌时间。超时后：
- 如果存在可质疑的上家，自动质疑。
- 如果是首回合或上家是自己，自动打出第一张手牌。

---

## 命令列表

| 命令 | 权限 | 说明 |
|------|------|------|
| `/liarbar set <ID>` | `liarsbar.admin` | 将当前位置设为某张桌子的中心 |
| `/liarbar build <ID>` | `liarsbar.admin` | 为桌子生成 Display Entity 座位 |
| `/liarbar create <ID>` | `liarsbar.admin` | 快速创建并保存桌子 |
| `/liarbar delete <ID>` | `liarsbar.admin` | 删除桌子 |
| `/liarbar gambling [on/off]` | `liarsbar.admin` | 开关赌博模式 |
| `/liarbar join <ID>` | `liarsbar.player` | 加入指定桌子 |
| `/liarbar leave` | `liarsbar.player` | 离开当前游戏 |
| `/liarbar start [ID]` | `liarsbar.player` | 开始游戏（ID 可省略，默认当前所在桌） |
| `/liarbar stop [ID]` | `liarsbar.admin` | 强制结束游戏 |
| `/liarbar mode <ID> <life/fantuan/kunkun>` | `liarsbar.admin` | 设置桌子下注模式 |
| `/liarbar select <1-5>` | `liarsbar.player` | 选择第几张手牌 |
| `/liarbar play` | `liarsbar.player` | 出掉已选择的手牌 |
| `/liarbar challenge` | `liarsbar.player` | 质疑上家 |
| `/liarbar info` | 所有玩家 | 查看所有桌子状态 |
| `/liarbar help` | 所有玩家 | 显示帮助 |

桌子 ID 不限制为 A-E，可使用 `lobby_1`、`vip_2`、`table_10` 等任意 ID 创建多张桌子。每张桌最多 4 人，同时开局的桌数没有代码层上限，实际承载量取决于服务器性能与 Display Entity 数量。

---

## 权限

| 权限节点 | 默认 | 说明 |
|----------|------|------|
| `liarsbar.admin` | op | 创建/删除桌子、设置坐标、开关赌博、强制结束 |
| `liarsbar.player` | true | 加入、离开、开始、选牌、出牌、质疑 |

---

## 配置

`config.yml` 示例：

```yaml
# 是否允许玩家赌饭团币/坤坤币
gambling-mode: true

# 桌子的中心坐标，管理员也可在游戏内用 /liarbar set <ID> 设置
tables: {}
# 示例：
# tables:
#   lobby_1:
#     world: world
#     x: 0
#     y: 64
#     z: 0
```

---

## 致谢

- **hunterkyou**：原数据包《骗子酒馆》作者，资源包与游戏规则均来自其项目。
- **Paper-Port**：Paper 插件移植、Display Entity 交互实现、Vault 经济接入。
- **[PaperMC](https://papermc.io)**：paperweight-userdev 与 Paper 服务端。
- **[CraftEngine](https://github.com/Xiao-MoMi/craft-engine)**：资源包分发方案。
- **[Vault](https://github.com/MilkBowl/Vault)**：经济接口抽象。

---

## 协议

本项目继承原数据包的发布协议。请勿将本插件用于商业用途，转载请注明出处与原作者。
