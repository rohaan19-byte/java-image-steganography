import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;

/*
Source: https://www.tutorialspoint.com/java_cryptography/java_cryptography_encrypting_data.htm

I modified it so that I will use symmetric encryption aka AES instead of RSA encryption because I want to encrypt larger data.
RSA encryption will be useful to securely exchange the symmetric encryption key.

Also, I modified it so that I use Base64 encoding, so that the encrypted message contains only characters that are within the
ASCII range
*/

public class TextEncryptor {
    public static void CreatingASignatureObject() throws Exception {
        Signature sign = Signature.getInstance("SHA256withRSA");
    }

    public SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    public Cipher getCipher() throws Exception {
        return Cipher.getInstance("AES");
    }

    public String encrypt(String input, SecretKey secretKey, Cipher cipher) throws Exception {
        byte[] inputBytes = input != null ? input.getBytes(StandardCharsets.UTF_8) : new byte[0];
        byte[] encryptedBytes = encrypt(inputBytes, secretKey, cipher);
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public byte[] encrypt(byte[] inputBytes, SecretKey secretKey, Cipher cipher) throws Exception {
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        return cipher.doFinal(inputBytes != null ? inputBytes : new byte[0]);
    }
}
