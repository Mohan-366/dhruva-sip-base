package com.cisco.dsb.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import java.io.IOException;
import lombok.CustomLog;

@CustomLog
public class JsonSchemaValidator {

  public static boolean validateSchema(String jsonString, String jsonSchema)
      throws IOException, ProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode jsonNode1 = mapper.readTree(jsonString);
    JsonNode jsonNodeSchema = JsonLoader.fromResource("/" + jsonSchema + ".json");
    final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
    final JsonSchema schema = factory.getJsonSchema(jsonNodeSchema);
    ProcessingReport report;
    report = schema.validate(jsonNode1);
    logger.info("Report : {}", report);
    return report.isSuccess();
  }
}
