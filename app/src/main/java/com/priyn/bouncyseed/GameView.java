package com.priyn.bouncyseed;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

/**
 * Bouncy Seed (MYBO LIMITED) Authentic 64-bit Android Revival.
 * Target Platform: Modern 64-bit Android (Samsung Galaxy S24 FE / Android 14+).
 * Baseline Resolution: 960 x 640 (Retina 3:2 baseline from original Haxe/NME engine).
 * 100% Authentic recovered sprites, bitmap number fonts, particle emitters, and OST/SFX.
 */
public final class GameView extends View {

    // Game States
    public static final int STATE_MENU = 0;
    public static final int STATE_PLAY = 1;
    public static final int STATE_PAUSE = 2;
    public static final int STATE_GAME_OVER = 3;
    public static final int STATE_SHOP = 4;

    // Design baseline: 640p virtual height (Retina 960x640 baseline)
    private static final float VIRTUAL_HEIGHT = 640f;
    private static final float SEED_RADIUS = 34f;
    private static final float WATER_LEVEL = 585f;
    private static final float GRAVITY = 720f;

    // Rainbow colors for power-up trail
    private static final int[] RAINBOW_COLORS = {
        0xfff33f4d, 0xffffa34d, 0xffffe456, 0xff5ce17b, 0xff4fc5f9, 0xff9c6ef2
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final HashMap<String, Bitmap> sprites = new HashMap<>();
    private final SharedPreferences prefs;
    private final Random random = new Random(540213);
    private final Vibrator vibrator;

    // Entity lists
    private final ArrayList<Trampoline> trampolines = new ArrayList<>();
    private final ArrayList<Sunlight> sunlights = new ArrayList<>();
    private final ArrayList<Hazard> hazards = new ArrayList<>();
    private final ArrayList<SeasonalItem> seasonItems = new ArrayList<>();
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<ScorePopup> scorePopups = new ArrayList<>();
    // Ambient Road Car Entity (bga2: blue van, red sedan, small compact)
    private static final class AmbientCar {
        float x;
        int type;
        float speed;
        AmbientCar(float x, int type, float speed) {
            this.x = x; this.type = type; this.speed = speed;
        }
    }

    // Ambient Jumping Clownfish Entity (fish.png)
    private static final class AmbientFish {
        float x;
        float timer = 0f;
        float duration = 1.4f;
        boolean active = false;
        float nextJump = 1.5f;
    }

    // Ambient Sky Bird Entity (bird1 7 frames, bird2 8 frames)
    private static final class AmbientBird {
        float x, y;
        float vx;
        int type;
        float flapTimer = 0f;
        AmbientBird(float x, float y, float vx, int type) {
            this.x = x; this.y = y; this.vx = vx; this.type = type;
        }
    }

    private final ArrayList<AmbientCar> ambientCars = new ArrayList<>();
    private final ArrayList<AmbientFish> ambientFish = new ArrayList<>();
    private final ArrayList<AmbientBird> ambientBirds = new ArrayList<>();


    // Viewport & Motion
    private float W = 960f;
    private float scale = 1f;
    private float clock = 0f;
    private float cameraX = 0f;
    private float seedX = 170f, seedY = 220f;
    private float seedVx = 240f, seedVy = -90f;
    private float oldSeedX = 170f, oldSeedY = 220f;
    private float seedRotation = 0f;
    private float bounceSquashTimer = 0f;
    private float farthestPickup = 200f, farthestHazard = 550f, farthestDecor = 220f;
    private float introTime = 0f;

    // Power-ups
    private float rainbowExpires = -1f;
    private float shieldExpires = -1f;
    private float doubleStarExpires = -1f;

    // Score & Progression
    private int state = STATE_MENU;
    private int score = 0;
    private int displayScore = 0;
    private int sunCount = 0;
    private int highScore = 0;
    private int lastDistance = 0;
    private int consecutiveBounces = 0;
    private int consecutiveSuns = 0;
    private float lastSunPickupTime = 0f;
    private int selectedSkin = 0; // 0=Classic, 1=Mike, 2=Style1, 3=Style2
    private int selectedHair = 0; // 0=None, 1=Afro, 2=Parted, 3=Mohawk
    private int currentBoardStyle = 0; // 0..3

    // Touch & Gestures
    private boolean dragging = false;
    private float gestureStartX, gestureStartY;
    private float gestureEndX, gestureEndY;
    private boolean soundEnabled = true;
    private boolean musicEnabled = true;
    private boolean showTutorial = true;

    // Timing
    private long lastFrameNanos = 0;

    // Typography
    private Typeface gameFont;

    // Audio
    private SoundPool soundPool;
    private final HashMap<String, Integer> soundMap = new HashMap<>();
    private MediaPlayer bgmPlayer;

    // Trampoline Line Entity
    private static final class Trampoline {
        float ax, ay, bx, by;
        float life = 6.0f;
        int style;
        float sagFactor = 0f; // 0 to 1 for bounce rebound animation
        Trampoline(float x1, float y1, float x2, float y2, int style) {
            ax = x1; ay = y1; bx = x2; by = y2;
            this.style = style;
        }
    }

    // Collectible Sunlight Entity
    private static final class Sunlight {
        float x, y;
        int type; // 0=Normal Sun, 1=Rainbow, 2=Shield, 3=Coin, 4=Double Star
        boolean collected = false;
        Sunlight(float px, float py, int t) { x = px; y = py; type = t; }
    }

    // Hazard Entity (Birds, Fish, Bombs)
    private static final class Hazard {
        float x, y;
        int kind; // 0=Bird1, 1=Bird2, 2=Fish, 3=Bomb
        float phase;
        float startY;
        boolean defeated = false;
        Hazard(float px, float py, int k, float ph) {
            x = px; y = py; kind = k; phase = ph; startY = py;
        }
    }

    // Seasonal Floating Item (Flower, Leaf, Maple, Snowflake)
    private static final class SeasonalItem {
        float x, y;
        int season; // 0=Spring, 1=Summer, 2=Autumn, 3=Winter
        float drift;
        boolean collected = false;
        SeasonalItem(float px, float py, int s, float d) {
            x = px; y = py; season = s; drift = d;
        }
    }

    // Particle
    private static final class Particle {
        float x, y, vx, vy, life, maxLife;
        int color;
        float size;
        String spriteKey;
        float rotation, vRot;
        Particle(float x, float y, float vx, float vy, float l, int c, float s, String spriteKey, float vRot) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = l; this.maxLife = l; this.color = c; this.size = s;
            this.spriteKey = spriteKey;
            this.rotation = 0f; this.vRot = vRot;
        }
    }

    // Floating Score Popup
    private static final class ScorePopup {
        float x, y;
        String text;
        int color;
        float life = 1.0f;
        ScorePopup(float x, float y, String t, int c) {
            this.x = x; this.y = y; this.text = t; this.color = c;
        }
    }

    public GameView(Context context) {
        this(context, null);
    }

    public GameView(Context context, Vibrator vibrator) {
        super(context);
        this.vibrator = vibrator;
        setFocusable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);

        prefs = context.getSharedPreferences("bouncy_seed_family_data", Context.MODE_PRIVATE);
        highScore = prefs.getInt("high_score", 0);
        soundEnabled = prefs.getBoolean("sound_on", true);
        musicEnabled = prefs.getBoolean("music_on", true);
        showTutorial = prefs.getBoolean("show_tutorial", true);
        selectedSkin = prefs.getInt("selected_skin", 0);
        selectedHair = prefs.getInt("selected_hair", 0);

        loadTypeface(context);
        loadArtwork(context);
        loadSounds(context);
        ambientCars.add(new AmbientCar(80f, 0, 50f));
        ambientCars.add(new AmbientCar(480f, 1, 70f));
        ambientCars.add(new AmbientCar(880f, 2, 60f));

        ambientFish.add(new AmbientFish());
        ambientFish.add(new AmbientFish());

        ambientBirds.add(new AmbientBird(150f, 100f, 65f, 0));
        ambientBirds.add(new AmbientBird(650f, 160f, -55f, 1));


