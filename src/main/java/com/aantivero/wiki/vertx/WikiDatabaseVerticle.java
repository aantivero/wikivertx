package com.aantivero.wiki.vertx;

import io.vertx.core.AbstractVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class WikiDatabaseVerticle extends AbstractVerticle {

    public static final String CONFIG_WIKIDB_JDBC_URL = "wikidb.jdbc.url";
    public static final String CONFIG_WIKIDB_JDBC_DRIVER_CLASS = "wikidb.jdbc.driver_class";
    public static final String CONFIG_WIKIDB_JDBC_MAX_POOL_SIZE = "wikidb.jdbc.max_pool_size";
    public static final String CONFIG_WIKIDB_SQL_QUERIES_RESOURCE_FILE = "wikidb.sqlqueries.resource.file";

    public static final String CONFIG_WIKIDB_QUEUE = "wikidb.queue";

    private static final Logger LOGGER = LoggerFactory.getLogger(WikiDatabaseVerticle.class);

    private enum SqlQuery {
        CREATE_PAGES_TABLE,
        ALL_PAGES,
        GET_PAGE,
        CREATE_PAGE,
        SAVE_PAGE,
        DELETE_PAGE
    }

    private final HashMap<SqlQuery, String> sqlQueries = new HashMap<>();

    
}
