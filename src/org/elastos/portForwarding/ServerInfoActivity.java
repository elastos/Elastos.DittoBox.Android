package org.elastos.portForwarding;

import android.support.v7.app.ActionBar;
import android.util.Log;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import org.elastos.android.R;

import org.elastos.carrier.exceptions.ElastosException;

public class ServerInfoActivity extends AppCompatActivity {
	private static final String TAG = "ServerInfoActivity";
	private PfdServer mServer;

	private TextView mServerIdView;
	private TextView mNameView;
	private TextView mPortView;

	private Button mDeletionButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_server_info);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowTitleEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
			actionBar.setTitle("服务节点信息");
		}

		String serverId = getIntent().getStringExtra("serverId");
		mServer = PfdAgent.singleton().getServer(serverId);

		mServerIdView = (TextView) findViewById(R.id.server_id_value);
		mNameView     = (TextView) findViewById(R.id.server_name_value);
		mPortView  = (EditText) findViewById(R.id.server_service_value);

		if (mServer != null) {
			mServerIdView.setText(mServer.getServerId());
			mNameView    .setText(mServer.getName());
			mPortView .setText(mServer.getPort());

			mPortView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
					if (i == EditorInfo.IME_ACTION_DONE ) {
						mServer.setPort(textView.getText().toString());
					}
					return false;
				}
			});

			mPortView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View view, boolean hasFocus) {
					if (!hasFocus) {
						Log.i(TAG, "Text: " + ((TextView)view).getText().toString());
						mServer.setPort(((TextView)view).getText().toString());
					}
				}
			});
		}

		mDeletionButton = (Button)findViewById(R.id.server_deletion);
		mDeletionButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				removeServer();
			}
		});

	}

	private void removeServer() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("确定要删除此服务节点?");
		builder.setPositiveButton(R.string.common_remove,  new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				try {
					PfdAgent.singleton().unpairServer(mServer.getServerId());
					finish();
				} catch (ElastosException e) {
					e.printStackTrace();
					Toast.makeText(ServerInfoActivity.this, "删除服务节点失败", Toast.LENGTH_SHORT).show();
				}
			}
		});

		builder.setNegativeButton(R.string.common_cancel, null);
		builder.show();
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return super.onSupportNavigateUp();
	}
}
