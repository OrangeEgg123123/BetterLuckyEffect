package com.example.betterluck;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 幸运加成怎么算：把「等级」换成「比例」，再把各来源相加。
 *
 * <p>等级表默认是 {@code 1→12.5% / 2→25% / 3→50% / 4→75% / 5→100%}，
 * 在配置里可改。超出表的长度就取最后一项。
 *
 * <p>来源分三份，各自查表后<b>相加</b>：
 * <ol>
 *   <li>胸甲上的幸运附魔等级</li>
 *   <li>主手物品上的幸运附魔等级（幸运附魔也能附在镐/锹/斧/锄上）</li>
 *   <li>其余幸运 —— 用 {@code player.getLuck()} 减去前两项装备贡献，
 *       所以药水以及任何别的模组给的幸运都算在这里</li>
 * </ol>
 *
 * <p>满配是 100% + 100% + 25%（强化版药水）= <b>+225%</b>。
 */
public final class LuckScale {
    private LuckScale() {
    }

    /**
     * 等级 → 加成比例。小数等级向上取整（0.5 级也算 1 级）。
     *
     * @param level 等级，≤0 返回 0
     * @return 加成比例，例如 0.5 表示 +50%
     */
    public static float bonusForLevel(float level) {
        if (level <= 0.0F) return 0.0F;
        List<Double> table = table();
        if (table.isEmpty()) return 0.0F;
        int index = Math.min((int) Math.ceil(level), table.size()) - 1;
        return table.get(Math.max(0, index)).floatValue();
    }

    /**
     * 玩家当前的总加成比例（三个来源相加）。
     */
    public static float bonusFrom(Player player) {
        float chest = enchantLevel(player.getItemBySlot(EquipmentSlot.CHEST));
        float hand = enchantLevel(player.getMainHandItem());
        // 用总幸运减去装备贡献，剩下的就算「其他来源」（药水、别的模组……）
        float other = Math.max(0.0F, player.getLuck() - chest - hand);
        // 再用幸运效果等级兜一次底。
        // 为什么需要：装备上的属性修正由 EnchantmentHelper.runLocationChangedEffects 每 tick 施加，
        // 如果那一刻还没生效（或者根本没生效），getLuck() 会小于装备等级，
        // 上面的减法就变成负数被夹成 0 —— 药水那一份会「凭空消失」。
        // 取两者的较大值，正常情况结果不变，异常情况也不会丢药水。
        other = Math.max(other, luckEffectLevel(player));
        return bonusForLevel(chest) + bonusForLevel(hand) + bonusForLevel(other);
    }

    /** 幸运效果提供的等级（没有就是 0）。 */
    private static float luckEffectLevel(Player player) {
        MobEffectInstance effect = player.getEffect(MobEffects.LUCK);
        return effect == null ? 0.0F : effect.getAmplifier() + 1.0F;
    }

    /** 读某件物品上的幸运附魔等级。不查注册表，直接读附魔组件。 */
    public static int enchantLevel(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(BetterLuckEffect.LUCK_ENCHANTMENT)) {
                return entry.getIntValue();
            }
        }
        return 0;
    }

    /**
     * 解析配置里的等级表。
     *
     * <p>用字符串列表而不是数字列表，是因为 NeoForge 的 {@code defineList}
     * 对数字列表的元素校验在不同版本上行为不完全一致；字符串在本工程里已经验证可行。
     */
    private static List<Double> table() {
        List<Double> result = new ArrayList<>();
        for (String raw : LuckTuning.bonusByLevel()) {
            if (raw == null) continue;
            try {
                result.add(Double.parseDouble(raw.trim()));
            } catch (NumberFormatException ignored) {
                // 写坏的条目跳过，不要让一条错配置把整个战利品流程搞崩
            }
        }
        return result;
    }
}
