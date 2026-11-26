package com.ruslan.glitch.item;

import com.ruslan.glitch.world.GlitchDirector;
import com.ruslan.glitch.world.GlitchState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Reality Anchor.
 * Right-click the center (crying obsidian) of a cross to set the anchor.
 * Right-click in the air to see which way the nearest cross is.
 */
public class RealityAnchorItem extends Item {
    public RealityAnchorItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        boolean crying = world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN);
        if (!(world instanceof ServerWorld sw)) {
            return crying ? ActionResult.SUCCESS : ActionResult.PASS;
        }
        if (!crying) return ActionResult.PASS;

        PlayerEntity player = ctx.getPlayer();
        GlitchState st = GlitchState.get(sw.getServer());
        if (st.banished || st.destroyed || !st.removeCross(pos)) {
            if (player != null) player.sendMessage(Text.literal("§7The anchor is silent. Wrong cross."), true);
            return ActionResult.SUCCESS;
        }

        GlitchDirector.eraseCross(sw, pos);
        st.ritualProgress++;
        st.markDirty();
        sw.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.5f, 0.6f);
        sw.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 60, 0.6, 1.2, 0.6, 0.05);
        if (player != null) {
            player.sendMessage(Text.literal("§5The anchor settles. §7(" + st.ritualProgress + "/" + GlitchState.ANCHORS_NEEDED + ")"), true);
            if (!player.isCreative()) ctx.getStack().decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw)) return TypedActionResult.success(stack);

        GlitchState st = GlitchState.get(sw.getServer());
        BlockPos near = st.nearestCross(user.getBlockPos());
        if (near == null) {
            user.sendMessage(Text.literal("§7The anchor is silent."), true);
        } else {
            int dx = near.getX() - user.getBlockX();
            int dz = near.getZ() - user.getBlockZ();
            String dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            user.sendMessage(Text.literal("§5The anchor trembles: a cross lies to the " + dir + ", about " + dist + " blocks away"), true);
        }
        user.getItemCooldownManager().set(this, 40);
        return TypedActionResult.success(stack);
    }
}
