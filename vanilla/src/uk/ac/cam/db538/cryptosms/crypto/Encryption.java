package uk.ac.cam.db538.cryptosms.crypto;

public class Encryption {
	public static final int HMAC_LENGTH = 32;
	public static final int HASH_LENGTH = 32;

	public static final int SYM_IV_LENGTH = 16;
	public static final int SYM_BLOCK_LENGTH = 16;
	public static final int SYM_KEY_LENGTH = 32;
	public static final int SYM_OVERHEAD = SYM_IV_LENGTH + HMAC_LENGTH;
	
	public static final int ASYM_KEY_LENGTH = 60;
	public static final int ASYM_BLOCK_LENGTH = ASYM_KEY_LENGTH;
	public static final int ASYM_SIGNATURE_LENGTH = ASYM_KEY_LENGTH;
	
	private static EncryptionInterface mEncryption = null;
	
	public static EncryptionInterface getEncryption() {
		return mEncryption;
	}
	
	public static void setEncryption(EncryptionInterface crypto) {
		mEncryption = crypto;
	}
}
