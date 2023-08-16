package com.cordovaplugincamerax;

import androidx.camera.core.ExperimentalGetImage;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

@ExperimentalGetImage
public class CameraXPlugin extends CordovaPlugin {
    private static final String START_CAMERA_ACTION = "startCameraX";
    private static final String STOP_CAMERA_ACTION = "stopCameraX";
    private static final String TAKE_PICTURE_ACTION = "takePictureWithCameraX";
    private static final String GET_MAX_ZOOM_ACTION = "getMaxZoomCameraX";
    private static final String SET_ZOOM_ACTION = "setZoomCameraX";
    private static final String GET_FLASH_MODE_ACTION = "getFlashModeCameraX";
    private static final String SET_FLASH_MODE_ACTION = "setFlashModeCameraX";
    private static final String START_RECORDING_ACTION = "startRecordingCameraX";
    private static final String STOP_RECORDING_ACTION = "stopRecordingCameraX";

    public CameraXPlugin() {
        super();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        CameraXHelper helper = CameraXHelper.getInstance(cordova, webView, this);
        switch (action) {
            case START_CAMERA_ACTION:
                return helper.startCameraX(args.getInt(0), args.getInt(1), args.getInt(2), args.getInt(3), args.getInt(4), args.getInt(5),
                        callbackContext);
            case STOP_CAMERA_ACTION:
                return helper.stopCameraX(callbackContext);
            case TAKE_PICTURE_ACTION:
                return helper.takePicture(args.getInt(2),
                        args.getString(3),
                        args.getInt(4),
                        callbackContext);
            case SET_ZOOM_ACTION:
                return helper.setZoom((float) args.getDouble(0), callbackContext);
            case GET_MAX_ZOOM_ACTION:
                return helper.getMaxZoom(callbackContext);
            case GET_FLASH_MODE_ACTION:
                return helper.getFlashMode(callbackContext);
            case SET_FLASH_MODE_ACTION:
                return helper.setFlashMode(args.getString(0), callbackContext);
            case START_RECORDING_ACTION:
                return helper.startRecording(args.getString(0), args.getInt(1), callbackContext);
            case STOP_RECORDING_ACTION:
                return helper.stopRecording(callbackContext);
            default:
                return false;
        }
    }
}