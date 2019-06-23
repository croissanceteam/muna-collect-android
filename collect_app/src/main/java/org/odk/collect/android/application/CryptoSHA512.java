package org.odk.collect.android.application;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CryptoSHA512 {
    private static MessageDigest md;

    public static String getPassWord(String s) throws NoSuchAlgorithmException {
        md=MessageDigest.getInstance("SHA-512");
        byte[] messageDigest = md.digest(s.getBytes());
        BigInteger no = new BigInteger(1, messageDigest);
        String hashtext = no.toString(16);
        while (hashtext.length() < 32) {
            hashtext = "0" + hashtext;
        }
        return hashtext;
    }
}
