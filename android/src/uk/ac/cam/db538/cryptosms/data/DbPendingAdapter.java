package uk.ac.cam.db538.cryptosms.data;

import java.util.ArrayList;

import org.joda.time.format.ISODateTimeFormat;

import uk.ac.cam.db538.cryptosms.MyApplication;
import uk.ac.cam.db538.cryptosms.data.Message.MessageType;
import uk.ac.cam.db538.cryptosms.utils.LowLevel;

import android.content.ContentValues;
import android.content.Context;
import android.database.*;
import android.database.sqlite.*;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.text.format.Time;
import android.util.Log;

public class DbPendingAdapter {
	private static final String DATABASE_NAME = "pending.db";
	private static final String DATABASE_TABLE = "sms";
	private static final int DATABASE_VERSION = 1;
 
  	// The index (key) column name for use in where clauses
	public static final String KEY_ID = "_id";
	public static final int COLUMN_ID = 0;
	
	// The name and column index of each column in your database
	public static final String KEY_SENDER = "sender"; 
	public static final int COLUMN_SENDER = 1;
	public static final String KEY_TIMESTAMP = "timeStamp"; 
	public static final int COLUMN_TIMESTAMP = 2;
	public static final String KEY_DATA = "data"; 
	public static final int COLUMN_DATA = 3;
	public static final String KEY_TYPE = "type"; 
	public static final int COLUMN_TYPE = 4;
	public static final String KEY_MSG_ID = "msgId"; 
	public static final int COLUMN_MSG_ID = 5;
  
  	// SQL Statement to create a new database
	private static final String DATABASE_CREATE_TABLE = "create table " + 
		DATABASE_TABLE + " (" + KEY_ID + 
		" integer primary key autoincrement, " +
		KEY_SENDER + " text not null, " + 
		KEY_TIMESTAMP + " datetime not null, " +
		KEY_DATA + " blob not null, " +
		KEY_TYPE + " integer, " +
		KEY_MSG_ID + " integer );";

  	// Variable to hold the database instance
	private SQLiteDatabase mDatabase;
	// Context of the application using the database.
	private final Context mContext;
	// Database open/upgrade helper
	private DbPendingHelper mHelper;

	public DbPendingAdapter(Context context) {
		mContext = context;
		mHelper = new DbPendingHelper(mContext, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public DbPendingAdapter open() throws SQLException {
		mDatabase = mHelper.getWritableDatabase();
		return this;
	}

	public void close() {
		mDatabase.close();
	}
	
	private ContentValues getValues(Pending pending) {
		ContentValues values = new ContentValues();
		values.put(KEY_SENDER, pending.getSender());
		values.put(KEY_TIMESTAMP, ISODateTimeFormat.dateTime().print(pending.getTimeStamp()));
		values.put(KEY_DATA, pending.getData());
		values.put(KEY_TYPE, pending.getType().ordinal());
		values.put(KEY_MSG_ID, pending.getId());
		return values;
	}
	
	private ArrayList<Pending> getPending(Cursor cursor) {
		ArrayList<Pending> list = new ArrayList<Pending>(); 
		if (cursor.moveToFirst()) {
			do {
				MessageType type = MessageType.UNKNOWN;
				try {
					type = MessageType.values()[cursor.getInt(COLUMN_TYPE)];
				} catch (IndexOutOfBoundsException e) {
				}
				
				Pending pending = new Pending(
						cursor.getString(COLUMN_SENDER),
						ISODateTimeFormat.dateTimeParser().parseDateTime(cursor.getString(COLUMN_TIMESTAMP)),
						cursor.getBlob(COLUMN_DATA),
						type,
						(byte) cursor.getInt(COLUMN_MSG_ID)
					);
				pending.setRowIndex(cursor.getLong(COLUMN_ID));
				list.add(pending);
			} while (cursor.moveToNext());
		}
		return list;
	}

	public long insertEntry(Pending pending) {
		pending.setRowIndex(mDatabase.insert(DATABASE_TABLE, null, getValues(pending)));
		return pending.getRowIndex();
	}

	public boolean removeEntry(Pending pending) {
		return mDatabase.delete(DATABASE_TABLE, KEY_ID + "=" + pending.getRowIndex(), null) > 0;
	}

	public Pending getEntry(long rowIndex) {
		Cursor cursor = mDatabase.query(DATABASE_TABLE, 
				                        null,
				                        KEY_ID + "=" + rowIndex,
				                        null,
				                        null,
				                        null,
				                        null);
		ArrayList<Pending> result = getPending(cursor);
		if (result.size() > 0)
			return result.get(0);
		else
			return null;
	}
	
	private ArrayList<Pending> getAllMatchingEntries(String where) {
		Cursor cursor = mDatabase.query(DATABASE_TABLE, 
		                                null,
		                                where,
		                                null,
		                                null,
		                                null,
		                                null);
		ArrayList<Pending> result = getPending(cursor);
		cursor.close();
		return result;
	}

	public ArrayList<Pending> getAllEntries() {
		return getAllMatchingEntries(null);
	}
	
	public ArrayList<Pending> getAllSenderEntries(String sender) {
		return getAllMatchingEntries(KEY_SENDER + "='" + sender + "'");
	}

	public ArrayList<Pending> getAllFirstParts() {
		return getAllMatchingEntries(KEY_TYPE + " IN ( " + MessageType.MESSAGE_FIRST.ordinal() + ", " + MessageType.KEYS_FIRST.ordinal() + ")");
	}

	public ArrayList<Pending> getAllParts(String sender, MessageType type, int id) {
		return getAllMatchingEntries(KEY_SENDER + "='" + sender + "' AND " + KEY_TYPE + "=" + type.ordinal() + " AND " + KEY_MSG_ID + "=" + id);
	}

	public boolean updateEntry(Pending pending) {
		return mDatabase.update(DATABASE_TABLE, getValues(pending), KEY_ID + "=" + pending.getRowIndex(), null) > 0;
	}

	private static class DbPendingHelper extends SQLiteOpenHelper {

		public DbPendingHelper(Context context, String name, 
                          CursorFactory factory, int version) {
			super(context, name, factory, version);
		}

		// Called when no database exists in disk and the helper class needs
		// to create a new one. 
		@Override
		public void onCreate(SQLiteDatabase _db) {
			_db.execSQL(DATABASE_CREATE_TABLE);
		}

		// Called when there is a database version mismatch meaning that the version
		// of the database on disk needs to be upgraded to the current version.
		@Override
		public void onUpgrade(SQLiteDatabase _db, int _oldVersion, int _newVersion) {
			// Log the version upgrade.
			Log.w("TaskDBAdapter", "Upgrading from version " + 
                             _oldVersion + " to " +
                             _newVersion + ", which will destroy all old data");
        
			// Upgrade the existing database to conform to the new version. Multiple 
			// previous versions can be handled by comparing _oldVersion and _newVersion
			// values.
			
			// The simplest case is to drop the old table and create a new one.
      		_db.execSQL("DROP TABLE IF EXISTS " + DATABASE_TABLE);
      		
      		// Create a new one.
      		onCreate(_db);
		}
	}
}

