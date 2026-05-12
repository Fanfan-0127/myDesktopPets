# 桌面宠物 (DesktopPet)

Windows 桌面宠物应用。在桌面上以透明置顶叠加层的形式渲染 Spine 3.8 骨骼动画，支持点击交互、拖拽移动、窗口吸附和自主行为。

## 功能

- **透明置顶** — 宠物浮于所有窗口之上，不遮挡鼠标操作
- **点击交互** — 点击宠物触发 Interact 动画；右键触发 Special 动画
- **拖拽移动** — 拖拽宠物跟随鼠标；释放时自动吸附到活动窗口边缘（上边缘坐姿、侧边缘站立）
- **自主行为** — 随机移动、发呆、睡眠，行为参数可在设置面板调节
- **系统托盘** — 托盘图标常驻，右键菜单支持显示/隐藏、切换模型、打开设置、退出
- **设置面板** — 10 个可调参数：模型、动画速度、移动速度、大小、特殊动画概率、移动频率、睡眠计时、休息间隔范围、交互开关
- **多模型支持** — 启动时自动检测 `models/` 目录，运行时热切换

## 运行

### 开发（需要 Java 21）

```
mvn compile
mvn package
java -jar target/desktoppet-1.0.jar
```

需在项目根目录下执行，以便访问 `models/` 目录。

### Windows Release（无需 Java）

```
package-windows.bat
```

或：

```
mvn package -P release
```

输出 `target/release/DesktopPet/` 目录，内含 `DesktopPet.exe` + 捆绑 JRE（~40MB）。可直接运行，无需安装 Java。分发时将该目录打包为 zip。

## 模型

将 Spine 3.8 模型放在 `models/<名称>/` 目录下，每个模型需包含：

- `*.atlas` — 纹理图集
- `*.png` — 纹理图片
- `*.skel` — 二进制骨骼文件

支持的动画名称（取决于模型）：`Default`、`Interact`、`Move`、`Relax`、`Sit`、`Sleep`、`Special`。

## 技术栈

Java 21 · libGDX 1.12.1 · LWJGL3 · Spine 3.8 · JNA 5.15.0 · Swing · AWT · Maven
