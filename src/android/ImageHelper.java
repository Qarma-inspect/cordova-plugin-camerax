package com.cordovaplugincamerax;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageProxy;
import com.cordovaplugincamerapreview.RectMathUtil;
import java.nio.ByteBuffer;

@ExperimentalGetImage
public class ImageHelper {
    public ImageHelper() {
    }

    public Bitmap rotateAndReturnImage(ImageProxy imageProxy) {
        byte[] data = imageProxyToByteArray(imageProxy);
        Matrix matrix = new Matrix();
        matrix.preRotate(imageProxy.getImageInfo().getRotationDegrees());
        Bitmap bitmapImage = BitmapFactory.decodeByteArray(data, 0, data.length);
        return applyMatrix(bitmapImage, matrix);
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