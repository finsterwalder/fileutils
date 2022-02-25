/*
 * fileutils - A simple FileWatcher utility
 * Copyright (C) 2013 Malte Finsterwalder
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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class PollingFileWatcherTest {

	private static final String FILENAME = "FILE_THAT_EXISTS";
	private static final String NOT_EXISTING_FILENAME = "FILE_THAT_DOES_NOT_EXIST";
	private final Path existingFile = Paths.get(FILENAME);
	private final Path notExistingFile = Paths.get(NOT_EXISTING_FILENAME);
	private PollingFileWatcher watcher;

	@BeforeEach
	public void before() throws IOException, InterruptedException {
		Files.deleteIfExists(notExistingFile);
		Files.deleteIfExists(existingFile);
		FileUtils.writeToFile(existingFile, "some content");
	}

	@AfterEach
	public void after() throws InterruptedException, IOException {
		Files.deleteIfExists(notExistingFile);
		Files.deleteIfExists(existingFile);
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
	public void whenAChangeWatcherDetectsAChangeItSchedulesADelayedNotify() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		FileUtils.writeToFile(notExistingFile, "text");
		changeWatcher.run();
		verify(executorMock).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void whenAChangeWatcherDetectsAChangeAndNoGracePeriodIsGivenItIsNotifiedImmediately() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 0, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		FileUtils.writeToFile(notExistingFile, "text");
		changeWatcher.run();
		verify(mockListener).fileChanged();
		verify(executorMock, never()).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void whenAChangeWatcherDetectsNoChangeNothingHappens() throws FileNotFoundException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(existingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		changeWatcher.run();
		verify(executorMock, never()).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void whenADelayedNotifyDetectsAnotherChangeItSchedulesAnotherDelayedNotify() throws IOException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.DelayedNotifier delayedNotifier = new PollingFileWatcher.DelayedNotifier(watcher);
		FileUtils.writeToFile(notExistingFile, "text");
		delayedNotifier.run();
		verify(executorMock).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}


	@Test
	public void whenADelayedNotifyDetectsNoChangeAnUpdateIsSent() throws FileNotFoundException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(existingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.DelayedNotifier delayedNotifier = new PollingFileWatcher.DelayedNotifier(watcher);
		delayedNotifier.run();
		verify(mockListener).fileChanged();
	}

	@Test
	public void changesInAnExistingFileAreDetected() throws IOException, InterruptedException {
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

	@Test
	public void removingAFileThatWasPreviouslySeenSchedulesADelayedNotify() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(existingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		Files.delete(existingFile);
		changeWatcher.run();
		verify(executorMock).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	@Test
	public void aFileThatDoesNotExistDoesNothing() throws IOException, InterruptedException {
		FileChangeListener mockListener = mock(FileChangeListener.class);
		ScheduledExecutorService executorMock = mock(ScheduledExecutorService.class);
		watcher = new PollingFileWatcher(notExistingFile, mockListener, 1, 6, executorMock);
		PollingFileWatcher.ChangeWatcher changeWatcher = new PollingFileWatcher.ChangeWatcher(watcher);
		changeWatcher.run();
		verify(executorMock, never()).schedule(any(PollingFileWatcher.DelayedNotifier.class), eq(6L), eq(TimeUnit.MILLISECONDS));
	}

	static void ensureNewFileWithNewTimestamp(final Path file) throws IOException {
		FileTime lastModified = Files.getLastModifiedTime(file);
		while (lastModified.compareTo(Files.getLastModifiedTime(file)) >= 0) {
			FileUtils.writeToFile(file, "other");
		}
	}
}
