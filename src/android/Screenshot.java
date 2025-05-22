/**
 * Copyright (C) 2012 30ideas (http://30ide.as)
 * MIT licensed
 *
 * @author Josemando Sobral
 * @created Jul 2nd, 2012.
 * improved by Hongbo LU
 */
package com.darktalker.cordova.screenshot;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.util.Log;
import android.webkit.WebView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import android.webkit.ValueCallback;



public class Screenshot extends CordovaPlugin {
    private CallbackContext mCallbackContext;
    private String mAction;
    private JSONArray mArgs;


    private String mFormat;
    private String mFileName;
    private Integer mQuality;

    protected final static String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
    public static final int PERMISSION_DENIED_ERROR = 20;
    public static final int SAVE_SCREENSHOT_SEC = 0;

    @Override
    public Object onMessage(String id, Object data) {
        if (id.equals("onGotXWalkBitmap")) {
            Bitmap bitmap = (Bitmap) data;
            if (bitmap != null) {
                if (mAction.equals("saveScreenshot")) {
                    saveScreenshot(bitmap, mFormat, mFileName, mQuality);
                } else if (mAction.equals("getScreenshotAsURI")) {
                    getScreenshotAsURI(bitmap, mQuality);
                }
            }
        }
        return null;
    }

    private Bitmap getBitmap() {
        Bitmap bitmap = null;

        boolean isCrosswalk = false;
        try {
            Class.forName("org.crosswalk.engine.XWalkWebViewEngine");
            isCrosswalk = true;
        } catch (Exception e) {
        }

        Log.d("FullScreenshot", "isCrosswalk" + isCrosswalk);

        if (isCrosswalk) {
            webView.getPluginManager().postMessage("captureXWalkBitmap", this);
        } else {
            /* 
            View view = webView.getView();//.getRootView();
            view.setDrawingCacheEnabled(true);
            bitmap = Bitmap.createBitmap(view.getDrawingCache());
            view.setDrawingCacheEnabled(false);
            */

            android.webkit.WebView awv = (android.webkit.WebView) this.webView.getView();
            Picture picture = awv.capturePicture();
            bitmap = Bitmap.createBitmap(picture.getWidth(), picture.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bitmap);
            picture.draw(c);
        }

        return bitmap;
    }

