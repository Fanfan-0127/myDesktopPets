package pet.ui;

import pet.Main;
import pet.PetCore;
import pet.model.ModelManager;
import pet.window.WindowManager;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public final class TrayManager {

    private static SettingsFrame settingsFrame;

    private static final Font MENU_FONT = new Font("Microsoft YaHei", Font.PLAIN, 12);

    private TrayManager() {}

    private static void setFont(MenuItem item) {
        item.setFont(MENU_FONT);
    }

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

        MenuItem showHide = new MenuItem("显示/隐藏宠物");
        setFont(showHide);
        showHide.addActionListener(TrayManager::toggleVisibility);
        menu.add(showHide);

        menu.addSeparator();

        Menu modelMenu = new Menu("选择模型");
        setFont(modelMenu);
        for (String name : ModelManager.listModels()) {
            MenuItem item = new MenuItem(name);
            setFont(item);
            item.addActionListener(e -> switchModel(name));
            modelMenu.add(item);
        }
        menu.add(modelMenu);

        MenuItem settings = new MenuItem("设置...");
        setFont(settings);
        settings.addActionListener(e -> openSettings());
        menu.add(settings);

        menu.addSeparator();

        MenuItem exit = new MenuItem("退出");
        setFont(exit);
        exit.addActionListener(e -> System.exit(0));
        menu.add(exit);

        return menu;
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
