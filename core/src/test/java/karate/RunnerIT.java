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
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RunnerIT extends AbstactIT {
    @Test
    public void run() {
        List<String> tags = new ArrayList<>(List.of("~@ignore", "~@helper"));

        if (!isGraalVMRuntime()) {
            // Skip polyglot tests on non-GraalVM runtimes (JS plugins won't load)
            tags.add("~@requires-graalvm");
        }

        var results = Runner.path("classpath:karate")
                .tags(tags.toArray(new String[0]))
                .parallel(1);

        assertEquals(0, results.getFailCount());
    }

    private static boolean isGraalVMRuntime() {
        try {
            Class.forName("org.graalvm.home.Version");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}