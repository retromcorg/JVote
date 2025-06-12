package com.wkaye.jvote;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class VoteJoinListener implements Listener {

    private final JVoteCommand voteCommand;

    public VoteJoinListener(JVoteCommand voteCommand) {
        this.voteCommand = voteCommand;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (voteCommand.isVoteActive()) {
            player.sendMessage(JVoteUtils.printMessage("§aA vote for "
                    + JVoteUtils.formatColor(voteCommand.getCurrentVoteType().color())
                    + JVoteUtils.formatColor(voteCommand.getCurrentVoteType().toString().toLowerCase())
                    + " §7is currently active!"));
            player.sendMessage(JVoteUtils.printMessage("Type §a/vote yes§7 or §a/vote no§7 to participate."));
        }
    }
}
