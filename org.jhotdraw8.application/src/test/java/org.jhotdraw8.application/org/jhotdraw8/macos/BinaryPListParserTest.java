/*
 * @(#)BinaryPListParserTest.java
 * Copyright © 2022 The authors and contributors of JHotDraw. MIT License.
 */

package org.jhotdraw8.macos;

import org.jhotdraw8.annotation.NonNull;
import org.jhotdraw8.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinaryPListParserTest {
    private static final Properties INDENT_XML_PROPERTIES = new Properties();
    
    public BinaryPListParserTest() {
    	
    }
    
    static {
        INDENT_XML_PROPERTIES.put(OutputKeys.INDENT, "yes");
        INDENT_XML_PROPERTIES.put(OutputKeys.ENCODING, "UTF-8");
        INDENT_XML_PROPERTIES.put("{http://xml.apache.org/xslt}indent-amount", "2");
    }

    private static final Properties NO_INDENT_XML_PROPERTIES = new Properties();

    static {
        NO_INDENT_XML_PROPERTIES.put(OutputKeys.ENCODING, "UTF-8");
    }

    /**
     * Tests a small property plist where the binary presentation has
     * less than 256 objects. In this case objects are encoded in
     * a byte array.
     *
     * @throws Exception on failure
     */
    @Test
    public void testSmallPropertyList() throws Exception {
        File xmlFile = new File(getClass().getResource("SmallXmlPropertyList.plist").toURI());
        final Document docFromXml = readXmlPropertyList(xmlFile);
        File binaryFile = new File(getClass().getResource("SmallBinaryPropertyList.plist").toURI());
        final Document docFromBinary = readBinaryPropertyList(binaryFile);
        assertEquals(docFromXml, docFromXml);
        // writeDocument(System.out, docFromXml, NO_INDENT_XML_PROPERTIES);
        // System.out.println();
        // writeDocument(System.out, docFromBinary, INDENT_XML_PROPERTIES);
    }

    /**
     * Tests a small property plist where the binary presentation has
     * exactly 256 objects.  In this case objects are encoded in
     * a short array.
     *
     * @throws Exception on failure
     */
    @Test
    public void testLargePropertyList() throws Exception {
        File xmlFile = new File(getClass().getResource("LargeXmlPropertyList.plist").toURI());
        final Document docFromXml = readXmlPropertyList(xmlFile);
        File binaryFile = new File(getClass().getResource("LargeBinaryPropertyList.plist").toURI());
        final Document docFromBinary = readBinaryPropertyList(binaryFile);
        assertEquals(docFromXml, docFromXml);
        // writeDocument(System.out, docFromXml, NO_INDENT_XML_PROPERTIES);
        // System.out.println();
        // writeDocument(System.out, docFromBinary, INDENT_XML_PROPERTIES);
    }

    private static Document readXmlPropertyList(@NonNull File file) throws Exception {
        InputSource inputSource = new InputSource(file.toString());
        assertTrue(file.exists(), "file does not exist, file=" + file);
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        builderFactory.setNamespaceAware(true);
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        // We do not want that the reader creates a socket connection!
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        Document doc = builder.parse(inputSource);
        return doc;
    }

    private static Document readBinaryPropertyList(@NonNull File file) throws Exception {
        return new BinaryPListParser().parse(file);
    }

    private static void writeDocument(OutputStream writer, Document doc, @Nullable Properties outputProperties) throws Exception {
        StreamResult result = new StreamResult(writer);
        writeDocument(result, doc, outputProperties);
    }

    private static void writeDocument(StreamResult result, Document doc, @Nullable Properties outputProperties) throws Exception {
        final TransformerFactory factory = TransformerFactory.newInstance();
        Transformer t = factory.newTransformer();
        if (outputProperties != null) {
            t.setOutputProperties(outputProperties);
        }
        DOMSource source = new DOMSource(doc);
        t.transform(source, result);
    }

    private static Document normalizeWhitespace(final Document doc, final boolean stripComments) {
        normalizeWhitespace((Node) doc, stripComments);
        return doc;
    }

    private static void normalizeWhitespace(final @NonNull Node node, final boolean stripComments) {
        final NodeList childNodes = node.getChildNodes();
        final List<Node> list = new ArrayList<>();
        // TODO: Remove too many control variables in the 'for' statement, FIXME: Modify the statement to suit for loop.
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            list.add(childNodes.item(i));
        }
        boolean deleteEmptyLine = false;
        for (final Node item : list) {
            if (item instanceof Element) {
                normalizeWhitespace(item, stripComments);
                deleteEmptyLine = false;
            } else if (item instanceof Comment) {
                if (stripComments) {
                    node.removeChild(item);
                }
                deleteEmptyLine = true;
            } else if (item instanceof Text) {
                final Text text = (Text) item;
                if (text.getTextContent().trim().isEmpty()) {
                    if (deleteEmptyLine) {
                        node.removeChild(item);
                    } else {
                        text.setTextContent("\n");
                    }
                    deleteEmptyLine = true;
                }
            }
        }
    }

}