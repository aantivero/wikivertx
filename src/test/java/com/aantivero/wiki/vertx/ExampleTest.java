package com.aantivero.wiki.vertx;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

//@RunWith(VertxUnitRunner.class)
public class ExampleTest {

    //@Test(timeout=5000)
    public void async_behavior(TestContext context) {
        Vertx vertx = Vertx.vertx();
        context.assertEquals("foo", "foo"); // TestContext assertion
        Async a1 = context.async(); // async object that can be later completed or failed
        Async a2 = context.async(3); // async object countdown that complete after 3 calls
        vertx.setTimer(100, n -> a1.complete()); // complete when the timer fires
        vertx.setTimer(100, n -> a2.countDown()); // each periodic task tick trigger a countdown.
        //the test passed when all async objects have completed

    }
}
