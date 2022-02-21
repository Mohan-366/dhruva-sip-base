package com.cisco.dhruva;

import com.cisco.dhruva.util.IntegrationTestListener;
import com.cisco.wx2.test.BaseTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Listeners;

@Listeners({IntegrationTestListener.class})
@ContextConfiguration(classes = {BaseTestConfig.class, DhruvaConfig.class})
public class DhruvaIT extends AbstractTestNGSpringContextTests {

  @Autowired protected SipStackService sipStackService;
}
