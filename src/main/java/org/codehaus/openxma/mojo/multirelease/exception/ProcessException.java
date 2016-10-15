package org.codehaus.openxma.mojo.multirelease.exception;

/**
 * Checked Exception thrown by executing process.
 */
public class ProcessException extends Exception {

	/**
	 * Serial Version UID
	 */
	private static final long serialVersionUID = -8573700467368279049L;

	public ProcessException() {
		super();
	}

	public ProcessException(final String message) {
		super(message);
	}

	public ProcessException(final Throwable cause) {
		super(cause);
	}

	public ProcessException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
