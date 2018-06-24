package com.aantivero.wiki.vertx.http;

import com.aantivero.wiki.vertx.database.WikiDatabaseVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class ApiTest {

	private Vertx vertx;
	private WebClient webClient;

	@Before
	public void prepare(TestContext context) {
		vertx = Vertx.vertx();

		JsonObject conf = new JsonObject()
			.put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_URL, "jdbc:hsqldb:mem:testdb;shutdown=true")
			.put(WikiDatabaseVerticle.CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE, 4);

		vertx.deployVerticle(new WikiDatabaseVerticle(),
			new DeploymentOptions().setConfig(conf), context.asyncAssertSuccess());

		vertx.deployVerticle(new HttpServerVerticle(), context.asyncAssertSuccess());

		webClient = WebClient.create(vertx,
			new WebClientOptions()
				.setDefaultHost("localhost")
				.setDefaultPort(8080));
	}

	@After
	public void finish(TestContext context) {
		vertx.close(context.asyncAssertSuccess());
	}

	@Test
	public void play_with_api(TestContext context) {
		Async async = context.async();

		JsonObject page = new JsonObject()
			.put("name", "Sample")
			.put("markdown", "# A Page");

		Future<JsonObject> postRequest = Future.future();

		webClient.post("/api/pages")
			.as(BodyCodec.jsonObject())
			.sendJsonObject(page, ar -> {
				if (ar.succeeded()) {
					HttpResponse<JsonObject> postResponse = ar.result();
					postRequest.complete(postResponse.body());
				} else {
					context.fail(ar.cause());
				}
			});

		postRequest.compose(response -> {
			context.assertTrue(response.getBoolean("success"));
			async.complete();
		}, Future.failedFuture("Oh?"));
	}
}
