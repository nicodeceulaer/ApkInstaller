package com.commonsware.android.installer2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import org.xmlpull.v1.XmlPullParserException;

public class FileProvider extends ContentProvider {
    private  static final String TAG = "FileProvider";
    private static final String[] COLUMNS = { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE };
    private static final String META_DATA_FILE_PROVIDER_PATHS = "android.support.FILE_PROVIDER_PATHS";

    private static final String TAG_ROOT_PATH = "root-path";
    private static final String TAG_FILES_PATH = "files-path";
    private static final String TAG_CACHE_PATH = "cache-path";
    private static final String TAG_EXTERNAL = "external-path";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_PATH = "path";

    private static final File DEVICE_ROOT = new File("/");

    // @GuardedBy("sCache")
    private static HashMap<String, PathStrategy> sCache = new HashMap<String, PathStrategy>();

    private PathStrategy mStrategy;

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        // Sanity check our security
/*     if (info.exported) {
            throw new SecurityException("Provider must not be exported");
        }*/
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }

        mStrategy = getPathStrategy(context, info.authority);
    }

    public static Uri getUriForFile(Context context, String authority, File file) {
        final PathStrategy strategy = getPathStrategy(context, authority);
        return strategy.getUriForFile(file);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // ContentProvider has already checked granted permissions
        final File file = mStrategy.getFileForUri(uri);

        if (projection == null) {
            projection = COLUMNS;
        }

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = file.getName();
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = file.length();
            }
        }
        cols = copyOf(cols, i);
        values = copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        // ContentProvider has already checked granted permissions
        final File file = mStrategy.getFileForUri(uri);

        final int lastDot = file.getName().lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = file.getName().substring(lastDot + 1);
            final String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("No external inserts");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("No external updates");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.i(TAG, " delete Binder.getcall = " + Binder.getCallingUid());
        int mDefContainerUid = 0;

        ApplicationInfo appInfo = null;
        try {
            appInfo = getContext().getPackageManager().
                    getApplicationInfo("org.droidtv.nettv.market", 0);
        } catch (NameNotFoundException e) {
            Log.wtf(TAG, "delete Could not get ApplicationInfo for org.droidtv.nettv.market", e);
        }
        if (appInfo != null) {
            mDefContainerUid = appInfo.uid;
        }

        if(Binder.getCallingUid()!=mDefContainerUid){
            Log.i(TAG, "delete FileProvider Null returned");
            return 0;
        }

        // ContentProvider has already checked granted permissions
        final File file = mStrategy.getFileForUri(uri);
        return file.delete() ? 1 : 0;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Log.i(TAG, "Binder.getcall = " + Binder.getCallingUid());
        int mDefContainerUid = 0;

        ApplicationInfo appInfo = null;
        try {
            appInfo = getContext().getPackageManager().getApplicationInfo("com.android.defcontainer", 0);
        } catch (NameNotFoundException e) {
            Log.wtf(TAG, "Could not get ApplicationInfo for com.android.defconatiner", e);
        }
        if (appInfo != null) {
            mDefContainerUid = appInfo.uid;
        }


        if(Binder.getCallingUid()!=mDefContainerUid){
            Log.i(TAG, "FileProvider Null returned");
            return null;
        }

        // ContentProvider has already checked granted permissions
        final File file = mStrategy.getFileForUri(uri);
        final int fileMode = modeToMode(mode);
        return ParcelFileDescriptor.open(file, fileMode);
    }

    private static PathStrategy getPathStrategy(Context context, String authority) {
        PathStrategy strat;
        synchronized (sCache) {
            strat = sCache.get(authority);
            if (strat == null) {
                try {
                    strat = parsePathStrategy(context, authority);
                } catch (IOException e) {
                    throw new IllegalArgumentException(
                            "Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e);
                } catch (XmlPullParserException e) {
                    throw new IllegalArgumentException(
                            "Failed to parse " + META_DATA_FILE_PROVIDER_PATHS + " meta-data", e);
                }
                sCache.put(authority, strat);
            }
        }
        return strat;
    }

    private static PathStrategy parsePathStrategy(Context context, String authority)
            throws IOException, XmlPullParserException {
        final SimplePathStrategy strat = new SimplePathStrategy(authority);
        final PackageManager     pm    = context.getPackageManager();
        final ProviderInfo       info  = pm.resolveContentProvider(authority, PackageManager.GET_META_DATA);

        final XmlResourceParser in = info.loadXmlMetaData( pm, META_DATA_FILE_PROVIDER_PATHS);
        if (in == null) {
            throw new IllegalArgumentException(
                    "Missing " + META_DATA_FILE_PROVIDER_PATHS + " meta-data");
        }

        int type;
        while ((type = in.next()) != END_DOCUMENT) {
            if (type == START_TAG) {
                final String tag = in.getName();

                final String name = in.getAttributeValue(null, ATTR_NAME);
                String path = in.getAttributeValue(null, ATTR_PATH);

                File target = null;
                if (TAG_ROOT_PATH.equals(tag)) {
                    target = buildPath(DEVICE_ROOT, path);
                } else if (TAG_FILES_PATH.equals(tag)) {
                    target = buildPath(context.getFilesDir(), path);
                } else if (TAG_CACHE_PATH.equals(tag)) {
                    target = buildPath(context.getCacheDir(), path);
                } else if (TAG_EXTERNAL.equals(tag)) {
                    target = buildPath(Environment.getExternalStorageDirectory(), path);
                }

                if (target != null) {
                    strat.addRoot(name, target);
                }
            }
        }

        return strat;
    }

    interface PathStrategy {
        /**
         * Return a {@link Uri} that represents the given {@link File}.
         */
        public Uri getUriForFile(File file);

        /**
         * Return a {@link File} that represents the given {@link Uri}.
         */
        public File getFileForUri(Uri uri);
    }

    static class SimplePathStrategy implements PathStrategy {
        private final String mAuthority;
        private final HashMap<String, File> mRoots = new HashMap<String, File>();

        public SimplePathStrategy(String authority) {
            mAuthority = authority;
        }

        public void addRoot(String name, File root) {
            if (TextUtils.isEmpty(name)) {
                throw new IllegalArgumentException("Name must not be empty");
            }

            try {
                // Resolve to canonical path to keep path checking fast
                root = root.getCanonicalFile();
            } catch (IOException e) {
                throw new IllegalArgumentException(
                        "Failed to resolve canonical path for " + root, e);
            }

            mRoots.put(name, root);
        }

        @Override
        public Uri getUriForFile(File file) {
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + file);
            }

            // Find the most-specific root path
            Map.Entry<String, File> mostSpecific = null;
            for (Map.Entry<String, File> root : mRoots.entrySet()) {
                final String rootPath = root.getValue().getPath();
                Log.i(TAG, "getUriForFile looking at root " + rootPath );
                if (path.startsWith(rootPath) && (mostSpecific == null
                        || rootPath.length() > mostSpecific.getValue().getPath().length())) {
                    mostSpecific = root;
                }
            }

            if (mostSpecific == null) {
                throw new IllegalArgumentException(
                        "Failed to find configured root that contains " + path);
            }

            // Start at first char of path under root
            final String rootPath = mostSpecific.getValue().getPath();
            if (rootPath.endsWith("/")) {
                path = path.substring(rootPath.length());
            } else {
                path = path.substring(rootPath.length() + 1);
            }

            // Encode the tag and path separately
            path = Uri.encode(mostSpecific.getKey()) + '/' + Uri.encode(path, "/");
            return new Uri.Builder().scheme("content")
                    .authority(mAuthority).encodedPath(path).build();
        }

        @Override
        public File getFileForUri(Uri uri) {
            String path = uri.getEncodedPath();

            final int splitIndex = path.indexOf('/', 1);
            final String tag = Uri.decode(path.substring(1, splitIndex));
            path = Uri.decode(path.substring(splitIndex + 1));

            final File root = mRoots.get(tag);
            if (root == null) {
                throw new IllegalArgumentException("Unable to find configured root for " + uri);
            }

            File file = new File(root, path);
            try {
                file = file.getCanonicalFile();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve canonical path for " + file);
            }

            if (!file.getPath().startsWith(root.getPath())) {
                throw new SecurityException("Resolved path jumped beyond configured root");
            }

            return file;
        }
    }

    /**
     * Copied from ContentResolver.java
     */
    private static int modeToMode(String mode) {
        int modeBits;
        if ("r".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_ONLY;
        } else if ("w".equals(mode) || "wt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else if ("wa".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_APPEND;
        } else if ("rw".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE;
        } else if ("rwt".equals(mode)) {
            modeBits = ParcelFileDescriptor.MODE_READ_WRITE
                    | ParcelFileDescriptor.MODE_CREATE
                    | ParcelFileDescriptor.MODE_TRUNCATE;
        } else {
            throw new IllegalArgumentException("Invalid mode: " + mode);
        }
        return modeBits;
    }

    private static File buildPath(File base, String... segments) {
        File cur = base;
        for (String segment : segments) {
            if (segment != null) {
                cur = new File(cur, segment);
            }
        }
        return cur;
    }

    private static String[] copyOf(String[] original, int newLength) {
        final String[] result = new String[newLength];
        System.arraycopy(original, 0, result, 0, newLength);
        return result;
    }

    private static Object[] copyOf(Object[] original, int newLength) {
        final Object[] result = new Object[newLength];
        System.arraycopy(original, 0, result, 0, newLength);
        return result;
    }
}