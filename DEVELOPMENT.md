# 开发笔记（工程搭建与实现细节）

> 面向**整合包作者**的文档在 [README.md](README.md)。
> 本文记录本机环境、构建方式、以及实现过程中踩过的坑，给后续改代码的人看。

---

## 更好的幸运效果 (Better Luck Effect) — Minecraft 1.21.1 / NeoForge

一个 NeoForge 1.21.1 模组，使用 **ModDevGradle** 构建。构建与运行时加载均已验证通过。

| 项 | 值 |
| --- | --- |
| 显示名 | 更好的幸运效果 |
| 英文名 | Better Luck Effect |
| mod id | `betterluck` |
| 包名 | `io.github.orangeegg123123.betterluck`（1.07 起，原来是 `com.example.betterluck`） |
| 产物 | `build/libs/betterluck-<版本>.jar`（版本见 `gradle.properties` 的 `mod_version`） |
| 已归档版本 | `releases/` 下按版本存放 jar 与源码包 |
| 版本库 | 本目录是一个本地 git 仓库，重构前的原始状态是第一个提交（`git log` 看得到） |

---

## 1. 快速开始

```powershell
.\build.bat          # 编译并打包 -> build\libs\betterluck-<版本>.jar
.\run-client.bat     # 启动带模组的 Minecraft 客户端
.\run-server.bat     # 启动服务端（验证酿造/世界逻辑用这个）
.\run-data.bat       # 运行数据生成器
.\clean.bat          # 清理构建产物
```

这些脚本会自动设置 `JAVA_HOME` 和 `GRADLE_USER_HOME`，无需手动配置环境变量。

### 已验证

> 下表的验证记录是在**版本 1.0.0**（`mod_version=1.0.0`）上做的，保留原始记录。

| 验证项 | 结果 |
| --- | --- |
| `.\build.bat` | ✅ BUILD SUCCESSFUL，产出 `build\libs\betterluck-<版本>.jar` |
| Minecraft 反编译（`createMinecraftArtifacts`） | ✅ 5364 个源文件，缓存后第二次仅需秒级 |
| `.\run-data.bat` 数据生成器 | ✅ 启动成功，模组列表含 `更好的幸运效果 1.0.0 (betterluck)` |
| `.\run-server.bat` 服务端 | ✅ `Done (13.492s)!`，附魔 / 药水 / 酿造 / 收益全部注册成功（见下） |

服务端启动日志（**1.07 的实测输出**，在 Linux 上用等价的 `./gradlew runServer` 跑的）：

```
[01:34:03] [Server thread/INFO] [minecraft/DedicatedServer]: Done (6.473s)! For help, type "help"
[01:34:03] [Server thread/INFO] [io.gi.or.be.BetterLuckEffect/]: 更好的幸运效果：自检 —— 稀有掉落 16 项；宝箱加成 41 项；幸运附魔 已载入（最高 5 级；槽位 [CHEST, HAND]；宝箱 是 / 附魔台 是 / 村民交易 是 / 怪物装备 是 / 与经验修补互斥 是）；自定义药水 2 档
[01:34:03] [Server thread/INFO] [io.gi.or.be.BetterLuckEffect/]: 更好的幸运效果：数值 —— 数量类等级表 [0.2, 0.4, 0.8, 1.2, 1.6]（挖矿 / 击杀普通掉落；胸甲 / 主手工具 / 其余幸运 各自查表后相加）；钓鱼宝藏 10.0%/级（上限 40.0%），稀有重掷 1 次/级（上限 2 次）
[01:34:03] [Server thread/INFO] [io.gi.or.be.BetterLuckEffect/]: 更好的幸运效果：宝箱 —— 金苹果转化 10.0%/级（上限 40.0%），排除 1 张必出表；加成物品（41 项）改为「额外重掷宝箱表、只补箱子里还没有的」，1 次/级（上限 3 次，0 = 关闭）
[01:34:03] [Server thread/INFO] [io.gi.or.be.BetterLuckEffect/]: 更好的幸运效果：劣质附魔 —— 已生效 3 项 {minecraft:vanishing_curse=0.25, minecraft:binding_curse=0.25}（……）
```

> 模组列表里显示的是 `更好的幸运效果 (Better Luck Effect) 1.07 (betterluck)`。
> 日志里的类名被日志框架缩写成了 `io.gi.or.be.BetterLuckEffect`，是正常的。

这几行自检是刻意加的：数据包文件路径写错时 Minecraft **不会报错**，只会静默失效。
附魔 JSON 解析失败会显示「缺失」，四个获取途径标签、互斥标签、宝箱加成标签
都是写错就变「否」或 `-1`，劣质附魔配置写错会被直接忽略。
**槽位**那一项尤其有用——`supported_items` 写错会让整个注册表加载失败（见 2.2 的警告）。

> ⚠️ **改配置默认值后必须删掉旧的 `config/betterluck-common.toml`。**
> NeoForge 会沿用文件里已有的值，新默认值只在文件不存在时才写入。
> 本次调平衡时就踩到了：代码改成 0.25，日志里却仍然打印 +50%。
>
> **1.07 升级到旧版本存档时更要删**：`maxExtraPerStack` / `maxExtraPerChest`
> 两个键已经不存在了，新键（`extraRollsPerLuck` / `maxExtraRolls`）会以默认值补进去，
> 旧键留在文件里只会让人以为它还在生效。

---

## 2. 已实现的功能

### 2.1 幸运药水可以酿造了

原版的 `minecraft:luck` 药水是「定义了但拿不到」——它不在任何酿造配方里，
也不出现在战利品表中，生存模式下完全没有获取途径。

现在补上酿造配方：

| 输入 | 材料 | 产出 |
| --- | --- | --- |
| 粗制的药水（Awkward Potion） | 绿宝石 | 幸运药水（幸运 I，5 分钟） |

