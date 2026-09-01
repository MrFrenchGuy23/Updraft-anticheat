package com.updraft.anticheat.checks.client;

import com.updraft.anticheat.UpdraftAC;
import com.updraft.anticheat.checks.api.Check;
import com.updraft.anticheat.checks.api.CheckType;
import com.updraft.anticheat.data.PlayerData;

/**
 * CommandCheck: flags players who execute commands matching the
 * {@code client.command.flagged-commands} list in {@code checks.yml}.
 * <p>
 * Detection is driven by {@link com.updraft.anticheat.command.CommandLogger},
 * which hooks {@code PlayerCommandPreprocessEvent} and also persists every
 * command to storage. This check only performs the VL/alerts/punishment side
 * of the pipeline via {@link #flag(PlayerData, String)}.
 */
public final class CommandCheck extends Check {

    public CommandCheck(UpdraftAC plugin) { super(plugin, CheckType.COMMAND); }

    /** Called by the command logger when a flagged command is executed. */
    public void flag(PlayerData data, String command) {
        fail(data, "cmd=" + command);
    }
}
