package server;

import database.*;
import model.*;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import java.util.Arrays;

public class ClientHandler implements Runnable {

    private Socket socket;
    private User user;
    private Server server;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    private String currentCallPartner = null;
    private String currentMeetingId   = null;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            Object firstObject = in.readObject();

            if (firstObject instanceof User) {
                this.user = (User) firstObject;
                User dbUser = UserDAO.getUserByLogin(user.getUsername(), user.getPassword());

                if (dbUser != null) {
                    this.user = dbUser;
                    this.user.setOnline(true);
                    UserDAO.updateUserOnlineStatus(user.getId(), true);
                    server.addClient(user.getUsername(), this);
                    sendObject("LOGIN_SUCCESS");
                    sendObject(dbUser);
                    sendUserList(server.getConnectedUsers());
                    System.out.println("Login : " + user.getUsername());
                } else {
                    sendObject("LOGIN_FAILED");
                    closeConnection(); return;
                }
            }

            while (true) {
                Object received = in.readObject();

                if (received instanceof Message) {
                    handleMessage((Message) received);

                } else if (received instanceof String) {
                    handleCommand((String) received);

                } else if (received instanceof call.Call) {
                    handleCall((call.Call) received);

                } else if (received instanceof byte[]) {
                    byte[] rawData = (byte[]) received;

                    // ── Appels de groupe ─────────────────────────────────
                    if (currentMeetingId != null) {
                        try {
                            byte[] audioHdr = ("MA:" + currentMeetingId + ":").getBytes("UTF-8");
                            if (bytesStartWith(rawData, audioHdr)) {
                                byte[] pcm = Arrays.copyOfRange(rawData, audioHdr.length, rawData.length);
                                // broadcastMeetingAudio préfixe "MAUDIO:<sender>:" pour fix echo
                                server.broadcastMeetingAudio(currentMeetingId, pcm, this);
                                continue;
                            }
                            byte[] videoHdr = ("MV:" + currentMeetingId + ":").getBytes("UTF-8");
                            if (bytesStartWith(rawData, videoHdr)) {
                                byte[] jpeg = Arrays.copyOfRange(rawData, videoHdr.length, rawData.length);
                                server.broadcastMeetingVideo(currentMeetingId, jpeg, this);
                                continue;
                            }
                        } catch (Exception ignored) {}
                    }

                    // ── Appels 1-1 ───────────────────────────────────────
                    // Client envoie "A:<pcm>" pour audio, "V:<jpeg>" pour vidéo
                    // On transmet tel quel au partenaire (avec le header)
                    if (currentCallPartner != null) {
                        server.broadcastAudio(rawData, this, currentCallPartner);
                    }
                }
            }

        } catch (EOFException e) {
            System.out.println("Déconnecté : " + (user != null ? user.getUsername() : "?"));
        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Erreur client : " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void handleMessage(Message message) {
        if (message instanceof model.VoiceMessage) {
            model.VoiceMessage vm = (model.VoiceMessage) message;
            if (vm.getAudioData() != null) {
                vm.setContent(java.util.Base64.getEncoder().encodeToString(vm.getAudioData()));
            }
        }
        server.broadcast(message, this);
        String recv = message.getReceiver().getUsername();
        if (server.isUserConnected(recv)) {
            MessageDAO.updateDeliveryStatus(message.getId(), "DELIVERED");
            sendObject("DELIVERED:" + message.getId());
        }
    }

    private void handleCommand(String command) {
        String log = command.length() > 100 ? command.substring(0, 100) + "..." : command;
        System.out.println("Cmd : " + log);

        if (command.equals("GET_USERS")) {
            sendUserList(server.getConnectedUsers());

        } else if (command.equals("DISCONNECT")) {
            closeConnection();

        } else if (command.startsWith("DELETE_MSG:")) {
            String[] p = command.split(":", 3);
            if (p.length == 3) { MessageDAO.deleteMessage(p[1]); server.broadcastCommand(command, p[2]); }

        } else if (command.startsWith("EDIT_MSG:")) {
            String[] p = command.split(":", 4);
            if (p.length == 4) { MessageDAO.editMessage(p[1], p[2]); server.broadcastCommand(command, p[3]); }

        } else if (command.startsWith("PIN_MSG:")) {
            String[] p = command.split(":", 4);
            if (p.length == 4) {
                MessageDAO.pinMessage(p[1], Boolean.parseBoolean(p[2]));
                server.broadcastCommand(command, p[3]);
            }

        } else if (command.startsWith("UPDATE_PROFILE:")) {
            String[] p = command.split(":", 4);
            if (p.length >= 3) {
                user.setUsername(p[2]);
                if (p.length == 4 && !p[3].isEmpty()) user.setProfilePhoto(p[3]);
                server.notifyUserList();
            }

        } else if (command.startsWith("SEEN:")) {
            String[] p = command.split(":", 3);
            if (p.length == 3) {
                MessageDAO.updateDeliveryStatus(p[1], "SEEN");
                server.broadcastCommand("SEEN:" + p[1], p[2]);
            }

        } else if (command.startsWith("CREATE_GROUP:")) {
            String[] p = command.split(":", 3);
            if (p.length == 3) {
                String grpId = UUID.randomUUID().toString();
                GroupDAO.createGroup(grpId, p[1], user.getId());
                GroupDAO.addMemberWithRole(grpId, user.getId(), "admin");
                for (String mid : p[2].split(","))
                    if (!mid.isEmpty() && !mid.equals(user.getId())) GroupDAO.addMember(grpId, mid);
                List<model.User> members = GroupDAO.getGroupMembers(grpId);
                String notif = "GROUP_CREATED:" + grpId + ":" + p[1] + ":" + user.getUsername();
                for (model.User m : members) server.broadcastCommand(notif, m.getUsername());
                sendObject("GROUP_CREATED:" + grpId + ":" + p[1]);
            }

        } else if (command.startsWith("GROUP_MSG:")) {
            String[] p = command.split(":", 4);
            if (p.length >= 3) {
                String msgId = UUID.randomUUID().toString();
                String replyTo = p.length == 4 ? p[3] : null;
                GroupDAO.saveGroupMessage(msgId, p[1], user.getId(), p[2], "TEXT", replyTo);
                String notif = "GROUP_MSG:" + p[1] + ":" + msgId + ":" + user.getUsername()
                        + ":" + p[2] + ":" + (replyTo != null ? replyTo : "");
                for (model.User m : GroupDAO.getGroupMembers(p[1]))
                    if (!m.getUsername().equals(user.getUsername()))
                        server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("GET_GROUP_HISTORY:")) {
            String grpId = command.split(":")[1];
            List<String[]> msgs = GroupDAO.getGroupMessages(grpId);
            StringBuilder sb = new StringBuilder("GROUP_HISTORY:" + grpId);
            for (String[] msg : msgs) {
                sb.append("\u0001").append(msg[0]).append("\u0002").append(msg[1]).append("\u0002")
                        .append(msg[2]).append("\u0002").append(msg[3]).append("\u0002").append(msg[4])
                        .append("\u0002").append(msg[5]).append("\u0002").append(msg[6]).append("\u0002")
                        .append(msg[7]).append("\u0002").append(msg[8]);
            }
            sendObject(sb.toString());

        } else if (command.startsWith("GET_MY_GROUPS:")) {
            String userId = command.split(":")[1];
            List<String[]> groups = GroupDAO.getGroupsOfUser(userId);
            StringBuilder sb = new StringBuilder("MY_GROUPS");
            for (String[] g : groups)
                sb.append("|").append(g[0]).append(";").append(g[1]).append(";").append(g[2]);
            sendObject(sb.toString());

        } else if (command.startsWith("ADD_MEMBER:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                GroupDAO.addMember(p[1], p[2]);
                String notif = "MEMBER_ADDED:" + p[1] + ":" + p[2];
                for (model.User m : GroupDAO.getGroupMembers(p[1]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("REMOVE_MEMBER:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                List<model.User> members = GroupDAO.getGroupMembers(p[1]);
                GroupDAO.removeMember(p[1], p[2]);
                String notif = "MEMBER_REMOVED:" + p[1] + ":" + p[2];
                for (model.User m : members) server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("PROMOTE_ADMIN:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                GroupDAO.promoteToAdmin(p[1], p[2]);
                String notif = "MEMBER_ROLE_UPDATED:" + p[1] + ":" + p[2] + ":admin";
                for (model.User m : GroupDAO.getGroupMembers(p[1]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("DEMOTE_MEMBER:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                GroupDAO.demoteToMember(p[1], p[2]);
                String notif = "MEMBER_ROLE_UPDATED:" + p[1] + ":" + p[2] + ":member";
                for (model.User m : GroupDAO.getGroupMembers(p[1]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("DELETE_GROUP:")) {
            String gId = command.split(":")[1];
            List<model.User> members = GroupDAO.getGroupMembers(gId);
            GroupDAO.deleteGroup(gId);
            for (model.User m : members) server.broadcastCommand("GROUP_DELETED:" + gId, m.getUsername());

        } else if (command.startsWith("UPDATE_GROUP:")) {
            String[] p = command.split(":", 4);
            if (p.length >= 3)
                for (model.User m : GroupDAO.getGroupMembers(p[1]))
                    server.broadcastCommand(command, m.getUsername());

        } else if (command.startsWith("START_MEETING:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                String grpId = p[1], type = p[2];
                String meetId = UUID.randomUUID().toString();

                MeetingDAO.createMeeting(meetId, grpId, user.getId(), type);
                MeetingDAO.updateMeetingStatus(meetId, "ACTIVE");

                // FIX : enregistrer l'hôte immédiatement
                this.currentMeetingId = meetId;
                server.joinMeeting(meetId, user.getUsername());

                String notif = "MEETING_STARTED:" + meetId + ":" + grpId + ":" + type + ":" + user.getUsername();
                for (model.User m : GroupDAO.getGroupMembers(grpId))
                    if (!m.getUsername().equals(user.getUsername()))
                        server.broadcastCommand(notif, m.getUsername());

                sendObject("MEETING_STARTED:" + meetId + ":" + grpId + ":" + type);
                System.out.println("[MEETING] Démarré : " + meetId + " | Hôte : " + user.getUsername());
            }

        } else if (command.startsWith("END_MEETING:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                MeetingDAO.updateMeetingStatus(p[1], "ENDED");
                server.leaveMeeting(p[1], user.getUsername());
                currentMeetingId = null;
                String notif = "MEETING_ENDED:" + p[1];
                for (model.User m : GroupDAO.getGroupMembers(p[2]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("JOIN_MEETING:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                currentMeetingId = p[1];
                java.util.Set<String> already = server.getMeetingParticipants(p[1]);
                server.joinMeeting(p[1], user.getUsername());
                String notif = "PARTICIPANT_JOINED:" + p[1] + ":" + user.getUsername();
                for (model.User m : GroupDAO.getGroupMembers(p[2]))
                    if (!m.getUsername().equals(user.getUsername()))
                        server.broadcastCommand(notif, m.getUsername());
                sendObject("JOINED_MEETING:" + p[1]);
                for (String ex : already)
                    sendObject("PARTICIPANT_JOINED:" + p[1] + ":" + ex);
            }

        } else if (command.startsWith("LEAVE_MEETING:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                server.leaveMeeting(p[1], user.getUsername());
                currentMeetingId = null;
                String notif = "PARTICIPANT_LEFT:" + p[1] + ":" + user.getUsername();
                for (model.User m : GroupDAO.getGroupMembers(p[2]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("MEETING_MUTE:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                String notif = "MEETING_MUTE_NOTIF:" + p[1] + ":" + user.getUsername() + ":" + p[2];
                for (String part : server.getMeetingParticipants(p[1]))
                    if (!part.equals(user.getUsername())) server.broadcastCommand(notif, part);
            }

        } else if (command.startsWith("GROUP_DELETE_MSG:")) {
            String[] p = command.split(":");
            if (p.length == 3) {
                GroupDAO.deleteGroupMessage(p[1]);
                String notif = "GROUP_DELETE_MSG:" + p[1];
                for (model.User m : GroupDAO.getGroupMembers(p[2]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("GROUP_EDIT_MSG:")) {
            String[] p = command.split(":", 4);
            if (p.length == 4) {
                GroupDAO.editGroupMessage(p[1], p[3]);
                String notif = "GROUP_EDIT_MSG:" + p[1] + ":" + p[3];
                for (model.User m : GroupDAO.getGroupMembers(p[2]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("GROUP_PIN_MSG:")) {
            String[] p = command.split(":");
            if (p.length == 4) {
                GroupDAO.pinGroupMessage(p[1], Boolean.parseBoolean(p[3]));
                String notif = "GROUP_PIN_MSG:" + p[1] + ":" + p[3];
                for (model.User m : GroupDAO.getGroupMembers(p[2]))
                    server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("GROUP_FILE:")) {
            int p1 = command.indexOf(':'), p2 = command.indexOf(':', p1+1),
                    p3 = command.indexOf(':', p2+1);
            if (p3 != -1) {
                String grpId = command.substring(p1+1, p2);
                String fileName = command.substring(p2+1, p3);
                String b64 = command.substring(p3+1);
                String msgId = UUID.randomUUID().toString();
                GroupDAO.saveGroupMessage(msgId, grpId, user.getId(), fileName + "||" + b64, "FILE", null);
                String notif = "GROUP_FILE:" + grpId + ":" + msgId + ":" + user.getUsername()
                        + ":" + fileName + ":" + b64;
                for (model.User m : GroupDAO.getGroupMembers(grpId))
                    if (!m.getUsername().equals(user.getUsername()))
                        server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("GROUP_VOICE:")) {
            int p1 = command.indexOf(':'), p2 = command.indexOf(':', p1+1);
            if (p2 != -1) {
                String grpId = command.substring(p1+1, p2);
                String b64   = command.substring(p2+1);
                String msgId = UUID.randomUUID().toString();
                GroupDAO.saveGroupMessage(msgId, grpId, user.getId(), b64, "VOICE", null);
                String notif = "GROUP_VOICE:" + grpId + ":" + msgId + ":" + user.getUsername() + ":" + b64;
                for (model.User m : GroupDAO.getGroupMembers(grpId))
                    if (!m.getUsername().equals(user.getUsername()))
                        server.broadcastCommand(notif, m.getUsername());
            }

        } else if (command.startsWith("GROUP_INVITE:")) {
            String[] p = command.split(":", 4);
            if (p.length == 4) {
                String notif = "GROUP_INVITE_NOTIF:" + p[1] + ":" + p[2] + ":" + user.getUsername();
                model.User invited = database.UserDAO.searchUserByUsername(server.getUsernameById(p[3]));
                if (invited != null) server.broadcastCommand(notif, invited.getUsername());
            }

        } else if (command.startsWith("GROUP_INVITE_RESPONSE:")) {
            String[] p = command.split(":", 4);
            if (p.length == 4) {
                if ("ACCEPT".equals(p[2])) {
                    GroupDAO.addMemberWithRole(p[1], user.getId(), "member");
                    String notif = "MEMBER_JOINED:" + p[1] + ":" + user.getUsername();
                    for (model.User m : GroupDAO.getGroupMembers(p[1]))
                        server.broadcastCommand(notif, m.getUsername());
                    sendObject("GROUP_JOIN_SUCCESS:" + p[1]);
                } else {
                    server.broadcastCommand("INVITE_REFUSED:" + p[1] + ":" + user.getUsername(), p[3]);
                }
            }

        } else if (command.equals("GET_PENDING_INVITATIONS")) {
            for (String[] inv : database.GroupDAO.getPendingInvitations(user.getId()))
                sendObject("GROUP_INVITE_NOTIF:" + inv[1] + ":" + inv[2] + ":" + inv[3]);
        }
    }

    public void handleCall(call.Call call) {
        System.out.println("Appel : " + call.getCaller().getUsername()
                + " → " + call.getCalled().getUsername() + " | " + call.getStatus());

        if (call.getStatus() == model.CallStatus.PENDING) {
            CallDAO.saveCall(call.getId(), call.getCaller().getId(), call.getCalled().getId(),
                    call.getCallType().name(), call.getStatus().name());
            server.forwardCall(call, call.getCalled().getUsername());

        } else if (call.getStatus() == model.CallStatus.ACTIVE) {
            this.currentCallPartner = call.getCaller().getUsername();
            ClientHandler ch = server.getClientHandler(call.getCaller().getUsername());
            if (ch != null) ch.setCurrentCallPartner(call.getCalled().getUsername());
            server.forwardCall(call, call.getCaller().getUsername());

        } else {
            this.currentCallPartner = null;
            String target = call.getCaller().getUsername().equals(user.getUsername())
                    ? call.getCalled().getUsername() : call.getCaller().getUsername();
            ClientHandler other = server.getClientHandler(target);
            if (other != null) other.setCurrentCallPartner(null);
            CallDAO.updateCallStatus(call.getId(), call.getStatus().name());
            server.forwardCall(call, target);
        }
    }

    public void handleIncomingCall() {}

    public void setCurrentCallPartner(String u) { this.currentCallPartner = u; }

    public void sendMessage(Message message) { sendObject(message); }
    public void sendUserList(List<User> users) { sendObject(users); }
    public void sendAudioData(byte[] data)    { sendObject(data); }
    public void sendVideoData(byte[] data)    { sendObject(data); }

    public void sendObject(Object obj) {
        try {
            if (out != null) { out.writeObject(obj); out.flush(); out.reset(); }
        } catch (IOException e) {
            System.out.println("Erreur envoi : " + e.getMessage());
        }
    }

    public void closeConnection() {
        try {
            if (user != null) server.removeClient(user);
            if (in != null)  in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("Erreur fermeture : " + e.getMessage());
        }
    }

    public User getUser() { return user; }

    private boolean bytesStartWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (data[i] != prefix[i]) return false;
        return true;
    }

    public void sendMessage(model.Message message, boolean s) { sendObject(message); }
}