实现在 `BetterLuckEffect#onRegisterBrewingRecipes`：

```java
@SubscribeEvent
public void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
    event.getBuilder().addMix(Potions.AWKWARD, Items.EMERALD, Potions.LUCK);
}
```

几个要点：

- 用 `addMix` 而**不是** `addStartMix`。后者会顺带加上「水瓶 + 绿宝石 → 浑浊的药水」，
  那是原版给糖、岩浆膏这类材料的写法。
- `RegisterBrewingRecipesEvent` 在**游戏事件总线**上触发
  （`PotionBrewing.bootstrap()` 里是 `NeoForge.EVENT_BUS.post(...)`），
  构造函数中注册的 `this` 能收到。
- 药水的显示名不需要额外加语言文件：`Potions.LUCK` 走的是原版键
  `item.minecraft.potion.effect.luck`，已有官方中文「幸运药水」。

> **验证方式**：`PotionBrewing.bootstrap()` 只在 `MinecraftServer` 构造和客户端连服时调用，
> **数据生成（`runData`）不会触发这个事件**。所以验证这类逻辑必须用
> `.\run-server.bat`，而不是 `.\run-data.bat`。

### 2.2 幸运来源

幸运效果靠给 `minecraft:generic.luck` **属性**加修正来生效（每级 +1）。
因为收益侧全部读 `player.getLuck()`，所以**任何一个新来源都会自动喂给全部收益**，不用改收益代码。

#### 幸运附魔（纯数据包）

`src/main/resources/data/betterluck/enchantment/luck.json`

```json
"effects": {
  "minecraft:attributes": [
    {
      "id": "betterluck:enchantment_luck",
      "attribute": "minecraft:generic.luck",
      "amount": { "type": "minecraft:linear", "base": 1.0, "per_level_above_first": 1.0 },
      "operation": "add_value"
    }
  ]
}
```

- 最高 **5 级**，每级 +1 幸运。
- 只能附在 **胸甲** 和 **挖掘工具（镐/锹/斧/锄）** 上：
  `slots: ["chest", "hand"]`，`supported_items` 指向 `#betterluck:luck_enchantable`。
- 能生效的前提是 `ServerPlayer` 每 tick 会调 `EnchantmentHelper.runLocationChangedEffects`
  ——这条位置型效果链确实会跑，所以装备上的属性修正会被加上。
- **附魔台只能刷出 I~II 级**：附魔台给出的等级上限是 30，而本附魔的
  `min_cost` 是 `15 + 9×(级数-1)`，即 15 / 24 / 33 / 42 / 51。
  所以 III 级起就只能靠**铁砧合并两本书**——和原版锋利 V 的机制一样。

> ⚠️ **`supported_items` 不能写成标签列表**。它的编解码器是
> `either(单个标签, 条目列表)`——列表里只能放**具体物品**，放 `#标签` 会让整个
> 附魔注册表加载失败（`Registry loading errors`，服务器直接起不来，
> 而且报错只说「Failed to parse」，不告诉你是哪个字段错）。
> 所以想在胸甲和工具上都生效，必须**自己建一个物品标签**去组合：
> `data/betterluck/tags/item/luck_enchantable.json`。
- 获取途径：**四种途径全开**。做法是往 `minecraft:non_treasure` 里加一条：

  | 标签 | 谁在消费它 | 本附魔 |
  | --- | --- | --- |
  | `minecraft:non_treasure` | 被下面四项引用 | ✅ **在** |
  | `minecraft:in_enchanting_table` | `EnchantmentMenu`（附魔台） | ✅ 引用 `#non_treasure` |
  | `minecraft:tradeable` | `VillagerTrades`（村民交易） | ✅ 引用 `#non_treasure` |
  | `minecraft:on_random_loot` | `EnchantRandomlyFunction` / `EnchantWithLevelsFunction`（宝箱） | ✅ 引用 `#non_treasure` |
  | `minecraft:on_mob_spawn_equipment` | 怪物生成时自带的装备 | ✅ 引用 `#non_treasure` |

  > 只写一个文件就够：上面四个标签**全都引用 `#minecraft:non_treasure`**，加一处就四处生效。
  >
  > 中途一度按需求改成过「只能开宝箱」（从 `non_treasure` 移出、单独加 `on_random_loot`），
  > 后来又改回全开。
  >
  > 另外查证：`minecraft:treasure` 这个标签在 1.21.1 里**没有任何代码消费**
  > （搜遍源码只在定义处出现），纯声明性，所以没有用它。

> **数值口径（重要）**：数量类加成走的是**等级表**，不是系数。
>
> | 等级 | 加成 |
> | --- | --- |
> | 1 | +20% |
> | 2 | +40% |
> | 3 | +80% |
> | 4 | +120% |
> | 5 及以上 | +160% |
>
> 三个来源**各自查表后相加**（实现在 `LuckScale`）：
>
> | 来源 | 最多贡献 |
> | --- | --- |
> | 胸甲上的幸运附魔等级 | +100%（5 级） |
> | 主手工具上的幸运附魔等级 | +100%（5 级） |
> | 其余幸运（药水等：用 `getLuck()` 减去上面两项装备贡献） | +25%（强化版药水 2 级） |
> | **满配合计** | **+360%** |
>
> 表本身放在配置里（`bonusByLevel`）。写成字符串列表而不是数字列表，
> 是为了绕开 `defineList` 数字元素校验的版本差异——本工程里字符串形式已验证可行。

> **与经验修补互斥**：`exclusive_set` 指向 `#betterluck:exclusive_set/luck`
> （内容为 `minecraft:mending` 与本附魔）。`Enchantment.areCompatible` 是**双向**判定
> ——只要任一方声明了对方就算互斥，所以只在我们这边声明就够了，
> **不用去覆盖原版的 `mending.json`**（覆盖会和其他模组打架）。
>
> 代价是实打实的：胸甲上想要幸运，就得放弃那一件的经验修补。
> 如果实测下来这附魔没人愿意用，可以把互斥范围缩小，或者调低 `weight`。

