package com.cordovaplugincamerax;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.util.Size;

import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import com.cordovaplugincamerapreview.RectMathUtil;
import java.nio.ByteBuffer;

@ExperimentalGetImage
public class ImageHelper {
    public ImageHelper() {
    }

    public Bitmap rotateAndReturnImage(ImageProxy imageProxy, Size targetSize, Size maxSizeAllowed) {
        byte[] data = imageProxyToByteArray(imageProxy);
        Matrix matrix = new Matrix();
        matrix.preRotate(imageProxy.getImageInfo().getRotationDegrees());
        Bitmap bitmapImage = BitmapFactory.decodeByteArray(data, 0, data.length);
        Bitmap imageWithProperRotation = applyMatrix(bitmapImage, matrix);
        return scaleDownImageIfNecessary(imageWithProperRotation, targetSize, maxSizeAllowed);
    }

    private Bitmap scaleDownImageIfNecessary(Bitmap bitmap, Size targetSize, Size maxSizeAllowed) {
        int imageWidth = bitmap.getWidth();
        int imageHeight = bitmap.getHeight();
        Size actualSize = new Size(imageWidth, imageHeight);
        if(!shouldScaleDownImage(actualSize, maxSizeAllowed)) {
            return bitmap;
        }
        Size finalImageSize = calculateNewImageSize(actualSize, targetSize);
        return Bitmap.createScaledBitmap(bitmap, finalImageSize.getWidth(), finalImageSize.getHeight(), true);
    }

    private boolean shouldScaleDownImage(Size actualSize, Size maxSizeAllowed) {
        double ratio = getImageRatio(actualSize.getWidth(), actualSize.getHeight());
        return actualSize.getWidth() * actualSize.getHeight() > maxSizeAllowed.getWidth() * maxSizeAllowed.getHeight() && ratio == 0.75;
    }

    private double getImageRatio(int width, int height) {
        return Math.min(width, height) / (double) Math.max(width, height);
    }

    private Size calculateNewImageSize(Size actualSize, Size targetSize) {
        boolean isPortrait = actualSize.getWidth() < actualSize.getHeight();
        int smallerEdge = Math.min(targetSize.getWidth(), targetSize.getHeight());
        int biggerEdge = Math.max(targetSize.getWidth(), targetSize.getHeight());
        int newWidth = isPortrait ? smallerEdge : biggerEdge;
        int newHeight = isPortrait ? biggerEdge : smallerEdge;
        return new Size(newWidth, newHeight);
    }

    public Bitmap createThumbnailImage(Bitmap bitmap) {
        Rect scaledRect = RectMathUtil.contain(bitmap.getWidth(), bitmap.getHeight(), 200, 200);
        Bitmap partOfImage = Bitmap.createBitmap(bitmap, scaledRect.left, scaledRect.top, scaledRect.width(),
                scaledRect.height());
        return Bitmap.createScaledBitmap(partOfImage, 200, 200, true);
    }

    @SuppressLint("RestrictedApi")
    private byte[] imageProxyToByteArray(ImageProxy imageProxy) {
        Image image = imageProxy.getImage();
        if (image == null) {
            return new byte[0];
        }

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

    private Bitmap applyMatrix(Bitmap source, Matrix matrix) {
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }
}