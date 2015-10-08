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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * Module tests for the SWTBotFixture.
 *
 */
public class SWTBotFixtureTest {

	@Test
	public void testMarkForRunningApplication() throws Exception {
		final Set<String> monitor = new HashSet<String>();
		Runnable firstLaunch = new Runnable() {

			@Override
			public void run() {
				try {
					SwtBotFixture swtBotFixture = new SwtBotFixture();
					monitor.add("first launched");
					swtBotFixture.waitUntilPreviousLaunchIsFinished();
					Thread.sleep(30);
					monitor.add("first finished");
					swtBotFixture.markApplicationStopped();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}
		};
		Runnable secondLaunch = new Runnable() {

			@Override
			public void run() {
				try {
					monitor.add("second launched");
					SwtBotFixture swtBotFixture = new SwtBotFixture();
					swtBotFixture.waitUntilPreviousLaunchIsFinished();
					assertTrue(monitor.contains("first finished"));
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

			}
		};
		new Thread(firstLaunch).start();
		new Thread(secondLaunch).start();
		Thread.sleep(10);
		assertEquals(2, monitor.size());
		assertTrue(monitor.contains("first launched"));
		assertTrue(monitor.contains("second launched"));
		Thread.sleep(30);
		assertEquals(3, monitor.size());
		assertTrue(monitor.contains("first finished"));
	}

	/**
	 * Tests the tear Down operation.
	 */
	@Test
	public void testTearDown() {
		final Set<String> monitor = new HashSet<String>();
		SwtBotFixture swtBotFixture = new SwtBotFixture() {
			@Override
			public void stopApplication() {
				monitor.add("stop");
			}
		};
		swtBotFixture.stopApplication();
		assertTrue(monitor.contains("stop"));
	}

	@Test
	public void testGetWorkspacePath() throws Exception {
		SwtBotFixture swtBotFixture = new SwtBotFixture();
		String userHomePath = swtBotFixture.getWorkspacePath();
		assertNotNull(userHomePath);
		System.setProperty("aut.workspace.path", "/tmp/ws");
		String externalPath = swtBotFixture.getWorkspacePath();
		assertFalse(userHomePath.equals(externalPath));
	}

}
