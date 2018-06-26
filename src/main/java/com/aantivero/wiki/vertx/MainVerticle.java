package com.aantivero.wiki.vertx;

import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        // rx-deploy-verticle
        Single<String> dbVerticleDeployment = vertx.rxDeployVerticle(
                "com.aantivero.wiki.vertx.database.WikiDatabaseVerticle");

        // rx secuential composition
        dbVerticleDeployment.flatMap(id -> {
            Single<String> httpVerticleDeployment = vertx.rxDeployVerticle("com.aantivero.wiki.vertx.http.HttpServerVerticle",
                    new DeploymentOptions().setInstances(2));
            return httpVerticleDeployment;
        }).flatMap(id ->
            vertx.rxDeployVerticle("com.aantivero.wiki.vertx.http.AuthInitializerVerticle")
        ).subscribe(id -> startFuture.complete(), startFuture::fail);
    }
}
