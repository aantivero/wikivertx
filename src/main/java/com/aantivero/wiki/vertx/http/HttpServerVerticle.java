package com.aantivero.wiki.vertx.http;

import com.aantivero.wiki.vertx.database.WikiDatabaseService;
import com.github.rjeschke.txtmark.Processor;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
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
    // name of the event bus destination to post message to the database
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

        // access to the event bus, send a message to the queue database verticle
        vertx.eventBus().send(wikiDbQueue, new JsonObject(), options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject)reply.result().body();
                context.put("title", "My Wiki Home");
                context.put("pages", body.getJsonArray("pages").getList());
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
        JsonObject request = new JsonObject().put("page", requestedPage);

        DeliveryOptions options = new DeliveryOptions().addHeader("action", "get-page");
        vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
            if (reply.succeeded()) {
                JsonObject body = (JsonObject)reply.result().body();

                boolean found = body.getBoolean("found");
                String rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN);
                context.put("title", requestedPage);
                context.put("id", body.getInteger("id", -1));
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
        JsonObject request = new JsonObject()
                .put("id", context.request().getParam("id"))
                .put("title", title)
                .put("markdown", context.request().getParam("markdown"));

        DeliveryOptions options = new DeliveryOptions();

        if ("yes".equals(context.request().getParam("newPage"))) {
            options.addHeader("action", "create-page");
        } else {
            options.addHeader("action", "save-page");
        }

        vertx.eventBus().send(wikiDbQueue, request, options, reply ->{
           if (reply.succeeded()) {
               context.response().setStatusCode(303);
               context.response().putHeader("Location", "/wiki/" + title);
               context.response().end();
           } else {
               context.fail(reply.cause());
           }
        });
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
        String id = context.request().getParam("id");
        JsonObject request = new JsonObject().put("id", id);
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "delete-page");

        vertx.eventBus().send(wikiDbQueue, request, options, reply -> {
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
