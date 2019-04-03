package test.android.osdownloads;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import tools.android.osdownloads.Constants;
import tools.android.osdownloads.DownLoadManager;


public class MainActivity extends AppCompatActivity {

    // debug version，just for test，will not install
    String url = "https://gist.github.com/liuchonghui/b9757b65748eb42548213ec7b9572116/raw/b78becf8667e7fe0382d22ea89e17a8efc447ed3/1.6_25.pptv.db9357c67b82f2da0afc1e540549296c.zip";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DownLoadManager.download(getApplicationContext(),
                        "cp.pptv.plugin", url, "1.6_25.pptv",
                        "pptv-plugin", "pptv-plugin-desc",
                        true, Constants.MIMETYPE_APK);
            }
        });
    }
}
