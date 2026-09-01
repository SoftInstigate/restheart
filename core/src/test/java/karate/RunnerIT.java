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
 * karate/ai/*.feature are disabled by default for the same reason (the {@code @ignore}
 * tag) — they need MongoDB with {@code $vectorSearch}/{@code createSearchIndexes}
 * support (e.g. {@code mongodb/mongodb-atlas-local:preview}), not the plain MongoDB
 * the rest of the suite runs against. To run them: point MongoDB at that image,
 * temporarily remove their {@code @ignore} tag, then run with
 * {@code -Dkarate.path=classpath:karate/ai}.
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