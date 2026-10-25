# 0009. Same-origin portal behind an Nginx edge

**Status:** Accepted (added during implementation)

## Context
A browser calling the API on another origin needs CORS configured on the API, and every extra
allowed origin is attack surface. Actuator endpoints must not be public.

## Decision
The portal is served by Nginx, which also forwards only `/api/` to the Claims API. In development
the Vite dev server proxies `/api` the same way. The edge limits request bodies to 64 KB, sets a
safe `X-Request-Id` when the caller has not, and adds security headers (CSP, frame denial, nosniff).

## Alternatives
- Call the API directly and configure CORS: works, but needs a per-environment origin list on the API.
- An API gateway product: heavier than the problem.

## Consequences
- No CORS configuration anywhere, and one origin to secure. `/actuator` is unreachable through the edge.
- In production a CDN or load balancer with a WAF plays the same role.
