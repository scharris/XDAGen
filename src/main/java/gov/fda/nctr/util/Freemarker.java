package gov.fda.nctr.util;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Map;

import freemarker.template.Template;
import freemarker.template.Version;


public class Freemarker
{
    public static final Version compatibilityVersion = new Version("2.3.28");

    public static String applyTemplate
    (
        Template template,
        Map<String,Object> templateModel
    )
    {
        try
        {
            Writer sw = new StringWriter();

            template.process(templateModel, sw);

            return sw.toString();
        }
        catch(Exception e)
        {
            throw new RuntimeException("Failed to apply template: " + e.getMessage());
        }
    }

}
