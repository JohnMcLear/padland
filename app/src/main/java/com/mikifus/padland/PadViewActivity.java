package com.mikifus.padland;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.v4.view.MenuItemCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.support.v7.widget.ShareActionProvider;
import android.widget.Toast;

import com.pnikosis.materialishprogress.ProgressWheel;

/**
 * This activity makes a webapp view into a browser and loads the document
 * url.
 * It does as well save the new visited urls in the documents list.
 *
 * @author mikifus
 */
public class PadViewActivity extends PadLandDataActivity {
    private WebView webView;
    private String current_padUrl = "";

    /*
    Progress wheel is a fancy alternative to the ProgressBar, that wasn't working at all.
     */
    private ProgressWheel pwheel;

    /*
    Let me know when it is ready, please, I need it.
     */
    private boolean javascriptIsReady = false;

    /*
    Maximum size for the webView, it is quite complicated
     */
    private int max_viewport_size = 400;


    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    /**
     * Local class to handle the Javascript onLoad callback
     */
    final class JavascriptCallbackHandler {
        /**
         * Gets called from Javascript
         */
        @JavascriptInterface
        public void onLoad() {
            // Must run on ui thread (async)
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    onLoadCompleted();
                }
            });
        }

        /*@JavascriptInterface
        public void resize(final float height) {
            PadViewActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    webView.setLayoutParams(new LinearLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels));
                }
            });
        }*/
    }

    /**
     * onCreate override
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Forces landscape view
//        this.setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_PORTRAIT );

        // If no network...
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.network_is_unreachable), Toast.LENGTH_LONG).show();
            return;
        }

        setContentView(R.layout.activity_padview);
        _loadProgressWheel();

        this._makePadUrl();
        this._saveNewPad();
        this._updateViewedPad();
        this._makeWebView();

        this.loadUrl("file:///android_asset/PadView.html");

        // Cookies will be needed for pads
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }
    }
    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        onCreate(savedInstanceState);
    }

    /**
     * Loads the fancy ProgressWheel to show it's loading.
     */
    private void _loadProgressWheel() {
        pwheel = (ProgressWheel) findViewById(R.id.progress_wheel);
        pwheel.spin();
    }

    private void _setProgressWheelProgress(int progress) {
        if (progress < 25) {
            // At least something visible
            progress = 25;
        }
        float p = progress / 100;
        Log.d("LOAD_PROGRESS_LOG", String.valueOf(progress));
        pwheel.setVisibility(View.VISIBLE);
        pwheel.setProgress(p);
        if (progress > 99) {
            _hideProgressWheel();
        }
    }

    public void _hideProgressWheel() {
        pwheel.setVisibility(View.GONE);
        Log.d("LOAD_PROGRESS_LOG", "PWheel must be gone by now...");
    }

    /**
     * Gets the pad data from the environment
     */
    private void _makePadUrl() {
        padData padData = this._getPadData();

        current_padUrl = padData.getUrl();
    }

    /**
     * Gets back the padData set.
     *
     * @return padData
     */
    private padData _getPadData() {
        long pad_id = this._getPadId();
        padData padData;

        if (pad_id > 0) {
            Cursor c = padlandDb._getPadDataById(pad_id);
            padData = new padData(c);
            c.close();
        } else {
            padData = this._getPadDataFromIntent();
        }

        return padData;
    }

    /**
     * The initial data of the pad is in the intent, it can be an URL, or some parameters in the
     * StringExtra intent.
     *
     * @return
     */
    private padData _getPadDataFromIntent() {
        Intent myIntent = getIntent();
        String action = myIntent.getAction();
        padData padData;

        if (Intent.ACTION_VIEW.equals(action)) {
            String padUrl = String.valueOf(myIntent.getData());
            padData = makePadData(null, null, padUrl);
        } else {
            String padName = myIntent.getStringExtra("padName");
            String padServer = myIntent.getStringExtra("padServer");
            String padUrl = myIntent.getStringExtra("padUrl");

            padData = makePadData(padName, padServer, padUrl);
        }

        return padData;
    }

    /**
     * It gets the pad id by all possible means. This is, reading it from the Intent (in a
     * LongExtra) or using the padUrl to make a database query.
     *
     * @return
     */
    public long _getPadId() {
        Intent myIntent = getIntent();
        long pad_id = myIntent.getLongExtra("pad_id", 0);

        if (pad_id > 0) {
            return pad_id;
        }

        padData padData = this._getPadDataFromIntent();
        Cursor c = padlandDb._getPadDataByUrl(padData.getUrl());

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            long id = c.getLong(0);
            c.close();
            return id;
        }

        return 0;
    }

    /**
     * Saves a new pad from the intent information
     */
    private boolean _saveNewPad() {
        Context context = getApplicationContext();
        SharedPreferences userDetails = context.getSharedPreferences(getPackageName() + "_preferences", context.MODE_PRIVATE);

        boolean result = false;
        if (userDetails.getBoolean("auto_save_new_pads", true) && this._getPadId() == 0) {
            // Add a new record
            ContentValues values = new ContentValues();
            padData intentData = this._getPadDataFromIntent();

            values.put(PadContentProvider.NAME, intentData.getName());
            values.put(PadContentProvider.SERVER, intentData.getServer());
            values.put(PadContentProvider.URL, intentData.getUrl());

            result = padlandDb.savePadData(0, values);
        }
        return result;
    }

    /**
     * Updates the last used date of a pad.
     * It might update more details in the future.
     * <p/>
     * Actually it only calls accessUpdate, where the bussiness is made.
     */
    private boolean _updateViewedPad() {
        boolean result = false;
        long pad_id = this._getPadId();
        if (pad_id != 0) {
            padlandDb.accessUpdate(pad_id);
            result = true;
        }
        return result;
    }

    /**
     * Makes the webview that will contain a document.
     * It loads asynchronously by calling Javascript.
     *
     * @return
     */
    private WebView _makeWebView() {
        final String current_padUrl = this.getCurrentPadUrl();
        webView = (WebView) findViewById(R.id.activity_main_webview);

        // WebViewClient is neeed in order to load asynchronously
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {

                Context context = getApplicationContext();
                SharedPreferences userDetails = context.getSharedPreferences(getPackageName() + "_preferences", context.MODE_PRIVATE);
                String default_username = userDetails.getString("padland_default_username", "PadLand Viewer User");
                String default_color = userDetails.getString("padland_default_color", "#555");

                //webView.loadUrl("javascript:start('" + current_padUrl + "', '" + default_username + "', '" + default_color + "' )");

                runJavascriptOnView(view, "start('" + current_padUrl + "', '" + default_username + "', '" + default_color + "' );");
                javascript_padViewResize();

                super.onPageFinished(view, url);
            }

            /**
             * This method is here because of http to https redirects.
             * @param view
             * @param url
             * @return
             */
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        this._makeWebSettings(webView);
        this._addListenersToView();
        this._addJavascriptOnLoad(webView);
        return webView;
    }

    /**
     * Enables the required settings and features for the webview
     *
     * @param webView
     */
    private void _makeWebSettings(WebView webView) {
        webView.setInitialScale(1);
        WebSettings webSettings = webView.getSettings();
        // Enable Javascript
        webSettings.setJavaScriptEnabled(true);
        // remove a weird white line on the right size
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);

        /*
        // fit the width of screen
        webSettings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setDisplayZoomControls(true);
        //webSettings.setDefaultZoom(WebSettings.ZoomDensity.FAR);

        webView.setPadding(0, 0, 0, 0);
        webView.setInitialScale(getScale());

        Log.d("WebViewSettings", "Get scale: " + getScale());*/
        webSettings.setUseWideViewPort(true);
        webSettings.setSupportZoom(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setLoadWithOverviewMode(true);
    }

    private void runJavascriptOnView(WebView view, String js_string) {
        runJavascriptOnView(view, js_string, false);
    }

    private void runJavascriptOnView(WebView view, String js_string, Boolean force) {
        if (!force && !javascriptIsReady) {
            // If javascript is not ready better not to break anything
            Log.d("LOAD_PROGRESS_LOG", "JS call has been interrupted: " + js_string);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // In KitKat+ you should use the evaluateJavascript method
            view.evaluateJavascript(js_string, new ValueCallback<String>() {
                @TargetApi(Build.VERSION_CODES.HONEYCOMB)
                @Override
                public void onReceiveValue(String s) {
                    //Log.d("onLoadCompleted", "Feedback test.");
                }
            });
        } else {
            view.loadUrl("javascript:" + js_string);
        }
    }

    /**
     * Creates a padData object trying to complete the empty fields the others.
     * Possible combinations is like this: padName + padServer = padUrl
     *
     * @param padName
     * @param padServer
     * @param padUrl
     * @return
     */
    public padData makePadData(String padName, String padServer, String padUrl) {
        if (padUrl == null || padUrl.isEmpty()) {
            if (padName == null || padName.isEmpty()) {
                return null;
            }
            if (padUrl == null || padUrl.isEmpty()) {
                padUrl = padServer + padName;
            }
            if (padServer == null || padServer.isEmpty()) {
                padServer = padUrl.replace(padName, "");
            }
        } else if (padName == null && padServer == null) {
            padName = padUrl.substring(padUrl.lastIndexOf("/") + 1);
            padServer = padUrl.substring(0, padUrl.lastIndexOf("/"));
        } else if (padName.isEmpty()) {
            padName = padUrl.replace(padServer, "");
        } else if (padServer.isEmpty()) {
            padServer = padUrl.replace(padName, "");
        }

        String[] columns = PadContentProvider.getPadFieldsList();

        // This creates a fake cursor
        MatrixCursor matrixCursor = new MatrixCursor(columns);
        startManagingCursor(matrixCursor);
        matrixCursor.addRow(new Object[]{0, padName, padServer, padUrl, 0, 0, 0});

        padData padData = new padData(matrixCursor);
        matrixCursor.close();

        return padData;
    }

    /**
     * Loads the specified url into the webView, it must be previously loaded.
     *
     * @param url
     */
    public void loadUrl(String url) {
        webView.loadUrl(url);
    }

    /**
     * Called by the JavascriptCallbackHandler class
     */
    public void onLoadCompleted() {
        Log.d("onLoadCompleted", "On load was triggered. Javascript can run.");

        javascriptIsReady = true;

        _hideProgressWheel();

        /*webView.evaluateJavascript("(function() { " +
                " alert(22); " +
                " $(\"#chatbox\").css('top','37px').css('bottom','37px'); " +
                " setTimeout(function(){" +
                "       $(\"#chatbox\").hide();chat.hide();  " +
                "       $(\"#chaticon\").hide(); " +
                "   }, 3000); " +
                "})();", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String s) {
                Log.d("onLoadCompleted", "Feedback test.");
            }
        });*/

        /*String fulljs = "(function() { " +
                " alert(22); " +
                " $(\"#chatbox\").css('top','37px').css('bottom','37px'); " +
                " setTimeout(function(){ " +
                "       $(\"#chatbox\").hide();chat.hide();  " +
                "       $(\"#chaticon\").hide(); " +
                "   }, 3000); " +
                "})();";
        webView.loadUrl(fulljs);*/
    }

    /**
     * Creates the options menu.
     *
     * @param menu
     * @return
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu, R.menu.pad_view);

        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.menuitem_share);

        // Fetch and store ShareActionProvider
        ShareActionProvider actionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_auto_text) + getCurrentPadUrl());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.share_document)));

        if( actionProvider == null ) {
            return false;
        }
        actionProvider.setShareIntent(sendIntent);

        // Return true to display menu
        return true;
    }

    private String getCurrentPadUrl() {
        return current_padUrl;
    }

    private void _addListenersToView() {
        webView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {

            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight,
                                       int oldBottom) {
                // its possible that the layout is not complete in which case
                // we will get all zero values for the positions, so ignore the event
                if (left == 0 && top == 0 && right == 0 && bottom == 0) {
                    return;
                }

                javascript_padViewResize();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, int progress) {
                _setProgressWheelProgress(progress);
            }
        });
    }

    /**
     * With a JavsascriptInterface I manage to call functions when the page loads
     *
     * @param webView
     */
    private void _addJavascriptOnLoad(WebView webView) {
        webView.addJavascriptInterface(new JavascriptCallbackHandler(), "webviewScriptAPI");
        String fulljs = "(\n    function() { \n";
        fulljs += "        window.onload = function() {\n";
        fulljs += "            webviewScriptAPI.onLoad();\n";
        fulljs += "        };\n";
        fulljs += "    })()\n";
        runJavascriptOnView(webView, fulljs, true); // Forced
    }

    private void javascript_padViewResize() {
        //runJavascriptOnView(webView, "PadViewResize(" + getResources().getDisplayMetrics().widthPixels + ", " + getResources().getDisplayMetrics().heightPixels + ")");
        int local_max_viewport_size = max_viewport_size;
        int w;
        int h;
        //int current_w = getResources().getDisplayMetrics().widthPixels;
        //int current_h = getResources().getDisplayMetrics().heightPixels;
        int current_w = webView.getMeasuredWidth();
        int current_h = webView.getMeasuredHeight();

        if( getResources().getDisplayMetrics().widthPixels < max_viewport_size )
        {
            local_max_viewport_size = 300; // Fallback for small screens
        }
        double ratio = current_h / (double) current_w;

        if( current_h > current_w ) // vertical
        {
            w = local_max_viewport_size;
            h = (int) Math.floor(local_max_viewport_size * ratio);
        }
        else // horizontal
        {
            h = local_max_viewport_size;
            w = (int) Math.floor(local_max_viewport_size / ratio);
        }

        Log.d("RESIZE", "old w: " + current_w + ", h: " + current_h + " ratio: " + ratio);
        Log.d("RESIZE", "new w: " + w + ", h: " + h);


        /*
        PadViewActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.setLayoutParams(new LinearLayout.LayoutParams(getResources().getDisplayMetrics().widthPixels, (int) (height * getResources().getDisplayMetrics().density)));
            }
        });*/
        runJavascriptOnView(webView, "PadViewResize("+w+","+h+")");
    }
}