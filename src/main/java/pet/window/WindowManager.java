package pet.window;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

public final class WindowManager {

    private static WinDef.HWND petHwnd;
    private static boolean initialized;
    private static boolean visible = true;

    private WindowManager() {}

    public static void init(String windowTitle) {
        for (int i = 0; i < 100; i++) {
            petHwnd = User32.INSTANCE.FindWindow(null, windowTitle);
            if (petHwnd != null) break;
            try { Thread.sleep(50); } catch (InterruptedException e) { break; }
        }
        if (petHwnd == null) return;

        int exStyle = User32.INSTANCE.GetWindowLong(petHwnd, WinUser.GWL_EXSTYLE);
        exStyle |= 0x8;
        User32.INSTANCE.SetWindowLong(petHwnd, WinUser.GWL_EXSTYLE, exStyle);
        initialized = true;
    }

    public static void moveWindow(int x, int y, int width, int height) {
        if (!initialized) return;
        User32.INSTANCE.SetWindowPos(petHwnd, null, x, y, width, height,
            WinUser.SWP_NOZORDER | WinUser.SWP_NOACTIVATE);
    }

    public static WinDef.RECT getForegroundWindowRect() {
        WinDef.HWND fg = User32.INSTANCE.GetForegroundWindow();
        if (fg == null || fg.equals(petHwnd)) return null;
        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(fg, rect)) return null;
        return rect;
    }

    public static int getTaskbarTop() {
        WinDef.HWND taskbar = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (taskbar == null) return 0;
        WinDef.RECT rect = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(taskbar, rect);
        return rect.top;
    }

    public static int[] getMovementBounds(int petW) {
        int screenW = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
        int minX = 0;
        int maxX = screenW - petW;
        WinDef.RECT fgRect = getForegroundWindowRect();
        if (fgRect != null) {
            minX = fgRect.left;
            maxX = Math.max(minX + 10, fgRect.right - petW);
        }
        if (maxX <= minX) {
            maxX = Math.max(minX + 10, screenW - petW);
        }
        return new int[] { minX, maxX };
    }

    public static SnapResult snapToNearestWindow(int petX, int petY, int petW, int petH) {
        WinDef.RECT rect = getForegroundWindowRect();
        if (rect == null) return null;

        int wndLeft = rect.left;
        int wndRight = rect.right;
        int wndTop = rect.top;
        int wndBottom = rect.bottom;

        int petCenterX = petX + petW / 2;
        if (petCenterX < wndLeft - 50 || petCenterX > wndRight + 50) return null;

        int distToTop = Math.abs(petY - wndTop);
        int distToBottom = Math.abs(petY + petH - wndBottom);

        int snapY;
        boolean snapToTop;
        if (distToTop < distToBottom) {
            snapY = wndTop - petH;
            snapToTop = true;
        } else {
            snapY = wndBottom;
            snapToTop = false;
        }

        int clampedX = Math.max(wndLeft, Math.min(Math.max(wndRight - petW, wndLeft), petX));
        return new SnapResult(clampedX, snapY, snapToTop);
    }

    public static void toggleVisibility() {
        if (!initialized) return;
        visible = !visible;
        User32.INSTANCE.ShowWindow(petHwnd, visible ? WinUser.SW_SHOW : WinUser.SW_HIDE);
    }

    public record SnapResult(int x, int y, boolean topEdge) {}
}
