package name.finsterwalder.utils;

import java.util.Date;

/**
 * Simple default implementation for a TimeProvider.
 *
 * @author Malte Finsterwalder
 * @since 2013-09-04 18:18
 */
public class SimpleDateTimeProvider implements TimeProvider {

	@Override
	public Date getDate() {
		return new Date();
	}

	@Override
	public long getTime() {
		return System.currentTimeMillis();
	}
}
