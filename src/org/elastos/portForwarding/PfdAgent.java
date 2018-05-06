package org.elastos.portForwarding;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.content.Intent;

import com.owncloud.android.MainApp;
import com.owncloud.android.R;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.File;

import org.apache.commons.io.IOUtils;
import org.elastos.carrier.*;
import org.elastos.carrier.exceptions.ElastosException;
import org.elastos.carrier.session.Manager;
import org.json.JSONArray;
import org.json.JSONObject;

public class PfdAgent extends AbstractCarrierHandler {
	private static String TAG = "PfdAgent";

	public static final String ACTION_SERVER_LIST_CHANGED  = "ACTION_SERVER_LIST_CHANGED";
	public static final String ACTION_SERVER_INFO_CHANGED  = "ACTION_SERVER_INFO_CHANGED";
	public static final String ACTION_AGENT_STATUS_CHANGED = "ACTION_AGENT_STATUS_CHANGED";

	public static PfdAgent pfdAgentInst;

	private Context mContext;
	private Carrier mCarrier;
	private Manager mSessionManager;
	private ConnectionStatus mStatus;
	private boolean mReady;

	private PfdServer mCheckedServer;
	private List<PfdServer> mServerList;
	private Map<String, PfdServer> mServerMap;

	public static int AGENT_READY = 0;


	public static PfdAgent singleton() {
		if (pfdAgentInst == null) {
			pfdAgentInst = new PfdAgent();
		}
		return pfdAgentInst;
	}

	private PfdAgent() {
		mContext = MainApp.getAppContext();
		mStatus = ConnectionStatus.Disconnected;
		mReady  = false;

		mServerList = new ArrayList<PfdServer>();
		mServerMap  = new HashMap();
	}

	public void checkLogin() throws ElastosException {
		String elaCarrierPath = mContext.getFilesDir().getAbsolutePath() + "/elaCarrier";
		File elaCarrierDir = new File(elaCarrierPath);
		if (!elaCarrierDir.exists()) {
			elaCarrierDir.mkdirs();
		}

		boolean udpEnabled = false;
		List<Carrier.Options.BootstrapNode> bootstraps = new ArrayList<>();

		try {
			InputStream configStream = mContext.getResources().openRawResource(R.raw.elastos_carrier_config);
			String configString = IOUtils.toString(configStream, "UTF-8");
			JSONObject jsonObject = new JSONObject(configString);

			udpEnabled = jsonObject.getBoolean("udp_enabled");

			JSONArray jsonBootstraps = jsonObject.getJSONArray("bootstraps");
			for (int i = 0, m = jsonBootstraps.length(); i < m; i++) {
				JSONObject jsonBootstrap = jsonBootstraps.getJSONObject(i);
				Carrier.Options.BootstrapNode bootstrap = new Carrier.Options.BootstrapNode();
				String ipv4 = jsonBootstrap.optString("ipv4");
				if (ipv4 != null) {
					bootstrap.setIpv4(ipv4);
				}
				String ipv6 = jsonBootstrap.optString("ipv6");
				if (ipv4 != null) {
					bootstrap.setIpv6(ipv6);
				}
				bootstrap.setPort(jsonBootstrap.getString("port"));
				bootstrap.setPublicKey(jsonBootstrap.getString("public_key"));
				bootstraps.add(bootstrap);
			}
		} catch (Exception e) {
			// report exception
		}

		Carrier.Options options = new Carrier.Options();
		options.setPersistentLocation(elaCarrierPath).
				setUdpEnabled(udpEnabled).
				setBootstrapNodes(bootstraps);

		mCarrier = Carrier.getInstance(options, this);
		Log.i(TAG, "Agent elastos carrier instance created successfully");

		mSessionManager = Manager.getInstance(mCarrier);
		Log.i(TAG, "Agent session manager created successfully");
	}

	public boolean isReady() {
		return mReady;
	}

	public void start() {
		try {
			if (mCarrier == null) {
				checkLogin();
			}

			mCarrier.start(50);
		} catch (ElastosException e) {
			Log.i(TAG, String.format("checkLogin error (0x%x)", e.getErrorCode()));
			//TODO;
			notifyAgentStatus(-1);
		}
	}

	public void logout() {
		String elaCarrierPath = mContext.getFilesDir().getAbsolutePath() + "/elaCarrier";
		File elaCarrierDir = new File(elaCarrierPath);
		if (elaCarrierDir.exists()) {
			File[] files = elaCarrierDir.listFiles();
			for (File file : files) {
				file.delete();
			}
		}

		this.kill();
	}

