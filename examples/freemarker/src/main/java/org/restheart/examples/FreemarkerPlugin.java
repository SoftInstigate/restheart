/*-
 * ========================LICENSE_START=================================
 * freemarker
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.examples;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.restheart.exchange.StringRequest;
import org.restheart.exchange.StringResponse;
import org.restheart.plugins.RegisterPlugin;
import org.restheart.plugins.StringService;
import org.restheart.utils.HttpStatus;

import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import freemarker.template.TemplateNotFoundException;

@RegisterPlugin(name = "html-example", description = "HTML sample page with Freemarker", enabledByDefault = true, defaultURI = "/site")
public class FreemarkerPlugin implements StringService {

    @Override
    public void handle(final StringRequest request, final StringResponse response) throws Exception {
        switch (request.getMethod()) {
            case GET -> {
                String user = request.getQueryParameterOrDefault("user", "Big Joe");
                final Map<String, Object> model = new HashMap<>();
                model.put("user", user);
                final String html = processTemplate(model, "index.html");

                response.setContentType("text/html; charset=utf-8");
                response.setContent(html);
                response.setStatusCode(HttpStatus.SC_OK);
            }
            case OPTIONS -> handleOptions(request);
            default -> response.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
        }
    }

    private static String processTemplate(Map<String, Object> model, String templateName)
            throws TemplateNotFoundException, MalformedTemplateNameException,
            IOException, TemplateException {
        Template template = loadFreemarkerTemplate(templateName);
        StringWriter sw = new StringWriter();
        template.process(model, sw);
        String html = sw.toString();
        return html;
    }

    private static Template loadFreemarkerTemplate(String templateName)
            throws IOException, TemplateNotFoundException, MalformedTemplateNameException {
        Configuration freeMarkerConfiguration = new Configuration(Configuration.VERSION_2_3_31);

        freeMarkerConfiguration.setClassForTemplateLoading(FreemarkerPlugin.class, "/templates");
        // Comment out the above statement and uncomment the following two lines if you want to load templates
        // from the local file system instead of the classpath
        //File templatesDir = Paths.get("src/main/resources/templates").toFile();
        //freeMarkerConfiguration.setDirectoryForTemplateLoading(templatesDir);

        freeMarkerConfiguration.setDefaultEncoding("UTF-8");
        // Sets how errors will appear.
        // During web page *development* TemplateExceptionHandler.HTML_DEBUG_HANDLER is
        // better.
        freeMarkerConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.HTML_DEBUG_HANDLER);
        // freeMarkerConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // Don't log exceptions inside FreeMarker that it will thrown at you anyway:
        freeMarkerConfiguration.setLogTemplateExceptions(false);

        // Wrap unchecked exceptions thrown during template processing into
        // TemplateException-s:
        freeMarkerConfiguration.setWrapUncheckedExceptions(true);

        // Do not fall back to higher scopes when reading a null loop variable:
        freeMarkerConfiguration.setFallbackOnNullLoopVariable(false);

        Template template = freeMarkerConfiguration.getTemplate(templateName);
        return template;
    }
}
