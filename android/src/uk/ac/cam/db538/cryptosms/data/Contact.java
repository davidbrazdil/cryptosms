/*
 *   Copyright 2011 David Brazdil
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package uk.ac.cam.db538.cryptosms.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import uk.ac.cam.db538.cryptosms.R;
import uk.ac.cam.db538.cryptosms.ui.UtilsSimIssues;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.telephony.PhoneNumberUtils;

/*
 * Class handling a single contact in the database
 */
public class Contact implements Comparable<Contact> {
	
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
                    + "(SELECT raw_contact_id "
                    + " FROM phone_lookup"
                    + " WHERE normalized_number GLOB('+*'))";

    private static final Uri PHONES_WITH_PRESENCE_URI = Data.CONTENT_URI;

    private static final String[] CALLER_ID_PROJECTION = new String[] {
            Phone.NUMBER,                   // 0
            Phone.LABEL,                    // 1
            Phone.DISPLAY_NAME,             // 2
            Phone.CONTACT_ID,               // 3
            Phone.CONTACT_PRESENCE,         // 4
            Phone.CONTACT_STATUS,           // 5
    };

    @SuppressWarnings("unused")
	private static final int PHONE_NUMBER_COLUMN = 0;
    private static final int PHONE_LABEL_COLUMN = 1;
    private static final int CONTACT_NAME_COLUMN = 2;
    private static final int CONTACT_ID_COLUMN = 3;
    @SuppressWarnings("unused")
	private static final int CONTACT_PRESENCE_COLUMN = 4;
    @SuppressWarnings("unused")
	private static final int CONTACT_STATUS_COLUMN = 5;
    
    private static ArrayList<Contact> mCacheContact = new ArrayList<Contact>();
    
    private String mPhoneNumber;
    private String mLabel;
    private String mName;
    private long mPersonId;
    private BitmapDrawable mAvatar;
    byte[] mAvatarData;
    
	private Contact(String phoneNumber) {
		setPhoneNumber(phoneNumber);
        setName("");
        setLabel("");
        mPersonId = 0;
	}
	
	private Contact(long contactId) {
		setPhoneNumber("");
        setName("");
        setLabel("");
        mPersonId = contactId;
	}

    /**
     * Gets avatar picture of the user
     *
     * @param context the context
     * @param defaultValue the default value
     * @return the avatar
     */
    public synchronized Drawable getAvatar(Context context, Drawable defaultValue) {
        if (mAvatar == null) {
            if (mAvatarData != null) {
                Bitmap b = BitmapFactory.decodeByteArray(mAvatarData, 0, mAvatarData.length);
                mAvatar = new BitmapDrawable(context.getResources(), b);
            }
        }
        return mAvatar != null ? mAvatar : defaultValue;
    }
    
    /**
     * Returns whether this contact is stored in the database
     *
     * @return true, if successful
     */
    public synchronized boolean existsInDatabase() {
        return (mPersonId > 0);
    }    

    public synchronized Uri getUri() {
        return ContentUris.withAppendedId(Contacts.CONTENT_URI, mPersonId);
    }

    // GETTERS / SETTERS

    public String getPhoneNumber() {
		return mPhoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.mPhoneNumber = phoneNumber;
	}

	public String getLabel() {
		return mLabel;
	}

