# 更好的幸运效果 — 整合包作者指南

> 面向整合包作者的文档。开发 / 构建 / 实现细节见 [DEVELOPMENT.md](DEVELOPMENT.md)。

| 项 | 值 |
| --- | --- |
| 模组 id / 命名空间 | `betterluck` |
| 需装客户端 / 服务端 | 两边都要装 |
| Minecraft | 1.21.1 |
| 依赖 | 无。NeoForge 即可 |
| 可选联动 | KubeJS（不装也能跑，只是脚本绑定不生效） |
| 配置文件 | `config/betterluck-common.toml` |

---

## 0. 术语先对齐

这个模组里有三个容易混的名字，看配置文件前先分清：

| 名字 | 是什么 | 归属 |
| --- | --- | --- |
| **幸运** | 原版的**状态效果**（`minecraft:luck`） | 原版，我们没改 |
| **运气附魔** | 本模组加的**附魔**（`betterluck:luck`） | 本模组 |
| **幸运等级** | `minecraft:generic.luck` **属性**的值 | 属性系统 |

三者的关系：幸运效果和运气附魔都会往**幸运属性**上加值，而模组所有的收益都在读这个属性。
所以**任何能提高幸运属性的东西都会自动生效**——包括别的模组。

> 中文名之所以把附魔叫「运气附魔」而不是「幸运附魔」，就是为了和原版的**幸运效果**区分开。

---

## 1. 这个模组做了什么

### 背景：原版的幸运为什么感觉没用

原版的幸运效果**确实**已经在起作用，但只在两个地方：

- `LootPool` 的 `quality` 字段（影响权重抽取）
- `LootPool` 的 `bonus_rolls` 字段（影响额外抽取次数）

问题在于：原版**怪物战利品表几乎不用 `quality`**，而**方块掉落完全不看幸运**。
所以喝下幸运药水，玩家大概率感觉不到任何变化。

这个模组把这两块补上，顺带扩展了幸运的来源。

### 幸运来源

| 来源 | 说明 |
| --- | --- |
| **幸运药水** | 原版有定义但拿不到，现在可以酿造 |
| **运气附魔** | 可附在**胸甲**和**挖掘工具**上，最高 5 级，**与经验修补互斥** |

酿造配方：

| 操作 | 产出 | 时长 |
| --- | --- | --- |
| 粗制的药水 + 绿宝石 | 幸运药水（幸运 I） | 5 分钟 |
| 幸运药水 + 红石 | 延长版（幸运 I） | 10 分钟 |
| 幸运药水 + 萤石粉 | 强化版（幸运 II） | 3 分钟 |

创造模式物品栏里有普通 / 延长 / 强化三种，外加喷溅与滞留形态。

### 幸运带来的收益

| 场景 | 触发条件 | 效果 | 数值 |
| --- | --- | --- | --- |
| 钓鱼 | 有幸运 | 按概率把整次渔获换成一次「宝藏」表抽取 | 10% / 级，上限 40% |
| 挖矿 | 幸运 + **时运**镐 + 矿石 | 矿物掉落数量增加 | 走等级表 |
| 击杀 · 稀有概率 | 有幸运 | 额外重掷实体战利品表，只保留稀有物 | 1 次 / 级，上限 2 次 |
| 击杀 · 数量 | 幸运 + **抢夺**武器 | **普通**掉落数量增加 | 走等级表 |
| 宝箱 · 金苹果 | 有幸运 | 金苹果按概率**整堆**变成附魔金苹果 | 10% / 级，上限 40% || 宝箱 · 数量 | 有幸运 | 钻石 / 锻造模板 数量增加 | 走等级表 |
| 宝箱 · 劣质附魔 | **与幸运无关** | 降低指定附魔的出现概率 | 按配置 |

两条刻意的克制，调整数值时**别把这两条拆了**：

- **数量与概率分家**：稀有掉落只走概率线，普通掉落只走数量线。
  同一个物品不会被「提高掉率」和「提高数量」各乘一次。
