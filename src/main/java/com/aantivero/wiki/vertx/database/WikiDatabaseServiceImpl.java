package com.aantivero.wiki.vertx.database;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class WikiDatabaseServiceImpl implements WikiDatabaseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseServiceImpl.class);

    private final HashMap<SqlQuery, String> sqlQueries;
    private final JDBCClient dbClient;

    public WikiDatabaseServiceImpl(JDBCClient dbClient,
                                   HashMap<SqlQuery, String> sqlQueries,
                                   Handler<AsyncResult<WikiDatabaseService>> readyHandler) {
        this.dbClient = dbClient;
        this.sqlQueries = sqlQueries;


    }

    @Override
    public WikiDatabaseService fetchAllPages(Handler<AsyncResult<JsonArray>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService fetchPage(String name, Handler<AsyncResult<JsonObject>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService createPage(String title, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService savePage(int id, String markdown, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }

    @Override
    public WikiDatabaseService deletePage(int id, Handler<AsyncResult<Void>> resultHandler) {
        return null;
    }
}
