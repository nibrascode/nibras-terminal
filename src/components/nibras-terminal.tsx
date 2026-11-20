import { useEffect, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { cloneGithub, probeHost, pushGithub, utf8ToBase64 } from "@/lib/terminal/github";
import { runRemote } from "@/lib/terminal/remote.functions";
import {
  LANGS,
  clipText,
  deleteAsk,
  isLang,
  linesLabel,
  loadLang,
  renameLabel,
  say,
  type Lang,
} from "@/lib/terminal/i18n";
import {
  ACTIONS,
  applyAnswer,
  emptySession,
  browserReady,
  extraKeyRows,
  formatBytes,
  hydrate,
  importRepo,
  saveRemoteFile,
  isMarked,
  listHere,
  packHere,
  runLine,
  runNode,
  storeZip,
  complete,
  usedBytes,
  visibleCommand,
  writeKey,
  DISK_BUDGET,
  type Ask,
  type Session,
  type ShellOut,
} from "@/lib/terminal/shell";

type Tab = "term" | "pick" | "file";
type Line = { kind: "in" | "out" | "err"; text: string };
type Repl = "python" | "node" | null;
type Slot = { name: string; session: Session; lines: Line[] };

const STORE = "nibras-terminal-session";
const LINES = "nibras-terminal-lines";
const TOKEN_KEY = "nibras-terminal-token";
const SSH_KEY = "nibras-terminal-ssh";
const SIZE_KEY = "nibras-terminal-size";
const LANG_KEY = "nibras-terminal-lang";

const MOTD = `| \\ | (_) |__  _ __ __ _ ___ 
|  \\| | | '_ \\| '__/ _\` / __|
| |\\  | | |_) | | | (_| \\__ \\
|_| \\_|_|_.__/|_|  \\__,_|___/

Welcome to Nibras Terminal!

Community:  https://github.com/nibrascode/nibras-terminal

Setup Nibras:
- Help: help

Working with packages:
- Phone files: nibras-setup-storage
- Python: pip install <paket>
- Run: python3 fayl.py
- Search: pkg search <query>
- Install: pkg install <package>
- Preview: preview fayl.md
- Hosts: host add ad user@host
- Backup: nibras-backup
- Check: nibras-doctor

Report issues at https://github.com/nibrascode/nibras-terminal/issues`;

const GREETING = "Welcome to Nibras Terminal";
const BOOT: Line[] = [{ kind: "out", text: MOTD }];

const PHONE_KEYS = [
  [
    ["ESC", "ESC"],
    ["/", "/"],
    ["-", "—"],
    ["HOME", "HOME"],
    ["UP", "↑"],
    ["END", "END"],
    ["PGUP", "PGUP"],
  ],
  [
    ["=", "="],
    ["CTRL", "CTRL"],
    ["ALT", "ALT"],
    ["LEFT", "←"],
    ["DOWN", "↓"],
    ["RIGHT", "→"],
    ["PGDN", "PGDN"],
  ],
] as const;

declare global {
  interface Window {
    loadPyodide?: (opts: { indexURL: string }) => Promise<Pyodide>;
  }
}

type Pyodide = {
  runPythonAsync: (code: string) => Promise<unknown>;
  loadPackage?: (name: string) => Promise<void>;
  pyimport?: (name: string) => { install: (name: string) => Promise<void> };
  setStdout: (opts: { batched: (msg: string) => void }) => void;
  setStderr: (opts: { batched: (msg: string) => void }) => void;
  FS: {
    writeFile: (path: string, data: string) => void;
    mkdirTree: (path: string) => void;
    readdir: (path: string) => string[];
    readFile: (path: string, opts: { encoding: string }) => string;
    stat: (path: string) => { mode: number };
    isDir: (mode: number) => boolean;
  };
};

type PhpBox = { run: (code: string) => Promise<unknown>; take: () => string };

let pyodidePromise: Promise<Pyodide> | null = null;
let phpPromise: Promise<PhpBox> | null = null;

function loadPython() {
  if (!pyodidePromise) {
    pyodidePromise = new Promise((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://cdn.jsdelivr.net/pyodide/v0.27.5/full/pyodide.js";
      script.onload = () => {
        const loader = window.loadPyodide;
        if (!loader) {
          pyodidePromise = null;
          reject(new Error("Python açılmadı"));
          return;
        }
        loader({ indexURL: "https://cdn.jsdelivr.net/pyodide/v0.27.5/full/" }).then(resolve, (error) => {
          pyodidePromise = null;
          reject(error);
        });
      };
      script.onerror = () => {
        pyodidePromise = null;
        reject(new Error("Python arxivi gəlmədi"));
      };
      document.head.appendChild(script);
    });
  }
  return pyodidePromise;
}

function loadPhp() {
  if (!phpPromise) {
    phpPromise = (async () => {
      const importer = new Function("url", "return import(url)") as (url: string) => Promise<{ PhpWeb: new () => PhpLike }>;
      const mod = await importer("https://cdn.jsdelivr.net/npm/php-wasm@0.2.0/PhpWeb.mjs");
      const php = new mod.PhpWeb();
      const chunks: string[] = [];
      php.addEventListener("output", (event) => chunks.push(phpChunk(event.detail)));
      php.addEventListener("error", (event) => chunks.push(phpChunk(event.detail)));
      return {
        run: async (code: string) => {
          chunks.length = 0;
          return php.run(code);
        },
        take: () => chunks.join(""),
      };
    })().catch((error) => {
      phpPromise = null;
      throw error;
    });
  }
  return phpPromise;
}

type PhpLike = {
  run: (code: string) => Promise<unknown>;
  addEventListener: (name: string, cb: (event: { detail: unknown }) => void) => void;
};

function phpChunk(detail: unknown) {
  if (typeof detail === "string") return detail;
  if (Array.isArray(detail)) return detail.map((item) => (typeof item === "number" ? String.fromCharCode(item) : String(item))).join("");
  return detail == null ? "" : String(detail);
}

function phpLiteral(value: string) {
  return `'${value.replace(/\\/g, "\\\\").replace(/'/g, "\\'")}'`;
}

async function pullText(url: string) {
  const parsed = new URL(url);
  const hosts = new Set(["github.com", "raw.githubusercontent.com", "gist.githubusercontent.com", "cdn.jsdelivr.net", "objects.githubusercontent.com"]);
  if (parsed.protocol !== "https:" || !hosts.has(parsed.hostname)) throw new Error("ünvan icazəli deyil");
  const response = await fetch(url);
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  const bytes = await response.arrayBuffer();
  if (bytes.byteLength > 262_144) throw new Error("256 KB-dan böyükdür");
  const text = new TextDecoder().decode(bytes);
  if (text.includes("\0")) throw new Error("mətn faylı deyil");
  return text;
}

function phpSource(session: Session, job: { code: string; file?: string; version?: boolean }) {
  if (job.version) return "<?php echo PHP_VERSION;";
  if (!job.file) {
    const code = job.code.trim();
    return code.includes("<?") ? code : `<?php ${code}`;
  }
  const lines = ["<?php"];
  for (const [path, body] of Object.entries(session.files)) {
    const dir = path.slice(0, path.lastIndexOf("/")) || "/";
    lines.push(`@mkdir(${phpLiteral(dir)}, 0777, true);`);
    lines.push(`file_put_contents(${phpLiteral(path)}, base64_decode(${phpLiteral(utf8ToBase64(body))}));`);
  }
  const folder = job.file.slice(0, job.file.lastIndexOf("/")) || "/";
  lines.push(`chdir(${phpLiteral(folder)});`);
  lines.push(`include ${phpLiteral(job.file)};`);
  return lines.join("\n");
}

function pullPython(py: Pyodide, session: Session) {
  const next = structuredClone(session);
  const walk = (dir: string) => {
    let names: string[] = [];
    try {
      names = py.FS.readdir(dir);
    } catch {
      return;
    }
    for (const name of names) {
      if (name === "." || name === "..") continue;
      const path = dir === "/" ? `/${name}` : `${dir}/${name}`;
      if (!path.startsWith("/root")) continue;
      let directory = false;
      try {
        directory = py.FS.isDir(py.FS.stat(path).mode);
      } catch {
        continue;
      }
      if (directory) {
        if (!next.dirs.includes(path)) next.dirs.push(path);
        walk(path);
      } else {
        try {
          const data = py.FS.readFile(path, { encoding: "utf8" });
          if (data.length < 200_000) next.files[path] = data;
        } catch {
          /* binary */
        }
      }
    }
  };
  walk("/root");
  return next;
}

async function execPython(py: Pyodide, session: Session, job: { code: string; file?: string; argv?: string[] }) {
  for (const dir of session.dirs) {
    try {
      py.FS.mkdirTree(dir);
    } catch {
      /* already there */
    }
  }
  for (const [path, body] of Object.entries(session.files)) {
    const folder = path.slice(0, path.lastIndexOf("/")) || "/";
    try {
      py.FS.mkdirTree(folder);
      py.FS.writeFile(path, body);
    } catch {
      /* skip */
    }
  }
  const logs: string[] = [];
  py.setStdout({ batched: (msg) => logs.push(msg) });
  py.setStderr({ batched: (msg) => logs.push(msg) });
  let value: unknown;
  if (job.file) {
    await py.runPythonAsync(
      `import runpy, sys\nsys.argv = ${JSON.stringify(job.argv ?? [job.file])}\nrunpy.run_path(${JSON.stringify(job.file)}, run_name="__main__")\n`,
    );
  } else value = await py.runPythonAsync(job.code);
  const extra = job.file || value == null ? "" : String(value);
  return { session: pullPython(py, session), output: [logs.join("\n"), extra].filter(Boolean).join("\n") };
}

function loadSession() {
  try {
    const parsed = hydrate(JSON.parse(localStorage.getItem(STORE) || "null"));
    parsed.hasToken = Boolean(sessionStorage.getItem(TOKEN_KEY));
    parsed.hasSsh = Boolean(sessionStorage.getItem(SSH_KEY));
    return parsed;
  } catch {
    return emptySession();
  }
}

function loadLines(): Line[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(LINES) || "null") as Line[] | null;
    if (!Array.isArray(parsed) || !parsed.length) return BOOT;
    return parsed
      .filter((line) => line && (line.kind === "in" || line.kind === "out" || line.kind === "err") && typeof line.text === "string")
      .slice(-80)
      .map((line) => ({ ...line, text: clipText(line.text) }));
  } catch {
    return BOOT;
  }
}

