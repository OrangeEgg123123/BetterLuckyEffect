package io.github.orangeegg123123.betterluck;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * 宝箱：幸运提高的是<b>物品出现的几率</b>，不是单次出现的数量。
 *
 * <p>三件事：
 * <ul>
 *   <li><b>额外重掷宝箱战利品表</b>，只保留 {@code betterluck:chest_bonus_items} 里的物品，
 *       而且箱子里<b>已经有的种类一律不再补</b> —— 这是本类的主线，
 *       见 {@link #addExtraAppearances}；</li>
 *   <li>金苹果按概率<b>整堆</b>换成附魔金苹果（同样按概率算，与上面是一个思路）；</li>
 *   <li>「劣质附魔」按配置降低出现概率（这一条<b>与幸运无关</b>，无条件生效）。</li>
 * </ul>
 *
 * <p><b>为什么不再给已有的堆加数量</b>：旧做法是「箱子里已有多少，就按幸运再多出几成」。
 * 那是数量加成 —— 箱子越富收益越大，玩家看到的是「同一堆东西变多」，
 * 而不是「更容易开出好东西」。现在改成重掷战利品表：表里每一条本来就是按概率抽中的，
 * 多掷一次等于它们的出现概率各涨一份，本来就<em>没有</em>的东西才有机会出现 ——
 * 这才是「提高出现几率」。
 *
 * <p>原版其实已经把幸运塞进了宝箱的 LootContext
 * （{@code RandomizableContainer} 里有 {@code withLuck(player.getLuck())}），
 * 只是原版战利品表几乎不用 {@code quality} 字段，所以体现不出来。
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

    /**
     * 「正在重掷」标记。
     *
     * <p>必须要有：{@code LootTable.getRandomItems} 会调用全局战利品修饰符，
     * 而我们又要在这中间再掷一次同一张表 —— 没有这个开关就会无限递归，
     * 直到栈溢出把服务端崩掉。
     *
     * <p>重掷出来的那一次<b>不做任何加成</b>（金苹果转化、劣质附魔都不做），
     * 因为补回来的东西随后会被筛成「加成物品」，其余处理没有意义。
     */
    private static final ThreadLocal<Boolean> REROLLING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public LuckChestModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        // 这一发是重掷出来的，直接放行，见 REROLLING 的说明
        if (Boolean.TRUE.equals(REROLLING.get())) return generatedLoot;

        ResourceLocation tableId = context.getQueriedLootTableId();
        if (tableId == null
                || !VANILLA_NAMESPACE.equals(tableId.getNamespace())
                || !tableId.getPath().startsWith(CHEST_PATH_PREFIX)) {
            return generatedLoot;
        }
        // 空箱子不补：这一条沿用了旧行为，也避免给「本来就什么都不出」的表凭空造物品
        if (generatedLoot.isEmpty()) return generatedLoot;

        // 宝箱的 LootContext 里 luck 就是开箱玩家的幸运（RandomizableContainer 塞进来的）
        float luck = context.getLuck();
        RandomSource random = context.getRandom();

        // (1) 金苹果 -> 附魔金苹果
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

        // (2) 加成物品的出现几率：额外重掷宝箱表
        if (luck > 0.0F) {
            addExtraAppearances(generatedLoot, context, tableId, luck);
        }

        // (3) 劣质附魔：与幸运无关，所以放在最后 —— 顺带也覆盖重掷补进来的附魔书
        rebalanceInferiorEnchantments(generatedLoot, context);

        return generatedLoot;
    }

    // ==================================================================
    //  出现几率：重掷宝箱表
    // ==================================================================

    /**
     * 幸运带来的「出现几率」加成：额外重掷宝箱战利品表若干次，
     * 只把 {@code betterluck:chest_bonus_items} 里的物品并入箱中，
     * 并且箱子<b>已经有的种类一律不再补</b>。
     *
     * <p>重掷的是<b>同一张表</b>，所以补进来的物品、数量分布和自然开箱完全一致 ——
     * 不存在「凭空捏一个物品出来」这回事，幸运改的只是「掷几次」。
     *
     * <p>「已经有的不再补」这条规则同时解决两件事：
     * <ul>
     *   <li>必出的物品不会被翻倍（和 {@code rare_drops} 的重掷用的是同一条规则）；</li>
     *   <li>富箱子的收益不会失控 —— 它本来就有那些东西，重掷只补它缺的。
     *       旧版数量加成正是栽在这里：沉船宝藏一次能多出 50 多件。</li>
     * </ul>
     *
     * <p>次数按「系数 × 总幸运」算，上限由配置给，公式与钓鱼 / 稀有掉落重掷一致。
     * 小数部分按概率补 1 次（见 {@link LuckMath#rollCount}），所以平均次数就等于算出来的期望。
     */
    private static void addExtraAppearances(ObjectArrayList<ItemStack> loot,
                                           LootContext context,
                                           ResourceLocation tableId,
                                           float luck) {
        int rolls = LuckMath.rollCount(
                (float) (LuckTuning.chestExtraRollsPerLuck() * (double) luck),
                context.getRandom(),
                LuckTuning.chestMaxExtraRolls());
        if (rolls <= 0) return;

        ServerLevel level = context.getLevel();
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));

        // 箱子里已有的物品种类，只有「还没有的」才补
        Set<Item> present = new HashSet<>();
        for (ItemStack stack : loot) {
            present.add(stack.getItem());
        }

        ObjectArrayList<ItemStack> rerolled = new ObjectArrayList<>();
        REROLLING.set(Boolean.TRUE);
        try {
            // 这里会再次触发本修饰符（以及别的模组的），所以必须有 REROLLING 拦一道
            for (int i = 0; i < rolls; i++) {
                table.getRandomItems(context, rerolled::add);
            }
        } finally {
            REROLLING.set(Boolean.FALSE);
        }

        for (ItemStack stack : rerolled) {
            if (stack.isEmpty() || !stack.is(LuckEffects.CHEST_BONUS_ITEMS)) continue;
            if (!present.add(stack.getItem())) continue;
            loot.add(stack);
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
