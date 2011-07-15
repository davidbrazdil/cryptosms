package uk.ac.cam.db538.securesms.database;

import java.nio.ByteBuffer;

import uk.ac.cam.db538.securesms.encryption.Encryption;
import junit.framework.TestCase;

public class FileEntryHeader_Test extends TestCase {

	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}

	public void testSmsHistory_HeaderIntLongLong() {
		FileEntryHeader header;
		
		// ASSIGNMENT
		
		header = new FileEntryHeader(12, 13L, 15L);
		assertEquals(header.getVersion(), 12);
		assertEquals(header.getIndexFree(), 13L);
		assertEquals(header.getIndexConversations(), 15L);
		
		// INDICES OUT OF BOUNDS
		
		// indexFree
		try {
			header = new FileEntryHeader(1, 
			                               0x0100000000L,
					                       1L);
			assertTrue(false);
		} catch (IndexOutOfBoundsException ex) {
		}
		
		// indexConversation
		try {
			header = new FileEntryHeader(1,
					                       1L,
                                           0x0100000000L); 
			assertTrue(false);
		} catch (IndexOutOfBoundsException ex) {
		}

		// version
		try {
			header = new FileEntryHeader(0x100,
			                               1L,
					                       1L);
			assertTrue(false);
		} catch (IndexOutOfBoundsException ex) {
		}
	}

	public void testCreateData() {
		long indexFree = 25L;
		long indexConversation = 13L;
		int version = 1;
		
		FileEntryHeader header = new FileEntryHeader(version, indexFree, indexConversation);
		byte[] dataAll = FileEntryHeader.createData(header);
		
		// chunk length
		assertEquals(dataAll.length, Database.CHUNK_SIZE);
		
		// plain header
		assertEquals(dataAll[0], (byte) 0x53); // S
		assertEquals(dataAll[1], (byte) 0x4D); // M
		assertEquals(dataAll[2], (byte) 0x53); // S
		assertEquals(dataAll[3], (byte) version); // Version 1
		
		// decrypt the encoded part
		ByteBuffer buf = ByteBuffer.allocate(Database.CHUNK_SIZE - 4);
		buf.put(dataAll, 4, Database.CHUNK_SIZE - 4);
		byte[] dataPlain = Encryption.decryptSymmetric(buf.array(), Encryption.retreiveEncryptionKey());
		
		// check the indices
		assertEquals(Database.getInt(dataPlain, Database.CHUNK_SIZE - 4 - Encryption.ENCRYPTION_OVERHEAD - 8), indexFree);
		assertEquals(Database.getInt(dataPlain, Database.CHUNK_SIZE - 4 - Encryption.ENCRYPTION_OVERHEAD - 4), indexConversation);
	}

	public void testParseData() {
		long indexFree = 25L;
		long indexConversation = 13L;
		int version = 1;
		
		byte[] dataPlain = new byte[FileEntryHeader.LENGTH_ENCRYPTED_HEADER];
		System.arraycopy(Database.getBytes(indexFree), 0, dataPlain, Database.CHUNK_SIZE - 4 - Encryption.ENCRYPTION_OVERHEAD - 8, 4);
		System.arraycopy(Database.getBytes(indexConversation), 0, dataPlain, Database.CHUNK_SIZE - 4 - Encryption.ENCRYPTION_OVERHEAD - 4, 4);
		byte[] dataEncrypted = Encryption.encryptSymmetric(dataPlain, Encryption.retreiveEncryptionKey());
		
		byte[] dataAll = new byte[Database.CHUNK_SIZE];
		System.arraycopy(dataEncrypted, 0, dataAll, 4, Database.CHUNK_SIZE - 4);

		// wrong header (SMS)
		dataAll[0] = 0x53; // S
		dataAll[1] = 0x53; // S
		dataAll[2] = 0x53; // S
		try {
			FileEntryHeader.parseData(dataAll);
			assertTrue(false);
		} catch (DatabaseFileException ex) {
		}

		// fixed, version set
		dataAll[1] = 0x4D; // M
		dataAll[3] = (byte) version;
		FileEntryHeader header = null;
		try {
			header = FileEntryHeader.parseData(dataAll);
		} catch (DatabaseFileException ex) {
			assertTrue(false);
		}
		assertEquals(indexFree, header.getIndexFree());
		assertEquals(indexConversation, header.getIndexConversations());
		assertEquals(version, header.getVersion());
	}

}