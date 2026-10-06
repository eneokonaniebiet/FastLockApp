package com.infinix.smart10.fastlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class FastLockSecurityManager {
    private static final String PREFS = "fastlock_security_state";
    private static final String KEYSTORE_ALIAS = "FastLockSecurityKey";
    private static final String FAILED_KEY = "failed_attempts";
    private static final String LOCKOUT_KEY = "lockout_until";
    private static final String ENCRYPTED_FAILED_KEY = "encrypted_failed_attempts";
    private static final String ENCRYPTED_LOCKOUT_KEY = "encrypted_lockout_until";
    private static final String IV_FAILED_KEY = "iv_failed_attempts";
    private static final String IV_LOCKOUT_KEY = "iv_lockout_until";
    private static final int MAX_FAILED_ATTEMPTS = 20;
    private static final long LOCKOUT_DURATION_MILLISECONDS = 30L * 60L * 1000L;

    private FastLockSecurityManager() {
    }

    public FastLockSecurityManager(Context context) {
    }

    public static synchronized int recordFailedAttempt(Context context) {
        if (isLockedOut(context)) {
            return getFailedAttempts(context);
        }

        int attempts = getFailedAttempts(context) + 1;
        persistEncryptedInt(context, FAILED_KEY, attempts);

        if (attempts > MAX_FAILED_ATTEMPTS) {
            persistEncryptedLong(
                    context,
                    LOCKOUT_KEY,
                    System.currentTimeMillis() + LOCKOUT_DURATION_MILLISECONDS
            );
        }

        return attempts;
    }

    public static synchronized boolean isLockedOut(Context context) {
        long lockoutUntil = getEncryptedLong(context, LOCKOUT_KEY, 0L);

        if (lockoutUntil <= 0L) {
            return false;
        }

        if (System.currentTimeMillis() >= lockoutUntil) {
            resetFailures(context);
            return false;
        }

        return true;
    }

    public static synchronized long getRemainingLockoutMilliseconds(Context context) {
        long lockoutUntil = getEncryptedLong(context, LOCKOUT_KEY, 0L);

        if (lockoutUntil <= 0L) {
            return 0L;
        }

        long remaining = lockoutUntil - System.currentTimeMillis();

        if (remaining <= 0L) {
            resetFailures(context);
            return 0L;
        }

        return remaining;
    }

    public static synchronized long getRemainingLockoutMinutes(Context context) {
        long milliseconds = getRemainingLockoutMilliseconds(context);

        if (milliseconds <= 0L) {
            return 0L;
        }

        return (milliseconds + 59999L) / 60000L;
    }

    public static synchronized int getFailedAttempts(Context context) {
        return getEncryptedInt(context, FAILED_KEY, 0);
    }

    public static synchronized boolean canVerify(Context context) {
        return !isLockedOut(context);
    }

    public static synchronized void resetFailures(Context context) {
        persistEncryptedInt(context, FAILED_KEY, 0);
        persistEncryptedLong(context, LOCKOUT_KEY, 0L);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            KeyStore.Entry entry = keyStore.getEntry(KEYSTORE_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                "AES",
                "AndroidKeyStore"
        );

        generator.init(
                new android.security.keystore.KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        android.security.keystore.KeyProperties.PURPOSE_ENCRYPT
                                | android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                        .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setRandomizedEncryptionRequired(true)
                        .build()
        );

        return generator.generateKey();
    }

    private static void persistEncryptedInt(Context context, String key, int value) {
        persistEncryptedString(context, key, Integer.toString(value));
    }

    private static void persistEncryptedLong(Context context, String key, long value) {
        persistEncryptedString(context, key, Long.toString(value));
    }

    private static void persistEncryptedString(Context context, String key, String value) {
        try {
            SecretKey secretKey = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();

            String encodedValue = Base64.encodeToString(encrypted, Base64.NO_WRAP);
            String encodedIv = Base64.encodeToString(iv, Base64.NO_WRAP);

            String ivKey = key.equals(FAILED_KEY)
                    ? IV_FAILED_KEY
                    : IV_LOCKOUT_KEY;

            String encryptedKey = key.equals(FAILED_KEY)
                    ? ENCRYPTED_FAILED_KEY
                    : ENCRYPTED_LOCKOUT_KEY;

            SharedPreferences preferences =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            preferences.edit()
                    .putString(encryptedKey, encodedValue)
                    .putString(ivKey, encodedIv)
                    .apply();
        } catch (Exception exception) {
            throw new IllegalStateException("FastLock security state encryption failed", exception);
        }
    }

    private static int getEncryptedInt(Context context, String key, int fallback) {
        String value = getEncryptedString(context, key, null);

        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long getEncryptedLong(Context context, String key, long fallback) {
        String value = getEncryptedString(context, key, null);

        if (value == null) {
            return fallback;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String getEncryptedString(
            Context context,
            String key,
            String fallback
    ) {
        try {
            SharedPreferences preferences =
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            String encryptedKey = key.equals(FAILED_KEY)
                    ? ENCRYPTED_FAILED_KEY
                    : ENCRYPTED_LOCKOUT_KEY;

            String ivKey = key.equals(FAILED_KEY)
                    ? IV_FAILED_KEY
                    : IV_LOCKOUT_KEY;

            String encryptedValue = preferences.getString(encryptedKey, null);
            String encodedIv = preferences.getString(ivKey, null);

            if (encryptedValue == null || encodedIv == null) {
                return fallback;
            }

            SecretKey secretKey = getOrCreateKey();

            byte[] encrypted =
                    Base64.decode(encryptedValue, Base64.NO_WRAP);

            byte[] iv =
                    Base64.decode(encodedIv, Base64.NO_WRAP);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    new GCMParameterSpec(128, iv)
            );

            return new String(
                    cipher.doFinal(encrypted),
                    StandardCharsets.UTF_8
            );
        } catch (Exception exception) {
            return fallback;
        }
    }
}
