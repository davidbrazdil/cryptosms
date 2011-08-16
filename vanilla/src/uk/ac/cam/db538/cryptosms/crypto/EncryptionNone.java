package uk.ac.cam.db538.cryptosms.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import uk.ac.cam.db538.cryptosms.utils.LowLevel;

public class EncryptionNone implements EncryptionInterface {
	private static final String HASHING_ALGORITHM = "SHA-256";
	private static SecureRandom mRandom = null;

	public static void initEncryption() {
		Encryption.setEncryption(new EncryptionNone());
	}
	
	@Override
	public byte[] decryptSymmetric(byte[] data, byte[] key)
			throws EncryptionException {
		byte[] dataDecrypted = LowLevel.cutData(data, Encryption.SYM_OVERHEAD, data.length - Encryption.SYM_OVERHEAD);
		byte[] hashSaved = LowLevel.cutData(data, 0, Encryption.SYM_MAC_LENGTH);
		byte[] hashReal = getHash(dataDecrypted);
		
		for (int i = 0; i < Encryption.SYM_MAC_LENGTH; ++i)
			if (hashSaved[i] != hashReal[i])
				throw new EncryptionException(new Exception(LowLevel.toHex(dataDecrypted)));
		return dataDecrypted;
	}

	@Override
	public byte[] decryptSymmetricWithMasterKey(byte[] data)
			throws EncryptionException {
		return decryptSymmetricWithMasterKey(data, false);
	}

	@Override
	public byte[] encryptSymmetric(byte[] data, byte[] key)
			throws EncryptionException {
		int alignedLength = Encryption.getEncryption().getSymmetricAlignedLength(data.length);
		byte[] buffer = new byte[alignedLength + Encryption.SYM_MAC_LENGTH + Encryption.SYM_IV_LENGTH];
		data = LowLevel.wrapData(data, alignedLength);
		System.arraycopy(getHash(data), 0, buffer, 0, Encryption.SYM_MAC_LENGTH);
		for (int i = 0; i < Encryption.SYM_IV_LENGTH; ++i)
			buffer[Encryption.SYM_MAC_LENGTH + i] = (byte) 0x49;
		System.arraycopy(data, 0, buffer, Encryption.SYM_OVERHEAD, alignedLength);
		return buffer;
	}

	@Override
	public byte[] encryptSymmetricWithMasterKey(byte[] data)
			throws EncryptionException {
		return encryptSymmetricWithMasterKey(data, false);
	}

	@Override
	public byte[] generateRandomData(int length) {
		if (mRandom == null)
			try {
				mRandom = SecureRandom.getInstance("SHA1PRNG");
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		byte[] data = new byte[length];
		mRandom.nextBytes(data);
		return data;
	}

	@Override
	public int getSymmetricAlignedLength(int length) {
		return length + (Encryption.SYM_BLOCK_LENGTH - (length % Encryption.SYM_BLOCK_LENGTH)) % Encryption.SYM_BLOCK_LENGTH;
	}

	@Override
	public int getSymmetricEncryptedLength(int length) {
		return getSymmetricAlignedLength(length) + Encryption.SYM_OVERHEAD;
	}

	@Override
	public byte[] getHash(byte[] data) {
		try {
			MessageDigest digester = MessageDigest.getInstance(HASHING_ALGORITHM);
			return digester.digest(data);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public byte[] encryptSymmetricWithMasterKey(byte[] data, boolean forceLogIn)
			throws EncryptionException {
		return encryptSymmetric(data, null);
	}

	@Override
	public byte[] decryptSymmetricWithMasterKey(byte[] data, boolean forceLogIn)
			throws EncryptionException {
		return decryptSymmetric(data, null);
	}

	@Override
	public byte[] encryptAsymmetric(byte[] dataPlain, long contactId,
			String contactKey) throws EncryptionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] decryptAsymmetric(byte[] dataEncrypted, long contactId,
			String contactKey) throws EncryptionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public byte[] sign(byte[] dataEncrypted) throws EncryptionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getAsymmetricEncryptedLength(int length) {
		return getAsymmetricAlignedLength(length);
	}

	@Override
	public int getAsymmetricAlignedLength(int length) {
		return length + (Encryption.ASYM_BLOCK_LENGTH - (length % Encryption.ASYM_BLOCK_LENGTH)) % Encryption.ASYM_BLOCK_LENGTH;
	}
}
