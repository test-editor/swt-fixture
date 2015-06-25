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
package org.testeditor.fixture.swt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.junit.Before;
import org.junit.Test;
import org.testeditor.util.PerformanceLogHandler;
import org.xml.sax.SAXException;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

/**
 * Testclass for {@link PerformanceLogHandler}.
 * 
 */

public class PerformanceLogHandlerTest {

	/**
	 * SetUp Method.
	 */
	@Before
	public void init() {
		new File("plotInput.xml").delete();
		new File("jamonReport.html").delete();
	}

	/**
	 * 
	 * @throws ParserConfigurationException
	 * @throws TransformerException
	 * @throws SAXException
	 * @throws IOException
	 */
	@Test
	public void plotFileForNonExistingFile() {

		PerformanceLogHandler plotFileUtil = new PerformanceLogHandler();

		try {
			plotFileUtil.setFile(new File("src/test/resources/plotInput.xml"));

			plotFileUtil.removeFile();

			plotFileUtil.createSuite();

			plotFileUtil.addTestcase("Testfall 1", "800");
			plotFileUtil.writeFile();

			plotFileUtil.loadFile();
			assertTrue(true);
		} catch (Exception e) {
			assertFalse(true);
		}
		plotFileUtil.removeFile();
	}

	/**
	 * Tests the plot file if exists.
	 * 
	 */
	@Test
	public void plotFileExistingFile() {

		PerformanceLogHandler plotFileUtil = new PerformanceLogHandler();

		try {

			new File("src/test/resources/plotInput2.xml").delete();

			Files.copy(Paths.get("src/test/resources/plotInputTemplate.xml"),
					Paths.get("src/test/resources/plotInput2.xml"));

			plotFileUtil.setFile(new File("src/test/resources/plotInput2.xml"));

			plotFileUtil.loadFile();

			plotFileUtil.addTestcase("Testfall 2", "2000");
			plotFileUtil.addTestcase("Testfall 3", "5000");
			plotFileUtil.writeFile();

			List<String> testCases = plotFileUtil.getTestCases();

			assertEquals(3, testCases.size());

			plotFileUtil.removeFile();

		} catch (Exception e) {
			assertFalse(e.getMessage(), true);
		}

	}

	/**
	 * 
	 */
	@Test
	public void plotfileExisting() {

		PerformanceLogHandler plotFileUtil = new PerformanceLogHandler();

		plotFileUtil.setFile(new File("src/test/resources/xxxxx.xml"));
		assertTrue(!plotFileUtil.fileExists());

		plotFileUtil.setFile(new File("src/test/resources/plotInputTemplate.xml"));
		assertTrue(plotFileUtil.fileExists());

	}

	/**
	 * creates dummy performance data.
	 * 
	 * @param label
	 *            label.
	 */
	private void createsSomeDummyJamonData(String label) {

		try {
			for (int i = 1; i <= 10; i++) {

				Monitor mon = MonitorFactory.start(label + i);

				Thread.sleep(10 + new Random().nextInt(100));

				mon.stop();

			}
		} catch (Exception e) {
			assertFalse(e.getMessage(), true);
		}

	}

	/**
	 * logs performance data for only one test.
	 */
	@Test
	public void logPerformanceDataForOneTest() {

		try {

			PerformanceLogHandler handler = new PerformanceLogHandler();

			// create some jamon files
			createsSomeDummyJamonData("labelX");
			String testName = "Testcase 1";
			handler.logPerformanceData(testName);

			assertTrue(new File("plotInput.xml").exists());
			assertTrue(new File("jamonReport.html").exists());

		} catch (Exception e) {
			assertFalse(e.getMessage(), true);
		}

	}

	/**
	 * 
	 * @throws ParserConfigurationException
	 * @throws TransformerException
	 * @throws SAXException
	 * @throws IOException
	 */
	@Test
	public void logPerformanceDataForSuite() {

		try {

			PerformanceLogHandler handler = new PerformanceLogHandler();

			// Testcase 1
			createsSomeDummyJamonData("labelX");
			String testName = "Testcase 1";
			handler.logPerformanceData(testName);

			// Testcase 2
			createsSomeDummyJamonData("labelY");
			testName = "Testcase 2";
			handler.logPerformanceData(testName);

			// Testcase 3
			createsSomeDummyJamonData("labelC");
			testName = "Testcase 3";
			handler.logPerformanceData(testName);

			assertTrue(new File("plotInput.xml").exists());
			assertTrue(new File("jamonReport.html").exists());

		} catch (Exception e) {
			assertFalse(e.getMessage(), true);
		}

	}

}