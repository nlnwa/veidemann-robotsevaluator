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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import no.nb.nna.veidemann.api.robotsevaluator.v1.IsAllowedReply;
import no.nb.nna.veidemann.api.robotsevaluator.v1.IsAllowedRequest;
import no.nb.nna.veidemann.api.robotsevaluator.v1.RobotsEvaluatorGrpc;
import no.nb.nna.veidemann.robotsparser.RobotsTxt;
import no.nb.nna.veidemann.robotsparser.RobotsTxtParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Objects;

import static no.nb.nna.veidemann.robotsparser.RobotsTxt.EMPTY_ALLOWED_REPLY;
import static no.nb.nna.veidemann.robotsservice.RobotsCache.EMPTY_ROBOTS;

/**
 *
 */
public class RobotsService extends RobotsEvaluatorGrpc.RobotsEvaluatorImplBase {

    private static final Logger LOG = LoggerFactory.getLogger(RobotsService.class);

    private final RobotsCache cache;

    private final RobotsTxtParser ROBOTS_TXT_PARSER = new RobotsTxtParser();

    public RobotsService(RobotsCache cache) {
        this.cache = cache;
    }

    @Override
    public void isAllowed(IsAllowedRequest request, StreamObserver<IsAllowedReply> respObserver) {
        Objects.requireNonNull(request.getExecutionId());
        Objects.requireNonNull(request.getJobExecutionId());
        Objects.requireNonNull(request.getPoliteness());
        Objects.requireNonNull(request.getUnknownFields());
        Objects.requireNonNull(request.getUserAgent());
        Objects.requireNonNull(request.getCollectionRef());
        try {
            URL uri = new URL(request.getUri());
            int ttlSeconds = request.getPoliteness().getPolitenessConfig().getMinimumRobotsValidityDurationS();
            if (ttlSeconds == 0) {
                ttlSeconds = 300;
            }
            IsAllowedReply reply;

            switch (request.getPoliteness().getPolitenessConfig().getRobotsPolicy()) {
                case OBEY_ROBOTS:
                case OBEY_ROBOTS_CLASSIC:
                    reply = cache.get(uri, ttlSeconds, request.getExecutionId(), request.getJobExecutionId(), request.getCollectionRef().getId())
                            .isAllowed(request.getUserAgent(), uri);
                    break;
                case IGNORE_ROBOTS:
                    reply = EMPTY_ALLOWED_REPLY;
                    break;
                case CUSTOM_ROBOTS:
                case CUSTOM_ROBOTS_CLASSIC:
                    reply = ROBOTS_TXT_PARSER.parse(request.getPoliteness().getPolitenessConfig().getCustomRobots(), "custom")
                            .isAllowed(request.getUserAgent(), uri);
                    break;
                case CUSTOM_IF_MISSING:
                case CUSTOM_IF_MISSING_CLASSIC:
                    RobotsTxt r = cache.get(uri, ttlSeconds, request.getExecutionId(), request.getJobExecutionId(), request.getCollectionRef().getId());
                    if (r == EMPTY_ROBOTS) {
                        reply = ROBOTS_TXT_PARSER.parse(request.getPoliteness().getPolitenessConfig().getCustomRobots(), "custom")
                                .isAllowed(request.getUserAgent(), uri);
                    } else {
                        reply = r.isAllowed(request.getUserAgent(), uri);
                    }
                    break;
                default:
                    LOG.warn("Robots Policy '{}' is not implemented.", request.getPoliteness()
                            .getPolitenessConfig().getRobotsPolicy());
                    reply = EMPTY_ALLOWED_REPLY;
                    break;
            }

            respObserver.onNext(reply);
            respObserver.onCompleted();
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            Status status = Status.UNKNOWN.withDescription(ex.toString());
            respObserver.onError(status.asException());
        }
    }
}
