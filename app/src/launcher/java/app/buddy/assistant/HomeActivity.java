package app.buddy.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Buddy as the home screen ("launcher" build type only): every app, with search, and a button into Buddy.
 * Kept in src/launcher so pulling the upstream project never conflicts with it. Its root view's id (buddy_home) is
 * how Buddy's own code (Home.java) treats this screen as an ordinary app rather than one of Buddy's screens.
 * Apps come from LauncherApps, so a work profile's apps show too (with their badge).
 */
public class HomeActivity extends Activity {
    private static final class App {
        final LauncherActivityInfo info;
        final String label;
        Drawable icon; // loaded when first shown: badged icons are slow

        App(LauncherActivityInfo info) {
            this.info = info;
            this.label = String.valueOf(info.getLabel());
        }
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    /** Icons kept across refreshes, by component and profile. */
    private final Map<String, Drawable> icons = new HashMap<>();
    private LauncherApps apps;
    private Adapter adapter;
    private EditText search;
    private float d;

    private final LauncherApps.Callback changes = new LauncherApps.Callback() {
        @Override
        public void onPackageRemoved(String pkg, UserHandle user) { reload(); }

        @Override
        public void onPackageAdded(String pkg, UserHandle user) { reload(); }

        @Override
        public void onPackageChanged(String pkg, UserHandle user) {
            icons.clear(); // an app's icon may be what changed
            reload();
        }

        @Override
        public void onPackagesAvailable(String[] pkgs, UserHandle user, boolean replacing) { reload(); }

        @Override
        public void onPackagesUnavailable(String[] pkgs, UserHandle user, boolean replacing) { reload(); }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        apps = getSystemService(LauncherApps.class);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setId(R.id.buddy_home);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(Color.argb(90, 0, 0, 0));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(px(16), px(8), px(16), px(8));
        search = new EditText(this);
        search.setHint(R.string.home_search_hint);
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { filter(); }
        });
        bar.addView(search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView buddy = new TextView(this);
        buddy.setText(R.string.home_open_buddy);
        buddy.setTextColor(Color.WHITE);
        buddy.setTextSize(16);
        buddy.setPadding(px(16), px(8), px(8), px(8));
        buddy.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        bar.addView(buddy);
        root.addView(bar);

        GridView grid = new GridView(this);
        grid.setNumColumns(4);
        grid.setVerticalSpacing(px(12));
        grid.setPadding(px(8), 0, px(8), 0);
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((p, v, i, id) -> launch(shown.get(i), v));
        root.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        // The manifest turns on predictive back (enableOnBackInvokedCallback), so on Android 13+ onBackPressed is
        // never called and back would close the home screen: swallow it here instead.
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> { });
        }
        apps.registerCallback(changes);
        reload();
    }

    @Override
    protected void onDestroy() {
        apps.unregisterCallback(changes);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: clear the search
        search.setText("");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // a home screen has nowhere to go back to (before Android 13; see onCreate for later)
    }

    private void reload() {
        all.clear();
        UserHandle me = Process.myUserHandle();
        for (UserHandle user : apps.getProfiles()) {
            List<LauncherActivityInfo> list;
            try {
                list = apps.getActivityList(null, user);
            } catch (SecurityException | IllegalStateException e) { // a profile that's locked or going away
                continue;
            }
            for (LauncherActivityInfo info : list) {
                if (user.equals(me) && info.getComponentName().getPackageName().equals(getPackageName())) continue;
                all.add(new App(info));
            }
        }
        Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        filter();
    }

    private void filter() {
        String needle = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (App a : all) if (needle.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(needle)) shown.add(a);
        adapter.notifyDataSetChanged();
    }

    private void launch(App a, View from) {
        try {
            apps.startMainActivity(a.info.getComponentName(), a.info.getUser(), null, null);
        } catch (RuntimeException e) { // uninstalled meanwhile, or its profile is paused
            reload();
        }
    }

    private Drawable icon(App a) {
        if (a.icon == null) {
            String key = a.info.getComponentName().flattenToShortString() + "/" + a.info.getUser().hashCode();
            a.icon = icons.get(key);
            if (a.icon == null) {
                a.icon = a.info.getBadgedIcon(0);
                icons.put(key, a.icon);
            }
        }
        return a.icon;
    }

    private int px(int dp) {
        return (int) (dp * d);
    }

    private class Adapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View convert, ViewGroup parent) {
            LinearLayout cell;
            ImageView icon;
            TextView label;
            if (convert == null) {
                Context c = HomeActivity.this;
                cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                icon = new ImageView(c);
                cell.addView(icon, new LinearLayout.LayoutParams(px(48), px(48)));
                label = new TextView(c);
                label.setTextColor(Color.WHITE);
                label.setTextSize(12);
                label.setSingleLine(true);
                label.setGravity(Gravity.CENTER);
                label.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(label);
            } else {
                cell = (LinearLayout) convert;
                icon = (ImageView) cell.getChildAt(0);
                label = (TextView) cell.getChildAt(1);
            }
            App a = shown.get(i);
            icon.setImageDrawable(icon(a));
            label.setText(a.label);
            return cell;
        }
    }
}
