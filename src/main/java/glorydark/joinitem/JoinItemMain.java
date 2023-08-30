package glorydark.joinitem;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.block.ItemFrameUseEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import glorydark.joinitem.languages.PluginLanguage;
import glorydark.joinitem.utils.Inventory;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class JoinItemMain extends PluginBase implements Listener {

    public List<JoinItem> items = new ArrayList<>();
    public HashMap<String, Long> coolDown = new HashMap<>();
    public boolean keepOnDeath;
    public String path;
    public String commandName;
    public int triggerType;
    public int coolDownTick;
    public boolean sendCoolDownTips;
    public static PluginLanguage language;

    @Override
    public void onEnable() {
        path = this.getDataFolder().getPath();
        this.saveDefaultConfig();
        new File(path+"/languages/").mkdirs();
        this.saveResource("languages/zh_CN.properties", false);
        this.saveResource("languages/en_US.properties", false);
        Config config = new Config(path + "/config.yml", Config.YAML);
        String languageName = config.getString("language", "en_US");
        language = new PluginLanguage(new File(this.getDataFolder() + "/languages/" + languageName + ".properties"));
        keepOnDeath = config.getBoolean("keep_on_death", true);
        triggerType = config.getInt("trigger_type", 0);
        coolDownTick = config.getInt("cooldown_tick", 10);
        sendCoolDownTips = config.getBoolean("send_cooldown_tip", false);
        List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) config.get("items");
        Config nbtCacheConfig = new Config(path + "/nbt.yml", Config.YAML);
        int index = 0;
        for (Map<String, Object> itemMap : itemsMap) {
            Item getItem = Item.fromString((String) itemMap.get("id"));
            getItem.setDamage((Integer) itemMap.get("meta"));
            getItem.setCount((Integer) itemMap.get("count"));
            if (itemMap.containsKey("lore") && itemMap.get("lore") instanceof String) {
                itemMap.put("lore", new ArrayList<>(Arrays.asList(((String) itemMap.get("lore")).split("\\n"))));
            }
            String nbtKey = (String) itemMap.getOrDefault("nbt", "null");
            String nbt = nbtCacheConfig.getString(nbtKey, "null");
            items.add(new JoinItem(index, getItem, (String) itemMap.get("name"), (List<String>) itemMap.getOrDefault("lore", new ArrayList<>()), nbt, (List<String>) itemMap.getOrDefault("messages", new ArrayList<>()), (List<String>) itemMap.getOrDefault("commands", new ArrayList<String>()), (Integer) itemMap.getOrDefault("slot", -1), (Integer) itemMap.getOrDefault("lock_type", 0)));
            index++;
        }
        this.getServer().getPluginManager().registerEvents(this, this);
        commandName = config.getString("command", "joinitem");
        this.getServer().getCommandMap().register("", new Commands(commandName));
        this.getLogger().info("JoinItem onLoad! Author: glorydark");
    }

    @EventHandler
    public void PlayerJoinEvent(PlayerLocallyInitializedEvent event) {
        Player player = event.getPlayer();
        if (player.getInventory() != null) {
            player.getInventory().getContents().forEach((integer, item1) -> {
                if (item1.hasCompoundTag() && item1.getNamedTag().contains("JoinItem")) {
                    player.getInventory().remove(item1);
                }
            });
            items.forEach(joinItem -> joinItem.giveItem(player));
        }
    }

    @EventHandler
    public void PlayerInteractEvent(PlayerInteractEvent event) {
        boolean b = event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK;
        boolean c = event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_AIR || event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_AIR;
        if ((triggerType == 0) || (triggerType == 1 && b) || (triggerType == 2 && c)) {
            Item check = event.getItem();
            AtomicReference<JoinItem> item = new AtomicReference<>();
            items.stream().filter(joinItem -> joinItem.checkItem(check)).sorted().findFirst().ifPresent(item::set);
            if (item.get() != null) {
                item.get().execute(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void BlockPlaceEvent(BlockPlaceEvent event) {
        Item check = event.getItem();
        AtomicReference<JoinItem> item = new AtomicReference<>();
        items.stream().filter(joinItem -> joinItem.checkItem(check)).sorted().findFirst().ifPresent(item::set);
        if (item.get() != null) {
            item.get().execute(event.getPlayer());
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void ItemFrameUseEvent(ItemFrameUseEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        Item check = event.getItem();
        AtomicReference<JoinItem> item = new AtomicReference<>();
        items.stream().filter(joinItem -> joinItem.checkItem(check)).sorted().findFirst().ifPresent(item::set);
        if (item.get() != null) {
            item.get().execute(player);
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void PlayerDropItemEvent(PlayerDropItemEvent event) {
        Item check = event.getItem();
        AtomicReference<JoinItem> item = new AtomicReference<>();
        items.stream().filter(joinItem -> joinItem.checkItem(check)).sorted().findFirst().ifPresent(item::set);
        if (item.get() != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void PlayerDeathEvent(PlayerDeathEvent event) {
        if (!keepOnDeath) {
            return;
        }
        List<Item> drops = new ArrayList<>(Arrays.asList(event.getDrops()));
        drops.removeIf(item -> item.hasCompoundTag() && item.getNamedTag().contains("JoinItem"));
        event.setDrops(drops.toArray(new Item[0]));
    }

    @EventHandler
    public void PlayerQuitEvent(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player != null && player.getInventory() != null) {
            for (Item item : player.getInventory().getContents().values()) {
                if (item.hasCompoundTag() && item.getNamedTag().contains("JoinItem")) {
                    player.getInventory().remove(item);
                }
            }
        }
    }

    public class Commands extends Command {

        public Commands(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender commandSender, String s, String[] strings) {
            if (strings.length == 0) {
                commandSender.sendMessage(language.getTranslation("command_help", commandName));
                return false;
            }
            switch (strings[0]) {
                case "regain":
                    if (commandSender.isPlayer()) {
                        Player player = (Player) commandSender;
                        player.getInventory().getContents().forEach((integer, item1) -> {
                            if (item1.hasCompoundTag() && item1.getNamedTag().contains("JoinItem")) {
                                player.getInventory().remove(item1);
                            }
                        });
                        items.forEach(joinItem -> joinItem.giveItem(player));
                        commandSender.sendMessage(language.getTranslation("command_regain_item_success"));
                    }
                    break;
                case "savenbt":
                    if (strings.length != 2) {
                        commandSender.sendMessage(language.getTranslation("command_help_savenbt", commandName));
                        return false;
                    }
                    if (commandSender.isPlayer() && commandSender.isOp()) {
                        Player player = commandSender.asPlayer();
                        Item item = player.getInventory().getItemInHand();
                        String string = Inventory.bytesToHexString(item.getCompoundTag());
                        if (string.equals("null")) {
                            commandSender.sendMessage(language.getTranslation("command_savenbt_no_nbt_exist"));
                            return true;
                        }
                        Config config = new Config(path + "/nbt.yml", Config.YAML);
                        config.set(strings[1], string);
                        config.save();
                        commandSender.sendMessage(language.getTranslation("command_savenbt_save_success", strings[1]));
                    }
                    break;
                case "reload":
                    if (commandSender.isOp() || !commandSender.isPlayer()) {
                        Config config = new Config(path + "/config.yml", Config.YAML);
                        keepOnDeath = config.getBoolean("keep_on_death", true);
                        triggerType = config.getInt("trigger_type", 0);
                        coolDownTick = config.getInt("cooldown_tick", 10);
                        sendCoolDownTips = config.getBoolean("send_message_tip", false);
                        List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) config.get("items");
                        Config nbtCacheConfig = new Config(path + "/nbt.yml", Config.YAML);
                        int index = 0;
                        for (Map<String, Object> itemMap : itemsMap) {
                            Item getItem = Item.fromString((String) itemMap.get("id"));
                            getItem.setDamage((Integer) itemMap.get("meta"));
                            getItem.setCount((Integer) itemMap.get("count"));
                            String nbtKey = (String) itemMap.getOrDefault("nbt", "null");
                            String nbt = nbtCacheConfig.getString(nbtKey, "null");
                            items.add(new JoinItem(index, getItem, (String) itemMap.get("name"), (List<String>) itemMap.getOrDefault("lore", new ArrayList<>()), nbt, (List<String>) itemMap.getOrDefault("messages", new ArrayList<>()), (List<String>) itemMap.getOrDefault("commands", new ArrayList<String>()), (Integer) itemMap.getOrDefault("slot", -1), (Integer) itemMap.getOrDefault("lock_type", 0)));
                            index++;
                        }
                        for (Player player : Server.getInstance().getOnlinePlayers().values()) {
                            player.getInventory().getContents().forEach((integer, item1) -> {
                                if (item1.hasCompoundTag() && item1.getNamedTag().contains("JoinItem")) {
                                    player.getInventory().remove(item1);
                                }
                            });
                            items.forEach(joinItem -> joinItem.giveItem(player));
                            commandSender.sendMessage(language.getTranslation("message_reload_player_regain_item"));
                        }
                        commandSender.sendMessage(language.getTranslation("command_reload_success"));
                    }
                    break;
                case "help":
                    commandSender.sendMessage(language.getTranslation("command_help_regain", commandName));
                    if (commandSender.isOp() || !commandSender.isPlayer()) {
                        commandSender.sendMessage(language.getTranslation("command_help_reload", commandName));
                        commandSender.sendMessage(language.getTranslation("command_help_savenbt", commandName));
                    }
                    break;
            }
            return true;
        }
    }

    public class JoinItem {
        private final int loadIndex;
        private final Item item;
        private final String name;
        private final List<String> lore;
        private final List<String> messages;
        private final List<String> commands;
        private final int slot;
        private final int lockType;

        public JoinItem(int loadIndex, Item item, String name, List<String> lore, String nbtString, List<String> messages, List<String> commands, int slot, int lockType) {
            this.loadIndex = loadIndex;
            this.item = item;
            this.name = name;
            if (!nbtString.equals("null")) {
                this.item.setNamedTag(Item.parseCompoundTag(Inventory.hexStringToBytes(nbtString)));
            }
            this.lore = lore;
            this.messages = messages;
            this.commands = commands;
            this.slot = slot;
            this.lockType = lockType;
        }

        public void execute(Player player) {
            if (System.currentTimeMillis() - coolDown.getOrDefault(player.getName(), 0L) >= coolDownTick * 50L) {
                for (String message : messages) {
                    player.sendMessage(message.replace("%player%", player.getName()));
                }
                for (String command : commands) {
                    Server.getInstance().executeCommand(player, command.replace("%player%", player.getName()));
                }
                coolDown.put(player.getName(), System.currentTimeMillis());
            } else {
                if (sendCoolDownTips) {
                    player.sendMessage(language.getTranslation("message_player_use_too_frequent"));
                }
            }
        }

        public void giveItem(Player player) {
            Item give = item.clone();
            give.setCustomName(name);
            give.setLore(lore.toArray(new String[0]));
            CompoundTag tag = give.getNamedTag();
            tag.putString("JoinItem", name).putBoolean("Unbreakable", true);
            give.setCompoundTag(tag);
            if (lockType >= 0 && lockType < JoinItemLockType.values().length) {
                give.setItemLockMode(JoinItemLockType.values()[lockType].itemLockMode);
            }
            if (slot != -1 && slot >= 0) {
                player.getInventory().setItem(slot, give);
            } else {
                player.getInventory().addItem(give);
            }
        }

        public boolean checkItem(Item item) {
            if (item != null && item.hasCompoundTag()) {
                if (item.getNamedTag().contains("JoinItem")) {
                    return item.getNamedTag().getString("JoinItem").equals(name);
                }
            }
            return false;
        }

        public int getLockType() {
            return lockType;
        }

        public int getSlot() {
            return slot;
        }

        public int getLoadIndex() {
            return loadIndex;
        }

        public enum JoinItemLockType {

            NONE(Item.ItemLockMode.NONE),
            LOCK_IN_SLOT(Item.ItemLockMode.LOCK_IN_SLOT),
            LOCK_IN_INVENTORY(Item.ItemLockMode.LOCK_IN_INVENTORY);

            private final Item.ItemLockMode itemLockMode;

            JoinItemLockType(Item.ItemLockMode itemLockMode){
                this.itemLockMode = itemLockMode;
            }
        }
    }
}
