package com.archyx.slate.menu;

import com.archyx.slate.Slate;
import com.archyx.slate.fill.FillData;
import com.archyx.slate.fill.FillItem;
import com.archyx.slate.fill.FillItemParser;
import com.archyx.slate.item.MenuItem;
import com.archyx.slate.item.parser.SingleItemParser;
import com.archyx.slate.item.parser.TemplateItemParser;
import com.archyx.slate.util.TextUtil;
import fr.minuskube.inv.SmartInventory;
import org.apache.commons.lang.StringUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MenuManager {

    private final Slate slate;
    private final Map<String, ConfigurableMenu> menus;
    private final Map<String, MenuProvider> menuProviders;

    public MenuManager(Slate slate) {
        this.slate = slate;
        this.menus = new LinkedHashMap<>();
        this.menuProviders = new HashMap<>();
    }

    @Nullable
    public ConfigurableMenu getMenu(String name) {
        return menus.get(name);
    }

    /**
     * Registers a menu provider for a menu. Providers are used to define unique behavior for menus.
     *
     * @param name The name of the menu
     * @param provider The provider instance
     */
    public void registerMenuProvider(String name, MenuProvider provider) {
        menuProviders.put(name, provider);
    }

    /**
     * Attempts to load a menu from a file
     *
     * @param file The file to load from, must be in Yaml syntax
     */
    public void loadMenu(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String menuName = file.getName();
        int pos = menuName.lastIndexOf(".");
        if (pos > 0) {
            menuName = menuName.substring(0, pos);
        }

        String title = config.getString("title", menuName);
        int size = config.getInt("size", 6);

        MenuProvider menuProvider = menuProviders.get(menuName);
        if (menuProvider == null) {
            throw new IllegalArgumentException("Registered MenuProvider not found for menu with name " + menuName);
        }

        Map<String, MenuItem> items = new HashMap<>();
        // Load single items
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemName : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemName);
                if (itemSection != null) {
                    MenuItem item = new SingleItemParser(slate).parse(itemSection, menuName, menuProvider);
                    items.put(itemName, item);
                }
            }
        }
        // Load template items
        ConfigurationSection templatesSection = config.getConfigurationSection("templates");
        if (templatesSection != null) {
            for (String templateName : templatesSection.getKeys(false)) {
                ConfigurationSection templateSection = templatesSection.getConfigurationSection(templateName);
                if (templateSection != null) {
                    MenuItem item = new TemplateItemParser<>(slate).parse(templateSection, menuName, menuProvider);
                    items.put(templateName, item);
                }
            }
        }
        // Load fill item
        ConfigurationSection fillSection = config.getConfigurationSection("fill");
        FillData fillData;
        if (fillSection != null) {
            boolean fillEnabled = fillSection.getBoolean("enabled", false);
            FillItem fillItem = new FillItemParser(slate).parse(fillSection, menuName, menuProvider);
            fillData = new FillData(fillItem, fillEnabled);
        } else {
            fillData = new FillData(FillItem.getDefault(slate), false);
        }

        // Add menu to map
        ConfigurableMenu menu = new ConfigurableMenu(menuName, title, size, items, menuProvider, fillData);
        menus.put(menuName, menu);
    }

    /**
     * Opens a loaded menu for a player, will fail silently if the menu does not exist.
     *
     * @param player The player to display the menu to
     * @param name The name of the menu
     * @param properties Map of menu properties
     * @param page The page number to open, 0 is the first page
     */
    public void openMenu(Player player, String name, Map<String, Object> properties, int page) {
        ConfigurableMenu menu = menus.get(name);
        if (menu == null) {
            throw new IllegalArgumentException("Menu with name " + name + " not registered");
        }
        MenuInventory menuInventory = new MenuInventory(slate, menu, player, properties, page);
        String title = menu.getTitle();
        // Replace title placeholders
        if (menu.getProvider() != null) {
            for (String placeholder : StringUtils.substringsBetween(title, "{", "}")) {
                title = TextUtil.replace(title, "{" + placeholder + "}",
                        menu.getProvider().onPlaceholderReplace(placeholder, player, menuInventory.getActiveMenu()));
            }
        }
        // Build inventory and open
        SmartInventory smartInventory = SmartInventory.builder()
                .title(title)
                .size(menu.getSize(), 9)
                .manager(slate.getInventoryManager())
                .provider(menuInventory)
                .build();
        smartInventory.open(player);
    }

    /**
     * Opens a loaded menu for a player, will fail silently if the menu does not exist.
     *
     * @param player The player to display the menu to
     * @param name The name of the menu
     * @param page The page number to open, 0 is the first page
     */
    public void openMenu(Player player, String name, int page) {
        openMenu(player, name, new HashMap<>(), page);
    }

    /**
     * Opens a loaded menu for a player, will fail silently if the menu does not exist.
     * Shows the first page.
     *
     * @param player The player to display the menu to
     * @param name The name of the menu
     */
    public void openMenu(Player player, String name) {
        openMenu(player, name, new HashMap<>(), 0);
    }

    public Set<String> getMenuProviderNames() {
        return menuProviders.keySet();
    }

}
