package com.cisco.dhruva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.cassandra.CassandraHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.cassandra.CassandraReactiveHealthContributorAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(
    exclude = {
      WebMvcAutoConfiguration.class,
      CassandraReactiveHealthContributorAutoConfiguration.class,
      CassandraHealthContributorAutoConfiguration.class
    })
@ComponentScan(basePackages = {"com.cisco.dsb", "com.cisco.dhruva"})
public class DhruvaApplication extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    setRegisterErrorPageFilter(false);
    return application.sources(DhruvaApplication.class);
  }

  public static void main(String[] args) {
    /*
    This is for handing of MDC when thread switch happens.
      Schedulers.onScheduleHook("MDC Hook", runnable -> {
          Map<String, String> map=MDC.getCopyOfContextMap();
          return ()->{
            if(map != null)
              MDC.setContextMap(map);
            runnable.run();
          };
    }*/
    SpringApplication.run(DhruvaApplication.class, args);
  }
}
