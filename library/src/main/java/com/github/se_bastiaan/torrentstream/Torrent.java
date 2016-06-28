/*
 * Copyright (C) 2015-2016 Sébastiaan (github.com/se-bastiaan)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.se_bastiaan.torrentstream;

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
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class Torrent implements AlertListener {

    private final static Integer MAX_PREPARE_COUNT = 20;
    private final static Integer MIN_PREPARE_COUNT = 2;
    private final static Integer DEFAULT_PREPARE_COUNT = 5;
    private final static Integer SEQUENTIAL_CONCURRENT_PIECES_COUNT = 5;

    public enum State {UNKNOWN, RETRIEVING_META, STARTING, STREAMING}

    private Integer piecesToPrepare;
    private Integer lastPieceIndex;
    private Integer firstPieceIndex;
    private Integer selectedFileIndex = -1;
    private Integer interestedPieceIndex = 0;

    private Double prepareProgress = 0d;
    private Double progressStep = 0d;
    private List<Integer> preparePieces;
    private Boolean[] hasPieces;

    private State state = State.RETRIEVING_META;

    private final TorrentHandle torrentHandle;
    private final TorrentListener listener;
    private final Long prepareSize;

    /**
     * The constructor for a new Torrent
     * <p/>
     * First the largest file in the download is selected as the file for playback
     * <p/>
     * After setting this priority, the first and last index of the pieces that make up this file are determined.
     * And last: amount of pieces that are needed for playback are calculated (needed for playback means: make up 10 megabyte of the file)
     *
     * @param torrentHandle jlibtorrent TorrentHandle
     */
    public Torrent(TorrentHandle torrentHandle, TorrentListener listener, Long prepareSize) {
        this.torrentHandle = torrentHandle;
        this.listener = listener;

        this.prepareSize = prepareSize;

        torrentHandle.setPriority(Priority.NORMAL.swig());

        if (selectedFileIndex == -1)
            setLargestFile();

        if (this.listener != null)
            this.listener.onStreamPrepared(this);
    }

    /**
     * Reset piece priorities of selected file to normal
     */
    private void resetPriorities() {
        Priority[] priorities = torrentHandle.getPiecePriorities();
        for (int i = 0; i < priorities.length; i++) {
            if (i >= firstPieceIndex && i <= lastPieceIndex) {
                torrentHandle.piecePriority(i, Priority.NORMAL);
            } else {
                torrentHandle.piecePriority(i, Priority.IGNORE);
            }
        }
    }

    /**
     * Get LibTorrent torrent handle of this torrent
     *
     * @return {@link TorrentHandle}
     */
    public TorrentHandle getTorrentHandle() {
        return torrentHandle;
    }

    public File getVideoFile() {
        return new File(torrentHandle.getSavePath() + "/" + torrentHandle.getTorrentInfo().files().filePath(selectedFileIndex));
    }

    /**
     * Get the location of the file that is being downloaded
     *
     * @return {@link File} The file location
     */
    public File getSaveLocation() {
        return new File(torrentHandle.getSavePath() + "/" + torrentHandle.getName());
    }

    /**
     * Resume the torrent download
     */
    public void resume() {
        torrentHandle.resume();
    }

    /**
     * Pause the torrent download
     */
    public void pause() {
        torrentHandle.pause();
    }

    /**
     * Set the selected file index to the largest file in the torrent
     */
    public void setLargestFile() {
        setSelectedFileIndex(-1);
    }

    /**
     * Set the index of the file that should be downloaded
     * If the given index is -1, then the largest file is chosen
     *
     * @param selectedFileIndex {@link Integer} Index of the file
     */
    public void setSelectedFileIndex(Integer selectedFileIndex) {
        TorrentInfo torrentInfo = torrentHandle.getTorrentInfo();
        FileStorage fileStorage = torrentInfo.files();

        if (selectedFileIndex == -1) {
            long highestFileSize = 0;
            int selectedFile = -1;
            for (int i = 0; i < fileStorage.numFiles(); i++) {
                long fileSize = fileStorage.fileSize(i);
                if (highestFileSize < fileSize) {
                    highestFileSize = fileSize;
                    torrentHandle.setFilePriority(selectedFile, Priority.IGNORE);
                    selectedFile = i;
                    torrentHandle.setFilePriority(i, Priority.NORMAL);
                } else {
                    torrentHandle.setFilePriority(i, Priority.IGNORE);
                }
            }
            selectedFileIndex = selectedFile;
        } else {
            for (int i = 0; i < fileStorage.numFiles(); i++) {
                if (i == selectedFileIndex) {
                    torrentHandle.setFilePriority(i, Priority.NORMAL);
                } else {
                    torrentHandle.setFilePriority(i, Priority.IGNORE);
                }
            }
        }
        this.selectedFileIndex = selectedFileIndex;

        Priority[] piecePriorities = torrentHandle.getPiecePriorities();
        int firstPieceIndexLocal = -1;
        int lastPieceIndexLocal = -1;
        for (int i = 0; i < piecePriorities.length; i++) {
            if (piecePriorities[i] != Priority.IGNORE) {
                if (firstPieceIndexLocal == -1) {
                    firstPieceIndexLocal = i;
                }
                piecePriorities[i] = Priority.IGNORE;
            } else {
                if (firstPieceIndexLocal != -1 && lastPieceIndexLocal == -1) {
                    lastPieceIndexLocal = i - 1;
                }
            }
        }

        if (lastPieceIndexLocal == -1) {
            lastPieceIndexLocal = piecePriorities.length - 1;
        }
        int pieceCount = lastPieceIndexLocal - firstPieceIndexLocal + 1;
        int pieceLength = torrentHandle.getTorrentInfo().pieceLength();
        int activePieceCount;
        if (pieceLength > 0) {
            activePieceCount = (int) (prepareSize / pieceLength);
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

        this.firstPieceIndex = firstPieceIndexLocal;
        this.interestedPieceIndex = this.firstPieceIndex;
        this.lastPieceIndex = lastPieceIndexLocal;
        piecesToPrepare = activePieceCount;
    }

    /**
     * Get the filenames of the files in the torrent
     *
     * @return {@link String[]}
     */
    public String[] getFileNames() {
        FileStorage fileStorage = torrentHandle.getTorrentInfo().files();
        String[] fileNames = new String[fileStorage.numFiles()];
        for (int i = 0; i < fileStorage.numFiles(); i++) {
            fileNames[i] = fileStorage.fileName(i);
        }
        return fileNames;
    }

    /**
     * Prepare torrent for playback. Prioritize the first {@code piecesToPrepare} pieces and the last {@code piecesToPrepare} pieces
     * from {@code firstPieceIndex} and {@code lastPieceIndex}. Ignore all other pieces.
     */
    public void startDownload() {
        if (state == State.STREAMING) return;
        state = State.STARTING;
        torrentHandle.setPriority(Priority.NORMAL.swig());

        List<Integer> indices = new ArrayList<>();

        Priority[] priorities = torrentHandle.getPiecePriorities();
        for (int i = 0; i < priorities.length; i++) {
            if (priorities[i] != Priority.IGNORE) {
                torrentHandle.piecePriority(i, Priority.NORMAL);
            }
        }

        for (int i = 0; i < piecesToPrepare; i++) {
            indices.add(lastPieceIndex - i);
            torrentHandle.piecePriority(lastPieceIndex - i, Priority.SEVEN);
            torrentHandle.setPieceDeadline(lastPieceIndex - i, 1000);
        }

        for (int i = 0; i < piecesToPrepare; i++) {
            indices.add(firstPieceIndex + i);
            torrentHandle.piecePriority(firstPieceIndex + i, Priority.SEVEN);
            torrentHandle.setPieceDeadline(firstPieceIndex + i, 1000);
        }

        preparePieces = indices;

        hasPieces = new Boolean[lastPieceIndex - firstPieceIndex + 1];
        Arrays.fill(hasPieces, false);

        TorrentInfo torrentInfo = torrentHandle.getTorrentInfo();
        TorrentStatus status = torrentHandle.getStatus();

        double blockCount = indices.size() * torrentInfo.pieceLength() / status.getBlockSize();

        progressStep = 100 / blockCount;

        torrentHandle.resume();

        listener.onStreamStarted(this);
    }

    /**
     * Check if the piece that contains the specified bytes were downloaded already
     *
     * @param bytes The bytes you're interested in
     * @return {@code true} if downloaded, {@code false} if not
     */
    public boolean hasBytes(long bytes) {
        if (hasPieces == null) {
            return false;
        }

        int pieceIndex = (int) (bytes / torrentHandle.getTorrentInfo().pieceLength());
        return hasPieces[pieceIndex];
    }

    /**
     * Set the bytes of the selected file that you're interested in
     * The piece of that specific offset is selected and that piece plus the 1 preceding and the 3 after it.
     * These pieces will then be prioritised, which results in continuing the sequential download after that piece
     *
     * @param bytes The bytes you're interested in
     */
    public void setInterestedBytes(long bytes) {
        if (hasPieces == null && bytes >= 0) {
            return;
        }

        int pieceIndex = (int) (bytes / torrentHandle.getTorrentInfo().pieceLength());
        interestedPieceIndex = pieceIndex;
        if (!hasPieces[pieceIndex] && torrentHandle.piecePriority(pieceIndex + firstPieceIndex) != Priority.SEVEN) {
            interestedPieceIndex = pieceIndex;
            int pieces = 5;
            for (int i = pieceIndex; i < hasPieces.length; i++) {
                // Set full priority to first found piece that is not confirmed finished
                if (!hasPieces[i]) {
                    torrentHandle.piecePriority(i + firstPieceIndex, Priority.SEVEN);
                    torrentHandle.setPieceDeadline(i + firstPieceIndex, 1000);
                    pieces--;
                    if (pieces == 0) {
                        break;
                    }
                }
            }
        }
    }

    /**
     * Checks if the interesting pieces are downloaded already
     *
     * @return {@code true} if the 5 pieces that were selected using `setInterestedBytes` are all reported complete including the `nextPieces`, {@code false} if not
     */
    public boolean hasInterestedBytes(int nextPieces) {
        for (int i = 0; i < 5 + nextPieces; i++) {
            int index = interestedPieceIndex + i;
            if (hasPieces.length <= index || index < 0) {
                continue;
            }

            if (!hasPieces[interestedPieceIndex + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if the interesting pieces are downloaded already
     *
     * @return {@code true} if the 5 pieces that were selected using `setInterestedBytes` are all reported complete, {@code false} if not
     */
    public boolean hasInterestedBytes() {
        return hasInterestedBytes(0);
    }

    /**
     * Get the index of the piece we're currently interested in
     * @return Interested piece index
     */
    public int getInterestedPieceIndex() {
        return interestedPieceIndex;
    }

    /**
     * Start sequential mode downloading
     */
    private void startSequentialMode() {
        resetPriorities();

        if (hasPieces == null) {
            torrentHandle.setSequentialDownload(true);
        } else {
            for (int i = firstPieceIndex + piecesToPrepare; i < firstPieceIndex + piecesToPrepare + SEQUENTIAL_CONCURRENT_PIECES_COUNT; i++) {
                torrentHandle.piecePriority(i, Priority.SEVEN);
                torrentHandle.setPieceDeadline(i, 1000);
            }
        }
    }

    /**
     * Get current torrent state
     *
     * @return {@link State}
     */
    public State getState() {
        return state;
    }

    /**
     * Piece finished
     *
     * @param alert
     */
    private void pieceFinished(PieceFinishedAlert alert) {
        if (state == State.STREAMING && hasPieces != null) {
            int pieceIndex = alert.pieceIndex() - firstPieceIndex;
            hasPieces[pieceIndex] = true;

            if (pieceIndex >= interestedPieceIndex) {
                for (int i = pieceIndex; i < hasPieces.length; i++) {
                    // Set full priority to first found piece that is not confirmed finished
                    if (!hasPieces[i]) {
                        torrentHandle.piecePriority(i + firstPieceIndex, Priority.SEVEN);
                        torrentHandle.setPieceDeadline(i + firstPieceIndex, 1000);
                        break;
                    }
                }
            }
        } else {
            Iterator<Integer> piecesIterator = preparePieces.iterator();
            while (piecesIterator.hasNext()) {
                int index = piecesIterator.next();
                if (index == alert.pieceIndex()) {
                    piecesIterator.remove();
                }
            }

            if (hasPieces != null) {
                hasPieces[alert.pieceIndex() - firstPieceIndex] = true;
            }

            if (preparePieces.size() == 0) {
                startSequentialMode();

                prepareProgress = 100d;
                sendStreamProgress();
                state = State.STREAMING;

                if (listener != null) {
                    listener.onStreamReady(this);
                }
            }
        }
    }

    private void blockFinished(BlockFinishedAlert alert) {
        for (Integer index : preparePieces) {
            if (index == alert.getPieceIndex()) {
                prepareProgress += progressStep;
                break;
            }
        }

        sendStreamProgress();
    }

    private void sendStreamProgress() {
        TorrentStatus status = torrentHandle.getStatus();
        float progress = status.getProgress() * 100;
        int seeds = status.getNumSeeds();
        int downloadSpeed = status.getDownloadPayloadRate();

        if (listener != null && prepareProgress >= 1) {
            listener.onStreamProgress(this, new StreamStatus(progress, prepareProgress.intValue(), seeds, downloadSpeed));
        }
    }

    @Override
    public int[] types() {
        return new int[]{
                AlertType.PIECE_FINISHED.swig(),
                AlertType.BLOCK_FINISHED.swig()
        };
    }

    @Override
    public void alert(Alert<?> alert) {
        switch (alert.type()) {
            case PIECE_FINISHED:
                pieceFinished((PieceFinishedAlert) alert);
                break;
            case BLOCK_FINISHED:
                blockFinished((BlockFinishedAlert) alert);
                break;
            default:
                break;
        }
    }

}