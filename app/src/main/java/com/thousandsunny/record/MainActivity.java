package com.thousandsunny.record;

import android.content.Context;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import org.lucasr.twowayview.widget.TwoWayView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class MainActivity extends ActionBarActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private TwoWayView videos;

    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videos = (TwoWayView) findViewById(R.id.videos);
        adapter = new Adapter();
        videos.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_camera) {
            final Intent intent = new Intent(getApplicationContext(), CameraActivity.class);
            startActivityForResult(intent, 0);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class MediaPlayerHolder {
        private Context applicationContext;

        private final SurfaceTexture surfaceTexture;

        private final File videoFile;

        private MediaPlayer mediaPlayer;

        public MediaPlayerHolder(final Context applicationContext,
                                 final SurfaceTexture surfaceTexture,
                                 final File videoFile) {
            this.applicationContext = applicationContext;
            this.surfaceTexture = surfaceTexture;
            this.videoFile = videoFile;
        }

        public void start() throws IOException {
            if (mediaPlayer != null) {
                return;
            }

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(applicationContext, Uri.fromFile(videoFile));
            mediaPlayer.setSurface(new Surface(surfaceTexture));
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(final MediaPlayer mp) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.start();
                }
            });
            mediaPlayer.prepareAsync();
        }

        public void stop() {
            if (mediaPlayer == null) {
                return;
            }

            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.reset();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public static class VideoViewHolder extends RecyclerView.ViewHolder {

        private final TextureView video;

        private final Context applicationContext;

        private MediaPlayerHolder mediaPlayerHolder;

        public VideoViewHolder(final View itemView, final Context applicationContext) {
            super(itemView);
            this.applicationContext = applicationContext;
            video = (TextureView) itemView.findViewById(R.id.video);
        }

        public void bind(final File videoFile) {
            video.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(final SurfaceTexture surfaceTexture, final int width, final int height) {
                    try {
                        mediaPlayerHolder = new MediaPlayerHolder(applicationContext,
                                surfaceTexture,
                                videoFile);
                        mediaPlayerHolder.start();
                    } catch (IOException e) {
                        Log.w(TAG, e.getLocalizedMessage(), e);
                    }
                }

                @Override
                public void onSurfaceTextureSizeChanged(final SurfaceTexture surface, final int width, final int height) {
                }

                @Override
                public boolean onSurfaceTextureDestroyed(final SurfaceTexture surface) {
                    mediaPlayerHolder.stop();
                    mediaPlayerHolder = null;
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(final SurfaceTexture surface) {
                }
            });
        }
    }

    private class Adapter extends RecyclerView.Adapter<VideoViewHolder> {
        private final List<File> videoFiles = new ArrayList<>();

        @Override
        public VideoViewHolder onCreateViewHolder(final ViewGroup viewGroup, final int i) {
            final View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_video, viewGroup, false);
            return new VideoViewHolder(view, getApplicationContext());
        }

        @Override
        public void onBindViewHolder(final VideoViewHolder viewHolder, final int i) {
            final File videoFile = videoFiles.get(i);
            viewHolder.bind(videoFile);
        }

        @Override
        public int getItemCount() {
            return videoFiles.size();
        }

        public void load() {
            final File[] files = getVideoDir().listFiles();
            videoFiles.clear();
            videoFiles.addAll(Arrays.asList(files));
            notifyDataSetChanged();
        }

        private File getVideoDir() {
            return getApplicationContext().getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        }
    }
}