#### 幸运药水：延长版与强化版

在原有「粗制的药水 + 绿宝石 → 幸运药水」之上再加两条：

| 操作 | 产出 | 效果 |
| --- | --- | --- |
| 幸运药水 + 红石 | 延长版幸运药水 | 幸运 I，12000 tick（10 分钟） |
| 幸运药水 + 萤石粉 | 强化版幸运药水 | 幸运 II，3600 tick（3 分钟） |

> **药水命名的坑**：`Potion` 的 `name` 字段会被拼到**硬编码前缀**
> `item.minecraft.potion.effect.` 后面，不带模组命名空间。
> 所以自定义药水要在语言文件里写 `item.minecraft.potion.effect.long_luck`
> 这类键；喷溅型 / 滞留型 / 药箭各有一套自己的前缀，得分别写。

### 2.3 幸运效果的实际收益

先说一个查源码才发现的背景：**原版的幸运其实已经在起作用**，但只在两处——

- `LootPool` 用 `quality × luck` 调整条目权重；
- `LootPool` 用 `floor(bonusRolls × luck)` 追加额外抽取次数。

问题在于原版的怪物战利品表几乎不用 `quality`，方块掉落更是**完全不看幸运**
（`Block.getDrops` 压根没把 luck 放进 LootContext），所以喝下去基本没感觉。

于是补上五块：

| 场景 | 触发条件 | 效果 | 默认值 |
| --- | --- | --- | --- |
| 钓鱼 | 玩家有幸运 | 按概率把整次渔获换成一次「宝藏」表抽取 | 10% / 级，上限 40% |
| 击杀 · 稀有掉落**概率** | 玩家有幸运 | 额外重掷实体战利品表，只保留稀有掉落 | 1 次 / 级，上限 2 次 |
| 挖矿 · 矿物**数量** | 幸运 + **时运**镐 + `c:ores` 方块 | 每个掉落堆叠额外增加数量 | 走等级表 |
| 击杀 · 掉落**数量** | 幸运 + **抢夺**武器 | **只加普通掉落** | 走等级表 |
| 宝箱 · 宝物**出现率** | 玩家有幸运 | 额外重掷宝箱战利品表，只补箱子里还没有的宝物 | 1 次 / 级，上限 3 次 |

数值全部可以在 `config/betterluck-common.toml` 里改。

#### 「数值」到底是多少

**数量类加成走等级表**，三个来源各自查表后相加：

| 等级 | 加成 |
| --- | --- |
| 1 | +20% |
| 2 | +40% |
| 3 | +80% |
| 4 | +120% |
| 5 及以上 | +160% |

| 来源 | 最多贡献 |
| --- | --- |
| 胸甲上的幸运附魔等级 | +100%（5 级） |
| 主手工具上的幸运附魔等级 | +100%（5 级） |
| 其余幸运（药水等） | +25%（强化版药水 2 级） |
| **满配合计** | **+360%** |

计算是**加法**，不是连乘：

```
额外数量 = 原数量 × ( 表(胸甲等级) + 表(主手等级) + 表(其余幸运) )
最终数量 = 原数量 + 额外数量
```

举例（只戴 5 级幸运胸甲、无工具无药水，原数量 1 个）：

| 配置 | 加成 | 结果 |
| --- | --- | --- |
| 只有一瓶普通药水（其余幸运 = 1） | +25% | 1 个 → 20% 概率变 2 个 |
| 胸甲 2 级 | +25% | 1 个 → 20% 概率变 2 个 |
| 胸甲 5 级 | +100% | 1 个 → 固定 2 个 |
| 胸甲 5 级 + 镐 5 级 + 强化药水 | +360% | 1 个 → 固定 4 个，且 60% 概率变 5 个 |

小数部分按概率补整：+20% 作用在 1 个物品上，就是 20% 概率 +1，平均 +0.2 个。

**概率类加成**（钓鱼宝藏、金苹果转化）不走这张表，仍然是
「系数 × 总幸运，各自封顶 40%」——因为 100% 的「必定出宝藏」显然不合理。

#### 为什么「数量」和「概率」要分家

早先稀有掉落**同时**吃两条线——额外重掷提高掉落**率**，抢夺线又提高掉落**量**，
同一个物品被乘两次，确实超模。现在：

- **稀有掉落**（`betterluck:rare_drops` 标签）→ 只走**概率**线，数量不再加成
- **普通掉落** → 只走**数量**线
- **宝箱宝物**（`betterluck:chest_bonus_items` 标签）→ 只走**概率**线（重掷），
  不再给已有的堆加数量

各自只吃一条，不再叠加。

> 顺带一提，挖矿和击杀仍然走等级表，是因为那两处的收益天生就是「数量」：
> 矿石掉落 1~2 个一堆，掉落物也一样，提高「出现几率」既没有对象也没有观感。
> 宝箱不一样——表里本来就是按概率抽条的，重掷才有意义。

**实现要点**

- 挖矿走 `BlockDropsEvent`、战斗走 `LivingDropsEvent`，都以玩家为单位取
  `player.getLuck()`。注意幸运效果是**通过 `Attributes.LUCK` 属性**生效的（每级 +1），
  `Player.getLuck()` 读的正是这个属性——所以用它才对，别去直接读效果等级。
- 「稀有掉落概率」的做法是**额外重掷实体战利品表，但只留下稀有物**。
  再配一条「已经掉过的不再重复」的规则，就避免把必掉物翻倍——比如凋灵的下界之星
  是必掉的，重掷一次就会变 2 个，这条规则正好挡住。
- 小数加成按「整数部分必得、小数部分按概率补 1」结算。否则 count=1、系数 0.5 时
  取整会把加成整个抹掉。宝箱的重掷次数用的是同一套口径（`LuckMath#rollCount`）。
