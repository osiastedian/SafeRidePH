package io.ted.saferideph;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    public static final String TAG = "CameraPreview";

    private SurfaceHolder holder;
    private Camera camera;
    private Context context;

    public CameraPreview(Context context, Camera camera) {
        super(context);
        setCamera(camera);
        holder = this.getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(holder);
//            camera.setDisplayOrientation(90);
            camera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        if (holder.getSurface() == null){
            return;
        }
        try {
            camera.stopPreview();
        } catch (Exception e){}

        try {
            camera.setPreviewDisplay(holder);
            camera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void setCamera(Camera camera) {
        if (this.camera == camera) { return; }

        stopPreviewAndFreeCamera();

        this.camera = camera;

        if (this.camera != null) {
            requestLayout();

            try {
                this.camera.setPreviewDisplay(this.holder);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Important: Call startPreview() to start updating the preview
            // surface. Preview must be started before you can take a picture.
            this.camera.startPreview();
        }
    }

    private void stopPreviewAndFreeCamera() {

        if (this.camera != null) {
            // Call stopPreview() to stop updating the preview surface.
            this.camera.stopPreview();

            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            this.camera.release();

            this.camera = null;
        }
    }
}
