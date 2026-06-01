package pet.model;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModelManager {

    private static final File MODELS_DIR = new File("models");

    private ModelManager() {}

    public static List<String> listModels() {
        if (!MODELS_DIR.isDirectory()) return Collections.emptyList();

        File[] subdirs = MODELS_DIR.listFiles(File::isDirectory);
        if (subdirs == null) return Collections.emptyList();

        List<String> models = new ArrayList<>();
        for (File subdir : subdirs) {
            File[] atlasFiles = subdir.listFiles((d, n) -> n.endsWith(".atlas"));
            File[] skelFiles = subdir.listFiles((d, n) -> n.endsWith(".skel"));
            if (atlasFiles != null && atlasFiles.length > 0
                && skelFiles != null && skelFiles.length > 0) {
                models.add(subdir.getName());
            }
        }
        Collections.sort(models);
        return models;
    }

    public static ModelFiles getModelFiles(String modelName) {
        FileHandle modelDir = Gdx.files.local("models/" + modelName);
        if (!modelDir.isDirectory()) return null;

        FileHandle[] atlasFiles = modelDir.list((d, n) -> n.endsWith(".atlas"));
        FileHandle[] skelFiles = modelDir.list((d, n) -> n.endsWith(".skel"));
        FileHandle[] pngFiles = modelDir.list((d, n) -> n.endsWith(".png"));
        FileHandle voiceDir = modelDir.child("voice");

        if (atlasFiles.length == 0 || skelFiles.length == 0) return null;
        return new ModelFiles(atlasFiles[0], skelFiles[0],
            pngFiles.length > 0 ? pngFiles : new FileHandle[0],
            voiceDir.isDirectory() ? voiceDir : null);
    }

    public record ModelFiles(FileHandle atlas, FileHandle skel, FileHandle[] pngs, FileHandle voiceDir) {}
}
