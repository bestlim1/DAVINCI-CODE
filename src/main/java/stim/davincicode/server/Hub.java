package stim.davincicode.server;

import static stim.davincicode.common.Proto.encode;
import static stim.davincicode.common.Proto.record;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import stim.davincicode.common.ChatFilter;
import stim.davincicode.common.GameView;
import stim.davincicode.common.GameView.Phase;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Names;
import stim.davincicode.common.Proto;

/**
 * 서버의 모든 상태(접속자, 로비, 방, 게임)를 관리한다.
 * 이 클래스의 메서드는 전부 GameServer의 게임 로직 스레드 하나에서만 실행된다.
 * 그래서 synchronized 없이도 두 명이 동시에 보낸 메시지가 섞이지 않는다 (도착한 순서대로 하나씩 처리).
 */
final class Hub {
    static final long GRACE_MS = 60_000;
    private static final int MAX_USERS = 500;
    private static final int MAX_ROOMS = 200;
    private static final int MAX_SPECTATORS = 10;
    static final long SETUP_MS = 20_000; // 시작 패 색을 고르는 시간
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GameServer server;
    private final Reports reports;
    private final Random rnd;
    private final Map<Integer, User> users = new LinkedHashMap<>();
    private final Map<String, User> byKey = new HashMap<>();
    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private final Set<Connection> conns = new HashSet<>();
    private final Deque<String> recentChat = new ArrayDeque<>();
    private int nextUserId = 1;
    private int nextRoomId = 1;
    private boolean lobbyDirty;

    Hub(GameServer server, Reports reports, Random rnd) {
        this.server = server;
        this.reports = reports;
        this.rnd = rnd;
        server.repeat(this::flushLobby, 250);
        server.repeat(this::checkIdle, 5000);
    }

    // ================================================================ 연결

    void onConnect(Connection c) {
        conns.add(c);
    }

    void onMessage(Connection c, String line) {
        if (c.closed) return;
        Msg m = Msg.parse(line);
        if (m.cmd.equals(Proto.PING)) {
            c.send(encode(Proto.PONG, m.s(0)));
            return;
        }
        User u = c.user;
        server.debug("[수신] " + (u == null ? c.remote : u.name) + " : " + Proto.pretty(line));
        if (u == null) {
            if (m.cmd.equals(Proto.HELLO)) hello(c, m);
            else c.send(error("NOT_HELLO", "먼저 접속 인사를 보내야 해요."));
            return;
        }
        switch (m.cmd) {
            case Proto.CHAT: chat(u, m.s(0)); break;
            case Proto.WHISPER: whisper(u, m.s(0), m.s(1)); break;
            case Proto.REACT: react(u, m.i(0, -1)); break;
            case Proto.CREATE: create(u, m); break;
            case Proto.JOIN: join(u, m.i(0, -1), m.s(1)); break;
            case Proto.SPECTATE: spectate(u, m.i(0, -1), m.s(1)); break;
            case Proto.QUICK: quick(u); break;
            case Proto.LEAVE: leaveRoom(u, true); break;
            case Proto.READY: ready(u, m.b(0)); break;
            case Proto.SETTINGS: settings(u, m); break;
            case Proto.KICK: kick(u, m.i(0, -1)); break;
            case Proto.START: start(u); break;
            case Proto.PICK:
            case Proto.DRAW:
            case Proto.JOKER:
            case Proto.GUESS:
            case Proto.STOP:
            case Proto.REVEAL:
                gameAction(u, m);
                break;
            case Proto.REPORT: report(u, m.s(0), m.s(1)); break;
            case Proto.PROFILE: profile(u, m); break;
            case Proto.HELLO: u.send(error("ALREADY", "이미 접속해 있어요.")); break;
            default: u.send(error("UNKNOWN", "알 수 없는 요청이에요: " + m.cmd));
        }
    }

