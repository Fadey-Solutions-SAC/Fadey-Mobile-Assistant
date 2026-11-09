package sac.fadey.mobileassistant.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import sac.fadey.mobileassistant.BuildConfig;
import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;
import sac.fadey.mobileassistant.http.LocalHttpServer;
import sac.fadey.mobileassistant.service.AssistantForegroundService;

public class HomeFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView txtStatus = view.findViewById(R.id.txtStatus);
        TextView txtEndpoint = view.findViewById(R.id.txtEndpoint);
        TextView txtLink = view.findViewById(R.id.txtLink);
        MaterialButton btnStart = view.findViewById(R.id.btnStart);
        MaterialButton btnStop = view.findViewById(R.id.btnStop);

        Runnable refresh = () -> {
            String ip = LocalHttpServer.detectLanIp();
            int port = BuildConfig.DEFAULT_HTTP_PORT;
            txtStatus.setText("Servicio en primer plano listo");
            txtEndpoint.setText("LAN: http://" + (ip.isEmpty() ? "IP" : ip) + ":" + port
                    + "\nHealth: /health · service=" + BuildConfig.ASSISTANT_SERVICE_NAME);
            boolean linked = FadeyApp.get().secureStore().isLinked();
            txtLink.setText(linked
                    ? "Nube vinculada: " + FadeyApp.get().secureStore().getApiBase()
                    : "Sin vínculo nube (configure en Ajustes o POST /api/efact/cloud-link)");
        };
        refresh.run();
        btnStart.setOnClickListener(v -> {
            AssistantForegroundService.start(requireContext());
            refresh.run();
        });
        btnStop.setOnClickListener(v -> {
            AssistantForegroundService.stop(requireContext());
            FadeyApp.get().secureStore().setServiceEnabled(false);
            txtStatus.setText("Servicio detenido");
        });
    }
}
