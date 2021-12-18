package io.github.brunocu.mcverifier.discord;

import io.github.brunocu.mcverifier.MCVerifier;
import io.github.brunocu.mcverifier.database.DataPipe;
import io.github.brunocu.mcverifier.util.GroupManager;
import io.github.brunocu.mcverifier.util.QueryManager;
import io.github.brunocu.mcverifier.util.colors.Discord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.logging.Logger;

public class SlashCommandsListener extends ListenerAdapter {
    private final Logger logger = MCVerifier.getPluginLogger();
    private final DataPipe data = MCVerifier.getData();
    private final GroupManager groupManager = MCVerifier.getGroupManager();
    private final QueryManager queryManager = MCVerifier.getQueryManager();

    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // send thinking message
        event.deferReply().queue();
        logger.info(event.getUser().getAsTag() + " called discord command: " + event.getName());
        // switch for appropriate handler
        switch (event.getName()) {
            case "verify":
                onVerify(event);
                break;
            case "unlink":
                onUnlink(event);
                break;
            case "info":
                onInfo(event);
                break;
        }
    }

    public void onVerify(SlashCommandEvent event) {
        // Check discord account not linked already
        Member eventMember = event.getMember();
        String caller_id = eventMember.getId();
        try {
            if (data.memberIsIn(caller_id)) {
                sendUserError(event.getHook(), "You are already linked to a minecraft account.\n" +
                        "Please use `/unlink` first if you wish to verify a different account.");
                return;
            }
        } catch (SQLException e) {
            sendSQLError(event.getHook(), e.getMessage());
            return;
        }
        // get mc username
        String username = event.getOption("username").getAsString();
        // query for player
        Player player = Bukkit.getPlayer(username);
        if (player == null) {
            // player not found
            sendUserError(event.getHook(), "Player not found.\nRemember you have to be connected and `username` is case-sensitive.");
            return;
        }
        String uuid = player.getUniqueId().toString();
        // check for user in db
        try {
            if (data.uuidIsIn(uuid)) {
                sendUserError(event.getHook(), "This player is already linked to a discord account.");
                return;
            }
        } catch (SQLException e) {
            sendSQLError(event.getHook(), e.getMessage());
            return;
        }
        // everything in order, start linking
        queryManager.startVerify(player, eventMember, event.getHook());
    }

    public void onUnlink(SlashCommandEvent event) {
        Member unlinkMember;
        String mcUsername;
        // if using option `user`
        // check for MANAGE_ROLES permission
        if (event.getOptions().size() > 0) {
            if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
                // permission deny
                sendUserError(event.getHook(), "You don't have permission to unlink another user's account.");
                return;
            }
            // get member to unlink
            unlinkMember = event.getOption("user").getAsMember();
        } else {
            // unlink event caller
            unlinkMember = event.getMember();
        }
        String memberId = unlinkMember.getId();
        try {
            // check if verified
            if (!data.memberIsIn(memberId)) {
                sendUserError(event.getHook(), "<@!" + memberId + "> is not verified");
                return;
            }
            logger.info("Unlinking discord user: " + unlinkMember.getUser().getAsTag());
            mcUsername = groupManager.unlinkMember(unlinkMember);
        } catch (SQLException e) {
            logger.warning(e.toString());
            sendSQLError(event.getHook(), e.toString());
            return;
        }
        logger.info("Unlink successful");
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Discord.GREEN)
                .setThumbnail(unlinkMember.getEffectiveAvatarUrl())
                .setTitle("Unlink Successful!")
                .setDescription("Unlinked <@!" + memberId + "> " +
                        "from associated minecraft account: **" + mcUsername + "**");
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    public void onInfo(SlashCommandEvent event) {
        Member infoMember;
        // if using option `user`
        // check for MANAGE_ROLES permission
        if (event.getOptions().size() > 0) {
            if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
                // permission deny
                sendUserError(event.getHook(), "You don't have permission to lookup another user's account.");
                return;
            }
            infoMember = event.getOption("user").getAsMember();
        } else {
            infoMember = event.getMember();
        }
        String memberId = infoMember.getId();
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Discord.BLURPLE)
                .setThumbnail(infoMember.getEffectiveAvatarUrl())
                .setTitle("Verification info");
        try {
            if (!data.memberIsIn(memberId)) {
                eb.setDescription("No associated minecraft account for <@!" + memberId + ">");
                return;
            }
            eb.setDescription("<@!" + memberId + "> " +
                    "associated minecraft account: **" +
                    data.getUsernameFromMemberId(memberId) + "**\n" +
                    "Verified on <t:" + data.getTimeStamp(memberId) + ">");
        } catch (SQLException e) {
            logger.warning(e.toString());
            sendSQLError(event.getHook(), e.toString());
            return;
        }
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    public static void sendUserError(InteractionHook webhook, String msg) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Discord.RED)
                .setDescription(msg);
        webhook.sendMessageEmbeds(eb.build()).queue();
    }

    public void sendSQLError(InteractionHook webhook, String msg) {
        logger.warning("SQL ERROR: " + msg);
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Discord.YELLOW)
                .setTitle("SQL ERROR")
                .setDescription(msg + "\nPlease notify an administrator.");
        webhook.sendMessageEmbeds(eb.build()).queue();
    }
}
