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

package eu.sv244.torrentstream;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert;
import com.frostwire.jlibtorrent.alerts.PieceFinishedAlert;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import eu.sv244.torrentstream.listeners.TorrentListener;

public class Torrent implements AlertListener {

    private final static Integer MAX_PREPARE_COUNT = 20;
    private final static Integer MIN_PREPARE_COUNT = 2;
    private final static Integer DEFAULT_PREPARE_COUNT = 5;

    public enum State { UNKNOWN, RETRIEVING_META, STARTING, STREAMING }

    private Integer mPiecesToPrepare;
    private Integer mLastPieceIndex;
    private Integer mFirstPieceIndex;
    private Integer mSelectedFile = -1;
    private Long mPrepareSize;

    private Double mPrepareProgress = 0d;
    private Double mProgressStep = 0d;
    private List<Integer> mPreparePieces;
    private Boolean[] mHasPieces;
    private Integer[] mProcessingPieces = new Integer[5];

    private TorrentHandle mTorrentHandle;
    private TorrentListener mListener;

    private State mState = State.RETRIEVING_META;

    /**
     * The constructor for a new Torrent
     *
     * First the largest file in the download is selected as the file for playback
     *
     * After setting this priority, the first and last index of the pieces that make up this file are determined.
     * And last: amount of pieces that are needed for playback are calculated (needed for playback means: make up 10 megabyte of the file)
     *
     * @param torrentHandle jlibtorrent TorrentHandle
     */
    public Torrent(TorrentHandle torrentHandle, TorrentListener listener, Long prepareSize) {
        mTorrentHandle = torrentHandle;
        mListener = listener;

        mPrepareSize = prepareSize;

        torrentHandle.setPriority(Priority.NORMAL.getSwig());

        if(mSelectedFile == -1)
            setLargestFile();

        if(mListener != null)
            mListener.onStreamPrepared(this);
    }

    /**
     * Reset piece priorities
     * First set all piece priorities to {@link Priority}.IGNORE and then set the file priority to the file selected for playback.
     */
    private void resetPriorities() {
        Priority[] priorities = mTorrentHandle.getPiecePriorities();
        for (int i = 0; i < priorities.length; i++) {
            if(i >= mFirstPieceIndex && i <= mLastPieceIndex) {
                mTorrentHandle.setPiecePriority(i, Priority.NORMAL);
            } else {
                mTorrentHandle.setPiecePriority(i, Priority.IGNORE);
            }
        }
    }

    /**
     * Get LibTorrent torrent handle of this torrent
     * @return
     */
    public TorrentHandle getTorrentHandle() {
        return mTorrentHandle;
    }

    public File getVideoFile() {
        return new File(mTorrentHandle.getSavePath() + "/" + mTorrentHandle.getTorrentInfo().getFiles().getFilePath(mSelectedFile));
    }

    public File getSaveLocation() {
        return new File(mTorrentHandle.getSavePath() + "/" + mTorrentHandle.getName());
    }

    public void resume() {
        mTorrentHandle.resume();
    }

    public void pause() {
        mTorrentHandle.pause();
    }

    public void setLargestFile() {
        setSelectedFile(-1);
    }

    /**
     * Set the file to download
     * If the given index is -1, then the largest file is chosen
     * @param selectedFileIndex Integer
     */
    public void setSelectedFile(Integer selectedFileIndex) {
        TorrentInfo torrentInfo = mTorrentHandle.getTorrentInfo();
        FileStorage fileStorage = torrentInfo.getFiles();

        if(selectedFileIndex == -1) {
            long highestFileSize = 0;
            int selectedFile = -1;
            for (int i = 0; i < fileStorage.getNumFiles(); i++) {
                long fileSize = fileStorage.getFileSize(i);
                if (highestFileSize < fileSize) {
                    highestFileSize = fileSize;
                    mTorrentHandle.setFilePriority(selectedFile, Priority.IGNORE);
                    selectedFile = i;
                    mTorrentHandle.setFilePriority(i, Priority.NORMAL);
                } else {
                    mTorrentHandle.setFilePriority(i, Priority.IGNORE);
                }
            }
            selectedFileIndex = selectedFile;
        } else {
            for (int i = 0; i < fileStorage.getNumFiles(); i++) {
                if(i == selectedFileIndex) {
                    mTorrentHandle.setFilePriority(i, Priority.NORMAL);
                } else {
                    mTorrentHandle.setFilePriority(i, Priority.IGNORE);
                }
            }
        }
        mSelectedFile = selectedFileIndex;

        Priority[] piecePriorities = mTorrentHandle.getPiecePriorities();
        int firstPieceIndex = -1;
        int lastPieceIndex = -1;
        for (int i = 0; i < piecePriorities.length; i++) {
            if (piecePriorities[i] != Priority.IGNORE) {
                if (firstPieceIndex == -1) {
                    firstPieceIndex = i;
                }
                piecePriorities[i] = Priority.IGNORE;
            } else {
                if (firstPieceIndex != -1 && lastPieceIndex == -1) {
                    lastPieceIndex = i - 1;
                }
            }
        }

        if (lastPieceIndex == -1) {
            lastPieceIndex = piecePriorities.length - 1;
        }
        int pieceCount = lastPieceIndex - firstPieceIndex + 1;
        int pieceLength = mTorrentHandle.getTorrentInfo().getPieceLength();
        int activePieceCount;
        if (pieceLength > 0) {
            activePieceCount = (int) (mPrepareSize / pieceLength);
            if (activePieceCount < MIN_PREPARE_COUNT) {
                activePieceCount = MIN_PREPARE_COUNT;
            } else if (activePieceCount > MAX_PREPARE_COUNT) {
                activePieceCount = MAX_PREPARE_COUNT;
            }
        } else {
            activePieceCount = DEFAULT_PREPARE_COUNT;
        }

        if (pieceCount < activePieceCount) {
            activePieceCount = pieceCount / 2;
        }

        mFirstPieceIndex = firstPieceIndex;
        mLastPieceIndex = lastPieceIndex;
        mPiecesToPrepare = activePieceCount;
    }

