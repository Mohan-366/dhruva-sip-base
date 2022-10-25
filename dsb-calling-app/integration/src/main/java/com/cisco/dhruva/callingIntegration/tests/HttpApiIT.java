package com.cisco.dhruva.callingIntegration.tests;

import static org.junit.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.cisco.dhruva.callingIntegration.CallingTestProperties;
import com.cisco.dhruva.callingIntegration.DhruvaTestConfig;
import com.cisco.dhruva.callingIntegration.util.IntegrationTestListener;
import com.cisco.dhruva.callingIntegration.util.TestSuiteListener;
import com.cisco.wx2.client.AuthorizationProvider;
import com.cisco.wx2.client.commonidentity.BearerAuthorizationProvider;
import com.cisco.wx2.server.auth.ng.Scope;
import com.cisco.wx2.test.BaseTestConfig;
import com.cisco.wx2.util.OrgId;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Assert;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({IntegrationTestListener.class, TestSuiteListener.class})
@ContextConfiguration(classes = {BaseTestConfig.class, DhruvaTestConfig.class})
public class HttpApiIT extends AbstractTestNGSpringContextTests {
  private static final Logger LOGGER = LoggerFactory.getLogger(HttpApiIT.class);
  @Autowired CallingTestProperties properties;

  @Autowired BaseTestConfig baseTestConfig;

  public AuthorizationProvider dhruvaAuthProvider() {
    AuthorizationProvider authProvider =
        BearerAuthorizationProvider.builder()
            .commonIdentityClientFactory(baseTestConfig.commonIdentityClientFactory())
            .orgId(OrgId.fromString(properties.getDhruvaOrgId()))
            .userId(properties.getDhruvaMachineAccountUser())
            .password(properties.getDhruvaMachineAccountPassword())
            .scope(com.cisco.wx2.server.auth.ng.Scope.of(Scope.Identity.SCIM))
            .clientId(properties.getDhruvaClientId())
            .clientSecret(properties.getDhruvaClientSecret())
            .build();
    try {
      String auth = authProvider.getAuthorization();
      if (auth != null && auth.length() < 512) {
        LOGGER.warn(
            "Check that machine account is using a self-contained token, length = {}",
            auth.length());
      }
    } catch (Exception e) {
      LOGGER.error(e.toString());
      Assert.fail("Unable to get machine account authorization");
    }
    return authProvider;
  }

  @Test
  @Category(HttpApiIT.class)
  public void dnsInjection() {
    try {
      String[] result = dhruvaAuthProvider().getAuthorization().split(" "); // Gets the access token
      String token = result[1]; // Separating out only the token from the response
      String dummyToken = "Dummy"; // Used for negative testing
      URL url = new URL(properties.getDefaultDhruvaHttpEndpoint());
      makeGetRequest(url, token, HttpMethod.GET);
      makePostRequest(url, token, HttpMethod.POST);
      makeGetRequest(url, token, HttpMethod.GET);
      makeDeleteRequest(url, token, HttpMethod.DELETE);
      makeGetRequest(url, token, HttpMethod.GET);
      makeGetRequest(url, dummyToken, HttpMethod.GET);

    } catch (Exception e) {
      LOGGER.error("URL needs to be checked");
    }
  }

  public void getResponse(HttpURLConnection conn, HttpMethod method) throws IOException {
    StringBuilder response = new StringBuilder();
    int HttpResult = conn.getResponseCode();
    if (HttpResult == HttpURLConnection.HTTP_OK) {
      BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
      String output = null;
      while ((output = in.readLine()) != null) {
        response.append(output + "\n");
      }
      assertEquals("Http Response code check", 200, HttpResult);
      if (method == HttpMethod.GET)
        assertTrue(
            response
                .toString()
                .contains(
                    "dnsARecords")); // GET request should have dnsARecord Key in it, even though it
      // can be empty
      else
        assertTrue(
            response
                .toString()
                .contains(
                    "SUCCESS")); // POST and DELETE won't show any records but their response should
      // be success
      LOGGER.info("Response from {} request:- {}", method, response.toString());
      in.close();
    } else {
      assertEquals("Http Response code check", 401, HttpResult); // Negative test case
      LOGGER.info("Did not receive 200 OK for {}, instead getting {}", method, HttpResult);
    }
  }

  public enum HttpMethod {
    GET,
    POST,
    DELETE
  }

  public void makeGetRequest(URL url, String token, HttpMethod method) throws IOException {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("Authorization", "Bearer " + token);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setDoInput(true);
      conn.setRequestMethod("GET");
      getResponse(conn, method);
    } catch (Exception e) {
      LOGGER.error("Error in get request. {}", e.toString());
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  public void makePostRequest(URL url, String token, HttpMethod method) throws IOException {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("Authorization", "Bearer " + token);
      conn.setRequestProperty("Content-Type", "application/json");
      conn.setRequestMethod("POST");
      conn.setDoOutput(true);
      String body =
          "{"
              + "\"dnsARecords\": "
              + "["
              + "{"
              + "\"name\": \""
              + "test.beech.com"
              + "\","
              + "\"ttl\": 36000,"
              + "\"address\": \""
              + "127.0.0.1"
              + "\","
              + "\"injectAction\": 2"
              + "},"
              + "{"
              + "\"name\": \""
              + "test1.ns.cisco.com"
              + "\","
              + "\"ttl\": 36000,"
              + "\"address\": \""
              + "127.0.0.1"
              + "\","
              + "\"injectAction\": 2"
              + "}"
              + "],"
              + "\"dnsSRVRecords\": [{}]}'";
      try (OutputStream os = conn.getOutputStream()) {
        byte[] input = body.getBytes("utf-8");
        os.write(input, 0, input.length);
      } catch (IOException e) {
        LOGGER.error(e.toString());
      }
      getResponse(conn, method);
    } catch (Exception e) {
      LOGGER.error("Error in POST request. {}", e.toString());
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  public void makeDeleteRequest(URL url, String token, HttpMethod method) throws IOException {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) url.openConnection();
      conn.setRequestProperty("Authorization", "Bearer " + token);
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      conn.setDoOutput(true);
      conn.setRequestMethod("DELETE");
      getResponse(conn, method);
    } catch (Exception e) {
      LOGGER.error("Error in delete request. {}", e.toString());
    } finally {
      if (conn != null) conn.disconnect();
    }
  }
}
