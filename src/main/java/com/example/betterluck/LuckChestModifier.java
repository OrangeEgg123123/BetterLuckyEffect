package com.example.betterluck;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 宝箱：让幸运改动宝箱里<em>本来就有</em>的东西。
 *
 * <p>三件事，都不会凭空添加物品：
 * <ul>
 *   <li>金苹果按概率<b>整堆</b>换成附魔金苹果；</li>
 *   <li>钻石 / 锻造模板的数量按幸运增加
 *       （清单见 {@code betterluck:chest_bonus_items} 标签）；</li>
 *   <li>「劣质附魔」按配置降低出现概率（这一条<b>与幸运无关</b>，无条件生效）。</li>
 * </ul>
 *
 * <p>原版其实已经把幸运塞进了宝箱的 LootContext
 * （{@code RandomizableContainer} 里有 {@code withLuck(player.getLuck())}），
 * 只是原版战利品表不用 {@code quality} 字段，所以体现不出来。
 *
 * <p>注意：这个修饰符在 JSON 里<b>没有条件</b>，会在所有战利品表上被调用，
 * 靠下面的表名前缀判断来限定范围。宝箱表有 56 张，
 * 逐个写 {@code neoforge:loot_table_id} 条件不现实；前缀比较的开销可以忽略。
 */
public class LuckChestModifier extends LootModifier {
    public static final MapCodec<LuckChestModifier> CODEC = RecordCodecBuilder.mapCodec(
            instance -> codecStart(instance).apply(instance, LuckChestModifier::new));

    /** 原版宝箱战利品表的路径前缀，即 {@code data/minecraft/loot_table/chests/}。 */
    private static final String CHEST_PATH_PREFIX = "chests/";
    private static final String VANILLA_NAMESPACE = "minecraft";

    public LuckChestModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation tableId = context.getQueriedLootTableId();
        if (!VANILLA_NAMESPACE.equals(tableId.getNamespace())
                || !tableId.getPath().startsWith(CHEST_PATH_PREFIX)) {
            return generatedLoot;
        }
        if (generatedLoot.isEmpty()) return generatedLoot;

        // (0) 劣质附魔：与幸运无关，所以放在幸运判断之前
        rebalanceInferiorEnchantments(generatedLoot, context);

        // 宝箱的 LootContext 里 THIS_ENTITY 就是开箱的玩家，拿它算等级表
        Player player = context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player p ? p : null;
        float luck = context.getLuck();
        RandomSource random = context.getRandom();

