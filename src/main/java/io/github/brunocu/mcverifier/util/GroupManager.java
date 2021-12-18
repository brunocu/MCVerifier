package io.github.brunocu.mcverifier.util;

import io.github.brunocu.mcverifier.MCVerifier;
import io.github.brunocu.mcverifier.database.DataPipe;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

// TODO make async?
public class GroupManager {
    // singleton spaghetti
    private final Logger logger = MCVerifier.getPluginLogger();
    private final DataPipe data = MCVerifier.getData();
    private final Permission perms = MCVerifier.getPerms();
    private final ConfigManager groupConfig;
    private Guild guild = null;
    private List<String> groupList = null;
    private Map<String, String> roleMap = null;
    private Map<String, String> permMap = null;
    private String onVerfifyGroup = null;

    public GroupManager(ConfigManager configManager) throws IOException, InvalidConfigurationException {
        this(configManager, null);
    }

    public GroupManager(ConfigManager configManager, String onVerifyGroup) throws IOException, InvalidConfigurationException {
        groupConfig = configManager;
        // asert >0 groups
        if (groupConfig.getFileConfig().getList("groups").isEmpty()) {
            throw new InvalidConfigurationException("groups.yml needs at least 1 group");
        }
        initGroups();
        if (onVerifyGroup != null && !onVerifyGroup.isEmpty()) {
            if (groupList.contains(onVerifyGroup))
                this.onVerfifyGroup = onVerifyGroup;
            else
                logger.warning("on-verify-group must be a group declared in groups.yml. Ignoring setting.");
        }
    }

    private void initGroups() throws IOException {
        // parse listed groups
        groupList = groupConfig.getFileConfig().getStringList("groups");
        Set<String> groupsKeys = groupConfig.getFileConfig().getKeys(false);
        if (groupList.size() > groupsKeys.size() - 1) {
            // generate missing group configs
            logger.info("Generating missing group configs");
            List<String> unInit = new ArrayList<>(groupList);
            unInit.removeAll(groupsKeys);
            for (String group : unInit) {
                /* FIXME
                 *   stop plugin if generates groups
                 *   or add group reload command
                 *   roleID=0 will fail
                 */
                groupConfig.getFileConfig().set(group + ".roleID", Integer.toString(0));
                groupConfig.getFileConfig().set(group + ".mcgroup", group);
            }
            groupConfig.saveConfig();
        }
        // group maps
        roleMap = new HashMap<>(groupList.size());
        permMap = new HashMap<>(groupList.size());
        for (String group : groupList) {
            // roleMap
            roleMap.put(group, groupConfig.getFileConfig().getString(group + ".roleID"));
            permMap.put(group, groupConfig.getFileConfig().getString(group + ".mcgroup"));
        }
    }

    public void verifyUser(Player player, Member member) throws SQLException {
        String uuid = player.getUniqueId().toString();
        String playerName = player.getName();
        String memberId = member.getId();
        data.insert(uuid, playerName, memberId);
        // grant onVerifyRole if exists
        if (onVerfifyGroup != null) {
            Role onVerifyRole = guild.getRoleById(getRole(onVerfifyGroup));
            guild.addRoleToMember(member, onVerifyRole).complete();
            // for some reason misses onVerifyGroup when syncGroups called immediately after assigning role
            perms.playerAddGroup(null, player, getPerm(onVerfifyGroup));
        }
        // sync groups for player
        syncGroups(player, member);
    }

    public void syncGroups(Player player) throws SQLException {
        String memberID = data.getMemberIdfromUuid(player.getUniqueId().toString());
        Member member = guild.retrieveMemberById(memberID).complete();
        syncGroups(player, member);
    }

    public void syncGroups(Player player, Member member) {
        /* TODO
         *   check if server has group support
         *   if not, fallback on permission nodes for group membership
         */
        logger.info("Syncing groups for player: " + player.getName());
        List<Role> memberRoles = member.getRoles();
        for (String group : groupList) {
            String roleID = getRole(group);
            String groupName = getPerm(group);
            if (memberRoles.stream().anyMatch(role -> role.getId().equals(roleID))) {
                // if member has role
                // add group
                if (!perms.playerInGroup(null, player, groupName)) {
                    perms.playerAddGroup(null, player, groupName);
                }
            } else {
                // player doesn't belong in group
                // remove from mc group if present
                if (perms.playerInGroup(null, player, groupName)) {
                    perms.playerRemoveGroup(null, player, groupName);
                }
            }
        }
        logger.info("Syncing finished for player: " + player.getName());
    }

    public String unlinkMember(Member member) throws SQLException {
        // Get linked mc account
        String uuid = data.getUuidFromMemberId(member.getId());
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        // remove onVerifyRole if exists
        if (onVerfifyGroup != null) {
            Role onVerifyRole = guild.getRoleById(getRole(onVerfifyGroup));
            guild.removeRoleFromMember(member, onVerifyRole).queue();
        }
        // remove all registered groups
        for (String group : groupList) {
            // assumes global permissions
            perms.playerRemoveGroup(null, player, getPerm(group));
        }
        // delete from table
        data.drop(member.getId());
        return player.getName();
    }

    // encapsulation methods

    public String getRole(String group) {
        return roleMap.get(group);
    }

    public String getPerm(String group) {
        return permMap.get(group);
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }
}
