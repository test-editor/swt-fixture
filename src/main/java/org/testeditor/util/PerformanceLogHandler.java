/*******************************************************************************
 * Copyright (c) 2012 - 2015 Signal Iduna Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Signal Iduna Corporation - initial API and implementation
 * akquinet AG
 *******************************************************************************/
/**
 * 
 */
package org.testeditor.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.jamonapi.MonitorFactory;

/**
 * TODO: Add a class description!
 * 
 */
public class PerformanceLogHandler {

	private static final Logger LOGGER = Logger.getLogger(PerformanceLogHandler.class);

	File plotInput = new File("plotInput.xml");
	File jmonFile = new File("jamonReport.html");

	private Document doc;
	private Element rootElement;

	/**
	 * 
	 */
	public List<String> getTestCases() {

		List<String> testCases = new ArrayList<String>();

		NodeList nList = doc.getElementsByTagName("testcase");

		for (int temp = 0; temp < nList.getLength(); temp++) {

			Node nNode = nList.item(temp);

			if (nNode.getNodeType() == Node.ELEMENT_NODE) {

				Element eElement = (Element) nNode;

				String testcase = "Testcase: " + nNode.getNodeName() + " Name : " + eElement.getAttribute("name")
						+ " Time : " + eElement.getAttribute("time");
				testCases.add(testcase);

			}
		}

		return testCases;

	}

	/**
	 * @param fileName2
	 */
	public void removeFile() {

		plotInput.delete();
	}

	/**
	 * @throws ParserConfigurationException
	 * @throws IOException
	 * @throws SAXException
	 * 
	 */
	public void loadFile() throws ParserConfigurationException, SAXException, IOException {

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		doc = dBuilder.parse(plotInput);

		rootElement = doc.getDocumentElement();

	}

	public void createSuite() throws ParserConfigurationException, TransformerException {

		DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

		// root elements
		doc = docBuilder.newDocument();
		rootElement = doc.createElement("testsuite");
		doc.appendChild(rootElement);

	}

	/**
	 * @param doc
	 * @throws TransformerFactoryConfigurationError
	 * @throws TransformerConfigurationException
	 * @throws TransformerException
	 */
	public void writeFile() throws TransformerFactoryConfigurationError, TransformerConfigurationException,
			TransformerException {
		// write the content into xml file
		TransformerFactory transformerFactory = TransformerFactory.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(doc);
		StreamResult result = new StreamResult(plotInput);

		// Output to console for testing
		// StreamResult result = new StreamResult(System.out);

		transformer.transform(source, result);
	}

	/**
	 * @param doc
	 * @param rootElement
	 */
	public void addTestcase(String name, String time) {
		// after testcase is running
		// staff elements
		Element testcase = doc.createElement("testcase");
		testcase.setAttribute("time", time);
		testcase.setAttribute("name", name);
		rootElement.appendChild(testcase);
	}

	/**
	 * @param file
	 */
	public void setFile(File file) {
		this.plotInput = file;
	}

	/**
	 * 
	 */
	private void writeJamonFile(String testName) {

		if (!jmonFile.exists()) {
			createAndInsertToJamonFile(testName);
		} else {
			addToJamonFile(testName);
		}

	}

	/**
	 * @param testName
	 */
	private void addToJamonFile(String testName) {
		try {
			String report = MonitorFactory.getRootMonitor().getReport();

			FileWriter jmonFileWriter = new FileWriter(jmonFile, true);

			jmonFileWriter.write("<h1>" + testName + "</h1><br>");

			jmonFileWriter.write(report);

			jmonFileWriter.flush();
			jmonFileWriter.close();
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	/**
	 * 
	 */
	private void createAndInsertToJamonFile(String testName) {
		try {
			String report = MonitorFactory.getRootMonitor().getReport();

			FileWriter jmonFileWriter = new FileWriter(jmonFile);

			String jmonFileAsHtmlString = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">"
					+ "<html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=windows-1250\">"
					+ "<meta name=\"generator\" content=\"PSPad editor, www.pspad.com\">"
					+ "<title></title></head><body>";

			jmonFileWriter.write("<h1>" + testName + "</h1><br>");
			jmonFileWriter.write(jmonFileAsHtmlString);

			jmonFileWriter.write(report);

			jmonFileWriter.write("</body></html>");

			jmonFileWriter.flush();
			jmonFileWriter.close();
		} catch (IOException e) {

			e.printStackTrace();
		}

	}

	/**
	 * @return
	 */
	public boolean fileExists() {

		if (plotInput != null) {

			return plotInput.exists();
		}

		return false;
	}

	/**
	 * Writes 2 type of files in current workspace for <br>
	 * <b>jamonReport.html</b> file : analyzes performance measure of runnig
	 * test. <br>
	 * <b>plotInput.xml</b> : based on jamonReport.html contains total runtime
	 * of each test. Is needed for displaying a chart on a Jenkins server within
	 * the plot plugin.
	 * 
	 * @param testcaseName
	 * 
	 */
	public void logPerformanceData(String testcaseName) {

		try {
			// 1. write jamon file
			writeJamonFile(testcaseName);

			// 2. write plot file
			if (!fileExists()) {
				createSuite();
			} else {
				loadFile();
			}

			double time = MonitorFactory.getRootMonitor().getTotal();

			addTestcase(testcaseName, Double.toString(time));

			writeFile();

		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		} finally {
			MonitorFactory.reset();
		}

	}

	/**
	 * Formats a given argument for logging. This method is intended to handle
	 * arrays as theses would otherwise be logged as something like
	 * "[Ljava.lang.String;@62a98a".
	 * 
	 * @param arg
	 *            the argument to be formatted
	 * @return a string containing the formatted argument.
	 */
	public static String formatArg(Object arg) {
		Object argToAppend;
		if (arg != null && arg.getClass().isArray()) {
			Object[] argArray = (Object[]) arg;
			argToAppend = Arrays.deepToString(argArray);
		} else {
			argToAppend = arg;
		}
		return "\"" + argToAppend + "\"";
	}

	/**
	 * Creates the Log-Message.
	 * 
	 * @param method
	 *            fixture method.
	 * @param convertedArgs
	 *            arguments of fixte method.
	 * @return created log message.
	 */
	public static String getLabel(Method method, Object... convertedArgs) {

		StringBuilder argumentListAsStr = new StringBuilder();

		for (int i = 0; i < convertedArgs.length; i++) {

			argumentListAsStr.append(formatArg(convertedArgs[i]));

			if (i < (convertedArgs.length - 1)) {
				argumentListAsStr.append(", ");
			}
		}

		return method.getName() + " ( " + argumentListAsStr.toString() + " )";

	}

}