        // (1) 金苹果 -> 附魔金苹果（这一条按总幸运算，与等级表无关）
        //     必出金苹果的宝箱表排除在外，否则等于每次开箱白送一个附魔金苹果
        if (luck > 0.0F && !LuckTuning.isGoldenAppleConversionExcluded(tableId)) {
            double appleChance = Math.min(
                    LuckTuning.goldenAppleChancePerLuck() * luck,
                    LuckTuning.goldenAppleChanceMax());
            if (appleChance > 0.0D) {
                for (int i = 0; i < generatedLoot.size(); i++) {
                    ItemStack stack = generatedLoot.get(i);
                    if (stack.is(Items.GOLDEN_APPLE) && random.nextDouble() < appleChance) {
                        generatedLoot.set(i, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, stack.getCount()));
                    }
                }
            }
        }

        // (2) 加成物品：数量增加，走等级表；超出「每箱上限」时按比例缩小本次倍率
        if (player != null) {
            float bonusPerItem = LuckScale.bonusFrom(player);
            if (bonusPerItem > 0.0F) {
                applyQuantityBonus(generatedLoot, bonusPerItem, random);
            }
        }

        return generatedLoot;
    }

    /**
     * 给宝箱里的加成物品增加数量，并把「本次开箱的额外总量」限制在每箱上限内。
     *
     * <p>为什么需要上限：加成是按「箱子里已有的数量」成比例放的，所以本来就富的箱子
     * （沉船宝藏、埋藏的宝藏、堡垒宝藏）会一次多出几十件，普通箱子只多几件，
     * 贫富差距被放大。加上限之后分布会平均很多。
     *
     * <p>为什么是「按比例缩小倍率」而不是「加够了就停」：后者会让列表靠前的几堆吃掉
     * 全部额度，后面的堆什么都拿不到，结果依赖战利品表的书写顺序。缩小倍率则让每一堆
     * 按同样比例缩水，分布更公平。
     *
     * <p>注意这是<b>期望值</b>上的封顶：先算不加限制时的期望额外量，超过上限才缩倍率，
     * 之后仍走 {@link LuckMath#rollExtra} 的概率取整，所以单次开箱的实际值会上下浮动。
     */
    private static void applyQuantityBonus(ObjectArrayList<ItemStack> loot, float bonusPerItem, RandomSource random) {
        int cap = LuckTuning.chestMaxExtraPerChest();

        float effective = bonusPerItem;
        if (cap > 0) {
            double expected = 0.0D;
            for (ItemStack stack : loot) {
                if (stack.is(LuckEffects.CHEST_BONUS_ITEMS)) {
                    expected += stack.getCount() * (double) bonusPerItem;
                }
            }
            if (expected > cap) {
                effective = (float) (bonusPerItem * (cap / expected));
            }
        }

        for (ItemStack stack : loot) {
            if (!stack.is(LuckEffects.CHEST_BONUS_ITEMS)) continue;
            // 每一堆单独封顶：避免少数大堆吃掉全部收益
            int extra = LuckMath.rollExtra(stack.getCount(), effective, random,
                    LuckTuning.chestMaxExtraPerStack());
            if (extra > 0) {
                stack.setCount(stack.getCount() + extra);
            }
        }
    }

    // ==================================================================
    //  劣质附魔
    // ==================================================================

    /**
     * 按配置降低「劣质附魔」的出现概率。
     *
     * <p>为什么只能这么做：宝箱附魔书走的是 {@code EnchantRandomlyFunction}，
     * 它第 64 行是 {@code Util.getRandomSafe(list, random)} —— <b>等概率均匀抽取</b>，
     * 附魔 JSON 里的 {@code weight} 字段在这里<b>完全不起作用</b>。
     * 所以「降低概率」只能在抽取之后重掷：命中时把该附魔换成同一个池子里的
     * 另一个非劣质附魔，等价于把它的出现概率按系数压低。
     *
     * <p>副作用是这也顺带实现了「配置文件可改」——数据包 JSON 读不到配置，
     * 后处理可以。
     */
    private static void rebalanceInferiorEnchantments(ObjectArrayList<ItemStack> loot, LootContext context) {
        Map<ResourceLocation, Double> factors = LuckTuning.inferiorEnchantments();
        if (factors.isEmpty()) return;

        // 替换池与原版附魔书/附魔装备用的是同一个标签，风格保持一致
        Registry<Enchantment> enchantments = context.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT);
        HolderSet.Named<Enchantment> pool = enchantments.getTag(EnchantmentTags.ON_RANDOM_LOOT).orElse(null);
        if (pool == null || pool.size() == 0) return;

        RandomSource random = context.getRandom();

        for (int i = 0; i < loot.size(); i++) {
            ItemStack stack = loot.get(i);
            boolean book = stack.is(Items.ENCHANTED_BOOK);
            DataComponentType<ItemEnchantments> component =
                    book ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;

            ItemEnchantments current = stack.get(component);
            if (current == null || current.isEmpty()) continue;

            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(current);
            boolean changed = false;

            for (var entry : current.entrySet()) {
                Holder<Enchantment> holder = entry.getKey();
                ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
                Double factor = id == null ? null : factors.get(id);
                if (factor == null) continue;
                // 掷中了「保留」，不动它
                if (random.nextDouble() < factor) continue;

                mutable.set(holder, 0); // set 传 0 即移除
                Holder<Enchantment> replacement = pickReplacement(pool, factors, stack, book, random);
                if (replacement != null) {
                    mutable.set(replacement, 1 + random.nextInt(replacement.value().getMaxLevel()));
                }
                changed = true;
            }

            if (changed) {
                stack.set(component, mutable.toImmutable());
            }
        }
    }

    /** 从替换池里随机挑一个<b>非</b>劣质附魔；附魔书不限制种类，装备则要求该物品支持。 */
    private static Holder<Enchantment> pickReplacement(HolderSet.Named<Enchantment> pool,
                                                       Map<ResourceLocation, Double> factors,
                                                       ItemStack stack,
                                                       boolean book,
                                                       RandomSource random) {
        List<Holder<Enchantment>> candidates = pool.stream()
                .filter(holder -> {
                    ResourceLocation id = holder.unwrapKey().map(ResourceKey::location).orElse(null);
                    return id != null && !factors.containsKey(id);
                })
                .filter(holder -> book || stack.supportsEnchantment(holder))
                .toList();
        return candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
    }
}
