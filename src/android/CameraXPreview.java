package com.cordovaplugincamerax;

import android.Manifest;
import android.util.Log;
import android.util.Size;
import android.view.ViewGroup;
import android.view.ViewParent;
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
    private static final String TAG = "CameraPreview";
    private static final String START_CAMERA_ACTION = "startCameraX";
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

    private void setupPreviewView(int x, int y, int width, int height) {
        this.previewView = new PreviewView(cordova.getActivity());
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(1080, 1440);
        layoutParams.leftMargin = x;
        layoutParams.topMargin = y;
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