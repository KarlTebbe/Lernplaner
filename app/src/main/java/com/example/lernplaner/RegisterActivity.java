package com.example.lernplaner;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;

public class RegisterActivity extends AppCompatActivity {

    private EditText emailEt, passwordEt, confirmEt;
    private Button registerBtn, backToLoginBtn;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();

        emailEt = findViewById(R.id.etRegisterEmail);
        passwordEt = findViewById(R.id.etRegisterPassword);
        confirmEt = findViewById(R.id.etRegisterConfirm);
        registerBtn = findViewById(R.id.btnDoRegister);
        backToLoginBtn = findViewById(R.id.btnBackToLogin);

        registerBtn.setOnClickListener(v -> registerUser());
        backToLoginBtn.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String email = emailEt.getText().toString();
        String password = passwordEt.getText().toString();
        String confirm = confirmEt.getText().toString();

        if (email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, getString(R.string.msg_fill_all_fields), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirm)) {
            Toast.makeText(this, getString(R.string.msg_passwords_dont_match), Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 6) {
            Toast.makeText(this, getString(R.string.msg_password_too_short), Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Log.i("RegisterActivity", "Registrierung erfolgreich!");
                        startActivity(new Intent(this, DailyPlanActivity.class));
                        finishAffinity();
                    } else {
                        String errorMessage = getString(R.string.msg_register_failed);
                        Exception exception = task.getException();

                        if (exception instanceof FirebaseAuthWeakPasswordException) {
                            errorMessage = getString(R.string.msg_weak_password);
                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = getString(R.string.msg_invalid_email);
                        } else if (exception instanceof FirebaseAuthUserCollisionException) {
                            errorMessage = getString(R.string.msg_email_collision);
                        } else if (exception != null) {
                            errorMessage = getString(R.string.msg_error_prefix, exception.getLocalizedMessage());
                        }

                        Log.e("RegisterActivity", "Fehler", exception);
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }
}