- 钓鱼**必须**用全局战利品修饰符（Global Loot Modifier）：`ItemFishedEvent` 构造时
  把掉落列表 `addAll` 复制了一份，改 `getDrops()` 根本不影响真正掉落的东西。

#### 宝箱

只对 `data/minecraft/loot_table/chests/` 下的 **56 张**宝箱表生效（`LuckChestModifier`）。

**1.07 起：幸运提高的是「物品出现的几率」，不是单次出现的数量。**
旧做法是「箱子里已有多少，就按幸运成比例再多出几成」（数量线，走等级表）。
现在改成**额外重掷宝箱战利品表**，只把 `chest_bonus_items` 里的物品并入箱中，
而且箱子里**已经有的种类一律不再补**。

| 效果 | 默认值 |
| --- | --- |
| 额外重掷宝箱表，只补箱子里**还没有的**宝物 | 1 次 / 级，上限 3 次 |
| 金苹果**整堆**变成附魔金苹果 | 10% / 级，上限 40% |
| 「劣质附魔」出现概率降低 | 按配置，**与幸运无关** |

实现要点（`LuckChestModifier#addExtraAppearances`）：

- 次数 = `min(extraRollsPerLuck × 总幸运, maxExtraRolls)`，走的是「概率类」那套公式
  （不再走 `bonusByLevel` 等级表）。小数部分按概率补 1 次，见 `LuckMath#rollCount`。
- 重掷调用的是 `LootTable#getRandomItems(LootContext, Consumer)`，**复用同一个 LootContext**：
  参数、幸运、随机源全都一致，所以抽出来的概率分布和自然开箱完全相同。
  不要自己重建 `LootParams`——宝箱表的 `paramSet` 必须完全匹配，缺参数会直接抛异常。
- **必须防重入**：`LootTable#getRandomItems` 内部会调 `CommonHooks.modifyLoot`，
  也就是会再触发一遍全局修饰符；不拦就会无限递归把服务端爆栈。
  用的是 `ThreadLocal<Boolean> REROLLING`（重掷那一发进来直接返回）。
  NeoForge 的 `LootContext` 也自带 visited 守卫，但那是在 `getRandomItemsRaw` 内部
  push/pop 的，`modifyLoot` 在 pop **之后**才调用，所以拦不住这一层。
- 「已经有的不再补」是替代旧版「每堆上限 / 每箱上限」的关键规则：必出物品不会被翻倍，
  富箱子能补的种类反而少，所以不再需要那两个硬顶。

物品清单在 `data/betterluck/tags/item/chest_bonus_items.json`，当前 **41 项**：
23 个显式物品（钻石、各种金属、红石、火药、末影珍珠、经验瓶……）
+ `#minecraft:trim_templates`（18 个盔甲纹饰模板）+ 下界合金升级模板。可直接增删。

> **为什么下界合金升级模板要单独写一条**：`#minecraft:trim_templates` 只含 18 个
> **盔甲纹饰**模板，**不含**下界合金升级模板，所以必须单独补上。
>
> **附魔书不受幸运影响**：它不在 `chest_bonus_items` 里，重掷补不进来；
> 早年吃过一次数量加成（+5%/级），已按要求移除。
> 关于「优质附魔」的替代方案**暂时搁置**，改做下面的「劣质附魔」。

> ✅ **验证情况（1.07）**：在 Linux 上跑了等价的完整构建，
> `BUILD SUCCESSFUL in 5m 32s`（NeoForm 反编译 + 打补丁 + ECJ 重编译），
> 又用 `./gradlew runServer` 起过一次服务端：`Done (6.473s)!`，自检日志见第 1 节。
> 所以「新包名能加载、新配置键能读、标签能解析、API 用法能编译」都是验过的。
>
> ⚠️ **但重掷这条代码路径本身还没被执行过**：全局战利品修饰符只在真的开箱时才触发，
> 启动服务端不会走到它 —— 包括 `REROLLING` 那道防重入，也还没在真实开箱里跑过。
> 发版前建议进游戏开几箱，确认：
>   1. 箱子里会多出**本来没有的**加成物品，且**已有的那种不会变多**；
>   2. 开箱不会卡死 / 崩服（递归保护生效）；
>   3. 顺手统计「一次开箱平均多出几件」，把数据补进 README。

#### 劣质附魔

配置项 `luck.inferiorEnchantments`，每行写成 `<附魔id>=<保留系数>`：

```toml
list = ["minecraft:binding_curse=0.25", "minecraft:vanishing_curse=0.25", "minecraft:bane_of_arthropods=0.5"]
```

| 系数 | 含义 |
| --- | --- |
| `1.0` | 不干预 |
| `0.25` | 出现概率降到约 1/4 |
| `0.0` | 完全不会出现 |

可增删**任意**附魔，格式写错的条目会被忽略（启动日志会打印实际生效的清单）。

> **为什么只能「抽取之后重掷」**：宝箱附魔书走的是 `EnchantRandomlyFunction`，
> 它第 64 行是 `Util.getRandomSafe(list, random)`——**等概率均匀抽取**，
> 附魔 JSON 里的 `weight` 字段在这里**完全不起作用**。
> 所以「降低概率」只能事后把命中的附魔换成同一抽取池里的另一个非劣质附魔，
> 等价于把它的出现概率按系数压低。
>
> 顺带一提，这也正好是唯一能实现「配置文件可改」的做法——
> 数据包 JSON 读不到配置，后处理可以。
>
> **这一条与幸运等级无关**，是无条件生效的，且目前**只作用于宝箱战利品**。

> **为什么不在 JSON 里限定宝箱表**：宝箱表有 56 张，逐张写 `neoforge:loot_table_id`
> 不现实。所以这个修饰符在 JSON 里**没有条件**，会在所有战利品表上被调用，
> 靠代码里的 `chests/` 前缀判断提前返回——开销只是一次字符串前缀比较。
>
> **原版其实已经把幸运塞进宝箱了**：`RandomizableContainer` 里有
> `withLuck(player.getLuck())`，只是原版战利品表不用 `quality` 字段，所以体现不出来。

