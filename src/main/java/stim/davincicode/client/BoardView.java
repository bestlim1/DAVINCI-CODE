package stim.davincicode.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import stim.davincicode.client.ui.Anim;
import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Icons;
import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.Deduction;
import stim.davincicode.common.GameView;
import stim.davincicode.common.GameView.Phase;
import stim.davincicode.common.GameView.SeatView;
import stim.davincicode.common.GameView.TileView;
import stim.davincicode.common.Msg;
import stim.davincicode.common.Proto;

/**
 * 게임판. 상대 타일 줄(위), 더미와 진행 상황(가운데), 내 타일 줄(아래)을 직접 그리고 마우스로 고르게 한다.
 * 서버가 보낸 STATE(내가 볼 수 있는 정보)만 그리므로 화면에 상대 숫자가 새어 나갈 길이 없다.
 */
final class BoardView extends JComponent {
    private static final long serialVersionUID = 1L;

    // ---- 화면에 표시할 이름 (게임 중 나간 사람도 남겨 둔다)
    static final class Who {
        String name;
        int avatar;
    }

    private enum HitType { TILE, PILE, SLOT, MY_TILE, NUM, CANCEL, BLOCK, PICK }

    private static final class Hit {
        final HitType type;
        final Rectangle2D r;
        int pid, uid, value;
        char color;

        Hit(HitType t, Rectangle2D r) {
            type = t;
            this.r = r;
        }
    }

    private static final class Floater {
        String text;
        Color color;
        double x, y;
        long start = Anim.now();
    }

    private static final class Particle {
        double x, y, vx, vy, rot, vr, size;
        Color c;
    }

    private final App app;
    private GameView v;
    private Deduction.Result ded; // 판 위에 보이는 숫자 확인용 (숫자 고르기 창에서 이미 보이는 숫자를 막는다)
    private final Map<Integer, Who> who = new HashMap<>();
    private long stateAt;
    private long totalMs = 1;
    private int lastTick = -1;
    private int popupPid = -1, popupUid = -1;
    private final Map<Integer, String> memos = new HashMap<>();
    private final javax.swing.Timer ringTimer = new javax.swing.Timer(50, e -> repaint());

    private final Map<Integer, Long> flipAt = new HashMap<>();
    private final Map<Integer, Long> popAt = new HashMap<>();
    private final Map<Integer, Long> shakeAt = new HashMap<>();
    private final Map<Integer, Long> okAt = new HashMap<>();
    private final Map<Integer, Long> elimAt = new HashMap<>();
    private final Map<Integer, long[]> reacts = new HashMap<>();
    private final List<Floater> floaters = new ArrayList<>();
    private final List<Particle> confetti = new ArrayList<>();
    private long confettiAt;
    private String banner;
    private Color bannerColor;
    private long bannerAt;
    private char ghostColor;
    private long ghostAt = -1;
    private long dealAt = -1;       // 시작 패를 나눠 준 시각 (타일이 하나씩 놓이는 효과)
    private int pickSent = -1;      // 서버에 보낸 시작 패 선택 (답이 오기 전에 바로 표시한다)

    // 레이아웃 결과
    private final List<Hit> hits = new ArrayList<>();
    private final Map<Integer, Rectangle2D> tileRect = new HashMap<>();
    private final Map<Integer, Rectangle2D> avatarRect = new HashMap<>();
    private Rectangle2D pileB, pileW, pendingRect;
    private Hit hover;
    private Point mouse;

    final Btn stopBtn = new Btn("턴 마치기", Btn.Kind.PRIMARY).icon("check");

