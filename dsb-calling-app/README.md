#DSB-Calling-App
Calling app uses three listen points to receive and send calls to PSTN, Media Anchor, Calling Core.
Check the application-local.yaml on how these are configured and modify listenPoints, SGs according to your need. 

Refer https://confluence-eng-gpk2.cisco.com/conf/display/DHRUVA/WebEx+calling+Trunking+Architecture for more details.

Enable local profile to load configuration from this property source file
`spring.profiles.active:local`. Add this in bootstrap.yaml present under server module.
