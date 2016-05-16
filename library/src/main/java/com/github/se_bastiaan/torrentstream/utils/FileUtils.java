/*
 *
 *  * This file is part of TorrentStreamer-Android.
 *  *
 *  * TorrentStreamer-Android is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU Lesser General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * TorrentStreamer-Android is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU Lesser General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU Lesser General Public License
 *  * along with TorrentStreamer-Android. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.github.se_bastiaan.torrentstream.utils;

import java.io.File;

public final class FileUtils {

    private FileUtils() throws InstantiationException {
        throw new InstantiationException("This class is not created for instantiation");
    }

    /**
     * Delete every item below the File location
     *
     * @param file Location
     * @return {@code true} when successful delete
     */
    public static boolean recursiveDelete(File file) {
        if (file.isDirectory()) {
            String[] children = file.list();
            if (children == null) return false;
            for (String child : children) {
                recursiveDelete(new File(file, child));
            }
        }

        return file.delete();
    }

}
