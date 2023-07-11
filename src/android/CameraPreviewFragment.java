package com.cordovaplugincamerax;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Base64;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.CameraInfo;
import androidx.exifinterface.media.ExifInterface;

import org.apache.cordova.LOG;
import org.apache.cordova.CordovaWebView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.Exception;
import java.lang.Integer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import android.media.MediaRecorder;
import android.media.CamcorderProfile;

public class CameraPreviewFragment extends Fragment {

    private static final String TAG = "CameraActivity";
    public FrameLayout mainLayout;
    public FrameLayout frameContainerLayout;

    private Preview mPreview;
    private boolean canTakePicture = true;

    private View view;
    private Camera.Parameters cameraParameters;
    private Camera mCamera;
    private int numberOfCameras;
    private int cameraCurrentlyLocked;
    private int currentQuality;

    // The first rear facing camera
    private int defaultCameraId;
    public String defaultCamera;
    public boolean tapToTakePicture;
    public boolean dragEnabled;
    public boolean tapToFocus;

    public int width;
    public int height;
    public int x;
    public int y;

    private enum RecordingState {
        INITIALIZING, STARTED, STOPPED
    };

    private RecordingState mRecordingState = RecordingState.INITIALIZING;
    private MediaRecorder mRecorder = null;
    private String recordFilePath;
    private CordovaWebView cordovaWebView;

    public void setEventListener(CameraPreviewListener listener) {
        eventListener = listener;
    }

    private String appResourcesPackage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        appResourcesPackage = getActivity().getPackageName();

        // Inflate the layout for this fragment
        view = inflater.inflate(getResources().getIdentifier("camera_fragment", "layout", appResourcesPackage),
                container,
                false);
        createCameraPreview();
        return view;
    }

    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private void createCameraPreview() {

    }

    @Override
    public void onResume() {
        super.onResume();
        try {
            mCamera = Camera.open(defaultCameraId);
            if (mCamera == null) {
                eventListener.onCameraStartedError("Cannot access CameraService");
            } else {
                if (cameraParameters != null) {
                    mCamera.setParameters(cameraParameters);
                }

                cameraCurrentlyLocked = defaultCameraId;

                if (mPreview.mPreviewSize == null) {
                    mPreview.setCamera(mCamera, cameraCurrentlyLocked);
                    eventListener.onCameraStarted();
                } else {
                    mPreview.switchCamera(mCamera, cameraCurrentlyLocked);
                    mCamera.startPreview();
                }

                Log.d(TAG, "cameraCurrentlyLocked:" + cameraCurrentlyLocked);

                final FrameLayout newFrameContainerLayout = (FrameLayout) view
                        .findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));

                ViewTreeObserver viewTreeObserver = newFrameContainerLayout.getViewTreeObserver();

                if (viewTreeObserver.isAlive()) {
                    viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            try {
                                newFrameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                                newFrameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED,
                                        View.MeasureSpec.UNSPECIFIED);
                                final RelativeLayout frameCamContainerLayout = (RelativeLayout) view
                                        .findViewById(getResources().getIdentifier("frame_camera_cont", "id",
                                                appResourcesPackage));

                                FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(
                                        newFrameContainerLayout.getWidth(), newFrameContainerLayout.getHeight());
                                camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
                                frameCamContainerLayout.setLayoutParams(camViewLayout);
                            } catch (Exception e) {
                                Log.d(TAG, e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }
}