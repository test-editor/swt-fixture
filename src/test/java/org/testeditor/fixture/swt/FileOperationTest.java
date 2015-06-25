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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import util.FileUtil;

public class FileOperationTest {

	class TestSwtBotFixture extends SwtBotFixture {

		private String workspacePath;

		public TestSwtBotFixture(String workspacePath) {
			this.workspacePath = workspacePath;
		}

		@Override
		public String getWorkspacePath() {
			return workspacePath;
		}
	}

	@Test
	public void testCopyInWorkspace() throws FileNotFoundException {

		File workspace = new File(".").getAbsoluteFile();
		SwtBotFixture fixture = new TestSwtBotFixture(workspace.getAbsolutePath());

		File source = new File("source.file");
		File absSource = new File(workspace, source.getPath());
		if (absSource.exists()) {
			absSource.delete();
		}
		PrintWriter pw = new PrintWriter(new File(workspace, source.getPath()));
		pw.println("Das ist ein Test");
		pw.close();

		File target = new File("target.file");
		File absTarget = new File(workspace, target.getPath());
		if (absTarget.exists()) {
			absTarget.delete();
		}

		fixture.copyInWorkspace(source.getPath(), target.getPath());

		Assert.assertTrue("target file not found", new File(workspace, target.getPath()).exists());
	}

	@Test
	public void testCopyInWorkspaceDirectory() throws FileNotFoundException {

		File workspace = new File(".").getAbsoluteFile();
		SwtBotFixture fixture = new TestSwtBotFixture(workspace.getAbsolutePath());

		File source = new File("sourcedir");
		File absSource = new File(workspace, source.getPath());
		if (absSource.exists()) {
			absSource.delete();
		}
		absSource.mkdirs();
		PrintWriter pw = new PrintWriter(new File(absSource, "a.file"));
		pw.println("Das ist ein Test");
		pw.close();

		File target = new File("target");
		File absTarget = new File(workspace, target.getPath());
		if (absTarget.exists()) {
			absTarget.delete();
		}

		fixture.copyInWorkspace(source.getPath(), target.getPath());

		Assert.assertTrue("target dir not found", new File(workspace, target.getPath()).exists());
		Assert.assertTrue("target file not found", new File(workspace, "target" + File.separator + "a.file").exists());
	}

	@Test
	public void testDeleteInWorkspace() throws FileNotFoundException {

		File workspace = new File(".").getAbsoluteFile();
		SwtBotFixture fixture = new TestSwtBotFixture(workspace.getAbsolutePath());

		File source = new File("targetdir" + File.separator + "innerdir");
		File absSource = new File(workspace, source.getPath());
		absSource.mkdirs();
		PrintWriter pw = new PrintWriter(new File(absSource, "a.file"));
		pw.println("Das ist ein Test");
		pw.close();

		fixture.deleteInWorkspace(source.getPath());

		Assert.assertFalse("target dir still found", new File(workspace, source.getPath()).exists());
	}

	@After
	public void tearDown() {
		File targetdir = new File("targetdir");
		if (targetdir.exists()) {
			FileUtil.deleteFileSystemDirectory(targetdir);
		}
		File sourcedir = new File("sourcedir");
		if (sourcedir.exists()) {
			FileUtil.deleteFileSystemDirectory(sourcedir);
		}
		File target = new File("target.file");
		if (target.exists()) {
			FileUtil.deleteFile(target);
		}
		File source = new File("source.file");
		if (source.exists()) {
			FileUtil.deleteFile(source);
		}
	}

}
