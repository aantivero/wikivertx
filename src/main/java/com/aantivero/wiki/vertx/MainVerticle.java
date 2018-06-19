package com.aantivero.wiki.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;

public class MainVerticle extends AbstractVerticle {

    private Future<Void> prepareDatabase() {
        Future<Void> future = Future.future();
        return future;
    }

    private Future<Void> startHttpServer() {
        Future<Void> future = Future.future();
        return future;
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
