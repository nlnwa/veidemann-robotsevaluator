/*
 * Copyright 2019 National Library of Norway.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package no.nb.nna.veidemann.robotsservice;

import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import no.nb.nna.veidemann.api.config.v1.ConfigObject;
import no.nb.nna.veidemann.api.config.v1.ConfigRef;
import no.nb.nna.veidemann.api.config.v1.Kind;
import no.nb.nna.veidemann.api.config.v1.PolitenessConfig.RobotsPolicy;
import no.nb.nna.veidemann.api.frontier.v1.QueuedUri;
import no.nb.nna.veidemann.commons.client.RobotsServiceClient;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RobotsServiceTest {
    RobotsCache robotsCache;
    RobotsApiServer service;
    RobotsServiceClient client;
    MockWebServer webServer;
    ConfigRef collectionRef = ConfigRef.newBuilder().setKind(Kind.collection).setId("collection1").build();

    @Before
    public void setUp() throws Exception {
        // Create a MockWebServer. These are lean enough that you can create a new
        // instance for every unit test.
        webServer = new MockWebServer();

        final Dispatcher dispatcher = new Dispatcher() {

            @Override
            public MockResponse dispatch(RecordedRequest request) {
                switch (request.getRequestLine()) {
                    case "GET http://www.example.com/robots.txt HTTP/1.1":
                        return new MockResponse().setResponseCode(404);
                    case "GET http://www.example2.com/robots.txt HTTP/1.1":
                        return new MockResponse().setResponseCode(200).setBody("user-agent : userAgent\ndisallow: /forbidden\n");
                }
                return new MockResponse().setResponseCode(404);
            }
        };
        webServer.setDispatcher(dispatcher);

        // Start the server.
        webServer.start();

        // Ask the server for its URL. You'll need this to make HTTP requests.
        HttpUrl baseUrl = webServer.url("");

        // Create robotsCache
        robotsCache = new RobotsCache(baseUrl.host(), baseUrl.port());

        // Create Robots evaluator service
        InProcessServerBuilder serverBuilder = InProcessServerBuilder.forName("Robots service");
        service = new RobotsApiServer(serverBuilder, robotsCache);
        service.start();

        // Create Robots evaluator client
        client = new RobotsServiceClient(InProcessChannelBuilder.forName("Robots service"));
    }

    @After
    public void tearDown() throws Exception {
        // Shut down the server. Instances cannot be reused.
        client.close();
        service.close();
        robotsCache.close();
        webServer.shutdown();
    }

    @Test
    public void isAllowed_OBEY() throws Exception {
        QueuedUri quri = QueuedUri.newBuilder()
                .setUri("http://www.example.com")
                .setJobExecutionId("jid")
                .setExecutionId("eid")
                .build();

        ConfigObject.Builder politeness = ConfigObject.newBuilder();
        politeness.getPolitenessConfigBuilder()
                .setRobotsPolicy(RobotsPolicy.OBEY_ROBOTS);


        boolean allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com/forbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isFalse();

        RecordedRequest request1 = webServer.takeRequest();
        assertThat(request1.getRequestLine()).isEqualTo("GET http://www.example.com/robots.txt HTTP/1.1");
        assertThat(request1.getHeader("veidemann_eid")).isEqualTo("eid");
        assertThat(request1.getHeader("veidemann_jeid")).isEqualTo("jid");
        assertThat(request1.getHeader("veidemann_cid")).isEqualTo("collection1");

        RecordedRequest request2 = webServer.takeRequest();
        assertThat(request2.getRequestLine()).isEqualTo("GET http://www.example2.com/robots.txt HTTP/1.1");

        assertThat(webServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    public void isAllowed_CUSTOM() throws Exception {
        QueuedUri quri = QueuedUri.newBuilder()
                .setUri("http://www.example.com")
                .setJobExecutionId("jid")
                .setExecutionId("eid")
                .build();

        ConfigObject.Builder politeness = ConfigObject.newBuilder();
        politeness.getPolitenessConfigBuilder()
                .setCustomRobots("user-agent : userAgent\ndisallow: /customforbidden\n")
                .setRobotsPolicy(RobotsPolicy.CUSTOM_ROBOTS);


        boolean allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com/forbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com/customforbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isFalse();

        assertThat(webServer.getRequestCount()).isEqualTo(0);
    }

    @Test
    public void isAllowed_CUSTOM_IF_MISSING() throws Exception {
        QueuedUri quri = QueuedUri.newBuilder()
                .setUri("http://www.example.com")
                .setJobExecutionId("jid")
                .setExecutionId("eid")
                .build();

        ConfigObject.Builder politeness = ConfigObject.newBuilder();
        politeness.getPolitenessConfigBuilder()
                .setCustomRobots("user-agent : userAgent\ndisallow: /customforbidden\n")
                .setRobotsPolicy(RobotsPolicy.CUSTOM_IF_MISSING);


        boolean allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com/forbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isFalse();

        quri = quri.toBuilder()
                .setUri("http://www.example.com/customforbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isFalse();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com/customforbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        RecordedRequest request1 = webServer.takeRequest();
        assertThat(request1.getRequestLine()).isEqualTo("GET http://www.example.com/robots.txt HTTP/1.1");
        assertThat(request1.getHeader("veidemann_eid")).isEqualTo("eid");
        assertThat(request1.getHeader("veidemann_jeid")).isEqualTo("jid");
        assertThat(request1.getHeader("veidemann_cid")).isEqualTo("collection1");

        RecordedRequest request2 = webServer.takeRequest();
        assertThat(request2.getRequestLine()).isEqualTo("GET http://www.example2.com/robots.txt HTTP/1.1");

        assertThat(webServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    public void isAllowed_IGNORE() throws Exception {
        QueuedUri quri = QueuedUri.newBuilder()
                .setUri("http://www.example.com")
                .setJobExecutionId("jid")
                .setExecutionId("eid")
                .build();

        ConfigObject.Builder politeness = ConfigObject.newBuilder();
        politeness.getPolitenessConfigBuilder()
                .setRobotsPolicy(RobotsPolicy.IGNORE_ROBOTS);


        boolean allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        quri = quri.toBuilder()
                .setUri("http://www.example2.com/forbidden")
                .build();
        allowed = client.isAllowed(quri, "userAgent", politeness.build(), collectionRef);
        assertThat(allowed).isTrue();

        assertThat(webServer.getRequestCount()).isEqualTo(0);
    }
}
