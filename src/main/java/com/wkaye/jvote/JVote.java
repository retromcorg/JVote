package com.wkaye.jvote;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JVote extends JavaPlugin implements Listener {
    private static JVote plugin;
    private Logger log;
    private String pluginName;
    private PluginDescriptionFile pdf;
    private int debugLevel;
    private JVoteCommand voteCommand;

    @Override
    public void onEnable() {
        plugin = this;
        log = this.getServer().getLogger();
        JVoteConfig.getInstance(plugin);
        voteCommand = new JVoteCommand(plugin);
        Bukkit.getPluginManager().registerEvents(new VoteJoinListener(voteCommand, this), this);
        Bukkit.getPluginCommand("vote").setExecutor(voteCommand);
        pdf = this.getDescription();
        pluginName = pdf.getName();
        log.info("[" + pluginName + "] Is Loading, Version: " + pdf.getVersion());
        debugLevel = JVoteConfig.getInstance().getConfigInteger("settings.debug-level");
    }

    @Override
    public void onDisable() {
        System.out.println("Plugin has stopped");
    }

    public void logger(Level level, String message) {
        Bukkit.getLogger().log(level, "[" + pluginName + "] " + message);
    }

    public int getDebugLevel() {
        return debugLevel;
    }
}