    private void hello(Connection c, Msg m) {
        if (m.i(0, 0) != Proto.VERSION) {
            c.sendAndClose(error("VERSION", "앱 버전이 서버와 달라요. 최신 버전으로 업데이트해 주세요."));
            return;
        }
        String device = Proto.clean(m.s(1), 64);
        String name = Proto.clean(m.s(2), 20);
        String why = Names.checkName(name);
        if (device.length() < 8) why = "기기 정보를 확인할 수 없어요.";
        if (why != null) {
            c.sendAndClose(error("BAD_NAME", why));
            return;
        }
        int avatar = Math.max(0, Math.min(Names.AVATARS - 1, m.i(3, 0)));
        String key = device + ":" + name.toLowerCase();

        User existing = byKey.get(key);
        if (existing != null) {
            // 같은 기기, 같은 닉네임 → 재접속 (또는 다른 창에서 다시 들어옴)
            if (existing.conn != null) {
                Connection old = existing.conn;
                old.user = null;
                old.sendAndClose(encode(Proto.KICKED, "다른 창에서 같은 닉네임으로 접속해서 이 연결을 끊었어요."));
                conns.remove(old);
            }
            if (existing.grace != null) {
                existing.grace.cancel(false);
                existing.grace = null;
            }
            existing.conn = c;
            existing.avatar = avatar;
            c.user = existing;
            Room r = existing.room;
            c.send(encode(Proto.WELCOME, existing.id, existing.name, server.name(), Proto.bool(r != null)));
            server.log("재접속: " + existing.name + (r != null ? " (방 #" + r.id + ")" : ""));
            if (r != null) {
                if (r.game != null) r.game.setConnected(existing.id, true);
                sys(r, existing.name + "님이 다시 연결됐어요.");
                sendRoom(r);
                if (r.game != null) {
                    // 내 차례였다면 줄어든 제한 시간을 원래대로 돌리고, 아니면 다른 사람 타이머는 건드리지 않는다
                    if (isTurnOf(r, existing.id)) rearm(r);
                    else sendState(r);
                }
            } else {
                c.send(lobbyLine());
            }
            lobbyDirty = true;
            updateOnline();
            return;
        }
        for (User o : users.values()) {
            if (o.name.equalsIgnoreCase(name)) {
                c.sendAndClose(error("NAME_TAKEN", "이미 접속 중인 닉네임이에요. 다른 닉네임을 써 주세요."));
                return;
            }
        }
        if (users.size() >= MAX_USERS) {
            c.sendAndClose(error("FULL", "서버가 꽉 찼어요. 잠시 후 다시 접속해 주세요."));
            return;
        }
        User u = new User(nextUserId++, key);
        u.name = name;
        u.avatar = avatar;
        u.conn = c;
        c.user = u;
        users.put(u.id, u);
        byKey.put(key, u);
        c.send(encode(Proto.WELCOME, u.id, u.name, server.name(), "0"));
        c.send(lobbyLine());
        server.log("접속: " + u.name + " (" + c.kind() + ", " + c.remote + ", 현재 " + users.size() + "명)");
        lobbyDirty = true;
        updateOnline();
    }

    void onDisconnect(Connection c) {
        conns.remove(c);
        User u = c.user;
        if (u == null || u.conn != c) return; // 이미 다른 연결로 바뀐 경우
        c.user = null;
        u.conn = null;
        Room r = u.room;
        if (r != null && r.game != null && !u.spectator) {
            Game.Seat seat = r.game.seat(u.id);
            if (seat != null && seat.alive) {
                // 게임 중이면 바로 내보내지 않고 재접속을 기다린다
                r.game.setConnected(u.id, false);
                server.log("연결 끊김 (재접속 대기): " + u.name);
                sys(r, u.name + "님의 연결이 끊겼어요. 60초 안에 돌아오지 않으면 기권 처리돼요.");
                u.grace = server.schedule(() -> graceExpired(u), GRACE_MS);
                if (isTurnOf(r, u.id)) rearm(r); // 끊긴 사람 차례면 제한 시간을 줄인다
                else sendState(r);
                lobbyDirty = true;
                updateOnline();
                return;
            }
        }
        server.log("접속 종료: " + u.name);
        removeUser(u);
    }

    private void graceExpired(User u) {
        u.grace = null;
        if (u.conn != null) return;
        server.log("재접속 시간 초과: " + u.name);
        removeUser(u);
    }

    private void removeUser(User u) {
        if (u.room != null) leaveRoom(u, false);
        users.remove(u.id);
        byKey.remove(u.key);
        lobbyDirty = true;
        updateOnline();
    }

    private void checkIdle() {
        long now = System.currentTimeMillis();
        for (Connection c : new ArrayList<>(conns)) {
            if (c.user == null && now - c.connectedAt > 15_000) {
                c.close();
            } else if (now - c.lastSeen > 45_000) {
                server.log("응답 없음으로 연결 종료: " + (c.user != null ? c.user.name : c.remote));
                c.close();
            }
        }
    }

