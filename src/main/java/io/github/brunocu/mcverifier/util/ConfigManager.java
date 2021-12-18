package io.github.brunocu.mcverifier.util;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class ConfigManager {
    private final File file;
    private final FileConfiguration fileConfig;

    public ConfigManager(JavaPlugin plugin, String ymlName) throws IOException, InvalidConfigurationException {
        file = new File(plugin.getDataFolder(), ymlName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(ymlName, false);
        }
        fileConfig = new YamlConfiguration();
        fileConfig.load(file);
    }

    public void saveConfig() throws IOException {
        fileConfig.save(file);
    }

    public FileConfiguration getFileConfig() {
        return fileConfig;
    }

}
