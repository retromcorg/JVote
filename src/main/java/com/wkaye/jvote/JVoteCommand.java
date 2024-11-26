package com.wkaye.jvote;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

// TODO: implement string config and permissions config
public class JVoteCommand implements CommandExecutor {
    private final JVote plugin;
    // need thread safety for this variable so that two players cant start a vote at the same time
    AtomicBoolean voteStarted;
    AtomicBoolean isVoteTimePassed;
    AtomicInteger countdownTaskId;

    // enum: task ID mapping for cancelling task
    ConcurrentHashMap<JVoteEnums, Integer> isOnCooldown;
    AtomicInteger totalVotes;
    HashMap<String, Player> playerHasVoted;
    JVoteEnums currentVoteType;

    public JVoteCommand(JVote plugin) {
        System.out.println("plugin instance created");
        this.plugin = plugin;
        voteStarted = new AtomicBoolean(false);
        isVoteTimePassed = new AtomicBoolean(false);
        totalVotes = new AtomicInteger(0);
        playerHasVoted = new HashMap<>();
        isOnCooldown = new ConcurrentHashMap<>();
        countdownTaskId = new AtomicInteger();
    }


    /*
     This command should have two stages: one where the voting commences and another where people vote yes/no
     Requirements:
         Starting a vote automatically votes yes for that player
         A player can only vote once
         If a cutoff is passed (let's say 50% of online players), then vote should pass
         Either one no vote will kill a vote or it should subtract one (-1) from total vote count
         End vote after a timer (1 minute will be baseline)
      */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // there should be two types of votes: one where the vote is initiated, and one where the vote is yes/no
        if (args.length != 1) {
            String msg = JVoteUtils.printMessage("Proper usage is &a/vote <day/night/storm/clear>");
            sender.sendMessage(msg);
            // invalid usage (should be /vote {type of vote}
            plugin.logger(Level.WARNING, "Attempted /vote with invalid number of args");
            return true;
        }
        if (!(sender instanceof Player)) {
            // command not available to console message
            plugin.logger(Level.WARNING, "Attempted /vote from console");
            return true;
        }
        Player player = (Player) sender;
        if (voteStarted.get()) {
            // vote started, check that the user actually supplied a yes or no vote
            if (!("yes".contains(args[0].toLowerCase()) || "no".contains(args[0].toLowerCase()))) {
                // invalid usage, return false
                sender.sendMessage(JVoteUtils.printMessage("A vote is already in progress"));
                plugin.logger(Level.WARNING, "Attempted /vote after vote started with improper args");
                return true;
            }
            if (checkVote(args[0], player)) {
                // vote passed, perform change and reset values
                doVote();
                return true;
            }

        }
        if (!voteStarted.get()) {
            try {
                // this line to trigger exception if not valid
                JVoteEnums.valueOf(args[0].toUpperCase());
                if (isOnCooldown.containsKey(JVoteEnums.valueOf(args[0].toUpperCase()))) {
                    // this vote is on a cool down still
                    sender.sendMessage(JVoteUtils.printMessage("This vote is on cool down"));
                    return true;
                }
                // TODO: fetch these from some config file. For now, only day/night and clear/storm
                // invalid usage, return false
                currentVoteType = JVoteEnums.valueOf(args[0].toUpperCase());
                String msg = JVoteUtils.printMessage(
                        "A vote for "
                                + JVoteUtils.formatColor(currentVoteType.color())
                                + JVoteUtils.formatColor(currentVoteType.toString().toLowerCase())
                                + JVoteUtils.formatColor("&7 has started. Vote by doing &a/vote <yes/no>"));
                plugin.getServer().broadcastMessage(msg);
                if (checkVote("yes", player)) {
                    doVote();
                } else {
                    doVoteTimer();
                }

            } catch (IllegalArgumentException e) {
                String msg = JVoteUtils.printMessage("Proper usage is &a/vote <day/night/storm/clear>");
                sender.sendMessage(msg);
                plugin.logger(Level.WARNING, "Attempted to start /vote with improper argument");
                return true;
            }
        }