    public String[] getFileNames() {
        FileStorage fileStorage = mTorrentHandle.getTorrentInfo().getFiles();
        String[] fileNames = new String[fileStorage.getNumFiles()];
        for(int i = 0; i < fileStorage.getNumFiles(); i++) {
            fileNames[i] = fileStorage.getFileName(i);
        }
        return fileNames;
    }

    /**
     * Prepare torrent for playback. Prioritize the first `mPiecesToPrepare` pieces and the last `mPiecesToPrepare` pieces
     * from `mFirstPieceIndex` and `mLastPieceIndex`. Ignore all other pieces.
     */
    public void startDownload() {
        if(mState == State.STREAMING) return;
        mState = State.STARTING;
        mTorrentHandle.setPriority(Priority.NORMAL.getSwig());

        List<Integer> indices = new ArrayList<>();

        Priority[] priorities = mTorrentHandle.getPiecePriorities();
        for (int i = 0; i < priorities.length; i++) {
            if(priorities[i] != Priority.IGNORE) {
                mTorrentHandle.setPiecePriority(i, Priority.NORMAL);
            }
        }

        for (int i = 0; i < mPiecesToPrepare; i++) {
            indices.add(mLastPieceIndex - i);
            mTorrentHandle.setPiecePriority(mLastPieceIndex - i, Priority.SEVEN);
            mTorrentHandle.setPieceDeadline(mLastPieceIndex - i, 1000);
        }

        for (int i = 0; i < mPiecesToPrepare; i++) {
            indices.add(mFirstPieceIndex + i);
            mTorrentHandle.setPiecePriority(mFirstPieceIndex + i, Priority.SEVEN);
            mTorrentHandle.setPieceDeadline(mFirstPieceIndex + i, 1000);
        }

        mPreparePieces = indices;

        mHasPieces = new Boolean[mLastPieceIndex - mFirstPieceIndex + 1];
        Arrays.fill(mHasPieces, false);

        TorrentInfo torrentInfo = mTorrentHandle.getTorrentInfo();
        TorrentStatus status = mTorrentHandle.getStatus();

        double blockCount = 0;
        for(Integer index : indices) {
            blockCount += (int) Math.ceil(torrentInfo.getPieceSize(index) / status.getBlockSize());
        }

        mProgressStep = 100 / blockCount;

        mTorrentHandle.resume();

        mListener.onStreamStarted(this);
    }

    private void startSequentialMode() {
        resetPriorities();

        if(mHasPieces == null) {
            mTorrentHandle.setSequentialDownload(true);
        } else {
            for (int i = mFirstPieceIndex + mPiecesToPrepare; i < mFirstPieceIndex + mPiecesToPrepare + 5; i++) {
                mTorrentHandle.setPiecePriority(i, Priority.SEVEN);
                mTorrentHandle.setPieceDeadline(i, 1000);
            }
        }
    }

    public State getState() {
        return mState;
    }

    public void pieceFinished(PieceFinishedAlert alert) {
        if(mState == State.STREAMING && mHasPieces != null) {
            mHasPieces[alert.getPieceIndex() - mFirstPieceIndex] = true;

            for(int i = alert.getPieceIndex() - mFirstPieceIndex; i < mHasPieces.length; i++) {
                if(!mHasPieces[i]) {
                    mTorrentHandle.setPiecePriority(i + mFirstPieceIndex, Priority.SEVEN);
                    mTorrentHandle.setPieceDeadline(i + mFirstPieceIndex, 1000);
                    break;
                }
            }
        } else {
            Iterator<Integer> piecesIterator = mPreparePieces.iterator();
            while (piecesIterator.hasNext()) {
                int index = piecesIterator.next();
                if (index == alert.getPieceIndex()) {
                    piecesIterator.remove();
                }
            }

            if(mHasPieces != null)
                mHasPieces[alert.getPieceIndex() - mFirstPieceIndex] = true;

            if (mPreparePieces.size() == 0) {
                startSequentialMode();

                mState = State.STREAMING;

                if (mListener != null)
                    mListener.onStreamReady(this);
            }
        }
    }

    public void blockFinished(BlockFinishedAlert alert) {
        for (Integer index : mPreparePieces) {
            if (index == alert.getPieceIndex()) {
                mPrepareProgress += mProgressStep;
            }
        }

        sendStreamProgress();
    }

    private void sendStreamProgress() {
        TorrentStatus status = mTorrentHandle.getStatus();
        float progress = status.getProgress() * 100;
        int seeds = status.getNumSeeds();
        int downloadSpeed = status.getDownloadPayloadRate();

        if(mListener != null && mPrepareProgress >= 1)
            mListener.onStreamProgress(this, new StreamStatus(progress, mPrepareProgress.intValue(), seeds, downloadSpeed));
    }

    @Override
    public int[] types() {
        return new int[] { AlertType.PIECE_FINISHED.getSwig(), AlertType.BLOCK_FINISHED.getSwig() };
    }

    @Override
    public void alert(Alert<?> alert) {
        switch (alert.getType()) {
            case PIECE_FINISHED:
                pieceFinished((PieceFinishedAlert) alert);
                break;
            case BLOCK_FINISHED:
                blockFinished((BlockFinishedAlert) alert);
                break;
        }
    }

}