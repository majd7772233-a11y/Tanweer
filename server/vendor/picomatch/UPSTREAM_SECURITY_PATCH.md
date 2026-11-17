# Tanweer picomatch security backport

This vendored CommonJS package is a local, reviewed security backport of upstream picomatch 4.0.7. It is not an official npm release.

- Upstream source: https://github.com/micromatch/picomatch
- Security change: https://github.com/micromatch/picomatch/pull/237
- Upstream commit: 1bf0d04563cb9ce568cf36efc5f6b7187d9911d5
- Advisory: https://github.com/advisories/GHSA-fc5q-39xc-vjpf
- Local package version: 4.0.8+tanweer.1 (4.0.8 build metadata identifies this downstream backport; it is not published upstream).

The upstream change detects a single repeated-extglob branch containing ordinary single-star wildcards, excluding path separators, globstar pairs, extglob/regex groups, alternation and brace syntax. It removes the redundant outer repetition: +(...) becomes a single occurrence and *(...) becomes an optional occurrence. This prevents nested unbounded quantifiers for the affected branch shapes.

Tanweer-specific hardening: capture mode uses the existing literal fallback for the affected risky pattern instead of collapsing it, because that could otherwise change repeated capture semantics. The regression suite covers compiled regex shape, normal matching behavior, capture-mode fallback, and a bounded child-process probe for crafted non-matching inputs.

The Server package declares the reviewed local tarball as a direct development dependency and uses the npm $picomatch override reference so all Vite, Vitest, tinyglobby and fdir consumers resolve the same root package. The checked-in archive is file:vendor/picomatch-4.0.8+tanweer.1.tgz; its integrity is pinned in package-lock.json. The unpacked source remains checked in beside the archive for review. The audit gate remains enabled. Re-evaluate and remove this backport only after an official upstream release is available and independently verified.

Scope note: this addresses GHSA-fc5q-39xc-vjpf in the affected picomatch code path; it is not a claim that every possible ReDoS pattern or every project security issue is eliminated.
