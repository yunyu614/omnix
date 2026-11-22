import com.android.apksig.ApkSigner;
import java.io.*;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;

/** apksig 纯 Java 签名工具：java Signer in.apk out.apk keystore pass alias */
public class Signer {
    public static void main(String[] a) throws Exception {
        String in = a[0], out = a[1], ks = a[2], pass = a[3], alias = a[4];
        KeyStore store = KeyStore.getInstance("JKS");
        try (FileInputStream f = new FileInputStream(ks)) { store.load(f, pass.toCharArray()); }
        PrivateKey key = (PrivateKey) store.getKey(alias, pass.toCharArray());
        X509Certificate cert = (X509Certificate) store.getCertificate(alias);
        List<ApkSigner.SignerConfig> signers = new ArrayList<>();
        signers.add(new ApkSigner.SignerConfig.Builder(
                alias, key, Collections.singletonList(cert)).build());
        new ApkSigner.Builder(signers)
                .setInputApk(new File(in))
                .setOutputApk(new File(out))
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setMinSdkVersion(21)
                .build()
                .sign();
        System.out.println("signed -> " + out);
    }
}
