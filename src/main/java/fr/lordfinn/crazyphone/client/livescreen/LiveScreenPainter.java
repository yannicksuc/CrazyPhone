package fr.lordfinn.crazyphone.client.livescreen;

import fr.lordfinn.crazyphone.utils.PhoneScreenApp;

/**
 * Paints a {@link LiveScreenView} into a tiny ARGB pixel grid, in the phone's own pixel-art style: the same
 * pale cyan screen, slate ink and yellow header as the phone GUI and the item textures.
 *
 * The grid is {@value #WIDTH}x{@value #HEIGHT}: exactly 4x4 canvas pixels per texel of the phone's screen
 * on its 32px item texture (8x12 texels, see crazyphone.png), so the live screen lines up with the item's
 * own pixel grid and stays crisp. The two bottom corners are the bezel's rounded corners on the texture and
 * are left transparent (0), which also means "not drawn".
 *
 * Pure Java on purpose (no Minecraft class at all): it can't fail on a version difference, and it is cheap
 * to run again whenever the view changes (a few thousand int writes).
 */
public final class LiveScreenPainter {
    public static final int WIDTH = 32;
    public static final int HEIGHT = 48;

    // Palette, taken from the phone's own textures (phone-background.png, crazyphone.png,
    // crazyphone-header-background.png, CrazyPhoneColors).
    static final int SCREEN = 0xFFCCF8FA;
    static final int SCREEN_MID = 0xFFA9F6F4;
    static final int SCREEN_DEEP = 0xFF8FF9F3;
    static final int GLARE = 0xFFE0F6F5;
    static final int TEAL = 0xFF58D8D1;
    static final int NIGHT_TOP = 0xFF1E2A44;
    static final int NIGHT_MID = 0xFF19223A;
    static final int NIGHT_BOTTOM = 0xFF14161A;
    static final int DUSK_TOP = 0xFFFFB86B;
    static final int DUSK_MID = 0xFFD98A7E;
    static final int DUSK_BOTTOM = 0xFF7E5BA8;
    static final int INK = 0xFF3A4456;
    static final int INK_SOFT = 0xFF4C5974;
    static final int LINE = 0xFF9FB4C4;
    static final int WHITE = 0xFFFFFFFF;
    static final int YELLOW = 0xFFFFD100;
    static final int YELLOW_SHADE = 0xFFE0A800;
    static final int ACCENT = 0xFFFFC107;
    static final int GREEN = 0xFF2ECC71;
    static final int GREEN_DARK = 0xFF1E9E55;
    static final int GREEN_LIGHT = 0xFFA8EFC4;
    static final int RED = 0xFFE74C3C;
    static final int BLUE = 0xFF4A90E2;
    static final int SKY = 0xFF7EC8F0;
    static final int GRASS = 0xFF72C266;
    static final int VIEWFINDER = 0xFF101318;
    static final int VIEW_SKY = 0xFF1C2B40;
    static final int VIEW_HILL = 0xFF1D3326;
    static final int SKIN = 0xFFE0AC69;
    static final int SKIN_SHADE = 0xFFC48A4F;
    static final int HAIR = 0xFF6B4A2B;
    static final int SHIRT = 0xFF2BB5B5;

