package io.github.valters.acme;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtensionsGenerator;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.X509ObjectIdentifiers;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;

public class KeyStorageUtil {
    private static final Logger logger = LoggerFactory.getLogger( KeyStorageUtil.class );

    private static final int USER_KEY_SIZE = 4096;

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
                        return asRsaKey( kp );
                    } );
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to generate (or load) " + ALG_RSA + " key pair.", e );
        }
    }

    public static RSAKey getUserKey( final String keyname ) {
        return getOrGenerate( keyname, USER_KEY_SIZE );
    }

    public static RSAKey getDomainKey( final String keyname ) {
        return getOrGenerate( keyname, CERT_KEY_SIZE );
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

    private static void savePem( final OutputStream outputStream, final X509Certificate cert ) throws IOException {
        try( JcaPEMWriter writer = new JcaPEMWriter( new PrintWriter( outputStream ) ) ) {
            writer.writeObject( cert );
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
            logger.debug( "key saved: {}", keyname );
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

    public static void saveDomainCertificate( final X509Certificate certificate, final String certFileName ) {

        try( OutputStream outputStream = new FileOutputStream( certFileName ) ) {
            savePem( outputStream, certificate );
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to store certificate ["+certFileName+"]", e );
        }

        getCACertificateURL( certificate ).ifPresent( KeyStorageUtil::storeIntermediateChain );
    }

    public static X509Certificate loadDomainCertificate( final String certFileName ) {
        try( InputStream is = new FileInputStream( certFileName ) ) {
            return parseCertificate( is );
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to load certificate ["+certFileName+"]", e );
        }
    }


    public static Optional<X509Certificate> getIntermediateChain( final X509Certificate certificate ) {
        return getCACertificateURL( certificate ).flatMap( KeyStorageUtil::retrieveCertificate );
    }

    private static void storeIntermediateChain( final String certUrl ) {
        try {
            final X509Certificate caIntermediateCertificate = retrieveCertificate( certUrl ).get();
            try( OutputStream outputStream = new FileOutputStream( "domain.chain.crt" ) ) {
                savePem( outputStream, caIntermediateCertificate );
            }
        }
        catch( final Exception e ) {
            throw new RuntimeException( "Failed to save intermediate cert", e );
        }
    }

    private static Optional<X509Certificate> retrieveCertificate( final String caIntermediateCertificateURL ) {
        try {
            logger.info( "retrieving intermediate {}", caIntermediateCertificateURL );
            try( InputStream is = new URL( caIntermediateCertificateURL ).openStream() ) { // TODO: use WS
                return Optional.of( parseCertificate( is ) );
            }
        }
        catch( final Exception e ) {
            throw new RuntimeException( "Failed to retrieve intermediate cert", e );
        }
    }

    public static Optional<String> getCACertificateURL( final X509Certificate certificate ) {
        try {
            final byte[] extension = certificate.getExtensionValue( Extension.authorityInfoAccess.getId() );
            final byte[] ext_octets = ( (ASN1OctetString) ASN1Primitive.fromByteArray( extension ) ).getOctets();
            final AuthorityInformationAccess access = AuthorityInformationAccess.getInstance( ASN1Sequence.fromByteArray( ext_octets ) );
            for( final AccessDescription ad : access.getAccessDescriptions() ) {
                if( ad.getAccessMethod().equals( X509ObjectIdentifiers.id_ad_caIssuers ) ) {
                    return Optional.of( ad.getAccessLocation().getName().toString() );
                }
            }
            return Optional.empty();
        }
        catch( final Exception e ) {
            logger.error( "Failed to save intermediate cert from["+certificate+"]", e );
            return Optional.empty();
        }
    }

    public static X509Certificate parseCertificate( final byte[] bytes ) {
        try( final ByteArrayInputStream is = new ByteArrayInputStream( bytes ) ) {
            return parseCertificate( is );
        }
        catch( final IOException e ) {
            throw new RuntimeException( "Failed to parse certificate", e );
        }
    }

    public static X509Certificate parseCertificate( final InputStream is ) {
        try {
            final CertificateFactory certFactory = CertificateFactory.getInstance( "X.509" );
            final Certificate cert = certFactory.generateCertificate( is );
            return (X509Certificate) cert;
        }
        catch( final Exception e ) {
            throw new RuntimeException( "Failed to parse certificate", e );
        }
    }

    public static KeyStore loadKeystore( final String filename, final String password )
            throws KeyStoreException, FileNotFoundException, IOException, NoSuchAlgorithmException, CertificateException {
        try( final FileInputStream is = new FileInputStream( filename ) ) {
            final KeyStore keystore = KeyStore.getInstance( KeyStore.getDefaultType() );
            keystore.load( is, password.toCharArray() );
            return keystore;
        }
    }

    public static void saveKeystore( final KeyStore keystore, final String filename, final String password )
            throws FileNotFoundException, IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
        try( final FileOutputStream out = new FileOutputStream( filename ) ) {
            keystore.store( out, password.toCharArray() );
        }
    }

    public static KeyStore newKeystore() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
        final KeyStore keystore = KeyStore.getInstance( KeyStore.getDefaultType() );
        keystore.load( null );
        return keystore;
    }

    public static void storeCertificateKey( final KeyStore keystore, final String password, final Certificate cert, final KeyPair certKey, final String keyAlias ) throws KeyStoreException {
        keystore.setKeyEntry( keyAlias, certKey.getPrivate(), password.toCharArray(), new java.security.cert.Certificate[] { cert } );
    }

}
