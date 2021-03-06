/* ******************************************************************************
 * Copyright (c) 2020 Konduit K.K.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/

package ai.konduit.serving.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.nd4j.shade.guava.base.Strings;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class LogUtils {
    /**
     * Gets the file where the logs are.
     * @return the logs file.
     */
    public static File getLogsFile() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        FileAppender<ILoggingEvent> fileAppender = (FileAppender<ILoggingEvent>) rootLogger.getAppender("FILE");

        if(fileAppender != null) {
            return new File(fileAppender.getFile());
        } else {
            return null;
        }
    }

    /**
     * Reads the last n lines from a file
     * @param file file where the data to be read is.
     * @param numOfLastLinesToRead the number of last lines to read
     * @return read lines
     */
    public static String readLastLines(File file, int numOfLastLinesToRead) throws IOException {
        List<String> result = new ArrayList<>();

        try (ReversedLinesFileReader reader = new ReversedLinesFileReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null && result.size() < numOfLastLinesToRead) {
                result.add(line);
            }
        } catch (IOException e) {
            log.error("Error while reading log file", e);
            throw e;
        }

        Collections.reverse(result);
        return String.join(System.lineSeparator(), result);
    }

    /**
     * Sets the file appender with the name of "FILE" if needed. If it's already been setup,
     * it would be ignored.
     */
    public static void setFileAppenderIfNeeded() throws Exception {
        File previousLogsFile = getLogsFile();

        File newLogsFile = Paths.get(getLogsDir(), "main.log").toFile();

        if(!newLogsFile.equals(previousLogsFile)) {
            ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
                    org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);

            LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();

            if(previousLogsFile != null) {
                rootLogger.detachAppender("FILE");
            }

            FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
            fileAppender.setName("FILE");
            fileAppender.setFile(newLogsFile.getAbsolutePath());
            fileAppender.setContext(context);

            PatternLayoutEncoder patternLayoutEncoder = new PatternLayoutEncoder();
            patternLayoutEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
            patternLayoutEncoder.setContext(context);
            patternLayoutEncoder.start();

            fileAppender.setEncoder(patternLayoutEncoder);
            fileAppender.start();

            rootLogger.addAppender(fileAppender);
        }
    }

    /**
     * Finds the log file and sends the output as a String
     * @param numOfLastLinesToRead Number of lines to read from the last few lines. If it's less than 1 then it will
     *                             return all the log file data.
     * @return current jvm process logs for konduit-serving.
     */
    public static String getLogs(int numOfLastLinesToRead) throws IOException {
        File logsFile = getLogsFile();

        if(logsFile == null || !logsFile.exists()) return "";

        if(numOfLastLinesToRead > 0) {
            return readLastLines(logsFile, numOfLastLinesToRead);
        } else {
            try {
                return FileUtils.readFileToString(logsFile, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.error("Error reading logs file: ", e);
                throw e;
            }
        }
    }

    /**
     * Returns the directory where all log files are
     * @return log files directory
     */
    public static String getLogsDir() throws Exception {
        String konduitServingLogDirFromEnv = System.getenv("KONDUIT_SERVING_LOG_DIR");
        String selectedDirectory;
        if(!Strings.isNullOrEmpty(konduitServingLogDirFromEnv)) {
            File directory = new File(konduitServingLogDirFromEnv);
            if(!(directory.exists() && directory.isDirectory())) {
                throw new Exception(String.format("The path specified by the environment variable KONDUIT_SERVING_LOG_DIR=%s doesn't exist or is an invalid directory.", konduitServingLogDirFromEnv));
            } else {
                selectedDirectory = konduitServingLogDirFromEnv;
            }
        } else {
            selectedDirectory = System.getProperty("user.dir");
            File directory = new File(selectedDirectory);
            if(!(directory.exists() && directory.isDirectory())) {
                throw new Exception(String.format("The path specified by the system property user.dir=%s doesn't exist or is an invalid directory.", selectedDirectory));
            }
        }

        return selectedDirectory;
    }

    public static File getZippedLogs() throws Exception {
        File zippedFile = Paths.get(LogUtils.getLogsDir(), "logs.zip").toFile();

        try (BufferedOutputStream archiveStream = new BufferedOutputStream(new FileOutputStream(zippedFile))) {
            try (ArchiveOutputStream archive = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {
                File logsFile = getLogsFile();

                if(logsFile != null) {
                    ZipArchiveEntry entry = new ZipArchiveEntry(logsFile.getName());
                    archive.putArchiveEntry(entry);

                    try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(logsFile))) {
                        IOUtils.copy(input, archive);
                        archive.closeArchiveEntry();
                        archive.finish();
                    }
                } else {
                    throw new FileNotFoundException("No logs file found!");
                }
            }
        }

        return zippedFile;
    }
}
