// PaymentActivity.java
package com.example.evchargerlocator_androidapplication;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class PaymentActivity extends AppCompatActivity {

    private String chargingLevel, connectorType, network;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_payment);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        chargingLevel = getIntent().getStringExtra("chargingLevel");
        connectorType = getIntent().getStringExtra("connectorType");
        network = getIntent().getStringExtra("network");

        Button googlePayButton = findViewById(R.id.googlePayButton);
        Button paypalButton = findViewById(R.id.paypalButton);
        TextView addPaymentMethodText = findViewById(R.id.addPaymentMethodText);
        TextView savedPaymentMethodText = findViewById(R.id.savedPaymentMethodText);
        TextView backArrowText = findViewById(R.id.backArrowText);

        backArrowText.setOnClickListener(v -> finish());

        savedPaymentMethodText.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentActivity.this, SavedPaymentMethodsActivity.class);
            intent.putExtra("chargingLevel", chargingLevel);
            intent.putExtra("connectorType", connectorType);
            intent.putExtra("network", network);
            startActivity(intent);
        });

        addPaymentMethodText.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentActivity.this, CardDetails.class);
            intent.putExtra("chargingLevel", chargingLevel);
            intent.putExtra("connectorType", connectorType);
            intent.putExtra("network", network);
            startActivity(intent);
        });

        // Open Google Pay Wallet
        googlePayButton.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://pay.google.com/"));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(PaymentActivity.this, "Google Pay is not available", Toast.LENGTH_SHORT).show();
            }
        });

        // Open PayPal App
        paypalButton.setOnClickListener(v -> {
            Intent intent = getPackageManager().getLaunchIntentForPackage("com.paypal.android.p2pmobile");
            if (intent != null) {
                startActivity(intent);
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.com/")));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(PaymentActivity.this, "PayPal app not installed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
