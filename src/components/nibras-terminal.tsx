import { useEffect, useRef, useState, type KeyboardEvent } from "react";
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
  extraKeyRows,
  formatBytes,
  hydrate,
  importRepo,
  saveRemoteFile,
  isMarked,
  listHere,
  packHere,
  prompt,
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

const WELCOME = "Nibras Terminal\nƏmr yaz, Seçimlərdən bas, lazım olsa xana açılır.\nİlk addım: mühit seç.";

declare global {
  interface Window {
    loadPyodide?: (opts: { indexURL: string }) => Promise<Pyodide>;
  }
}

type Pyodide = {
  runPythonAsync: (code: string) => Promise<unknown>;
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
    if (!Array.isArray(parsed) || !parsed.length) return [{ kind: "out", text: WELCOME }];
    return parsed
      .filter((line) => line && (line.kind === "in" || line.kind === "out" || line.kind === "err") && typeof line.text === "string")
      .slice(-80)
      .map((line) => ({ ...line, text: clipText(line.text) }));
  } catch {
    return [{ kind: "out", text: WELCOME }];
  }
}

export function NibrasTerminal() {
  const [session, setSession] = useState<Session>(emptySession);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>("term");
  const [lines, setLines] = useState<Line[]>([{ kind: "out", text: WELCOME }]);
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
  const [menu, setMenu] = useState(false);
  const [hunt, setHunt] = useState("");
  const [mark, setMark] = useState("");
  const [bell, setBell] = useState(false);
  const [scriptOpen, setScriptOpen] = useState(false);
  const [scriptText, setScriptText] = useState("echo bir\necho iki");
  const [needle, setNeedle] = useState("");
  const [size, setSize] = useState(1);
  const [lang, setLang] = useState<Lang>("az");
  const [langBusy, setLangBusy] = useState<Lang | "">("");
  const scroller = useRef<HTMLDivElement>(null);
  const box = useRef<HTMLInputElement>(null);
  const nodeScope = useRef<Record<string, unknown>>({});

  useEffect(() => {
    const loaded = loadSession();
    setSession(loaded);
    setLines(loadLines());
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
        setBusy("Python yüklənir…");
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
        setBusy("PHP yüklənir…");
        const php = await loadPhp();
        await php.run(phpSource(current, result.php));
        const text = php.take().replace(/\s+$/, "");
        if (text) push([{ kind: "out", text }]);
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
    if (result.device) {
      const text = await runDevice(result.device);
      if (result.device.kind === "toast") setCopied(result.device.text);
      if (text) push([{ kind: text.startsWith("!") ? "err" : "out", text: text.replace(/^!/, "") }]);
    }
    setSession(current);
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
    const lead = repl === "python" ? ">>>" : repl === "node" ? ">" : prompt(base);
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
    push([{ kind: "in", text: `${prompt(next)} ${body}` }]);
    void apply(runLine(next, "sh .nibras.sh"));
  }

  function onKey(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "Tab") {
      event.preventDefault();
      const next = complete(session, draft)[0];
      if (next) setDraft(next);
      return;
    }
    if (event.ctrlKey && !event.altKey) {
      const key = event.key.toLowerCase();
      if (key === "r" && !repl) {
        event.preventDefault();
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
        event.preventDefault();
        submit("clear");
        return;
      }
      if (key === "c") {
        event.preventDefault();
        if (repl) {
          setRepl(null);
          push([{ kind: "out", text: "çıxdı" }]);
        } else setDraft("");
        return;
      }
      if (key === "a" || key === "e") {
        event.preventDefault();
        const pos = key === "a" ? 0 : draft.length;
        requestAnimationFrame(() => box.current?.setSelectionRange(pos, pos));
        return;
      }
      if (key === "u") {
        event.preventDefault();
        setDraft("");
        return;
      }
      if (key === "w") {
        event.preventDefault();
        setDraft((value) => value.replace(/\s*\S+\s*$/, ""));
        return;
      }
      if (key === "k") {
        event.preventDefault();
        const pos = box.current?.selectionStart ?? draft.length;
        setDraft(draft.slice(0, pos));
        return;
      }
    }
    if (event.key === "Escape") {
      setHunt("");
      return;
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
    const next = { name: String(slots.length + 1), session: fresh, lines: [{ kind: "out" as const, text: WELCOME }] };
    setSlots((list) => [...list.map((item, index) => (index === slot ? { ...item, session, lines } : item)), next]);
    setSlot(slots.length);
    setSession(fresh);
    setLines(next.lines);
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
      <div className={`nt-phone ${bell ? "outline outline-1 outline-[#ff6b6b]" : ""}`}>
        <header className="nt-title">
          <nav className="flex shrink-0 gap-3">
            <Nav label={say(lang, "Terminal")} active={tab === "term"} onClick={() => setTab("term")} />
            <Nav label={say(lang, "Seçimlər")} active={tab === "pick"} onClick={() => setTab("pick")} />
            <Nav label={say(lang, "Fayl")} active={tab === "file"} onClick={() => setTab("file")} />
          </nav>
          <span className="min-w-0 flex-1 truncate text-xs text-[#6d6d6d]">{prompt(session)}</span>
          <button className="nt-key on h-7 min-w-7" type="button" onClick={() => setMenu((value) => !value)} aria-label="menu">
            ≡
          </button>
        </header>
        {menu ? (
          <div className="shrink-0 space-y-2 border-b border-[#222] bg-[#141414] px-3 py-2 font-mono text-xs text-[#cfcfcf]">
            <div className="flex flex-wrap items-center gap-2">
              {LANGS.map((code) => (
                <button key={code} className={`nt-key ${code === lang ? "on" : ""}`} type="button" onClick={() => void pickLang(code)}>
                  {langBusy === code ? "…" : code}
                </button>
              ))}
              <button className="nt-key" type="button" onClick={() => setSize((value) => (value === 0 ? 0 : value - 1))}>A−</button>
              <button className="nt-key" type="button" onClick={() => setSize((value) => (value === 2 ? 2 : value + 1))}>A+</button>
              <button className="nt-key" type="button" onClick={() => { setScriptOpen(true); setMenu(false); }}>{say(lang, "Skript")}</button>
              <button className="nt-key" type="button" onClick={() => setPast((value) => !value)}>{say(lang, "Keçmiş")}</button>
              <button
                className="nt-key"
                type="button"
                onClick={() => {
                  const body = shown.map((line) => say(lang, line.text)).join("\n");
                  void navigator.clipboard?.writeText(body);
                }}
              >
                {say(lang, "Kopyala")}
              </button>
            </div>
            <input id="find" className="nt-field w-full" value={needle} placeholder={say(lang, "nəticədə axtar")} onChange={(event) => setNeedle(event.target.value)} />
            <p className="text-[#6d6d6d]">
              {session.env ?? say(lang, "mühit yoxdur")} · {formatBytes(usedBytes(session))} / {formatBytes(DISK_BUDGET)}
              {session.hasToken ? " · token" : ""}
            </p>
          </div>
        ) : null}
        <main className="flex min-h-0 flex-1 flex-col overflow-hidden">
          {tab === "term" ? (
            <div className="flex min-h-0 flex-1 flex-col" onClick={() => box.current?.focus()}>
              <div ref={scroller} className={`nt-screen ${sizeClass}`}>
                {shown.length ? null : <p className="text-[#6d6d6d]">{say(lang, "tapılmadı")}</p>}
                {shown.map((line, index) => (
                  <div key={`${line.kind}-${index}`} className="whitespace-pre-wrap break-words">
                    {paintLine(say(lang, line.text), line.kind)}
                  </div>
                ))}
                {busy ? <p className="text-[#8a8a8a]">{say(lang, busy)}</p> : null}
                {copied ? <p className="text-[#8fbf8a]">{say(lang, copied)}</p> : null}
                <form
                  className="nt-line"
                  onSubmit={(event) => {
                    event.preventDefault();
                    submit(draft);
                  }}
                >
                  <span className="shrink-0">
                    <PromptMark text={repl === "python" ? ">>>" : repl === "node" ? ">" : prompt(session)} />
                  </span>
                  {session.envVars["?"] && session.envVars["?"] !== "0" ? <span className="text-[#ff6b6b]">{session.envVars["?"]}</span> : null}
                  {hunt ? <span className="text-[#6d6d6d]">keçmiş</span> : null}
                  {mark ? <span className="text-[#6d6d6d]">{mark}</span> : null}
                  <label className="sr-only" htmlFor="cmd">{say(lang, "Əmr")}</label>
                  <input
                    ref={box}
                    id="cmd"
                    className="nt-input"
                    value={draft}
                    disabled={Boolean(busy)}
                    autoCapitalize="none"
                    autoCorrect="off"
                    spellCheck={false}
                    onChange={(event) => setDraft(event.target.value)}
                    onKeyDown={onKey}
                  />
                </form>
              </div>
              {past && recent.length ? (
                <div className="max-h-28 overflow-y-auto border-t border-[#222] bg-[#101010]">
                  {recent.map((item, index) => (
                    <button key={`${index}-${item}`} className="block w-full px-3 py-1 text-left font-mono text-xs text-[#d7d7d7]" type="button" onClick={() => { setPast(false); submit(item); }}>
                      {item}
                    </button>
                  ))}
                </div>
              ) : null}
              {hints.length ? (
                <div className="flex gap-3 overflow-x-auto bg-[#101010] px-3 py-1 font-mono text-xs text-[#8a8a8a]">
                  {hints.map((item) => (
                    <button key={item} className="shrink-0" type="button" onClick={() => { setDraft(item); box.current?.focus(); }}>
                      {item.trim()}
                    </button>
                  ))}
                </div>
              ) : null}
              {session.server ? (
                <iframe title="Önizləmə" className="h-36 w-full border-t border-[#222] bg-[#0c0c0c]" sandbox="" srcDoc={session.server.html} />
              ) : null}
              <div className="nt-keys">
                <div className="mb-1 flex gap-1 overflow-x-auto">
                  {(slots.length ? slots : [{ name: "1", session, lines }]).map((item, index) => (
                    <button key={`${item.name}-${index}`} className={`nt-key ${index === slot ? "on" : ""}`} type="button" onClick={() => switchSlot(index)}>
                      {item.name}
                    </button>
                  ))}
                  <button className="nt-key" type="button" onClick={addSlot}>+</button>
                  <button className="nt-key" type="button" onClick={() => pressKey("PASTE")}>PASTE</button>
                </div>
                {extraKeyRows(session).map((row, rowIndex) => (
                  <div key={rowIndex} className="flex gap-1 overflow-x-auto py-0.5">
                    {row.map((key) => {
                      const on = (key.toUpperCase() === "CTRL" && mods.ctrl) || (key.toUpperCase() === "ALT" && mods.alt);
                      return (
                        <button key={`${rowIndex}-${key}`} className={`nt-key ${on ? "on" : ""}`} type="button" onClick={() => pressKey(key)}>
                          {key}
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
      <div className="mt-3 grid max-h-64 grid-cols-[auto_1fr] overflow-hidden border border-line">
        <pre
          ref={gutter}
          aria-hidden="true"
          className="overflow-hidden bg-bg px-2 py-3 text-right font-mono text-sm leading-6 text-muted"
        >
          {lines.map((_, index) => String(index + 1)).join("\n")}
        </pre>
        <textarea
          ref={editor}
          className="min-h-48 w-full resize-y overflow-auto bg-surface p-3 font-mono text-sm leading-6 text-fg outline-none"
          value={text}
          onChange={(event) => setText(event.target.value)}
          onScroll={() => {
            if (gutter.current && editor.current) gutter.current.scrollTop = editor.current.scrollTop;
          }}
        />
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

function Nav({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button className={`font-mono text-xs ${active ? "text-[#5fdf7a]" : "text-[#7a7a7a]"}`} type="button" onClick={onClick}>
      {label}
    </button>
  );
}

function PromptMark({ text }: { text: string }) {
  const match = /^(.*)@([^:]+):(.*)([#$])$/.exec(text);
  if (!match) return <span className="text-[#5fdf7a]">{text} </span>;
  return (
    <>
      <span className="text-[#5fdf7a]">{match[1]}@{match[2]}</span>
      <span className="text-[#6cb6ff]">:{match[3]}</span>
      <span className="text-[#f2f2f2]">{match[4]} </span>
    </>
  );
}

function paintLine(text: string, kind: "in" | "out" | "err") {
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

let screenLock: { release: () => Promise<void> } | null = null;

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
    window.speechSynthesis.speak(new SpeechSynthesisUtterance(device.text));
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
  if (!("Notification" in window)) return "!bildiriş yoxdur";
  const permit = Notification.permission === "granted" ? "granted" : await Notification.requestPermission();
  if (permit !== "granted") return "!bildiriş bağlıdır";
  new Notification(device.title, { body: device.body });
  return device.title;
}
