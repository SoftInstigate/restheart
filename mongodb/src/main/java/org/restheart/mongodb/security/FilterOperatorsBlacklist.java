package org.restheart.mongodb.security;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpServerExchange;

import java.util.List;
import java.util.Map;

import static org.restheart.plugins.ConfigurablePlugin.argValue;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.Request;
import org.restheart.plugins.Initializer;
import org.restheart.plugins.InjectConfiguration;
import org.restheart.plugins.InjectPluginsRegistry;
import org.restheart.plugins.PluginsRegistry;
import org.restheart.plugins.RegisterPlugin;

@RegisterPlugin(name = "filterOperatorsBlacklist",
    description = "forbids requests containing filter qparameter using operator in blacklist")
public class FilterOperatorsBlacklist implements Initializer {
    private PluginsRegistry registry;
    private List<String> blacklist;

    @InjectPluginsRegistry
    public void registry(PluginsRegistry registry) {
        this.registry = registry;
    }

    @InjectConfiguration
    public void configuration(Map<String, Object> args) {
        this.blacklist = argValue(args, "blacklist");

        if (!blacklist.stream().allMatch(o -> o.startsWith("$"))) {
            throw new IllegalArgumentException("All entries of blacklist must start with $");
        }
    }

    @Override
    public void init() {
        this.registry
                .getGlobalSecurityPredicates()
                .add((Predicate) (HttpServerExchange exchange) -> {
                    var request = Request.of(exchange);

                    if (request instanceof MongoRequest) {
                        return !contains(((MongoRequest)request).getFiltersDocument(), blacklist);
                    } else {
                        return true;
                    }
                });
    }

    /**
     *
     * @param doc
     * @param blacklist
     * @return true if doc contains any operator in blacklist
     */
    private boolean contains(BsonDocument doc, List<String> blacklist) {
        if (doc  == null) {
            return true;
        }

        var found = doc.keySet().stream().anyMatch(k -> blacklist.contains(k));

        if (found) {
            return true;
        } else {
            var foundInSubDocs = doc.keySet().stream()
                .filter(key -> doc.get(key).isDocument())
                .map(key -> doc.get(key).asDocument())
                .anyMatch(sdoc -> contains(sdoc, blacklist));

            if (foundInSubDocs) {
                return true;
            } else {
                return doc.keySet().stream()
                    .filter(key -> doc.get(key).isArray())
                    .map(key -> doc.get(key).asArray())
                    .anyMatch(array -> contains(array, blacklist));
            }
        }
    }

    private boolean contains(BsonArray array, List<String> blacklist) {
        if (array == null) {
            return false;
        }

        var foundInDocs = array.stream()
            .filter(el -> el.isDocument())
            .map(el -> el.asDocument())
            .anyMatch(doc -> contains(doc, blacklist));

        if (foundInDocs) {
            return true;
        } else {
            return array.stream()
                .filter(el -> el.isArray())
                .map(el -> el.asArray())
                .anyMatch(subArray -> contains(subArray, blacklist));
        }
    }
}
