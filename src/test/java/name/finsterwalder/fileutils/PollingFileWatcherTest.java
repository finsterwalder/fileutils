/*
 * fileutils - A simple FileWatcher utility
 * Copyright (C) 2013 PARSHIP GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package name.finsterwalder.fileutils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import static org.mockito.Mockito.*;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class PollingFileWatcherTest {

	private static final String FILENAME = "FILE_THAT_EXISTS";
	private static final String NOT_EXISTING_FILENAME = "FILE_THAT_DOES_NOT_EXIST";
	private final File file = new File(FILENAME);
	private final File not_existing_file = new File(NOT_EXISTING_FILENAME);
	private FileWatcher watcher;

	@Before
	public void before() throws IOException, InterruptedException {
		not_existing_file.delete();
		file.delete();
		try (PrintWriter writer = new PrintWriter(file)) {
			writer.println("some content");
		}
	}

	@After
	public void after() throws InterruptedException {
		not_existing_file.delete();
		file.delete();
		if (watcher != null) {
			watcher.unwatch();
		}
	}

	@Test
	public void nonExistingFilesCanBeWatched() throws FileNotFoundException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		watcher = PollingFileWatcher.watch(not_existing_file, mockListener, 1, 1);
		try (PrintWriter writer = new PrintWriter(not_existing_file)) {
			writer.println("new");
		}
		Thread.sleep(10);
		verify(mockListener).fileChanged();
	}

	@Test
	public void existingFilesCanBeWatched() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		watcher = PollingFileWatcher.watch(file, mockListener, 1, 1);
		// needed because of the file.lastModified timestamp check with 1sec granularity
		ensureNewFileWithNewTimestamp();
		Thread.sleep(10);
		verify(mockListener).fileChanged();
	}

	@Test
	public void afterUnwatchNoUpdatesAreSent() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		watcher = PollingFileWatcher.watch(file, mockListener, 1, 1);
		watcher.unwatch();
		// needed because of the file.lastModified timestamp check with 1sec granularity
		ensureNewFileWithNewTimestamp();
		Thread.sleep(10);
		verifyNoMoreInteractions(mockListener);
	}

	private void ensureNewFileWithNewTimestamp() throws FileNotFoundException {
		long lastModified = file.lastModified();
		while (lastModified >= file.lastModified()) {
			try (PrintWriter writer = new PrintWriter(file)) {
				writer.println("other");
			}
		}
	}

//	@Ignore("runs too long")
	@Test
	public void obeyGracePeriod() throws InterruptedException, FileNotFoundException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		watcher = PollingFileWatcher.watch(file, mockListener, 100, 2000);
		Thread.sleep(1500);
		ensureNewFileWithNewTimestamp();
		Thread.sleep(1500);
		ensureNewFileWithNewTimestamp();
		Thread.sleep(1500);
		ensureNewFileWithNewTimestamp();
		Thread.sleep(4000);
		verify(mockListener).fileChanged();
	}
}
