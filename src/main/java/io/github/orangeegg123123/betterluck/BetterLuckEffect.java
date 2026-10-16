package io.github.orangeegg123123.betterluck;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

/**
 * 更好的幸运效果 —— 模组主类。
 *
 * <p>NeoForge 通过 {@link Mod} 注解找到这个类，并按类型注入构造函数参数。
 * 构造函数里把各个 {@link DeferredRegister} 挂到模组事件总线上，
 * 注册内容才会在正确的时机被登记。
 */
@Mod(BetterLuckEffect.MODID)
public class BetterLuckEffect {
    /** 模组 ID，必须与 gradle.properties / neoforge.mods.toml 中保持一致。 */
    public static final String MODID = "betterluck";

    public static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------
    //  注册器：一个游戏对象类型对应一个 DeferredRegister
    // ------------------------------------------------------------------
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(Registries.POTION, MODID);

    /** 全局战利品修饰符的序列化器注册器（钓鱼的幸运加成用）。 */
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> GLOBAL_LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);

    // ------------------------------------------------------------------
    //  幸运来源之一：幸运附魔
    //
    //  纯数据包实现，定义在 data/betterluck/enchantment/luck.json，
    //  靠 minecraft:attributes 效果给 minecraft:generic.luck 加修正。
    //  因为所有收益都读 player.getLuck()，这个附魔一旦生效就自动喂给全部收益。
    //
    //  能否在附魔台刷出由标签决定：我们往 minecraft:non_treasure 里塞了它，
    //  而 in_enchanting_table / tradeable / on_random_loot 都引用 #non_treasure，
    //  所以加一处即可全部生效。
    // ------------------------------------------------------------------
    public static final ResourceKey<Enchantment> LUCK_ENCHANTMENT =
            ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(MODID, "luck"));

    // ------------------------------------------------------------------
    //  幸运来源之一：药水的延长版与强化版
    //
    //  原版 Potions.LUCK 是 6000 tick（5 分钟）的幸运 I，且拿不到。
    //  这里再补两档，用酿造台从普通幸运药水升级：
    //    红石   -> 延长版：幸运 I，12000 tick（10 分钟）
    //    萤石粉 -> 强化版：幸运 II，3600 tick（3 分钟）
    //
    //  注意药水名字：Potion 的 name 字段会拼到 "item.minecraft.potion.effect." 后面，
    //  所以语言文件里要写 item.minecraft.potion.effect.long_luck 这样的键
    //  （喷溅/滞留/药箭各有一套前缀，见 lang 文件）。
    // ------------------------------------------------------------------
    public static final DeferredHolder<Potion, Potion> LONG_LUCK = POTIONS.register("long_luck",
            () -> new Potion("long_luck", new MobEffectInstance(MobEffects.LUCK, 12000)));

    public static final DeferredHolder<Potion, Potion> STRONG_LUCK = POTIONS.register("strong_luck",
            () -> new Potion("strong_luck", new MobEffectInstance(MobEffects.LUCK, 3600, 1)));

    /**
     * 钓鱼幸运修饰符。
     *
     * <p>光注册序列化器还不够——真正的实例定义在
     * {@code data/betterluck/loot_modifiers/luck_fishing.json}，
     * 再由 {@code data/neoforge/loot_modifiers/global_loot_modifiers.json} 挂进全局列表。
     */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<LuckFishingModifier>> LUCK_FISHING_MODIFIER =
            GLOBAL_LOOT_MODIFIERS.register("luck_fishing", () -> LuckFishingModifier.CODEC);

    /**
     * 宝箱幸运修饰符。
     *
     * <p>与钓鱼那个不同，它在 JSON 里<b>没有条件</b>，会在所有战利品表上被调用，
     * 靠 {@code LuckChestModifier} 里的表名前缀判断限定在 {@code chests/} 下。
     */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<LuckChestModifier>> LUCK_CHEST_MODIFIER =
            GLOBAL_LOOT_MODIFIERS.register("luck_chest", () -> LuckChestModifier.CODEC);

    // ------------------------------------------------------------------
    //  占位内容
    //
    //  下面的 example_block / example_item 只是为了打通「注册 → 进游戏 → 看到东西」
    //  的链路。等确定模组实际内容后，把它们替换成真正的方块/物品即可。
    // ------------------------------------------------------------------
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));

    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem("example_item",
            new Item.Properties().food(new FoodProperties.Builder()
                    .alwaysEdible()
                    .nutrition(1)
                    .saturationModifier(2.0F)
                    .build()));

    /** 模组自己的创造模式标签页。 */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> LUCK_TAB =
            CREATIVE_MODE_TABS.register("luck_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MODID))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> PotionContents.createItemStack(Items.POTION, Potions.LUCK))
                    .displayItems((parameters, output) -> {
                        // 三档幸运药水。原版不把它们放进创造模式，所以这里手动放出来。
                        output.accept(PotionContents.createItemStack(Items.POTION, Potions.LUCK));
                        output.accept(PotionContents.createItemStack(Items.POTION, LONG_LUCK));
                        output.accept(PotionContents.createItemStack(Items.POTION, STRONG_LUCK));
                        output.accept(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.LUCK));
                        output.accept(PotionContents.createItemStack(Items.LINGERING_POTION, Potions.LUCK));
                        output.accept(EXAMPLE_ITEM.get());
                        output.accept(EXAMPLE_BLOCK_ITEM.get());
                    })
                    .build());

    // ------------------------------------------------------------------
    //  生命周期
    // ------------------------------------------------------------------
    public BetterLuckEffect(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        // BuildCreativeModeTabContentsEvent 属于「模组事件总线」，
        // 不能用 @SubscribeEvent 挂到 NeoForge.EVENT_BUS 上，否则启动即报错。
        modEventBus.addListener(this::addCreative);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        POTIONS.register(modEventBus);
        GLOBAL_LOOT_MODIFIERS.register(modEventBus);

        // 游戏事件总线（非模组加载事件）
        NeoForge.EVENT_BUS.register(this);
        // 幸运效果带来的挖矿 / 战斗收益
        LuckEffects.register();

        // 注册配置文件，生成于 config/betterluck-common.toml
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("更好的幸运效果：通用初始化完成");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 启动自检：数据文件路径写错时不会报错、只会静默失效，所以这里主动查一遍。
        var registries = event.getServer().registryAccess();

        int rareCount = registries.registryOrThrow(Registries.ITEM)
                .getTag(LuckEffects.RARE_DROPS)
                .map(HolderSet::size)
                .orElse(-1);

        int chestItemCount = registries.registryOrThrow(Registries.ITEM)
                .getTag(LuckEffects.CHEST_BONUS_ITEMS)
                .map(HolderSet::size)
                .orElse(-1);

        var enchantments = registries.registryOrThrow(Registries.ENCHANTMENT);
        boolean enchantLoaded = enchantments.containsKey(LUCK_ENCHANTMENT);
        // areCompatible 是双向判定，返回 false 就代表两边互斥。
        boolean mendingExclusive = enchantLoaded
                && !Enchantment.areCompatible(
                        enchantments.getHolderOrThrow(LUCK_ENCHANTMENT),
                        enchantments.getHolderOrThrow(Enchantments.MENDING));

        LOGGER.info("更好的幸运效果：自检 —— 稀有掉落 {} 项；宝箱加成 {} 项；幸运附魔 {}（最高 {} 级；槽位 {}；宝箱 {} / 附魔台 {} / 村民交易 {} / 怪物装备 {} / 与经验修补互斥 {}）；自定义药水 2 档",
                rareCount,
                chestItemCount,
                enchantLoaded ? "已载入" : "缺失",
                enchantLoaded ? enchantments.getHolderOrThrow(LUCK_ENCHANTMENT).value().getMaxLevel() : 0,
                enchantLoaded ? enchantments.getHolderOrThrow(LUCK_ENCHANTMENT).value().definition().slots() : "-",
                yesNo(inTag(enchantments, EnchantmentTags.ON_RANDOM_LOOT)),
                yesNo(inTag(enchantments, EnchantmentTags.IN_ENCHANTING_TABLE)),
                yesNo(inTag(enchantments, EnchantmentTags.TRADEABLE)),
                yesNo(inTag(enchantments, EnchantmentTags.ON_MOB_SPAWN_EQUIPMENT)),
                yesNo(mendingExclusive));

        LOGGER.info("更好的幸运效果：数值 —— 数量类等级表 {}（挖矿 / 击杀普通掉落；胸甲 / 主手工具 / 其余幸运 各自查表后相加）；钓鱼宝藏 {}%/级（上限 {}%），稀有重掷 {} 次/级（上限 {} 次）",
                LuckTuning.bonusByLevel(),
                LuckTuning.treasureChancePerLuck() * 100,
                LuckTuning.treasureChanceMax() * 100,
                LuckTuning.rareDropRollsPerLuck(),
                LuckTuning.rareDropMaxRolls());

        LOGGER.info("更好的幸运效果：宝箱 —— 金苹果转化 {}%/级（上限 {}%），排除 {} 张必出表；加成物品（{} 项）改为「额外重掷宝箱表、只补箱子里还没有的」，{} 次/级（上限 {} 次，0 = 关闭）",
                LuckTuning.goldenAppleChancePerLuck() * 100,
                LuckTuning.goldenAppleChanceMax() * 100,
                LuckTuning.goldenAppleExcludedTables().size(),
                chestItemCount,
                LuckTuning.chestExtraRollsPerLuck(),
                LuckTuning.chestMaxExtraRolls());

        // 把解析后的劣质附魔打出来：配置格式写错时不会报错、只会静默失效，这里能直接看出来。
        var inferior = LuckTuning.inferiorEnchantments();
        LOGGER.info("更好的幸运效果：劣质附魔 —— 已生效 {} 项 {}（与幸运无关；格式错的条目会被忽略）",
                inferior.size(), inferior);
    }

    /** 判断本模组的幸运附魔是否注册在某个附魔标签里。 */
    private static boolean inTag(Registry<Enchantment> enchantments, TagKey<Enchantment> tag) {
        return enchantments.getTag(tag)
                .map(holders -> holders.contains(enchantments.getHolderOrThrow(LUCK_ENCHANTMENT)))
                .orElse(false);
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    // ------------------------------------------------------------------
    //  酿造配方
    // ------------------------------------------------------------------

    /**
     * 让幸运药水可以被获得，并且能继续升级。
     *
     * <p>原版的 {@link Potions#LUCK} 只是「定义了但拿不到」——它不在任何酿造配方里，
     * 也不出现在战利品表中，生存模式下无解。这里补上三条：
     *
     * <ul>
     *   <li>粗制的药水 + 绿宝石 → 幸运药水</li>
     *   <li>幸运药水 + 红石 → 延长版幸运药水（10 分钟）</li>
     *   <li>幸运药水 + 萤石粉 → 强化版幸运药水（幸运 II）</li>
     * </ul>
     *
     * <p>本事件在<b>游戏事件总线</b>上触发（{@code PotionBrewing.bootstrap()} 里是
     * {@code NeoForge.EVENT_BUS.post(...)}），所以构造函数中注册的 {@code this} 能收到。
     *
     * <p>第一条用 {@code addMix} 而不是 {@code addStartMix}：后者会顺带加上
     * 「水瓶 + 绿宝石 → 浑浊的药水」，那是原版给糖、岩浆膏这类材料用的写法。
     */
    @SubscribeEvent
    public void onRegisterBrewingRecipes(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, Items.EMERALD, Potions.LUCK);
        event.getBuilder().addMix(Potions.LUCK, Items.REDSTONE, LONG_LUCK);
        event.getBuilder().addMix(Potions.LUCK, Items.GLOWSTONE_DUST, STRONG_LUCK);
        LOGGER.info("更好的幸运效果：已注册 3 条酿造配方（幸运 / 延长版 / 强化版）");
    }

    /** 往已有的原版标签页里塞物品的示例（这里故意留空，按需启用）。 */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // if (event.getTabKey() == CreativeModeTabs.BREWING) {
        //     event.accept(PotionContents.createItemStack(Items.POTION, Potions.LUCK));
        // }
    }
}
