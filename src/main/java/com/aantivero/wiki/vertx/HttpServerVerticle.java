package com.aantivero.wiki.vertx;

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

/**
 * Verticles to deal with HTTP requests
 */
public class HttpServerVerticle extends AbstractVerticle{

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerVerticle.class);

    public static final String CONFIG_HTTP_SERVER_PORT = "http.server.port";
    // name of the event bus destination to post message to the database
    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private String wikiDbQueue = "wikidb.queue";

    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

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


}
