package sac.fadey.mobileassistant.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

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
        MaterialButton btnDiag = view.findViewById(R.id.btnDiag);

        Runnable refresh = () -> {
            String ip = LocalHttpServer.detectLanIp();
            int port = BuildConfig.DEFAULT_HTTP_PORT;
            boolean linked = FadeyApp.get() != null
                    && FadeyApp.get().secureStore() != null
                    && FadeyApp.get().secureStore().isLinked();
            boolean operating = linked && FadeyApp.get().secureStore().getLastOperationOperating();
            String opDetail = linked ? FadeyApp.get().secureStore().getLastOperationDetail() : "";
            if (operating) {
                txtStatus.setText("Sistema en operación · asistente activo");
            } else if (linked) {
                txtStatus.setText("Esperando sistema (abra caja en el POS)");
            } else {
                txtStatus.setText("Servicio listo · falta vincular");
            }
            String lan = "http://" + (ip.isEmpty() ? "IP_WIFI" : ip) + ":" + port;
            txtEndpoint.setText("URL para POS en misma Wi‑Fi (PC):\n" + lan
                    + "\n\nEl servidor avisa si hay caja abierta; el APK se reactiva solo (~1–2 min)."
                    + (opDetail.isEmpty() ? "" : ("\nEstado: " + opDetail))
                    + "\nHealth: " + lan + "/health");
            txtLink.setText(linked
                    ? "Nube vinculada: " + FadeyApp.get().secureStore().getApiBase()
                    : "Sin vínculo nube → Configuración → pegar API y token JWT");
        };
        refresh.run();
        btnStart.setOnClickListener(v -> {
            if (FadeyApp.get() != null && FadeyApp.get().secureStore() != null) {
                FadeyApp.get().secureStore().setServiceEnabled(true);
                FadeyApp.get().secureStore().setFollowSystemOperation(false);
            }
            AssistantForegroundService.start(requireContext());
            refresh.run();
        });
        btnStop.setOnClickListener(v -> {
            AssistantForegroundService.stop(requireContext());
            if (FadeyApp.get() != null && FadeyApp.get().secureStore() != null) {
                FadeyApp.get().secureStore().setServiceEnabled(false);
                FadeyApp.get().secureStore().setFollowSystemOperation(true);
            }
            txtStatus.setText("Servicio detenido · seguirá el estado del sistema");
        });
        btnDiag.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.diagFragment));
    }

    @Override
    public void onResume() {
        super.onResume();
        new Thread(() -> {
            try {
                sac.fadey.mobileassistant.cloud.OperationSync.sync(requireContext());
            } catch (Throwable ignored) {}
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    View root = getView();
                    if (root != null) {
                        TextView txtStatus = root.findViewById(R.id.txtStatus);
                        TextView txtEndpoint = root.findViewById(R.id.txtEndpoint);
                        TextView txtLink = root.findViewById(R.id.txtLink);
                        if (txtStatus == null) return;
                        boolean linked = FadeyApp.get() != null
                                && FadeyApp.get().secureStore() != null
                                && FadeyApp.get().secureStore().isLinked();
                        boolean operating = linked && FadeyApp.get().secureStore().getLastOperationOperating();
                        txtStatus.setText(operating
                                ? "Sistema en operación · asistente activo"
                                : (linked ? "Esperando sistema (abra caja en el POS)" : "Servicio listo · falta vincular"));
                        if (txtLink != null && linked) {
                            txtLink.setText("Nube vinculada: " + FadeyApp.get().secureStore().getApiBase());
                        }
                        if (txtEndpoint != null && linked) {
                            String d = FadeyApp.get().secureStore().getLastOperationDetail();
                            if (!d.isEmpty()) {
                                txtEndpoint.append("\nEstado: " + d);
                            }
                        }
                    }
                });
            }
        }, "home-op-sync").start();
    }
}

