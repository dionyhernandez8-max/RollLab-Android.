package com.rolllab.simple;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.view.View;
import android.view.WindowInsets;
import android.os.Build;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private static final int IMPORT = 10, EXPORT = 11;
    private WebView web;
    private android.webkit.ValueCallback<Uri[]> chooser;
    private String exportData, exportName;
    private final String origin = "https://appassets.androidplatform.net/rolllab/index.html";

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(0xff0d1422);
        web.setOnApplyWindowInsetsListener((v, i) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());
            }
            return i;
        });
        setContentView(web);
        WebSettings cfg = web.getSettings();
        cfg.setJavaScriptEnabled(true);
        cfg.setDomStorageEnabled(true);
        cfg.setAllowFileAccess(false);
        cfg.setAllowContentAccess(true);
        cfg.setAllowFileAccessFromFileURLs(false);
        cfg.setAllowUniversalAccessFromFileURLs(false);
        cfg.setBlockNetworkLoads(true);
        web.addJavascriptInterface(new NativeBridge(), "RollLabNative");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !origin.equals(request.getUrl().toString());
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView w, android.webkit.ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (chooser != null) chooser.onReceiveValue(null);
                chooser = cb;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                try { startActivityForResult(i, IMPORT); }
                catch (Exception ex) { chooser.onReceiveValue(null); chooser=null; notice("File picker unavailable"); }
                return true;
            }
            @Override public boolean onJsConfirm(WebView w, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                    .setPositiveButton("OK", (d, n) -> result.confirm())
                    .setNegativeButton("Cancel", (d, n) -> result.cancel())
                    .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }
        });
        try (InputStream in = getAssets().open("index.html")) {
            byte[] data = new byte[in.available()];
            int n = 0, part;
            while (n < data.length && (part = in.read(data, n, data.length-n)) > 0) n += part;
            web.loadDataWithBaseURL(origin, new String(data, 0, n, StandardCharsets.UTF_8), "text/html", "UTF-8", origin);
        } catch (Exception ex) { notice("RollLab could not load"); }
    }
    public final class NativeBridge {
        @JavascriptInterface public void saveFile(String name, String mime, String data) {
            runOnUiThread(() -> {
                if (data == null || data.length() > 24000000) { notice("Backup too large"); return; }
                exportData = data;
                exportName = name.replaceAll("[^A-Za-z0-9._-]", "_");
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType(mime.startsWith("text/csv") ? "text/csv" : "application/json");
                i.putExtra(Intent.EXTRA_TITLE, exportName);
                try { startActivityForResult(i, EXPORT); }
                catch (Exception ex) { exportData=null; notice("Document picker unavailable"); }
            });
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == IMPORT) {
            if (chooser != null) {
                chooser.onReceiveValue(result == RESULT_OK && data != null && data.getData() != null ? new Uri[]{data.getData()} : null);
                chooser = null;
            }
        } else if (request == EXPORT) {
            if (result == RESULT_OK && data != null && data.getData() != null && exportData != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    out.write(exportData.getBytes(StandardCharsets.UTF_8));
                    notice("Saved " + exportName);
                } catch (Exception ex) { notice("Export failed"); }
            }
            exportData=null;
        }
    }
    private void notice(String msg) { Toast.makeText(this,msg,Toast.LENGTH_SHORT).show(); }
    @Override protected void onDestroy() {
        if (chooser != null) chooser.onReceiveValue(null);
        web.removeJavascriptInterface("RollLabNative");
        web.destroy();
        super.onDestroy();
    }
}
