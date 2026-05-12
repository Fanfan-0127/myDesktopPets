package pet.ui;

import pet.Main;
import pet.PetCore;
import pet.model.ModelManager;
import javax.swing.*;
import java.awt.*;

public final class SettingsFrame extends JFrame {

    private final JComboBox<String> modelSelector;
    private final JSlider animSpeedSlider;
    private final JSlider moveSpeedSlider;
    private final JSlider sizeSlider;
    private final JSlider specialChanceSlider;
    private final JSlider moveFreqSlider;
    private final JSlider sleepTimerSlider;
    private final JSlider relaxMinSlider;
    private final JSlider relaxMaxSlider;
    private final JCheckBox interactiveCheck;
    private final JLabel animSpeedLabel;
    private final JLabel moveSpeedLabel;
    private final JLabel sizeLabel;
    private final JLabel specialLabel;
    private final JLabel moveFreqLabel;
    private final JLabel sleepLabel;
    private final JLabel relaxMinLabel;
    private final JLabel relaxMaxLabel;

    public SettingsFrame() {
        setTitle("桌面宠物设置");
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setResizable(false);
        setAlwaysOnTop(true);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 4, 3, 4);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;

        modelSelector = new JComboBox<>();
        for (String name : ModelManager.listModels()) modelSelector.addItem(name);

        animSpeedSlider = slider(50, 200, 100);
        moveSpeedSlider = slider(50, 200, 100);
        sizeSlider = slider(30, 150, 50);
        specialChanceSlider = slider(0, 100, 10);
        moveFreqSlider = slider(0, 100, 70);
        sleepTimerSlider = slider(10, 300, 120);
        relaxMinSlider = slider(1, 10, 2);
        relaxMaxSlider = slider(2, 30, 5);

        animSpeedLabel = label("动画速度:", animSpeedSlider, "%");
        moveSpeedLabel = label("移动速度:", moveSpeedSlider, "%");
        sizeLabel = label("宠物大小:", sizeSlider, "%");
        specialLabel = label("特殊动画概率:", specialChanceSlider, "%");
        moveFreqLabel = label("移动频率:", moveFreqSlider, "%");
        sleepLabel = label("睡眠计时:", sleepTimerSlider, "s");
        relaxMinLabel = label("休息最短:", relaxMinSlider, "s");
        relaxMaxLabel = label("休息最长:", relaxMaxSlider, "s");

        interactiveCheck = new JCheckBox("启用交互", true);

        addRow(panel, gbc, new JLabel("模型:"));
        gbc.gridy++;
        panel.add(modelSelector, gbc);

        gbc.gridy++;
        addRow(panel, gbc, animSpeedLabel);
        gbc.gridy++;
        panel.add(animSpeedSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, moveSpeedLabel);
        gbc.gridy++;
        panel.add(moveSpeedSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, sizeLabel);
        gbc.gridy++;
        panel.add(sizeSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, specialLabel);
        gbc.gridy++;
        panel.add(specialChanceSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, moveFreqLabel);
        gbc.gridy++;
        panel.add(moveFreqSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, sleepLabel);
        gbc.gridy++;
        panel.add(sleepTimerSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, relaxMinLabel);
        gbc.gridy++;
        panel.add(relaxMinSlider, gbc);

        gbc.gridy++;
        addRow(panel, gbc, relaxMaxLabel);
        gbc.gridy++;
        panel.add(relaxMaxSlider, gbc);

        gbc.gridy++;
        panel.add(interactiveCheck, gbc);

        animSpeedSlider.addChangeListener(e -> animSpeedLabel.setText("动画速度: " + animSpeedSlider.getValue() + "%"));
        moveSpeedSlider.addChangeListener(e -> moveSpeedLabel.setText("移动速度: " + moveSpeedSlider.getValue() + "%"));
        sizeSlider.addChangeListener(e -> sizeLabel.setText("宠物大小: " + sizeSlider.getValue() + "%"));
        specialChanceSlider.addChangeListener(e -> specialLabel.setText("特殊动画概率: " + specialChanceSlider.getValue() + "%"));
        moveFreqSlider.addChangeListener(e -> moveFreqLabel.setText("移动频率: " + moveFreqSlider.getValue() + "%"));
        sleepTimerSlider.addChangeListener(e -> sleepLabel.setText("睡眠计时: " + sleepTimerSlider.getValue() + "s"));
        relaxMinSlider.addChangeListener(e -> relaxMinLabel.setText("休息最短: " + relaxMinSlider.getValue() + "s"));
        relaxMaxSlider.addChangeListener(e -> relaxMaxLabel.setText("休息最长: " + relaxMaxSlider.getValue() + "s"));

        JButton applyBtn = new JButton("应用");
        applyBtn.addActionListener(e -> applySettings());

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        panel.add(applyBtn, gbc);

        add(panel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(null);
    }

    private static void addRow(JPanel p, GridBagConstraints gbc, JComponent comp) {
        gbc.gridwidth = 2;
        p.add(comp, gbc);
    }

    private static JLabel label(String text, JSlider slider, String unit) {
        return new JLabel(text + " " + slider.getValue() + unit);
    }

    private static JSlider slider(int min, int max, int val) {
        JSlider s = new JSlider(min, max, val);
        s.setMajorTickSpacing(50);
        s.setMinorTickSpacing(10);
        s.setPaintTicks(true);
        return s;
    }

    private void applySettings() {
        PetCore pet = Main.getPetCore();
        if (pet == null) return;

        String selectedModel = (String) modelSelector.getSelectedItem();
        if (selectedModel != null) pet.switchModel(selectedModel);

        pet.setAnimSpeed(animSpeedSlider.getValue() / 100f);
        pet.setMoveSpeed(moveSpeedSlider.getValue() / 100f);
        pet.setPetScale(sizeSlider.getValue() / 100f);
        pet.setSpecialChance(specialChanceSlider.getValue());
        pet.setMoveFrequency(moveFreqSlider.getValue());
        pet.setSleepTimeout(sleepTimerSlider.getValue());
        pet.setRelaxMin(relaxMinSlider.getValue());
        pet.setRelaxMax(relaxMaxSlider.getValue());
        pet.setInteractive(interactiveCheck.isSelected());
    }

    public void loadCurrentValues() {
        PetCore pet = Main.getPetCore();
        if (pet == null) return;
        animSpeedSlider.setValue((int) (pet.getAnimSpeed() * 100));
        moveSpeedSlider.setValue((int) (pet.getMoveSpeed() * 100));
        sizeSlider.setValue((int) (pet.getPetScale() * 100));
        specialChanceSlider.setValue((int) pet.getSpecialChance());
        moveFreqSlider.setValue((int) pet.getMoveFrequency());
        sleepTimerSlider.setValue((int) pet.getSleepTimeout());
        relaxMinSlider.setValue((int) pet.getRelaxMin());
        relaxMaxSlider.setValue((int) pet.getRelaxMax());
        interactiveCheck.setSelected(pet.isInteractive());
    }

    public void refreshModelList() {
        modelSelector.removeAllItems();
        for (String name : ModelManager.listModels()) modelSelector.addItem(name);
    }
}
