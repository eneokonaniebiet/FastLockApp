package com.infinix.smart10.fastlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class SetupAndPinActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "fastlock_setup_state";
    private static final String ACTIVATED_KEY = "is_activated";
    private static final String ENCRYPTED_PIN_KEY = "encrypted_fastlock_pin";
    private static final String ENCRYPTED_PIN_IV_KEY = "encrypted_fastlock_pin_iv";
    private static final String KEYSTORE_ALIAS = "FastLockPinKey";
    private static final String SAFETY_KEY = "7924";
    private static final int MIN_PIN_LENGTH = 4;
    private static final int MAX_PIN_LENGTH = 6;
    private static final long LOCKOUT_MILLISECONDS = 30L * 60L * 1000L;

    private EditText safetyKeyInput;
    private EditText pinSetupInput;
    private TextView setupStatus;
    private TextView pinStatus;

    private Button btnSetupUnlock;
    private Button btnSavePin;

    private Button btn0;
    private Button btn1;
    private Button btn2;
    private Button btn3;
    private Button btn4;
    private Button btn5;
    private Button btn6;
    private Button btn7;
    private Button btn8;
    private Button btn9;
    private Button btnBackspace;
    private Button btnCancel;

    private ImageView pinDot1;
    private ImageView pinDot2;
    private ImageView pinDot3;
    private ImageView pinDot4;
    private ImageView pinDot5;
    private ImageView pinDot6;

    private CountDownTimer lockoutTimer;
    private StringBuilder pinInput;
    private boolean setupMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        if (!preferences.getBoolean(ACTIVATED_KEY, false)) {
            showSetupInterface();
        } else {
            showPinInterface();
        }
    }

    private void showSetupInterface() {
        setupMode = true;
        setContentView(R.layout.activity_setup);

        safetyKeyInput = findViewById(R.id.safetyKeyInput);
        pinSetupInput = findViewById(R.id.pinSetupInput);
        setupStatus = findViewById(R.id.setupStatus);
        btnSetupUnlock = findViewById(R.id.btnSetupUnlock);
        btnSavePin = findViewById(R.id.btnSavePin);

        btnSetupUnlock.setOnClickListener(v -> validateSafetyKey());
        btnSavePin.setOnClickListener(v -> saveNewPin());

        pinSetupInput.setEnabled(false);
        btnSavePin.setEnabled(false);
    }

    private void validateSafetyKey() {
        String entered = safetyKeyInput.getText().toString().trim();

        if (SAFETY_KEY.equals(entered)) {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(ACTIVATED_KEY, true)
                    .apply();

            safetyKeyInput.setEnabled(false);
            btnSetupUnlock.setEnabled(false);
            pinSetupInput.setEnabled(true);
            btnSavePin.setEnabled(true);

            setupStatus.setText("SAFETY KEY ACCEPTED");
            pinSetupInput.requestFocus();
            showKeyboard(pinSetupInput);
        } else {
            safetyKeyInput.setText("");
            setupStatus.setText("INCORRECT SAFETY KEY");
        }
    }

    private void saveNewPin() {
        if (!getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(ACTIVATED_KEY, false)) {
            setupStatus.setText("UNLOCK SETUP WITH SAFETY KEY FIRST");
            return;
        }

        String pin = pinSetupInput.getText().toString();

        if (!pin.matches("[0-9]+")
                || pin.length() < MIN_PIN_LENGTH
                || pin.length() > MAX_PIN_LENGTH) {
            setupStatus.setText("USE A 4–6 DIGIT PIN");
            return;
        }

        try {
            SecretKey secretKey = getOrCreatePinKey();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encrypted = cipher.doFinal(
                    pin.getBytes(StandardCharsets.UTF_8)
            );

            String encodedPin =
                    Base64.encodeToString(encrypted, Base64.NO_WRAP);

            String encodedIv =
                    Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(ENCRYPTED_PIN_KEY, encodedPin)
                    .putString(ENCRYPTED_PIN_IV_KEY, encodedIv)
                    .apply();

            hideKeyboard(pinSetupInput);
            setupStatus.setText("FASTLOCK PIN SAVED");
            showPinInterface();

        } catch (Exception exception) {
            setupStatus.setText("SECURE PIN STORAGE FAILED");
        }
    }

    private void showPinInterface() {
        setupMode = false;
        setContentView(R.layout.activity_pin_fallback);

        pinInput = new StringBuilder();

        btn0 = findViewById(R.id.btn0);
        btn1 = findViewById(R.id.btn1);
        btn2 = findViewById(R.id.btn2);
        btn3 = findViewById(R.id.btn3);
        btn4 = findViewById(R.id.btn4);
        btn5 = findViewById(R.id.btn5);
        btn6 = findViewById(R.id.btn6);
        btn7 = findViewById(R.id.btn7);
        btn8 = findViewById(R.id.btn8);
        btn9 = findViewById(R.id.btn9);
        btnBackspace = findViewById(R.id.btnBackspace);
        btnCancel = findViewById(R.id.btnCancel);

        pinDot1 = findViewById(R.id.pinDot1);
        pinDot2 = findViewById(R.id.pinDot2);
        pinDot3 = findViewById(R.id.pinDot3);
        pinDot4 = findViewById(R.id.pinDot4);
        pinDot5 = findViewById(R.id.pinDot5);
        pinDot6 = findViewById(R.id.pinDot6);

        pinStatus = findViewById(R.id.pinStatus);

        btn0.setOnClickListener(v -> appendDigit("0"));
        btn1.setOnClickListener(v -> appendDigit("1"));
        btn2.setOnClickListener(v -> appendDigit("2"));
        btn3.setOnClickListener(v -> appendDigit("3"));
        btn4.setOnClickListener(v -> appendDigit("4"));
        btn5.setOnClickListener(v -> appendDigit("5"));
        btn6.setOnClickListener(v -> appendDigit("6"));
        btn7.setOnClickListener(v -> appendDigit("7"));
        btn8.setOnClickListener(v -> appendDigit("8"));
        btn9.setOnClickListener(v -> appendDigit("9"));
        btnBackspace.setOnClickListener(v -> removeLastDigit());
        btnCancel.setOnClickListener(v -> cancelPinEntry());

        updatePinDots();
        updateLockoutState();
    }

    private void appendDigit(String digit) {
        if (FastLockSecurityManager.isLockedOut(this)) {
            updateLockoutState();
            return;
        }

        if (pinInput.length() >= MAX_PIN_LENGTH) {
            return;
        }

        pinInput.append(digit);
        updatePinDots();

        if (pinInput.length() == MIN_PIN_LENGTH) {
            verifyEnteredPin();
        } else if (pinInput.length() == MAX_PIN_LENGTH) {
            verifyEnteredPin();
        }
    }

    private void removeLastDigit() {
        if (FastLockSecurityManager.isLockedOut(this)) {
            updateLockoutState();
            return;
        }

        if (pinInput.length() == 0) {
            return;
        }

        pinInput.deleteCharAt(pinInput.length() - 1);
        updatePinDots();

        if (pinStatus != null) {
            pinStatus.setText("ENTER YOUR FASTLOCK PIN");
        }
    }

    private void cancelPinEntry() {
        if (FastLockSecurityManager.isLockedOut(this)) {
            updateLockoutState();
            return;
        }

        pinInput.setLength(0);
        updatePinDots();

        if (pinStatus != null) {
            pinStatus.setText("ENTER YOUR FASTLOCK PIN");
        }
    }

    private void updatePinDots() {
        ImageView[] dots = new ImageView[]{
                pinDot1,
                pinDot2,
                pinDot3,
                pinDot4,
                pinDot5,
                pinDot6
        };

        for (int index = 0; index < dots.length; index++) {
            if (dots[index] == null) {
                continue;
            }

            android.graphics.drawable.GradientDrawable drawable =
                    new android.graphics.drawable.GradientDrawable();

            drawable.setShape(
                    android.graphics.drawable.GradientDrawable.OVAL
            );

            drawable.setSize(18, 18);

            if (index < pinInput.length()) {
                drawable.setColor(0xFF00FFCC);
                dots[index].setAlpha(1.0f);
            } else {
                drawable.setColor(0xFF34474B);
                dots[index].setAlpha(0.65f);
            }

            dots[index].setImageDrawable(drawable);
        }
    }

    private void verifyEnteredPin() {
        if (FastLockSecurityManager.isLockedOut(this)) {
            updateLockoutState();
            return;
        }

        if (pinInput.length() < MIN_PIN_LENGTH
                || pinInput.length() > MAX_PIN_LENGTH) {
            return;
        }

        try {
            String storedPin = decryptStoredPin();

            if (storedPin == null) {
                handleFailedPin();
                return;
            }

            boolean valid = MessageDigest.isEqual(
                    pinInput.toString().getBytes(StandardCharsets.UTF_8),
                    storedPin.getBytes(StandardCharsets.UTF_8)
            );

            if (valid) {
                authenticateSuccessfully();
            } else {
                handleFailedPin();
            }
        } catch (Exception exception) {
            if (pinStatus != null) {
                pinStatus.setText("PIN VALIDATION FAILED");
            }
            handleFailedPin();
        }
    }

    private String decryptStoredPin() throws Exception {
        SharedPreferences preferences =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String encryptedPin =
                preferences.getString(ENCRYPTED_PIN_KEY, null);

        String encryptedIv =
                preferences.getString(ENCRYPTED_PIN_IV_KEY, null);

        if (encryptedPin == null || encryptedIv == null) {
            return null;
        }

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            return null;
        }

        KeyStore.SecretKeyEntry entry =
                (KeyStore.SecretKeyEntry) keyStore.getEntry(
                        KEYSTORE_ALIAS,
                        null
                );

        if (entry == null) {
            return null;
        }

        byte[] encryptedBytes =
                Base64.decode(encryptedPin, Base64.NO_WRAP);

        byte[] ivBytes =
                Base64.decode(encryptedIv, Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.DECRYPT_MODE,
                entry.getSecretKey(),
                new GCMParameterSpec(128, ivBytes)
        );

        return new String(
                cipher.doFinal(encryptedBytes),
                StandardCharsets.UTF_8
        );
    }

    private void handleFailedPin() {
        pinInput.setLength(0);
        updatePinDots();

        FastLockSecurityManager.recordFailedAttempt(this);

        if (FastLockSecurityManager.isLockedOut(this)) {
            activateLockoutMode();
            return;
        }

        if (pinStatus != null) {
            pinStatus.setText(
                    "INCORRECT PIN • "
                            + FastLockSecurityManager.getFailedAttempts(this)
                            + "/20"
            );
        }
    }

    private void authenticateSuccessfully() {
        FastLockSecurityManager.resetFailures(this);
        stopLockoutTimer();

        pinInput.setLength(0);
        updatePinDots();

        setResult(RESULT_OK);
        finish();
    }

    private void updateLockoutState() {
        if (FastLockSecurityManager.isLockedOut(this)) {
            activateLockoutMode();
        } else {
            enablePinControls();
        }
    }

    private void activateLockoutMode() {
        disablePinControls();

        if (pinStatus != null) {
            pinStatus.setText("SECURITY LOCKOUT");
        }

        startLockoutTimer();
    }

    private void disablePinControls() {
        Button[] buttons = new Button[]{
                btn0,
                btn1,
                btn2,
                btn3,
                btn4,
                btn5,
                btn6,
                btn7,
                btn8,
                btn9,
                btnBackspace,
                btnCancel
        };

        for (Button button : buttons) {
            if (button != null) {
                button.setEnabled(false);
            }
        }
    }

    private void enablePinControls() {
        Button[] buttons = new Button[]{
                btn0,
                btn1,
                btn2,
                btn3,
                btn4,
                btn5,
                btn6,
                btn7,
                btn8,
                btn9,
                btnBackspace,
                btnCancel
        };

        for (Button button : buttons) {
            if (button != null) {
                button.setEnabled(true);
            }
        }

        if (pinStatus != null) {
            pinStatus.setText("ENTER YOUR FASTLOCK PIN");
        }

        stopLockoutTimer();
    }

    private void startLockoutTimer() {
        stopLockoutTimer();

        long remainingMs =
                FastLockSecurityManager.getRemainingLockoutMilliseconds(this);

        if (remainingMs <= 0L) {
            FastLockSecurityManager.resetFailures(this);
            enablePinControls();
            return;
        }

        lockoutTimer = new CountDownTimer(
                remainingMs,
                1000L
        ) {
            @Override
            public void onTick(long millisUntilFinished) {
                long totalSeconds =
                        (millisUntilFinished + 999L) / 1000L;

                long minutes = totalSeconds / 60L;
                long seconds = totalSeconds % 60L;

                if (pinStatus != null) {
                    pinStatus.setText(
                            String.format(
                                    Locale.US,
                                    "SECURITY LOCKOUT\nTRY AGAIN IN %02d:%02d",
                                    minutes,
                                    seconds
                            )
                    );
                }
            }

            @Override
            public void onFinish() {
                FastLockSecurityManager.resetFailures(
                        SetupAndPinActivity.this
                );

                pinInput.setLength(0);
                updatePinDots();
                enablePinControls();
            }
        };

        lockoutTimer.start();
    }

    private void stopLockoutTimer() {
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
            lockoutTimer = null;
        }
    }

    private void showKeyboard(View targetView) {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (manager != null) {
            manager.showSoftInput(
                    targetView,
                    InputMethodManager.SHOW_IMPLICIT
            );
        }
    }

    private void hideKeyboard(View targetView) {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(
                        Context.INPUT_METHOD_SERVICE
                );

        if (manager != null) {
            manager.hideSoftInputFromWindow(
                    targetView.getWindowToken(),
                    0
            );
        }
    }

    private SecretKey getOrCreatePinKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            KeyStore.SecretKeyEntry entry =
                    (KeyStore.SecretKeyEntry) keyStore.getEntry(
                            KEYSTORE_ALIAS,
                            null
                    );

            if (entry != null) {
                return entry.getSecretKey();
            }
        }

        KeyGenerator generator =
                KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES,
                        "AndroidKeyStore"
                );

        KeyGenParameterSpec spec =
                new KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT
                                | KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(
                                KeyProperties.ENCRYPTION_PADDING_NONE
                        )
                        .setRandomizedEncryptionRequired(true)
                        .build();

        generator.init(spec);
        return generator.generateKey();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!setupMode && pinInput != null) {
            updateLockoutState();
        }
    }

    @Override
    protected void onDestroy() {
        stopLockoutTimer();
        super.onDestroy();
    }
}
