package uk.ac.cam.db538.cryptosms.data;

import java.security.MessageDigest;
import java.util.ArrayList;

import uk.ac.cam.db538.cryptosms.MyApplication;
import uk.ac.cam.db538.cryptosms.crypto.EllipticCurveDeffieHellman;
import uk.ac.cam.db538.cryptosms.crypto.Encryption;
import uk.ac.cam.db538.cryptosms.crypto.EncryptionInterface.EncryptionException;
import uk.ac.cam.db538.cryptosms.crypto.EncryptionInterface.WrongKeyDecryptionException;
import uk.ac.cam.db538.cryptosms.data.PendingParser.ParseResult;
import uk.ac.cam.db538.cryptosms.data.PendingParser.PendingParseResult;
import uk.ac.cam.db538.cryptosms.storage.Conversation;
import uk.ac.cam.db538.cryptosms.storage.MessageData;
import uk.ac.cam.db538.cryptosms.storage.SessionKeys;
import uk.ac.cam.db538.cryptosms.storage.StorageFileException;
import uk.ac.cam.db538.cryptosms.utils.LowLevel;

public class KeysMessage extends Message {
	
	// Set handshake validity period to 5 days (4 - 6 days with different time zones)
	public static final long HANDSHAKE_VALIDITY_PERIOD = 5L * 24L * 60L * 60L * 1000L;
	// Clock tolerance is 5 mins
	public static final long CLOCK_TOLERANCE = 5L * 60L * 1000L;
	
	protected static final int OFFSET_DATA = OFFSET_HEADER + LENGTH_HEADER;
	protected static final int LENGTH_DATA = MessageData.LENGTH_MESSAGE - OFFSET_DATA;
	
	public static final int LENGTH_TIMESTAMP = 8;
	public static final int OFFSET_PUBLIC_KEY = 0;
	public static final int OFFSET_TIMESTAMP = EllipticCurveDeffieHellman.LENGTH_PUBLIC_KEY;
	public static final int OFFSET_SIGNATURE = OFFSET_TIMESTAMP + LENGTH_TIMESTAMP;
	public static final int LENGTH_CONTENT = OFFSET_SIGNATURE + Encryption.ASYM_SIGNATURE_LENGTH;
	
	private byte[] mPublicKey;
	private byte[] mPrivateKey;
	private long mTimeStamp;

	private byte[] mOtherPublicKey;
	private long mOtherTimeStamp;
	
	private boolean mIsConfirmation;
		
	EllipticCurveDeffieHellman mECDH;
	
	public KeysMessage() throws StorageFileException {
		mIsConfirmation = false;
		
		mECDH = new EllipticCurveDeffieHellman();
		mPublicKey = mECDH.getPublicKey();
		mPrivateKey = mECDH.getPrivateKey();
		mTimeStamp = getCurrentTimeUTC();
	}
	
	public KeysMessage(long originalTimeStamp, byte[] privateKey, long otherTimeStamp, byte[] otherPublicKey) {
		mIsConfirmation = false;
		
		mECDH = new EllipticCurveDeffieHellman(privateKey);
		mPublicKey = mECDH.getPublicKey();
		mPrivateKey = mECDH.getPrivateKey();
		mTimeStamp = originalTimeStamp;
		
		mOtherTimeStamp = otherTimeStamp;
		mOtherPublicKey = otherPublicKey;
	}
	
	public KeysMessage(long otherTimeStamp, byte[] otherPublicKey) throws StorageFileException {
		mIsConfirmation = true;
		
		mECDH = new EllipticCurveDeffieHellman();
		mPublicKey = mECDH.getPublicKey();
		mPrivateKey = mECDH.getPrivateKey();
		mTimeStamp = getCurrentTimeUTC();

		mOtherTimeStamp = otherTimeStamp;
		mOtherPublicKey = otherPublicKey;
	}
	
	public byte[] getPublicKey() {
		return mPublicKey;
	}
	
	public byte[] getPrivateKey() {
		return mPrivateKey;
	}
	
	public long getTimeStamp() {
		return mTimeStamp;
	}
	
