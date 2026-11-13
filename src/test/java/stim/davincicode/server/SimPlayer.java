package stim.davincicode.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.GameView.Phase;
import stim.davincicode.common.Msg;

/**
 * 테스트용 자동 플레이어. 사람과 똑같이 "자기에게 보내지는 STATE"만 해석해서 둔다.
 * 규칙 엔진을 수천 판 돌려 보는 시뮬레이션에 쓴다. 실력 0 대충 / 1 보통 / 2 신중
 */
final class SimPlayer {
    private SimPlayer() { }

    private static final class Target {
        int pid, uid;
        char color;
        int[] cands;
    }

    /** 시작 패 고르기 단계면 아직 안 고른 모두가 무작위로 고른다. */
    static List<Game.Event> pickAll(Game game, Random rnd) {
        List<Game.Event> ev = new ArrayList<>();
        for (Game.Seat s : game.seats) {
            if (game.phase() == Phase.SETUP && s.alive && s.pick < 0) ev.addAll(game.pick(s.pid, rnd.nextInt(game.handSize + 1)));
        }
        return ev;
    }

    /** 지금 차례인 사람이 한 단계 행동한다. */
    static List<Game.Event> act(Game game, int pid, int level, Random rnd) {
        GameView v = GameView.parse(Msg.parse(game.snapshot(pid, -1)), pid);
        GameView.SeatView me = v.me();
        switch (v.phase) {
            case SETUP:
                return pickAll(game, rnd);
            case DRAW: {
                char c;
                if (v.deckB == 0) c = 'W';
                else if (v.deckW == 0) c = 'B';
                else c = rnd.nextInt(v.deckB + v.deckW) < v.deckB ? 'B' : 'W';
                return game.draw(pid, c);
            }
            case JOKER:
                return game.placeJoker(pid, rnd.nextInt(me.tiles.size() + 1));
            case REVEAL_SELF: {
                List<GameView.TileView> hidden = new ArrayList<>();
                for (GameView.TileView t : me.tiles) if (!t.revealed) hidden.add(t);
                return game.revealSelf(pid, hidden.get(rnd.nextInt(hidden.size())).uid);
            }
            case GUESS:
            case CONTINUE:
                return think(game, v, pid, level, rnd);
            default:
                return new ArrayList<>();
        }
    }

    private static List<Game.Event> think(Game game, GameView v, int pid, int level, Random rnd) {
        Deduction.Result r = Deduction.analyze(v);
        List<Target> targets = new ArrayList<>();
        for (GameView.SeatView s : v.seats) {
            if (s.pid == pid || !s.alive) continue;
            for (GameView.TileView t : s.tiles) {
                if (t.revealed) continue;
                Target tg = new Target();
                tg.pid = s.pid;
                tg.uid = t.uid;
                tg.color = t.color;
                tg.cands = t.known() ? new int[] { t.value } : r.of(t.uid);
                if (tg.cands == null || tg.cands.length == 0) tg.cands = allUnknown(r, t.color);
                targets.add(tg);
            }
        }
        // 후보가 가장 적은 타일 (같으면 무작위)
        Target best = null;
        int ties = 0;
        for (Target t : targets) {
            if (best == null || t.cands.length < best.cands.length) {
                best = t;
                ties = 1;
            } else if (t.cands.length == best.cands.length && rnd.nextInt(++ties) == 0) {
                best = t;
            }
        }
        if (best == null) return game.stop(pid);
        int size = best.cands.length;
        int myHidden = v.me().hiddenCount();

        if (v.phase == Phase.CONTINUE) {
            boolean go;
            if (level == 0) go = size == 1 && rnd.nextInt(2) == 0;
            else if (level == 1) go = size == 1 || (size == 2 && rnd.nextInt(4) == 0);
            else go = size == 1 || (size == 2 && myHidden >= 3 && rnd.nextInt(5) < 2);
            if (!go) return game.stop(pid);
        }

        // 쉬움: 가끔 후보를 무시하고 아무 숫자나 고른다
        if (level == 0 && rnd.nextInt(100) < 35) {
            int[] all = allUnknown(r, best.color);
            return game.guess(pid, best.pid, best.uid, all[rnd.nextInt(all.length)]);
        }
        return game.guess(pid, best.pid, best.uid, pick(best.cands, rnd));
    }

    /** 후보 중 하나를 고른다. 조커는 확률이 낮아서 덜 고른다. */
    private static int pick(int[] cands, Random rnd) {
        List<Integer> nums = new ArrayList<>();
        boolean joker = false;
        for (int c : cands) {
            if (c == GameView.JOKER) joker = true;
            else nums.add(c);
        }
        if (nums.isEmpty()) return GameView.JOKER;
        if (joker && rnd.nextInt(nums.size() * 3 + 1) == 0) return GameView.JOKER;
        return nums.get(rnd.nextInt(nums.size()));
    }

    private static int[] allUnknown(Deduction.Result r, char color) {
        List<Integer> out = new ArrayList<>();
        for (int n = 0; n < 12; n++) if (r.unknown(color, n)) out.add(n);
        if (out.isEmpty()) for (int n = 0; n < 12; n++) out.add(n);
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }
}
