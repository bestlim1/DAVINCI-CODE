package stim.davincicode.server;

import stim.davincicode.common.GameView;

/** 서버만 가지고 있는 진짜 타일. 클라이언트에는 {@link #codeFor}로 걸러진 표기만 나간다. */
final class Tile {
    final int uid;        // 판마다 무작위로 섞어 매기는 번호 (숫자와 관계없음)
    final char color;     // 'B' 검정, 'W' 흰색
    final int value;      // 0~11, 조커는 -1
    boolean revealed;

    Tile(int uid, char color, int value) {
        this.uid = uid;
        this.color = color;
        this.value = value;
    }

    boolean joker() {
        return value == GameView.JOKER;
    }

    /** 정렬용 값: 숫자가 작으면 왼쪽, 같은 숫자면 검정이 왼쪽 */
    int key() {
        return value * 2 + (color == 'W' ? 1 : 0);
    }

    /** viewer가 볼 수 있는 만큼만 담은 표기. viewer 0은 관전자. */
    String codeFor(int viewer, int owner, boolean revealAll) {
        boolean visible = revealAll || revealed || viewer == owner;
        return GameView.code(uid, color, visible ? value : GameView.UNKNOWN, revealed);
    }

    @Override
    public String toString() {
        return "" + color + (joker() ? "J" : String.valueOf(value)) + (revealed ? "*" : "");
    }
}
