package io.github.orangeegg123123.betterluck;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * 「劣质附魔」配置的解析。
 *
 * <p>配置里每条写成 {@code <附魔id>=<保留系数>}，例如
 * {@code minecraft:binding_curse=0.25} 表示绑定诅咒的出现概率降到约 1/4。
 *
 * <p>每次用到时现解析。列表通常只有几条，开销可以忽略，
 * 而且这样配置热重载后不需要额外清理缓存。
 *
 * <p>脚本覆盖那一层在 {@link LuckTuning#inferiorEnchantments()}。
 */
public final class InferiorEnchantments {
    private InferiorEnchantments() {
    }

    /**
     * 只读配置文件。
     *
     * @return 附魔 id → 保留系数。解析失败的条目、以及系数 ≥ 1.0 的条目会被忽略。
     */
    public static Map<ResourceLocation, Double> fromConfig() {
        Map<ResourceLocation, Double> result = new HashMap<>();
        for (String raw : Config.INFERIOR_ENCHANTMENTS.get()) {
            if (raw == null) continue;
            int eq = raw.indexOf('=');
            if (eq <= 0) continue;

            ResourceLocation id = ResourceLocation.tryParse(raw.substring(0, eq).trim());
            if (id == null) continue;

            try {
                double factor = Double.parseDouble(raw.substring(eq + 1).trim());
                if (factor >= 1.0D) continue;
                result.put(id, Math.max(0.0D, factor));
            } catch (NumberFormatException ignored) {
                // 写坏了就当没写，不要让一条错配置把整个战利品流程搞崩
            }
        }
        return result;
    }
}
