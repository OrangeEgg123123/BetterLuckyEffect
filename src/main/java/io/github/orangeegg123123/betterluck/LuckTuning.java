package io.github.orangeegg123123.betterluck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * 运行时可覆盖的数值层。
 *
 * <p>优先级：<b>KubeJS 脚本设置的值 &gt; 配置文件 &gt; 内置默认</b>。
 * 没有任何覆盖时原样回落配置，所以不装 KubeJS、不写脚本的玩家行为完全不变。
 *
 * <p>所有字段都允许为 {@code null}，含义是「没被脚本覆盖」。
 */
public final class LuckTuning {
    private static volatile List<String> bonusByLevel;
    private static volatile Integer rareDropRollsPerLuck;
    private static volatile Integer rareDropMaxRolls;
    private static volatile Double treasureChancePerLuck;
    private static volatile Double treasureChanceMax;
    private static volatile Double goldenAppleChancePerLuck;
    private static volatile Double goldenAppleChanceMax;
    private static volatile List<String> goldenAppleExcludedTables;
    private static volatile Integer chestExtraRollsPerLuck;
    private static volatile Integer chestMaxExtraRolls;

    /** 脚本对「劣质附魔」的逐项覆盖，键为附魔 id。 */
    private static final Map<ResourceLocation, Double> inferiorOverrides = new ConcurrentHashMap<>();

    private LuckTuning() {
    }

    // ==================================================================
    //  读：覆盖优先，否则回落配置
    // ==================================================================

    public static List<String> bonusByLevel() {
        List<String> override = bonusByLevel;
        if (override != null) return override;
        // Config 给的是 List<? extends String>，这里拷一份转成 List<String>
        return List.copyOf(Config.BONUS_BY_LEVEL.get());
    }

    public static int rareDropRollsPerLuck() {
        Integer override = rareDropRollsPerLuck;
        return override != null ? override : Config.RARE_DROP_ROLLS_PER_LUCK.get();
    }

    public static int rareDropMaxRolls() {
        Integer override = rareDropMaxRolls;
        return override != null ? override : Config.RARE_DROP_MAX_ROLLS.get();
    }

    public static double treasureChancePerLuck() {
        Double override = treasureChancePerLuck;
        return override != null ? override : Config.FISHING_TREASURE_CHANCE_PER_LUCK.get();
    }

    public static double treasureChanceMax() {
        Double override = treasureChanceMax;
        return override != null ? override : Config.FISHING_TREASURE_CHANCE_MAX.get();
    }

    public static double goldenAppleChancePerLuck() {
        Double override = goldenAppleChancePerLuck;
        return override != null ? override : Config.CHEST_GOLDEN_APPLE_CHANCE_PER_LUCK.get();
    }

    public static double goldenAppleChanceMax() {
        Double override = goldenAppleChanceMax;
        return override != null ? override : Config.CHEST_GOLDEN_APPLE_CHANCE_MAX.get();
    }

    public static List<String> goldenAppleExcludedTables() {
        List<String> override = goldenAppleExcludedTables;
        if (override != null) return override;
        return List.copyOf(Config.CHEST_GOLDEN_APPLE_EXCLUDED.get());
    }

    /** 每级幸运额外重掷几次宝箱战利品表（0 = 关闭这一条）。 */
    public static int chestExtraRollsPerLuck() {
        Integer override = chestExtraRollsPerLuck;
        return override != null ? override : Config.CHEST_EXTRA_ROLLS_PER_LUCK.get();
    }

    /** 每个箱子额外重掷宝箱表的次数上限（0 = 关闭这一条）。 */
    public static int chestMaxExtraRolls() {
        Integer override = chestMaxExtraRolls;
        return override != null ? override : Config.CHEST_MAX_EXTRA_ROLLS.get();
    }

