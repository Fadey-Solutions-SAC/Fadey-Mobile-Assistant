package sac.fadey.mobileassistant.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

/** Abre el instalador del sistema. Android exige esta confirmación. */
public class InstallUpdateActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File apk = AppUpdater.apkFile(this);
        if (!apk.exists()) {
            Toast.makeText(this, "No hay actualización descargada", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            Intent allow = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            allow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(allow);
            Toast.makeText(this, "Permite instalar apps y vuelve a tocar Actualizar", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
        Intent view = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        view.setData(uri);
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        view.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        view.putExtra(Intent.EXTRA_RETURN_RESULT, false);
        try {
            startActivity(view);
        } catch (Throwable t) {
            Intent fallback = new Intent(Intent.ACTION_VIEW);
            fallback.setDataAndType(uri, "application/vnd.android.package-archive");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(fallback);
        }
        finish();
    }
}
