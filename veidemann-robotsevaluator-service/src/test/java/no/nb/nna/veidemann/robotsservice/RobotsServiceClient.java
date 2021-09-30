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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import no.nb.nna.veidemann.api.config.v1.ConfigObject;
import no.nb.nna.veidemann.api.config.v1.ConfigRef;
import no.nb.nna.veidemann.api.frontier.v1.QueuedUri;
import no.nb.nna.veidemann.api.robotsevaluator.v1.IsAllowedReply;
import no.nb.nna.veidemann.api.robotsevaluator.v1.IsAllowedRequest;
import no.nb.nna.veidemann.api.robotsevaluator.v1.RobotsEvaluatorGrpc;
import no.nb.nna.veidemann.commons.client.GrpcUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class RobotsServiceClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RobotsServiceClient.class);

    private final ManagedChannel channel;

    private final RobotsEvaluatorGrpc.RobotsEvaluatorBlockingStub blockingStub;

    private final RobotsEvaluatorGrpc.RobotsEvaluatorStub asyncStub;

    public RobotsServiceClient(final String host, final int port) {
        this(ManagedChannelBuilder.forAddress(host, port).usePlaintext());
        LOG.info("Robots service client pointing to " + host + ":" + port);
    }

    public RobotsServiceClient(ManagedChannelBuilder<?> channelBuilder) {
        LOG.info("Setting up Robots service client");
        channel = channelBuilder.build();
        blockingStub = RobotsEvaluatorGrpc.newBlockingStub(channel);
        asyncStub = RobotsEvaluatorGrpc.newStub(channel);
    }

    public boolean isAllowed(QueuedUri queuedUri, String userAgent, ConfigObject politeness, ConfigRef collectionRef) {
        try {
            IsAllowedRequest request = IsAllowedRequest.newBuilder()
                    .setJobExecutionId(queuedUri.getJobExecutionId())
                    .setExecutionId(queuedUri.getExecutionId())
                    .setUri(queuedUri.getUri())
                    .setUserAgent(userAgent)
                    .setPoliteness(politeness)
                    .setCollectionRef(collectionRef)
                    .build();

            IsAllowedReply reply = GrpcUtil.forkedCall(() -> blockingStub.isAllowed(request));

            return reply.getIsAllowed();
        } catch (StatusRuntimeException ex) {
            Code code = ex.getStatus().getCode();
            if (code.equals(Status.CANCELLED.getCode())
                    || code.equals(Status.DEADLINE_EXCEEDED.getCode())
                    || code.equals(Status.ABORTED.getCode())) {
                LOG.warn("Request was aborted", ex);
            } else {
                LOG.error("RPC failed: " + ex.getStatus(), ex);
            }
            throw ex;
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }


}
