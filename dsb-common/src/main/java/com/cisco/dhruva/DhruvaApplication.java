package com.cisco.dhruva;

import com.cisco.dsb.DhruvaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {WebMvcAutoConfiguration.class})
@ComponentScan(
    basePackages = {"com.cisco.dsb", "com.cisco.dhruva"},
    basePackageClasses = {DhruvaConfig.class, ProxyService.class})
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
