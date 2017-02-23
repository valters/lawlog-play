package io.github.valters.acme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;

public class KeyStorage {

    private static final String USER_KEY = "user.key";
    private static final String DOMAIN_KEY = "domain.key";

    private static final int USER_KEY_SIZE = 2048; //4096;
    private static final int CERT_KEY_SIZE = 2048;

    public static final String ALG_RSA = "RSA";

    public static final JWSAlgorithm RS256 = JWSAlgorithm.RS256;

    private static final String SIG_SHA256 = "SHA256withRSA";

    public static KeyPair generateKeyPair( final int keySize ) {
        try {
            final KeyPairGenerator keyGen = KeyPairGenerator.getInstance( ALG_RSA );
            keyGen.initialize( keySize );
            return keyGen.generateKeyPair();
        }
        catch( final NoSuchAlgorithmException e ) {
            throw new RuntimeException( "Failed to generate " + ALG_RSA + " key pair.", e );
        }
    }

    public static RSAKey asRsaKey( final KeyPair kp ) {
        return new RSAKey.Builder( (RSAPublicKey) kp.getPublic() ).privateKey( (RSAPrivateKey) kp.getPrivate() ).algorithm( RS256 ).build();
    }

    private static RSAKey getOrGenerate( final String keyname, final int keySize ) {
        try {
            final Optional<KeyPair> key = loadKeyPair( keyname );
            return key.flatMap( kp -> Optional.of( asRsaKey( kp ) ) )
               .orElseGet( () -> {
                   final KeyPair kp = generateKeyPair( keySize );
                   saveKeyPair( keyname, kp );
                   return asRsaKey(kp);
               } );
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to generate (or load) " + ALG_RSA + " key pair.", e );
        }
    }

    public static RSAKey getUserKey() {
        return getOrGenerate( USER_KEY, USER_KEY_SIZE );
    }

    public static RSAKey getDomainKey() {
        return getOrGenerate( DOMAIN_KEY, CERT_KEY_SIZE );
    }

    public static PKCS10CertificationRequest generateCertificateSigningRequest( final KeyPair domainKey, final String... domainNames )
            throws OperatorCreationException, IOException {
        final X500NameBuilder namebuilder = new X500NameBuilder( X500Name.getDefaultStyle() );
        namebuilder.addRDN( BCStyle.CN, domainNames[0] );

        final List<GeneralName> subjectAltNames = new ArrayList<>( domainNames.length );
        for( final String cn : domainNames ) {
            subjectAltNames.add( new GeneralName( GeneralName.dNSName, cn ) );
        }
        final GeneralNames subjectAltName = new GeneralNames( subjectAltNames.toArray( new GeneralName[0] ) );

        final ExtensionsGenerator extGen = new ExtensionsGenerator();
        extGen.addExtension( Extension.subjectAlternativeName, false, subjectAltName.toASN1Primitive() );

        final PKCS10CertificationRequestBuilder p10Builder = new JcaPKCS10CertificationRequestBuilder( namebuilder.build(), domainKey.getPublic() );
        p10Builder.addAttribute( PKCSObjectIdentifiers.pkcs_9_at_extensionRequest, extGen.generate() );
        final JcaContentSignerBuilder csBuilder = new JcaContentSignerBuilder( SIG_SHA256 );
        final ContentSigner signer = csBuilder.build( domainKey.getPrivate() );
        final PKCS10CertificationRequest request = p10Builder.build( signer );
        return request;
    }

    private static KeyPair loadPem( final InputStream privateKeyInputStream ) throws IOException {
        try( PEMParser pemParser = new PEMParser( new InputStreamReader( privateKeyInputStream ) ) ) {
            final PEMKeyPair keyPair = (PEMKeyPair) pemParser.readObject();
            return new JcaPEMKeyConverter().getKeyPair( keyPair );
        }
    }

    private static void savePem( final OutputStream outputStream, final KeyPair kp ) throws IOException {
        try( JcaPEMWriter writer = new JcaPEMWriter( new PrintWriter( outputStream ) ) ) {
            writer.writeObject( kp );
        }
    }

    private static Optional<KeyPair> loadKeyPair( final String keyname ) throws IOException {
        final File filePrivateKey = new File( keyname );
        if( !filePrivateKey.exists() ) {
            return Optional.empty();
        }

        try( InputStream privateKeyInputStream = new FileInputStream( filePrivateKey ) ) {
            return Optional.of( loadPem( privateKeyInputStream ) );
        }
    }

    private static void saveKeyPair( final String keyname, final KeyPair kp ) {
        try {
            final File filePrivateKey = new File( keyname );
            try( OutputStream outputStream = new FileOutputStream( filePrivateKey ) ) {
                savePem( outputStream, kp );
            }
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to generate (or load) " + ALG_RSA + " key pair.", e );
        }
    }

    public static String asPem( final PKCS10CertificationRequest csr ) {
        try {
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try( JcaPEMWriter writer = new JcaPEMWriter( new PrintWriter( bos ) ) ) {
                writer.writeObject( csr );
            }
            return new String( bos.toByteArray() );
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to write CSR to String.", e );
        }
    }

    public static String asBase64( final PKCS10CertificationRequest csr ) {
        try {
            return Base64URL.encode( csr.getEncoded() ).toString();
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to write CSR to Base64 String.", e );
        }

    }

}
