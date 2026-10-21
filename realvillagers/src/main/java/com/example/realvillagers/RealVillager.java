package com.example.realvillagers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RealVillager extends PathfinderMob {

    public static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(RealVillager.class, EntityDataSerializers.INT);
    public static final int ANIM_NONE = 0, ANIM_WAVE = 1, ANIM_DANCE = 2, ANIM_HANDS_UP = 3, ANIM_SAD = 4, ANIM_POINT = 5;

    public enum Emotion {
        NEUTRAL("§7•", null, "нормально"),
        HAPPY("§a☺", ParticleTypes.HAPPY_VILLAGER, "отлично!"),
        SAD("§9☹", ParticleTypes.FALLING_WATER, "грустно..."),
        ANGRY("§c✖", ParticleTypes.ANGRY_VILLAGER, "я зол!"),
        SCARED("§e!", ParticleTypes.SMOKE, "мне страшно..."),
        LOVE("§d❤", ParticleTypes.HEART, "я счастлив как никогда!");
        final String icon; final ParticleOptions particle; final String text;
        Emotion(String i, ParticleOptions p, String t) { icon = i; particle = p; text = t; }
    }

    public enum Order { NONE, FOLLOW, STAY, GUARD, ATTACK, COLLECT, GO_HOME, FLEE }

    private static final String[] NAMES = {"Борис", "Глеб", "Олег", "Степан", "Фома", "Игнат", "Марфа", "Агата",
            "Василиса", "Тихон", "Ефим", "Прохор", "Аксинья", "Демьян", "Зина", "Савелий", "Нюра"};
    private static final Pattern TAG = Pattern.compile("\\[(EMOTION|REL|FEAR|ACTION|MEMORY):([^\\]]*)\\]");

    // ключевые слова чата -> действие
    private static final String[][] COMMANDS = {
            {"FREE", "свободен", "отдыхай", "вольно", "отбой", "хватит"},
            {"FOLLOW", "за мной", "иди сюда", "следуй", "пошли со мной", "идём", "идем"},
            {"STAY", "стой", "жди", "замри", "стоять"},
            {"GUARD", "охраняй", "сторожи", "защищай"},
            {"ATTACK", "атакуй", "убей", "нападай", "фас", "в атаку"},
            {"COLLECT", "собери", "подбери", "собирай"},
            {"DROP_ALL", "выброси всё", "выброси все", "выкинь всё", "выкинь все"},
            {"GIVE", "отдай", "дай "},
            {"EQUIP", "надень", "вооружись", "возьми оружие"},
            {"DANCE", "танцуй", "станцуй", "потанцуй"},
            {"WAVE", "помаши"},
            {"STAND", "встань"},
            {"CROUCH", "присядь", "сядь"},
            {"JUMP", "прыгни", "прыгай"},
            {"SET_HOME", "здесь твой дом", "живи здесь", "живи тут"},
            {"GO_HOME", "иди домой", "домой"},
            {"FLEE", "беги", "убегай", "проваливай"},
    };

    public final SimpleContainer inventory = new SimpleContainer(27);
    private final List<String> memories = new ArrayList<>();
    private final Map<UUID, Integer> relations = new HashMap<>();
    private final Map<UUID, Integer> fears = new HashMap<>();
    public final List<String[]> chatHistory = new ArrayList<>();
    private String baseName;
    private int bravery;
    private Emotion emotion = Emotion.NEUTRAL;
    private int emotionTimer, animTimer;
    public Order order = Order.NONE;
    public UUID orderPlayer;
    public BlockPos homePos, guardPos;
    public int fleeTimer;
    private long lastGreet = -10000;
    private int lastScare = -10000;

    public RealVillager(EntityType<? extends RealVillager> type, Level level) {
        super(type, level);
        baseName = NAMES[random.nextInt(NAMES.length)];
        bravery = random.nextInt(101);
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 2.0F);
        ((GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ATTACK_KNOCKBACK, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ANIM, ANIM_NONE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new VillagerGoals.CombatGoal(this));
        goalSelector.addGoal(2, new PanicGoal(this, 1.4D) {
            @Override public boolean canUse() { return !hasWeapon() && !isBrave() && super.canUse(); }
        });
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Monster.class, 10.0F, 0.9D, 1.3D) {
            @Override public boolean canUse() { return !hasWeapon() && order != Order.STAY && super.canUse(); }
        });
        goalSelector.addGoal(3, new VillagerGoals.OrderGoal(this));
        goalSelector.addGoal(4, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6D) {
            @Override public boolean canUse() { return order == Order.NONE && getAnim() == ANIM_NONE && super.canUse(); }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false,
                e -> hasWeapon() && !(e instanceof Creeper) && !(e instanceof EnderMan)));
    }

    // ================== ТИК / ЗРЕНИЕ / ЭМОЦИИ / ТЕЛО ==================
    @Override
    public void aiStep() {
        updateSwingTime();
        super.aiStep();
        if (level().isClientSide) return;
        if (getCustomName() == null) updateName();
        if (emotionTimer > 0 && --emotionTimer == 0) { emotion = Emotion.NEUTRAL; updateName(); }
        if (animTimer > 0 && --animTimer == 0) entityData.set(ANIM, ANIM_NONE);
        if (tickCount % 40 == 0) spawnEmotionParticles();
        if (tickCount % 20 == 0) observe();
    }

    private void observe() {
        Player p = level().getNearestPlayer(this, 8);
        if (p != null && !p.isSpectator() && hasLineOfSight(p) && level().getGameTime() - lastGreet > 2400) {
            lastGreet = level().getGameTime();
            String n = p.getName().getString();
            if (getRelation(p) >= 30) { say("О, " + n + "! Рад тебя видеть!"); setEmotion(Emotion.HAPPY, 100); playAnim(ANIM_WAVE, 50); }
            else if (getFear(p) >= 50) { say("Н-не подходи ко мне, " + n + "!"); setEmotion(Emotion.SCARED, 100); playAnim(ANIM_HANDS_UP, 60); }
            else if (getRelation(p) <= -30) { say("Опять ты... держись подальше."); setEmotion(Emotion.ANGRY, 100); }
        }
        Monster m = level().getNearestEntity(Monster.class, TargetingConditions.forCombat().range(10), this,
                getX(), getY(), getZ(), getBoundingBox().inflate(10));
        if (m != null && hasLineOfSight(m) && tickCount - lastScare > 400) {
            lastScare = tickCount;
            if (hasWeapon()) { say("Монстр! К бою!"); setEmotion(Emotion.ANGRY, 80); }
            else { say("Монстр!! Помогите!"); setEmotion(Emotion.SCARED, 100); }
            remember("видел монстра: " + m.getName().getString());
        }
    }

    public int getAnim() { return entityData.get(ANIM); }
    public void playAnim(int anim, int ticks) { entityData.set(ANIM, anim); animTimer = ticks; }
    public void setCrouch(boolean c) { setShiftKeyDown(c); setPose(c ? Pose.CROUCHING : Pose.STANDING); }

    public void setEmotion(Emotion e, int ticks) { emotion = e; emotionTimer = ticks; updateName(); spawnEmotionParticles(); }

    private void updateName() {
        setCustomName(Component.literal(baseName + " " + emotion.icon));
        setCustomNameVisible(true);
    }

    private void spawnEmotionParticles() {
        if (emotion.particle != null && level() instanceof ServerLevel sl)
            sl.sendParticles(emotion.particle, getX(), getY() + 2.2D, getZ(), 3, 0.3D, 0.2D, 0.3D, 0.02D);
    }

    public String getBaseName() { return baseName; }
    public boolean isBrave() { return bravery > 70; }

    // ================== ОТНОШЕНИЯ И ПАМЯТЬ ==================
    public int getRelation(Player p) { return relations.getOrDefault(p.getUUID(), 0); }
    public int getFear(Player p) { return fears.getOrDefault(p.getUUID(), 0); }
    public void changeRelation(Player p, int d) { relations.put(p.getUUID(), Mth.clamp(getRelation(p) + d, -100, 100)); }
    public void changeFear(Player p, int d) { fears.put(p.getUUID(), Mth.clamp(getFear(p) + d, 0, 100)); }

    public String relationWord(Player p) {
        int r = getRelation(p);
        if (r >= 70) return "лучший друг";
        if (r >= 30) return "друг";
        if (r >= -10) return "просто знакомый";
        if (r >= -50) return "неприятный тип";
        return "враг";
    }

    public void remember(String s) {
        long day = level().getDayTime() / 24000L + 1;
        memories.add("[день " + day + "] " + s);
        while (memories.size() > 60) memories.remove(0);
    }

    public void addHistory(String role, String text) {
        chatHistory.add(new String[]{role, text});
        while (chatHistory.size() > 10) chatHistory.remove(0);
    }

    public void say(String text) {
        Component c = Component.literal("§6<" + baseName + ">§r " + text);
        for (Player p : level().players()) if (p.distanceToSqr(this) < 32 * 32) p.sendSystemMessage(c);
    }

    private String pick(String... s) { return s[random.nextInt(s.length)]; }
    private static boolean has(String msg, String... keys) { for (String k : keys) if (msg.contains(k)) return true; return false; }
    public static String cut(String s) { return s.length() > 60 ? s.substring(0, 60) + "..." : s; }

    // ================== ИНВЕНТАРЬ И ОРУЖИЕ ==================
    public boolean hasWeapon() { return weaponScore(getMainHandItem()) > 0; }
    public boolean hasRangedWeapon() { Item i = getMainHandItem().getItem(); return i instanceof ProjectileWeaponItem || i instanceof TridentItem; }

    private static float weaponScore(ItemStack s) {
        Item i = s.getItem();
        if (i instanceof SwordItem sw) return sw.getDamage() + 4;
        if (i instanceof AxeItem ax) return ax.getAttackDamage() + 3.5F;
        if (i instanceof TridentItem) return 9;
        if (i instanceof ProjectileWeaponItem) return 7;
        return -1;
    }

    public void equipBestGear() {
        int best = -1; float bestScore = weaponScore(getMainHandItem());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            float sc = weaponScore(inventory.getItem(i));
            if (sc > bestScore) { bestScore = sc; best = i; }
        }
        if (best >= 0) {
            ItemStack newW = inventory.removeItemNoUpdate(best);
            ItemStack old = getMainHandItem();
            setItemSlot(EquipmentSlot.MAINHAND, newW);
            inventory.setItem(best, old);
        }
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.getItem() instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                ItemStack cur = getItemBySlot(slot);
                int curDef = cur.getItem() instanceof ArmorItem a ? a.getDefense() : -1;
                if (armor.getDefense() > curDef) { setItemSlot(slot, s); inventory.setItem(i, cur); }
            }
        }
    }

    public String inventoryText() {
        List<String> list = new ArrayList<>();
        if (!getMainHandItem().isEmpty()) list.add("в руке " + getMainHandItem().getHoverName().getString());
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty()) list.add(s.getHoverName().getString() + " x" + s.getCount());
        }
        return list.isEmpty() ? "пусто" : String.join(", ", list);
    }

    public void pickUp(ItemEntity e) {
        ItemStack stack = e.getItem();
        ItemStack rest = inventory.addItem(stack.copy());
        int taken = stack.getCount() - rest.getCount();
        if (taken == 0) { say("Мои карманы полны!"); order = Order.NONE; return; }
        take(e, taken);
        if (rest.isEmpty()) e.discard(); else e.setItem(rest);
        equipBestGear();
    }

    private static boolean matches(ItemStack s, String[] words) {
        if (words.length == 0) return true;
        String name = (s.getHoverName().getString() + " " + s.getItem()).toLowerCase(Locale.ROOT);
        for (String w : words) {
            String stem = w.substring(0, Math.max(3, Math.min(4, w.length() - 1)));
            if (name.contains(stem)) return true;
        }
        return false;
    }

    public void giveItem(Player p, String what) {
        String[] words = Arrays.stream(what.toLowerCase(Locale.ROOT).split("[\\s,.!?]+"))
                .filter(w -> w.length() >= 3).toArray(String[]::new);
        int slot = -1;
        for (int i = 0; i < inventory.getContainerSize() && slot < 0; i++)
            if (!inventory.getItem(i).isEmpty() && matches(inventory.getItem(i), words)) slot = i;
        ItemStack give;
        if (slot >= 0) give = inventory.removeItemNoUpdate(slot);
        else if (!getMainHandItem().isEmpty() && matches(getMainHandItem(), words)) {
            give = getMainHandItem().copy(); setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else { say("У меня такого нет."); return; }
        say("Держи: " + give.getHoverName().getString());
        remember("отдал " + p.getName().getString() + " " + give.getHoverName().getString());
        if (!p.getInventory().add(give)) p.drop(give, false);
    }

    public void dropAll() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
        say("Вот, всё выкинул.");
    }

    public void shootAt(LivingEntity t) {
        ItemStack w = getMainHandItem();
        swing(InteractionHand.MAIN_HAND);
        AbstractArrow proj;
        boolean trident = w.getItem() instanceof TridentItem;
        if (trident) proj = new ThrownTrident(level(), this, w.copy());
        else {
            proj = ProjectileUtil.getMobArrow(this, new ItemStack(Items.ARROW), 1.0F);
            if (w.is(RealVillagers.LONGBOW) || w.is(RealVillagers.HEAVY_CROSSBOW)) proj.setBaseDamage(proj.getBaseDamage() + 2.0D);
        }
        proj.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        double dx = t.getX() - getX(), dy = t.getY(0.3333D) - proj.getY(), dz = t.getZ() - getZ();
        double h = Math.sqrt(dx * dx + dz * dz);
        proj.shoot(dx, dy + h * 0.2D, dz, 1.6F, 6.0F);
        playSound(trident ? SoundEvents.TRIDENT_THROW : SoundEvents.ARROW_SHOOT, 1.0F, 1.0F);
        level().addFreshEntity(proj);
        w.hurtAndBreak(1, this, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
    }

    // ================== ВЗАИМОДЕЙСТВИЕ ==================
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        getLookControl().setLookAt(player, 30, 30);
        if (player.isShiftKeyDown()) {
            if (getRelation(player) >= 20 || getFear(player) >= 50) {
                if (getRelation(player) < 20) { setEmotion(Emotion.SCARED, 200); playAnim(ANIM_HANDS_UP, 80); say("Б-бери что хочешь..."); }
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, inventory),
                        Component.literal("Вещи: " + baseName)));
            } else { setEmotion(Emotion.ANGRY, 100); playAnim(ANIM_POINT, 40); say("Не трогай мои вещи!"); changeRelation(player, -2); }
        } else if (!stack.isEmpty()) {
            ItemStack rest = inventory.addItem(stack.copy());
            if (rest.getCount() < stack.getCount()) {
                String name = stack.getHoverName().getString();
                int bonus = stack.is(Items.EMERALD) || stack.is(Items.DIAMOND) || stack.is(Items.GOLD_INGOT) ? 12 : stack.isEdible() ? 6 : 3;
                stack.setCount(rest.getCount());
                changeRelation(player, bonus);
                remember(player.getName().getString() + " подарил мне " + name);
                setEmotion(getRelation(player) >= 70 ? Emotion.LOVE : Emotion.HAPPY, 200);
                playAnim(ANIM_WAVE, 40);
                say(pick("Спасибо за " + name + "!", "Ого, " + name + "! Ты очень добр!", "Я этого не забуду!"));
                equipBestGear();
            } else say("Мне некуда это положить.");
        } else {
            playAnim(ANIM_WAVE, 40);
            say(getRelation(player) >= 30 ? "Привет, " + player.getName().getString() + "!" : pick("Здравствуй.", "Чего тебе?", "Хм?"));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean res = super.hurt(src, amount);
        if (res && !level().isClientSide && isAlive() && src.getEntity() instanceof Player p) {
            changeRelation(p, -15); changeFear(p, 10);
            remember(p.getName().getString() + " ударил меня");
            if ((hasWeapon() && bravery > 40) || isBrave()) { setEmotion(Emotion.ANGRY, 300); say(pick("Ах так?! Получай!", "Ты пожалеешь!")); }
            else { setEmotion(Emotion.SCARED, 300); say(pick("Ай! За что?!", "Не бей меня, пожалуйста!")); }
        }
        return res;
    }

    @Override
    public void die(DamageSource src) {
        if (!level().isClientSide) say("Прощайте...");
        super.die(src);
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
    }

    // ================== ДЕЙСТВИЯ (общие для чата и ИИ) ==================
    public LivingEntity findTarget(ServerPlayer commander, String m) {
        m = m.toLowerCase(Locale.ROOT);
        for (ServerPlayer o : commander.server.getPlayerList().getPlayers())
            if (o != commander && o.level() == level() && m.contains(o.getName().getString().toLowerCase(Locale.ROOT))) return o;
        for (RealVillager v : level().getEntitiesOfClass(RealVillager.class, getBoundingBox().inflate(24), e -> e != this))
            if (m.contains(v.getBaseName().toLowerCase(Locale.ROOT))) return v;
        return level().getNearestEntity(Monster.class, TargetingConditions.forCombat().range(24), this,
                getX(), getY(), getZ(), getBoundingBox().inflate(24));
    }

    public void doAction(ServerPlayer p, String a) {
        String[] parts = a.split(":", 2);
        String act = parts[0].trim().toUpperCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";
        switch (act) {
            case "FREE" -> { order = Order.NONE; setTarget(null); setCrouch(false); }
            case "FOLLOW" -> { order = Order.FOLLOW; orderPlayer = p.getUUID(); }
            case "STAY" -> order = Order.STAY;
            case "GUARD" -> { order = Order.GUARD; guardPos = blockPosition(); }
            case "ATTACK" -> {
                LivingEntity t = findTarget(p, arg);
                if (t == null) say("Я никого не вижу.");
                else if (t instanceof Player tp && getRelation(tp) >= 50) say("Нет! " + tp.getName().getString() + " мой друг!");
                else {
                    setTarget(t); order = Order.ATTACK; orderPlayer = p.getUUID(); playAnim(ANIM_POINT, 30);
                    remember("напал на " + t.getName().getString() + " по приказу " + p.getName().getString());
                }
            }
            case "ATTACK_SPEAKER" -> { setTarget(p); setEmotion(Emotion.ANGRY, 400); }
            case "COLLECT" -> order = Order.COLLECT;
            case "GIVE" -> giveItem(p, arg);
            case "DROP_ALL" -> dropAll();
            case "EQUIP" -> equipBestGear();
            case "DANCE" -> playAnim(ANIM_DANCE, 200);
            case "WAVE" -> playAnim(ANIM_WAVE, 60);
            case "HANDS_UP" -> playAnim(ANIM_HANDS_UP, 100);
            case "SAD" -> playAnim(ANIM_SAD, 100);
            case "CROUCH" -> setCrouch(true);
            case "STAND" -> setCrouch(false);
            case "JUMP" -> getJumpControl().jump();
            case "SET_HOME" -> homePos = blockPosition();
            case "GO_HOME" -> { if (homePos != null) order = Order.GO_HOME; else say("А где мой дом?"); }
            case "FLEE" -> { order = Order.FLEE; orderPlayer = p.getUUID(); fleeTimer = 120; }
            default -> {}
        }
    }

    private boolean obey(ServerPlayer p) {
        if (getRelation(p) >= 30) return true;
        if (getFear(p) >= 40 + bravery / 2) {
            setEmotion(Emotion.SCARED, 200);
            say(pick("Л-ладно, ладно...", "Хорошо, только не трогай меня!"));
            return true;
        }
        setEmotion(Emotion.ANGRY, 100);
        say(pick("С чего бы мне тебя слушать?", "Сначала подружись со мной.", "Нет."));
        changeRelation(p, -1);
        return false;
    }

    // ================== ЧАТ (без ИИ) ==================
    public void handleChat(ServerPlayer p, String raw) {
        String m = raw.toLowerCase(Locale.ROOT) + " ";
        String pn = p.getName().getString();
        getLookControl().setLookAt(p, 30, 30);

        // угрозы
        if (has(m, "убью", "угрожаю", "сдохни", "прибью", "зарежу", "уничтожу", "пожалеешь", "конец тебе")) {
            changeFear(p, 25); changeRelation(p, -10);
            remember(pn + " угрожал мне");
            if (hasWeapon() && bravery > 60 && getFear(p) < 70) {
                setEmotion(Emotion.ANGRY, 400); say(pick("Попробуй, если смелый!", "Ну давай, нападай!")); setTarget(p);
            } else if (bravery > 40 && getFear(p) < 50) {
                setEmotion(Emotion.ANGRY, 200); playAnim(ANIM_POINT, 40); say("Не смей мне угрожать!");
            } else {
                setEmotion(Emotion.SCARED, 300); playAnim(ANIM_HANDS_UP, 100);
                say(pick("Н-не надо! Я сделаю всё, что скажешь!", "Пощади!"));
            }
            return;
        }

        // приказы
        for (String[] cmd : COMMANDS) {
            for (int i = 1; i < cmd.length; i++) {
                if (!m.contains(cmd[i])) continue;
                if (!obey(p)) return;
                String act = cmd[0];
                String arg = "";
                if (act.equals("GIVE")) { int idx = m.indexOf(cmd[i]); arg = m.substring(idx + cmd[i].length()).replace("мне", ""); }
                if (act.equals("ATTACK")) arg = m.replace(baseName.toLowerCase(Locale.ROOT), "");
                if (getRelation(p) >= 30 && !act.equals("GIVE") && !act.equals("DROP_ALL"))
                    say(pick("Хорошо!", "Сделаю.", "Как скажешь, " + pn + "."));
                doAction(p, act + ":" + arg);
                return;
            }
        }

        // разговор
        if (has(m, "давай дружить", "будь моим другом", "ты мой друг", "люблю тебя", "ты классный", "ты хороший", "молодец", "спасибо")) {
            changeRelation(p, 6);
            setEmotion(getRelation(p) >= 70 ? Emotion.LOVE : Emotion.HAPPY, 200);
            say(getRelation(p) >= 30 ? "Ты мне тоже очень нравишься, " + pn + "!" : "Приятно слышать... может, и подружимся.");
            return;
        }
        if (has(m, "дурак", "тупой", "идиот", "урод", "ненавижу")) {
            changeRelation(p, -8);
            if (bravery > 50) { setEmotion(Emotion.ANGRY, 200); say("Сам такой!"); }
            else { setEmotion(Emotion.SAD, 200); playAnim(ANIM_SAD, 100); say("За что ты так со мной..."); }
            return;
        }
        if (has(m, "что у тебя", "инвентарь", "покажи вещи", "в карман")) { say("У меня: " + inventoryText()); return; }
        if (has(m, "помнишь", "память", "воспомина")) {
            if (memories.isEmpty()) say("Я ещё ничего не помню.");
            else say("Я помню: " + String.join("; ", memories.subList(Math.max(0, memories.size() - 5), memories.size())));
            return;
        }
        if (has(m, "кто я тебе", "мы друзья", "ты меня любишь", "отношени")) {
            say("Ты для меня " + relationWord(p) + "." + (getFear(p) > 40 ? " И я тебя боюсь." : "")); return;
        }
        if (has(m, "как дела", "как ты", "настроение")) { say("Мне " + emotion.text); return; }
        if (has(m, "кто ты", "как тебя зовут", "твое имя", "твоё имя")) { say("Я " + baseName + ". Живу тут."); return; }
        if (has(m, "привет", "здравствуй", "хай")) { playAnim(ANIM_WAVE, 40); say(getRelation(p) >= 30 ? "Привет, " + pn + "!" : "Ну, здравствуй."); return; }
        if (has(m, "пока", "до свидания")) { playAnim(ANIM_WAVE, 40); say("Пока!"); return; }

        if (getRelation(p) >= 30) say(pick("Интересно...", "Ага, понимаю.", "Расскажи ещё!"));
        else if (getRelation(p) <= -30) say(pick("Отстань.", "Не хочу с тобой говорить."));
        else say(pick("Хм.", "Не понимаю, о чём ты.", "Ладно..."));
    }

    // ================== ИИ ==================
    private String nearbyText() {
        List<String> l = new ArrayList<>();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16), e -> e != this && e.isAlive()))
            if (l.size() < 10) l.add(e.getName().getString());
        return l.isEmpty() ? "никого" : String.join(", ", l);
    }

    public String buildPrompt(Player p) {
        return "Ты житель Minecraft по имени " + baseName + ". Ты живой человек с характером, памятью, чувствами и телом, а не бот. "
                + "Храбрость: " + bravery + "/100. Эмоция сейчас: " + emotion.name() + ". Здоровье: " + (int) getHealth() + "/" + (int) getMaxHealth() + ".\n"
                + "С тобой говорит игрок " + p.getName().getString() + ". Отношения: " + getRelation(p) + " из [-100..100] (" + relationWord(p)
                + "), твой страх перед ним: " + getFear(p) + "/100.\n"
                + "Инвентарь: " + inventoryText() + ". Время: " + (level().isDay() ? "день" : "ночь") + ". Рядом: " + nearbyText() + ".\n"
                + "Воспоминания:\n" + String.join("\n", memories.subList(Math.max(0, memories.size() - 20), memories.size())) + "\n\n"
                + "ПРАВИЛА: отвечай по-русски, коротко (1-2 предложения), живо и в характере. Ты сам решаешь, выполнять ли просьбу: "
                + "друзей слушаешься охотно, тех, кого сильно боишься, слушаешься из страха, остальным можешь отказать. "
                + "На угрозы реагируй по храбрости: пугайся или давай отпор. "
                + "В конце ответа добавь теги: [EMOTION:NEUTRAL|HAPPY|SAD|ANGRY|SCARED|LOVE] [REL:+N или -N] [FEAR:+N или -N], "
                + "при необходимости [ACTION:...] и [MEMORY:короткий факт]. "
                + "Действия: FOLLOW, STAY, GUARD, ATTACK:<имя или пусто = ближайший монстр>, ATTACK_SPEAKER, COLLECT, GIVE:<предмет>, "
                + "DROP_ALL, EQUIP, DANCE, WAVE, HANDS_UP, SAD, CROUCH, STAND, JUMP, SET_HOME, GO_HOME, FLEE, FREE.";
    }

    public void applyAi(ServerPlayer p, String reply) {
        addHistory("assistant", reply);
        String text = TAG.matcher(reply).replaceAll("").trim();
        if (!text.isEmpty()) say(text);
        Matcher m = TAG.matcher(reply);
        while (m.find()) {
            String k = m.group(1), v = m.group(2).trim();
            try {
                switch (k) {
                    case "EMOTION" -> setEmotion(Emotion.valueOf(v.toUpperCase(Locale.ROOT)), 300);
                    case "REL" -> changeRelation(p, Integer.parseInt(v.replace("+", "")));
                    case "FEAR" -> changeFear(p, Integer.parseInt(v.replace("+", "")));
                    case "MEMORY" -> remember(v);
                    case "ACTION" -> doAction(p, v);
                }
            } catch (Exception ignored) {}
        }
    }

    // ================== СОХРАНЕНИЕ ==================
    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("BaseName", baseName);
        t.putInt("Bravery", bravery);
        t.put("Inv", inventory.createTag());
        ListTag mem = new ListTag();
        for (String s : memories) mem.add(StringTag.valueOf(s));
        t.put("Memories", mem);
        CompoundTag rel = new CompoundTag(); relations.forEach((k, v) -> rel.putInt(k.toString(), v)); t.put("Rel", rel);
        CompoundTag fear = new CompoundTag(); fears.forEach((k, v) -> fear.putInt(k.toString(), v)); t.put("Fear", fear);
        t.putString("Order", order.name());
        if (orderPlayer != null) t.putUUID("OrderPlayer", orderPlayer);
        if (homePos != null) t.putLong("Home", homePos.asLong());
        if (guardPos != null) t.putLong("GuardPos", guardPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        if (t.contains("BaseName")) baseName = t.getString("BaseName");
        if (t.contains("Bravery")) bravery = t.getInt("Bravery");
        inventory.fromTag(t.getList("Inv", Tag.TAG_COMPOUND));
        memories.clear();
        ListTag mem = t.getList("Memories", Tag.TAG_STRING);
        for (int i = 0; i < mem.size(); i++) memories.add(mem.getString(i));
        CompoundTag rel = t.getCompound("Rel");
        for (String k : rel.getAllKeys()) relations.put(UUID.fromString(k), rel.getInt(k));
        CompoundTag fear = t.getCompound("Fear");
        for (String k : fear.getAllKeys()) fears.put(UUID.fromString(k), fear.getInt(k));
        try { order = Order.valueOf(t.getString("Order")); } catch (Exception e) { order = Order.NONE; }
        if (order == Order.ATTACK) order = Order.NONE;
        if (t.hasUUID("OrderPlayer")) orderPlayer = t.getUUID("OrderPlayer");
        if (t.contains("Home")) homePos = BlockPos.of(t.getLong("Home"));
        if (t.contains("GuardPos")) guardPos = BlockPos.of(t.getLong("GuardPos"));
        updateName();
    }
}
