/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.github.moosbusch.audiothekar;

import com.rometools.opml.feed.opml.Opml;
import com.rometools.opml.feed.opml.Outline;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.WireFeedInput;
import io.github.moosbusch.audiothekar.concurrent.DownloadVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author komano
 */
public class Main {

    private static final Logger LOGGER = LogManager.getLogger(Main.class);
    private final Vertx vertx;
    private final String opmlFilename;
    private final String outputDirname;
    private final AtomicInteger deployedVerticleCount;

    public Main(final String opmlFilename, final String outputDirname) {
        this.opmlFilename = opmlFilename;
        this.outputDirname = outputDirname;
        this.vertx = Vertx.vertx(new VertxOptions().setBlockedThreadCheckInterval(5).setBlockedThreadCheckIntervalUnit(TimeUnit.MINUTES));
        this.deployedVerticleCount = new AtomicInteger(0);

        execute();
    }

    private void execute() {
        Map<String, String> feeds = null;

//        try {
////            AudiothekarUtil.convertTree(new File ("/data/data/podcasts"));
//            AudiothekarUtil.listTree(new File ("/data/data/podcasts"));
//        } catch (IOException ex) {
//            LOGGER.error(ex.getMessage());
//        }
        try {
            feeds = parseOPML(opmlFilename);
        } catch (IOException | IllegalArgumentException | FeedException ex) {
            LOGGER.error(ex.getMessage());
            vertx.close();
        }

        if (feeds != null) {
            processFeeds(feeds);
        }
    }

    private Map<String, String> parseOPML(final String opmlFilename) throws IOException, FileNotFoundException, IllegalArgumentException, FeedException {
        final Map<String, String> result = new HashMap<>();
        final WireFeedInput wireFeedInput = new WireFeedInput();
        final Opml feedAsOpml = (Opml) wireFeedInput.build(new File(opmlFilename));;
        final List<Outline> outlines = (List<Outline>) feedAsOpml.getOutlines();

        outlines.forEach(outline -> {
            result.put(outline.getTitle(), outline.getXmlUrl());
        });

        return result;
    }

    private void processFeeds(final Map<String, String> feeds) {
        final List<DownloadVerticle> verticles = new ArrayList<>(0);

        for (final Map.Entry<String, String> feedEntry : feeds.entrySet()) {
            final String feedTitle = feedEntry.getKey();
            final String feedUrl = feedEntry.getValue();

            final DownloadVerticle verticle = new DownloadVerticle(feedTitle, feedUrl, outputDirname);

            verticles.add(verticle);
        }

        deployedVerticleCount.addAndGet(verticles.size());
        LOGGER.info("Deploying " + deployedVerticleCount.get() + " verticles.");

        for (final DownloadVerticle verticle : verticles) {
            vertx.deployVerticle(verticle, deployResult -> {

                if (deployResult.succeeded()) {
                    deployedVerticleCount.decrementAndGet();
                    LOGGER.info("Undeployed verticle...");
                    LOGGER.info("Total deployed verticles: " + deployedVerticleCount.get());
                } else {
                    deployedVerticleCount.decrementAndGet();
                    LOGGER.error("Deployment of verticle failed. Feed: " + verticle.getFeedTitle() + ", URL: " + verticle.getFeedUrl());
                }

                if (deployedVerticleCount.get() == 0) {
                    vertx.close();
                }

            });
        }
    }

    public static void main(String[] args) {
        final String opmlFilename = args[1];
        final String outputDirname = args[3];
        final Main main = new Main(opmlFilename, outputDirname);

        main.execute();
    }

}