	public void kill() {
		savePreferences();

		for (PfdServer server: mServerList) {
			server.close();
		}

		mServerMap.clear();
		mServerList.clear();

		if (mCarrier != null) {
			mSessionManager.cleanup();
			mCarrier.kill();
		}

		pfdAgentInst = null;
	}

	public Manager getSessionManager() {
		return mSessionManager;
	}

	private void loadPreferences() {
		SharedPreferences preferences = mContext.getSharedPreferences("elastos", Context.MODE_PRIVATE);
		String serverId = preferences.getString("checkedServerId", null);
		if (serverId != null) {
			PfdServer server = mServerMap.get(serverId);
			if (server != null)
				mCheckedServer = server;
		}
	}

	private void savePreferences() {
		if (mCheckedServer != null) {
			SharedPreferences preferences = mContext.getSharedPreferences("elastos", Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = preferences.edit();

			editor.putString("checkedServerId", mCheckedServer.getServerId());
			editor.commit();
		}
	}

	private void updatePreference() {
		SharedPreferences preferences = mContext.getSharedPreferences("elastos", Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = preferences.edit();


		if (mCheckedServer == null)
			edit.remove("checkedServerId");
		else
			edit.putString("checkedServerId", mCheckedServer.getServerId());

		edit.commit();
	}

	public void setCheckedServer(String serverId) {
		PfdServer server = mServerMap.get(serverId);

		if (server != null && server != mCheckedServer) {
			Log.i(TAG, "Checked server changed to " + serverId);

			if (mCheckedServer != null) {
                mCheckedServer.close();
            }

			mCheckedServer = server;

			savePreferences();

			if (mStatus == ConnectionStatus.Connected) {
                notifyAgentStatus(AGENT_READY);
                mCheckedServer.setupPortforwarding();
            }
		}
	}

	public PfdServer getCheckedServer() {
		return mCheckedServer;
	}

	public List<PfdServer> getServerList() {
		return mServerList;
	}

	public PfdServer getServer(String serverId) {
		return mServerMap.get(serverId);
	}

	public void pairServer(String serverId, String password) throws ElastosException {
		if (!mCarrier.isFriend(serverId)) {
			String hello = hash256(password);
			mCarrier.addFriend(serverId, hello);
			Log.i(TAG, "Friend request to portforwarding server " + serverId + " success");
		}
	}

	public void unpairServer(String serverId) throws ElastosException {
		if (mCarrier.isFriend(serverId)) {
			mCarrier.removeFriend(serverId);
			Log.i(TAG, "Removed " + serverId + " friend");
		}
	}

	public UserInfo getInfo() throws ElastosException {
		return mCarrier.getSelfInfo();
	}

	@Override
	public void onConnection(Carrier carrier, ConnectionStatus status) {
		Log.i(TAG, "Agent connection status changed to " + status);

		mStatus = status;

		if (mReady && status == ConnectionStatus.Connected)
			notifyAgentStatus(AGENT_READY);
	}

	@Override
	public void onReady(Carrier carrier) {
		try {
			UserInfo info;
			info = carrier.getSelfInfo();

			if (info.getName().isEmpty()) {
				String manufacturer = Build.MANUFACTURER;
				String name = Build.MODEL;

				if (!name.startsWith(manufacturer))
					name = manufacturer + " " + name;
				if (name.length() > UserInfo.MAX_USER_NAME_LEN)
					name = name.substring(0, UserInfo.MAX_USER_NAME_LEN);

				info.setName(name);

				carrier.setSelfInfo(info);
			}
		} catch (ElastosException e) {
			Log.e(TAG, String.format("Update current user name error (0x%x)", e.getErrorCode()));
			e.printStackTrace();
			notifyAgentStatus(-1);
			return;
		}

		Log.i(TAG, "Elastos carrier instance is ready.");

		if (mCheckedServer == null) {
			for (PfdServer server: mServerList) {
				if (server.isOnline()) {
					mCheckedServer = server;
					savePreferences();
					break;
				}
			}
		}

		mReady = true;
		notifyAgentStatus(AGENT_READY);
	}

	@Override
	public void onFriends(Carrier carrier, List<FriendInfo> friends) {
		Log.i(TAG, "Client portforwarding agent received friend list: " + friends);

		for (FriendInfo info: friends) {
			String serverId = info.getUserId();
			PfdServer server;

			server = mServerMap.get(serverId);
			if (server == null) {
				server = new PfdServer();
				mServerList.add(server);
				mServerMap.put(serverId, server);
			}

			server.setInfo(info);
			server.setConnectionStatus(info.getConnectionStatus());
			server.setPresenceStatus(info.getPresence());
		}

        loadPreferences();

		notifyServerChanged();
	}

	@Override
	public void onFriendInfoChanged(Carrier carrier, String friendId, FriendInfo friendInfo) {
		PfdServer server = mServerMap.get(friendId);
		assert(server != null);

		Log.i(TAG, "Server " + friendId + "info changed to " + friendInfo);

		server.setInfo(friendInfo);
		notifyServerInfoChanged(friendInfo.getUserId());
	}

	@Override
	public void onFriendConnection(Carrier carrier, String friendId, ConnectionStatus status) {
		PfdServer server = mServerMap.get(friendId);
		assert(server != null);

		Log.i(TAG, "Server " + friendId + " connection status changed to " + status);

		server.setConnectionStatus(status);
        notifyServerInfoChanged(server.getServerId());

		if (server.equals(mCheckedServer)) {
            notifyAgentStatus(AGENT_READY);

            if (server.isOnline()) {
                server.setupPortforwarding();
            } else {
                server.close();
            }
        }

		notifyServerChanged();
	}

	@Override
	public void onFriendPresence(Carrier carrier, String friendId, PresenceStatus presence) {
		PfdServer server = mServerMap.get(friendId);
		assert(server != null);

		Log.i(TAG, "Server" + friendId + "presence changed to " + presence);

		server.setPresenceStatus(presence);
        notifyServerInfoChanged(server.getServerId());

		if (server.equals(mCheckedServer)) {
            notifyAgentStatus(AGENT_READY);

            if (server.isOnline()) {
                server.setupPortforwarding();
            } else {
                server.close();
            }
        }

		notifyServerChanged();
	}

	@Override
	public void onFriendAdded(Carrier carrier, FriendInfo friendInfo) {
		PfdServer server = new PfdServer();
		server.setInfo(friendInfo);
		server.setConnectionStatus(friendInfo.getConnectionStatus());
		server.setPresenceStatus(friendInfo.getPresence());

		mServerList.add(server);
		mServerMap.put(server.getServerId(), server);

		Log.i(TAG, "Server " + server.getServerId() + " added: " + friendInfo);

		if (mCheckedServer == null) {
			mCheckedServer = server;
			savePreferences();
			notifyAgentStatus(AGENT_READY);
		}

		notifyServerChanged();
	}

	@Override
	public void onFriendRemoved(Carrier carrier, String friendId) {
		PfdServer server = mServerMap.remove(friendId);
		assert(server != null);

		mServerList.remove(server);
		Log.i(TAG, "Portforwarding server " + friendId + "removed");

		server.clearPreferences();

		if (mCheckedServer.equals(server)) {
			mCheckedServer = null;
			for (PfdServer svr: mServerList) {
				if (svr.isOnline())
					mCheckedServer = svr;
			}

			updatePreference();

			notifyAgentStatus(AGENT_READY);
		}

		notifyServerChanged();
	}

	public void notifyAgentStatus(int status) {
		notifyAgentStatus(status, false);
	}

	private void notifyAgentStatus(int status, boolean onReady) {
		Intent intent = new Intent(ACTION_AGENT_STATUS_CHANGED);
		intent.putExtra("status", status);
		intent.putExtra("onReady", onReady);
		MainApp.getAppContext().sendBroadcast(intent);
	}

	private void notifyServerChanged() {
		Intent intent = new Intent(ACTION_SERVER_LIST_CHANGED);
		MainApp.getAppContext().sendBroadcast(intent);
	}

    public void notifyServerInfoChanged(String userName) {
		Intent intent = new Intent(ACTION_SERVER_INFO_CHANGED);
		MainApp.getAppContext().sendBroadcast(intent);
	}

    private String hash256(String string) {
        MessageDigest md = null;
        String result = null;
        byte[] bt = string.getBytes();
        try {
            md = MessageDigest.getInstance("SHA-256");
            md.update(bt);
            result = bytes2Hex(md.digest()); // to HexString
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return result;
    }

    private String bytes2Hex(byte[] bts) {
        String des = "";
        String tmp = null;
        for (int i = 0; i < bts.length; i++) {
            tmp = (Integer.toHexString(bts[i] & 0xFF));
            if (tmp.length() == 1) {
                des += "0";
            }
            des += tmp;
        }
        return des;
    }
}
