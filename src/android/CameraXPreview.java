package com.cordovaplugincamerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.cordovaplugincamerapreview.RectMathUtil;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@ExperimentalGetImage public class CameraXPreview extends CordovaPlugin {
    private static final String TAG = "CameraXPreview";
    private static final String START_CAMERA_ACTION = "startCameraX";
    private static final String STOP_CAMERA_ACTION = "stopCameraX";
    private static final String TAKE_PICTURE_ACTION = "takePictureWithCameraX";
    private static final String GET_MAX_ZOOM = "getMaxZoomCameraX";

    private static final String SET_ZOOM = "setZoomCameraX";

    private static final int CAM_REQ_CODE = 0;

    private static final String[] permissions = {
            Manifest.permission.CAMERA
    };

    private PreviewView previewView;
    private Camera cameraInstance;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector;
    private ProcessCameraProvider cameraProvider;
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
                cordova.requestPermissions(this, CAM_REQ_CODE, permissions);
            }
        } else if (STOP_CAMERA_ACTION.equals(action)) {
            return stopCameraX(callbackContext);
        } else if (TAKE_PICTURE_ACTION.equals(action)) {
            return takePicture(args.getInt(2),
                    args.getString(3),
                    args.getInt(4),
                    callbackContext);
        } else if(SET_ZOOM.equals(action)) {
            return setZoom((float) args.getDouble(0), callbackContext);
        } else if(GET_MAX_ZOOM.equals(action)) {
            return getMaxZoom(callbackContext);
        }
        return false;
    }

    private boolean startCameraX(int x, int y, int width, int height, String alpha, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            setupPreviewView(x, y, width, height);

            cameraProviderFuture = ProcessCameraProvider.getInstance(cordova.getActivity());
            cameraProviderFuture.addListener(() -> {
                setupUseCasesAndInitCameraInstance(callbackContext);
            }, ContextCompat.getMainExecutor(cordova.getContext()));
        });
        return true;
    }

    private boolean stopCameraX(CallbackContext callbackContext) {
        if (cameraInstance != null) {
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProviderFuture.get().unbindAll();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cameraInstance = null;

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

    private boolean takePicture(int quality, String targetFileName, int orientation,
            CallbackContext callbackContext) {
        try {
            imageCapture.takePicture(getExecutor(),
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                            processImage(imageProxy, quality, targetFileName, orientation, callbackContext);
                        }

                        @Override
                        public void onError(@NonNull ImageCaptureException exception) {
                            callbackContext.error(exception.getMessage());
                        }
                    });
        } catch (Exception e) {
            callbackContext.error(e.getMessage());
        }

        return true;
    }

    private boolean setZoom(float zoomRatio, CallbackContext callbackContext) {
        if(cameraInstance == null) {
            callbackContext.error("no camera instance");
        }
        cameraInstance.getCameraControl().setZoomRatio(zoomRatio);
        callbackContext.success();
        return true;
    }

    private boolean getMaxZoom(CallbackContext callbackContext) {
        if(cameraInstance == null) {
            callbackContext.error("no camera instance");
        }
        float zoomRatio = cameraInstance.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, zoomRatio);
        callbackContext.sendPluginResult(pluginResult);
        return true;
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(cordova.getContext());
    }

    private void processImage(ImageProxy imageProxy, int quality, String targetFileName, int orientation,
            CallbackContext callbackContext) {
        Bitmap outputImage = rotateAndReturnImage(imageProxy, orientation, callbackContext);
        saveBitmapToFile(outputImage, targetFileName, quality, callbackContext);

        Bitmap thumbnailImage = createThumbnailImage(outputImage);
        saveBitmapToFile(thumbnailImage, getThumbnailFilename(targetFileName), Math.max(quality - 20, 20),
                callbackContext);

        sendPluginResult(targetFileName, callbackContext);
    }

    @SuppressLint("RestrictedApi")
    private Bitmap rotateAndReturnImage(ImageProxy imageProxy, int orientation, CallbackContext callbackContext) {
        byte[] data = imageProxyToByteArray(imageProxy);
        Matrix matrix = new Matrix();
        matrix.preRotate(getImageRotationAngle(orientation));
        Bitmap bitmapImage = BitmapFactory.decodeByteArray(data, 0, data.length);
        Bitmap outputImage = applyMatrix(bitmapImage, matrix);
        return outputImage;
    }

    private float getImageRotationAngle(int orientation) {
        // TODO Find a way to get the returned image in a correct rotation, so that we wont need to do post-processing
        switch (orientation) {
            case 0: // portrait-primary
                return 90;
            case 180: // portrait-secondary
                return 270;
            case 90: // landscape-primary
                return 180;
            default: // landscape-secondary
                return 0;
        }
    }

    private byte[] imageProxyToByteArray(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();

        Image.Plane[] planes = image.getPlanes();

        // Calculate the total buffer size required
        int bufferSize = 0;
        for (Image.Plane plane : planes) {
            bufferSize += plane.getBuffer().remaining();
        }

        byte[] byteArray = new byte[bufferSize];

        // Copy the pixel data from each plane into the byte array
        int offset = 0;
        for (Image.Plane plane : planes) {
            ByteBuffer buffer = plane.getBuffer();
            int length = buffer.remaining();
            buffer.get(byteArray, offset, length);
            offset += length;
        }

        // Close the Image and ImageProxy
        image.close();
        imageProxy.close();

        return byteArray;
    }
    private void saveBitmapToFile(Bitmap bitmap, String fileName, Integer quality, CallbackContext callbackContext) {
        Context context = cordova.getContext();
        try {
            FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
        } catch (IOException e) {
            callbackContext.error(e.getMessage());
        }
    }

    private Bitmap createThumbnailImage(Bitmap bitmap) {
        Rect scaledRect = RectMathUtil.contain(bitmap.getWidth(), bitmap.getHeight(), 200, 200);
        // turn image to correct aspect ratio.
        Bitmap partOfImage = Bitmap.createBitmap(bitmap, scaledRect.left, scaledRect.top, scaledRect.width(),
                scaledRect.height());
        // scale down without stretching.
        return Bitmap.createScaledBitmap(partOfImage, 200, 200, true);
    }

    private void sendPluginResult(String targetFileName, CallbackContext callbackContext) {
        JSONArray output = new JSONArray();
        output.put(targetFileName);
        output.put(getThumbnailFilename(targetFileName));

        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, output);
        callbackContext.sendPluginResult(pluginResult);
    }

    private String getThumbnailFilename(String fileName) {
        return "thumb-" + fileName;
    }

    private Bitmap applyMatrix(Bitmap source, Matrix matrix) {
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private void setupPreviewView(int x, int y, int width, int height) {
        previewView = new PreviewView(cordova.getActivity());

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
        previewView.setLayoutParams(layoutParams);
        cordova.getActivity().addContentView(previewView, layoutParams);
    }

    private void setupUseCasesAndInitCameraInstance(CallbackContext callbackContext) {
        try {
            cameraProvider = cameraProviderFuture.get();
            cameraSelector = setupCameraSelectorUseCase();
            Preview preview = setupPreviewUseCase();

            imageCapture = setupImageCaptureUseCase();

            // Create the Camera instance
            cameraInstance = cameraProvider.bindToLifecycle(
                    cordova.getActivity(), cameraSelector, preview, imageCapture);

            callbackContext.success();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        }
    }

    private Preview setupPreviewUseCase() {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        pinchToZoom();
        preview.setSurfaceProvider(this.previewView.getSurfaceProvider());
        return preview;
    }

    private void pinchToZoom() {
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(cordova.getActivity(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float scale = (float) (cameraInstance.getCameraInfo().getZoomState().getValue().getZoomRatio() * detector.getScaleFactor());
                cameraInstance.getCameraControl().setZoomRatio(scale);
                return true;
            }
        });
        previewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                scaleGestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    private CameraSelector setupCameraSelectorUseCase() {
        return new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
    }

    private ImageCapture setupImageCaptureUseCase() {

        ImageCapture.Builder builder = new ImageCapture.Builder();

        builder.setTargetResolution(new Size(1200, 1600));
        turnOffNoiseReduction(builder);

        return builder.build();
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void turnOffNoiseReduction(ImageCapture.Builder builder) {
        Camera2Interop.Extender extender = new Camera2Interop.Extender(builder);
        extender.setCaptureRequestOption(
                CaptureRequest.NOISE_REDUCTION_MODE,
                CaptureRequest.NOISE_REDUCTION_MODE_OFF);
    }
}