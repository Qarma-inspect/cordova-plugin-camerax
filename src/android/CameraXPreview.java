package com.cordovaplugincamerax;

import android.Manifest;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.ExecutionException;

public class CameraXPreview extends CordovaPlugin {
    private static final String TAG = "CameraXPreview";
    private static final String START_CAMERA_ACTION = "startCameraX";
    private static final String STOP_CAMERA_ACTION = "stopCameraX";
    
    private static final int CAM_REQ_CODE = 0;

    private static final String[] permissions = {
            Manifest.permission.CAMERA
    };

    private CallbackContext execCallback;
    private JSONArray execArgs;

    private PreviewView previewView;
    private Camera camera;
    private ImageCapture imageCapture;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    public CameraXPreview() {
        super();
        Log.d(TAG, "Constructing");
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        Log.d(TAG, "Called CameraPreview plugin with action : " + action);
        if (START_CAMERA_ACTION.equals(action)) {
            if (cordova.hasPermission(permissions[0])) {
                return startCameraX(args.getInt(0), args.getInt(1), args.getInt(2), args.getInt(3), args.getString(8),
                        callbackContext);
            } else {
                this.execCallback = callbackContext;
                this.execArgs = args;
                cordova.requestPermissions(this, CAM_REQ_CODE, permissions);
            }
        } else if(STOP_CAMERA_ACTION.equals(action)) {
            return stopCameraX(callbackContext);
        }
        return false;
    }

    private boolean startCameraX(int x, int y, int width, int height, String alpha, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            setupPreviewView(x, y, width, height);

            // Initialize the cameraProviderFuture using ProcessCameraProvider
            this.cameraProviderFuture = ProcessCameraProvider.getInstance(cordova.getActivity());
            cameraProviderFuture.addListener(() -> {
                setupUseCases(callbackContext);
            }, ContextCompat.getMainExecutor(cordova.getContext()));
        });
        return true;
    }

    private boolean stopCameraX(CallbackContext callbackContext) {
        if (camera != null) {
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProviderFuture.get().unbindAll();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                camera = null;

                // Remove the dynamic PreviewView from the Cordova activity's layout
                ViewGroup parentView = (ViewGroup) this.previewView.getParent();
                if (parentView != null) {
                    parentView.removeView(this.previewView);
                }

                callbackContext.success();
            }, ContextCompat.getMainExecutor(cordova.getContext()));
        }
        return true;
    }

    private void setupPreviewView(int x, int y, int width, int height) {
        this.previewView = new PreviewView(cordova.getActivity());

        DisplayMetrics metrics = cordova.getActivity().getResources().getDisplayMetrics();
        // offset
        int computedX = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, x, metrics);
        int computedY = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, y, metrics);

        // size
        int computedWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, width, metrics);
        int computedHeight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, height, metrics);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(computedWidth, computedHeight);
        layoutParams.leftMargin = computedX;
        layoutParams.topMargin = computedY;
        this.previewView.setLayoutParams(layoutParams);
        cordova.getActivity().addContentView(this.previewView, layoutParams);
    }

    private void setupUseCases(CallbackContext callbackContext) {
        try {
            // Get the ProcessCameraProvider from the cameraProviderFuture
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

            // Set up the Preview use case with the desired resolution
            Preview preview = new Preview.Builder()
                    .setTargetResolution(new Size(3000, 4000))
                    .build();
            preview.setSurfaceProvider(this.previewView.getSurfaceProvider());

            // Set up the CameraSelector to use the rear camera
            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            // Create the Camera instance
            this.camera = cameraProvider.bindToLifecycle(
                    cordova.getActivity(), cameraSelector, preview);

            // Set up the ImageCapture use case
            this.imageCapture = new ImageCapture.Builder().build();
            callbackContext.success();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        }
    }
}