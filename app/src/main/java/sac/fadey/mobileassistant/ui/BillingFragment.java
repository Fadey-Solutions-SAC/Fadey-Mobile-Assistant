package sac.fadey.mobileassistant.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;

public class BillingFragment extends Fragment {
    private ActivityResultLauncher<Intent> picker;
    private EditText editPassword;
    private TextView status;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            Uri uri = result.getData().getData();
            if (uri == null) return;
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IllegalStateException("No se pudo leer el archivo");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
                String pwd = editPassword.getText().toString();
                FadeyApp.get().secureStore().setPfx(b64, pwd);
                refresh();
                Toast.makeText(requireContext(), "Certificado guardado cifrado", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_billing, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        editPassword = view.findViewById(R.id.editPfxPassword);
        status = view.findViewById(R.id.txtCertStatus);
        view.findViewById(R.id.btnImportPfx).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            picker.launch(i);
        });
        view.findViewById(R.id.btnClearPfx).setOnClickListener(v -> {
            FadeyApp.get().secureStore().clearPfx();
            refresh();
        });
        refresh();
    }

    private void refresh() {
        boolean has = !FadeyApp.get().secureStore().getPfxBase64().isEmpty();
        status.setText(has
                ? "Reserva local: .pfx cargado en el teléfono"
                : "Modo normal: certificado del sistema (servidor) · sin .pfx local");
    }
}
