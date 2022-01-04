#DSB-Calling-App
Calling app uses three listen points to receive and send calls to PSTN, Media Anchor, Calling Core.
Check the application-local.yaml on how these are configured and modify listenPoints, SGs according to your need. 

Enable local profile to load configuration from this property source file
`spring.profiles.active:local`. Add this in bootstrap.yaml present under server module.
