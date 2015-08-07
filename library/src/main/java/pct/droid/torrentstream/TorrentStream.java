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

package pct.droid.torrentstream;

import android.os.Handler;
import android.os.HandlerThread;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.DHT;
import com.frostwire.jlibtorrent.Downloader;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.Session;
import com.frostwire.jlibtorrent.SettingsPack;
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

import pct.droid.torrentstream.exceptions.DirectoryCreationException;
import pct.droid.torrentstream.exceptions.NotInitializedException;
import pct.droid.torrentstream.exceptions.TorrentInfoException;
import pct.droid.torrentstream.listeners.TorrentAlertListener;
import pct.droid.torrentstream.listeners.TorrentListener;
import pct.droid.torrentstream.utils.FileUtils;
import pct.droid.torrentstream.utils.ThreadUtils;

public class TorrentStream {

    private static final String LIBTORRENT_THREAD_NAME = "TORRENTSTREAM_LIBTORRENT", STREAMING_THREAD_NAME = "TORRENTSTREAMER_STREAMING";
    private static TorrentStream INSTANCE;

    private Session mTorrentSession;
    private DHT mDHT;
    private Boolean mInitialised = false, mIsStreaming = false, mIsCancelled = false;
    private String mSaveLocation;

    private Torrent mCurrentTorrent;
    private String mCurrentTorrentUrl;

    private List<TorrentListener> mListener = new ArrayList<>();

    private HandlerThread mLibTorrentThread, mStreamingThread;
    private Handler mLibTorrentHandler, mStreamingHandler;

    private TorrentStream(String saveLocation) {
        mSaveLocation = saveLocation;

        initialise();
    }

    public static TorrentStream init(String saveLocation) {
        INSTANCE = new TorrentStream(saveLocation);
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
                    // Start libtorrent session and init DHT
                    mTorrentSession = new Session();

                    SettingsPack settingsPack = new SettingsPack();
                    settingsPack.setAnonymousMode(true);
                    mTorrentSession.applySettings(settingsPack);

                    mTorrentSession.addListener(mAlertListener);
                    mDHT = new DHT(mTorrentSession);
                    mDHT.start();

                    mInitialised = true;
                }
            });
        }
    }

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

    public void pauseSession() {
        if(!mIsStreaming)
            mLibTorrentHandler.post(new Runnable() {
                @Override
                public void run() {
                    mTorrentSession.pause();
                }
            });
    }

    private TorrentInfo getTorrentInfo(String torrentUrl) {
        if (torrentUrl.startsWith("magnet")) {
            Downloader d = new Downloader(mTorrentSession);

            if (!mDHT.isRunning()) {
                mDHT.start();
            }

            if (mDHT.totalNodes() < 1) {
                mDHT.waitNodes(30);
            }

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

                File saveDirectory = new File(mSaveLocation);
                if(!saveDirectory.isDirectory()) {
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

                TorrentInfo torrentInfo = getTorrentInfo(torrentUrl);

                if(torrentInfo == null) {
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

    public void stopStream() {
        stopStream(true);
    }

    public void stopStream(Boolean removeDownloadedFiles) {
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

            if (removeDownloadedFiles) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        int tries = 0;
                        while(!FileUtils.recursiveDelete(saveLocation) && tries < 5) {
                            tries++;
                            try {
                                wait(1000); // If deleted failed then something is still using the file, wait and then retry
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
    }

    public Boolean applySettings(SettingsPack settingsPack) {
        if(mTorrentSession == null)
            return false;
        mTorrentSession.applySettings(settingsPack);
        return true;
    }

    public boolean isStreaming() {
        return mIsStreaming;
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
            if(alert.getType() == AlertType.TORRENT_ADDED) {
                InternalTorrentListener listener = new InternalTorrentListener();
                mCurrentTorrent = new Torrent(((TorrentAddedAlert) alert).getHandle(), listener);
                mTorrentSession.addListener(mCurrentTorrent);
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
