package com.example.lernplaner;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class CongratulationsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_congratulations);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> {
            startActivity(new Intent(CongratulationsActivity.this, GoalInputActivity.class));
            finish();
        });

        findViewById(R.id.btnToMain).setOnClickListener(v -> {
            startActivity(new Intent(CongratulationsActivity.this, DailyPlanActivity.class));
            finish();
        });
    }
}