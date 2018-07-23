/**
 * ownCloud Android client application
 *
 * @author Bartek Przybylski
 * @author David A. Velasco
 * @author masensio
 * @author David González Verdugo
 * Copyright (C) 2011  Bartek Przybylski
 * Copyright (C) 2017 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.elastos.android.providers;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.elastos.android.MainApp;
import org.elastos.android.R;
import org.elastos.android.datamodel.OCFile;
import org.elastos.android.datamodel.UploadsStorageManager;
import org.elastos.android.db.ProviderMeta;
import com.owncloud.android.lib.common.accounts.AccountUtils;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.lib.resources.shares.ShareType;
import org.elastos.android.utils.FileStorageUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * The ContentProvider for the ownCloud App.
 */
public class FileContentProvider extends ContentProvider {

    private DataBaseHelper mDbHelper;

    private static final int SINGLE_FILE = 1;
    private static final int DIRECTORY = 2;
    private static final int ROOT_DIRECTORY = 3;
    private static final int SHARES = 4;
    private static final int CAPABILITIES = 5;
    private static final int UPLOADS = 6;
    private static final int CAMERA_UPLOADS_SYNC = 7;

    private static final String TAG = FileContentProvider.class.getSimpleName();

    private static final String MAX_SUCCESSFUL_UPLOADS = "30";

    private UriMatcher mUriMatcher;

    private static HashMap<String, String> mFileProjectionMap = new HashMap<>();

