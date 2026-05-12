package pet;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import pet.ui.TrayManager;

public class Main {

    private static PetCore petCore;

    public static PetCore getPetCore() { return petCore; }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();

        config.setTitle("DesktopPet");
        config.setWindowedMode(300, 300);
        config.setTransparentFramebuffer(true);
        config.setDecorated(false);
        config.setResizable(false);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setBackBufferConfig(8, 8, 8, 8, 0, 0, 0);

        petCore = new PetCore();

        TrayManager.install();
        TrayManager.showSettings();

        new Lwjgl3Application(petCore, config);
    }
}
