package com.cisco.dhruva.application.errormapping;

import java.util.List;
import javax.validation.constraints.NotBlank;
import lombok.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder(setterPrefix = "set")
public class ErrorMappingPolicy {
  @NotBlank private String name;
  List<Mappings> mappings;

  @Override
  public boolean equals(Object a) {
    if (this == a) return true;
    if (a instanceof ErrorMappingPolicy) {
      ErrorMappingPolicy b = ((ErrorMappingPolicy) a);
      return new EqualsBuilder().append(name, b.name).isEquals();
    }
    return false;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(name).toHashCode();
  }
}
