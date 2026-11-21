export type GithubFile = { path: string; content: string };

export type GithubPush = {
  repo: string;
  message: string;
  files: GithubFile[];
};

export function utf8ToBase64(value: string) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

export async function pushGithub(token: string, push: GithubPush): Promise<string> {
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "Content-Type": "application/json",
    "X-GitHub-Api-Version": "2022-11-28",
  };
  const repo = push.repo;
  const existing = await fetch(`https://api.github.com/repos/${repo}`, { headers });
  if (existing.status === 401) throw new Error("Token qəbul edilmədi.");
  if (existing.status === 404) {
    const me = await fetch("https://api.github.com/user", { headers });
    if (me.status === 401) throw new Error("Token qəbul edilmədi.");
    if (!me.ok) throw new Error("Hesab oxunmadı.");
    const user = (await me.json()) as { login?: string };
    const [owner, name] = repo.split("/");
    if (!user.login || user.login !== owner) throw new Error("Bu depo yoxdur.");
    const created = await fetch("https://api.github.com/user/repos", {
      method: "POST",
      headers,
      body: JSON.stringify({ name, private: true, auto_init: true, description: "Nibras Terminal" }),
    });
    if (created.status === 401) throw new Error("Token qəbul edilmədi.");
    if (!created.ok) {
      const err = (await created.json().catch(() => ({}))) as { message?: string };
      throw new Error(err.message || "Depo yaradılmadı. Tokenə repo icazəsi lazımdır.");
    }
  } else if (!existing.ok) {
    const err = (await existing.json().catch(() => ({}))) as { message?: string };
    throw new Error(err.message || "Depo açılmadı.");
  }

  let sent = 0;
  for (const file of push.files) {
    const url = `https://api.github.com/repos/${repo}/contents/${file.path.split("/").map(encodeURIComponent).join("/")}`;
    const prior = await fetch(url, { headers });
    let sha: string | undefined;
    if (prior.ok) {
      const body = (await prior.json()) as { sha?: string };
      sha = body.sha;
    } else if (prior.status !== 404) {
      if (prior.status === 401) throw new Error("Token qəbul edilmədi.");
      throw new Error(`${file.path} oxunmadı`);
    }
    const put = await fetch(url, {
      method: "PUT",
      headers,
      body: JSON.stringify({
        message: push.message,
        content: utf8ToBase64(file.content),
        sha,
      }),
    });
    if (put.status === 401) throw new Error("Token qəbul edilmədi.");
    if (!put.ok) {
      const err = (await put.json().catch(() => ({}))) as { message?: string };
      throw new Error(err.message || `${file.path} göndərilmədi`);
    }
    sent += 1;
  }
  return `${sent} fayl göndərildi · https://github.com/${repo}`;
}

export async function cloneGithub(repo: string, token: string): Promise<{ files: GithubFile[]; cut: boolean }> {
  if (!/^[\w.-]+\/[\w.-]+$/.test(repo)) throw new Error("depo belə yazılır: sahib/ad");
  const headers: Record<string, string> = {
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
    "User-Agent": "nibras-terminal",
  };
  if (token) headers.Authorization = `Bearer ${token}`;
  const treeRes = await fetch(`https://api.github.com/repos/${repo}/git/trees/HEAD?recursive=1`, { headers });
  if (treeRes.status === 401 || treeRes.status === 403) throw new Error("Token qəbul edilmədi.");
  if (treeRes.status === 404) throw new Error("Depo açılmadı.");
  if (!treeRes.ok) throw new Error("Depo oxunmadı.");
  const tree = (await treeRes.json()) as { tree?: Array<{ path?: string; type?: string; size?: number }> };
  const blobs = (tree.tree ?? []).filter((item) => item.type === "blob" && item.path && !item.path.includes("..") && (item.size ?? 0) > 0 && (item.size ?? 0) <= 100_000);
  const picked = blobs.slice(0, 40);
  const files: GithubFile[] = [];
  for (const item of picked) {
    const path = item.path ?? "";
    const url = `https://api.github.com/repos/${repo}/contents/${path.split("/").map(encodeURIComponent).join("/")}`;
    const res = await fetch(url, { headers: { ...headers, Accept: "application/vnd.github.raw" } });
    if (res.status === 401 || res.status === 403) throw new Error("Token qəbul edilmədi.");
    if (!res.ok) continue;
    const content = await res.text();
    if (!content || content.includes("\0") || content.length > 100_000) continue;
    files.push({ path, content });
  }
  if (!files.length) throw new Error("mətn faylı yoxdur");
  return { files, cut: blobs.length > 40 };
}