	public void setLabel(String label) {
		this.mLabel = label;
	}

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		this.mName = name;
	}
	
	public long getId() {
		return this.mPersonId;
	}

	// STATIC STUFF
	
	/**
	 * Queries the caller id info with the phone number.
	 *
	 * @param context the context
	 * @param phoneNumber the phone number
	 * @return a Contact containing the caller id info corresponding to the number.
	 */
    public static Contact getContact(Context context, String phoneNumber) {
        phoneNumber = UtilsSimIssues.formatPhoneNumber(phoneNumber);
        
        // check whether it is already in the cache
    	for (Contact contact : mCacheContact)
    		if (PhoneNumberUtils.compare(contact.getPhoneNumber(), phoneNumber))
    			return contact;

    	// if it's not, create a new one
    	Contact entry = new Contact(phoneNumber);
    	mCacheContact.add(entry);

        // We need to include the phone number in the selection string itself rather then
        // selection arguments, because SQLite needs to see the exact pattern of GLOB
        // to generate the correct query plan
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                PHONES_WITH_PRESENCE_URI,
                CALLER_ID_PROJECTION,
                selection,
                new String[] { phoneNumber },
                null);

        if (cursor == null) {
            return entry;
        }

        try {
            if (cursor.moveToFirst()) {
                synchronized (entry) {
                    entry.setLabel(cursor.getString(PHONE_LABEL_COLUMN));
                    entry.setName(cursor.getString(CONTACT_NAME_COLUMN));
                    entry.mPersonId = cursor.getLong(CONTACT_ID_COLUMN);
                }

                byte[] data = loadAvatarData(context, entry);

                synchronized (entry) {
                    entry.mAvatarData = data;
                }
            }
        } finally {
            cursor.close();
        }

        return entry;
    }

	/**
	 * Queries the caller id info with the phone number.
	 *
	 * @param context the context
	 * @param phoneNumber the phone number
	 * @param contactId the contact id
	 * @return a Contact containing the caller id info corresponding to the number.
	 */
    public static Contact getContact(Context context, String phoneNumber, long contactId) {
        phoneNumber = UtilsSimIssues.formatPhoneNumber(phoneNumber);
        
        // check whether it is already in the cache
    	for (Contact contact : mCacheContact)
    		if (PhoneNumberUtils.compare(contact.getPhoneNumber(), phoneNumber))
    			return contact;

    	// if it's not, create a new one
    	Contact entry = new Contact(phoneNumber);

        // We need to include the phone number in the selection string itself rather then
        // selection arguments, because SQLite needs to see the exact pattern of GLOB
        // to generate the correct query plan
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));
        Cursor cursor = context.getContentResolver().query(
                PHONES_WITH_PRESENCE_URI,
                CALLER_ID_PROJECTION,
                selection,
                new String[] { phoneNumber },
                null);

        if (cursor == null) {
            return entry;
        }

    	boolean found = false;
        try {
            if (cursor.moveToFirst()) {
            	do {
	                synchronized (entry) {
	                    entry.setLabel(cursor.getString(PHONE_LABEL_COLUMN));
	                    entry.setName(cursor.getString(CONTACT_NAME_COLUMN));
	                    entry.mPersonId = cursor.getLong(CONTACT_ID_COLUMN);
	                }
	
                    if (entry.mPersonId == contactId) {
                    	found = true;
                    	byte[] data = loadAvatarData(context, entry);
		                synchronized (entry) {
		                    entry.mAvatarData = data;
		                }
                    }
            	} while (cursor.moveToNext() && !found);
            }
        } finally {
            cursor.close();
        }

        if (!found)
        	return null;
        
    	mCacheContact.add(entry);
        return entry;
    }
    
    /*
     * Load the avatar data from the cursor into memory.  Don't decode the data
     * until someone calls for it (see getAvatar).  Hang onto the raw data so that
     * we can compare it when the data is reloaded.
     */
    private static byte[] loadAvatarData(Context context, Contact entry) {
        byte [] data = null;

        if (entry.mPersonId == 0 || entry.mAvatar != null) {
            return null;
        }

        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, entry.mPersonId);

        InputStream avatarDataStream = Contacts.openContactPhotoInputStream(
                    context.getContentResolver(),
                    contactUri);
        try {
            if (avatarDataStream != null) {
                data = new byte[avatarDataStream.available()];
                avatarDataStream.read(data, 0, data.length);
            }
        } catch (IOException ex) {
            //
        } finally {
            try {
                if (avatarDataStream != null) {
                    avatarDataStream.close();
                }
            } catch (IOException e) {
            }
        }

        return data;
    }
    
    public static class PhoneNumber {
    	private int mType;
    	private String mPhoneNumber;
    	private Context mContext;
    	
    	/**
	     * Instantiates a new phone number.
	     *
	     * @param context the context
	     * @param type the type
	     * @param phoneNumber the phone number
	     */
	    public PhoneNumber(Context context, int type, String phoneNumber) {
    		setType(type);
    		setPhoneNumber(phoneNumber);
    		mContext = context;
    	}
    	
		public void setType(int type) {
			this.mType = type;
		}
		public int getType() {
			return mType;
		}
		
		public void setPhoneNumber(String phoneNumber) {
			this.mPhoneNumber = phoneNumber;
		}
		
		public String getPhoneNumber() {
			return mPhoneNumber;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			Resources res = mContext.getResources();
			String type = "";
			switch (mType) {
			case Phone.TYPE_HOME:
				type += res.getString(R.string.contacts_type_home);
				break;
			case Phone.TYPE_WORK:
				type += res.getString(R.string.contacts_type_work);
				break;
			case Phone.TYPE_MOBILE:
				type += res.getString(R.string.contacts_type_mobile);
				break;
			}
			return mPhoneNumber + " (" + type + ")";
		}
    }
    
    /**
     * Gets all phone numbers of a given contact.
     *
     * @param context the context
     * @param contactId the contact id
     * @return the phone numbers
     */
    public static ArrayList<PhoneNumber> getPhoneNumbers(Context context, long contactId) {
    	ContentResolver cr = context.getContentResolver();
    	ArrayList<PhoneNumber> list = new ArrayList<PhoneNumber>();
        Cursor phones = cr.query(Phone.CONTENT_URI, null,
            Phone.CONTACT_ID + " = " + contactId, null, null);
        while (phones.moveToNext()) {
            list.add(new PhoneNumber(
            	context,
            	phones.getInt(phones.getColumnIndex(Phone.TYPE)), 
            	phones.getString(phones.getColumnIndex(Phone.NUMBER))
            ));
        }
        phones.close();
        return list;
    }

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(Contact another) {
		String nameThis = (this.existsInDatabase() ? this.getName() : this.getPhoneNumber());
		String nameAnother = (another.existsInDatabase() ? another.getName() : another.getPhoneNumber());
		return nameThis.compareToIgnoreCase(nameAnother);
	}
}
