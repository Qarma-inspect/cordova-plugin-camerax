package com.cordovaplugincamerax;

import android.Manifest;
import android.content.pm.PackageManager;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.graphics.Color;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import com.cordovaplugincamerax.CameraPreviewFragment;
import com.cordovaplugincamerax.CameraStartedCallback;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;

public class CameraPreview extends CordovaPlugin {
    private static final String VIDEO_FILE_EXTENSION = ".mp4";
    private static final String TAG = "CameraPreview";

    private static final String COLOR_EFFECT_ACTION = "setColorEffect";
    private static final String ZOOM_ACTION = "setZoom";
    private static final String GET_ZOOM_ACTION = "getZoom";
    private static final String GET_MAX_ZOOM_ACTION = "getMaxZoom";
    private static final String SUPPORTED_FLASH_MODES_ACTION = "getSupportedFlashModes";
    private static final String GET_FLASH_MODE_ACTION = "getFlashMode";
    private static final String SET_FLASH_MODE_ACTION = "setFlashMode";
    private static final String START_CAMERA_ACTION = "startCamera";
    private static final String STOP_CAMERA_ACTION = "stopCamera";
    private static final String PREVIEW_SIZE_ACTION = "setPreviewSize";
    private static final String SWITCH_CAMERA_ACTION = "switchCamera";
    private static final String TAKE_PICTURE_ACTION = "takePicture";
    private static final String TAKE_PICTURE_TO_FILE_ACTION = "takePictureToFile";
    private static final String SHOW_CAMERA_ACTION = "showCamera";
    private static final String HIDE_CAMERA_ACTION = "hideCamera";
    private static final String TAP_TO_FOCUS = "tapToFocus";
    private static final String SUPPORTED_PICTURE_SIZES_ACTION = "getSupportedPictureSizes";
    private static final String SUPPORTED_PREVIEW_SIZES_ACTION = "getSupportedPreviewSizes";
    private static final String SUPPORTED_FOCUS_MODES_ACTION = "getSupportedFocusModes";
    private static final String SUPPORTED_WHITE_BALANCE_MODES_ACTION = "getSupportedWhiteBalanceModes";
    private static final String GET_FOCUS_MODE_ACTION = "getFocusMode";
    private static final String SET_FOCUS_MODE_ACTION = "setFocusMode";
    private static final String GET_EXPOSURE_MODES_ACTION = "getExposureModes";
    private static final String GET_EXPOSURE_MODE_ACTION = "getExposureMode";
    private static final String SET_EXPOSURE_MODE_ACTION = "setExposureMode";
    private static final String GET_EXPOSURE_COMPENSATION_ACTION = "getExposureCompensation";
    private static final String SET_EXPOSURE_COMPENSATION_ACTION = "setExposureCompensation";
    private static final String GET_EXPOSURE_COMPENSATION_RANGE_ACTION = "getExposureCompensationRange";
    private static final String GET_WHITE_BALANCE_MODE_ACTION = "getWhiteBalanceMode";
    private static final String SET_WHITE_BALANCE_MODE_ACTION = "setWhiteBalanceMode";
    private static final String GET_CAMERA_INFO_ROTATION = "getCameraInfoRotation";
    private static final String SET_CAMERA_PARAMETER_RESOLUTION = "setCameraParameterResolution";
    private static final String START_RECORD_VIDEO_ACTION = "startRecordVideo";
    private static final String STOP_RECORD_VIDEO_ACTION = "stopRecordVideo";

    private static final int CAM_REQ_CODE = 0;
    private static final int VID_REQ_CODE = 1;

    private static final String[] permissions = {
            Manifest.permission.CAMERA
    };
    private static final String[] videoPermissions = {
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    };

    private CameraPreviewFragment fragment;
    private CallbackContext takePictureCallbackContext;
    private CallbackContext setFocusCallbackContext;
    private CallbackContext startCameraCallbackContext;
    private CallbackContext startRecordVideoCallbackContext;
    private CallbackContext stopRecordVideoCallbackContext;

    private CallbackContext execCallback;
    private JSONArray execArgs;

    private ViewParent webViewParent;

    private int containerViewId = 1;
    private String VIDEO_FILE_PATH = "";

