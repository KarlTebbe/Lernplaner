package com.example.lernplaner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailInput, passwordInput;
    private Button loginButton, registerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.editTextEmail);
        passwordInput = findViewById(R.id.editTextPassword);
        loginButton = findViewById(R.id.buttonLogin);
        registerButton = findViewById(R.id.buttonRegister);

        mAuth = FirebaseAuth.getInstance();

        // --- LOGIN BUTTON ---
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String email = emailInput.getText().toString();
                String password = passwordInput.getText().toString();

                if(!email.isEmpty() && !password.isEmpty()) {
                    loginUser(email, password);
                } else {
                    Toast.makeText(LoginActivity.this, getString(R.string.msg_enter_email_password), Toast.LENGTH_SHORT).show();
                }
            }
        });

        // --- ZUR REGISTRIERUNG WECHSELN ---
        registerButton.setText(getString(R.string.btn_new_account));
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        });
    }

    private void loginUser(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i("LoginActivity", "Login erfolgreich!");
                        goToDailyPlan();
                    } else {
                        String errorMessage = getString(R.string.msg_login_failed);
                        Exception exception = task.getException();
                        
                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            errorMessage = getString(R.string.msg_user_not_found);
                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = getString(R.string.msg_invalid_credentials);
                        } else if (exception != null) {
                            errorMessage = getString(R.string.msg_error_prefix, exception.getLocalizedMessage());
                        }
                        
                        Log.e("LoginActivity", "Login fehlgeschlagen", exception);
                        Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToDailyPlan() {
        Intent intent = new Intent(this, DailyPlanActivity.class);
        startActivity(intent);
        finish();
    }
}