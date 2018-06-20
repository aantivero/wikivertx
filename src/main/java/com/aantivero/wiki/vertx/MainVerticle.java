package com.aantivero.wiki.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.templ.FreeMarkerTemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

    private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
    private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
    private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
    private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
    private static final String SQL_ALL_PAGES = "select Name from Pages";
    private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";

    private JDBCClient dbClient;
    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);
    private final FreeMarkerTemplateEngine templateEngine = FreeMarkerTemplateEngine.create();

    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();
        dbClient = JDBCClient.createNonShared(vertx, new JsonObject()
            .put("url", "jdbc:hsqldb:file:db/wiki")
            .put("driver_class", "org.hsqldb.jdbcDriver")
            .put("max_pool_size", 30));

        dbClient.getConnection(ar -> {
            if (ar.failed()) {
                LOGGER.error("Could not open a database connection", ar.cause());
                future.fail(ar.cause());
            } else {
                SQLConnection connection = ar.result();
                connection.execute(SQL_CREATE_PAGES_TABLE, create -> {
                    connection.close();
                    if(create.failed()) {
                        LOGGER.error("Database preparation error", create.cause());
                        future.fail(create.cause());
                    } else {
                        future.complete();
                    }
                });
            }
        });
        return future;
    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/wiki/:page").handler(this::pageRenderingHandler);
        router.post().handler(BodyHandler.create());
        router.post("/save").handler(this::pageUpdateHandler);
        router.post("/create").handler(this::pageCreateHandler);
        router.post("/delete").handler(this::pageDeletionHandler);

        server
                .requestHandler(router::accept)
                .listen(8080, ar -> {
                    if (ar.succeeded()) {
                        LOGGER.info("HTTP Server running on port 8080");
                        future.complete();
                    } else {
                        LOGGER.error("Could not start a HTTP Server", ar.cause());
                        future.fail(ar.cause());
                    }
                });
        return future;
    }

    private void indexHandler(RoutingContext routingContext) {
        dbClient.getConnection(car -> {
            if(car.succeeded()) {
                SQLConnection connection = car.result();
                connection.query(SQL_ALL_PAGES, res -> {
                    connection.close();

                    if(res.succeeded()){
                        List<String> pages = res.result()
                                .getResults()
                                .stream()
                                .map(json -> json.getString(0))
                                .sorted()
                                .collect(Collectors.toList());

                        routingContext.put("title", "Wiki Home");
                        routingContext.put("pages", pages);
                        templateEngine.render(routingContext, "templates", "/index.ftl", ar -> {
                            if(ar.succeeded()) {
                                routingContext.response().putHeader("Content-Type", "text/html");
                                routingContext.response().end(ar.result());
                            } else {
                                routingContext.fail(ar.cause());
                            }
                        });
                    } else {
                        routingContext.fail(res.cause());
                    }
                });
            } else {
                routingContext.fail(car.cause());
            }
        });
    }

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        //el startHttpServer no se llama si el prepareDatabase no funciono
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(startFuture);
    }

    public void anotherStart(Future<Void> startFuture) throws Exception {
        Future<Void> steps = prepareDatabase().compose(v -> startHttpServer());
        steps.setHandler(ar -> {
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}
