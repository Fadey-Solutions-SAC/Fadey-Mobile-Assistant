package sac.fadey.mobileassistant.ui;

import android.os.Bundle;
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
import sac.fadey.mobileassistant.data.SecureStore;

public class SettingsFragment extends Fragment {
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

        view.findViewById(R.id.btnSaveLink).setOnClickListener(v -> {
            store.setApiBase(api.getText().toString());
            store.setToken(token.getText().toString());
            msg.setText(store.isLinked() ? "Vínculo guardado" : "Faltan apiBase o token");
            Toast.makeText(requireContext(), "Guardado", Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.btnUnlink).setOnClickListener(v -> {
            store.unlink();
            api.setText("");
            token.setText("");
            msg.setText("Desvinculado");
        });
    }
}