        if (musicEnabled) {
            startBgm();
        }
    }

    private AssetFileDescriptor openFd(Context c, String path) {
        try {
            return c.getAssets().openFd(path);
        } catch (Exception e1) {
            try {
                return c.getAssets().openFd(path.replace('/', '\\'));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private void loadTypeface(Context c) {
        try {
            gameFont = Typeface.createFromAsset(c.getAssets(), "original/font/kids.ttf");
        } catch (Exception e1) {
            try {
                gameFont = Typeface.createFromAsset(c.getAssets(), "original\\font\\kids.ttf");
            } catch (Exception e2) {
                try {
                    gameFont = Typeface.createFromAsset(c.getAssets(), "original/font/shark.ttf");
                } catch (Exception ignored) {
                    gameFont = Typeface.create("sans-serif-rounded", Typeface.BOLD);
                }
            }
        }
    }

    private InputStream openAsset(Context c, String path) {
        try {
            return c.getAssets().open(path);
        } catch (Exception e1) {
            try {
                return c.getAssets().open(path.replace('/', '\\'));
            } catch (Exception e2) {
                try {
                    return c.getAssets().open(path.replace('\\', '/'));
                } catch (Exception e3) {
                    return null;
                }
            }
        }
    }

    private void loadArtwork(Context c) {
        try {
            String[] rootList = c.getAssets().list("");
            android.util.Log.i("BouncySeed", "Assets root list: " + java.util.Arrays.toString(rootList));
            String[] modernList = c.getAssets().list("modern");
            android.util.Log.i("BouncySeed", "Assets modern list count: " + (modernList != null ? modernList.length : -1));
        } catch (Exception e) {
            android.util.Log.e("BouncySeed", "Error listing assets: " + e);
        }

        String[] keys = {
            "hero", "hero_bg", "hero_mike_bg", "hero_style1_bg", "hero_style2_bg", "hero_leaf",
            "hair1", "hair2", "hair3",
            "bga3", "bgb3", "bgc3", "bgd1", "bgd2",
            "bga_cloud", "bgb_cloud", "bgc_cloud", "bgd_cloud",
            "bga_river", "bgb_river", "bgc_river2", "bgd_river",
            "bga_land", "bgb_land", "bgc_land", "bgd_land",
            "board1_line", "board2_line", "board3_line", "board4_line",
            "board1_dot", "board2_dot", "board3_dot", "board4_dot",
            "board_dot", "board_dot_strong",
            "bird1", "bird2", "fish", "bomb", "bomb_effect",
            "sun", "sun0001", "sun0002", "sun0003", "sun0004", "sun0005", "sun0006", "sun0007", "sun0008", "sun0009",
            "star", "star2", "coin", "flower", "leaf", "maple", "snow", "ice", "tear", "shield", "cover", "circle",
            "title_logo", "title_splash", "bga2", "bgc4", "shop",
            "play", "index_play", "index_rank", "index_pack", "index_about", "index_mybo", "index_leaf", "index_signin", "index_signout",
            "footer", "hand", "drawhere1", "drawhere2", "drawhere3", "cannotdraw", "dont",
            "gameover_bg1", "gameover_bg2", "gameover_bg3", "gameover_bg4",
            "gameover_grass", "gameover_title", "gameover_sign", "gameover_retry", "gameover_menu", "gameover_rank", "gameover_pack",
            "pause", "resume", "retry", "menu", "sound", "top", "scosun", "oversun", "overcoin",
            "num1", "num2", "num3", "num4", "num5", "num6", "font1", "font3"
        };

        for (String key : keys) {
            Bitmap b = null;
            // 1. Try modern RGBA transparent composite first
            for (String ext : new String[]{".png", ".jpg"}) {
                try (InputStream in = openAsset(c, "modern/" + key + ext)) {
                    if (in != null) {
                        BitmapFactory.Options opt = new BitmapFactory.Options();
                        opt.inScaled = false;
                        b = BitmapFactory.decodeStream(in, null, opt);
                        if (b != null) break;
                    }
                } catch (Exception ignored) {}
            }
            // 2. Try original Retina directory
            if (b == null) {
                for (String ext : new String[]{".png", ".null", ".jpg"}) {
                    try (InputStream in = openAsset(c, "original/Retina/" + key + ext)) {
                        if (in != null) {
                            BitmapFactory.Options opt = new BitmapFactory.Options();
                            opt.inScaled = false;
                            b = BitmapFactory.decodeStream(in, null, opt);
                            if (b != null) break;
                        }
                    } catch (Exception ignored) {}
                }
            }
            if (b != null) {
                sprites.put(key, b);
            } else {
                android.util.Log.w("BouncySeed", "Failed to load sprite: " + key);
            }
        }
        android.util.Log.i("BouncySeed", "Successfully loaded sprites: " + sprites.size() + " / " + keys.length);
    }

    private void loadSounds(Context c) {
        try {
            soundPool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(
                new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build();

            String[] soundFiles = {
                "bounce.ogg", "jumppad.ogg",
                "spring_01.ogg", "spring_02.ogg", "spring_03.ogg", "spring_04.ogg", "spring_05.ogg", "spring_06.ogg",
                "spring_01pang.ogg", "spring_02pang.ogg", "spring_03pang.ogg", "spring_04pang.ogg", "spring_05pang.ogg", "spring_06pang.ogg",
                "sfx_sun_1.ogg", "sfx_sun_2.ogg", "sfx_sun_3.ogg", "sfx_sun_4.ogg",
                "coin.ogg", "wings.ogg", "fly.ogg", "mega_jump.ogg",
                "bird1.ogg", "bird2.ogg", "fish1.ogg", "fish2.ogg",
                "bomb.ogg", "splash.ogg", "hop.ogg", "death.ogg", "gameover.ogg",
                "score_tick.ogg", "score_finish.ogg", "icebreak.ogg"
            };

            for (String file : soundFiles) {
                try {
                    AssetFileDescriptor afd = openFd(c, "original/ogg/" + file);
                    if (afd != null) {
                        int soundId = soundPool.load(afd, 1);
                        soundMap.put(file, soundId);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void playSfx(String name, float volume) {
        if (!soundEnabled || soundPool == null) return;
        Integer id = soundMap.get(name);
        if (id != null && id > 0) {
            soundPool.play(id, volume, volume, 1, 0, 1.0f);
        }
    }

    private void startBgm() {
        if (!musicEnabled) return;
        try {
            if (bgmPlayer == null) {
                bgmPlayer = new MediaPlayer();
                AssetFileDescriptor afd = openFd(getContext(), "original/ogg/gamebg.ogg");
                if (afd != null) {
                    bgmPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                    bgmPlayer.setLooping(true);
                    bgmPlayer.setVolume(0.55f, 0.55f);
                    bgmPlayer.prepare();
                }
            }
            if (bgmPlayer != null) {
                bgmPlayer.start();
            }
        } catch (Exception ignored) {}
    }

    public void pauseBgm() {
        if (bgmPlayer != null && bgmPlayer.isPlaying()) {
            bgmPlayer.pause();
        }
    }

    public void resumeBgm() {
        if (musicEnabled && bgmPlayer != null && !bgmPlayer.isPlaying()) {
            bgmPlayer.start();
        }
    }

    public void stopBgm() {
        if (bgmPlayer != null) {
            try {
                bgmPlayer.stop();
                bgmPlayer.release();
            } catch (Exception ignored) {}
            bgmPlayer = null;
        }
    }

    private void triggerHaptic(int ms) {
        if (vibrator == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float approach(float current, float target, float delta) {
        if (current < target) return Math.min(target, current + delta);
        if (current > target) return Math.max(target, current - delta);
        return target;
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        return (float) Math.hypot(dx, dy);
    }

    private static float lineSegDist(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        if (l2 < 1e-4f) return dist(px, py, x1, y1);
        float t = clamp(((px - x1) * dx + (py - y1) * dy) / l2, 0f, 1f);
        return dist(px, py, x1 + t * dx, y1 + t * dy);
    }

    private int currentSeason() {
        return ((int) (cameraX / 5200f)) % 4;
    }

    private String seasonKey() {
        int s = currentSeason();
        return (s == 0) ? "bga" : (s == 1) ? "bgb" : (s == 2) ? "bgc" : "bgd";
    }

    // Authentic Bitmap Number Rendering (num1..num6)
    private void drawBitmapNumber(Canvas c, String fontKey, int number, float x, float y, Paint.Align align) {
        Bitmap bmp = sprites.get(fontKey);
        if (bmp == null) {
            // Fallback to text if sprite not loaded
            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(Color.WHITE);
            paint.setTextSize(32f);
            paint.setTypeface(gameFont);
            paint.setTextAlign(align);
            c.drawText(String.valueOf(number), x, y + 28f, paint);
            return;
        }

        if (number < 0) number = 0;
        String s = Integer.toString(number);
        float digitW = bmp.getWidth() / 10f;
        float digitH = bmp.getHeight();
        float totalW = s.length() * digitW;

        float curX = x;
        if (align == Paint.Align.CENTER) {
            curX = x - totalW * 0.5f;
        } else if (align == Paint.Align.RIGHT) {
            curX = x - totalW;
        }

        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        Rect src = new Rect();
        RectF dst = new RectF();

        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0';
            if (d < 0 || d > 9) d = 0;
            src.set((int) (d * digitW), 0, (int) ((d + 1) * digitW), (int) digitH);
            dst.set(curX, y, curX + digitW, y + digitH);
            c.drawBitmap(bmp, src, dst, paint);
            curX += digitW;
        }
    }

    private void blitFrame(Canvas c, String key, int frameIndex, int totalFrames, float x, float y, float w, float h, float alpha) {
        Bitmap b = sprites.get(key);
        if (b == null) return;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        if (alpha < 0.999f) {
            paint.setAlpha((int) (clamp(alpha, 0f, 1f) * 255f));
        }
        int frameH = b.getHeight() / Math.max(1, totalFrames);
        int top = frameIndex * frameH;
        Rect src = new Rect(0, top, b.getWidth(), top + frameH);
        RectF dst = new RectF(x, y, x + w, y + h);
        c.drawBitmap(b, src, dst, paint);
    }

    
    private void blitHorizontalFrame(Canvas c, String key, int frameIndex, int totalFrames, float x, float y, float w, float h, float alpha) {
        Bitmap b = sprites.get(key);
        if (b == null) return;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        if (alpha < 0.999f) {
            paint.setAlpha((int) (clamp(alpha, 0f, 1f) * 255f));
        }
        int frameW = b.getWidth() / Math.max(1, totalFrames);
        int left = frameIndex * frameW;
        Rect src = new Rect(left, 0, left + frameW, b.getHeight());
        RectF dst = new RectF(x, y, x + w, y + h);
        c.drawBitmap(b, src, dst, paint);
    }

    private void drawCar(Canvas c, int carIndex, float x, float y) {
        Bitmap bga2 = sprites.get("bga2");
        if (bga2 == null) return;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        Rect src;
        float w, h = 42f;
        if (carIndex == 0) {
            src = new Rect(0, 0, 225, 85);
            w = 110f;
        } else if (carIndex == 1) {
            src = new Rect(295, 0, 445, 85);
            w = 75f;
        } else {
            src = new Rect(450, 0, 550, 85);
            w = 50f;
        }
        RectF dst = new RectF(x, y - h, x + w, y);
        c.drawBitmap(bga2, src, dst, paint);
    }

    private void drawJumpingFish(Canvas c, AmbientFish f) {
        if (!f.active) return;
        float progress = f.timer / f.duration;
        float jumpHeight = 65f * (float) Math.sin(progress * Math.PI);
        float curY = WATER_LEVEL - jumpHeight;
        float curX = f.x - cameraX;

        c.save();
        c.translate(curX, curY);
        float angle = (progress - 0.5f) * 80f;
        c.rotate(angle);
        blit(c, "fish", -20f, -20f, 40f, 40f, 1.0f);
        c.restore();
    }

    private void blit(Canvas c, String key, float x, float y, float w, float h, float alpha) {
        Bitmap b = sprites.get(key);
        if (b == null) return;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        if (alpha < 0.999f) {
            paint.setAlpha((int) (clamp(alpha, 0f, 1f) * 255f));
        }
        Rect src = new Rect(0, 0, b.getWidth(), b.getHeight());
        RectF dst = new RectF(x, y, x + w, y + h);
        c.drawBitmap(b, src, dst, paint);
    }

    private void blitTile(Canvas c, String key, float x, float y, float w, float h) {
        Bitmap b = sprites.get(key);
        if (b == null) return;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        float curX = x;
        float endX = x + w;
        float bW = b.getWidth();
        float bH = b.getHeight();
        while (curX < endX) {
            float drawW = Math.min(bW, endX - curX);
            Rect src = new Rect(0, 0, (int) drawW, (int) bH);
            RectF dst = new RectF(curX, y, curX + drawW, y + h);
            c.drawBitmap(b, src, dst, paint);
            curX += drawW;
        }
    }

    private void spawnEntities() {
        // Collectibles (Sun, Coins, Rainbow, Shield, Double Star)
        while (farthestPickup < cameraX + W + 650f) {
            farthestPickup += 140f + random.nextFloat() * 110f;
            float py = 120f + random.nextFloat() * 320f;
            float roll = random.nextFloat();
            int type = 0; // standard sun
            if (roll < 0.05f) type = 1;      // Rainbow Rush
            else if (roll < 0.09f) type = 2; // Shield
            else if (roll < 0.22f) type = 3; // Gold Coin (+75 pts)
            else if (roll < 0.27f) type = 4; // Double Star
            sunlights.add(new Sunlight(farthestPickup, py, type));
        }

        // Hazards (Birds, Fish, Bombs)
        while (farthestHazard < cameraX + W + 700f) {
            farthestHazard += 350f + random.nextFloat() * 320f;
            float roll = random.nextFloat();
            if (roll < 0.35f) {
                // Bird 1
                hazards.add(new Hazard(farthestHazard, 90f + random.nextFloat() * 180f, 0, random.nextFloat() * 6.28f));
            } else if (roll < 0.65f) {
                // Bird 2
                hazards.add(new Hazard(farthestHazard, 140f + random.nextFloat() * 200f, 1, random.nextFloat() * 6.28f));
            } else if (roll < 0.82f) {
                // Fish in river
                hazards.add(new Hazard(farthestHazard, WATER_LEVEL - 20f, 2, random.nextFloat() * 6.28f));
            } else {
                // Bomb mine
                hazards.add(new Hazard(farthestHazard, 160f + random.nextFloat() * 260f, 3, 0f));
            }
        }

        // Seasonal floating decor items
        while (farthestDecor < cameraX + W + 500f) {
            farthestDecor += 180f + random.nextFloat() * 150f;
            seasonItems.add(new SeasonalItem(
                farthestDecor, 80f + random.nextFloat() * 360f,
                currentSeason(), (random.nextFloat() - 0.5f) * 45f
            ));
        }
    }

    private void step(float dt) {
        clock += dt;
        bounceSquashTimer = Math.max(0f, bounceSquashTimer - dt * 3.5f);

        // Ambient seasonal particles (leaves drifting in autumn, snow in winter, flower petals in spring)
        if (random.nextFloat() < 0.25f) {
            int s = currentSeason();
            String key = (s == 0) ? "flower" : (s == 1) ? "leaf" : (s == 2) ? "maple" : "snow";
            particles.add(new Particle(
                cameraX + random.nextFloat() * W, -20f,
                (random.nextFloat() - 0.4f) * 50f, 40f + random.nextFloat() * 60f,
                6.0f, 0xffffffff, 24f, key, (random.nextFloat() - 0.5f) * 120f
            ));
        }

        
        // Update ambient road cars
        for (AmbientCar car : ambientCars) {
            car.x += car.speed * dt;
            if (car.x > W + 200f) {
                car.x = -150f;
            }
        }

        // Update ambient jumping clownfish
        for (AmbientFish f : ambientFish) {
            if (!f.active) {
                f.nextJump -= dt;
                if (f.nextJump <= 0f) {
                    f.active = true;
                    f.timer = 0f;
                    f.duration = 1.2f + random.nextFloat() * 0.4f;
                    f.x = cameraX + 80f + random.nextFloat() * (W - 160f);
                    f.nextJump = 3f + random.nextFloat() * 4f;
                }
            } else {
                f.timer += dt;
                if (f.timer >= f.duration) {
                    f.active = false;
                }
            }
        }

        // Update ambient sky birds
        for (AmbientBird b : ambientBirds) {
            b.x += b.vx * dt;
            b.flapTimer += dt;
            if (b.vx > 0 && b.x > W + 150f) {
                b.x = -120f;
                b.y = 70f + random.nextFloat() * 120f;
            } else if (b.vx < 0 && b.x < -150f) {
                b.x = W + 120f;
                b.y = 70f + random.nextFloat() * 120f;
            }
        }

        if (state != STATE_PLAY) {
            updateParticles(dt);
            return;
        }

        introTime += dt;
        oldSeedX = seedX;
        oldSeedY = seedY;

        // Gravity
        seedVy += GRAVITY * dt;

        // Airborne air drag & forward drive
        float targetVx = 250f + Math.min(220f, score * 0.05f);
        if (rainbowExpires > clock) targetVx = 560f;
        seedVx = approach(seedVx, targetVx, 120f * dt);

        // Position integrate
        seedX += seedVx * dt;
        seedY += seedVy * dt;

        // Rotation follows velocity vector smoothly
        float targetRot = (float) Math.toDegrees(Math.atan2(seedVy, seedVx)) * 0.5f;
        seedRotation = approach(seedRotation, targetRot, 300f * dt);

        // Camera smoothly tracks seed forward
        float targetCamX = seedX - W * 0.32f;
        if (targetCamX > cameraX) {
            cameraX += (targetCamX - cameraX) * Math.min(1f, dt * 7.5f);
        }

        // Distance score
        int distTraveled = (int) (seedX / 12f);
        if (distTraveled > lastDistance) {
            int gain = distTraveled - lastDistance;
            if (rainbowExpires > clock) gain *= 3;
            else if (doubleStarExpires > clock) gain *= 2;
            score += gain;
            lastDistance = distTraveled;
        }

        spawnEntities();
        updateTrampolines(dt);
        updateHazards(dt);
        updateParticles(dt);
        updateScorePopups(dt);
        checkCollisions();

        // Water impact death
        if (seedY + SEED_RADIUS >= WATER_LEVEL) {
            triggerWaterSplash(seedX, WATER_LEVEL);
            gameOver();
        }
    }

    private void updateTrampolines(float dt) {
        Iterator<Trampoline> it = trampolines.iterator();
        while (it.hasNext()) {
            Trampoline t = it.next();
            t.life -= dt;
            t.sagFactor = approach(t.sagFactor, 0f, dt * 4.5f);
            if (t.life <= 0f) it.remove();
        }
    }

    private void updateHazards(float dt) {
        for (Hazard h : hazards) {
            h.phase += dt * 3.2f;
            if (h.kind == 0 || h.kind == 1) {
                // Flying bird oscillation
                h.x -= (h.kind == 0 ? 55f : 85f) * dt;
                h.y = h.startY + (float) Math.sin(h.phase) * 22f;
            } else if (h.kind == 2) {
                // Jumping fish arc
                float fishCycle = (clock * 1.8f + h.phase) % 4.0f;
                if (fishCycle < 1.4f) {
                    float t = fishCycle / 1.4f;
                    h.y = WATER_LEVEL - (float) Math.sin(t * Math.PI) * 140f;
                } else {
                    h.y = WATER_LEVEL + 50f;
                }
            }
        }
    }

    private void updateParticles(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.rotation += p.vRot * dt;
            if (p.life <= 0f) it.remove();
        }
    }

    private void updateScorePopups(float dt) {
        Iterator<ScorePopup> it = scorePopups.iterator();
        while (it.hasNext()) {
            ScorePopup sp = it.next();
            sp.life -= dt * 1.2f;
            sp.y -= 45f * dt;
            if (sp.life <= 0f) it.remove();
        }
    }

    private void checkCollisions() {
        // 1. Trampoline collisions
        for (Trampoline t : trampolines) {
            if (t.life <= 0.1f) continue;
            // Check collision with line segment
            float dist = lineSegDist(seedX, seedY, t.ax, t.ay, t.bx, t.by);
            if (dist < SEED_RADIUS + 10f && seedVy > -30f) {
                // Compute line normal
                float dx = t.bx - t.ax;
                float dy = t.by - t.ay;
                float len = (float) Math.hypot(dx, dy);
                if (len < 1f) continue;

                // Normal pointing upwards
                float nx = -dy / len;
                float ny = dx / len;
                if (ny > 0) { nx = -nx; ny = -ny; }

                // Elastic impulse with combo escalation
                consecutiveBounces++;
                int bounceIndex = Math.min(6, consecutiveBounces);
                String soundFile = "spring_0" + bounceIndex + ".ogg";
                playSfx(soundFile, 0.85f);
                if (bounceIndex >= 3) {
                    playSfx("spring_0" + bounceIndex + "pang.ogg", 0.6f);
                }

                // Bounce impulse
                float bouncePower = 580f + Math.min(220f, consecutiveBounces * 20f);
                if (rainbowExpires > clock) bouncePower = 780f;

                seedVx = Math.max(180f, seedVx * 0.9f + nx * 140f);
                seedVy = ny * bouncePower;
                seedY = Math.min(seedY, Math.min(t.ay, t.by) - SEED_RADIUS);

                t.sagFactor = 1.0f;
                bounceSquashTimer = 1.0f;
                triggerHaptic(25);

                // Authentic LineEffect: emit seasonal particles along the line
                emitLineFlakes(t);

                // Authentic bounceParticle: radial burst of stars
                emitBounceStars(seedX, seedY);

                // Score for successful bounce
                int pts = 50 * consecutiveBounces;
                score += pts;
                scorePopups.add(new ScorePopup(seedX - cameraX, seedY - 40f, "+" + pts, 0xffffdd44));
                break;
            }
        }

        // 2. Sunlight pickups
        for (Sunlight s : sunlights) {
            if (s.collected) continue;
            if (dist(seedX, seedY, s.x, s.y) < SEED_RADIUS + 26f) {
                s.collected = true;
                sunCount++;
                consecutiveSuns++;
                lastSunPickupTime = clock;

                // Sound escalation sfx_sun_1..4
                int sfxIdx = 1 + (consecutiveSuns % 4);
                playSfx("sfx_sun_" + sfxIdx + ".ogg", 0.75f);
                triggerHaptic(15);

                if (s.type == 0) {
                    // Normal Sun
                    int pts = 100 * (rainbowExpires > clock ? 3 : 1);
                    score += pts;
                    scorePopups.add(new ScorePopup(s.x - cameraX, s.y - 20f, "+" + pts, 0xffffef66));
                } else if (s.type == 1) {
                    // Rainbow Rush
                    rainbowExpires = clock + 6.0f;
                    playSfx("wings.ogg", 0.95f);
                    playSfx("fly.ogg", 0.9f);
                    scorePopups.add(new ScorePopup(s.x - cameraX, s.y - 25f, "RAINBOW RUSH!", 0xffff44aa));
                } else if (s.type == 2) {
                    // Shield Bubble
                    shieldExpires = clock + 12.0f;
                    playSfx("jumppad.ogg", 0.85f);
                    scorePopups.add(new ScorePopup(s.x - cameraX, s.y - 25f, "SHIELD!", 0xff44ddff));
                } else if (s.type == 3) {
                    // Gold Coin
                    score += 75;
                    playSfx("coin.ogg", 0.85f);
                    scorePopups.add(new ScorePopup(s.x - cameraX, s.y - 20f, "+75 COIN", 0xffffcc00));
                } else if (s.type == 4) {
                    // Double Star Multiplier
                    doubleStarExpires = clock + 8.0f;
                    playSfx("mega_jump.ogg", 0.85f);
                    scorePopups.add(new ScorePopup(s.x - cameraX, s.y - 25f, "2X STARS!", 0xffff9933));
                }

                // Sparkle burst
                emitPickupSparkles(s.x, s.y, (s.type == 1) ? 0xffff44aa : 0xffffe456);
            }
        }

        // 3. Hazards (Birds, Fish, Bombs)
        for (Hazard h : hazards) {
            if (h.defeated) continue;
            float hitRadius = (h.kind == 3) ? 36f : 42f;
            if (dist(seedX, seedY, h.x, h.y) < SEED_RADIUS + hitRadius) {
                // If Rainbow or Shield active, smash hazard safely
                if (rainbowExpires > clock) {
                    h.defeated = true;
                    playSfx("hop.ogg", 0.8f);
                    emitBounceStars(h.x, h.y);
                    score += 250;
                    scorePopups.add(new ScorePopup(h.x - cameraX, h.y, "+250", 0xffff55aa));
                    continue;
                } else if (shieldExpires > clock) {
                    shieldExpires = -1f; // Shield absorbed
                    h.defeated = true;
                    playSfx("icebreak.ogg", 0.9f);
                    emitBounceStars(h.x, h.y);
                    scorePopups.add(new ScorePopup(seedX - cameraX, seedY, "SHIELD BROKEN", 0xff66ccff));
                    continue;
                }

                // If seed hits bird from above, hop bounce on bird!
                if ((h.kind == 0 || h.kind == 1) && seedVy > 40f && seedY < h.y) {
                    h.defeated = true;
                    seedVy = -540f;
                    playSfx("hop.ogg", 0.9f);
                    emitBounceStars(h.x, h.y);
                    score += 150;
                    scorePopups.add(new ScorePopup(h.x - cameraX, h.y, "+150 HOP!", 0xffaaff44));
                    continue;
                }

                // Lethal collision
                if (h.kind == 3) {
                    playSfx("bomb.ogg", 1.0f);
                    triggerBombExplosion(h.x, h.y);
                } else {
                    playSfx("death.ogg", 0.9f);
                }
                gameOver();
                return;
            }
        }

        // 4. Seasonal collectible floating items
        for (SeasonalItem item : seasonItems) {
            if (item.collected) continue;
            if (dist(seedX, seedY, item.x, item.y) < SEED_RADIUS + 24f) {
                item.collected = true;
                score += 50;
                playSfx("sfx_sun_1.ogg", 0.6f);
                scorePopups.add(new ScorePopup(item.x - cameraX, item.y, "+50", 0xff88ff88));
                emitPickupSparkles(item.x, item.y, 0xffaaff66);
            }
        }
    }

    // Authentic LineEffect Flakes
    private void emitLineFlakes(Trampoline t) {
        int s = currentSeason();
        String flakeKey = (s == 0) ? "flower" : (s == 1) ? "leaf" : (s == 2) ? "maple" : "snow";
        for (int i = 0; i < 10; i++) {
            float frac = (i + 1) / 11f;
            float px = t.ax + (t.bx - t.ax) * frac;
            float py = t.ay + (t.by - t.ay) * frac;
            float vx = (random.nextFloat() - 0.5f) * 80f;
            float vy = -40f - random.nextFloat() * 60f;
            particles.add(new Particle(px, py, vx, vy, 1.2f, 0xffffffff, 28f, flakeKey, (random.nextFloat() - 0.5f) * 180f));
        }
    }

    // Authentic bounceParticle Star Burst
    private void emitBounceStars(float x, float y) {
        for (int i = 0; i < 14; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            float spd = 120f + random.nextFloat() * 220f;
            float vx = (float) Math.cos(angle) * spd;
            float vy = (float) Math.sin(angle) * spd - 60f;
            String key = (random.nextBoolean()) ? "star" : "star2";
            particles.add(new Particle(x, y, vx, vy, 0.75f, 0xffffee44, 30f, key, (random.nextFloat() - 0.5f) * 240f));
        }
    }

    private void emitPickupSparkles(float x, float y, int color) {
        for (int i = 0; i < 8; i++) {
            float vx = (random.nextFloat() - 0.5f) * 160f;
            float vy = (random.nextFloat() - 0.5f) * 160f;
            particles.add(new Particle(x, y, vx, vy, 0.6f, color, 22f, "star", (random.nextFloat() - 0.5f) * 200f));
        }
    }

    private void triggerWaterSplash(float x, float y) {
        playSfx("splash.ogg", 0.95f);
        triggerHaptic(40);
        int s = currentSeason();
        String dropKey = (s == 3) ? "ice" : "circle";
        for (int i = 0; i < 16; i++) {
            float vx = (random.nextFloat() - 0.5f) * 180f;
            float vy = -120f - random.nextFloat() * 160f;
            particles.add(new Particle(x, y, vx, vy, 0.8f, 0xff88ddff, 20f, dropKey, 0f));
        }
    }

    private void triggerBombExplosion(float x, float y) {
        triggerHaptic(70);
        particles.add(new Particle(x, y, 0f, 0f, 0.6f, 0xffffffff, 120f, "bomb_effect", 0f));
        for (int i = 0; i < 20; i++) {
            double angle = random.nextDouble() * 2.0 * Math.PI;
            float spd = 150f + random.nextFloat() * 260f;
            particles.add(new Particle(
                x, y, (float) Math.cos(angle) * spd, (float) Math.sin(angle) * spd,
                0.9f, 0xffff6622, 26f, "star2", 0f
            ));
        }
    }

    private void gameOver() {
        state = STATE_GAME_OVER;
        consecutiveBounces = 0;
        playSfx("gameover.ogg", 0.9f);
        if (score > highScore) {
            highScore = score;
            prefs.edit().putInt("high_score", highScore).apply();
        }
        displayScore = 0;
    }

    private void resetGame() {
        state = STATE_PLAY;
        score = 0;
        displayScore = 0;
        sunCount = 0;
        clock = 0f;
        introTime = 0f;
        cameraX = 0f;
        seedX = 170f; seedY = 220f;
        seedVx = 240f; seedVy = -90f;
        oldSeedX = seedX; oldSeedY = seedY;
        seedRotation = 0f;
        consecutiveBounces = 0;
        consecutiveSuns = 0;
        rainbowExpires = -1f;
        shieldExpires = -1f;
        doubleStarExpires = -1f;
        farthestPickup = 200f;
        farthestHazard = 550f;
        farthestDecor = 220f;
        trampolines.clear();
        sunlights.clear();
        hazards.clear();
        seasonItems.clear();
        particles.clear();
        scorePopups.clear();
        spawnEntities();
    }

    // Sky & Parallax Landscape
    private void drawSky(Canvas c) {
        int s = currentSeason();
        int skyTop, skyBot;
        if (s == 0) { // Spring
            skyTop = 0xff54c2f8; skyBot = 0xffe6f6ff;
        } else if (s == 1) { // Summer
            skyTop = 0xff2fb6f0; skyBot = 0xfff6f3cf;
        } else if (s == 2) { // Autumn
            skyTop = 0xfff89648; skyBot = 0xfffff3d6;
        } else { // Winter
            skyTop = 0xff6688aa; skyBot = 0xffdce8f4;
        }

        paint.reset();
        paint.setShader(new LinearGradient(0, 0, 0, VIRTUAL_HEIGHT, skyTop, skyBot, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, VIRTUAL_HEIGHT, paint);
        paint.setShader(null);
    }

    private void drawScenery(Canvas c) {
        String key = seasonKey();

        // Parallax Clouds (continuous smooth drifting)
        float cloudScroll = (clock * 16f + cameraX * 0.18f) % 768f;
        blit(c, key + "_cloud", -cloudScroll, 35f, 768f, 176f, 0.85f);
        blit(c, key + "_cloud", -cloudScroll + 768f, 35f, 768f, 176f, 0.85f);
        if (W > 768f) {
            blit(c, key + "_cloud", -cloudScroll + 1536f, 35f, 768f, 176f, 0.85f);
        }

        // Parallax Mountains / Hills (bga3, bgb3, bgc3, bgd1)
        String hillKey = (key.equals("bgd")) ? "bgd1" : (key + "3");
        Bitmap hillBmp = sprites.get(hillKey);
        if (hillBmp != null) {
            float hillW = 1000f;
            float hillH = 248f;
            int totalFrames = 3;
            if (hillKey.equals("bgb3")) { hillH = 155f; totalFrames = 4; }
            else if (hillKey.equals("bgc3")) { hillH = 239f; totalFrames = 4; }
            else if (hillKey.equals("bgd1")) { hillH = 156f; totalFrames = 4; }

            float hillY = WATER_LEVEL - hillH + 30f;
            float hillScroll = (cameraX * 0.45f) % hillW;
            blitFrame(c, hillKey, 0, totalFrames, -hillScroll, hillY, hillW, hillH, 1.0f);
            blitFrame(c, hillKey, 0, totalFrames, -hillScroll + hillW, hillY, hillW, hillH, 1.0f);
            if (W > hillW) {
                blitFrame(c, hillKey, 0, totalFrames, -hillScroll + hillW * 2f, hillY, hillW, hillH, 1.0f);
            }
        }

        // Animated Road Traffic (bga2 cars driving along road)
        for (AmbientCar car : ambientCars) {
            drawCar(c, car.type, car.x, WATER_LEVEL - 20f);
        }

        // River Surface Waves (continuous smooth rippling)
        String riverKey = (key.equals("bgc")) ? "bgc_river2" : (key + "_river");
        float riverScroll = (clock * 38f + cameraX * 0.85f) % 480f;
        for (float rx = -riverScroll; rx < W + 480f; rx += 480f) {
            blit(c, riverKey, rx, WATER_LEVEL - 20f, 480f, 54f, 1.0f);
        }

        // Jumping Clownfish leaping out of river
        for (AmbientFish f : ambientFish) {
            drawJumpingFish(c, f);
        }

        // Ambient Sky Birds flapping across the sky
        for (AmbientBird b : ambientBirds) {
            int totalFrames = (b.type == 0) ? 7 : 8;
            int frame = ((int) (b.flapTimer * 12f)) % totalFrames;
            float bw = (b.type == 0) ? 76f : 84f;
            float bh = (b.type == 0) ? 58f : 64f;
            blitHorizontalFrame(c, (b.type == 0) ? "bird1" : "bird2", frame, totalFrames, b.x, b.y + (float) Math.sin(b.x * 0.02f) * 8f, bw, bh, 0.95f);
        }

        // Deep water fill
        paint.reset();
        paint.setColor(0xee16628b);
        c.drawRect(0, WATER_LEVEL + 24f, W, VIRTUAL_HEIGHT, paint);
    }

    private void drawEntities(Canvas c) {
        // 1. Trampoline Lines (using authentic board1_line..4 and board_dot)
        for (Trampoline t : trampolines) {
            float x1 = t.ax - cameraX;
            float y1 = t.ay;
            float x2 = t.bx - cameraX;
            float y2 = t.by;
            if (Math.max(x1, x2) < -100f || Math.min(x1, x2) > W + 100f) continue;

            float alpha = clamp(t.life, 0f, 1f);
            float dx = x2 - x1, dy = y2 - y1;
            float len = (float) Math.hypot(dx, dy);
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

            c.save();
            c.translate(x1, y1);
            c.rotate(angle);

            // Trampoline line textured bar
            String lineKey = "board" + (t.style + 1) + "_line";
            blit(c, lineKey, 0, -18f + t.sagFactor * 8f, len, 36f, alpha);

            c.restore();

            // End knobs (board_dot.png)
            blit(c, "board_dot", x1 - 18f, y1 - 18f, 36f, 36f, alpha);
            blit(c, "board_dot", x2 - 18f, y2 - 18f, 36f, 36f, alpha);
        }

        // 2. Collectibles (Sun, Coins, Rainbow, Shield, Double Star)
        int sunFrame = ((int) (clock * 14f)) % 9 + 1;
        String sunKey = "sun000" + sunFrame;
        for (Sunlight s : sunlights) {
            if (s.collected) continue;
            float sx = s.x - cameraX;
            if (sx < -80f || sx > W + 80f) continue;

            if (s.type == 0) {
                // Spinning Animated Sun
                blit(c, sunKey, sx - 32f, s.y - 32f, 64f, 64f, 1.0f);
            } else if (s.type == 1) {
                // Rainbow Power-up
                blit(c, sunKey, sx - 36f, s.y - 36f, 72f, 72f, 1.0f);
                blit(c, "star2", sx - 24f, s.y - 24f, 48f, 48f, 0.9f);
            } else if (s.type == 2) {
                // Shield Pickup
                blit(c, "shield", sx - 34f, s.y - 34f, 68f, 68f, 0.95f);
            } else if (s.type == 3) {
                // Gold Coin
                blit(c, "coin", sx - 26f, s.y - 26f, 52f, 52f, 1.0f);
            } else if (s.type == 4) {
                // Double Star
                blit(c, "star", sx - 30f, s.y - 30f, 60f, 60f, 1.0f);
            }
        }

        // 3. Hazards (Birds, Fish, Bombs)
        for (Hazard h : hazards) {
            if (h.defeated) continue;
            float hx = h.x - cameraX;
            if (hx < -100f || hx > W + 100f) continue;

            if (h.kind == 0) {
                // Bird 1
                int bFrame = ((int) (clock * 14f)) % 7;
                blitHorizontalFrame(c, "bird1", bFrame, 7, hx - 38f, h.y - 28f, 76f, 56f, 1.0f);
            } else if (h.kind == 1) {
                // Bird 2
                int bFrame = ((int) (clock * 14f)) % 8;
                blitHorizontalFrame(c, "bird2", bFrame, 8, hx - 42f, h.y - 32f, 84f, 64f, 1.0f);
            } else if (h.kind == 2) {
                // Leaping Fish
                c.save();
                c.translate(hx, h.y);
                float fishAngle = (float) Math.sin(h.phase) * 35f;
                c.rotate(fishAngle);
                blit(c, "fish", -32f, -24f, 64f, 48f, 1.0f);
                c.restore();
            } else if (h.kind == 3) {
                // Floating Bomb
                blit(c, "bomb", hx - 32f, h.y - 32f, 64f, 64f, 1.0f);
            }
        }

        // 4. Seasonal Collectible Decor
        for (SeasonalItem item : seasonItems) {
            if (item.collected) continue;
            float ix = item.x - cameraX;
            if (ix < -60f || ix > W + 60f) continue;
            String key = (item.season == 0) ? "flower" : (item.season == 1) ? "leaf" : (item.season == 2) ? "maple" : "snow";
            blit(c, key, ix - 22f, item.y - 22f, 44f, 44f, 0.95f);
        }

        // 5. Particles
        for (Particle p : particles) {
            float px = p.x - cameraX;
            if (px < -60f || px > W + 60f) continue;
            float alpha = clamp(p.life / p.maxLife, 0f, 1f);

            c.save();
            c.translate(px, p.y);
            if (p.rotation != 0f) c.rotate(p.rotation);

            if (p.spriteKey != null && sprites.containsKey(p.spriteKey)) {
                blit(c, p.spriteKey, -p.size * 0.5f, -p.size * 0.5f, p.size, p.size, alpha);
            } else {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setColor(p.color);
                paint.setAlpha((int) (alpha * 255f));
                c.drawCircle(0, 0, p.size * 0.5f, paint);
            }
            c.restore();
        }

        // 6. Floating Score Popups
        for (ScorePopup sp : scorePopups) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setTypeface(gameFont);
            paint.setTextSize(26f);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(sp.color);
            paint.setAlpha((int) (clamp(sp.life, 0f, 1f) * 255f));
            c.drawText(sp.text, sp.x, sp.y, paint);
        }
    }

    // Authentic Rainbow Trail behind Seed
    private void drawRainbowTrail(Canvas c, float sx, float sy) {
        if (rainbowExpires <= clock) return;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);

        for (int i = 0; i < RAINBOW_COLORS.length; i++) {
            paint.setColor(RAINBOW_COLORS[i]);
            paint.setStrokeWidth(5f);
            float offY = (i - 2.5f) * 4f;

            Path path = new Path();
            path.moveTo(sx - 150f, sy + offY + (float) Math.sin(clock * 12f) * 8f);
            path.quadTo(sx - 75f, sy + offY - 6f, sx, sy + offY);
            c.drawPath(path, paint);
        }

        // Sparkling rainbow stars
        if (random.nextFloat() < 0.6f) {
            particles.add(new Particle(
                seedX - 30f + (random.nextFloat() - 0.5f) * 20f,
                seedY + (random.nextFloat() - 0.5f) * 24f,
                -120f - random.nextFloat() * 80f, (random.nextFloat() - 0.5f) * 60f,
                0.5f, 0xffffffff, 26f, "star", (random.nextFloat() - 0.5f) * 240f
            ));
        }
    }

    // Authentic 3-layer Seed Character Rendering
    private void drawSeed(Canvas c, float cx, float cy, float diameter) {
        c.save();
        c.translate(cx, cy);

        // Squash & stretch on bounce
        float scaleX = 1.0f + bounceSquashTimer * 0.25f;
        float scaleY = 1.0f - bounceSquashTimer * 0.25f;
        c.scale(scaleX, scaleY);

        if (state == STATE_PLAY) {
            c.rotate(seedRotation);
        }

        float r = diameter * 0.5f;

        // 1. Skin Body (hero_bg, hero_mike_bg, hero_style1_bg, hero_style2_bg)
        String skinKey = (selectedSkin == 1) ? "hero_mike_bg" :
                         (selectedSkin == 2) ? "hero_style1_bg" :
                         (selectedSkin == 3) ? "hero_style2_bg" : "hero_bg";
        blit(c, skinKey, -r, -r, diameter, diameter, 1.0f);

        // 2. Facial Expressions (from 63-frame hero atlas)
        Bitmap heroAtlas = sprites.get("hero");
        if (heroAtlas != null) {
            int frame = 14; // Default happy joy
            if (bounceSquashTimer > 0.3f) {
                frame = 2; // Squash impact
            } else if (seedVy < -250f) {
                frame = 16; // Excited ascent
            } else if (seedVy > 300f) {
                frame = 48; // Screaming panic drop
            } else if (rainbowExpires > clock) {
                frame = 18; // Ecstatic flight
            } else if (state == STATE_GAME_OVER) {
                frame = 60; // Knocked out dizzy
            }

            int col = frame % 9;
            int row = frame / 9;
            float frameW = heroAtlas.getWidth() / 9f;
            float frameH = heroAtlas.getHeight() / 7f;

            Rect src = new Rect((int) (col * frameW), (int) (row * frameH), (int) ((col + 1) * frameW), (int) ((row + 1) * frameH));
            RectF dst = new RectF(-r, -r, r, r);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            c.drawBitmap(heroAtlas, src, dst, paint);
        }

        // 3. Sprout / Leaf Propeller (hero_leaf)
        Bitmap leafAtlas = sprites.get("hero_leaf");
        if (leafAtlas != null) {
            // Row 0 = fluttering glide, Row 1 = fast spinning propeller
            int leafRow = (rainbowExpires > clock || seedVy < -250f) ? 1 : 0;
            int leafCol = ((int) (clock * 24f)) % 12;
            float lFrameW = leafAtlas.getWidth() / 12f;
            float lFrameH = leafAtlas.getHeight() / 3f;

            Rect lSrc = new Rect((int) (leafCol * lFrameW), (int) (leafRow * lFrameH), (int) ((leafCol + 1) * lFrameW), (int) ((leafRow + 1) * lFrameH));
            RectF lDst = new RectF(-r * 0.85f, -r * 1.55f, r * 0.85f, -r * 0.15f);
            c.drawBitmap(leafAtlas, lSrc, lDst, paint);
        }

        // 4. Hairstyle / Hat overlay (hair1=Afro, hair2=Parted, hair3=Mohawk)
        if (selectedHair > 0) {
            String hairKey = "hair" + selectedHair;
            blit(c, hairKey, -r * 1.1f, -r * 1.45f, diameter * 1.1f, diameter * 0.95f, 1.0f);
        }

        // 5. Protective Shield Bubble
        if (shieldExpires > clock) {
            float pulse = 1.0f + (float) Math.sin(clock * 6f) * 0.08f;
            c.scale(pulse, pulse);
            blit(c, "shield", -r * 1.4f, -r * 1.4f, diameter * 1.4f, diameter * 1.4f, 0.85f);
        }

        c.restore();
    }

    // Dragging preview when finger drawing a trampoline
    private void drawDragPreview(Canvas c) {
        if (!dragging) return;
        float x1 = gestureStartX - cameraX;
        float y1 = gestureStartY;
        float x2 = gestureEndX - cameraX;
        float y2 = gestureEndY;

        float len = dist(x1, y1, x2, y2);
        if (len < 32f) {
            // Below minLineLength: show cannotdraw indicator
            blit(c, "cannotdraw", (x1 + x2) * 0.5f - 110f, (y1 + y2) * 0.5f - 40f, 220f, 32f, 0.85f);
            return;
        }

        float dx = x2 - x1, dy = y2 - y1;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        c.save();
        c.translate(x1, y1);
        c.rotate(angle);
        String lineKey = "board" + (currentBoardStyle + 1) + "_line";
        blit(c, lineKey, 0, -18f, len, 36f, 0.85f);
        c.restore();

        blit(c, "board_dot", x1 - 18f, y1 - 18f, 36f, 36f, 0.85f);
        blit(c, "board_dot", x2 - 18f, y2 - 18f, 36f, 36f, 0.85f);
    }

    // In-Game HUD using Authentic Bitmap Numbers
    private void drawHud(Canvas c) {
        // Sunlight Count: scosun.png + num2
        blit(c, "scosun", 24f, 18f, 48f, 48f, 1.0f);
        drawBitmapNumber(c, "num2", sunCount, 82f, 20f, Paint.Align.LEFT);

        // Distance / Score: num1
        drawBitmapNumber(c, "num1", score, W - 110f, 18f, Paint.Align.RIGHT);

        // Pause Button: pause.png
        blit(c, "pause", W - 80f, 16f, 58f, 50f, 1.0f);

        // Rainbow active banner
        if (rainbowExpires > clock) {
            float bannerX = W * 0.5f;
            paint.reset();
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(gameFont);
            paint.setTextSize(26f);
            paint.setColor(0xffff3377);
            c.drawText("RAINBOW RUSH 3X!", bannerX, 42f, paint);
        } else if (shieldExpires > clock) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(gameFont);
            paint.setTextSize(24f);
            paint.setColor(0xff22bbff);
            c.drawText("SHIELD ACTIVE", W * 0.5f, 42f, paint);
        }

        // Beginner hint
        if (showTutorial && introTime < 6.5f && state == STATE_PLAY) {
            float bounceY = (float) Math.sin(clock * 5f) * 8f;
            blit(c, "drawhere1", W * 0.5f - 130f, VIRTUAL_HEIGHT - 210f + bounceY, 260f, 68f, 0.95f);
            blit(c, "hand", W * 0.5f - 35f, VIRTUAL_HEIGHT - 140f + bounceY, 70f, 96f, 0.95f);
        }
    }

    // Authentic Start Menu Screen
    private void drawMenu(Canvas c) {
        float centerX = W * 0.5f;

        // Big Authentic PLAY Button (centered vertically at 48% height)
        float pulse = 1.0f + (float) Math.sin(clock * 3.5f) * 0.035f;
        float playW = 330f * pulse;
        float playH = 172f * pulse;
        float playX = centerX - playW * 0.5f;
        float playY = VIRTUAL_HEIGHT * 0.46f - playH * 0.5f;
        blit(c, "play", playX, playY, playW, playH, 1.0f);

        // Authentic Title Logo (floating naturally right above PLAY)
        float logoW = Math.min(W * 0.65f, 480f);
        float logoH = logoW * (470f / 670f);
        float logoY = playY - logoH + 20f + (float) Math.sin(clock * 2.5f) * 6f;
        blit(c, "title_logo", centerX - logoW * 0.5f, logoY, logoW, logoH, 1.0f);

        // High Score Record using authentic BitmapNumber (num5)
        if (highScore > 0) {
            float recordY = playY + playH + 12f;
            drawBitmapNumber(c, "num5", highScore, centerX, recordY, Paint.Align.CENTER);
        }

        // Authentic Footer Bar (footer.png 960x86 across bottom)
        float footerY = VIRTUAL_HEIGHT - 86f;
        blitTile(c, "footer", 0, footerY, W, 86f);

        // Authentic Footer Buttons
        blit(c, "index_about", W * 0.15f - 38f, footerY - 5f, 76f, 80f, 1.0f);
        blit(c, "index_rank", W * 0.35f - 38f, footerY - 5f, 76f, 80f, 1.0f);
        blit(c, "index_pack", W * 0.55f - 38f, footerY - 5f, 76f, 80f, 1.0f);
        blit(c, "sound", W * 0.75f - 60f, footerY + 8f, 120f, 60f, (soundEnabled && musicEnabled) ? 1.0f : 0.45f);
        blit(c, "index_signin", W * 0.90f - 65f, footerY + 8f, 130f, 60f, 0.95f);
    }

    // Authentic Backpack & Shop Modal (Trampoline, Hair, Kids Mode)
    private void drawShop(Canvas c) {
        paint.reset();
        paint.setColor(0xcc000e1a);
        c.drawRect(0, 0, W, VIRTUAL_HEIGHT, paint);

        float centerX = W * 0.5f;

        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(0xffffffff);
        paint.setTextSize(34f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(gameFont);
        c.drawText("PAUL'S BACKPACK & SHOP", centerX, 70f, paint);

        paint.setTextSize(22f);
        paint.setColor(0xffffcc00);
        c.drawText("Trampoline: Style " + (currentBoardStyle + 1) + " (Tap to change)", centerX, 150f, paint);
        String lineKey = "board" + (currentBoardStyle + 1) + "_line";
        blit(c, lineKey, centerX - 140f, 180f, 280f, 36f, 1.0f);
        blit(c, "board_dot", centerX - 158f, 180f, 36f, 36f, 1.0f);
        blit(c, "board_dot", centerX + 122f, 180f, 36f, 36f, 1.0f);

        c.drawText("Hairstyle: " + (selectedHair == 0 ? "Default" : selectedHair == 1 ? "Afro" : selectedHair == 2 ? "Parted" : "Mochica") + " (Tap to change)", centerX, 275f, paint);
        if (selectedHair > 0) {
            blit(c, "hair" + selectedHair, centerX - 45f, 305f, 90f, 80f, 1.0f);
        } else {
            blit(c, "leaf", centerX - 25f, 320f, 50f, 50f, 1.0f);
        }

        blit(c, "scosun", centerX - 80f, 420f, 44f, 44f, 1.0f);
        drawBitmapNumber(c, "num2", sunCount, centerX - 25f, 425f, Paint.Align.LEFT);

        blit(c, "resume", centerX - 130f, 500f, 260f, 85f, 1.0f);
    }

    // Authentic Pause Screen (PauseView)
    private void drawPause(Canvas c) {
        // Darkened backdrop
        paint.reset();
        paint.setColor(0x88001020);
        c.drawRect(0, 0, W, VIRTUAL_HEIGHT, paint);

        // Authentic Buttons
        float centerX = W * 0.5f;
        blit(c, "resume", centerX - 180f, 180f, 360f, 117f, 1.0f);
        blit(c, "sound", centerX - 60f, 320f, 120f, 60f, (soundEnabled && musicEnabled) ? 1.0f : 0.45f);
        blit(c, "retry", centerX - 120f, 400f, 240f, 115f, 1.0f);
        blit(c, "menu", centerX - 120f, 525f, 240f, 115f, 1.0f);
    }

    // Authentic Game Over Screen (GameOverView)
    private void drawGameOver(Canvas c) {
        // Darkened backdrop
        paint.reset();
        paint.setColor(0x99000c18);
        c.drawRect(0, 0, W, VIRTUAL_HEIGHT, paint);

        int season = currentSeason();
        String goBg = "gameover_bg" + (season + 1);
        float centerX = W * 0.5f;

        // Seasonal backdrop illustration
        blit(c, goBg, centerX - 240f, 50f, 480f, 540f, 0.95f);

        // Hanging signpost on the left
        blit(c, "gameover_sign", centerX - 270f, 0, 150f, 480f, 1.0f);

        // Title: gameover_title.png
        blit(c, "gameover_title", centerX - 210f, 55f, 420f, 168f, 1.0f);

        // Score tallying
        if (displayScore < score) {
            displayScore += Math.max(1, (score - displayScore) / 4);
            playSfx("score_tick.ogg", 0.35f);
        }
        drawBitmapNumber(c, "num1", displayScore, centerX, 245f, Paint.Align.CENTER);

        // Sun count & Best Record
        blit(c, "oversun", centerX - 130f, 320f, 58f, 56f, 1.0f);
        drawBitmapNumber(c, "num2", sunCount, centerX - 65f, 330f, Paint.Align.LEFT);

        blit(c, "top", centerX + 30f, 322f, 52f, 50f, 1.0f);
        drawBitmapNumber(c, "num3", highScore, centerX + 90f, 332f, Paint.Align.LEFT);

        // Grass hill along bottom
        blit(c, "gameover_grass", centerX - 320f, VIRTUAL_HEIGHT - 120f, 640f, 120f, 1.0f);

        // Buttons
        blit(c, "gameover_retry", centerX - 150f, 410f, 130f, 130f, 1.0f);
        blit(c, "gameover_menu", centerX + 20f, 410f, 130f, 125f, 1.0f);
        blit(c, "gameover_rank", centerX - 250f, 520f, 105f, 85f, 1.0f);
        blit(c, "gameover_pack", centerX + 145f, 520f, 100f, 85f, 1.0f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getHeight() <= 0) return;

        // Scaling for 640p Retina Baseline
        scale = getHeight() / VIRTUAL_HEIGHT;
        W = getWidth() / scale;

        long now = System.nanoTime();
        float dt = (lastFrameNanos == 0) ? 0.016f : clamp((now - lastFrameNanos) / 1_000_000_000f, 0.001f, 0.045f);
        lastFrameNanos = now;

        step(dt);

        canvas.save();
        canvas.scale(scale, scale);

        drawSky(canvas);
        drawScenery(canvas);

        if (state == STATE_PLAY || state == STATE_PAUSE || state == STATE_GAME_OVER) {
            drawEntities(canvas);
            drawRainbowTrail(canvas, seedX - cameraX, seedY);
            drawSeed(canvas, seedX - cameraX, seedY, 78f);
            drawDragPreview(canvas);
            drawHud(canvas);
        }

        if (state == STATE_MENU) {
            drawMenu(canvas);
        } else if (state == STATE_SHOP) {
            drawShop(canvas);
        } else if (state == STATE_PAUSE) {
            drawPause(canvas);
        } else if (state == STATE_GAME_OVER) {
            drawGameOver(canvas);
        }

        canvas.restore();
        postInvalidateOnAnimation();
    }

    public void pauseFromSystem() {
        if (state == STATE_PLAY) {
            state = STATE_PAUSE;
        }
        pauseBgm();
    }

    public void back() {
        if (state == STATE_PLAY) {
            state = STATE_PAUSE;
        } else if (state == STATE_PAUSE || state == STATE_GAME_OVER) {
            state = STATE_MENU;
        }
    }

    public void onDestroy() {
        stopBgm();
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
        }
        for (Bitmap b : sprites.values()) {
            if (b != null && !b.isRecycled()) {
                b.recycle();
            }
        }
        sprites.clear();
    }

    private void updateGestureEnd(float worldX, float y) {
        float dx = worldX - gestureStartX;
        float dy = y - gestureStartY;
        float len = (float) Math.hypot(dx, dy);
        if (len > 280f) {
            dx = dx * 280f / len;
            dy = dy * 280f / len;
        }
        gestureEndX = gestureStartX + dx;
        gestureEndY = gestureStartY + dy;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float touchX = event.getX() / scale;
        float touchY = event.getY() / scale;
        float worldX = touchX + cameraX;

        int action = event.getActionMasked();

        if (state == STATE_MENU) {
            if (action == MotionEvent.ACTION_DOWN) {
                float centerX = W * 0.5f;
                float logoW = Math.min(W * 0.68f, 500f);
                float logoH = logoW * (470f / 670f);
                float logoY = 30f;
                float playY = logoY + logoH - 15f;

                // 1. Tapping Play Button
                if (touchX >= centerX - 180f && touchX <= centerX + 180f &&
                    touchY >= playY && touchY <= playY + 180f) {
                    playSfx("bounce.ogg", 0.9f);
                    triggerHaptic(30);
                    resetGame();
                    return true;
                }

                // 2. Tapping Seed character in logo cycles skins and hair
                if (dist(touchX, touchY, centerX, logoY + logoH * 0.42f) < 85f) {
                    selectedSkin = (selectedSkin + 1) % 4;
                    selectedHair = (selectedHair + 1) % 4;
                    prefs.edit().putInt("selected_skin", selectedSkin)
                                .putInt("selected_hair", selectedHair).apply();
                    playSfx("hop.ogg", 0.85f);
                    triggerHaptic(20);
                    return true;
                }

                // 3. Audio toggle on footer
                float footerY = VIRTUAL_HEIGHT - 86f;
                if (touchX >= W * 0.75f - 60f && touchX <= W * 0.75f + 60f &&
                    touchY >= footerY) {
                    soundEnabled = !soundEnabled;
                    musicEnabled = soundEnabled;
                    prefs.edit().putBoolean("sound_on", soundEnabled)
                                .putBoolean("music_on", musicEnabled).apply();
                    if (musicEnabled) startBgm(); else pauseBgm();
                    triggerHaptic(15);
                    return true;
                }

                // 4. Style cycle via footer pack button
                if (touchX >= W * 0.55f - 40f && touchX <= W * 0.55f + 40f &&
                    touchY >= footerY) {
                    currentBoardStyle = (currentBoardStyle + 1) % 4;
                    playSfx("bounce.ogg", 0.7f);
                    triggerHaptic(15);
                    return true;
                }
            }
            return true;
        }

        if (state == STATE_PAUSE) {
            if (action == MotionEvent.ACTION_DOWN) {
                float centerX = W * 0.5f;

                // Resume
                if (touchX >= centerX - 200f && touchX <= centerX + 200f &&
                    touchY >= 180f && touchY <= 300f) {
                    playSfx("bounce.ogg", 0.8f);
                    state = STATE_PLAY;
                    return true;
                }

                // Audio toggle
                if (touchX >= centerX - 70f && touchX <= centerX + 70f &&
                    touchY >= 320f && touchY <= 380f) {
                    soundEnabled = !soundEnabled;
                    musicEnabled = soundEnabled;
                    prefs.edit().putBoolean("sound_on", soundEnabled)
                                .putBoolean("music_on", musicEnabled).apply();
                    if (musicEnabled) resumeBgm(); else pauseBgm();
                    return true;
                }

                // Retry
                if (touchX >= centerX - 130f && touchX <= centerX + 130f &&
                    touchY >= 400f && touchY <= 515f) {
                    resetGame();
                    return true;
                }

                // Menu
                if (touchX >= centerX - 130f && touchX <= centerX + 130f &&
                    touchY >= 525f && touchY <= 630f) {
                    state = STATE_MENU;
                    return true;
                }
            }
            return true;
        }

        if (state == STATE_GAME_OVER) {
            if (action == MotionEvent.ACTION_DOWN) {
                float centerX = W * 0.5f;

                // Retry button
                if (touchX >= centerX - 160f && touchX <= centerX - 10f &&
                    touchY >= 400f && touchY <= 550f) {
                    playSfx("bounce.ogg", 0.9f);
                    triggerHaptic(30);
                    resetGame();
                    return true;
                }

                // Menu button
                if (touchX >= centerX + 10f && touchX <= centerX + 160f &&
                    touchY >= 400f && touchY <= 550f) {
                    state = STATE_MENU;
                    return true;
                }
            }
            return true;
        }

        // In Gameplay: Drawing finger trampolines
        if (state == STATE_PLAY) {
            // Check Pause button tap
            if (action == MotionEvent.ACTION_DOWN && touchX >= W - 90f && touchY <= 80f) {
                state = STATE_PAUSE;
                return true;
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    gestureStartX = worldX;
                    gestureStartY = touchY;
                    gestureEndX = worldX;
                    gestureEndY = touchY;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        updateGestureEnd(worldX, touchY);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        dragging = false;
                        updateGestureEnd(worldX, touchY);
                        float len = dist(gestureStartX, gestureStartY, gestureEndX, gestureEndY);
                        if (len >= 32f) {
                            // Ensure left-to-right order for consistency
                            float x1 = gestureStartX, y1 = gestureStartY;
                            float x2 = gestureEndX, y2 = gestureEndY;
                            if (x1 > x2) {
                                x1 = gestureEndX; y1 = gestureEndY;
                                x2 = gestureStartX; y2 = gestureStartY;
                            }
                            trampolines.add(new Trampoline(x1, y1, x2, y2, currentBoardStyle));
                            playSfx("bounce.ogg", 0.45f);

                            // Max active trampolines
                            if (trampolines.size() > 4) {
                                trampolines.remove(0);
                            }
                        }
                    }
                    return true;
            }
        }

        return super.onTouchEvent(event);
    }
}
