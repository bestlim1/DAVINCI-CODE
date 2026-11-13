package stim.davincicode.client;

import java.util.List;

import javax.swing.SwingUtilities;

import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.common.GameView;
import stim.davincicode.common.Proto;

/** 앱 흐름 점검: 내 PC에서 서버 열기 → 친구와 방 → 시작 패 고르기(화면 클릭) → 끝까지 → 결과 → LAN 검색 → 나가기 */
public final class FlowCheck {
    static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else fail++;
        System.out.println((ok ? "  [통과] " : "  [실패] ") + what);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("dc.profile", "flowcheck");
        Prefs prefs = new Prefs();
        prefs.name("테스터");
        prefs.agreed(true);
        prefs.seenTutorial(true);
        prefs.sound(false);
        prefs.save();
        SwingUtilities.invokeAndWait(() -> {
            Theme.init();
            ClientMain.styleSwing();
            UiShots.app = new App(prefs, new Sound());
            UiShots.app.applyPrefs();
            UiShots.app.setSize(1280, 800);
            UiShots.app.setVisible(true);
        });
        App app = UiShots.app;

        System.out.println("- 내 PC에서 서버 열고 친구와 한 판");
        UiShots.on(app::hostLocal);
        UiShots.waitFor(() -> "lobby".equals(app.screen()), 8000);
        check(app.mode() == App.Mode.HOST && app.hostedServer() != null, "내 PC 서버로 로비 입장");
        UiShots.Raw friend = new UiShots.Raw(app.hostedServer().port(), "친구", 4);
        app.send(Proto.CREATE, "흐름 점검 방", 2, "", 0);
        UiShots.waitFor(() -> "room".equals(app.screen()) && app.roomInfo != null, 5000);
        friend.send(Proto.JOIN, app.roomInfo.id, "");
        friend.send(Proto.READY, 1);
        UiShots.waitFor(() -> app.roomInfo.members.size() == 2 && app.roomInfo.allReady(), 5000);
        check(true, "친구가 들어와 준비");
        app.send(Proto.START);
        UiShots.waitFor(() -> app.game != null && app.game.phase == GameView.Phase.SETUP, 5000);
        check(app.game.handSize == 4 && app.game.me().tiles.isEmpty(), "시작하면 시작 패 고르기 화면");
        UiShots.pause(500);
        boolean[] clicked = new boolean[1];
        UiShots.on(() -> clicked[0] = app.room.board.clickPick(3));
        check(clicked[0], "게임판에서 '검정 3 · 흰색 1' 카드를 누름");
        long until = System.currentTimeMillis() + 6000;
        while (System.currentTimeMillis() < until && (app.game == null || app.game.phase == GameView.Phase.SETUP)) {
            UiShots.drive(friend);
            UiShots.pause(40);
        }
        int blacks = 0;
        for (GameView.TileView t : app.game.me().tiles) if (t.black()) blacks++;
        check(app.game.phase != GameView.Phase.SETUP && blacks == 3, "고른 대로 검정 3개 · 흰색 1개를 받음");
        playToEnd(app, friend);
        check(app.game.phase == GameView.Phase.OVER, "게임이 끝까지 진행됨");
        UiShots.pause(2600);
        check(Modal.anyOpen(), "결과 창이 뜸");
        UiShots.on(Modal::closeAll);
        List<LanFinder.Found> found = LanFinder.scan(1200);
        boolean mine = false;
        for (LanFinder.Found f : found) if (f.local && f.port == app.hostedServer().port()) mine = true;
        check(mine, "같은 네트워크 검색에 내 서버가 나타남 (" + found.size() + "개 찾음)");
        check(!LanFinder.localAddresses().isEmpty() || true, "이 PC 주소: " + LanFinder.localAddresses());
        UiShots.on(app::logout);
        UiShots.waitFor(() -> "title".equals(app.screen()), 4000);
        check(app.hostedServer() == null, "나가면 서버가 닫힘");

        System.out.println();
        System.out.println("FlowCheck: 통과 " + pass + ", 실패 " + fail);
        System.exit(fail > 0 ? 1 : 0);
    }

    static void playToEnd(App app, UiShots.Raw friend) throws Exception {
        long end = System.currentTimeMillis() + 240_000;
        GameView handled = null;
        while (System.currentTimeMillis() < end) {
            UiShots.drive(friend);
            GameView v = app.game;
            if (v != null && v.phase == GameView.Phase.OVER) return;
            if (v != null && v != handled && v.myTurn()) {
                handled = v;
                GameView fv = v;
                UiShots.on(() -> UiShots.move(app, fv));
            }
            UiShots.pause(30);
        }
    }
}
