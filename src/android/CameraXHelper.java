package com.cordovaplugincamerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.hardware.camera2.CaptureRequest;
import android.util.DisplayMetrics;
import android.util.Size;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraProvider;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@ExperimentalGetImage
public class CameraXHelper {
    private static CameraXHelper helper = null;
    private PreviewView previewView;
    private Camera cameraInstance;
    private ImageCapture imageCapture;

    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private String recordFilePath;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector;
    private Preview preview;
    private boolean recordingStoppedByUser = false;
    private final CordovaInterface cordova;
    private final CordovaWebView webView;
    private final CordovaPlugin plugin;
    private static final int CAM_REQ_CODE = 0;
    private static final String[] imagePermissions = {
            Manifest.permission.CAMERA,
    };
    private static final String[] videoPermissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
    };

    private OrientationEventListener mOrientationEventListener;
    private int mOrientation = 0;

    private CameraXHelper(CordovaInterface cordovaInterface, CordovaWebView cordovaWebView,
            CordovaPlugin cordovaPlugin) {
        cordova = cordovaInterface;
        webView = cordovaWebView;
        plugin = cordovaPlugin;
        registerOrientationListener();
    }

    private void registerOrientationListener() {
        if (mOrientationEventListener == null) {
            mOrientationEventListener = new OrientationEventListener(this.cordova.getActivity(), SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    mOrientation = orientation;
                }
            };
        }
    }

    private void enableOrientationListener() {
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }
    }

    private void disableOrientationListener() {
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.disable();
        }
    }

    public static CameraXHelper getInstance(CordovaInterface cordovaInterface, CordovaWebView cordovaWebView,
            CordovaPlugin cordovaPlugin) {
        if (helper == null) {
            helper = new CameraXHelper(cordovaInterface, cordovaWebView, cordovaPlugin);
        }
        return helper;
    }

    public boolean startCameraX(int x, int y, int width, int height, int targetPictureWidth, int targetPictureHeight, CallbackContext callbackContext) {
        enableOrientationListener();
        if (cordova.hasPermission(imagePermissions[0])) {
            cordova.getActivity().runOnUiThread(() -> {
                setupPreviewView(x, y, width, height);
                cameraProviderFuture = ProcessCameraProvider.getInstance(cordova.getActivity());
                cameraProviderFuture.addListener(() -> setupPreviewUseCasesAndInitCameraInstance(callbackContext, targetPictureWidth, targetPictureHeight),
                        ContextCompat.getMainExecutor(cordova.getContext()));
            });
        } else {
            cordova.requestPermissions(plugin, CAM_REQ_CODE, imagePermissions);
            callbackContext.error("Camera permission not allowed");
        }
        return true;
    }

    public boolean stopCameraX(CallbackContext callbackContext) {
        disableOrientationListener();
        if (cameraInstance != null) {
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProviderFuture.get().unbindAll();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
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

    public boolean takePicture(int quality, String targetFileName, int orientation,
            CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if(imageCapture == null) {
                    addImageCaptureUseCase(1200, 1600);
                }
                imageCapture.setTargetRotation(getTargetRotation());
                imageCapture.takePicture(getExecutor(),
                        new ImageCapture.OnImageCapturedCallback() {
                            @Override
                            public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                                processImage(imageProxy, quality, targetFileName, callbackContext);
                            }

                            @Override
                            public void onError(@NonNull ImageCaptureException exception) {
                                callbackContext.error(exception.getMessage());
                            }
                        });
            } catch (Exception e) {
                callbackContext.error(e.getMessage());
            }
        });
        return true;
    }

    private void addImageCaptureUseCase(int width, int height) {
        try {
            imageCapture = setupImageCaptureUseCase(width, height);
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbind(videoCapture);
            videoCapture = null;
            cameraProvider.bindToLifecycle(cordova.getActivity(), cameraSelector, preview, imageCapture);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    private int getTargetRotation() {
        if(mOrientation >= 45 && mOrientation < 135) {
            return Surface.ROTATION_270;
        } else if(mOrientation >= 135 && mOrientation < 225) {
            return Surface.ROTATION_180;
        } else if(mOrientation >= 225 && mOrientation < 315) {
            return Surface.ROTATION_90;
        } else {
            return Surface.ROTATION_0;
        }
    }

    private void processImage(ImageProxy imageProxy, int quality, String targetFileName,
            CallbackContext callbackContext) {
        ImageHelper imageHelper = new ImageHelper();
        Bitmap outputImage = imageHelper.rotateAndReturnImage(imageProxy);
        saveBitmapToFile(outputImage, targetFileName, quality, callbackContext);

        Bitmap thumbnailImage = imageHelper.createThumbnailImage(outputImage);
        saveBitmapToFile(thumbnailImage, getThumbnailFilename(targetFileName), Math.max(quality - 20, 20),
                callbackContext);

        sendPluginResult(targetFileName, callbackContext);
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

    public boolean setZoom(float zoomRatio, CallbackContext callbackContext) {
        if (cameraInstance == null) {
            callbackContext.error("no camera instance");
        }
        cameraInstance.getCameraControl().setZoomRatio(zoomRatio);
        notifyZoomRatioUpdate(zoomRatio);
        callbackContext.success();
        return true;
    }

    public boolean getMaxZoom(CallbackContext callbackContext) {
        if (cameraInstance == null) {
            callbackContext.error("no camera instance");
        }
        ZoomState zoomState = cameraInstance.getCameraInfo().getZoomState().getValue();
        if (zoomState != null) {
            float zoomRatio = zoomState.getMaxZoomRatio();
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, zoomRatio);
            callbackContext.sendPluginResult(pluginResult);
        } else {
            callbackContext.error("Zoom State is null");
        }

        return true;
    }

    public boolean getFlashMode(CallbackContext callbackContext) {
        if (imageCapture == null) {
            callbackContext.error("no camera instance");
        }
        int mode = imageCapture.getFlashMode();
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, mode);
        callbackContext.sendPluginResult(pluginResult);
        return true;
    }

    public boolean setFlashMode(String flashMode, CallbackContext callbackContext) {
        if (imageCapture == null) {
            callbackContext.error("no camera instance");
        }
        int mode = getFlashModeAsInteger(flashMode);
        imageCapture.setFlashMode(mode);
        callbackContext.success();
        return true;
    }

    private int getFlashModeAsInteger(String flashMode) {
        switch (flashMode) {
            case "on":
                return 1;
            case "off":
                return 2;
            default:
                return 0;
        }
    }

    public boolean startRecording(String fileName, int durationLimit, CallbackContext callbackContext) {
        if (cordova.hasPermission(videoPermissions[0]) && cordova.hasPermission(videoPermissions[1])) {
            cordova.getActivity().runOnUiThread(() -> {
                try {
                    if(videoCapture == null) {
                        addVideoCaptureUseCase(callbackContext);
                    }


                    recordingStoppedByUser = false;
                    recordFilePath = cordova.getActivity().getFileStreamPath(fileName).toString();

                    File newFile = new File(recordFilePath);
                    FileOutputOptions options = new FileOutputOptions.Builder(newFile)
                            .build();

                    if (ActivityCompat.checkSelfPermission(cordova.getActivity(),
                            Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        callbackContext.error("Permission not allowed");
                    }
                    recording = videoCapture.getOutput().prepareRecording(cordova.getActivity(), options)
                            .withAudioEnabled()
                            .start(getExecutor(), videoRecordEvent -> {
                                if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                                    stopRecordingAfterMilliseconds((durationLimit + 1) * 1000L);
                                    callbackContext.success();
                                }
                                if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    notifyRecordedVideoPath((VideoRecordEvent.Finalize) videoRecordEvent);
                                }
                            });
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            });
        } else {
            cordova.requestPermissions(plugin, CAM_REQ_CODE, videoPermissions);
            callbackContext.error("Camera permission not allowed");
        }
        return true;
    }

    private void addVideoCaptureUseCase(CallbackContext callbackContext) {
        try {
            videoCapture = setupVideoCaptureUseCase();
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbind(imageCapture);
            imageCapture = null;
            cameraProvider.bindToLifecycle(cordova.getActivity(), cameraSelector, preview, videoCapture);
        } catch (ExecutionException e) {
            callbackContext.error(e.getMessage());
        } catch (InterruptedException e) {
            callbackContext.error(e.getMessage());
        }

    }

    private void stopRecordingAfterMilliseconds(long milliseconds) {
        delayInMilliseconds(() -> {
            if (recording != null) {
                recording.stop();
            }
        }, milliseconds);
    }

    private void notifyRecordedVideoPath(VideoRecordEvent.Finalize event) {
        cordova.getActivity().runOnUiThread(() -> {
            if (!recordingStoppedByUser) {
                String filePath = event.getOutputResults().getOutputUri().toString();
                webView.loadUrl("javascript:" + "cordova.fireDocumentEvent('videoRecorderUpdate', {filePath: '"
                        + filePath + "' }, true);");
            }
        });
    }

    public boolean stopRecording(CallbackContext callbackContext) {
        recordingStoppedByUser = true;
        recording.stop();
        recording = null;
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, recordFilePath);
        delayInMilliseconds(() -> callbackContext.sendPluginResult(pluginResult), 1000);
        return true;
    }

    private void delayInMilliseconds(Runnable runnable, long duration) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }
        }, duration);
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

    private void setupPreviewUseCasesAndInitCameraInstance(CallbackContext callbackContext, int targetPictureWidth, int targetPictureHeight) {
        try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraSelector = setupCameraSelectorUseCase();
            preview = setupPreviewUseCase();

            // The reason we don't bind imageCapture and videoCapture use cases here
            // is because on some phones, it is only allowed to bind either imageCapture or videoCapture at a time.
            cameraInstance = cameraProvider.bindToLifecycle(
                    cordova.getActivity(), cameraSelector, preview);

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
        tapToFocusAndPinchToZoom();
        preview.setSurfaceProvider(this.previewView.getSurfaceProvider());
        return preview;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void tapToFocusAndPinchToZoom() {
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(cordova.getActivity(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        ZoomState zoomState = cameraInstance.getCameraInfo().getZoomState().getValue();
                        if (zoomState != null) {
                            float scale = zoomState.getZoomRatio() * detector.getScaleFactor();
                            cameraInstance.getCameraControl().setZoomRatio(scale);
                            notifyZoomRatioUpdate(scale);
                        }

                        return true;
                    }
                });
        previewView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                triggerAutoFocusAndMetering(event.getX(), event.getY());
            } else {
                scaleGestureDetector.onTouchEvent(event);
            }
            return true;
        });
    }

    private void notifyZoomRatioUpdate(float ratio) {
        cordova.getActivity().runOnUiThread(() -> {
            String statement = "cordova.fireDocumentEvent('zoomRatioUpdate', {ratio:'" + ratio + "'}, true);";
            webView.loadUrl("javascript:" + statement);
        });
    }

    private CameraSelector setupCameraSelectorUseCase() {
        return new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();
    }

    private ImageCapture setupImageCaptureUseCase(int targetPictureWidth, int targetPictureHeight) {
        ImageCapture.Builder builder = new ImageCapture.Builder();
        builder.setTargetResolution(new Size(targetPictureWidth, targetPictureHeight));
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

    private void triggerAutoFocusAndMetering(float x, float y) {
        CameraControl cameraControl = cameraInstance.getCameraControl();

        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
        cameraControl.startFocusAndMetering(action);
    }

    private VideoCapture<Recorder> setupVideoCaptureUseCase() {
        QualitySelector qualitySelector = QualitySelector.from(Quality.SD,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.SD));
        Recorder recorder = new Recorder.Builder()
                .setExecutor(getExecutor())
                .setQualitySelector(qualitySelector)
                .build();
        return VideoCapture.withOutput(recorder);
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(cordova.getContext());
    }
}
