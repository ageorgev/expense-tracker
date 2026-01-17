package com.util.encryptor.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;

public class Utilities {
    public static String encrypt(String plainText) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        /**
         * Important: The master key must be set as an environment variable
         * named 'JASYPT_ENCRYPTOR_PASSWORD'. If not found, the program will exit with an error message.
         * 1. Open the JSON Settings
         * Press Ctrl + Shift + P (Windows/Linux) or Cmd + Shift + P (macOS) to open the Command Palette.
         *
         * Type "Open User Settings (JSON)" and press Enter.
         * To set the environment variable in VSCode, add the following line to your settings.json:
         * 
         * "terminal.integrated.env.osx": {
         *     "JASYPT_ENCRYPTOR_PASSWORD": "your_master_key_here"
         * }   
         * For Windows, use "terminal.integrated.env.windows"
         * For Linux, use "terminal.integrated.env.linux"     
         */
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