    private void updateOnline() {
        int n = 0;
        for (User u : users.values()) if (u.conn != null) n++;
        server.setOnline(n);
    }

    // ================================================================ 채팅

    private boolean allowChat(User u) {
        long now = System.currentTimeMillis();
        if (now - u.chatWindow > 5000) {
            u.chatWindow = now;
            u.chatCount = 0;
        }
        return ++u.chatCount <= 5;
    }

    private void remember(String scope, User u, String text) {
        recentChat.addLast(LocalTime.now().format(HM) + " [" + scope + "] " + u.name + ": " + text);
        while (recentChat.size() > 300) recentChat.removeFirst();
    }

    private void chat(User u, String raw) {
        String text = Proto.clean(raw, Names.CHAT_MAX);
        if (text.isEmpty()) return;
        if (!allowChat(u)) {
            u.send(error("CHAT_FLOOD", "메시지를 너무 빨리 보내고 있어요. 잠시 후에 보내 주세요."));
            return;
        }
        text = ChatFilter.filter(text);
        if (u.room == null) {
            String line = encode(Proto.CHAT, "L", u.id, u.name, u.avatar, text, "");
            for (User o : users.values()) if (o.room == null) o.send(line);
            remember("로비", u, text);
        } else {
            Room r = u.room;
            String line = encode(Proto.CHAT, "R", u.id, u.name, u.avatar, text, u.spectator ? "S" : "");
            for (User o : r.audience()) o.send(line);
            remember("방" + r.id, u, text);
        }
    }

    private void whisper(User u, String targetName, String raw) {
        String text = Proto.clean(raw, Names.CHAT_MAX);
        if (text.isEmpty()) return;
        User t = null;
        for (User o : users.values()) {
            if (o.conn != null && o.name.equalsIgnoreCase(targetName.trim())) t = o;
        }
        if (t == null) {
            u.send(error("NO_USER", "'" + Proto.clean(targetName, 20) + "'님은 지금 접속해 있지 않아요."));
            return;
        }
        if (t == u) {
            u.send(error("SELF", "나에게는 귓속말을 보낼 수 없어요."));
            return;
        }
        if (!allowChat(u)) {
            u.send(error("CHAT_FLOOD", "메시지를 너무 빨리 보내고 있어요. 잠시 후에 보내 주세요."));
            return;
        }
        text = ChatFilter.filter(text);
        String line = encode(Proto.WHISPER, u.name, t.name, u.avatar, text);
        u.send(line);
        t.send(line);
        remember("귓속말→" + t.name, u, text);
    }

    private void react(User u, int code) {
        Room r = u.room;
        if (r == null || code < 0 || code > 5) return;
        long now = System.currentTimeMillis();
        if (now - u.lastReact < 1200) return;
        u.lastReact = now;
        String line = encode(Proto.REACT, u.id, code);
        for (User o : r.audience()) o.send(line);
    }

    private void sys(Room r, String text) {
        String line = encode(Proto.SYS, "R", text);
        for (User o : r.audience()) o.send(line);
    }

    /** 로비에서 닉네임·캐릭터 바꾸기 */
    private void profile(User u, Msg m) {
        if (u.room != null) {
            u.send(error("IN_ROOM", "방에 있을 때는 프로필을 바꿀 수 없어요. 로비에서 바꿔 주세요."));
            return;
        }
        long now = System.currentTimeMillis();
        if (now - u.lastProfile < 2000) {
            u.send(error("PROFILE_WAIT", "잠시 후에 다시 바꿔 주세요."));
            return;
        }
        String name = Proto.clean(m.s(0), 20);
        String why = Names.checkName(name);
        if (why != null) {
            u.send(error("BAD_NAME", why));
            return;
        }
        for (User o : users.values()) {
            if (o != u && o.name.equalsIgnoreCase(name)) {
                u.send(error("NAME_TAKEN", "이미 접속 중인 닉네임이에요. 다른 닉네임을 써 주세요."));
                return;
            }
        }
        u.lastProfile = now;
        String newKey = u.key.substring(0, u.key.lastIndexOf(':') + 1) + name.toLowerCase();
        if (!newKey.equals(u.key)) {
            byKey.remove(u.key);
            u.key = newKey;
            byKey.put(newKey, u);
        }
        String old = u.name;
        u.name = name;
        u.avatar = Math.max(0, Math.min(Names.AVATARS - 1, m.i(1, u.avatar)));
        u.send(encode(Proto.PROFILE, u.name, u.avatar));
        if (!old.equals(name)) server.log("닉네임 변경: " + old + " → " + name);
        lobbyDirty = true;
    }

