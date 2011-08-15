package uk.ac.cam.db538.cryptosms.ui;

import java.util.ArrayList;
import java.util.Collections;

import uk.ac.cam.db538.cryptosms.R;
import uk.ac.cam.db538.cryptosms.state.Pki;
import uk.ac.cam.db538.cryptosms.state.State;
import uk.ac.cam.db538.cryptosms.state.State.StateChangeListener;
import uk.ac.cam.db538.cryptosms.storage.Conversation;
import uk.ac.cam.db538.cryptosms.storage.Header;
import uk.ac.cam.db538.cryptosms.storage.StorageFileException;
import uk.ac.cam.db538.cryptosms.storage.Conversation.ConversationsChangeListener;
import android.app.Dialog;
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
	private DialogManager mDialogManager = new DialogManager();
	
	private void startConversation(Conversation conv) {
		Intent intent = new Intent(TabRecent.this, ConversationActivity.class);
		intent.putExtra(ConversationActivity.OPTION_PHONE_NUMBER, conv.getPhoneNumber());
		intent.putExtra(ConversationActivity.OPTION_OFFER_KEYS_SETUP, false);
		startActivity(intent);
	}
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.screen_main_listtab);

        final ListView listView = getListView();
        final LayoutInflater inflater = LayoutInflater.from(this);
        
        // set appearance of list view
		listView.setFastScrollEnabled(true);
		
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
    	// set adapter
    	mAdapterRecent = adapterContacts;
		// specify what to do when clicked on items
		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> adapterView, View view,	int arg2, long arg3) {
				TabRecentItem item = (TabRecentItem) view;
				Conversation conv;
	    		if ((conv = item.getConversationHeader()) != null) {
		    		// clicked on a conversation
    				startConversation(conv);
	    		}
			}
		});
    }

	private StateChangeListener mPkiStateListener = new StateChangeListener() {
		@Override
		public void onConnect() {
		}

		@Override
		public void onDisconnect() {
		}

		@Override
		public void onLogin() {
			mConversationChangeListener.onUpdate();
			setListAdapter(mAdapterRecent);
			mDialogManager.restoreState();
		}

		@Override
		public void onLogout() {
			setListAdapter(null);
			mDialogManager.saveState();
		}

		@Override
		public void onPkiMissing() {
		}

		@Override
		public void onFatalException(Exception ex) {
		}

		@Override
		public void onSimState() {
		}
	};
	
	private ConversationsChangeListener mConversationChangeListener = new ConversationsChangeListener() {
		
		@Override
		public void onUpdate() {
			try {
	    		mRecent.clear();
	
	    		Conversation conv = Header.getHeader().getFirstConversation();
	    		while (conv != null) {
	    			if (conv.getFirstMessageData() != null)
	    				mRecent.add(conv);
	    			conv = conv.getNextConversation();
	    		}
	    		
	    		Collections.sort(mRecent, Collections.reverseOrder());
	    		mAdapterRecent.notifyDataSetChanged();
			} catch (StorageFileException ex) {
				State.fatalException(ex);
				return;
			}
		}
	};

	@Override
	protected void onStart() {
		super.onStart();
		setListAdapter(null);
		State.addListener(mPkiStateListener);
		Conversation.addListener(mConversationChangeListener);
	}
	
	@Override
	protected void onStop() {
		State.removeListener(mPkiStateListener);
		Conversation.removeListener(mConversationChangeListener);
		super.onStop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		Pki.login(false);
	}

}