- **额外重掷不重复给**：已经掉过的稀有物不再补。
  所以凋灵的下界之星这类**必掉物**不会被翻倍。

---

## 2. 配置文件

`config/betterluck-common.toml`。**改完要重启游戏**才会重新读取。

### 等级表（数量类加成的核心）

```toml
[luck.scaling]
    bonusByLevel = ["0.2", "0.4", "0.8", "1.2", "1.6"]
```

第 N 项对应等级 N，**超出表的长度就取最后一项**。写成字符串（`"0.5"` = +50%）。

加成的三个来源**各自查表后相加**：

| 来源 | 怎么算 |
| --- | --- |
| 胸甲上的运气附魔等级 | 查表 |
| 主手物品上的运气附魔等级 | 查表 |
| 其余幸运 | 用 `player.getLuck()` 减去上面两项装备贡献，剩下的都算这里 |

用默认表，满配 = 160% + 160% + 40% = **+360%**。

> **为什么「其余幸运」是减出来的**：这样药水、以及**任何别的模组**给的幸运都会自动
> 落进这一档，不用逐个来源枚举。

### 概率类

```toml
[luck.fishing]
    treasureChancePerLuck = 0.10   # 每级幸运提升的概率
    treasureChanceMax     = 0.40   # 上限

[luck.chests]
    goldenAppleChancePerLuck = 0.10
    goldenAppleChanceMax     = 0.40

    # 这些宝箱表里的金苹果不做转化
    goldenAppleExcludedTables = ["minecraft:chests/igloo_chest"]

    # 每个箱子额外增加的数量上限（0 = 不限制）
    maxExtraPerChest = 12

    # 每一堆额外增加的数量上限（0 = 不限制）
    maxExtraPerStack = 3
```

公式统一是 `min(系数 × 总幸运, 上限)`。**这两项不走等级表**——因为「100% 必定出宝藏」
显然不合理，它们需要各自封顶。

> **为什么默认排除雪屋箱子**：只有 `minecraft:chests/igloo_chest` 的金苹果是
> **100% 必出**的（单独一个池、`rolls: 1`、只有一个条目），转化就等于每次开箱
> 白送一个附魔金苹果。其余宝箱表的金苹果都是概率抽出的，最高的也只有 33%
> （试炼密室 `reward_unique`），再乘上转化概率之后仍然是稀有事件。
>
> 各表出金苹果的概率（原版数据估算）：
>
> | 概率 | 宝箱表 |
> | --- | --- |
> | **100%** | `chests/igloo_chest` ← 默认排除 |
> | 33% | `chests/trial_chambers/reward_unique` |
> | 28% | `chests/abandoned_mineshaft` |
> | 24% | `chests/desert_pyramid` |
> | 22% | `chests/woodland_mansion`、`chests/simple_dungeon` |
> | 21% | `chests/ruined_portal` |
> | 10% | `chests/trial_chambers/reward_ominous_rare`、`bastion_other`、`bastion_hoglin_stable` |
> | 4% | `chests/underwater_ruin_big` |
> | 3% | `chests/stronghold_corridor` |
>
> **战利品表不支持标签**（原版没有 `loot_table` 标签注册表），
> 所以这份排除名单只能放配置里，没法做成数据包标签。

### 稀有掉落重掷

```toml
[luck.combat]
    rareDropRollsPerLuck = 1   # 每级幸运多掷几次
    rareDropMaxRolls     = 2   # 次数上限
```

公式是 `min(向下取整(系数 × 总幸运), 上限)`。设为 `0` 即关闭这条收益。

### 劣质附魔

```toml
[luck.inferiorEnchantments]
    list = [
        "minecraft:binding_curse=0.25",
        "minecraft:vanishing_curse=0.25",
        "minecraft:bane_of_arthropods=0.5"
    ]
```

每行 `"<附魔id>=<保留系数>"`：