### 2.4 「稀有掉落物」怎么判定

用物品标签 `betterluck:rare_drops`，文件在
`src/main/resources/data/betterluck/tags/item/rare_drops.json`，
默认收录凋灵骷髅头、三叉戟、鹦鹉螺壳、下界之星、不死图腾等 16 项，可直接增删。

服务端启动时会打印它的条数——路径写错就会显示 `-1`：

```
更好的幸运效果：自检 —— 稀有掉落 16 项；宝箱加成 41 项；幸运附魔 已载入（最高 5 级；宝箱 是 / 附魔台 是 / 村民交易 是 / 怪物装备 是 / 与经验修补互斥 是）；自定义药水 2 档
```

### 2.5 全局战利品修饰符的文件位置

钓鱼修饰符需要**两个** JSON，缺一不可：

| 文件 | 作用 |
| --- | --- |
| `data/betterluck/loot_modifiers/luck_fishing.json` | 定义修饰符实例与生效条件（`neoforge:loot_table_id` = `minecraft:gameplay/fishing`） |
| `data/neoforge/loot_modifiers/global_loot_modifiers.json` | 把上面的实例挂进全局列表 |

只注册序列化器（`GLOBAL_LOOT_MODIFIERS`）而漏掉这两个 JSON，修饰符不会生效，
而且**不会有任何报错**。

### 2.6 KubeJS 集成

用 `compileOnly` 依赖 KubeJS（`dev.latvian.mods:kubejs-neoforge:2101.7.2-build.379`）。
**不装 KubeJS 时模组照常运行**，只是脚本绑定不生效。

脚本里可以用一个 `BetterLuck` 对象—— **既能读，也能改数值**：

```js
// 读
BetterLuck.luckLevel(player)                  // 幸运等级
BetterLuck.bonus(player)                      // 当前总加成，1.25 = +125%
BetterLuck.bonusByLevel()                     // 当前等级表
BetterLuck.treasureChancePerLuck()            // 钓鱼宝藏概率 / 级
BetterLuck.chestExtraRollsPerLuck()           // 宝箱重掷次数 / 级
BetterLuck.chestMaxExtraRolls()               // 每箱重掷次数上限
BetterLuck.inferiorEnchantments()             // 劣质附魔清单

// 改概率（覆盖配置文件）
BetterLuck.setTreasureChancePerLuck(0.2)
BetterLuck.setTreasureChanceMax(0.5)
BetterLuck.setGoldenAppleChancePerLuck(0.15)
BetterLuck.setGoldenAppleChanceMax(0.6)
BetterLuck.setChestExtraRollsPerLuck(2)
BetterLuck.setChestMaxExtraRolls(6)
BetterLuck.setRareDropRollsPerLuck(2)
BetterLuck.setRareDropMaxRolls(4)
BetterLuck.setBonusLevel(1, 0.2)              // 只改第 1 级
BetterLuck.setInferiorEnchantment("minecraft:bane_of_arthropods", 0.1)

// 撤销全部覆盖，回落配置文件
BetterLuck.reset()
```

**优先级：脚本 > 配置文件 > 内置默认。** 没有任何覆盖时，行为和不装 KubeJS 完全一致
（覆盖层实现在 `LuckTuning`，所有读取点都走它）。

> **插件是怎么被发现的**：KubeJS **不用注解**，而是读各模组 jar 根目录下的
> `kubejs.plugins.txt`，每行一个**实现 `KubeJSPlugin` 接口**的类名，
> 行尾可以再写一个「依赖的模组 id」（没装就跳过）。本工程的文件在
> `src/main/resources/kubejs.plugins.txt`。
>
> **为什么依赖要写 `transitive = false`**：KubeJS 的 POM 依赖 architectury 等仓库，
> 而 `maven.architectury.dev` 在这个网络下连不上（ECONNRESET）。
> 我们只用到 kubejs jar 里的 3 个类型（`KubeJSPlugin` / `BindingRegistry` / `ClassFilter`），
> 关掉传递依赖既够用，又绕开了那些仓库。
>
> **为什么推荐 `setBonusLevel(等级, 值)` 而不是整表替换**：从 JS 传数组过来要做类型转换
> （Rhino 的数组不一定实现 `Iterable`），单点设置最稳；整表替换的
> `setBonusByLevel(Object)` 也提供了，但容错逻辑更复杂。

> ⚠️ **这一块没做运行时验证**：开发环境里没装 KubeJS，所以只验证到
> 「jar 里正确带上 `kubejs.plugins.txt`、类能编译、**不装 KubeJS 时服务端正常启动**」。
> 绑定的实际运行需要在实例里装上 KubeJS 才能确认。

### 2.7 占位内容

`example_block` / `example_item` 目前只是占位，用来保证注册链路通畅，
后续换成实际内容。

---

## 3. 中文与编码（重要，改名前务必看）

模组名是中文，而 Gradle 有**两个**会破坏中文的默认行为，工程里都已处理：

### 3.1 `gradle.properties` 只能放 ASCII

Gradle 用 **ISO-8859-1** 读取 `gradle.properties`（`java.util.Properties` 的规范行为）。
把中文写进去，再经 `${mod_name}` 注入 TOML，会得到双重编码的乱码：

```
displayName = "æ´å¥½çå¹¸è¿ææå"     # 实际是 更好的幸运效果
```

所以 **`displayName` 与 `description` 直接字面写在**
`src/main/resources/META-INF/neoforge.mods.toml` **里**，
`gradle.properties` 只保留 `mod_id` / `mod_version` 这类 ASCII 字段
（`mod_name` 保留为英文名备用）。

### 3.2 `filteringCharset` 必须显式设为 UTF-8