    BoardView(App app) {
        this.app = app;
        setLayout(null);
        add(stopBtn);
        stopBtn.setToolTipText("가져온 타일을 숨긴 채 내 줄에 넣고 차례를 넘겨요.");
        stopBtn.addActionListener(e -> app.send(Proto.STOP));

        MouseAdapter m = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                mouse = e.getPoint();
                Hit h = hitAt(e.getPoint());
                if (h != hover) {
                    hover = h;
                    boolean clickable = h != null && h.type != HitType.BLOCK && h.type != HitType.CANCEL;
                    setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = null;
                mouse = null;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    rightClick(e);
                    return;
                }
                click(hitAt(e.getPoint()));
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getActionMap().put("cancel", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (popupUid >= 0) {
                    popupUid = -1;
                    repaint();
                }
            }
        });
        updateButtons();
    }

    // ================================================================ 상태 갱신

    void reset() {
        v = null;
        memos.clear();
        flipAt.clear();
        popAt.clear();
        shakeAt.clear();
        okAt.clear();
        elimAt.clear();
        reacts.clear();
        floaters.clear();
        confetti.clear();
        banner = null;
        ghostAt = -1;
        dealAt = -1;
        pickSent = -1;
        popupUid = -1;
    }

    void updateWho(Model.RoomInfo r) {
        for (Model.Member m : r.members) {
            Who w = new Who();
            w.name = m.name;
            w.avatar = m.avatar;
            who.put(m.id, w);
        }
    }

    private Who who(int pid) {
        Who w = who.get(pid);
        if (w == null) {
            w = new Who();
            w.name = "?";
        }
        return w;
    }

    String nameOf(int pid) {
        return pid == app.myId ? "나" : who(pid).name;
    }

    GameView view() {
        return v;
    }

    void update(GameView nv) {
        v = nv;
        stateAt = Anim.now();
        totalMs = Math.max(1, nv.remainingMs);
        lastTick = -1;
        ded = Deduction.analyze(nv);
        if (nv.phase != Phase.SETUP) pickSent = -1;
        if (!nv.myTurn() || (nv.phase != Phase.GUESS && nv.phase != Phase.CONTINUE)) {
            popupUid = -1;
        }
        if (popupUid >= 0) {
            TileView t = nv.findTile(popupUid);
            if (t == null || t.revealed) popupUid = -1;
        }
        updateButtons();
        Anim.run(this, 700);
        if (nv.remainingMs >= 0 && nv.phase != Phase.OVER) ringTimer.start();
        else ringTimer.stop();
        repaint();
    }

    /** 화면 확인용: 첫 번째로 고를 수 있는 상대 타일의 숫자 고르기 창을 연다 */
    boolean openFirstTarget() {
        for (Hit h : hits) {
            if (h.type == HitType.TILE) {
                popupPid = h.pid;
                popupUid = h.uid;
                repaint();
                return true;
            }
        }
        return false;
    }

    /** 화면 확인용: 시작 패 고르기 카드 하나를 누른다 (검정 개수로 고른다) */
    boolean clickPick(int black) {
        for (Hit h : hits) {
            if (h.type == HitType.PICK && h.value == black) {
                click(h);
                return true;
            }
        }
        return false;
    }

    void stopTimers() {
        ringTimer.stop();
    }

    private void updateButtons() {
        boolean playing = v != null && v.phase != Phase.OVER && v.me() != null;
        stopBtn.setVisible(playing && v.myTurn() && v.phase == Phase.CONTINUE);
        doLayout();
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight();
        double myH = myRackH(h);
        int by = (int) (h - 28 - myH);
        java.awt.Dimension sd = new java.awt.Dimension(170, 44);
        int bx = w - 30 - sd.width - 14;
        stopBtn.setBounds(bx, (int) (by + myH / 2 - 22), sd.width, 44);
    }

    private static double myRackH(int h) {
        return Math.max(150, Math.min(200, h * 0.26));
    }

    // ================================================================ 이벤트 (애니메이션과 기록)

    void onEvent(Msg m, ChatPanel log) {
        String type = m.s(0);
        long now = Anim.now();
        switch (type) {
            case "SETUP": {
                boolean playing = app.roomInfo == null || !app.roomInfo.isSpectator(app.myId);
                banner = playing ? "시작 패를 고르세요" : "시작 패 고르는 중";
                bannerColor = Theme.CORAL;
                bannerAt = now;
                if (playing) {
                    app.sound.play(Sound.Sfx.TURN);
                    app.alertTurn();
                }
                log.addLog("── 시작 패 고르기: 검정과 흰색을 합쳐 " + m.i(1, 4) + "개 받아요 ──", Theme.TEXT_3);
                break;
            }
            case "PICK": {
                int pid = m.i(1, 0);
                log.addLog(pid == app.myId ? "시작 패를 골랐어요." : nameOf(pid) + "님이 시작 패를 골랐어요.", Theme.TEXT_2);
                break;
            }
            case "DEAL":
                dealAt = now;
                app.sound.play(Sound.Sfx.PLACE);
                log.addLog("고른 대로 타일을 나눠 줬어요. 숫자는 무작위예요.", Theme.MINT);
                break;
            case "TURN": {
                int pid = m.i(1, 0);
                boolean mine = pid == app.myId;
                banner = mine ? "내 차례!" : who(pid).name + "님의 차례";
                bannerColor = mine ? Theme.CORAL : Theme.SKY;
                bannerAt = now;
                if (mine) {
                    app.sound.play(Sound.Sfx.TURN);
                    app.alertTurn();
                }
                log.addLog("── " + (mine ? "내" : who(pid).name + "님의") + " 차례 ──", Theme.TEXT_3);
                break;
            }
            case "DRAW": {
                int pid = m.i(1, 0);
                ghostColor = m.s(2).isEmpty() ? 'B' : m.s(2).charAt(0);
                ghostAt = now;
                app.sound.play(Sound.Sfx.DRAW);
                log.addLog(nameOf(pid) + (pid == app.myId ? "가 " : "님이 ") + (ghostColor == 'B' ? "검정" : "흰색") + " 타일을 가져왔어요.", Theme.TEXT_2);
                break;
            }
            case "GUESS": {
                int pid = m.i(1, 0), target = m.i(2, 0), uid = m.i(3, 0);
                String val = m.s(4).equals("J") ? "조커" : m.s(4);
                boolean ok = m.b(5);
                Rectangle2D r = tileRect.get(uid);
                if (r != null) {
                    floatText(val + "?", Theme.TEXT, r.getCenterX(), r.getY() - 6);
                    Floater res = floatText(ok ? "정답!" : "땡!", ok ? Theme.MINT : Theme.RED, r.getCenterX(), r.getY() - 30);
                    res.start = now + 350;
                }
                if (ok) okAt.put(uid, now + 300);
                else shakeAt.put(uid, now + 300);
                int idx = v != null && v.seat(target) != null ? v.seat(target).indexOf(uid) + 1 : 0;
                app.sound.play(ok ? Sound.Sfx.CORRECT : Sound.Sfx.WRONG);
                log.addLog(nameOf(pid) + " → " + nameOf(target) + "의 " + idx + "번째 타일을 '" + val + "'(으)로 추리 … "
                        + (ok ? "정답!" : "틀림"), ok ? Theme.MINT : Theme.RED);
                break;
            }
            case "REVEAL":
                flipAt.put(m.i(2, 0), now + 320);
                break;
            case "PLACE": {
                int pid = m.i(1, 0), uid = m.i(2, 0);
                popAt.put(uid, now);
                app.sound.play(Sound.Sfx.PLACE);
                boolean open = m.b(3);
                log.addLog(nameOf(pid) + (pid == app.myId ? "가 " : "님이 ") + "가져온 타일을 " + (open ? "공개한 채로" : "숨긴 채로") + " 넣었어요.",
                        open ? Theme.CORAL : Theme.TEXT_2);
                break;
            }
            case "ELIM": {
                int pid = m.i(1, 0);
                elimAt.put(pid, now + 400);
                log.addLog(nameOf(pid) + (pid == app.myId ? "는 " : "님은 ") + "모든 타일이 공개되어 탈락했어요.", Theme.AMBER);
                if (pid == app.myId) app.sound.play(Sound.Sfx.LOSE);
                break;
            }
            case "TIMEOUT": {
                int pid = m.i(1, 0);
                Rectangle2D a = avatarRect.get(pid);
                if (a != null) floatText("시간 초과", Theme.AMBER, a.getCenterX(), a.getY() - 4);
                if ("SETUP".equals(m.s(2))) log.addLog(nameOf(pid) + " 시간 초과 — 시작 패를 임의로 정했어요.", Theme.AMBER);
                else log.addLog(nameOf(pid) + " 시간 초과 — 자동으로 진행했어요.", Theme.AMBER);
                break;
            }
            case "FORFEIT":
                log.addLog(nameOf(m.i(1, 0)) + " 기권", Theme.AMBER);
                break;
            case "OVER": {
                int winner = m.i(1, 0);
                boolean me = winner == app.myId;
                banner = me ? "승리!" : who(winner).name + "님 승리!";
                bannerColor = me ? Theme.AMBER : Theme.SKY;
                bannerAt = now;
                startConfetti();
                boolean playing = v != null && v.me() != null;
                app.sound.play(me ? Sound.Sfx.WIN : playing ? Sound.Sfx.LOSE : Sound.Sfx.CORRECT);
                log.addLog("게임 끝! " + (me ? "내가" : who(winner).name + "님이") + " 이겼어요.", Theme.AMBER);
                break;
            }
            default:
        }
        Anim.run(this, 1800);
    }

    void onReact(int pid, int code) {
        reacts.put(pid, new long[] { code, Anim.now() });
        Anim.run(this, 2600);
    }

    private Floater floatText(String text, Color c, double x, double y) {
        Floater f = new Floater();
        f.text = text;
        f.color = c;
        f.x = x;
        f.y = Math.max(52, y);
        floaters.add(f);
        return f;
    }

    private void startConfetti() {
        if (Anim.reduced) return;
        confetti.clear();
        Random r = new Random();
        Color[] cs = { Theme.CORAL, Theme.AMBER, Theme.MINT, Theme.SKY, Theme.VIOLET, Color.WHITE };
        for (int i = 0; i < 140; i++) {
            Particle p = new Particle();
            p.x = r.nextDouble() * getWidth();
            p.y = -20 - r.nextDouble() * getHeight() * 0.5;
            p.vx = (r.nextDouble() - 0.5) * 60;
            p.vy = 120 + r.nextDouble() * 180;
            p.rot = r.nextDouble() * Math.PI;
            p.vr = (r.nextDouble() - 0.5) * 8;
            p.size = 6 + r.nextDouble() * 7;
            p.c = cs[r.nextInt(cs.length)];
            confetti.add(p);
        }
        confettiAt = Anim.now();
        Anim.run(this, 4200);
    }

    // ================================================================ 마우스

    private Hit hitAt(Point p) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).r.contains(p)) return hits.get(i);
        }
        return null;
    }

    private void click(Hit h) {
        if (v == null) return;
        if (popupUid >= 0) {
            if (h != null && h.type == HitType.NUM) {
                app.send(Proto.GUESS, popupPid, popupUid, h.value == GameView.JOKER ? "J" : String.valueOf(h.value));
                app.sound.play(Sound.Sfx.CLICK);
                popupUid = -1;
            } else if (h != null && h.type == HitType.BLOCK) {
                return;
            } else if (h == null || h.type == HitType.CANCEL) {
                popupUid = -1;
            } else if (h.type == HitType.TILE) {
                popupPid = h.pid;
                popupUid = h.uid;
            }
            repaint();
            return;
        }
        if (h == null) return;
        switch (h.type) {
            case PICK:
                if (pickSent < 0) {
                    pickSent = h.value;
                    app.send(Proto.PICK, h.value);
                    app.sound.play(Sound.Sfx.CLICK);
                }
                break;
            case PILE:
                app.send(Proto.DRAW, String.valueOf(h.color));
                break;
            case TILE:
                popupPid = h.pid;
                popupUid = h.uid;
                app.sound.play(Sound.Sfx.CLICK);
                break;
            case SLOT:
                app.send(Proto.JOKER, h.value);
                break;
            case MY_TILE:
                Modal.confirm(app, "이 타일을 공개할까요?", "틀렸고 더미가 비어 있어서 내 숨은 타일 하나를 공개해야 해요.", "공개하기", true,
                        () -> app.send(Proto.REVEAL, h.uid));
                break;
            default:
        }
        repaint();
    }

    /** 상대 숨은 타일에 메모(추측한 숫자)를 붙인다. 나만 보이는 표시다. */
    private void rightClick(MouseEvent e) {
        if (v == null) return;
        for (Map.Entry<Integer, Rectangle2D> en : tileRect.entrySet()) {
            if (!en.getValue().contains(e.getPoint())) continue;
            int uid = en.getKey();
            TileView t = v.findTile(uid);
            SeatView owner = v.ownerOf(uid);
            if (t == null || owner == null || owner.pid == app.myId || t.known()) return;
            javax.swing.JPanel grid = UI.panel(new java.awt.GridLayout(0, 4, 4, 4));
            grid.setBorder(UI.pad(6, 6, 6, 6));
            for (int n = 0; n <= 11; n++) {
                String s = String.valueOf(n);
                Btn b = new Btn(s, Btn.Kind.SECONDARY).small();
                b.addActionListener(ev -> {
                    memos.put(uid, s);
                    Modal.Popover.closeCurrent();
                    repaint();
                });
                grid.add(b);
            }
            Btn clear = new Btn("지우기", Btn.Kind.GHOST).small();
            clear.addActionListener(ev -> {
                memos.remove(uid);
                Modal.Popover.closeCurrent();
                repaint();
            });
            grid.add(clear);
            javax.swing.JPanel box = UI.panel(new java.awt.BorderLayout(0, 4));
            box.add(UI.label("  메모 (나만 보여요)", Theme.bold(13), Theme.TEXT_2), java.awt.BorderLayout.NORTH);
            box.add(grid, java.awt.BorderLayout.CENTER);
            Modal.Popover.showAt(this, e.getLocationOnScreen(), box);
            return;
        }
    }

    // ================================================================ 그리기

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = UI.g(g0);
        hits.clear();
        tileRect.clear();
        avatarRect.clear();
        int w = getWidth(), h = getHeight();
        Rectangle2D felt = new Rectangle2D.Double(10, 10, w - 20, h - 20);
        Art.felt(g, felt, 24);
        if (v == null) {
            UI.center(g, "게임을 준비하고 있어요…", Theme.bold(18), Theme.TEXT_2, w / 2.0, h / 2.0);
            g.dispose();
            return;
        }
        long now = Anim.now();

        // 자리 순서: 내 다음 사람부터 위에, 나는 맨 아래
        List<SeatView> others = new ArrayList<>();
        SeatView bottom;
        int meIdx = -1;
        for (int i = 0; i < v.seats.size(); i++) if (v.seats.get(i).pid == app.myId) meIdx = i;
        if (meIdx >= 0) {
            bottom = v.seats.get(meIdx);
            for (int i = 1; i < v.seats.size(); i++) others.add(v.seats.get((meIdx + i) % v.seats.size()));
        } else {
            bottom = v.seats.get(0);
            for (int i = 1; i < v.seats.size(); i++) others.add(v.seats.get(i));
        }
        boolean spectator = meIdx < 0;

        double myH = myRackH(h);
        double centerH = 128;
        double top = felt.getY() + 18;
        double bottomY = felt.getMaxY() - 18 - myH;
        double centerY = bottomY - 14 - centerH;
        double area = centerY - 12 - top;
        int k = others.size();
        double rowH = Math.min(156, (area - (k - 1) * 10) / k);
        double rowsTotal = rowH * k + (k - 1) * 10;
        double y = top + Math.max(0, (area - rowsTotal) / 2);
        double rackX = felt.getX() + 18, rackW = felt.getWidth() - 36;
        for (SeatView s : others) {
            paintSeat(g, s, rackX, y, rackW, rowH, false, now);
            y += rowH + 10;
        }
        paintCenter(g, rackX, centerY, rackW, centerH, now);
        paintSeat(g, bottom, rackX, bottomY, rackW - (spectator ? 0 : 0), myH, !spectator, now);

        paintGhost(g, now);
        paintFloaters(g, now);
        paintReacts(g, now);
        paintBanner(g, w, h, now);
        if (popupUid >= 0) paintPopup(g, w, h);
        paintConfetti(g, now);
        g.dispose();
    }

    private void paintSeat(Graphics2D g, SeatView s, double x, double y, double w, double h, boolean mine, long now) {
        Who wh = who(s.pid);
        boolean turn = v.turnPid == s.pid && v.phase != Phase.OVER;
        boolean out = !s.alive;
        // 받침대
        if (turn) {
            for (int i = 5; i >= 1; i--) {
                g.setColor(UI.a(Theme.CORAL, 16 - i * 2));
                g.fill(UI.rr(x - i * 1.6, y - i * 1.6, w + i * 3.2, h + i * 3.2, 18 + i));
            }
        }
        g.setColor(mine ? new Color(10, 20, 34, 175) : Theme.RACK);
        g.fill(UI.rr(x, y, w, h, 18));
        g.setColor(turn ? UI.a(Theme.CORAL, 200) : UI.a(Color.WHITE, 22));
        g.setStroke(new BasicStroke(turn ? 2f : 1f));
        g.draw(UI.rr(x + 0.5, y + 0.5, w - 1, h - 1, 18));
        g.setStroke(new BasicStroke(1f));

        // 왼쪽 정보
        double infoW = mine ? 190 : 176;
        double ad = mine ? 54 : Math.min(48, h - 52);
        double ax = x + 18, ay = y + (h - ad - 40) / 2;
        Graphics2D gi = (Graphics2D) g.create();
        if (out) UI.alpha(gi, 0.45f);
        Art.avatar(gi, ax, ay, ad, wh.avatar, false);
        avatarRect.put(s.pid, new Rectangle2D.Double(ax, ay, ad, ad));
        String name = mine ? wh.name + " (나)" : wh.name;
        UI.text(gi, UI.ellipsize(name, Theme.bold(15), (int) (infoW - 30)), Theme.bold(15), Theme.TEXT, ax, ay + ad + 6);
        String sub;
        Color subC = Theme.TEXT_3;
        if (out) {
            sub = "탈락";
            subC = Theme.AMBER;
        } else if (!s.connected) {
            sub = "연결 끊김 · 기다리는 중";
            subC = Theme.RED;
        } else if (v.phase == Phase.SETUP) {
            boolean done = s.picked() || (mine && pickSent >= 0);
            sub = done ? "시작 패를 골랐어요" : "시작 패 고르는 중…";
            subC = done ? Theme.MINT : Theme.TEXT_3;
        } else {
            sub = "숨은 타일 " + s.hiddenCount() + "개";
        }
        UI.text(gi, sub, Theme.font(12), subC, ax, ay + ad + 26);
        if (turn && !out) {
            double cw = UI.width(Theme.bold(11), "차례") + 14;
            gi.setColor(Theme.CORAL);
            gi.fill(UI.rr(ax + ad - cw / 2 + 6, ay - 6, cw, 18, 9));
            UI.center(gi, "차례", Theme.bold(11), Color.WHITE, ax + ad + 6, ay + 3);
        }
        gi.dispose();

        // 타일
        int n = s.tiles.size();
        double areaX = x + infoW, areaW = w - infoW - (mine ? 210 : 24);
        if (v.phase == Phase.SETUP) {
            if (mine) paintPickOptions(g, s, x + infoW, y, w - infoW - 24, h);
            else paintPickWaiting(g, s, areaX, y, areaW, h, now);
            return;
        }
        double th = mine ? Math.max(64, Math.min(112, h - 56)) : Math.max(44, Math.min(94, h - 36));
        double tw = th * 0.7;
        double gap = Math.max(5, Math.min(12, tw * 0.16));
        double need = n * tw + Math.max(0, n - 1) * gap;
        if (need > areaW) {
            tw = (areaW - Math.max(0, n - 1) * gap) / Math.max(1, n);
            th = tw / 0.7;
            need = areaW;
        }
        double tx0 = areaX + (areaW - need) / 2;
        double ty = y + (h - th) / 2 - (mine ? 4 : 8);

        boolean canTarget = !mine && v.myTurn() && s.alive && (v.phase == Phase.GUESS || v.phase == Phase.CONTINUE);
        boolean canSlot = mine && v.myTurn() && v.phase == Phase.JOKER;
        boolean canReveal = mine && v.myTurn() && v.phase == Phase.REVEAL_SELF;

        for (int i = 0; i < n; i++) {
            TileView t = s.tiles.get(i);
            double tx = tx0 + i * (tw + gap);
            Rectangle2D r = new Rectangle2D.Double(tx, ty, tw, th);
            tileRect.put(t.uid, r);
            boolean selectable = (canTarget && !t.revealed) || (canReveal && !t.revealed);
            boolean hovered = hover != null && (hover.type == HitType.TILE || hover.type == HitType.MY_TILE) && hover.uid == t.uid;
            boolean selected = popupUid == t.uid;
            double lift = (hovered || selected) ? 6 : 0;
            double dx = 0;
            Long sh = shakeAt.get(t.uid);
            if (sh != null && now >= sh && now - sh < 450) dx = Math.sin((now - sh) / 30.0) * 6 * (1 - (now - sh) / 450.0);
            double scale = 1;
            Long pop = popAt.get(t.uid);
            if (pop != null && now - pop < 380) scale = 0.6 + 0.4 * Anim.easeBack(Anim.progress(pop, 380));
            if (dealAt >= 0 && now - dealAt < 1400) {
                // 시작 패: 왼쪽부터 하나씩 놓인다
                long at = dealAt + i * 90L;
                if (now < at) continue;
                scale = 0.5 + 0.5 * Anim.easeBack(Anim.progress(at, 360));
            }

            Art.Look l = look(t, mine);
            l.glow = hovered || selected;
            l.glowColor = canReveal ? Theme.RED : Theme.CORAL;
            Long ok = okAt.get(t.uid);
            if (ok != null && now >= ok && now - ok < 700) {
                l.glow = true;
                l.glowColor = Theme.MINT;
            }
            Long fl = flipAt.get(t.uid);
            if (fl != null) {
                double p = now < fl ? 0 : Anim.progress(fl, 420);
                if (p < 1) l.flip = p;
            }
            if (out) l.alpha = 0.55;
            Graphics2D gt = (Graphics2D) g.create();
            if (scale != 1) {
                gt.translate(tx + tw / 2, ty + th / 2);
                gt.scale(scale, scale);
                gt.translate(-(tx + tw / 2), -(ty + th / 2));
            }
            Art.tile(gt, tx + dx, ty - lift, tw, th, l);
            gt.dispose();

            if (selectable) {
                Hit hh = new Hit(canReveal ? HitType.MY_TILE : HitType.TILE, r);
                hh.pid = s.pid;
                hh.uid = t.uid;
                hits.add(hh);
            }
            if (mine && !t.revealed && v.phase != Phase.OVER) {
                Icons.paint(g, "lock", tx + tw / 2 - 6, ty + th + 4, 12, UI.a(Theme.TEXT, 70));
            }
        }

        // 조커 놓을 자리
        if (canSlot) {
            for (int i = 0; i <= n; i++) {
                double sx = (i == 0 ? tx0 - gap / 2 - 10 : tx0 + i * (tw + gap) - gap / 2) - 4;
                Rectangle2D sr = new Rectangle2D.Double(sx - 8, ty - 8, 24, th + 16);
                boolean hv = hover != null && hover.type == HitType.SLOT && hover.value == i;
                g.setColor(UI.a(Theme.AMBER, hv ? 230 : 110));
                g.fill(UI.rr(sx, ty, 8, th, 4));
                if (hv) {
                    Art.Look jl = new Art.Look();
                    jl.color = v.pending != null ? v.pending.color : 'B';
                    jl.face = "-";
                    jl.alpha = 0.75;
                    Art.tile(g, sx + 4 - tw / 2, ty - th - 14, tw, th, jl);
                }
                Hit hh = new Hit(HitType.SLOT, sr);
                hh.value = i;
                hits.add(hh);
            }
        }

        // 탈락 도장
        if (out) {
            Long ea = elimAt.get(s.pid);
            double p = ea == null ? 1 : Anim.easeBack(Anim.progress(ea, 420));
            Graphics2D gs = (Graphics2D) g.create();
            double cx = areaX + areaW / 2, cy = y + h / 2;
            gs.rotate(-0.12, cx, cy);
            gs.translate(cx, cy);
            gs.scale(0.6 + 0.4 * p, 0.6 + 0.4 * p);
            gs.translate(-cx, -cy);
            UI.alpha(gs, (float) Math.min(1, p));
            Font f = Theme.bold(26);
            double sw = UI.width(f, "탈락") + 40;
            gs.setColor(UI.a(Theme.AMBER, 40));
            gs.fill(UI.rr(cx - sw / 2, cy - 24, sw, 48, 12));
            gs.setColor(Theme.AMBER);
            gs.setStroke(new BasicStroke(3f));
            gs.draw(UI.rr(cx - sw / 2, cy - 24, sw, 48, 12));
            UI.center(gs, "탈락", f, Theme.AMBER, cx, cy);
            gs.dispose();
        }
    }

    /** 시작 패 고르기: 검정·흰색 조합 카드들을 내 자리에 늘어놓는다 */
    private void paintPickOptions(Graphics2D g, SeatView s, double x, double y, double w, double h) {
        int hs = v.handSize;
        int chosen = s.pick >= 0 ? s.pick : pickSent;
        int opts = hs + 1;
        double gap = 12;
        double cw = Math.min(176, (w - gap * (opts - 1)) / opts);
        double ch = h - 26;
        double total = cw * opts + gap * (opts - 1);
        double x0 = x + (w - total) / 2, cy = y + 13;
        for (int i = 0; i < opts; i++) {
            int black = hs - i; // 왼쪽은 검정이 많은 조합, 오른쪽은 흰색이 많은 조합
            double cx = x0 + i * (cw + gap);
            Rectangle2D r = new Rectangle2D.Double(cx, cy, cw, ch);
            boolean picked = chosen == black;
            boolean dim = chosen >= 0 && !picked;
            boolean hv = chosen < 0 && hover != null && hover.type == HitType.PICK && hover.value == black;
            Graphics2D gc = (Graphics2D) g.create();
            if (dim) UI.alpha(gc, 0.35f);
            double lift = hv ? 4 : 0;
            gc.setColor(picked ? UI.a(Theme.CORAL, 46) : hv ? UI.a(Color.WHITE, 30) : UI.a(Color.WHITE, 14));
            gc.fill(UI.rr(cx, cy - lift, cw, ch, 16));
            gc.setColor(picked ? Theme.CORAL : hv ? UI.a(Theme.CORAL, 200) : UI.a(Color.WHITE, 40));
            gc.setStroke(new BasicStroke(picked || hv ? 2f : 1f));
            gc.draw(UI.rr(cx + 0.5, cy - lift + 0.5, cw - 1, ch - 1, 16));
            gc.setStroke(new BasicStroke(1f));
            // 작은 타일 뒷면으로 조합을 보여 준다
            double labelH = 38;
            double tth = Math.min(58, ch - labelH - 24), ttw = tth * 0.7;
            double tg = 4;
            double need = hs * ttw + (hs - 1) * tg;
            if (need > cw - 20) {
                ttw = (cw - 20 - (hs - 1) * tg) / hs;
                tth = ttw / 0.7;
                need = cw - 20;
            }
            double tx = cx + (cw - need) / 2, ty = cy - lift + (ch - labelH - tth) / 2 + 4;
            for (int k = 0; k < hs; k++) {
                Art.Look l = new Art.Look();
                l.color = k < black ? 'B' : 'W';
                l.back = true;
                Art.tile(gc, tx + k * (ttw + tg), ty, ttw, tth, l);
            }
            String label = black == hs ? "모두 검정" : black == 0 ? "모두 흰색" : "검정 " + black + " · 흰색 " + (hs - black);
            UI.center(gc, label, Theme.bold(cw < 120 ? 12 : 14), picked ? Theme.CORAL : Theme.TEXT, cx + cw / 2, cy - lift + ch - 22);
            if (picked) {
                double bw = UI.width(Theme.bold(11), "선택함") + 16;
                gc.setColor(Theme.CORAL);
                gc.fill(UI.rr(cx + cw - bw - 8, cy - lift + 8, bw, 20, 10));
                UI.center(gc, "선택함", Theme.bold(11), Color.WHITE, cx + cw - bw / 2 - 8, cy - lift + 18);
            }
            gc.dispose();
            if (chosen < 0) {
                Hit hh = new Hit(HitType.PICK, r);
                hh.value = black;
                hits.add(hh);
            }
        }
    }

    /** 시작 패 고르기: 다른 사람 자리에는 골랐는지만 보여 준다 (몇 개를 골랐는지는 나눠 줄 때까지 비밀) */
    private void paintPickWaiting(Graphics2D g, SeatView s, double x, double y, double w, double h, long now) {
        double cx = x + w / 2, cy = y + h / 2;
        if (s.picked()) {
            Icons.paint(g, "check", cx - 70, cy - 11, 22, Theme.MINT);
            UI.text(g, "골랐어요", Theme.bold(16), Theme.MINT, cx - 42, cy - UI.fm(Theme.bold(16)).getHeight() / 2.0);
        } else if (s.alive) {
            // 고르는 중: 점 세 개가 차례로 깜빡인다
            for (int i = 0; i < 3; i++) {
                double a = 0.35 + 0.65 * Math.max(0, Math.sin((now / 260.0) - i * 0.9));
                g.setColor(UI.a(Theme.TEXT_2, (int) (255 * a)));
                g.fill(new Ellipse2D.Double(cx - 64 + i * 14, cy - 4, 8, 8));
            }
            UI.text(g, "고르는 중", Theme.bold(15), Theme.TEXT_2, cx - 18, cy - UI.fm(Theme.bold(15)).getHeight() / 2.0);
            Anim.run(this, 600);
        }
    }

    private Art.Look look(TileView t, boolean mine) {
        Art.Look l = new Art.Look();
        l.color = t.color;
        l.back = !t.known();
        l.face = t.face();
        l.mineOpen = mine && t.revealed && v.phase != Phase.OVER;
        if (!mine && !t.known()) l.note = memos.get(t.uid);
        return l;
    }

    private void paintCenter(Graphics2D g, double x, double y, double w, double h, long now) {
        boolean myDraw = v.myTurn() && v.phase == Phase.DRAW;
        boolean compact = w < 840; // 창이 좁으면 더미와 가져온 타일 칸을 줄여서 안내 문구 자리를 확보한다
        // 더미 두 개
        double pw = compact ? 44 : 56, ph = compact ? 64 : 80, pgap = compact ? 14 : 26;
        double px = x + (compact ? 16 : 26), py = y + (h - ph) / 2 - 8;
        pileB = new Rectangle2D.Double(px, py, pw, ph);
        pileW = new Rectangle2D.Double(px + pw + pgap, py, pw, ph);
        paintPile(g, pileB, 'B', v.deckB, myDraw);
        paintPile(g, pileW, 'W', v.deckW, myDraw);

        // 가운데 안내
        double mx = pileW.getMaxX() + (compact ? 22 : 40);
        double rightBox = compact ? 104 : 210;
        double ringR = compact ? 24 : 30;
        double rcx = mx + ringR + 4, rcy = y + h / 2;
        long remain = v.remainingMs < 0 ? -1 : Math.max(0, v.remainingMs - (now - stateAt));
        if (remain >= 0 && v.phase != Phase.OVER) {
            double frac = Math.max(0, Math.min(1, remain / (double) totalMs));
            int sec = (int) Math.ceil(remain / 1000.0);
            Color rc = sec <= 10 ? Theme.RED : Theme.MINT;
            g.setColor(UI.a(Color.WHITE, 26));
            g.setStroke(new BasicStroke(compact ? 5f : 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Double(rcx - ringR, rcy - ringR, ringR * 2, ringR * 2));
            g.setColor(rc);
            g.draw(new java.awt.geom.Arc2D.Double(rcx - ringR, rcy - ringR, ringR * 2, ringR * 2, 90, frac * 360, java.awt.geom.Arc2D.OPEN));
            g.setStroke(new BasicStroke(1f));
            UI.center(g, String.valueOf(sec), Theme.num(compact ? 17 : 20), Theme.TEXT, rcx, rcy);
            if ((v.myTurn() || needPick()) && sec <= 5 && sec != lastTick && sec > 0) {
                lastTick = sec;
                app.sound.play(Sound.Sfx.TICK);
            }
        } else {
            Icons.paint(g, "timer", rcx - 14, rcy - 14, 28, Theme.TEXT_3);
        }
        String[] lines = statusText();
        double tx = rcx + ringR + (compact ? 14 : 18);
        int avail = (int) (x + w - rightBox - 16 - tx);
        Font f1 = Theme.bold(compact ? 17 : 19), f2 = Theme.font(compact ? 13 : 14);
        UI.text(g, UI.ellipsize(lines[0], f1, avail), f1, v.myTurn() || needPick() ? Theme.CORAL : Theme.TEXT, tx, rcy - (compact ? 23 : 26));
        UI.text(g, UI.ellipsize(lines[1], f2, avail), f2, Theme.TEXT_2, tx, rcy + 3);
        if (v.streak > 0 && v.phase == Phase.CONTINUE) {
            stim.davincicode.client.ui.Controls.Chip.paint(g, v.streak + "연속 정답", Theme.MINT, "check", tx, rcy + 27);
        }

        // 가져온 타일
        double bx = x + w - rightBox, bw = rightBox - 14;
        g.setColor(UI.a(Color.BLACK, 60));
        g.fill(UI.rr(bx, y + 8, bw, h - 16, 16));
        g.setColor(UI.a(Color.WHITE, 20));
        g.draw(UI.rr(bx + 0.5, y + 8.5, bw - 1, h - 17, 16));
        double tw = compact ? 46 : 54, th = compact ? 66 : 76;
        pendingRect = compact ? new Rectangle2D.Double(bx + (bw - tw) / 2, y + 16, tw, th)
                : new Rectangle2D.Double(bx + 18, y + (h - th) / 2, tw, th);
        double lx = pendingRect.getMaxX() + 14;
        if (v.pending != null) {
            Art.Look l = new Art.Look();
            l.color = v.pending.color;
            l.back = !v.pending.known();
            l.face = v.pending.face();
            double sc = ghostAt >= 0 && now - ghostAt < 420 ? Anim.progress(ghostAt, 420) : 1;
            if (sc >= 1) Art.tile(g, pendingRect.getX(), pendingRect.getY(), pendingRect.getWidth(), pendingRect.getHeight(), l);
            if (compact) {
                UI.center(g, v.pending.joker() ? "조커!" : "가져온 타일", Theme.bold(11), Theme.TEXT_2, bx + bw / 2, pendingRect.getMaxY() + 14);
            } else {
                UI.text(g, "가져온 타일", Theme.bold(13), Theme.TEXT, lx, y + h / 2 - 22);
                String sub = v.pending.joker() ? "조커! 원하는 자리에" : v.myTurn() ? "숨긴 채 들고 있어요" : "숫자는 주인만 알아요";
                UI.text(g, sub, Theme.font(12), Theme.TEXT_3, lx, y + h / 2);
            }
        } else {
            g.setColor(UI.a(Color.WHITE, 36));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5, 5 }, 0));
            g.draw(UI.rr(pendingRect.getX(), pendingRect.getY(), pendingRect.getWidth(), pendingRect.getHeight(), 9));
            g.setStroke(new BasicStroke(1f));
            if (compact) {
                UI.center(g, v.deckTotal() == 0 ? "더미 없음" : "없음", Theme.font(11), Theme.TEXT_3, bx + bw / 2, pendingRect.getMaxY() + 14);
            } else {
                UI.text(g, v.deckTotal() == 0 ? "더미가 비었어요" : "가져온 타일 없음", Theme.font(12), Theme.TEXT_3, lx, y + h / 2 - 9);
            }
        }
    }

    /** 시작 패를 아직 안 골랐는가 */
    private boolean needPick() {
        SeatView me = v.me();
        return v.phase == Phase.SETUP && me != null && me.alive && !me.picked() && pickSent < 0;
    }

    private String[] statusText() {
        String who = v.myTurn() ? "" : nameOf(v.turnPid) + "님";
        boolean spectator = v.me() == null;
        if (v.phase == Phase.OVER) return new String[] { "게임이 끝났어요", "모든 타일이 공개됐어요. 결과를 확인해 보세요." };
        if (v.phase == Phase.SETUP) {
            if (spectator) return new String[] { "관전 중 · 시작 패 고르는 중", "모두 고르면 타일을 나눠 줘요." };
            if (needPick()) return new String[] { "시작 패를 고르세요", "아래에서 검정·흰색 조합을 골라요." };
            return new String[] { "다른 사람을 기다리는 중", "모두 고르면 고른 대로 타일을 나눠 줘요." };
        }
        if (v.myTurn()) {
            switch (v.phase) {
                case DRAW: return new String[] { "타일을 가져오세요", "왼쪽 더미에서 검정 또는 흰색을 고르세요." };
                case JOKER: return new String[] { "조커를 놓을 자리를 고르세요", "내 줄의 노란 칸 중 원하는 곳을 누르세요." };
                case GUESS: return new String[] { "상대 타일을 맞혀 보세요", "숨은 타일을 눌러 숫자를 골라요 (우클릭: 메모)" };
                case CONTINUE: return new String[] { "정답! 한 번 더 할까요?", "더 맞히면 이어서, 멈추면 가져온 타일을 숨긴 채 넣어요." };
                case REVEAL_SELF: return new String[] { "아쉬워요! 내 타일 하나를 공개하세요", "더미가 비어서 내 숨은 타일 중 하나를 골라야 해요." };
                default:
            }
        }
        String prefix = spectator ? "관전 중 · " : "";
        switch (v.phase) {
            case DRAW: return new String[] { prefix + who + " 차례", "타일을 가져오는 중이에요." };
            case JOKER: return new String[] { prefix + who + " 차례", "조커를 놓을 자리를 고르는 중이에요." };
            case CONTINUE: return new String[] { prefix + who + "이 맞혔어요!", "더 맞힐지 고민하는 중이에요." };
            case REVEAL_SELF: return new String[] { prefix + who + " 차례", "공개할 자기 타일을 고르는 중이에요." };
            default: return new String[] { prefix + who + " 차례", "누구의 타일을 노릴지 추리하는 중이에요." };
        }
    }

    private void paintPile(Graphics2D g, Rectangle2D r, char color, int count, boolean selectable) {
        boolean hv = hover != null && hover.type == HitType.PILE && hover.color == color;
        if (count == 0) {
            g.setColor(UI.a(Color.WHITE, 40));
            g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5, 5 }, 0));
            g.draw(UI.rr(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 9));
            g.setStroke(new BasicStroke(1f));
        } else {
            int layers = Math.min(3, count);
            for (int i = layers - 1; i >= 0; i--) {
                Art.Look l = new Art.Look();
                l.color = color;
                l.back = true;
                l.glow = i == 0 && (hv || (selectable && Anim.now() % 1600 < 800));
                l.glowColor = Theme.CORAL;
                Art.tile(g, r.getX() + i * 3, r.getY() - i * 4 - (hv ? 4 : 0), r.getWidth(), r.getHeight(), l);
            }
            if (selectable) {
                Hit hh = new Hit(HitType.PILE, new Rectangle2D.Double(r.getX() - 4, r.getY() - 16, r.getWidth() + 12, r.getHeight() + 22));
                hh.color = color;
                hits.add(hh);
                Anim.run(this, 900);
            }
        }
        String label = (color == 'B' ? "검정 " : "흰색 ") + count;
        UI.center(g, label, Theme.bold(12), count == 0 ? Theme.TEXT_3 : Theme.TEXT_2, r.getCenterX() + 3, r.getMaxY() + 14);
    }

    private void paintGhost(Graphics2D g, long now) {
        if (ghostAt < 0 || pendingRect == null) return;
        double p = Anim.progress(ghostAt, 420);
        if (p >= 1) {
            ghostAt = -1;
            return;
        }
        Rectangle2D from = ghostColor == 'B' ? pileB : pileW;
        if (from == null) return;
        double e = Anim.easeInOut(p);
        double x = from.getX() + (pendingRect.getX() - from.getX()) * e;
        double y = from.getY() + (pendingRect.getY() - from.getY()) * e - Math.sin(e * Math.PI) * 40;
        Art.Look l = new Art.Look();
        l.color = ghostColor;
        l.back = true;
        AffineTransform old = g.getTransform();
        g.rotate(Math.sin(e * Math.PI) * 0.25, x + 27, y + 38);
        Art.tile(g, x, y, pendingRect.getWidth(), pendingRect.getHeight(), l);
        g.setTransform(old);
    }

    private void paintFloaters(Graphics2D g, long now) {
        floaters.removeIf(f -> now - f.start > 1300);
        for (Floater f : floaters) {
            if (now < f.start) continue;
            double p = (now - f.start) / 1300.0;
            double a = p < 0.15 ? p / 0.15 : p > 0.7 ? (1 - p) / 0.3 : 1;
            Graphics2D gf = (Graphics2D) g.create();
            UI.alpha(gf, (float) a);
            Font font = Theme.bold(18);
            double tw = UI.width(font, f.text) + 22;
            double yy = f.y - p * 26;
            gf.setColor(new Color(10, 14, 26, 210));
            gf.fill(UI.rr(f.x - tw / 2, yy - 15, tw, 30, 15));
            gf.setColor(UI.a(f.color, 160));
            gf.draw(UI.rr(f.x - tw / 2, yy - 15, tw, 30, 15));
            UI.center(gf, f.text, font, f.color, f.x, yy);
            gf.dispose();
        }
    }

    private void paintReacts(Graphics2D g, long now) {
        for (Map.Entry<Integer, long[]> e : new ArrayList<>(reacts.entrySet())) {
            long[] r = e.getValue();
            long age = now - r[1];
            if (age > 2500) {
                reacts.remove(e.getKey());
                continue;
            }
            Rectangle2D a = avatarRect.get(e.getKey());
            if (a == null) continue;
            int code = (int) Math.max(0, Math.min(ChatPanel.REACTIONS.length - 1, r[0]));
            String text = ChatPanel.REACTIONS[code];
            Color c = ChatPanel.REACTION_COLORS[code];
            double s = Anim.easeBack(Math.min(1, age / 260.0));
            double alpha = age > 2100 ? (2500 - age) / 400.0 : 1;
            Graphics2D gr = (Graphics2D) g.create();
            UI.alpha(gr, (float) alpha);
            Font f = Theme.bold(16);
            double bw = UI.width(f, text) + 26, bh = 34;
            double bx = a.getMaxX() - 6, by = a.getY() - bh + 4;
            gr.translate(bx, by + bh);
            gr.scale(s, s);
            gr.translate(-bx, -(by + bh));
            gr.setColor(c);
            gr.fill(UI.rr(bx, by, bw, bh, 17));
            java.awt.geom.Path2D tail = new java.awt.geom.Path2D.Double();
            tail.moveTo(bx + 8, by + bh - 4);
            tail.lineTo(bx - 2, by + bh + 8);
            tail.lineTo(bx + 20, by + bh - 2);
            gr.fill(tail);
            UI.center(gr, text, f, new Color(0x141826), bx + bw / 2, by + bh / 2);
            gr.dispose();
        }
    }

    private void paintBanner(Graphics2D g, int w, int h, long now) {
        if (banner == null) return;
        long age = now - bannerAt;
        boolean over = v != null && v.phase == Phase.OVER;
        long life = over ? 2600 : 1300;
        if (age > life) {
            banner = null;
            return;
        }
        double in = Anim.easeOut(Math.min(1, age / 240.0));
        double out = age > life - 300 ? (life - age) / 300.0 : 1;
        Graphics2D gb = (Graphics2D) g.create();
        UI.alpha(gb, (float) Math.min(in, out));
        Font f = Theme.bold(over ? 44 : 32);
        double bw = UI.width(f, banner) + 80, bh = over ? 92 : 72;
        double bx = (w - bw) / 2, by = h / 2.0 - bh / 2 - 20 + (1 - in) * 20;
        UI.shadow(gb, bx, by, bw, bh, 24, 16, 160, 6);
        gb.setColor(new Color(12, 18, 34, 235));
        gb.fill(UI.rr(bx, by, bw, bh, 24));
        gb.setColor(UI.a(bannerColor, 200));
        gb.setStroke(new BasicStroke(2f));
        gb.draw(UI.rr(bx + 1, by + 1, bw - 2, bh - 2, 23));
        UI.center(gb, banner, f, bannerColor, w / 2.0, by + bh / 2);
        gb.dispose();
    }

    private void paintPopup(Graphics2D g, int w, int h) {
        Rectangle2D tr = tileRect.get(popupUid);
        TileView t = v.findTile(popupUid);
        SeatView owner = v.ownerOf(popupUid);
        if (tr == null || t == null || owner == null) {
            popupUid = -1;
            return;
        }
        // 바깥을 누르면 닫히도록 전체를 덮는 취소 영역을 먼저 넣는다
        hits.add(new Hit(HitType.CANCEL, new Rectangle2D.Double(0, 0, w, h)));
        double cell = 46, cellH = 42, gap = 6, pad = 16;
        int rows = 4;
        double pw = pad * 2 + cell * 4 + gap * 3;
        double ph = 54 + rows * (cellH + gap) + 34;
        double px = Math.max(16, Math.min(w - pw - 16, tr.getCenterX() - pw / 2));
        double py = tr.getMaxY() + 14;
        if (py + ph > h - 16) py = tr.getY() - ph - 14;
        py = Math.max(16, py);
        UI.shadow(g, px, py, pw, ph, 18, 14, 190, 6);
        g.setColor(new Color(0x1F2846));
        g.fill(UI.rr(px, py, pw, ph, 18));
        g.setColor(UI.a(Theme.CORAL, 150));
        g.draw(UI.rr(px + 0.5, py + 0.5, pw - 1, ph - 1, 18));
        Hit block = new Hit(HitType.BLOCK, new Rectangle2D.Double(px, py, pw, ph)); // 카드 안의 빈 곳은 아무 일도 하지 않는다
        int idx = owner.indexOf(popupUid) + 1;
        String title = nameOf(owner.pid) + "의 " + idx + "번째 · " + (t.black() ? "검정" : "흰색");
        UI.text(g, title, Theme.bold(15), Theme.TEXT, px + pad, py + 14);
        UI.text(g, "어떤 숫자일까요?", Theme.font(12), Theme.TEXT_3, px + pad, py + 34);
        List<Hit> nums = new ArrayList<>();
        Deduction.Result r = ded;
        for (int n = 0; n < 12; n++) {
            int col = n % 4, row = n / 4;
            double cx = px + pad + col * (cell + gap), cy = py + 54 + row * (cellH + gap);
            // 판 위에 이미 보이는 숫자(내 타일, 공개된 타일)는 이 타일일 수 없으니 막아 둔다
            boolean known = r != null && !r.unknown(t.color, n);
            nums.add(numCell(g, cx, cy, cell, cellH, String.valueOf(n), n, known, t.color));
        }
        double jy = py + 54 + 3 * (cellH + gap);
        boolean jokerKnown = r != null && !r.unknown(t.color, GameView.JOKER);
        nums.add(numCell(g, px + pad, jy, pw - pad * 2, cellH, "조커", GameView.JOKER, jokerKnown, t.color));
        UI.center(g, "취소 (Esc)", Theme.font(12), Theme.TEXT_3, px + pw / 2, py + ph - 18);
        Hit cancel = new Hit(HitType.CANCEL, new Rectangle2D.Double(px, py + ph - 32, pw, 32));
        hits.add(block);
        hits.add(cancel);
        hits.addAll(nums);
    }

    private Hit numCell(Graphics2D g, double x, double y, double w, double h, String label, int value, boolean known, char color) {
        boolean hv = hover != null && hover.type == HitType.NUM && hover.value == value && !known;
        Color fill = known ? UI.a(Theme.SURFACE_LO, 160) : hv ? Theme.CORAL : Theme.SURFACE_HI;
        g.setColor(fill);
        g.fill(UI.rr(x, y, w, h, 10));
        Color ink = known ? Theme.TEXT_3 : hv ? Color.WHITE : Theme.TEXT;
        UI.center(g, label, label.matches("[0-9]+") ? Theme.num(18) : Theme.bold(15), ink, x + w / 2, y + h / 2);
        if (known) {
            g.setColor(UI.a(Theme.TEXT_3, 160));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new java.awt.geom.Line2D.Double(x + 10, y + h - 10, x + w - 10, y + 10));
            g.setStroke(new BasicStroke(1f));
            return new Hit(HitType.NUM, new Rectangle2D.Double(0, 0, 0, 0));
        }
        Hit hh = new Hit(HitType.NUM, new Rectangle2D.Double(x, y, w, h));
        hh.value = value;
        return hh;
    }

    private void paintConfetti(Graphics2D g, long now) {
        if (confetti.isEmpty()) return;
        double t = (now - confettiAt) / 1000.0;
        if (t > 4) {
            confetti.clear();
            return;
        }
        double alpha = t > 3 ? 4 - t : 1;
        Graphics2D gc = (Graphics2D) g.create();
        UI.alpha(gc, (float) alpha);
        for (Particle p : confetti) {
            double x = p.x + p.vx * t + Math.sin(t * 3 + p.rot) * 14;
            double y = p.y + p.vy * t + 40 * t * t;
            AffineTransform old = gc.getTransform();
            gc.rotate(p.rot + p.vr * t, x, y);
            gc.setColor(p.c);
            gc.fill(new Rectangle2D.Double(x - p.size / 2, y - p.size / 4, p.size, p.size / 2));
            gc.setTransform(old);
        }
        gc.dispose();
    }
}
