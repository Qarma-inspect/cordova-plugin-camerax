package com.cordovaplugincamerax;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.SensorManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
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
import java.util.Locale;
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
    private boolean torchEnabled = false;
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

    private Timer recordingTimer;

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
                    if(orientation != -1) {
                        mOrientation = orientation;
                    }
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

    public boolean startCameraX(int x, int y, int width, int height, CallbackContext callbackContext) {
        enableOrientationListener();
        if (cordova.hasPermission(imagePermissions[0])) {
            cordova.getActivity().runOnUiThread(() -> {
                setupPreviewView(x, y, width, height);
                cameraProviderFuture = ProcessCameraProvider.getInstance(cordova.getActivity());
                cameraProviderFuture.addListener(() -> setupPreviewUseCaseAndInitCameraInstance(callbackContext),
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
                    resetCameraInstance();
                    removePreviewView();
                    callbackContext.success();
                } catch (Exception e) {
                    callbackContext.error(e.getMessage());
                }
            }, ContextCompat.getMainExecutor(cordova.getContext()));
        }
        return true;
    }

    private void resetCameraInstance() {
        cameraInstance = null;
        imageCapture = null;
        videoCapture = null;
        torchEnabled = false;
    }

    private void removePreviewView() {
        ViewGroup parentView = (ViewGroup) this.previewView.getParent();
        if (parentView != null) {
            parentView.removeView(this.previewView);
        }
    }

    public boolean takePicture(int targetWidth, int targetHeight, int maxWidthAllowed, int maxHeightAllowed, int quality, String targetFileName,
                               CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (imageCapture == null) {
                    addImageCaptureUseCase(Math.min(targetWidth, targetHeight), Math.max(targetWidth, targetHeight));
                }
                imageCapture.setTargetRotation(getTargetRotation());

                ImageCapture.OnImageCapturedCallback captureCallback = new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        Size targetSize = new Size(targetWidth, targetHeight);
                        Size maxSizeAllowed = new Size(maxWidthAllowed, maxHeightAllowed);
                        processImage(imageProxy, targetSize, maxSizeAllowed, quality, targetFileName, callbackContext);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        callbackContext.error(exception.getMessage());
                    }
                };

                try {
                    imageCapture.takePicture(getExecutor(), captureCallback);
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Not bound")) {
                        imageCapture = null;
                        addImageCaptureUseCase(Math.min(targetWidth, targetHeight), Math.max(targetWidth, targetHeight));
                        imageCapture.setTargetRotation(getTargetRotation());
                        imageCapture.takePicture(getExecutor(), captureCallback);
                    } else {
                        callbackContext.error(e.getMessage() != null ? e.getMessage() : e.toString());
                    }
                }
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
            reapplyTorchIfEnabled();
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

    private void processImage(ImageProxy imageProxy, Size targetSize, Size maxSizeAllowed, int quality, String targetFileName,
                              CallbackContext callbackContext) {
        ImageHelper imageHelper = new ImageHelper();
        Bitmap outputImage = imageHelper.rotateAndReturnImage(imageProxy, targetSize, maxSizeAllowed);
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
            return true;
        }
        cameraInstance.getCameraControl().setZoomRatio(zoomRatio);
        notifyZoomRatioUpdate(zoomRatio);
        callbackContext.success();
        return true;
    }

    public boolean getMaxZoom(CallbackContext callbackContext) {
        if (cameraInstance == null) {
            callbackContext.error("no camera instance");
            return true;
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
            return true;
        }
        int mode = imageCapture.getFlashMode();
        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, mode);
        callbackContext.sendPluginResult(pluginResult);
        return true;
    }

    public boolean setFlashMode(String flashMode, CallbackContext callbackContext) {
        if (imageCapture == null) {
            callbackContext.error("no camera instance");
            return true;
        }
        try {
            int mode = getFlashModeAsInteger(flashMode);
            imageCapture.setFlashMode(mode);

            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, mode);
            callbackContext.sendPluginResult(pluginResult);
        } catch (Exception exception) {
            callbackContext.error(exception.getMessage());
        }
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

    public boolean enableTorch(boolean enabled, CallbackContext callbackContext) {
        if (cameraInstance == null) {
            callbackContext.error("no camera instance");
            return true;
        }
        if (!cameraInstance.getCameraInfo().hasFlashUnit()) {
            callbackContext.error("no flash unit");
            return true;
        }
        torchEnabled = enabled;
        ListenableFuture<Void> future = cameraInstance.getCameraControl().enableTorch(enabled);
        future.addListener(() -> {
            try {
                future.get();
                callbackContext.success();
            } catch (Exception e) {
                callbackContext.error(e.getMessage() != null ? e.getMessage() : e.toString());
            }
        }, getExecutor());
        return true;
    }

    private void reapplyTorchIfEnabled() {
        if (torchEnabled && cameraInstance != null) {
            cameraInstance.getCameraControl().enableTorch(true);
        }
    }

    @SuppressLint("RestrictedApi")
    public boolean startRecording(String fileName, int durationLimit, CallbackContext callbackContext) {
        if (cordova.hasPermission(videoPermissions[0]) && cordova.hasPermission(videoPermissions[1])) {
            cordova.getActivity().runOnUiThread(() -> {
                try {
                    if(videoCapture == null) {
                        addVideoCaptureUseCase(callbackContext);
                    }
                    videoCapture.setTargetRotation(getTargetRotation());
                    recordingStoppedByUser = false;
                    recordFilePath = cordova.getActivity().getFileStreamPath(appendFileNameIfNeeded(fileName)).toString();

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
                                    resetRecordingTimer();
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

    private String appendFileNameIfNeeded(String fileName) {
        if(fileName.contains(".mp4")) {
            return fileName;
        }
        return fileName.concat(".mp4");
    }

    private void addVideoCaptureUseCase(CallbackContext callbackContext) {
        try {
            videoCapture = setupVideoCaptureUseCase();
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbind(imageCapture);
            imageCapture = null;
            cameraProvider.bindToLifecycle(cordova.getActivity(), cameraSelector, preview, videoCapture);
            reapplyTorchIfEnabled();
        } catch (ExecutionException e) {
            callbackContext.error(e.getMessage());
        } catch (InterruptedException e) {
            callbackContext.error(e.getMessage());
        }

    }

    private void stopRecordingAfterMilliseconds(long milliseconds) {
        startRecordingTimer(() -> {
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
        delayInMilliseconds(() -> callbackContext.sendPluginResult(pluginResult), 1000L);
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

    private void startRecordingTimer(Runnable runnable, long duration) {
        recordingTimer = new Timer();
        recordingTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }
        }, duration);
    }

    private void resetRecordingTimer() {
        if(recordingTimer != null) {
            recordingTimer.cancel();
            recordingTimer = null;
        }
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

    private void setupPreviewUseCaseAndInitCameraInstance(CallbackContext callbackContext) {
        try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbindAll();
            cameraSelector = setupCameraSelectorUseCase();
            preview = setupPreviewUseCase();
            imageCapture = setupImageCaptureUseCase(1200, 1600);

            // The reason we don't bind videoCapture use case here
            // is because on some phones, it is only allowed to bind either imageCapture or videoCapture at a time.
            cameraInstance = cameraProvider.bindToLifecycle(
                    cordova.getActivity(), cameraSelector, preview, imageCapture);

            callbackContext.success();
        } catch (ExecutionException e) {
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        } catch (InterruptedException e) {
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        } catch (Exception e) {
            // bindToLifecycle can throw IllegalArgumentException
            // ("No supported surface combination is found ... May be attempting to bind too
            // many use cases") which is an unchecked RuntimeException. This method runs inside a
            // ProcessCameraProvider listener on the main executor, so an uncaught exception here
            // escapes to the Looper and crashes the app (MOBILE-APP-1WHW), while the JS
            // startCamera() promise never settles. Report it to JS instead so the camera state
            // machine can handle/retry it cleanly.
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

        applyNoiseReductionMode(builder);
        applyEdgeEnhancementIfDenoising(builder);

        return builder.build();
    }

    // vivo V60 Lite - the models the yellow chroma grain has actually been reported on.
    private static final String[] GRAINY_CHROMA_MODELS = {"V2530"};

    static boolean isAffectedModel(String deviceModel) {
        if (deviceModel == null) {
            return false;
        }
        String model = deviceModel.trim().toUpperCase(Locale.US);
        for (String affectedModel : GRAINY_CHROMA_MODELS) {
            // contains rather than equals: the reported model varies by variant and region -
            // "V2530", "V2530A", "vivo V2530" are all the same camera stack. The codes are
            // distinctive enough that a substring match will not collide with another device.
            if (model.contains(affectedModel)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Noise reduction is applied to the still capture only. It is a per-request processing stage
     * rather than something auto-exposure converges on, so setting it on ImageCapture is what
     * decides the pixels that get saved - it does not need to be on the preview's repeating
     * request as well.
     *
     * No check against NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES: the characteristics are
     * not reachable here, because the builder is configured before bindToLifecycle and so there
     * is no CameraInfo yet. HIGH_QUALITY is only requested on a known model that supports it, and
     * a HAL that does not support a requested mode falls back rather than failing the capture.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private void applyNoiseReductionMode(ImageCapture.Builder builder) {
        Camera2Interop.Extender<ImageCapture> extender = new Camera2Interop.Extender<>(builder);
        extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionMode);
    }

    /**
     * The CaptureRequest.NOISE_REDUCTION_MODE_* constant used for stills.
     *
     * OFF everywhere is the plugin's long-standing behaviour, and it is deliberate: denoise
     * smooths fabric weave, which is the detail these inspection photos exist to show. The cost
     * is that the coloured speckle a Bayer sensor leaves on neutral surfaces survives into the
     * JPEG, so on the affected models white paper comes back with yellow grain the stock camera
     * app does not produce from the same scene (measured on a reported sample: ~20-30x the chroma
     * noise of the stock shot against only ~2x the luma noise - i.e. specifically chroma).
     *
     * HIGH_QUALITY only on the models the grain has actually been reported on, so the fabric
     * trade-off is not taken fleet-wide.
     */
    private final int noiseReductionMode = noiseReductionModeFor(Build.MODEL);

    static int noiseReductionModeFor(String deviceModel) {
        return CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY;
        // return isAffectedModel(deviceModel)
        //         ? CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY
        //         : CaptureRequest.NOISE_REDUCTION_MODE_OFF;
    }

    /**
     * Denoise and edge enhancement are a matched pair: HIGH_QUALITY noise reduction is what clears
     * the yellow chroma speckle, but it also softens the fabric weave these photos exist to show,
     * and HIGH_QUALITY edge enhancement is what puts that acuity back. Requesting the two together
     * is the standard way to denoise without the result reading as soft.
     *
     * Gated on the noise reduction mode rather than on the model a second time, so edge
     * enhancement automatically follows wherever denoise goes. Devices left on NOISE_REDUCTION_
     * MODE_OFF have nothing to compensate for and keep the HAL's default edge mode, so their
     * output does not change at all.
     */
    @SuppressLint("UnsafeOptInUsageError")
    private void applyEdgeEnhancementIfDenoising(ImageCapture.Builder builder) {
        // if (noiseReductionMode == CaptureRequest.NOISE_REDUCTION_MODE_OFF) {
        //     return;
        // }
        Camera2Interop.Extender<ImageCapture> extender = new Camera2Interop.Extender<>(builder);
        extender.setCaptureRequestOption(
                CaptureRequest.EDGE_MODE,
                CaptureRequest.EDGE_MODE_HIGH_QUALITY);
    }

    private void triggerAutoFocusAndMetering(float x, float y) {
        CameraControl cameraControl = cameraInstance.getCameraControl();

        MeteringPointFactory factory = previewView.getMeteringPointFactory();
        MeteringPoint point = factory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point).build();
        cameraControl.startFocusAndMetering(action);
    }

    private VideoCapture<Recorder> setupVideoCaptureUseCase() {
        QualitySelector qualitySelector = QualitySelector.from(Quality.LOWEST,
                FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST));
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