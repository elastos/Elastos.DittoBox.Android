/**
 *   ownCloud Android client application
 *
 *   @author masensio
 *   Copyright (C) 2016 ownCloud GmbH.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.elastos.ditto.ui.asynctasks;

import android.accounts.Account;
import android.os.AsyncTask;
import android.util.Pair;

import org.elastos.ditto.MainApp;
import org.elastos.ditto.datamodel.FileDataStorageManager;
import org.elastos.ditto.datamodel.OCFile;
import org.elastos.ditto.lib.common.OwnCloudAccount;
import org.elastos.ditto.lib.common.OwnCloudClient;
import org.elastos.ditto.lib.common.OwnCloudClientManagerFactory;
import org.elastos.ditto.lib.common.operations.OnRemoteOperationListener;
import org.elastos.ditto.lib.common.operations.RemoteOperation;
import org.elastos.ditto.lib.common.operations.RemoteOperationResult;
import org.elastos.ditto.lib.common.utils.Log_OC;
import org.elastos.ditto.operations.GetSharesForFileOperation;

import org.elastos.ditto.MainApp;

import java.lang.ref.WeakReference;

/**
 * Async Task to get the users and groups which a file is shared with
 */
public class GetSharesForFileAsyncTask extends AsyncTask<Object, Void, Pair<RemoteOperation, RemoteOperationResult>> {

    private final String TAG = GetSharesForFileAsyncTask.class.getSimpleName();
    private final WeakReference<OnRemoteOperationListener> mListener;

    public GetSharesForFileAsyncTask(OnRemoteOperationListener listener) {
        mListener = new WeakReference<OnRemoteOperationListener>(listener);
    }

    @Override
    protected Pair<RemoteOperation, RemoteOperationResult> doInBackground(Object... params) {

        GetSharesForFileOperation operation = null;
        RemoteOperationResult result = null;

        if (params != null && params.length == 3) {
            OCFile file = (OCFile) params[0];
            Account account = (Account) params[1];
            FileDataStorageManager fileDataStorageManager = (FileDataStorageManager) params[2];

            try {
                // Get shares request
                operation = new GetSharesForFileOperation(file.getRemotePath(), false, false);
                OwnCloudAccount ocAccount = new OwnCloudAccount(
                        account,
                        MainApp.getAppContext()
                );
                OwnCloudClient client = OwnCloudClientManagerFactory.getDefaultSingleton().
                        getClientFor(ocAccount, MainApp.getAppContext());
                result = operation.execute(client, fileDataStorageManager);

            } catch (Exception e) {
                result = new RemoteOperationResult(e);
                Log_OC.e(TAG, "Exception while getting shares", e);
            }
        } else {
            result = new RemoteOperationResult(RemoteOperationResult.ResultCode.UNKNOWN_ERROR);
        }

        return new Pair(operation, result);
    }

    @Override
    protected void onPostExecute(Pair<RemoteOperation, RemoteOperationResult> result) {

        // a cancelled task shouldn't call the listener, even if the reference exists;
        // the Activity responsible could be stopped, and its abilities to do things constrained
        if (result!= null && !isCancelled())
        {
            OnRemoteOperationListener listener = mListener.get();
            if (listener!= null)
            {
                listener.onRemoteOperationFinish(result.first, result.second);
            }
        }
    }

}