    /**
     * 该宝箱表是否在「金苹果不转化」名单里。
     *
     * <p>为什么需要这个：{@code minecraft:chests/igloo_chest} 的金苹果是
     * <b>100% 必出</b>的（单独一个池、{@code rolls: 1}、只有一条目），
     * 转化就等于每次开箱白送一个附魔金苹果。其余宝箱表的金苹果都是概率抽出的，
     * 再乘上转化概率之后仍然是稀有事件。
     *
     * <p>战利品表<b>不支持标签</b>（原版没有 {@code loot_table} 标签注册表），
     * 所以这份名单只能放在配置里，而不是做成数据包标签。
     */
    public static boolean isGoldenAppleConversionExcluded(ResourceLocation tableId) {
        if (tableId == null) return false;
        for (String raw : goldenAppleExcludedTables()) {
            if (raw == null) continue;
            ResourceLocation id = ResourceLocation.tryParse(raw.trim());
            if (tableId.equals(id)) return true;
        }
        return false;
    }

    /** 劣质附魔：配置文件里的清单 + 脚本的逐项覆盖。 */
    public static Map<ResourceLocation, Double> inferiorEnchantments() {
        Map<ResourceLocation, Double> result = new HashMap<>(InferiorEnchantments.fromConfig());
        result.putAll(inferiorOverrides);
        return result;
    }

    // ==================================================================
    //  写：给 KubeJS 绑定用。传 null 表示撤销覆盖、回落配置。
    // ==================================================================

    public static void setBonusByLevel(List<String> value) {
        bonusByLevel = value == null ? null : List.copyOf(value);
    }

    /**
     * 只改某一级的加成，其余保持不变。
     *
     * <p>给脚本用的一般是这种形式（{@code BetterLuck.setBonusLevel(1, 0.2)}），
     * 因为从 JS 传数组过来要处理类型转换，单点设置最稳。
     *
     * @param level 等级，从 1 开始
     * @param value 加成比例，传 {@code null} 表示该级归零
     */
    public static void setBonusLevel(int level, Double value) {
        if (level < 1) return;
        List<String> current = new java.util.ArrayList<>(bonusByLevel());
        String filler = current.isEmpty() ? "0.0" : current.get(current.size() - 1);
        while (current.size() < level) {
            current.add(filler);
        }
        current.set(level - 1, value == null ? "0.0" : String.valueOf(value));
        bonusByLevel = List.copyOf(current);
    }

    public static void setRareDropRollsPerLuck(Integer value) {
        rareDropRollsPerLuck = value;
    }

    public static void setRareDropMaxRolls(Integer value) {
        rareDropMaxRolls = value;
    }

    public static void setTreasureChancePerLuck(Double value) {
        treasureChancePerLuck = value;
    }

    public static void setTreasureChanceMax(Double value) {
        treasureChanceMax = value;
    }

    public static void setGoldenAppleChancePerLuck(Double value) {
        goldenAppleChancePerLuck = value;
    }

    public static void setGoldenAppleChanceMax(Double value) {
        goldenAppleChanceMax = value;
    }

    public static void setGoldenAppleExcludedTables(List<String> value) {
        goldenAppleExcludedTables = value == null ? null : List.copyOf(value);
    }

    public static void setChestExtraRollsPerLuck(Integer value) {
        chestExtraRollsPerLuck = value;
    }

    public static void setChestMaxExtraRolls(Integer value) {
        chestMaxExtraRolls = value;
    }

    public static void setInferiorEnchantment(ResourceLocation id, Double factor) {
        if (id == null) return;
        if (factor == null) {
            inferiorOverrides.remove(id);
        } else {
            inferiorOverrides.put(id, factor);
        }
    }

    /** 撤销全部脚本覆盖。 */
    public static void reset() {
        bonusByLevel = null;
        rareDropRollsPerLuck = null;
        rareDropMaxRolls = null;
        treasureChancePerLuck = null;
        treasureChanceMax = null;
        goldenAppleChancePerLuck = null;
        goldenAppleChanceMax = null;
        goldenAppleExcludedTables = null;
        chestExtraRollsPerLuck = null;
        chestMaxExtraRolls = null;
        inferiorOverrides.clear();
    }
}
