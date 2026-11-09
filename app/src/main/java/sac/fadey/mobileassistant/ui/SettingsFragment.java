package sac.fadey.mobileassistant.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;
import sac.fadey.mobileassistant.cloud.ApiLinkHelper;
import sac.fadey.mobileassistant.data.SecureStore;

public class SettingsFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText api = view.findViewById(R.id.editApiBase);
        EditText token = view.findViewById(R.id.editToken);
        TextView msg = view.findViewById(R.id.txtSettingsMsg);
        SecureStore store = FadeyApp.get().secureStore();
        api.setText(store.getApiBase());
        token.setText(store.getToken());
        if (store.isLinked()) {
            msg.setText("Vínculo guardado · " + store.getApiBase());
        }

        view.findViewById(R.id.btnSaveLink).setOnClickListener(v -> {
            String rawApi = api.getText().toString();
            String rawTok = token.getText().toString();
            String normApi = ApiLinkHelper.normalizeApiBase(rawApi);
            String normTok = ApiLinkHelper.normalizeToken(rawTok);
            msg.setText("Probando vínculo con el servidor…");
            view.findViewById(R.id.btnSaveLink).setEnabled(false);
            new Thread(() -> {
                ApiLinkHelper.ProbeResult probe = ApiLinkHelper.probe(normApi, normTok);
                main.post(() -> {
                    view.findViewById(R.id.btnSaveLink).setEnabled(true);
                    if (!probe.ok) {
                        msg.setText(probe.message);
                        Toast.makeText(requireContext(), "No se pudo vincular", Toast.LENGTH_LONG).show();
                        return;
                    }
                    store.setApiBase(normApi);
                    store.setToken(normTok);
                    store.setFollowSystemOperation(true);
                    api.setText(normApi);
                    msg.setText(probe.message);
                    Toast.makeText(requireContext(), "Sistema vinculado", Toast.LENGTH_SHORT).show();
                    sac.fadey.mobileassistant.service.OperationWatchScheduler.ensureScheduled(requireContext());
                    new Thread(() -> {
                        sac.fadey.mobileassistant.cloud.OperationSync.Result op =
                                sac.fadey.mobileassistant.cloud.OperationSync.sync(requireContext());
                        main.post(() -> {
                            if (op.ok) {
                                msg.setText(probe.message + "\n" + op.detail);
                            }
                        });
                    }, "link-op-sync").start();
                });
            }, "fadey-link-probe").start();
        });
        view.findViewById(R.id.btnUnlink).setOnClickListener(v -> {
            store.unlink();
            sac.fadey.mobileassistant.service.OperationWatchScheduler.cancel(requireContext());
            sac.fadey.mobileassistant.service.AssistantForegroundService.stop(requireContext());
            api.setText("");
            token.setText("");
            msg.setText("Desvinculado");
        });
    }
}
