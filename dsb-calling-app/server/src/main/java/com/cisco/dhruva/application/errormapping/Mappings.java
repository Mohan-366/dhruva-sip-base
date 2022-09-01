package com.cisco.dhruva.application.errormapping;

import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder(setterPrefix = "set")
public class Mappings {
  @NotBlank private int mappedResponseCode;
  @NotBlank private String mappedResponsePhrase;
  @NotBlank List<Integer> errorCodes;
}
