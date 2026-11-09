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

import org.json.JSONObject;

import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;
import sac.fadey.mobileassistant.print.PrintDispatcher;

public class PrintersFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_printers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        EditText editTipo = view.findViewById(R.id.editTipo);
        EditText editAddress = view.findViewById(R.id.editAddress);
        EditText editPort = view.findViewById(R.id.editPort);
        EditText editWidth = view.findViewById(R.id.editWidth);
        TextView msg = view.findViewById(R.id.txtPrinterMsg);

        JSONObject cfg = FadeyApp.get().printerConfigStore().getModule("caja");
        if (cfg != null) {
            editTipo.setText(cfg.optString("tipo", "bluetooth"));
            editAddress.setText(cfg.optString("address", ""));
            editPort.setText(String.valueOf(cfg.optInt("port", 9100)));
            editWidth.setText(String.valueOf(cfg.optInt("paperWidth", 80)));
        }

        view.findViewById(R.id.btnSavePrinter).setOnClickListener(v -> {
            try {
                JSONObject m = new JSONObject();
                m.put("tipo", editTipo.getText().toString().trim());
                m.put("nombre", "caja");
                m.put("address", editAddress.getText().toString().trim());
                m.put("port", Integer.parseInt(editPort.getText().toString().trim().isEmpty()
                        ? "9100" : editPort.getText().toString().trim()));
                m.put("paperWidth", Integer.parseInt(editWidth.getText().toString().trim().isEmpty()
                        ? "80" : editWidth.getText().toString().trim()));
                m.put("enabled", true);
                FadeyApp.get().printerConfigStore().putModule("caja", m);
                msg.setText("Guardado");
                Toast.makeText(requireContext(), "Config caja guardada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                msg.setText(e.getMessage());
            }
        });

        view.findViewById(R.id.btnTestPrinter).setOnClickListener(v -> new Thread(() -> {
            try {
                PrintDispatcher.printTest(requireContext(), "caja");
                requireActivity().runOnUiThread(() -> {
                    msg.setText("Prueba enviada");
                    Toast.makeText(requireContext(), "OK", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> msg.setText(e.getMessage()));
            }
        }).start());
    }
}