| 系数 | 含义 |
| --- | --- |
| `1.0` | 不干预 |
| `0.25` | 出现概率降到约 1/4 |
| `0.0` | 完全不会出现 |

格式写错的条目会被**静默忽略**（启动日志里会打印实际生效的清单，可以照着核对）。

> **这一条与幸运无关**，无条件生效，且**只作用于宝箱战利品**。
>
> **为什么只能「抽取之后重掷」**：宝箱附魔书走的是 `EnchantRandomlyFunction`，
> 它是**等概率均匀抽取**——附魔 JSON 里的 `weight` 字段在这里**完全不起作用**。
> 所以降低概率只能事后把它换成同一抽取池里的另一个非劣质附魔。

### 完整默认值速查

| 配置项 | 默认 | 作用 |
| --- | --- | --- |
| `scaling.bonusByLevel` | `0.2 / 0.4 / 0.8 / 1.2 / 1.6` | 数量类加成的等级表 |
| `combat.rareDropRollsPerLuck` | `1` | 稀有掉落重掷次数 / 级 |
| `combat.rareDropMaxRolls` | `2` | 重掷次数上限 |
| `fishing.treasureChancePerLuck` | `0.10` | 钓鱼宝藏概率 / 级 |
| `fishing.treasureChanceMax` | `0.40` | 概率上限 |
| `chests.goldenAppleChancePerLuck` | `0.10` | 金苹果转化概率 / 级 |
| `chests.goldenAppleChanceMax` | `0.40` | 概率上限 |
| `chests.goldenAppleExcludedTables` | `["minecraft:chests/igloo_chest"]` | 金苹果不转化的宝箱表 |
| `chests.maxExtraPerChest` | `12` | 每个箱子额外增加的数量上限（0 = 不限制） |
| `chests.maxExtraPerStack` | `3` | 每一堆额外增加的数量上限（0 = 不限制） |
| `inferiorEnchantments.list` | 3 项 | 劣质附魔清单 |

---

## 3. 可自定义的数据标签

都可以在数据包里覆盖，**不需要改模组本体**。

| 标签 | 作用 | 默认内容 |
| --- | --- | --- |
| `betterluck:rare_drops` | 哪些物品算「稀有掉落」，只吃概率加成 | 16 项（各类头颅、三叉戟等） |
| `betterluck:chest_bonus_items` | 宝箱里哪些物品吃数量加成 | 41 项，见下 |
| `betterluck:luck_enchantable` | 运气附魔能附在哪些物品上 | `#enchantable/chest_armor` + `#enchantable/mining_loot` |
| `betterluck:exclusive_set/luck` | 运气附魔与哪些附魔互斥 | `minecraft:mending` |
| `minecraft:non_treasure` | 运气附魔的获取途径 | 加了 `betterluck:luck` |

> **获取途径只需改一个标签**。`in_enchanting_table`（附魔台）、`tradeable`（村民交易）、
> `on_random_loot`（宝箱）、`on_mob_spawn_equipment`（怪物装备）**全都引用
> `#minecraft:non_treasure`**，所以往它里面加/删一条就四处同时生效。
>
> **`luck_enchantable` 为什么存在**：`supported_items` 的编解码器是
> `either(单个标签, 条目列表)`——列表里只能放**具体物品**，放 `#标签` 会让整个附魔注册表
> 加载失败。所以要组合多个标签，必须自己建一个物品标签。

#### `chest_bonus_items` 里有什么（41 项）

22 个显式条目 + `#minecraft:trim_templates` 展开的 18 个盔甲纹饰模板 +
下界合金升级模板 = **41 项**。

| 类别 | 物品 |
| --- | --- |
| 宝石 | 钻石、绿宝石、青金石、紫水晶碎片、下界石英、回响碎片 |
| 金属 | 铁锭、金锭、铁粒、金粒、远古残骸、下界合金碎片、下界合金锭 |
| 材料 | 红石、火药、萤石、岩浆膏、黑曜石、哭泣的黑曜石、末影珍珠、书 |
| 消耗品 | 经验瓶 |
| 模板 | 18 个盔甲纹饰模板 + 下界合金升级模板 |

