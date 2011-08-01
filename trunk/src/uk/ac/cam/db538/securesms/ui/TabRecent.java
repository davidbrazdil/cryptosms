package uk.ac.cam.db538.securesms.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import uk.ac.cam.db538.securesms.R;
import uk.ac.cam.db538.securesms.data.Utils;
import uk.ac.cam.db538.securesms.storage.Conversation;
import uk.ac.cam.db538.securesms.storage.Header;
import uk.ac.cam.db538.securesms.storage.SessionKeys;
import uk.ac.cam.db538.securesms.storage.StorageFileException;
import uk.ac.cam.db538.securesms.storage.Conversation.ConversationUpdateListener;
import uk.ac.cam.db538.securesms.storage.SessionKeys.SessionKeysStatus;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class TabRecent extends ListActivity {
		
	private ArrayList<Conversation> mRecent = new ArrayList<Conversation>();;
	private ArrayAdapter<Conversation> mAdapterRecent;
	
	private void updateContacts() throws StorageFileException, IOException {
		mRecent.clear();

		Conversation conv = Header.getHeader().getFirstConversation();
		while (conv != null) {
			if (conv.getFirstMessage() != null)
				mRecent.add(conv);
			conv = conv.getNextConversation();
		}
		
		Collections.sort(mRecent, Collections.reverseOrder());
	}
	
	private void startConversation(Conversation conv) {
		Intent intent = new Intent(TabRecent.this, ConversationActivity.class);
		intent.putExtra(ConversationActivity.OPTION_PHONE_NUMBER, conv.getPhoneNumber());
		intent.putExtra(ConversationActivity.OPTION_OFFER_KEYS_SETUP, false);
		startActivity(intent);
	}	
	
	public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_main_recent);

        final Context context = this;
        final ListView listView = getListView();
        final LayoutInflater inflater = LayoutInflater.from(this);
        
        // set appearance of list view
		listView.setFastScrollEnabled(true);
		
        try {
        	// initialize the list of conversations
        	updateContacts();
        	// create the adapter
        	final ArrayAdapter<Conversation> adapterContacts = new ArrayAdapter<Conversation>(this, R.layout.item_main_contacts, mRecent) {
        		@Override
				public View getView(int position, View convertView, ViewGroup parent) {
					TabRecentItem row;

					if (convertView == null)
						row = (TabRecentItem) inflater.inflate(R.layout.item_main_recent, listView, false);
					else
						row = (TabRecentItem) convertView;
				    
					row.bind(getItem(position));
					return row;
				}
			};
        	// add listeners			
        	Conversation.addUpdateListener(new ConversationUpdateListener() {
				public void onUpdate() {
					try {
						updateContacts();
						adapterContacts.notifyDataSetChanged();
					} catch (StorageFileException ex) {
						Utils.dialogDatabaseError(context, ex);
					} catch (IOException ex) {
						Utils.dialogIOError(context, ex);
					}
				}
			});
        	// set adapter
        	mAdapterRecent = adapterContacts;
			setListAdapter(mAdapterRecent);
			// specify what to do when clicked on items
			listView.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> adapterView, View view,	int arg2, long arg3) {
					TabRecentItem item = (TabRecentItem) view;
					Conversation conv;
		    		if ((conv = item.getConversationHeader()) != null) {
			    		// clicked on a conversation
	    				startConversation(conv);
		    		}
				}
			});
		} catch (StorageFileException ex) {
			Utils.dialogDatabaseError(this, ex);
		} catch (IOException ex) {
			Utils.dialogIOError(this, ex);
		}
    }
}