    // 3x5 font: digits, A-Z and a few symbols. Each glyph is 5 rows of 3 bits, top row first.
    private static final String SMALL_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-_:.+?!";
    private static final String[] SMALL = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
            "010101111101101", "110101110101110", "011100100100011", "110101101101110", "111100110100111",
            "111100110100100", "011100101101011", "101101111101101", "111010010010111", "001001001101010",
            "101101110101101", "100100100100111", "101111111101101", "110101101101101", "010101101101010",
            "110101110100100", "010101101110011", "110101110101101", "011100010001110", "111010010010010",
            "101101101101111", "101101101101010", "101101111111101", "101101010101101", "101101010010010",
            "111001010100111", "000000111000000", "000000000000111", "000010000010000", "000000000000010",
            "000010111010000", "111001011000010", "010010010000010"
    };
    // 4x7 font for the big clock / call timer digits, 7 rows of 4 bits.
    private static final String[] BIG = {
            "0110100110011001100110010110", "0010011000100010001000100111", "0110100100010010010010001111",
            "1110000100010110000100011110", "1001100110011111000100010001", "1111100011100001000110010110",
            "0110100010001110100110010110", "1111000100100010010001000100", "0110100110010110100110010110",
            "0110100110010111000100010110"
    };

    // 7x7 icons ('#' = drawn), used in the app header and scaled up elsewhere.
    private static final String[] ICON_PHONE = {
            "##.....",
            "###....",
            ".##....",
            ".##....",
            "..##.##",
            "...####",
            "....##."};
    private static final String[] ICON_HOME = {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            ".##.##.",
            ".##.##.",
            "......."};
    private static final String[] ICON_PERSON = {
            "..###..",
            "..###..",
            "..###..",
            ".......",
            ".#####.",
            "#######",
            "#######"};
    private static final String[] ICON_CHAT = {
            "#######",
            "#.....#",
            "#.###.#",
            "#.....#",
            "#######",
            ".##....",
            ".#....."};
    private static final String[] ICON_CAMERA = {
            ".......",
            "..##...",
            "#######",
            "##...##",
            "##.#.##",
            "##...##",
            "#######"};
    private static final String[] ICON_PHOTO = {
            "#######",
            "#.....#",
            "#....##",
            "#..#..#",
            "#.###.#",
            "#######",
            "......."};
    private static final String[] ICON_LOCK = {
            "..###..",
            ".#...#.",
            ".#...#.",
            "#######",
            "###.###",
            "###.###",
            "#######"};
    private static final String[] ICON_GEAR = {
            "..#.#..",
            ".#####.",
            "##...##",
            ".#...#.",
            "##...##",
            ".#####.",
            "..#.#.."};
    private static final String[] ICON_BALLOT = {
            "...#...",
            "..#.#..",
            "...#...",
            "#######",
            "#.###.#",
            "#.....#",
            "#######"};
    private static final String[] ICON_DOTS = {
            ".......",
            ".......",
            ".......",
            "#.#.#.#",
            ".......",
            ".......",
            "......."};
    private static final String[] ICON_ENVELOPE = {
            "#######",
            "##...##",
            "#.#.#.#",
            "#..#..#",
            "#######"};
    // Generic 8x8 Minecraft-style face for call tiles: deliberately nobody's real skin (privacy, and no
    // texture read-back needed). h = hair, s = skin, d = skin shade, e = eye, m = mouth.
    private static final String[] FACE = {
            "hhhhhhhh",
            "hhhhhhhh",
            "hssssssh",
            "seessees",
            "ssssssss",
            "sssddsss",
            "ssmmmmss",
            "dsssssss"};

    private final int[] px = new int[WIDTH * HEIGHT];

    private LiveScreenPainter() {
    }

    /** Paints {@code view} into a fresh grid (row-major, ARGB, 0 = transparent / not drawn). */
    public static int[] paint(LiveScreenView view) {
        LiveScreenPainter p = new LiveScreenPainter();
        switch (view.mode()) {
            case LOCK -> p.lock(view);
            case RINGING -> p.ringing(view);
            case DIALING -> p.dialing(view);
            case IN_CALL -> p.inCall(view);
            case CAMERA -> p.camera(view);
            case APP -> p.app(view);
        }
        p.bezelCorners();
        return p.px;
    }

    // ---- screens --------------------------------------------------------------------------------------

    private void lock(LiveScreenView v) {
        wallpaper(v.dayPhase());
        int ink = v.dayPhase() == LiveScreenView.DAY ? INK : SCREEN;
        int soft = v.dayPhase() == LiveScreenView.DAY ? INK_SOFT : LINE;
        statusBar(soft);
        int minutes = Math.floorMod(v.clockMinutes(), 24 * 60);
        String clock = two(minutes / 60) + ":" + two(minutes % 60);
        int clockY = 9;
        int width = bigWidth(clock);
        int x = (WIDTH - width) / 2;
        if (v.dayPhase() == LiveScreenView.DAY)
            bigText(clock, x + 1, clockY + 1, TEAL);
        bigText(clock, x, clockY, ink);
        // Sun or moon under the clock.
        if (v.dayPhase() == LiveScreenView.NIGHT) {
            rect(14, 19, 3, 4, SCREEN);
            px(17, 19, SCREEN);
            px(17, 22, SCREEN);
            px(13, 20, SCREEN);
            px(13, 21, SCREEN);
        } else {
            int sun = v.dayPhase() == LiveScreenView.DAY ? YELLOW : ACCENT;
            rect(14, 19, 4, 4, sun);
            px(15, 18, sun);
            px(16, 18, sun);
            px(15, 23, sun);
            px(16, 23, sun);
            px(13, 20, sun);
            px(13, 21, sun);
            px(18, 20, sun);
            px(18, 21, sun);
        }
        if (v.unread() > 0) {
            String count = v.unread() > 9 ? "9+" : Integer.toString(v.unread());
            int w = 7 + 2 + smallWidth(count) + 2 + 2;
            int bx = (WIDTH - w) / 2;
            int by = 28;
            roundRect(bx, by, w, 9, YELLOW);
            hline(bx + 1, by + 8, w - 2, YELLOW_SHADE);
            icon(ICON_ENVELOPE, bx + 2, by + 2, INK);
            smallText(count, bx + 2 + 7 + 2, by + 2, INK);
        }
        homeBar(soft);
    }

    private void ringing(LiveScreenView v) {
        gradient(GREEN_LIGHT, SCREEN, SCREEN);
        int cx = 16, cy = 15;
        // Expanding rings, one step per animation frame.
        int r = 9 + (v.frame() % 3) * 2;
        ringOutline(cx, cy, r, v.frame() % 3 == 2 ? SCREEN_DEEP : TEAL);
        disc(cx, cy, 7, GREEN);
        int shake = v.frame() % 2 == 0 ? -1 : 1;
        icon(ICON_PHONE, cx - 3 + shake, cy - 3, WHITE);
        if (!v.caller().isEmpty())
            marquee(v.caller(), 26, INK, v.frame());
        else
            dots(26, v.frame(), INK_SOFT);
        callButton(9, 38, RED, false);
        callButton(23, 38, GREEN, true);
        homeBar(INK_SOFT);
    }

    private void dialing(LiveScreenView v) {
        gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
        int cx = 16, cy = 15;
        disc(cx, cy, 7, TEAL);
        icon(ICON_PHONE, cx - 3, cy - 3, WHITE);
        if (!v.caller().isEmpty())
            marquee(v.caller(), 26, INK, v.frame());
        dots(v.caller().isEmpty() ? 26 : 33, v.frame(), INK_SOFT);
        callButton(16, 39, RED, false);
        homeBar(INK_SOFT);
    }

    private void inCall(LiveScreenView v) {
        gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
        header(GREEN, GREEN_DARK, ICON_PHONE, WHITE);
        String timer;
        if (v.callSeconds() < 0) {
            timer = "--:--";
        } else if (v.callSeconds() < 3600) {
            timer = two(v.callSeconds() / 60) + ":" + two(v.callSeconds() % 60);
        } else {
            int hours = Math.min(9, v.callSeconds() / 3600);
            timer = hours + ":" + two((v.callSeconds() / 60) % 60);
        }
        if (v.callSeconds() < 0)
            smallText(timer, (WIDTH - smallWidth(timer)) / 2, 12, INK);
        else
            bigText(timer, (WIDTH - bigWidth(timer)) / 2, 10, INK);
        int n = Math.max(1, Math.min(4, v.participants()));
        int tile = 8, gap = 2;
        int total = n * tile + (n - 1) * gap;
        int tx = (WIDTH - total) / 2;
        for (int i = 0; i < n; i++)
            face(tx + i * (tile + gap), 20);
        // Voice level bars, gently animated.
        int[] heights = {2, 4, 3, 5, 2};
        for (int i = 0; i < 5; i++) {
            int h = 1 + (heights[(i + v.frame()) % 5]);
            rect(11 + i * 2, 34 - h, 1, h, TEAL);
        }
        callButton(16, 39, RED, false);
    }

    private void camera(LiveScreenView v) {
        fill(VIEWFINDER);
        // A dim, generic scene (sky and a hill) so it reads as a viewfinder, never the real image.
        rect(2, 8, 28, 20, VIEW_SKY);
        for (int x = 2; x < 30; x++) {
            int top = 22 + (int) Math.round(3 * Math.sin((x + 4) / 5.0));
            rect(x, top, 1, 36 - top, VIEW_HILL);
        }
        rect(2, 28, 28, 8, VIEW_HILL);
        // Corner brackets.
        int l = 2, t = 6, rr = 29, b = 37;
        hline(l, t, 4, WHITE);
        vline(l, t, 4, WHITE);
        hline(rr - 3, t, 4, WHITE);
        vline(rr, t, 4, WHITE);
        hline(l, b, 4, WHITE);
        vline(l, b - 3, 4, WHITE);
        hline(rr - 3, b, 4, WHITE);
        vline(rr, b - 3, 4, WHITE);
        // Focus cross.
        hline(14, 21, 4, GLARE);
        vline(15, 20, 1, GLARE);
        vline(16, 22, 1, GLARE);
        // Recording dot, blinking.
        if (v.frame() % 2 == 0)
            rect(4, 2, 2, 2, RED);
        smallText("REC", 7, 1, v.frame() % 2 == 0 ? WHITE : LINE);
        // Shutter button.
        disc(16, 42, 3, WHITE);
        disc(16, 42, 2, LINE);
        px(16, 42, WHITE);
    }

    private void app(LiveScreenView v) {
        gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
        PhoneScreenApp app = v.app();
        header(YELLOW, YELLOW_SHADE, iconFor(app), INK);
        int f = v.frame();
        switch (app) {
            case HOME -> {
                appTile(4, 12, ACCENT, ICON_PERSON, INK);
                appTile(18, 12, TEAL, ICON_CAMERA, INK);
                appTile(4, 26, BLUE, ICON_PHOTO, WHITE);
                appTile(18, 26, GREEN, ICON_CHAT, WHITE);
            }
            case CONTACTS -> {
                int[] colors = {ACCENT, TEAL, BLUE, GREEN};
                for (int i = 0; i < 4; i++) {
                    int y = 11 + i * 8;
                    roundRect(3, y, 6, 6, colors[i]);
                    hline(11, y + 1, 14 - (i % 2) * 4, INK_SOFT);
                    hline(11, y + 3, 9 + (i % 3) * 2, LINE);
                }
            }
            case CONVERSATION -> {
                bubble(2, 11, 16, false);
                bubble(14, 18, 16, true);
                bubble(2, 25, 12, false);
                // "Someone is typing": the last bubble's dots.
                roundRect(14, 32, 16, 7, YELLOW);
                for (int i = 0; i < 3; i++)
                    rect(18 + i * 3, 35 - (i == f % 3 ? 1 : 0), 2, 2, INK);
                hline(2, 41, 28, WHITE);
            }
            case PHOTOS -> {
                for (int row = 0; row < 3; row++)
                    for (int col = 0; col < 3; col++)
                        thumbnail(3 + col * 9, 11 + row * 10, (row * 3 + col + f) % 4);
            }
            case CALL -> {
                disc(16, 22, 8, GREEN);
                icon(ICON_PHONE, 13, 19, WHITE);
                dots(34, f, INK_SOFT);
            }
            case LOCKED -> {
                iconScaled(ICON_LOCK, 9, 12, 2, INK);
                for (int i = 0; i < 4; i++) {
                    boolean filled = i <= f % 4;
                    roundRect(5 + i * 6, 31, 4, 4, filled ? INK : LINE);
                }
            }
            case SETTINGS -> {
                iconScaled(ICON_GEAR, 9, 11, 2, INK_SOFT);
                hline(4, 29, 24, LINE);
                hline(4, 32, 18, LINE);
                roundRect(20, 35, 8, 4, f % 2 == 0 ? GREEN : LINE);
                hline(4, 36, 12, LINE);
            }
            case ELECTION -> {
                iconScaled(ICON_BALLOT, 9, 11, 2, INK);
                int[] votes = {14, 9, 18};
                int[] colors = {ACCENT, TEAL, BLUE};
                for (int i = 0; i < 3; i++)
                    rect(4, 29 + i * 4, Math.min(24, votes[i] + (i == f % 3 ? 1 : 0)), 2, colors[i]);
            }
            default -> {
                hline(4, 13, 24, LINE);
                hline(4, 17, 18, LINE);
                hline(4, 21, 22, LINE);
                hline(4, 25, 12, LINE);
            }
        }
        homeBar(INK_SOFT);
    }

    private static String[] iconFor(PhoneScreenApp app) {
        return switch (app) {
            case HOME -> ICON_HOME;
            case CONTACTS -> ICON_PERSON;
            case CONVERSATION -> ICON_CHAT;
            case CAMERA -> ICON_CAMERA;
            case PHOTOS -> ICON_PHOTO;
            case CALL -> ICON_PHONE;
            case LOCKED -> ICON_LOCK;
            case SETTINGS -> ICON_GEAR;
            case ELECTION -> ICON_BALLOT;
            default -> ICON_DOTS;
        };
    }

    // ---- building blocks -------------------------------------------------------------------------------

    private void wallpaper(int dayPhase) {
        switch (dayPhase) {
            case LiveScreenView.NIGHT -> {
                gradient(NIGHT_TOP, NIGHT_MID, NIGHT_BOTTOM);
                int[][] stars = {{4, 6}, {25, 4}, {9, 25}, {27, 22}, {20, 33}, {3, 38}};
                for (int[] s : stars)
                    px(s[0], s[1], SCREEN);
            }
            case LiveScreenView.TWILIGHT -> gradient(DUSK_TOP, DUSK_MID, DUSK_BOTTOM);
            default -> {
                gradient(SCREEN, SCREEN_MID, SCREEN_DEEP);
                // The diagonal glare band of the item texture, one texel (4 px) wide.
                for (int y = 0; y < HEIGHT; y++) {
                    int x = 30 - y;
                    hline(Math.max(0, x), y, 3, GLARE);
                }
            }
        }
    }

    /** Three horizontal bands rather than a smooth ramp: reads the same at this size, far fewer quads. */
    private void gradient(int top, int mid, int bottom) {
        rect(0, 0, WIDTH, 16, top);
        rect(0, 16, WIDTH, 16, mid);
        rect(0, 32, WIDTH, HEIGHT - 32, bottom);
    }

    private void header(int color, int shade, String[] icon, int iconColor) {
        rect(0, 0, WIDTH, 8, color);
        hline(0, 8, WIDTH, shade);
        icon(icon, (WIDTH - icon[0].length()) / 2, 1, iconColor);
    }

    private void statusBar(int color) {
        // Signal bars, left.
        for (int i = 0; i < 3; i++)
            rect(2 + i * 2, 4 - i, 1, 1 + i, color);
        // Battery, right.
        rect(23, 2, 6, 3, color);
        rect(24, 3, 3, 1, color == INK_SOFT ? GREEN : GREEN_LIGHT);
        px(29, 3, color);
    }

    private void homeBar(int color) {
        hline(11, 45, 10, color);
    }

    private void appTile(int x, int y, int color, String[] icon, int iconColor) {
        roundRect(x, y, 10, 10, color);
        hline(x + 1, y + 10, 8, INK_SOFT);
        icon(icon, x + 2, y + 2, iconColor);
    }

    private void bubble(int x, int y, int w, boolean mine) {
        roundRect(x, y, w, 6, mine ? YELLOW : WHITE);
        hline(x + 2, y + 2, w - 5, mine ? YELLOW_SHADE : LINE);
        hline(x + 2, y + 4, w / 2, mine ? YELLOW_SHADE : LINE);
    }

    private void thumbnail(int x, int y, int variant) {
        rect(x, y, 8, 8, WHITE);
        rect(x + 1, y + 1, 6, 4, variant == 2 ? DUSK_TOP : SKY);
        rect(x + 1, y + 5, 6, 2, GRASS);
        px(x + 2 + variant, y + 4, GRASS);
        px(x + 5, y + 2, YELLOW);
    }

    private void face(int x, int y) {
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 8; col++) {
                char c = FACE[row].charAt(col);
                int color = switch (c) {
                    case 'h' -> HAIR;
                    case 'e' -> INK;
                    case 'm' -> SKIN_SHADE;
                    case 'd' -> SKIN_SHADE;
                    default -> SKIN;
                };
                px(x + col, y + row, color);
            }
        hline(x, y + 8, 8, SHIRT);
    }

    private void callButton(int cx, int cy, int color, boolean answer) {
        disc(cx, cy, 4, color);
        if (answer) {
            rect(cx - 2, cy - 1, 1, 2, WHITE);
            rect(cx - 1, cy + 1, 3, 1, WHITE);
            rect(cx + 2, cy - 1, 1, 2, WHITE);
        } else {
            rect(cx - 2, cy, 5, 1, WHITE);
        }
    }

    private void dots(int y, int frame, int color) {
        for (int i = 0; i < 3; i++)
            rect(11 + i * 4, y - (i == frame % 3 ? 1 : 0), 2, 2, color);
    }

    /** Centered if it fits, otherwise scrolled by one character per animation frame. */
    private void marquee(String text, int y, int color, int frame) {
        int width = smallWidth(text);
        if (width <= WIDTH - 1) {
            smallText(text, (WIDTH - width + 1) / 2, y, color);
            return;
        }
        int visibleChars = (WIDTH - 4) / 4;
        String loop = text + "   ";
        int start = Math.floorMod(frame, loop.length());
        StringBuilder window = new StringBuilder();
        for (int i = 0; i < visibleChars; i++)
            window.append(loop.charAt((start + i) % loop.length()));
        smallText(window.toString(), 2, y, color);
    }

    private void bezelCorners() {
        for (int y = HEIGHT - 4; y < HEIGHT; y++)
            for (int x = 0; x < 4; x++) {
                px[y * WIDTH + x] = 0;
                px[y * WIDTH + (WIDTH - 1 - x)] = 0;
            }
    }

    // ---- text ------------------------------------------------------------------------------------------

    static int smallWidth(String text) {
        return text.isEmpty() ? 0 : text.length() * 4 - 1;
    }

    private void smallText(String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            int index = SMALL_CHARS.indexOf(Character.toUpperCase(text.charAt(i)));
            if (index < 0) {
                x += 4; // unknown or space: blank cell
                continue;
            }
            String bits = SMALL[index];
            for (int row = 0; row < 5; row++)
                for (int col = 0; col < 3; col++)
                    if (bits.charAt(row * 3 + col) == '1')
                        px(x + col, y + row, color);
            x += 4;
        }
    }

    static int bigWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++)
            width += Character.isDigit(text.charAt(i)) ? 5 : 2;
        return Math.max(0, width - 1);
    }

    private void bigText(String text, int x, int y, int color) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                String bits = BIG[c - '0'];
                for (int row = 0; row < 7; row++)
                    for (int col = 0; col < 4; col++)
                        if (bits.charAt(row * 4 + col) == '1')
                            px(x + col, y + row, color);
                x += 5;
            } else {
                if (c == ':') {
                    px(x, y + 2, color);
                    px(x, y + 4, color);
                } else if (c == '-') {
                    px(x, y + 3, color);
                }
                x += 2;
            }
        }
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    // ---- primitives ------------------------------------------------------------------------------------

    private void px(int x, int y, int color) {
        if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT)
            px[y * WIDTH + x] = color;
    }

    private void fill(int color) {
        java.util.Arrays.fill(px, color);
    }

    private void rect(int x, int y, int w, int h, int color) {
        for (int yy = Math.max(0, y); yy < Math.min(HEIGHT, y + h); yy++)
            for (int xx = Math.max(0, x); xx < Math.min(WIDTH, x + w); xx++)
                px[yy * WIDTH + xx] = color;
    }

    private void hline(int x, int y, int w, int color) {
        rect(x, y, w, 1, color);
    }

    private void vline(int x, int y, int h, int color) {
        rect(x, y, 1, h, color);
    }

    private void roundRect(int x, int y, int w, int h, int color) {
        rect(x + 1, y, w - 2, h, color);
        rect(x, y + 1, w, h - 2, color);
    }

    private void disc(int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++)
            for (int x = -r; x <= r; x++)
                if (x * x + y * y <= r * r + r)
                    px(cx + x, cy + y, color);
    }

    private void ringOutline(int cx, int cy, int r, int color) {
        int outer = r * r + r;
        int inner = (r - 1) * (r - 1) + (r - 1);
        for (int y = -r; y <= r; y++)
            for (int x = -r; x <= r; x++) {
                int d = x * x + y * y;
                if (d <= outer && d > inner)
                    px(cx + x, cy + y, color);
            }
    }

    private void icon(String[] icon, int x, int y, int color) {
        iconScaled(icon, x, y, 1, color);
    }

    private void iconScaled(String[] icon, int x, int y, int scale, int color) {
        for (int row = 0; row < icon.length; row++)
            for (int col = 0; col < icon[row].length(); col++)
                if (icon[row].charAt(col) == '#')
                    rect(x + col * scale, y + row * scale, scale, scale, color);
    }
}
