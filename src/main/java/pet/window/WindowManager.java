package pet.window;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import java.util.ArrayList;
import java.util.List;

public final class WindowManager {

    private static final int WS_EX_TRANSPARENT = 0x20;
    private static final int WS_EX_TOPMOST = 0x08;
    private static final int WS_EX_TOOLWINDOW = 0x00000080;
    private static final int DWMWA_CLOAKED = 14;
    private static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;
    private static final int SNAP_DISTANCE = 36;
    private static final int HORIZONTAL_SNAP_GAP = 18;
    private static final int MIN_WINDOW_SIZE = 120;
    private static final int GA_ROOT = 2;
    private static final WinDef.HWND HWND_TOPMOST = new WinDef.HWND(Pointer.createConstant(-1));
    private static final WinDef.HWND HWND_NOTOPMOST = new WinDef.HWND(Pointer.createConstant(-2));

    private static final Dwmapi DWMAPI =
        Native.load("dwmapi", Dwmapi.class);

    private static WinDef.HWND petHwnd;
    private static boolean initialized;
    private static boolean visible = true;

    private WindowManager() {}

    public static void init(String windowTitle) {
        for (int i = 0; i < 100; i++) {
            petHwnd = User32.INSTANCE.FindWindow(null, windowTitle);
            if (petHwnd != null) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (petHwnd == null) {
            return;
        }

        int exStyle = User32.INSTANCE.GetWindowLong(petHwnd, WinUser.GWL_EXSTYLE);
        exStyle |= WS_EX_TRANSPARENT | WS_EX_TOPMOST;
        User32.INSTANCE.SetWindowLong(petHwnd, WinUser.GWL_EXSTYLE, exStyle);
        User32.INSTANCE.SetWindowPos(
            petHwnd,
            HWND_TOPMOST,
            0, 0, 0, 0,
            WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE | WinUser.SWP_FRAMECHANGED
        );
        initialized = true;
    }

    public static void moveWindow(int x, int y, int width, int height) {
        if (!initialized) {
            return;
        }
        User32.INSTANCE.SetWindowPos(
            petHwnd,
            null,
            x,
            y,
            width,
            height,
            WinUser.SWP_NOZORDER | WinUser.SWP_NOACTIVATE
        );
    }

    public static int getTaskbarTop() {
        WinDef.HWND taskbar = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (taskbar == null) {
            return 0;
        }
        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(taskbar, rect)) {
            return 0;
        }
        return rect.top;
    }

    public static int[] getMovementBounds(int petW) {
        int screenW = User32.INSTANCE.GetSystemMetrics(WinUser.SM_CXSCREEN);
        return new int[] { 0, Math.max(0, screenW - petW) };
    }

    public static SnapResult snapToNearestWindow(int petX, int petY, int petW, int petH) {
        List<WindowCandidate> candidates = listWindowCandidates();
        WindowCandidate taskbar = getTaskbarCandidate();
        if (taskbar != null) {
            candidates.add(taskbar);
        }
        if (candidates.isEmpty()) {
            return null;
        }

        SnapCandidate best = null;
        for (WindowCandidate candidate : candidates) {
            SnapCandidate current = buildSnapCandidate(candidate, petX, petY, petW, petH);
            if (current == null) {
                continue;
            }
            if (best == null || current.distance() < best.distance()) {
                best = current;
            }
        }

        if (best == null || best.distance() > SNAP_DISTANCE) {
            return null;
        }
        return new SnapResult(best.x(), best.y(), best.left(), best.right());
    }

    public static void toggleVisibility() {
        if (!initialized) {
            return;
        }
        visible = !visible;
        User32.INSTANCE.ShowWindow(petHwnd, visible ? WinUser.SW_SHOW : WinUser.SW_HIDE);
    }

    public static void setAlwaysOnTop(boolean on) {
        if (!initialized) {
            return;
        }
        int exStyle = User32.INSTANCE.GetWindowLong(petHwnd, WinUser.GWL_EXSTYLE);
        if (on) {
            exStyle |= WS_EX_TOPMOST;
        } else {
            exStyle &= ~WS_EX_TOPMOST;
        }
        User32.INSTANCE.SetWindowLong(petHwnd, WinUser.GWL_EXSTYLE, exStyle);
        WinDef.HWND insertAfter = on ? HWND_TOPMOST : HWND_NOTOPMOST;
        User32.INSTANCE.SetWindowPos(
            petHwnd,
            insertAfter,
            0, 0, 0, 0,
            WinUser.SWP_NOMOVE | WinUser.SWP_NOSIZE | WinUser.SWP_NOACTIVATE | WinUser.SWP_FRAMECHANGED
        );
    }

