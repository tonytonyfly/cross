package org.CrossApp.lib;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

//import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
//import com.tencent.smtt.sdk.ValueCallback;
//import com.tencent.smtt.sdk.WebChromeClient;
//import com.tencent.smtt.sdk.WebSettings.LayoutAlgorithm;
//import com.tencent.smtt.sdk.WebView;
//import com.tencent.smtt.sdk.WebViewClient;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.ByteBuffer;

//import android.webkit.SslErrorHandler;
//import android.webkit.WebChromeClient;
//import android.webkit.WebView;
//import android.webkit.WebViewClient;

public class CrossAppWebViewNative extends WebView {
    private static final String TAG = CrossAppWebViewHelper.class.getSimpleName();

    private int viewTag;
    private String jsScheme;
    private String szWebViewRect;

    public CrossAppWebViewNative(Context context) {
        this(context, -1);


    }

    @SuppressLint("SetJavaScriptEnabled")
    public CrossAppWebViewNative(Context context, int viewTag) {
        super(context);
        this.viewTag = viewTag;
        this.jsScheme = "";
        this.szWebViewRect = "0-0-0-0";

        this.setFocusable(true);
        this.setFocusableInTouchMode(true);

        this.getSettings().setSupportZoom(true);
        this.getSettings().setBuiltInZoomControls(true);
        this.getSettings().setJavaScriptEnabled(true);
        this.addJavascriptInterface(new InJavaScriptLocalObj(), "local_obj");
        this.getSettings().setUseWideViewPort(true);
        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        this.getSettings().setLoadWithOverviewMode(true);

        // `searchBoxJavaBridge_` has big security risk. http://jvn.jp/en/jp/JVN53768697
        try {
            Method method = this.getClass().getMethod("removeJavascriptInterface", new Class[]{String.class});
            method.invoke(this, "searchBoxJavaBridge_");
        } catch (Exception e) {
            Log.d(TAG, "This API level do not support `removeJavascriptInterface`");
        }

        this.setWebViewClient(new CrossAppWebViewClient());
        this.setWebChromeClient(new MyWebChromeClient());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return CrossAppGLSurfaceView.getInstance().onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    public void setJavascriptInterfaceScheme(String scheme) {
        this.jsScheme = scheme != null ? scheme : "";
    }

    public void setScalesPageToFit(boolean scalesPageToFit) {
        this.getSettings().setSupportZoom(scalesPageToFit);
    }

    private Bitmap bmp = null;
    private ByteBuffer imageData = null;

    private static native void onSetByteArrayBuffer(int index, byte[] buf, int wdith, int height);

    public void getWebViewImage() {
        bmp = this.getDrawingCache();
        if (bmp != null && imageData == null) {
            imageData = ByteBuffer.allocate(bmp.getRowBytes() * bmp.getHeight());
            bmp.copyPixelsToBuffer(imageData);

            CrossAppActivity.getContext().runOnGLThread(new Runnable() {
                @Override
                public void run() {
                    onSetByteArrayBuffer(viewTag, imageData.array(), bmp.getWidth(), bmp.getHeight());
                    imageData = null;
                }
            });
        }
    }

    class CrossAppWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, final String urlString) {
            if (urlString.startsWith("weixin://wap/pay?")) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(urlString));
                    CrossAppActivity.getContext().startActivity(intent);
                } catch (Exception exception) {
                    Toast.makeText(CrossAppActivity.getContext(), "支付失败,请重试", Toast.LENGTH_SHORT).show();
                }
                CrossAppActivity.getContext().runOnGLThread(new Runnable() {
                    @Override
                    public void run() {
                        CrossAppWebViewHelper._onJsCallback(viewTag, urlString);
                    }
                });
                return true;
            } else if (urlString.contains("platformapi/startapp")) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(urlString));
                    intent.addCategory("android.intent.category.BROWSABLE");
                    intent.setComponent(null);
                    CrossAppActivity.getContext().startActivity(intent);
                } catch (Exception exception) {
                    Toast.makeText(CrossAppActivity.getContext(), "支付失败,请重试", Toast.LENGTH_SHORT).show();
                }
                CrossAppActivity.getContext().runOnGLThread(new Runnable() {
                    @Override
                    public void run() {
                        CrossAppWebViewHelper._onJsCallback(viewTag, urlString);
                    }
                });
                return true;
            }

            URI uri = URI.create(urlString);
            if (uri != null && uri.getScheme().equals(jsScheme)) {
                CrossAppActivity.getContext().runOnGLThread(new Runnable() {
                    @Override
                    public void run() {
                        CrossAppWebViewHelper._onJsCallback(viewTag, urlString);
                    }
                });
                return true;
            }
            return CrossAppWebViewHelper._shouldStartLoading(viewTag, urlString);
        }

        @Override
        public void onPageFinished(WebView view, final String url) {
            super.onPageFinished(view, url);
            CrossAppActivity.getContext().runOnGLThread(new Runnable() {
                @Override
                public void run() {
                    CrossAppWebViewHelper._didFinishLoading(viewTag, url);
                }
            });
        }

        @Override
        public void onReceivedError(WebView view, int errorCode, String description, final String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            CrossAppActivity.getContext().runOnGLThread(new Runnable() {
                @Override
                public void run() {
                    CrossAppWebViewHelper._didFailLoading(viewTag, failingUrl);
                }
            });

        }

        //        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(CrossAppActivity.getContext());
            String message = "SSL Certificate error.";
            switch (error.getPrimaryError()) {
                case SslError.SSL_UNTRUSTED:
                    message = "The certificate authority is not trusted.";
                    break;
                case SslError.SSL_EXPIRED:
                    message = "The certificate has expired.";
                    break;
                case SslError.SSL_IDMISMATCH:
                    message = "The certificate Hostname mismatch.";
                    break;
                case SslError.SSL_NOTYETVALID:
                    message = "The certificate is not yet valid.";
                    break;
            }
            message += " Do you want to continue anyway?";

            builder.setTitle("SSL Certificate Error");
            builder.setMessage(message);
            builder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.proceed();
                }
            });
            builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.cancel();
                }
            });
            final AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    final class InJavaScriptLocalObj {
        public void showSource(String html) {
            CrossAppWebViewHelper.didLoadHtmlSource(html);
            CrossAppWebViewHelper.s_bWaitGetHemlSource = false;
        }
    }

    public void setWebViewRect(int left, int top, int maxWidth, int maxHeight) {
        fixSize(left, top, maxWidth, maxHeight);
        this.szWebViewRect = String.format("%d-%d-%d-%d", left, top, maxWidth, maxHeight);
    }

    public String getWebViewRectString() {
        return szWebViewRect;
    }

    public int getViewTag() {
        return viewTag;
    }

    private void fixSize(int left, int top, int width, int height) {
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        layoutParams.leftMargin = left;
        layoutParams.topMargin = top;
        layoutParams.width = width;
        layoutParams.height = height;
        this.setLayoutParams(layoutParams);
    }


    public class MyWebChromeClient extends WebChromeClient {

        // Android 3.0 以下
        public void openFileChooser(ValueCallback<Uri> valueCallback) {
            CrossAppActivity.getContext().getOnValueNativeCallbackListenner().OnValueCallback(valueCallback);
        }

        // Android 3~4.1
        public void openFileChooser(ValueCallback valueCallback, String acceptType) {
            CrossAppActivity.getContext().getOnValueNativeCallbackListenner().OnValueCallback(valueCallback, acceptType);
        }

        // Android  4.1以上
        public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
            CrossAppActivity.getContext().getOnValueNativeCallbackListenner().OnValueCallback(valueCallback, acceptType, capture);
        }

        // Android 5.0以上
        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            CrossAppActivity.getContext().getOnValueNativeCallbackListenner().OnValueCallback(webView, filePathCallback, fileChooserParams);
            return true;
        }
    }

    public interface OnValueCallbackListenner {
        void OnValueCallback(ValueCallback<Uri> valueCallback);

        void OnValueCallback(ValueCallback valueCallback, String acceptType);

        void OnValueCallback(ValueCallback<Uri> valueCallback, String acceptType, String capture);

        void OnValueCallback(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams);

    }

}