export async function probeHost(host: string) {
  const started = performance.now();
  let address = "";
  try {
    const response = await fetch(`https://cloudflare-dns.com/dns-query?name=${encodeURIComponent(host)}&type=A`, {
      headers: { Accept: "application/dns-json" },
    });
    const body = (await response.json()) as { Answer?: Array<{ type?: number; data?: string }> };
    address = body.Answer?.find((item) => item.type === 1)?.data ?? "";
  } catch {
    address = "";
  }
  if (address && isPrivateAddress(address)) return "bu ünvan bağlıdır";
  let status = "https açılmadı";
  try {
    const response = await fetch(`https://${host}`, { method: "GET", redirect: "manual", signal: AbortSignal.timeout(5000) });
    status = `https ${response.status}`;
  } catch {
    status = "https açılmadı";
  }
  const ms = Math.max(0, Math.round(performance.now() - started));
  return [host, address ? `ünvan: ${address}` : "ad tapılmadı", `${status} · ${ms} ms`].join("\n");
}

function isPrivateAddress(host: string) {
  const parts = host.split(".").map(Number);
  if (parts.length !== 4 || parts.some((part) => !Number.isInteger(part))) return false;
  const [a, b] = parts;
  if (a === 0 || a === 10 || a === 127) return true;
  if (a === 169 && b === 254) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  return false;
}

const APK_REPO = "nibrascode/nibras-terminal";

export async function runApk(
  token: string,
  verb: "qur" | "status" | "endir",
  sdk: "35" | "34" | "33" = "35",
  kind: "apk" | "aab" | "sign" = "apk",
) {
  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
  };
  const tools = sdk === "33" ? "build-tools 33.0.2" : sdk === "34" ? "build-tools 34.0.0" : "build-tools 35.0.0";
  const command = kind === "sign" ? "nibras sign" : `nibras ${kind}`;
  if (verb === "qur") {
    if (kind === "sign") await ensureReleaseKey(token, headers);
    const sent = await fetch(`https://api.github.com/repos/${APK_REPO}/actions/workflows/debug-apk.yml/dispatches`, {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/json" },
      body: JSON.stringify({ ref: "main", inputs: { sdk, kind } }),
    });
    if (sent.status === 401) throw new Error("Token qəbul edilmədi.");
    if (sent.status !== 204) throw new Error(await githubError(sent));
    const doing = kind === "sign" ? "Signing the release APK and AAB." : kind === "aab" ? "Building the AAB." : "Building the APK.";
    return { text: `${doing}\nJava 17 · Android SDK ${sdk} · ${tools} · Gradle\nversionCode increases on every build.\n${command} status` };
  }
  const listed = await fetch(`https://api.github.com/repos/${APK_REPO}/actions/workflows/debug-apk.yml/runs?per_page=10`, { headers });
  if (listed.status === 401) throw new Error("Token qəbul edilmədi.");
  if (!listed.ok) throw new Error(await githubError(listed));
  const body = (await listed.json()) as { workflow_runs?: Array<{ name?: string; run_number?: number; status: string; conclusion: string | null; html_url: string; artifacts_url: string }> };
  const run = body.workflow_runs?.find((item) => matchKind(item.name || "", kind));
  if (!run) return { text: `No build yet. ${command}${kind === "sign" ? "" : " build"}` };
  if (run.status !== "completed") return { text: `${run.status}\n${run.html_url}` };
  if (run.conclusion !== "success") return { text: `failed: ${run.conclusion ?? "error"}\n${run.html_url}` };
  const code = run.run_number ? String(100 + run.run_number) : "";
  if (verb === "status") return { text: `ready${code ? `\nversionCode ${code}` : ""}\n${run.html_url}\ndownload: ${command} download` };
  const arts = await fetch(run.artifacts_url, { headers });
  if (!arts.ok) throw new Error(await githubError(arts));
  const packed = (await arts.json()) as { artifacts?: Array<{ name: string; archive_download_url: string }> };
  const wanted = `nibras-${kind}-${sdk}`;
  const file = packed.artifacts?.find((item) => item.name === wanted) ?? packed.artifacts?.[0];
  if (!file) return { text: `ready, file was not found\n${run.html_url}` };
  const zip = await fetch(file.archive_download_url, { headers });
  if (zip.status === 401) throw new Error("Token qəbul edilmədi.");
  if (!zip.ok) throw new Error(await githubError(zip));
  const bytes = await zip.arrayBuffer();
  const packedFiles = await unzip(bytes);
  const version = new TextDecoder().decode(packedFiles.get("version.txt") ?? new Uint8Array()).trim() || code;
  const files = packageFiles(kind, packedFiles, version);
  if (!files.length) return { text: `ready, file was not found\n${run.html_url}` };
  const versionLine = version ? `\nversionCode ${version}` : "";
  const text =
    kind === "sign"
      ? `Signed release APK and AAB downloaded.${versionLine}\nThe same release key was used. The password is not shown.`
      : kind === "aab"
        ? `AAB downloaded.${versionLine}\nSigned with the Android debug key.`
        : `APK downloaded. Open it on the phone to install.${versionLine}\nSigned with the Android debug key.`;
  return { text, files };
}

