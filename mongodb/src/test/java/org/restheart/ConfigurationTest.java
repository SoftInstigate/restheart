/*-
 * ========================LICENSE_START=================================
 * restheart-mongodb
 * %%
 * Copyright (C) 2014 - 2022 SoftInstigate
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
package org.restheart;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import static org.junit.Assert.*;

import org.restheart.configuration.ConfigurationException;
import org.restheart.mongodb.MongoServiceConfiguration;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 *
 * @author mturatti
 */
public class ConfigurationTest {

    MustacheFactory mf = new DefaultMustacheFactory();

    /**
     *
     */
    public ConfigurationTest() {
    }

    /**
     *
     * @throws IOException
     * @throws ConfigurationException
     */
    public void testConfigurationTemplate() throws IOException, ConfigurationException {
        Map<String, Object> obj = LoadConfiguration("env.properties");

        MongoServiceConfiguration.init(obj);

        MongoServiceConfiguration conf = MongoServiceConfiguration.get();

        assertEquals("/", conf.getMongoMounts().get(0).get("what"));
        assertEquals("mongodb://restheart:R3ste4rt!@mongodb-test/?readPreference=primary&authSource=admin",
                conf.getMongoUri().toString());
    }

    private Map<String, Object> LoadConfiguration(String envProperties) throws IOException {
        Properties p = new Properties();
        p.load(this.getClass().getClassLoader().getResourceAsStream(envProperties));
        Mustache m = mf.compile("restheart.yml");
        StringWriter writer = new StringWriter();
        m.execute(writer, p);
        writer.flush();
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> obj = yaml.load(writer.toString());
        return obj;
    }

}
