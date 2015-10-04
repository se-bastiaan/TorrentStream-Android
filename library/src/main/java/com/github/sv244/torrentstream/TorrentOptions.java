/*
 *
 *  * This file is part of TorrentStreamer-Android.
 *  *
 *  * TorrentStreamer-Android is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * TorrentStreamer-Android is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with TorrentStreamer-Android. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.github.sv244.torrentstream;

import java.io.File;

public class TorrentOptions {

    protected String mSaveLocation = "/", mProxyHost, mProxyUsername, mProxyPassword, mPeerFingerprint;
    protected Integer mMaxDownloadSpeed = 0, mMaxUploadSpeed = 0, mMaxConnections = 200, mMaxDht = 88, mListeningPort = -1;
    protected Boolean mRemoveFiles = false, mAnonymousMode = true;
    protected Long mPrepareSize = 10 * 1024L * 1024L;

    public void setSaveLocation(String saveLocation) {
        mSaveLocation = saveLocation;
    }

    public void setSaveLocation(File saveLocation) {
        mSaveLocation = saveLocation.getAbsolutePath();
    }

    public void setMaxUploadSpeed(Integer maxUploadSpeed) {
        mMaxUploadSpeed = maxUploadSpeed;
    }

    public void setMaxDownloadSpeed(Integer maxDownloadSpeed) {
        mMaxDownloadSpeed = maxDownloadSpeed;
    }

    public void setMaxConnections(Integer maxConnections) {
        mMaxConnections = maxConnections;
    }

    public void setMaxActiveDHT(Integer maxActiveDHT) {
        mMaxDht = maxActiveDHT;
    }

    public void setRemoveFilesAfterStop(Boolean b) {
        mRemoveFiles = b;
    }

    public void setPrepareSize(Long prepareSize) {
        mPrepareSize = prepareSize;
    }

    public void setListeningPort(Integer port) {
        mListeningPort = port;
    }

    public void setProxy(String host, String username, String password) {
        mProxyHost = host;
        mProxyUsername = username;
        mProxyPassword = password;
    }

    public void setPeerFingerprint(String peerId) {
        mPeerFingerprint = peerId;
        mAnonymousMode = false;
    }

    public void setAnonymousMode(Boolean enable) {
        mAnonymousMode = enable;
        if(mAnonymousMode)
            mPeerFingerprint = null;
    }


}
