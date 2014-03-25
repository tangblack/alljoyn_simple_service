/*
 * Copyright (c) 2010-2011, AllSeen Alliance. All rights reserved.
 *
 *    Permission to use, copy, modify, and/or distribute this software for any
 *    purpose with or without fee is hereby granted, provided that the above
 *    copyright notice and this permission notice appear in all copies.
 *
 *    THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 *    WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 *    MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 *    ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 *    WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 *    ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 *    OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package org.alljoyn.bus.samples.simpleservice;

import android.app.Activity;
import android.os.*;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import org.alljoyn.bus.*;
import org.alljoyn.cops.peergroupmanager.BusObjectData;
import org.alljoyn.cops.peergroupmanager.PeerGroupListener;
import org.alljoyn.cops.peergroupmanager.PeerGroupManager;

import java.util.ArrayList;

//import org.alljoyn.bus.p2p.WifiDirectAutoAccept;

public class Service extends Activity {
    /* Load the native alljoyn_java library. */
    static {
        System.loadLibrary("alljoyn_java");
    }
    
    private static final String TAG = "SimpleService";
    
    private static final int MESSAGE_PING = 1;
    private static final int MESSAGE_PING_REPLY = 2;
    private static final int MESSAGE_POST_TOAST = 3;

    private ArrayAdapter<String> mListViewArrayAdapter;
    private ListView mListView;
    private Menu menu;
    
    //private WifiDirectAutoAccept mWfdAutoAccept;

    private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                case MESSAGE_PING:
                    String ping = (String) msg.obj;
                    mListViewArrayAdapter.add("Ping:  " + ping);
                    break;
                case MESSAGE_PING_REPLY:
                    String reply = (String) msg.obj;
                    mListViewArrayAdapter.add("Reply:  " + reply);
                    break;
                case MESSAGE_POST_TOAST:
                    Toast.makeText(getApplicationContext(), (String) msg.obj, Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
                }
            }
        };
    
    /* The AllJoyn object that is our service. */
    private SimpleService mSimpleService;

    /* Handler used to make calls to AllJoyn methods. See onCreate(). */
    private Handler mBusHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mListViewArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mListView = (ListView) findViewById(R.id.ListView);
        mListView.setAdapter(mListViewArrayAdapter);
        
        /* Prepare the auto-accept object.  It will not automatically
         * accept any connections until its intercept() method is called.
         */
        //mWfdAutoAccept = new WifiDirectAutoAccept(getApplicationContext());

        /* Make all AllJoyn calls through a separate handler thread to prevent blocking the UI. */
        HandlerThread busThread = new HandlerThread("BusHandler");
        busThread.start();
        mBusHandler = new BusHandler(busThread.getLooper());

        /* Start our service. */
        mSimpleService = new SimpleService();
        mBusHandler.sendEmptyMessage(BusHandler.CONNECT);
    }

    @Override
    public void onResume() {
        super.onResume();

        /* The auto-accept handler is automatically deregistered
         * when the application goes in to the background, so
         * it must be registered again here in onResume().
         *
         * Since any push-button group formation request will be
         * accepted while the auto-accept object is intercepting
         * requests, only call intercept(true) when the application is
         * expecting incoming connections.  Call intercept(false) as soon
         * as incoming connections are not expected.
         */
        //mWfdAutoAccept.intercept(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mainmenu, menu);
        this.menu = menu;
        return true;
    }
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    // Handle item selection
	    switch (item.getItemId()) {
	    case R.id.quit:
	    	finish();
	        return true;
	    default:
	        return super.onOptionsItemSelected(item);
	    }
	}
    
    @Override
    protected void onStop() {
        super.onStop();

        /* While the auto-accept handler can automatically de-register
         * when the app goes in to the background or stops, it's a
         * good idea to explicitly de-register here so the handler is
         * in a known state if the application restarts.
         */
        //mWfdAutoAccept.intercept(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        //mWfdAutoAccept.intercept(false);

        /* Disconnect to prevent any resource leaks. */
        mBusHandler.sendEmptyMessage(BusHandler.DISCONNECT);        
    }
    
    /* The class that is our AllJoyn service.  It implements the SimpleInterface. */
    class SimpleService implements SimpleInterface, BusObject {

        /*
         * This is the code run when the client makes a call to the Ping method of the
         * SimpleInterface.  This implementation just returns the received String to the caller.
         *
         * This code also prints the string it received from the user and the string it is
         * returning to the user to the screen.
         */
        public String Ping(String inStr) {
            sendUiMessage(MESSAGE_PING, inStr);

            /* Simply echo the ping message. */
            sendUiMessage(MESSAGE_PING_REPLY, inStr);
            return inStr;
        }        

        /* Helper function to send a message to the UI thread. */
        private void sendUiMessage(int what, Object obj) {
            mHandler.sendMessage(mHandler.obtainMessage(what, obj));
        }
    }

    /* This class will handle all AllJoyn calls. See onCreate(). */
    class BusHandler extends Handler {
		/*
		* Group prefix is handed to the PeerGroupManager's constructor
		* and used to advertise any created groups to peers. A reverse
		* URL naming style is used.
		*
		* Group name is an identifier for your specific group, used in
		HT80-BA066-1 Rev. B 4 Qualcomm Innovation Center, Inc.
		MAY CONTAIN U.S. AND INTERNATIONAL EXPORT CONTROLLED INFORMATION  Peer Group Manager calls such as createGroup and joinGroup
		*/
		private static final String GROUP_PREFIX = "org.alljoyn.bus.samples.simple";
		private static final String GROUP_NAME = "service";

		private PeerGroupManager mPeerGroupManager;


		/* These are the messages sent to the BusHandler from the UI. */
        public static final int CONNECT = 1;
        public static final int DISCONNECT = 2;

        public BusHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            /* Connect to the bus and start our service. */
            case CONNECT: { 
            	org.alljoyn.bus.alljoyn.DaemonInit.PrepareDaemon(getApplicationContext());
				/*
				* PeerGroupManager handles all communication with AllJoyn.
				*
				* PeerGroupManager takes a group prefix, defined previously.
				*
				* Also, a PeerGroupListener is required to receive informative callback
				* methods. Only desired callback methods must be overridden. However,
				* no callback information is needed in this example, so an instance of
				* a PeerGroupListener with no overridden methods is passed into the
				* constructor.
				*
				* Finally, an array of BusObjects that you want the PeerGroupManager
				* to register with AllJoyn is passed in. This simple Service has only
				* one bus object.
				*/
				/*
				* Create a BusObject array
				*/
				ArrayList<BusObjectData> busObjects = new ArrayList<BusObjectData>();
				busObjects.add(new BusObjectData(mSimpleService, "/SimpleService"));
				/*
				* Create the PeerGroupManager
				*/
				mPeerGroupManager = new PeerGroupManager(GROUP_PREFIX, new PeerGroupListener(), busObjects);

                /*
				* To allow peers to connect, a group must be created.
				* We use the group name previously defined.
				*/
				Status status = mPeerGroupManager.createGroup(GROUP_NAME);
				logStatus(String.format("PeerGroupManager.createGroup%s)", GROUP_NAME), status);
				if (status != Status.OK) {
					finish();
					return;
				}


				break;
            }
            
            /* Release all resources acquired in connect. */
            case DISCONNECT: {
                /*
				* PeerGroupManager has a cleanup method which unregisters bus objects and
				* disconnects from AllJoyn. The PeerGroupManager should no longer be used
				* after calling cleanup.
				*/
				mPeerGroupManager.cleanup();
				mBusHandler.getLooper().quit();
				break;
            }

            default:
                break;
            }
        }
    }

    private void logStatus(String msg, Status status) {
        String log = String.format("%s: %s", msg, status);
        if (status == Status.OK) {
            Log.i(TAG, log);
        } else {
            Message toastMsg = mHandler.obtainMessage(MESSAGE_POST_TOAST, log);
            mHandler.sendMessage(toastMsg);
            Log.e(TAG, log);
        }
    }
}
