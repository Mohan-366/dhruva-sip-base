package com.cisco.dhruva.application.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The Calling App microservice. */
@RestController
@RequestMapping("${cisco-spark.server.api-path:/api}/v1")
public class CallingAppController {
  @Autowired
  public CallingAppController() {}
}
