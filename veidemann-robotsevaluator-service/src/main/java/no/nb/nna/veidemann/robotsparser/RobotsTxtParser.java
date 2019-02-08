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

import no.nb.nna.veidemann.robots.RobotstxtLexer;
import no.nb.nna.veidemann.robots.RobotstxtParser;
import no.nb.nna.veidemann.robots.RobotstxtParserBaseListener;
import no.nb.nna.veidemann.robotsparser.RobotsTxt.Directive;
import no.nb.nna.veidemann.robotsparser.RobotsTxt.DirectiveGroup;
import no.nb.nna.veidemann.robotsparser.RobotsTxt.DirectiveType;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;

/**
 *
 */
public class RobotsTxtParser {
    private static final Logger LOG = LoggerFactory.getLogger(RobotsTxtParser.class);

    public RobotsTxtParser() {
    }

    public RobotsTxt parse(String robotsContent, String sourceName) throws IOException {
        return parse(CharStreams.fromString(robotsContent), sourceName);
    }

    public RobotsTxt parse(Reader robotsReader, String sourceName) throws IOException {
        return parse(CharStreams.fromReader(robotsReader), sourceName);
    }

    public RobotsTxt parse(CharStream robotsStream, String sourceName) throws IOException {
        RobotsTxt robotsTxt = new RobotsTxt(sourceName);
        ErrorListener errorListener = new ErrorListener(robotsTxt);

        TokenSource tokenSource = new RobotstxtLexer(robotsStream);
        ((RobotstxtLexer) tokenSource).removeErrorListeners();
        ((RobotstxtLexer) tokenSource).addErrorListener(errorListener);
        TokenStream tokens = new CommonTokenStream(tokenSource);

        RobotstxtParser parser = new RobotstxtParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        ParseTree p = parser.robotstxt();
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(new RobotsListener(robotsTxt), p);

        if (!robotsTxt.errors.isEmpty()) {
            LOG.info("Errors found in {}:\n    {}", sourceName, String.join("\n    ", robotsTxt.errors));
        }
        return robotsTxt;
    }

    private class ErrorListener extends BaseErrorListener {
        final RobotsTxt robotsTxt;

        public ErrorListener(RobotsTxt robotsTxt) {
            this.robotsTxt = robotsTxt;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            String err = String.format("%d:%d: %s", line, charPositionInLine, msg);
            robotsTxt.errors.add(err);
        }
    }

    private class RobotsListener extends RobotstxtParserBaseListener {

        DirectiveGroup currentDirective = null;

        final RobotsTxt robotsTxt;

        public RobotsListener(RobotsTxt robotsTxt) {
            this.robotsTxt = robotsTxt;
        }

        @Override
        public void enterStartgroupline(RobotstxtParser.StartgrouplineContext ctx) {
            if (ctx.agentvalue() != null) {
                currentDirective.userAgents.add(ctx.agentvalue().getText().toLowerCase());
            }
        }

        @Override
        public void enterEntry(RobotstxtParser.EntryContext ctx) {
            if (!ctx.startgroupline().isEmpty()) {
                currentDirective = new DirectiveGroup();
            }
        }

        @Override
        public void enterUrlnongroupfield(RobotstxtParser.UrlnongroupfieldContext ctx) {
            if (ctx.urlnongrouptype() != null) {
                String name = ctx.urlnongrouptype().getText().toLowerCase();
                if ("sitemap".equals(name)) {
                    robotsTxt.sitemaps.add(ctx.urlvalue().getText());
                } else {
                    robotsTxt.addOtherField(name, ctx.urlvalue().getText());
                }
            }
        }

        @Override
        public void enterOthernongroupfield(RobotstxtParser.OthernongroupfieldContext ctx) {
            if (ctx.othernongrouptype() != null && !(ctx.othernongrouptype().getText().isEmpty() && ctx.textvalue().getText().isEmpty())) {
                robotsTxt.addOtherField(ctx.othernongrouptype().getText().toLowerCase(), ctx.textvalue().getText());
            }
        }

        @Override
        public void enterOthermemberfield(RobotstxtParser.OthermemberfieldContext ctx) {
            if (ctx.othermembertype() != null && ctx.textvalue() != null) {
                String fieldName = ctx.othermembertype().getText().toLowerCase();
                try {
                    switch (fieldName) {
                        case "cache-delay":
                            currentDirective.cacheDelay = Float.parseFloat(ctx.textvalue().getText());
                            break;
                        case "crawl-delay":
                            currentDirective.crawlDelay = Float.parseFloat(ctx.textvalue().getText());
                            break;
                        default:
                            currentDirective.addOtherField(fieldName, ctx.textvalue().getText());
                            break;
                    }
                } catch (NumberFormatException e) {

                }
            }
        }

        @Override
        public void enterPathmemberfield(RobotstxtParser.PathmemberfieldContext ctx) {
            if (ctx.pathmembertype() != null && ctx.pathvalue() != null) {
                if (ctx.pathmembertype().ALLOW() != null) {
                    currentDirective.addDirective(new Directive(DirectiveType.ALLOW, ctx.pathvalue().getText()));
                }
                if (ctx.pathmembertype().DISALLOW() != null) {
                    currentDirective.addDirective(new Directive(DirectiveType.DISALLOW, ctx.pathvalue().getText()));
                }
            }
        }

        @Override
        public void exitEntry(RobotstxtParser.EntryContext ctx) {
            if (currentDirective != null) {
                robotsTxt.directives.add(currentDirective);
                currentDirective = null;
            }
        }

    }

}
