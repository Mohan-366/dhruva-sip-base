package com.cisco.dsb.common.sip.dto;

import com.cisco.dsb.common.record.DhruvaAppRecord;
import com.cisco.dsb.common.util.log.event.EventingService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@EqualsAndHashCode
@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EventMetaData {

  private boolean isInternallyGenerated;
  private DhruvaAppRecord appRecord;
  private EventingService eventingService;
}