export function packageFiles(kind: "apk" | "aab" | "sign", files: Map<string, Uint8Array>, version: string) {
  const stamp = version || "0";
  const apkType = "application/vnd.android.package-archive";
  if (kind === "sign") {
    const apk = named(files, ".apk");
    const aab = named(files, ".aab");
    const out: Array<{ name: string; bytes: Uint8Array; type: string }> = [];
    if (apk) out.push({ name: `nibras-terminal-${stamp}.apk`, bytes: apk, type: apkType });
    if (aab) out.push({ name: `nibras-terminal-${stamp}.aab`, bytes: aab, type: "application/octet-stream" });
    return out;
  }
  if (kind === "aab") {
    const aab = named(files, ".aab");
    return aab ? [{ name: `nibras-terminal-${stamp}.aab`, bytes: aab, type: "application/octet-stream" }] : [];
  }
  const apk = named(files, ".apk");
  return apk ? [{ name: `nibras-terminal-${stamp}.apk`, bytes: apk, type: apkType }] : [];
}

function named(files: Map<string, Uint8Array>, suffix: string) {
  for (const [name, bytes] of files) if (name.endsWith(suffix)) return bytes;
  return undefined;
}

function matchKind(name: string, kind: "apk" | "aab" | "sign") {
  if (kind === "apk") return name.startsWith("apk ") || name === "Debug APK" || name === "Android package";
  return name.startsWith(`${kind} `);
}

async function githubError(response: Response) {
  const body = (await response.json().catch(() => ({}))) as { message?: string };
  return body.message || `qurulmadı: ${response.status}`;
}

const SECRET_NAMES = ["NIBRAS_KEYSTORE_B64", "NIBRAS_STORE_PASSWORD", "NIBRAS_KEY_PASSWORD", "NIBRAS_KEY_ALIAS"] as const;

async function ensureReleaseKey(token: string, headers: Record<string, string>) {
  const listed = await fetch(`https://api.github.com/repos/${APK_REPO}/actions/secrets`, { headers });
  if (listed.status === 401) throw new Error("Token qəbul edilmədi.");
  if (!listed.ok) throw new Error("Release key was not saved.");
  const body = (await listed.json()) as { secrets?: Array<{ name: string }> };
  const names = new Set((body.secrets ?? []).map((item) => item.name));
  if (SECRET_NAMES.every((name) => names.has(name))) return;
  const made = await createReleaseKey();
  await putSecret(token, headers, "NIBRAS_KEY_ALIAS", made.alias);
  await putSecret(token, headers, "NIBRAS_STORE_PASSWORD", made.password);
  await putSecret(token, headers, "NIBRAS_KEY_PASSWORD", made.password);
  await putSecret(token, headers, "NIBRAS_KEYSTORE_B64", made.p12);
}

