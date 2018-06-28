package com.aantivero.wiki.vertx.http;

import com.aantivero.wiki.vertx.database.WikiDatabaseService;
import com.github.rjeschke.txtmark.Processor;

import io.reactivex.Completable;
import io.reactivex.Single;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.KeyStoreOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.jwt.JWTOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;

// rx-java-imports
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.http.HttpServer;
import io.vertx.reactivex.ext.auth.User;
import io.vertx.reactivex.ext.auth.jdbc.JDBCAuth;
import io.vertx.reactivex.ext.auth.jwt.JWTAuth;
import io.vertx.reactivex.ext.jdbc.JDBCClient;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.ext.web.handler.*;
import io.vertx.reactivex.ext.web.sstore.LocalSessionStore;
import io.vertx.reactivex.ext.web.templ.FreeMarkerTemplateEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.aantivero.wiki.vertx.DatabaseConstants.*;

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

    private WebClient webClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue");

        //dbService = WikiDatabaseService.createProxy(vertx, wikiDbQueue);
		dbService = com.aantivero.wiki.vertx.database.WikiDatabaseService.createProxy(vertx.getDelegate(), wikiDbQueue);

        // init the web client
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setSsl(true)
                .setUserAgent("antivero-wiki-vertx"));


        //ssl support
        HttpServer server = vertx.createHttpServer(new HttpServerOptions()
	        .setSsl(true)
	        .setKeyStoreOptions(new JksOptions()
		        .setPath("server-keystore.jks")
		        .setPassword("secret")
	        )
        );

        //jdbc-auth
	    JDBCClient dbClient = JDBCClient.createShared(vertx, new JsonObject()
		    .put("url", config().getString(CONFIG_WIKIDB_JDBC_URL, DEFAULT_WIKI_JDBC_URL))
		    .put("driver_class", config().getString(CONFIG_WIKIDB_JDBC_DRIVER_CLASS, DEFAULT_WIKI_JDBC_URL))
		    .put("max_pool_size", config().getInteger(CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, DEFAULT_JDBC_MAX_POOL_SIZE))
	    );

	    JDBCAuth auth = JDBCAuth.create(vertx, dbClient);

        Router router = Router.router(vertx);

        // Auth Routes
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)));
        router.route().handler(UserSessionHandler.create(auth));

	    AuthHandler authHandler = RedirectAuthHandler.create(auth, "/login");
	    router.route("/").handler(authHandler);
	    router.route("/wiki/*").handler(authHandler);
	    router.route("/action/*").handler(authHandler);

        router.get("/").handler(this::indexHandler);
	    router.get("/wiki/:page").handler(this::pageRenderingHandler);
	    router.post("/action/save").handler(this::pageUpdateHandler);
	    router.post("/action/create").handler(this::pageCreateHandler);
	    router.get("/action/backup").handler(this::backupHandler);
	    router.post("/action/delete").handler(this::pageDeleteHandler);
        // router.post().handler(BodyHandler.create());
	    // end auth routes

	    // auth login
	    router.get("/login").handler(this::loginHandler);
	    router.post("/login-auth").handler(FormLoginHandler.create(auth));

	    router.get("/logout").handler(context -> {
	    	context.clearUser();
	    	context.response()
			    .setStatusCode(302)
			    .putHeader("Location", "/")
			    .end();
	    });
	    //end auth login

	    // jwt token handler on api routes
        Router apiRouter = Router.router(vertx);

	    JWTAuth jwtAuth = JWTAuth.create(vertx,
		    new JWTAuthOptions()
			    .setKeyStore(
			    	new KeyStoreOptions()
					    .setPath("keystore.jceks")
					    .setType("jceks")
					    .setPassword("secret")));
	    apiRouter.route().handler(JWTAuthHandler.create(jwtAuth, "/api/token"));
	    // generate jwt tokens
	    apiRouter.get("/token").handler(context -> {
	    	JsonObject creds = new JsonObject()
			    .put("username", context.request().getHeader("login"))
			    .put("password", context.request().getHeader("password"));

	    	auth.rxAuthenticate(creds).flatMap(user -> {
				Single<Boolean> create = user.rxIsAuthorized("create");
				Single<Boolean> delete = user.rxIsAuthorized("delete");
				Single<Boolean> update = user.rxIsAuthorized("update");

				return Single.zip(create, delete, update, (canCreate, canDelete, canUpdate) -> {
					return jwtAuth.generateToken(
							new JsonObject()
								.put("username", context.request().getHeader("login"))
								.put("canCreate", canCreate)
								.put("canDelete", canDelete)
								.put("canUpdate", canUpdate),
							new JWTOptions()
								.setSubject("Antivero Wiki API")
								.setIssuer("Vert.x")
					);
				});
			}).subscribe(token -> {
				context.response().putHeader("Content-Type", "text/plain").end(token);
			}, t -> context.fail(401));
	    });

        apiRouter.get("/pages").handler(this::apiRoot);
        apiRouter.get("/pages/:id").handler(this::apiGetPage);
        apiRouter.post().handler(BodyHandler.create());
        apiRouter.post("/pages").handler(this::apiCreate);
        apiRouter.put().handler(BodyHandler.create());
        apiRouter.put("/pages/:id").handler(this::apiUpdatePage);
        apiRouter.delete("/pages/:id").handler(this::apiDeletePage);
        router.mountSubRouter("/api", apiRouter);

        int portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080);
        server
			.requestHandler(router::accept)
			.rxListen(portNumber)
			.subscribe(s -> {
				LOGGER.info("HTTP server running on port " + portNumber);
				startFuture.complete();
			}, t -> {
				LOGGER.error("Could not start HTTP server", t);
			});
    }

    private Completable checkAuthorised(RoutingContext context, String authority) {
    	return context.user().rxIsAuthorised(authority)
				.flatMapCompletable(authorized -> authorized ? Completable.complete() : Completable.error(new UnauthorizedThrowable(authority)));
	}

	private void loginHandler(RoutingContext context) {
    	context.put("title", "Login");
    	templateEngine.render(context, "templates", "/login.ftl", ar -> {
    		if (ar.succeeded()) {
    			context.response().putHeader("Content-Type", "text/html");
    			context.response().end(ar.result());
		    } else {
    			context.fail(ar.cause());
		    }
	    });
	}

	private void apiDeletePage(RoutingContext context) {
    	if (context.user().principal().getBoolean("canDelete", false)) {
		    int id = Integer.valueOf(context.request().getParam("id"));
		    dbService.rxDeletePage(id).subscribe(() -> apiResponse(context, 200, null, null), t -> apiFailure(context, t));
	    } else {
    		context.fail(401);
	    }
	}

	private void apiUpdatePage(RoutingContext context) {
    	if (context.user().principal().getBoolean("canUpdate", false)) {
		    JsonObject page = context.getBodyAsJson();
		    int id = Integer.valueOf(context.request().getParam("id"));

		    if (!validateJsonPageDocument(context, page, "markdown")) {
			    return;
		    }

		    dbService.rxSavePage(id, page.getString("markdown"))
					.subscribe(() -> apiResponse(context, 200, null, null), t -> apiFailue(context, t));
	    } else {
    		context.fail(401);
	    }
	}

	private void handleSimpleDbReply(RoutingContext context, AsyncResult<Void> reply) {
		context.response().putHeader("Content-Type", "application/json");
    	if (reply.succeeded()) {
    		context.response().setStatusCode(200);
    		context.response().end(
    			new JsonObject()
				    .put("success", true)
				    .encode()
		    );
	    } else {
    		context.response().setStatusCode(500);
    		context.response().end(
    			new JsonObject()
				    .put("success", false)
				    .put("error", reply.cause().getMessage())
				    .encode()
		    );

	    }
	}

	private void apiCreate(RoutingContext context) {
    	if (context.user().principal().getBoolean("canCreate",false)) {
		    JsonObject page = context.getBodyAsJson();
		    if (!validateJsonPageDocument(context, page, "name", "markdown")) {
			    return;
		    }
		    dbService.rxCreatePage(page.getString("name"), page.getString("markdown"))
					.subscribe(() -> apiResponse(context, 201, null, null), t -> apiFailure(context, t));
	    } else {
    		context.fail(401);
	    }
    }

    private boolean validateJsonPageDocument(RoutingContext context, JsonObject page, String... expectedKeys) {
        if (!Arrays.stream(expectedKeys).allMatch(page::containsKey)) {
            LOGGER.error("Bad page creation JSON payload: " + page.encodePrettily() +
                " from: " + context.request().remoteAddress());

            context.response().setStatusCode(400);
            context.response().putHeader("Content-Type", "application/json");
            context.response().end(new JsonObject()
                .put("success", false)
                .put("error", "Bad request payload").encode());
            return false;
        }
        return true;
    }

    private void apiGetPage(RoutingContext context) {
    	int id = Integer.valueOf(context.request().getParam("id"));
    	dbService.rxFetchPageById(id)
				.subscribe(dbObject -> {
					if (dbObject.getBoolean("found")) {
						JsonObject payload = new JsonObject()
								.put("name", dbObject.getString("name"))
								.put("id", dbObject.getInteger("id"))
								.put("markdown", dbObject.getString("content"))
								.put("html", Processor.process(dbObject.getString("content")));
						apiResponse(context, 200, "page", payload);
					} else {
						apiFailure(context, 404, "There is no page with ID " + id);
					}
				}, t -> apiFailure(context, t));
	}

	//TODO continue refactoring
	private void apiRoot(RoutingContext context) {
        dbService.fetchAllPagesData(reply -> {
            JsonObject response = new JsonObject();
            if (reply.succeeded()) {
                List<JsonObject> pages = reply.result()
                        .stream()
                        .map(obj -> new JsonObject()
                                .put("id", obj.getInteger("ID"))
                                .put("name", obj.getString("NAME")))
                        .collect(Collectors.toList());

                response
                        .put("success", true)
                        .put("pages", pages);
                context.response().setStatusCode(200);
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(response.encode());
            } else {
                response.put("success", false);
                response.put("error", reply.cause().getMessage());
                context.response().setStatusCode(500);
                context.response().putHeader("Content-Type", "application/json");
                context.response().end(response.encode());
            }
        });
    }

    private void indexHandler(RoutingContext context) {
	    context.user().isAuthorized("create", res -> {
	    	boolean canCreatePage = res.succeeded() && res.result();
		    dbService.fetchAllPages(reply -> {
			    if (reply.succeeded()) {
				    context.put("title", "My Wiki Home");
				    context.put("pages", reply.result().getList());
				    context.put("canCreatePage", canCreatePage);
				    context.put("username", context.user().principal().getString("username"));
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
	    });
    }

    private void pageRenderingHandler(RoutingContext context) {
    	context.user().isAuthorized("update", updateResponse -> {
    		boolean canSavePage = updateResponse.succeeded() && updateResponse.result();
    		context.user().isAuthorized("delete", deleteResponse -> {
    			boolean canDeletePage = deleteResponse.succeeded() && deleteResponse.result();

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
					    context.put("username", context.user().principal().getString("username"));
					    context.put("canSavePage", canSavePage);
					    context.put("canDeletePage", canDeletePage);

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
		    });
	    });
    }

    private void pageUpdateHandler(RoutingContext context) {
    	boolean pageCreation = "yes".equalsIgnoreCase(context.request().getParam("newPage"));
    	context.user().isAuthorized(pageCreation ? "create" : "update", res -> {
    		if (res.succeeded() && res.result()) {
			    String title = context.request().getParam("title");
			    Handler<AsyncResult<Void>> handler = reply -> {
				    if (reply.succeeded()) {
					    context.response().setStatusCode(303);
					    context.response().putHeader("Location", "/wiki/" + title);
					    context.response().end();
				    } else {
					    context.fail(reply.cause());
				    }
			    };

			    String markdown = context.request().getParam("markdown");
			    if (pageCreation) {
				    dbService.createPage(title, markdown, handler);
			    } else {
				    dbService.savePage(Integer.valueOf(context.request().getParam("id")), markdown, handler);
			    }
		    } else {
    			context.response().setStatusCode(403).end();
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
    	context.user().isAuthorized("delete", res -> {
    		if (res.succeeded() && res.result()) {
			    dbService.deletePage(Integer.valueOf(context.request().getParam("id")), reply -> {
				    if (reply.succeeded()) {
					    context.response().setStatusCode(303);
					    context.response().putHeader("Location", "/");
					    context.response().end();
				    } else {
					    context.fail(reply.cause());
				    }
			    });
		    } else {
    			context.response().setStatusCode(403).end();
		    }
	    });
    }

    private void backupHandler(RoutingContext context) {
    	context.user().isAuthorized("role:writer", res -> {
    		if (res.succeeded() && res.result()) {
			    dbService.fetchAllPagesData(reply -> {
				    if (reply.succeeded()) {

					    JsonArray filesObject = new JsonArray();
					    JsonObject payload = new JsonObject()
						    .put("files", filesObject)
						    .put("language", "plaintext")
						    .put("title", "antivero-wiki-vertx-backup")
						    .put("public", "true");

					    reply
						    .result()
						    .forEach(page -> {
							    JsonObject fileObject = new JsonObject();
							    fileObject.put("name", page.getString("NAME"));
							    fileObject.put("content", page.getString("CONTENT"));
							    filesObject.add(fileObject);
						    });

					    webClient.post(443, "snippets.glot.io", "/snippets")
						    .putHeader("Content-Type", "application/json")
						    .as(BodyCodec.jsonObject())
						    .sendJsonObject(payload, ar -> {
							    if (ar.succeeded()) {
								    HttpResponse<JsonObject> response = ar.result();
								    if (response.statusCode() == 200) {
									    String url = "https://glot.io/snippets/" + response.body().getString("id");
									    context.put("backup_gist_url", url);
									    indexHandler(context);
								    } else {
									    StringBuilder message = new StringBuilder();
									    message.append("Could not backup the wiki");
									    message.append(response.statusMessage());

									    JsonObject body = response.body();
									    if (body != null) {
										    message.append(System.getProperty("line.separator"))
											    .append(body.encodePrettily());
									    }
									    LOGGER.error(message.toString());
									    context.fail(502);
								    }
							    } else {
								    Throwable err = ar.cause();
								    LOGGER.error("HTTP Client error", err);
								    context.fail(err);
							    }
						    });
				    } else {
					    context.fail(reply.cause());
				    }
			    });
		    } else {
    			context.response().setStatusCode(403).end();
		    }
	    });

    }

    private static final class UnauthorizedThrowable extends Throwable {
    	UnauthorizedThrowable(String message) {
    		super(message, null, false, false);
		}
	}
}