export function NibrasTerminal() {
  const [session, setSession] = useState<Session>(emptySession);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>("term");
  const [lines, setLines] = useState<Line[]>(BOOT);
  const [slots, setSlots] = useState<Slot[]>([]);
  const [slot, setSlot] = useState(0);
  const [mods, setMods] = useState({ ctrl: false, alt: false });
  const [draft, setDraft] = useState("");
  const [cursor, setCursor] = useState(-1);
  const [ask, setAsk] = useState<Ask | null>(null);
  const [field, setField] = useState("");
  const [editing, setEditing] = useState("/root/README.txt");
  const [busy, setBusy] = useState("");
  const [repl, setRepl] = useState<Repl>(null);
  const [filter, setFilter] = useState("");
  const [copied, setCopied] = useState("");
  const [past, setPast] = useState(false);
  const [hunt, setHunt] = useState("");
  const [mark, setMark] = useState("");
  const [bell, setBell] = useState(false);
  const [favs, setFavs] = useState<string[]>([]);
  const [now, setNow] = useState(() => Date.now());
  const [scriptOpen, setScriptOpen] = useState(false);
  const [scriptText, setScriptText] = useState("echo bir\necho iki");
  const [needle, setNeedle] = useState("");
  const [drawer, setDrawer] = useState(false);
  const [palette, setPalette] = useState(false);
  const [pq, setPq] = useState("");
  const [bright, setBright] = useState(100);
  const live = useRef(session);
  const [findOn, setFindOn] = useState(false);
  const [size, setSize] = useState(1);
  const [lang, setLang] = useState<Lang>("az");
  const [langBusy, setLangBusy] = useState<Lang | "">("");
  const scroller = useRef<HTMLDivElement>(null);
  const pinch = useRef<{ dist: number; size: number } | null>(null);
  const box = useRef<HTMLInputElement>(null);
  const nodeScope = useRef<Record<string, unknown>>({});

  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1000);
    try {
      const saved = JSON.parse(localStorage.getItem("nibras.favs") || "[]") as unknown;
      if (Array.isArray(saved)) setFavs(saved.filter((item) => typeof item === "string").slice(0, 24));
    } catch {
      setFavs([]);
    }
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    const loaded = browserReady(loadSession());
    setSession(loaded);
    const stored = loadLines();
    const stock = !stored.length || (stored.length <= 4 && stored.some((line) => line.text.includes(GREETING) || line.text.includes("screenfetch") || line.text.includes("#     #") || line.text.includes("Welcome to Nibras")));
    setLines(stock ? [{ kind: "out", text: MOTD }] : stored);
    const first = Object.keys(loaded.files).sort()[0];
    if (first) setEditing(first);
    const savedSize = Number(localStorage.getItem(SIZE_KEY));
    if (savedSize === 0 || savedSize === 2) setSize(savedSize);
    setReady(true);
    const savedLang = localStorage.getItem(LANG_KEY);
    if (isLang(savedLang) && savedLang !== "az") {
      setLangBusy(savedLang);
      void loadLang(savedLang).then(
        () => {
          setLang(savedLang);
          setLangBusy("");
        },
        () => setLangBusy(""),
      );
    }
  }, []);

  useEffect(() => {
    if (!ready) return;
    try {
      const { hasToken, hasSsh, ...rest } = session;
      localStorage.setItem(STORE, JSON.stringify({ ...rest, hasToken: false, hasSsh: false }));
      void hasToken;
      void hasSsh;
    } catch {
      /* storage full */
    }
  }, [session, ready]);

  useEffect(() => {
    if (!ready) return;
    try {
      localStorage.setItem(LINES, JSON.stringify(lines.slice(-80)));
    } catch {
      /* storage full */
    }
  }, [lines, ready]);

  useEffect(() => {
    if (!ready) return;
    setSlots((list) => {
      if (!list.length) return [{ name: "1", session, lines }];
      return list.map((item, index) => (index === slot ? { ...item, session, lines } : item));
    });
  }, [ready, session, lines, slot]);

  useEffect(() => {
    live.current = session;
  }, [session]);

  useEffect(() => {
    if (!ready) return;
    const rules = (session.files[`${session.home || "/root"}/.nibras/when`] ?? "")
      .split("\n")
      .filter(Boolean)
      .map((row) => {
        const cut = row.indexOf("\t");
        return { event: cut < 0 ? "" : row.slice(0, cut), command: cut < 0 ? "" : row.slice(cut + 1) };
      })
      .filter((row) => row.command);
    const fire = (event: string) => {
      for (const rule of rules) {
        if (rule.event === event) void apply(runLine(live.current, rule.command));
      }
    };
    const on = () => fire("online");
    const off = () => fire("offline");
    window.addEventListener("online", on);
    window.addEventListener("offline", off);
    let dead = false;
    const nav = navigator as Navigator & { getBattery?: () => Promise<{ level: number; addEventListener: (name: string, fn: () => void) => void }> };
    void nav.getBattery?.().then((bat) => {
      if (dead) return;
      bat.addEventListener("levelchange", () => {
        if (bat.level < 0.2) fire("battery");
      });
    });
    return () => {
      dead = true;
      window.removeEventListener("online", on);
      window.removeEventListener("offline", off);
    };
  }, [ready, session.files, session.home]);

  useEffect(() => {
    if (!ready) return;
    const rows = (session.files[`${session.home || "/root"}/.nibras/jobs`] ?? "")
      .split("\n")
      .filter(Boolean)
      .map((row) => {
        const [id, period, ...rest] = row.split("\t");
        return { id, period: Number(period), command: rest.join("\t") };
      })
      .filter((row) => row.period >= 15000 && row.command);
    const timers = rows.map((row) =>
      window.setInterval(() => {
        void apply(runLine(live.current, row.command));
      }, row.period),
    );
    return () => timers.forEach((timer) => window.clearInterval(timer));
  }, [ready, session.files, session.home]);

  useEffect(() => {
    if (!ready) return;
    try {
      localStorage.setItem(SIZE_KEY, String(size));
    } catch {
      /* storage full */
    }
  }, [size, ready]);

  useEffect(() => {
    if (!ready) return;
    localStorage.setItem(LANG_KEY, lang);
    document.documentElement.lang = lang;
  }, [lang, ready]);

  useEffect(() => {
    scroller.current?.scrollTo({ top: scroller.current.scrollHeight });
  }, [lines, tab, session.server, busy]);

  function push(next: Line[]) {
    setLines((current) => [...current, ...next.map((line) => ({ ...line, text: clipText(line.text) }))].slice(-80));
  }

  async function apply(result: ShellOut) {
    const started = performance.now();
    let current = result.session;
    if (result.output === "\u0000clear") setLines([]);
    else if (result.output) push([{ kind: result.ok === false ? "err" : "out", text: result.output }]);
    if (result.token) sessionStorage.setItem(TOKEN_KEY, result.token);
    if (result.clearToken) sessionStorage.removeItem(TOKEN_KEY);
    if (result.keepSsh) sessionStorage.setItem(SSH_KEY, result.keepSsh);
    if (result.clearSsh) sessionStorage.removeItem(SSH_KEY);
    current.hasToken = Boolean(sessionStorage.getItem(TOKEN_KEY));
    current.hasSsh = Boolean(sessionStorage.getItem(SSH_KEY));
    if (result.open) {
      setEditing(result.open);
      setTab("file");
    }
    if (result.repl) {
      setRepl(result.repl);
      if (result.repl === "node") nodeScope.current = {};
    }
    try {
      if (result.python) {
        setBusy("Python");
        const py = await loadPython();
        const ran = await execPython(py, current, result.python);
        current = ran.session;
        if (ran.output) push([{ kind: "out", text: ran.output }]);
      }
      if (result.node) {
        setBusy("Node");
        const ran = runNode(result.node.code, current, { file: result.node.file, argv: result.node.argv });
        current = ran.session;
        if (ran.output) push([{ kind: "out", text: ran.output }]);
      }
      if (result.php) {
        setBusy("PHP");
        const php = await loadPhp();
        await php.run(phpSource(current, result.php));
        const text = php.take().replace(/\s+$/, "");
        if (text) push([{ kind: "out", text }]);
      }
      if (result.pip) {
        setBusy("pip");
        try {
          const py = await loadPython();
          await installPip(py, result.pip);
          push([{ kind: "out", text: `${result.pip} quraşdı` }]);
        } catch (error) {
          push([{ kind: "err", text: error instanceof Error ? error.message : "quraşmadı" }]);
        }
        setBusy("");
      }
      if (result.fetch) {
        setBusy("Gətirilir…");
        const saved = await pullText(result.fetch.url);
        current = {
          ...current,
          files: { ...current.files, [result.fetch.dest]: saved.endsWith("\n") ? saved : `${saved}\n` },
        };
        push([{ kind: "out", text: result.fetch.dest }]);
      }
      if (result.github) {
        setBusy("GitHub");
        const token = sessionStorage.getItem(TOKEN_KEY) ?? "";
        if (!token) {
          push([{ kind: "err", text: "Token tapılmadı." }]);
          current.hasToken = false;
        } else {
          try {
            const text = await pushGithub(token, result.github);
            push([{ kind: "out", text }]);
          } catch (error) {
            const message = error instanceof Error ? error.message : "göndərilmədi";
            if (message.startsWith("Token")) {
              sessionStorage.removeItem(TOKEN_KEY);
              current.hasToken = false;
            }
            push([{ kind: "err", text: message }]);
          }
        }
      }
      if (result.clone) {
        setBusy("GitHub");
        try {
          const token = sessionStorage.getItem(TOKEN_KEY) ?? "";
          const got = await cloneGithub(result.clone.repo, token);
          const imported = importRepo(current, result.clone.dest, got.files, result.clone.repo, result.clone.pull);
          current = imported.session;
          const text = `${imported.output}${got.cut && !imported.output.includes("kəsildi") ? "\n… kəsildi" : ""}`;
          push([{ kind: imported.ok === false ? "err" : "out", text }]);
        } catch (error) {
          const message = error instanceof Error ? error.message : "gəlmədi";
          if (message.startsWith("Token")) {
            sessionStorage.removeItem(TOKEN_KEY);
            current.hasToken = false;
          }
          push([{ kind: "err", text: message }]);
        }
      }
      if (result.probe) {
        setBusy("Ping");
        push([{ kind: "out", text: await probeHost(result.probe.host) }]);
      }
      if (result.remote) {
        setBusy(result.remote.kind === "exec" ? "SSH" : "SFTP");
        const password = sessionStorage.getItem(SSH_KEY) ?? "";
        if (!password) {
          push([{ kind: "err", text: "SSH şifrəsi yoxdur" }]);
          current.hasSsh = false;
        } else {
          const ran = await runRemote({
            data: {
              kind: result.remote.kind,
              user: result.remote.user,
              host: result.remote.host,
              port: result.remote.port,
              command: result.remote.command,
              remotePath: result.remote.remotePath,
              password,
              content: result.remote.content,
            },
          });
          if (ran.ok && result.remote.kind === "get" && ran.content && result.remote.localPath) {
            const saved = saveRemoteFile(current, result.remote.localPath, ran.content);
            current = saved.session;
            push([{ kind: saved.ok === false ? "err" : "out", text: saved.output }]);
          } else push([{ kind: ran.ok ? "out" : "err", text: ran.output }]);
        }
      }
    } catch (error) {
      push([{ kind: "err", text: error instanceof Error ? error.message : "xəta" }]);
    } finally {
      setBusy("");
    }
    if (result.sleep) {
      setBusy("gözləyir");
      await new Promise((resolve) => setTimeout(resolve, Math.min(3000, result.sleep ?? 0)));
      setBusy("");
      if (result.cont) {
        setSession(current);
        await apply(runLine(current, result.cont));
        return;
      }
    }
    if (result.keygen) {
      try {
        const made = await createKey(result.keygen.path);
        current = writeKey(current, result.keygen.path, made.priv, made.pub);
        push([{ kind: "out", text: `${result.keygen.path}\nP-256 PEM · ${made.fingerprint}\nözəl açar ekrana yazılmır` }]);
      } catch {
        push([{ kind: "err", text: "açar yaradılmadı" }]);
      }
    }
    if (result.device?.kind === "storage") {
      setBusy("Yaddaş");
      const mounted = await mountPhoneStorage();
      if (!mounted.ok) push([{ kind: "err", text: mounted.error }]);
      else {
        const root = `${current.home || "/root"}/storage/shared`;
        const files = { ...current.files };
        const dirs = new Set(current.dirs);
        dirs.add(root);
        for (const [rel, body] of Object.entries(mounted.files)) {
          const path = `${root}/${rel}`;
          files[path] = body;
          let folder = path.slice(0, path.lastIndexOf("/"));
          while (folder && folder !== "/" && !dirs.has(folder)) {
            dirs.add(folder);
            folder = folder.slice(0, folder.lastIndexOf("/"));
          }
        }
        current = { ...current, files, dirs: [...dirs] };
        push([{ kind: "out", text: mounted.message }]);
      }
      setBusy("");
    } else if (result.device?.kind === "palette") {
      setPalette(true);
      setPq("");
    } else if (result.device?.kind === "bright") {
      setBright(result.device.level);
    } else if (result.device?.kind === "volume") {
      speechVolume = result.device.level / 100;
    } else if (result.device?.kind === "reset") {
      localStorage.removeItem(STORE);
      localStorage.removeItem(LINES);
      window.location.reload();
    } else if (result.device?.kind === "download") {
      setBusy("Endirilir…");
      const saved = await fetchDownload(result.device.url);
      setBusy("");
      if (!saved.ok) push([{ kind: "err", text: saved.error }]);
      else if (saved.text !== undefined) {
        const path = `${current.home || "/root"}/storage/downloads/${result.device.dest}`;
        const dirs = new Set(current.dirs);
        dirs.add(`${current.home || "/root"}/storage`);
        dirs.add(`${current.home || "/root"}/storage/downloads`);
        current = { ...current, dirs: [...dirs], files: { ...current.files, [path]: saved.text.endsWith("\n") ? saved.text : `${saved.text}\n` } };
        push([{ kind: "out", text: path }]);
      } else push([{ kind: "out", text: saved.message }]);
    } else if (result.device?.kind === "backup") {
      push([{ kind: "out", text: downloadBackup(current) }]);
    } else if (result.device?.kind === "restore") {
      setBusy("Bərpa");
      const restored = await restoreBackup();
      setBusy("");
      if (!restored.ok) push([{ kind: "err", text: restored.error }]);
      else {
        const dirs = new Set(current.dirs);
        for (const path of Object.keys(restored.files)) {
          let folder = path.slice(0, path.lastIndexOf("/"));
          while (folder && folder !== "/") {
            dirs.add(folder);
            folder = folder.slice(0, folder.lastIndexOf("/"));
          }
        }
        current = {
          ...current,
          files: { ...current.files, ...restored.files },
          dirs: [...dirs],
          env: restored.env ?? current.env,
          pkgs: restored.pkgs.length ? [...new Set([...current.pkgs, ...restored.pkgs])] : current.pkgs,
        };
        push([{ kind: "out", text: restored.message }]);
      }
    } else if (result.device) {
      if (result.device.kind === "video") setBusy("Video");
      const text = await runDevice(result.device);
      setBusy("");
      if (result.device.kind === "toast") setCopied(result.device.text);
      if (text) push([{ kind: text.startsWith("!") ? "err" : "out", text: text.replace(/^!/, "") }]);
    }
    setSession(current);
    void syncPhoneStorage(current);
    const ms = Math.round(performance.now() - started);
    setMark(result.ok === false ? "xəta" : ms > 400 ? `${ms} ms` : "");
    if (result.ok === false) {
      setBell(true);
      window.setTimeout(() => setBell(false), 160);
    }
    if (result.ask) {
      setAsk(result.ask);
      setField("");
    }
    setCursor(-1);
    box.current?.focus();
  }

  function submit(line: string, baseSession = session) {
    const typed = line.trim();
    if (!typed || busy) return;
    const shown = visibleCommand(typed);
    const base = { ...baseSession, history: [...baseSession.history, shown].slice(-80) };
    const lead = repl === "python" ? ">>>" : repl === "node" ? ">" : `${shortPrompt(base)} $`;
    push([{ kind: "in", text: `${lead} ${shown}` }]);
    setDraft("");
    setHunt("");
    if (repl) {
      void runRepl(typed, base);
      return;
    }
    void apply(runLine(base, typed));
  }

  async function runRepl(typed: string, base: Session) {
    if (typed === "exit" || typed === "exit()" || typed === "quit") {
      setRepl(null);
      setSession(base);
      push([{ kind: "out", text: "çıxdı" }]);
      return;
    }
    try {
      if (repl === "python") {
        setBusy("Python");
        const py = await loadPython();
        const ran = await execPython(py, base, { code: typed });
        if (ran.output) push([{ kind: "out", text: ran.output }]);
        setSession(ran.session);
      } else {
        const ran = runNode(typed, base, { repl: true, scope: nodeScope.current });
        if (ran.output) push([{ kind: "out", text: ran.output }]);
        setSession({ ...ran.session, history: base.history });
      }
    } catch (error) {
      setSession(base);
      push([{ kind: "err", text: error instanceof Error ? error.message : "xəta" }]);
    } finally {
      setBusy("");
      box.current?.focus();
    }
  }

  function answerAsk() {
    if (!ask || ask.id === "confirm") return;
    const value = field.trim();
    if (!value) return;
    if (ask.id === "token") sessionStorage.setItem(TOKEN_KEY, value);
    if (ask.id === "ssh") sessionStorage.setItem(SSH_KEY, value);
    const filled = applyAnswer(session, ask, value);
    filled.hasToken = Boolean(sessionStorage.getItem(TOKEN_KEY));
    filled.hasSsh = Boolean(sessionStorage.getItem(SSH_KEY));
    setAsk(null);
    setField("");
    push([{ kind: "in", text: ask.secret ? "••••" : value }]);
    if (ask.id === "ssh") {
      sessionStorage.setItem(SSH_KEY, value);
      filled.hasSsh = true;
      setSession(filled);
      void apply(runLine(filled, ask.resume));
      return;
    }
    if (ask.resume === "git config user.name" || ask.resume === "git config user.email" || ask.resume === "nibras token") {
      setSession(filled);
      push([{ kind: "out", text: ask.id === "token" ? "Token saxlandı. Ekrana yazılmır." : "saxlandı" }]);
      return;
    }
    void apply(runLine(filled, ask.resume, value));
  }

  function runScript() {
    const body = scriptText.replace(/\r\n/g, "\n").trim();
    if (!body || busy) return;
    setScriptOpen(false);
    const path = "/root/.nibras.sh";
    const next = {
      ...session,
      files: { ...session.files, [path]: body.endsWith("\n") ? body : `${body}\n` },
      history: [...session.history, "sh .nibras.sh"].slice(-80),
    };
    push([{ kind: "in", text: `$ ${body}` }]);
    void apply(runLine(next, "sh .nibras.sh"));
  }

  function ctrlAction(key: string) {
    if (key === "r" && !repl) {
      const query = (hunt || draft).toLowerCase();
      if (!hunt) setHunt(query);
      const list = session.history;
      let start = list.lastIndexOf(draft);
      if (start < 0) start = list.length;
      for (let index = start - 1; index >= 0; index -= 1) {
        const item = list[index] ?? "";
        if (!query || item.toLowerCase().includes(query)) {
          setDraft(item);
          return;
        }
      }
      return;
    }
    if (key === "l" && !repl) {
      submit("clear");
      return;
    }
    if (key === "c") {
      if (repl) {
        setRepl(null);
        push([{ kind: "out", text: "çıxdı" }]);
      } else setDraft("");
      return;
    }
    if (key === "a" || key === "e") {
      const pos = key === "a" ? 0 : draft.length;
      requestAnimationFrame(() => box.current?.setSelectionRange(pos, pos));
      return;
    }
    if (key === "u") {
      setDraft("");
      return;
    }
    if (key === "w") {
      setDraft((value) => value.replace(/\s*\S+\s*$/, ""));
      return;
    }
    if (key === "f" && !repl) {
      setFindOn((value) => {
        if (value) setNeedle("");
        return !value;
      });
      return;
    }
    if (key === "k") {
      const pos = box.current?.selectionStart ?? draft.length;
      setDraft(draft.slice(0, pos));
    }
  }

  function onKey(event: KeyboardEvent<HTMLInputElement>) {
    if (!event.ctrlKey && !event.metaKey && !event.altKey && "([{\"'".includes(event.key)) {
      const close = event.key === "(" ? ")" : event.key === "[" ? "]" : event.key === "{" ? "}" : event.key;
      event.preventDefault();
      const el = box.current;
      const start = el?.selectionStart ?? draft.length;
      const end = el?.selectionEnd ?? start;
      if (start === end && draft[start] === close && (event.key === ")" || event.key === "]" || event.key === "}" || event.key === close)) {
        requestAnimationFrame(() => el?.setSelectionRange(start + 1, start + 1));
        return;
      }
      const next = `${draft.slice(0, start)}${event.key}${draft.slice(start, end)}${close}${draft.slice(end)}`;
      setDraft(next);
      const caret = start + 1;
      requestAnimationFrame(() => el?.setSelectionRange(caret, end > start ? end + 1 : caret));
      return;
    }
    if (event.key === "Tab") {
      event.preventDefault();
      const next = complete(session, draft)[0];
      if (next) setDraft(next);
      return;
    }
    if ((event.ctrlKey || mods.ctrl) && !event.altKey) {
      const key = event.key.toLowerCase();
      if ("rclaeuwkf".includes(key)) {
        event.preventDefault();
        ctrlAction(key);
        setMods({ ctrl: false, alt: false });
        return;
      }
    }
    if (event.key === "Escape") {
      setHunt("");
      return;
    }
    if (event.key === "ArrowRight") {
      const pos = box.current?.selectionStart ?? draft.length;
      const ghost = ghostOf(session, draft, repl);
      if (ghost && pos === draft.length) {
        event.preventDefault();
        setDraft(ghost);
        return;
      }
    }
    if (event.key === "ArrowUp") {
      event.preventDefault();
      const next = cursor < 0 ? session.history.length - 1 : Math.max(0, cursor - 1);
      setCursor(next);
      setDraft(session.history[next] ?? "");
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      if (cursor < 0) return;
      const next = cursor + 1;
      if (next >= session.history.length) {
        setCursor(-1);
        setDraft("");
      } else {
        setCursor(next);
        setDraft(session.history[next] ?? "");
      }
    }
  }

  function switchSlot(index: number) {
    const target = slots[index];
    if (!target || index === slot) return;
    setSlot(index);
    setSession(target.session);
    setLines(target.lines);
    setDraft("");
    setCursor(-1);
  }

  function addSlot() {
    if (slots.length >= 4) return;
    const fresh = emptySession();
    fresh.env = session.env;
    fresh.user = session.user;
    fresh.home = session.home;
    fresh.cwd = session.cwd;
    fresh.files = session.files;
    fresh.dirs = session.dirs;
    fresh.pkgs = [...session.pkgs];
    fresh.envVars = { ...session.envVars };
    fresh.links = { ...session.links };
    const next = { name: String(slots.length + 1), session: fresh, lines: BOOT.map((line) => ({ ...line })) };
    setSlots((list) => [...list.map((item, index) => (index === slot ? { ...item, session, lines } : item)), next]);
    setSlot(slots.length);
    setSession(fresh);
    setLines(next.lines);
    setDraft("");
  }

  function closeSlot() {
    if (slots.length < 2) return;
    const next = slots.filter((_, index) => index !== slot);
    const at = Math.max(0, slot - 1);
    setSlots(next);
    setSlot(at);
    setSession(next[at]?.session ?? session);
    setLines(next[at]?.lines ?? lines);
    setDraft("");
  }

  function insertText(text: string) {
    const el = box.current;
    const pos = el?.selectionStart ?? draft.length;
    setDraft(draft.slice(0, pos) + text + draft.slice(pos));
    requestAnimationFrame(() => el?.setSelectionRange(pos + text.length, pos + text.length));
  }

  function pressKey(key: string) {
    const name = key.toUpperCase();
    if (name === "CTRL") {
      setMods((value) => ({ ...value, ctrl: !value.ctrl }));
      return;
    }
    if (name === "ALT") {
      setMods((value) => ({ ...value, alt: !value.alt }));
      return;
    }
    if (name === "ESC") {
      setMods({ ctrl: false, alt: false });
      return;
    }
    if (name === "TAB") {
      const next = complete(session, draft)[0];
      if (next) setDraft(next);
      return;
    }
    if (name === "UP" || name === "DOWN") {
      const next = name === "UP" ? (cursor < 0 ? session.history.length - 1 : Math.max(0, cursor - 1)) : cursor + 1;
      if (name === "DOWN" && (cursor < 0 || next >= session.history.length)) {
        setCursor(-1);
        setDraft("");
        return;
      }
      setCursor(next);
      setDraft(session.history[next] ?? "");
      return;
    }
    const el = box.current;
    const pos = el?.selectionStart ?? draft.length;
    if (name === "LEFT") {
      const at = Math.max(0, pos - 1);
      requestAnimationFrame(() => el?.setSelectionRange(at, at));
      return;
    }
    if (name === "RIGHT") {
      const ghost = ghostOf(session, draft, repl);
      if (ghost && pos >= draft.length) {
        setDraft(ghost);
        return;
      }
      const at = Math.min(draft.length, pos + 1);
      requestAnimationFrame(() => el?.setSelectionRange(at, at));
      return;
    }
    if (name === "HOME") {
      requestAnimationFrame(() => el?.setSelectionRange(0, 0));
      return;
    }
    if (name === "END") {
      requestAnimationFrame(() => el?.setSelectionRange(draft.length, draft.length));
      return;
    }
    if (name === "PGUP") {
      scroller.current?.scrollTo({ top: 0 });
      return;
    }
    if (name === "PGDN") {
      scroller.current?.scrollTo({ top: scroller.current.scrollHeight });
      return;
    }
    if (name === "BKSP") {
      if (pos < 1) return;
      setDraft(draft.slice(0, pos - 1) + draft.slice(pos));
      return;
    }
    if (name === "DEL") {
      setDraft(draft.slice(0, pos) + draft.slice(pos + 1));
      return;
    }
    if (mods.ctrl && name === "C") {
      if (repl) setRepl(null);
      setDraft("");
      setMods({ ctrl: false, alt: false });
      return;
    }
    if (mods.ctrl && name === "L") {
      submit("clear");
      setMods({ ctrl: false, alt: false });
      return;
    }
    if (mods.ctrl && name === "U") {
      setDraft("");
      setMods({ ctrl: false, alt: false });
      return;
    }
    if (mods.ctrl && name === "W") {
      setDraft(draft.replace(/\s*\S+\s*$/, ""));
      setMods({ ctrl: false, alt: false });
      return;
    }
    if (name === "PASTE") {
      void navigator.clipboard?.readText().then((text) => {
        if (text) insertText(text);
      });
      return;
    }
    if (key.length === 1 || (key.length > 1 && !["ESC", "HOME", "END", "PGUP", "PGDN", "CTRL", "ALT"].includes(name))) {
      insertText(key.length === 1 ? key : key);
      return;
    }
    box.current?.focus();
  }

  async function pickLang(code: Lang) {
    if (code === lang || langBusy) return;
    if (code === "az") {
      setLang("az");
      return;
    }
    setLangBusy(code);
    try {
      await loadLang(code);
      setLang(code);
    } catch {
      setCopied("dil gəlmədi");
      window.setTimeout(() => setCopied(""), 1200);
    } finally {
      setLangBusy("");
    }
  }
  const query = filter.trim().toLowerCase();
  const actions = ACTIONS.filter((item) => {
    const label = say(lang, item.label).toLowerCase();
    const group = say(lang, item.group).toLowerCase();
    return !query || label.includes(query) || group.includes(query) || item.label.toLowerCase().includes(query) || item.line.includes(query);
  });
  const groups = [...new Set(actions.map((item) => item.group))];
  const hints = repl || !draft.trim() ? [] : complete(session, draft);
  const recent = session.history.slice(-8).reverse();
  const look = needle.trim().toLowerCase();
  const shown = look
    ? lines.filter((line) => say(lang, line.text).toLowerCase().includes(look) || line.text.toLowerCase().includes(look))
    : lines;
  const sizeClass = size === 0 ? "text-xs leading-5" : size === 2 ? "text-base leading-7" : "text-sm leading-6";

  return (
    <div className="nt-app">
      <div className={`nt-window ${bell ? "outline outline-1 outline-[#c04040]" : ""}`} style={bright < 100 ? { filter: `brightness(${bright}%)` } : undefined}>
        <main className="flex min-h-0 flex-1 flex-col overflow-hidden bg-black">
          {tab === "term" ? (
            <div className="flex min-h-0 flex-1 flex-col">
              <button className="nt-edge" type="button" aria-label="Seans" onClick={() => setDrawer(true)} />
              <div
                className="nt-term"
                onClick={() => {
                  if (!window.getSelection()?.toString()) box.current?.focus();
                }}
                onTouchStart={(event) => {
                  if (event.touches.length === 1 && event.touches[0].clientX < 28) {
                    pinch.current = { dist: -1, size: event.touches[0].clientX };
                    return;
                  }
                  if (event.touches.length !== 2) return;
                  const [a, b] = [event.touches[0], event.touches[1]];
                  pinch.current = { dist: Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY), size };
                }}
                onTouchMove={(event) => {
                  if (pinch.current?.dist === -1 && event.touches.length === 1 && event.touches[0].clientX - pinch.current.size > 48) {
                    setDrawer(true);
                    pinch.current = null;
                    return;
                  }
                  if (event.touches.length !== 2 || !pinch.current || pinch.current.dist < 0) return;
                  const [a, b] = [event.touches[0], event.touches[1]];
                  const dist = Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY);
                  const ratio = dist / pinch.current.dist;
                  const next = ratio > 1.18 ? Math.min(2, pinch.current.size + 1) : ratio < 0.84 ? Math.max(0, pinch.current.size - 1) : pinch.current.size;
                  if (next !== size) setSize(next);
                }}
              >
                {findOn ? (
                  <input
                    className="nt-find"
                    value={needle}
                    placeholder={say(lang, "nəticədə axtar")}
                    onChange={(event) => setNeedle(event.target.value)}
                  />
                ) : null}
                <div ref={scroller} className={`nt-screen relative z-10 bg-transparent ${sizeClass}`}>
                  {shown.length ? null : <p className="text-[#6d6d6d]">{say(lang, "tapılmadı")}</p>}
                  {shown.map((line, index) => (
                    <div key={`${line.kind}-${index}`} className="whitespace-pre-wrap break-words">
                      {paintLine(say(lang, line.text), line.kind)}
                    </div>
                  ))}
                  {busy ? <p className="text-[#8a8a8a]">{say(lang, busy)}</p> : null}
                  {copied ? <p className="text-[#8fbf8a]">{say(lang, copied)}</p> : null}
                  <form
                    className="mt-1 flex min-h-6 items-center font-mono"
                    onSubmit={(event) => {
                      event.preventDefault();
                      submit(draft);
                    }}
                  >
                    <span className="shrink-0 text-[#f2f2f2]">{repl === "python" ? ">>> " : repl === "node" ? "> " : `${shortPrompt(session)} $ `}</span>
                    {session.envVars["?"] && session.envVars["?"] !== "0" ? <span className="mr-1 text-[#ff6b6b]">{session.envVars["?"]}</span> : null}
                    <label className="sr-only" htmlFor="cmd">{say(lang, "Əmr")}</label>
                    <div className="relative min-w-0 flex-1">
                      {ghostOf(session, draft, repl) ? (
                        <span className="pointer-events-none absolute left-0 top-0 truncate text-[#5c5c5c]">
                          <span className="invisible">{draft}</span>
                          {ghostOf(session, draft, repl).slice(draft.length)}
                        </span>
                      ) : null}
                      <input
                        ref={box}
                        id="cmd"
                        className="nt-input relative w-full bg-transparent"
                        value={draft}
                        disabled={Boolean(busy)}
                        autoFocus
                        autoCapitalize="none"
                        autoCorrect="off"
                        spellCheck={false}
                        onChange={(event) => setDraft(event.target.value)}
                        onKeyDown={onKey}
                      />
                    </div>
                    {draft ? null : <span className="nt-block" />}
                    {mark ? <span className="ml-2 text-[#6d6d6d]">{mark}</span> : null}
                  </form>
                </div>
              </div>
              {past && recent.length ? (
                <div className="max-h-24 overflow-y-auto rounded-xl border border-[#1c2830] bg-[#0c1218]">
                  {recent.map((item, index) => (
                    <button key={`${index}-${item}`} className="block w-full px-3 py-1.5 text-left font-mono text-xs text-[#d7d7d7]" type="button" onClick={() => { setPast(false); submit(item); }}>
                      {item}
                    </button>
                  ))}
                </div>
              ) : null}
              {hints.length ? (
                <div className="flex gap-3 overflow-x-auto px-1 font-mono text-xs text-[#8a8a8a]">
                  {hints.map((item) => (
                    <button key={item} className="shrink-0" type="button" onClick={() => { setDraft(item); box.current?.focus(); }}>
                      {item.trim()}
                    </button>
                  ))}
                </div>
              ) : null}
              {session.server ? <iframe title="Önizləmə" className="h-28 w-full border-t border-[#222] bg-black" sandbox="" srcDoc={session.server.html} /> : null}
              <div className="nt-keys">
                {extraKeyRows(session).map((row, rowIndex) => (
                  <div key={rowIndex} className="nt-keyrow">
                    {row.map((key) => {
                      const name = key.toUpperCase();
                      const on = (name === "CTRL" && mods.ctrl) || (name === "ALT" && mods.alt);
                      const label = name === "UP" ? "↑" : name === "DOWN" ? "↓" : name === "LEFT" ? "←" : name === "RIGHT" ? "→" : key;
                      return (
                        <button key={`${rowIndex}-${key}`} className={`nt-key ${on ? "on" : ""}`} type="button" onClick={() => pressKey(key)}>
                          {label}
                        </button>
                      );
                    })}
                  </div>
                ))}
              </div>
            </div>
          ) : null}
          {tab === "pick" ? (
            <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
              <p className="text-sm leading-6 text-muted">{say(lang, "Düymə terminal əmrinin özüdür. Ad, token və ya təsdiq lazım olanda xana açılır.")}</p>
              <input className="nt-field mt-3 w-full" value={filter} placeholder={say(lang, "seçim axtar")} onChange={(event) => setFilter(event.target.value)} />
              {groups.map((group) => (
                <section key={group} className="mt-5">
                  <h2 className="font-mono text-xs tracking-widest text-gold">{say(lang, group)}</h2>
                  <div className="mt-2 grid grid-cols-2 gap-2">
                    {actions
                      .filter((item) => item.group === group)
                      .map((item) => (
                        <button
                          key={item.label}
                          className={`min-h-11 border px-3 text-left text-sm ${isMarked(session, item.mark) ? "border-gold text-gold" : "border-line bg-surface text-fg"}`}
                          type="button"
                          onClick={() => {
                            setTab("term");
                            submit(item.line);
                          }}
                        >
                          {say(lang, item.label)}
                        </button>
                      ))}
                  </div>
                </section>
              ))}
            </div>
          ) : null}
          {tab === "file" ? (
            <FilePane
              session={session}
              editing={editing}
              onPick={setEditing}
              lang={lang}
              onCwd={setSession}
              onCommand={(line, text) => {
                const files =
                  text === undefined
                    ? session.files
                    : { ...session.files, [editing]: text.endsWith("\n") ? text : `${text}\n` };
                setTab("term");
                submit(line, { ...session, files });
              }}
              onSave={(path, text) => {
                setSession({ ...session, files: { ...session.files, [path]: text.endsWith("\n") ? text : `${text}\n` } });
              }}
            />
          ) : null}
        </main>
        {drawer ? (
          <div className="nt-drawer" role="dialog">
            <p className="px-4 py-3 font-mono text-xs tracking-widest text-[#8a8a8a]">SEANS</p>
            {slots.map((item, index) => (
              <button
                key={`${item.name}-${index}`}
                className={`nt-sess ${index === slot ? "on" : ""}`}
                type="button"
                onClick={() => {
                  switchSlot(index);
                  setDrawer(false);
                }}
              >
                {index + 1} · {item.name}
              </button>
            ))}
            <button className="nt-sess" type="button" onClick={() => { addSlot(); setDrawer(false); }}>
              Yeni
            </button>
            {slots.length > 1 ? (
              <button className="nt-sess" type="button" onClick={() => { closeSlot(); setDrawer(false); }}>
                Bağla
              </button>
            ) : null}
            <button className="nt-sess" type="button" onClick={() => setDrawer(false)}>
              Geri
            </button>
          </div>
        ) : null}
        {palette ? (
          <form
            className="nt-drawer"
            onSubmit={(event) => {
              event.preventDefault();
              const found = ACTIONS.find((item) => `${item.label} ${item.line}`.toLowerCase().includes(pq.trim().toLowerCase()));
              setPalette(false);
              setPq("");
              if (found) submit(found.line);
            }}
          >
            <input className="nt-field m-3" value={pq} placeholder={say(lang, "əmr axtar")} autoFocus onChange={(event) => setPq(event.target.value)} />
            {ACTIONS.filter((item) => `${item.label} ${item.line}`.toLowerCase().includes(pq.trim().toLowerCase()))
              .slice(0, 12)
              .map((item) => (
                <button
                  key={`${item.group}-${item.line}-${item.label}`}
                  className="nt-sess"
                  type="button"
                  onClick={() => {
                    setPalette(false);
                    setPq("");
                    submit(item.line);
                  }}
                >
                  {say(lang, item.label)}
                </button>
              ))}
          </form>
        ) : null}
      </div>
      {scriptOpen ? (
        <div className="fixed inset-0 z-20 flex items-end justify-center bg-deep/80 p-4" role="dialog" aria-modal="true">
          <form
            className="w-full max-w-lg border border-line bg-surface p-5"
            onSubmit={(event) => {
              event.preventDefault();
              runScript();
            }}
          >
            <h2 className="font-mono text-sm text-[#d7d7d7]">{say(lang, "Skript")}</h2>
            <p className="mt-2 text-sm leading-6 text-muted">{say(lang, "Hər sətir ayrı əmrdir. Boş sətir və # keçilir.")}</p>
            <textarea
              className="mt-4 min-h-40 w-full border border-line bg-bg p-3 font-mono text-sm leading-6 text-fg outline-none"
              value={scriptText}
              autoFocus
              spellCheck={false}
              onChange={(event) => setScriptText(event.target.value)}
            />
            <div className="mt-4 flex gap-2">
              <button className="min-h-11 bg-gold px-4 text-sm text-bg" type="submit">
                {say(lang, "İşlət")}
              </button>
              <button className="min-h-11 border border-line px-4 text-sm text-fg" type="button" onClick={() => setScriptOpen(false)}>
                {say(lang, "Bağla")}
              </button>
            </div>
          </form>
        </div>
      ) : null}
      {ask ? (
        <div className="fixed inset-0 z-20 flex items-end justify-center bg-deep/80 p-4" role="dialog" aria-modal="true">
          <form
            className="w-full max-w-lg border border-line bg-surface p-5"
            onSubmit={(event) => {
              event.preventDefault();
              if (ask.id === "confirm") {
                const resume = ask.resume;
                setAsk(null);
                push([{ kind: "in", text: "bəli" }]);
                void apply(runLine(session, resume, "bəli"));
                return;
              }
              answerAsk();
            }}
          >
            <h2 className="font-mono text-sm text-[#d7d7d7]">{say(lang, ask.title)}</h2>
            <p className="mt-2 text-sm leading-6 text-muted">{say(lang, ask.label)}</p>
            {ask.id === "confirm" ? null : (
              <input
                className="nt-field mt-4 w-full"
                type={ask.secret ? "password" : "text"}
                autoComplete="off"
                autoFocus
                value={field}
                placeholder={ask.secret ? say(lang, "gizli qalır") : ""}
                onChange={(event) => setField(event.target.value)}
              />
            )}
            <div className="mt-4 flex gap-2">
              <button className="min-h-11 bg-gold px-4 text-sm text-bg" type="submit">
                {ask.id === "confirm" ? say(lang, "Bəli") : say(lang, "Saxla")}
              </button>
              <button className="min-h-11 border border-line px-4 text-sm text-fg" type="button" onClick={() => setAsk(null)}>
                {say(lang, "Bağla")}
              </button>
            </div>
          </form>
        </div>
      ) : null}
    </div>
  );
}

