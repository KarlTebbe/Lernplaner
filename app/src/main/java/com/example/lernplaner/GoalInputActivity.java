package com.example.lernplaner;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Diese Activity ermöglicht die Eingabe neuer Lernziele sowie die Bearbeitung
 * bestehender Projekte. Sie nutzt einen DatePicker für die Deadline-Auswahl.
 */
public class GoalInputActivity extends AppCompatActivity {

    private EditText etProjectName, etDeadline, etTotalAmount;
    private Spinner spinnerUnit;
    private Button btnSaveGoal;
    private Calendar calendar = Calendar.getInstance();
    private FirebaseFirestore db;
    private String[] units = {"Seiten", "Aufgaben", "Folien", "Kapitel", "Stunden"};
    private String goalId = null; // Bleibt null bei neuem Projekt, wird befüllt im Edit-Modus

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_input);

        db = FirebaseFirestore.getInstance();

        // UI-Elemente verknüpfen
        etProjectName = findViewById(R.id.etProjectName);
        etDeadline = findViewById(R.id.etDeadline);
        etTotalAmount = findViewById(R.id.etTotalAmount);
        spinnerUnit = findViewById(R.id.spinnerUnit);
        btnSaveGoal = findViewById(R.id.btnSaveGoal);

        // Spinner mit Einheiten befüllen
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, units);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnit.setAdapter(adapter);

        // ÜBERPRÜFUNG: Edit-Modus oder Neu-Modus?
        // Wenn ein Intent mit einer "goalId" ankommt, werden die Felder vorbefüllt.
        if (getIntent().hasExtra("goalId")) {
            goalId = getIntent().getStringExtra("goalId");
            etProjectName.setText(getIntent().getStringExtra("projectName"));
            etTotalAmount.setText(String.valueOf(getIntent().getLongExtra("totalAmount", 0)));
            
            // Die richtige Einheit im Spinner vorselektieren
            String unit = getIntent().getStringExtra("unit");
            for (int i = 0; i < units.length; i++) {
                if (units[i].equals(unit)) {
                    spinnerUnit.setSelection(i);
                    break;
                }
            }
            
            // Datum aus den übergebenen Millisekunden wiederherstellen
            long deadlineMillis = getIntent().getLongExtra("deadlineMillis", 0);
            if (deadlineMillis > 0) {
                calendar.setTimeInMillis(deadlineMillis);
                updateDeadlineText();
            }
            
            btnSaveGoal.setText(getString(R.string.btn_save_changes));
            setTitle(getString(R.string.edit_goal_title));
        }

        etDeadline.setOnClickListener(v -> showDatePicker());
        btnSaveGoal.setOnClickListener(v -> saveGoal());

        setupNavigationBar();
    }

    /**
     * Öffnet den Android-Standard-Kalenderdialog zur Auswahl einer Deadline.
     */
    private void showDatePicker() {
        DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, day) -> {
            calendar.set(year, month, day);
            updateDeadlineText();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        
        // Verhindert, dass Termine in der Vergangenheit gewählt werden
        dpd.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dpd.show();
    }

    private void updateDeadlineText() {
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        int month = calendar.get(Calendar.MONTH);
        int year = calendar.get(Calendar.YEAR);
        etDeadline.setText(day + "." + (month + 1) + "." + year);
    }

    /**
     * Sammelt die eingegebenen Daten und speichert sie in Firestore.
     * Entscheidet anhand der goalId, ob ein neues Dokument erstellt (add)
     * oder ein bestehendes aktualisiert (update) wird.
     */
    private void saveGoal() {
        String name = etProjectName.getText().toString().trim();
        String amountStr = etTotalAmount.getText().toString().trim();
        String unit = spinnerUnit.getSelectedItem().toString();

        // Validierung: Alle Felder müssen befüllt sein
        if (name.isEmpty() || amountStr.isEmpty() || etDeadline.getText().toString().isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        // Daten-Map für Firestore vorbereiten
        Map<String, Object> goal = new HashMap<>();
        goal.put("projectName", name);
        goal.put("totalAmount", Long.parseLong(amountStr));
        goal.put("unit", unit);
        goal.put("deadline", new Timestamp(calendar.getTime()));

        if (goalId == null) {
            // FALL 1: NEUES ZIEL ERSTELLEN
            goal.put("currentAmount", 0);
            goal.put("createdAt", Timestamp.now());
            goal.put("userId", FirebaseAuth.getInstance().getCurrentUser().getUid());

            db.collection("goals").add(goal)
                    .addOnSuccessListener(doc -> {
                        Toast.makeText(this, getString(R.string.msg_project_created, name), Toast.LENGTH_SHORT).show();
                        goToDailyPlan();
                    });
        } else {
            // FALL 2: BESTEHENDES ZIEL AKTUALISIEREN (Update)
            db.collection("goals").document(goalId).update(goal)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, getString(R.string.msg_progress_saved), Toast.LENGTH_SHORT).show();
                        goToDailyPlan();
                    });
        }
    }

    private void goToDailyPlan() {
        startActivity(new Intent(this, DailyPlanActivity.class));
        finish();
    }

    /**
     * Navigation-Setup: Ermöglicht den Wechsel zwischen den Hauptbereichen.
     */
    private void setupNavigationBar() {
        findViewById(R.id.btnNavDaily).setOnClickListener(v -> {
            startActivity(new Intent(this, DailyPlanActivity.class));
            finish();
        });
        // btnNavGoal ist in dieser Activity inaktiv/leer, da wir uns bereits hier befinden.
        findViewById(R.id.btnNavEval).setOnClickListener(v -> {
            startActivity(new Intent(this, EvaluationActivity.class));
            finish();
        });
        findViewById(R.id.btnNavSettings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
        });
    }
}
