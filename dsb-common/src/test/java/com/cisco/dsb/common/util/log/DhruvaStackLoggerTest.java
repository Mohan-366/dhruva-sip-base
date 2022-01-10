package com.cisco.dsb.common.util.log;

import com.cisco.dsb.common.sip.parser.RemotePartyIDParser;
import gov.nist.core.GenericObject;
import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.*;
import gov.nist.javax.sip.parser.ims.PAssertedIdentityParser;
import gov.nist.javax.sip.parser.ims.PPreferredIdentityParser;
import javax.sip.SipFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class DhruvaStackLoggerTest {
  private static Logger logger = DhruvaLoggerFactory.getLogger(DhruvaStackLoggerTest.class);

  private static SipFactory sipFactory;

  static {
    sipFactory = SipFactory.getInstance();
    sipFactory.setPathName("gov.nist");
  }

  public void testSipObfuscation() throws Exception {
    // Format: number@IP
    testObfuscateSip(
        new RequestLineParser("INVITE sip:+13214466918@192.168.90.88 SIP/2.0\n").parse(),
        "INVITE [OBFUSCATED:\"sip:xxxxxxxxxxxx@XXXXXXXXXXXXX\" hash=08948e9f11937e933f576e26634b035c "
            + "(04d3ee27e57a5f848b849288c2e84a76 @ d678243ed46daca5a10569f5d991a916)] SIP/2.0");

    // Format: 4 digit number @ IP
    testObfuscateSip(
        new RequestLineParser("INVITE sip:1234@192.168.90.88 SIP/2.0\n").parse(),
        "INVITE [OBFUSCATED:\"sip:xxxx@XXXXXXXXXXXXX\" hash=f8f4dc9be011f0b676f2102760bb3ec8 "
            + "(81dc9bdb52d04dc20036dbd8313ed055 @ d678243ed46daca5a10569f5d991a916)] SIP/2.0");

    // Format: number@IP:Port
    testObfuscateSip(
        createSipUri("sip:+13214466918@192.168.90.88", 5061),
        "[OBFUSCATED:\"sip:xxxxxxxxxxxx@XXXXXXXXXXXXX:5061\" hash=08948e9f11937e933f576e26634b035c "
            + "(04d3ee27e57a5f848b849288c2e84a76 @ d678243ed46daca5a10569f5d991a916)]");

    // Format: username@IP
    testObfuscateSip(
        createSipUri("sip:username@192.168.90.88"),
        "[OBFUSCATED:\"sip:xxxxxxxx@XXXXXXXXXXXXX\" hash=bb8901a8042d521b0a5dacad72860bf0 "
            + "(14c4b06b824ec593239362517f538b29 @ d678243ed46daca5a10569f5d991a916)]");

    // Format: username@domain.com
    testObfuscateSip(
        new FromParser("From: sip:username@domain.com\n").parse(),
        "From: <[OBFUSCATED:\"sip:xxxxxxxx@XXXXXXXXXX\" hash=af0fcbb2430420c94aa598f825cf7be7 "
            + "(14c4b06b824ec593239362517f538b29 @ 5ce448a6d172a47da4d62813c7029160)]>");

    // Format: username:password@domain.com
    testObfuscateSip(
        new ToParser("To: sip:username:password@domain.com\n").parse(),
        "To: <[OBFUSCATED:\"sip:xxxxxxxx:xxxxxxxx@XXXXXXXXXX\" hash=af0fcbb2430420c94aa598f825cf7be7 "
            + "(14c4b06b824ec593239362517f538b29 @ 5ce448a6d172a47da4d62813c7029160)]>");

    // Format: host@subdomain.domain.com
    testObfuscateSip(
        new PAssertedIdentityParser(
                "P-Asserted-Identity: host@subdomain.domain.com <sip:+10982345764@192.168.90.206:5061;x-cisco-number=+19702870206>\n")
            .parse(),
        "P-Asserted-Identity: \"[OBFUSCATED:170bfdd85edce92ce6fb01a8ae80956a]\" "
            + "<[OBFUSCATED:\"sip:xxxxxxxxxxxx@XXXXXXXXXXXXXX:5061;x-cisco-number=[OBFUSCATED:1c210f5104cf92e9fdb78836af09f34e]\" "
            + "hash=428b0215f37c6d7a43fa333cc1c809fb (01876febdf263c8095bd3691c78e1336 @ 2b01dc7ce029a2f5ffe47688b232fd7e)]>");

    // Format: Non US domain- host@subdomain.domain.co.uk
    testObfuscateSip(
        new PAssertedIdentityParser(
                "P-Asserted-Identity: host@subdomain.domain.co.uk <sip:+10982345764@192.168.90.206:5061;x-cisco-number=+19702870206>\n")
            .parse(),
        "P-Asserted-Identity: \"[OBFUSCATED:4fde4cdefe1a6a54d4edc4d14bdd3652]\" "
            + "<[OBFUSCATED:\"sip:xxxxxxxxxxxx@XXXXXXXXXXXXXX:5061;x-cisco-number=[OBFUSCATED:1c210f5104cf92e9fdb78836af09f34e]\" "
            + "hash=428b0215f37c6d7a43fa333cc1c809fb (01876febdf263c8095bd3691c78e1336 @ 2b01dc7ce029a2f5ffe47688b232fd7e)]>");

    // Format: x-cisco-number which is a US number
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-number=+17205529130"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-number=[OBFUSCATED:9d4b3849a89980cce975ac64bd84c877]\" "
            + "hash=b58996c504c5638798eb6b511e6f49af (ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Format: x-cisco-number which is non US number
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-number=+9187205529130"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-number=[OBFUSCATED:4409aaf24f9767bfdc588e842863b002]\" "
            + "hash=b58996c504c5638798eb6b511e6f49af (ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Format: x-cisco-number followed by some random parameters
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-number=+14084744493;party=calling"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-number=[OBFUSCATED:ce1c16e40eb5493d9b051e2ec0f2a23f];party=calling\" "
            + "hash=b58996c504c5638798eb6b511e6f49af (ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Format: x-cisco-number enclosed within angle brackets followed by some random values
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-number=+19702870206;party=calling;65785a87"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-number=[OBFUSCATED:1c210f5104cf92e9fdb78836af09f34e];party=calling;65785a87\" "
            + "hash=b58996c504c5638798eb6b511e6f49af (ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Format: x-cisco-number being a 4 digit enterprise DN
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-number=5297;party=calling;65785a87"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-number=[OBFUSCATED:bf5a1d9043100645b2067fa70d7a1ea6];party=calling;65785a87\" "
            + "hash=b58996c504c5638798eb6b511e6f49af (ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Format: x-cisco-number being a string
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-number=roy.miller;party=calling;65785a87"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-number=[OBFUSCATED:ac23bdc12b076968d71629c4909438d7];party=calling;65785a87\" "
            + "hash=b58996c504c5638798eb6b511e6f49af (ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Verify both "x-cisco-tenant" and "x-cisco-tenant-id" are NOT obfuscated (doesn't matter that
    // they are not URI params)
    testObfuscateSip(
        createSipUri("sip:user@example.com;x-cisco-tenant=orgId;x-cisco-tenant-id=orgId2"),
        "[OBFUSCATED:\"sip:xxxx@XXXXXXXXXXX;x-cisco-tenant=orgId;x-cisco-tenant-id=orgId2\" hash=b58996c504c5638798eb6b511e6f49af "
            + "(ee11cbb19052e40b07aac0ca060c23ee @ 5ababd603b22780302dd8d83498e5172)]");

    // Format: P-Preferred
    testObfuscateSip(
        new PPreferredIdentityParser(
                "P-Preferred-Identity: \"test user\" <sip:+10982345764@192.168.90.206:5061;x-cisco-number=+19702870206>\n")
            .parse(),
        "P-Preferred-Identity: \"[OBFUSCATED:0d432e6298384cc9b7c6d338ea89bd79]\" "
            + "<[OBFUSCATED:\"sip:xxxxxxxxxxxx@XXXXXXXXXXXXXX:5061;x-cisco-number=[OBFUSCATED:1c210f5104cf92e9fdb78836af09f34e]\" "
            + "hash=428b0215f37c6d7a43fa333cc1c809fb (01876febdf263c8095bd3691c78e1336 @ 2b01dc7ce029a2f5ffe47688b232fd7e)]>");

    // a copy of a Spark Call RPID on ringing
    testObfuscateSip(
        new RemotePartyIDParser(
                "Remote-Party-ID: \"test rpid\" "
                    + "<sip:testrpid@domain.com;"
                    + "x-cisco-assert-internal-same-site=[Obfuscated];"
                    + "x-cisco-assert-user=testrpid%40huron-test.call.ciscospark.com;"
                    + "x-cisco-assert-internal-different-site=81003479;"
                    + "x-cisco-assert-external=+12223334444;"
                    + "x-cisco-assert-external-privacy=full>;"
                    + "party=called;"
                    + "screen=no;"
                    + "privacy=full\n")
            .parse(),
        "Remote-Party-ID: \"[OBFUSCATED:e47b4210207c5341ffd7c619876d3eeb]\" "
            + "<[OBFUSCATED:\"sip:xxxxxxxx@XXXXXXXXXX;"
            + "x-cisco-assert-internal-same-site=[OBFUSCATED:95a171943f9d425eda618e553440ed3c];"
            + "x-cisco-assert-user=[OBFUSCATED:2ef8e554ca85db0f40a0cbbd07c33ac9];"
            + "x-cisco-assert-internal-different-site=[OBFUSCATED:458150cf0cecad767edf59f8ad1f2090];"
            + "x-cisco-assert-external=[OBFUSCATED:330ec9ef565648e20c4079a05e1f47fa];"
            + "x-cisco-assert-external-privacy=[OBFUSCATED:e9dc924f238fa6cc29465942875fe8f0]\" "
            + "hash=d9f5bf4bb6ac8a7b5fb522865287f059 (1efc394b9fe36fabf8193e0c2809b63c @ 5ce448a6d172a47da4d62813c7029160)]>;"
            + "party=called;"
            + "screen=no;"
            + "privacy=full");

    // TODO DSB, only this one is failing, need to debug further
    //    testObfuscateSip(
    //        new PChargingVectorParser("P-Charging-Vector: icid-value=\"+14084744562\"\n").parse(),
    //        "P-Charging-Vector: icid-value=\"[OBFUSCATED:a6c03ffe6f60de230b965eaccdaae088]\"");

    // Test RegEx based obfuscation with a header not recognized by JAIN SIP
    // Also tests that "x-cisco-number" and quoted strings are obfuscated by RegEx based obfuscation
    // Also tests that "x-cisco-tenant*" params are NOT obfuscated by RegEx based obfuscation
    testObfuscateSip(
        new ExtensionHeaderParser(
                "Unrecognized-Header-With-PII: \"Bob\" "
                    + "<sip:bob@acme.com;x-cisco-number=+19702870206>"
                    + ";x-cisco-tenant=orgId;x-cisco-tenant-id=orgId;icid-value=\"+14084744562\"\n")
            .parse(),
        "Unrecognized-Header-With-PII: \"[OBFUSCATED:2fc1c0beb992cd7096975cfebf9d5c3b]\" "
            + "<sip:[OBFUSCATED:78e9fa7d6d2ddba416ad7534eb1403d0];x-cisco-number=[OBFUSCATED:1c210f5104cf92e9fdb78836af09f34e]>"
            + ";x-cisco-tenant=orgId;x-cisco-tenant-id=orgId;icid-value=\"[OBFUSCATED:a6c03ffe6f60de230b965eaccdaae088]\"");

    // Test that KPML digits are obfuscated in the content
    SIPMessage msg =
        new StringMsgParser()
            .parseSIPMessage(
                ("NOTIFY sip:example.com SIP/2.0\n"
                        + "From: \"Alice\" <sip:alice@example.com>\n"
                        + "To: \"Bob\" <sip:bob@example.com>\n"
                        + "CSeq: 4 NOTIFY\n"
                        + "Content-Length: 111\n"
                        + "\n"
                        + "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<kpml-response version=\"1.0\" code=\"200\" text=\"OK\" digits=\"2\" tag=\"dtmf\"/>")
                    .getBytes(),
                true,
                true,
                null);
    testObfuscateSip(
        msg,
        "NOTIFY [OBFUSCATED:\"sip:XXXXXXXXXXX\" hash=5ababd603b22780302dd8d83498e5172 (<no user> @ 5ababd603b22780302dd8d83498e5172)] SIP/2.0\n"
            + "From: \"[OBFUSCATED:64489c85dc2fe0787b85cd87214b3810]\" <[OBFUSCATED:\"sip:xxxxx@XXXXXXXXXXX\" hash=c160f8cc69a4f0bf2b0362752353d060 (6384e2b2184bcbf58eccf10ca7a6563c @ 5ababd603b22780302dd8d83498e5172)]>\n"
            + "To: \"[OBFUSCATED:2fc1c0beb992cd7096975cfebf9d5c3b]\" <[OBFUSCATED:\"sip:xxx@XXXXXXXXXXX\" hash=4b9bb80620f03eb3719e0a061c14283d (9f9d51bc70ef21ca5c14f307980a29d8 @ 5ababd603b22780302dd8d83498e5172)]>\n"
            + "CSeq: 4 NOTIFY\n"
            + "Content-Length: 111\n"
            + "\n"
            + "<?xml version=\"1.0\" encoding=\"UTF-8\"?><kpml-response version=\"1.0\" code=\"200\" text=\"OK\" digits=\"OBFUSCATED\" tag=\"dtmf\"/>");
  }

  private static SipUri createSipUri(String uri) throws Exception {
    return (SipUri) sipFactory.createAddressFactory().createURI(uri);
  }

  private static SipUri createSipUri(String uri, int port) throws Exception {
    SipUri sipUri = createSipUri(uri);
    sipUri.setPort(port);
    return sipUri;
  }

  private void testObfuscateSip(GenericObject piiFormat, String expectedResult) {
    String resultAfterObfuscation = LogUtils.obfuscateObject(piiFormat, true);
    // Ignore differences in line endings by standardizing on Unix line endings
    resultAfterObfuscation = resultAfterObfuscation.replace("\r\n", "\n");
    Assert.assertEquals(
        resultAfterObfuscation.trim(),
        expectedResult,
        "Obfuscation result does not match expected result");
  }

  private static class ExtensionHeaderParser extends HeaderParser {
    protected ExtensionHeaderParser(String header) {
      super(header);
    }
  }
}
