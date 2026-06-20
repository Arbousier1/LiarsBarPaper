package com.jijifujiji.liarsbar;

import com.jijifujiji.liarsbar.command.LiarBarCommand;
import com.jijifujiji.liarsbar.command.LiarBarTabCompleter;
import com.jijifujiji.liarsbar.config.ConfigManager;
import com.jijifujiji.liarsbar.display.CraftEngineFurnitureBridge;
import com.jijifujiji.liarsbar.display.DisplayManager;
import com.jijifujiji.liarsbar.game.EconomyManager;
import com.jijifujiji.liarsbar.game.TableManager;
import com.jijifujiji.liarsbar.i18n.Messages;
import com.jijifujiji.liarsbar.listener.CraftEngineFurnitureInteractListener;
import com.jijifujiji.liarsbar.listener.EntityInteractListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

public final class LiarsBarPlugin extends JavaPlugin {

    private static LiarsBarPlugin instance;
    private static final String CRAFT_ENGINE_BUNDLE_ROOT = "craftengine/liarsbar";
    private ConfigManager configManager;
    private Messages messages;
    private EconomyManager economyManager;
    private TableManager tableManager;
    private CraftEngineFurnitureBridge craftEngineFurnitureBridge;

    @Override
    public void onLoad() {
        installCraftEngineBundle();
    }

    @Override
    public void onEnable() {
        instance = this;
        this.configManager = new ConfigManager(this);
        this.messages = new Messages(this);
        this.economyManager = new EconomyManager(getLogger(), messages);
        this.economyManager.setup();
        this.craftEngineFurnitureBridge = new CraftEngineFurnitureBridge(this);
        this.tableManager = new TableManager(this, configManager);

        getCommand("liarbar").setExecutor(new LiarBarCommand(this));
        getCommand("liarbar").setTabCompleter(new LiarBarTabCompleter(this));
        CraftEngineFurnitureInteractListener.register(this);
        getServer().getPluginManager().registerEvents(new EntityInteractListener(this), this);

        getLogger().info(messages.plain("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (tableManager != null) tableManager.shutdownAll();
        DisplayManager.clearAll();
        if (messages != null) {
            getLogger().info(messages.plain("plugin.disabled"));
        }
    }

    public static LiarsBarPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public Messages messages() { return messages; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public TableManager getTableManager() { return tableManager; }
    public CraftEngineFurnitureBridge getCraftEngineFurnitureBridge() { return craftEngineFurnitureBridge; }

    private void installCraftEngineBundle() {
        try (InputStream indexStream = getResource(CRAFT_ENGINE_BUNDLE_ROOT + "/_bundle_index.txt")) {
            if (indexStream == null) {
                getLogger().warning("CraftEngine bundle index is missing from the plugin jar.");
                return;
            }

            File pluginsDir = getDataFolder().getParentFile();
            if (pluginsDir == null) {
                getLogger().warning("Unable to locate the server plugins directory.");
                return;
            }

            Path resourcesRoot = pluginsDir.toPath().resolve("CraftEngine").resolve("resources").normalize();
            Path targetRoot = resourcesRoot.resolve("liarsbar").normalize();
            if (!targetRoot.startsWith(resourcesRoot) || targetRoot.equals(resourcesRoot)) {
                getLogger().warning("Refusing to install CraftEngine bundle outside the expected resource directory: " + targetRoot);
                return;
            }
            deleteRecursively(targetRoot);
            Files.createDirectories(targetRoot);

            String index = new String(indexStream.readAllBytes(), StandardCharsets.UTF_8);
            int copied = 0;
            for (String rawEntry : index.split("\\R")) {
                String entry = rawEntry.trim();
                if (entry.isEmpty()) continue;
                Path target = targetRoot.resolve(entry).normalize();
                if (!target.startsWith(targetRoot)) {
                    getLogger().warning("Skipped unsafe CraftEngine bundle entry: " + entry);
                    continue;
                }

                try (InputStream fileStream = getResource(CRAFT_ENGINE_BUNDLE_ROOT + "/" + entry)) {
                    if (fileStream == null) {
                        getLogger().warning("Missing CraftEngine bundle file: " + entry);
                        continue;
                    }
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(fileStream, target, StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                }
            }

            getLogger().info("Installed CraftEngine resource pack bundle to " + targetRoot + " (" + copied + " files).");
        } catch (IOException e) {
            getLogger().warning("Failed to install CraftEngine resource pack bundle: " + e.getMessage());
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
