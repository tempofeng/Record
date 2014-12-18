package com.thousandsunny.record;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import org.bytedeco.javacv.FrameRecorder;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class CameraActivity extends Activity {
    private static final String TAG = CameraActivity.class.getSimpleName();

    private FrameLayout cameraPreview;

    private Button capture;

    private Camera camera;

    private int degrees;

    private CameraView cameraView;

    private AtomicReference<OpenCVRecorder> openCVRecorderRef = new AtomicReference<>();

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
        capture = (Button) findViewById(R.id.button_capture);
        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (openCVRecorderRef.get() == null) {
                    startRecording();
                    capture.setText("Stop");
                } else {
                    stopRecording();
                    capture.setText("Start");
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startCamera();
    }

    @Override
    protected void onPause() {
        stopRecording();
        stopCamera();
        super.onPause();
    }

    private void startCamera() {
        if (camera != null) {
            return;
        }

        camera = Camera.open(0);
        degrees = setCameraDisplayOrientation(0, camera);

        cameraView = new CameraView(this, camera, degrees, openCVRecorderRef);
        cameraPreview.addView(new CroppedCameraView(getApplicationContext(), cameraView));
    }

    private void stopCamera() {
        cameraPreview.removeAllViews();

        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private void startRecording() {
        if (openCVRecorderRef.get() != null) {
            return;
        }

        final String videoFilename = UUID.randomUUID().toString() + ".mp4";
        final File videoFile = new File(getVideoDir(), videoFilename);

        final OpenCVRecorder openCVRecorder = new OpenCVRecorder(camera, degrees, 120, videoFile);
        openCVRecorderRef.set(openCVRecorder);
        try {
            openCVRecorder.start();
        } catch (FrameRecorder.Exception e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    public void stopRecording() {
        final OpenCVRecorder openCVRecorder = openCVRecorderRef.get();
        if (openCVRecorder == null) {
            return;
        }

        openCVRecorder.stop();
        openCVRecorderRef.set(null);
    }

    private File getVideoDir() {
        return getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
    }

    private int setCameraDisplayOrientation(final int cameraId,
                                            final Camera camera) {
        final Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        final int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
        return result;
    }

    public static class CroppedCameraView extends ViewGroup {
        private final CameraView cameraView;

        public CroppedCameraView(final Context context,
                                 final CameraView cameraView) {
            super(context);
            this.cameraView = cameraView;
            addView(cameraView);
        }

        @Override
        protected void onLayout(final boolean changed, final int left, final int top, final int right, final int bottom) {
            final int width = right - left;
            final int height = bottom - top;

            final Point previewSize = cameraView.getDisplayPreviewSize();
            if (previewSize.x <= previewSize.y) {
                final float ratioX = ((float) width) / ((float) previewSize.x);
                final int expectedHeight = (int) (ratioX * previewSize.y);

                cameraView.layout(0,
                        -((expectedHeight - height) / 2),
                        width,
                        expectedHeight - ((expectedHeight - height) / 2));
            } else {
                final float ratioY = ((float) height) / ((float) previewSize.y);
                final int expectedWidth = (int) (ratioY * previewSize.x);

                cameraView.layout(0,
                        0,
                        expectedWidth + ((expectedWidth - width) / 2),
                        height);
            }
        }
    }

    public static class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {
        private final SurfaceHolder holder;

        private final Camera camera;

        private final int degree;

        private final AtomicReference<OpenCVRecorder> openCVRecorderRef;

        public CameraView(final Context context,
                          final Camera camera,
                          final int degree,
                          final AtomicReference<OpenCVRecorder> openCVRecorderRef) {
            super(context);
            this.degree = degree;
            this.openCVRecorderRef = openCVRecorderRef;
            this.camera = camera;

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            holder = getHolder();
            holder.addCallback(this);

            // deprecated setting, but required on Android versions prior to 3.0
            // holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        public void surfaceCreated(final SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);
                camera.startPreview();
            } catch (final IOException e) {
                Log.e(TAG, "Error setting camera preview: " + e.getMessage(), e);
            }
        }

        public void surfaceDestroyed(final SurfaceHolder holder) {
            try {
                holder.addCallback(null);
                camera.setPreviewCallback(null);
            } catch (final Exception e) {
                // The camera has probably just been released, ignore.
            }
        }

        public void surfaceChanged(final SurfaceHolder holder, final int format, final int w, final int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (holder.getSurface() == null) {
                // preview surface does not exist
                return;
            }


            // stop preview before making changes
            try {
                camera.stopPreview();
            } catch (final Exception e) {
                // ignore: tried to stop a non-existent preview
            }

            // start preview with new settings
            try {
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);
                camera.startPreview();
            } catch (final IOException e) {
                Log.e(TAG, "Error starting camera preview: " + e.getLocalizedMessage(), e);
            }
        }

        @Override
        public void onPreviewFrame(final byte[] bytes, final Camera camera) {
            final OpenCVRecorder openCVRecorder = openCVRecorderRef.get();
            if (openCVRecorder == null) {
                return;
            }

            openCVRecorder.onPreviewFrame(bytes);
        }

        public Point getDisplayPreviewSize() {
            final Camera.Size previewSize = camera.getParameters().getPreviewSize();
            switch (degree) {
                case 0:
                case 180:
                    return new Point(previewSize.width, previewSize.height);
                case 90:
                case 270:
                    return new Point(previewSize.height, previewSize.width);
                default:
                    throw new IllegalArgumentException("Unknown degree:" + degree);
            }
        }
    }
}