function parentOf(path: string) {
  const cut = path.lastIndexOf("/");
  return cut <= 0 ? "/" : path.slice(0, cut);
}

function FilePane({
  session,
  editing,
  lang,
  onPick,
  onSave,
  onCommand,
  onCwd,
}: {
  session: Session;
  editing: string;
  lang: Lang;
  onPick: (path: string) => void;
  onSave: (path: string, text: string) => void;
  onCommand: (line: string, text?: string) => void;
  onCwd: (session: Session) => void;
}) {
  const here = listHere(session);
  const base = editing.split("/").pop() ?? editing;
  const [text, setText] = useState(session.files[editing] ?? "");
  const [note, setNote] = useState("");
  const [renaming, setRenaming] = useState(false);
  const [nextName, setNextName] = useState(base);
  const [query, setQuery] = useState("");
  const [folderOpen, setFolderOpen] = useState(false);
  const [folderName, setFolderName] = useState("");
  const [sure, setSure] = useState(false);
  const [bySize, setBySize] = useState(false);
  const [find, setFind] = useState("");
  const [replace, setReplace] = useState("");
  const incoming = useRef<HTMLInputElement>(null);
  const editor = useRef<HTMLTextAreaElement>(null);
  const gutter = useRef<HTMLPreElement>(null);
  useEffect(() => {
    setText(session.files[editing] ?? "");
  }, [editing, session.files]);
  const quoted = (value: string) => (/\s/.test(value) ? `"${value}"` : value);
  const join = (name: string) => (session.cwd === "/" ? `/${name}` : `${session.cwd}/${name}`);
  const look = query.trim().toLowerCase();
  const folders = here.folders.filter((name) => name.toLowerCase().includes(look));
  const files = here.files
    .filter((name) => name.toLowerCase().includes(look))
    .sort((a, b) => {
      if (!bySize) return a.localeCompare(b);
      const size = (name: string) => new TextEncoder().encode(session.files[join(name)] ?? "").length;
      return size(b) - size(a) || a.localeCompare(b);
    });
  const lines = text.split("\n");
  const enter = (name: string) => {
    const next = runLine(session, `cd ${quoted(name)}`);
    if (next.ok === false) {
      setNote(next.output || "açılmadı");
      return;
    }
    onCwd(next.session);
  };
  const runLineFor = () => {
    const dir = quoted(parentOf(editing));
    const name = quoted(base);
    if (editing.endsWith(".py")) return `cd ${dir} && python3 ${name}`;
    if (editing.endsWith(".js")) return `cd ${dir} && node ${name}`;
    if (editing.endsWith(".php")) return `cd ${dir} && php ${name}`;
    if (editing.endsWith(".c") || editing.endsWith(".cpp")) return `cd ${dir} && gcc ${name}`;
    if (editing.endsWith(".html")) return `cd ${dir} && nibras serve`;
    if (editing.endsWith(".md")) return `cd ${dir} && nibras md ${name}`;
    if (editing.endsWith(".json")) return `cd ${dir} && jq . ${name}`;
    return `cat ${name}`;
  };
  return (
    <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
      <div className="flex items-baseline justify-between gap-3">
        <h2 className="truncate font-mono text-sm text-[#d7d7d7]">{say(lang, "Fayllar")}</h2>
        <div className="flex gap-3">
          <button className="min-h-11 text-sm text-gold" type="button" onClick={() => setBySize((value) => !value)}>
            {say(lang, bySize ? "Ölçü" : "Ad")}
          </button>
          <button className="min-h-11 text-sm text-gold" type="button" onClick={() => setFolderOpen((value) => !value)}>
            {say(lang, "Qovluq")}
          </button>
          <button className="min-h-11 text-sm text-gold" type="button" onClick={() => onCommand("edit")}>
            {say(lang, "Yeni")}
          </button>
        </div>
      </div>
      <p className="mt-1 truncate font-mono text-sm text-gold">{session.cwd}</p>
      {folderOpen ? (
        <form
          className="mt-3 flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            const name = folderName.trim();
            if (!name) return;
            const next = runLine(session, `mkdir ${quoted(name)}`);
            setFolderOpen(false);
            setFolderName("");
            if (next.ok === false) {
              setNote(next.output || "olmadı");
              return;
            }
            onCwd(next.session);
            setNote("qovluq açıldı");
          }}
        >
          <input className="nt-field min-w-0 flex-1" value={folderName} placeholder={say(lang, "qovluq adı")} onChange={(event) => setFolderName(event.target.value)} />
          <button className="min-h-11 bg-gold px-4 text-sm text-bg" type="submit">
            {say(lang, "Aç")}
          </button>
        </form>
      ) : null}
      <input className="nt-field mt-3 w-full" value={query} placeholder={say(lang, "bu qovluqda")} onChange={(event) => setQuery(event.target.value)} />
      <ul className="mt-3 max-h-40 overflow-y-auto">
        {session.cwd !== "/" ? (
          <li>
            <button className="min-h-11 text-left font-mono text-sm text-muted" type="button" onClick={() => enter("..")}>
              ../
            </button>
          </li>
        ) : null}
        {folders.map((name) => (
          <li key={`dir-${name}`}>
            <button className="min-h-11 text-left font-mono text-sm text-gold" type="button" onClick={() => enter(name)}>
              {name}/
            </button>
          </li>
        ))}
        {files.map((name) => {
          const path = join(name);
          return (
            <li key={path}>
              <button className={`min-h-11 text-left font-mono text-sm ${path === editing ? "text-gold" : "text-fg"}`} type="button" onClick={() => onPick(path)}>
                {name}
              </button>
            </li>
          );
        })}
      </ul>
      <div className="mt-3 flex gap-2">
        <input className="nt-field min-w-0 flex-1" value={find} placeholder={say(lang, "faylda axtar")} onChange={(event) => setFind(event.target.value)} />
        <input className="nt-field min-w-0 flex-1" value={replace} placeholder={say(lang, "əvəz")} onChange={(event) => setReplace(event.target.value)} />
        <button
          className="min-h-11 px-3 text-sm text-gold"
          type="button"
          onClick={() => {
            if (!find) return;
            const count = text.split(find).length - 1;
            setText(text.split(find).join(replace));
            setNote(count ? `${count} dəyişdi` : "tapılmadı");
          }}
        >
          {say(lang, "Dəyiş")}
        </button>
      </div>
      <div className="mt-3 grid max-h-64 grid-cols-[auto_1fr] overflow-hidden border border-line">
        <pre
          ref={gutter}
          aria-hidden="true"
          className="overflow-hidden bg-bg px-2 py-3 text-right font-mono text-sm leading-6 text-muted"
        >
          {lines.map((_, index) => String(index + 1)).join("\n")}
        </pre>
        <div className="relative min-h-48">
          <pre aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden whitespace-pre-wrap break-words p-3 font-mono text-sm leading-6 text-[#d7d7d7]">
            {paintCode(text, editing)}
          </pre>
          <textarea
            ref={editor}
            className="relative min-h-48 w-full resize-y overflow-auto bg-transparent p-3 font-mono text-sm leading-6 text-transparent caret-[#eee] outline-none"
            value={text}
            onChange={(event) => setText(event.target.value)}
            onScroll={() => {
              if (gutter.current && editor.current) gutter.current.scrollTop = editor.current.scrollTop;
            }}
          />
        </div>
      </div>
      <p className="mt-1 font-mono text-xs text-muted">{linesLabel(lang, lines.length)}</p>
      <div className="mt-3 flex flex-wrap gap-2">
        <button
          className="min-h-11 bg-gold px-4 text-sm text-bg"
          type="button"
          onClick={() => {
            onSave(editing, text);
            setNote("saxlandı");
          }}
        >
          {say(lang, "Saxla")}
        </button>
        <button className="min-h-11 border border-line px-4 text-sm text-fg" type="button" onClick={() => onCommand(runLineFor(), text)}>
          {say(lang, "İşlət")}
        </button>
        <button
          className="min-h-11 border border-line px-4 text-sm text-fg"
          type="button"
          onClick={() => {
            const blob = new Blob([text.endsWith("\n") ? text : `${text}\n`], { type: "text/plain" });
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = base || "fayl.txt";
            link.click();
            URL.revokeObjectURL(url);
          }}
        >
          {say(lang, "Endir")}
        </button>
        <button
          className="min-h-11 border border-line px-4 text-sm text-fg"
          type="button"
          onClick={() => {
            const rows = packHere(session);
            if (!rows.length) {
              setNote("qovluq boşdur");
              return;
            }
            const bytes = storeZip(rows);
            const blob = new Blob([bytes], { type: "application/zip" });
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            const folder = session.cwd === "/" ? "root" : session.cwd.split("/").pop() || "qovluq";
            link.href = url;
            link.download = `${folder}.zip`;
            link.click();
            URL.revokeObjectURL(url);
            setNote("zip endirildi");
          }}
        >
          {say(lang, "Hamısı")}
        </button>
        <button className="min-h-11 border border-line px-4 text-sm text-fg" type="button" onClick={() => incoming.current?.click()}>
          {say(lang, "İçəri")}
        </button>
        <button
          className="min-h-11 border border-line px-4 text-sm text-fg"
          type="button"
          onClick={() => {
            setNextName(base);
            setRenaming(true);
          }}
        >
          {renameLabel(lang)}
        </button>
        <button className="min-h-11 border border-danger px-4 text-sm text-danger" type="button" onClick={() => setSure(true)}>
          {say(lang, "Sil")}
        </button>
      </div>
      {sure ? (
        <div className="mt-3 flex flex-wrap items-center gap-2">
          <p className="text-sm text-fg">{deleteAsk(lang, base)}</p>
          <button
            className="min-h-11 border border-danger px-4 text-sm text-danger"
            type="button"
            onClick={() => {
              const next = runLine(session, `rm ${quoted(editing)}`);
              setSure(false);
              if (next.ok === false) {
                setNote(next.output || "silinmədi");
                return;
              }
              const left = listHere(next.session).files[0];
              onCwd(next.session);
              if (left) onPick(next.session.cwd === "/" ? `/${left}` : `${next.session.cwd}/${left}`);
              setNote("silindi");
            }}
          >
            {say(lang, "Bəli")}
          </button>
          <button className="min-h-11 border border-line px-4 text-sm text-fg" type="button" onClick={() => setSure(false)}>
            {say(lang, "Xeyr")}
          </button>
        </div>
      ) : null}
      {renaming ? (
        <form
          className="mt-3 flex gap-2"
          onSubmit={(event) => {
            event.preventDefault();
            const safe = nextName.trim().replace(/[^\w.-]+/g, "-");
            if (!safe || safe === base) {
              setRenaming(false);
              return;
            }
            const dest = `${parentOf(editing)}/${safe}`.replace(/\/+/g, "/");
            setRenaming(false);
            onPick(dest);
            onCommand(`mv ${quoted(editing)} ${quoted(dest)}`);
          }}
        >
          <input className="nt-field min-w-0 flex-1" value={nextName} onChange={(event) => setNextName(event.target.value)} />
          <button className="min-h-11 bg-gold px-4 text-sm text-bg" type="submit">
            {say(lang, "Saxla")}
          </button>
        </form>
      ) : null}
        <input
          ref={incoming}
          className="sr-only"
          type="file"
          accept=".txt,.py,.js,.php,.c,.cpp,.h,.html,.css,.json,.md,.sh,text/plain"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.target.value = "";
            if (!file) return;
            if (file.size > 200_000) {
              setNote("200 KB-dan böyükdür");
              return;
            }
            void file.text().then((body) => {
              if (body.includes("\0")) {
                setNote("mətn faylı deyil");
                return;
              }
              const name = file.name.split(/[/\\]/).pop()?.replace(/[^\w.-]+/g, "-") || "fayl.txt";
              const dest = `${session.cwd.replace(/\/$/, "")}/${name}`;
              onSave(dest, body);
              onPick(dest);
              setNote("içəri alındı");
            });
          }}
        />
      {note ? <p className="mt-2 text-sm text-ok">{say(lang, note)}</p> : null}
    </div>
  );
}