Gradle 的 `filter` / `expand` 默认用**平台编码**（中文 Windows 上是 GBK），
而源文件是 UTF-8。不设置的话，连**字面写在文件里的中文**都会变乱码：

```groovy
tasks.withType(ProcessResources).configureEach {
    filteringCharset = 'UTF-8'
    filesMatching(['META-INF/neoforge.mods.toml', 'pack.mcmeta']) {
        expand replaceProperties
    }
}
```

> 验证方式：构建后直接读 jar 里的字节。
> `E6 9B B4` = 更、`E5 A5 BD` = 好…… 只要不是 `C3 A6 C2 9B` 这种就对了。

### 3.3 别被 PowerShell 的显示乱码骗了

这台机器上 `native.encoding = GBK`，`Get-Content` 不带 `-Encoding UTF8`
时中文会显示成 `閫氱敤璁剧疆` 这类乱码，**但文件本身是好的**。
确认内容请用 `-Encoding UTF8`，或直接读字节：

```powershell
[System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($p))
```

---

## 4. 本机环境（已装好）

这台机器原先**没有安装任何 JDK**，且 `E:` 盘上的工程目录 ACL 缺少写权限。
目前状态：

| 组件 | 版本 | 位置 |
| --- | --- | --- |
| JDK | Temurin 21.0.12.1+1 (LTS) | `E:\DFY\.tools\jdk-21` |
| Gradle | 8.12 | `E:\DFY\gradle\wrapper`（wrapper 首次运行会自行下载） |
| Gradle 用户目录 | — | `E:\DFY\.gradle-home` |

> Gradle 用户目录之所以放在工程内，是因为当前开发环境的文件沙箱**只允许写入
> `E:\DFY`**，写到 `C:\Users\...\.gradle` 会被拒绝。`.tools/` 与 `.gradle-home/`
> 都已在 `.gitignore` 中排除。

---

## 5. 目录结构

```
E:\DFY
├── build.bat / run-*.bat / clean.bat    # 便捷脚本
├── gradlew / gradlew.bat / gradle/      # Gradle wrapper
├── build.gradle                         # 构建脚本（ModDevGradle）
├── settings.gradle                      # 插件仓库 + 国内镜像改写
├── gradle.properties                    # 构建参数（只能放 ASCII）
├── src/main/java/io/github/orangeegg123123/betterluck/
│   ├── BetterLuckEffect.java            # 模组主类：注册内容、绑定事件、酿造配方
│   ├── Config.java                      # 配置文件定义
│   └── LuckChestModifier.java           # 宝箱：重掷宝箱表提高宝物出现率
└── src/main/resources/
    ├── META-INF/neoforge.mods.toml      # 模组描述（中文名写在这里）
    ├── pack.mcmeta
    ├── assets/betterluck/               # 贴图、模型、方块状态、语言文件
    └── data/betterluck/                 # 战利品表等数据
```

---

## 6. 改名指引

### 6.1 改显示名 / 简介

直接编辑 `src/main/resources/META-INF/neoforge.mods.toml`：

```toml
[[mods]]
modId = "${mod_id}"
version = "${mod_version}"
displayName = "更好的幸运效果"        # ← 这里可以写中文
authors = "${mod_authors}"
description = '''让幸运效果真正有用起来。'''
```

创造模式标签页的名字在
`src/main/resources/assets/betterluck/lang/zh_cn.json` 的 `itemGroup.betterluck`。

### 6.2 改 mod id / 包名（改动面较大）

`mod_id` 无法由变量覆盖到文件路径上，需要同步修改：

- `gradle.properties` 的 `mod_id`、`mod_group_id`
- `settings.gradle` 的 `rootProject.name`
- `src/main/java/io/github/orangeegg123123/betterluck/` → 你的包路径，以及各 `.java` 的 `package` 与 `import`
- `BetterLuckEffect.java` 里的 `MODID` 常量与 `@Mod(...)` 注解
- `src/main/resources/assets/betterluck/` → `assets/<新modid>/`
- `src/main/resources/data/betterluck/` → `data/<新modid>/`
- 各 JSON 里的 `betterluck:` 命名空间、`.lang` 里的键前缀
- **`src/main/resources/kubejs.plugins.txt`** —— 里面写的是**完整类名**，
  改了包路径就要同步改，否则 KubeJS 找不到插件（而且**不会有任何报错**，
  只是绑定静默失效）

---

## 7. 国内网络适配（本工程的核心难点）

`maven.neoforged.net` 在国内同时被 **DNS 污染**和 **TLS SNI 阻断**，
直连必然失败（实测：同一个 Cloudflare IP，换成 `repo1.maven.org` 的 SNI 返回
200，换成 `maven.neoforged.net` 就被 RST）。本工程做了以下处理：

### 7.1 仓库改写（`settings.gradle`）

把所有指向官方仓库的地址**按原路径**改写到 `https://neoforged.forgecdn.net`
（CurseForge 提供的官方完整镜像）。这里有两个仓库路径，缺一不可：

| 官方地址 | 镜像地址 | 提供什么 |
| --- | --- | --- |
| `maven.neoforged.net/releases` | `neoforged.forgecdn.net/releases` | NeoForge、NeoForm、各种构建工具 |
| `maven.neoforged.net/mojang-meta` | `neoforged.forgecdn.net/mojang-meta` | `net.neoforged:minecraft-dependencies`（NeoForm 生成的 Minecraft 依赖清单） |

> **踩坑记录 1**：一开始把整个 URL 统一替换成 `/releases`，结果
> `minecraft-dependencies` 解析失败——因为它只存在于 `/mojang-meta` 下。
>
> **踩坑记录 2**：ModDevGradle 会把仓库注入到
> `settings.dependencyResolutionManagement`，而构建脚本自己也可能声明仓库。
> 两处都必须拦截，并且要用 `configureEach` 做**延迟**拦截，因为插件是在
> 构建过程中动态注册仓库的。
>
> **踩坑记录 3**：`minecraft-dependencies` 是一个 **Gradle module metadata**
> 构件（`.module` 有 151 KB，列出全部 MC 库），不是普通 POM。所以镜像必须
> 同时提供 `.module` 文件。

