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
package org.testeditor.fixture.swt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.testeditor.fixture.core.elementlist.ElementListService;
import org.testeditor.fixture.core.exceptions.ElementKeyNotFoundException;
import org.testeditor.fixture.core.exceptions.StopTestException;
import org.testeditor.fixture.core.interaction.Fixture;
import org.testeditor.fixture.core.interaction.StoppableFixture;
import org.testeditor.util.PerformanceLogHandler;

import com.jamonapi.Monitor;
import com.jamonapi.MonitorFactory;

/**
 * Fixture for communication via socket with swtbot agent.
 * 
 */
public class SwtBotFixture implements StoppableFixture, Fixture {
	private static final Logger LOGGER = Logger.getLogger(SwtBotFixture.class);
	private static final int AGENT_PORT = 9090;
	private static final String AGENT_HOST = "localhost";
	private static final String COMMAND_DELIMITER = ";";
	// Agent commands
	private static final String STOP_APPLICATION = "stop";
	private static final String CHARSET_UTF_8 = "UTF-8";

	private ElementListService elementListService;
	private Process process;
	private static boolean runningApp = false;
	private String testName;
	private Monitor javaMon;
	private List<String> launchApplicationCommandList;

	/**
	 * Creates the element list instance representing the GUI-Map for widget
	 * element id's of an application and the user defined names for this
	 * represented GUI element. Often used in a FitNesse ScenarioLibrary for
	 * configuration purpose. <br />
	 * 
	 * Usage for FitNesse: |set elementlist|../ElementList/content.txt|
	 * 
	 * @param elementList
	 *            relative path of the element list content.txt wiki site on a
	 *            FitNesse Server where WikiPages is the directory where all the
	 *            Wiki Sites of the recent project are
	 */
	public void setElementlist(String elementList) {
		this.elementListService = ElementListService.instanceFor(elementList);
	}

	/**
	 * Stops running AUT.
	 * 
	 */
	public void stopApplication() {
		try {
			Socket client = getSocket();
			PrintStream os = new PrintStream(client.getOutputStream(), false, CHARSET_UTF_8);
			os.println(STOP_APPLICATION);
			client.close();
			boolean appTerminated = false;
			while (!appTerminated) {
				try {
					process.exitValue();
					appTerminated = true;
					LOGGER.info("AUT terminated.");
				} catch (IllegalThreadStateException e) {
					LOGGER.info("AUT is shutting down...");
				}
				Thread.sleep(100);
			}

			writePerformanceLog();

			Runtime.getRuntime().addShutdownHook(addConfigCleaner());
		} catch (UnknownHostException e) {
			LOGGER.error("stopApplication UnknownHostException: ", e);
		} catch (IOException e) {
			LOGGER.error("stopApplication IOException ", e);
		} catch (InterruptedException e) {
			LOGGER.error("stopApplication ", e);
		} finally {
			if (process != null) {
				process.destroy();
			}
			markApplicationStopped();
		}
	}

