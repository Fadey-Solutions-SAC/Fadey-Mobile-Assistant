package sac.fadey.mobileassistant.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import sac.fadey.mobileassistant.BuildConfig;
import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;
import sac.fadey.mobileassistant.http.LocalHttpServer;

public class DiagFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_diag, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        View v = getView();
        if (v == null) return;
        TextView txt = v.findViewById(R.id.txtDiag);
        try {
            JSONObject health = new JSONObject();
            health.put("status", "ok");
            health.put("mode", "assistant");
            health.put("port", BuildConfig.DEFAULT_HTTP_PORT);
            health.put("lanIp", LocalHttpServer.detectLanIp());
            health.put("service", BuildConfig.ASSISTANT_SERVICE_NAME);
            health.put("linked", FadeyApp.get().secureStore().isLinked());
            health.put("apiBase", FadeyApp.get().secureStore().getApiBase());
            health.put("hasPfx", !FadeyApp.get().secureStore().getPfxBase64().isEmpty());
            health.put("lastError", FadeyApp.get().secureStore().getLastError());
            health.put("printerConfig", FadeyApp.get().printerConfigStore().load());
            txt.setText(health.toString(2));
        } catch (Exception e) {
            txt.setText(e.getMessage());
        }
    }
}