### 7.2 重编译改用 Eclipse 编译器（`gradle.properties`）

即使仓库全部打通，NeoForm 在最后一步「重编译反编译产物」时仍会失败：

```
Compiling 5364 source files...
ERROR Line: 1, ..net.minecraft.world.ticks.package-info...: null in .../package-info.java
java.io.IOException: Compilation failed
```

这是 NeoForm 自带的 JDK 编译器路径与 JDK 21.0.12 的兼容问题。
ModDevGradle 提供了官方开关，改用 NeoForm 内置的 Eclipse 编译器（ECJ）即可：

```properties
neoForge.neoFormRuntime.useEclipseCompiler=true
```

### 7.3 关闭 Gradle 原生文件监视

受限环境下 `fileevents` 会以 `Couldn't open current thread, error = 5` 崩溃：

```properties
org.gradle.vfs.watch=false
```

---

## 8. 常见问题

**Q: 为什么 `gradle wrapper` 用镜像 URL？**
`services.gradle.org` 的分发包下载节点在此网络下证书链不完整（Java 下报
`PKIX path building failed`），只有目录页正常。所以 `gradle-wrapper.properties`
里用的是腾讯云镜像：

```
distributionUrl=https://mirrors.cloud.tencent.com/gradle/gradle-8.12-bin.zip
```

备选镜像：`https://repo.huaweicloud.com/gradle/gradle-8.12-bin.zip`

**Q: 构建时卡在 `createMinecraftArtifacts`？**
这一步会反编译整个 Minecraft（5364 个源文件），首次约 8 分钟，之后走缓存。
加 `--info` 可以看到 NeoForm 的实时进度。

**Q: `.gradle-home` 为什么这么大？**
里面是 Gradle 依赖缓存 + NeoForm 的 Minecraft 反编译产物，数 GB 量级。
删掉后首次构建需要重新反编译。已在 `.gitignore` 中排除。

**Q: `curl` / `Invoke-WebRequest` 报 `SEC_E_NO_CREDENTIALS`？**
这是沙箱把进程降到低完整性级别后 Windows schannel 拿不到 TLS 凭据导致的，
只影响 curl / PowerShell；**Java 与 Node 使用自带 TLS 栈，不受影响**，
Gradle 构建完全正常。

**Q: 服务端测试留下的 `run/world` 要不要提交？**
`run/` 整个目录已在 `.gitignore` 中排除，随手删掉即可。
服务端需要 `run/eula.txt` 里 `eula=true`，已经准备好了。

---

## 9. 技术栈

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.209 |
| ModDevGradle | 2.0.107 |
| Gradle | 8.12 |
| Java | 21 |
| KubeJS（可选，`compileOnly`） | 2101.7.2-build.379 |

---

## 10. 国际化（翻译）

### 现状

- `assets/betterluck/lang/` 下有 5 个语言文件：`zh_cn` / `zh_tw` / `zh_hk` / `lzh` / `en_us`，
  **各 46 个键，五边完全一致**
- 模组名 / 简介写在 `neoforge.mods.toml` 里，是**中英双语字面量**

### 各地区用语差异（不是转字形就完事）

繁体有两套、文言一套，各自要跟**原版对应语言**的用词对齐。实测差异：

| 词 | zh_cn | zh_tw（台灣） | zh_hk（香港） | lzh（文言） |
| --- | --- | --- | --- | --- |
| 经验修补 | 经验修补 | **修補** | **經驗修補** | **修繕** |
| 抢夺 | 抢夺 | 掠奪 | 掠奪 | **奪** |
| 时运 | 时运 | **幸運** | 幸運 | **運** |
| 幸运（效果） | 幸运 | 幸運 | 幸運 | **幸** |
| 喷溅型 | 喷溅型 | **飛濺** | **飛濺型** | **噴劑** |
| 滞留型 | 滞留型 | **滯留** | **滯留型** | **滯劑** |
| 附魔台 | 附魔台 | 附魔台 | 附魔台 | **淬靈案** |
| 钻石 | 钻石 | 鑽石 | 鑽石 | **金剛石** |
| 金苹果 | 金苹果 | 金蘋果 | 金蘋果 | **金林檎** |
| 附魔金苹果 | 附魔金苹果 | 附魔金蘋果 | 附魔金蘋果 | **淬靈金林檎** |
| 锻造模板 | 锻造模板 | 鍛造模板 | 鍛造模板 | **鍛模** |
| 药水 | 药水 | 藥水 | 藥水 | **藥劑** |
| 药箭 | 药箭 | 之箭 | 之箭 | **藥矢** |

两处**用字**差异特别容易搞错（都是从官方文件统计出来的）：

| 用字 | 台灣 | 香港 | 文言 |
| --- | --- | --- | --- |
| 「里」的繁体 | **裡**（11 次） | **裏**（1 次，裡 0 次） | — |
| 「为」的繁体 | 為 | 為 | **爲**（262 次，為 0 次） |

> 文言（lzh）的语体是**极简**：`options.difficulty.hard` = 「難」、`gui.done` = 「畢」、
> `menu.quit` = 「離去」、`deathScreen.title` = 「子歿矣！」。附魔名多为单字或双字
> （銳 / 護 / 速 / 運 / 奪 / 完璧 / 羽落）。等级用「階」，所以本模组的等级表叫「階表」。
>
> ⚠️ **文言翻译是尽力而为**。术语对齐了官方 lzh，但文言的语感很讲究，
> 建议找懂文言的人过一遍。

### 繁中（zh_tw）不是简单转字形