**刻意不收的**：

- **金苹果 / 附魔金苹果** —— 它们有自己的转化机制，再吃数量加成就是双重收益
- **装备**（钻石镐、金胸甲……）、**马铠**、**鞍**、**藏宝图**、**唱片**、**药水**
  —— 这类功能唯一的物品「变多」很怪
- **食物和垃圾**（面包、小麦、腐肉、线、骨头）—— 不是宝物

**覆盖率实测**（扫原版 56 张宝箱表）：**48 张至少含一件被覆盖物品**。
最常开的地牢箱子 `simple_dungeon` 从「完全没反应」变成**每次开箱平均命中约 5 件**
（书 / 铁锭 / 金锭 / 红石 / 火药）。

**实际效果实测**（用假玩家构造和真人开箱一致的 LootParams，每张表 500 次）：

| 宝箱表 | 无幸运 | 胸甲 5 级（+160%） | 满配（+200%） |
| --- | --- | --- | --- |
| `simple_dungeon` | 5.0 件/箱 | **13.7 件/箱** | **14.8 件/箱** |
| `abandoned_mineshaft` | 3.5 | 8.8 | 9.9 |
| `stronghold_corridor` | 2.3 | 5.7 | 7.3 |
| `stronghold_library` | 5.7 | 14.8 | 16.8 |
| `buried_treasure` | 18.8 | 48.1 | 56.4 |

也就是说**满级胸甲能让一个地牢箱子多出约 8.6 件宝物**，越富的箱子收益越大。

> 表里的「满配」是胸甲 5 级 + 强化版药水（+200%）。
> 如果主手再拿一把 5 级运气附魔的工具，会到 **+360%**。

#### 为什么要加「每箱上限」

数量加成是**按箱子里已有的数量成比例**放的，所以本来就富的箱子会被放大得特别厉害——
沉船宝藏一次能多出 50 多件，而普通箱子只多几件，贫富差距反而被拉开。

加上每箱上限之后分布会平均很多（幸运 V，8 张代表性表各 200 次实测）：

| 宝箱表 | 无上限 | 上限 +12 |
| --- | --- | --- |
| `chests/shipwreck_treasure` | **+53.3** 件/箱 | **+11.9** |
| `chests/buried_treasure` | +28.6 | +10.7 |
| `chests/bastion_treasure` | +27.3 | +9.9 |
| `chests/ruined_portal` | +21.9 | +8.2 |
| `chests/stronghold_library` | +9.1 | +7.2 |
| `chests/simple_dungeon` | +8.6 | +6.7 |
| `chests/abandoned_mineshaft` | +6.1 | +4.7 |
| `chests/stronghold_corridor` | +3.0 | +4.1 |
| **最高 ÷ 中位** | **2.4×** | **1.4×** |

**实现方式是「超出上限时按比例缩小本次的加成倍率」**，而不是「加够了就停」——
后者会让列表靠前的几堆吃掉全部额度，后面的堆什么都拿不到，结果会依赖战利品表的书写顺序。

#### 加成是怎么落到每一堆上的（逐物品判定）

「+160%」不是「把整堆乘以 2.6」，而是**逐个物品判定**：

- 倍率的整数部分（1）：每个物品都固定多 1 件
- 小数部分（0.6）：每个物品各自按 60% 概率再多 1 件

**期望值和不拆时完全一样**（都是 `数量 × 倍率`），区别只在观感：

| 一堆 3 个、加成 80% | 结果 |
| --- | --- |
| 旧做法（先整堆取整，余数再掷） | 固定 2 件，再 40% 概率给第 3 件 → **只有 2 或 3** |
| 现在（逐物品判定） | 每件各自 80% → **1~3，期望同样是 2.4** |

