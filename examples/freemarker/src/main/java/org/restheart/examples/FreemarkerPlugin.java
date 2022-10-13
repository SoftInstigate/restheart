package org.restheart.examples;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Paths;
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
                String user = request.getQueryParameterOfDefault("user", "Big Joe");
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
