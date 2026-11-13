package stim.davincicode.server;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Msg;

/**
 * 웹 클라이언트(proto.js)가 상태 해석과 숫자 계산을 Java와 똑같이 하는지 비교하려고,
 * 자동 플레이어끼리 게임을 돌리며 (STATE, 보는 사람, Java 해석·계산 결과)를 JSON 줄로 저장한다. 비교는 test/web/parity.mjs가 한다.
 */
public final class DeductionDump {
    public static void main(String[] args) throws Exception {
        Random rnd = new Random(99);
        int lines = 0;
        try (PrintWriter out = new PrintWriter(args[0], StandardCharsets.UTF_8)) {
            for (int gi = 0; gi < 300; gi++) {
                int n = 2 + gi % 3;
                List<Integer> pids = new ArrayList<>();
                for (int i = 1; i <= n; i++) pids.add(i);
                Game g = new Game(pids, 60, new Random(rnd.nextLong()));
                g.start();
                // 시작 패를 고르는 중인 장면 (일부만 고름): 고른 사람, 다른 사람, 관전자가 각각 보는 화면
                for (int k = 0; k < n - 1; k++) if (rnd.nextBoolean()) g.pick(pids.get(k), rnd.nextInt(g.handSize + 1));
                for (int viewer : new int[] { pids.get(0), pids.get(n - 1), 0 }) {
                    out.println(dump(g, viewer));
                    lines++;
                }
                int steps = 0;
                while (!g.over() && steps++ < 2000) {
                    int cur = g.currentPid();
                    if (steps % 4 == 0) {
                        out.println(dump(g, cur));
                        lines++;
                    }
                    SimPlayer.act(g, cur, rnd.nextInt(3), rnd);
                }
            }
        }
        System.out.println("저장: " + lines + "개 상태");
    }

    private static String dump(Game g, int viewer) {
        String line = g.snapshot(viewer, -1);
        GameView v = GameView.parse(Msg.parse(line), viewer);
        Deduction.Result r = Deduction.analyze(v);
        StringBuilder c = new StringBuilder("{");
        boolean first = true;
        for (GameView.SeatView s : v.seats) {
            if (s.pid == viewer) continue;
            for (GameView.TileView t : s.tiles) {
                int[] cs = r.of(t.uid);
                if (cs == null) continue;
                if (!first) c.append(',');
                first = false;
                c.append('"').append(t.uid).append("\":[");
                for (int i = 0; i < cs.length; i++) c.append(i > 0 ? "," : "").append(cs[i]);
                c.append(']');
            }
        }
        c.append('}');
        StringBuilder picks = new StringBuilder("[");
        for (GameView.SeatView s : v.seats) picks.append(picks.length() > 1 ? "," : "").append(s.pick);
        picks.append(']');
        return "{\"viewer\":" + viewer + ",\"phase\":\"" + v.phase + "\",\"hand\":" + v.handSize + ",\"picks\":" + picks
                + ",\"state\":\"" + json(line) + "\",\"cands\":" + c + "}";
    }

    private static String json(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '"' || ch == '\\') b.append('\\').append(ch);
            else if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
            else b.append(ch);
        }
        return b.toString();
    }
}
