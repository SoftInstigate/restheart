/*
 * RESTHeart - the Web API for MongoDB
 * Copyright (C) SoftInstigate Srl
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.restheart;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
import org.yaml.snakeyaml.Yaml;

/**
 *
 * @author mturatti
 */
public class ConfigurationTest {

    MustacheFactory mf = new DefaultMustacheFactory();

    public ConfigurationTest() {
    }

    public void testConfigurationTemplate() throws IOException, ConfigurationException {
        Map<String, Object> obj = LoadConfiguration("env.properties");

        Configuration conf = new Configuration(obj, false);

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
        Yaml yaml = new Yaml();
        Map<String, Object> obj = yaml.load(writer.toString());
        return obj;
    }

}
