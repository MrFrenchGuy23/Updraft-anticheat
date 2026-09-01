package com.updraft.anticheat.checks.movement;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;
import org.bukkit.entity.Player;

/**
 * Step: flags players who ascend more than the configurable {@code max-step}
 * height (default 0.6 = slab) in a single tick without jumping.
 * <p>
 * A vanilla step (without jump) is at most 0.6 blocks. Values above that
 * without the player being in the air from a jump indicate a step cheat.
 */
public final class Step extends Check {

    public Step(UpdraftAC plugin) { super(plugin, CheckType.STEP); }

    @Override
    public void onTick(PlayerData data) {
        if (!data.onGround() || !data.lastOnGround()) return; // only ground-to-ground

        double dy = data.dy();
        if (dy <= 0) return; // only upward steps matter

        double maxStep = plugin.config().checks().getDouble("movement.step.max-step", 0.6);
        if (dy > maxStep) {
            fail(data, String.format("step=%.3f max=%.3f", dy, maxStep));
        }
    }
}
