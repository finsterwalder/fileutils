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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Arrays.asList;


/**
 * Write the given text to a file.
 *
 * @author mfinsterwalder
 * @since 2013-09-06 17:55
 */
public class FileUtils {
	static void writeToFile(final Path file, final String text) throws IOException {
		Files.write(file, asList(text), Charset.forName("UTF-8"));
	}
}
