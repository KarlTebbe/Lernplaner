package com.example.lernplaner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class DailyPlanActivity extends AppCompatActivity {

    // UI-Elemente für die Anzeige und Interaktion
    private TextView tvProjectTitle, tvWorkloadMain, tvDailyRecommendation;
    private EditText progressInput;
    private Button saveProgressButton;
    private Spinner spinnerProjects;
    private ImageButton btnDeleteProject, btnEditProject;

    // Datenbank-Instanz und Listen zur Datenverwaltung
    private FirebaseFirestore db;
    private List<DocumentSnapshot> projectDocs = new ArrayList<>();
    private DocumentSnapshot selectedGoalDoc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_plan);

        // Initialisierung der Datenbank und Verknüpfung der UI-Elemente
        db = FirebaseFirestore.getInstance();
        tvProjectTitle = findViewById(R.id.tvDailyPlanTitle);
        tvWorkloadMain = findViewById(R.id.tvWorkloadMain);
        tvDailyRecommendation = findViewById(R.id.tvDailyRecommendation);
        progressInput = findViewById(R.id.etProgress);
        saveProgressButton = findViewById(R.id.btnSaveProgress);
        spinnerProjects = findViewById(R.id.spinnerProjects);
        btnDeleteProject = findViewById(R.id.btnDeleteCurrentProject);
        btnEditProject = findViewById(R.id.btnEditCurrentProject);

        // Basis-Setup: Projekte laden und Navigation vorbereiten
        loadAllProjects();
        setupNavigationBar();

        // Event-Listener für Buttons
        saveProgressButton.setOnClickListener(v -> saveProgress());
        btnDeleteProject.setOnClickListener(v -> confirmDelete());
        btnEditProject.setOnClickListener(v -> editProject());

        // Spinner-Logik: Berechnet die Werte neu, wenn ein anderes Projekt ausgewählt wird
        spinnerProjects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < projectDocs.size()) {
                    selectedGoalDoc = projectDocs.get(position);
                    calculateWorkload();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Lädt alle Lernziele des aktuell angemeldeten Nutzers aus Firestore.
     * Filtert nach der userId und sortiert die Projekte nach Erstellungsdatum.
     */
    private void loadAllProjects() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("goals")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    projectDocs.clear();
                    List<String> projectNames = new ArrayList<>();
                    
                    if (queryDocumentSnapshots.isEmpty()) {
                        updateUIForNoProjects();
                        return;
                    }

                    // Dokumente sortieren: Neueste zuerst
                    List<DocumentSnapshot> docs = new ArrayList<>(queryDocumentSnapshots.getDocuments());
                    docs.sort((d1, d2) -> {
                        Timestamp t1 = d1.getTimestamp("createdAt");
                        Timestamp t2 = d2.getTimestamp("createdAt");
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });

                    // Listen für den Spinner befüllen
                    for (DocumentSnapshot doc : docs) {
                        projectDocs.add(doc);
                        projectNames.add(doc.getString("projectName"));
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                            android.R.layout.simple_spinner_item, projectNames);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerProjects.setAdapter(adapter);

                    if (!projectDocs.isEmpty()) {
                        selectedGoalDoc = projectDocs.get(0);
                        calculateWorkload();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, getString(R.string.msg_error_loading), Toast.LENGTH_SHORT).show());
    }

    /**
     * Kern-Logik: Berechnet die verbleibende Menge und die tägliche Empfehlung
     * basierend auf dem aktuellen Fortschritt und der verbleibenden Zeit bis zur Deadline.
     */
    private void calculateWorkload() {
        if (selectedGoalDoc == null) return;
        try {
            String name = selectedGoalDoc.getString("projectName");
            long total = selectedGoalDoc.getLong("totalAmount");
            long current = selectedGoalDoc.getLong("currentAmount");
            Timestamp deadlineTs = selectedGoalDoc.getTimestamp("deadline");
            String unit = selectedGoalDoc.getString("unit");

            tvProjectTitle.setText(name);
            
            // Zeitdifferenz berechnen
            Date deadline = deadlineTs.toDate();
            long diff = deadline.getTime() - new Date().getTime();
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            if (days <= 0) days = 1; // Sicherstellung: Mindestens 1 Tag Teiler

            long remaining = total - current;
            if (remaining <= 0) {
                tvWorkloadMain.setText(getString(R.string.congrats_message));
                tvDailyRecommendation.setText(getString(R.string.msg_well_done));
                return;
            }

            // Durchschnittliche tägliche Menge berechnen
            double daily = (double) remaining / days;
            
            tvWorkloadMain.setText(getString(R.string.status_remaining_format, remaining, unit, days));
            tvDailyRecommendation.setText(getString(R.string.recommendation_format, daily, unit));
                
        } catch (Exception e) {
            tvWorkloadMain.setText(getString(R.string.msg_calculation_error));
        }
    }

    /**
     * Speichert den neu eingegebenen Fortschritt in Firestore.
     * Prüft danach, ob das Ziel erreicht wurde und wechselt ggf. zum Erfolgs-Bildschirm.
     */
    private void saveProgress() {
        String val = progressInput.getText().toString();
        if (val.isEmpty() || selectedGoalDoc == null) return;
        try {
            int added = Integer.parseInt(val);
            long totalAmount = selectedGoalDoc.getLong("totalAmount");
            long newTotal = selectedGoalDoc.getLong("currentAmount") + added;
            
            // Update in der Cloud
            db.collection("goals").document(selectedGoalDoc.getId()).update("currentAmount", newTotal)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.msg_progress_saved), Toast.LENGTH_SHORT).show();
                    progressInput.setText("");
                    
                    if (newTotal >= totalAmount) {
                        // Ziel erreicht: Projekt löschen und Gratulation anzeigen
                        handleGoalReached();
                    } else {
                        loadAllProjects();
                    }
                });
        } catch (Exception e) {
            Log.e("DailyPlan", "Save error", e);
        }
    }

    private void handleGoalReached() {
        db.collection("goals").document(selectedGoalDoc.getId()).delete()
            .addOnCompleteListener(task -> {
                startActivity(new Intent(DailyPlanActivity.this, CongratulationsActivity.class));
                finish();
            });
    }

    private void updateUIForNoProjects() {
        tvWorkloadMain.setText(getString(R.string.msg_no_active_goal));
        tvDailyRecommendation.setText("--");
        tvProjectTitle.setText(getString(R.string.msg_no_project));
        spinnerProjects.setAdapter(null);
        selectedGoalDoc = null;
    }

    /**
     * Zeigt einen Bestätigungsdialog vor dem Löschen eines Projekts an.
     */
    private void confirmDelete() {
        if (selectedGoalDoc == null) return;
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.msg_confirm_delete_title))
                .setMessage(getString(R.string.msg_confirm_delete_text, selectedGoalDoc.getString("projectName")))
                .setPositiveButton(getString(R.string.btn_delete), (d, w) -> {
                    db.collection("goals").document(selectedGoalDoc.getId()).delete()
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(this, getString(R.string.msg_deleted), Toast.LENGTH_SHORT).show();
                            loadAllProjects();
                        });
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    /**
     * Startet den Edit-Modus: Übergibt die Projektdaten per Intent an die GoalInputActivity.
     */
    private void editProject() {
        if (selectedGoalDoc == null) return;
        Intent intent = new Intent(this, GoalInputActivity.class);
        intent.putExtra("goalId", selectedGoalDoc.getId());
        intent.putExtra("projectName", selectedGoalDoc.getString("projectName"));
        intent.putExtra("totalAmount", selectedGoalDoc.getLong("totalAmount"));
        intent.putExtra("unit", selectedGoalDoc.getString("unit"));
        
        Timestamp deadlineTs = selectedGoalDoc.getTimestamp("deadline");
        if (deadlineTs != null) {
            intent.putExtra("deadlineMillis", deadlineTs.toDate().getTime());
        }
        
        startActivity(intent);
    }

    /**
     * Initialisiert die Click-Listener für die untere Navigationsleiste.
     */
    private void setupNavigationBar() {
        findViewById(R.id.btnNavGoal).setOnClickListener(v -> startActivity(new Intent(this, GoalInputActivity.class)));
        findViewById(R.id.btnNavEval).setOnClickListener(v -> startActivity(new Intent(this, EvaluationActivity.class)));
        findViewById(R.id.btnNavSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }
}
