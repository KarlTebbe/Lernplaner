package com.example.lernplaner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        mAuth = FirebaseAuth.getInstance();

        findViewById(R.id.btnResetPassword).setOnClickListener(v -> resetPassword());
        findViewById(R.id.btnLogoutSettings).setOnClickListener(v -> logout());
        
        setupNavigationBar();
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
        findViewById(R.id.btnNavEval).setOnClickListener(v -> {
            startActivity(new Intent(this, EvaluationActivity.class));
            finish();
        });
    }

    private void resetPassword() {
        String email = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;
        if (email != null) {
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, R.string.msg_reset_email_sent, Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, getString(R.string.msg_error_prefix, task.getException().getMessage()), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void logout() {
        mAuth.signOut();
        Intent intent = new Intent(this, StartActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