    private void scanPhoto(String imageFileName) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(imageFileName);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.cordova.getActivity().sendBroadcast(mediaScanIntent);
    }

    private void saveScreenshot(Bitmap bitmap, String format, String fileName, Integer quality) {
        try {
            File folder = new File(Environment.getExternalStorageDirectory(), "Pictures");
            if (!folder.exists()) {
                folder.mkdirs();
            }

            File f = new File(folder, fileName + "." + format);

            FileOutputStream fos = new FileOutputStream(f);
            if (format.equals("png")) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            } else if (format.equals("jpg")) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality == null ? 100 : quality, fos);
            }
            JSONObject jsonRes = new JSONObject();
            jsonRes.put("filePath", f.getAbsolutePath());
            PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
            mCallbackContext.sendPluginResult(result);

            scanPhoto(f.getAbsolutePath());
            fos.close();
        } catch (JSONException e) {
            mCallbackContext.error(e.getMessage());

        } catch (IOException e) {
            mCallbackContext.error(e.getMessage());

        }
    }

    private void getScreenshotAsURI(Bitmap bitmap, int quality) {
        try {
            ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();

            if (bitmap.compress(CompressFormat.JPEG, quality, jpeg_data)) {
                byte[] code = jpeg_data.toByteArray();
                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                String js_out = new String(output);
                js_out = "data:image/jpeg;base64," + js_out;
                JSONObject jsonRes = new JSONObject();
                jsonRes.put("URI", js_out);
                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                mCallbackContext.sendPluginResult(result);

                js_out = null;
                output = null;
                code = null;
            }

            jpeg_data = null;

        } catch (JSONException e) {
            mCallbackContext.error(e.getMessage());

        } catch (Exception e) {
            mCallbackContext.error(e.getMessage());

        }
    }

    public void saveScreenshot() throws JSONException{
        mFormat = (String) mArgs.get(0);
        mQuality = (Integer) mArgs.get(1);
        mFileName = (String) mArgs.get(2);

        super.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mFormat.equals("png") || mFormat.equals("jpg")) {
                    Bitmap bitmap = getBitmap();
                    if (bitmap != null) {
                        saveScreenshot(bitmap, mFormat, mFileName, mQuality);
                    }
                } else {
                    mCallbackContext.error("format " + mFormat + " not found");

                }
            }
        });
    }

    public void getScreenshotAsURI() throws JSONException{
        mQuality = (Integer) mArgs.get(0);

        super.cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap = getBitmap();
                if (bitmap != null) {
                    getScreenshotAsURI(bitmap, mQuality);
                }
            }
        });
    }

     public void getScreenshotAsURISync() throws JSONException{
        mQuality = (Integer) mArgs.get(0);
        
        Runnable r = new Runnable(){
            @Override
            public void run() {
                Bitmap bitmap = getBitmap();
                if (bitmap != null) {
                    getScreenshotAsURI(bitmap, mQuality);
                }
                synchronized (this) { this.notify(); }
            }
        };

        synchronized (r) {
            super.cordova.getActivity().runOnUiThread(r);
            try{
                r.wait();
            } catch (InterruptedException e){
                mCallbackContext.error(e.getMessage());
            }
        }
    }
    
    private void captureFullPage(final CallbackContext callbackContext) {
        final View view = this.webView.getView();
        final android.webkit.WebView awv = (android.webkit.WebView) view;
        
        Log.d("FullScreenshot", "WILL CAPTURE FULL SCREEN" + isCrosswalk);

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d("FullScreenshot", "WILL CAPTURE FULL SCREEN 1" + isCrosswalk);
                awv.evaluateJavascript(
                    "(function() { return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight); })();",
                    new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            Log.d("FullScreenshot", "WILL CAPTURE FULL SCREEN 2" + isCrosswalk);
                            try {
                                int contentHeight = Integer.parseInt(value.replaceAll("[^\\d]", ""));
                                final int originalHeight = view.getLayoutParams().height;

                                view.getLayoutParams().height = contentHeight;
                                view.requestLayout();

                                view.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d("FullScreenshot", "WILL CAPTURE FULL SCREEN 3" + isCrosswalk);
                                        try {
                                            Picture picture = awv.capturePicture();
                                            Bitmap bitmap = Bitmap.createBitmap(
                                                picture.getWidth(),
                                                picture.getHeight(),
                                                Bitmap.Config.ARGB_8888
                                            );
                                            Canvas canvas = new Canvas(bitmap);
                                            picture.draw(canvas);

                                            // Encode to base64
                                            ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
                                            int quality = mQuality != null ? mQuality : 100;

                                            if (bitmap.compress(CompressFormat.JPEG, quality, jpeg_data)) {
                                                byte[] code = jpeg_data.toByteArray();
                                                byte[] output = Base64.encode(code, Base64.NO_WRAP);
                                                String js_out = new String(output);
                                                js_out = "data:image/jpeg;base64," + js_out;

                                                JSONObject jsonRes = new JSONObject();
                                                jsonRes.put("URI", js_out);
                                                PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
                                                callbackContext.sendPluginResult(result);
                                            }

                                            // Restore view height
                                            view.getLayoutParams().height = originalHeight;
                                            view.requestLayout();
                                        } catch (Exception e) {
                                            callbackContext.error("Capture failed: " + e.getMessage());
                                        }
                                    }
                                }, 500);
                            } catch (Exception e) {
                                callbackContext.error("Failed to evaluate scroll height: " + e.getMessage());
                            }
                        }
                    }
                );
            }
        });
    }

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        // starting on ICS, some WebView methods
        // can only be called on UI threads
        mCallbackContext = callbackContext;
        mAction = action;
        mArgs = args;

        if (action.equals("saveScreenshot")) {
            if(PermissionHelper.hasPermission(this, PERMISSIONS[0])) {
                saveScreenshot();
            } else {
                PermissionHelper.requestPermissions(this, SAVE_SCREENSHOT_SEC, PERMISSIONS);
            }
            return true;
        } else if (action.equals("getScreenshotAsURI")) {
            captureFullPage(callbackContext);
            return true;
        } else if (action.equals("getScreenshotAsURISync")){
            getScreenshotAsURISync();
            return true;
        }
        callbackContext.error("action not found");
        return false;
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        for(int r:grantResults)
        {
            if(r == PackageManager.PERMISSION_DENIED)
            {
                mCallbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR));
                return;
            }
        }
        switch(requestCode)
        {
            case SAVE_SCREENSHOT_SEC:
                saveScreenshot();
                break;
        }
    }


}
