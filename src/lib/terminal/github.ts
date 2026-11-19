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
