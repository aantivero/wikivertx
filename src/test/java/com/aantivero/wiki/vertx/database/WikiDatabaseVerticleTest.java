package com.aantivero.wiki.vertx.database;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class WikiDatabaseVerticleTest {

    private Vertx vertx;

    @Before
    public void prepare(TestContext context) throws InterruptedException {
        vertx = Vertx.vertx();
    }

    @After
    public void finish(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void async_behavior(TestContext context) {
        vertx = Vertx.vertx();
        context.assertEquals("foo", "foo");
        Async a1 = context.async();
        Async a2 = context.async(3);
        vertx.setTimer(100, n -> a1.complete());
        vertx.setPeriodic(100, n -> a2.countDown());
    }
}