    private void report(User u, String targetName, String reason) {
        long now = System.currentTimeMillis();
        if (now - u.lastReport < 30_000) {
            u.send(error("REPORT_WAIT", "신고는 30초에 한 번만 할 수 있어요."));
            return;
        }
        u.lastReport = now;
        String target = Proto.clean(targetName, 20);
        StringBuilder sb = new StringBuilder();
        sb.append("신고자=").append(u.name).append(" 대상=").append(target)
          .append(" 사유=").append(Proto.clean(reason, 100));
        for (String line : recentChat) {
            if (line.contains("] " + target + ":")) sb.append(System.lineSeparator()).append("    ").append(line);
        }
        reports.add(sb.toString());
        u.send(encode(Proto.NOTICE, "신고가 접수됐어요. 운영진이 확인할게요."));
    }

    // ================================================================ 로비

    private String lobbyLine() {
        List<String> rs = new ArrayList<>();
        for (Room r : rooms.values()) {
            rs.add(record(r.id, r.title, r.game != null ? "P" : "W", r.players.size(), r.max,
                    Proto.bool(r.locked()), r.turnSec, r.host != null ? r.host.name : "", r.spectators.size()));
            if (rs.size() >= 100) break;
        }
        List<String> us = new ArrayList<>();
        for (User u : users.values()) {
            if (u.conn != null) us.add(record(u.id, u.name, u.avatar, u.where()));
            if (us.size() >= 200) break;
        }
        return encode(Proto.LOBBY, Proto.list(rs), Proto.list(us), server.name());
    }

    private void flushLobby() {
        if (!lobbyDirty) return;
        lobbyDirty = false;
        String line = lobbyLine();
        for (User u : users.values()) if (u.room == null) u.send(line);
    }

    // ================================================================ 방

    private Room requireHostWaiting(User u) {
        Room r = u.room;
        if (r == null || u.spectator) {
            u.send(error("NO_ROOM", "방에 들어가 있지 않아요."));
            return null;
        }
        if (r.host != u) {
            u.send(error("NOT_HOST", "방장만 할 수 있어요."));
            return null;
        }
        if (r.game != null) {
            u.send(error("PLAYING", "게임 중에는 바꿀 수 없어요."));
            return null;
        }
        return r;
    }

    private void applySettings(Room r, Msg m, String fallbackTitle) {
        r.title = Names.cleanTitle(m.s(0), fallbackTitle);
        r.max = Math.max(2, Math.min(4, m.i(1, 4)));
        r.password = Proto.clean(m.s(2), 12);
        r.turnSec = Room.normalizeTurn(m.i(3, 60));
    }

    private void create(User u, Msg m) {
        if (u.room != null) {
            u.send(error("ALREADY", "이미 방에 들어가 있어요."));
            return;
        }
        if (rooms.size() >= MAX_ROOMS) {
            u.send(error("ROOMS_FULL", "방이 너무 많아요. 다른 방에 들어가 보세요."));
            return;
        }
        Room r = new Room(nextRoomId++);
        applySettings(r, m, u.name + "님의 방");
        r.host = u;
        rooms.put(r.id, r);
        enterAsPlayer(u, r);
        server.log("방 생성: #" + r.id + " " + r.title + " (" + u.name + ")");
        sys(r, "방을 만들었어요. 친구가 들어와서 준비하면 시작할 수 있어요.");
    }

    private void enterAsPlayer(User u, Room r) {
        u.room = r;
        u.spectator = false;
        u.ready = false;
        r.players.add(u);
        sendRoom(r);
        lobbyDirty = true;
    }

