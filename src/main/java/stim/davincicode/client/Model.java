package stim.davincicode.client;

import java.util.ArrayList;
import java.util.List;

import stim.davincicode.common.Msg;

/** 서버가 보낸 로비·방 정보를 화면에서 쓰기 좋게 담는 객체들 */
public final class Model {
    private Model() { }

    public static final class Member {
        public int id;
        public String name;
        public int avatar;
        public boolean ready, connected = true;
    }

    public static final class RoomInfo {
        public int id;
        public String title;
        public int hostId;
        public boolean playing;
        public int max;
        public boolean locked;
        public int turnSec;
        public final List<Member> members = new ArrayList<>();
        public final List<Member> spectators = new ArrayList<>();

        public Member member(int id) {
            for (Member m : members) if (m.id == id) return m;
            for (Member m : spectators) if (m.id == id) return m;
            return null;
        }

        public boolean isSpectator(int id) {
            for (Member m : spectators) if (m.id == id) return true;
            return false;
        }

        public boolean allReady() {
            for (Member m : members) if (m.id != hostId && !m.ready) return false;
            return true;
        }
    }

    public static final class LobbyRoom {
        public int id;
        public String title;
        public boolean playing;
        public int count, max;
        public boolean locked;
        public int turnSec;
        public String host;
        public int spectators;
    }

    public static final class LobbyUser {
        public int id;
        public String name;
        public int avatar;
        public String where;
    }

    public static RoomInfo parseRoom(Msg m) {
        RoomInfo r = new RoomInfo();
        r.id = m.i(0, 0);
        r.title = m.s(1);
        r.hostId = m.i(2, 0);
        r.playing = "P".equals(m.s(3));
        r.max = m.i(4, 4);
        r.locked = m.b(5);
        r.turnSec = m.i(6, 60);
        for (String[] rec : m.list(7)) {
            Member x = new Member();
            x.id = Msg.num(rec, 0, 0);
            x.name = Msg.str(rec, 1);
            x.avatar = Msg.num(rec, 2, 0);
            x.ready = Msg.flag(rec, 3);
            x.connected = Msg.flag(rec, 4);
            r.members.add(x);
        }
        for (String[] rec : m.list(8)) {
            Member x = new Member();
            x.id = Msg.num(rec, 0, 0);
            x.name = Msg.str(rec, 1);
            x.avatar = Msg.num(rec, 2, 0);
            r.spectators.add(x);
        }
        return r;
    }

    public static List<LobbyRoom> parseRooms(Msg m) {
        List<LobbyRoom> out = new ArrayList<>();
        for (String[] rec : m.list(0)) {
            LobbyRoom r = new LobbyRoom();
            r.id = Msg.num(rec, 0, 0);
            r.title = Msg.str(rec, 1);
            r.playing = "P".equals(Msg.str(rec, 2));
            r.count = Msg.num(rec, 3, 0);
            r.max = Msg.num(rec, 4, 4);
            r.locked = Msg.flag(rec, 5);
            r.turnSec = Msg.num(rec, 6, 60);
            r.host = Msg.str(rec, 7);
            r.spectators = Msg.num(rec, 8, 0);
            out.add(r);
        }
        return out;
    }

    public static List<LobbyUser> parseUsers(Msg m) {
        List<LobbyUser> out = new ArrayList<>();
        for (String[] rec : m.list(1)) {
            LobbyUser u = new LobbyUser();
            u.id = Msg.num(rec, 0, 0);
            u.name = Msg.str(rec, 1);
            u.avatar = Msg.num(rec, 2, 0);
            u.where = Msg.str(rec, 3);
            out.add(u);
        }
        return out;
    }

    public static String turnText(int sec) {
        return sec == 0 ? "시간 무제한" : sec + "초";
    }
}
