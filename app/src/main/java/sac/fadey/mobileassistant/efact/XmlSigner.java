package sac.fadey.mobileassistant.efact;

import org.apache.xml.security.Init;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.transforms.Transforms;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** Firma XMLDSig enveloped RSA-SHA256 (Apache Santuario) y coloca Signature en ExtensionContent. */
public final class XmlSigner {
    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            if (!Init.isInitialized()) Init.init();
        } catch (Exception ignored) {}
    }

    private XmlSigner() {}

    public static class KeyMaterial {
        public PrivateKey privateKey;
        public X509Certificate certificate;
    }

    public static KeyMaterial loadPfx(byte[] pfxBytes, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(pfxBytes), password);
        String alias = null;
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            if (ks.isKeyEntry(a)) {
                alias = a;
                break;
            }
        }
        if (alias == null) throw new IllegalArgumentException("PFX sin clave privada");
        KeyMaterial km = new KeyMaterial();
        km.privateKey = (PrivateKey) ks.getKey(alias, password);
        km.certificate = (X509Certificate) ks.getCertificate(alias);
        if (km.privateKey == null || km.certificate == null) {
            throw new IllegalArgumentException("PFX incompleto");
        }
        return km;
    }

    public static byte[] sign(byte[] xmlBytes, byte[] pfxBytes, String password) throws Exception {
        KeyMaterial km = loadPfx(pfxBytes, password == null ? new char[0] : password.toCharArray());
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
        Element root = doc.getDocumentElement();
        if (root.hasAttribute("Id")) root.removeAttribute("Id");

        NodeList extContents = doc.getElementsByTagNameNS(
                "urn:oasis:names:specification:ubl:schema:xsd:CommonExtensionComponents-2",
                "ExtensionContent");
        Element parent = extContents.getLength() > 0 ? (Element) extContents.item(0) : root;

        XMLSignature sig = new XMLSignature(doc, "", XMLSignature.ALGO_ID_SIGNATURE_RSA_SHA256);
        parent.appendChild(sig.getElement());
        Transforms transforms = new Transforms(doc);
        transforms.addTransform(Transforms.TRANSFORM_ENVELOPED_SIGNATURE);
        transforms.addTransform(Transforms.TRANSFORM_C14N_EXCL_OMIT_COMMENTS);
        sig.addDocument("", transforms, Constants.ALGO_ID_DIGEST_SHA256);
        sig.addKeyInfo(km.certificate);
        sig.sign(km.privateKey);
        sig.getElement().setAttribute("Id", "SignatureSP");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        javax.xml.transform.Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.transform(new DOMSource(doc), new StreamResult(bos));
        return bos.toByteArray();
    }
}