        return true;
    }

    private boolean checkVote(String arg, CommandSender sender) {
        Player player = (Player) sender;
        double currentVotePercentage = 0;
        String hostname = player.getAddress().getHostName();
        // check if a player has voted or if someone from same IP has voted (fixing vote cooldown bypassing)
        if (playerHasVoted.containsKey(hostname)) {
            if (playerHasVoted.get(hostname) == null) {
                /*
                this would be saying that the IP has been logged as a vote being logged but no list of players has
                been created??

                idk if its even possible for this case, someone feel free to correct this if it is. for now just
                continuing
                */
            } else {
                if (!playerHasVoted.get(hostname).equals(player)) {
                    // another player is logged onto the same IP. probably using an alt. for now, restricting one vote
                    // per ip
                    plugin.logger(Level.WARNING, player.getName() + " has tried to vote from an IP that has " +
                            "already logged a vote");
                }
                // player has voted, send message that he/she already voted and return
                String msg = JVoteUtils.printMessage("You have already voted");
                sender.sendMessage(msg);
                return false;
            }

        }
        // player has not yet voted
        sender.sendMessage(JVoteUtils.printMessage("You have voted"));
        playerHasVoted.put(player.getAddress().getHostName(), player);
        
        if ("yes".contains(arg.toLowerCase())) {
            currentVotePercentage = (double) totalVotes.incrementAndGet()
                    / Bukkit.getServer().getOnlinePlayers().length;
        } else if ("no".contains(arg.toLowerCase())) {
            currentVotePercentage = (double) totalVotes.decrementAndGet()
                    / Bukkit.getServer().getOnlinePlayers().length;
        }
        if (plugin.getDebugLevel() > 0) {
            plugin.logger(Level.INFO, "voting percentage at: " + currentVotePercentage);
        }
        return currentVotePercentage > 0.5;
    }

    private boolean checkVote() {
        double currentVotePercentage = (double) totalVotes.get()
                / Bukkit.getServer().getOnlinePlayers().length;
        if (plugin.getDebugLevel() > 0) {
            plugin.logger(Level.INFO, "voting percentage at: " + currentVotePercentage);
        }
        return currentVotePercentage > 0.5;
    }

    private void doVote() {
        if (currentVoteType == null) {
            plugin.logger(Level.SEVERE, "Unexpected error when getting vote type");
        } else {
            switch (currentVoteType) {
                case DAY:
                    for (World world : Bukkit.getWorlds()) {
                        world.setTime(0);
                    }
                    break;
                case NIGHT:
                    for (World world : Bukkit.getWorlds()) {
                        world.setTime(14000);
                    }
                    break;
                case SUN:
                case CLEAR:
                    for (World world : Bukkit.getWorlds()) {
                        if (world.hasStorm()) {
                            world.setThundering(false);
                            world.setWeatherDuration(5);
                        }
                    }
                    break;
                case STORM:
                    for (World world : Bukkit.getWorlds()) {
                        if (!world.hasStorm()) {
                            world.setWeatherDuration(5);
                        }
                    }
                    break;
                default:
                    plugin.logger(Level.WARNING, "Not implemented yet");
            }
            plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Vote passed"));
            Bukkit.getScheduler().cancelTask(countdownTaskId.get());
            resetValues(currentVoteType);
        }
    }

    private void resetValues(JVoteEnums cmd) {
        plugin.logger(Level.INFO, "Resetting values after vote ended and adding cool down");
        voteStarted.set(false);
        totalVotes.set(0);
        isVoteTimePassed.set(false);
        currentVoteType = null;
        playerHasVoted.clear();
        int cooldownTimer = JVoteConfig.getInstance().getConfigInteger("settings.cooldown-timer-ticks");
        if (cmd.equals(JVoteEnums.CLEAR) || cmd.equals(JVoteEnums.SUN)) {
            isOnCooldown.putIfAbsent(JVoteEnums.SUN, 0);
            isOnCooldown.putIfAbsent(JVoteEnums.CLEAR, 0);
            Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, () -> {
                        isOnCooldown.remove(JVoteEnums.CLEAR);
                        isOnCooldown.remove(JVoteEnums.SUN);
                    }, cooldownTimer
            );
        } else {
            isOnCooldown.putIfAbsent(cmd, 0);
            Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, () -> isOnCooldown.remove(cmd),
                    cooldownTimer);
        }
    }

    // this function will handle the timer and check that the vote has passed at 1s intervals
    @SuppressWarnings("unchecked")
    private void doVoteTimer() {
        if (!voteStarted.get()) {
            AtomicInteger count = new AtomicInteger(JVoteConfig.getInstance().getConfigInteger("settings.timer-length"));
            voteStarted.set(true);
            countdownTaskId.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
                int curr = count.getAndDecrement();
                if (curr == 0) {
                    if (totalVotes.get() > 0) {
                        doVote();
                    } else {
                        plugin.getServer().broadcastMessage(JVoteUtils.printMessage("Voting has ended"));
                        Bukkit.getScheduler().cancelTask(countdownTaskId.get());
                        resetValues(currentVoteType);
                    }
                } else {
                    ArrayList<Integer> frequencies = (ArrayList<Integer>)
                            JVoteConfig.getInstance().getConfigOption("settings.reminder-frequency");
                    if (JVoteConfig.getInstance().getConfigBoolean("settings.toggle-timer") &&
                            frequencies.contains(curr)) {
                        plugin.getServer().broadcastMessage(JVoteUtils.printMessage(curr + " seconds remaining"));
                    }
                }
                if (checkVote()) {
                    doVote();
                    Bukkit.getScheduler().cancelTask(countdownTaskId.get());
                }
            }, 20, 20));
            plugin.logger(Level.INFO, "Scheduled task with ID: " + countdownTaskId.get());
        } else {
            plugin.logger(Level.INFO, "Vote already in progress");
        }
    }
}
