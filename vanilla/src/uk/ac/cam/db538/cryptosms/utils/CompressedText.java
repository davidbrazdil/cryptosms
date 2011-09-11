package uk.ac.cam.db538.cryptosms.utils;

import java.util.zip.DataFormatException;

import uk.ac.cam.db538.cryptosms.crypto.Encryption;
import uk.ac.cam.db538.cryptosms.utils.Charset;
import uk.ac.cam.db538.cryptosms.utils.Compression;

public class CompressedText {
	public enum TextCharset {
		ASCII,
		UTF8,
		UTF16
	}

	protected static final byte HEADER_ASCII = (byte) 0x40;
	protected static final byte HEADER_UTF8 = (byte) 0x80;
	protected static final byte HEADER_UTF16 = (byte) 0xC0;
	protected static final byte FLAG_COMPRESSED = (byte) 0x20;
	protected static final byte FLAG_ALIGNED = (byte) 0x10;
	
	private TextCharset mCharset;
	private boolean mCompression;
	private byte[] mData;
	private String mString;
	
	private CompressedText() {
	}
	
	public static CompressedText fromString(String text) {
		CompressedText msg = new CompressedText();
		msg.mString = text;
		
		if (Charset.isConvertableToAscii(text)) {
			msg.mCharset = TextCharset.ASCII;
			
			byte[] dataAscii8Compressed = Compression.compressZ(Charset.toAscii8(text)); 

			int lengthAscii7 = Charset.computeLengthInAscii7(text);
			int lengthAscii8Compressed = dataAscii8Compressed.length;
			if (lengthAscii7 <= lengthAscii8Compressed) {
				msg.mCompression = false;
				msg.mData = Charset.toAscii7(text);
			} else {
				msg.mCompression = true;
				msg.mData = dataAscii8Compressed;
			}
		} else {
			// try UTF16 and UTF8
			byte[] dataUTF16 = Charset.toUTF16(text);
			byte[] dataUTF16Compressed = Compression.compressZ(dataUTF16);
			byte[] dataUTF8 = Charset.toUTF8(text);
			byte[] dataUTF8Compressed = Compression.compressZ(dataUTF8);
			
			// set to UTF16
			msg.mCharset = TextCharset.UTF16;
			msg.mCompression = false;
			msg.mData = dataUTF16;
			
			// try UTF16 + compression
			if (msg.mData.length > dataUTF16Compressed.length) {
				msg.mCharset = TextCharset.UTF16;
				msg.mCompression = true;
				msg.mData = dataUTF16Compressed;
			}
			
			// try UTF8
			if (msg.mData.length > dataUTF8.length) {
				msg.mCharset = TextCharset.UTF8;
				msg.mCompression = false;
				msg.mData = dataUTF8;
			}

			// try UTF8 + compression
			if (msg.mData.length > dataUTF8Compressed.length) {
				msg.mCharset = TextCharset.UTF8;
				msg.mCompression = true;
				msg.mData = dataUTF8Compressed;
			}
		}
		
		return msg;
	}
	
	public static CompressedText decode(byte[] data) throws DataFormatException {
		boolean aligned = ((data[0] & FLAG_ALIGNED) != 0x00);
		if (aligned) {
			// get the length of junk at the end
			int lengthJunk = LowLevel.getUnsignedByte((byte) (data[0] & 0x0F));
			// cut out the data
			data = LowLevel.cutData(data, 0, data.length - lengthJunk);
		}
		
		CompressedText msg = new CompressedText();
		msg.mCompression = ((data[0] & FLAG_COMPRESSED) != 0x00);
		msg.mData = LowLevel.cutData(data, 1, data.length - 1);
		
		byte[] dataDecompressed = null;
		if (msg.mCompression)
			dataDecompressed = Compression.decompressZ(msg.mData);
		else
			dataDecompressed = msg.mData;
		
		int header = (byte) (data[0] & 0xC0);
		switch (header) {
		case HEADER_ASCII:
			msg.mCharset = TextCharset.ASCII;
			msg.mString = (msg.mCompression) ? Charset.fromAscii8(dataDecompressed) : Charset.fromAscii7(dataDecompressed);
			break;
		case HEADER_UTF8:
			msg.mCharset = TextCharset.UTF8;
			msg.mString = Charset.fromUTF8(dataDecompressed);
			break;
		default:
		case HEADER_UTF16:
			msg.mCharset = TextCharset.UTF16;
			msg.mString = Charset.fromUTF16(dataDecompressed);
			break;
		}

		return msg;
	}
	
	public int getDataLength() {
		return mData.length + 1;
	}
	
	public byte[] getNormalData() {
		// add header to the front
		byte header = 0x00;
		switch (mCharset) {
		case ASCII:
			header |= HEADER_ASCII;
			break;
		case UTF8:
			header |= HEADER_UTF8;
			break;
		case UTF16:
			header |= HEADER_UTF16;
			break;
		}
			
		if (mCompression)
			header |= FLAG_COMPRESSED;
		
		byte[] data = new byte[mData.length + 1];
		data[0] = header;
		System.arraycopy(mData, 0, data, 1, mData.length);
		
		return data;
	}
	
	public byte[] getAlignedData() {
		int lengthData = mData.length + 1;
		int lengthJunk = (Encryption.SYM_BLOCK_LENGTH - (lengthData % Encryption.SYM_BLOCK_LENGTH)) % Encryption.SYM_BLOCK_LENGTH;
		byte[] normalData = getNormalData();
		
		// put in the ALIGNED flag
		normalData[0] |= FLAG_ALIGNED;
		// save the junk length in lower four bits
		normalData[0] |= LowLevel.getBytesUnsignedByte(lengthJunk) & 0x0F;
		// wrap the data and return
		return LowLevel.wrapData(normalData, lengthData + lengthJunk);
	}
	
	public String getMessage() {
		return mString;
	}
	
	public boolean isCompressed() {
		return mCompression;
	}
	
	public TextCharset getCharset() {
		return mCharset;
	}

	@Override
	public boolean equals(Object o) {
		try {
			CompressedText another = (CompressedText) o;
			return (this.mCharset == another.mCharset && 
					this.mCompression == another.mCompression &&
					this.mString.compareTo(another.mString) == 0);
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public String toString() {
		return getMessage();
	}
}
