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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.Observer;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

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
    private PreviewView streamStateObservedView;
    private Observer<PreviewView.StreamState> streamStateObserver;
    private Camera cameraStateObservedCamera;
    private Observer<CameraState> cameraStateObserver;
    private boolean recordingStoppedByUser = false;
    private boolean torchEnabled = false;
    // Not final: Cordova builds a new CordovaInterfaceImpl, CordovaWebView and plugin instance
    // every time MainActivity is recreated, and this helper is a process-scoped singleton that
    // outlives them. See rebindTo().
    private CordovaInterface cordova;
    private CordovaWebView webView;
    private CordovaPlugin plugin;
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

    /**
     * QARMA-21334 diagnostics: reports native start/stop/bind/unbind timing and identity of the
     * previewView in play. Originally purely observational, used to confirm a suspected race
     * where a delayed stopCameraX() unbind (its callback reads the previewView/cameraInstance
     * fields live, not a value captured when stopCameraX() was called) ran after a subsequent
     * startCameraX() had already rebound the camera, silently tearing the live preview back out
     * while JS still believed it was active. stopCameraX() now guards against this directly (see
     * its staleUnbind check); these events remain to confirm how often it's still occurring.
     */
    private JSONObject diagnosticIds() {
        JSONObject o = new JSONObject();
        try {
            o.put("previewViewId", previewView != null ? System.identityHashCode(previewView) : -1);
        } catch (JSONException ignored) { }
        return o;
    }

    private JSONObject diagnosticIdsWithOk(boolean ok, String error) {
        JSONObject o = diagnosticIds();
        try {
            o.put("ok", ok);
            if (error != null) {
                o.put("error", error);
            }
        } catch (JSONException ignored) { }
        return o;
    }

    private JSONObject staleUnbindIds(int requestedPreviewViewId, int livePreviewViewId) {
        JSONObject o = new JSONObject();
        try {
            o.put("requestedPreviewViewId", requestedPreviewViewId);
            o.put("livePreviewViewId", livePreviewViewId);
        } catch (JSONException ignored) { }
        return o;
    }

    private JSONObject streamStateIds(PreviewView view, PreviewView.StreamState state) {
        JSONObject o = new JSONObject();
        try {
            o.put("previewViewId", System.identityHashCode(view));
            o.put("state", state.name());
        } catch (JSONException ignored) { }
        return o;
    }

    private JSONObject cameraStateIds(CameraState state) {
        JSONObject o = diagnosticIds();
        try {
            o.put("type", state.getType().name());
            o.put("error", state.getError() != null ? state.getError().getCode() : JSONObject.NULL);
        } catch (JSONException ignored) { }
        return o;
    }

    private void notifyDiagnostic(String kind, JSONObject extra) {
        JSONObject payload = extra != null ? extra : new JSONObject();
        try {
            payload.put("kind", kind);
            payload.put("ts", System.currentTimeMillis());
            payload.put("thread", Thread.currentThread().getName());
        } catch (JSONException ignored) { }
        String script = "cordova.fireDocumentEvent('cameraDiagnostic', " + payload.toString() + ", true);";
        cordova.getActivity().runOnUiThread(() -> webView.loadUrl("javascript:" + script));
    }

    private void attachStreamStateObserver(PreviewView view) {
        detachStreamStateObserver();
        streamStateObservedView = view;
        streamStateObserver = state -> notifyDiagnostic("stream-state", streamStateIds(view, state));
        cordova.getActivity().runOnUiThread(() -> view.getPreviewStreamState().observeForever(streamStateObserver));
    }

    private void detachStreamStateObserver() {
        if (streamStateObserver != null && streamStateObservedView != null) {
            PreviewView viewToDetach = streamStateObservedView;
            Observer<PreviewView.StreamState> observerToRemove = streamStateObserver;
            cordova.getActivity().runOnUiThread(() -> viewToDetach.getPreviewStreamState().removeObserver(observerToRemove));
            streamStateObserver = null;
            streamStateObservedView = null;
        }
    }

    /**
     * QARMA-21811: bindToLifecycle succeeds even when the camera device then fails to open
     * (in use by another client, disabled by policy, HAL fatal error, ...). Those failures only
     * surface through CameraInfo.getCameraState(), which nobody observed, so the app saw a
     * successful start followed by a black preview. Every state change is forwarded to JS as a
     * "camera-state" diagnostic; the ones carrying an error code fail the pending start there.
     */
    private void attachCameraStateObserver(Camera camera) {
        detachCameraStateObserver();
        cameraStateObservedCamera = camera;
        Observer<CameraState> observer = state -> notifyDiagnostic("camera-state", cameraStateIds(state));
        cameraStateObserver = observer;
        cordova.getActivity().runOnUiThread(() -> camera.getCameraInfo().getCameraState().observeForever(observer));
    }

    private void detachCameraStateObserver() {
        if (cameraStateObserver != null && cameraStateObservedCamera != null) {
            Camera cameraToDetach = cameraStateObservedCamera;
            Observer<CameraState> observerToRemove = cameraStateObserver;
            cordova.getActivity().runOnUiThread(() -> cameraToDetach.getCameraInfo().getCameraState().removeObserver(observerToRemove));
            cameraStateObserver = null;
            cameraStateObservedCamera = null;
        }
    }

    /**
     * QARMA-21811: a new session must never inherit native state from the previous one. The
     * Swoon Realme 14 Pro logged "Recording.stop() on a null object", "A recording is already in
     * progress" and "Not bound to a valid Camera" across sessions the app believed were closed.
     * Called on the UI thread at the start of every startCameraX().
     */
    private void resetSessionState() {
        if (recording != null) {
            // Its Finalize event is ignored by the identity check in startRecording's listener
            // once recording no longer points at it.
            try {
                recording.stop();
            } catch (Exception ignored) { }
            recording = null;
        }
        resetRecordingTimer();
        detachStreamStateObserver();
        detachCameraStateObserver();
        removePreviewView();
        resetCameraInstance();
    }

    /**
     * A start that failed after setupPreviewView() must not leave its (black) PreviewView on top
     * of the WebView, nor half-initialised fields for the next start or stop to trip over.
     */
    private void cleanupFailedStart() {
        try {
            if (cameraProviderFuture != null && cameraProviderFuture.isDone()) {
                cameraProviderFuture.get().unbindAll();
            }
        } catch (Exception ignored) { }
        detachStreamStateObserver();
        detachCameraStateObserver();
        removePreviewView();
        resetCameraInstance();
    }

    public static CameraXHelper getInstance(CordovaInterface cordovaInterface, CordovaWebView cordovaWebView,
                                            CordovaPlugin cordovaPlugin) {
        if (helper == null) {
            helper = new CameraXHelper(cordovaInterface, cordovaWebView, cordovaPlugin);
        } else {
            helper.rebindTo(cordovaInterface, cordovaWebView, cordovaPlugin);
        }
        return helper;
    }

    /**
     * Sentry MOBILE-APP-1V0D: this helper is a process-scoped singleton, but Cordova creates a
     * new CordovaInterfaceImpl, CordovaWebView and plugin instance every time MainActivity is
     * recreated (CordovaActivity.onCreate). getInstance() used to keep whatever was passed on
     * first use and discard every later set, so after one recreation cordova.getActivity()
     * returned the DESTROYED Activity for the rest of the process's life.
     *
     * CameraX does not report that. LifecycleCameraRepository.createLifecycleCamera() skips
     * registerCamera() when the lifecycle is DESTROYED, and bindToLifecycleCamera() then returns
     * early ("LifecycleCamera is not registered due to lifecycle destroyed, simply do nothing"),
     * so bindToLifecycle() throws nothing and hands back a Camera bound to nothing. Every start
     * failed the isBound() check below with "use cases not bound after bindToLifecycle", and
     * every JS retry failed identically because the state is permanent, not transient. The stale
     * webView also meant diagnostics and events were posted into a dead WebView.
     *
     * CameraXPlugin.execute() passes the live references on every call, so refreshing them here
     * is enough to keep the singleton pointed at the current Activity.
     */
    private void rebindTo(CordovaInterface cordovaInterface, CordovaWebView cordovaWebView,
                          CordovaPlugin cordovaPlugin) {
        boolean activityChanged = cordova == null
                || cordova.getActivity() != cordovaInterface.getActivity();
        cordova = cordovaInterface;
        webView = cordovaWebView;
        plugin = cordovaPlugin;
        if (activityChanged) {
            discardStateOfPreviousActivity();
        }
    }

    /**
     * The previous Activity is gone, and its PreviewView, observers and bound camera went with
     * it. Drop them without posting anything to the old Activity -- its views are already
     * detached and its lifecycle is destroyed -- and re-create the orientation listener against
     * the new one. The ProcessCameraProvider itself is process-scoped, so unbinding there is
     * both safe and necessary to release a camera the dead Activity still held.
     */
    private void discardStateOfPreviousActivity() {
        notifyDiagnostic("activity-replaced", diagnosticIds());
        if (recording != null) {
            try {
                recording.stop();
            } catch (Exception ignored) { }
            recording = null;
        }
        resetRecordingTimer();
        try {
            if (cameraProviderFuture != null && cameraProviderFuture.isDone()) {
                cameraProviderFuture.get().unbindAll();
            }
        } catch (Exception ignored) { }
        streamStateObserver = null;
        streamStateObservedView = null;
        cameraStateObserver = null;
        cameraStateObservedCamera = null;
        previewView = null;
        preview = null;
        cameraSelector = null;
        cameraProviderFuture = null;
        resetCameraInstance();
        if (mOrientationEventListener != null) {
            mOrientationEventListener.disable();
            mOrientationEventListener = null;
        }
        registerOrientationListener();
    }

    /**
     * CameraX binds silently to a destroyed lifecycle (see rebindTo()), so a start against a
     * dead Activity cannot be detected from bindToLifecycle()'s result. Check up front, so the
     * failure names its cause instead of surfacing as "use cases not bound".
     */
    private boolean isHostActivityDestroyed() {
        AppCompatActivity activity = cordova.getActivity();
        return activity == null
                || activity.isDestroyed()
                || activity.getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED;
    }

    public boolean startCameraX(int x, int y, int width, int height, CallbackContext callbackContext) {
        enableOrientationListener();
        notifyDiagnostic("start-requested", diagnosticIds());
        if (cordova.hasPermission(imagePermissions[0])) {
            cordova.getActivity().runOnUiThread(() -> {
                resetSessionState();
                setupPreviewView(x, y, width, height);
                notifyDiagnostic("preview-view-created", diagnosticIds());
                // QARMA-21811: the bind runs later, when the provider future completes. On a
                // cold start that is asynchronous, and a second startCameraX() can replace
                // previewView in between. Bind against the view THIS call created, not whatever
                // the field holds by then.
                final PreviewView requestedPreviewView = previewView;
                cameraProviderFuture = ProcessCameraProvider.getInstance(cordova.getActivity());
                cameraProviderFuture.addListener(() -> setupPreviewUseCaseAndInitCameraInstance(requestedPreviewView, callbackContext),
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
        final Camera requestedCameraInstance = cameraInstance;
        final PreviewView requestedPreviewView = previewView;
        final int requestedPreviewViewId = requestedPreviewView != null ? System.identityHashCode(requestedPreviewView) : -1;
        notifyDiagnostic("stop-requested", diagnosticIds());
        if (cameraInstance == null) {
            // QARMA-21811: this used to return without ever invoking the callback, so the JS
            // stop promise stayed pending and the camera state machine was stuck at STOPPING
            // (Done, Cancel and back all refused) whenever a stop followed a failed start.
            // There is nothing bound to unbind; a failed start already cleaned up after itself.
            notifyDiagnostic("stop-noop", diagnosticIds());
            callbackContext.success();
            return true;
        }
        cameraProviderFuture.addListener(() -> {
            int livePreviewViewId = previewView != null ? System.identityHashCode(previewView) : -1;
            boolean staleUnbind = cameraInstance != requestedCameraInstance || previewView != requestedPreviewView;
            if (staleUnbind) {
                // QARMA-21334 fix: this unbind was queued for the camera/previewView that
                // was live when stopCameraX() was called, but a newer startCameraX() has
                // since rebound to a different instance (or another stop already tore the
                // old one down). unbindAll()/removePreviewView() read those fields live, so
                // running them now would unbind and remove the CURRENT, unrelated session
                // instead of the one this call was asked to stop - silently blacking out a
                // camera screen the app still believes is active. Nothing this call targeted
                // is left to unbind, so skip the teardown and resolve as a no-op.
                notifyDiagnostic("stale-unbind-detected", staleUnbindIds(requestedPreviewViewId, livePreviewViewId));
                callbackContext.success();
                return;
            }
            notifyDiagnostic("unbind-begin", diagnosticIds());
            try {
                cameraProviderFuture.get().unbindAll();
                detachStreamStateObserver();
                detachCameraStateObserver();
                resetCameraInstance();
                removePreviewView();
                notifyDiagnostic("unbind-end", diagnosticIdsWithOk(true, null));
                callbackContext.success();
            } catch (Exception e) {
                notifyDiagnostic("unbind-end", diagnosticIdsWithOk(false, e.getMessage()));
                callbackContext.error(e.getMessage());
            }
        }, ContextCompat.getMainExecutor(cordova.getContext()));
        return true;
    }

    private void resetCameraInstance() {
        cameraInstance = null;
        imageCapture = null;
        videoCapture = null;
        torchEnabled = false;
    }

    private void removePreviewView() {
        if (this.previewView == null) {
            return;
        }
        ViewGroup parentView = (ViewGroup) this.previewView.getParent();
        if (parentView != null) {
            parentView.removeView(this.previewView);
        }
        this.previewView = null;
    }

    public boolean takePicture(int targetWidth, int targetHeight, int maxWidthAllowed, int maxHeightAllowed, int quality, String targetFileName,
                               int clockwiseAngleFromPortrait, CallbackContext callbackContext) {
        cordova.getActivity().runOnUiThread(() -> {
            try {
                if (imageCapture == null) {
                    addImageCaptureUseCase(Math.min(targetWidth, targetHeight), Math.max(targetWidth, targetHeight));
                }
                imageCapture.setTargetRotation(getTargetRotation(clockwiseAngleFromPortrait));

                final boolean[] rebound = {false};
                ImageCapture.OnImageCapturedCallback captureCallback = new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                        Size targetSize = new Size(targetWidth, targetHeight);
                        Size maxSizeAllowed = new Size(maxWidthAllowed, maxHeightAllowed);
                        processImage(imageProxy, targetSize, maxSizeAllowed, quality, targetFileName, callbackContext);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (exception.getImageCaptureError() == ImageCapture.ERROR_INVALID_CAMERA && !rebound[0]) {
                            // QARMA-21811: "Not bound to a valid Camera". The use case we hold
                            // was unbound behind our back (a stop that landed on the wrong
                            // session, an activity lifecycle event, ...). On CameraX 1.4 this
                            // arrives here through onError, not as the synchronous
                            // IllegalStateException the catch below was written for. Rebind
                            // preview + a fresh ImageCapture once and retry the shot.
                            rebound[0] = true;
                            notifyDiagnostic("capture-rebind", diagnosticIdsWithOk(false, exception.getMessage()));
                            ImageCapture.OnImageCapturedCallback retryCallback = this;
                            cordova.getActivity().runOnUiThread(() -> {
                                try {
                                    imageCapture = null;
                                    addImageCaptureUseCase(Math.min(targetWidth, targetHeight), Math.max(targetWidth, targetHeight));
                                    imageCapture.setTargetRotation(getTargetRotation(clockwiseAngleFromPortrait));
                                    imageCapture.takePicture(getExecutor(), retryCallback);
                                } catch (Exception e) {
                                    callbackContext.error(e.getMessage() != null ? e.getMessage() : e.toString());
                                }
                            });
                            return;
                        }
                        callbackContext.error(exception.getMessage());
                    }
                };

                try {
                    imageCapture.takePicture(getExecutor(), captureCallback);
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("Not bound")) {
                        // Older CameraX threw synchronously; count it as the one allowed rebind.
                        rebound[0] = true;
                        imageCapture = null;
                        addImageCaptureUseCase(Math.min(targetWidth, targetHeight), Math.max(targetWidth, targetHeight));
                        imageCapture.setTargetRotation(getTargetRotation(clockwiseAngleFromPortrait));
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
            if (videoCapture != null) {
                cameraProvider.unbind(videoCapture);
            }
            videoCapture = null;
            cameraInstance = cameraProvider.bindToLifecycle(cordova.getActivity(), cameraSelector, preview, imageCapture);
            reapplyTorchIfEnabled();
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Rotation for a still capture. The angle from JS is preferred: it is the same
     * OrientationService value that rotates the camera UI buttons, so the saved photo always
     * matches the orientation the user saw on screen. On some devices (QARMA-21263, Huawei
     * nova 12 / HarmonyOS 4.2) this class's own OrientationEventListener stays silent even
     * though the app-level one works, which froze every capture at portrait. The sensor value
     * remains the fallback for callers that send no angle (-1).
     *
     * The JS angle is clockwise-from-portrait, so it maps to the mirrored Surface rotation:
     * a device turned 90 degrees clockwise has a display rotated 270 degrees counter-clockwise.
     */
    private int getTargetRotation(int clockwiseAngleFromPortrait) {
        switch (clockwiseAngleFromPortrait) {
            case 0:
                return Surface.ROTATION_0;
            case 90:
                return Surface.ROTATION_270;
            case 180:
                return Surface.ROTATION_180;
            case 270:
                return Surface.ROTATION_90;
            default:
                return getTargetRotation();
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
                    if (recording != null) {
                        // QARMA-21811: "A recording is already in progress" - a recording the
                        // app believed finished (auto-stopped at the duration limit, or left over
                        // from a session that was torn down) was still alive natively. Its
                        // Finalize event will still arrive asynchronously; the identity check in
                        // the listener below makes sure it is ignored rather than mistaken for
                        // the new recording finishing.
                        recording.stop();
                        recording = null;
                        resetRecordingTimer();
                    }
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
                    // Events are delivered on the main executor, and this block runs on the main
                    // thread, so holder[0] is assigned before the first event can be observed.
                    final Recording[] holder = new Recording[1];
                    holder[0] = videoCapture.getOutput().prepareRecording(cordova.getActivity(), options)
                            .withAudioEnabled()
                            .start(getExecutor(), videoRecordEvent -> {
                                if (holder[0] != recording) {
                                    // Event from a recording that has since been replaced or
                                    // stopped. Its timer and its file are not ours to report.
                                    return;
                                }
                                if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                                    stopRecordingAfterMilliseconds((durationLimit + 1) * 1000L);
                                    callbackContext.success();
                                }
                                if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                                    resetRecordingTimer();
                                    notifyRecordedVideoPath((VideoRecordEvent.Finalize) videoRecordEvent);
                                    // Finalised: nothing left to stop. Lets stopRecording() and
                                    // the next startRecording() see the truth.
                                    recording = null;
                                }
                            });
                    recording = holder[0];
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
            if (imageCapture != null) {
                cameraProvider.unbind(imageCapture);
            }
            imageCapture = null;
            cameraInstance = cameraProvider.bindToLifecycle(cordova.getActivity(), cameraSelector, preview, videoCapture);
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
        if (recording == null) {
            // QARMA-21811: was an NPE ("Recording.stop() on a null object reference") when the
            // recording had already been finalised natively, e.g. by the duration-limit timer.
            callbackContext.error("no active recording");
            return true;
        }
        recordingStoppedByUser = true;
        recording.stop();
        recording = null;
        resetRecordingTimer();
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

    private void setupPreviewUseCaseAndInitCameraInstance(PreviewView requestedPreviewView, CallbackContext callbackContext) {
        if (previewView != requestedPreviewView) {
            // QARMA-21811: a newer startCameraX() ran before this bind got its turn (or a stop
            // removed the view). Binding now would attach this session's use cases to the newer
            // session's view, and failing now would tear that newer session down. Do neither.
            notifyDiagnostic("stale-bind-skipped", diagnosticIds());
            callbackContext.error("Failed to start the camera: superseded by a newer start");
            return;
        }
        if (isHostActivityDestroyed()) {
            // Binding here would silently do nothing and then fail the isBound() check below
            // with a message that describes the symptom rather than the cause.
            notifyDiagnostic("bind-skipped-destroyed-activity", diagnosticIds());
            cleanupFailedStart();
            callbackContext.error("Failed to start the camera: the activity hosting the camera was destroyed");
            return;
        }
        notifyDiagnostic("bind-begin", diagnosticIds());
        try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
            cameraProvider.unbindAll();
            cameraSelector = setupCameraSelectorUseCase();
            preview = setupPreviewUseCase(requestedPreviewView);
            imageCapture = setupImageCaptureUseCase(1200, 1600);

            // The reason we don't bind videoCapture use case here
            // is because on some phones, it is only allowed to bind either imageCapture or videoCapture at a time.
            cameraInstance = cameraProvider.bindToLifecycle(
                    cordova.getActivity(), cameraSelector, preview, imageCapture);

            // bindToLifecycle() does NOT throw when the bind is a no-op: with a destroyed
            // LifecycleOwner, CameraX skips registerCamera() and bindToLifecycleCamera() returns
            // early, so it returns a Camera bound to nothing (Sentry MOBILE-APP-1V0D). The
            // isHostActivityDestroyed() check above catches the known cause; this stays as the
            // backstop for any other way the use cases end up unregistered.
            if (!cameraProvider.isBound(preview) || !cameraProvider.isBound(imageCapture)) {
                throw new IllegalStateException("use cases not bound after bindToLifecycle");
            }

            attachStreamStateObserver(requestedPreviewView);
            attachCameraStateObserver(cameraInstance);
            notifyDiagnostic("bind-end", diagnosticIdsWithOk(true, null));
            callbackContext.success();
        } catch (ExecutionException e) {
            notifyDiagnostic("bind-end", diagnosticIdsWithOk(false, e.getMessage()));
            cleanupFailedStart();
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        } catch (InterruptedException e) {
            notifyDiagnostic("bind-end", diagnosticIdsWithOk(false, e.getMessage()));
            cleanupFailedStart();
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        } catch (Exception e) {
            // bindToLifecycle can throw IllegalArgumentException
            // ("No supported surface combination is found ... May be attempting to bind too
            // many use cases") which is an unchecked RuntimeException. This method runs inside a
            // ProcessCameraProvider listener on the main executor, so an uncaught exception here
            // escapes to the Looper and crashes the app (MOBILE-APP-1WHW), while the JS
            // startCamera() promise never settles. Report it to JS instead so the camera state
            // machine can handle/retry it cleanly.
            notifyDiagnostic("bind-end", diagnosticIdsWithOk(false, e.getMessage()));
            cleanupFailedStart();
            callbackContext.error("Failed to start the camera: " + e.getMessage());
        }
    }

    private Preview setupPreviewUseCase(PreviewView view) {
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build();
        tapToFocusAndPinchToZoom(view);
        preview.setSurfaceProvider(view.getSurfaceProvider());
        return preview;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void tapToFocusAndPinchToZoom(PreviewView view) {
        ScaleGestureDetector scaleGestureDetector = new ScaleGestureDetector(cordova.getActivity(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(@NonNull ScaleGestureDetector detector) {
                        Camera camera = cameraInstance;
                        if (camera == null) {
                            return true;
                        }
                        ZoomState zoomState = camera.getCameraInfo().getZoomState().getValue();
                        if (zoomState != null) {
                            float scale = zoomState.getZoomRatio() * detector.getScaleFactor();
                            camera.getCameraControl().setZoomRatio(scale);
                            notifyZoomRatioUpdate(scale);
                        }

                        return true;
                    }
                });
        view.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                triggerAutoFocusAndMetering(view, event.getX(), event.getY());
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

    private void triggerAutoFocusAndMetering(PreviewView view, float x, float y) {
        Camera camera = cameraInstance;
        if (camera == null || view != previewView) {
            // Gesture finished on a view that no longer belongs to the live session.
            return;
        }
        CameraControl cameraControl = camera.getCameraControl();

        MeteringPointFactory factory = view.getMeteringPointFactory();
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