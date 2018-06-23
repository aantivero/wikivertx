package com.aantivero.wiki.vertx.http;

import com.aantivero.wiki.vertx.database.WikiDatabaseService;
import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

/**
 * Verticles to deal with HTTP requests
 */
public class HttpServerVerticle extends AbstractVerticle{

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";

    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" +
            "\n" +
            "Feel-free to write in MarkDown!\n";

    private WikiDatabaseService dbService;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

        dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);

        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        router.post().handler(BodyHandler.create());
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler(this::pageDeleteHandler);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server
                .requestHandler(router::accept)
                .listen(portNumber, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP Server running on port " + portNumber);
                        startFuture.complete();
                    } else {
                        LOGGER.error("Could not start HTTP Server", ar.cause());
                        startFuture.fail(ar.cause());
                    }
                });
    }

    private void indexHandler(RoutingContext context) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "all-pages");

        // replace the call of the event bus to the database service
        dbService.fetchAllPages(reply -> {
            if (reply.succeeded()) {
                context.put("title", "My Wiki Home");
                context.put("pages", reply.result().getList());
                templateEngine.render(context, "templates", "/index.ftl", ar -> {
                    if (ar.succeeded()) {
                        context.response().putHeader("Content-Type", "text/html");
                        context.response().end(ar.result());
                    } else {
                        context.fail(ar.cause());
                    }
                });
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void pageRenderingHandler(RoutingContext context) {
        String requestedPage = context.request().getParam("page");

        dbService.fetchPage(requestedPage, reply -> {
            if (reply.succeeded()) {
                JsonObject payLoad = reply.result();

                boolean found = payLoad.getBoolean("found");
                String rawContent = payLoad.getString("rawContent", EMPTY_PAGE_MARKDOWN);
                context.put("title", requestedPage);
                context.put("id", payLoad.getInteger("id", -1));
                context.put("newPage", found ? "no" : "yes");
                context.put("rawContent", rawContent);
                context.put("content", Processor.process(rawContent));
                context.put("timestamp", new Date().toString());

                templateEngine.render(context, "templates", "/page.ftl", ar -> {
                    if (ar.succeeded()) {
                        context.response().putHeader("Content-Type", "text/html");
                        context.response().end(ar.result());
                    } else {
                        context.fail(ar.cause());
                    }
                });
            } else {
                context.fail(reply.cause());
            }
        });
    }

    private void pageUpdateHandler(RoutingContext context) {
        String title = context.request().getParam("title");
        String markdown = context.request().getParam("markdown");
        String id = context.request().getParam("id");

        Handler<AsyncResult<Void>> handler = reply -> {
            if (reply.succeeded()) {
                context.response().setStatusCode(303);
                context.response().putHeader("Location", "/wiki/" + title);
                context.response().end();
            } else {
                context.fail(reply.cause());
            }
        };

        if ("yes".equals(context.request().getParam("newPage"))) {
            dbService.createPage(title, markdown, handler);
        } else {
            dbService.savePage(Integer.valueOf(id), markdown, handler);
        }
    }

    private void pageCreateHandler(RoutingContext context) {
        String pageName = context.request().getParam("name");
        String location = "/wiki/" + pageName;

        if (pageName == null || pageName.isEmpty()) {
            location = "/";
        }

        context.response().setStatusCode(303);
        context.response().putHeader("Location", location);
        context.response().end();
    }

    private void pageDeleteHandler(RoutingContext context) {
        dbService.deletePage(Integer.valueOf(context.request().getParam("id")), reply -> {
           if (reply.succeeded()) {
               context.response().setStatusCode(303);
               context.response().putHeader("Location", "/");
               context.response().end();
           } else {
               context.fail(reply.cause());
           }
        });
    }

}
