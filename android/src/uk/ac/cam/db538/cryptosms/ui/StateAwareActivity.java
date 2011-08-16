package uk.ac.cam.db538.cryptosms.ui;

import uk.ac.cam.db538.cryptosms.state.Pki;
import uk.ac.cam.db538.cryptosms.state.State;
import uk.ac.cam.db538.cryptosms.state.State.StateChangeListener;
import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

public class StateAwareActivity extends Activity {
	private static final String BUNDLE_DIALOG_MANAGER = "DIALOG_MANAGER";

	private ErrorOverlay mErrorOverlay;
	private View mMainView;
	private DialogManager mDialogManager = new DialogManager();
	
	// CONTENT STUFF
	
	@Override
	public void setContentView(View view, LayoutParams params) {
		mMainView = view;
		mErrorOverlay = new ErrorOverlay(this);
		mErrorOverlay.setMainView(mMainView);
		getErrorOverlay().hide();
		
		FrameLayout layoutRoot = new FrameLayout(this);
		layoutRoot.addView(mMainView, params);
		layoutRoot.addView(mErrorOverlay, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		
		super.setContentView(layoutRoot);
	}

	@Override
	public void setContentView(View view) {
		setContentView(view, new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
	}

	@Override
	public void setContentView(int layoutResID) {
		setContentView(LayoutInflater.from(this).inflate(layoutResID, null));
	}

	@Override
	public View findViewById(int id) {
		return mMainView.findViewById(id);
	}

	public ErrorOverlay getErrorOverlay() {
		return mErrorOverlay;
	}

	public View getMainView() {
		return mMainView;
	}
	
	public DialogManager getDialogManager() {
		return mDialogManager;
	}
	
	// STATE LISTENER

	private StateChangeListener mStateListener = new StateChangeListener() {
		@Override
		public void onSimState() {
			StateAwareActivity.this.onSimState();
		}
		
		@Override
		public void onPkiMissing() {
			StateAwareActivity.this.onPkiMissing();
		}
		
		@Override
		public void onLogout() {
			StateAwareActivity.this.onPkiLogout();
		}
		
		@Override
		public void onLogin() {
			StateAwareActivity.this.onPkiLogin();
		}
		
		@Override
		public void onFatalException(Exception ex) {
			StateAwareActivity.this.onFatalException(ex);
		}
		
		@Override
		public void onDisconnect() {
			StateAwareActivity.this.onPkiDisconnect();
		}
		
		@Override
		public void onConnect() {
			StateAwareActivity.this.onPkiConnect();
		}
	};
	
	public void onSimState() {
		
	}
	
	public void onPkiConnect() {

	}
	
	public void onPkiDisconnect() {
		getErrorOverlay().modeDisconnected();
		getErrorOverlay().show();
		this.closeContextMenu();
		this.closeOptionsMenu();
	}
	
	public void onPkiLogin() {
		getErrorOverlay().hide();
        mDialogManager.restoreState();
	}
	
	public void onPkiLogout() {
		getErrorOverlay().modeLogin();
		getErrorOverlay().show();
        mDialogManager.saveState();
		this.closeContextMenu();
		this.closeOptionsMenu();
	}
	
	public void onPkiMissing() {
		getErrorOverlay().modePkiMissing();
		getErrorOverlay().show();
		this.closeContextMenu();
		this.closeOptionsMenu();
	}
	
	public void onFatalException(Exception ex) {
		getErrorOverlay().modeFatalException(ex);
		getErrorOverlay().show();
		this.closeContextMenu();
		this.closeOptionsMenu();
	}

	// OVERRIDES
	
	@Override
	protected void onStart() {
		super.onStart();
		State.addListener(mStateListener);
		Pki.login(false);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		State.removeListener(mStateListener);
	}

	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		
		mDialogManager.setSavedState(state.getBundle(BUNDLE_DIALOG_MANAGER));
		mDialogManager.restoreState();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		mDialogManager.saveState();
		outState.putBundle(BUNDLE_DIALOG_MANAGER, mDialogManager.getSavedState());
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
		return Pki.isLoggedIn();
	}
	
	
}
