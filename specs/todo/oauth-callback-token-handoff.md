# OAuth callback: hand back the token, not just a cookie

## Problem

`OAuthCallback` (`accounts/src/main/java/org/restheart/accounts/oauth/OAuthCallback.java`)
is the only accounts flow that *must* go through a real top-level browser
redirect (the user has to visit the provider's consent screen — this can't
be a `fetch()` call). Today, on success, it only sets the `rh_auth` cookie
and 307-redirects to `frontendSuccessUrl`:

```java
private void setAuthCookieAndRedirect(StringResponse res, StringRequest req, String jwtToken, String flow) {
    ...
    res.getHeaders().add(HttpString.tryFromString("Set-Cookie"),
        JwtHelper.setCookieHeader(jwtToken, conf.cookieName(),
            RequestOverrides.cookieDomain(req, conf), conf.jwtTtl()));
    res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);
    var location = flow != null ? oauthConfig.frontendSuccessUrl() + "?flow=" + flow : oauthConfig.frontendSuccessUrl();
    res.getHeaders().put(Headers.LOCATION, location);
}
```

`restheart-cloud-kit`'s move to Bearer-token-in-memory session handling
(`restheart-cloud-kit/specs/todo/bearer-token-session.md`) means the SPA no
longer reads a session from a cross-origin cookie at all. Every other
accounts flow already hands the token back in a normal JSON response body
to a kit-initiated `fetch()` — OAuth is the one exception: there's no
`fetch()` call for the kit to read a body from, since the token only
exists after the browser has already been redirected back from the OAuth
provider.

## Goal

`OAuthCallback` hands the JWT back to the frontend on the success redirect,
in a form the kit can read and hold in memory — in addition to (not instead
of) setting the cookie, so nothing that currently relies on the cookie
breaks.

## Design

This reuses the shared fragment-building helper **already shipped** in
`restheart` 9.5: `org.restheart.security.services.TokenRedirectHelper`
(`security/src/main/java/org/restheart/security/services/TokenRedirectHelper.java`),
introduced alongside `GET /token/redirect` — see
`restheart-website/docs/security/oauth.adoc` ("Redirect-Based
Authentication") for the *why URL fragment, not query parameter*
rationale. `OAuthCallback` doesn't call `/token/redirect` over HTTP (it
already has the JWT from its own internal `jwt.issueToken(...)` call); it
calls `TokenRedirectHelper.appendTokenFragment(...)` directly against its
own existing `oauthConfig.frontendSuccessUrl()` config — no new config
needed here, `OAuthCallback` already has a redirect-target setting, it
just needs to stop hand-rolling the fragment string itself. `accounts`
already depends on `restheart-security`, so no new module dependency is
needed either.

```java
private void setAuthCookieAndRedirect(StringResponse res, StringRequest req, String jwtToken, String flow) {
    if (flow != null) {
        res.getHeaders().add(HttpString.tryFromString("X-OAuth-Flow"), flow);
    }
    res.getHeaders().add(
            HttpString.tryFromString("Set-Cookie"),
            JwtHelper.setCookieHeader(jwtToken, conf.cookieName(),
                    RequestOverrides.cookieDomain(req, conf), conf.jwtTtl()));
    res.setStatusCode(HttpStatus.SC_TEMPORARY_REDIRECT);

    var query = flow != null ? "?flow=" + flow : "";
    var location = TokenRedirectHelper.appendTokenFragment(
            oauthConfig.frontendSuccessUrl() + query, jwtToken, "Bearer", null);
    res.getHeaders().put(Headers.LOCATION, location);
}
```

### Keep setting the cookie

No reason to remove it: dedicated-tier customers (or anyone not yet
migrated to the new kit) may still rely on cookie-based sessions. The
fragment is additive.

### `frontendErrorUrl` — no change needed

Errors don't carry a token, nothing to add there.

## Non-goals

- The fragment-vs-query-param rationale and the shared helper's design —
  already shipped, see `restheart-website/docs/security/oauth.adoc`.
- Changing the Authorization Code flow itself (code exchange between
  RESTHeart and the provider stays server-side, unaffected — only the
  *final* hand-off to the frontend changes).
- Any change to `restheart-cloud-server` or the kit beyond what's already
  described in their own specs — this spec is scoped to
  `OAuthCallback.java`.

## Dependencies

`TokenRedirectHelper` and `GET /token/redirect` — **already shipped**. This
spec is a small consumer of that work: no further blocking dependency.

## Testing plan

- Unit/integration test: successful OAuth callback (new user, existing
  user, invite-acceptance-via-OAuth — all three call sites in
  `OAuthCallback`) redirects with both `Set-Cookie` present (regression)
  and a `#access_token=` fragment containing a JWT that decodes to the
  expected `sub`/`roles`/team claim.
- Verify the fragment never appears in the `Location` header's query
  portion (i.e. it's genuinely after the `#`, not accidentally
  concatenated into the query string built from `?flow=...`).
- Manual test with the kit (`restheart-cloud-starter-ng`, once
  `oauthLogin: true`): full Google/GitHub sign-in round trip lands the user
  authenticated with an in-memory token, no reliance on the cookie.

## Open questions

- Whether `X-OAuth-Flow` should also move to the fragment for consistency,
  or stay a header (it's not sensitive, no strong reason to move it).
