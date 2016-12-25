package com.andyserver.maven.plugins;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import org.apache.http.ssl.TrustStrategy;

public class AcceptAllTrustStrategy implements TrustStrategy {

    public static AcceptAllTrustStrategy INSTANCE = new AcceptAllTrustStrategy();

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        return true;
    }

}