function MenuList({ items, onPick }: { items: Array<{ label: string; line: string }>; onPick: (line: string) => void }) {
  return (
    <div>
      {items.map((item) => (
        <button key={item.line} className="flex w-full items-center justify-between gap-3 px-2 py-1.5 text-left text-xs text-[#d7dde3]" type="button" onClick={() => onPick(item.line)}>
          <span>{item.label}</span>
          <span className="truncate font-mono text-[#8b98a5]">{item.line}</span>
        </button>
      ))}
    </div>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button className="grid h-9 w-9 shrink-0 place-items-center rounded-full border border-[#24303a] bg-[#10161c] text-[#d5dee6]" type="button" aria-label={label} onClick={onClick}>
      {children}
    </button>
  );
}

function Stat({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return (
    <div className="min-w-0 border-l border-[#1c2830] px-1 text-center first:border-l-0">
      <div className="mx-auto mb-1 grid h-4 place-items-center text-[#3dff9a]">{icon}</div>
      <p className="truncate text-[0.6rem] text-[#8b98a5]">{label}</p>
      <p className="truncate font-mono text-[0.58rem] text-[#e7eef3]">{value}</p>
    </div>
  );
}

function readDevice() {
  const ua = typeof navigator === "undefined" ? "" : navigator.userAgent;
  const android = /Android\s+(\d+)/.exec(ua);
  const model = /;\s*([^;)]+)\s+Build\//.exec(ua)?.[1]?.trim();
  const arch = /aarch64|arm64/i.test(ua) ? "arm64-v8a" : /x86_64|Win64|x64/i.test(ua) ? "x86_64" : "web";
  return { device: model || "Web", system: android?.[1] ?? "Web", arch };
}

