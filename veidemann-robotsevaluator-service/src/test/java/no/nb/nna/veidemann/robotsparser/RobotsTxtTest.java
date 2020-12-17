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

import org.junit.Test;

import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *
 */
public class RobotsTxtTest {

    @Test
    public void testDirectiveGroup_matchUserAgent() {
        RobotsTxt.DirectiveGroup directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.userAgents.add("googlebot-news");
        directiveGroup.userAgents.add("googlebot");

        assertThat(directiveGroup.matchUserAgent("googlebot-news").get().matchedLength).isEqualTo(14);
        assertThat(directiveGroup.matchUserAgent("googlebot").get().matchedLength).isEqualTo(9);
        assertThat(directiveGroup.matchUserAgent("googlebot-images").get().matchedLength).isEqualTo(9);
        assertThat(directiveGroup.matchUserAgent("foo").isPresent()).isFalse();

        directiveGroup.userAgents.add("*");
        assertThat(directiveGroup.matchUserAgent("foo").get().matchedLength).isEqualTo(0);
    }

    @Test
    public void testDirectiveGroup_isAllowed() throws MalformedURLException {
        RobotsTxt.DirectiveGroup directiveGroup;

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/p"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/"));
        assertThat(directiveGroup.isAllowed(new URL("http://example.com/page"))).isTrue();

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/folder/"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/folder"));
        assertThat(directiveGroup.isAllowed(new URL("http://example.com/folder/page"))).isTrue();

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/page"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/*.htm"));
        assertThat(directiveGroup.isAllowed(new URL("http://example.com/page.htm"))).isFalse();

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/$"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/"));
        assertThat(directiveGroup.isAllowed(new URL("http://example.com/"))).isTrue();

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/$"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/"));
        assertThat(directiveGroup.isAllowed(new URL("http://example.com/page.htm"))).isFalse();
    }

    @Test
    public void testDirective_comparePath() {
        RobotsTxt.Directive directive;

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/");
        assertThat(directive.comparePath("/").matchedLength).isEqualTo(1);
        assertThat(directive.comparePath("/foo").matchedLength).isEqualTo(1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/*");
        assertThat(directive.comparePath("/").matchedLength).isEqualTo(1);
        assertThat(directive.comparePath("/foo").matchedLength).isEqualTo(1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/fish");
        assertThat(directive.comparePath("/fish").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fish.html").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fish/salmon.html").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fishheads").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fishheads/yummy.html").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fish.php?id=anything").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/Fish.asp").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/catfish").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/?id=fish").matchedLength).isEqualTo(-1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/fish*");
        assertThat(directive.comparePath("/fish").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fish.html").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fish/salmon.html").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fishheads").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fishheads/yummy.html").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/fish.php?id=anything").matchedLength).isEqualTo(5);
        assertThat(directive.comparePath("/Fish.asp").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/catfish").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/?id=fish").matchedLength).isEqualTo(-1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/fish/");
        assertThat(directive.comparePath("/fish/").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/fish/?id=anything").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/fish/salmon.html").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/fish").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/fish.html").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/Fish/Salmon.asp").matchedLength).isEqualTo(-1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/*.php");
        assertThat(directive.comparePath("/filename.php").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/folder/filename.php").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/folder/filename.php?parameters").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/folder/any.php.file.html").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/filename.php/").matchedLength).isEqualTo(6);
        assertThat(directive.comparePath("/").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/windows.PHP").matchedLength).isEqualTo(-1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/*.php$");
        assertThat(directive.comparePath("/filename.php").matchedLength).isEqualTo(7);
        assertThat(directive.comparePath("/folder/filename.php").matchedLength).isEqualTo(7);
        assertThat(directive.comparePath("/filename.php?parameters").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/filename.php/").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/filename.php5").matchedLength).isEqualTo(-1);
        assertThat(directive.comparePath("/windows.PHP").matchedLength).isEqualTo(-1);

        directive = new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/fish*.php");
        assertThat(directive.comparePath("/fish.php").matchedLength).isEqualTo(10);
        assertThat(directive.comparePath("/fishheads/catfish.php?parameters").matchedLength).isEqualTo(10);
        assertThat(directive.comparePath("/Fish.PHP").matchedLength).isEqualTo(-1);
    }

    /**
     * Test of isAllowed method, of class RobotsTxt.
     */
    @Test
    public void testIsAllowed() throws MalformedURLException {
        RobotsTxt robots = new RobotsTxt("test");

        // Test that empty robots.txt allows all
        assertThat(robots.isAllowed("googlebot-news", new URL("http://example.com/page")).getIsAllowed()).isTrue();

        RobotsTxt.DirectiveGroup directiveGroup;
        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.userAgents.add("googlebot-news");
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/p"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/"));
        robots.directives.add(directiveGroup);
        assertThat(robots.isAllowed("googlebot-news", new URL("http://example.com/page")).getIsAllowed()).isTrue();

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.userAgents.add("googlebot");
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/folder/"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/folder"));
        robots.directives.add(directiveGroup);
        assertThat(robots.isAllowed("Googlebot/2.1 (+http://www.google.com/bot.html)",
                new URL("http://example.com/folder/page")).getIsAllowed()).isTrue();
        assertThat(robots.isAllowed("googlebot-news", new URL("http://example.com/folder/page")).getIsAllowed())
                .isFalse();
        assertThat(robots.isAllowed("googlebo", new URL("http://example.com/folder/page")).getIsAllowed())
                .isTrue();
        assertThat(robots.isAllowed("foo", new URL("http://example.com/folder/page")).getIsAllowed())
                .isTrue();

        directiveGroup = new RobotsTxt.DirectiveGroup();
        directiveGroup.userAgents.add("*");
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.ALLOW, "/page"));
        directiveGroup.addDirective(new RobotsTxt.Directive(RobotsTxt.DirectiveType.DISALLOW, "/*.htm"));
        robots.directives.add(directiveGroup);
        assertThat(robots.isAllowed("foo", new URL("http://example.com/folder/page")).getIsAllowed())
                .isTrue();
    }

}