    private void join(User u, int roomId, String pw) {
        Room r = rooms.get(roomId);
        if (r == null) {
            u.send(error("NO_ROOM", "방이 사라졌어요."));
            return;
        }
        if (u.room == r && u.spectator) {
            if (r.game != null) {
                u.send(error("PLAYING", "게임이 끝나면 참가할 수 있어요."));
            } else if (r.players.size() >= r.max) {
                u.send(error("FULL", "자리가 없어요."));
            } else {
                r.spectators.remove(u);
                enterAsPlayer(u, r);
                sys(r, u.name + "님이 관전을 마치고 참가했어요.");
            }
            return;
        }
        if (u.room != null) {
            u.send(error("ALREADY", "이미 방에 들어가 있어요."));
            return;
        }
        if (r.kicked.contains(u.key)) {
            u.send(error("KICKED", "내보내진 방에는 다시 들어갈 수 없어요."));
            return;
        }
        if (r.locked() && !r.password.equals(pw)) {
            u.send(error("BAD_PASSWORD", "비밀번호가 맞지 않아요."));
            return;
        }
        if (r.game != null) {
            u.send(error("PLAYING", "게임이 진행 중이에요. 관전으로 들어가 보세요."));
            return;
        }
        if (r.players.size() >= r.max) {
            u.send(error("FULL", "방이 꽉 찼어요."));
            return;
        }
        enterAsPlayer(u, r);
        sys(r, u.name + "님이 들어왔어요.");
    }

    private void spectate(User u, int roomId, String pw) {
        Room r = rooms.get(roomId);
        if (r == null) {
            u.send(error("NO_ROOM", "방이 사라졌어요."));
            return;
        }
        if (u.room != null) {
            u.send(error("ALREADY", "이미 방에 들어가 있어요."));
            return;
        }
        if (r.locked() && !r.password.equals(pw)) {
            u.send(error("BAD_PASSWORD", "비밀번호가 맞지 않아요."));
            return;
        }
        if (r.kicked.contains(u.key)) {
            u.send(error("KICKED", "내보내진 방에는 들어갈 수 없어요."));
            return;
        }
        if (r.spectators.size() >= MAX_SPECTATORS) {
            u.send(error("FULL", "관전 자리가 꽉 찼어요."));
            return;
        }
        u.room = r;
        u.spectator = true;
        u.ready = false;
        r.spectators.add(u);
        sendRoom(r);
        if (r.game != null) {
            long remaining = r.deadline > 0 ? Math.max(0, r.deadline - System.currentTimeMillis()) : -1;
            u.send(r.game.snapshot(0, remaining));
        }
        sys(r, u.name + "님이 관전을 시작했어요.");
        lobbyDirty = true;
    }

    private void quick(User u) {
        if (u.room != null) {
            u.send(error("ALREADY", "이미 방에 들어가 있어요."));
            return;
        }
        Room best = null;
        for (Room r : rooms.values()) {
            if (r.game != null || r.locked() || r.players.size() >= r.max || r.players.isEmpty()) continue;
            if (r.kicked.contains(u.key)) continue;
            if (best == null || r.players.size() > best.players.size()) best = r;
        }
        if (best != null) {
            enterAsPlayer(u, best);
            sys(best, u.name + "님이 빠른 시작으로 들어왔어요.");
            return;
        }
        Room r = new Room(nextRoomId++);
        r.title = u.name + "님의 빠른 대전";
        r.host = u;
        rooms.put(r.id, r);
        enterAsPlayer(u, r);
        sys(r, "들어갈 수 있는 방이 없어서 새 방을 만들었어요. 다른 사람이 오면 바로 시작할 수 있어요.");
    }

    private void ready(User u, boolean on) {
        Room r = u.room;
        if (r == null || u.spectator || r.game != null || r.host == u) return;
        u.ready = on;
        sendRoom(r);
    }

    private void settings(User u, Msg m) {
        Room r = requireHostWaiting(u);
        if (r == null) return;
        int max = Math.max(2, Math.min(4, m.i(1, 4)));
        if (max < r.players.size()) {
            u.send(error("TOO_SMALL", "지금 인원(" + r.players.size() + "명)보다 적게 줄일 수 없어요."));
            sendRoom(r);
            return;
        }
        String oldPassword = r.password;
        String before = rules(r);
        applySettings(r, m, r.title);
        if ("*".equals(m.s(2))) r.password = oldPassword; // "*"는 비밀번호를 그대로 둔다는 뜻
        boolean changed = !before.equals(rules(r));
        if (changed) for (User p : r.players) p.ready = false;
        sendRoom(r);
        if (changed) sys(r, "규칙이 바뀌었어요: " + rules(r) + ". 다시 준비해 주세요.");
        lobbyDirty = true;
    }

    private static String rules(Room r) {
        return r.max + "명 · " + (r.turnSec == 0 ? "시간 무제한" : r.turnSec + "초");
    }

