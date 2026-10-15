package com.example.myhtmlapp;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_PROJECT = 301;
    private WebView webView;
    private LinearLayout toolbar;
    private File projectDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableImmersiveMode();
        projectDir = new File(getFilesDir(), "project");
        if (!projectDir.exists()) projectDir.mkdirs();
        buildUi();
        File index = new File(projectDir, "index.html");
        if (index.exists()) loadProject();
        else showWelcomePage();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101827);

        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setPadding(dp(12), dp(10), dp(12), dp(10));
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("My HTML App");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        Button importButton = new Button(this);
        importButton.setText("Import HTML / ZIP");
        importButton.setOnClickListener(v -> chooseProject());
        toolbar.addView(importButton);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFFFFFFFF);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showWelcomePage() {
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:system-ui;background:#f1f5f9;color:#172033;padding:28px;line-height:1.5}" +
                ".card{max-width:540px;margin:10vh auto;background:white;border-radius:22px;padding:25px;box-shadow:0 8px 30px #0001}" +
                "button{background:#1769aa;color:white;border:0;border-radius:12px;padding:14px 18px;font-size:16px}</style></head>" +
                "<body><div class='card'><h1>My HTML App</h1><p>Import an HTML file or a ZIP containing your website project.</p>" +
                "<p>For a complete project, ZIP <b>index.html</b> together with its CSS, JavaScript, images and other assets.</p>" +
                "<p>Tap <b>Import HTML / ZIP</b> in the top bar to begin.</p></div></body></html>";
        webView.loadDataWithBaseURL("https://local.invalid/", html, "text/html", "UTF-8", null);
    }

    private void chooseProject() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/html", "application/zip", "application/x-zip-compressed", "application/octet-stream"});
        startActivityForResult(intent, PICK_PROJECT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PROJECT || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = queryDisplayName(uri);
        String lower = name.toLowerCase(Locale.ROOT);
        try {
            clearDirectory(projectDir);
            if (lower.endsWith(".zip")) {
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new Exception("Could not open selected ZIP file.");
                    unzipSafely(input, projectDir);
                }
                File index = findIndex(projectDir);
                if (index == null) throw new Exception("ZIP must contain index.html at its root or one folder below.");
                if (!index.getParentFile().equals(projectDir)) {
                    File source = index.getParentFile();
                    File[] children = source.listFiles();
                    if (children != null) {
                        for (File child : children) {
                            File destination = new File(projectDir, child.getName());
                            if (!child.equals(index)) copyRecursively(child, destination);
                        }
                    }
                    File rootIndex = new File(projectDir, "index.html");
                    copyFile(index, rootIndex);
                    // Keep sibling assets in the same folder as index.html.
                    File[] siblings = source.listFiles();
                    if (siblings != null) for (File child : siblings) {
                        if (!child.equals(index)) copyRecursively(child, new File(projectDir, child.getName()));
                    }
                }
            } else if (lower.endsWith(".html") || lower.endsWith(".htm")) {
                try (InputStream input = getContentResolver().openInputStream(uri);
                     OutputStream output = new FileOutputStream(new File(projectDir, "index.html"))) {
                    if (input == null) throw new Exception("Could not open selected HTML file.");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            } else {
                throw new Exception("Please select an .html or .zip file.");
            }
            loadProject();
            Toast.makeText(this, "Project imported", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            showWelcomePage();
        }
    }

    private String queryDisplayName(Uri uri) {
        // Ask the file provider for the real file name (e.g. "site.zip").
        try (android.database.Cursor cursor = getContentResolver().query(uri,
                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String displayName = cursor.getString(0);
                if (displayName != null && !displayName.isEmpty()) return displayName;
            }
        } catch (Exception ignored) {
        }
        // Fall back to the MIME type when the name is unavailable.
        String type = getContentResolver().getType(uri);
        if (type != null && type.contains("zip")) return "project.zip";
        if (type != null && type.contains("html")) return "project.html";
        String path = uri.getLastPathSegment();
        if (path == null) return "project.html";
        int colon = path.lastIndexOf(':');
        if (colon >= 0 && colon < path.length() - 1) path = path.substring(colon + 1);
        return path;
    }

    private void unzipSafely(InputStream input, File destination) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                    zip.closeEntry();
                    continue;
                }
                File out = new File(destination, name);
                String base = destination.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(base)) {
                    zip.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) out.mkdirs();
                else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (FileOutputStream output = new FileOutputStream(out)) {
                        int count;
                        while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private File findIndex(File dir) {
        File direct = new File(dir, "index.html");
        if (direct.isFile()) return direct;
        File[] children = dir.listFiles();
        if (children != null) for (File child : children) {
            if (child.isDirectory()) {
                File candidate = new File(child, "index.html");
                if (candidate.isFile()) return candidate;
            }
        }
        return null;
    }

    private void copyRecursively(File source, File target) throws Exception {
        if (source.isDirectory()) {
            if (!target.exists()) target.mkdirs();
            File[] children = source.listFiles();
            if (children != null) for (File child : children)
                copyRecursively(child, new File(target, child.getName()));
        } else copyFile(source, target);
    }

    private void copyFile(File source, File target) throws Exception {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        try (InputStream input = new java.io.FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void clearDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
    }

    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private void loadProject() {
        File index = new File(projectDir, "index.html");
        if (index.exists()) webView.loadUrl(Uri.fromFile(index).toString());
        else showWelcomePage();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void enableImmersiveMode() {
        Window window = getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
