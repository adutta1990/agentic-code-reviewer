package com.ai.agents.sandbox;

import com.ai.agents.sandbox.SandboxResult.TestStatus;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads Surefire's {@code TEST-*.xml} reports into a per-test status map. Machine-readable and
 * authoritative — far more reliable than scraping console output — and depends only on the JDK's
 * XML parser, so it adds no dependency. The parse itself is pure given a directory, so it is
 * unit-testable against a fixture directory with no build in the loop.
 */
@Component
public class SurefireReportParser {

    /** Parse every report in {@code reportsDir} into {@code classname#method -> status}. */
    public Map<String, TestStatus> parse(Path reportsDir) throws IOException {
        Map<String, TestStatus> results = new LinkedHashMap<>();
        if (reportsDir == null || !Files.isDirectory(reportsDir)) {
            return results;
        }
        DocumentBuilder builder = newSecureBuilder();
        try (Stream<Path> files = Files.list(reportsDir)) {
            List<Path> reports = files
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("TEST-") && n.endsWith(".xml");
                    })
                    .toList();
            for (Path report : reports) {
                parseInto(builder, report, results);
            }
        }
        return results;
    }

    private void parseInto(DocumentBuilder builder, Path report, Map<String, TestStatus> out) throws IOException {
        try {
            Document doc = builder.parse(report.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element c = (Element) cases.item(i);
                String classname = c.getAttribute("classname");
                String name = c.getAttribute("name");
                String id = classname + "#" + name;
                out.put(id, statusOf(c));
            }
        } catch (org.xml.sax.SAXException e) {
            throw new IOException("Malformed surefire report: " + report, e);
        } finally {
            builder.reset();
        }
    }

    private static TestStatus statusOf(Element testcase) {
        if (testcase.getElementsByTagName("failure").getLength() > 0) {
            return TestStatus.FAILED;
        }
        if (testcase.getElementsByTagName("error").getLength() > 0) {
            return TestStatus.ERROR;
        }
        if (testcase.getElementsByTagName("skipped").getLength() > 0) {
            return TestStatus.SKIPPED;
        }
        return TestStatus.PASSED;
    }

    private static DocumentBuilder newSecureBuilder() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Surefire reports are local build artifacts, but disable external entities anyway.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw new IOException("Cannot create XML parser", e);
        }
    }
}
