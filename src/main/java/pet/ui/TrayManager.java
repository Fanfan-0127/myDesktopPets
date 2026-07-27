package pet.ui;

import pet.Main;
import pet.PetCore;
import pet.model.ModelManager;
import pet.window.WindowManager;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class TrayManager {

    private static final String MENU_GLYPHS = "显示隐藏宠物选择模型设置退出";
    private static final String[] MENU_FONT_FAMILIES = {
        "Microsoft YaHei UI",
        "Microsoft YaHei",
        "Microsoft JhengHei UI",
        "SimSun",
        Font.DIALOG
    };
    private static final Font MENU_FONT = resolveMenuFont();

    private static SettingsFrame settingsFrame;

    private TrayManager() {}

    public static void install() {
        if (!SystemTray.isSupported()) return;

        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(100, 180, 255));
        g.fillOval(0, 0, 16, 16);
        g.setColor(new Color(50, 130, 220));
        g.fillOval(2, 2, 4, 4);
        g.fillOval(10, 2, 4, 4);
        g.fillArc(3, 5, 10, 8, 0, -180);
        g.dispose();

        TrayIcon trayIcon = new TrayIcon(img, "桌面宠物", createMenu());
        trayIcon.setImageAutoSize(true);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }

    private static PopupMenu createMenu() {
        PopupMenu menu = new PopupMenu();
        menu.setFont(MENU_FONT);

        MenuItem showHide = new MenuItem("显示/隐藏宠物");
        showHide.setFont(MENU_FONT);
        showHide.addActionListener(TrayManager::toggleVisibility);
        menu.add(showHide);

        menu.addSeparator();

        Menu modelMenu = new Menu("选择模型");
        modelMenu.setFont(MENU_FONT);
        for (String name : ModelManager.listModels()) {
            MenuItem item = new MenuItem(name);
            item.setFont(MENU_FONT);
            item.addActionListener(e -> switchModel(name));
            modelMenu.add(item);
        }
        menu.add(modelMenu);

        MenuItem settings = new MenuItem("设置...");
        settings.setFont(MENU_FONT);
        settings.addActionListener(e -> openSettings());
        menu.add(settings);

        menu.addSeparator();

        MenuItem exit = new MenuItem("退出");
        exit.setFont(MENU_FONT);
        exit.addActionListener(e -> System.exit(0));
        menu.add(exit);

        return menu;
    }

    private static Font resolveMenuFont() {
        for (String family : MENU_FONT_FAMILIES) {
            Font font = new Font(family, Font.PLAIN, 12);
            if (font.canDisplayUpTo(MENU_GLYPHS) == -1) {
                return font;
            }
        }
        return new Font(Font.DIALOG, Font.PLAIN, 12);
    }

    private static void toggleVisibility(java.awt.event.ActionEvent e) {
        WindowManager.toggleVisibility();
    }

    private static void switchModel(String name) {
        PetCore pet = Main.getPetCore();
        if (pet != null) pet.switchModel(name);
    }

    private static void openSettings() {
        if (settingsFrame == null) {
            settingsFrame = new SettingsFrame();
        }
        settingsFrame.refreshModelList();
        settingsFrame.loadCurrentValues();
        settingsFrame.setVisible(true);
        settingsFrame.toFront();
    }

    public static void showSettings() {
        SwingUtilities.invokeLater(TrayManager::openSettings);
    }
}
