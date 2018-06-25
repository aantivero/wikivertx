package com.aantivero.wiki.vertx;

import com.aantivero.wiki.vertx.database.WikiDatabaseVerticle;
import com.aantivero.wiki.vertx.http.AuthInitializerVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        // deploying the verticle an asynchronous operation with a Future, return the identifier
        Future<String> dbVerticleDeployment = Future.future();
        // create a verticle instance with new
        vertx.deployVerticle(new WikiDatabaseVerticle(), dbVerticleDeployment.completer());

        dbVerticleDeployment.compose(id -> {
            // deploy the auth initialize verticle
            Future<String> authInitDeplyment = Future.future();
            vertx.deployVerticle(new AuthInitializerVerticle(), authInitDeplyment);
            return authInitDeplyment;
        }).compose(id -> {
            // sequential composition run one asynchronous operation after the other
           Future<String> httpVerticleDeployment = Future.future();
           vertx.deployVerticle(
                   "com.aantivero.wiki.vertx.http.HttpServerVerticle", // class name as a string
                   new DeploymentOptions().setInstances(2), // number of instances to deploy
                   httpVerticleDeployment.completer());

           return httpVerticleDeployment; // return the next future
        }).setHandler(ar -> { // handler
            if (ar.succeeded()) {
                startFuture.complete();
            } else {
                startFuture.fail(ar.cause());
            }
        });
    }
}
