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

package no.nb.nna.veidemann.robotsparser;

import org.antlr.v4.runtime.CharStreams;
import org.junit.Test;
import org.netpreserve.commons.uri.Uri;
import org.netpreserve.commons.uri.UriConfigs;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
public class RobotsTxtParserTest {
    final static String BOT1 = "Googlebot/2.1 (+http://www.google.com/bot.html)";
    final static String BOT2 = "nlnbot/1.0 (+http://www.google.com/bot.html)";

    @Test
    public void checkIsAllowed() throws IOException {
        RobotsTxtParser parser = new RobotsTxtParser();
        RobotsTxt robots = parser.parse(CharStreams.fromFileName("src/test/resources/examples/robotstxt/robots1.txt"), "robots1.txt");

        Uri denied = UriConfigs.WHATWG.buildUri("http://example.com/denied");
        Uri allowed = UriConfigs.WHATWG.buildUri("http://example.com/allowed");

        assertThat(robots.isAllowed(BOT1, denied).getIsAllowed()).isFalse();
        assertThat(robots.isAllowed(BOT1, allowed).getIsAllowed()).isTrue();
    }

    @Test
    public void checkGrammar() throws IOException {
        RobotsTxtParser parser = new RobotsTxtParser();
        parser.parse(CharStreams.fromFileName("src/test/resources/examples/robotstxt/robots1.txt"), "robots1.txt");
        parser.parse(CharStreams.fromFileName("src/test/resources/examples/robotstxt/robots2.txt"), "robots2.txt");
        parser.parse(CharStreams.fromFileName("src/test/resources/examples/robotstxt/robots3.txt"), "robots3.txt");

        RobotsTxt robots = parser.parse(CharStreams.fromFileName("src/test/resources/examples/robotstxt/robots4.txt"), "robots4.txt");
        Uri denied = UriConfigs.WHATWG.buildUri("http://example.com/test6");
        Uri allowed = UriConfigs.WHATWG.buildUri("http://example.com/test9");

        assertThat(robots.isAllowed(BOT2, denied).getIsAllowed()).isFalse();
        assertThat(robots.isAllowed(BOT2, denied).getCrawlDelay()).isEqualTo(7.0f);
        assertThat(robots.isAllowed(BOT1, denied).getIsAllowed()).isTrue();
        assertThat(robots.isAllowed(BOT1, denied).getCrawlDelay()).isEqualTo(4.0f);
        assertThat(robots.isAllowed(BOT1, allowed).getIsAllowed()).isTrue();
    }

}