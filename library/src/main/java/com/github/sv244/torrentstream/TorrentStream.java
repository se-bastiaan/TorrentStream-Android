/*
 * This file is part of TorrentStreamer-Android.
 *
 * TorrentStreamer-Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TorrentStreamer-Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with TorrentStreamer-Android. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.sv244.torrentstream;

import android.os.Handler;
import android.os.HandlerThread;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.DHT;
import com.frostwire.jlibtorrent.Downloader;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.TorrentAddedAlert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import com.github.sv244.torrentstream.exceptions.DirectoryCreationException;
import com.github.sv244.torrentstream.exceptions.NotInitializedException;
import com.github.sv244.torrentstream.exceptions.TorrentInfoException;
import com.github.sv244.torrentstream.listeners.TorrentListener;
import com.github.sv244.torrentstream.utils.FileUtils;
import com.github.sv244.torrentstream.utils.ThreadUtils;

public class TorrentStream {

    private static final String LIBTORRENT_THREAD_NAME = "TORRENTSTREAM_LIBTORRENT", STREAMING_THREAD_NAME = "TORRENTSTREAMER_STREAMING";
    private static TorrentStream INSTANCE;

    private Session mTorrentSession;
    private DHT mDHT;
    private Boolean mInitialised = false, mIsStreaming = false, mIsCancelled = false;
    private TorrentOptions mTorrentOptions;

    private Torrent mCurrentTorrent;
    private String mCurrentTorrentUrl;

    private List<TorrentListener> mListener = new ArrayList<>();

    private HandlerThread mLibTorrentThread, mStreamingThread;
    private Handler mLibTorrentHandler, mStreamingHandler;

    private TorrentStream(TorrentOptions options) {
        mTorrentOptions = options;
        initialise();
    }

    public static TorrentStream init(TorrentOptions options) {
        INSTANCE = new TorrentStream(options);
        return INSTANCE;
    }

    public static TorrentStream getInstance() throws NotInitializedException {
        if(INSTANCE == null)
            throw new NotInitializedException();

        return INSTANCE;
    }

    private void initialise() {
        if (mLibTorrentThread != null && mTorrentSession != null) {
            resumeSession();
        } else {
            if(mInitialised) {
                if (mLibTorrentThread != null) {
                    mLibTorrentThread.interrupt();
                }
            }

            mLibTorrentThread = new HandlerThread(LIBTORRENT_THREAD_NAME);
            mLibTorrentThread.start();
            mLibTorrentHandler = new Handler(mLibTorrentThread.getLooper());
            mLibTorrentHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTorrentSession = new Session();

                    SettingsPack settingsPack = new SettingsPack();
                    settingsPack.setAnonymousMode(true);
                    mTorrentSession.applySettings(settingsPack);

                    mDHT = new DHT(mTorrentSession);
                    mDHT.start();

                    mInitialised = true;
                }
            });
        }
    }

    /**
     * Resume TorrentSession
     */
    public void resumeSession() {
        if (mLibTorrentThread != null && mTorrentSession != null) {
            mLibTorrentHandler.removeCallbacksAndMessages(null);

            //resume torrent session if needed
            if (mTorrentSession.isPaused()) {
                mLibTorrentHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mTorrentSession.resume();
                    }
                });
            }

            //start DHT if needed
            if (mDHT != null && !mDHT.isRunning()) {
                mLibTorrentHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mDHT.start();
                    }
                });
            }
        }
    }

    /**
     * Pause TorrentSession
     */
    public void pauseSession() {
        if(!mIsStreaming)
            mLibTorrentHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTorrentSession.pause();
                }
            });
    }

    /**
     * Get torrent metadata, either by downloading the .torrent or fetching the magnet
     * @param torrentUrl {@link String} URL to .torrent or magnet link
     * @return {@link TorrentInfo}
     */
    private TorrentInfo getTorrentInfo(String torrentUrl) {
        if (torrentUrl.startsWith("magnet")) {
            Downloader d = new Downloader(mTorrentSession);

            byte[] data = d.fetchMagnet(torrentUrl, 30000);

            if(data != null) {
                return TorrentInfo.bdecode(data);
            } else {
                return null;
            }
        } else {
            try {
                URL url = new URL(torrentUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(true);
                connection.connect();

                InputStream inputStream = connection.getInputStream();

                byte[] responseByteArray = new byte[0];

                if(connection.getResponseCode() == 200) {
                    ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

                    int bufferSize = 1024;
                    byte[] buffer = new byte[bufferSize];

                    int len = 0;
                    while ((len = inputStream.read(buffer)) != -1) {
                        byteBuffer.write(buffer, 0, len);
                    }

                    responseByteArray = byteBuffer.toByteArray();
                }

                inputStream.close();
                connection.disconnect();

                if(responseByteArray.length > 0) {
                    return TorrentInfo.bdecode(responseByteArray);
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    /**
     * Start stream download for specified torrent
     * @param torrentUrl {@link String} .torrent or magnet link
     */
    public void startStream(final String torrentUrl) {
        if(!mInitialised)
            initialise();

        if (mLibTorrentHandler == null || mIsStreaming) return;

        mIsCancelled = false;

        mStreamingThread = new HandlerThread(STREAMING_THREAD_NAME);
        mStreamingThread.start();
        mStreamingHandler = new Handler(mStreamingThread.getLooper());

        mStreamingHandler.post(new Runnable() {
            @Override
            public void run() {
                mIsStreaming = true;
                mCurrentTorrentUrl = torrentUrl;

                File saveDirectory = new File(mTorrentOptions.mSaveLocation);
                if (!saveDirectory.isDirectory()) {
                    if (!saveDirectory.mkdirs()) {
                        for (final TorrentListener listener : mListener) {
                            ThreadUtils.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onStreamError(null, new DirectoryCreationException());
                                }
                            });
                        }
                        mIsStreaming = false;
                        return;
                    }
                }

                mTorrentSession.removeListener(mAlertListener);
                TorrentInfo torrentInfo = getTorrentInfo(torrentUrl);
                mTorrentSession.addListener(mAlertListener);

                if (torrentInfo == null) {
                    for (final TorrentListener listener : mListener) {
                        ThreadUtils.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                listener.onStreamError(null, new TorrentInfoException());
                            }
                        });
                    }
                    mIsStreaming = false;
                    return;
                }

                Priority[] priorities = new Priority[torrentInfo.getNumPieces()];
                for (int i = 0; i < priorities.length; i++) {
                    priorities[i] = Priority.IGNORE;
                }

                if (!mCurrentTorrentUrl.equals(torrentUrl) || mIsCancelled) {
                    return;
                }

                mTorrentSession.asyncAddTorrent(torrentInfo, saveDirectory, priorities, null);
            }
        });
    }

    /**
     * Stop current torrent stream
     */
    public void stopStream() {
        //remove all callbacks from handler
        if(mLibTorrentHandler != null)
            mLibTorrentHandler.removeCallbacksAndMessages(null);
        if(mStreamingHandler != null)
            mStreamingHandler.removeCallbacksAndMessages(null);

        mIsCancelled = true;
        mIsStreaming = false;
        if (mCurrentTorrent != null) {
            final File saveLocation = mCurrentTorrent.getSaveLocation();

            mCurrentTorrent.pause();
            mTorrentSession.removeListener(mCurrentTorrent);
            mTorrentSession.removeTorrent(mCurrentTorrent.getTorrentHandle());
            mCurrentTorrent = null;

            if (mTorrentOptions.mRemoveFiles) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int tries = 0;
                        while(!FileUtils.recursiveDelete(saveLocation) && tries < 5) {
                            tries++;
                            try {
                                Thread.sleep(1000); // If deleted failed then something is still using the file, wait and then retry
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }).start();
            }
        }

        if(mStreamingThread != null)
            mStreamingThread.interrupt();

        for (final TorrentListener listener : mListener) {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listener.onStreamStopped();
                }
            });
        }
    }

    public TorrentOptions getOptions() {
        return mTorrentOptions;
    }

    public void setOptions(TorrentOptions options) {
        mTorrentOptions = options;

        SettingsPack settingsPack = new SettingsPack();
        settingsPack.setConnectionsLimit(mTorrentOptions.mMaxConnections);
        settingsPack.setDownloadRateLimit(mTorrentOptions.mMaxDownloadSpeed);
        settingsPack.setUploadRateLimit(mTorrentOptions.mMaxUploadSpeed);
        mTorrentSession.applySettings(settingsPack);
    }

    public boolean isStreaming() {
        return mIsStreaming;
    }

    public String getCurrentTorrentUrl() {
        return mCurrentTorrentUrl;
    }

    public void addListener(TorrentListener listener) {
        if(listener != null)
            mListener.add(listener);
    }

    public void removeListener(TorrentListener listener) {
        if(listener != null)
            mListener.remove(listener);
    }

    private AlertListener mAlertListener = new AlertListener() {
        @Override
        public int[] types() {
            return new int[] { AlertType.TORRENT_ADDED.getSwig() };
        }

        @Override
        public void alert(Alert<?> alert) {
            switch (alert.getType()) {
                case TORRENT_ADDED:
                    InternalTorrentListener listener = new InternalTorrentListener();
                    TorrentHandle th = mTorrentSession.findTorrent(((TorrentAddedAlert) alert).getHandle().getInfoHash());
                    mCurrentTorrent = new Torrent(th, listener, mTorrentOptions.mPrepareSize);
                    mTorrentSession.addListener(mCurrentTorrent);
                    break;
            }
        }
    };

    protected class InternalTorrentListener implements TorrentListener {

        public void onStreamStarted(final Torrent torrent) {
            for (final TorrentListener listener : mListener) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStreamStarted(torrent);
                    }
                });
            }
        }

        public void onStreamError(final Torrent torrent, final Exception e) {
            for (final TorrentListener listener : mListener) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStreamError(torrent, e);
                    }
                });
            }
        }

        public void onStreamReady(final Torrent torrent) {
            for (final TorrentListener listener : mListener) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStreamReady(torrent);
                    }
                });
            }
        }

        public void onStreamProgress(final Torrent torrent, final StreamStatus status) {
            for (final TorrentListener listener : mListener) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStreamProgress(torrent, status);
                    }
                });
            }
        }

        @Override
        public void onStreamStopped() {
            // Not used
        }

        @Override
        public void onStreamPrepared(final Torrent torrent) {
            for (final TorrentListener listener : mListener) {
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStreamPrepared(torrent);
                    }
                });
            }
        }
    }

}
