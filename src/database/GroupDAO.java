package database;

import model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {

    private static Connection getConnection() {
        return DatabaseManager.getConnection();
    }

    // ═══════════════════════════════════════════════════════════════
    // GROUPE
    // ═══════════════════════════════════════════════════════════════

    public static boolean createGroup(String groupId, String groupName, String creatorId) {
        String sql = "INSERT INTO groups_table (id, name, creator_id, created_at) VALUES (?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, groupName);
            stmt.setString(3, creatorId);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            System.out.println("Groupe créé : " + groupName);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur création groupe : " + e.getMessage());
            return false;
        }
    }

    // Mettre à jour nom + description + photo du groupe
    public static boolean updateGroupInfo(String groupId, String newName,
                                          String description, String photoBase64) {
        String sql = "UPDATE groups_table SET name = ?, description = ?, photo = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, newName);
            stmt.setString(2, description);
            stmt.setString(3, photoBase64);
            stmt.setString(4, groupId);
            stmt.executeUpdate();
            System.out.println("Infos groupe mises à jour : " + groupId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur mise à jour groupe : " + e.getMessage());
            return false;
        }
    }

    // Récupérer les infos complètes d'un groupe [id, name, creatorId, description, photo]
    public static String[] getGroupInfo(String groupId) {
        String sql = "SELECT id, name, creator_id, description, photo FROM groups_table WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("creator_id"),
                        rs.getString("description") != null ? rs.getString("description") : "",
                        rs.getString("photo") != null ? rs.getString("photo") : ""
                };
            }
        } catch (SQLException e) {
            System.out.println("Erreur getGroupInfo : " + e.getMessage());
        }
        return null;
    }

    // Supprimer un groupe entier (admin seulement)
    public static boolean deleteGroup(String groupId) {
        try {
            // Supprimer messages
            PreparedStatement s1 = getConnection().prepareStatement(
                    "DELETE FROM group_messages WHERE group_id = ?");
            s1.setString(1, groupId); s1.executeUpdate();
            // Supprimer membres
            PreparedStatement s2 = getConnection().prepareStatement(
                    "DELETE FROM group_members WHERE group_id = ?");
            s2.setString(1, groupId); s2.executeUpdate();
            // Supprimer groupe
            PreparedStatement s3 = getConnection().prepareStatement(
                    "DELETE FROM groups_table WHERE id = ?");
            s3.setString(1, groupId); s3.executeUpdate();
            System.out.println("Groupe supprimé : " + groupId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur suppression groupe : " + e.getMessage());
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // MEMBRES & RÔLES
    // ═══════════════════════════════════════════════════════════════

    // Ajouter un membre avec rôle (par défaut "member")
    public static boolean addMember(String groupId, String userId) {
        return addMemberWithRole(groupId, userId, "member");
    }

    // Ajouter le créateur comme admin
    public static boolean addMemberWithRole(String groupId, String userId, String role) {
        String sql = "INSERT INTO group_members (group_id, user_id, role) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE role = VALUES(role)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, userId);
            stmt.setString(3, role);
            stmt.executeUpdate();
            System.out.println("Membre ajouté (" + role + ") : " + userId + " → " + groupId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur ajout membre : " + e.getMessage());
            return false;
        }
    }

    // Retirer un membre
    public static boolean removeMember(String groupId, String userId) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, userId);
            int rows = stmt.executeUpdate();
            System.out.println("Membre retiré : " + userId + " → " + groupId);
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("Erreur suppression membre : " + e.getMessage());
            return false;
        }
    }

    // Promouvoir un membre en admin
    public static boolean promoteToAdmin(String groupId, String userId) {
        String sql = "UPDATE group_members SET role = 'admin' WHERE group_id = ? AND user_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, userId);
            int rows = stmt.executeUpdate();
            System.out.println("Promu admin : " + userId + " → " + groupId);
            return rows > 0;
        } catch (SQLException e) {
            System.out.println("Erreur promotion admin : " + e.getMessage());
            return false;
        }
    }

    // Rétrograder un admin en membre
    public static boolean demoteToMember(String groupId, String userId) {
        String sql = "UPDATE group_members SET role = 'member' WHERE group_id = ? AND user_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, userId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur rétrogradation : " + e.getMessage());
            return false;
        }
    }

    // Vérifier si un user est admin du groupe
    public static boolean isAdmin(String groupId, String userId) {
        String sql = "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return "admin".equals(rs.getString("role"));
            }
        } catch (SQLException e) {
            System.out.println("Erreur vérification admin : " + e.getMessage());
        }
        return false;
    }

    // Récupérer les membres avec leur rôle — retourne [userId, username, role, isOnline, profilePhoto]
    public static List<String[]> getGroupMembersWithRoles(String groupId) {
        List<String[]> members = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.is_online, u.profile_photo, gm.role " +
                "FROM users u " +
                "JOIN group_members gm ON u.id = gm.user_id " +
                "WHERE gm.group_id = ? " +
                "ORDER BY FIELD(gm.role, 'admin', 'member'), u.username ASC";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                members.add(new String[]{
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("role"),
                        String.valueOf(rs.getBoolean("is_online")),
                        rs.getString("profile_photo") != null ? rs.getString("profile_photo") : ""
                });
            }
        } catch (SQLException e) {
            System.out.println("Erreur membres avec rôles : " + e.getMessage());
        }
        return members;
    }

    // Récupérer les membres (User) — inchangé pour compatibilité
    public static List<User> getGroupMembers(String groupId) {
        List<User> members = new ArrayList<>();
        String sql = "SELECT u.* FROM users u " +
                "JOIN group_members gm ON u.id = gm.user_id " +
                "WHERE gm.group_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password")
                );
                user.setOnline(rs.getBoolean("is_online"));
                user.setProfilePhoto(rs.getString("profile_photo"));
                members.add(user);
            }
        } catch (SQLException e) {
            System.out.println("Erreur liste membres : " + e.getMessage());
        }
        return members;
    }

    // Récupérer les groupes d'un utilisateur — inclut description et photo
    public static List<String[]> getGroupsOfUser(String userId) {
        List<String[]> groups = new ArrayList<>();
        String sql = "SELECT g.id, g.name, g.creator_id, g.description, g.photo " +
                "FROM groups_table g " +
                "JOIN group_members gm ON g.id = gm.group_id " +
                "WHERE gm.user_id = ? " +
                "ORDER BY g.created_at DESC";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                // [groupId, groupName, creatorId, description, photo]
                groups.add(new String[]{
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("creator_id"),
                        rs.getString("description") != null ? rs.getString("description") : "",
                        rs.getString("photo") != null ? rs.getString("photo") : ""
                });
            }
        } catch (SQLException e) {
            System.out.println("Erreur groupes utilisateur : " + e.getMessage());
        }
        return groups;
    }

    // ═══════════════════════════════════════════════════════════════
    // MESSAGES
    // ═══════════════════════════════════════════════════════════════

    public static boolean saveGroupMessage(String messageId, String groupId,
                                           String senderId, String content) {
        String sql = "INSERT INTO group_messages (id, content, sender_id, group_id, timestamp) " +
                "VALUES (?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, messageId);
            stmt.setString(2, content);
            stmt.setString(3, senderId);
            stmt.setString(4, groupId);
            stmt.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur sauvegarde message groupe : " + e.getMessage());
            return false;
        }
    }

    public static List<String[]> getGroupMessages(String groupId) {
        List<String[]> messages = new ArrayList<>();
        String sql = "SELECT gm.id, gm.content, gm.message_type, gm.timestamp, " +
                "gm.is_deleted, gm.is_edited, gm.is_pinned, gm.reply_to_id, " +
                "u.username, u.id as sender_id " +
                "FROM group_messages gm " +
                "JOIN users u ON gm.sender_id = u.id " +
                "WHERE gm.group_id = ? " +
                "ORDER BY gm.timestamp ASC";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                messages.add(new String[]{
                        rs.getString("id"),                          // [0] messageId
                        rs.getString("username"),                    // [1] senderUsername
                        rs.getString("content"),                     // [2] content
                        rs.getTimestamp("timestamp").toString(),     // [3] timestamp
                        rs.getString("message_type"),                // [4] type
                        String.valueOf(rs.getBoolean("is_deleted")), // [5] deleted
                        String.valueOf(rs.getBoolean("is_edited")),  // [6] edited
                        String.valueOf(rs.getBoolean("is_pinned")),  // [7] pinned
                        rs.getString("reply_to_id") != null ? rs.getString("reply_to_id") : "" // [8] replyToId
                });
            }
        } catch (SQLException e) {
            System.out.println("Erreur getGroupMessages : " + e.getMessage());
        }
        return messages;
    }

    // ═══════════════════════════════════════════════════════════════