	private byte[] getKey(String prefix) {
		return Encryption.getEncryption().getHash(
				(prefix + mECDH.getSharedKey(mOtherPublicKey).toString()).getBytes() 
			);
	}
	
	public byte[] getKeyOut() {
		return getKey(mIsConfirmation ? "0" : "1");
	}
	
	public byte[] getKeyIn() {
		return getKey(mIsConfirmation ? "1" : "0");
	}
	
	public boolean isConfirmation() {
		return mIsConfirmation;
	}

	/**
	 * Returns data ready to be sent via SMS
	 * @return
	 * @throws StorageFileException 
	 * @throws MessageException 
	 * @throws EncryptionException 
	 */
	@Override
	public ArrayList<byte[]> getBytes() throws StorageFileException, MessageException, EncryptionException {
		MessageDigest hashing = Encryption.getEncryption().getHashingFunction();
		if (mIsConfirmation) {
			hashing.update(getOtherHeader());
			hashing.update(LowLevel.getBytesLong(mOtherTimeStamp));
			hashing.update(mOtherPublicKey);
		}
		byte[] timeStampBytes = LowLevel.getBytesLong(mTimeStamp);

		hashing.update(getHeader());
		hashing.update(timeStampBytes);
		hashing.update(mPublicKey);

		byte[] hash = hashing.digest();
		byte[] signature = Encryption.getEncryption().sign(hash);
		
		byte[] data = new byte[LENGTH_CONTENT];
		System.arraycopy(mPublicKey, 0, data, OFFSET_PUBLIC_KEY, EllipticCurveDeffieHellman.LENGTH_PUBLIC_KEY);
		System.arraycopy(timeStampBytes, 0, data, OFFSET_TIMESTAMP, LENGTH_TIMESTAMP);
		System.arraycopy(signature, 0, data, OFFSET_SIGNATURE, Encryption.ASYM_SIGNATURE_LENGTH);
		
		ArrayList<byte[]> dataSms = new ArrayList<byte[]>(1);
		dataSms.add(data);
		return dataSms;
	}
	
