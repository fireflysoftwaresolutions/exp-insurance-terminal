# Governed staff underwriting

The staff underwriting API carries referral creation, independent referral approval, and decision recording through the underwriting Domain to its Core owner. Experience performs request handling and bounded delegation. The Core owns the action, referral, decision, audit, and recovery state. This API does not establish a complete customer quote, bind, policy issuance, or claims lifecycle.

## Preserve the original operation

Use the matching generated `StaffUnderwritingApi` from this repository's complete controller-produced OpenAPI document. Do not edit generated Java or create a reduced specification. Configure a separate bearer client for each invocation; never change a shared client's authentication between employees.

1. Create one caller-owned action UUID and retain the intended operation, submission, risk, expected referral version, and any applicable referral or prior receipt identifiers.
2. Call `POST /api/v1/staff/underwriting/intents`. It delegates preparation to Domain without creating the business effect. Retain the returned action identity and opaque intent digest together with the exact original request.
3. Call `POST /api/v1/staff/underwriting/actions` with that original intent and digest. A new action ID would describe a different operation; it is not a retry or recovery mechanism.
4. If a response is lost or incomplete, call `GET /api/v1/staff/underwriting/actions/{actionId}?intentDigest=...` using the same original values and current recovery authority. This is a read, not another mutation.

Prepare, execute, and recover responses use `Cache-Control: no-store`. Generic HTTP idempotency response caching is disabled for these routes because the business owner governs action identity and each request requires current authority.

## Interpret the owner's result

| Result | Meaning and next action |
| --- | --- |
| `COMMITTED` | The returned owner receipt matches the original action, digest, and operation. Retain it. |
| `REFUSED` | The owner returned a typed refusal for that action. Do not report a completed business operation. |
| `COMMITTED_RESPONSE_UNAVAILABLE` | The owner reported a commit but a complete current receipt could not be delivered. Recover the same action; do not submit a replacement. |
| `OUTCOME_UNKNOWN` | The available response cannot establish the write outcome. Recover the same action before deciding what to do next. |

The action endpoint returns HTTP 200 for typed committed/refused results and HTTP 503 for unavailable or unknown results. A platform failure before dispatch may also return the framework error shape documented in the full OpenAPI. HTTP status alone does not identify the business outcome. An empty recovery result does not authorize inventing a replacement action.

The terminal checks returned action, digest, operation, referral, version, and decision identity. It never substitutes the requested outcome for missing owner data. A late authority failure after a known commit preserves the known commit and withholds the receipt instead of rewriting the result as an unexecuted operation.

## Configure the two authorization boundaries

Enable `insurance.terminal.staff-client.enabled` only with an explicit Domain endpoint, issuer, JWKS URI, Experience and Domain audiences, inbound and delegating client IDs, Spring OAuth client registration ID, and platform tenant. The corresponding property names are defined in `StaffUnderwritingSettings`; all are required when enabled. HTTPS is required except explicitly enabled loopback HTTP for a controlled local environment. Disabled or incomplete integration cannot return a synthetic successful receipt.

The Spring OAuth client registration must use token exchange, `client_secret_basic`, the same issuer and delegating client ID, and the required operation scopes. Supply the client secret through deployment configuration. Register the actual issuer metadata/token endpoint; an arbitrary client-credentials token is not a substitute for delegated staff authority. Experience exchanges the original signed staff token for the Domain audience. Domain performs its separately configured delegation to Core.

Mutation scopes are `insurance:underwriting:referrals:create`, `insurance:underwriting:referrals:approve`, or `insurance:underwriting:decisions:record`. Recovery requires both `insurance:underwriting:actions:recover` and `insurance:underwriting:read`. The exchanged token has exactly the requested scope set and retains the original subject, staff actor, platform tenant, and bounded validity. Delegation and the owner call share the original five-second budget; no automatic mutation retry or renewed budget is introduced.

The new underwriting receiver requires a signed `STAFF` principal and preserves the opaque platform namespace. It does not coerce that namespace to an institution UUID. The Core resolves its current governed configuration and business authorization. The legacy terminal receiver's UUID tenant contract remains separate. Native global authentication and current policy remain required; adding a scoped receiver does not replace the global chain or grant business permission.

## Delivery boundary

Source tests exercise real adapters against controlled HTTP/identity fixtures. The complete generated SDK and packaged startup are separate checks. Only an actual Experience → Domain → Core execution with persisted owner effects, refusal checks, and original-action recovery can qualify the integrated operation.

The legacy terminal still contains sample in-memory record and audit adapters. This new staff path does not make those adapters durable or establish whole-platform readiness. Live institution identity, current authorization policies, independent approval configuration, customer insurance lifecycle, and durable replacement of legacy sample adapters remain separately required.
