package io.github.orangeegg123123.betterluck;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 更好的幸运效果 —— 数值调节。
 *
 * <p>分两类：
 * <ul>
 *   <li><b>数量类</b>（挖矿矿石、击杀普通掉落）走<b>等级表</b>：
 *       按来源各自查表再相加，见 {@link LuckScale}。</li>
 *   <li><b>概率类</b>（钓鱼宝藏、宝箱重掷、金苹果转化）走「系数 × 总幸运」，各自封顶。</li>
 * </ul>
 *
 * <p>幸运来源：幸运药水（强化版 +2）、幸运附魔（胸甲 + 主手工具各最多 5 级）。
 * 按默认等级表，满配 = 160% + 160% + 40% = <b>+360%</b>。
 */
public final class Config {
    public static final ModConfigSpec SPEC;

    /** 等级表：第 N 项对应等级 N，超出取最后一项。字符串形式，"0.5" 表示 +50%。 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BONUS_BY_LEVEL;

    // ---- 战斗 ----
    public static final ModConfigSpec.IntValue RARE_DROP_ROLLS_PER_LUCK;
    public static final ModConfigSpec.IntValue RARE_DROP_MAX_ROLLS;

    // ---- 钓鱼 ----
    public static final ModConfigSpec.DoubleValue FISHING_TREASURE_CHANCE_PER_LUCK;
    public static final ModConfigSpec.DoubleValue FISHING_TREASURE_CHANCE_MAX;

    // ---- 宝箱 ----
    public static final ModConfigSpec.DoubleValue CHEST_GOLDEN_APPLE_CHANCE_PER_LUCK;
    public static final ModConfigSpec.DoubleValue CHEST_GOLDEN_APPLE_CHANCE_MAX;

    /** 这些宝箱表里的金苹果不做转化（默认排除「必出金苹果」的雪屋箱子）。 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHEST_GOLDEN_APPLE_EXCLUDED;

    /** 每级幸运额外重掷几次宝箱战利品表（0 = 关闭这一条）。 */
    public static final ModConfigSpec.IntValue CHEST_EXTRA_ROLLS_PER_LUCK;

    /** 每个箱子额外重掷宝箱表的次数上限（0 = 关闭这一条）。 */
    public static final ModConfigSpec.IntValue CHEST_MAX_EXTRA_ROLLS;

    // ---- 劣质附魔 ----
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INFERIOR_ENCHANTMENTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment(
                "更好的幸运效果 —— 分两类数值。",
                "  数量类（矿石 / 普通掉落）走等级表，各来源查表后相加；",
                "  概率类（钓鱼宝藏 / 宝箱重掷 / 金苹果转化）走「系数 × 总幸运」，各自封顶。",
                "改完配置需要重启游戏才会重新读取。").push("luck");

        builder.comment(
                "等级表：第 N 项对应等级 N，超出表的长度取最后一项。",
                "默认 1→20% / 2→40% / 3→80% / 4→120% / 5→160%。",
                "写成字符串（\"0.5\" 表示 +50%），这样在配置文件里更稳。",
                "加成来源分三份相加：胸甲附魔等级、主手工具附魔等级、其余幸运（药水等）。",
                "默认满配 = 160% + 160% + 40% = +360%。",
                "挖矿和击杀按这张表全额生效；宝箱不吃这张表，它走的是重掷次数，见 chests。").push("scaling");
        BONUS_BY_LEVEL = builder
                .comment("每项一个等级对应的加成比例。")
                .defineList("bonusByLevel",
                        List.of("0.2", "0.4", "0.8", "1.2", "1.6"),
                        () -> "0.2",
                        o -> o instanceof String s && s.contains("."));
        builder.pop();

        builder.comment(
                "战斗：掉落数量那一档需要主手附魔了抢夺。",
                "稀有掉落（betterluck:rare_drops 标签）不吃数量加成，只吃下面的概率加成——",
                "这是为了避免同一个场景同时叠「数量」和「概率」两条线。").push("combat");
        RARE_DROP_ROLLS_PER_LUCK = builder
                .comment("每级幸运额外重掷几次实体战利品表；重掷结果里只保留稀有掉落，已经掉过的不再重复。")
                .defineInRange("rareDropRollsPerLuck", 1, 0, 8);
        RARE_DROP_MAX_ROLLS = builder
                .comment("上面那个额外重掷的次数上限。")
                .defineInRange("rareDropMaxRolls", 2, 0, 16);
        builder.pop();

        builder.comment("钓鱼：按总幸运算，与等级表无关。").push("fishing");
        FISHING_TREASURE_CHANCE_PER_LUCK = builder
                .comment("每级幸运把普通渔获升级成宝藏的概率。")
                .defineInRange("treasureChancePerLuck", 0.10D, 0.0D, 1.0D);
        FISHING_TREASURE_CHANCE_MAX = builder
                .comment("上面的概率上限。0.4 = 最多 40%。")
                .defineInRange("treasureChanceMax", 0.4D, 0.0D, 1.0D);
        builder.pop();

