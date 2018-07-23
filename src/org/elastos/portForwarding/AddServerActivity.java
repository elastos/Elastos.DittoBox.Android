package org.elastos.portForwarding;

import org.elastos.android.R;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import org.elastos.carrier.exceptions.ElastosException;

public class AddServerActivity extends AppCompatActivity implements OnClickListener {

	private static final String TAG = AddServerActivity.class.getSimpleName();

	private EditText etPassword;
	private Button btnPair;

	private String serverId;
	private String mPassword;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_server);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("添加服务节点");
        }

		initView();

        Intent intent = getIntent();
        serverId = intent.getExtras().getString("serverId");
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

    private void initView() {
		etPassword = (EditText) findViewById(R.id.et_pair_password);
		btnPair = (Button) findViewById(R.id.btn_pair);
        btnPair.setOnClickListener(this);
	}

	// 点击开始匹配
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btn_pair:
			mPassword = etPassword.getText().toString().trim().replace(" ", "");;
            addPfServer();
			break;
		default:
			break;
		}
	}

	private void addPfServer() {
		try {
			PfdAgent.singleton().pairServer(serverId, mPassword);
			Toast.makeText(this, "授权申请已发送", Toast.LENGTH_SHORT).show();
            addServerFinish();
		} catch (ElastosException e) {
			Log.i(TAG, String.format("Friend requst 0x%x", e.getErrorCode()));
			e.printStackTrace();

			if (e.getErrorCode() == 0x8100000C) {
				Toast.makeText(this, "已添加过该节点", Toast.LENGTH_SHORT).show();
                addServerFinish();
			} else {
				Toast.makeText(this, "节点验证失败", Toast.LENGTH_SHORT).show();
			}
		}
	}

    private void addServerFinish() {
        Intent intent = new Intent();
        intent.setAction("AddServerFinish");
        this.sendBroadcast(intent);

        finish();
    }
}