再配合 `maxExtraPerStack = 3`，**任何一堆最多只多 3 件**——
改之前一堆 10 个金锭在 +160% 下会一次多出 16 件，这是「观感集中」的主要来源。

**每堆上限取多少，实测对比**（幸运 V，8 张代表性表各 200 次，看每箱多出的中位数）：

| 每堆上限 | 中位多出/箱 | 最高 ÷ 中位 |
| --- | --- | --- |
| +2 | 5.4 | 1.8× |
| **+3** | **8.2** | **1.3×** |

> ⚠️ **一个容易踩的坑**：等级表对宝箱收益的影响**很小**——每堆上限是硬顶，
> 把 `bonusByLevel` 从 160% 提到 240%，中位数只从 5.4 涨到 5.5（+2%）。
> 真正能调宝箱力度的是 `maxExtraPerStack`。
>
> 但**挖矿和击杀不受每堆上限限制**（矿石和掉落物本来就是 1~2 个一堆，封顶没意义），
> 所以改 `bonusByLevel` 会主要作用在那两处。

> 这是**期望值**上的封顶：先算不加限制时的期望额外量，超过上限才缩倍率，
> 之后仍走概率取整，所以单次开箱的实际值会上下浮动。

没覆盖的 8 张全是食物或杂物表，本来也不该出宝物：

| 宝箱表 | 里面是什么 |
| --- | --- |
| `chests/spawn_bonus_chest` | 木石工具、苹果、面包、鲑鱼 |
| `chests/village/village_cartographer` | 地图、纸、指南针、面包 |
| `chests/trial_chambers/supply` | 箭、烤马铃薯、发光浆果、木板 |
| `chests/jungle_temple_dispenser` | 只有箭 |
| `chests/ancient_city_ice_box` | 迷之炖菜、金胡萝卜、烤马铃薯、浮冰 |
| `chests/trial_chambers/entrance` | 试炼钥匙、木棍、蜂蜜块 |
| `chests/trial_chambers/reward`、`reward_ominous` | （空池，靠随机函数填） |

> 命中堆数最高的几张：`shipwreck_treasure` 8.17、`buried_treasure` 6.90、
> `stronghold_library` 4.46、`bastion_treasure` 4.29。
> 也就是说**越富的箱子，幸运的收益越明显**——这个梯度是刻意的。

> ⚠️ **标签只能「加」，不能「删」（重要）**
>
> 多个数据包对同一个标签的贡献是**并集**。所以数据包想**移除**一条，必须写
> `"replace": true` 并给出**完整的目标清单**——把原有条目也照抄进去。
> `replace: true` 会**清空**这个标签，写不全就会误删。
>
> `minecraft:non_treasure` 原版有几十个条目，直接替换会让一大批原版附魔
> 从附魔台和村民交易里消失，**这一步要格外小心**。
>
> **要移除的话用 KubeJS 省事得多**（这是原版数据包做不到的）：
>
> ```js
> ServerEvents.tags('enchantment', event => {
>     event.remove('minecraft:non_treasure', 'betterluck:luck')
> })
> ```
>
> `TagKubeEvent` 提供了 `remove` / `removeAll` / `removeAllTagsFrom`。
>
> **往标签里「加」东西没这个问题**，数据包直接 merge 就生效。
> 所以上面 `rare_drops`、`chest_bonus_items`、`luck_enchantable` 三个标签的常规用法
> 就是往里面加条目，不受影响。

---

## 4. KubeJS 脚本 API

装了 KubeJS 的话，脚本里会多出一个 `BetterLuck` 对象——**既能读，也能改数值**。

