package app.buddy.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * Local addition, not upstream: Buddy as the phone's home screen (the "launcher" build type's HomeActivity, see
 * docs/HOME_LAUNCHER.md). The home screen is in Buddy's own package, but to the agent and the bubble it must be an
 * ordinary app, like any other launcher: agents may read and tap it (with the usual approvals), opening it folds the
 * panel, and touching it pauses a working agent. Buddy's real screens stay off-limits as upstream has them.
 * <p>
 * HomeActivity's root view carries the id "buddy_home"; Buddy's accessibility service reports view ids
 * (flagReportViewIds), so a window holding that id is the home screen. In builds without HomeActivity nothing matches,
 * and every check below gives upstream's answer.
 */
final class Home {
    private Home() {
    }

    private static String rootId(Context c) {
        return c.getPackageName() + ":id/buddy_home";
    }

    /** Is this window root Buddy's home screen? */
    static boolean isHomeRoot(Context c, AccessibilityNodeInfo root) {
        if (c == null || root == null || root.getPackageName() == null
                || !c.getPackageName().contentEquals(root.getPackageName())) {
            return false;
        }
        try {
            return !root.findAccessibilityNodeInfosByViewId(rootId(c)).isEmpty();
        } catch (RuntimeException e) { // the window went away meanwhile
            return false;
        }
    }

    /** Is this node in Buddy's home screen? */
    static boolean contains(Context c, AccessibilityNodeInfo n) {
        if (c == null || n == null) return false;
        AccessibilityWindowInfo w = n.getWindow();
        return w != null && isHomeRoot(c, w.getRoot());
    }

    /**
     * Is this window root Buddy's own UI (its screens, the bubble, its cards)? What upstream asks with
     * getPackageName().contentEquals(root.getPackageName()), except that the home screen is not.
     */
    static boolean ownUi(Context c, AccessibilityNodeInfo root) {
        return root != null && c.getPackageName().contentEquals(String.valueOf(root.getPackageName()))
                && !isHomeRoot(c, root);
    }

    /** Is Buddy's home screen the active app window? */
    static boolean inFront(AccessibilityService svc) {
        try {
            for (AccessibilityWindowInfo w : svc.getWindows()) {
                if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !w.isActive()) continue;
                return isHomeRoot(svc, w.getRoot());
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }
}