台湾用语和大陆有差别，必须对齐**原版繁中**的术语，只做字形转换会读起来很怪。
做法是先从官方资源服务器取到 `assets/minecraft/lang/zh_tw.json`（约 45 万字节），
再逐个对照：

| 简中（原版） | 繁中（原版） | 备注 |
| --- | --- | --- |
| 时运 | **幸運** | 繁中里 Fortune 和幸运效果**同名** |
| 经验修补 | **修補** | 简中带「经验」，繁中不带 |
| 抢夺 | **掠奪** | |
| 喷溅型 | **飛濺** | |
| 滞留型 | **滯留** | |
| 概率 | **機率** | 台湾标准用词 |

顺带一个有意思的结论：本模组把附魔叫「運氣附魔」，在繁中里区分度**反而更好**
——繁中里 Fortune 就叫「幸運」，和幸运效果撞名。

### 怎么验证繁中没混进简体字（实用技巧）

没有转换库也能查：**用官方 `zh_tw.json` 的字符集反查**。
官方繁中不可能出现简体字，所以「我用了、但官方繁中从未出现」的汉字就是可疑项。

实测下来只有 `益 幾 憑 劣` 四个——都是正确的繁体字，只是原版文本里恰好没用到
（收益 / 幾次 / 憑空 / 劣質）。另外 `干`（干預）在繁体里其实也对，
但为了让说明更直白，改成了「不調整」。

取官方语言文件的办法（`piston-meta.mojang.com` 和 `resources.download.minecraft.net`
在这个网络下都能直连，不用走镜像）：

```
version_manifest_v2.json  ->  1.21.1.json  ->  assetIndex.url (= 17.json)
  ->  objects["minecraft/lang/zh_tw.json"].hash
  ->  https://resources.download.minecraft.net/<hash 前两位>/<hash>
```

脚本放在 `.setup/fetch-zhtw.js` 和 `.setup/check-lang.js`（都不进版本库）。

### 为什么模组名不能本地化

NeoForge 21.1.209 **不支持**本地化模组名和简介。证据（都是实际读源码 / 反编译得到的）：

- `ModListWidget`（NeoForge 自己的模组列表界面）用的是
  `Component.literal(stripControlCodes(container.getModInfo().getDisplayName()))`
  ——`literal` 不解翻译键
- FML 的 `ModInfo`（在 `loader-4.0.41.jar` 里）只有一个普通 `String displayName` 字段，
  在**模组发现阶段**就定下来了，那时语言文件**还没加载**
- `net.neoforged.fml.i18n` 包里只有 `FMLTranslations`（FML 自己的提示文本）
  和 `MavenVersionTranslator`（版本号显示），没有任何处理模组名的东西

所以 `displayName` 只能写成中英双语。想改回纯中文就改那一行。

### 配置界面的翻译键格式（容易搞错）

NeoForge 的配置界面会给每个条目套一层 `Component.translatable`，而且**没有 fallback**
——缺键就直接显示原始键名（比如界面上出现 `betterluck.configuration.list`）。
所以每个配置项都得在语言文件里补一条。

键的推导规则来自 `ConfigurationScreen.getTranslationKey()`：

```java
protected String getTranslationKey(final String key) {
    final ValueSpec valueSpec = getValueSpec(key);
    final String result = valueSpec != null ? valueSpec.getTranslationKey()
                                            : context.modSpec.getLevelTranslationKey(context.makeKeyList(key));
    return result != null ? result : context.modId + ".configuration." + key;
}
```

**兜底用的是叶子段名 `key`，不是完整路径**。对应到本工程：

| 界面上的东西 | 语言键 |
| --- | --- |
| 配置总标题 | `betterluck.configuration.title` |
| 根分类 `luck` | `betterluck.configuration.luck` |
| 子分类 `scaling` | `betterluck.configuration.scaling` |
| `scaling` 下的 `bonusByLevel` | `betterluck.configuration.bonusByLevel` ← **没有** `scaling.` 前缀 |
| 任意值的提示气泡 | 上面的键 + `.tooltip` |

`tooltip` 是有 fallback 的：`Component.translatableWithFallback(tooltipKey, comment)`，
回落值是**配置注释本身**。所以不写 `.tooltip` 键的话，界面里显示的就是 `Config.java`
里注释的原文。

> ⚠️ 这套键名是**从源码读出来的，没有实际跑客户端点开配置界面验证过**
> （沙箱里没法操作 GUI）。要确认的话进游戏打开模组配置界面看一眼即可——
> 如果还是显示原始键名，说明推导规则理解有误，改 `.translation(...)` 显式指定即可：
> `ModConfigSpec.Builder.translation(String)` 确实存在（`ModConfigSpec.java:820`）。

### 配置文件的注释没法翻译

`config/betterluck-common.toml` 里的注释是写死在 `Config.java` 里的字符串，
**只受 TOML 文件本身影响，不受游戏语言影响**。整合包作者改配置时看到的就是中文，
这一点在 README（面向整合包作者）里也说明了。

### 药水命名的坑（已修）

`long_luck` / `strong_luck` 这类键会被拼到**硬编码前缀**后面，不带模组命名空间：

| 形态 | 前缀 |
| --- | --- |
| 普通药水 | `item.minecraft.potion.effect.` |
| 喷溅型 | `item.minecraft.splash_potion.effect.` |
| 滞留型 | `item.minecraft.lingering_potion.effect.` |
| 药箭 | `item.minecraft.tipped_arrow.effect.` |

四套前缀**各写各的**，漏一套对应形态就会显示成原始键名。

> 另外注意：原版对延长版 / 强化版药水**用同一个名字**（靠提示气泡里的效果等级区分）。
> 本模组刻意给它们起了不同的名字（延长版 / 强化版），因为三种幸运药水效果相同、
> 只在时长和等级上有差别，同名会分不清。英文侧已同步（`Extended` / `Strong`）。
