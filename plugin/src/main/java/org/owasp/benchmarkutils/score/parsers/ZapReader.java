/**
 * OWASP Benchmark Project
 *
 * <p>This file is part of the Open Web Application Security Project (OWASP) Benchmark Project For
 * details, please see <a
 * href="https://owasp.org/www-project-benchmark/">https://owasp.org/www-project-benchmark/</a>.
 *
 * <p>The OWASP Benchmark is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation, version 2.
 *
 * <p>The OWASP Benchmark is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE. See the GNU General Public License for more details
 *
 * @author Dave Wichers
 * @created 2015
 */
package org.owasp.benchmarkutils.score.parsers;

import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.owasp.benchmarkutils.score.ResultFile;
import org.owasp.benchmarkutils.score.TestCaseResult;
import org.owasp.benchmarkutils.score.TestSuiteResults;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

public class ZapReader extends Reader {

    @Override
    public boolean canRead(ResultFile resultFile) {
        return resultFile.filename().endsWith(".xml")
                && (resultFile.line(0).contains("<OWASPZAPReport")
                        || (resultFile.filename().endsWith(".xml")
                                && resultFile.line(1).contains("<OWASPZAPReport")));
    }

    @Override
    public TestSuiteResults parse(ResultFile resultFile) throws Exception {
        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        // Prevent XXE
        docBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(resultFile.content()));
        Document doc = docBuilder.parse(is);

        TestSuiteResults tr =
                new TestSuiteResults("OWASP ZAP", false, TestSuiteResults.ToolType.DAST);

        // If the filename includes an elapsed time in seconds (e.g., TOOLNAME-seconds.xml), set the
        // compute time on the score card.
        tr.setTime(resultFile.file());

        Node zap = doc.getDocumentElement();
        String version = getAttributeValue("version", zap);
        tr.setToolVersion(version);

        List<Node> siteList = getNamedChildren("site", zap);
        List<Node> alertsList = getNamedChildren("alerts", siteList);
        List<Node> issueList = getNamedChildren("alertitem", alertsList);

        for (Node flaw : issueList) {
            try {
                parseAndAddZapIssues(flaw, tr);
            } catch (Exception e) {
                // print and continue
                e.printStackTrace();
            }
        }
        return tr;
    }

    //    <OWASPZAPReport generated="Thu, 2 Jul 2015 15:59:49" version="2.4.0">
    //    <site host="localhost" name="http://localhost:8080" port="8080" ssl="false">
    //
    //    <alerts>
    //
    //    <alertitem>
    //      <pluginid>10016</pluginid>
    //      <alert>Web Browser XSS Protection Not Enabled</alert>
    //      <riskcode>1</riskcode>
    //      <confidence>2</confidence>
    //      <riskdesc>Low (Medium)</riskdesc>
    //      <desc>Web Browser XSS Protection is not enabled, or is disabled by the configuration of
    // the 'X-XSS-Protection' HTTP response header on the web server
    //        </desc>

    //    <uri>http://localhost:8080/benchmark/BenchmarkTest00028.html</uri>
    //      <param/>
    //      <attack/>
    // OR, for merged reports:
    //      <instances>
    //        <instance>
    //          <uri>http://localhost:8080/benchmark/BenchmarkTest00028.html</uri>
    //          <param/>
    //          <attack/>
    //        </instance>
    //        <!-- more "instance" elements per merged alert -->
    //      </instances>

    //      <otherinfo>The X-XSS-Protection HTTP response header allows the web server to enable or
    // disable the web browser's XSS protection mechanism. The following values would attempt to
    // enable it:
    //      <solution>Ensure that the web browser's XSS filter is enabled, by setting the
    // X-XSS-Protection HTTP response header to '1'.
    //        </solution>
    //      <otherinfo>The X-XSS-Protection HTTP response header allows the web server to enable or
    // disable the web browser's XSS protection mechanism. The following values would attempt to
    // enable it:
    //        X-XSS-Protection: 1; mode=block
    //        </otherinfo>
    //      <reference>https://owasp.org/XSS_(Cross_Site_Scripting)_Prevention_Cheat_Sheet
    //        https://blog.veracode.com/2014/03/guidelines-for-setting-security-headers/
    //        </reference>
    //      <cweid>933</cweid>
    //      <wascid>14</wascid>
    //    </alertitem>

    private void parseAndAddZapIssues(Node flaw, TestSuiteResults tr) throws URISyntaxException {
        int cwe = -1;
        Node rule = getNamedChild("cweid", flaw);
        if (rule != null) {
            cwe = cweLookup(rule.getTextContent());
        }

        String cat = getNamedChild("alert", flaw).getTextContent();

        int conf = Integer.parseInt(getNamedChild("confidence", flaw).getTextContent());

        Node instances = getNamedChild("instances", flaw);
        if (instances == null) {
            addIssue(flaw, tr, cwe, cat, conf);
            return;
        }

        List<Node> instanceList = getNamedChildren("instance", instances);
        for (Node instance : instanceList) {
            addIssue(instance, tr, cwe, cat, conf);
        }
    }

    private void addIssue(
            Node alertData, TestSuiteResults tr, int cwe, String category, int confidence) {
        int testNumber = testNumber(getNamedChild("uri", alertData).getTextContent());
        tr.put(createTestCaseResult(cwe, category, confidence, testNumber));
    }

    private TestCaseResult createTestCaseResult(
            int cwe, String category, int confidence, int testNumber) {
        TestCaseResult tcr = new TestCaseResult();
        if (cwe != -1) {
            tcr.setCWE(cwe);
        }
        tcr.setCategory(category);
        tcr.setEvidence(category);
        tcr.setConfidence(confidence);
        tcr.setNumber(testNumber);
        return tcr;
    }

    private int cweLookup(String orig) {
        return ZapJsonReader.mapCwe(orig);
    }
}
