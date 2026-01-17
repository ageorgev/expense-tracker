package com.util.encryptor.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

public class Utilities {
    public static String encrypt(String plainText) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        String masterKey = System.getenv("JASYPT_ENCRYPTOR_PASSWORD");
        if (masterKey == null || masterKey.isEmpty()) {
            System.err.println("Error: Environment variable 'JASYPT_ENCRYPTOR_PASSWORD' not found!");
            System.err.println("Please restart VSCode or check your settings.json.");
            return "";
        }
        config.setPassword(masterKey);
        config.setAlgorithm("PBEWithMD5AndDES"); // Default algorithm
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setProviderName("SunJCE");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.NoIvGenerator");
        config.setStringOutputType("base64");
        
        encryptor.setConfig(config);
        return encryptor.encrypt(plainText);
    }
}
