package com.ruslan.glitch.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.Identifier;

import java.util.Random;

/** Рендер человекоподобной сущности с глитч-скином (используется и для Глича, и для приспешников). */
public class GlitchRenderer<T extends MobEntity> extends BipedEntityRenderer<T, BipedEntityModel<T>> {
    private final Identifier texture;
    private final int jitterChance;

    public GlitchRenderer(EntityRendererFactory.Context ctx, Identifier texture, int jitterChance) {
        super(ctx, new BipedEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER)), 0.5f);
        this.texture = texture;
        this.jitterChance = jitterChance;
    }

    @Override
    public void render(T entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        // Глич-дрожание: иногда модель на мгновение сдвигается и "ломается"
        Random r = new Random(entity.age / 2L * 31L + entity.getId());
        if (r.nextInt(100) < jitterChance) {
            matrices.translate((r.nextDouble() - 0.5) * 0.25, 0, (r.nextDouble() - 0.5) * 0.25);
            matrices.scale(1.0f + (r.nextFloat() - 0.5f) * 0.3f, 1.0f + r.nextFloat() * 0.25f, 1.0f);
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(T entity) {
        return texture;
    }
}
