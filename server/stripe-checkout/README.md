# Solis Pro checkout server (Stripe)

Takes payment for Solis Pro in the **direct** build (the APK you distribute yourself, outside
Google Play). The Play build keeps Google Play Billing, because Play's payments policy requires
it for digital unlocks sold in Play-distributed apps.

It is one dependency-free Cloudflare Worker (`src/index.js`). The free plan is plenty.

```
app "unlock"  ──►  GET /checkout  ──303──►  Stripe Checkout (card, Apple/Google Pay…)
                                                   │ paid
solis://pro?license=…  ◄──  GET /success?session_id=…  ◄──┘
   (app verifies the signature offline and unlocks Pro)
```

`/success` asks Stripe whether the session is really paid before it signs anything, so a forged
URL gets nothing. Licences are ECDSA P-256 signatures; the app holds only the public key.

## Keys: what goes where

| Value | Where it lives | Secret? |
|---|---|---|
| Stripe secret key `sk_live_…` | Worker secret `STRIPE_SECRET_KEY` | **yes** |
| Licence private key (JWK) | Worker secret `LICENSE_PRIVATE_KEY` | **yes** |
| Stripe price ID `price_…` | `wrangler.toml` `STRIPE_PRICE_ID` | no |
| Licence public key | `solis.licensePublicKey` Gradle property | no |
| Worker URL | `solis.checkoutUrl` Gradle property | no |

Never commit the two secrets or put them in the app.

## Setup

1. **Stripe**: in the dashboard, Product catalog → add product "Solis Pro", one-time price $4.99.
   Copy the price ID into `wrangler.toml`. Under Developers → API keys, create a restricted key
   with *Checkout Sessions: Write* (or use the secret key). Start with test mode keys.
2. **Licence keys**: `node scripts/gen-license-keys.mjs` (Node 18+). Keep the output somewhere
   safe, such as a password manager.
3. **Deploy**:
   ```
   npx wrangler login
   npx wrangler secret put STRIPE_SECRET_KEY     # paste sk_test_… / sk_live_…
   npx wrangler secret put LICENSE_PRIVATE_KEY   # paste the JWK line
   npx wrangler deploy                           # prints https://solis-checkout.<you>.workers.dev
   ```
4. **App**: add to `%USERPROFILE%\.gradle\gradle.properties` (not the repo):
   ```
   solis.checkoutUrl=https://solis-checkout.<you>.workers.dev
   solis.licensePublicKey=<public key from step 2>
   ```
   then `gradlew :app:assembleDirectRelease` (or `assembleDirectDebug`).
5. **Test** with Stripe test card `4242 4242 4242 4242`, any future date and CVC. Switch to
   live keys when it works.

For local runs, put the two secrets in `.dev.vars` (git-ignored) and use `npx wrangler dev`.

## Restore and limits

- The success page is the receipt: opening it again on a new phone restores Pro. Stripe's
  emailed receipt does not contain it, so the page tells buyers to bookmark it.
- A licence isn't tied to one device, so a buyer could share their link. For a $4.99 unlock
  this is the usual trade-off; tying licences to accounts would need a database and sign-in.
- Refunds made in Stripe don't revoke a licence already issued.
