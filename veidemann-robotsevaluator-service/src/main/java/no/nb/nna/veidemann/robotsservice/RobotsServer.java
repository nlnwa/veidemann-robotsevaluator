/*
 * Copyright 2017 National Library of Norway.
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
package no.nb.nna.veidemann.robotsservice;


import com.typesafe.config.Config;
import com.typesafe.config.ConfigBeanFactory;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import io.jaegertracing.Configuration;
import no.nb.nna.veidemann.robotsservice.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class RobotsServer {

    private static final Logger LOG = LoggerFactory.getLogger(RobotsServer.class);

    private static final Settings SETTINGS;

    static {
        Config config = ConfigFactory.load();
        config.checkValid(ConfigFactory.defaultReference());
        SETTINGS = ConfigBeanFactory.create(config, Settings.class);

        Tracer tracer = Configuration.fromEnv().getTracer();
        GlobalTracer.registerIfAbsent(tracer);
    }

    public RobotsServer() {
    }

    /**
     * Start the service.
     * <p>
     *
     * @return this instance
     */
    public RobotsServer start() {
        try (RobotsCache robotsCache = new RobotsCache(SETTINGS.getProxyHost(), SETTINGS.getProxyPort());
             RobotsApiServer apiServer = new RobotsApiServer(SETTINGS.getApiPort(), robotsCache)) {

            registerShutdownHook();

            apiServer.start();

            LOG.info("Veidemann Robots Evaluator (v. {}) started", RobotsServer.class.getPackage().getImplementationVersion());

            try {
                Thread.currentThread().join();
            } catch (InterruptedException ex) {
                // Interrupted, shut down
            }
        } catch (ConfigException ex) {
            System.err.println("Configuration error: " + ex.getLocalizedMessage());
            System.exit(1);
        }

        return this;
    }

    private void registerShutdownHook() {
        Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");

            mainThread.interrupt();
            try {
                mainThread.join();
            } catch (InterruptedException e) {
                //
            }

            System.err.println("*** gracefully shut down");
        }));
    }

    /**
     * Get the settings object.
     * <p>
     *
     * @return the settings
     */
    public static Settings getSettings() {
        return SETTINGS;
    }

}
