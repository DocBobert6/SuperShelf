# Shader pack (Mega Bezel, adapted) — publishing

This folder is NOT bundled in the APK. The app downloads
`shaders.json` + `shaders.json.sig` from a dedicated `shaders`
release in this repo and verifies the signature against the
pinned shaders public key in `ShaderPack.kt` before installing.

To publish a new pack (requires the SHADERS_SIGNING_KEY repo
secret / the private key — never commit it):

1. Edit `shaders.json` (preset values only; no upstream shader
   code or bezel artwork is re-hosted here).
2. Sign it: `openssl dgst -sha256 -sign shaders-signing.key -out shaders.json.sig shaders.json`
3. Verify: `openssl dgst -sha256 -verify <pubkey> -signature shaders.json.sig shaders.json`
4. Upload both files to the `shaders` release (create it if needed).

If upstream ever objects to the adapted preset names, or if
re-hosting any upstream asset is required, stop and re-check
the Mega Bezel licence before publishing — the values-only
pack above is the safe v1 form.
