package com.example.evchargerlocator_androidapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Calendar;

public class CardDetails extends AppCompatActivity {

    private EditText cardNumberInput, cardHolderInput, expiryDateInput, cvvInput;
    private Button continueButton;
    private CheckBox saveCardCheckbox;
    private FirebaseAuth auth;
    private DatabaseReference databaseRef;

    private String chargingLevel, connectorType, network;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_details);

        auth = FirebaseAuth.getInstance();
        databaseRef = FirebaseDatabase.getInstance().getReference("SavedCards");

        cardNumberInput = findViewById(R.id.cardNumberInput);
        cardHolderInput = findViewById(R.id.cardHolderInput);
        expiryDateInput = findViewById(R.id.expiryDateInput);
        cvvInput = findViewById(R.id.cvvInput);
        continueButton = findViewById(R.id.continueButton);
        saveCardCheckbox = findViewById(R.id.saveCardCheckbox);
        TextView backArrowText = findViewById(R.id.backArrowText);

        chargingLevel = getIntent().getStringExtra("chargingLevel");
        connectorType = getIntent().getStringExtra("connectorType");
        network = getIntent().getStringExtra("network");

        backArrowText.setOnClickListener(v -> finish());
        continueButton.setOnClickListener(v -> saveCardDetails());
    }

    private void saveCardDetails() {
        String userId = auth.getCurrentUser().getUid();
        String cardNumber = cardNumberInput.getText().toString().trim();
        String cardHolderName = cardHolderInput.getText().toString().trim();
        String expiryDate = expiryDateInput.getText().toString().trim();
        String cvv = cvvInput.getText().toString().trim();

        if (cardNumber.isEmpty() || cardHolderName.isEmpty() || expiryDate.isEmpty() || cvv.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cardNumber.length() != 12 && cardNumber.length() != 16) {
            Toast.makeText(this, "Enter a valid 12 or 16-digit card number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!expiryDate.matches("(0[1-9]|1[0-2])/(\\d{2})")) {
            Toast.makeText(this, "Enter a valid expiry date (MM/YY)", Toast.LENGTH_SHORT).show();
            return;
        }

        Calendar calendar = Calendar.getInstance();
        int currentMonth = calendar.get(Calendar.MONTH) + 1;
        int currentYear = calendar.get(Calendar.YEAR) % 100;

        String[] expiryParts = expiryDate.split("/");
        int expiryMonth = Integer.parseInt(expiryParts[0]);
        int expiryYear = Integer.parseInt(expiryParts[1]);

        if (expiryYear < currentYear || (expiryYear == currentYear && expiryMonth < currentMonth)) {
            Toast.makeText(this, "Expiry date must be in the future", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cvv.length() != 3 && cvv.length() != 4) {
            Toast.makeText(this, "Enter a valid 3 or 4-digit CVV", Toast.LENGTH_SHORT).show();
            return;
        }

        String maskedCardNumber = "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);

        if (saveCardCheckbox.isChecked()) {
            String cardId = databaseRef.child(userId).push().getKey();
            Card card = new Card(maskedCardNumber, cardHolderName, expiryDate);
            databaseRef.child(userId).child(cardId).setValue(card).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Card saved successfully!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to save card", Toast.LENGTH_SHORT).show();
                }
            });
        }

        Intent intent = new Intent(this, PaymentProcessingActivity.class);
        intent.putExtra("chargingLevel", chargingLevel);
        intent.putExtra("connectorType", connectorType);
        intent.putExtra("network", network);
        startActivity(intent);
        finish();
    }
}
