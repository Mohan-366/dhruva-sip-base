# RFC 1: OPTIONS PING for Dynamic Server Groups
**Author(s)**:Kalpa Vishnani, Akshay Gowda

---

## 1. Overview
The idea here is to know the status of the SIP endpoint. SIP endpoints enabled to respond for OPTIONS message indicate if it's UP or DOWN. Once we know the status of the endpoint, it can be used to determine if any further requests has to be sent. 

Some endpoints defined are based on SRV/A record, which requires DNS resolution before sending OPTIONS. This RFC describes the considerations, design and implementation details on how to get status for Endpoints which are based on DNS.

## 2. Requirements
1. Configured Dynamic ServerGroups(DSG)
   1. Support for OptionsPing Policy which determines various time interval and failure codes.
   2. Dynamic update without application restart for config change
   3. Respect the TTL value, remove stale endpoints after every dns resolution
2. Dynamic ServerGroups which are part of redirection(i.e 3xx) should support OPTIONS
   1. Support both config based AS and creation of dynamic SG from contact of 3xx
   2. For DSG created as part of 3xx, remove the DSG from OPTIONS status store if none of the subsequent 3xx has it for a given amount of time.

## 3. Design Consideration

### Ping logic for configured DSG
Introduce new API in DnsServerGroupUtil to give back Flux<ServerGroupElements> with resolved ServerGroupElements.
Then use filter to get UP or DOWN elements.
```java
protected Flux<ServerGroupElement> getUpElements(ServerGroup sg) {
  return Flux.defer(() -> {
    if(sg.getSgType() != SGType.STATIC) {
      return dnsServerGroupUtil.getDNSServerGroup(sg, null);
    } else {
      return  Flux.fromIterable(sg.getElements());
    }
  }).filter(
          e -> {
            Boolean status = elementStatus.get(e.toUniqueElementString());
            return status == null || status;
          });
}
```

### Ping logic for DSG created as part of code(i.e. 3xx Contact)
Introduce new API in OptionsPing module that creates new flux. This newly created flux should start pinging the elements present in the DSG.
We should not create flux of same SG multiple times. If the flux already exists then we need to update the associated SGE.
Abstract trunk should decide if Redirection SG needs monitoring, if so call the new API(say, ***pingSG(ServerGroup sg)*** ).
Once the trunk initiates pings towards SG, it can subsequently call status API to check status of  SG or SGE.

1. Call the DNSUtil API to get a resolved SG.
2. Start ping pipeline for the above DSG by calling an api from OptionsPing
3. OptionsPing checks if the pipeline exists for the given SG(based on SG name and hostname). If it does not exist create one, else do nothing.
4. After every UP/Down interval, DNS resolution has to be performed to get new set of elements.
5. Elimination of duplicates in multiple flux. (discussed in next section)
6. Update the status of the element and respective SG

### Elimination of duplicates
A single element can be part of multiple flux, for example element can be part of configuration and also part of SG 
created using contact header. Sending OPTIONS to the same element twice in given duration of time is redundant and creates
flood of requests for that element. To overcome this problem we need to maintain status cache.

Status object contains status of the object(i.e UP/DOWN), timestamp. This timestamp equals to time at which the latest request was sent.
Now if any flux wants to ping this element needs to check if the current status can be considered as valid.
The status is considered as valid if (currentTime-timestamp) < TimeInterval. If valid, make changes to SG associated and skip the element.
If not, change the timestamp to currentTime and send out the OPTIONS. There is a corner case scenario here. Let's say timestamp
is valid but the status does not reflect the latest status. This can happen when request is sent out and before the response is received another
flux tries to access the same element. The status in this case would be for previous request, not the current one which is 
in progress.


### Status Update of ServerGroup and ServerGroupElements
SGE is marked either UP or DOWN based on the response received and stored in HashMap<SGE,Status>. ServerGroup status is 
also stored in HashMap<SG,boolean>. During filtering of elements in each UP or DOWN flux update the SG status if the element
is going to be skipped. For all other elements which received the response, mark the SG as UP if anyone element received UP else
mark is as DOWN.

### Disposal of Flux
If the SG or any of it's associated elements did not receive status query, then it does not make sense to send OPTIONS towards them.
Such Flux should be removed. To achieve this we need to have a global timerTask which periodically cleans up the flux(only the flux created
during runtime). Whenever a SG is queried for status update it's timestamp. If this timestamp is older than the staleTimePeriod
then the associated flux should be disposed.


