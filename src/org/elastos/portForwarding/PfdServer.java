package org.elastos.portForwarding;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.owncloud.android.MainApp;

import java.net.ServerSocket;

import org.elastos.carrier.ConnectionStatus;
import org.elastos.carrier.FriendInfo;
import org.elastos.carrier.PresenceStatus;
import org.elastos.carrier.session.*;
import org.elastos.carrier.exceptions.ElastosException;

public class PfdServer extends AbstractStreamHandler implements SessionRequestCompleteHandler {
	private static String TAG = "PfServer";

	public static String ACTION_SERVER_STATUS_CHANGED = "ACTION_SERVER_STATUS_CHANGED";

	private FriendInfo mFriendInfo;
	private Session mSession;
	private String  mPort;
	private int mPfId;
	private Stream mStream;
	private StreamState mState = StreamState.Closed;

	private boolean mNeedClosePortforwarding = false;

	public static final int STATUS_READY   = 0;
	public static final int STATUS_INPROGRESS = 1;
	public static final int STATUS_OFFLINE = 2;
	public static final int STATUS_SESSION_REFUSED = 3;

	PfdServer() {}

	public String getHost() { return "127.0.0.1"; }

	public String getPort() {
		return mPort;
	}

	public String getName() {
		return mFriendInfo.getName();
	}

	public String getServerId() {
		return mFriendInfo.getUserId();
	}

	public void setPort(String port) {
		if (mPort != port) {
			mPort = port;
			savePreferences();

			PfdAgent.singleton().notifyServerInfoChanged(getServerId());

            if (mState == StreamState.Connected) {
                try {
                    mNeedClosePortforwarding = true;
                    openPortforwarding();
                    notifyPortforwardingStatus(STATUS_READY);
                } catch (ElastosException e) {
                    e.printStackTrace();

                    Log.e(TAG, "Portforwarding to " + getServerId() + " opened error.");
                    notifyPortforwardingStatus(e.getErrorCode());
                }
                return;
            }
		}
	}

	public void setInfo(FriendInfo friendInfo) {
		mFriendInfo = friendInfo;
		loadPreferences();
	}

	public void setConnectionStatus(ConnectionStatus connectionStatus) {
		mFriendInfo.setConnectionStatus(connectionStatus);
	}

	public void setPresenceStatus(PresenceStatus presence) {
		mFriendInfo.setPresence(presence);
	}

	public boolean isOnline() {
		return mFriendInfo.getConnectionStatus() == ConnectionStatus.Connected &&
				mFriendInfo.getPresence() == PresenceStatus.None;
	}

	@Override
	public void onCompletion(Session session, int status, String reason, String sdp) {
		if (status != 0) {
			Log.i(TAG, String.format("Session request completion with error (%d:%s", status, reason));

			close();
			notifyPortforwardingStatus(STATUS_SESSION_REFUSED);
			return;
		}

		try {
			session.start(sdp);
			Log.i(TAG, "Session started success.");
		} catch (ElastosException e) {
			Log.e(TAG, "Session start error " + e.getErrorCode());
		}
	}

