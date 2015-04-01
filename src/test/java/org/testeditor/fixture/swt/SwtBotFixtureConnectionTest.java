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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import org.junit.Test;
import org.testeditor.fixture.core.exceptions.StopTestException;

/**
 * A class with a main-method to test the connection to the Test-Editor under
 * test.
 */
public final class SwtBotFixtureConnectionTest {

	// /**
	// * Private-Constructor.
	// */
	// private SwtBotFixtureConnectionTest() {
	// // do nothing
	// }

	/**
	 * 
	 * 
	 * @param args
	 *            String[]
	 * @throws Exception
	 *             , if connection to the Test-Editor under test fails.
	 */
	public static void main(String[] args) throws Exception {

		SwtBotFixture swtBotFixture = new SwtBotFixture();

		swtBotFixture.setTestName("MyTest");

		/**
		 * This test creates a new testcase with the Test-Editor under test.
		 * Using language = german.
		 */

		// should be set to the correct value
		String pathToTestEditorUnderTest = "d:\\Development\\TestEditor_AKTUELL\\testeditor.exe ";
		String bundleDir = "d:\\Development\\TestEditor_AKTUELL\\plugins";
		String wsPath = "d:\\Development\\.testeditor_aut ";
		swtBotFixture
				.setElementlist("d:\\Development\\TestEditor_AKTUELL\\.testeditor\\TestEditorTests\\ElementList.conf");

		swtBotFixture.startJUnitApplication(pathToTestEditorUnderTest, wsPath, bundleDir);

		/**
		 * Starts the wizzard "Neuer Testfall" in the project DemoWebTests and
		 * creates a new testcase.
		 */

		long currentTimeMillis = System.currentTimeMillis();
		swtBotFixture.expandTreeItems("DemoWebTests,LocalDemoSuite,LoginSuite");
		swtBotFixture.clickContextMenu("Neuer Testfall");
		swtBotFixture.setTextById("new.test.page.name", "MyFirstTest" + currentTimeMillis);
		swtBotFixture.clickButton("TEXT::Fertigstellen");

		/**
		 * Adds a description
		 */

		swtBotFixture.setTextById("create.description.text", "Dies ist ein sehr langer Text");
		swtBotFixture.clickButton("REGEX::.*Hin.*en");
		swtBotFixture.waitSeconds("2");

		/**
		 * add a new step in the testcase. Start browser Firefox.
		 */
		swtBotFixture.selectComboBoxWithId("chose.maske", "Allgemein Browser");
		swtBotFixture.selectComboBoxWithId("chose.action", "Browser starten");
		swtBotFixture.selectComboBoxWithId("action.line.combo1", "Firefox");
		swtBotFixture.clickButton("ID::add.new.action");

		swtBotFixture.waitSeconds("2");

		swtBotFixture.selectComboBoxWithId("chose.maske", "Lokale Anmeldung");
		swtBotFixture.selectComboBoxWithId("chose.action", "Wert eingeben");

		swtBotFixture.analyzeWidgets();

		swtBotFixture.selectComboBoxWithId("action.line.combo1", "Name");
		swtBotFixture.setTextById("action.line.text3", "max");
		swtBotFixture.clickButton("ID::add.new.action");

		swtBotFixture.selectComboBoxWithId("chose.maske", "Anmeldung");
		swtBotFixture.selectComboBoxWithId("chose.action", "Wert eingeben");

		swtBotFixture.analyzeWidgets();

		swtBotFixture.selectComboBoxWithId("action.line.combo1", "Passwort");
		swtBotFixture.setTextById("action.line.text3", "xyz");
		swtBotFixture.clickButton("ID::add.new.action");

		swtBotFixture.selectComboBoxWithId("chose.maske", "Allgemein Browser");
		swtBotFixture.selectComboBoxWithId("chose.action", "Browser beenden");
		swtBotFixture.clickButton("ID::add.new.action");

		swtBotFixture.clickMenuByName("Datei");
		swtBotFixture.clickMenuByName("Speichern");

		swtBotFixture.expandTreeItems("DemoWebTests,LocalDemoSuite,LoginSuite," + "MyFirstTest" + currentTimeMillis);
		swtBotFixture.clickContextMenu("view.testExplorer.showHistory");
		swtBotFixture.compareLabelById("view.testHistory.label", "Testhistorie von: MyDemoTest");

		swtBotFixture.clickContextMenu(".*Eintrag.*l.*schen");
		swtBotFixture.clickButton("TEXT::OK");

		swtBotFixture.waitSeconds("3");

		swtBotFixture.clickMenuByName("Datei");
		swtBotFixture.clickMenuByName("Beenden");
		swtBotFixture.clickButton("TEXT::OK");

		swtBotFixture.waitSeconds("2");
		swtBotFixture.writePerformanceLog();
		swtBotFixture.stopApplication();

	}

	@Test
	public void parametherInFileTest() throws Exception {
		String content = "Line1: foo, Bar \r\n" + "Line2: This is a secon Line \r\n" + "And a Third one";
		String fileName = "content.txt";

		System.setProperty("aut.workspace.path", System.getProperty("user.home") + File.separator
				+ "/.testeditor_jUnit_tests");
		String workspacePath = System.getProperty("aut.workspace.path");
		File wsPathFile = new File(workspacePath);
		Path wsPath = wsPathFile.toPath();
		if (wsPathFile.exists()) {
			Files.walkFileTree(wsPath, getDeleteFileVisitor());
		}
		Path testFolder = new File(wsPath.toString() + File.separator + "Test" + File.separator + "FitNesseRoot"
				+ File.separator + "Test").toPath();
		Files.createDirectories(testFolder);
		FileWriter fw = new FileWriter(new File(testFolder + File.separator + fileName));
		fw.write(content);
		fw.close();

		SwtBotFixture swtBotFixture = new SwtBotFixture();
		assertThat(swtBotFixture.checkTextInCodeLine("Test", "foo", 1), is(true));

	}

	@Test
	public void parametherNotInFileTest() throws Exception {
		String content = "Line1: foo, Bar \r\n" + "Line2: This is a secon Line \r\n" + "And a Third one";
		String fileName = "content.txt";

		System.setProperty("aut.workspace.path", System.getProperty("user.home") + File.separator
				+ "/.testeditor_jUnit_tests");
		String workspacePath = System.getProperty("aut.workspace.path");
		File wsPathFile = new File(workspacePath);
		Path wsPath = wsPathFile.toPath();
		if (wsPathFile.exists()) {
			Files.walkFileTree(wsPath, getDeleteFileVisitor());
		}
		Path testFolder = new File(wsPath.toString() + File.separator + "Test" + File.separator + "FitNesseRoot"
				+ File.separator + "Test").toPath();
		Files.createDirectories(testFolder);
		FileWriter fw = new FileWriter(new File(testFolder + File.separator + fileName));
		fw.write(content);
		fw.close();

		SwtBotFixture swtBotFixture = new SwtBotFixture();
		assertThat(swtBotFixture.checkTextInCodeLine("Test", "test", 1), is(false));

	}

	@Test(expected = StopTestException.class)
	public void parametherInFileTestWorkspaceEmpty() throws Exception {
		SwtBotFixture swtBotFixture = new SwtBotFixture();
		System.setProperty("aut.workspace.path", "");
		swtBotFixture.checkTextInCodeLine("testContet.txt", "foo", 1);

	}

	/**
	 * 
	 * @return utility FileVisitor class for recursive directory delete
	 *         operations.
	 */
	private FileVisitor<Path> getDeleteFileVisitor() {
		return new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		};
	}
}