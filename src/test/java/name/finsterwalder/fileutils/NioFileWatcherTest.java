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

import name.finsterwalder.utils.TimeProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.mockito.Mockito.*;


/**
 * Test the NioFileWatcher.
 * The tests rely on timing, since they work with actual file notifications.
 * When the file access is slow, some tests may break and the timings (grace period and sleeps) need to be increased.
 *
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class NioFileWatcherTest {

	private static final String FILENAME = "fileToWatchDoesNotExist.txt";
	private static final Path file = Paths.get(FILENAME);
	FileChangeListener fileChangeListenerMock = mock(FileChangeListener.class);
	FileWatcher watcher;
	private Path dirThatDoesNotExist = Paths.get("DirectoryThatDoesNotExist");
	private Path fileInDirThatDoesNotExist = Paths.get("DirectoryThatDoesNotExist", "file");

	@Before
	@After
	public void deleteFile() throws IOException {
		Files.deleteIfExists(file);
		if (watcher != null) {
			watcher.unwatch();
		}
		Files.deleteIfExists(fileInDirThatDoesNotExist);
		Files.deleteIfExists(dirThatDoesNotExist);
	}

	@Test
	public void watchingAFileInADirectoryThatDoesNotExistStartsAPollingWatcher() throws IOException, InterruptedException {
		watcher = new NioFileWatcher(fileInDirThatDoesNotExist, fileChangeListenerMock, 20);
		Files.createDirectories(dirThatDoesNotExist);
		FileUtils.writeToFile(fileInDirThatDoesNotExist, "Some text");
		Thread.sleep(1100); // Needed because of the 500ms polling intervall
		verify(fileChangeListenerMock).fileChanged();
	}

	@Test
	public void watchingAFileThatDoesNotYetExist() throws IOException, InterruptedException {
		watcher = new NioFileWatcher(file, fileChangeListenerMock, 20);
		FileUtils.writeToFile(file, "Some text");
		Thread.sleep(1100); // Needed because of the 500ms polling intervall
		verify(fileChangeListenerMock).fileChanged();
	}

	@Test
	public void changesBeforeWatchingAreNotNotified() throws IOException, InterruptedException {
		FileUtils.writeToFile(file, "Some text");
		watcher = new NioFileWatcher(file, fileChangeListenerMock, 10);
		waitForNotify();
		verifyNoMoreInteractions(fileChangeListenerMock);
	}

	private void waitForNotify() throws InterruptedException {
		Thread.sleep(50);
	}

	@Test
	public void changesAfterWatchingAreNotified() throws IOException, InterruptedException {
		FileUtils.writeToFile(file, "Some text");
		watcher = new NioFileWatcher(file, fileChangeListenerMock, 10);
		FileUtils.writeToFile(file, "Some change");
		waitForNotify();
		verify(fileChangeListenerMock).fileChanged();
	}

	@Test
	public void noMoreNotificationsAfterUnwatch() throws IOException, InterruptedException {
		FileUtils.writeToFile(file, "Some text");
		watcher = new NioFileWatcher(file, fileChangeListenerMock, 10);
		FileUtils.writeToFile(file, "New text");
		waitForNotify();
		verify(fileChangeListenerMock).fileChanged();
		watcher.unwatch();
		FileUtils.writeToFile(file, "Even more text");
		waitForNotify();
		verifyNoMoreInteractions(fileChangeListenerMock);
	}

	@Test(expected = IllegalArgumentException.class)
	public void theRootDirectoryCanNotBeWatchedBecauseItDoesNotHaveAParent() {
		watcher = new NioFileWatcher("/", mock(FileChangeListener.class));
	}

	@Test
	public void obeyGracePeriod() throws IOException, InterruptedException {
		FileUtils.writeToFile(file, "Some Text");
		TimeProvider mockTimeProvider = mock(TimeProvider.class);
		when(mockTimeProvider.getTime()).thenReturn(1L).thenReturn(40L).thenReturn(79L);
		watcher = new NioFileWatcher(file, fileChangeListenerMock, 40, mockTimeProvider);
		for (int i = 0; i < 3; i++) {
			FileUtils.writeToFile(file, "Other Text " + i);
			Thread.sleep(5);
		}
		waitForNotify();
		verify(fileChangeListenerMock).fileChanged();
	}
}