function clock(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const h = String(Math.floor(total / 3600)).padStart(2, "0");
  const m = String(Math.floor((total % 3600) / 60)).padStart(2, "0");
  const s = String(total % 60).padStart(2, "0");
  return `${h}:${m}:${s}`;
}

function svg(path: string) {
  return (
    <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d={path} strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function FolderIcon() {
  return svg("M3 7h6l2 2h10v10H3z");
}
function ClockIcon() {
  return svg("M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z");
}
function GearIcon() {
  return svg("M12 15.5a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7zM12 3v2M12 19v2M3 12h2M19 12h2");
}
function PhoneIcon() {
  return svg("M8 3h8v18H8zM11 18h2");
}
function BotIcon() {
  return svg("M9 9h6v8H9zM12 6V3M8 13H5M19 13h-3");
}
function ChipIcon() {
  return svg("M8 8h8v8H8zM12 4v4M12 16v4M4 12h4M16 12h4");
}
function HomeIcon() {
  return svg("M4 11 12 4l8 7v9H4z");
}
function BoltIcon() {
  return svg("M13 3 6 13h5l-1 8 8-12h-5z");
}
function GridIcon() {
  return svg("M5 5h6v6H5zM13 5h6v6h-6zM5 13h6v6H5zM13 13h6v6h-6z");
}
function StarIcon() {
  return svg("M12 4l2 5h5l-4 3 2 5-5-3-5 3 2-5-4-3h5z");
}
function Crown() {
  return (
    <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor" aria-hidden="true">
      <path d="M3 17h18l-2-9-5 4-2-6-2 6-5-4z" />
    </svg>
  );
}
function Mark() {
  return (
    <span className="grid h-11 w-11 shrink-0 place-items-center rounded-2xl bg-[#10261a] text-[#3dff9a]">
      <svg viewBox="0 0 32 32" width="26" height="26" aria-hidden="true">
        <path fill="currentColor" d="M6 6h6l4 8 4-8h6v20h-6V16l-4 7-4-7v10H6z" />
      </svg>
    </span>
  );
}
function NWatermark() {
  return (
    <svg className="nt-mark" viewBox="0 0 120 140" aria-hidden="true">
      <path fill="#147a3d" d="M8 8h28l24 48 24-48h28v124H84V62L60 104 36 62v70H8z" />
    </svg>
  );
}

function ghostOf(session: Session, draft: string, repl: "python" | "node" | null) {
  if (repl || !draft) return "";
  return [...session.history].reverse().find((item) => item.startsWith(draft) && item !== draft) ?? "";
}

function shortPrompt(session: Session) {
  const home = session.home || "/root";
  if (session.cwd === home) return "~";
  if (session.cwd.startsWith(`${home}/`)) return `~${session.cwd.slice(home.length)}`;
  return session.cwd;
}

function PromptMark({ text }: { text: string }) {
  return <span className="text-[#e8e8e8]">{text} </span>;
}

function paintCode(source: string, path: string) {
  const words = path.endsWith(".py")
    ? new Set(["def", "class", "return", "if", "else", "elif", "for", "while", "import", "from", "in", "not", "and", "or", "True", "False", "None", "try", "except", "with", "pass"])
    : path.endsWith(".js") || path.endsWith(".ts")
      ? new Set(["function", "const", "let", "var", "return", "if", "else", "for", "while", "import", "from", "class", "new", "async", "await", "export", "true", "false", "null"])
      : path.endsWith(".sh")
        ? new Set(["if", "then", "else", "fi", "for", "while", "do", "done", "echo", "case", "esac"])
        : null;
  const mark = path.endsWith(".py") || path.endsWith(".sh") ? "#" : "//";
  return source.split("\n").map((line, index) => {
    const cut = words && line.includes(mark) ? line.indexOf(mark) : -1;
    const code = cut >= 0 ? line.slice(0, cut) : line;
    const note = cut >= 0 ? line.slice(cut) : "";
    const bits = code.split(/("[^"]*"|'[^']*'|\b[A-Za-z_]\w*\b)/g);
    return (
      <span key={index}>
        {bits.map((bit, bitIndex) => {
          if (!bit) return null;
          if (bit.startsWith("\"") || bit.startsWith("'")) return <span key={bitIndex} className="text-[#3dff9a]">{bit}</span>;
          if (words?.has(bit)) return <span key={bitIndex} className="text-[#e6c84a]">{bit}</span>;
          return <span key={bitIndex}>{bit}</span>;
        })}
        {note ? <span className="text-[#6d6d6d]">{note}</span> : null}
        {index < source.split("\n").length - 1 ? "\n" : ""}
      </span>
    );
  });
}

function paintLine(text: string, kind: "in" | "out" | "err") {
  if (kind === "in") {
    const dollar = text.indexOf(" $ ");
    if (dollar >= 0 && dollar < 48) return <span className="text-[#f2f2f2]">{text}</span>;
  }
  if (kind === "in" && (text.startsWith("$ ") || text.startsWith(">>> ") || text.startsWith("> "))) {
    const mark = text.startsWith("$ ") ? "$" : text.startsWith(">>> ") ? ">>>" : ">";
    const body = text.slice(mark.length + 1);
    const word = body.split(" ")[0] ?? "";
    const rest = body.slice(word.length);
    const bits = rest.split(/(".*?")/g);
    return (
      <>
        <span className="text-[#3dff9a]">{mark} </span>
        <span className="text-[#e6c84a]">{word}</span>
        {bits.map((bit, index) => (
          <span key={`${index}-${bit.slice(0, 12)}`} className={bit.startsWith("\"") ? "text-[#3dff9a]" : "text-[#d7dde3]"}>
            {bit}
          </span>
        ))}
      </>
    );
  }
  if (kind === "out" && text.includes("|__  _ __")) {
    return (
      <>
        {text.split("\n").map((row, index) => (
          <span key={index} className={index < 4 ? "block whitespace-pre leading-4 text-white" : "block whitespace-pre text-[#d7d7d7]"}>
            {index < 4 ? row || " " : linkText(row)}
          </span>
        ))}
      </>
    );
  }
  if (kind === "err") return <span className="text-[#ff6b6b]">{linkText(text)}</span>;
  if (kind !== "in") return <span className="text-[#d7d7d7]">{linkText(text)}</span>;
  const cut = text.indexOf(" ");
  if (cut < 1) return <span className="text-[#f2f2f2]">{text}</span>;
  return (
    <>
      <PromptMark text={text.slice(0, cut)} />
      <span className="text-[#f2f2f2]">{text.slice(cut + 1)}</span>
    </>
  );
}

function linkText(text: string) {
  const parts = text.split(/(https:\/\/[^\s]+)/g);
  return parts.map((part, index) =>
    part.startsWith("https://") ? (
      <a key={`${index}-${part}`} className="text-[#6cb6ff] underline" href={part} target="_blank" rel="noreferrer">
        {part}
      </a>
    ) : (
      <span key={`${index}-t`}>{part}</span>
    ),
  );
}

function pem(label: string, bytes: ArrayBuffer) {
  let raw = "";
  for (const byte of new Uint8Array(bytes)) raw += String.fromCharCode(byte);
  const body = btoa(raw).match(/.{1,64}/g)?.join("\n") ?? btoa(raw);
  return `-----BEGIN ${label}-----\n${body}\n-----END ${label}-----\n`;
}

async function createKey(path: string) {
  void path;
  const pair = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
  const pubBytes = await crypto.subtle.exportKey("spki", pair.publicKey);
  const digest = await crypto.subtle.digest("SHA-256", pubBytes);
  const fingerprint = [...new Uint8Array(digest)].slice(0, 12).map((byte) => byte.toString(16).padStart(2, "0")).join(":");
  return {
    priv: pem("PRIVATE KEY", await crypto.subtle.exportKey("pkcs8", pair.privateKey)),
    pub: pem("PUBLIC KEY", pubBytes),
    fingerprint,
  };
}

let speechVolume = 1;
let player: HTMLAudioElement | null = null;
let phoneNotes: Notification[] = [];
let torchTrack: MediaStreamTrack | null = null;

async function fetchDownload(url: string): Promise<{ ok: true; text?: string; message: string } | { ok: false; error: string }> {
  try {
    const response = await fetch(url);
    if (!response.ok) return { ok: false, error: "endirilmədi" };
    const type = response.headers.get("content-type") ?? "";
    if (type.startsWith("text") || type.includes("json") || type.includes("xml") || type.includes("javascript")) {
      const text = await response.text();
      if (text.length > 200_000) return { ok: false, error: "fayl çox böyükdür" };
      return { ok: true, text, message: "" };
    }
    const blob = await response.blob();
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = "nibras-download";
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(link.href), 4000);
    return { ok: true, message: "telefon endirdi" };
  } catch {
    return { ok: false, error: "endirilmədi" };
  }
}

function takePhoto() {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "image/*";
  input.capture = "environment";
  return new Promise<string>((resolve) => {
    input.onchange = () => {
      const file = input.files?.[0];
      if (!file) {
        resolve("!şəkil seçilmədi");
        return;
      }
      const link = document.createElement("a");
      link.href = URL.createObjectURL(file);
      link.download = file.name || "nibras.jpg";
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(link.href), 4000);
      resolve(file.name || "şəkil saxlandı");
    };
    window.addEventListener("focus", () => window.setTimeout(() => resolve(input.files?.[0] ? input.files[0].name : "!şəkil seçilmədi"), 500), { once: true });
    input.click();
  });
}

async function setTorch(on: boolean) {
  if (!on) {
    torchTrack?.stop();
    torchTrack = null;
    return "fənər söndü";
  }
  if (!navigator.mediaDevices?.getUserMedia) return "!fənər yoxdur";
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
    const track = stream.getVideoTracks()[0];
    if (!track) return "!fənər yoxdur";
    await track.applyConstraints({ advanced: [{ torch: true } as MediaTrackConstraintSet] });
    torchTrack = track;
    return "fənər yanır";
  } catch {
    return "!fənər yoxdur";
  }
}

