package com.example.lernplaner;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Diese Activity ist für die statistische Auswertung der Lernziele zuständig.
 * Sie zeigt detaillierte Berechnungen wie Fortschritt, Tempo und Prognosen an.
 */
public class EvaluationActivity extends AppCompatActivity {

    private TextView tvStats, tvProjectNameFocus;
    private ProgressBar pbLargeProgress; // Statischer Balken aus der XML
    private LinearLayout projectListContainer; // Container für die dynamische Liste
    private Spinner spinnerProjects;
    private FirebaseFirestore db;
    private List<DocumentSnapshot> allProjectDocs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_evaluation);

        // Initialisierung
        db = FirebaseFirestore.getInstance();
        tvStats = findViewById(R.id.tvStats);
        tvProjectNameFocus = findViewById(R.id.tvProjectNameFocus);
        pbLargeProgress = findViewById(R.id.pbLargeProgress);
        projectListContainer = findViewById(R.id.projectListContainer);
        spinnerProjects = findViewById(R.id.spinnerEvalProjects);

        loadAllData();
        setupNavigationBar();

        // Wenn ein Projekt im Spinner gewählt wird, zeige die detaillierten Stats oben an
        spinnerProjects.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < allProjectDocs.size()) {
                    displayAdvancedStats(allProjectDocs.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * Lädt alle Projektdaten des Nutzers.
     * Befüllt sowohl den Spinner (für die Detailansicht) als auch die dynamische Liste (unten).
     */
    private void loadAllData() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("goals")
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allProjectDocs.clear();
                    projectListContainer.removeAllViews(); // Liste leeren, bevor sie neu befüllt wird
                    List<String> names = new ArrayList<>();
                    
                    List<DocumentSnapshot> docs = queryDocumentSnapshots.getDocuments();
                    if (docs.isEmpty()) {
                        showEmptyState();
                        return;
                    }

                    // Sortierung nach Datum
                    docs.sort((d1, d2) -> {
                        Timestamp t1 = d1.getTimestamp("createdAt");
                        Timestamp t2 = d2.getTimestamp("createdAt");
                        if (t1 == null || t2 == null) return 0;
                        return t2.compareTo(t1);
                    });

                    allProjectDocs.addAll(docs);

                    // Jedes Projekt einzeln zur Liste hinzufügen und Namen für Spinner sammeln
                    for (DocumentSnapshot doc : docs) {
                        names.add(doc.getString("projectName"));
                        addProjectToList(doc);
                    }

                    // Spinner-Adapter setzen
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                            android.R.layout.simple_spinner_item, names);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinnerProjects.setAdapter(adapter);

                    if (!allProjectDocs.isEmpty()) {
                        displayAdvancedStats(allProjectDocs.get(0));
                    }
                })
                .addOnFailureListener(e -> Log.e("EvalActivity", "Ladefehler", e));
    }

    /**
     * Berechnet die fortgeschrittenen Statistiken für das ausgewählte Projekt.
     * Beinhaltet Fortschritt in %, durchschnittliches Tempo und eine Zeit-Prognose.
     */
    private void displayAdvancedStats(DocumentSnapshot doc) {
        try {
            String name = doc.getString("projectName");
            long total = doc.getLong("totalAmount");
            long current = doc.getLong("currentAmount");
            Timestamp createdTs = doc.getTimestamp("createdAt");
            String unit = doc.getString("unit");

            tvProjectNameFocus.setText(name);
            
            // Prozentberechnung für den statischen Balken oben
            double progressPercent = (total > 0) ? ((double) current / total) * 100 : 0;
            pbLargeProgress.setProgress((int) progressPercent);

            // Zeit-Logik: Wie viele Tage ist das Projekt schon alt?
            Date startDate = (createdTs != null) ? createdTs.toDate() : new Date();
            long diffInMs = new Date().getTime() - startDate.getTime();
            long daysPassed = TimeUnit.MILLISECONDS.toDays(diffInMs);
            if (daysPassed <= 0) daysPassed = 1;

            // Tempo-Berechnung (Durchschnitt pro Tag)
            double avgPace = (double) current / daysPassed;
            long remaining = total - current;

            // String-Zusammenbau für das Statistik-Textfeld
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.label_progress)).append(" ").append(String.format(Locale.GERMAN, "%.1f", progressPercent)).append("%\n");
            sb.append(getString(R.string.label_stand)).append(" ").append(current).append(" / ").append(total).append(" ").append(unit).append("\n\n");
            
            // Prognose-Berechnung: Wie viele Tage dauert es bei diesem Tempo noch?
            if (avgPace > 0 && remaining > 0) {
                long daysNeeded = (long) Math.ceil(remaining / avgPace);
                sb.append(getString(R.string.label_forecast)).append("\n").append(getString(R.string.format_finish_in, daysNeeded));
            } else if (remaining <= 0) {
                sb.append(getString(R.string.msg_goal_reached_upper));
            }

            tvStats.setText(sb.toString());
        } catch (Exception e) {
            tvStats.setText(getString(R.string.msg_error_stats));
        }
    }

    /**
     * DYNAMISCHE UI-ERZEUGUNG: Erstellt für jedes Projekt eine Karte in der Liste.
     * Hier wird die ProgressBar per Code erzeugt, da die Anzahl der Projekte variabel ist.
     */
    private void addProjectToList(DocumentSnapshot doc) {
        String name = doc.getString("projectName");
        long current = doc.getLong("currentAmount");
        long total = doc.getLong("totalAmount");
        String unit = doc.getString("unit");
        double progress = (total > 0) ? ((double)current/total * 100) : 0;

        // Container-Karte (CardView) erstellen
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(24f);
        card.setCardElevation(4f);

        // Layout für den Inhalt der Karte
        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setPadding(32, 24, 32, 24);

        // Projekttitel
        TextView tvTitle = new TextView(this);
        tvTitle.setText(name);
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(Color.BLACK);

        // DYNAMISCHE ProgressBar erzeugen
        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20));
        pb.setProgressDrawable(ContextCompat.getDrawable(this, R.drawable.progress_rounded));
        pb.setProgress((int) progress);

        // Text-Details (z.B. "50 / 100 Seiten")
        TextView tvDetail = new TextView(this);
        tvDetail.setText(getString(R.string.format_list_detail, current, total, unit, progress));
        tvDetail.setTextSize(14);
        tvDetail.setPadding(0, 8, 0, 0);

        // Elemente zusammenfügen
        textLayout.addView(tvTitle);
        textLayout.addView(pb);
        textLayout.addView(tvDetail);
        card.addView(textLayout);

        // Interaktion: Klick auf Karte wählt Projekt im Spinner aus
        card.setOnClickListener(v -> {
            int index = allProjectDocs.indexOf(doc);
            if (index != -1) spinnerProjects.setSelection(index);
        });

        projectListContainer.addView(card);
    }

    private void showEmptyState() {
        tvStats.setText(getString(R.string.msg_no_active_goals));
        tvProjectNameFocus.setText(getString(R.string.msg_no_goals));
        pbLargeProgress.setProgress(0);
        spinnerProjects.setAdapter(null);
    }

    private void setupNavigationBar() {
        findViewById(R.id.btnNavDaily).setOnClickListener(v -> {
            startActivity(new Intent(this, DailyPlanActivity.class));
            finish();
        });
        findViewById(R.id.btnNavGoal).setOnClickListener(v -> {
            startActivity(new Intent(this, GoalInputActivity.class));
            finish();
        });
        findViewById(R.id.btnNavSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
    }
}
