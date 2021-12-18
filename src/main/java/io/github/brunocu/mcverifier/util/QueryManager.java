package io.github.brunocu.mcverifier.util;

import io.github.brunocu.mcverifier.MCVerifier;
import io.github.brunocu.mcverifier.util.colors.Discord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.javatuples.Triplet;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class QueryManager implements CommandExecutor {
    private final Logger logger = MCVerifier.getPluginLogger();
    private final GroupManager groupManager = MCVerifier.getGroupManager();
    private final int timeout;
    // unlikely to have more than one verification request at a time
    // but async programming is fun ¯\_(ツ)_/¯
    private final ScheduledExecutorService scheduler;
    private final ConcurrentMap<Player, Triplet<Member, InteractionHook, Future<?>>> standingQueries = new ConcurrentHashMap<>(5);

    private class RequestTimeout implements Runnable {
        private final Player player;

        public RequestTimeout(Player player) {
            this.player = player;
        }

        public void run() {
            InteractionHook messageHook = standingQueries.get(player).getValue1();
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Discord.RED)
                    .setDescription("Verification request timed out");
            messageHook.editOriginalEmbeds(eb.build()).queue();
            final TextComponent timeoutMsg = Component.text("Request timed out!", NamedTextColor.YELLOW);
            player.sendMessage(timeoutMsg);
            standingQueries.remove(player);
        }
    }

    public QueryManager(int timeout) {
        this.timeout = timeout;
        if (timeout > 0)
            scheduler = Executors.newSingleThreadScheduledExecutor(); // is single thread ok?
        else
            scheduler = null;
    }

    public void startVerify(Player player, Member member, InteractionHook messageHook) {
        if (standingQueries.containsKey(player)) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Discord.RED)
                    .setDescription("**" + player.getName() + "** has an active verification request.\n" +
                            "Please complete or cancel it before sending a new one.");
            messageHook.sendMessageEmbeds(eb.build()).queue();
            return;
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Verification started...")
                .setThumbnail(member.getEffectiveAvatarUrl())
                .setColor(Discord.BLURPLE)
                .setDescription("Sending verification request to minecraft player: **" +
                        player.getName() + "**");
        if (timeout > 0) {
            eb.appendDescription("\nTimeout: " + timeout + " seconds.");
        }
        messageHook.sendMessageEmbeds(eb.build()).queue();
        Future<?> future = null;
        if (scheduler != null) {
            future = scheduler.schedule(new RequestTimeout(player), timeout, TimeUnit.SECONDS);
        }
        standingQueries.put(player, Triplet.with(member, messageHook, future));
        // build message
        final TextComponent notifyMsg = Component.text(member.getUser().getAsTag(), NamedTextColor.BLUE)
                .append(Component.text(" sent you a verification request", NamedTextColor.YELLOW));
        final TextComponent acceptClk = Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept", NamedTextColor.GRAY, TextDecoration.ITALIC)))
                .clickEvent(ClickEvent.runCommand("/verify accept"));
        final TextComponent declineClk = Component.text("[DECLINE]", NamedTextColor.RED, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("Click to decline", NamedTextColor.GRAY, TextDecoration.ITALIC)))
                .clickEvent(ClickEvent.runCommand("/verify decline"));
        player.sendMessage(notifyMsg);
        player.sendMessage(Component.join(JoinConfiguration.noSeparators(), acceptClk, declineClk));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Not a console command go away");
            return true;
        }
        Player player = (Player) sender;
        // check correct arguments
        if (args.length != 1) {
            return false;
        }

        TextComponent reply;
        if (!standingQueries.containsKey(player)) {
            reply = Component.text("You have no active verification requests.", NamedTextColor.RED);
            sender.sendMessage(reply);
            return true;
        }
        String response = args[0];
        if (response.equals("accept") || response.equals("decline")) {
            Triplet<Member, InteractionHook, Future<?>> mapTriplet = standingQueries.remove(player);
            EmbedBuilder eb;
            if (mapTriplet.getValue2() != null && !mapTriplet.getValue2().cancel(false)) {
                // edge case: player sends command while future is running
                // future couldn't be cancelled
                reply = Component.text("Request timed out!", NamedTextColor.RED);
                sender.sendMessage(reply);
                return true;
            }
            switch (response) {
                case "accept":
                    // begin linking
                    try {
                        groupManager.verifyUser(player, mapTriplet.getValue0());
                    } catch (SQLException e) {
                        logger.warning(e.toString());
                        // TODO make embed methods in SlashCommandsListener static
                        eb = new EmbedBuilder()
                                .setColor(Discord.YELLOW)
                                .setTitle("SQL Error")
                                .setDescription(e.toString() +
                                        "\nPlease notify an administrator.");
                        mapTriplet.getValue1().editOriginalEmbeds(eb.build()).queue();
                        reply = Component.text("There was an error while verifying! Please contact a moderator",
                                NamedTextColor.RED);
                        sender.sendMessage(reply);
                        return true;
                    }
                    // send success card
                    eb = new EmbedBuilder()
                            .setColor(Discord.GREEN)
                            .setThumbnail(mapTriplet.getValue0().getEffectiveAvatarUrl())
                            .setTitle("Verify Successful!")
                            .setDescription("Verified account: **" + player.getName() + "**");
                    mapTriplet.getValue1().editOriginalEmbeds(eb.build()).queue();
                    reply = Component.text("Account Verified! :)", NamedTextColor.YELLOW);
                    sender.sendMessage(reply);
                    return true;
                case "decline":
                    // :C
                    eb = new EmbedBuilder()
                            .setColor(Discord.RED)
                            .setTitle("Verify declined")
                            .setThumbnail(mapTriplet.getValue0().getEffectiveAvatarUrl())
                            .setDescription("**" + player.getName() + "** " +
                                    "declined your verify request \uD83D\uDE14");
                    mapTriplet.getValue1().editOriginalEmbeds(eb.build()).queue();
                    reply = Component.text("Request declined", NamedTextColor.YELLOW);
                    sender.sendMessage(reply);
                    return true;
            }
        }
        return false;
    }
}
