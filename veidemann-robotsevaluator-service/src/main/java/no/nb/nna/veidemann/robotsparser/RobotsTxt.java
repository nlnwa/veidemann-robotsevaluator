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

import no.nb.nna.veidemann.api.robotsevaluator.v1.IsAllowedReply;
import no.nb.nna.veidemann.api.robotsevaluator.v1.IsAllowedReply.OtherField;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class RobotsTxt {
    public final static IsAllowedReply EMPTY_ALLOWED_REPLY = IsAllowedReply.newBuilder().setIsAllowed(true).build();

    final static UserAgentParser USER_AGENT_PARSER = new UserAgentParser();

    final String sourceName;

    final List<DirectiveGroup> directives = new ArrayList<>();

    private final List<OtherField> otherFields = new ArrayList<>();

    final List<String> sitemaps = new ArrayList<>();

    final List<String> errors = new ArrayList<>();

    public RobotsTxt(String sourceName) {
        this.sourceName = sourceName;
    }

    void addOtherField(String name, String value) {
        otherFields.add(OtherField.newBuilder().setName(name).setValue(value).build());
    }

    public IsAllowedReply isAllowed(String userAgent, URL uri) {
        String ua = USER_AGENT_PARSER.parse(userAgent);

        return findMatchingDirectives(ua)
                .map(d -> IsAllowedReply.newBuilder()
                        .setIsAllowed(d.isAllowed(uri))
                        .setCrawlDelay(d.crawlDelay)
                        .setCacheDelay(d.cacheDelay)
                        .addAllOtherFields(otherFields)
                        .addAllOtherFields(d.otherFields)
                        .addAllSitemap(sitemaps)
                        .build())
                .orElse(EMPTY_ALLOWED_REPLY);
    }

    Optional<DirectiveGroup> findMatchingDirectives(String parsedUserAgent) {
        return directives.stream()
                .map(dg -> dg.matchUserAgent(parsedUserAgent))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparingInt(d -> d.matchedLength))
                .map(mdg -> mdg.directive);
    }

    @Override
    public String toString() {
        return "RobotsTxt{" + "\n  directives=" + directives + ",\n  otherFields=" + otherFields + '}';
    }

    public static class DirectiveGroup {

        final List<String> userAgents = new ArrayList<>();

        private final List<Directive> directives = new ArrayList<>();

        private final List<OtherField> otherFields = new ArrayList<>();

        float crawlDelay = -1;

        float cacheDelay = -1;

        void addDirective(Directive directive) {
            directive.group = this;
            directives.add(directive);
        }

        void addOtherField(String name, String value) {
            otherFields.add(OtherField.newBuilder().setName(name).setValue(value).build());
        }

        /**
         * Return the number of characters matching this directives best user agent match.
         *
         * @param parsedUserAgent the User Agent to check access for
         * @return an Optional with the number of characters matching, 0 for wildcard match ('*'), empty Optional for no
         * match
         */
        Optional<MatchedDirectiveGroup> matchUserAgent(final String parsedUserAgent) {
            return userAgents.stream()
                    .map(ua -> compareUA(ua, parsedUserAgent))
                    .filter(d -> d.matchedLength >= 0)
                    .max(Comparator.comparingInt(l -> l.matchedLength));
        }

        /**
         * Return the number of characters matching.
         *
         * @param ua              a User Agent from a robots.txt file
         * @param parsedUserAgent the User Agent to check access for
         * @return the number of characters matching, -1 for no match, 0 for wildcard match ('*')
         */
        MatchedDirectiveGroup compareUA(final String ua, final String parsedUserAgent) {
            if ("*".equals(ua)) {
                return new MatchedDirectiveGroup(0, this);
            }
            if (parsedUserAgent.length() < ua.length()) {
                return new MatchedDirectiveGroup(-1, this);
            }

            int i;
            for (i = 0; i < ua.length(); i++) {
                if (ua.charAt(i) != parsedUserAgent.charAt(i)) {
                    i--;
                    break;
                }
            }

            return new MatchedDirectiveGroup(i, this);
        }

        boolean isAllowed(URL uri) {
            final String path = uri.getPath();
            Optional<MatchedDirective> match = directives.stream()
                    .map(d -> d.comparePath(path))
                    .filter(md -> md.matchedLength >= 0)
                    .max(Comparator.comparingInt(d -> d.matchedLength));
            if (match.isPresent()) {
                return match.get().directive.type == DirectiveType.ALLOW;
            } else {
                return true;
            }
        }

        @Override
        public String toString() {
            return "\n    DirectiveGroup{" + "userAgents=" + userAgents + ", directives=" + directives
                    + ", otherFields=" + otherFields + ", crawlDelay=" + crawlDelay
                    + ", cacheDelay=" + cacheDelay + '}';
        }

        static class MatchedDirective {

            final int matchedLength;

            final Directive directive;

            public MatchedDirective(int matchedLength, Directive directive) {
                this.matchedLength = matchedLength;
                this.directive = directive;
            }

        }

        static class MatchedDirectiveGroup {

            final int matchedLength;

            final DirectiveGroup directive;

            public MatchedDirectiveGroup(int matchedLength, DirectiveGroup directive) {
                this.matchedLength = matchedLength;
                this.directive = directive;
            }

        }
    }

    enum DirectiveType {
        ALLOW,
        DISALLOW
    }

    public static class Directive {

        final DirectiveType type;

        final String path;

        final Pattern pattern;

        DirectiveGroup group;

        public Directive(DirectiveType type, String path) {
            this.type = type;
            // remove trailing wildcard
            if (path.endsWith("*")) {
                path = path.substring(0, path.length() - 1);
            }
            this.path = path;
            if (path.endsWith("$") || path.contains("*")) {
                String patternString = "^" + path.replaceAll("\\*", ".*");
                if (!path.endsWith("$")) {
                    patternString += ".*$";
                }
                pattern = Pattern.compile(patternString);
            } else {
                pattern = null;
            }
        }

        DirectiveGroup.MatchedDirective comparePath(final String pathToCompare) {
            if ("/".equals(path)) {
                return new DirectiveGroup.MatchedDirective(1, this);
            }

            if (pattern != null) {
                Matcher m = pattern.matcher(pathToCompare);
                if (m.matches()) {
                    return new DirectiveGroup.MatchedDirective(path.length(), this);
                }
            } else if (pathToCompare.startsWith(path)) {
                return new DirectiveGroup.MatchedDirective(path.length(), this);
            }

            return new DirectiveGroup.MatchedDirective(-1, this);
        }

        @Override
        public String toString() {
            return "Directive{" + type + ": " + path + '}';
        }

    }

//    public static class NonGroupField {
//
//        private final String name;
//
//        private final String value;
//
//        public NonGroupField(String name, String value) {
//            this.name = name;
//            this.value = value;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public String getValue() {
//            return value;
//        }
//
//        @Override
//        public String toString() {
//            final StringBuffer sb = new StringBuffer("NonGroupField{");
//            sb.append("name='").append(name).append('\'');
//            sb.append(", value='").append(value).append('\'');
//            sb.append('}');
//            return sb.toString();
//        }
//    }
}
