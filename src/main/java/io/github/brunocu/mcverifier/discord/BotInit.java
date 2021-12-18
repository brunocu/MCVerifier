package io.github.brunocu.mcverifier.discord;

import io.github.brunocu.mcverifier.MCVerifier;
import io.github.brunocu.mcverifier.util.GroupManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

import java.util.logging.Logger;

public class BotInit extends ListenerAdapter {
    private final Logger logger = MCVerifier.getPluginLogger();
    private final GroupManager groupManager = MCVerifier.getGroupManager();

    @Override
    public void onReady(ReadyEvent event) {
        // bot can only be connected to single server
        if (event.getGuildTotalCount() != 1) {
            logger.warning("Bot has to belong to EXACTLY one guild");
            // only singleton call
            // how do I get rid of it?
            // observer?
            MCVerifier.prematureDisable();
            return;
        }
        logger.info("Updating guild commands");
        Guild guild = event.getJDA().getGuilds().get(0);
        // Pass guild to GroupManager
        groupManager.setGuild(guild);
        // update commands on server
        CommandListUpdateAction commands = guild.updateCommands();

        // build commands
        // /verify <username>
        CommandData verifyCmd = new CommandData("verify", "verify your minecraft account")
                .addOption(OptionType.STRING, "username", "your minecraft username", true);
        // /unlink [<user>]
        CommandData unlinkCmd = new CommandData("unlink", "unlink your minecraft account")
                .addOption(OptionType.USER, "user", "discord user, optional");
        // /info [<user>]
        CommandData infoCmd = new CommandData("info", "show verification info")
                .addOption(OptionType.USER, "user", "discord user, optional");

        // add commands
        commands.addCommands(verifyCmd, unlinkCmd, infoCmd).complete();
        // add command listener
        event.getJDA().addEventListener(new SlashCommandsListener());
        logger.info("Commands updated");
    }
}
