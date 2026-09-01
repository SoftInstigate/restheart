/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2026 SoftInstigate
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END==================================
 */
package karate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.restheart.test.integration.AbstactIT;

import com.intuit.karate.Runner;

/**
 * streams tests are disabled because can fail on slow hosts
 * to enable them, remove 'ignore' tag from streams.feature
 *
 * The default MongoDB (started by core/pom.xml's docker-maven-plugin binding, "mongodb"
 * profile) is {@code mongodb/mongodb-atlas-local} — bundles mongod + mongot as a
 * self-initializing single-node replica set — so {@code karate/ai/*.feature}
 * ({@code @requires-vector-search}) run by default, no separate setup needed. CI's
 * compatibility-matrix legs that instead start the official {@code mongo} image (no
 * {@code $vectorSearch}/{@code createSearchIndexes} support — see the
 * {@code mongodb-classic} profile) pass {@code -Dkarate.vectorSearch=false} to exclude
 * them on those legs specifically.
 *
 * {@code karate/ai/embedding-provider.feature} ({@code @requires-embedding-provider})
 * additionally needs a real embedding-provider API key (a live HTTP call, not just a
 * running MongoDB) and is opt-in via {@code -Dkarate.embeddingProvider=true} — off by
 * default so a plain {@code mvn clean verify} never requires a secret. CI's atlas-local
 * leg sets it (and the {@code RHO} env var carrying the key — see
 * {@code .github/workflows/*.yml}) only when a {@code VOYAGE_API_KEY} secret is
 * configured on the repository.
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RunnerIT extends AbstactIT {
    @Test
    public void run() {
        List<String> tags = new ArrayList<>(List.of("~@ignore", "~@helper"));

        if (!isGraalVM25_1_OrLater()) {
            // Skip polyglot tests on non-GraalVM or GraalVM < 25.1 (JS plugins won't load)
            tags.add("~@requires-graalvm");
        }

        // true unless a caller explicitly says the running MongoDB lacks $vectorSearch/
        // createSearchIndexes support (CI's official-mongo compatibility-matrix legs) —
        // matches the default MongoDB (mongodb-atlas-local) actually supporting them.
        if (!Boolean.parseBoolean(System.getProperty("karate.vectorSearch", "true"))) {
            tags.add("~@requires-vector-search");
        }

        // false unless a caller explicitly opts in AND has wired a real embedding-provider
        // API key via the RHO env var (see karate/ai/embedding-provider.feature) — never
        // required for a plain local run.
        if (!Boolean.parseBoolean(System.getProperty("karate.embeddingProvider", "false"))) {
            tags.add("~@requires-embedding-provider");
        }

        // Defaults to the whole suite. Narrow it while debugging with a comma-separated list:
        //   mvn verify -Dit.includes=**/RunnerIT.java \
        //              -Dkarate.path=classpath:karate/stripe/subscription-acl-variable.feature
        var paths = System.getProperty("karate.path", "classpath:karate").split(",");

        var results = Runner.path(paths)
                .tags(tags.toArray(new String[0]))
                .hook(new ProgressHook())
                .parallel(1);

        assertEquals(0, results.getFailCount());
    }

    private static boolean isGraalVM25_1_OrLater() {
        try {
            var versionClass = Class.forName("org.graalvm.home.Version");
            var getCurrent = versionClass.getMethod("getCurrent");
            var version = getCurrent.invoke(null);
            var compareTo = versionClass.getMethod("compareTo", versionClass);
            var v25_1 = versionClass.getMethod("create", int.class, int.class)
                    .invoke(null, 25, 1);
            return (int) compareTo.invoke(version, v25_1) >= 0;
        } catch (Exception e) {
            return false;
        }
    }
}