    private void kick(User u, int targetId) {
        Room r = requireHostWaiting(u);
        if (r == null) return;
        User t = null;
        for (User p : r.players) if (p.id == targetId) t = p;
        if (t == null || t == u) return;
        r.players.remove(t);
        t.room = null;
        t.ready = false;
        r.kicked.add(t.key);
        t.send(encode(Proto.LEFT, "방장이 방에서 내보냈어요."));
        t.send(lobbyLine());
        sys(r, t.name + "님이 방에서 나갔어요.");
        sendRoom(r);
        lobbyDirty = true;
    }

    /** notify가 true면 본인에게 LEFT와 로비 정보를 보낸다 (연결이 끊긴 경우에는 false). */
    private void leaveRoom(User u, boolean notify) {
        Room r = u.room;
        if (r == null) return;
        if (u.spectator) {
            r.spectators.remove(u);
            u.room = null;
            u.spectator = false;
            sys(r, u.name + "님이 관전을 마쳤어요.");
            sendRoom(r);
        } else {
            r.players.remove(u);
            u.room = null;
            u.ready = false;
            if (u.grace != null) {
                u.grace.cancel(false);
                u.grace = null;
            }
            if (r.game != null && r.game.seat(u.id) != null && r.game.seat(u.id).alive) {
                sys(r, u.name + "님이 게임을 나가서 기권 처리됐어요.");
                afterGame(r, r.game.forfeit(u.id));
            } else {
                sys(r, u.name + "님이 나갔어요.");
            }
            if (rooms.containsKey(r.id)) fixHostOrClose(r);
        }
        if (notify) {
            u.send(encode(Proto.LEFT, ""));
            u.send(lobbyLine());
        }
        lobbyDirty = true;
    }

    /** 사람이 아무도 없으면 방을 닫고, 방장이 나갔으면 다음 사람에게 넘긴다. */
    private void fixHostOrClose(Room r) {
        if (r.players.isEmpty()) {
            closeRoom(r, "방에 참가자가 없어서 방이 닫혔어요.");
            return;
        }
        if (r.host == null || !r.players.contains(r.host)) {
            User p = r.players.get(0);
            r.host = p;
            p.ready = false;
            sys(r, p.name + "님이 새 방장이 됐어요.");
        }
        sendRoom(r);
    }

    private void closeRoom(Room r, String reason) {
        r.cancelTimers();
        rooms.remove(r.id);
        for (User u : r.audience()) {
            u.room = null;
            u.spectator = false;
            u.ready = false;
            if (u.conn == null) {
                if (u.grace != null) u.grace.cancel(false);
                users.remove(u.id);
                byKey.remove(u.key);
                continue;
            }
            u.send(encode(Proto.LEFT, reason));
            u.send(lobbyLine());
        }
        r.players.clear();
        r.spectators.clear();
        server.log("방 닫힘: #" + r.id);
        lobbyDirty = true;
        updateOnline();
    }

    private void sendRoom(Room r) {
        List<String> ms = new ArrayList<>();
        for (User p : r.players) {
            ms.add(record(p.id, p.name, p.avatar, Proto.bool(p.ready), Proto.bool(p.online())));
        }
        List<String> ss = new ArrayList<>();
        for (User s : r.spectators) ss.add(record(s.id, s.name, s.avatar));
        String line = encode(Proto.ROOM, r.id, r.title, r.host != null ? r.host.id : 0, r.game != null ? "P" : "W",
                r.max, Proto.bool(r.locked()), r.turnSec, Proto.list(ms), Proto.list(ss));
        for (User u : r.audience()) u.send(line);
    }

    // ================================================================ 게임

    private void start(User u) {
        Room r = requireHostWaiting(u);
        if (r == null) return;
        if (r.players.size() < 2) {
            u.send(error("NEED_PLAYERS", "2명 이상이어야 시작할 수 있어요. 친구를 기다려 주세요."));
            return;
        }
        for (User p : r.players) {
            if (p != r.host && (!p.ready || p.conn == null)) {
                u.send(error("NOT_READY", p.name + "님이 아직 준비하지 않았어요."));
                return;
            }
        }
        List<User> order = new ArrayList<>(r.players);
        Collections.shuffle(order, rnd);
        List<Integer> pids = new ArrayList<>();
        r.gameUsers.clear();
        for (User p : order) {
            pids.add(p.id);
            r.gameUsers.put(p.id, p);
        }
        r.game = new Game(pids, r.turnSec, rnd);
        for (User p : r.players) p.ready = false;
        server.log("게임 시작: 방 #" + r.id + " " + pids.size() + "명");
        sendRoom(r);
        sys(r, "게임을 시작합니다! 먼저 시작 패로 검정과 흰색을 몇 개씩 받을지 골라 주세요.");
        afterGame(r, r.game.start());
        lobbyDirty = true;
    }