    public CameraPreview() {
        super();
        Log.d(TAG, "Constructing");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "Called CameraPreview plugin with action : " + action);
        if (START_CAMERA_ACTION.equals(action)) {
            if (cordova.hasPermission(permissions[0])) {
                return startCamera(args.getInt(0), args.getInt(1), args.getInt(2), args.getInt(3), callbackContext);
            } else {
                this.execCallback = callbackContext;
                this.execArgs = args;
                cordova.requestPermissions(this, CAM_REQ_CODE, permissions);
            }
        } else if (TAKE_PICTURE_ACTION.equals(action)) {
        } else if (TAKE_PICTURE_TO_FILE_ACTION.equals(action)) {
        } else if (COLOR_EFFECT_ACTION.equals(action)) {
        } else if (ZOOM_ACTION.equals(action)) {
        } else if (GET_ZOOM_ACTION.equals(action)) {
        } else if (GET_MAX_ZOOM_ACTION.equals(action)) {
        } else if (PREVIEW_SIZE_ACTION.equals(action)) {
        } else if (SUPPORTED_FLASH_MODES_ACTION.equals(action)) {
        } else if (GET_FLASH_MODE_ACTION.equals(action)) {
        } else if (SET_FLASH_MODE_ACTION.equals(action)) {
        } else if (STOP_CAMERA_ACTION.equals(action)) {
        } else if (SHOW_CAMERA_ACTION.equals(action)) {
        } else if (HIDE_CAMERA_ACTION.equals(action)) {
        } else if (TAP_TO_FOCUS.equals(action)) {
        } else if (SWITCH_CAMERA_ACTION.equals(action)) {
        } else if (SUPPORTED_PICTURE_SIZES_ACTION.equals(action)) {
        } else if (SUPPORTED_PREVIEW_SIZES_ACTION.equals(action)) {
        } else if (GET_EXPOSURE_MODES_ACTION.equals(action)) {
        } else if (SUPPORTED_FOCUS_MODES_ACTION.equals(action)) {
        } else if (GET_FOCUS_MODE_ACTION.equals(action)) {
        } else if (SET_FOCUS_MODE_ACTION.equals(action)) {
        } else if (GET_EXPOSURE_MODE_ACTION.equals(action)) {
        } else if (SET_EXPOSURE_MODE_ACTION.equals(action)) {
        } else if (GET_EXPOSURE_COMPENSATION_ACTION.equals(action)) {
        } else if (SET_EXPOSURE_COMPENSATION_ACTION.equals(action)) {
        } else if (GET_EXPOSURE_COMPENSATION_RANGE_ACTION.equals(action)) {
        } else if (SUPPORTED_WHITE_BALANCE_MODES_ACTION.equals(action)) {
        } else if (GET_WHITE_BALANCE_MODE_ACTION.equals(action)) {
        } else if (SET_WHITE_BALANCE_MODE_ACTION.equals(action)) {
        } else if (GET_CAMERA_INFO_ROTATION.equals(action)) {
        } else if (SET_CAMERA_PARAMETER_RESOLUTION.equals(action)) {
        } else if (START_RECORD_VIDEO_ACTION.equals(action)) {

        } else if (STOP_RECORD_VIDEO_ACTION.equals(action)) {
        }
        return false;
    }

    private boolean startCamera(int x, int y, int width, int height, CallbackContext callbackContext) {
        webView.getView().setBackgroundColor(0x00000000);
        // Request focus on webView as page needs to be clicked/tapped to get focus on
        // page events
        webView.getView().requestFocus();

        fragment = new CameraPreviewFragment((err) -> {
            if (err != null) {
                callbackContext.error(err.getMessage());
                return;
            }
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "Camera started");
            callbackContext.sendPluginResult(pluginResult);
        });

        try {
            RunnableFuture<Void> addViewTask = new FutureTask<>(
                    new Runnable() {
                        @Override
                        public void run() {
                            DisplayMetrics metrics = new DisplayMetrics();
                            cordova.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

                            FrameLayout containerView = cordova.getActivity().findViewById(containerViewId);
                            if (containerView == null) {
                                containerView = new FrameLayout(cordova.getActivity().getApplicationContext());
                                containerView.setId(containerViewId);
                                FrameLayout.LayoutParams containerLayoutParams = new FrameLayout.LayoutParams(width,
                                        height);
                                containerLayoutParams.setMargins(x, y, 0, 0);
                                cordova.getActivity().addContentView(containerView, containerLayoutParams);
                            }
                            cordova.getActivity().getWindow().getDecorView().setBackgroundColor(Color.BLACK);
                            webViewParent = webView.getView().getParent();
                            webView.getView().bringToFront();
                            cordova.getActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(containerViewId, fragment).commitAllowingStateLoss();
                        }
                    },
                    null);
            cordova.getActivity().runOnUiThread(addViewTask);
            addViewTask.get();
        } catch (Exception e) {

        }
        return true;
    }
}