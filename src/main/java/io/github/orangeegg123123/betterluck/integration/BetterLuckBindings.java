package io.github.orangeegg123123.betterluck.integration;

import io.github.orangeegg123123.betterluck.LuckScale;
import io.github.orangeegg123123.betterluck.LuckTuning;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 暴露给 KubeJS 脚本的 {@code BetterLuck} 对象。
 *
 * <p>用法（放在 {@code kubejs/startup_scripts/} 或 {@code kubejs/server_scripts/}）：
 *
 * <pre>{@code
 * // 读
 * BetterLuck.luckLevel(player)
 * BetterLuck.bonus(player)               // 当前总加成比例，1.25 = +125%
 *
 * // 改概率（覆盖配置文件）
 * BetterLuck.setTreasureChancePerLuck(0.2)
 * BetterLuck.setGoldenAppleChanceMax(0.5)
 * BetterLuck.setInferiorEnchantment("minecraft:bane_of_arthropods", 0.1)
 * BetterLuck.setBonusLevel(1, 0.2)
 *
 * // 撤销全部覆盖，回落配置
 * BetterLuck.reset()
 * }</pre>
 *
 * <p>覆盖是<b>进程级全局</b>的，优先级高于配置文件。写脚本时建议在
 * {@code ServerEvents.loaded} 之类的地方设置一次即可，不要每次事件都调。
 */
public class BetterLuckBindings {

    // ==================================================================
    //  读
    // ==================================================================

    /** 幸运等级（就是 minecraft:generic.luck 属性的值）。 */
    public float luckLevel(Player player) {
        return player == null ? 0.0F : player.getLuck();
    }

    /** 当前幸运带来的总加成比例。1.25 表示 +125%。 */
    public float bonus(Player player) {
        return player == null ? 0.0F : LuckScale.bonusFrom(player);
    }

    /** 当前的等级表（生效值，含脚本覆盖）。 */
    public List<String> bonusByLevel() {
        return new ArrayList<>(LuckTuning.bonusByLevel());
    }

    public int rareDropRollsPerLuck() {
        return LuckTuning.rareDropRollsPerLuck();
    }

    public int rareDropMaxRolls() {
        return LuckTuning.rareDropMaxRolls();
    }

    public double treasureChancePerLuck() {
        return LuckTuning.treasureChancePerLuck();
    }

    public double treasureChanceMax() {
        return LuckTuning.treasureChanceMax();
    }

    public double goldenAppleChancePerLuck() {
        return LuckTuning.goldenAppleChancePerLuck();
    }

    public double goldenAppleChanceMax() {
        return LuckTuning.goldenAppleChanceMax();
    }

    /** 金苹果不转化的宝箱表清单。 */
    public List<String> goldenAppleExcludedTables() {
        return new ArrayList<>(LuckTuning.goldenAppleExcludedTables());
    }

    /** 每级幸运额外重掷几次宝箱战利品表，0 表示关闭。 */
    public int chestExtraRollsPerLuck() {
        return LuckTuning.chestExtraRollsPerLuck();
    }

    /** 每个箱子额外重掷宝箱表的次数上限，0 表示关闭。 */
    public int chestMaxExtraRolls() {
        return LuckTuning.chestMaxExtraRolls();
    }

    /** 当前的劣质附魔清单，键是附魔 id。 */
    public Map<String, Double> inferiorEnchantments() {
        Map<String, Double> result = new LinkedHashMap<>();
        LuckTuning.inferiorEnchantments().forEach((id, factor) -> result.put(id.toString(), factor));
        return result;
    }

    // ==================================================================
    //  写：覆盖配置
    // ==================================================================

    public void setRareDropRollsPerLuck(int value) {
        LuckTuning.setRareDropRollsPerLuck(value);
    }

    public void setRareDropMaxRolls(int value) {
        LuckTuning.setRareDropMaxRolls(value);
    }

    public void setTreasureChancePerLuck(double value) {
        LuckTuning.setTreasureChancePerLuck(value);
    }

    public void setTreasureChanceMax(double value) {
        LuckTuning.setTreasureChanceMax(value);
    }

    public void setGoldenAppleChancePerLuck(double value) {
        LuckTuning.setGoldenAppleChancePerLuck(value);
    }

    public void setGoldenAppleChanceMax(double value) {
        LuckTuning.setGoldenAppleChanceMax(value);
    }

    /** 整表替换「金苹果不转化」名单。接受 JS 数组、Java 数组或任意 Iterable。 */
    public void setGoldenAppleExcludedTables(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof Object[] array) {
            for (Object element : array) list.add(String.valueOf(element));
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) list.add(String.valueOf(element));
        } else if (value != null) {
            list.add(String.valueOf(value));
        }
        LuckTuning.setGoldenAppleExcludedTables(list);
    }

    /** 设置每级幸运的宝箱重掷次数，0 表示关闭。 */
    public void setChestExtraRollsPerLuck(int value) {
        LuckTuning.setChestExtraRollsPerLuck(value);
    }

    /** 设置每箱重掷次数上限，0 表示关闭。 */
    public void setChestMaxExtraRolls(int value) {
        LuckTuning.setChestMaxExtraRolls(value);
    }

    /** 单独改某一级的加成，例如 {@code setBonusLevel(1, 0.2)}。 */
    public void setBonusLevel(int level, double value) {
        LuckTuning.setBonusLevel(level, value);
    }

    /** 整表替换。接受 JS 数组、Java 数组或任意 Iterable，元素会转成字符串。 */
    public void setBonusByLevel(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof Object[] array) {
            for (Object element : array) {
                list.add(String.valueOf(element));
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                list.add(String.valueOf(element));
            }
        } else if (value != null) {
            list.add(String.valueOf(value));
        }
        LuckTuning.setBonusByLevel(list);
    }

    /** 把某个附魔标为劣质，保留系数 0.0 ~ 1.0。 */
    public void setInferiorEnchantment(String id, double factor) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null) {
            LuckTuning.setInferiorEnchantment(key, factor);
        }
    }

    /** 撤销某个附魔的覆盖。 */
    public void clearInferiorEnchantment(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null) {
            LuckTuning.setInferiorEnchantment(key, null);
        }
    }

    /** 撤销全部脚本覆盖，全部回落配置文件。 */
    public void reset() {
        LuckTuning.reset();
    }
}