    private void gameAction(User u, Msg m) {
        Room r = u.room;
        if (r == null || r.game == null || u.spectator) {
            u.send(error("NOT_PLAYING", "게임에 참가하고 있지 않아요."));
            return;
        }
        Game g = r.game;
        List<Game.Event> ev;
        try {
            switch (m.cmd) {
                case Proto.PICK:
                    ev = g.pick(u.id, m.i(0, -1));
                    break;
                case Proto.DRAW:
                    ev = g.draw(u.id, m.s(0).isEmpty() ? '?' : m.s(0).charAt(0));
                    break;
                case Proto.JOKER:
                    ev = g.placeJoker(u.id, m.i(0, -1));
                    break;
                case Proto.GUESS:
                    ev = g.guess(u.id, m.i(0, 0), m.i(1, -1), "J".equals(m.s(2)) ? GameView.JOKER : m.i(2, -99));
                    break;
                case Proto.STOP:
                    ev = g.stop(u.id);
                    break;
                default:
                    ev = g.revealSelf(u.id, m.i(0, -1));
            }
        } catch (Game.Illegal e) {
            u.send(error("BAD_MOVE", e.getMessage()));
            return;
        }
        afterGame(r, ev);
    }

    /** 게임 상태가 바뀐 뒤: 이벤트 전송 → 끝났는지 확인 → 타이머 예약 → 사람마다 다른 STATE 전송 */
    private void afterGame(Room r, List<Game.Event> ev) {
        for (Game.Event e : ev) {
            String line = e.encode();
            if (e.onlyFor == 0) {
                for (User a : r.audience()) a.send(line);
            } else {
                User t = r.member(e.onlyFor);
                if (t != null) t.send(line);
            }
        }
        if (r.game.over()) {
            endGame(r);
            return;
        }
        rearm(r);
    }

    /** 현재 단계에 맞게 제한 시간을 다시 걸고 상태를 보낸다. */
    private void rearm(Room r) {
        r.seq++;
        r.cancelTimers();
        long dur = phaseDuration(r);
        r.deadline = dur > 0 ? System.currentTimeMillis() + dur : 0;
        if (dur > 0) {
            int seq = r.seq;
            r.timer = server.schedule(() -> onTimeout(r, seq), dur);
        }
        sendState(r);
    }

    /** 지금 pid가 행동할 차례인가 (시작 패를 고르는 동안은 모두 동시에 고르므로 차례가 없다) */
    private static boolean isTurnOf(Room r, int pid) {
        return r.game.phase() != Phase.SETUP && r.game.currentPid() == pid;
    }

    private long phaseDuration(Room r) {
        Game g = r.game;
        // 시작 패 고르기는 누가 끊겼든 정해진 시간이 지나면 남은 사람 것을 임의로 정한다.
        // 다 고르면 바로 넘어가므로, 이미 시간이 흐르고 있었다면 남은 시간을 그대로 둔다
        if (g.phase() == Phase.SETUP) {
            if (r.deadline > 0) return Math.max(1, r.deadline - System.currentTimeMillis());
            return SETUP_MS;
        }
        User cur = r.gameUsers.get(g.currentPid());
        boolean disconnected = cur != null && cur.conn == null;
        long base = r.turnSec * 1000L;
        Phase p = g.phase();
        if (p == Phase.JOKER || p == Phase.REVEAL_SELF) base = base == 0 ? 20_000 : Math.min(base, 20_000);
        if (g.autoDrawn && p == Phase.GUESS) base = base == 0 ? 15_000 : Math.min(base, 15_000);
        if (disconnected) base = base == 0 ? 20_000 : Math.min(base, 20_000);
        return base;
    }

