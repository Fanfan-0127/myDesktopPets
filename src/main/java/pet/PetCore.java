package pet;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import pet.model.ModelManager;
import pet.model.ModelManager.ModelFiles;
import pet.window.WindowManager;
import pet.window.WindowManager.SnapResult;

public class PetCore extends ApplicationAdapter {

    private PolygonSpriteBatch batch;
    private OrthographicCamera camera;
    private SkeletonRenderer renderer;

    private Skeleton skeleton;
    private AnimationState animationState;
    private SkeletonData skeletonData;
    private float animSpeed = 1f;
    private float moveSpeed = 120f;
    private float petScale = 0.5f;

    private int windowX, windowY;
    private int targetScreenX;
    private int windowW, windowH;
    private float renderX, renderY;
    private boolean moving;
    private boolean dragging;
    private boolean clickStart;
    private float dragOffsetScreenX, dragOffsetScreenY;
    private float dragStartX, dragStartY;
    private float idleTimer;
    private float relaxTimer;
    private String currentAnim;
    private boolean facingRight = true;
    private boolean windowInitDone;
    private float petMinX, petMinY, petMaxX, petMaxY;

    private float sleepTimeout = 120f;
    private float relaxMin = 1.5f;
    private float relaxMax = 5f;
    private float moveChance = 1f;
    private float specialChance = 0.02f;
    private boolean interactive = true;
    private int pad = 30;

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
    }

    private void calcWindowSize() {
        skeleton.setPosition(0, 0);
        skeleton.updateWorldTransform();
        float[] bbox = computeBbox();
        if (bbox != null) {
            windowW = (int) (bbox[2] - bbox[0]) + pad * 2;
            windowH = (int) (bbox[3] - bbox[1]) + pad * 2;
            renderX = windowW / 2f;
            renderY = pad - bbox[1];
            petMinX = renderX + bbox[0];
            petMinY = renderY + bbox[1];
            petMaxX = renderX + bbox[2];
            petMaxY = renderY + bbox[3];
        } else {
            windowW = pad * 2 + 100;
            windowH = pad * 2 + 100;
            renderX = windowW / 2f;
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
    }

    @Override
    public void render() {
        if (!windowInitDone) {
            WindowManager.init("DesktopPet");
            WindowManager.moveWindow(windowX, windowY, windowW, windowH);
            windowInitDone = true;
        }

        float delta = Gdx.graphics.getDeltaTime() * animSpeed;
        idleTimer += delta;

        handleInput();
        updateBehavior(delta);

        animationState.update(delta);
        animationState.apply(skeleton);

        skeleton.setPosition(renderX, renderY);
        skeleton.setScaleX(facingRight ? petScale : -petScale);
        skeleton.setScaleY(petScale);
        skeleton.updateWorldTransform();

        if (moving) {
            float dx = targetScreenX - windowX;
            if (Math.abs(dx) > 3) {
                int step = (int) (moveSpeed * delta);
                windowX += (int) (Math.signum(dx) * Math.min(Math.abs(dx), step));
                facingRight = dx > 0;
                WindowManager.moveWindow(windowX, windowY, windowW, windowH);
            } else {
                windowX = targetScreenX;
                WindowManager.moveWindow(windowX, windowY, windowW, windowH);
                moving = false;
                relaxTimer = MathUtils.random(relaxMin, relaxMax);
                setAnimation("Relax", true);
            }
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
                WindowManager.moveWindow(windowX, windowY, windowW, windowH);
            }
        }

        if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            if (clickStart) {
                clickStart = false;
                if (hasAnimation("Interact")) {
                    animationState.setAnimation(0, "Interact", false);
                    animationState.addAnimation(0, "Relax", true, 0);
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
        SnapResult snap = WindowManager.snapToNearestWindow(windowX, windowY, windowW, windowH);
        if (snap != null) {
            windowX = snap.x();
            windowY = snap.y();
            WindowManager.moveWindow(windowX, windowY, windowW, windowH);
            setAnimation(snap.topEdge() ? "Sit" : "Relax", true);
        } else {
            setAnimation("Relax", true);
        }
        relaxTimer = MathUtils.random(relaxMin, relaxMax);
    }

    private void updateBehavior(float delta) {
        if (dragging || clickStart) return;

        if (idleTimer > sleepTimeout && !currentAnim.equals("Sleep")) {
            if (hasAnimation("Sleep")) setAnimation("Sleep", true);
            return;
        }

        if (currentAnim.equals("Sleep") || isPlayingOneShot()) return;

        if (!moving && currentAnim.equals("Relax")) {
            relaxTimer -= delta;
            if (relaxTimer <= 0 && MathUtils.random() < moveChance) {
                int[] bounds = WindowManager.getMovementBounds(windowW);
                targetScreenX = MathUtils.random(bounds[0], bounds[1]);
                moving = true;
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
    public void setMoveSpeed(float speed) { this.moveSpeed = speed * 120f; }
    public float getMoveSpeed() { return moveSpeed / 120f; }

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
    public void setInteractive(boolean on) { this.interactive = on; }
    public boolean isInteractive() { return interactive; }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.position.set(width / 2f, height / 2f, 0);
        camera.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
