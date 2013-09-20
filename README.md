fileutils
=========

A Simple FileWatcher Utility

To watch for changes to a file simply create a FileWatcher on that file.
There are two implementations of the FileWatcher, the PollingFileWatcher and the NioFileWatcher.

The PollingFileWatcher checks the modified timestamp of the file in regular intervalls.

It is created like this:

```java
File file = ...
FileChangeListener listener = ...
// This creates a PollingFileWatcher with a default polling intervall of 500ms and a default grace period of 1000ms.
new PollingFileWatcher(file, fileChangeListener);
```

Whenever the timestamp of the file changed, the PollingFileWatcher waits a grace period, before
it checks the timestamp again to ensure that no further modifications where made and that the modification to the file was completed.
Then the registered ChangeListener is notified.

The NioFileWatcher uses the java.nio.file.WatchService, which can be used to register a listener for file changes with the operating system.
It can be created like this:

```java
File file = ...
FileChangeListener listener = ...
// This creates a NioFileWatcher with a default grace period of 1000ms.
new NioFileWatcher(file, fileChangeListener);
```

It also has a grace period to wait that all changes to the file are completed, to reduce the amount of notifications and to prevent
access to a file, while it is changed by another process. A notification is issued, when there where no more changes during the grace period.

NioFileWatcher does not work reliably on NFS mounted file systems.
The NioFileWatcher registers itself for all changes in the parent directory of the file to watch.