```js
// kubejs/server_scripts/luck.js

// ---- 读 ----
BetterLuck.luckLevel(player)              // 幸运等级
BetterLuck.bonus(player)                  // 当前总加成，1.25 = +125%
BetterLuck.bonusByLevel()                 // 当前等级表
BetterLuck.treasureChancePerLuck()        // 钓鱼宝藏概率 / 级
BetterLuck.inferiorEnchantments()         // 劣质附魔清单

// ---- 改概率（覆盖配置文件）----
BetterLuck.setTreasureChancePerLuck(0.2)
BetterLuck.setTreasureChanceMax(0.5)
BetterLuck.setGoldenAppleChancePerLuck(0.15)
BetterLuck.setGoldenAppleChanceMax(0.6)
BetterLuck.setRareDropRollsPerLuck(2)
BetterLuck.setRareDropMaxRolls(4)
BetterLuck.setBonusLevel(1, 0.2)          // 只改第 1 级
BetterLuck.setInferiorEnchantment("minecraft:bane_of_arthropods", 0.1)

// ---- 撤销全部覆盖，回落配置文件 ----
BetterLuck.reset()
```

**优先级：脚本 > 配置文件 > 内置默认。** 没有任何覆盖时，行为和不装 KubeJS 完全一致。

> **覆盖是全局的**，建议在脚本加载时设置一次即可，不要每次事件都调。
>
> **推荐用 `setBonusLevel(等级, 值)`**，而不是整表替换——从 JS 传数组过来要做类型转换，
> 单点设置最稳。（`setBonusByLevel(...)` 也提供了，容错逻辑更复杂。）
>
> **副作用提醒**：如果脚本改了数值，玩家在配置文件里看到的数就不代表实际生效值了。
> 排查问题时记得先确认这一点。

---

## 5. 常见调整场景

### 完全关掉「劣质附魔」

```toml
[luck.inferiorEnchantments]
    list = []
```

### 让绑定 / 消失诅咒再也不出现

```toml
list = ["minecraft:binding_curse=0.0", "minecraft:vanishing_curse=0.0"]
```

### 只想降低诅咒，不想动节肢杀手

把 `bane_of_arthropods` 那一行删掉即可。这张表**可增删任意附魔**，
比如想顺手压制「节肢杀手」以外的战斗附魔，加一行 `"minecraft:smite=0.5"` 就行。

### 觉得满配 +360% 太强

改等级表，比如压到满级 +50%：

```toml
bonusByLevel = ["0.05", "0.1", "0.2", "0.35", "0.5"]
```

### 只想让药水有用，不想让运气附魔参与

```js
ServerEvents.tags('enchantment', event => {
    event.remove('minecraft:non_treasure', 'betterluck:luck')
})
```

这一行就把运气附魔从**附魔台、村民交易、宝箱、怪物装备四处全部**移除。

### 只保留一部分获取途径

因为那四个标签**只是引用 `#non_treasure`**，所以「从某一条里单独拿掉」不能直接删
——它并不是 `in_enchanting_table` 的直接成员，而是通过 `non_treasure` 继承进去的。

正确做法是**先全删、再单独加回**：

```js
ServerEvents.tags('enchantment', event => {
    event.remove('minecraft:non_treasure', 'betterluck:luck')    // 四处全清
    event.add('minecraft:on_random_loot', 'betterluck:luck')     // 只加回宝箱
})
```

上面这个例子就是「**只能开宝箱获得**」。

> 用**纯数据包**做同一件事会很麻烦：你得覆盖 `in_enchanting_table` 等标签、
> 写 `"replace": true`，并把 `non_treasure` 的完整内容**展开**抄进去再去掉我们那条。
> 漏一条就会有原版附魔从附魔台神秘消失。**这种需求建议直接用 KubeJS。**

### 不想让运气附魔和经验修补互斥

这是本模组自己的标签（只有两条），覆盖很安全——但**必须写 `"replace": true`**，
否则并集语义会让 `minecraft:mending` 继续留着：

```json
// data/betterluck/tags/enchantment/exclusive_set/luck.json
{
  "replace": true,
  "values": ["betterluck:luck"]
}
```

### 让运气附魔也能附在剑 / 三叉戟 / 别的物品上