    private static SnapCandidate buildSnapCandidate(
        WindowCandidate candidate,
        int petX,
        int petY,
        int petW,
        int petH
    ) {
        WinDef.RECT rect = candidate.rect;
        int petLeft = petX;
        int petRight = petX + petW;
        boolean overlapsHorizontally = petRight >= rect.left && petLeft <= rect.right;
        int horizontalGap = 0;
        if (!overlapsHorizontally) {
            horizontalGap = petRight < rect.left
                ? rect.left - petRight
                : petLeft - rect.right;
        }
        if (!overlapsHorizontally && horizontalGap > HORIZONTAL_SNAP_GAP) {
            return null;
        }

        int petBottom = petY + petH;
        int snapTopY = rect.top;
        int distToTop = Math.abs(petBottom - snapTopY);
        int snapBottom = snapTopY;
        int distance = distToTop;

        if (candidate.allowBottomEdge()) {
            int snapBottomY = rect.bottom;
            int distToBottom = Math.abs(petBottom - snapBottomY);
            if (distToBottom < distance) {
                snapBottom = snapBottomY;
                distance = distToBottom;
            }
        }

        int minX = rect.left;
        int maxX = Math.max(rect.left, rect.right - petW);
        int snapX = clamp(petX, minX, maxX);

        return new SnapCandidate(snapX, snapBottom, distance, rect.left, rect.right);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<WindowCandidate> listWindowCandidates() {
        List<WindowCandidate> windows = new ArrayList<>();
        User32.INSTANCE.EnumWindows((hwnd, data) -> {
            WindowCandidate candidate = toCandidate(hwnd);
            if (candidate != null) {
                windows.add(candidate);
            }
            return true;
        }, null);
        return windows;
    }

    private static WindowCandidate getTaskbarCandidate() {
        WinDef.HWND taskbar = User32.INSTANCE.FindWindow("Shell_TrayWnd", null);
        if (taskbar == null) {
            return null;
        }
        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(taskbar, rect)) {
            return null;
        }
        return new WindowCandidate(taskbar, copyRect(rect), "Taskbar", false);
    }

    private static WindowCandidate toCandidate(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return null;
        }
        if (petHwnd != null && hwnd.equals(petHwnd)) {
            return null;
        }
        if (!User32.INSTANCE.IsWindowVisible(hwnd)) {
            return null;
        }
        if (isMinimized(hwnd)) {
            return null;
        }
        if (!hwnd.equals(User32.INSTANCE.GetAncestor(hwnd, GA_ROOT))) {
            return null;
        }
        if (isToolWindow(hwnd) || isCloaked(hwnd)) {
            return null;
        }

        WinDef.RECT rect = getVisibleWindowRect(hwnd);
        if (rect == null) {
            return null;
        }

        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;
        if (width < MIN_WINDOW_SIZE || height < MIN_WINDOW_SIZE) {
            return null;
        }

        String title = readWindowText(hwnd);
        if (title.isBlank()) {
            return null;
        }

        return new WindowCandidate(hwnd, copyRect(rect), title, true);
    }

    private static boolean isToolWindow(WinDef.HWND hwnd) {
        int exStyle = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE);
        return (exStyle & WS_EX_TOOLWINDOW) != 0;
    }

    private static boolean isMinimized(WinDef.HWND hwnd) {
        WinUser.WINDOWPLACEMENT placement = new WinUser.WINDOWPLACEMENT();
        if (!User32.INSTANCE.GetWindowPlacement(hwnd, placement).booleanValue()) {
            return false;
        }
        return placement.showCmd == WinUser.SW_SHOWMINIMIZED;
    }

    private static boolean isCloaked(WinDef.HWND hwnd) {
        try {
            int[] cloaked = new int[1];
            int result = DWMAPI.DwmGetWindowAttribute(
                hwnd,
                DWMWA_CLOAKED,
                cloaked,
                Integer.BYTES
            );
            return result == 0 && cloaked[0] != 0;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static String readWindowText(WinDef.HWND hwnd) {
        char[] buffer = new char[512];
        int len = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        if (len <= 0) {
            return "";
        }
        return Native.toString(buffer).trim();
    }

    private static WinDef.RECT getVisibleWindowRect(WinDef.HWND hwnd) {
        try {
            WinDef.RECT rect = new WinDef.RECT();
            int result = DWMAPI.DwmGetWindowAttribute(
                hwnd,
                DWMWA_EXTENDED_FRAME_BOUNDS,
                rect,
                rect.size()
            );
            if (result == 0) {
                return rect;
            }
        } catch (UnsatisfiedLinkError e) {
            // Fall through to GetWindowRect.
        }

        WinDef.RECT rect = new WinDef.RECT();
        if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) {
            return null;
        }
        return rect;
    }

    private static WinDef.RECT copyRect(WinDef.RECT source) {
        WinDef.RECT copy = new WinDef.RECT();
        copy.left = source.left;
        copy.top = source.top;
        copy.right = source.right;
        copy.bottom = source.bottom;
        return copy;
    }

    private interface Dwmapi extends com.sun.jna.Library {
        int DwmGetWindowAttribute(
            WinDef.HWND hwnd,
            int dwAttribute,
            int[] pvAttribute,
            int cbAttribute
        );

        int DwmGetWindowAttribute(
            WinDef.HWND hwnd,
            int dwAttribute,
            WinDef.RECT pvAttribute,
            int cbAttribute
        );
    }

    private record WindowCandidate(
        WinDef.HWND hwnd,
        WinDef.RECT rect,
        String title,
        boolean allowBottomEdge
    ) {}

    private record SnapCandidate(
        int x,
        int y,
        int distance,
        int left,
        int right
    ) {}

    public record SnapResult(int x, int bottom, int left, int right) {}
}
