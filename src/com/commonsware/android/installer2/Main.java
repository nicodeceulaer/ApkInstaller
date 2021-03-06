/***
  Copyright (c) 2008-2012 CommonsWare, LLC
  Licensed under the Apache License, Version 2.0 (the "License"); you may not
  use this file except in compliance with the License. You may obtain	a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required
  by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS,	WITHOUT	WARRANTIES OR CONDITIONS
  OF ANY KIND, either express or implied. See the License for the specific
  language governing permissions and limitations under the License.
	
  From _The Busy Coder's Guide to Android Development_
    http://commonsware.com/Android
*/

package com.commonsware.android.installer2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Main extends Activity {
    static final int REQUEST_INSTALL = 1;
    static final int REQUEST_UNINSTALL = 2;
    static final String TAG = "InstallApk";

    static final String ASSET_PACKAGE_NAME = "org.droidtv.apkstorage";
    static final String APK_TO_INSTALL     = "HelloActivity.apk";

    private PackageManager mPackageManager;
    private TvApkInstallerInstallObserver mInstallObserver;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if( mPackageManager == null ) {
            mPackageManager = getPackageManager();
        }

        if( mInstallObserver == null ) {
            mInstallObserver = new TvApkInstallerInstallObserver();
        }

        Log.i(TAG, "onCreate" );
        setContentView(R.layout.activity_main);

        // Watch for button clicks.
        Button button = (Button)findViewById(R.id.unknown_source);
        button.setOnClickListener(mUnknownSourceListener);
        button = (Button)findViewById(R.id.my_source);
        button.setOnClickListener(mMySourceListener);
        button = (Button)findViewById(R.id.replace);
        button.setOnClickListener(mReplaceListener);
        button = (Button)findViewById(R.id.uninstall);
        button.setOnClickListener(mUninstallListener);
        button = (Button)findViewById(R.id.uninstall_result);
        button.setOnClickListener(mUninstallResultListener);
        button = (Button) findViewById(R.id.providerInstall);
        button.setOnClickListener(mFileProviderInstallListener);
        button = (Button) findViewById( R.id.fileUriInstall);
        button.setOnClickListener(mFileUriInstallListener);
        button = (Button) findViewById( R.id.externalFileUriInstall );
        button.setOnClickListener(mExtFileUriInstallListener);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
         Log.i(TAG, "onActivityResult requestCode=" + requestCode + ", resultCode= " + requestCode);

        if (requestCode == REQUEST_INSTALL) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Install succeeded!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Install canceled!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Install Failed!", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_UNINSTALL) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Uninstall succeeded!", Toast.LENGTH_SHORT).show();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Uninstall canceled!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Uninstall Failed!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private OnClickListener mUnknownSourceListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mUnknownSourceListener");
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(Uri.fromFile(prepareApk( APK_TO_INSTALL, null ) ) );
            startActivity(intent);
        }
    };

    private OnClickListener mMySourceListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mMySourceListener");
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(Uri.fromFile( prepareApk( APK_TO_INSTALL, null ) ) );
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                    getApplicationInfo().packageName);
            startActivityForResult(intent, REQUEST_INSTALL);
        }
    };

    private OnClickListener mReplaceListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mReplaceListener");
            Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            intent.setData(Uri.fromFile(prepareApk( APK_TO_INSTALL, null ) ) );
            intent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            intent.putExtra(Intent.EXTRA_ALLOW_REPLACE, true);
            intent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME,
                    getApplicationInfo().packageName);
            startActivityForResult(intent, REQUEST_INSTALL);
        }
    };

    private OnClickListener mUninstallListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mUninstallListener");
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse(
                    "package:com.commonsware.android.skeleton"));
            startActivity(intent);
        }
    };

    private OnClickListener mUninstallResultListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mUninstallResultListener");
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            intent.setData(Uri.parse(
                    "package:com.commonsware.android.skeleton"));
            intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(intent, REQUEST_UNINSTALL);
        }
    };

    private OnClickListener mFileProviderInstallListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mFileProviderInstallListener");
            ProviderInstallApk("HelloActivity.apk");
        }
    };

    private OnClickListener mFileUriInstallListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mFileUrlInstallListener" );
            FileUriInstallApk(APK_TO_INSTALL);
        }
    };

    private OnClickListener mExtFileUriInstallListener = new OnClickListener() {
        public void onClick(View v) {
            Log.i(TAG, "mExtFileUrlInstallListener" );
            ExternalFileUriInstallApk( APK_TO_INSTALL, ASSET_PACKAGE_NAME );
        }
    };

    /**  install using package manager, use content provider URL
     *   this currently does NOT work, due to InstallPackageManager not allowing anything but file:// URI's
     */
    private void ProviderInstallApk(String apkName )  {
        File newfile = prepareApk(apkName, null );

        if( !newfile.exists() ){
            Log.i(TAG, "file does not exist " + newfile );
            return;
        }
        Log.i(TAG, "get a URI for file " + newfile.getAbsolutePath() );
        Uri contentUri = FileProvider.getUriForFile(this, "com.commonsware.android.installer2", newfile);

        Log.i(TAG, "Install apk - contentUri: " + contentUri );

        Class<?>[] types = new Class[] {Uri.class, IPackageInstallObserver.class, int.class, String.class};
        Method method = null;
        try {
            method = mPackageManager.getClass().getMethod("installPackage", types);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return;
        }

        try {
            method.invoke(mPackageManager, new Object[] { contentUri, mInstallObserver, 0, this.getPackageName() } );
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        // only works on platform development env
        // mPackageManager.installPackage(contentUri, mInstallObserver, 0, this.getPackageName());
    }

    private void FileInstallApk( String packageName, File apkFile )
    {
        if( !apkFile.exists() ){
            Log.i(TAG, "file does not exist " + apkFile );
            return;
        }
        Log.i(TAG, "get a URI for file " + apkFile.getAbsolutePath() );
        Uri contentUri = Uri.fromFile( apkFile );
        Log.i(TAG, "Install apk - contentUri: " + contentUri );

        // only works on platform development env
        // mPackageManager.installPackage(contentUri, mInstallObserver, 0, this.getPackageName());
        Class<?>[] types = new Class[] {Uri.class, IPackageInstallObserver.class, int.class, String.class};
        Method method = null;
        try {
            method = mPackageManager.getClass().getMethod("installPackage", types);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            return;
        }

        try {
            method.invoke(mPackageManager, new Object[] { contentUri, mInstallObserver, 0, this.getPackageName() } );
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    // install an apk we get from within our own assets,
    // using a file:// URI
    private void FileUriInstallApk(String apkName )  {
        ExternalFileUriInstallApk( apkName, null);
    }

    // install an apk we get from another app's assets
    // using a file:// URI
    private void ExternalFileUriInstallApk(String apkName, String assetPackageName )  {
        File newfile = prepareApk( apkName, assetPackageName );
        FileInstallApk( apkName, newfile );
    }

    // retrieve a stream handle for an asset from a package
    private InputStream getApkStream( String assetName, String assetPackageName )
    {
        InputStream  is = null;
        Context      ctx;
        AssetManager am;

        try {
            if( assetPackageName == null ) {
                Log.d( TAG, "use local assets" );
                ctx = (Context)this;
            } else {
                Log.d(TAG, "get assets from package " + assetPackageName );
                ctx = createPackageContext( assetPackageName, 0);
            }
            am = ctx.getAssets();

            // debug code - only needed if you want to work out why we can't find an asset
            //
            // String[] filesinApklistfolder = am.list("");
            // List<String> assetApkList = Arrays.asList(filesinApklistfolder);
            // for( String virginapp : assetApkList ) {
            //    Log.i( TAG, "Asset found : " + virginapp );
            // }

            Log.d(TAG, "retrieving asset " + assetName );
            is = am.open( assetName );
        } catch (Exception e ) {
            Log.e(TAG, "failed getting asset " + assetName + " from package " + assetPackageName );
            e.printStackTrace();
        }
        return is;
    }

    private File prepareApk(String assetName, String assetPackageName ) {
        Log.i(TAG, "prepareApk " + assetName );
        // Copy the given asset out into a file so that it can be installed.
        // Returns the path to the file.
        byte[]           buffer = new byte[8192];
        InputStream      is     = null;
        FileOutputStream fout   = null;
        try {
            is = getApkStream( assetName, assetPackageName );
            if( is == null ) {
                return null;
            }

            fout = openFileOutput("tmp.apk", Context.MODE_WORLD_READABLE);
            int n;
            while ((n=is.read(buffer)) >= 0) {
                Log.i(TAG, "writing apk data " + n );
                fout.write(buffer, 0, n);
            }
        } catch (IOException e) {
            Log.i("InstallApk", "Failed transferring", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (IOException e) {
            }
            try {
                if (fout != null) {
                    fout.close();
                }
            } catch (IOException e) {
            }
        }

        return getFileStreamPath("tmp.apk");
    }

    class TvApkInstallerInstallObserver extends IPackageInstallObserver.Stub
    {
        @Override
        public void packageInstalled(String packageName, int returnCode)
        {
            if( packageName == null){
                Log.i(TAG, "Packagename was null ?? " );
                packageName = "unknown package";
            }
            Log.i(TAG, "packageInstalled - packageName=" + packageName + ", returnCode=" + returnCode);
        }
    }
}
