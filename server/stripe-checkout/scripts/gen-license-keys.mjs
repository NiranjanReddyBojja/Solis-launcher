// Generates the licence signing key pair. Run once: `node scripts/gen-license-keys.mjs`
//   - the private JWK goes to the Worker: `npx wrangler secret put LICENSE_PRIVATE_KEY`
//   - the public key goes to the app build: solis.licensePublicKey=<value> in gradle.properties
// Keep the private key out of git. Losing it means old licences still verify, but you can't
// issue new ones with the same app build.
const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
const jwk = await crypto.subtle.exportKey("jwk", pair.privateKey);
const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
console.log("LICENSE_PRIVATE_KEY (Worker secret, keep private):");
console.log(JSON.stringify(jwk));
console.log("\nsolis.licensePublicKey (app build, safe to share):");
console.log(Buffer.from(spki).toString("base64"));