覆盖 `data/betterluck/tags/item/luck_enchantable.json`，加一行
`"#minecraft:enchantable/sword"` 即可（**加条目是 merge，不需要 `replace`**）：

```json
// data/betterluck/tags/item/luck_enchantable.json
{
  "values": [
    "#minecraft:enchantable/chest_armor",
    "#minecraft:enchantable/mining_loot",
    "#minecraft:enchantable/sword"
  ]
}
```

> ⚠️ 注意 `supported_items` **不能直接写标签列表**，必须通过这个标签间接组合。

### 彻底关掉某条收益

把对应系数设为 `0`。例如关掉钓鱼：

```toml
[luck.fishing]
    treasureChancePerLuck = 0.0
```

关掉宝箱金苹果转化同理，改 `goldenAppleChancePerLuck = 0.0`。

---

## 6. 兼容性与注意事项

### LootJS

**天然兼容，不需要额外配置。**

本模组的两个战利品修饰符（`betterluck:luck_fishing`、`betterluck:luck_chest`）是标准的
NeoForge **全局战利品修饰符**，而 LootJS 的 `LootJS.modifiers()` 事件**跑在这些修饰符之后**
（这是 LootJS 文档明确写的）。也就是说 LootJS 脚本里：

```js
LootJS.modifiers(event => {
    event.getGlobalModifiers()                     // 能看到 betterluck 的两个修饰符
    event.removeGlobalModifiers(/betterluck:.*/)   // 也能整个移除（正则）
})
```

> 字符串过滤器在 LootJS 里是**字面匹配**，正则要写成 `/.../` 形式。

> ⚠️ 上面这段是按 LootJS 官方文档写的，**我没有实际装 LootJS 跑过**。
> 如果你在包里用到了，建议实测一下两个修饰符的先后顺序是否符合预期。

### KubeJS

可选。不装的话模组照常运行，只是没有 `BetterLuck` 绑定。

### 与经验修补互斥

运气附魔默认和**经验修补**互斥。这是刻意的设计取舍——想要幸运就得放弃那一件的经验修补。
不想要这个限制的话见上面「常见调整场景」。

### 运气附魔的等级上限

附魔最高 5 级，但**附魔台只能刷出 I~II 级**。

附魔台给出的等级上限是 30，而这个附魔的 `min_cost` 是 `15 + 9×(级数-1)`，
即 15 / 24 / 33 / 42 / 51。所以 III 级起只能靠**铁砧合并两本书**——
和原版锋利 V 的机制一样。

### 启动自检

服务端启动时会在日志里打印自检信息，**改动数据包后建议看一眼**：

```
更好的幸运效果：自检 —— 稀有掉落 16 项；宝箱加成 41 项；幸运附魔 已载入（最高 5 级；槽位 [CHEST, HAND]；宝箱 是 / 附魔台 是 / 村民交易 是 / 怪物装备 是 / 与经验修补互斥 是）；自定义药水 2 档
更好的幸运效果：数值 —— 等级表 [0.2, 0.4, 0.8, 1.2, 1.6]（胸甲 / 主手工具 / 其余幸运 各自查表后相加）；钓鱼宝藏 10.0%/级（上限 40.0%），稀有重掷 1 次/级（上限 2 次）
更好的幸运效果：宝箱 —— 金苹果转化 10.0%/级（上限 40.0%）；钻石 / 锻造模板走等级表
更好的幸运效果：劣质附魔 —— 已生效 3 项 {minecraft:vanishing_curse=0.25, minecraft:binding_curse=0.25, minecraft:bane_of_arthropods=0.5}（与幸运无关；格式错的条目会被忽略）
```

这几行的用处：**数据包文件路径或标签名写错时 Minecraft 不会报错，只会静默失效**。
附魔 JSON 解析失败会显示「缺失」，标签写错会变成「否」，配置格式写错会被直接忽略。

### 语言

模组自带 **五种语言**，含配置界面：

