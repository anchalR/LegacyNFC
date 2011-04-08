package mobisocial.nfc.legacy;


import org.apache.commons.codec.binary.Base64;

import mobisocial.nfc.R;
import mobisocial.nfc.ndefexchange.ConnectionHandoverManager;
import mobisocial.nfc.util.NdefHelper;
import mobisocial.nfc.util.QR;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class NfcBridgeActivity extends Activity {
	protected static final String TAG = "nfcserver";
	protected static final String ACTION_UPDATE = "mobisocial.intent.UPDATE";
	protected static final int QR_NFC_PAIR = 345;
	private TextView mStatusView = null;
	private Button mToggleButton = null;
	private Button mConfigButton = null;
	private Button mPairButton = null;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(ACTION_UPDATE);
        
        mToggleButton = (Button)findViewById(R.id.toggle);
        mConfigButton = (Button)findViewById(R.id.config);
        mPairButton = (Button)findViewById(R.id.pair);
        mStatusView = (TextView)findViewById(R.id.status);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
	    doBindService();
	    registerReceiver(mUpdateReceiver, mIntentFilter);
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    	unregisterReceiver(mUpdateReceiver);
    	doUnbindService();
    	mUiBuilt = false;
    }
    
    private View.OnClickListener mToggleBridge = new View.OnClickListener() {
    	public void onClick(View v) {
    		if (mBoundService.isBridgeRunning()) {
    			mBoundService.disableBridge();
    		} else {
    			mBoundService.enableBridge();
    		}
    	}
    };

    private View.OnClickListener mConfigListener= new View.OnClickListener() {
    	public void onClick(View v) {
    		if (!mBoundService.isBridgeRunning()) {
        		Toast.makeText(NfcBridgeActivity.this, "Service must be running.", Toast.LENGTH_SHORT).show();
        	} else {
        		String handover = mBoundService.getBridgeReference();
                String content = ConnectionHandoverManager.USER_HANDOVER_PREFIX + new String(Base64.encodeBase64(
        				NdefHelper.getHandoverNdef(handover).toByteArray()));
        		String qr = QR.getQrl(content);
        		Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(qr));
        		startActivity(view);
        	}
    	}
    };

    private View.OnClickListener mPairListener= new View.OnClickListener() {
    	public void onClick(View v) {
    		if (!mBoundService.isBridgeRunning()) {
        		Toast.makeText(NfcBridgeActivity.this, "Service must be running.", Toast.LENGTH_SHORT).show();
        	} else {
        		Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                intent.putExtra("SCAN_MODE", "QR_CODE_MODE");
                startActivityForResult(intent, QR_NFC_PAIR);
        	}
    	}
    };
    
    boolean mUiBuilt = false;
    private void buildUi() {
    	mToggleButton.setOnClickListener(mToggleBridge);
        mConfigButton.setOnClickListener(mConfigListener);
        mPairButton.setOnClickListener(mPairListener);

    	if (!mBoundService.isBridgeRunning()) {
    		mStatusView.setText(R.string.bridge_not_running);
    		mToggleButton.setText(R.string.enable_bridge);
    	} else {
    		mStatusView.setText("Bridge running on " + mBoundService.getBridgeReference());
    		mToggleButton.setText(R.string.disable_bridge);
    	}
    	mUiBuilt = true;
    }

    
    /* Service binding */
    BroadcastReceiver mUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context arg0, Intent arg1) {
			NfcBridgeActivity.this.buildUi();
		}
	};
	
	IntentFilter mIntentFilter;

    private boolean mIsBound;
    private NfcBridgeService mBoundService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBoundService = ((NfcBridgeService.LocalBinder)service).getService();
            buildUi();
        }

        public void onServiceDisconnected(ComponentName className) {
            mBoundService = null;
        }
    };

    void doBindService() {
        bindService(new Intent(NfcBridgeActivity.this, 
                NfcBridgeService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService() {
        if (mIsBound) {
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == QR_NFC_PAIR) {
        	try {
	            if (resultCode != RESULT_OK) {
	            	throw new Exception();
	            }
                String data = intent.getStringExtra("SCAN_RESULT");
                if (!data.startsWith(ConnectionHandoverManager.USER_HANDOVER_PREFIX)) {
                	throw new Exception();
                }
                NdefMessage ndef = new NdefMessage(android.util.Base64.decode(
                		data.substring(ConnectionHandoverManager.USER_HANDOVER_PREFIX.length()),
                		android.util.Base64.URL_SAFE));
                mBoundService.setNdefExchangeTarget(ndef);
                toast("Set Ndef exchange pairing.");
        	} catch (Exception e) {
        		toast("Could not set nfc partner.");
        	}
        }
    }

    public void toast(String text) {
    	Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }
}