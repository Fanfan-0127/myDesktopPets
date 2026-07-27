package pet;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.spine.AnimationState;
import com.esotericsoftware.spine.AnimationStateData;
import com.esotericsoftware.spine.Skeleton;
import com.esotericsoftware.spine.SkeletonBinary;
import com.esotericsoftware.spine.SkeletonData;
import com.esotericsoftware.spine.SkeletonRenderer;
import com.esotericsoftware.spine.Slot;
import com.esotericsoftware.spine.attachments.Attachment;
import com.esotericsoftware.spine.attachments.RegionAttachment;
import com.esotericsoftware.spine.attachments.MeshAttachment;
import java.util.List;
import pet.config.AppConfig;
import pet.model.ModelManager;
import pet.model.ModelManager.ModelFiles;
import pet.window.WindowManager;
import pet.window.WindowManager.SnapResult;

public class PetCore extends ApplicationAdapter {

    private static final float BASE_MOVE_SPEED = 100f;
    private static final int SNAP_EDGE_GUARD = 50;

    private PolygonSpriteBatch batch;
    private OrthographicCamera camera;
    private SkeletonRenderer renderer;

    private final AppConfig cfg = AppConfig.getInstance();

    private Skeleton skeleton;
    private AnimationState animationState;
    private SkeletonData skeletonData;
    private float animSpeed = cfg.getAnimSpeed();
    private float moveSpeed = cfg.getMoveSpeed();
    private float petScale = cfg.getPetScale();

    private int windowX, windowY;
    private int targetScreenX;
    private int targetWindowY;
    private int windowW, windowH;
    private float renderX, renderY;
    private boolean moving;
    private boolean snapping;
    private boolean dragging;
    private boolean clickStart;
    private float dragOffsetScreenX, dragOffsetScreenY;
    private float dragStartX, dragStartY;
    private float idleTimer;
    private float relaxTimer;
    private float sleepTimer;
    private float greetingDelayTimer;
    private float voiceCooldownTimer;
    private String currentAnim;
    private boolean facingRight = true;
    private boolean windowInitDone;
    private float petMinX, petMinY, petMaxX, petMaxY;
    private boolean snapped;
    private int snappedLeftBound;
    private int snappedRightBound;

    private float sleepTimeout = cfg.getSleepTimeout();
    private float sleepChance = cfg.getSleepChance();
    private float relaxMin = cfg.getRelaxMin();
    private float relaxMax = cfg.getRelaxMax();
    private float moveChance = cfg.getMoveChance();
    private float specialChance = cfg.getSpecialChance();
    private float ambientVoiceIntervalSeconds = cfg.getAmbientVoiceInterval();
    private boolean interactive = cfg.isInteractive();
    private boolean voiceEnabled = cfg.isVoiceEnabled();
    private volatile boolean alwaysOnTop = cfg.isAlwaysOnTop();
    private int pad = cfg.getPad();
    private int rightPadExtra = cfg.getRightPadExtra();

    private Music interactVoice;
    private Music greetingVoice;
    private Music[] ambientVoices = new Music[0];
    private boolean greetingPending;
    private Music currentVoice;

    @Override
    public void create() {
        batch = new PolygonSpriteBatch();
        camera = new OrthographicCamera();
        renderer = new SkeletonRenderer();
        renderer.setPremultipliedAlpha(true);

        List<String> models = ModelManager.listModels();
        String initialModel = !models.isEmpty() ? models.get(0) : "oblivionis";
        loadModel(initialModel);

        setAnimation("Relax", true);
        animationState.update(0);
        animationState.apply(skeleton);
        skeleton.updateWorldTransform();
        calcWindowSize();

        int taskbarTop = WindowManager.getTaskbarTop();
        if (taskbarTop == 0) taskbarTop = Gdx.graphics.getDisplayMode().height - 40;
        windowX = (Gdx.graphics.getDisplayMode().width - windowW) / 2;
        windowY = taskbarTop - windowH;

        relaxTimer = MathUtils.random(relaxMin, relaxMax);
        greetingDelayTimer = 1f;
        greetingPending = true;
        scheduleNextAmbientVoice();
    }

