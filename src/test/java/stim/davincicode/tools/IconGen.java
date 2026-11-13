package stim.davincicode.tools;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import stim.davincicode.client.ui.Art;
import stim.davincicode.client.ui.Theme;

/**
 * 앱 아이콘 파일을 만든다 (코드로 그린 아이콘 → icon.png, icon.ico, 스토어용 큰 PNG들).
 *   java -cp ... stim.davincicode.tools.IconGen packaging
 */
public final class IconGen {
    private IconGen() { }

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "packaging");
        dir.mkdirs();
        Theme.init();
        ImageIO.write(Art.appIcon(512), "png", new File(dir, "icon.png"));
        ImageIO.write(Art.appIcon(1024), "png", new File(dir, "icon-1024.png"));
        // Microsoft Store 타일 크기
        for (int s : new int[] { 44, 50, 150, 300 }) ImageIO.write(Art.appIcon(s), "png", new File(dir, "store-" + s + ".png"));

        int[] sizes = { 16, 24, 32, 48, 64, 128, 256 };
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            BufferedImage img = Art.appIcon(s);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            pngs.add(bos.toByteArray());
        }
        // ICO: 헤더 6바이트 + 항목 16바이트씩 + PNG 데이터
        int offset = 6 + 16 * sizes.length;
        ByteBuffer head = ByteBuffer.allocate(offset).order(ByteOrder.LITTLE_ENDIAN);
        head.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            head.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            head.putShort((short) 1).putShort((short) 32);
            head.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        try (FileOutputStream out = new FileOutputStream(new File(dir, "icon.ico"))) {
            out.write(head.array());
            for (byte[] p : pngs) out.write(p);
        }
        // 웹 버전 아이콘 (브라우저 탭, 휴대폰 홈 화면)
        File web = new File(args.length > 1 ? args[1] : "src/main/resources/stim/davincicode/web");
        web.mkdirs();
        ImageIO.write(Art.appIcon(192), "png", new File(web, "icon-192.png"));
        ImageIO.write(Art.appIcon(512), "png", new File(web, "icon-512.png"));
        System.out.println("아이콘 생성: " + dir.getAbsolutePath() + ", " + web.getAbsolutePath());
    }
}