function closeNote(title: string) {
  const found = phoneNotes.filter((note) => note.title === title);
  found.forEach((note) => note.close());
  phoneNotes = phoneNotes.filter((note) => note.title !== title);
  return found.length ? title : "!bildiriş yoxdur";
}

async function cameraInfo() {
  if (!navigator.mediaDevices?.enumerateDevices) return "!kamera yoxdur";
  const devices = await navigator.mediaDevices.enumerateDevices();
  const cams = devices.filter((item) => item.kind === "videoinput");
  if (!cams.length) return "!kamera yoxdur";
  return cams.map((item, index) => `${index} ${item.label || "kamera"}`).join("\n");
}

function recordMic(seconds: number) {
  if (!navigator.mediaDevices?.getUserMedia) return Promise.resolve("!mikrofon yoxdur");
  return navigator.mediaDevices.getUserMedia({ audio: true }).then(
    (stream) =>
      new Promise<string>((resolve) => {
        const rec = new MediaRecorder(stream);
        const chunks: Blob[] = [];
        rec.ondataavailable = (event) => {
          if (event.data.size) chunks.push(event.data);
        };
        rec.onstop = () => {
          stream.getTracks().forEach((track) => track.stop());
          const blob = new Blob(chunks, { type: rec.mimeType || "audio/webm" });
          const link = document.createElement("a");
          link.href = URL.createObjectURL(blob);
          link.download = "nibras.webm";
          link.click();
          window.setTimeout(() => URL.revokeObjectURL(link.href), 4000);
          resolve(`${seconds} san · nibras.webm`);
        };
        rec.start();
        window.setTimeout(() => rec.stop(), seconds * 1000);
      }),
    () => "!mikrofon yoxdur",
  );
}

