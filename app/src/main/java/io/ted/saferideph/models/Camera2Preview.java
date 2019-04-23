package io.ted.saferideph.models;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Camera2Preview implements TextureView.SurfaceTextureListener {
    private static final int PERMISSION_REQUEST = 0x0111;
    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final String TAG = "Camera2Preview";
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }




    CameraManager cameraManager;
    Camera2TextureView mContainer;
    Activity mActivity;
    Context mContext;
    Semaphore mCameraOpenCloseLock = new Semaphore(1);
    Integer mSensorOrientation;
    Size mVideoSize;
    Size mPreviewSize;

    MediaRecorder mMediaRecorder;
    CameraDevice mCameraDevice;
    CameraCaptureSession mPreviewSession;
    String mNextVideoAbsolutePath;
    CaptureRequest.Builder mPreviewBuilder;
    Handler mBackgroundHandler;
    HandlerThread mBackgroundThread;

    Handler previewHandler;
    HandlerThread previewHandlerThread;

    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };


    public Camera2Preview (Activity activity, Context context, Camera2TextureView container) throws Exception {
        if(context == null) new Exception();
        mActivity = activity;
        mContext = context;
        mContainer = container;
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        container.setSurfaceTextureListener(this);
    }

    public void onResume() {
        this.checkPermissions();
        startBackgroundThread();
        startPreviewThread();
        if (mContainer.isAvailable()) {
            openCamera(mContainer.getWidth(), mContainer.getHeight());
        } else {
            mContainer.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void onPause() {
        closeCamera();
        stopPreviewThread();
        stopBackgroundThread();
    }

    public boolean checkPermissions() {
        
        List<String> filtered  = Arrays.stream(VIDEO_PERMISSIONS)
            .filter(permission -> ActivityCompat.checkSelfPermission(mContext, permission) != PackageManager.PERMISSION_GRANTED )
            .collect(Collectors.toList());
        
        if (filtered.size() > 0 ) {
            ActivityCompat.requestPermissions(mActivity, filtered.toArray(new String[filtered.size()]), PERMISSION_REQUEST);
            return false;
        }

        return true;
    }


    @SuppressLint("MissingPermission")
    private void openCamera(int width, int height) {
        try {
            if(!checkPermissions()) return;
            if(cameraManager == null) {
                cameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            }
            if(!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = this.cameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (streamConfigurationMap == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            mVideoSize = chooseVideoSize(streamConfigurationMap.getOutputSizes((MediaRecorder.class)));
            mPreviewSize = new Size(mContainer.getWidth(), mContainer.getHeight()); //chooseOptimalSize(streamConfigurationMap.getOutputSizes(SurfaceTexture.class),width, height, mVideoSize);

            int orientation = this.mContext.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                this.mContainer.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                this.mContainer.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }

            this.cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    mCameraDevice = camera;
                    startPreview();
                    mCameraOpenCloseLock.release();
                    if (null != mContainer) {
                        configureTransform(mContainer.getWidth(), mContainer.getHeight());
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    mCameraOpenCloseLock.release();
                    camera.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    mCameraOpenCloseLock.release();
                    camera.close();
                    mCameraDevice = null;
                    Activity activity = mActivity;
                    if (null != activity) {
                        activity.finish();
                    }
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException cameraAc) {

        } catch (InterruptedException interupt) {

        }
    }

    void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 16 / 9){// && size.getWidth() <= 1080) {
                return size;
            }
        }
        return choices[choices.length - 1];
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    void startPreview() {
        if(null == mCameraDevice || !mContainer.isAvailable() || null == mPreviewSize) {
            return;
        }

        try {
            closePreviewSession();
            mMediaRecorder = new MediaRecorder();
            setUpMediaRecorder();
            SurfaceTexture texture = mContainer.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            Surface recordSurface = mMediaRecorder.getSurface();
            surfaces.add(recordSurface);
            mPreviewBuilder.addTarget(recordSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mPreviewSession = session;
                    startPreviewThread();
                    updatePreview();
                    mActivity.runOnUiThread(() -> {
                        mMediaRecorder.start();
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Activity activity = mActivity;
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            }, mBackgroundHandler);

        } catch (IOException ioException) {

        } catch (CameraAccessException cameraAccessException) {

        }
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
        if(mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
        }
    }

    private void setUpMediaRecorder() throws IOException {
        final Context context = mContext;
        if (null == context) {
            return;
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(this.mContext);
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
//        mMediaRecorder.setVideoEncodingBitRate(10000000);
//        mMediaRecorder.setVideoFrameRate(30);
//        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }

    private String getVideoFilePath(Context context) {
        final File dir = context.getExternalFilesDir(null);
        return (dir == null ? "" : (dir.getAbsolutePath() + "/"))
                + System.currentTimeMillis() + ".mp4";
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = mActivity;
        if (null == mContainer || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mContainer.setTransform(matrix);
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startPreviewThread() {
        previewHandlerThread = new HandlerThread("Camera2Preview Handler");
        previewHandlerThread.start();
        previewHandler = new Handler(previewHandlerThread.getLooper());
    }
    private void stopPreviewThread() {
        previewHandlerThread.quitSafely();
        try {
            previewHandlerThread.join();
            previewHandlerThread = null;
            previewHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

}
