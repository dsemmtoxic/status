#!/usr/bin/env bash
# Also usable locally with an existing ANDROID_KEYSTORE_FILE.
set -euo pipefail
umask 077
export LC_ALL=C
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

fail() { printf '%s\n' "$*" >&2; exit 1; }
for name in ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
    [[ -n "${!name:-}" ]] || fail "Configure o secret $name."
done

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$sdk" ]] || fail "Defina ANDROID_HOME para o Android SDK."
apksigner="$sdk/build-tools/35.0.0/apksigner"
[[ -x "$apksigner" ]] || fail "Instale build-tools;35.0.0 no Android SDK."

tmp="$(mktemp -d "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/status-signing.XXXXXX")"
trap 'rm -rf -- "$tmp"' EXIT

if [[ -n "${ANDROID_KEYSTORE_BASE64:-}" ]]; then
    [[ -z "${ANDROID_KEYSTORE_FILE:-}" ]] || fail "Use Base64 ou um arquivo de chave por vez."
    printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$tmp/upload.jks" ||
        fail "ANDROID_KEYSTORE_BASE64 inválido."
    export ANDROID_KEYSTORE_FILE="$tmp/upload.jks"
else
    [[ -n "${ANDROID_KEYSTORE_FILE:-}" ]] || fail "Configure o secret ANDROID_KEYSTORE_BASE64."
    export ANDROID_KEYSTORE_FILE="$(realpath -- "$ANDROID_KEYSTORE_FILE")"
fi
unset ANDROID_KEYSTORE_BASE64
[[ -s "$ANDROID_KEYSTORE_FILE" ]] || fail "O arquivo da chave está vazio ou não existe."

keytool -exportcert -storetype JKS -keystore "$ANDROID_KEYSTORE_FILE" \
    -storepass:env ANDROID_KEYSTORE_PASSWORD -alias "$ANDROID_KEY_ALIAS" \
    -file "$tmp/upload.der" > /dev/null 2>&1 ||
    fail "Não foi possível abrir a chave. Confira o arquivo, o alias e a senha do keystore."
expected="$(sha256sum "$tmp/upload.der" | cut -d ' ' -f 1)"

bash gradlew --no-daemon -PstatusRequireSigning :app:bundleRelease :app:assembleRelease

apk=app/build/outputs/apk/release/app-release.apk
aab=app/build/outputs/bundle/release/app-release.aab
[[ -s "$apk" && -s "$aab" ]] || fail "O APK e o AAB de release não foram gerados."

"$apksigner" verify --print-certs "$apk" > "$tmp/apk-signature.txt"
apk_digest="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$tmp/apk-signature.txt")"
[[ "$apk_digest" == "$expected" ]] || fail "A assinatura do APK não corresponde à chave de upload."

jarsigner -verify "$aab" > "$tmp/aab-signature.txt"
grep -qx 'jar verified\.' "$tmp/aab-signature.txt" || fail "A assinatura do AAB não foi verificada."
keytool -printcert -jarfile "$aab" -rfc > "$tmp/aab.pem"
aab_digest="$(openssl x509 -in "$tmp/aab.pem" -outform DER | sha256sum | cut -d ' ' -f 1)"
[[ "$aab_digest" == "$expected" ]] || fail "A assinatura do AAB não corresponde à chave de upload."
printf 'APK e AAB assinados e verificados. SHA-256 do certificado: %s\n' "$expected"
