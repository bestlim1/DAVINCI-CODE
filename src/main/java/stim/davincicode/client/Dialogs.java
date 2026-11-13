package stim.davincicode.client;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import stim.davincicode.client.ui.Anim;
import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Btn;
import stim.davincicode.client.ui.Controls;
import stim.davincicode.client.ui.Field;
import stim.davincicode.client.ui.Icons;
import stim.davincicode.client.ui.Modal;
import stim.davincicode.client.ui.Theme;
import stim.davincicode.client.ui.UI;
import stim.davincicode.common.Names;
import stim.davincicode.common.Proto;

/** 앱의 대화상자 모음 */
final class Dialogs {
    private Dialogs() { }

    private static JPanel column() {
        JPanel p = UI.panel(null);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static JComponent labeled(String label, JComponent c) {
        JPanel p = UI.panel(new BorderLayout(0, 7));
        p.add(UI.label(label, Theme.bold(13), Theme.TEXT_2), BorderLayout.NORTH);
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 26));
        return p;
    }

    private static void left(JPanel col) {
        for (java.awt.Component c : col.getComponents()) ((JComponent) c).setAlignmentX(0f);
    }

    // ================================================================ 프로필

    static void profile(App app, boolean firstRun, Runnable then) {
        JPanel body = column();
        AvatarPicker picker = new AvatarPicker(app.prefs.avatar());
        body.add(labeled("캐릭터", picker));
        body.add(Box.createVerticalStrut(14));
        Field name = new Field("2~10글자 · 한글, 영문, 숫자, _").icon("user").limit(Names.NAME_MAX);
        name.setText(app.prefs.name());
        body.add(labeled("닉네임", name));
        JLabel err = UI.label(" ", Theme.font(13), Theme.RED);
        body.add(Box.createVerticalStrut(6));
        body.add(err);
        Controls.Toggle agree = new Controls.Toggle(app.prefs.agreed());
        if (!app.prefs.agreed()) {
            body.add(Box.createVerticalStrut(8));
            JPanel row = UI.panel(new BorderLayout(12, 0));
            row.add(agree, BorderLayout.WEST);
            JLabel t = UI.label("<html>이용 규칙과 <u>개인정보 처리방침</u>에 동의해요</html>", Theme.font(14), Theme.TEXT_2);
            UI.hand(t);
            t.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    about(app);
                }
            });
            row.add(t, BorderLayout.CENTER);
            row.setAlignmentX(0f);
            body.add(row);
        }
        left(body);
        Btn save = new Btn(firstRun ? "시작하기" : "저장", Btn.Kind.PRIMARY);
        Btn cancel = new Btn("취소", Btn.Kind.SECONDARY);
        Modal.Dialog d = firstRun
                ? Modal.dialog(app, "반가워요! 프로필을 만들어 주세요", "다른 사람에게 보이는 이름과 캐릭터예요. 나중에 바꿀 수 있어요.", body, 480, true, save)
                : Modal.dialog(app, "프로필", "다른 사람에게 보이는 이름과 캐릭터예요.", body, 480, true, cancel, save);
        Runnable submit = () -> {
            String n = name.getText().trim();
            String why = Names.checkName(n);
            if (why != null) {
                err.setText(why);
                name.setError(true);
                return;
            }
            if (!agree.isOn()) {
                err.setText("이용 규칙과 개인정보 처리방침에 동의해야 해요.");
                return;
            }
            app.prefs.name(n);
            app.prefs.avatar(picker.selected);
            app.prefs.agreed(true);
            app.prefs.save();
            d.close();
            app.title.repaint();
            if (then != null) then.run();
        };
        save.addActionListener(e -> submit.run());
        name.addActionListener(e -> submit.run());
        cancel.addActionListener(e -> d.close());
    }

    /** 로비에서 닉네임·캐릭터 바꾸기. 서버가 받아들이면 바로 다른 사람들에게도 바뀐 모습이 보인다 */
    static void profileOnline(App app) {
        JPanel body = column();
        AvatarPicker picker = new AvatarPicker(app.prefs.avatar());
        body.add(labeled("캐릭터", picker));
        body.add(Box.createVerticalStrut(14));
        Field name = new Field("2~10글자 · 한글, 영문, 숫자, _").icon("user").limit(Names.NAME_MAX);
        name.setText(app.myName);
        body.add(labeled("닉네임", name));
        JLabel err = UI.label(" ", Theme.font(13), Theme.RED);
        body.add(Box.createVerticalStrut(6));
        body.add(err);
        body.add(Box.createVerticalStrut(4));
        body.add(UI.paragraph("방에 들어가 있을 때는 바꿀 수 없어요. 로비에서 바꾸면 접속자 목록에도 바로 보여요.",
                Theme.font(13), Theme.TEXT_3, 420));
        left(body);
        Btn cancel = new Btn("취소", Btn.Kind.SECONDARY);
        Btn save = new Btn("저장", Btn.Kind.PRIMARY);
        Modal.Dialog d = Modal.dialog(app, "프로필 바꾸기", "다른 사람에게 보이는 닉네임과 캐릭터예요.", body, 480, true, cancel, save);
        Runnable submit = () -> {
            String n = name.getText().trim();
            String why = Names.checkName(n);
            if (why != null) {
                err.setText(why);
                name.setError(true);
                return;
            }
            if (n.equals(app.myName) && picker.selected == app.prefs.avatar()) {
                d.close();
                return;
            }
            name.setError(false);
            err.setText(" ");
            save.setEnabled(false);
            save.setText("저장 중…");
            app.changeProfile(n, picker.selected, problem -> {
                if (problem == null) {
                    d.close();
                    return;
                }
                save.setEnabled(true);
                save.setText("저장");
                err.setText(problem);
                name.setError(true);
            });
        };
        save.addActionListener(e -> submit.run());
        name.addActionListener(e -> submit.run());
        cancel.addActionListener(e -> d.close());
    }

    static final class AvatarPicker extends JComponent {
        private static final long serialVersionUID = 1L;
        int selected;

        AvatarPicker(int sel) {
            selected = Math.floorMod(sel, Names.AVATARS);
            UI.hand(this);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int i = e.getX() / 52;
                    if (i >= 0 && i < Names.AVATARS) {
                        selected = i;
                        repaint();
                    }
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(52 * Names.AVATARS, 52);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            for (int i = 0; i < Names.AVATARS; i++) {
                double x = i * 52 + 4;
                if (i == selected) {
                    g.setColor(Theme.CORAL);
                    g.setStroke(new java.awt.BasicStroke(3f));
                    g.draw(new java.awt.geom.Ellipse2D.Double(x - 2, 2, 48, 48));
                }
                Art.avatar(g, x + 3, 7, 38, i, false);
            }
            g.dispose();
        }
    }

    // ================================================================ 방 만들기

    static void createRoom(App app) {
        JPanel body = column();
        Field title = new Field("방 제목").limit(Names.TITLE_MAX);
        title.setText(app.myName + "님의 방");
        Controls.Segmented max = new Controls.Segmented(2, "2명", "3명", "4명");
        Controls.Segmented time = new Controls.Segmented(2, "30초", "45초", "60초", "90초", "무제한");
        Field.Pass pass = new Field.Pass("비밀번호 (비우면 누구나 들어올 수 있어요)").limit(12);
        body.add(labeled("방 제목", title));
        body.add(Box.createVerticalStrut(14));
        body.add(labeled("최대 인원", max));
        body.add(Box.createVerticalStrut(14));
        body.add(labeled("한 번에 쓸 수 있는 시간", time));
        body.add(Box.createVerticalStrut(14));
        body.add(labeled("비밀번호 (선택)", pass));
        left(body);
        Btn cancel = new Btn("취소", Btn.Kind.SECONDARY);
        Btn ok = new Btn("방 만들기", Btn.Kind.PRIMARY).icon("plus");
        Modal.Dialog d = Modal.dialog(app, "방 만들기", null, body, 500, true, cancel, ok);
        cancel.addActionListener(e -> d.close());
        Runnable submit = () -> {
            int turn = new int[] { 30, 45, 60, 90, 0 }[time.getSelected()];
            app.send(Proto.CREATE, title.getText(), max.getSelected() + 2, new String(pass.getPassword()), turn);
            d.close();
        };
        ok.addActionListener(e -> submit.run());
        title.addActionListener(e -> submit.run());
    }

    // ================================================================ 설정

    static void settings(App app) {
        Prefs p = app.prefs;
        JPanel body = column();
        Controls.Toggle sound = new Controls.Toggle(p.sound());
        Controls.Slider volume = new Controls.Slider(p.volume());
        volume.setPreferredSize(new Dimension(180, 28));
        Controls.Toggle chatSound = new Controls.Toggle(p.chatSound());
        Controls.Toggle alert = new Controls.Toggle(p.turnAlert());
        Controls.Toggle motion = new Controls.Toggle(p.reduceMotion());
        body.add(section("소리"));
        body.add(Controls.row("효과음", null, sound));
        body.add(Controls.row("음량", null, volume));
        body.add(Controls.row("채팅 알림음", "새 메시지가 오면 짧게 알려요", chatSound));
        body.add(section("게임"));
        body.add(Controls.row("내 차례 알림", "창이 뒤에 있으면 작업 표시줄에서 알려요", alert));
        body.add(Controls.row("움직임 줄이기", "애니메이션과 배경 효과를 줄여요", motion));
        body.add(section("차단한 사람"));
        JPanel blocked = column();
        fillBlocked(app, blocked);
        body.add(blocked);
        left(body);
        Runnable save = () -> {
            p.sound(sound.isOn());
            p.volume(volume.getValue());
            p.chatSound(chatSound.isOn());
            p.turnAlert(alert.isOn());
            p.reduceMotion(motion.isOn());
            p.save();
            app.applyPrefs();
        };
        sound.onChange(() -> {
            save.run();
            app.sound.play(Sound.Sfx.CLICK);
        });
        volume.onRelease(v -> {
            save.run();
            app.sound.play(Sound.Sfx.CORRECT);
        });
        for (Controls.Toggle t : new Controls.Toggle[] { chatSound, alert, motion }) t.onChange(save);
        Btn how = new Btn("게임 방법", Btn.Kind.GHOST).icon("help");
        Btn info = new Btn("정보 · 개인정보", Btn.Kind.GHOST).icon("info");
        Btn done = new Btn("완료", Btn.Kind.PRIMARY);
        javax.swing.JScrollPane sp = Controls.scroll(body);
        sp.setPreferredSize(new Dimension(460, 470));
        Modal.Dialog d = Modal.dialog(app, "설정", null, sp, 520, true, how, info, done);
        how.addActionListener(e -> {
            d.close();
            howTo(app);
        });
        info.addActionListener(e -> {
            d.close();
            about(app);
        });
        done.addActionListener(e -> d.close());
    }

    private static JComponent section(String title) {
        JPanel p = UI.panel(new BorderLayout());
        p.setBorder(UI.pad(14, 0, 4, 0));
        p.add(UI.label(title, Theme.bold(13), Theme.CORAL), BorderLayout.WEST);
        p.setAlignmentX(0f);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        return p;
    }

    private static void fillBlocked(App app, JPanel box) {
        box.removeAll();
        if (app.prefs.blocked().isEmpty()) {
            JLabel l = UI.label("차단한 사람이 없어요. 채팅에서 닉네임을 우클릭하면 차단할 수 있어요.", Theme.font(13), Theme.TEXT_3);
            l.setBorder(UI.pad(6, 0, 6, 0));
            box.add(l);
        }
        for (String n : app.prefs.blocked()) {
            Btn un = new Btn("차단 풀기", Btn.Kind.SECONDARY).small();
            un.addActionListener(e -> {
                app.prefs.block(n, false);
                fillBlocked(app, box);
            });
            box.add(Controls.row(n, null, un));
        }
        left(box);
        box.revalidate();
        box.repaint();
    }

    // ================================================================ 신고

    static void report(App app, String name) {
        String[] reasons = { "욕설·비하", "도배", "부적절한 닉네임", "게임 방해", "기타" };
        Controls.Segmented reason = new Controls.Segmented(0, reasons);
        JPanel body = column();
        body.add(labeled("이유", reason));
        body.add(Box.createVerticalStrut(10));
        body.add(UI.paragraph("신고하면 최근 채팅 기록과 함께 운영진에게 전달돼요. 거짓 신고는 제재될 수 있어요.", Theme.font(13), Theme.TEXT_3, 440));
        left(body);
        Btn cancel = new Btn("취소", Btn.Kind.SECONDARY);
        Btn ok = new Btn("신고하기", Btn.Kind.DANGER).icon("flag");
        Modal.Dialog d = Modal.dialog(app, name + "님 신고", null, body, 520, true, cancel, ok);
        cancel.addActionListener(e -> d.close());
        ok.addActionListener(e -> {
            app.send(Proto.REPORT, name, reasons[reason.getSelected()]);
            d.close();
        });
    }

    // ================================================================ 정보 · 개인정보

    static void about(App app) {
        JPanel body = column();
        JComponent logo = new JComponent() {
            private static final long serialVersionUID = 1L;

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(460, 120);
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = UI.g(g0);
                Art.logo(g, getWidth() / 2.0, 4, 0.82);
                g.dispose();
            }
        };
        body.add(logo);
        body.add(UI.label("버전 " + Proto.APP_VERSION + " · 프로토콜 " + Proto.VERSION + " · 만든 사람: Team STIM (고제윤, 임재윤)", Theme.font(13), Theme.TEXT_3));
        body.add(section("개인정보 처리방침 (요약)"));
        body.add(UI.paragraph(
                "• 수집하는 정보: 닉네임, 캐릭터 번호, 기기를 구별하는 임의의 ID(끊겼을 때 재접속용). 게임 기록은 서버에 남기지 않아요.\n"
                + "• 채팅은 서버에 저장하지 않아요. 신고가 들어온 경우에만 해당 사용자의 최근 채팅이 신고 기록에 함께 남아요.\n"
                + "• 위치, 연락처, 결제 정보 등 다른 개인정보는 수집하지 않아요. 광고나 외부 분석 도구도 쓰지 않아요.\n"
                + "• 이 PC의 설정 파일은 설정 폴더에만 저장되고, 앱을 지우면 함께 지울 수 있어요.",
                Theme.font(13), Theme.TEXT_2, 460));
        body.add(section("이용 규칙"));
        body.add(UI.paragraph("욕설·비하·도배·게임 방해는 금지예요. 채팅은 자동으로 걸러지고, 신고·차단 기능이 있어요. 규칙을 어기면 이용이 제한될 수 있어요.",
                Theme.font(13), Theme.TEXT_2, 460));
        body.add(section("오픈소스와 저작권"));
        body.add(UI.paragraph("외부 라이브러리 없이 Java 표준 기능만으로 만들었어요. 그림·아이콘·효과음은 모두 코드로 직접 만든 원본이에요. "
                + "숫자 타일 추리 규칙은 전통 추리 게임 방식을 바탕으로 직접 구현했어요.", Theme.font(13), Theme.TEXT_2, 460));
        body.add(section("설정 폴더"));
        body.add(UI.label(app.prefs.dir().toString(), Theme.font(12), Theme.TEXT_3));
        left(body);
        Btn ok = new Btn("닫기", Btn.Kind.PRIMARY);
        javax.swing.JScrollPane sp = Controls.scroll(body);
        sp.setPreferredSize(new Dimension(480, 460));
        Modal.Dialog d = Modal.dialog(app, "정보", null, sp, 540, true, ok);
        ok.addActionListener(e -> d.close());
    }

    // ================================================================ 게임 방법

    static void howTo(App app) {
        String[][] pages = {
            { "목표", "상대가 숨겨 둔 숫자 타일을 모두 맞혀서 공개시키면 그 사람은 탈락해요. 마지막까지 내 타일을 지킨 사람이 이겨요." },
            { "시작 패 고르기", "게임을 시작하면 처음 받을 타일을 검정 몇 개, 흰색 몇 개로 받을지 모두 동시에 골라요 (2~3명은 4개, 4명은 3개). 숫자는 무작위예요. 20초 안에 안 고르면 임의로 정해져요." },
            { "정렬 규칙이 열쇠", "모든 줄은 작은 숫자가 왼쪽에 놓여요. 같은 숫자면 검정이 왼쪽이에요. 공개된 타일 사이에 낀 숨은 타일은 숫자 범위가 좁아져요." },
            { "내 차례", "① 더미에서 검정이나 흰색 타일을 하나 가져와요. ② 상대의 숨은 타일 하나를 눌러 숫자를 골라요. 오른쪽 클릭으로 추측을 메모해 둘 수 있어요." },
            { "맞히면 · 틀리면", "맞히면 그 타일이 공개되고 한 번 더 도전하거나 멈출 수 있어요. 멈추면 가져온 타일을 숨긴 채 넣어요. 틀리면 가져온 타일이 모두에게 공개된 채로 들어가요." },
            { "조커", "조커(—)는 아무 자리에나 놓을 수 있는 특별한 타일이에요. 숫자 정렬 규칙을 따르지 않아서 추리를 헷갈리게 해요. 맞힐 때는 숫자 대신 '조커'를 고르면 돼요. 처음 나눠 줄 때는 빠져 있다가 더미에 섞여요." },
        };
        int[] page = { 0 };
        HowArt art = new HowArt();
        JLabel title = UI.label(pages[0][0], Theme.bold(20), Theme.TEXT);
        UI.Paragraph text = (UI.Paragraph) UI.paragraph(pages[0][1], Theme.font(15), Theme.TEXT_2, 500);
        Dots dots = new Dots(pages.length);
        JPanel body = column();
        body.add(art);
        body.add(Box.createVerticalStrut(14));
        body.add(title);
        body.add(Box.createVerticalStrut(8));
        body.add(text);
        body.add(Box.createVerticalStrut(14));
        body.add(dots);
        left(body);
        Btn prev = new Btn("이전", Btn.Kind.SECONDARY);
        Btn next = new Btn("다음", Btn.Kind.PRIMARY);
        Modal.Dialog d = Modal.dialog(app, "게임 방법", null, body, 580, true, prev, next);
        Runnable refresh = () -> {
            title.setText(pages[page[0]][0]);
            text.setText(pages[page[0]][1]);
            art.page = page[0];
            art.start = Anim.now();
            Anim.run(art, 900);
            dots.at = page[0];
            dots.repaint();
            prev.setEnabled(page[0] > 0);
            next.setText(page[0] == pages.length - 1 ? "시작해 볼게요" : "다음");
            next.revalidate();
            d.card.revalidate();
            d.card.repaint();
        };
        prev.addActionListener(e -> {
            if (page[0] > 0) page[0]--;
            refresh.run();
        });
        next.addActionListener(e -> {
            if (page[0] == pages.length - 1) {
                d.close();
                return;
            }
            page[0]++;
            refresh.run();
        });
        refresh.run();
    }

    private static final class Dots extends JComponent {
        private static final long serialVersionUID = 1L;
        final int n;
        int at;

        Dots(int n) {
            this.n = n;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(200, 12);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            for (int i = 0; i < n; i++) {
                g.setColor(i == at ? Theme.CORAL : UI.a(Theme.TEXT, 50));
                double w = i == at ? 22 : 8;
                double x = i * 16 + (i > at ? 14 : 0);
                g.fill(UI.rr(x, 2, w, 8, 4));
            }
            g.dispose();
        }
    }

    /** 게임 방법 그림 (페이지마다 다름) */
    private static final class HowArt extends JComponent {
        private static final long serialVersionUID = 1L;
        int page;
        long start = Anim.now();

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(520, 190);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, 190);
        }

        private Art.Look t(char c, String face, boolean back) {
            Art.Look l = new Art.Look();
            l.color = c;
            l.face = face;
            l.back = back;
            return l;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = UI.g(g0);
            int w = getWidth(), h = getHeight();
            Art.felt(g, new java.awt.geom.Rectangle2D.Double(0, 0, w, h), 18);
            double p = Anim.easeOut(Anim.progress(start, 700));
            double tw = 46, th = 66, gap = 10;
            switch (page) {
                case 0: {
                    // 위: 상대 줄 (일부 공개), 아래: 내 줄
                    String[] faces = { "1", "?", "6", "?" };
                    char[] cols = { 'B', 'W', 'W', 'B' };
                    double x0 = w / 2.0 - (4 * tw + 3 * gap) / 2;
                    for (int i = 0; i < 4; i++) {
                        Art.Look l = t(cols[i], faces[i], faces[i].equals("?"));
                        if (i == 1) {
                            l.glow = true;
                            l.flip = p;
                            l.back = p < 0.5;
                            l.face = "3";
                        }
                        Art.tile(g, x0 + i * (tw + gap), 22, tw, th, l);
                    }
                    UI.center(g, "상대", Theme.bold(12), Theme.TEXT_3, x0 - 30, 22 + th / 2);
                    String[] mine = { "0", "4", "8", "11" };
                    char[] mc = { 'W', 'B', 'B', 'W' };
                    for (int i = 0; i < 4; i++) Art.tile(g, x0 + i * (tw + gap), 106, tw, th - 4, t(mc[i], mine[i], false));
                    UI.center(g, "나", Theme.bold(12), Theme.TEXT_3, x0 - 30, 106 + th / 2);
                    break;
                }
                case 1: {
                    // 검정·흰색 조합 카드 (가운데 것을 고른 모습)
                    int opts = 5;
                    double cw = 84, cg = 10, cx0 = w / 2.0 - (opts * cw + (opts - 1) * cg) / 2;
                    double mtw = 15, mth = 22;
                    for (int i = 0; i < opts; i++) {
                        int black = 4 - i;
                        double cx = cx0 + i * (cw + cg);
                        boolean sel = i == 1;
                        double lift = sel ? 6 * p : 0;
                        g.setColor(sel ? UI.a(Theme.CORAL, 46) : UI.a(Color.WHITE, 16));
                        g.fill(UI.rr(cx, 30 - lift, cw, 96, 14));
                        g.setColor(sel ? Theme.CORAL : UI.a(Color.WHITE, 40));
                        g.draw(UI.rr(cx + 0.5, 30.5 - lift, cw - 1, 95, 14));
                        double tx = cx + (cw - (4 * mtw + 3 * 3)) / 2;
                        for (int k = 0; k < 4; k++) Art.tile(g, tx + k * (mtw + 3), 46 - lift, mtw, mth, t(k < black ? 'B' : 'W', "?", true));
                        UI.center(g, "검정 " + black, Theme.bold(12), sel ? Theme.CORAL : Theme.TEXT, cx + cw / 2, 88 - lift);
                        UI.center(g, "흰색 " + (4 - black), Theme.bold(12), sel ? Theme.CORAL : Theme.TEXT_2, cx + cw / 2, 106 - lift);
                    }
                    UI.center(g, "숫자는 무작위, 색 조합만 내가 골라요", Theme.bold(14), Theme.MINT, w / 2.0, 156);
                    break;
                }
                case 2: {
                    String[] faces = { "1", "1", "?", "7", "9" };
                    char[] cols = { 'B', 'W', 'B', 'W', 'B' };
                    double x0 = w / 2.0 - (5 * tw + 4 * gap) / 2;
                    for (int i = 0; i < 5; i++) {
                        Art.Look l = t(cols[i], faces[i], faces[i].equals("?"));
                        l.glow = i == 2;
                        Art.tile(g, x0 + i * (tw + gap), 30, tw, th, l);
                    }
                    UI.center(g, "작은 수 ←─────────────→ 큰 수", Theme.bold(13), Theme.TEXT_2, w / 2.0, 124);
                    UI.center(g, "가운데 검정 타일은 2~7 사이의 검정 숫자!", Theme.bold(14), Theme.MINT, w / 2.0, 154);
                    break;
                }
                case 3: {
                    for (int i = 2; i >= 0; i--) Art.tile(g, 70 + i * 3, 40 - i * 4, tw, th, t('B', "?", true));
                    for (int i = 2; i >= 0; i--) Art.tile(g, 140 + i * 3, 40 - i * 4, tw, th, t('W', "?", true));
                    double fx = 140 + (300 - 140) * p, fy = 40 - Math.sin(p * Math.PI) * 30;
                    Art.tile(g, fx, fy, tw, th, t('W', "?", true));
                    Icons.paint(g, "next", 230, 62, 26, Theme.TEXT_2);
                    double x0 = 370;
                    Art.Look target = t('B', "?", true);
                    target.glow = true;
                    Art.tile(g, x0, 40, tw, th, target);
                    g.setColor(new Color(0x1F2846));
                    g.fill(UI.rr(x0 + 56, 40, 70, 66, 12));
                    UI.center(g, "5?", Theme.num(24), Theme.CORAL, x0 + 91, 73);
                    UI.center(g, "① 가져오기", Theme.bold(13), Theme.TEXT_2, 120, 150);
                    UI.center(g, "② 숫자 맞히기", Theme.bold(13), Theme.TEXT_2, 420, 150);
                    break;
                }
                case 4: {
                    Art.Look ok = t('W', "5", false);
                    ok.glow = true;
                    ok.glowColor = Theme.MINT;
                    Art.tile(g, w / 4.0 - tw / 2, 34, tw, th, ok);
                    UI.center(g, "정답! 공개되고 한 번 더", Theme.bold(14), Theme.MINT, w / 4.0, 130);
                    Art.Look no = t('B', "8", false);
                    no.mineOpen = true;
                    double sh = Math.sin(p * 20) * 5 * (1 - p);
                    Art.tile(g, w * 3 / 4.0 - tw / 2 + sh, 34, tw, th, no);
                    UI.center(g, "틀리면 가져온 타일 공개", Theme.bold(14), Theme.RED, w * 3 / 4.0, 130);
                    break;
                }
                default: {
                    // 조커가 숫자 사이 아무 데나 끼어 있는 줄
                    String[] faces = { "2", "-", "1", "7", "9" };
                    char[] cols = { 'B', 'W', 'W', 'B', 'W' };
                    double x0 = w / 2.0 - (5 * tw + 4 * gap) / 2;
                    for (int i = 0; i < 5; i++) {
                        Art.Look l = t(cols[i], faces[i], false);
                        l.glow = i == 1;
                        l.glowColor = Theme.AMBER;
                        Art.tile(g, x0 + i * (tw + gap), 34, tw, th, l);
                    }
                    UI.center(g, "조커는 정렬 규칙과 상관없이 아무 자리에나!", Theme.bold(14), Theme.AMBER, w / 2.0, 140);
                }
            }
            g.dispose();
        }
    }
}
