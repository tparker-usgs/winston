package gov.usgs.volcanoes.winston.server.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;

public final class HttpTemplateConfiguration extends Configuration {
  Logger LOGGER = LoggerFactory.getLogger(HttpTemplateConfiguration.class);

  private static class HttpTemplateConfigufationHolder {
    public static HttpTemplateConfiguration config = new HttpTemplateConfiguration();
  }

  private HttpTemplateConfiguration() {
    super(Configuration.VERSION_2_3_22);

    setTemplateLoader(new ClassTemplateLoader(getClass(), "/freemarker/www"));
    setDefaultEncoding("UTF-8");
    setBooleanFormat("1,0");

    setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
  }

  public static HttpTemplateConfiguration getInstance() {
    return HttpTemplateConfigufationHolder.config;
  }
}