    private void calcWindowSize() {
        skeleton.setPosition(0, 0);
        skeleton.updateWorldTransform();
        float[] bbox = computeBbox();
        if (bbox != null) {
            windowW = (int) (bbox[2] - bbox[0]) + pad * 2 + rightPadExtra;
            windowH = (int) (bbox[3] - bbox[1]) + pad * 2;
            renderX = pad - bbox[0];
            renderY = pad - bbox[1];
            petMinX = renderX + bbox[0];
            petMinY = renderY + bbox[1];
            petMaxX = renderX + bbox[2];
            petMaxY = renderY + bbox[3];
        } else {
            windowW = pad * 2 + rightPadExtra + 100;
            windowH = pad * 2 + 100;
            renderX = pad;
            renderY = pad;
        }
    }

    private float[] computeBbox() {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        boolean found = false;
        for (Slot slot : skeleton.getDrawOrder()) {
            Attachment att = slot.getAttachment();
            if (att == null) continue;
            float[] verts = null;
            if (att instanceof RegionAttachment) {
                verts = new float[8];
                ((RegionAttachment) att).computeWorldVertices(slot.getBone(), verts, 0, 2);
            } else if (att instanceof MeshAttachment) {
                MeshAttachment ma = (MeshAttachment) att;
                int n = ma.getWorldVerticesLength();
                verts = new float[n];
                ma.computeWorldVertices(slot, 0, n, verts, 0, 2);
            }
            if (verts != null) {
                found = true;
                for (int i = 0; i < verts.length; i += 2) {
                    if (verts[i] < minX) minX = verts[i];
                    if (verts[i] > maxX) maxX = verts[i];
                    if (verts[i + 1] < minY) minY = verts[i + 1];
                    if (verts[i + 1] > maxY) maxY = verts[i + 1];
                }
            }
        }
        return found ? new float[] { minX, minY, maxX, maxY } : null;
    }

    private void loadModel(String modelName) {
        disposeVoices();
        ModelFiles files = ModelManager.getModelFiles(modelName);
        if (files == null) {
            Gdx.app.error("PetCore", "Model not found: " + modelName);
            return;
        }

        TextureAtlas atlas = new TextureAtlas(files.atlas());
        SkeletonBinary binary = new SkeletonBinary(atlas);
        skeletonData = binary.readSkeletonData(files.skel());

        skeleton = new Skeleton(skeletonData);
        skeleton.setScaleX(petScale);
        skeleton.setScaleY(petScale);
        AnimationStateData stateData = new AnimationStateData(skeletonData);
        stateData.setDefaultMix(0.3f);
        animationState = new AnimationState(stateData);

        loadVoices(files.voiceDir());
        greetingDelayTimer = 1f;
        greetingPending = true;
        scheduleNextAmbientVoice();
    }

    @Override
    public void render() {
        if (!windowInitDone) {
            Lwjgl3Graphics graphics = (Lwjgl3Graphics) Gdx.graphics;
            long glfwWindow = graphics.getWindow().getWindowHandle();
            WindowManager.init(glfwWindow, alwaysOnTop);
            WindowManager.moveWindow(windowX, windowY, windowW, windowH);
            windowInitDone = true;
        }

        float delta = Gdx.graphics.getDeltaTime() * animSpeed;
        idleTimer += delta;

        handleInput();
        updateBehavior(delta);
        updateVoicePlayback(delta);

        animationState.update(delta);
        animationState.apply(skeleton);

        skeleton.setPosition(renderX, renderY);
        skeleton.setScaleX(facingRight ? petScale : -petScale);
        skeleton.setScaleY(petScale);
        skeleton.updateWorldTransform();

        if (moving || snapping) {
            updateMotion(delta);
        }

        camera.viewportWidth = windowW;
        camera.viewportHeight = windowH;
        camera.position.set(windowW / 2f, windowH / 2f, 0);
        camera.update();
        batch.setProjectionMatrix(camera.combined);

        Gdx.gl.glClearColor(0, 0, 0, 0);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        renderer.draw(batch, skeleton);
        batch.end();
    }

    private void handleInput() {
        if (!interactive) return;

        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.input.getY();
        float screenMouseX = windowX + mouseX;
        float screenMouseY = windowY + mouseY;

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (hitTest(mouseX, windowH - mouseY)) {
                dragStartX = mouseX;
                dragStartY = mouseY;
                clickStart = true;
                idleTimer = 0;
            }
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (clickStart) {
                float dist = Math.abs(mouseX - dragStartX) + Math.abs(mouseY - dragStartY);
                if (dist > 3) {
                    clickStart = false;
                    dragging = true;
                    dragOffsetScreenX = windowX - screenMouseX;
                    dragOffsetScreenY = windowY - screenMouseY;
                    setAnimation("Default", true);
                }
            }
            if (dragging) {
                windowX = (int) (screenMouseX + dragOffsetScreenX);
                windowY = (int) (screenMouseY + dragOffsetScreenY);
                moving = false;
                snapping = false;
                clearSnappedBounds();
                WindowManager.moveWindow(windowX, windowY, windowW, windowH);
            }
        }

