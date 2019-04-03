package tools.android.osdownloads;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;

import java.io.File;

public class DownloadInstallerActivity extends AppCompatActivity {

    private static final String KEY_FILE = ":file";
    private static final int GET_UNKNOWN_APP_SOURCES = 10000;

    private String file;

    public static Intent getIntent(Context context,String path){
        Intent intent = new Intent(context, DownloadInstallerActivity.class);
        intent.putExtra(KEY_FILE,path);
        if(!(context instanceof Activity)){
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if(null == intent){
            finish();
            return;
        }

        file = intent.getStringExtra(KEY_FILE);
        if(TextUtils.isEmpty(file)){
            finish();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            installMoreN();
        } else {
            Intent launchIntent = new Intent(Intent.ACTION_VIEW);
            Uri path = Uri.parse(file);
            // If there is no scheme, then it must be a file
            if (path.getScheme() == null) {
                path = Uri.fromFile(new File(file));
            }
            launchIntent.setDataAndType(path, Constants.MIMETYPE_APK);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            try {
                startActivity(launchIntent);
            } catch (ActivityNotFoundException ex) {
                ex.printStackTrace();
            }

            finish();
        }
    }

    private void installMoreN() {
        try {
            File apkFile = new File(file);
            Uri apkUri = DownloadFileProvider.getUriForFile(getApplicationContext(), Constants.AUTHORITY, apkFile);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean isGranted = getPackageManager().canRequestPackageInstalls();
                if (!isGranted) {
                    startInstallPermissionSettingActivity();
                    return;
                }
            }
            startActivity(intent);
            finish();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startInstallPermissionSettingActivity() {
        Uri uri = Uri.parse("package:" + getPackageName());
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,uri);
        startActivityForResult(intent,GET_UNKNOWN_APP_SOURCES);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == GET_UNKNOWN_APP_SOURCES && resultCode == Activity.RESULT_OK){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                boolean isGranted = getPackageManager().canRequestPackageInstalls();
                if(!isGranted){
                    finish();
                }else {
                    installMoreN();
                }
            }else {
                finish();
            }
        }else {
            finish();
        }
    }
}
