package uk.ac.cam.db538.cryptosms.storage;

/**
 * 
 * Exception thrown by the Database class object
 * 
 * @author David Brazdil
 *
 */
public class StorageFileException extends Exception {
	private static final long serialVersionUID = -7100685462486843982L;

	public StorageFileException(String message) {
		super(message);
	}

	public StorageFileException(Exception e) {
		super(e.getMessage());
		initCause(e);
	}
}