	public static ParseResult parseKeysMessage(ArrayList<Pending> idGroup) {
		try {
			// check the sender
			Contact contact = Contact.getContact(MyApplication.getSingleton().getApplicationContext(), idGroup.get(0).getSender());
			if (!contact.existsInDatabase())
				return new ParseResult(idGroup, PendingParseResult.UNKNOWN_SENDER, null);

			if (idGroup.size() != getPartsCount())
				return new ParseResult(idGroup, PendingParseResult.REDUNDANT_PARTS, null);
			
			byte[] dataAll = idGroup.get(0).getData();
			byte[] dataRelevant = LowLevel.cutData(dataAll, OFFSET_DATA, LENGTH_DATA);
			String sender = idGroup.get(0).getSender();
			byte header = getMessageHeader(dataAll);
			MessageType type = getMessageType(dataAll);
	
			byte[] publicKey = LowLevel.cutData(dataRelevant, OFFSET_PUBLIC_KEY, EllipticCurveDeffieHellman.LENGTH_PUBLIC_KEY);
			byte[] timeStampBytes = LowLevel.cutData(dataRelevant, OFFSET_TIMESTAMP, LENGTH_TIMESTAMP);
			byte[] signature = LowLevel.cutData(dataRelevant, OFFSET_SIGNATURE, Encryption.ASYM_SIGNATURE_LENGTH);
			
			long timeStamp = LowLevel.getLong(timeStampBytes);
			
			// check the time stamp isn't too old or in the future
			long now = getCurrentTimeUTC();
			if (timeStamp > now + CLOCK_TOLERANCE)
				return new ParseResult(idGroup, PendingParseResult.TIMESTAMP_IN_FUTURE, null);
			else if (now - timeStamp > HANDSHAKE_VALIDITY_PERIOD) 
				return new ParseResult(idGroup, PendingParseResult.TIMESTAMP_OLD, null);
			
			if (type == MessageType.HANDSHAKE) {
				MessageDigest hashing = Encryption.getEncryption().getHashingFunction();
				hashing.update(header);
				hashing.update(timeStampBytes);
				hashing.update(publicKey);
				
				byte[] hash = hashing.digest();

				// cut out the rubbish part at the end
				dataRelevant = LowLevel.cutData(dataRelevant, 0, LENGTH_CONTENT);
				
				// check the signature
				boolean signatureVerified = false;
				try {
					signatureVerified = Encryption.getEncryption().verify(hash, signature, contact.getId());
				} catch (EncryptionException e) {
				} catch (WrongKeyDecryptionException e) {
				}
				if (!signatureVerified)
					return new ParseResult(idGroup, PendingParseResult.COULD_NOT_VERIFY, null);
				
				// all seems to be fine, so just retrieve the keys and return the result
				return new ParseResult(idGroup, 
				                            PendingParseResult.OK_HANDSHAKE_MESSAGE, 
				                            new KeysMessage(
				                            	timeStamp,
				                            	publicKey
				                            ));
			} else if (type == MessageType.CONFIRM) {
				// find the session keys for this person
				SessionKeys keys = Conversation.getConversation(sender).getSessionKeys(SimCard.getSingleton().getNumber());
				if (keys == null)
					return new ParseResult(idGroup, PendingParseResult.COULD_NOT_VERIFY, null);
				
				MessageDigest hashing = Encryption.getEncryption().getHashingFunction();
				hashing.update(HEADER_HANDSHAKE);
				hashing.update(LowLevel.getBytesLong(keys.getTimeStamp()));
				hashing.update(new EllipticCurveDeffieHellman(keys.getPrivateKey()).getPublicKey());
				hashing.update(header);
				hashing.update(timeStampBytes);
				hashing.update(publicKey);

				byte[] hash = hashing.digest();
				
				// check the signature
				boolean signatureVerified = false;
				try {
					signatureVerified = Encryption.getEncryption().verify(hash, signature, contact.getId());
				} catch (EncryptionException e) {
				} catch (WrongKeyDecryptionException e) {
				}
				if (!signatureVerified)
					return new ParseResult(idGroup, PendingParseResult.COULD_NOT_VERIFY, null);
				
				// all seems to be fine, so save the result
                KeysMessage keysMsg = new KeysMessage(
                    	keys.getTimeStamp(),
                    	keys.getPrivateKey(),
                    	timeStamp,
                    	publicKey
                    );
                
                keys.setSessionKey_Out(keysMsg.getKeyOut());
                keys.setSessionKey_In(keysMsg.getKeyIn());
                keys.setNextID_Out((byte) 0);
                keys.setLastID_In((byte) 0);
                keys.setKeysConfirmed(true);
                keys.setPrivateKey(Encryption.getEncryption().generateRandomData(EllipticCurveDeffieHellman.LENGTH_PRIVATE_KEY));
                keys.setTimeStamp(0L);
                keys.saveToFile();
				
				return new ParseResult(idGroup, 
				                       PendingParseResult.OK_CONFIRM_MESSAGE,
				                       keysMsg);
			} else
				return new ParseResult(idGroup, PendingParseResult.COULD_NOT_DECRYPT, null);
		} catch (StorageFileException e) {
			return new ParseResult(idGroup, PendingParseResult.INTERNAL_ERROR, null);
		}
	}
	
	private static long getCurrentTimeUTC() {
		return System.currentTimeMillis();
	}

	@Override
	public byte getHeader() {
		if (mIsConfirmation)
			return HEADER_CONFIRM;
		else
			return HEADER_HANDSHAKE;
	}

	public byte getOtherHeader() {
		if (mIsConfirmation)
			return HEADER_HANDSHAKE;
		else
			return HEADER_CONFIRM;
	}

	@Override
	public int getMessagePartCount() {
		return getPartsCount();
	}

	public static int getPartsCount() {
		return 1;
	}

	public static long getMessageTimeStamp(byte[] data) {
		return LowLevel.getLong(LowLevel.cutData(data, OFFSET_TIMESTAMP, LENGTH_TIMESTAMP));
	}
}
