package io.github.brunocu.mcverifier;

import io.github.brunocu.mcverifier.database.DataPipe;
import io.github.brunocu.mcverifier.database.MysqlDataPipe;
import io.github.brunocu.mcverifier.database.SqliteDataPipe;
import io.github.brunocu.mcverifier.discord.BotInit;
import io.github.brunocu.mcverifier.util.ConfigManager;
import io.github.brunocu.mcverifier.util.GroupManager;
import io.github.brunocu.mcverifier.util.QueryManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

public class MCVerifier extends JavaPlugin implements Listener {
    private static JavaPlugin singleton = null; // ugly
    private static Logger logger = null;
    private static Permission perms = null;
    private static DataPipe data = null;
    private static GroupManager groupManager = null;
    private static QueryManager queryManager = null;
    private FileConfiguration config;
    private JDA jda;

    // get important singletons on instantiation
    public MCVerifier() {
        singleton = this;
        logger = getLogger();
    }

    @Override
    public void onEnable() {
        // init
        // get plugin config
        saveDefaultConfig();
        config = getConfig();
        // load Vault(sie?)
        perms = setupPermissions();

        logger.info("Starting database connection");
        try {
            data = loadDatabase();
        } catch (SQLException e) {
            logger.warning("Something happened when connecting to the database");
            logger.warning(e.toString());
            prematureDisable();
            return;
        }
        logger.info("Database ready!");

        // Init group manager
        try {
            groupManager = loadGroupManager();
        } catch (IOException | InvalidConfigurationException e) {
            logger.warning(e.toString());
            prematureDisable();
            return;
        }
        // init verification manager
        queryManager = new QueryManager(config.getInt("verify-timeout"));

        // try to open discord gateway
        try {
            /* TODO: Figure out needed GatewayIntents
             *   Needed OAuth2 scopes: bot, applications.commands
             *   Bot permissions int: 413122553856
             */
            this.jda = JDABuilder.createLight(config.getString("token"))
                    .addEventListeners(new BotInit())
                    .build();
        } catch (LoginException e) {
            // disable plugin on Login failure
            logger.warning(e.toString());
            prematureDisable();
        }

        // register commands
        getCommand("verify").setExecutor(queryManager);
        // attach server event listeners
        getServer().getPluginManager().registerEvents(this, this);
    }

    private GroupManager loadGroupManager() throws IOException, InvalidConfigurationException {
        String onVerify = config.getString("on-verify-group");
        ConfigManager groupConfig = new ConfigManager(this, "groups.yml");
        return new GroupManager(groupConfig, onVerify);
    }

    private DataPipe loadDatabase() throws SQLException {
        // determine database type
        if (config.getBoolean("mysql.use")) {
            // use mysql
            return new MysqlDataPipe(
                    config.getString("mysql.host"),
                    config.getString("mysql.port"),
                    config.getString("mysql.database"),
                    config.getString("mysql.username"),
                    config.getString("mysql.password"),
                    config.getString("mysql.table-prefix")
            );
        } else {
            // use sqlite
            return new SqliteDataPipe(getDataFolder() + "\\database.db");
        }
    }

    private Permission setupPermissions() {
        // original method from
        // https://github.com/MilkBowl/VaultAPI/blob/master/README.md
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        return rsp.getProvider();
    }

    @Override
    public void onDisable() {
        logger.info("Closing database connection");
        if (data != null) data.close();
        logger.info("Closing JDA gateway");
        if (jda != null) jda.shutdown();
        singleton = null;
    }

    public static void prematureDisable() {
        logger.warning("Premature disable");
        Bukkit.getPluginManager().disablePlugin(singleton);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // if player verified then sync
        try {
            String uuid = player.getUniqueId().toString();
            if (data.uuidIsIn(uuid)) {
                // update username on db
                data.updateUsername(uuid, player.getName());
                // sync
                groupManager.syncGroups(player);
            } else {
                // send unverified message
                if (config.getBoolean("on-login-warning")) {
                    TextComponent unVerifiedMsg = Component.text(
                            "You are unverified", NamedTextColor.RED
                    );
                    player.sendMessage(unVerifiedMsg);
                }
            }
        } catch (SQLException e) {
            logger.warning("Error syncing groups for " + player.getName());
            logger.warning(e.toString());
        }
    }

    // TODO change singletons for constructor parameter in helper classes? Σ(-᷅_-᷄๑)
    // static getters
    public static Logger getPluginLogger() {
        return logger;
    }

    public static Permission getPerms() {
        return perms;
    }

    public static DataPipe getData() {
        return data;
    }

    public static GroupManager getGroupManager() {
        return groupManager;
    }

    public static QueryManager getQueryManager() {
        return queryManager;
    }
}
