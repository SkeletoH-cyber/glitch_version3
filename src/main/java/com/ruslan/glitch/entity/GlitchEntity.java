package com.ruslan.glitch.entity;

import com.ruslan.glitch.network.ScarePayload;
import com.ruslan.glitch.world.GlitchDirector;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Glitch: бессмертная сущность. Убить нельзя, изгнать можно только ритуалом.
 * Режимы: HUNT (преследует и бьёт), STRIKE (один удар и исчезает), WATCH (наблюдает издалека).
 * Если игрок погиб, Глич пропадает.
 */
public class GlitchEntity extends HostileEntity {
    public enum Mode { HUNT, STRIKE, WATCH }

    private static final String[] HUNT_LINES = {"it's me", "i can see you", "i found you", "hi", "i'm right here"};
    private static final String[] WATCH_LINES = {"i'm watching you", "i can see you", "don't look away", "you can't hide"};

    public int lifeTicks = 12000;
    private Mode mode = Mode.HUNT;
    private int hopCooldown = 60;
    private int speakCooldown = 100;

    public GlitchEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createGlitchAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 200.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("GlitchLife", lifeTicks);
        nbt.putInt("GlitchMode", mode.ordinal());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("GlitchLife")) lifeTicks = nbt.getInt("GlitchLife");
        if (nbt.contains("GlitchMode")) {
            int i = nbt.getInt("GlitchMode");
            if (i >= 0 && i < Mode.values().length) setMode(Mode.values()[i]);
        }
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode m) {
        this.mode = m;
        if (m == Mode.WATCH) {
            this.setAiDisabled(true);
        }
        if (m == Mode.STRIKE) {
            EntityAttributeInstance speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
            if (speed != null) speed.setBaseValue(0.42);
        }
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            if (this.random.nextInt(3) == 0) {
                w.addParticle(ParticleTypes.REVERSE_PORTAL, this.getParticleX(0.5), this.getRandomBodyY(),
                        this.getParticleZ(0.5), 0, 0, 0);
            }
            return;
        }
        ServerWorld sw = (ServerWorld) w;
        if (--lifeTicks <= 0 || this.getY() < sw.getBottomY() - 10) {
            vanish(sw);
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && !target.isAlive()) { // убил игрока: исчезает
            vanish(sw);
            return;
        }
        if (mode == Mode.WATCH) {
            watchTick(sw);
            return;
        }
        if (--hopCooldown <= 0) {
            hopCooldown = 50 + this.random.nextInt(100);
            glitchHop(sw);
        }
        if (--speakCooldown <= 0) {
            speakCooldown = 160 + this.random.nextInt(240);
            if (target != null && this.squaredDistanceTo(target) < 900) {
                GlitchDirector.say(sw.getServer(), HUNT_LINES[this.random.nextInt(HUNT_LINES.length)]);
            }
        }
    }

    /** WATCH: стоит на месте, смотрит на ближайшего игрока и растворяется, если к нему подойти. */
    private void watchTick(ServerWorld sw) {
        PlayerEntity pl = sw.getClosestPlayer(this, 80);
        if (pl == null) return;
        this.lookAtEntity(pl, 360f, 360f);
        this.setBodyYaw(this.getYaw());
        this.setHeadYaw(this.getYaw());
        if (this.squaredDistanceTo(pl) < 36) {
            sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.3f);
            vanish(sw);
            return;
        }
        if (--speakCooldown <= 0) {
            speakCooldown = 300 + this.random.nextInt(400);
            GlitchDirector.say(sw.getServer(), WATCH_LINES[this.random.nextInt(WATCH_LINES.length)]);
        }
    }

    /** "Глич-прыжок": мгновенно оказывается рядом с целью. */
    private void glitchHop(ServerWorld sw) {
        LivingEntity target = this.getTarget();
        if (target == null || this.squaredDistanceTo(target) < 25) return;
        BlockPos spot = GlitchDirector.findSpotAround(sw, target.getBlockPos(), 3, 6);
        if (spot == null) return;
        GlitchDirector.puff(sw, this.getX(), this.getY(), this.getZ());
        this.getNavigation().stop();
        this.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, this.getYaw(), 0f);
        this.lookAtEntity(target, 360f, 360f);
        sw.playSound(null, spot, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.4f);
        GlitchDirector.puff(sw, this.getX(), this.getY(), this.getZ());
    }

    private void vanish(ServerWorld sw) {
        GlitchDirector.puff(sw, this.getX(), this.getY(), this.getZ());
        this.discard();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // /kill админа работает, всё остальное бессильно: Глич лишь "дёргается" и перескакивает
        if (source.isOf(DamageTypes.GENERIC_KILL)) return super.damage(source, amount);
        if (!this.getWorld().isClient && mode != Mode.WATCH && this.random.nextInt(3) == 0) {
            glitchHop((ServerWorld) this.getWorld());
        }
        return false;
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean ok = super.tryAttack(target);
        if (ok && target instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, new ScarePayload(14, 1));
            sp.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
            if (mode == Mode.STRIKE) { // один удар и исчезает
                this.lifeTicks = Math.min(this.lifeTicks, 8);
                this.getWorld().playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM,
                        SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
        }
        return ok;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return mode == Mode.WATCH ? null : SoundEvents.ENTITY_ENDERMAN_STARE;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_SCREAM;
    }
}
