package com.cisco.dhruva;

import com.cisco.dsb.DhruvaConfig;
import com.cisco.wx2.db.config.CassandraConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {WebMvcAutoConfiguration.class, CassandraConfiguration.class})
@ComponentScan(
    basePackages = {"com.cisco.dsb"},
    basePackageClasses = DhruvaConfig.class)
public class DhruvaApplication extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    setRegisterErrorPageFilter(false);
    return application.sources(DhruvaApplication.class);
  }

  public static void main(String[] args) {
    SpringApplication.run(DhruvaApplication.class, args);
  }
}
