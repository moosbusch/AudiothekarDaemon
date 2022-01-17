/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.moosbusch.audiothekar.concurrent;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import io.github.moosbusch.audiothekar.filter.ClientFilter;
import io.github.moosbusch.audiothekar.util.AudiothekarUtil;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author komano
 */
public class DownloadVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LogManager.getLogger(DownloadVerticle.class);
    private final String feedUrl;
    private final String outputDirname;
    private final String feedTitle;
    private Client client;

    public DownloadVerticle(final String feedTitle, final String feedUrl, final String outputDirname) {
        this.feedTitle = feedTitle;
        this.feedUrl = feedUrl;
        this.outputDirname = outputDirname;
    }

    private boolean execute() {
        final WebTarget webTarget = client.target(feedUrl);
        final Invocation.Builder invocationBuilder = webTarget.request();
        boolean result;
        
        try (Response response = invocationBuilder.get()) {
            final String responseReasonPhrase = response.getStatusInfo().getReasonPhrase();
            final int responseStatusCode = response.getStatusInfo().getStatusCode();
            
            LOGGER.info(webTarget.getUri());
            LOGGER.info(responseReasonPhrase);
            LOGGER.info(responseStatusCode);

            if (responseStatusCode < 400) {
                final String feedAsString = AudiothekarUtil.removeBOM(response.readEntity(String.class));

                if (StringUtils.contains(feedAsString, "<!DOCTYPE html>")) {
                    LOGGER.warn("Skipping " + webTarget.getUri() + ". CAUSE: Response consists of HTML only. Maybe redirection due to non-existing feed?");
                    result = false;
                } else {
                    try {
                        parseFeed(feedAsString);
                        result = true;
                    } catch (IOException | FeedException ex) {
                        LOGGER.error(ex.getMessage());
                        result = false;
                    }
                }
            } else {
                result = false;
            }
        }

        return result;
    }

    private void parseFeed(final String feedAsString) throws IOException, FeedException {
        final Map<String, String> feedContents = new LinkedHashMap<>();
        final SyndFeedInput syndFeedInput = new SyndFeedInput();
        final SyndFeed syndFeed = syndFeedInput.build(new StringReader(feedAsString));
        final List<SyndEntry> syndEntries = syndFeed.getEntries();
        int syndEntryCnt = -1;

        for (final SyndEntry syndEntry : syndEntries) {
            final List<SyndEnclosure> syndEnclosures = syndEntry.getEnclosures();

            syndEntryCnt++;

            if (!syndEnclosures.isEmpty()) {
                final SyndEnclosure syndEnclosure = syndEntry.getEnclosures().get(0);
                final String syndEntryTitle = syndEntry.getTitle();

                feedContents.put(syndEntryTitle, syndEnclosure.getUrl());
            } else {
                LOGGER.warn("Feed " + feedUrl + ", index #" + syndEntryCnt + "  has no items to process...");
            }
        }

        downloadFeedContents(feedContents);
    }

    private boolean downloadFeedContents(final Map<String, String> feedContents) throws IOException {
        final File mainOutputDir = new File(outputDirname);

        if (!mainOutputDir.exists()) {
            if (!mainOutputDir.mkdir()) {
                LOGGER.error("Unable to create main output-directory. Exiting");
                return false;
            }
        }

        for (final Map.Entry<String, String> feedContent : feedContents.entrySet()) {
            final String urlEncodedFeedTitle = AudiothekarUtil.encodeFileObjectNameUTF8(feedTitle, 128);
            final String episodeTitle = AudiothekarUtil.encodeFileObjectNameUTF8(feedContent.getKey(), 128);
            final String episodeUrl = feedContent.getValue();
            final String fileSuffix = episodeUrl.substring(episodeUrl.lastIndexOf("."));
            final String feedOutputDirname = mainOutputDir + "/" + urlEncodedFeedTitle;
            final String outputFilename = feedOutputDirname + "/" + episodeTitle + fileSuffix;
            final File outputFile = new File(outputFilename);
            final File feedOutputDir = new File(feedOutputDirname);

            if (!feedOutputDir.exists()) {
                if (!feedOutputDir.mkdir()) {
                    LOGGER.error("Unable to create output-directory for feed " + urlEncodedFeedTitle + ". Exiting");
                    return false;
                }
            }

            if (outputFile.exists() && outputFile.length() > 0) {
                LOGGER.debug("File " + outputFile.getName() + " already exists. Skipping...");
                continue;
            }

            final WebTarget webTarget = client.target(episodeUrl);
            final Invocation.Builder invocationBuilder = webTarget.request();

            try (Response response = invocationBuilder.get()) {
                final String responseReasonPhrase = response.getStatusInfo().getReasonPhrase();
                final int responseStatusCode = response.getStatusInfo().getStatusCode();
                final InputStream resonseEntityAsStream;
                final OutputStream outputFileStream;

                LOGGER.info(webTarget.getUri());
                LOGGER.info(responseReasonPhrase);
                LOGGER.info(responseStatusCode);

                if (responseStatusCode < 400) {
                    resonseEntityAsStream = response.readEntity(InputStream.class);
                    outputFileStream = new BufferedOutputStream(new FileOutputStream(outputFile));
                    IOUtils.copy(resonseEntityAsStream, outputFileStream);

                    outputFileStream.flush();
                    IOUtils.close(resonseEntityAsStream);
                    IOUtils.close(outputFileStream);
                    LOGGER.info(outputFile.getAbsoluteFile() + " saved");
                } else {
                    final StringBuilder errorStrBuilder = new StringBuilder();

                    errorStrBuilder.append("Failed to process episode: '");
                    errorStrBuilder.append(episodeTitle);
                    errorStrBuilder.append("' of feed '");
                    errorStrBuilder.append(feedTitle);
                    errorStrBuilder.append("'. Cause: Http-Status was: ");
                    errorStrBuilder.append(responseStatusCode);
                    errorStrBuilder.append("-");
                    errorStrBuilder.append(responseReasonPhrase);
                    
                    LOGGER.warn(errorStrBuilder.toString());
                }
            }
        }

        return true;
    }

    @Override
    public final void start() throws Exception {
        super.start();
        client = ClientBuilder.newClient();
        client.register(ClientFilter.class);
    }

    @Override
    public final void start(Promise<Void> startPromise) throws Exception {
        start();
        if (execute()) {
            startPromise.complete();
        } else {
            final StringBuilder errorStrBuilder = new StringBuilder();

            errorStrBuilder.append("Failed to process feed: '");
            errorStrBuilder.append(feedTitle);
            errorStrBuilder.append("' (");
            errorStrBuilder.append(feedUrl);
            errorStrBuilder.append(")");

            startPromise.fail(errorStrBuilder.toString());
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        client.close();
    }

    public String getFeedUrl() {
        return feedUrl;
    }

    public String getFeedTitle() {
        return feedTitle;
    }

}
