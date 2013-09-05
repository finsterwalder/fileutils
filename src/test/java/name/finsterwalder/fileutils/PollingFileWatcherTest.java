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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class PollingFileWatcherTest {

	private static final String FILENAME = "FILE_THAT_EXISTS";
	private static final String NOT_EXISTING_FILENAME = "FILE_THAT_DOES_NOT_EXIST";
	private final File existingFile = new File(FILENAME);
	private final File notExistingFile = new File(NOT_EXISTING_FILENAME);
	private PollingFileWatcher watcher;

	@Before
	public void before() throws IOException, InterruptedException {
		notExistingFile.delete();
		existingFile.delete();
		writeToFile(existingFile, "some content");
	}

	@After
	public void after() throws InterruptedException {
		notExistingFile.delete();
		existingFile.delete();
		if (watcher != null) {
			watcher.unwatch();
		}
	}

	@Test
	public void watchingAFileStartsAnExecutorAtAFixedRate() throws FileNotFoundException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		verify(executorMock).scheduleAtFixedRate(any(PollingFileWatcher.ChangeWatcher.class), eq(1L), eq(1L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void whenAChangeWatcherDetectsAChangeItSchedulesADelayedNotify() throws FileNotFoundException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		writeToFile(notExistingFile, "text");
		changeWatcher.run();
		verify(executorMock).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void whenAChangeWatcherDetectsNoChangeNothingHappens() throws FileNotFoundException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		changeWatcher.run();
		verify(executorMock, never()).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void whenADelayedNotifyDetectsAnotherChangeItSchedulesAnotherDelayedNotify() throws FileNotFoundException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.DelayedNotifier delayedNotifier = new PollingFileWatcher.DelayedNotifier(watcher);
		writeToFile(notExistingFile, "text");
		delayedNotifier.run();
		verify(executorMock).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}


	@Test
	public void whenADelayedNotifyDetectsNoChangeAnUpdateIsSent() throws FileNotFoundException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.DelayedNotifier delayedNotifier = new PollingFileWatcher.DelayedNotifier(watcher);
		delayedNotifier.run();
		verify(mockListener).fileChanged();
	}

	@Test
	public void changesInAnExistingFileAreDetected() throws FileNotFoundException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(existingFile, mockListener, 1, 6, executorMock);
		ensureNewFileWithNewTimestamp(existingFile);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		changeWatcher.run();
		verify(executorMock).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void unwatchStopsPolling() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(existingFile, mockListener, 1, 6, executorMock);
		watcher.unwatch();
		verify(executorMock).shutdownNow();
	}

	private void ensureNewFileWithNewTimestamp(File file) throws FileNotFoundException {
		long lastModified = file.lastModified();
		while (lastModified >= file.lastModified()) {
			writeToFile(file, "other");
		}
	}

	private void writeToFile(final File file, final String text) throws FileNotFoundException {
		try (PrintWriter writer = new PrintWriter(file)) {
			writer.println(text);
		}
	}
}
