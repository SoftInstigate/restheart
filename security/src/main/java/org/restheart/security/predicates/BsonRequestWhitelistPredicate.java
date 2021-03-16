package org.restheart.security.predicates;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.restheart.exchange.BsonRequest;
import org.restheart.exchange.Request;
import org.restheart.utils.BsonUtils;

import io.undertow.predicate.Predicate;
import io.undertow.predicate.PredicateBuilder;
import io.undertow.server.HttpServerExchange;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a predicate that resolve to true if the request content is bson and
 * all keys in the request content are in the specified whitelist
 */
public class BsonRequestWhitelistPredicate implements Predicate {
    private static final Logger LOGGER = LoggerFactory.getLogger(BsonRequestWhitelistPredicate.class);

    private final Set<String> whitelist;

    public BsonRequestWhitelistPredicate(String[] whitelist) {
        this.whitelist = whitelist == null ? Sets.newHashSet() : Sets.newHashSet(whitelist);
    }

    @Override
    public boolean resolve(HttpServerExchange exchange) {
        var _request = Request.of(exchange);

        if (_request == null || !(_request instanceof BsonRequest)) {
            LOGGER.warn("bson-request-whitelist predicate not invoked on BsonRequest but {}, it won't allow the request", _request == null ? _request: _request.getClass().getSimpleName());
            return false;
        } else {
            return areAllKeysWhitelisted(this.whitelist, ((BsonRequest)_request).getContent());
        }
    }

    private boolean areAllKeysWhitelisted(Set<String> whitelist, BsonValue docArArrayOfDocs) {
        if (docArArrayOfDocs == null ) {
            return true;
        } else if (docArArrayOfDocs.isDocument()) {
            return areAllKeysWhitelisted(whitelist, docArArrayOfDocs.asDocument());
        } else if (docArArrayOfDocs.isArray()){
            return docArArrayOfDocs.asArray().stream().filter(BsonValue::isDocument).map(BsonValue::asDocument)
                .allMatch(doc -> areAllKeysWhitelisted(whitelist, doc));
        } else {
            throw new IllegalArgumentException("");
        }
    }

    private boolean areAllKeysWhitelisted(Set<String> whitelist, BsonDocument doc) {
        if (doc == null || doc.isEmpty()) {
            return true;
        } if (whitelist == null || whitelist.isEmpty() ) {
            return false;
        } else {
            return getLeafsKeys(doc).stream().allMatch(key -> isWhitelisted(whitelist, key));
        }
    }

    private boolean isWhitelisted(Set<String> whitelist, String leafKey) {
        return whitelist.stream().anyMatch(whitelistedKey ->
        leafKey.equals(whitelistedKey) || leafKey.startsWith(whitelistedKey.concat(".")));
    }

    private Set<String> getLeafsKeys(BsonDocument d) {
        var flatten = BsonUtils.flatten(d, true);

        final Set<String> leafsKeys = Sets.newHashSet();
        leafsKeys.addAll(flatten.keySet());

        d.keySet().stream().filter(BsonUtils::isUpdateOperator)
            .map(uo -> d.get(uo))
            .filter(doc -> doc.isDocument())
            .map(doc -> doc.asDocument())
            .map(doc -> doc.keySet())
            .forEach(leafsKeys::addAll);

        d.keySet().stream().filter(BsonUtils::isUpdateOperator).forEach(leafsKeys::remove);

        return leafsKeys;
    }


    public static class Builder implements PredicateBuilder {
        @Override
        public String name() {
            return "bson-request-whitelist";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.<String, Class<?>>singletonMap("whitelist", String[].class);
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.singleton("whitelist");
        }

        @Override
        public String defaultParameter() {
            return "whitelist";
        }

        @Override
        public Predicate build(Map<String, Object> config) {
            return new BsonRequestWhitelistPredicate((String[]) config.get("whitelist"));
        }
    }
}
