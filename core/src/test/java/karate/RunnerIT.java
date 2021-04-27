/*-
 * ========================LICENSE_START=================================
 * restheart-core
 * %%
 * Copyright (C) 2014 - 2020 SoftInstigate
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

import static org.junit.Assert.assertEquals;
import com.intuit.karate.Runner;

import org.junit.Test;
import org.restheart.test.integration.AbstactIT;

/**
 * streams tests are disabled because can fail on slow hosts
 * to enable them, remove 'ignore' tag from streams.feature
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */

public class RunnerIT extends AbstactIT {
    @Test
    public void run() {
        var results = Runner.path("classpath:karate")
                .tags("~@ignore")
                .parallel(1);

        assertEquals(0, results.getFailCount());
    }
}
