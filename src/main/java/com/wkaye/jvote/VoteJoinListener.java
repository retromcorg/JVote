package com.wkaye.jvote;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public class VoteJoinListener implements Listener {

    private final JVoteCommand voteCommand;
    private final Plugin plugin;

    public VoteJoinListener(JVoteCommand voteCommand, Plugin plugin) {
        this.voteCommand = voteCommand;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (voteCommand.isVoteActive()) {
                    player.sendMessage(JVoteUtils.printMessage("§aA vote for "
                            + JVoteUtils.formatColor(voteCommand.getCurrentVoteType().color())
                            + JVoteUtils.formatColor(voteCommand.getCurrentVoteType().toString().toLowerCase())
                            + "§a is currently active!"));
                    player.sendMessage(JVoteUtils.printMessage("Type §b/vote yes§a or §b/vote no§a to participate."));
                }
            }
        }, 1L);
    }
}