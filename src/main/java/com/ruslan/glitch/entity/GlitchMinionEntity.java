package com.ruslan.glitch.entity;

import com.ruslan.glitch.world.GlitchDirector;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Приспешник Глича: бродит и не трогает игрока, пока кого-то из них не ударят. Тогда нападают все. */
public class GlitchMinionEntity extends HostileEntity {
    private boolean angry = false;

    public GlitchMinionEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 0;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createMinionAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.15, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.7));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        // целей нет: цель появляется только когда нас спровоцировали
    }

    @Override
    public void tick() {
        super.tick();
        World w = this.getWorld();
        if (w.isClient) {
            if (this.random.nextInt(6) == 0) {
                w.addParticle(ParticleTypes.REVERSE_PORTAL, this.getParticleX(0.5), this.getRandomBodyY(),
                        this.getParticleZ(0.5), 0, 0, 0);
            }
            return;
        }
        if (!angry && this.getTarget() != null) this.setTarget(null);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean result = super.damage(source, amount);
        if (!this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity p) {
            provoke(p);
        }
        return result;
    }

    private void provoke(PlayerEntity p) {
        boolean first = !angry;
        World w = this.getWorld();
        Box box = this.getBoundingBox().expand(64);
        for (GlitchMinionEntity m : w.getEntitiesByClass(GlitchMinionEntity.class, box, e -> true)) {
            m.angry = true;
            m.setTarget(p);
        }
        this.angry = true;
        this.setTarget(p);
        if (first && w instanceof ServerWorld sw) {
            sw.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1.0f, 0.6f);
            GlitchDirector.say(sw.getServer(), "you shouldn't have done that");
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }
}