function playUrl(url: string) {
  stopPlay();
  player = new Audio(url);
  player.volume = speechVolume;
  return player.play().then(
    () => url,
    () => "!səs açılmadı",
  );
}

function stopPlay() {
  player?.pause();
  player = null;
  return "səs dayandı";
}

async function askFingerprint() {
  if (!window.PublicKeyCredential) return "!barmaq izi yoxdur";
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  try {
    const cred = await navigator.credentials.create({
      publicKey: {
        challenge: bytes,
        rp: { name: "Nibras" },
        user: { id: bytes, name: "nibras", displayName: "Nibras" },
        pubKeyCredParams: [{ type: "public-key", alg: -7 }],
        authenticatorSelection: { authenticatorAttachment: "platform", userVerification: "required" },
        timeout: 20000,
      },
    });
    return cred ? "təsdiqləndi" : "!barmaq izi yoxdur";
  } catch {
    return "!barmaq izi yoxdur";
  }
}

let screenLock: { release: () => Promise<void> } | null = null;

type DirHandle = FileSystemDirectoryHandle & {
  entries: () => AsyncIterable<[string, FileSystemHandle]>;
  removeEntry: (name: string) => Promise<void>;
};

type PhoneStore = {
  prefix: string;
  root: DirHandle;
  files: Map<string, FileSystemFileHandle>;
  dirs: Map<string, DirHandle>;
  snap: Map<string, string>;
};

let phoneStore: PhoneStore | null = null;

function downloadBackup(session: Session) {
  const home = session.home || "/root";
  const files: Record<string, string> = {};
  for (const [path, body] of Object.entries(session.files)) {
    if (!path.startsWith(`${home}/`) || body.length > 100_000 || body.includes("\u0000")) continue;
    files[path] = body;
    if (Object.keys(files).length >= 200) break;
  }
  const blob = new Blob([JSON.stringify({ env: session.env, pkgs: session.pkgs, files })], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "nibras-backup.json";
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 4000);
  return `${Object.keys(files).length} fayl saxlandı · nibras-backup.json`;
}

async function restoreBackup(): Promise<{ ok: true; files: Record<string, string>; env: Session["env"]; pkgs: string[]; message: string } | { ok: false; error: string }> {
  const input = document.createElement("input");
  input.type = "file";
  input.accept = "application/json,.json";
  const file = await new Promise<File | null>((resolve) => {
    input.onchange = () => resolve(input.files?.[0] ?? null);
    window.addEventListener("focus", () => window.setTimeout(() => resolve(input.files?.[0] ?? null), 400), { once: true });
    input.click();
  });
  if (!file) return { ok: false, error: "fayl seçilmədi" };
  try {
    const data = JSON.parse(await file.text()) as { env?: string; pkgs?: unknown; files?: unknown };
    if (!data.files || typeof data.files !== "object") return { ok: false, error: "ehtiyat səhvdir" };
    const files: Record<string, string> = {};
    for (const [path, body] of Object.entries(data.files)) {
      if (!path.startsWith("/") || path.includes("..") || typeof body !== "string" || body.length > 100_000) continue;
      files[path] = body;
    }
    const env = data.env === "alpine" || data.env === "debian" || data.env === "ubuntu" ? data.env : null;
    const pkgs = Array.isArray(data.pkgs) ? data.pkgs.filter((item): item is string => typeof item === "string").slice(0, 40) : [];
    return { ok: true, files, env, pkgs, message: `${Object.keys(files).length} fayl qayıtdı` };
  } catch {
    return { ok: false, error: "ehtiyat səhvdir" };
  }
}

function listenSpeech() {
  const Box = window as Window & { SpeechRecognition?: new () => SpeechRec; webkitSpeechRecognition?: new () => SpeechRec };
  const Ctor = Box.SpeechRecognition || Box.webkitSpeechRecognition;
  if (!Ctor) return Promise.resolve("!səs tanınmır");
  return new Promise<string>((resolve) => {
    const rec = new Ctor();
    rec.lang = "az-AZ";
    rec.onresult = (event) => resolve(event.results[0]?.[0]?.transcript || "!səs tanınmır");
    rec.onerror = () => resolve("!səs tanınmır");
    try {
      rec.start();
    } catch {
      resolve("!səs tanınmır");
    }
  });
}

type SpeechRec = {
  lang: string;
  start: () => void;
  onresult: ((event: { results: Array<Array<{ transcript: string }>> }) => void) | null;
  onerror: (() => void) | null;
};

async function readSensor() {
  const motion = await new Promise<DeviceMotionEvent | null>((resolve) => {
    const timer = window.setTimeout(() => resolve(null), 1200);
    window.addEventListener(
      "devicemotion",
      (event) => {
        window.clearTimeout(timer);
        resolve(event);
      },
      { once: true },
    );
  });
  const acc = motion?.accelerationIncludingGravity;
  if (acc?.x == null || acc.y == null || acc.z == null) return "!sensor yoxdur";
  return `x ${acc.x.toFixed(2)} · y ${acc.y.toFixed(2)} · z ${acc.z.toFixed(2)}`;
}

async function installPip(py: Pyodide, name: string) {
  if (!py.loadPackage || !py.pyimport) throw new Error("pip yoxdur");
  await py.loadPackage("micropip");
  const micropip = py.pyimport("micropip");
  await micropip.install(name);
}

async function mountPhoneStorage(): Promise<{ ok: true; files: Record<string, string>; message: string } | { ok: false; error: string }> {
  const picker = (window as Window & { showDirectoryPicker?: () => Promise<DirHandle> }).showDirectoryPicker;
  if (!picker) return { ok: false, error: "bu brauzer qovluq açmır" };
  let root: DirHandle;
  try {
    root = await picker();
  } catch {
    return { ok: false, error: "qovluq seçilmədi" };
  }
  const store: PhoneStore = { prefix: "", root, files: new Map(), dirs: new Map(), snap: new Map() };
  const files: Record<string, string> = {};
  const budget = { n: 80 };
  await walkStore(root, "", store, files, budget);
  phoneStore = store;
  const count = Object.keys(files).length;
  return { ok: true, files, message: budget.n <= 0 ? `${count} fayl açıldı · limiti keçdi` : `${count} fayl açıldı · ~/storage/shared` };
}

