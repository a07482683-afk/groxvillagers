package com.example.realvillagers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.*;

import java.util.ArrayList;
import java.util.List;

public class RealVillagers implements ModInitializer {
    public static final String MODID = "realvillagers";
    public static final List<Item> ALL_ITEMS = new ArrayList<>();

    public static final EntityType<RealVillager> REAL_VILLAGER = Registry.register(BuiltInRegistries.ENTITY_TYPE, id("real_villager"),
            EntityType.Builder.of(RealVillager::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10).build("real_villager"));

    public static final Item SPAWN_EGG = item("real_villager_spawn_egg", new SpawnEggItem(REAL_VILLAGER, 0x563C33, 0xBD8B72, new Item.Properties()));

    // ===== ОРУЖИЕ =====
    public static final Item DAGGER     = item("dagger",     new SwordItem(Tiers.IRON, 1, -1.4F, new Item.Properties()));
    public static final Item RAPIER     = item("rapier",     new SwordItem(Tiers.IRON, 2, -1.8F, new Item.Properties()));
    public static final Item CUTLASS    = item("cutlass",    new SwordItem(Tiers.IRON, 3, -2.2F, new Item.Properties()));
    public static final Item KATANA     = item("katana",     new SwordItem(Tiers.DIAMOND, 4, -2.0F, new Item.Properties()));
    public static final Item LONGSWORD  = item("longsword",  new SwordItem(Tiers.NETHERITE, 5, -2.6F, new Item.Properties().fireResistant()));
    public static final Item GREATSWORD = item("greatsword", new SwordItem(Tiers.DIAMOND, 7, -3.0F, new Item.Properties()));
    public static final Item SPEAR      = item("spear",      new SwordItem(Tiers.IRON, 4, -2.8F, new Item.Properties()));
    public static final Item HALBERD    = item("halberd",    new SwordItem(Tiers.IRON, 6, -3.1F, new Item.Properties()));
    public static final Item SCYTHE     = item("scythe",     new SwordItem(Tiers.DIAMOND, 6, -3.0F, new Item.Properties()));
    public static final Item SICKLE     = item("sickle",     new SwordItem(Tiers.IRON, 2, -2.0F, new Item.Properties()));
    public static final Item MACE       = item("mace",       new SwordItem(Tiers.IRON, 6, -3.2F, new Item.Properties()));
    public static final Item CLUB       = item("club",       new SwordItem(Tiers.WOOD, 4, -3.0F, new Item.Properties()));
    public static final Item WAR_HAMMER = item("war_hammer", new AxeItem(Tiers.DIAMOND, 8.0F, -3.4F, new Item.Properties()));
    public static final Item BATTLE_AXE = item("battle_axe", new AxeItem(Tiers.NETHERITE, 7.0F, -3.1F, new Item.Properties().fireResistant()));
    public static final Item LONGBOW    = item("longbow",    new BowItem(new Item.Properties().durability(600)));
    public static final Item HEAVY_CROSSBOW = item("heavy_crossbow", new CrossbowItem(new Item.Properties().durability(800)));

    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"),
            FabricItemGroup.builder()
                    .title(Component.literal("Real Villagers"))
                    .icon(() -> new ItemStack(SPAWN_EGG))
                    .displayItems((params, out) -> ALL_ITEMS.forEach(out::accept))
                    .build());

    private static Item item(String name, Item it) {
        Registry.register(BuiltInRegistries.ITEM, id(name), it);
        ALL_ITEMS.add(it);
        return it;
    }

    public static ResourceLocation id(String path) { return new ResourceLocation(MODID, path); }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(REAL_VILLAGER, RealVillager.createAttributes());
        AiBrain.loadConfig();
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
                ChatHandler.onChat(sender, message.signedContent()));
    }
}