        if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (clickStart) {
                clickStart = false;
                if (hasAnimation("Interact")) {
                    animationState.setAnimation(0, "Interact", false);
                    animationState.addAnimation(0, "Relax", true, 0);
                    tryPlayVoice(interactVoice);
                }
                currentAnim = "Relax";
                relaxTimer = MathUtils.random(relaxMin, relaxMax);
            }
            if (dragging) {
                dragging = false;
                onDragRelease();
            }
        }

        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && hasAnimation("Special")) {
            animationState.setAnimation(0, "Special", false);
            animationState.addAnimation(0, "Relax", true, 0);
            currentAnim = "Relax";
            relaxTimer = MathUtils.random(relaxMin, relaxMax);
        }
    }

    private void onDragRelease() {
        int petBodyLeft = Math.round(windowX + petMinX);
        int petBodyTop = Math.round(windowY + (windowH - petMaxY));
        int petBodyWidth = Math.max(1, Math.round(petMaxX - petMinX));
        int petBodyHeight = Math.max(1, Math.round(petMaxY - petMinY));

        SnapResult snap = WindowManager.snapToNearestWindow(
            petBodyLeft,
            petBodyTop,
            petBodyWidth,
            petBodyHeight
        );
        if (snap != null) {
            windowX = Math.round(snap.x() - petMinX);
            windowY = Math.round(snap.bottom() - petMaxY);
            stopMotion();
            rememberSnappedBounds(snap.left(), snap.right());
            WindowManager.moveWindow(windowX, windowY, windowW, windowH);
        } else {
            clearSnappedBounds();
        }
        relaxTimer = MathUtils.random(relaxMin, relaxMax);
        setAnimation("Relax", true);
    }

    private void updateBehavior(float delta) {
        if (dragging || clickStart) return;

        if (idleTimer > sleepTimeout && !currentAnim.equals("Sleep")) {
            if (hasAnimation("Sleep") && MathUtils.random() < sleepChance) {
                sleepTimer = sleepTimeout;
                stopMotion();
                setAnimation("Sleep", true);
            } else {
                idleTimer = 0f;
            }
            return;
        }

        if (currentAnim.equals("Sleep")) {
            stopMotion();
            sleepTimer -= delta;
            if (sleepTimer <= 0f) {
                idleTimer = 0f;
                relaxTimer = MathUtils.random(relaxMin, relaxMax);
                setAnimation("Relax", true);
            }
            return;
        }

        if (isPlayingOneShot()) return;

        if (!moving && currentAnim.equals("Relax")) {
            relaxTimer -= delta;
            if (relaxTimer <= 0 && MathUtils.random() < moveChance) {
                int[] bounds = WindowManager.getMovementBounds(windowW);
                targetScreenX = chooseNextTargetX(bounds[0], bounds[1]);
                targetWindowY = windowY;
                moving = true;
                snapping = false;
                setAnimation("Move", true);
            } else if (relaxTimer <= 0) {
                relaxTimer = MathUtils.random(relaxMin, relaxMax);
            }
        }

        if (!moving && hasAnimation("Special")
            && MathUtils.random() < specialChance * delta) {
            animationState.setAnimation(0, "Special", false);
            animationState.addAnimation(0, "Relax", true, 0);
        }
    }

    private void setAnimation(String name, boolean loop) {
        if (!name.equals(currentAnim)) {
            currentAnim = name;
            animationState.setAnimation(0, name, loop);
        }
    }

    private boolean hasAnimation(String name) {
        for (var anim : skeletonData.getAnimations()) {
            if (anim.getName().equals(name)) return true;
        }
        return false;
    }

    private boolean isPlayingOneShot() {
        var entry = animationState.getCurrent(0);
        if (entry == null || entry.getAnimation() == null) return false;
        String name = entry.getAnimation().getName();
        return "Interact".equals(name) || "Special".equals(name);
    }

    private boolean hitTest(float x, float y) {
        return x > petMinX && x < petMaxX && y > petMinY && y < petMaxY;
    }

    private void updateMotion(float delta) {
        float dx = targetScreenX - windowX;
        float dy = targetWindowY - windowY;
        if (Math.abs(dx) > 1f) {
            facingRight = dx > 0;
        }

        int step = Math.max(1, (int) (moveSpeed * delta));
        int moveX = (int) Math.signum(dx) * Math.min(Math.abs(Math.round(dx)), step);
        int moveY = (int) Math.signum(dy) * Math.min(Math.abs(Math.round(dy)), step);

        boolean reachedX = Math.abs(dx) <= 3f;
        boolean reachedY = Math.abs(dy) <= 3f;

        if (!reachedX) {
            windowX += moveX;
        } else {
            windowX = targetScreenX;
        }

        if (!reachedY) {
            windowY += moveY;
        } else {
            windowY = targetWindowY;
        }

        WindowManager.moveWindow(windowX, windowY, windowW, windowH);

        if (reachedX && reachedY) {
            moving = false;
            snapping = false;
            relaxTimer = MathUtils.random(relaxMin, relaxMax);
            setAnimation("Relax", true);
        }
    }

    private void stopMotion() {
        moving = false;
        snapping = false;
        targetScreenX = windowX;
        targetWindowY = windowY;
    }

    private int chooseNextTargetX(int minX, int maxX) {
        int fallback = MathUtils.random(minX, maxX);
        if (!snapped) {
            return fallback;
        }

        int petLeft = Math.round(windowX + petMinX);
        int petRight = Math.round(windowX + petMaxX);
        int leftDistance = Math.abs(petLeft - snappedLeftBound);
        int rightDistance = Math.abs(snappedRightBound - petRight);

        int leftEscapeMin = minX;
        int leftEscapeMax = Math.max(minX, windowX - 1);
        int rightEscapeMin = Math.min(maxX, windowX + 1);
        int rightEscapeMax = maxX;

        if (leftDistance < SNAP_EDGE_GUARD && rightEscapeMin <= rightEscapeMax) {
            return MathUtils.random(rightEscapeMin, rightEscapeMax);
        }
        if (rightDistance < SNAP_EDGE_GUARD && leftEscapeMin <= leftEscapeMax) {
            return MathUtils.random(leftEscapeMin, leftEscapeMax);
        }
        return fallback;
    }

    private void rememberSnappedBounds(int left, int right) {
        snapped = true;
        snappedLeftBound = left;
        snappedRightBound = right;
    }

    private void clearSnappedBounds() {
        snapped = false;
        snappedLeftBound = 0;
        snappedRightBound = 0;
    }

    private void updateVoicePlayback(float delta) {
        if (!voiceEnabled) {
            return;
        }

        if (greetingPending && greetingVoice != null) {
            greetingDelayTimer -= delta;
            if (greetingDelayTimer <= 0f) {
                if (tryPlayVoice(greetingVoice)) {
                    greetingPending = false;
                }
            }
        }

        if (ambientVoices.length == 0 || ambientVoiceIntervalSeconds <= 0f) {
            return;
        }

        if (!isAmbientVoiceAllowed()) {
            return;
        }

        voiceCooldownTimer -= delta;
        if (voiceCooldownTimer <= 0f) {
            tryPlayVoice(ambientVoices[MathUtils.random(ambientVoices.length - 1)]);
            scheduleNextAmbientVoice();
        }
    }

    private void loadVoices(FileHandle voiceDir) {
        interactVoice = null;
        greetingVoice = null;
        ambientVoices = new Music[0];
        if (voiceDir == null || !voiceDir.isDirectory()) {
            return;
        }

        FileHandle interactFile = voiceDir.child("戳一下.wav");
        if (interactFile.exists()) {
            interactVoice = createVoice(interactFile);
        }

        FileHandle greetingFile = voiceDir.child("问候.wav");
        if (greetingFile.exists()) {
            greetingVoice = createVoice(greetingFile);
        }

        String[] ambientNames = { "交谈1.wav", "交谈2.wav", "交谈3.wav", "信赖触摸.wav" };
        List<Music> sounds = new java.util.ArrayList<>();
        for (String ambientName : ambientNames) {
            FileHandle ambientFile = voiceDir.child(ambientName);
            if (ambientFile.exists()) {
                sounds.add(createVoice(ambientFile));
            }
        }
        ambientVoices = sounds.toArray(new Music[0]);
    }

    private void scheduleNextAmbientVoice() {
        if (ambientVoiceIntervalSeconds <= 0f) {
            voiceCooldownTimer = Float.MAX_VALUE;
            return;
        }
        float halfInterval = ambientVoiceIntervalSeconds * 0.5f;
        voiceCooldownTimer = MathUtils.random(
            ambientVoiceIntervalSeconds,
            ambientVoiceIntervalSeconds + halfInterval
        );
    }

    private Music createVoice(FileHandle file) {
        Music voice = Gdx.audio.newMusic(file);
        voice.setOnCompletionListener(completed -> {
            completed.stop();
            completed.setPosition(0f);
            if (currentVoice == completed) {
                currentVoice = null;
            }
        });
        return voice;
    }

    private boolean tryPlayVoice(Music voice) {
        if (voice == null || isVoicePlaying()) {
            return false;
        }
        currentVoice = voice;
        voice.stop();
        voice.setPosition(0f);
        voice.play();
        return true;
    }

    private boolean isVoicePlaying() {
        return currentVoice != null && currentVoice.isPlaying();
    }

    private boolean isAmbientVoiceAllowed() {
        if (dragging || clickStart) {
            return false;
        }
        return "Relax".equals(currentAnim) || "Move".equals(currentAnim);
    }

    private void disposeVoice(Music voice) {
        if (voice != null) {
            voice.stop();
            voice.dispose();
        }
    }

    private void disposeVoices() {
        disposeVoice(interactVoice);
        interactVoice = null;
        disposeVoice(greetingVoice);
        greetingVoice = null;
        for (Music ambientVoice : ambientVoices) {
            disposeVoice(ambientVoice);
        }
        ambientVoices = new Music[0];
        currentVoice = null;
    }

    public void switchModel(String modelName) {
        Gdx.app.postRunnable(() -> {
            String prevAnim = currentAnim;
            loadModel(modelName);

            String anim = prevAnim != null ? prevAnim : "Relax";
            setAnimation(anim, true);
            animationState.update(0);
            animationState.apply(skeleton);
            skeleton.updateWorldTransform();
            calcWindowSize();
            WindowManager.moveWindow(windowX, windowY, windowW, windowH);
        });
    }

    public void setAnimSpeed(float speed) { this.animSpeed = speed; }
    public float getAnimSpeed() { return animSpeed; }
    public void setMoveSpeed(float speed) { this.moveSpeed = speed * BASE_MOVE_SPEED; }
    public float getMoveSpeed() { return moveSpeed / BASE_MOVE_SPEED; }

    public void setPetScale(float scale) {
        Gdx.app.postRunnable(() -> {
            this.petScale = scale;
            skeleton.setScaleX(petScale);
            skeleton.setScaleY(petScale);
            calcWindowSize();
            WindowManager.moveWindow(windowX, windowY, windowW, windowH);
        });
    }
    public float getPetScale() { return petScale; }

    public void setSleepTimeout(float sec) { this.sleepTimeout = sec; }
    public float getSleepTimeout() { return sleepTimeout; }
    public void setSpecialChance(float pct) { this.specialChance = pct / 100f; }
    public float getSpecialChance() { return specialChance * 100f; }
    public void setMoveFrequency(float pct) { this.moveChance = pct / 100f; }
    public float getMoveFrequency() { return moveChance * 100f; }
    public void setRelaxMin(float sec) { this.relaxMin = sec; }
    public float getRelaxMin() { return relaxMin; }
    public void setRelaxMax(float sec) { this.relaxMax = sec; }
    public float getRelaxMax() { return relaxMax; }
    public void setAmbientVoiceIntervalSeconds(float sec) {
        this.ambientVoiceIntervalSeconds = sec;
        scheduleNextAmbientVoice();
    }
    public float getAmbientVoiceIntervalSeconds() { return ambientVoiceIntervalSeconds; }
    public void setInteractive(boolean on) { this.interactive = on; }
    public boolean isInteractive() { return interactive; }
    public void setVoiceEnabled(boolean on) { this.voiceEnabled = on; }
    public boolean isVoiceEnabled() { return voiceEnabled; }
    public void setAlwaysOnTop(boolean on) {
        this.alwaysOnTop = on;
        if (Gdx.app != null) {
            Gdx.app.postRunnable(() -> WindowManager.setAlwaysOnTop(on));
        }
    }
    public boolean isAlwaysOnTop() { return alwaysOnTop; }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.position.set(width / 2f, height / 2f, 0);
        camera.update();
    }

    @Override
    public void dispose() {
        disposeVoices();
        batch.dispose();
    }
}