	/**
	 * cleans the configuration of application_under_test.
	 * 
	 * @return the new thread
	 */
	private Thread addConfigCleaner() {
		return new Thread() {
			@Override
			public void run() {
				try {
					FileVisitor<Path> visitor = getDeleteFileVisitor();
					Path configPath = new File(System.getProperty("java.io.tmpdir") + File.separator + "configuration")
							.toPath();
					Files.walkFileTree(configPath, visitor);
					LOGGER.info("Cleaning up temporary configuration of the RCP AUT.");
				} catch (IOException e) {
					LOGGER.error("Error deleting temporary RCP config.", e);
				}

			}
		};
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
				file.toFile().setWritable(true);
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

	/**
	 * Expands nodes in tree. Can expand all nodes, their given a list of nodes.
	 * 
	 * e.g. expandTreeItems;node1,node2,nodeN...
	 * 
	 * @param nodeList
	 *            node1,node2,nodeN..
	 * @return the result of the message.
	 */
	public boolean expandTreeItems(String nodeList) {
		return sendMessage("expandTreeItems" + COMMAND_DELIMITER + nodeList);
	}

	/**
	 * Selects an Element ore more in a Table.
	 * 
	 * @param index
	 *            to be selected.
	 * @return the result of the message.
	 */
	public boolean selectTableAtIndex(String index) {
		return sendMessage("selectTableAtIndex" + COMMAND_DELIMITER + index);
	}

	/**
	 * clicks a menuitem of a contextmenu.
	 * 
	 * @param menuItemName
	 *            name of menuitem
	 * @return the result of the message.
	 */
	public boolean clickContextMenu(String menuItemName) {
		return sendMessage("clickContextMenu" + COMMAND_DELIMITER + getLocator(menuItemName));
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the text.
	 * @param inputText
	 *            string
	 * @return the result of the message.
	 */
	public boolean setTextById(String locator, String inputText) {
		return sendMessage("setTextById" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER + inputText);
	}

	/**
	 * 
	 * @param menuName
	 *            name of the menu
	 * @return the result of the message.
	 */
	public boolean clickMenuByName(String menuName) {
		return sendMessage("clickMenuByName" + COMMAND_DELIMITER + getLocator(menuName));
	}

	/**
	 * 
	 * @param id
	 *            of the menu-item
	 * @return the result of the message.
	 */
	public boolean clickMenuById(String id) {
		LOGGER.error("clickMenuById: " + getLocator(id));
		return sendMessage("clickMenuById" + COMMAND_DELIMITER + getLocator(id));

	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the button
	 * @return the result of the message.
	 */
	public boolean clickButton(String locator) {
		return sendMessage("clickButton" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * Starts the Application again. The workspace is not dropped and recreated.
	 * It used also the previous launch configuration of the
	 * 
	 * @return true if the new start of the application works.
	 */
	public boolean startApplicationAgain() {
		try {
			LOGGER.info("Start the application again. The last workspace is used.");
			waitUntilPreviousLaunchIsFinished();
			createAndLaunchProcess();
		} catch (Exception e) {
			LOGGER.error("Error Test execution: ", e);
			throw new StopTestException(e);
		}
		return true;
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the check box or the check box label
	 * @return the result of the message.
	 */
	public boolean clickCheckBox(String locator) {
		return sendMessage("clickCheckBox" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the check box
	 * @return the result of the message.
	 */
	public boolean isCheckBoxEnabled(String locator) {
		return sendMessage("isCheckBoxEnabled" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the check box
	 * @return the result of the message.
	 */
	public boolean isCheckBoxDisabled(String locator) {
		return !sendMessage("isCheckBoxEnabled" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the check box
	 * @return the result of the message.
	 */
	public boolean isCheckBoxChecked(String locator) {
		return sendMessage("isCheckBoxChecked" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the check box
	 * @return the result of the message.
	 */
	public boolean isCheckBoxNotChecked(String locator) {
		return !sendMessage("isCheckBoxChecked" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the button
	 * @return the result of the message.
	 */
	public boolean isButtonEnabled(String locator) {
		LOGGER.error("isButtonEnabled: " + locator);
		return sendMessage("isButtonEnabled" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * reads all projects in the tree and trace the names of them to the
	 * SWT.log.
	 * 
	 * @return the result of the message.
	 */
	public boolean readAllProjectsInTree() {
		return sendMessage("readAllProjectsInTree");
	}

	/**
	 * deletes all projects.
	 * 
	 * @return the result of the message.
	 */
	public boolean deleteAllProjects() {
		return sendMessage("deleteAllProjects");
	}

	/**
	 * compares the count of the projects with the parameter expectedCount.
	 * 
	 * @param expectedCount
	 *            the expected number of projects
	 * @return the result of the message.
	 */
	public boolean countProjectsEquals(String expectedCount) {
		return sendMessage("countProjectsEquals" + COMMAND_DELIMITER + expectedCount);
	}

	/**
	 * compares the number of children of the parent with the expectedCount.
	 * 
	 * @param parentName
	 *            as nodes from the root separated by comma
	 * @param expectedCount
	 *            the expected count of children
	 * @return the result of the message.
	 */
	public boolean countChildrenEquals(String parentName, String expectedCount) {
		return sendMessage("countChildrenEquals" + COMMAND_DELIMITER + parentName + COMMAND_DELIMITER + expectedCount);
	}

	/**
	 * Compare the count of a list with expected count.
	 * 
	 * @param locator
	 *            locator id of the widget with items.
	 * @param expectedCount
	 *            count of items in the widget.
	 * @return true if the amount of items equals the expectedCount.
	 */
	public boolean countItemsEquals(String locator, String expectedCount) {
		return sendMessage("countItemsEquals" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ expectedCount);
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the button
	 * @return the result of the message.
	 */
	public boolean isButtonDisabled(String locator) {
		LOGGER.error("isButtonDisabled: " + locator);
		return !sendMessage("isButtonEnabled" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * @param locator
	 *            used to identify the table.
	 * @param expectedRowNumber
	 *            number of row.
	 */
	public boolean checkRowNumberOfTable(String locator, String expectedRowNumber) {
		return sendMessage("checkRowNumberOfTable" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ expectedRowNumber);

	}

	/**
	 * sets playbacktime of SWTBot.
	 * 
	 * @param milliSeconds
	 *            time to wait in milliseconds
	 * @return the result of the message.
	 */
	public boolean setPlayBackTime(String milliSeconds) {
		// TODO return sendMessage("setStyledTextWithId" + COMMAND_DELIMITER +
		// milliSeconds);
		return false;
	}

	/**
	 * Waits for the given period of time before executing the next command.<br />
	 * 
	 * @param timeToWait
	 *            Time to wait in seconds
	 * @return always true to show inside FitNesse a positive result
	 */
	public boolean waitSeconds(String timeToWait) {
		waitTime(new Long(timeToWait) * 1000);
		return true;
	}

	/**
	 * Waits for the given period.
	 * 
	 * @param milliseconds
	 *            Time to wait in milliseconds
	 */
	private void waitTime(long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		} catch (InterruptedException e) {
			LOGGER.error(e.getMessage());
		}
	}

	/**
	 * 
	 * @param regEx
	 *            the regularexpression to identify the button
	 * @return the result of the message.
	 * 
	 */
	public boolean clickButtonByRegEx(String regEx) {
		return sendMessage("clickButtonByRegEx" + COMMAND_DELIMITER + regEx);
	}

	/**
	 * Checks if text is visible in any kind of widget (with function
	 * getText()).
	 * 
	 * @param text
	 *            Text to be found
	 * @return the result of the message
	 */
	public boolean checkTextForAllWidgets(String text) {
		LOGGER.error("checkTextForAllWidgets: " + text);
		return sendMessage("checkTextForAllWidgets" + COMMAND_DELIMITER + text);
	}

	/**
	 * compares the text in the text-field identified with the id to the
	 * comptext.
	 * 
	 * @param locator
	 *            identifier of text
	 * @param comptext
	 *            text to compare with the content in the text-field.
	 * @return the result of the message.
	 */
	public boolean compareTextById(String locator, String comptext) {
		return sendMessage("compareTextById" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER + comptext);
	}

	/**
	 * compares the text in the label identified with the id to the comptext.
	 * 
	 * @param locator
	 *            identifier of text
	 * @param comptext
	 *            text to compare with the content in the text-field.
	 * @return the result of the message.
	 */
	public boolean compareLabelById(String locator, String comptext) {
		return sendMessage("compareLabelById" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER + comptext);
	}

	/**
	 * compares the text in the label identified with the id to the comptext and
	 * returns true if the text is not in the label.
	 * 
	 * @param locator
	 *            identifier of text
	 * @param comptext
	 *            text to compare with the content in the text-field.
	 * @return the result of the message.
	 */
	public boolean compareLabelByIdTextNotInWidget(String locator, String comptext) {
		return !sendMessage("compareLabelById" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER + comptext);
	}

	/**
	 * sends the modificationKeys and the key.
	 * 
	 * @param locator
	 *            identifier of styled-text
	 * @param modificationKeys
	 *            the combination of SWT.ALT | SWT.CTRL | SWT.SHIFT |
	 *            SWT.COMMAND.
	 * @param key
	 *            the character
	 * @return true, after sending the keys
	 */
	public boolean pressShortcutWithModificationKeyOfStyledText(String locator, String modificationKeys, String key) {
		return sendMessage("pressShortcutWithModificationKeyOfStyledText" + COMMAND_DELIMITER + getLocator(locator)
				+ COMMAND_DELIMITER + getLocator(modificationKeys) + COMMAND_DELIMITER + key);
	}

	/**
	 * Presses the shortcut specified by the given keys.
	 * 
	 * @param locator
	 *            identifier of the StyledText
	 * @param key
	 *            the key to press as the string-representing of the
	 *            {@link Keystrokes}
	 * @return true, after sending the key
	 */
	public boolean pressShortcutOfStyledText(final String locator, final String key) {
		LOGGER.error("pressShortcutOfStyledText" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ getLocator(key));
		return sendMessage("pressShortcutOfStyledText" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ getLocator(key));
	}

	/**
	 * sends the modificationKeys and the key to the active window.
	 * 
	 * @param modificationKeys
	 *            the combination of SWT.ALT | SWT.CTRL | SWT.SHIFT |
	 *            SWT.COMMAND.
	 * @param key
	 *            the character
	 * @return true, after sending the keys
	 */
	public boolean pressGlobalShortcut(String modificationKeys, String key) {
		return sendMessage("pressGlobalShortcut" + COMMAND_DELIMITER + getLocator(modificationKeys) + COMMAND_DELIMITER
				+ key);
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the styled-text.
	 * @param lineNumber
	 *            linenumber in the text
	 * @return the result of the message.
	 */
	public boolean selectLineInText(String locator, String lineNumber) {
		return sendMessage("selectLineInText" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ lineNumber);
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the styled-text.
	 * @param contents
	 *            part of the line in styled-text to be marked
	 * @param position
	 *            start-position for the marked range
	 * @return the result of the message.
	 */
	public boolean setCursorInTextWithContentsAtPosition(String locator, String contents, String position) {
		return sendMessage("setCursorInTextWithContentsAtPosition" + COMMAND_DELIMITER + getLocator(locator)
				+ COMMAND_DELIMITER + contents + COMMAND_DELIMITER + position);
	}

	/**
	 * 
	 * @param locator
	 *            locator-id or key of the combobox.
	 * @param selectItemAsText
	 *            as string.
	 * @return the result of the message.
	 */
	public boolean selectComboBoxWithId(String locator, String selectItemAsText) {
		return sendMessage("selectComboBoxWithId" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ selectItemAsText);
	}

	/**
	 * sends the message clickToolbarButtonWithId.
	 * 
	 * @param locator
	 *            locator-id or key of the button
	 * @return the result of the message.
	 */
	public boolean clickToolbarButtonWithId(String locator) {
		return sendMessage("clickToolbarButtonWithId" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * 
	 * sends the message clickToolbarButtonWithTooltip.
	 * 
	 * @param locator
	 *            locator or key of the button
	 * @return the result of the message.
	 */
	public boolean clickToolbarButtonWithTooltip(String locator) {
		return sendMessage("clickToolbarButtonWithTooltip" + COMMAND_DELIMITER + getLocator(locator));
	}

	/**
	 * sends the message closeTabItemWithName.
	 * 
	 * @param name
	 *            name of the tab
	 * @return the result of the message.
	 */
	public boolean closeTabItemWithName(String name) {
		return sendMessage("closeTabItemWithName" + COMMAND_DELIMITER + getLocator(name));
	}

	/**
	 * sends the message analyzeWidgets.
	 * 
	 * @return the result of the message.
	 */
	public boolean analyzeWidgets() {
		return sendMessage("analyzeWidgets");
	}

	/**
	 * checks the visibility of a text given by the parameter.
	 * 
	 * @param text
	 *            the text to check
	 * @return true, if the text is visible, else false
	 */
	public boolean textIsVisible(String text) {
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("textIsVisible: " + text);
		}
		return sendMessage("textIsVisible" + COMMAND_DELIMITER + text);
	}

	/**
	 * checks the visibility of a text given by the parameter.
	 * 
	 * @param text
	 *            the text to check
	 * @return true, if the text is invisible, else false
	 */
	public boolean textIsInVisible(String text) {
		return !sendMessage("textIsVisible" + COMMAND_DELIMITER + text);
	}

	/**
	 * compares in a parameter given comptext with the text in styledText given
	 * by id.
	 * 
	 * @param id
	 *            String
	 * @param compText
	 *            String
	 * @return true, if the comptext is in the styledText.
	 */
	public boolean compareTextInStyledById(String id, String compText) {
		LOGGER.info("compareTextInStyledById " + id + " " + compText);
		return sendMessage("compareTextInStyledById" + COMMAND_DELIMITER + getLocator(id) + COMMAND_DELIMITER
				+ compText);
	}

	/**
	 * compares in a parameter given comptext with the text in styledText given.
	 * by id.
	 * 
	 * @param id
	 *            String
	 * @param compText
	 *            String
	 * @return true, if the comptext is not in the styledText.
	 */
	public boolean compareTextNotInStyledById(String id, String compText) {
		LOGGER.info("compareTextInStyledById " + id + " " + compText);
		return !sendMessage("compareTextInStyledById" + COMMAND_DELIMITER + getLocator(id) + COMMAND_DELIMITER
				+ compText);
	}

	/**
	 * checks that a text not exists in styled-text identified by the locator.
	 * 
	 * @param locator
	 *            locator or key of the styled-text
	 * @param text
	 *            the searched text
	 * @return true, if the text is found, else false
	 */
	public boolean checkTextNotExistInWidgets(String locator, String text) {
		return !sendMessage("checkTextExistInWidgets" + COMMAND_DELIMITER + getLocator(locator) + COMMAND_DELIMITER
				+ text);
	}

	/**
	 * @param message
	 *            the message as a string, that should send.
	 * @return the result of the call
	 * @throws UnknownHostException
	 * @throws IOException
	 */
	private boolean sendMessage(String message) {

		StringBuilder result = new StringBuilder();

		try {
			Socket client = getSocket();

			PrintStream os = new PrintStream(client.getOutputStream(), false, CHARSET_UTF_8);
			os.println(message);
			LOGGER.info("Send message to AUT:" + message);
			os.flush();
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), CHARSET_UTF_8));

			int c;
			while ((c = in.read()) != -1) {
				result.append((char) c);
			}
			String myMessage = result.toString();
			if (myMessage.indexOf("ERROR ") > -1) {
				LOGGER.error("Fails: " + myMessage);
				throw new RuntimeException("Message: " + message + " fails with: " + myMessage);
			}

			client.close();

		} catch (UnknownHostException e) {
			LOGGER.error("SendMessage Host not Found", e);
		} catch (IOException e) {
			LOGGER.error("Send Message IOException ", e);
		}
		if (result.toString().startsWith("true")) {
			return true;
		}
		return false;
	}

	/**
	 * This method searches for the FitNesse-server for the project. It searches
	 * on the FitNesse-server, that is running under the configured port, after
	 * the main page of project. If the main-page is found, than it returns
	 * true, else false.
	 * 
	 * @param port
	 *            of the fitNesseServer
	 * @param projectName
	 *            the name of the project.
	 * @return true if a FitNesse-server is for this project.
	 */
	public boolean isFitNesseProjectServerRunning(String port, String projectName) {

		HttpGet httpGet = new HttpGet(getFitnesseUrl(port) + projectName + "?search&searchString=" + projectName
				+ "&searchType=title");
		httpGet.setHeader("Content-Type", "application/json");

		String strOfWikiPages;

		try {
			DefaultHttpClient defaultHttpClient = new DefaultHttpClient();

			HttpResponse httpResponse = defaultHttpClient.execute(httpGet);
			strOfWikiPages = (new BasicResponseHandler()).handleResponse(httpResponse);
			String searchString = "<a href=\"" + projectName + "\">" + projectName + "</a>";
			if (strOfWikiPages.contains(searchString)) {
				LOGGER.trace("Server is running");
				return true;
			}
		} catch (Exception e) {

			LOGGER.error("No FitNesse found in: " + getFitnesseUrl(port) + projectName + "\n" + e.getMessage());
			return false;
		}
		return false;
	}

	/**
	 * Looks up the TestProjectConfiguration to build the Fitnesse URL.
	 * 
	 * @param port
	 *            the port of the project
	 * @return the url to the fitnesse server.
	 */
	private String getFitnesseUrl(String port) {
		StringBuilder sb = new StringBuilder();
		sb.append("http://localhost:").append(port).append("/");
		return sb.toString();
	}

	/**
	 * Starts the AUT, an Eclipse e4 RCP executable. The Config is read and
	 * extended with the SWTBot Agent of the TestEditor. This one launches the
	 * Application in a Testmode.
	 * 
	 * @param applicationPath
	 *            to the executable
	 * @throws Exception
	 *             on Test execution
	 */
	public void startApplication(String applicationPath) throws Exception {
		try {
			if (System.getProperty("aut.workspace.path") == null) {
				LOGGER.error("Workspace path <aut.workspace.path> for the aut is not set.");
			}
			waitUntilPreviousLaunchIsFinished();
			runningApp = true;
			prepareAUTWorkspace();
			if (!new File(applicationPath).exists()) {
				LOGGER.info("AUT not found at: " + applicationPath);
				throw new StopTestException("Executable of the AUT not found.");
			}
			LOGGER.info("AUT found.");

			LOGGER.info("java.class.path : " + System.getProperty("java.class.path"));

			String swtBotAgnetBundlePath = System.getProperty("SWT_BOT_AGENT_BUNDLE_PATH");

			String autConfiguration = createAUTConfiguration(applicationPath, swtBotAgnetBundlePath);
			launchApplicationCommandList = new ArrayList<String>();

			// if the AUT is a MAC OS X binary
			if (applicationPath.endsWith(".app")) {
				launchApplicationCommandList.add("open");
				launchApplicationCommandList.add(applicationPath);
				launchApplicationCommandList.add("--args");
			}
			// for all other binaries (Linux, Windows)
			else {
				launchApplicationCommandList.add(applicationPath);
			}

			launchApplicationCommandList.add("-clean");
			launchApplicationCommandList.add("-application");
			launchApplicationCommandList.add("org.testeditor.agent.swtbot.TestEditorSWTBotAgent");
			launchApplicationCommandList.add("-aut");
			launchApplicationCommandList.add("org.eclipse.e4.ui.workbench.swt.E4Application");
			launchApplicationCommandList.add("-data");
			launchApplicationCommandList.add(getWorkspacePath());

			launchApplicationCommandList.add("-nl");
			launchApplicationCommandList.add("de_de");
			launchApplicationCommandList.add("-configuration");
			launchApplicationCommandList.add(autConfiguration);
			createAndLaunchProcess();
		} catch (Exception exp) {
			LOGGER.error("Error Test execution: ", exp);
			throw new StopTestException(exp);
		}
	}

	/**
	 * Creates and launches the Process for the AUT.
	 * 
	 * @throws Exception
	 *             on problems to create the AUT process.
	 */
	private void createAndLaunchProcess() throws Exception {
		LOGGER.trace("Start List: " + Arrays.toString(launchApplicationCommandList.toArray()));
		ProcessBuilder builder = new ProcessBuilder(launchApplicationCommandList);
		builder.redirectErrorStream(true);
		LOGGER.info("Start SWT-app-under-test");
		process = builder.start();
		createAndRunLoggerOnStream(process.getInputStream(), false);
		createAndRunLoggerOnStream(process.getErrorStream(), true);
		LOGGER.info("Output from SWT-app-under-test");
		boolean launched = false;
		int timeOut = 0;
		while (!launched) {
			try {
				Thread.sleep(200);
				LOGGER.info("waiting for launch");
				launched = isLaunched();
				timeOut++;
				if (timeOut > 200) {
					stopApplication();
					throw new StopTestException("Time out launching AUT.");
				}
			} catch (InterruptedException e) {
				LOGGER.error("startApplication InterruptedException: ", e);
			}
		}
		LOGGER.info("SWT-app-under-test is ready for test");
		sendMessage("setTestName" + COMMAND_DELIMITER + testName);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			LOGGER.error("startApplication InterruptedException: ", e);
		}
	}

	/**
	 * Waits until a previous launch is terminated.
	 * 
	 * @throws InterruptedException
	 *             while waiting.
	 * 
	 */
	protected void waitUntilPreviousLaunchIsFinished() throws InterruptedException {
		LOGGER.info("Already a process running? " + runningApp);
		int count = 0;
		while (runningApp) {
			Thread.sleep(100);
			count++;
			if (count > 100) {
				LOGGER.error(">>>>>>> Old process blocks AUT start for 10 seconds. Giving up for test: " + testName
						+ ".");
				try {
					List<String> allLines = Files.readAllLines(new File(new File(getWorkspacePath(), ".metadata"),
							".log").toPath(), Charset.forName("UTF-8"));
					LOGGER.error("AUT .log content:");
					for (String string : allLines) {
						LOGGER.error(string);
					}
				} catch (Exception e) {
					LOGGER.error("Error reading .log of AUT.", e);
				} finally {
					stopApplication();
				}
			}
		}
	}

	/**
	 * Marks application as stopped.
	 * 
	 * @return true.
	 */
	public boolean markApplicationStopped() {
		runningApp = false;
		return true;
	}

	/**
	 * Executes the AUT for local Debugging outsite the TE Context as an JUnit
	 * Test.
	 * 
	 * @param applicationPath
	 *            to AUT
	 * @param workspace
	 *            the workspace for the testeditor_aut
	 * @param bundleDir
	 *            Path to the Parent directory of the SWTBot agent server
	 *            bundle.
	 * @throws Exception
	 *             on Test execution
	 */
	protected void startJUnitApplication(String applicationPath, String workspace, String bundleDir) throws Exception {

		try {
			String autConfiguration = createAUTConfiguration(applicationPath, bundleDir);
			ArrayList<String> list = new ArrayList<String>();
			list.add(applicationPath);
			list.add("-clean");
			list.add("-application");
			list.add("org.testeditor.agent.swtbot.TestEditorSWTBotAgent");
			list.add("-aut");
			list.add("org.eclipse.e4.ui.workbench.swt.E4Application");
			list.add("-data");
			list.add(workspace);
			list.add("-nl");
			list.add("de_de");
			list.add("-configuration");
			list.add(autConfiguration);
			ProcessBuilder builder = new ProcessBuilder(list);
			builder.redirectErrorStream(true);
			LOGGER.info("Start SWT-app-under-test");
			process = builder.start();
			createAndRunLoggerOnStream(process.getInputStream(), false);
			createAndRunLoggerOnStream(process.getErrorStream(), true);
			LOGGER.info("Output from SWT-app-under-test");
			boolean launched = false;
			while (!launched) {
				try {
					Thread.sleep(100);
					LOGGER.info("waiting for launch");
					launched = isLaunched();
				} catch (InterruptedException e) {
					LOGGER.error("startApplication InterruptedException: ", e);
				}
			}
			LOGGER.info("SWT-app-under-test is ready for test");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				LOGGER.error("startApplication InterruptedException: ", e);
			}
		} catch (Exception exp) {
			LOGGER.error("Error Test execution: ", exp);
			throw exp;
		}
	}

	/**
	 * Cleans the Workspace of the AUT and creates a demo Project.
	 * 
	 * @throws IOException
	 *             on reset the workspace.
	 * @throws URISyntaxException
	 *             on reset the workspace.
	 */
	private void prepareAUTWorkspace() throws IOException, URISyntaxException {

		File wsPathFile = new File(getWorkspacePath());
		Path wsPath = wsPathFile.toPath();
		if (wsPathFile.exists()) {
			Files.walkFileTree(wsPath, getDeleteFileVisitor());
			LOGGER.info("Removed AUT_WS: " + getWorkspacePath());
		}
		Files.createDirectory(wsPath);
		Map<String, String> env = new HashMap<String, String>();
		env.put("create", "true");
		FileSystem fs = FileSystems.newFileSystem(getClass().getResource("/DemoWebTests.zip").toURI(), env);
		Iterable<Path> rootDirectories = fs.getRootDirectories();
		for (Path root : rootDirectories) {
			DirectoryStream<Path> directoryStream = Files.newDirectoryStream(root);
			for (Path path : directoryStream) {
				if (path.getFileName().startsWith("DemoWebTests.zip")) {
					LOGGER.info("Found DemoWebTest.");
					Files.copy(path, Paths.get(wsPath.toString(), "DemoWebTests.zip"));
					URI uriDemoZip = new URI("jar:" + Paths.get(wsPath.toString(), "/DemoWebTests.zip").toUri());
					LOGGER.info(uriDemoZip);
					FileSystem zipFs = FileSystems.newFileSystem(uriDemoZip, env);
					copyFolder(zipFs.getPath("/"), Paths.get(getWorkspacePath()));
					zipFs.close();
				}
			}
		}
		fs.close();
		LOGGER.info("Created Demoproject in: " + getWorkspacePath());
	}

	/**
	 * copies the directories.
	 * 
	 * @param src
	 *            source-directory
	 * @param dest
	 *            destination-directory
	 * @throws IOException
	 *             IOException
	 */
	private void copyFolder(Path src, Path dest) throws IOException {
		if (Files.isDirectory(src)) {
			// if directory not exists, create it
			if (!Files.exists(dest)) {
				Files.createDirectory(dest);
			}
			DirectoryStream<Path> directoryStream = Files.newDirectoryStream(src);
			for (Path path : directoryStream) {
				Path srcFile = path;
				Path destFile = Paths.get(dest.toString() + "/" + path.getFileName());
				copyFolder(srcFile, destFile);
			}
		} else {
			Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/**
	 * Creates a thread to log the content of the input stream. This thread is
	 * started after creation.
	 * 
	 * @param inputStream
	 *            to piped to the logger.
	 * @param errorStream
	 *            if true the logger uses the error level in other cases info.
	 */
	private void createAndRunLoggerOnStream(final InputStream inputStream, final boolean errorStream) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				char[] cbuf = new char[8192];
				int len = -1;
				try {
					InputStreamReader reader = new InputStreamReader(inputStream, CHARSET_UTF_8);
					while ((len = reader.read(cbuf)) > 0) {
						if (errorStream) {
							LOGGER.error(new String(cbuf, 0, len));
						} else {
							LOGGER.info(new String(cbuf, 0, len));
						}
					}
				} catch (IOException e) {
					LOGGER.debug("Error reading remote Process Stream", e);
				}
			}
		}).start();
	}

	/**
	 * copies the original config.ini of the SWT-app-under-test in the
	 * temporary-directory of the os and adds a reference to the SWTBotAgent.
	 * This is necessary to start the application with the SWTBotAgent.
	 * 
	 * 
	 * @param applicationPath
	 *            the path of the application.
	 * @param swtBotAgentBundlePath
	 *            Directory to the TestEditor bundle directory.
	 * @return the absolutPath to the directory of the config.ini of the
	 *         application.
	 * @throws IOException
	 *             on creating the property file.
	 */
	private String createAUTConfiguration(String applicationPath, String swtBotAgentBundlePath) throws IOException {
		FileInputStream fileInputStreamConfig = null;
		FileOutputStream fileOutputStream = null;
		Properties properties = new Properties();
		String result = null;
		fileInputStreamConfig = new FileInputStream(lookUpConfigIni(applicationPath));
		properties.load(fileInputStreamConfig);
		fileInputStreamConfig.close();
		String bundles = properties.getProperty("osgi.bundles");
		LOGGER.info("Bundle: " + swtBotAgentBundlePath);
		// begin; This part is just for considering the testing of an swt
		// application with the test-editor started from IDE
		if (new File(swtBotAgentBundlePath).isDirectory()) {
			LOGGER.info("Directory found for bundle: " + swtBotAgentBundlePath);
			File srcBundleDir = new File(swtBotAgentBundlePath + File.separator + "target");
			LOGGER.info("Directory found for source bundle: " + srcBundleDir + " -> " + srcBundleDir.exists());
			File jarBundle = srcBundleDir.listFiles(getSWTBotAgentFilter())[0];
			swtBotAgentBundlePath = jarBundle.getAbsolutePath();
		}
		// end;
		LOGGER.info("Found Path to Agent Bundle: " + swtBotAgentBundlePath);
		properties.setProperty("osgi.bundles", bundles + ",reference:file:" + swtBotAgentBundlePath);
		File file = new File(System.getProperty("java.io.tmpdir") + File.separator + "configuration");
		if (!file.exists()) {
			if (!file.mkdir()) {
				return "";
			}
		}
		result = file.getAbsolutePath() + File.separator + "config.ini";
		fileOutputStream = new FileOutputStream(result);
		properties.store(fileOutputStream, "Changed for TestEditor run.");
		fileOutputStream.close();
		LOGGER.info("New congfig.ini: " + result);
		return new File(result).getParentFile().getAbsolutePath();
	}

	/**
	 * Look up for the config.ini of the AUT. On Linux and Windows an RCP
	 * Application has the config.ini related to the
	 * binary/configuration/config.ini. On MacOs it is
	 * ../../../configuration/config.ini
	 * 
	 * @param applicationPath
	 *            to the executable
	 * @return a File to an existing file.
	 */
	private File lookUpConfigIni(String applicationPath) {
		String path = new File(applicationPath).getParentFile().getAbsolutePath();
		File linuxOrWinFile = new File(path + File.separator + "configuration" + File.separator + "config.ini");
		if (linuxOrWinFile.exists()) {
			return linuxOrWinFile;
		} else {
			return new File(path + "/../../../configuration/config.ini");
		}
	}

	/**
	 * FileFilter to find the SWTBotAgent Bundle.
	 * 
	 * @return true if there is a an SWTBot bundle
	 */
	protected FileFilter getSWTBotAgentFilter() {
		return new FileFilter() {

			@Override
			public boolean accept(File pathname) {
				if (pathname.getName().startsWith("org.testeditor.agent.swtbot")) {
					return true;
				}
				return false;
			}
		};
	}

	/**
	 * Returns true if the application is launched.
	 * 
	 * @return true, if the server is ready.
	 */
	private boolean isLaunched() {
		try {
			Socket client = getSocket();
			LOGGER.info("Is server ready for " + testName + "?");
			PrintStream os = new PrintStream(client.getOutputStream(), false, CHARSET_UTF_8);
			os.println("isLaunched");
			os.flush();
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), CHARSET_UTF_8));
			String s = in.readLine();
			LOGGER.info(s);
			client.close();
			return Boolean.valueOf(s);
		} catch (UnknownHostException e) {
			LOGGER.error("isLaunched UnknownHostException: ", e);
		} catch (ConnectException e) {
			LOGGER.trace("Server not available.");
		} catch (IOException e) {
			LOGGER.error("isLaunched IOException: ", e);
		}
		return false;
	}

	/**
	 * 
	 * @return Socket connected to the SWTBotAgent.
	 * @throws UnknownHostException
	 *             by socket
	 * @throws IOException
	 *             by socket
	 */
	private Socket getSocket() throws UnknownHostException, IOException {
		return new Socket(AGENT_HOST, AGENT_PORT);
	}

	/**
	 * 
	 * @param elementKey
	 *            the key for the element-list
	 * 
	 * @return the value to the key in the element-list, if found, else the key.
	 */
	private String getLocator(String elementKey) {
		String locator = getLocatorWrapped(elementKey);
		if (locator == null) {
			return elementKey;
		}
		return locator;
	}

	/**
	 * 
	 * @param elementKey
	 *            the key for the element-list
	 * @return the value to the key, if found.
	 */
	private String getLocatorWrapped(String elementKey) {
		String locator = null;
		try {
			locator = elementListService.getValue(elementKey);
			return locator;
		} catch (ElementKeyNotFoundException e) {
			String message = "The specified Key for the Gui-Element \"" + elementKey + "\" could not be found!";
			LOGGER.info(message, e);
			return null;
		}
	}

	@Override
	public boolean tearDown() {
		LOGGER.info("TearDown to cleanup the AUT.");
		stopApplication();
		return true;
	}

	@Override
	public String getTestName() {
		return testName;
	}

	@Override
	public void setTestName(String testName) {
		this.testName = testName;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.testeditor.fixture.core.interaction.Fixture#preInvoke(java.lang.reflect
	 * .Method, java.lang.Object, java.lang.Object[])
	 */
	@Override
	public void preInvoke(Method method, Object instance, Object... convertedArgs) throws InvocationTargetException,
			IllegalAccessException {

		String label = PerformanceLogHandler.getLabel(method, convertedArgs);

		javaMon = MonitorFactory.start(label);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.testeditor.fixture.core.interaction.Fixture#postInvoke(java.lang.
	 * reflect.Method, java.lang.Object, java.lang.Object[])
	 */
	@Override
	public void postInvoke(Method method, Object instance, Object... convertedArgs) throws InvocationTargetException,
			IllegalAccessException {

		javaMon.stop();

	}

	/**
	 * This method writes the collected logdata at the end of each test.
	 */
	public void writePerformanceLog() {

		try {

			if (LOGGER.isInfoEnabled()) {
				LOGGER.info("TOTAL_TIME_FOR_INDIVIDUAL_TEST:" + MonitorFactory.getRootMonitor().getTotal());
			}

			new PerformanceLogHandler().logPerformanceData(getTestName());

			// resetting logdata for the last called Test
			javaMon.reset();

		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
	}

	/**
	 * Returns the path to the Workspace of the AUT. Relative paths are
	 * converted to direct paths.
	 * 
	 * @return the path to the workspace of the AUT as String.
	 * @throws IOException
	 *             on looking up the real path.
	 */
	public String getWorkspacePath() throws IOException {
		String workspacePath = System.getProperty("aut.workspace.path");

		if (workspacePath == null) {
			workspacePath = "@user.home/.testeditor_aut";
		}

		return new File(workspacePath).getCanonicalPath();
	}

	/**
	 * 
	 * @param proprtyFileName
	 *            of the property file.
	 * @param propertyKey
	 *            the key of the property wich should be checked in the config.
	 * @param propertyValue
	 *            the value of the property to be mached to the value of the
	 *            property key.
	 * @return true if the value according to the key in the files is equals to
	 *         propertyValue.
	 * @throws IOException
	 *             on error reading proprty file
	 */
	public boolean checkInProprtyValue(String proprtyFileName, String propertyKey, String propertyValue)
			throws IOException {
		Properties properties = new Properties();
		FileInputStream inputStream = new FileInputStream(proprtyFileName);
		properties.load(inputStream);
		inputStream.close();
		boolean found = properties.get(propertyKey).equals(propertyValue);
		LOGGER.info("Property search in " + proprtyFileName + " with key " + propertyKey + " and value: "
				+ propertyValue + " is: " + found);
		return found;
	}

	/**
	 * Creates TestStructure Files in the filesystem of a fitnesse backend
	 * system. This method doesn't use the api for that and does no
	 * notifications to the test-editor or fitnsse server.
	 * 
	 * @param destinationTestStructure
	 *            full name of the new one
	 * @return true on success
	 * @throws IOException
	 *             on creation error.
	 */
	public boolean createTestStructureFiles(String destinationTestStructure) {
		String[] tsNameParts = destinationTestStructure.split("\\.");

		try {
			String destPath = getWorkspacePath() + File.separator + tsNameParts[0] + File.separator + "FitNesseRoot"
					+ File.separator
					+ destinationTestStructure.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
			Path tsDir = Files.createDirectories(Paths.get(destPath));
			LOGGER.trace("Created: " + tsDir.toAbsolutePath());
			String xml = "<?xml version=\"1.0\"?><properties><Edit>true</Edit><Files>true</Files><Properties>true</Properties><RecentChanges>true</RecentChanges><Refactor>true</Refactor><Search>true</Search><Test/><Versions>true</Versions><WhereUsed>true</WhereUsed></properties>";
			Files.write(Paths.get(destPath, "properties.xml"), xml.getBytes());
			return new File(tsDir.toFile(), "content.txt").createNewFile();
		} catch (Exception e) {
			LOGGER.error("Error creating testobject from " + destinationTestStructure, e);
		}
		return false;
	}

	/**
	 * Waits until a button is enabled.
	 * 
	 * @param locator
	 *            of the button.
	 * @param timeOut
	 *            to wait for the button. the time is in seconds.
	 * @return true if it was possible to click on the button.
	 */
	public boolean waitForButtonAndClick(String locator, int timeOut) {
		int timeOutMax = timeOut * 1000;
		int timeOutCounter = 0;
		while (!isButtonEnabled(locator) & timeOutMax > timeOutCounter) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				LOGGER.error("Interruption by waiting for ui elment,", e);
			}
			timeOutCounter = timeOutCounter + 100;
		}
		return clickButton(locator);
	}

	/**
	 * Waits until a button is enabled. The timeout for waiting is 30 seconds.
	 * 
	 * @param locator
	 *            of the button.
	 * @return true if it was possible to click on the button.
	 */
	public boolean waitForButtonAndClick(String locator) {
		return waitForButtonAndClick(locator, 30);
	}

	/**
	 * Selects an entry in the active auto complete field.
	 * 
	 * @param item
	 *            String to be selected in the auto complete list.
	 * @return the result of the message.
	 */
	public boolean selectElementInAtuocompleteWidget(String item) {
		return sendMessage("selectElementInAtuocompleteWidget" + COMMAND_DELIMITER + item);
	}

	/**
	 * This method checks if a specific line contains a text. Line numbers are
	 * starting at one.
	 * 
	 * @param testFilePath
	 *            path to the source file, starting from "aut.workspace.path",
	 *            comma separated.
	 * @param text
	 *            the text to search for
	 * @param line
	 *            line number starting at 1
	 * @return true if the text is found in the line
	 * @throws StopTestException
	 *             will be thrown if the "aut.workspace.path" system variable is
	 *             not set or line is zero
	 * @throws IOException
	 *             will be thrown if the file not exist
	 */
	public boolean checkTextInCodeLine(String testFilePath, String text, int line) throws StopTestException,
			IOException {
		String workspacePath = System.getProperty("aut.workspace.path");
		if (workspacePath == null || workspacePath.equals("")) {
			LOGGER.error("aut.workspace.path not set");
			throw new StopTestException("aut.workspace.path not set");
		}
		if (line == 0) {
			throw new StopTestException("line 0 not exist");
		}

		return sourceList(workspacePath, testFilePath).get(line - 1).contains(text);
	}

	/**
	 * This method checks if a specific line dosn't contain a text. Line numbers
	 * are starting at one.
	 * 
	 * @param testFilePath
	 *            path to the source file, starting from "aut.workspace.path",
	 *            comma separated.
	 * @param text
	 *            the text thats not in the line
	 * @param line
	 *            line number starting at 1
	 * @return true if the text is found in the line
	 * @throws StopTestException
	 *             will be thrown if the "aut.workspace.path" system variable is
	 *             not set or line is zero
	 * @throws IOException
	 *             will be thrown if the file not exist
	 */
	public boolean checkNotTextInCodeLine(String testFilePath, String text, int line) throws StopTestException,
			IOException {
		String workspacePath = System.getProperty("aut.workspace.path");
		if (workspacePath == null || workspacePath.equals("")) {
			LOGGER.error("aut.workspace.path not set");
			throw new StopTestException("aut.workspace.path not set");
		}
		if (line == 0) {
			throw new StopTestException("line 0 not exist");
		}

		return !sourceList(workspacePath, testFilePath).get(line - 1).contains(text);
	}

	/**
	 * Opens a file in the workspace and returns the source as a List.
	 * 
	 * @param directory
	 *            start path parameter
	 * @param testFilePath
	 *            comma separated path to file path
	 * @return a list of all source lines
	 * @throws IOException
	 *             if file not exist
	 */
	private List<String> sourceList(String directory, String testFilePath) throws IOException {
		String[] folder = testFilePath.split(",");
		directory += File.separator + folder[0] + File.separator + "FitNesseRoot"; // Project
																					// name
																					// and
																					// FitNsesseRoot
		for (String string : folder) {
			directory += File.separator + string;
		}
		directory += File.separator + "content.txt";
		File dirFile = new File(directory);
		Path path = dirFile.toPath();
		LOGGER.warn("PATH " + path.toString() + "  " + path.toFile().exists() + "---" + Charset.defaultCharset());
		List<String> lines = Files.readAllLines(path, Charset.defaultCharset());
		return lines;
	}

	/**
	 * Copy a File or a Directory inside the AUT workspace. Existing target
	 * files / Directories are overwritten without warnings.
	 *
	 * @param relSourcePath
	 *            the workspace relative path of the source file or directory to
	 *            copy
	 * @param relTargetPath
	 *            the workspace relative path of the target file or directory
	 */
	public void copyInWorkspace(String relSourcePath, String relTargetPath) {

		LOGGER.info("kopiere. " + relSourcePath + " nach " + relTargetPath);

		File workspaceDir;
		try {
			workspaceDir = new File(getWorkspacePath());
		} catch (IOException e1) {
			String msg = "cannot find workspacePath";
			LOGGER.error(msg);
			throw new StopTestException(msg);
		}
		File source = new File(workspaceDir, relSourcePath);
		File target = new File(workspaceDir, relTargetPath);
		Path sourcePath = Paths.get(source.getAbsolutePath());
		Path targetPath = Paths.get(target.getAbsolutePath());

		if (!source.exists()) {
			String msg = "cannot copy '" + source + "': File does not exist";
			LOGGER.error(msg);
			throw new StopTestException(msg);
		}
		if (!source.canRead()) {
			String msg = "cannot copy '" + source + "': File cannot be read";
			LOGGER.error(msg);
			throw new StopTestException(msg);
		}

		if (source.isDirectory()) {
			try {
				copyFolder(sourcePath, targetPath);
			} catch (IOException e) {
				String msg = "cannot copy directory '" + source + "' to '" + target + "'";
				LOGGER.error(msg, e);
				throw new StopTestException(msg, e);
			}
		} else {
			try {
				Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				String msg = "cannot copy directory '" + source + "' to '" + target + "'";
				LOGGER.error(msg, e);
				throw new StopTestException(msg, e);
			}
		}

	}

	/**
	 * delete a given file or directory in the workspace
	 * 
	 * @param relTargetPath
	 *            the workspace relative path of the source file or directory to
	 *            delete (recursively in the later case)
	 */
	public void deleteInWorkspace(String relTargetPath) {
		File workspaceDir;
		try {
			workspaceDir = new File(getWorkspacePath());
		} catch (IOException e1) {
			String msg = "cannot find workspacePath";
			LOGGER.error(msg);
			throw new StopTestException(msg);
		}
		File target = new File(workspaceDir, relTargetPath);

		if (target.exists()) {
			if (target.isDirectory()) {
				deleteFolder(target);
			} else {
				try {
					Files.delete(target.toPath());
				} catch (IOException e) {
					String msg = "cannot delete file '" + target + "'";
					LOGGER.error(msg, e);
					throw new StopTestException(msg, e);
				}
			}
		}

	}

	private void deleteFolder(File target) {
		List<File> files = Arrays.asList(target.listFiles());
		for (File file : files) {
			if (file.isFile()) {
				try {
					Files.delete(file.toPath());
				} catch (IOException e) {
					String msg = "cannot delete file '" + target + "'";
					LOGGER.error(msg, e);
					throw new StopTestException(msg, e);
				}
			} else {
				deleteFolder(file);
			}
		}
		try {
			Files.delete(target.toPath());
		} catch (IOException e) {
			String msg = "cannot delete file '" + target + "'";
			LOGGER.error(msg, e);
			throw new StopTestException(msg, e);
		}
	}

	/**
	 * create or overwrite a file in the workspace and fill it with the given
	 * content.
	 * 
	 * @param relTargetPath
	 *            the workspace relative path of the target file to create
	 * @param content
	 *            the content of the new file
	 */
	public void createFileInWorkspace(String relTargetPath, String content) {
		File workspaceDir;
		try {
			workspaceDir = new File(getWorkspacePath());
		} catch (IOException e1) {
			String msg = "cannot find workspacePath";
			LOGGER.error(msg);
			throw new StopTestException(msg);
		}
		File target = new File(workspaceDir, relTargetPath);

		deleteInWorkspace(relTargetPath);

		try {
			PrintWriter pw = new PrintWriter(target);
			pw.print(content);
			pw.close();
		} catch (FileNotFoundException e) {
			String msg = "cannot create file '" + target + "'";
			LOGGER.error(msg, e);
			throw new StopTestException(msg, e);
		}

	}

	public boolean checkValueInDropDownBox(String dropDownBoxID, String value) {
		return sendMessage("checkDropDownContains" + COMMAND_DELIMITER + getLocator(dropDownBoxID) + COMMAND_DELIMITER
				+ value);
	}

}