    private void onTimeout(Room r, int seq) {
        if (r.game == null || r.seq != seq || !rooms.containsKey(r.id)) return;
        User cur = r.gameUsers.get(r.game.currentPid());
        boolean setup = r.game.phase() == Phase.SETUP;
        server.log("시간 초과: 방 #" + r.id + " " + (setup ? "시작 패 고르기" : (cur != null ? cur.name : "?") + " " + r.game.phase()));
        afterGame(r, r.game.timeout());
    }

    private void sendState(Room r) {
        long remaining = r.deadline > 0 ? Math.max(0, r.deadline - System.currentTimeMillis()) : -1;
        for (User p : r.players) p.send(r.game.snapshot(p.id, remaining));
        if (!r.spectators.isEmpty()) {
            String spec = r.game.snapshot(0, remaining);
            for (User s : r.spectators) s.send(spec);
        }
    }

    private void endGame(Room r) {
        Game g = r.game;
        r.cancelTimers();
        r.seq++;
        r.deadline = 0;
        // 끝나면 모든 타일을 공개한 최종 화면을 보낸다
        for (User p : r.players) p.send(g.snapshot(p.id, -1));
        for (User s : r.spectators) s.send(g.snapshot(0, -1));

        List<Integer> order = g.placements();
        int n = order.size();
        List<String> recs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            User pu = r.gameUsers.get(order.get(i));
            if (pu == null) continue;
            recs.add(record(pu.id, pu.name, pu.avatar, i + 1));
        }
        String result = encode(Proto.RESULT, Proto.list(recs));
        for (User a : r.audience()) a.send(result);
        User winner = r.gameUsers.get(g.winner);
        sys(r, (winner != null ? winner.name + "님이 이겼어요!" : "게임이 끝났어요.") + " 다시 하려면 준비를 눌러 주세요.");
        server.log("게임 종료: 방 #" + r.id + " 승자 " + (winner != null ? winner.name : "?") + ", " + g.turnCount + "턴");

        r.game = null;
        r.gameUsers.clear();
        // 연결이 끊긴 채로 끝난 사람은 정리한다
        for (User p : new ArrayList<>(r.players)) {
            p.ready = false;
            if (p.conn == null) {
                r.players.remove(p);
                p.room = null;
                if (p.grace != null) p.grace.cancel(false);
                p.grace = null;
                users.remove(p.id);
                byKey.remove(p.key);
            }
        }
        fixHostOrClose(r);
        lobbyDirty = true;
        updateOnline();
    }

    // ================================================================ 운영

    private static String error(String code, String message) {
        return encode(Proto.ERROR, code, message);
    }

    void shutdown() {
        for (Connection c : new ArrayList<>(conns)) {
            c.sendAndClose(encode(Proto.KICKED, "서버가 종료됐어요. 잠시 후 다시 접속해 주세요."));
        }
    }

    /** 서버 콘솔 명령 */
    void admin(String line, Consumer<String> out) {
        String[] p = line.trim().split(" ", 2);
        String cmd = p[0].toLowerCase();
        String arg = p.length > 1 ? p[1].trim() : "";
        switch (cmd) {
            case "status":
                out.accept("접속 " + users.size() + "명, 방 " + rooms.size() + "개, 연결 " + conns.size() + "개");
                break;
            case "users":
                for (User u : users.values()) {
                    out.accept("  #" + u.id + " " + u.name + " " + u.where()
                            + (u.conn == null ? " [끊김]" : " " + u.conn.remote));
                }
                break;
            case "rooms":
                for (Room r : rooms.values()) {
                    StringBuilder sb = new StringBuilder("  #" + r.id + " " + r.title + " [" + (r.game != null ? "게임 중" : "대기") + "] ");
                    for (User u : r.players) sb.append(u.name).append(u == r.host ? "(방장) " : " ");
                    out.accept(sb.toString());
                }
                break;
            case "notice":
                if (arg.isEmpty()) break;
                for (User u : users.values()) u.send(encode(Proto.NOTICE, "[공지] " + Proto.clean(arg, 200)));
                out.accept("공지를 보냈어요.");
                break;
            case "kick": {
                for (User u : new ArrayList<>(users.values())) {
                    if (u.name.equalsIgnoreCase(arg) && u.conn != null) {
                        Connection c = u.conn;
                        c.sendAndClose(encode(Proto.KICKED, "운영자에 의해 연결이 종료됐어요."));
                        out.accept(u.name + " 내보냄");
                    }
                }
                break;
            }
            default:
                out.accept("명령어: status, users, rooms, notice <내용>, kick <닉네임>, stop");
        }
    }
}