| 语言 | 文件 |
| --- | --- |
| 简体中文 | `zh_cn.json` |
| 繁體中文（台灣） | `zh_tw.json` |
| 繁體中文（香港） | `zh_hk.json` |
| 文言（華夏） | `lzh.json` |
| English | `en_us.json` |

五种语言各 42 个键，键名完全一致。

**模组名和简介是中英双语**：

```
更好的幸运效果 (Better Luck Effect)
```

这不是偷懒——**NeoForge 21.1.209 不支持本地化模组名**（模组列表用的是
`Component.literal`，不解翻译键），所以只能写成一个字符串让所有语言都能看懂。

**配置文件里的注释只有中文。** 那是 TOML 文件本身的注释，写死在代码里，
不受游戏语言影响，也没法用语言文件翻译。这一块目前没有英文版。

### 许可证

**分两部分授权**：

| 部分 | 许可证 | 范围 | 文件 |
| --- | --- | --- | --- |
| 代码 | **LGPL-3.0** | `src/main/java/` | `LICENSE` |
| 素材 | **CC BY 4.0** | `src/main/resources/assets/`（贴图 / 模型 / 语言文件） | `LICENSE-ASSETS` |
| GPL-3.0 全文 | — | LGPL-3.0 引用它，完整副本需要一起带上 | `LICENSE-GPL-3.0.txt` |

> LGPL-3.0 是 **GPL-3.0 的补充条款**，不是独立协议——所以仓库里必须同时有两份文本。
> 少了 `LICENSE-GPL-3.0.txt` 的话，LGPL 的副本是不完整的。

**你可以做的事**：

- ✅ 把模组放进整合包（含商业整合包）
- ✅ 修改代码、再发布你的版本——但**你的版本也必须以 LGPL-3.0 开源**
- ✅ 搬运或改编素材——但**必须署名**（CC BY 4.0）

**你不能做的事**：

- ❌ 把修改后的代码闭源发布（LGPL-3.0 要求）
- ❌ 单独售卖模组本体（受 [Minecraft 使用准则](https://www.minecraft.net/en-us/usage-guidelines) 约束，与开源协议无关）
- ❌ 拿走素材却不署名

署名信息（三处，已填好，要改就一起改）：

| 位置 | 当前值 |
| --- | --- |
| `gradle.properties` 的 `mod_authors` | `OrangeEgg123123` |
| `gradle.properties` 的 `mod_license` | `LGPL-3.0-only` |
| `LICENSE` / `LICENSE-ASSETS` 的版权行 | `Copyright (c) 2026 OrangeEgg123123` |

> `mod_authors` 和 `mod_license` 会经 Gradle 注入 `neoforge.mods.toml`，
> 显示在模组列表里。注意它们**必须是 ASCII**——`gradle.properties` 按 ISO-8859-1 读取，
> 中文会变乱码。
>
> **`1.0.0` ~ `1.05` 是 MIT 授权的**（那些版本发布时用的是 MIT）。
> 协议变更只对 `1.06` 及之后的版本生效，已分发的旧版本授权不可撤回。
>
> **还有一处不是占位符但建议改**：`mod_group_id=com.example.betterluck`
> 以及 Java 包名 `com.example.betterluck`。`com.example` 是不能用于发布的保留域名，
> 正规做法是换成你自己的域名倒写（例如 `io.github.OrangeEgg123123.betterluck`）。
> 改包名要同步改目录结构和 `kubejs.plugins.txt`（里面写的是完整类名），
> 漏改的话 KubeJS 绑定会**静默失效**。详见 `DEVELOPMENT.md` 的改名指引。

### 已知限制

- **劣质附魔只作用于宝箱战利品**，不影响附魔台、村民交易、怪物装备。
  （附魔台那边还有 UI 一致性问题：改生成结果会导致「界面显示 A、实际给 B」。）
- **附魔书不吃幸运的数量加成**。宝箱里的附魔书目前完全不受幸运影响。
- 概率类加成**不走等级表**，各自封顶 40%。
