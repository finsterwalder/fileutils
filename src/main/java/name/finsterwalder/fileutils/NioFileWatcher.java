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

import name.finsterwalder.utils.Ensure;
import name.finsterwalder.utils.SimpleDateTimeProvider;
import name.finsterwalder.utils.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.*;


/**
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class NioFileWatcher implements FileWatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(NioFileWatcher.class);
	private static final int DEFAULT_GRACE_PERIOD = 1000;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
	private PollingFileWatcher pollingFileWatcher;
	private WatchService watchService;
	private final Path fileToWatch;
	private final Object lock = new Object();
	private volatile long lastChanged;
	private volatile long lastProcessed;
	private final TimeProvider timeProvider;

	/**
	 * Watch a file with a default grace period of 1sek.
	 */
	public NioFileWatcher(final String fileToWatch, final FileChangeListener fileChangeListener) {
		this(Paths.get(fileToWatch), fileChangeListener, DEFAULT_GRACE_PERIOD, new SimpleDateTimeProvider());
	}

	/**
	 * Watch a file with a default grace period of 1sek.
	 */
	public NioFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener) {
		this(fileToWatch.toPath(), fileChangeListener, DEFAULT_GRACE_PERIOD, new SimpleDateTimeProvider());
	}

	/**
	 * Watch a file with a default grace period of 1sek.
	 */
	public NioFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener) {
		this(fileToWatch, fileChangeListener, DEFAULT_GRACE_PERIOD, new SimpleDateTimeProvider());
	}

	public NioFileWatcher(final String fileToWatch, final FileChangeListener fileChangeListener, long gracePeriod) {
		this(FileSystems.getDefault().getPath(fileToWatch), fileChangeListener, gracePeriod, new SimpleDateTimeProvider());
	}

	public NioFileWatcher(final File fileToWatch, final FileChangeListener fileChangeListener, long gracePeriod) {
		this(fileToWatch.toPath(), fileChangeListener, gracePeriod, new SimpleDateTimeProvider());
	}

	public NioFileWatcher(final Path fileToWatch, final FileChangeListener fileChangeListener, long gracePeriod) {
		this(fileToWatch, fileChangeListener, gracePeriod, new SimpleDateTimeProvider());
	}

	/*package*/ NioFileWatcher(final Path fileToWatchParam, final FileChangeListener fileChangeListener, final long gracePeriod, TimeProvider timeProvider) {
		Ensure.notNull(fileToWatchParam, "fileToWatchParam");
		Ensure.notNull(fileChangeListener, "fileChangeListener");
		Ensure.notNull(timeProvider, "timeProvider");
		this.timeProvider = timeProvider;
		fileToWatch = fileToWatchParam.toAbsolutePath();
		final Path directoryPath = fileToWatch.getParent();
		if (directoryPath == null) {
			throw new IllegalArgumentException("File does not have a parent directory: " + fileToWatch);
		}
		if (Files.exists(directoryPath)) {
			initWatcher(fileChangeListener, gracePeriod, directoryPath);
		} else {
			pollingFileWatcher = new PollingFileWatcher(fileToWatch.toFile(), new FileChangeListener() {
				@Override
				public void fileChanged() {
					pollingFileWatcher.unwatch();
					pollingFileWatcher = null;
					initWatcher(fileChangeListener, gracePeriod, directoryPath);
					fileChangeListener.fileChanged();
				}
			});
		}
	}

	private void initWatcher(final FileChangeListener fileChangeListener, final long gracePeriod, final Path directoryPath) {
		final Path filenamePath = fileToWatch.getFileName();
		try {
			watchService = directoryPath.getFileSystem().newWatchService();
			directoryPath.register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
			new Thread() {
				@Override
				public void run() {
					boolean valid = true;
					try {
						while (valid && !isInterrupted()) {
							WatchKey takenWatchKey = watchService.take();
							for (WatchEvent<?> event : takenWatchKey.pollEvents()) {
								if (OVERFLOW != event.kind()) {
									Path context = (Path)event.context();
									if (filenamePath.equals(context)) {
										notifyChangeListener(fileChangeListener, gracePeriod);
									}
								}
							}
							valid = takenWatchKey.reset();
						}
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} catch (ClosedWatchServiceException e) {
						// nothing to do, since polling loop is already cancelled
					} finally {
						close(watchService, fileToWatch);
					}
				}

				private void notifyChangeListener(final FileChangeListener fileChangeListener, final long gracePeriod) {
					if (gracePeriod > 0) {
						final long changeTimestamp = NioFileWatcher.this.timeProvider.getTime();
						synchronized (lock) {
							lastChanged = changeTimestamp;
						}
						scheduler.schedule(new Runnable() {

							@Override
							public void run() {
								synchronized (lock) {
									long lastChanged = NioFileWatcher.this.lastChanged;
									if (changeTimestamp == lastChanged && NioFileWatcher.this.lastProcessed < lastChanged) {
										NioFileWatcher.this.lastProcessed = lastChanged;
										fileChangeListener.fileChanged();
									}
								}
							}
						}, gracePeriod, TimeUnit.MILLISECONDS);
					} else {
						fileChangeListener.fileChanged();
					}
				}

			}.start();
		} catch (Exception e) {
			throw new RuntimeException("Could not initialize file watcher for " + fileToWatch.toAbsolutePath(), e);
		}
	}

	@Override
	public void unwatch() {
		close(watchService, fileToWatch);
		if (pollingFileWatcher != null) {
			pollingFileWatcher.unwatch();
		}
	}

	private static void close(final WatchService watchService, final Path fileToWatch) {
		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException e) {
				LOGGER.info("Could not close file watcher for {}", fileToWatch.toAbsolutePath(), e);
			}
		}
	}
}
