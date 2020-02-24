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

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.opentracing.contrib.ServerTracingInterceptor;
import io.opentracing.util.GlobalTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class RobotsApiServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RobotsApiServer.class);

    private final Server server;
    private final ExecutorService threadPool;

    public RobotsApiServer(int port, RobotsCache robotsCache) {
        this(ServerBuilder.forPort(port), robotsCache);
    }

    public RobotsApiServer(ServerBuilder<?> serverBuilder, RobotsCache robotsCache) {

        ServerTracingInterceptor tracingInterceptor = new ServerTracingInterceptor.Builder(GlobalTracer.get())
                .withTracedAttributes(ServerTracingInterceptor.ServerRequestAttribute.CALL_ATTRIBUTES,
                        ServerTracingInterceptor.ServerRequestAttribute.METHOD_TYPE)
                .build();

        threadPool = Executors.newCachedThreadPool();
        serverBuilder.executor(threadPool);

        RobotsService robotsService = new RobotsService(robotsCache);
        server = serverBuilder.addService(tracingInterceptor.intercept(robotsService)).build();
    }

    public RobotsApiServer start() {
        try {
            server.start();

            LOG.info("Robots Evaluator api listening on {}", server.getPort());

            return this;
        } catch (IOException ex) {
            close();
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void close() {
        server.shutdown();
        try {
            server.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            server.shutdownNow();
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(5L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        System.err.println("*** server shut down");
    }
}
