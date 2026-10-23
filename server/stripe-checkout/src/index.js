// Solis Pro checkout server (Cloudflare Worker, no dependencies).
//
//   GET /checkout          creates a Stripe Checkout Session and redirects to it
//   GET /success?session_id=cs_...
//                          confirms with Stripe that the session is paid, signs a licence and
//                          links back into the app as solis://pro?license=<licence>
//   GET /cancel            "no charge" page
//
// Secrets (set with `wrangler secret put`, never committed):
//   STRIPE_SECRET_KEY      sk_live_... or sk_test_... (a restricted key with Checkout Sessions
//                          write access is enough)
//   LICENSE_PRIVATE_KEY    JWK printed by scripts/gen-license-keys.mjs
// Plain vars (wrangler.toml):
//   STRIPE_PRICE_ID        price_... for the $4.99 one-time "Solis Pro" price

const PRODUCT = "solis_pro_lifetime";

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "GET") return new Response("Method not allowed", { status: 405 });
    try {
      switch (url.pathname) {
        case "/checkout":
          return await checkout(url, env);
        case "/success":
          return await success(url, env);
        case "/cancel":
          return page("No charge", "<p>Checkout was cancelled. You weren't charged.</p>" +
            '<p><a class="button" href="solis://pro">back to solis</a></p>');
        default:
          return new Response("Not found", { status: 404 });
      }
    } catch (err) {
      console.error(err);
      return page("Something went wrong", "<p>Please try again in a minute.</p>", 500);
    }
  },
};

async function checkout(url, env) {
  const session = await stripe(env, "POST", "/v1/checkout/sessions", {
    mode: "payment",
    "line_items[0][price]": env.STRIPE_PRICE_ID,
    "line_items[0][quantity]": "1",
    success_url: `${url.origin}/success?session_id={CHECKOUT_SESSION_ID}`,
    cancel_url: `${url.origin}/cancel`,
    "metadata[product]": PRODUCT,
  });
  return Response.redirect(session.url, 303);
}

async function success(url, env) {
  const id = url.searchParams.get("session_id") ?? "";
  if (!/^cs_[A-Za-z0-9_]+$/.test(id)) return page("Not found", "<p>Unknown purchase.</p>", 404);

  // Ask Stripe directly; the query string alone proves nothing.
  const session = await stripe(env, "GET", `/v1/checkout/sessions/${id}`);
  if (session.payment_status !== "paid" || session.metadata?.product !== PRODUCT) {
    return page("Payment not finished", "<p>This purchase hasn't been paid yet. " +
      "If you just paid, refresh this page in a moment.</p>", 402);
  }

  const license = await signLicense(env, { v: 1, product: PRODUCT, sid: session.id, iat: session.created });
  const link = `solis://pro?license=${encodeURIComponent(license)}`;
  return page("Thank you", `
    <p>Solis Pro is yours. Tap below on the phone where Solis is installed.</p>
    <p><a class="button" href="${link}">unlock solis pro</a></p>
    <p class="note">Bookmark this page. Opening it again on a new phone restores Pro.</p>`);
}

async function stripe(env, method, path, params) {
  const res = await fetch(`https://api.stripe.com${path}`, {
    method,
    headers: {
      Authorization: `Bearer ${env.STRIPE_SECRET_KEY}`,
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: params ? new URLSearchParams(params) : undefined,
  });
  const body = await res.json();
  if (!res.ok) throw new Error(`Stripe ${res.status}: ${body.error?.message}`);
  return body;
}

// Licence = base64url(payload JSON) "." base64url(ECDSA P-256 SHA-256 signature, raw r||s).
// The app verifies it with the matching public key (LicenseVerifier.kt).
export async function signLicense(env, payload) {
  const key = await crypto.subtle.importKey(
    "jwk", JSON.parse(env.LICENSE_PRIVATE_KEY), { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const head = b64url(new TextEncoder().encode(JSON.stringify(payload)));
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(head));
  return `${head}.${b64url(new Uint8Array(sig))}`;
}

function b64url(bytes) {
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function page(title, body, status = 200) {
  const html = `<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>${title} · solis</title>
<style>
  body { background:#000; color:#fff; font-family:ui-monospace,monospace; max-width:32rem; margin:15vh auto; padding:0 1.5rem; }
  h1 { font-weight:400; font-size:1.6rem; }
  .button { display:inline-block; border:1px solid #fff; color:#fff; padding:.8rem 1.4rem; text-decoration:none; }
  .note { color:#888; font-size:.85rem; }
</style></head><body><h1>${title.toLowerCase()}</h1>${body}</body></html>`;
  return new Response(html, { status, headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" } });
}
