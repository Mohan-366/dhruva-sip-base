/*
 * Copyright (c) 2020  by Cisco Systems, Inc.All Rights Reserved.
 * @author graivitt
 */

package com.cisco.dhruva.service;

import com.cisco.dhruva.common.executor.DhruvaExecutorService;
import com.cisco.dhruva.config.sip.DhruvaSIPConfigProperties;
import com.cisco.dhruva.util.log.DhruvaLoggerFactory;
import com.cisco.dhruva.util.log.Logger;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class ProxyService {

  Logger logger = DhruvaLoggerFactory.getLogger(ProxyService.class);

  @Autowired DhruvaSIPConfigProperties dhruvaSIPConfigProperties;

  @Autowired private DhruvaExecutorService dhruvaExecutorService;

  @Autowired private Environment env;

  @Autowired public MetricService metricsService;

  @Autowired SipServerLocatorService resolver;

  @PostConstruct
  public void init() throws Exception {}

  @PreDestroy
  private void releaseServiceResources() {}
}
