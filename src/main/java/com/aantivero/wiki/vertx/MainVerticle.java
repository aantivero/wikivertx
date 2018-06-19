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
        startFuture.complete();
    }
}