async function walkStore(dir: DirHandle, rel: string, store: PhoneStore, files: Record<string, string>, budget: { n: number }) {
  store.dirs.set(rel, dir);
  for await (const [name, handle] of dir.entries()) {
    if (budget.n <= 0 || name.startsWith(".")) continue;
    const next = rel ? `${rel}/${name}` : name;
    if (next.split("/").length > 6) continue;
    if (handle.kind === "directory") {
      await walkStore(handle as DirHandle, next, store, files, budget);
      continue;
    }
    const fileHandle = handle as FileSystemFileHandle;
    const file = await fileHandle.getFile();
    if (file.size > 150_000) continue;
    const text = await file.text();
    if (text.includes("\u0000")) continue;
    files[next] = text;
    store.files.set(next, fileHandle);
    store.snap.set(next, text);
    budget.n -= 1;
  }
}

async function syncPhoneStorage(session: Session) {
  const store = phoneStore;
  if (!store) return;
  const prefix = `${session.home || "/root"}/storage/shared`;
  store.prefix = prefix;
  try {
    for (const [rel, prev] of [...store.snap.entries()]) {
      const now = session.files[`${prefix}/${rel}`];
      if (now === undefined) {
        const folder = rel.includes("/") ? rel.slice(0, rel.lastIndexOf("/")) : "";
        const name = rel.slice(folder.length ? folder.length + 1 : 0);
        await store.dirs.get(folder)?.removeEntry(name);
        store.snap.delete(rel);
        store.files.delete(rel);
        continue;
      }
      if (now !== prev) await writeStored(store, rel, now);
    }
    for (const [path, body] of Object.entries(session.files)) {
      if (!path.startsWith(`${prefix}/`)) continue;
      const rel = path.slice(prefix.length + 1);
      if (store.snap.get(rel) === body) continue;
      await writeStored(store, rel, body);
    }
  } catch {
    phoneStore = null;
  }
}

async function writeStored(store: PhoneStore, rel: string, body: string) {
  const folder = rel.includes("/") ? rel.slice(0, rel.lastIndexOf("/")) : "";
  const name = rel.slice(folder.length ? folder.length + 1 : 0);
  let dir = store.dirs.get(folder);
  if (!dir) {
    dir = store.root;
    if (folder) {
      for (const part of folder.split("/")) dir = (await dir.getDirectoryHandle(part, { create: true })) as DirHandle;
      store.dirs.set(folder, dir);
    }
  }
  const handle = await dir.getFileHandle(name, { create: true });
  const writable = await handle.createWritable();
  await writable.write(body);
  await writable.close();
  store.files.set(rel, handle);
  store.snap.set(rel, body);
}

function pickVideos() {
  return new Promise<File[]>((resolve) => {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = "video/*";
    input.multiple = true;
    let done = false;
    const finish = (files: File[]) => {
      if (done) return;
      done = true;
      resolve(files);
    };
    input.onchange = () => finish([...(input.files ?? [])]);
    window.addEventListener(
      "focus",
      () => {
        window.setTimeout(() => finish([...(input.files ?? [])]), 400);
      },
      { once: true },
    );
    input.click();
  });
}

function playClip(file: File, canvas: HTMLCanvasElement, ctx: CanvasRenderingContext2D, audio: AudioContext, dest: MediaStreamAudioDestinationNode) {
  return new Promise<void>((resolve, reject) => {
    const video = document.createElement("video");
    video.playsInline = true;
    video.src = URL.createObjectURL(file);
    video.onerror = () => reject(new Error("video açılmadı"));
    video.onloadeddata = () => {
      let source: MediaElementAudioSourceNode | null = null;
      try {
        source = audio.createMediaElementSource(video);
        source.connect(dest);
      } catch {
        source = null;
      }
      const paint = () => {
        if (video.ended || video.paused) return;
        ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
        requestAnimationFrame(paint);
      };
      video.onended = () => {
        source?.disconnect();
        URL.revokeObjectURL(video.src);
        resolve();
      };
      const start = () => video.play().then(paint);
      start().catch(() => {
        video.muted = true;
        start().catch(() => reject(new Error("video açılmadı")));
      });
    };
  });
}

async function pickContacts() {
  const nav = navigator as Navigator & { contacts?: { select: (fields: string[], opts?: { multiple?: boolean }) => Promise<Array<{ name?: string[]; tel?: string[] }>> } };
  if (!nav.contacts?.select) return "!kontakt yoxdur";
  try {
    const rows = await nav.contacts.select(["name", "tel"], { multiple: true });
    return rows.map((row) => `${row.name?.[0] ?? ""} ${row.tel?.[0] ?? ""}`.trim()).filter(Boolean).join("\n") || "kontakt yoxdur";
  } catch {
    return "!kontakt yoxdur";
  }
}

async function phoneInfo() {
  const nav = navigator as Navigator & {
    connection?: { effectiveType?: string; downlink?: number };
    userAgentData?: { mobile?: boolean; platform?: string; getHighEntropyValues?: (hints: string[]) => Promise<{ model?: string; platformVersion?: string }> };
  };
  let model = "";
  let version = "";
  try {
    const high = await nav.userAgentData?.getHighEntropyValues?.(["model", "platformVersion"]);
    model = high?.model ?? "";
    version = high?.platformVersion ?? "";
  } catch {
    model = "";
  }
  return [`model ${model || "məlum deyil"}`, `platform ${nav.userAgentData?.platform ?? navigator.platform}`, version ? `versiya ${version}` : "", `şəbəkə ${nav.connection?.effectiveType ?? "məlum deyil"}`].filter(Boolean).join("\n");
}

async function usbDevices() {
  const nav = navigator as Navigator & { usb?: { getDevices: () => Promise<Array<{ productName?: string; vendorId: number }>> } };
  if (!nav.usb) return "!usb yoxdur";
  try {
    const rows = await nav.usb.getDevices();
    return rows.map((row) => row.productName || String(row.vendorId)).join("\n") || "usb yoxdur";
  } catch {
    return "!usb yoxdur";
  }
}

async function mergeVideos() {
  const files = await pickVideos();
  if (files.length < 2) return "!iki video seç";
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  if (!ctx) return "!video birləşmədi";
  const probe = document.createElement("video");
  probe.preload = "metadata";
  probe.src = URL.createObjectURL(files[0]);
  await new Promise<void>((resolve, reject) => {
    probe.onloadedmetadata = () => resolve();
    probe.onerror = () => reject(new Error("video açılmadı"));
  }).catch(() => undefined);
  canvas.width = probe.videoWidth || 640;
  canvas.height = probe.videoHeight || 360;
  URL.revokeObjectURL(probe.src);
  const audio = new AudioContext();
  await audio.resume();
  const dest = audio.createMediaStreamDestination();
  const stream = canvas.captureStream(30);
  dest.stream.getAudioTracks().forEach((track) => stream.addTrack(track));
  const mime = MediaRecorder.isTypeSupported("video/webm") ? "video/webm" : "";
  let rec: MediaRecorder;
  try {
    rec = new MediaRecorder(stream, mime ? { mimeType: mime } : undefined);
  } catch {
    stream.getTracks().forEach((track) => track.stop());
    await audio.close();
    return "!video birləşmədi";
  }
  const chunks: Blob[] = [];
  rec.ondataavailable = (event) => {
    if (event.data.size) chunks.push(event.data);
  };
  const stopped = new Promise<void>((resolve) => {
    rec.onstop = () => resolve();
  });
  rec.start();
  try {
    for (const file of files) await playClip(file, canvas, ctx, audio, dest);
  } catch {
    rec.stop();
    stream.getTracks().forEach((track) => track.stop());
    await audio.close();
    return "!video açılmadı";
  }
  rec.stop();
  await stopped;
  stream.getTracks().forEach((track) => track.stop());
  await audio.close();
  const blob = new Blob(chunks, { type: mime || "video/webm" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = "nibras.webm";
  link.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 4000);
  return `${files.length} video birləşdi · nibras.webm`;
}

async function runDevice(device: NonNullable<ShellOut["device"]>) {
  if (device.kind === "clip-set") {
    try {
      await navigator.clipboard.writeText(device.text);
      return "lövhəyə yazıldı";
    } catch {
      return "!lövhə yazılmadı";
    }
  }
  if (device.kind === "clip-get") {
    try {
      return (await navigator.clipboard.readText()) || "lövhə boşdur";
    } catch {
      return "!lövhə oxunmadı";
    }
  }
  if (device.kind === "toast") return device.text;
  if (device.kind === "vibrate") return navigator.vibrate?.(device.ms) === false ? "!titrəmə yoxdur" : `${device.ms} ms`;
  if (device.kind === "contacts") return pickContacts();
  if (device.kind === "telephony") return phoneInfo();
  if (device.kind === "usb") return usbDevices();
  if (device.kind === "battery") {
    const nav = navigator as Navigator & { getBattery?: () => Promise<{ level: number; charging: boolean }> };
    if (!nav.getBattery) return "!batareya oxunmur";
    try {
      const bat = await nav.getBattery();
      return `${Math.round(bat.level * 100)}% · ${bat.charging ? "doldurulur" : "batareya"}`;
    } catch {
      return "!batareya oxunmur";
    }
  }
  if (device.kind === "speak") {
    if (!window.speechSynthesis) return "!səs yoxdur";
    window.speechSynthesis.cancel();
    const utter = new SpeechSynthesisUtterance(device.text);
    utter.volume = speechVolume;
    window.speechSynthesis.speak(utter);
    return device.text;
  }
  if (device.kind === "open") {
    window.open(device.url, "_blank", "noopener,noreferrer");
    return device.url;
  }
  if (device.kind === "wake-lock") {
    const nav = navigator as Navigator & { wakeLock?: { request: (kind: "screen") => Promise<{ release: () => Promise<void> }> } };
    if (!nav.wakeLock) return "!oyaqlıq yoxdur";
    try {
      screenLock = await nav.wakeLock.request("screen");
      return "ekran oyaqdır";
    } catch {
      return "!oyaqlıq yoxdur";
    }
  }
  if (device.kind === "wake-unlock") {
    await screenLock?.release();
    screenLock = null;
    return "ekran buraxıldı";
  }
  if (device.kind === "share") {
    if (navigator.share) {
      try {
        await navigator.share({ text: device.text });
        return "paylaşıldı";
      } catch {
        return "!paylaşılmadı";
      }
    }
    try {
      await navigator.clipboard.writeText(device.text);
      return "lövhəyə yazıldı";
    } catch {
      return "!paylaşılmadı";
    }
  }
  if (device.kind === "network") {
    const nav = navigator as Navigator & { connection?: { effectiveType?: string; downlink?: number; rtt?: number } };
    const link = nav.connection;
    if (!link) return "!şəbəkə oxunmur";
    return `${link.effectiveType ?? "şəbəkə"} · ${link.downlink ?? "?"} Mb/s · ${link.rtt ?? "?"} ms`;
  }
  if (device.kind === "video") return mergeVideos();
  if (device.kind === "listen") return listenSpeech();
  if (device.kind === "sensor") return readSensor();
  if (device.kind === "camera-info") return cameraInfo();
  if (device.kind === "mic") return recordMic(device.seconds);
  if (device.kind === "play") return playUrl(device.url);
  if (device.kind === "play-stop") return stopPlay();
  if (device.kind === "notify-list") return phoneNotes.map((note) => note.title).join("\n") || "bildiriş yoxdur";
  if (device.kind === "fingerprint") return askFingerprint();
  if (device.kind === "camera") return takePhoto();
  if (device.kind === "torch") return setTorch(device.on);
  if (device.kind === "notify-remove") return closeNote(device.title);
  if (!("Notification" in window) || device.kind !== "notify") return "!bildiriş yoxdur";
  const permit = Notification.permission === "granted" ? "granted" : await Notification.requestPermission();
  if (permit !== "granted") return "!bildiriş bağlıdır";
  const note = new Notification(device.title, { body: device.body });
  phoneNotes.push(note);
  return device.title;
}