    static {

        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta._ID, ProviderMeta.ProviderTableMeta._ID);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_PARENT, ProviderMeta.ProviderTableMeta.FILE_PARENT);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_NAME, ProviderMeta.ProviderTableMeta.FILE_NAME);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_CREATION, ProviderMeta.ProviderTableMeta.FILE_CREATION);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_MODIFIED, ProviderMeta.ProviderTableMeta.FILE_MODIFIED);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA,
                ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_CONTENT_LENGTH, ProviderMeta.ProviderTableMeta.FILE_CONTENT_LENGTH);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_CONTENT_TYPE, ProviderMeta.ProviderTableMeta.FILE_CONTENT_TYPE);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH, ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_PATH, ProviderMeta.ProviderTableMeta.FILE_PATH);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER, ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE, ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA,
                ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_KEEP_IN_SYNC, ProviderMeta.ProviderTableMeta.FILE_KEEP_IN_SYNC);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_ETAG, ProviderMeta.ProviderTableMeta.FILE_ETAG);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_TREE_ETAG, ProviderMeta.ProviderTableMeta.FILE_TREE_ETAG);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK, ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE, ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_PERMISSIONS, ProviderMeta.ProviderTableMeta.FILE_PERMISSIONS);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID, ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_UPDATE_THUMBNAIL, ProviderMeta.ProviderTableMeta.FILE_UPDATE_THUMBNAIL);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_IS_DOWNLOADING, ProviderMeta.ProviderTableMeta.FILE_IS_DOWNLOADING);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_ETAG_IN_CONFLICT, ProviderMeta.ProviderTableMeta.FILE_ETAG_IN_CONFLICT);
        mFileProjectionMap.put(ProviderMeta.ProviderTableMeta.FILE_PRIVATE_LINK, ProviderMeta.ProviderTableMeta.FILE_PRIVATE_LINK);
    }

    private static HashMap<String, String> mShareProjectionMap = new HashMap<>();

    static {

        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta._ID, ProviderMeta.ProviderTableMeta._ID);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_FILE_SOURCE, ProviderMeta.ProviderTableMeta.OCSHARES_FILE_SOURCE);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_ITEM_SOURCE, ProviderMeta.ProviderTableMeta.OCSHARES_ITEM_SOURCE);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_TYPE, ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_TYPE);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH, ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_PATH, ProviderMeta.ProviderTableMeta.OCSHARES_PATH);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_PERMISSIONS, ProviderMeta.ProviderTableMeta.OCSHARES_PERMISSIONS);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_SHARED_DATE, ProviderMeta.ProviderTableMeta.OCSHARES_SHARED_DATE);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_EXPIRATION_DATE, ProviderMeta.ProviderTableMeta.OCSHARES_EXPIRATION_DATE);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_TOKEN, ProviderMeta.ProviderTableMeta.OCSHARES_TOKEN);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH_DISPLAY_NAME,
                ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH_DISPLAY_NAME);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_IS_DIRECTORY,
                ProviderMeta.ProviderTableMeta.OCSHARES_IS_DIRECTORY);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_USER_ID, ProviderMeta.ProviderTableMeta.OCSHARES_USER_ID);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_ID_REMOTE_SHARED, ProviderMeta.ProviderTableMeta.OCSHARES_ID_REMOTE_SHARED);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_ACCOUNT_OWNER, ProviderMeta.ProviderTableMeta.OCSHARES_ACCOUNT_OWNER);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_NAME, ProviderMeta.ProviderTableMeta.OCSHARES_NAME);
        mShareProjectionMap.put(ProviderMeta.ProviderTableMeta.OCSHARES_URL, ProviderMeta.ProviderTableMeta.OCSHARES_URL);
    }

    private static HashMap<String, String> mCapabilityProjectionMap = new HashMap<>();

    static {

        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta._ID, ProviderMeta.ProviderTableMeta._ID);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MAYOR,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MAYOR);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MINOR,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MINOR);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MICRO,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MICRO);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_STRING,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_STRING);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_EDITION,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_EDITION);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_CORE_POLLINTERVAL,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_CORE_POLLINTERVAL);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_API_ENABLED,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_API_ENABLED);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_ENABLED,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_ENABLED);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENABLED,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENABLED);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENFORCED,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENFORCED);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SEND_MAIL,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SEND_MAIL);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_UPLOAD,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_UPLOAD);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_MULTIPLE,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_MULTIPLE);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SUPPORTS_UPLOAD_ONLY,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SUPPORTS_UPLOAD_ONLY);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_USER_SEND_MAIL,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_USER_SEND_MAIL);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_RESHARING,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_RESHARING);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_OUTGOING,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_OUTGOING);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_INCOMING,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_INCOMING);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_BIGFILECHUNKING,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_BIGFILECHUNKING);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_UNDELETE,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_UNDELETE);
        mCapabilityProjectionMap.put(ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_VERSIONING,
                ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_VERSIONING);
    }

    private static HashMap<String, String> mUploadProjectionMap = new HashMap<>();

    static {

        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta._ID, ProviderMeta.ProviderTableMeta._ID);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_PATH, ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_PATH);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_REMOTE_PATH, ProviderMeta.ProviderTableMeta.UPLOADS_REMOTE_PATH);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_ACCOUNT_NAME, ProviderMeta.ProviderTableMeta.UPLOADS_ACCOUNT_NAME);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_FILE_SIZE, ProviderMeta.ProviderTableMeta.UPLOADS_FILE_SIZE);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_STATUS, ProviderMeta.ProviderTableMeta.UPLOADS_STATUS);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_BEHAVIOUR,
                ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_BEHAVIOUR);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_TIME, ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_TIME);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_FORCE_OVERWRITE, ProviderMeta.ProviderTableMeta.UPLOADS_FORCE_OVERWRITE);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_IS_CREATE_REMOTE_FOLDER,
                ProviderMeta.ProviderTableMeta.UPLOADS_IS_CREATE_REMOTE_FOLDER);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_END_TIMESTAMP,
                ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_END_TIMESTAMP);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_LAST_RESULT, ProviderMeta.ProviderTableMeta.UPLOADS_LAST_RESULT);
        mUploadProjectionMap.put(ProviderMeta.ProviderTableMeta.UPLOADS_CREATED_BY, ProviderMeta.ProviderTableMeta.UPLOADS_CREATED_BY);
    }

    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        //Log_OC.d(TAG, "Deleting " + uri + " at provider " + this);
        int count = 0;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            count = delete(db, uri, where, whereArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    private int delete(SQLiteDatabase db, Uri uri, String where, String[] whereArgs) {
        int count = 0;
        switch (mUriMatcher.match(uri)) {
            case SINGLE_FILE:
                Cursor c = query(db, uri, null, where, whereArgs, null);
                String remoteId = "";
                if (c != null && c.moveToFirst()) {
                    remoteId = c.getString(c.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID));
                    //ThumbnailsCacheManager.removeFileFromCache(remoteId);
                    c.close();
                }
                Log_OC.d(TAG, "Removing FILE " + remoteId);

                count = db.delete(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                        ProviderMeta.ProviderTableMeta._ID
                                + "="
                                + uri.getPathSegments().get(1)
                                + (!TextUtils.isEmpty(where) ? " AND (" + where
                                + ")" : ""), whereArgs);
                break;
            case DIRECTORY:
                // deletion of folder is recursive
            /*
            Uri folderUri = ContentUris.withAppendedId(ProviderTableMeta.CONTENT_URI_FILE, Long.parseLong(uri.getPathSegments().get(1)));
            Cursor folder = query(db, folderUri, null, null, null, null);
            String folderName = "(unknown)";
            if (folder != null && folder.moveToFirst()) {
                folderName = folder.getString(folder.getColumnIndex(ProviderTableMeta.FILE_PATH));
            }
            */
                Cursor children = query(uri, null, null, null, null);
                if (children != null && children.moveToFirst()) {
                    long childId;
                    boolean isDir;
                    while (!children.isAfterLast()) {
                        childId = children.getLong(children.getColumnIndex(ProviderMeta.ProviderTableMeta._ID));
                        isDir = "DIR".equals(children.getString(
                                children.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_CONTENT_TYPE)
                        ));
                        //remotePath = children.getString(children.getColumnIndex(ProviderTableMeta.FILE_PATH));
                        if (isDir) {
                            count += delete(
                                    db,
                                    ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_DIR, childId),
                                    null,
                                    null
                            );
                        } else {
                            count += delete(
                                    db,
                                    ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_FILE, childId),
                                    null,
                                    null
                            );
                        }
                        children.moveToNext();
                    }
                    children.close();
                } /*else {
                Log_OC.d(TAG, "No child to remove in DIRECTORY " + folderName);
            }
            Log_OC.d(TAG, "Removing DIRECTORY " + folderName + " (or maybe not) ");
            */
                count += db.delete(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                        ProviderMeta.ProviderTableMeta._ID
                                + "="
                                + uri.getPathSegments().get(1)
                                + (!TextUtils.isEmpty(where) ? " AND (" + where
                                + ")" : ""), whereArgs);
            /* Just for log
             if (folder != null) {
                folder.close();
            }*/
                break;
            case ROOT_DIRECTORY:
                //Log_OC.d(TAG, "Removing ROOT!");
                count = db.delete(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, where, whereArgs);
                break;
            case SHARES:
                count = db.delete(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME, where, whereArgs);
                break;
            case CAPABILITIES:
                count = db.delete(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME, where, whereArgs);
                break;
            case UPLOADS:
                count = db.delete(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME, where, whereArgs);
                break;
            case CAMERA_UPLOADS_SYNC:
                count = db.delete(ProviderMeta.ProviderTableMeta.CAMERA_UPLOADS_SYNC_TABLE_NAME, where, whereArgs);
                break;
            default:
                //Log_OC.e(TAG, "Unknown uri " + uri);
                throw new IllegalArgumentException("Unknown uri: " + uri.toString());
        }
        return count;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
                return ProviderMeta.ProviderTableMeta.CONTENT_TYPE;
            case SINGLE_FILE:
                return ProviderMeta.ProviderTableMeta.CONTENT_TYPE_ITEM;
            default:
                throw new IllegalArgumentException("Unknown Uri id."
                        + uri.toString());
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        Uri newUri = null;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            newUri = insert(db, uri, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(newUri, null);
        return newUri;
    }

    private Uri insert(SQLiteDatabase db, Uri uri, ContentValues values) {
        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
            case SINGLE_FILE:
                String remotePath = values.getAsString(ProviderMeta.ProviderTableMeta.FILE_PATH);
                String accountName = values.getAsString(ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER);
                String[] projection = new String[]{
                        ProviderMeta.ProviderTableMeta._ID, ProviderMeta.ProviderTableMeta.FILE_PATH,
                        ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER
                };
                String where = ProviderMeta.ProviderTableMeta.FILE_PATH + "=? AND " +
                        ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?";
                String[] whereArgs = new String[]{remotePath, accountName};
                Cursor doubleCheck = query(db, uri, projection, where, whereArgs, null);
                // ugly patch; serious refactorization is needed to reduce work in
                // FileDataStorageManager and bring it to FileContentProvider
                if (doubleCheck == null || !doubleCheck.moveToFirst()) {
                    if (doubleCheck != null) {
                        doubleCheck.close();
                    }
                    long rowId = db.insert(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, null, values);
                    if (rowId > 0) {
                        return ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_FILE, rowId);
                    } else {
                        throw new SQLException("ERROR " + uri);
                    }
                } else {
                    // file is already inserted; race condition, let's avoid a duplicated entry
                    Uri insertedFileUri = ContentUris.withAppendedId(
                            ProviderMeta.ProviderTableMeta.CONTENT_URI_FILE,
                            doubleCheck.getLong(doubleCheck.getColumnIndex(ProviderMeta.ProviderTableMeta._ID))
                    );
                    doubleCheck.close();

                    return insertedFileUri;
                }

            case SHARES:
                Uri insertedShareUri;
                long rowId = db.insert(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME, null, values);
                if (rowId > 0) {
                    insertedShareUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_SHARE, rowId);
                } else {
                    throw new SQLException("ERROR " + uri);

                }
                updateFilesTableAccordingToShareInsertion(db, values);
                return insertedShareUri;

            case CAPABILITIES:
                Uri insertedCapUri;
                long id = db.insert(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME, null, values);
                if (id > 0) {
                    insertedCapUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_CAPABILITIES, id);
                } else {
                    throw new SQLException("ERROR " + uri);

                }
                return insertedCapUri;

            case UPLOADS:
                Uri insertedUploadUri;
                long uploadId = db.insert(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME, null, values);
                if (uploadId > 0) {
                    insertedUploadUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_UPLOADS, uploadId);
                    trimSuccessfulUploads(db);
                } else {
                    throw new SQLException("ERROR " + uri);

                }
                return insertedUploadUri;

            case CAMERA_UPLOADS_SYNC:
                Uri insertedCameraUploadUri;
                long cameraUploadId = db.insert(ProviderMeta.ProviderTableMeta.CAMERA_UPLOADS_SYNC_TABLE_NAME, null,
                        values);
                if (cameraUploadId > 0) {
                    insertedCameraUploadUri =
                            ContentUris.withAppendedId(ProviderMeta.ProviderTableMeta.CONTENT_URI_CAMERA_UPLOADS_SYNC,
                                    cameraUploadId);
                } else {
                    throw new SQLException("ERROR " + uri);
                }
                return insertedCameraUploadUri;

            default:
                throw new IllegalArgumentException("Unknown uri id: " + uri);
        }

    }

    private void updateFilesTableAccordingToShareInsertion(
            SQLiteDatabase db, ContentValues newShare
    ) {
        ContentValues fileValues = new ContentValues();
        int newShareType = newShare.getAsInteger(ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_TYPE);
        if (newShareType == ShareType.PUBLIC_LINK.getValue()) {
            fileValues.put(ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK, 1);
        } else if (
                newShareType == ShareType.USER.getValue() ||
                        newShareType == ShareType.GROUP.getValue() ||
                        newShareType == ShareType.FEDERATED.getValue()) {
            fileValues.put(ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE, 1);
        }

        String where = ProviderMeta.ProviderTableMeta.FILE_PATH + "=? AND " +
                ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?";
        String[] whereArgs = new String[]{
                newShare.getAsString(ProviderMeta.ProviderTableMeta.OCSHARES_PATH),
                newShare.getAsString(ProviderMeta.ProviderTableMeta.OCSHARES_ACCOUNT_OWNER)
        };
        db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, fileValues, where, whereArgs);
    }

    @Override
    public boolean onCreate() {
        mDbHelper = new DataBaseHelper(getContext());

        String authority = getContext().getResources().getString(R.string.authority);
        mUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mUriMatcher.addURI(authority, null, ROOT_DIRECTORY);
        mUriMatcher.addURI(authority, "file/", SINGLE_FILE);
        mUriMatcher.addURI(authority, "file/#", SINGLE_FILE);
        mUriMatcher.addURI(authority, "dir/", DIRECTORY);
        mUriMatcher.addURI(authority, "dir/#", DIRECTORY);
        mUriMatcher.addURI(authority, "shares/", SHARES);
        mUriMatcher.addURI(authority, "shares/#", SHARES);
        mUriMatcher.addURI(authority, "capabilities/", CAPABILITIES);
        mUriMatcher.addURI(authority, "capabilities/#", CAPABILITIES);
        mUriMatcher.addURI(authority, "uploads/", UPLOADS);
        mUriMatcher.addURI(authority, "uploads/#", UPLOADS);
        mUriMatcher.addURI(authority, "cameraUploadsSync/", CAMERA_UPLOADS_SYNC);
        mUriMatcher.addURI(authority, "cameraUploadsSync/#", CAMERA_UPLOADS_SYNC);

        return true;
    }


    @Override
    public Cursor query(
            @NonNull Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {

        Cursor result = null;
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        db.beginTransaction();
        try {
            result = query(db, uri, projection, selection, selectionArgs, sortOrder);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return result;
    }

    private Cursor query(
            SQLiteDatabase db,
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {

        if (selection != null && selectionArgs == null) {
            throw new IllegalArgumentException("Selection not allowed, use parameterized queries");
        }

        SQLiteQueryBuilder sqlQuery = new SQLiteQueryBuilder();

        sqlQuery.setTables(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME);

        switch (mUriMatcher.match(uri)) {
            case ROOT_DIRECTORY:
                sqlQuery.setProjectionMap(mFileProjectionMap);
                break;
            case DIRECTORY:
                String folderId = uri.getPathSegments().get(1);
                sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta.FILE_PARENT + "="
                        + folderId);
                sqlQuery.setProjectionMap(mFileProjectionMap);
                break;
            case SINGLE_FILE:
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                sqlQuery.setProjectionMap(mFileProjectionMap);
                break;
            case SHARES:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                sqlQuery.setProjectionMap(mShareProjectionMap);
                break;
            case CAPABILITIES:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhereEscapeString(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                sqlQuery.setProjectionMap(mCapabilityProjectionMap);
                break;
            case UPLOADS:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                sqlQuery.setProjectionMap(mUploadProjectionMap);
                break;
            case CAMERA_UPLOADS_SYNC:
                sqlQuery.setTables(ProviderMeta.ProviderTableMeta.CAMERA_UPLOADS_SYNC_TABLE_NAME);
                if (uri.getPathSegments().size() > 1) {
                    sqlQuery.appendWhere(ProviderMeta.ProviderTableMeta._ID + "="
                            + uri.getPathSegments().get(1));
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown uri id: " + uri);
        }

        String order;
        if (TextUtils.isEmpty(sortOrder)) {
            switch (mUriMatcher.match(uri)) {
                case SHARES:
                    order = ProviderMeta.ProviderTableMeta.OCSHARES_DEFAULT_SORT_ORDER;
                    break;
                case CAPABILITIES:
                    order = ProviderMeta.ProviderTableMeta.CAPABILITIES_DEFAULT_SORT_ORDER;
                    break;
                case UPLOADS:
                    order = ProviderMeta.ProviderTableMeta.UPLOADS_DEFAULT_SORT_ORDER;
                    break;
                case CAMERA_UPLOADS_SYNC:
                    order = ProviderMeta.ProviderTableMeta.CAMERA_UPLOADS_SYNC_DEFAULT_SORT_ORDER;
                    break;
                default: // Files
                    order = ProviderMeta.ProviderTableMeta.FILE_DEFAULT_SORT_ORDER;
                    break;
            }
        } else {
            order = sortOrder;
        }

        // DB case_sensitive
        db.execSQL("PRAGMA case_sensitive_like = true");
        Cursor c = sqlQuery.query(db, projection, selection, selectionArgs, null, null, order);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {

        int count = 0;
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            count = update(db, uri, values, selection, selectionArgs);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    private int update(
            SQLiteDatabase db,
            Uri uri,
            ContentValues values,
            String selection,
            String[] selectionArgs
    ) {
        switch (mUriMatcher.match(uri)) {
            case DIRECTORY:
                return 0; //updateFolderSize(db, selectionArgs[0]);
            case SHARES:
                return db.update(
                        ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME, values, selection, selectionArgs
                );
            case CAPABILITIES:
                return db.update(
                        ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME, values, selection, selectionArgs
                );
            case UPLOADS:
                int ret = db.update(
                        ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME, values, selection, selectionArgs
                );
                trimSuccessfulUploads(db);
                return ret;
            case CAMERA_UPLOADS_SYNC:
                return db.update(
                        ProviderMeta.ProviderTableMeta.CAMERA_UPLOADS_SYNC_TABLE_NAME, values, selection,
                        selectionArgs);
            default:
                return db.update(
                        ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME, values, selection, selectionArgs
                );
        }
    }

    @NonNull
    @Override
    public ContentProviderResult[] applyBatch(@NonNull ArrayList<ContentProviderOperation> operations)
            throws OperationApplicationException {
        Log_OC.d("FileContentProvider", "applying batch in provider " + this +
                " (temporary: " + isTemporary() + ")");
        ContentProviderResult[] results = new ContentProviderResult[operations.size()];
        int i = 0;

        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.beginTransaction();  // it's supposed that transactions can be nested
        try {
            for (ContentProviderOperation operation : operations) {
                results[i] = operation.apply(this, results, i);
                i++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        Log_OC.d("FileContentProvider", "applied batch in provider " + this);
        return results;
    }


    private class DataBaseHelper extends SQLiteOpenHelper {

        DataBaseHelper(Context context) {
            super(context, ProviderMeta.DB_NAME, null, ProviderMeta.DB_VERSION);

        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // files table
            Log_OC.i("SQL", "Entering in onCreate");
            createFilesTable(db);

            // Create ocshares table
            createOCSharesTable(db);

            // Create capabilities table
            createCapabilitiesTable(db);

            // Create uploads table
            createUploadsTable(db);

            // Create user profiles table
            createUserProfilesTable(db);

            // Create camera upload sync table
            createCameraUploadsSyncTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log_OC.i("SQL", "Entering in onUpgrade");
            boolean upgraded = false;
            if (oldVersion == 1 && newVersion >= 2) {
                Log_OC.i("SQL", "Entering in the #1 ADD in onUpgrade");
                db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                        " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_KEEP_IN_SYNC + " INTEGER " +
                        " DEFAULT 0");
                upgraded = true;
            }
            if (oldVersion < 3 && newVersion >= 3) {
                Log_OC.i("SQL", "Entering in the #2 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA +
                            " INTEGER " + " DEFAULT 0");

                    // assume there are not local changes pending to upload
                    db.execSQL("UPDATE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " SET " + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA + " = "
                            + System.currentTimeMillis() +
                            " WHERE " + ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 4 && newVersion >= 4) {
                Log_OC.i("SQL", "Entering in the #3 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA +
                            " INTEGER " + " DEFAULT 0");

                    db.execSQL("UPDATE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " SET " + ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA + " = " +
                            ProviderMeta.ProviderTableMeta.FILE_MODIFIED +
                            " WHERE " + ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 5 && newVersion >= 5) {
                Log_OC.i("SQL", "Entering in the #4 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_ETAG + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 6 && newVersion >= 6) {
                Log_OC.i("SQL", "Entering in the #5 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK + " INTEGER " +
                            " DEFAULT 0");

                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_PUBLIC_LINK + " TEXT " +
                            " DEFAULT NULL");

                    // Create table ocshares
                    createOCSharesTable(db);

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 7 && newVersion >= 7) {
                Log_OC.i("SQL", "Entering in the #7 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_PERMISSIONS + " TEXT " +
                            " DEFAULT NULL");

                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 8 && newVersion >= 8) {
                Log_OC.i("SQL", "Entering in the #8 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_UPDATE_THUMBNAIL + " INTEGER " +
                            " DEFAULT 0");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 9 && newVersion >= 9) {
                Log_OC.i("SQL", "Entering in the #9 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_IS_DOWNLOADING + " INTEGER " +
                            " DEFAULT 0");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 10 && newVersion >= 10) {
                Log_OC.i("SQL", "Entering in the #10 ADD in onUpgrade");
                updateAccountName(db);
                upgraded = true;
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 11 && newVersion >= 11) {
                Log_OC.i("SQL", "Entering in the #11 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_ETAG_IN_CONFLICT + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 12 && newVersion >= 12) {
                Log_OC.i("SQL", "Entering in the #12 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE + " INTEGER " +
                            " DEFAULT 0");
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 13 && newVersion >= 13) {
                Log_OC.i("SQL", "Entering in the #13 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    // Create capabilities table
                    createCapabilitiesTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (oldVersion < 14 && newVersion >= 14) {
                Log_OC.i("SQL", "Entering in the #14 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    // drop old instant_upload table
                    db.execSQL("DROP TABLE IF EXISTS " + "instant_upload" + ";");
                    // Create uploads table
                    createUploadsTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (oldVersion < 15 && newVersion >= 15) {
                Log_OC.i("SQL", "Entering in the #15 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    // Create user profiles table
                    createUserProfilesTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 16 && newVersion >= 16) {
                Log_OC.i("SQL", "Entering in the #16 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_TREE_ETAG + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 17 && newVersion >= 17) {
                Log_OC.i("SQL", "Entering in the #17 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.OCSHARES_NAME + " TEXT " +
                            " DEFAULT NULL");
                    upgraded = true;
                    db.setTransactionSuccessful();

                    // SQLite does not allow to drop a columns; ftm, we'll not recreate
                    // the files table without the column FILE_PUBLIC_LINK, just forget about

                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 18 && newVersion >= 18) {
                Log_OC.i("SQL", "Entering in the #18 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.OCSHARES_URL + " TEXT " +
                            " DEFAULT NULL");
                    upgraded = true;
                    db.setTransactionSuccessful();

                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 19 && newVersion >= 19) {

                Log_OC.i("SQL", "Entering in the #19 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_MULTIPLE
                            + " INTEGER " + " DEFAULT -1");
                    upgraded = true;
                    db.setTransactionSuccessful();

                } finally {
                    db.endTransaction();
                }
            }
            if (oldVersion < 20 && newVersion >= 20) {

                Log_OC.i("SQL", "Entering in the #20 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.
                            CAPABILITIES_SHARING_PUBLIC_SUPPORTS_UPLOAD_ONLY + " INTEGER " +
                            " DEFAULT -1");
                    upgraded = true;
                    db.setTransactionSuccessful();

                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);
            }

            if (oldVersion < 21 && newVersion >= 21) {
                Log_OC.i("SQL", "Entering in the #21 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    db.execSQL("ALTER TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME +
                            " ADD COLUMN " + ProviderMeta.ProviderTableMeta.FILE_PRIVATE_LINK + " TEXT " +
                            " DEFAULT NULL");

                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded)
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);

            if (oldVersion < 22 && newVersion >= 22) {
                Log_OC.i("SQL", "Entering in the #22 ADD in onUpgrade");
                db.beginTransaction();
                try {
                    createCameraUploadsSyncTable(db);
                    upgraded = true;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            if (!upgraded) {
                Log_OC.i("SQL", "OUT of the ADD in onUpgrade; oldVersion == " + oldVersion +
                        ", newVersion == " + newVersion);
            }
        }
    }

    private void createFilesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.FILE_NAME + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_PATH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_PARENT + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_CREATION + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_MODIFIED + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_CONTENT_TYPE + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_CONTENT_LENGTH + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_KEEP_IN_SYNC + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_LAST_SYNC_DATE_FOR_DATA + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_MODIFIED_AT_LAST_SYNC_FOR_DATA + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_ETAG + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_TREE_ETAG + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_SHARED_VIA_LINK + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.FILE_PUBLIC_LINK + " TEXT, "
                + ProviderMeta.ProviderTableMeta.FILE_PERMISSIONS + " TEXT null,"
                + ProviderMeta.ProviderTableMeta.FILE_REMOTE_ID + " TEXT null,"
                + ProviderMeta.ProviderTableMeta.FILE_UPDATE_THUMBNAIL + " INTEGER," //boolean
                + ProviderMeta.ProviderTableMeta.FILE_IS_DOWNLOADING + " INTEGER," //boolean
                + ProviderMeta.ProviderTableMeta.FILE_ETAG_IN_CONFLICT + " TEXT,"
                + ProviderMeta.ProviderTableMeta.FILE_SHARED_WITH_SHAREE + " INTEGER,"
                + ProviderMeta.ProviderTableMeta.FILE_PRIVATE_LINK + " TEXT );"
        );
    }

    private void createOCSharesTable(SQLiteDatabase db) {
        // Create ocshares table
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.OCSHARES_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_FILE_SOURCE + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_ITEM_SOURCE + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_TYPE + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_PATH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_PERMISSIONS + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARED_DATE + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_EXPIRATION_DATE + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_TOKEN + " TEXT, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_SHARE_WITH_DISPLAY_NAME + " TEXT, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_IS_DIRECTORY + " INTEGER, "  // boolean
                + ProviderMeta.ProviderTableMeta.OCSHARES_USER_ID + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_ID_REMOTE_SHARED + " INTEGER,"
                + ProviderMeta.ProviderTableMeta.OCSHARES_ACCOUNT_OWNER + " TEXT, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_URL + " TEXT, "
                + ProviderMeta.ProviderTableMeta.OCSHARES_NAME + " TEXT );");
    }

    private void createCapabilitiesTable(SQLiteDatabase db) {
        // Create capabilities table
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.CAPABILITIES_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_ACCOUNT_NAME + " TEXT, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MAYOR + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MINOR + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_MICRO + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_STRING + " TEXT, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_VERSION_EDITION + " TEXT, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_CORE_POLLINTERVAL + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_API_ENABLED + " INTEGER, " // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_ENABLED + " INTEGER, "  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_PASSWORD_ENFORCED + " INTEGER, "    // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENABLED + " INTEGER, "  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_DAYS + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_EXPIRE_DATE_ENFORCED + " INTEGER, " // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SEND_MAIL + " INTEGER, "    // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_UPLOAD + " INTEGER, "       // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_MULTIPLE + " INTEGER, "     // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_PUBLIC_SUPPORTS_UPLOAD_ONLY + " INTEGER, "     // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_USER_SEND_MAIL + " INTEGER, "      // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_RESHARING + " INTEGER, "           // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_OUTGOING + " INTEGER, "     // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_SHARING_FEDERATION_INCOMING + " INTEGER, "     // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_BIGFILECHUNKING + " INTEGER, "   // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_UNDELETE + " INTEGER, "  // boolean
                + ProviderMeta.ProviderTableMeta.CAPABILITIES_FILES_VERSIONING + " INTEGER );");   // boolean
    }

    private void createUploadsTable(SQLiteDatabase db) {
        // Create uploads table
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_PATH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_REMOTE_PATH + " TEXT, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_ACCOUNT_NAME + " TEXT, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_FILE_SIZE + " LONG, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_STATUS + " INTEGER, "               // UploadStatus
                + ProviderMeta.ProviderTableMeta.UPLOADS_LOCAL_BEHAVIOUR + " INTEGER, "      // Upload LocalBehaviour
                + ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_TIME + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_FORCE_OVERWRITE + " INTEGER, "  // boolean
                + ProviderMeta.ProviderTableMeta.UPLOADS_IS_CREATE_REMOTE_FOLDER + " INTEGER, "  // boolean
                + ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_END_TIMESTAMP + " INTEGER, "
                + ProviderMeta.ProviderTableMeta.UPLOADS_LAST_RESULT + " INTEGER, "     // Upload LastResult
                + ProviderMeta.ProviderTableMeta.UPLOADS_CREATED_BY + " INTEGER );"    // Upload createdBy
        );
    }

    private void createUserProfilesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.USER_AVATARS__TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.USER_AVATARS__ACCOUNT_NAME + " TEXT, "
                + ProviderMeta.ProviderTableMeta.USER_AVATARS__CACHE_KEY + " TEXT, "
                + ProviderMeta.ProviderTableMeta.USER_AVATARS__MIME_TYPE + " TEXT, "
                + ProviderMeta.ProviderTableMeta.USER_AVATARS__ETAG + " TEXT );"
        );
    }

    private void createCameraUploadsSyncTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + ProviderMeta.ProviderTableMeta.CAMERA_UPLOADS_SYNC_TABLE_NAME + "("
                + ProviderMeta.ProviderTableMeta._ID + " INTEGER PRIMARY KEY, "
                + ProviderMeta.ProviderTableMeta.PICTURES_LAST_SYNC_TIMESTAMP + " INTEGER,"
                + ProviderMeta.ProviderTableMeta.VIDEOS_LAST_SYNC_TIMESTAMP + " INTEGER);"
        );
    }

    /**
     * Version 10 of database does not modify its scheme. It coincides with the upgrade of the ownCloud account names
     * structure to include in it the path to the server instance. Updating the account names and path to local files
     * in the files table is a must to keep the existing account working and the database clean.
     *
     * See {@link org.elastos.android.authentication.AccountUtils#updateAccountVersion(android.content.Context)}
     *
     * @param db Database where table of files is included.
     */
    private void updateAccountName(SQLiteDatabase db) {
        Log_OC.d("SQL", "THREAD:  " + Thread.currentThread().getName());
        AccountManager ama = AccountManager.get(getContext());
        try {
            // get accounts from AccountManager ;  we can't be sure if accounts in it are updated or not although
            // we know the update was previously done in {link @FileActivity#onCreate} because the changes through
            // AccountManager are not synchronous
            Account[] accounts = AccountManager.get(getContext()).getAccountsByType(
                    MainApp.getAccountType());
            String serverUrl, username, oldAccountName, newAccountName;
            for (Account account : accounts) {
                // build both old and new account name
                serverUrl = ama.getUserData(account, AccountUtils.Constants.KEY_OC_BASE_URL);
                username = AccountUtils.getUsernameForAccount(account);
                oldAccountName = AccountUtils.buildAccountNameOld(Uri.parse(serverUrl), username);
                newAccountName = AccountUtils.buildAccountName(Uri.parse(serverUrl), username);

                // update values in database
                db.beginTransaction();
                try {
                    ContentValues cv = new ContentValues();
                    cv.put(ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER, newAccountName);
                    int num = db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                            cv,
                            ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=?",
                            new String[]{oldAccountName});

                    Log_OC.d("SQL", "Updated account in database: old name == " + oldAccountName +
                            ", new name == " + newAccountName + " (" + num + " rows updated )");

                    // update path for downloaded files
                    updateDownloadedFiles(db, newAccountName, oldAccountName);

                    db.setTransactionSuccessful();

                } catch (SQLException e) {
                    Log_OC.e(TAG, "SQL Exception upgrading account names or paths in database", e);
                } finally {
                    db.endTransaction();
                }
            }
        } catch (Exception e) {
            Log_OC.e(TAG, "Exception upgrading account names or paths in database", e);
        }
    }


    /**
     * Rename the local ownCloud folder of one account to match the a rename of the account itself. Updates the
     * table of files in database so that the paths to the local files keep being the same.
     *
     * @param db             Database where table of files is included.
     * @param newAccountName New name for the target OC account.
     * @param oldAccountName Old name of the target OC account.
     */
    private void updateDownloadedFiles(SQLiteDatabase db, String newAccountName,
                                       String oldAccountName) {

        String whereClause = ProviderMeta.ProviderTableMeta.FILE_ACCOUNT_OWNER + "=? AND " +
                ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + " IS NOT NULL";

        Cursor c = db.query(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                null,
                whereClause,
                new String[]{newAccountName},
                null, null, null);

        try {
            if (c.moveToFirst()) {
                // create storage path
                String oldAccountPath = FileStorageUtils.getSavePath(oldAccountName);
                String newAccountPath = FileStorageUtils.getSavePath(newAccountName);

                // move files
                File oldAccountFolder = new File(oldAccountPath);
                File newAccountFolder = new File(newAccountPath);
                oldAccountFolder.renameTo(newAccountFolder);

                // update database
                do {
                    // Update database
                    String oldPath = c.getString(
                            c.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH));
                    OCFile file = new OCFile(
                            c.getString(c.getColumnIndex(ProviderMeta.ProviderTableMeta.FILE_PATH)));
                    String newPath = FileStorageUtils.getDefaultSavePathFor(newAccountName, file);

                    ContentValues cv = new ContentValues();
                    cv.put(ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH, newPath);
                    db.update(ProviderMeta.ProviderTableMeta.FILE_TABLE_NAME,
                            cv,
                            ProviderMeta.ProviderTableMeta.FILE_STORAGE_PATH + "=?",
                            new String[]{oldPath});

                    Log_OC.v("SQL", "Updated path of downloaded file: old file name == " + oldPath +
                            ", new file name == " + newPath);

                } while (c.moveToNext());
            }
        } finally {
            c.close();
        }

    }

    /**
     * Grants that total count of successful uploads stored is not greater than MAX_SUCCESSFUL_UPLOADS.
     * 
     * Removes older uploads if needed.
     */
    private void trimSuccessfulUploads(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery(
                    "delete from " + ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME +
                            " where " + ProviderMeta.ProviderTableMeta.UPLOADS_STATUS + " == "
                            + UploadsStorageManager.UploadStatus.UPLOAD_SUCCEEDED.getValue() +
                            " and " + ProviderMeta.ProviderTableMeta._ID +
                            " not in (select " + ProviderMeta.ProviderTableMeta._ID +
                            " from " + ProviderMeta.ProviderTableMeta.UPLOADS_TABLE_NAME +
                            " where " + ProviderMeta.ProviderTableMeta.UPLOADS_STATUS + " == "
                            + UploadsStorageManager.UploadStatus.UPLOAD_SUCCEEDED.getValue() +
                            " order by " + ProviderMeta.ProviderTableMeta.UPLOADS_UPLOAD_END_TIMESTAMP +
                            " desc limit " + MAX_SUCCESSFUL_UPLOADS +
                            ")",
                    null
            );
            c.moveToFirst(); // do something with the cursor, or deletion doesn't happen; true story

        } catch (Exception e) {
            Log_OC.e(
                    TAG,
                    "Something wrong trimming successful uploads, database could grow more than expected",
                    e
            );

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }
}