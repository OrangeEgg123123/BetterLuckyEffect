package io.github.orangeegg123123.betterluck;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 钓鱼：幸运越高，越容易把普通渔获升级成「宝藏」。
 *
 * <p>为什么这里必须用全局战利品修饰符（Global Loot Modifier）而不是事件：
 * {@code ItemFishedEvent} 构造时把掉落列表 {@code addAll} 复制了一份，
 * 改 {@code getDrops()} 不会影响真正掉落的东西
 * （NeoForge 的注释也写着「要影响战利品请用 LootTable」）。
 *
 * <p>原版其实已经把幸运加进了钓鱼的 LootContext，靠战利品表的 quality 权重生效，
 * 但幅度很小（幸运 I 只把宝藏权重从 5 抬到 7）。这里直接按概率把整次渔获
 * 换成一次宝藏表抽取，效果明显得多。
 *
 * <p>修饰符实例由 {@code data/betterluck/loot_modifiers/luck_fishing.json} 定义，
 * 条件限定为只对 {@code minecraft:gameplay/fishing} 生效。
 */
public class LuckFishingModifier extends LootModifier {
    public static final MapCodec<LuckFishingModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, LuckFishingModifier::new));

    public LuckFishingModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        float luck = context.getLuck();
        if (luck <= 0.0F || generatedLoot.isEmpty()) return generatedLoot;

        double chance = Math.min(
                LuckTuning.treasureChancePerLuck() * luck,
                LuckTuning.treasureChanceMax());
        if (chance <= 0.0D || context.getRandom().nextDouble() >= chance) return generatedLoot;

        if (!(context.getLevel() instanceof ServerLevel serverLevel)) return generatedLoot;

        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        Entity hook = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (origin == null || tool == null || hook == null) return generatedLoot;

        LootTable treasureTable = serverLevel.getServer().reloadableRegistries()
                .getLootTable(BuiltInLootTables.FISHING_TREASURE);

        LootParams params = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, tool)
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(luck)
                .create(LootContextParamSets.FISHING);

        // 宝藏表的 id 不是 gameplay/fishing，所以上面那个条件不会再次命中，不会递归。
        ObjectArrayList<ItemStack> treasure = treasureTable.getRandomItems(params);
        return treasure.isEmpty() ? generatedLoot : treasure;
    }
}