        builder.comment(
                "宝箱：只对 data/minecraft/loot_table/chests/ 下的 56 张宝箱表生效。",
                "幸运提高的是「物品出现的几率」，不是单次出现的数量——",
                "  做法是额外重掷同一张宝箱表，只把 chest_bonus_items 标签里的物品并入箱中，",
                "  而且箱子里【已经有的种类一律不再补】。",
                "  （旧版是给已有的堆按比例加数量，箱子越富收益越大；现已改成重掷。）",
                "金苹果转化按总幸运算；钻石 / 锻造模板等加成物品走重掷次数。").push("chests");
        CHEST_GOLDEN_APPLE_CHANCE_PER_LUCK = builder
                .comment("每级幸运让「金苹果」整堆变成「附魔金苹果」的概率。")
                .defineInRange("goldenAppleChancePerLuck", 0.10D, 0.0D, 1.0D);
        CHEST_GOLDEN_APPLE_CHANCE_MAX = builder
                .comment("上面的概率上限。0.4 = 最多 40%。")
                .defineInRange("goldenAppleChanceMax", 0.4D, 0.0D, 1.0D);
        CHEST_GOLDEN_APPLE_EXCLUDED = builder
                .comment("这些宝箱表里的金苹果不做转化。",
                        "默认排除 minecraft:chests/igloo_chest —— 它的金苹果是 100% 必出的，",
                        "  转化就等于每次开雪屋箱子都白送一个附魔金苹果，太超模。",
                        "其余宝箱表的金苹果都是概率抽出的（最高的是试炼密室 33%），",
                        "  再乘上这里的概率之后仍然是稀有事件。",
                        "可增删任意宝箱表 id。").defineList("goldenAppleExcludedTables",
                        List.of("minecraft:chests/igloo_chest"),
                        () -> "minecraft:chests/igloo_chest",
                        o -> o instanceof String s && s.contains(":"));
        CHEST_EXTRA_ROLLS_PER_LUCK = builder
                .comment("每级幸运额外重掷几次宝箱战利品表。",
                        "重掷就是「把整张表再抽一遍」：表里每条目本来就是按概率抽中的，",
                        "  多掷一次等于它们的出现概率各涨一份，本来就【没有】的东西才有机会出现。",
                        "重掷结果只会保留 chest_bonus_items 标签里的物品（钻石、各种金属、锻造模板等），",
                        "  其余的一律丢掉 —— 否则等于整箱东西翻倍。",
                        "两条防超模的规则：",
                        "  1) 箱子里已经有的种类不再补（必出物品不会被翻倍，富箱子也不会更富）；",
                        "  2) 每个箱子的重掷次数有上限，见 maxExtraRolls。",
                        "公式 min(系数 × 总幸运, 上限)，小数部分按概率补 1 次；设为 0 即关闭这条收益。")
                .defineInRange("extraRollsPerLuck", 1, 0, 8);
        CHEST_MAX_EXTRA_ROLLS = builder
                .comment("每个箱子额外重掷宝箱表的次数上限。",
                        "因为按「每级 N 次 × 总幸运」算，满配时裸算会到十几次，必须封顶。",
                        "上限只管重掷次数，不管最终多出多少件——实际多出多少取决于箱子缺什么：",
                        "  只有「这个箱子里还没有的」加成物品才会被补进来，所以最多也就补齐那几种。",
                        "数值越大，越容易看到新种类的宝物出现；0 = 关闭这条收益。")
                .defineInRange("maxExtraRolls", 3, 0, 16);
        builder.pop();

        builder.comment(
                "劣质附魔：降低这些附魔在随机抽取时出现的概率。",
                "格式：\"<附魔id>=<保留系数>\"，系数范围 0.0 ~ 1.0。",
                "  1.0 = 不干预；0.25 = 出现概率降到约 1/4；0.0 = 完全不会出现。",
                "原理：命中时把该附魔替换成同一抽取池里的另一个非劣质附魔，等级随机。",
                "说明：宝箱附魔书走的是等概率均匀抽取，附魔自身的 weight 字段在这里不起作用，",
                "      所以只能这样在抽取之后重掷。",
                "注意：这一条与幸运等级【无关】，是无条件生效的，且目前只作用于宝箱战利品。",
                "可增删任意附魔，填错格式的条目会被忽略。").push("inferiorEnchantments");
        INFERIOR_ENCHANTMENTS = builder
                .comment("每行一条。")
                .defineList("list",
                        List.of(
                                "minecraft:binding_curse=0.25",
                                "minecraft:vanishing_curse=0.25",
                                "minecraft:bane_of_arthropods=0.5"),
                        () -> "minecraft:binding_curse=0.25",
                        o -> o instanceof String s && s.contains("="));
        builder.pop();

        builder.pop();
        SPEC = builder.build();
    }

    private Config() {
    }
}
