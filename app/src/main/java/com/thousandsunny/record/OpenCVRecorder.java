package com.thousandsunny.record;

import android.hardware.Camera;
import android.util.Log;

import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FrameRecorder;

import java.io.File;

import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvCopy;
import static org.bytedeco.javacpp.opencv_core.cvFlip;
import static org.bytedeco.javacpp.opencv_core.cvTranspose;

public class OpenCVRecorder {
    private static final String TAG = OpenCVRecorder.class.getSimpleName();

    private final int[] tempImageBuffer;

    private final opencv_core.IplImage bgrImage;

    private final opencv_core.IplImage squareImage;

    private final opencv_core.IplImage transposed;

    private final int squareSide;

    private final Camera.Size previewFrameSize;

    private FFmpegFrameRecorder recorder;

    private final int degree;

    private final int outputSquareSideLength;

    private final File outputVideoFile;

    private long startTime;

    /**
     * @param degree front = 270, back = 90 on portrait mode
     */
    public OpenCVRecorder(final Camera camera,
                          final int degree,
                          final int outputSquareSideLength,
                          final File outputVideoFile) {
        this.degree = degree;
        this.outputSquareSideLength = outputSquareSideLength;
        this.outputVideoFile = outputVideoFile;
        previewFrameSize = camera.getParameters().getPreviewSize();

        tempImageBuffer = new int[previewFrameSize.width * previewFrameSize.height];

        bgrImage = opencv_core.IplImage.create(previewFrameSize.width,
                previewFrameSize.height,
                IPL_DEPTH_8U,
                4);

        squareSide = Math.min(previewFrameSize.width, previewFrameSize.height);

        squareImage = opencv_core.IplImage.create(squareSide,
                squareSide,
                IPL_DEPTH_8U,
                4);

        transposed = opencv_core.IplImage.create(squareSide,
                squareSide,
                IPL_DEPTH_8U,
                4);
    }

    public synchronized void start() throws FrameRecorder.Exception {
        if (recorder != null) {
            return;
        }

        startTime = System.currentTimeMillis();

        recorder = new FFmpegFrameRecorder(outputVideoFile, outputSquareSideLength, outputSquareSideLength, 0);
        recorder.setFormat("mp4");
        recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
        recorder.setVideoOption("preset", "ultrafast");
        recorder.setVideoBitrate(168000);
        recorder.setFrameRate(30);
        recorder.start();
    }

    public synchronized void stop() {
        if (recorder == null) {
            return;
        }

        try {
            recorder.stop();
            recorder.release();
        } catch (Exception e) {
            Log.w(TAG, e.getLocalizedMessage(), e);
        } finally {
            recorder = null;
        }
    }

    public synchronized void onPreviewFrame(final byte[] bytes) {
        if (recorder == null) {
            return;
        }

        try {
            YUV_NV21_TO_BGR(tempImageBuffer, bytes, previewFrameSize.width, previewFrameSize.height);
            bgrImage.getIntBuffer().put(tempImageBuffer);

            final opencv_core.IplROI roi = new opencv_core.IplROI();
            roi.xOffset((bgrImage.width() - squareSide) / 2);
            roi.yOffset((bgrImage.height() - squareSide) / 2);
            roi.width(squareSide);
            roi.height(squareSide);
            cvCopy(bgrImage.roi(roi), squareImage);

            // TODO test for horizontal camera
            if (degree == 90 || degree == 270) {
                // rotate
                cvTranspose(squareImage, transposed);
            }

            if (degree == 90) {
                cvFlip(transposed, transposed, 1);
            }

            long t = 1000 * (System.currentTimeMillis() - startTime);
            if (t > recorder.getTimestamp()) {
                recorder.setTimestamp(t);
            }
            recorder.record(transposed);
        } catch (final Exception e) {
            Log.w(TAG, e.getLocalizedMessage(), e);
        } finally {
            bgrImage.roi(null);
        }
    }

    private void YUV_NV21_TO_BGR(final int[] bgr, final byte[] yuv, final int width, final int height) {
        final int frameSize = width * height;

        final int ii = 0;
        final int ij = 0;
        final int di = +1;
        final int dj = +1;

        int a = 0;
        for (int i = 0, ci = ii; i < height; ++i, ci += di) {
            for (int j = 0, cj = ij; j < width; ++j, cj += dj) {
                int y = (0xff & ((int) yuv[ci * width + cj]));
                int v = (0xff & ((int) yuv[frameSize + (ci >> 1) * width
                        + (cj & ~1) + 0]));
                int u = (0xff & ((int) yuv[frameSize + (ci >> 1) * width
                        + (cj & ~1) + 1]));
                y = y < 16 ? 16 : y;

                final int a0 = 1192 * (y - 16);
                final int a1 = 1634 * (v - 128);
                final int a2 = 832 * (v - 128);
                final int a3 = 400 * (u - 128);
                final int a4 = 2066 * (u - 128);

                int r = (a0 + a1) >> 10;
                int g = (a0 - a2 - a3) >> 10;
                int b = (a0 + a4) >> 10;

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                bgr[a++] = 0xff000000 | (b << 16) | (g << 8) | r;
            }
        }
    }
}
