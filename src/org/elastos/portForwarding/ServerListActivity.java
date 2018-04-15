package org.elastos.portForwarding;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.owncloud.android.R;

import java.util.List;

import org.elastos.carrier.exceptions.ElastosException;

public class ServerListActivity extends AppCompatActivity {
	private static final String TAG = AddServerActivity.class.getSimpleName();;

	private DeviceRecyclerViewAdapter mAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server_list);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		toolbar.setTitle("服务节点列表");
		setSupportActionBar(toolbar);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		RecyclerView recyclerView = (RecyclerView) findViewById(R.id.server_list);
		setupRecyclerView(recyclerView);

		IntentFilter filter = new IntentFilter();
		filter.addAction(PfdAgent.ACTION_SERVER_LIST_CHANGED);
		registerReceiver(broadcastReceiver, filter);
	}

	@Override
	protected void onDestroy() {
		unregisterReceiver(broadcastReceiver);
		super.onDestroy();
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return super.onSupportNavigateUp();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_add_server, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_add_server) {
			startActivity(new Intent(this, AddServerActivity.class));
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void removeServer(final PfdServer server) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("确定要删除此服务节点?");
		builder.setPositiveButton(R.string.common_remove,  new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				try {
					PfdAgent.singleton().unpairServer(server.getServerId());
				} catch (ElastosException e) {
					e.printStackTrace();
					Toast.makeText(ServerListActivity.this, "删除服务节点失败", Toast.LENGTH_SHORT).show();
				}
			}
		});

		builder.setNegativeButton(R.string.common_cancel, null);
		builder.show();
	}

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(TAG, "Received intent with action: " + intent.getAction());
			mAdapter.notifyDataSetChanged();
		}
	};

	private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
		mAdapter = new DeviceRecyclerViewAdapter();
		recyclerView.setAdapter(mAdapter);
	}

	public class DeviceRecyclerViewAdapter
		extends RecyclerView.Adapter<DeviceRecyclerViewAdapter.ViewHolder> {

		private final List<PfdServer> mServerList;

		public DeviceRecyclerViewAdapter() {
			mServerList = PfdAgent.singleton().getServerList();
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
				.inflate(R.layout.content_list_server, parent, false);
			return new ViewHolder(view);
		}

		@Override
		public void onBindViewHolder(final ViewHolder holder, int position) {
			if (position >= mServerList.size())
				return;

			PfdServer server = mServerList.get(position);
			holder.mServer = server;

			if (server.equals(PfdAgent.singleton().getCheckedServer()))
				holder.mFocusIcon.setVisibility(View.VISIBLE);
			else
				holder.mFocusIcon.setVisibility(View.INVISIBLE);

			if (server.getName().isEmpty())
				holder.mTitleView.setText(server.getServerId());
			else
				holder.mTitleView.setText(server.getName());

			if (server.isOnline())
				holder.mStatusIcon.setImageResource(android.R.drawable.presence_online);
			else
				holder.mStatusIcon.setImageResource(android.R.drawable.presence_offline);

			holder.mDetailIcon.setVisibility(View.VISIBLE);

			holder.mDetailIcon.setOnClickListener(new ImageView.OnClickListener() {
				@Override
				public void onClick(View v) {
					Context context = v.getContext();
					Intent intent = new Intent(context, ServerInfoActivity.class);
					intent.putExtra("serverId", holder.mServer.getServerId());
					context.startActivity(intent);
				}

			});

			holder.mView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					if (holder.mServer.isOnline()) {
						PfdAgent.singleton().setCheckedServer(holder.mServer.getServerId());
						finish();
					}
				}
			});

			holder.mView.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					if (holder.mServer != null)
						removeServer(holder.mServer);
					return true;
				}
			});
		}

		@Override
		public int getItemCount() {
			return mServerList.size();
		}

		public class ViewHolder extends RecyclerView.ViewHolder {
			public final View mView;
			public final ImageView mFocusIcon;
			public final TextView mTitleView;
			public final ImageView mStatusIcon;
			public final ImageView mDetailIcon;
			public PfdServer mServer;

			public ViewHolder(View view) {
				super(view);
				mView = view;
				mFocusIcon = (ImageView) view.findViewById(R.id.focusIcon);
				mTitleView = (TextView)view.findViewById(R.id.title);
				mStatusIcon = (ImageView) view.findViewById(R.id.statusIcon);
				mDetailIcon = (ImageView) view.findViewById(R.id.detailIcon);
			}

			@Override
			public String toString() {
				return super.toString() + " '" + mTitleView.getText() + "'";
			}
		}
	}
}