	private void savePreferences() {
		SharedPreferences preferences = MainApp.getAppContext()
									.getSharedPreferences("elastos", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(getServerId() + ":port", mPort);
		editor.commit();
	}

	private void loadPreferences() {
		SharedPreferences preferences = MainApp.getAppContext()
									.getSharedPreferences("elastos", Context.MODE_PRIVATE);
		String port = preferences.getString(getServerId() + ":port", null);
		if (port != null)
			mPort = port;
	}

	public void clearPreferences() {
		SharedPreferences preferences = MainApp.getAppContext()
									.getSharedPreferences("elastos", Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.remove(getServerId() + ":port");
		editor.commit();
	}

	@Override
	public void onStateChanged(Stream stream, StreamState state) {
		Log.i(TAG, "onStateChanged : " + stream.getStreamId() + "  :  " + state);
		mState = state;
		try {
			switch (state) {
				case Initialized:
					mSession.request(this);
					Log.i(TAG, "Session request to " + getServerId() + " sent.");
					break;

				case TransportReady:
					Log.i(TAG, "Stream to " + getServerId() + " transport ready");
					break;

				case Connected:
					Log.i(TAG, "Stream to " + getServerId() + " connected.");
					mStream = stream;
					openPortforwarding();
					notifyPortforwardingStatus(STATUS_READY);
					break;

				case Deactivated:
					Log.i(TAG, "Stream deactived");
					close();
					break;
				case Closed:
					Log.i(TAG, "Stream closed");
					close();
					break;
				case Error:
					Log.i(TAG, "Stream error");
					close();
					break;
			}
		} catch (ElastosException e) {
			Log.e(TAG, String.format("Stream error (0x%x)", e.getErrorCode()));
			close();
			notifyPortforwardingStatus(e.getErrorCode());
		}
	}

	private int findFreePort() {
		int port;

		try {
			ServerSocket socket = new ServerSocket(0);
			port = socket.getLocalPort();
			socket.close();

		} catch(Exception e) {
			port = -1;
		}

		return port;
	}

	public void setupPortforwarding() {
		if (!isOnline()) {
			notifyPortforwardingStatus(STATUS_OFFLINE);
			return;
		}

		if (mState == StreamState.Initialized || mState == StreamState.TransportReady
			|| mState == StreamState.Connecting) {
			notifyPortforwardingStatus(STATUS_INPROGRESS);
			return;
		}
		else if (mState == StreamState.Connected) {
			try {
				openPortforwarding();
				notifyPortforwardingStatus(STATUS_READY);
			} catch (ElastosException e) {
				e.printStackTrace();

				Log.e(TAG, "Portforwarding to " + getServerId() + " opened error.");
				notifyPortforwardingStatus(e.getErrorCode());
			}
			return;
		}
		else {
			mState = StreamState.Closed;

			int sopt = Stream.PROPERTY_MULTIPLEXING
					| Stream.PROPERTY_PORT_FORWARDING
					| Stream.PROPERTY_RELIABLE;

			try {
				mSession = PfdAgent.singleton().getSessionManager()
							.newSession(mFriendInfo.getUserId());
				mSession.addStream(StreamType.Application, sopt, this);
			}
			catch (ElastosException e) {
				e.printStackTrace();

				if (mSession == null) {
					Log.e(TAG, String.format("New session to %s error (0x%x)",
						getServerId(), e.getErrorCode()));
				}
				else {
					Log.e(TAG, String.format("Add stream error (0x%x)", e.getErrorCode()));
					mSession.close();
					mSession = null;
				}
				notifyPortforwardingStatus(e.getErrorCode());
			}
		}
	}

	private void openPortforwarding() throws ElastosException {
		if (mPfId > 0 && !mNeedClosePortforwarding) {
			Log.i(TAG, "Portforwarding to " + getName() + " already opened.");
		}
		else {
			if (mPfId > 0) {
				mStream.closePortForwarding(mPfId);
				mPfId = -1;
                mNeedClosePortforwarding = false;
			}

			String port = mPort;
			if (port == null || port.isEmpty()) {
				port = String.valueOf(findFreePort());;
			}
			mPfId = mStream.openPortFowarding("owncloud", PortForwardingProtocol.TCP,
											"127.0.0.1", port);

			mPort = port;
			savePreferences();

			Log.i(TAG, "Portforwarding to " + getServerId() + " opened.");
		}
	}

	private void notifyPortforwardingStatus(int status) {
		Intent intent = new Intent(ACTION_SERVER_STATUS_CHANGED);
		intent.putExtra("serverId", getServerId());
		intent.putExtra("status", status);
		MainApp.getAppContext().sendBroadcast(intent);
	}

	public void close() {
		if (mSession != null) {
			mSession.close();
			mSession = null;
			mStream = null;
			mState  = StreamState.Closed;
			mPfId = -1;
		}
	}

	public boolean isConnected() {
		if (mPfId > 0) {
			return true;
		}
		else {
			setupPortforwarding();
			return false;
		}
	}
}