// CONFIDENTIALITÉ & INVITATIONS
// ═══════════════════════════════════════════════════════════════

    // Récupérer la permission d'ajout groupe d'un user
    public static String getGroupAddPermission(String userId) {
        String sql = "SELECT group_add_permission FROM user_privacy_settings WHERE user_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("group_add_permission");
        } catch (SQLException e) {
            System.out.println("Erreur getGroupAddPermission : " + e.getMessage());
        }
        return "everyone"; // défaut si pas de ligne
    }

    // Mettre à jour la permission d'ajout groupe
    public static boolean setGroupAddPermission(String userId, String permission) {
        String sql = "INSERT INTO user_privacy_settings (user_id, group_add_permission) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE group_add_permission = VALUES(group_add_permission)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            stmt.setString(2, permission);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur setGroupAddPermission : " + e.getMessage());
            return false;
        }
    }

    // Vérifier si userId est dans les contacts de targetId
    public static boolean isInContacts(String targetId, String userId) {
        String sql = "SELECT 1 FROM contacts WHERE user_id = ? AND contact_id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, targetId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("Erreur isInContacts : " + e.getMessage());
            return false;
        }
    }

    // Logique principale : ajouter direct OU envoyer invitation
// Retourne : "ADDED", "INVITED", "ALREADY_MEMBER", "ALREADY_INVITED", "BLOCKED"
    public static String tryAddMemberToGroup(String groupId, String addedByUserId, String targetUserId) {
        // Vérifier si déjà membre
        String checkMember = "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?";
        try {
            PreparedStatement ps = getConnection().prepareStatement(checkMember);
            ps.setString(1, groupId); ps.setString(2, targetUserId);
            if (ps.executeQuery().next()) return "ALREADY_MEMBER";
        } catch (SQLException e) { e.printStackTrace(); }

        // Vérifier si invitation déjà envoyée
        String checkInv = "SELECT 1 FROM group_invitations WHERE group_id = ? AND invited_user_id = ? AND status = 'pending'";
        try {
            PreparedStatement ps = getConnection().prepareStatement(checkInv);
            ps.setString(1, groupId); ps.setString(2, targetUserId);
            if (ps.executeQuery().next()) return "ALREADY_INVITED";
        } catch (SQLException e) { e.printStackTrace(); }

        String permission = getGroupAddPermission(targetUserId);

        switch (permission) {
            case "everyone":
                addMember(groupId, targetUserId);
                return "ADDED";

            case "contacts_only":
                if (isInContacts(targetUserId, addedByUserId)) {
                    addMember(groupId, targetUserId);
                    return "ADDED";
                } else {
                    sendGroupInvitation(groupId, targetUserId, addedByUserId);
                    return "INVITED";
                }

            case "nobody":
                sendGroupInvitation(groupId, targetUserId, addedByUserId);
                return "INVITED";

            default:
                return "BLOCKED";
        }
    }

    // Envoyer une invitation
    public static boolean sendGroupInvitation(String groupId, String invitedUserId, String invitedByUserId) {
        // Supprimer l'ancienne invitation (accepted ou declined) avant d'en créer une nouvelle
        String deleteSql = "DELETE FROM group_invitations WHERE group_id = ? AND invited_user_id = ? AND status != 'pending'";
        try {
            PreparedStatement del = getConnection().prepareStatement(deleteSql);
            del.setString(1, groupId);
            del.setString(2, invitedUserId);
            del.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Erreur nettoyage invitation : " + e.getMessage());
        }

        String sql = "INSERT IGNORE INTO group_invitations (group_id, invited_user_id, invited_by_user_id) VALUES (?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, groupId);
            stmt.setString(2, invitedUserId);
            stmt.setString(3, invitedByUserId);
            stmt.executeUpdate();
            System.out.println("Invitation envoyée → " + invitedUserId);
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur sendGroupInvitation : " + e.getMessage());
            return false;
        }
    }

    // Récupérer les invitations en attente d'un user [invitationId, groupId, groupName, invitedByUsername]
    public static java.util.List<String[]> getPendingInvitations(String userId) {
        java.util.List<String[]> list = new java.util.ArrayList<>();
        String sql = "SELECT gi.id, gi.group_id, g.name, u.username " +
                "FROM group_invitations gi " +
                "JOIN groups_table g ON gi.group_id = g.id " +
                "JOIN users u ON gi.invited_by_user_id = u.id " +
                "WHERE gi.invited_user_id = ? AND gi.status = 'pending'";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new String[]{
                        String.valueOf(rs.getInt("id")),
                        rs.getString("group_id"),
                        rs.getString("name"),
                        rs.getString("username")
                });
            }
        } catch (SQLException e) {
            System.out.println("Erreur getPendingInvitations : " + e.getMessage());
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════════
// MESSAGES GROUPE — EDIT / DELETE / PIN / REPLY / FICHIER / VOCAL
// ═══════════════════════════════════════════════════════════════

    public static boolean saveGroupMessage(String messageId, String groupId,
                                           String senderId, String content,
                                           String messageType, String replyToId) {
        String sql = "INSERT INTO group_messages (id, content, message_type, sender_id, group_id, timestamp, reply_to_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, messageId);
            stmt.setString(2, content);
            stmt.setString(3, messageType != null ? messageType : "TEXT");
            stmt.setString(4, senderId);
            stmt.setString(5, groupId);
            stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            stmt.setString(7, replyToId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur saveGroupMessage (full) : " + e.getMessage());
            return false;
        }
    }

    public static boolean deleteGroupMessage(String messageId) {
        String sql = "UPDATE group_messages SET is_deleted = 1 WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, messageId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur deleteGroupMessage : " + e.getMessage());
            return false;
        }
    }

    public static boolean editGroupMessage(String messageId, String newContent) {
        String sql = "UPDATE group_messages SET content = ?, is_edited = 1 WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, newContent);
            stmt.setString(2, messageId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur editGroupMessage : " + e.getMessage());
            return false;
        }
    }

    public static boolean pinGroupMessage(String messageId, boolean pinned) {
        String sql = "UPDATE group_messages SET is_pinned = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setBoolean(1, pinned);
            stmt.setString(2, messageId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur pinGroupMessage : " + e.getMessage());
            return false;
        }
    }

    // Répondre à une invitation (accept = true → rejoint le groupe)
    public static boolean respondToInvitation(int invitationId, boolean accept) {
        String newStatus = accept ? "accepted" : "declined";
        String sql = "UPDATE group_invitations SET status = ? WHERE id = ?";
        try {
            PreparedStatement stmt = getConnection().prepareStatement(sql);
            stmt.setString(1, newStatus);
            stmt.setInt(2, invitationId);
            stmt.executeUpdate();
            if (accept) {
                String sel = "SELECT group_id, invited_user_id FROM group_invitations WHERE id = ?";
                PreparedStatement ps2 = getConnection().prepareStatement(sel);
                ps2.setInt(1, invitationId);
                ResultSet rs = ps2.executeQuery();
                if (rs.next()) {
                    addMember(rs.getString("group_id"), rs.getString("invited_user_id"));
                }
            }
            return true;
        } catch (SQLException e) {
            System.out.println("Erreur respondToInvitation : " + e.getMessage());
            return false;
        }
    }
}