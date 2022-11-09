# DSB RateLimiter Design & Usage
**Author**: Kalpa Vishnani

## DSB RateLimiter
DSB provides ways for the applications to define rate-limiting, allow and deny rules based on certain incoming message characteristics. 
Please note that RateLimiter component encompasses all the three features including rate-limiting, allow-list and deny-list.

### Rate-limiting
This is to limit the number of messages a DSB application wants to handle per given interval of time. Anything beyond that would be rejected.
For instance, an application may want to rate-limit messages
 1. per DhruvaNetwork per remote IP address
 2. per DhruvaNetwork per call-id. 
 3. for entire application
 4. and so on.

Currently for all the requests that are rate-limited, are rejected with a response of 599 Fraud and Dos and all the rate-limited responses are dropped.

### Allow List
Allow List supports configuring remote IPs which will bypass the rate-limiting rules. We can configure allow list either globally or per DhruvaNetwork. This list supports adding IPs as well as CIDRs.
There is also an autoBuild config which when set to true will add all ServerGroup IPs configured for a particular network. 

### Deny List
When a remote IP is added to denyList, all messages from it will be dropped. DenyList can be configured either globally or per DhruvaNetwork. This list supports adding IPs as well as CIDRs. 
Currently for all the messages that are denied due to this config, are simply dropped.

## DSB RateLimiter Design
DSBRateLimiter is implemented on top of the RateLimiter library provided by CSB.
DSB RateLimiter is configured as a JAIN SIP Valve which receives the messages from the JAIN stack MessageChannel after it is parsed and before the transaction is created/procured for the message. This way we can make a decision to accept/reject the message early on in our application stack. 
Once a message is received by the valve, it is either handled by processRequest or processResponse method based on the message type. 
A RateLimitContext is created for the message. This context is built from the metadata of the message such as remote IP, local IP and from the message itself from headers such as call-ID and so on (based on the application requirement). 
The application must set the UserID for the RateLimitContext from one of the fields passed to the context. This is the field which is used to rate-limit/allow/deny the message. For instance, DSB Calling app used remote IP as the parameter to accept or reject the call. 
The RateLimiter iterated over the configured policies. If a match is found, the action for that policy is executed.
  - If a message passes an allow action policy, then it bypasses all the subsequent policy iterations and it is allowed to go further. 
  - If a messages satisfies a deny action policy then the message is rejected by the valve and not passed any further. 
  - If the message is rate-limited it is responded with a 599 Fraud and Dos in case of a request and is dropped in case of a response.
  - We are going to make the response and its type optional for deny and rate limit going forward.  

### DSB RateLimitPolicy to CSB RateLimiter Policy Mapping
DSBRateLimiter allows configuration of RateLimitPolicy as follows:

#### Policy Organization
<img width="378" alt="Screenshot 2022-11-07 at 1 02 31 PM" src="https://sqbu-github.cisco.com/storage/user/4057/files/c9570689-0da8-4b3c-beba-19668c8ed7c4">

Each rateLimiterPolicy is further broken into three policies in the following order. 
  - denyPolicy
  - allowPolicy
  - rateLimitPolicy

The order here is crucial because the policy iteration takes place in the order in which the policies are configured.  

#### Global Vs Network Policy
  1. Type: Network 
A Network policy is applied to a given network. So for a given usedID (remoteIP in case of Calling App), rate-limiting will be applicable per network per remote IP. So if there are 100 remote endpoints sending messages on dhruvaNetworkA, which has a rate-limit of 1000 permits per 60 seconds configured, then each remote endpoint can send at most 1000 requests per minute. Anything beyond that will be rejected.
Allow List and deny list for network type policy are only applicable to the remoteIPs sending messages on that given network. They will be allowed on other networks. 
  2. Type: Global
A global policy is applicable to all the dhruvaNetworks that are eligible for rateLimiting (who's listenPoint have enableRateLimiter set to true).  

### Policy To Network Mapping
Each policy is mapped to one or more DhruvaNetworks. Only if the local IP of a given message matches the corresponding dhruva network interface IP, the policy is applied to the message. The mapping is done as follows:

<img width="273" alt="Screenshot 2022-11-07 at 1 02 44 PM" src="https://sqbu-github.cisco.com/storage/user/4057/files/e982eba5-11c3-4580-962e-c6f886bb8778">


## RateLimiter Config
For any DhruvaNetwork to be eligible for RateLimiting under "network" or "global" type we must set enableRateLimiter to true for them as follows:

<img width="274" alt="Screen Shot 2022-11-02 at 11 06 13 AM" src="https://sqbu-github.cisco.com/storage/user/4057/files/5279664b-8740-4e43-b9d0-6b498089e6cf">


Here, in the above config, only net_sp will be eligible for rate-limiter and not net_b2b or net_cc.
Let's go through the complete RL config to understand the features better.

<img width="416" alt="Screen Shot 2022-11-02 at 11 54 44 AM" src="https://sqbu-github.cisco.com/storage/user/4057/files/d2bdb3b6-dc77-41a4-af41-6313744ba17a">

The above config goes under the "app" section in application.yaml config. Each application must define its own config. 
Here we can see that there is a list of rateLimitPolicies and a map of policies with key as policy and value as a combination of policyName and DhruvaNetworks to which this policy is applicable.
Each rateLimitPolicy has three main fields, 
  - **allowList**: Can have IPs or CIRDs configured.
  - **denyList**: Can have IPs or CIDRs configured.
  - **rateLimit**: No. of permits per internal of time which can be configured in milliseconds/seconds/minutes/hours/dats as ms|s|m|h|d. Permits = -1 means unlimited and Permits = 0 means Zero messages allowed.
All the above three are optional fields.
**autoBuild**: This is an optional filed which is true by default. This field is specific to the allowList. If this field is set to true, we add all the ServerGroup IPs to this list, for the ServerGroups that belong to the network for which this policy is applicable. In the above example, we see that rateLimitPolicyPstn is applicable to net_sp. So the IPs of all the static ServerGroups on network, net_sp, will be added to the allowList implicitly. 
**type**: This is also an optional field which is set to "network" by default. When set to network, it means that this policy will be applicable to only the network(s) mapped to this policy. If the type is set to global, it means the policy will be applicable to all the networks that are eligible for rateLimiting (who have enableRateLimiter set to true in their listenPoint).

## More About the RateLimiter Feature
This feature has been implemented on top of CSB's RateLimiter.
There is a backlog JIRA (WEBEX-288419) to have the responses configurable to rate-limited/denied requests. Currently is it hardcoded. For rate-limited requests we send a 599 response and for denied messages we simply drop them. As part of this JIRA, we will make the response option and type configurable. 
