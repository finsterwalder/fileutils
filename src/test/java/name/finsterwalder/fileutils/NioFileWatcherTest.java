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

import name.finsterwalder.utils.TimeProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import static org.mockito.Mockito.*;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class NioFileWatcherTest {

	private static final String FILENAME = "fileToWatchDoesNotExist.txt";
	private static final Path PATH = FileSystems.getDefault().getPath(FILENAME);
	FileChangeListener fileChangeListenerMock = mock(FileChangeListener.class);
	File file = new File(FILENAME);
	FileWatcher watcher;
	private File dirThatDoesNotExist = new File("DirectoryThatDoesNotExist");
	private File fileInDirThatDoesNotExist = new File("DirectoryThatDoesNotExist/file");

	@Before
	@After
	public void deleteFile() {
		file.delete();
		if (watcher != null) {
			watcher.unwatch();
		}
		fileInDirThatDoesNotExist.delete();
		dirThatDoesNotExist.delete();
	}

	@Test
	public void watchingAFileInADirectoryThatDoesNotExist() throws FileNotFoundException, InterruptedException {
		watcher = new NioFileWatcher("DirectoryThatDoesNotExist/file", fileChangeListenerMock, 200);
		dirThatDoesNotExist.mkdirs();
		try (PrintWriter writer = new PrintWriter(fileInDirThatDoesNotExist)) {
			writer.println("Some Text");
		}
		Thread.sleep(3000); // Needed because of the 500ms polling intervall
		verify(fileChangeListenerMock).fileChanged();
	}

	@Test
	public void watchingAFileThatDoesNotYetExist() throws FileNotFoundException, InterruptedException {
		watcher = new NioFileWatcher(FILENAME, fileChangeListenerMock, 200);
		writeToFile("Some Text");
		Thread.sleep(1500);
		verify(fileChangeListenerMock).fileChanged();
	}

	@Test
	public void noMoreNotificationsAfterUnwatch() throws FileNotFoundException, InterruptedException {
		watcher = new NioFileWatcher(FILENAME, fileChangeListenerMock, 200);
		watcher.unwatch();
		writeToFile("Some Text");
		Thread.sleep(220);
		verifyNoMoreInteractions(fileChangeListenerMock);
	}

	@Test(expected = IllegalArgumentException.class)
	public void theRootDirectoryCanNotBeWatchedBecauseItDoesNotHaveAParent() {
		watcher = new NioFileWatcher("/", mock(FileChangeListener.class));
	}

	@Test
	public void watchExistingFile() throws FileNotFoundException, InterruptedException {
		writeToFile("Some Text");
		watcher = new NioFileWatcher(FILENAME, fileChangeListenerMock, 200);
		Thread.sleep(10);
		writeToFile("Other Text");
		Thread.sleep(250);
		verify(fileChangeListenerMock).fileChanged();
		verifyNoMoreInteractions(fileChangeListenerMock);
	}

	@Test
	public void obeyGracePeriod() throws FileNotFoundException, InterruptedException {
		writeToFile("Some Text");
		TimeProvider mockTimeProvider = mock(TimeProvider.class);
		when(mockTimeProvider.getTime()).thenReturn(1L).thenReturn(40L).thenReturn(79L);
		watcher = new NioFileWatcher(PATH, fileChangeListenerMock, 400, mockTimeProvider);
		for (int i = 0; i < 3; i++) {
			writeToFile("Other Text " + i);
			Thread.sleep(3);
		}
		Thread.sleep(600);
		verify(fileChangeListenerMock).fileChanged();
		verifyNoMoreInteractions(fileChangeListenerMock);
	}

	private void writeToFile(final String text) throws FileNotFoundException {
		try (PrintWriter writer = new PrintWriter(FILENAME)) {
			writer.println(text);
		}
	}
}
