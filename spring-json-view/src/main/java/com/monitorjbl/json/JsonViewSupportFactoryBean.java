package com.monitorjbl.json;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonViewSupportFactoryBean implements InitializingBean {
  private static final Logger log = LoggerFactory.getLogger(JsonViewSupportFactoryBean.class);

  @Autowired
  private RequestMappingHandlerAdapter adapter;

  private final JsonViewMessageConverter converter;
  private final DefaultView defaultView;

  public JsonViewSupportFactoryBean() {
    this(new ObjectMapper());
  }

  public JsonViewSupportFactoryBean(ObjectMapper mapper) {
    this(new JsonViewMessageConverter(mapper.copy()), DefaultView.create());
  }

  public JsonViewSupportFactoryBean(DefaultView defaultView) {
    this(new JsonViewMessageConverter(new ObjectMapper()), defaultView);
  }

  public JsonViewSupportFactoryBean(ObjectMapper mapper, DefaultView defaultView) {
    this(new JsonViewMessageConverter(mapper.copy()), defaultView);
  }

  private JsonViewSupportFactoryBean(JsonViewMessageConverter converter, DefaultView defaultView) {
    this.converter = converter;
    this.defaultView = defaultView;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>(adapter.getReturnValueHandlers());

    List<HttpMessageConverter<?>> converters = removeJackson(adapter.getMessageConverters());
    converters.add(converter);
    adapter.setMessageConverters(converters);

    decorateHandlers(handlers);
    adapter.setReturnValueHandlers(handlers);
  }

  private List<HttpMessageConverter<?>> removeJackson(List<HttpMessageConverter<?>> converters) {
    List<HttpMessageConverter<?>> copy = new ArrayList<>(converters);
    Iterator<HttpMessageConverter<?>> iter = copy.iterator();
    while(iter.hasNext()) {
      HttpMessageConverter<?> next = iter.next();
      if(next instanceof MappingJackson2HttpMessageConverter) {
        log.debug("Removing MappingJackson2HttpMessageConverter as it interferes with us");
        iter.remove();
      }
    }
    return copy;
  }

  private void decorateHandlers(List<HandlerMethodReturnValueHandler> handlers) {
    List<HttpMessageConverter<?>> converters = new ArrayList<>(adapter.getMessageConverters());
    converters.add(converter);
    for(HandlerMethodReturnValueHandler handler : handlers) {
      int index = handlers.indexOf(handler);
      if(handler instanceof HttpEntityMethodProcessor) {
        handlers.set(index, new JsonViewHttpEntityMethodProcessor(converters));
      } else if(handler instanceof RequestResponseBodyMethodProcessor) {
        handlers.set(index, new JsonViewReturnValueHandler(converters, defaultView));
        break;
      }
    }
  }


  /**
   * Registering custom serializer allows to the JSonView to deal with custom serializations for certains field types.<br>
   * This way you could register for instance a JODA serialization as  a DateTimeSerializer. <br>
   * Thus, when JSonView find a field of that type (DateTime), it will delegate the serialization to the serializer specified.<br>
   * Example:<br>
   * <code>
   *   JsonViewSupportFactoryBean bean = new JsonViewSupportFactoryBean( mapper );
   *   bean.registerCustomSerializer( DateTime.class, new DateTimeSerializer() );
   * </code>
   * @param <T> Type class of the serializer
   * @param class1 {@link Class} the class type you want to add a custom serializer 
   * @param forType {@link JsonSerializer} the serializer you want to apply for that type
   */
  public <T> void registerCustomSerializer( Class<T> class1, JsonSerializer<T> forType )
  {
      this.converter.registerCustomSerializer( class1, forType );
  }
  
  
  /**
   * Unregister a previously registtered serializer. @see registerCustomSerializer
   * @param class1
   */
  public <T> void unregisterCustomSerializer( Class<T> class1 )
  {
      this.converter.unregisterCustomSerializer(class1);
  }

}