/*
 * Copyright 2017 Decebal Suiu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ro.fortsoft.pf4j.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Downloads a file from a URL.
 *
 * @author Decebal Suiu
 */
public class SimpleFileDownloader implements FileDownloader {

    private static final Logger log = LoggerFactory.getLogger(SimpleFileDownloader.class);

    public Path downloadFile(URL fileUrl) throws PluginException, IOException {
        Path destination = Files.createTempDirectory("pf4j-update-downloader");
        destination.toFile().deleteOnExit();

        // create a temporary file
        Path tmpFile = destination.resolve(DigestUtils.getSHA1(fileUrl.toString()) + ".tmp");
        Files.deleteIfExists(tmpFile);

        log.debug("Download '{}' to '{}'", fileUrl, tmpFile);

        // set up the URL connection
        URLConnection connection = fileUrl.openConnection();

        // connect to the remote site (may takes some time)
        connection.connect();

        // check for http authorization
        HttpURLConnection httpConnection = (HttpURLConnection) connection;
        if (httpConnection.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED) {
            throw new ConnectException("HTTP Authorization failure");
        }

        // try to get the server-specified last-modified date of this artifact
        long lastModified = httpConnection.getHeaderFieldDate("Last-Modified", System.currentTimeMillis());

        // try to get the input stream (three times)
        InputStream is = null;
        for (int i = 0; i < 3; i++) {
            try {
                is = connection.getInputStream();
                break;
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
        if (is == null) {
            throw new ConnectException("Can't get '" + fileUrl + " to '" + tmpFile + "'");
        }

        // reade from remote resource and write to the local file
        FileOutputStream fos = new FileOutputStream(tmpFile.toFile());
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) >= 0) {
            fos.write(buffer, 0, length);
        }
        fos.close();
        is.close();

        // rename tmp file to resource file
        String path = fileUrl.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        Path file = destination.resolve(fileName);
        Files.deleteIfExists(file);
        log.debug("Rename '{}' to {}", tmpFile, file);
        Files.move(tmpFile, file);

        log.debug("Set last modified of '{}' to '{}'", file, lastModified);
        Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified));

        return file;
    }

}