async function putSecret(token: string, headers: Record<string, string>, name: string, value: string) {
  const keyRes = await fetch(`https://api.github.com/repos/${APK_REPO}/actions/secrets/public-key`, { headers });
  if (keyRes.status === 401) throw new Error("Token qəbul edilmədi.");
  if (!keyRes.ok) throw new Error("Release key was not saved.");
  const key = (await keyRes.json()) as { key_id: string; key: string };
  const sodium = (await import("libsodium-wrappers")).default;
  await sodium.ready;
  const sealed = sodium.crypto_box_seal(value, sodium.from_base64(key.key, sodium.base64_variants.ORIGINAL));
  const saved = await fetch(`https://api.github.com/repos/${APK_REPO}/actions/secrets/${name}`, {
    method: "PUT",
    headers: { ...headers, "Content-Type": "application/json" },
    body: JSON.stringify({ encrypted_value: sodium.to_base64(sealed, sodium.base64_variants.ORIGINAL), key_id: key.key_id }),
  });
  if (saved.status === 401) throw new Error("Token qəbul edilmədi.");
  if (!saved.ok) throw new Error("Release key was not saved.");
}

export async function createReleaseKey() {
  const forge = (await import("node-forge")).default;
  forge.options.usePureJavaScript = true;
  const keys = forge.pki.rsa.generateKeyPair(2048);
  const cert = forge.pki.createCertificate();
  cert.publicKey = keys.publicKey;
  cert.serialNumber = "01";
  cert.validity.notBefore = new Date();
  cert.validity.notAfter = new Date();
  cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 27);
  const attrs = [
    { name: "commonName", value: "Nibras Terminal" },
    { name: "countryName", value: "AZ" },
  ];
  cert.setSubject(attrs);
  cert.setIssuer(attrs);
  cert.sign(keys.privateKey, forge.md.sha256.create());
  const password = randomPassword();
  const p12 = forge.pkcs12.toPkcs12Asn1(keys.privateKey, [cert], password, { algorithm: "aes256", friendlyName: "nibras" });
  return { alias: "nibras", password, p12: btoa(forge.asn1.toDer(p12).getBytes()) };
}

function randomPassword() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return [...bytes].map((byte) => alphabet[byte % alphabet.length]).join("");
}

export async function unzip(buffer: ArrayBuffer) {
  const bytes = new Uint8Array(buffer);
  const view = new DataView(buffer);
  let eocd = -1;
  const start = Math.max(0, bytes.length - 22 - 65535);
  for (let index = bytes.length - 22; index >= start; index -= 1) {
    if (view.getUint32(index, true) === 0x06054b50) {
      eocd = index;
      break;
    }
  }
  const out = new Map<string, Uint8Array>();
  if (eocd < 0) return out;
  const count = view.getUint16(eocd + 10, true);
  let offset = view.getUint32(eocd + 16, true);
  for (let entry = 0; entry < count; entry += 1) {
    if (offset + 46 > bytes.length || view.getUint32(offset, true) !== 0x02014b50) break;
    const method = view.getUint16(offset + 10, true);
    const compressed = view.getUint32(offset + 20, true);
    const nameLen = view.getUint16(offset + 28, true);
    const extraLen = view.getUint16(offset + 30, true);
    const commentLen = view.getUint16(offset + 32, true);
    const local = view.getUint32(offset + 42, true);
    const name = new TextDecoder().decode(bytes.subarray(offset + 46, offset + 46 + nameLen));
    const localName = view.getUint16(local + 26, true);
    const localExtra = view.getUint16(local + 28, true);
    const dataStart = local + 30 + localName + localExtra;
    const data = bytes.subarray(dataStart, dataStart + compressed);
    const base = name.split("/").pop() || name;
    if (base && !name.endsWith("/")) {
      if (method === 0) out.set(base, data.slice());
      else if (method === 8) out.set(base, await inflateRaw(data));
    }
    offset += 46 + nameLen + extraLen + commentLen;
  }
  return out;
}

async function inflateRaw(data: Uint8Array) {
  const stream = new DecompressionStream("deflate-raw");
  const compressed = new Uint8Array(data.byteLength);
  compressed.set(data);
  const writer = stream.writable.getWriter();
  await writer.write(compressed);
  await writer.close();
  const reader = stream.readable.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const step = await reader.read();
    if (step.done) break;
    chunks.push(step.value);
    total += step.value.byteLength;
  }
  const out = new Uint8Array(total);
  let at = 0;
  for (const chunk of chunks) {
    out.set(chunk, at);
    at += chunk.byteLength;
  }
  return out;
}
