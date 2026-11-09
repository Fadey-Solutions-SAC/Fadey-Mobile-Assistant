package sac.fadey.mobileassistant.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import sac.fadey.mobileassistant.FadeyApp;
import sac.fadey.mobileassistant.R;
import sac.fadey.mobileassistant.data.JobEntity;

public class HistoryFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView txt = view.findViewById(R.id.txtHistory);
        Runnable load = () -> new Thread(() -> {
            List<JobEntity> jobs = FadeyApp.get().db().jobDao().recent();
            StringBuilder sb = new StringBuilder();
            DateFormat df = DateFormat.getDateTimeInstance();
            for (JobEntity j : jobs) {
                sb.append(df.format(new Date(j.updatedAt)))
                        .append(" · ").append(j.type)
                        .append(" · ").append(j.module)
                        .append(" · ").append(j.status)
                        .append(" · ").append(j.id)
                        .append("\n");
            }
            if (sb.length() == 0) sb.append("Sin trabajos aún.");
            String text = sb.toString();
            requireActivity().runOnUiThread(() -> txt.setText(text));
        }).start();
        view.findViewById(R.id.btnRefreshHistory).setOnClickListener(v -> load.run());
        load.run();
    }
}
