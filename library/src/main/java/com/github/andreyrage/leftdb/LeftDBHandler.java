/*
 * Copyright 2017 Andrii Horishnii
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.andreyrage.leftdb;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class LeftDBHandler extends SQLiteOpenHelper {

	private static final String TAG = LeftDBHandler.class.getName();
	private SQLiteDatabase dataBase;
	private Context context;
	private String name;
	private String path;
	private int version;

	private OnDbChangeCallback mCallback;
	private boolean isTemp;

	/**
	 * Rightutils compatibility
	 * */
	public LeftDBHandler(@NonNull Context context, @NonNull String name, int version, @NonNull OnDbChangeCallback mCallback) {
		super(context, name, null, version);
		this.context = context;
		this.name = name;
		this.path = context.getFilesDir() + "/databases/";
		this.version = version;
		this.mCallback = mCallback;
		try {
			createOrCopyDataBaseFromAssets();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected LeftDBHandler(@NonNull Context context, @NonNull String name, int version, boolean tempDb) {
		super(context, name, null, version);
		this.context = context;
		this.name = name;
		this.path = context.getFilesDir() + "/databases/";
		this.version = version;
		this.isTemp = tempDb;
		try {
			createOrCopyDataBaseFromAssets();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public Context getContext() {
		return context;
	}

	public String getName() {
		return name;
	}

	public int getVersion() {
		return version;
	}

	private void createOrCopyDataBaseFromAssets() throws IOException {
		if (!checkDataBase()) {
			if (assetsDbExists()) {
				Log.i(TAG, "copy DataBase");
				copyDataBase();
			} else {
				Log.i(TAG, "create DataBase");
				createDataBase();
			}
		}
	}

	/**
	 * Rightutils compatibility
	 * */
	public void deleteDataBase() {
		if (checkDataBase()) {
			close();
			File dbFile = new File(path + getDbFileName());
			dbFile.delete();
		}
	}

	protected boolean assetsDbExists() throws IOException {
		return Arrays.asList(context.getAssets().list("")).contains(name);
	}

	protected boolean checkDataBase() {
		File dbFile = new File(path + getDbFileName());
		return dbFile.exists();
	}

	/**
	 * Rightutils compatibility
	 * */
	private void createDataBase() {
		SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(getDbFile(), null);
		db.close();
	}

	private void copyDataBase() throws IOException {
		InputStream myInput = context.getAssets().open(name);

		final File file = getDbFile();

		OutputStream myOutput = new FileOutputStream(file);
		byte[] buffer = new byte[1024];
		int length;
		while ((length = myInput.read(buffer)) > 0) {
			myOutput.write(buffer, 0, length);
		}
		myOutput.flush();
		myOutput.close();
		myInput.close();
	}

	private File getDbFile() {
		final File dir = new File(path);
		dir.mkdirs();
		return new File(dir, getDbFileName());
	}

	private String getDbFileName() {
		if (isTemp) {
			return String.format("LeftDbTemp_v%d_%s", version, this.name);
		} else {
			return name;
		}
	}

	/**
	 * Rightutils compatibility
	 * */
	public SQLiteDatabase openDataBase(int openType) throws SQLException {
		String myPath = path + getDbFileName();
		dataBase = SQLiteDatabase.openDatabase(myPath, null, openType);
		dataBase.execSQL("PRAGMA foreign_keys=ON;");
		if (!isTemp) {
			validateVersion(dataBase);
		}
		return dataBase;
	}

	private void validateVersion(@NonNull SQLiteDatabase db) {
		int currentVersion = db.getVersion();
		if (currentVersion != version) {
			if (mCallback != null) {
				if (db.isReadOnly()) {
					Log.e(TAG, "Can't upgrade read-only database from version " +
							currentVersion + " to " + version);
				}
				db.beginTransaction();
				try {
					if (currentVersion == 0 && !assetsDbExists()) {
						mCallback.onCreate(db);
					} else {
						if (currentVersion > version) {
							mCallback.onDowngrade(db, currentVersion, version);
						} else if (currentVersion < version) {
							mCallback.onUpgrade(db, currentVersion, version);
						}
					}
					db.setVersion(version);
					db.setTransactionSuccessful();
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					db.endTransaction();
				}
			} else {
				Log.e(TAG, "Can't upgrade database from version " + currentVersion
						+ " to " + version + ", cause onVersionChangeCallback is null");
			}
		}
	}

	@Override
	public synchronized void close() {
		if (dataBase != null)
			dataBase.close();
		super.close();
	}

	/**
	 * Unsupported
	 * */
	@Override
	public void onCreate(SQLiteDatabase db) {
	}

	/**
	 * Unsupported
	 * */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
	}

	/**
	 * Rightutils compatibility
	 * */
	@Override
	public SQLiteDatabase getWritableDatabase() {
		return openDataBase(SQLiteDatabase.OPEN_READWRITE);
	}

	/**
	 * Rightutils compatibility
	 * */
	@Override
	public SQLiteDatabase getReadableDatabase() {
		return openDataBase(SQLiteDatabase.OPEN_READONLY);
	}

	public interface OnDbChangeCallback {
		void onCreate(SQLiteDatabase db);
		void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);
		void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion);
	}
}
