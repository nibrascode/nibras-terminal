declare module "node-forge" {
  const forge: {
    pki: {
      rsa: { generateKeyPair(bits: number): { privateKey: unknown; publicKey: unknown } };
      createCertificate(): {
        publicKey: unknown;
        serialNumber: string;
        validity: { notBefore: Date; notAfter: Date };
        setSubject(attrs: Array<{ name: string; value: string }>): void;
        setIssuer(attrs: Array<{ name: string; value: string }>): void;
        sign(key: unknown, md: unknown): void;
      };
    };
    pkcs12: {
      toPkcs12Asn1(key: unknown, certs: unknown[], password: string, options: { algorithm: string; friendlyName: string }): unknown;
    };
    asn1: { toDer(obj: unknown): { getBytes(): string } };
    md: { sha256: { create(): unknown } };
    options: { usePureJavaScript: boolean };
  };
  export default forge;
}
