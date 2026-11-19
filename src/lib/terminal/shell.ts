import { md5, sha1, sha256 } from "./hash.ts";
import { runC } from "./c-run.ts";

export type EnvName = "alpine" | "debian" | "ubuntu";

export type Session = {
  cwd: string;
  dirs: string[];
  files: Record<string, string>;
  env: EnvName | null;
  pkgs: string[];
  gitName: string;
  gitEmail: string;
  gitRoot: string;
  remote: string;
  hasToken: boolean;
  commits: Array<{ message: string; files: string[] }>;
  staged: string[];
  history: string[];
  aliases: Record<string, string>;
  envVars: Record<string, string>;
  server: { port: number; html: string } | null;
  started: number;
  trash: Array<{ path: string; body: string }>;
  modes: Record<string, string>;
  branch: string;
  heads: Record<string, { files: Record<string, string>; base: Record<string, string> }>;
  hasSsh: boolean;
  user: string;
  home: string;
  links: Record<string, string>;
  shadow: Record<string, string>;
  functions: Record<string, string>;
  jobs: Array<{ id: number; cmd: string; state: string }>;
  oldpwd: string;
  stack: string[];
};

export type Ask = {
  id: "confirm" | "token" | "git-name" | "git-email" | "remote" | "text" | "ssh";
  title: string;
  label: string;
  secret?: boolean;
  resume: string;
};

export type GithubFile = { path: string; content: string };

export type ShellOut = {
  session: Session;
  output: string;
  ok?: boolean;
  ask?: Ask;
  open?: string;
  python?: { code: string; file?: string; argv?: string[] };
  node?: { code: string; file?: string; argv?: string[] };
  php?: { code: string; file?: string; version?: boolean };
  github?: { repo: string; message: string; files: GithubFile[] };
  repl?: "python" | "node";
  token?: string;
  clearToken?: boolean;
  fetch?: { url: string; dest: string };
  clone?: { repo: string; dest: string; pull: boolean };
  probe?: { host: string };
  remote?: { kind: "exec" | "get" | "put"; user: string; host: string; port: number; command?: string; remotePath?: string; localPath?: string; content?: string };
  keepSsh?: string;
  clearSsh?: boolean;
  sleep?: number;
  cont?: string;
  device?:
    | { kind: "clip-get" }
    | { kind: "clip-set"; text: string }
    | { kind: "toast"; text: string }
    | { kind: "vibrate"; ms: number }
    | { kind: "battery" }
    | { kind: "speak"; text: string }
    | { kind: "open"; url: string }
    | { kind: "notify"; title: string; body: string }
    | { kind: "wake-lock" }
    | { kind: "wake-unlock" }
    | { kind: "share"; text: string }
    | { kind: "network" };
  keygen?: { path: string };
};

export const DISK_BUDGET = 2 * 1024 * 1024 * 1024;

const SIZES: Record<string, number> = {
  alpine: 4_023_732,
  debian: 17_883_916,
  ubuntu: 18_207_820,
  python: 29_440_910,
  node: 33_053_848,
  git: 536,
  php: 12_500_000,
  build: 8_192,
};

const ENVS: EnvName[] = ["alpine", "debian", "ubuntu"];

export const ACTIONS: Array<{ group: string; label: string; line: string; mark?: string }> = [
  { group: "Mühit", label: "Alpine", line: "nibras install alpine", mark: "alpine" },
  { group: "Mühit", label: "Debian", line: "nibras install debian", mark: "debian" },
  { group: "Mühit", label: "Ubuntu", line: "nibras install ubuntu", mark: "ubuntu" },
  { group: "Mühit", label: "Mühiti sil", line: "nibras sil" },
  { group: "Alət", label: "Python", line: "nibras install python", mark: "python" },
  { group: "Alət", label: "Node", line: "nibras install node", mark: "node" },
  { group: "Alət", label: "PHP", line: "nibras install php", mark: "php" },
  { group: "Alət", label: "Git", line: "nibras install git", mark: "git" },
  { group: "Alət", label: "C", line: "nibras install build", mark: "build" },
  { group: "Layihə", label: "Python layihəsi", line: "nibras layihe python" },
  { group: "Layihə", label: "Node layihəsi", line: "nibras layihe node" },
  { group: "Layihə", label: "PHP layihəsi", line: "nibras layihe php" },
  { group: "Layihə", label: "C layihəsi", line: "nibras layihe c" },
  { group: "Layihə", label: "Qeyd", line: "nibras layihe md" },
  { group: "Layihə", label: "Dayandır", line: "nibras stop" },
  { group: "Fayl", label: "Yeni fayl", line: "edit" },
  { group: "Fayl", label: "Qovluq", line: "mkdir" },
  { group: "Fayl", label: "Boş qovluq", line: "rmdir" },
  { group: "Fayl", label: "Oxu", line: "cat" },
  { group: "Fayl", label: "Axtar", line: "grep" },
  { group: "Fayl", label: "Gətir", line: "curl" },
  { group: "Fayl", label: "Ağac", line: "tree" },
  { group: "Fayl", label: "Növ", line: "file" },
  { group: "Fayl", label: "Hex", line: "xxd" },
  { group: "Fayl", label: "Yoxlama", line: "sha256sum" },
  { group: "Fayl", label: "Markdown", line: "nibras md" },
  { group: "Fayl", label: "Arxiv", line: "tar" },
  { group: "Fayl", label: "Zibil", line: "nibras zibil" },
  { group: "Mətn", label: "Sütun", line: "awk" },
  { group: "Mətn", label: "Döngü", line: "for i in bir iki; do echo $i; done" },
  { group: "Mətn", label: "Fərq", line: "diff" },
  { group: "Mətn", label: "Bük", line: "fold" },
  { group: "Mətn", label: "Qarışdır", line: "shuf" },
  { group: "Mətn", label: "Faktor", line: "factor" },
  { group: "Mətn", label: "Hesabla", line: "expr" },
  { group: "Mətn", label: "Base64", line: "base64" },
  { group: "Mətn", label: "JSON", line: "jq" },
  { group: "Mətn", label: "Təqvim", line: "cal" },
  { group: "Git", label: "Depo başlat", line: "git init" },
  { group: "Git", label: "Vəziyyət", line: "git status" },
  { group: "Git", label: "Əlavə et", line: "git add ." },
  { group: "Git", label: "Commit", line: "git commit" },
  { group: "Git", label: "Ad", line: "git config user.name" },
  { group: "Git", label: "Poçt", line: "git config user.email" },
  { group: "Git", label: "GitHub deposu", line: "git remote add origin" },
  { group: "Git", label: "Klonla", line: "git clone" },
  { group: "Git", label: "Çək", line: "git pull" },
  { group: "Git", label: "Budaq", line: "git branch" },
  { group: "Şəbəkə", label: "Ping", line: "ping" },
  { group: "Şəbəkə", label: "SSH", line: "ssh" },
  { group: "Sistem", label: "Sistem", line: "lsb_release -a" },
  { group: "Sistem", label: "Kim", line: "whoami" },
  { group: "Telefon", label: "Yaddaş", line: "nibras-setup-storage" },
  { group: "Telefon", label: "Batareya", line: "nibras-battery-status" },
  { group: "Telefon", label: "Paket", line: "pkg list-all" },
  { group: "Telefon", label: "Oyaq", line: "nibras-wake-lock" },
  { group: "Telefon", label: "Şəbəkə", line: "nibras-wifi-connectioninfo" },
  { group: "Telefon", label: "Paylaş", line: "nibras-share" },
  { group: "Hesab", label: "Dəyər", line: "read" },
  { group: "Hesab", label: "Ləqəb", line: "alias" },
  { group: "Hesab", label: "Tokeni sil", line: "nibras token sil" },
  { group: "Hesab", label: "Disk", line: "nibras disk" },
  { group: "Hesab", label: "Təmizlə", line: "clear" },
  { group: "Hesab", label: "Kömək", line: "help" },
];

export function emptySession(): Session {
  return {
    cwd: "/root",
    dirs: ["/", "/root", "/root/Projects"],
    files: {
      "/root/README.txt":
        "Nibras Terminal\nƏmr yaz, Seçimlər düyməsinə bas, lazım olsa xanaya yaz.\nFayl redaktoru ilə terminal eyni qovluqdadır.\n",
    },
    env: null,
    pkgs: [],
    gitName: "",
    gitEmail: "",
    gitRoot: "",
    remote: "",
    hasToken: false,
    commits: [],
    staged: [],
    history: [],
    aliases: {},
    envVars: { HOME: "/root", USER: "root", TERM: "nibras" },
    server: null,
    started: Date.now(),
    trash: [],
    modes: {},
    branch: "main",
    heads: {},
    hasSsh: false,
    user: "root",
    home: "/root",
    links: {},
    shadow: {},
    functions: {},
    jobs: [],
    oldpwd: "",
    stack: [],
  };
}

export function hydrate(raw: unknown): Session {
  const base = emptySession();
  if (!raw || typeof raw !== "object") return base;
  const parsed = raw as Partial<Session> & { staged?: unknown };
  const files = parsed.files && typeof parsed.files === "object" ? parsed.files : base.files;
  const dirs = Array.isArray(parsed.dirs) ? parsed.dirs.filter((item) => typeof item === "string") : base.dirs;
  const env = parsed.env === "alpine" || parsed.env === "debian" || parsed.env === "ubuntu" ? parsed.env : null;
  let staged: string[] = [];
  if (Array.isArray(parsed.staged)) staged = parsed.staged.filter((item) => typeof item === "string");
  else if (parsed.staged === true) staged = Object.keys(files);
  return {
    ...base,
    cwd: typeof parsed.cwd === "string" ? parsed.cwd : base.cwd,
    dirs: dirs.includes("/") ? dirs : ["/", ...dirs],
    files,
    env,
    pkgs: Array.isArray(parsed.pkgs) ? parsed.pkgs.filter((item) => typeof item === "string") : [],
    gitName: typeof parsed.gitName === "string" ? parsed.gitName : "",
    gitEmail: typeof parsed.gitEmail === "string" ? parsed.gitEmail : "",
    gitRoot: typeof parsed.gitRoot === "string" ? parsed.gitRoot : "",
    remote: typeof parsed.remote === "string" ? parsed.remote : "",
    hasToken: false,
    commits: Array.isArray(parsed.commits) ? parsed.commits : [],
    staged,
    history: Array.isArray(parsed.history) ? parsed.history.filter((item) => typeof item === "string").slice(-80) : [],
    aliases: aliasesFrom(parsed.aliases),
    envVars: { ...base.envVars, ...(parsed.envVars && typeof parsed.envVars === "object" ? parsed.envVars : {}) },
    server: parsed.server && typeof parsed.server === "object" && typeof parsed.server.html === "string" ? parsed.server : null,
    started: typeof parsed.started === "number" && Number.isFinite(parsed.started) ? parsed.started : Date.now(),
    trash: trashFrom(parsed.trash),
    modes: modesFrom(parsed.modes),
    branch: typeof parsed.branch === "string" && /^[\w.-]+$/.test(parsed.branch) ? parsed.branch : "main",
    heads: headsFrom(parsed.heads),
    hasSsh: false,
    user: typeof parsed.user === "string" && /^[\w.-]+$/.test(parsed.user) ? parsed.user : "root",
    home: typeof parsed.home === "string" && parsed.home.startsWith("/") ? parsed.home : "/root",
    links: stringMap(parsed.links, 200),
    shadow: stringMap(parsed.shadow, 64),
    functions: stringMap(parsed.functions, 500),
    jobs: jobsFrom(parsed.jobs),
    oldpwd: typeof parsed.oldpwd === "string" ? parsed.oldpwd : "",
    stack: Array.isArray(parsed.stack) ? parsed.stack.filter((item) => typeof item === "string").slice(-20) : [],
  };
}

export function isMarked(session: Session, mark?: string) {
  if (!mark) return false;
  if (mark === session.env) return true;
  return session.pkgs.includes(mark);
}

export function suggestions(session: Session): Array<{ label: string; line: string }> {
  if (!session.env) {
    return [
      { label: "Alpine", line: "nibras install alpine" },
      { label: "Debian", line: "nibras install debian" },
      { label: "Ubuntu", line: "nibras install ubuntu" },
    ];
  }
  const items: Array<{ label: string; line: string }> = [];
  if (!session.pkgs.includes("python")) items.push({ label: "Python", line: "nibras install python" });
  if (!session.pkgs.includes("node")) items.push({ label: "Node", line: "nibras install node" });
  if (!session.pkgs.includes("php")) items.push({ label: "PHP", line: "nibras install php" });
  if (session.files[`${session.cwd}/main.py`]) items.push({ label: "Python işlət", line: "python3 main.py" });
  if (session.files[`${session.cwd}/server.js`]) items.push({ label: "Server", line: "node server.js" });
  if (session.files[`${session.cwd}/index.php`]) items.push({ label: "PHP işlət", line: "php index.php" });
  if (session.files[`${session.cwd}/main.c`]) {
    items.push(session.pkgs.includes("build") ? { label: "C işlət", line: "gcc main.c" } : { label: "C seç", line: "nibras install build" });
  }
  if (session.files[`${session.cwd}/a.out`]) items.push({ label: "a.out", line: "./a.out" });
  const note = Object.keys(session.files).find((file) => parent(file) === session.cwd && file.endsWith(".md"));
  if (note) items.push({ label: "Qeyd", line: `nibras md ${note.split("/").pop()}` });
  const data = Object.keys(session.files).find((file) => parent(file) === session.cwd && file.endsWith(".json"));
  if (data) items.push({ label: "JSON", line: `jq . ${data.split("/").pop()}` });
  if (session.files[resolve(session.cwd, "index.html")] || session.files[`${session.cwd}/index.html`]) {
    items.push({ label: "Önizlə", line: "nibras serve" });
  }
  if (!items.length) items.push({ label: "Python layihəsi", line: "nibras layihe python" }, { label: "HTML", line: "nibras layihe web" });
  items.push({ label: "Fayl", line: "edit" });
  return items.slice(0, 6);
}

export function usedBytes(session: Session) {
  const marked = new Set<string>();
  if (session.env) marked.add(session.env);
  for (const pkg of session.pkgs) marked.add(pkg);
  let total = 0;
  for (const key of marked) total += SIZES[key] ?? 0;
  for (const body of Object.values(session.files)) total += body.length;
  return total;
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${Math.round(kb)} KB`;
  const mb = kb / 1024;
  if (mb < 1024) return `${mb.toFixed(1)} MB`;
  return `${(mb / 1024).toFixed(1)} GB`;
}

export function applyAnswer(session: Session, ask: Ask, value: string): Session {
  const next = structuredClone(session);
  const text = value.trim();
  if (ask.id === "token") next.hasToken = Boolean(text);
  if (ask.id === "git-name") next.gitName = text;
  if (ask.id === "git-email") next.gitEmail = text;
  if (ask.id === "remote") next.remote = text.replace(/^https:\/\/github\.com\//, "").replace(/\.git$/, "");
  return next;
}

export function visibleCommand(line: string) {
  return line.replace(/^(\s*nibras\s+token\s+)(?!sil\b)\S[\s\S]*$/i, "$1••••");
}

export function runLine(session: Session, line: string, answer?: string): ShellOut {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("#")) return ok(session, "");
  const history = session.history.at(-1) === trimmed ? session.history.slice(0, -1) : session.history;
  const expanded = expandBang(history, trimmed);
  if (expanded.error) return fail(session, expanded.error);
  try {
    return runSequence(structuredClone(session), expanded.line, answer);
  } catch (error) {
    return fail(session, error instanceof Error ? error.message : "xəta");
  }
}

function expandBang(history: string[], line: string): { line: string; error?: string } {
  if (!line.includes("!")) return { line };
  const last = history.at(-1) ?? "";
  const lastArg = last.split(/\s+/).filter(Boolean).at(-1) ?? "";
  if ((/(^|\s)!!(?=\s|$)/.test(line) || /(^|\s)!\$/.test(line)) && !last) return { line, error: "keçmiş boşdur" };
  let missing = false;
  const next = line
    .replace(/(^|\s)!!(?=\s|$)/g, `$1${last}`)
    .replace(/(^|\s)!\$/g, `$1${lastArg}`)
    .replace(/(^|\s)!(\d+)(?=\s|$)/g, (_all, space: string, n: string) => {
      const item = history[Number(n) - 1];
      if (!item) missing = true;
      return `${space}${item ?? ""}`;
    });
  if (missing) return { line, error: "keçmişdə yoxdur" };
  return { line: next };
}

function runSequence(session: Session, line: string, answer?: string, depth = 0): ShellOut {
  if (depth > 6) return stamp(fail(session, "çox dərin"));
  const parts = splitSequence(line);
  let current = session;
  let last = ok(current, "");
  for (let index = 0; index < parts.length; index += 1) {
    const part = parts[index];
    if (!part?.text) continue;
    const prev = index > 0 ? parts[index - 1] : undefined;
    if (prev?.next === "and" && last.ok === false) continue;
    if (prev?.next === "or" && last.ok !== false) continue;
    const given = index === 0 ? answer : undefined;
    let text = part.text;
    const background = depth === 0 && /(^|[^&])&\s*$/.test(text);
    if (background) text = text.replace(/\s*&\s*$/, "");
    const controlled = control(current, text, given, depth);
    last = stamp(controlled ?? runPipeline(current, text, given));
    current = last.session;
    if (background && !pause(last) && !last.sleep) {
      const state = current.server && /\bserve\b/.test(text) ? "işləyir" : "bitdi";
      current.jobs.push({ id: current.jobs.length + 1, cmd: text, state });
      if (current.jobs.length > 20) current.jobs.shift();
      last = ok(current, `[${current.jobs.at(-1)?.id}] ${state}`);
    }
    if (last.sleep) return { ...last, session: current, cont: restOf(parts, index) || undefined };
    if (pause(last)) return { ...last, session: current };
  }
  return { ...last, session: current };
}

function runPipeline(session: Session, line: string, answer?: string): ShellOut {
  const peeled = peelErr(line);
  const parsed = splitPipeline(expandAlias(session, peeled.line), session);
  let current = session;
  let stdin: string | undefined;
  let last = ok(current, "");
  for (let index = 0; index < parsed.commands.length; index += 1) {
    const argv = splitArgs(parsed.commands[index] ?? "", current);
    let cmd = argv[0] ?? "";
    let cmdArgs = argv.slice(1);
    const alias = current.aliases[cmd];
    if (alias && cmd !== "alias" && cmd !== "unalias" && !/[|&;<>]/.test(alias)) {
      const extra = splitArgs(alias, current);
      cmd = extra[0] ?? cmd;
      cmdArgs = [...extra.slice(1), ...cmdArgs];
    }
    last = dispatch(current, cmd, cmdArgs, index === 0 ? answer : undefined, stdin);
    current = last.session;
    if (pause(last)) return last;
    stdin = last.output;
    if (last.ok === false) return stamp(hideErr(current, last, peeled.err));
  }
  if (parsed.redirect && last.ok !== false) {
    const path = resolve(current.cwd, parsed.redirect.path);
    ensureDir(current, parent(path));
    const prev = current.files[path] ?? "";
    const chunk = last.output.endsWith("\n") || !last.output ? last.output : `${last.output}\n`;
    current.files[path] = parsed.redirect.append ? `${prev}${chunk}` : chunk;
    return stamp(ok(current, ""));
  }
  return stamp(last);
}

function dispatch(session: Session, cmd: string, args: string[], answer: string | undefined, stdin: string | undefined): ShellOut {
  if (!cmd) return ok(session, stdin ?? "");
  if (cmd === "sudo") return sudoCmd(session, args, answer, stdin);
  if (cmd.includes("=") && /^[A-Za-z_][\w]*=/.test(cmd)) {
    const cut = cmd.indexOf("=");
    session.envVars[cmd.slice(0, cut)] = cmd.slice(cut + 1);
    return ok(session, "");
  }
  switch (cmd) {
    case "help":
    case "man":
      return helpTopic(session, args[0]);
    case "clear":
      return ok(session, "\u0000clear");
    case "pwd":
      return ok(session, session.cwd);
    case "ls":
      return ls(session, args);
    case "alias":
      return aliasCmd(session, args, answer);
    case "unalias":
      return unaliasCmd(session, args, answer);
    case "cd":
      return cd(session, args[0] ?? "");
    case "pushd":
      return pushd(session, args[0]);
    case "popd":
      return popd(session);
    case "dirs":
      return dirsCmd(session);
    case "watch":
      return watchCmd(session, args);
    case "fc":
      return ok(session, session.history.at(-1) ?? "");
    case "open":
      return openCmd(session, args, answer);
    case "reset":
      return ok(session, "\u0000clear");
    case "logcat":
      return logcatCmd(session, args);
    case "adb":
      return fail(session, "adb bu ünvanda yoxdur. Kabel və Android Studio lazımdır");
    case "cat":
      return cat(session, args, answer, stdin);
    case "mkdir":
      return mkdir(session, args, answer);
    case "touch":
      return put(session, args[0] || answer, "", "touch");
    case "rm":
      return rm(session, args, answer);
    case "cp":
      return cp(session, args, answer);
    case "mv":
      return mv(session, args, answer);
    case "echo":
      return ok(session, args[0] === "-n" ? args.slice(1).join(" ") : args.join(" "));
    case "printf":
      return ok(session, args.join(" ").replace(/\\[ntr\\]/g, (token) => (token === "\\n" ? "\n" : token === "\\t" ? "\t" : token === "\\r" ? "\r" : "\\")));
    case "uname":
      return ok(session, uname(session));
    case "id":
      return ok(session, idLine(session));
    case "whoami":
    case "logname":
    case "who":
      return ok(session, session.user || "root");
    case "groups":
      return ok(session, session.user === "root" ? "root" : `${session.user} sudo`);
    case "time":
      return timeCmd(session, args, answer, stdin);
    case "date":
      return ok(session, new Date().toLocaleString("az-AZ", { hour12: false }));
    case "df":
    case "free":
      return ok(session, diskLine(session));
    case "env":
      return ok(session, Object.entries(session.envVars).filter(([key]) => key !== "?").map(([key, value]) => `${key}=${value}`).join("\n"));
    case "export":
      return exportVar(session, args.join(" "));
    case "history":
      if (args[0] === "-c") {
        session.history = [];
        return ok(session, "");
      }
      return ok(session, session.history.map((item, index) => `${index + 1}  ${item}`).join("\n"));
    case "grep":
    case "egrep":
    case "fgrep":
    case "rg":
      return grep(session, cmd === "egrep" ? ["-E", ...args] : cmd === "fgrep" ? ["-F", ...args] : args, answer, stdin);
    case "head":
      return sliceLines(session, args, stdin, "head");
    case "tail":
      return sliceLines(session, args, stdin, "tail");
    case "wc":
      return wc(session, args, stdin);
    case "sort":
      return sortCmd(session, args, answer, stdin);
    case "uniq":
      return uniqCmd(session, args, answer, stdin);
    case "cut":
      return cutCmd(session, args, stdin);
    case "diff":
      return diffCmd(session, args, answer);
    case "base64":
      return base64Cmd(session, args, answer, stdin);
    case "expr":
      return exprCmd(session, args, answer);
    case "sed":
      return sedCmd(session, args, stdin);
    case "tr":
      return trCmd(session, args, stdin);
    case "seq":
      return seqCmd(session, args);
    case "stat":
      return statCmd(session, args, answer);
    case "du":
      return duCmd(session, args);
    case "tree":
      return treeCmd(session, args);
    case "which":
      return whichCmd(session, args, answer);
    case "nl":
      return nlCmd(session, args, stdin);
    case "tac":
      return tacCmd(session, args, stdin);
    case "jq":
      return jqCmd(session, args, answer, stdin);
    case "cal":
      return calCmd(session);
    case "true":
      return ok(session, "");
    case "false":
      return fail(session, "");
    case "test":
    case "[":
      return testCmd(session, args, cmd === "[");
    case "tee":
      return teeCmd(session, args, stdin);
    case "rev":
      return revCmd(session, args, stdin);
    case "basename":
      return baseNameCmd(session, args, answer);
    case "dirname":
      return dirNameCmd(session, args, answer);
    case "file":
      return fileCmd(session, args, answer);
    case "xxd":
      return xxdCmd(session, args, answer, stdin);
    case "sha256sum":
    case "sha1sum":
    case "md5sum":
      return sumCmd(session, cmd, args, answer, stdin);
    case "fold":
      return foldCmd(session, args, answer, stdin);
    case "paste":
      return pasteCmd(session, args, answer);
    case "awk":
      return awkCmd(session, args, answer, stdin);
    case "column":
      return columnCmd(session, args, answer, stdin);
    case "cmp":
      return cmpCmd(session, args);
    case "join":
      return joinCmd(session, args);
    case "split":
      return splitCmd(session, args);
    case "truncate":
      return truncateCmd(session, args);
    case "dos2unix":
      return lineEndCmd(session, args, stdin, false);
    case "unix2dos":
      return lineEndCmd(session, args, stdin, true);
    case "less":
      return pageCmd(session, args, answer, stdin, 40);
    case "more":
      return pageCmd(session, args, answer, stdin, 20);
    case "tar":
      return tarCmd(session, args, answer);
    case "chmod":
      return chmodCmd(session, args);
    case "realpath":
      return realPathCmd(session, args, answer);
    case "hostname":
      return ok(session, "nibras");
    case "arch":
      return ok(session, "aarch64");
    case "uptime":
      return ok(session, uptime(session));
    case "rmdir":
      return rmdirCmd(session, args, answer);
    case "unset":
      return unsetCmd(session, args, answer);
    case "printenv":
      return printEnv(session, args, answer);
    case "strings":
      return stringsCmd(session, args, answer, stdin);
    case "shuf":
      return shufCmd(session, args, answer, stdin);
    case "comm":
      return commCmd(session, args, answer);
    case "expand":
      return expandCmd(session, args, answer, stdin);
    case "fmt":
      return fmtCmd(session, args, answer, stdin);
    case "mktemp":
      return mktempCmd(session, args);
    case "factor":
      return factorCmd(session, args, answer);
    case "xargs":
      return xargsCmd(session, args, stdin);
    case "read":
      return readCmd(session, args, answer);
    case "nproc":
      return ok(session, String(cpuCount()));
    case "tty":
      return ok(session, "/dev/pts/0");
    case "type":
      return typeCmd(session, args, answer);
    case "command":
      return commandCmd(session, args, answer);
    case "find":
      return find(session, args);
    case "edit":
      return edit(session, args[0] || answer);
    case "ps":
      return psCmd(session);
    case "jobs":
      return jobsCmd(session);
    case "lsb_release":
      return lsbRelease(session);
    case "dpkg":
      return dpkgCmd(session, args);
    case "apt-cache":
      return pkg(session, "apt", ["search", ...args], answer);
    case "ip":
      return ipCmd(session);
    case "ss":
      return ssCmd(session);
    case "sleep":
      return sleepCmd(session, args);
    case "ln":
      return lnCmd(session, args);
    case "readlink":
      return readlinkCmd(session, args);
    case "adduser":
    case "useradd":
      return addUser(session, args[0] || answer);
    case "passwd":
      return passwdCmd(session, args, answer);
    case "su":
      return suCmd(session, args[0] || answer);
    case "source":
      return sourceCmd(session, args[0] || answer);
    case "nano":
    case "vi":
      return edit(session, args[0] || answer);
    case "python":
    case "python3":
      return python(session, args, answer);
    case "node":
      return nodeCmd(session, args, answer);
    case "php":
      return php(session, args, answer);
    case "gcc":
    case "g++":
    case "cc":
      return gcc(session, args, answer);
    case "curl":
    case "wget":
      return fetchText(session, args, answer);
    case "ping":
      return pingCmd(session, args, answer);
    case "ssh":
      return sshCmd(session, args, answer);
    case "sftp":
      return sftpCmd(session, args, answer);
    case "sh":
    case "bash":
      return script(session, args[0] || answer);
    case "git":
      return git(session, args, answer);
    case "nibras":
      return nibras(session, args, answer);
    case "top":
    case "htop":
      return topCmd(session);
    case "ssh-keygen":
      return keygenCmd(session, args);
    case "nibras-distro":
      return distroCmd(session, args, answer);
    case "pkg":
      return phonePkg(session, args, answer);
    case "apt":
    case "apt-get":
    case "apk":
      return pkg(session, cmd, args, answer);
    default: {
      if (cmd.startsWith("termux-")) return fail(session, `${cmd.replace(/^termux-/, "nibras-")} yaz`);
      if (cmd === "proot-distro") return fail(session, "nibras-distro yaz");
      if (cmd.startsWith("nibras-")) return phoneCmd(session, cmd, args, answer);
      const body = session.functions[cmd];
      if (body) return runSequence(session, body, answer, 1);
      if (cmd.startsWith("./")) return runBinary(session, cmd.slice(2));
      return fail(session, `tanınmayan əmr: ${cmd}\nhelp yaz, və ya Seçimlər bölməsinə keç`);
    }
  }
}

function expandAlias(session: Session, line: string) {
  let current = line.trim();
  for (let depth = 0; depth < 4; depth += 1) {
    const cmd = splitArgs(current, session)[0];
    if (!cmd || cmd === "alias" || cmd === "unalias" || !session.aliases[cmd]) return current;
    const value = session.aliases[cmd] ?? "";
    const rest = current.replace(new RegExp(`^\\s*${cmd.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`), "").trim();
    current = `${value}${rest ? ` ${rest}` : ""}`.trim();
  }
  return current;
}

function jobsFrom(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is { id: number; cmd: string; state: string } => {
      if (!item || typeof item !== "object") return false;
      const row = item as { id?: unknown; cmd?: unknown; state?: unknown };
      return typeof row.id === "number" && typeof row.cmd === "string" && typeof row.state === "string";
    })
    .slice(-20);
}

function stringMap(value: unknown, limit: number) {
  const out: Record<string, string> = {};
  if (!value || typeof value !== "object") return out;
  for (const [key, body] of Object.entries(value)) {
    if (!key || key.includes("..") || key.length > 200 || typeof body !== "string" || body.length > limit) continue;
    out[key] = body;
    if (Object.keys(out).length >= 40) break;
  }
  return out;
}

function headsFrom(value: unknown) {
  const out: Session["heads"] = {};
  if (!value || typeof value !== "object") return out;
  for (const [name, head] of Object.entries(value)) {
    if (!/^[\w.-]+$/.test(name) || !head || typeof head !== "object") continue;
    const row = head as { files?: unknown; base?: unknown };
    const files = fileMap(row.files);
    out[name] = { files, base: fileMap(row.base) };
  }
  return out;
}

function fileMap(value: unknown) {
  const files: Record<string, string> = {};
  if (!value || typeof value !== "object" || Array.isArray(value)) return files;
  for (const [path, body] of Object.entries(value)) {
    if (typeof body !== "string" || path.includes("..") || body.length > 20_000) continue;
    files[path] = body;
    if (Object.keys(files).length >= 40) break;
  }
  return files;
}

function trashFrom(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value
    .filter((item): item is { path: string; body: string } => {
      if (!item || typeof item !== "object") return false;
      const row = item as { path?: unknown; body?: unknown };
      return typeof row.path === "string" && typeof row.body === "string" && row.body.length <= 200_000;
    })
    .slice(0, 20);
}

function modesFrom(value: unknown) {
  const out: Record<string, string> = {};
  if (!value || typeof value !== "object") return out;
  for (const [key, body] of Object.entries(value)) {
    if (typeof body === "string" && /^[0-7]{3,4}$/.test(body)) out[key] = body;
  }
  return out;
}

function aliasesFrom(value: unknown) {
  const out: Record<string, string> = {};
  if (!value || typeof value !== "object") return out;
  for (const [key, body] of Object.entries(value)) {
    if (/^[\w.-]+$/.test(key) && typeof body === "string" && body.length > 0 && body.length < 200) out[key] = body;
  }
  return out;
}

function aliasCmd(session: Session, args: string[], answer?: string): ShellOut {
  const raw = (args.join(" ") || answer || "").trim();
  if (!raw) {
    const have = Object.entries(session.aliases)
      .map(([key, value]) => `${key} → ${value}`)
      .join("\n");
    return askFor(session, { id: "text", title: "Ləqəb", label: have || "ad=əmr. Məsələn salam=echo Salam", resume: "alias" });
  }
  const cut = raw.indexOf("=");
  if (cut < 0) {
    const found = session.aliases[raw];
    if (!found) return fail(session, `ləqəb yoxdur: ${raw}`);
    return ok(session, `${raw} → ${found}`);
  }
  const name = raw.slice(0, cut).trim();
  const value = raw.slice(cut + 1).trim();
  if (!/^[\w.-]+$/.test(name) || !value || value.length > 200) return fail(session, "alias ad=əmr");
  if (name === "alias" || name === "unalias") return fail(session, "bu ad bağlanmır");
  session.aliases[name] = value;
  return ok(session, `${name} → ${value}`);
}

function unaliasCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "Ləqəbi sil", label: "Ad", resume: "unalias" });
  if (!session.aliases[name]) return fail(session, `ləqəb yoxdur: ${name}`);
  delete session.aliases[name];
  return ok(session, `${name} silindi`);
}

function ls(session: Session, args: string[] = []): ShellOut {
  const all = args.includes("-a") || args.includes("-la") || args.includes("-al");
  const targets = args.filter((item) => !item.startsWith("-"));
  if (targets.length > 1) return ok(session, targets.join("\n"));
  const arg = targets[0];
  const dir = at(session, arg ?? ".");
  if (session.files[dir] !== undefined) return ok(session, dir.split("/").pop() ?? dir);
  if (!hasDir(session, dir)) return fail(session, `qovluq yoxdur: ${dir}`);
  const names = new Set<string>();
  for (const item of session.dirs) {
    if (parent(item) === dir) {
      const base = item.split("/").pop();
      if (base && (all || !base.startsWith("."))) names.add(`${base}/`);
    }
  }
  for (const file of Object.keys(session.files)) {
    if (parent(file) === dir) {
      const base = file.split("/").pop() ?? file;
      if (all || !base.startsWith(".")) names.add(base);
    }
  }
  for (const [path, target] of Object.entries(session.links)) {
    if (parent(path) !== dir) continue;
    const base = path.split("/").pop() ?? path;
    if (all || !base.startsWith(".")) names.add(`${base} -> ${target}`);
  }
  const list = [...names].sort();
  return ok(session, list.length ? list.join("\n") : "boş qovluq");
}

function cd(session: Session, arg: string): ShellOut {
  const home = session.home || session.envVars.HOME || "/root";
  let asked = arg || home;
  if (arg === "-") {
    asked = session.oldpwd || session.envVars.OLDPWD || "";
    if (!asked) return fail(session, "əvvəlki qovluq yoxdur");
  }
  const dir = at(session, asked);
  if (!hasDir(session, dir)) return fail(session, `qovluq yoxdur: ${dir}`);
  session.oldpwd = session.cwd;
  session.envVars.OLDPWD = session.cwd;
  session.envVars.PWD = dir;
  session.cwd = dir;
  return ok(session, arg === "-" ? dir : "");
}

function pushd(session: Session, arg?: string): ShellOut {
  if (!arg) return fail(session, "pushd qovluq");
  const from = session.cwd;
  const moved = cd(session, arg);
  if (moved.ok === false) return moved;
  session.stack.push(from);
  return dirsCmd(session);
}

function popd(session: Session): ShellOut {
  const prev = session.stack.pop();
  if (!prev) return fail(session, "stək boşdur");
  const moved = cd(session, prev);
  if (moved.ok === false) return moved;
  return dirsCmd(session);
}

function dirsCmd(session: Session): ShellOut {
  return ok(session, [session.cwd, ...[...session.stack].reverse()].join(" "));
}

function watchCmd(session: Session, args: string[]): ShellOut {
  const seconds = Math.min(3, Math.max(1, Number(args[0]) || 0));
  const rest = args.slice(1).join(" ").trim();
  if (!seconds || !rest || /\bwatch\b/.test(rest)) return fail(session, "watch saniyə əmr");
  const count = Number(session.envVars.WATCH || "0");
  if (count >= 3) {
    delete session.envVars.WATCH;
    return ok(session, "watch dayandı");
  }
  const ran = runSequence(session, rest);
  if (pause(ran) || ran.sleep) return ran;
  ran.session.envVars.WATCH = String(count + 1);
  return { ...ran, sleep: seconds * 1000, cont: `watch ${seconds} ${rest}` };
}

function openCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "Aç", label: "fayl və ya https://", resume: "open" });
  if (name.startsWith("https://")) {
    let parsed: URL;
    try {
      parsed = new URL(name);
    } catch {
      return fail(session, "ünvan səhvdir");
    }
    if (isBlockedHost(parsed.hostname)) return fail(session, "bu ünvan bağlıdır");
    return { ...ok(session, ""), device: { kind: "open", url: name } };
  }
  const path = at(session, name);
  if (session.files[path] === undefined) return fail(session, `fayl yoxdur: ${path}`);
  return { ...ok(session, path), open: path };
}

function logcatCmd(session: Session, args: string[]): ShellOut {
  const body = session.files["/var/log/nibras.log"] ?? "";
  const count = Math.min(40, Math.max(1, Number(args.find((arg) => /^\d+$/.test(arg)) || "20")));
  const lines = body.trim().split("\n").filter(Boolean).slice(-count);
  return ok(session, ["Nibras terminal jurnalı. Sistem logcat-i bu vebdə yoxdur.", ...lines].join("\n"));
}

function cat(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) {
    if (stdin !== undefined) return ok(session, stdin);
    return askFor(session, { id: "text", title: "Faylı oxu", label: "Fayl adı", resume: "cat" });
  }
  const chunks: string[] = [];
  for (const arg of name === answer && !args[0] ? [name] : args.length ? args : [name]) {
    const path = at(session, arg);
    const body = session.files[path];
    if (body === undefined) return fail(session, `fayl yoxdur: ${path}`);
    chunks.push(body.replace(/\n$/, ""));
  }
  return ok(session, chunks.join("\n"));
}

function mkdir(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => arg !== "-p" && !arg.startsWith("-"));
  if (!names.length && answer) names.push(answer);
  if (!names.length) return askFor(session, { id: "text", title: "Qovluq", label: "Ad", resume: "mkdir" });
  for (const name of names) ensureDir(session, resolve(session.cwd, name));
  return ok(session, "");
}

function put(session: Session, arg: string | undefined, content: string, resume: string): ShellOut {
  if (!arg) return askFor(session, { id: "text", title: "Fayl", label: "Ad", resume });
  const path = resolve(session.cwd, arg);
  ensureDir(session, parent(path));
  session.files[path] = content;
  return ok(session, "");
}

function rm(session: Session, args: string[], answer?: string): ShellOut {
  const yes = args.includes("--yes") || answer === "bəli";
  const recursive = args.includes("-r") || args.includes("-rf") || args.includes("-fr");
  const names = args.filter((arg) => !arg.startsWith("-"));
  const name = names[0] || (!args.length ? answer : undefined);
  if (!name) return askFor(session, { id: "text", title: "Sil", label: "Fayl və ya qovluq", resume: "rm" });
  if (names.length > 1 && !recursive) {
    const paths = names.map((item) => resolve(session.cwd, item));
    const missing = paths.find((path) => session.files[path] === undefined);
    if (missing) return fail(session, `yoxdur: ${missing}`);
    for (const path of paths) keepTrash(session, path);
    return ok(session, "");
  }
  const path = resolve(session.cwd, name);
  const linked = session.links[path] !== undefined;
  if (linked) delete session.links[path];
  if (path === "/" || path === "/root") return fail(session, "bu qovluq silinmir");
  const file = session.files[path];
  const directory = hasDir(session, path) && file === undefined;
  if (file === undefined && !directory) return linked ? ok(session, "") : fail(session, `yoxdur: ${path}`);
  if (directory && !recursive) return fail(session, "qovluqdur. rm -r yaz");
  if (directory && !yes) {
    return askFor(session, {
      id: "confirm",
      title: "Qovluq silinsin?",
      label: path,
      resume: `rm -r ${shellQuote(name)} --yes`,
    });
  }
  if (file !== undefined) keepTrash(session, path);
  if (directory || recursive) {
    for (const item of Object.keys(session.files)) {
      if (item === path || item.startsWith(`${path}/`)) delete session.files[item];
    }
    session.dirs = session.dirs.filter((item) => item !== path && !item.startsWith(`${path}/`));
  }
  return ok(session, "");
}

function cp(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  const sourceName = names[0] || answer?.split(/\s+/)[0];
  const destName = names[1] || answer?.split(/\s+/).slice(1).join(" ");
  if (!sourceName || !destName) {
    return askFor(session, { id: "text", title: "Köçür", label: "mənbə hədəf", resume: "cp" });
  }
  return copyPath(session, sourceName, destName, false);
}

function mv(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  const sourceName = names[0] || answer?.split(/\s+/)[0];
  const destName = names[1] || answer?.split(/\s+/).slice(1).join(" ");
  if (!sourceName || !destName) {
    return askFor(session, { id: "text", title: "Köçür və sil", label: "mənbə hədəf", resume: "mv" });
  }
  const copied = copyPath(session, sourceName, destName, false);
  if (copied.ok === false) return copied;
  delete copied.session.files[resolve(session.cwd, sourceName)];
  return copied;
}

function copyPath(session: Session, sourceName: string, destName: string, _deep: boolean): ShellOut {
  const source = resolve(session.cwd, sourceName);
  const dest = resolve(session.cwd, destName);
  if (session.files[source] === undefined) return fail(session, `fayl yoxdur: ${source}`);
  ensureDir(session, parent(dest));
  session.files[dest] = session.files[source] ?? "";
  return ok(session, "");
}

function grep(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  const flags = args.filter((arg) => arg.startsWith("-"));
  const rest = args.filter((arg) => !arg.startsWith("-"));
  const pattern = rest[0] || answer;
  if (!pattern) return askFor(session, { id: "text", title: "Axtar", label: "Söz", resume: "grep" });
  const extended = flags.includes("-E") && !flags.includes("-F");
  let matcher: (line: string) => boolean;
  if (!extended) matcher = (line) => line.includes(pattern);
  else {
    try {
      const re = new RegExp(pattern);
      matcher = (line) => re.test(line);
    } catch {
      return fail(session, "nümunə səhvdir");
    }
  }
  const file = rest[1];
  let text = stdin;
  if (file) {
    const body = session.files[resolve(session.cwd, file)];
    if (body === undefined) return fail(session, `fayl yoxdur: ${file}`);
    text = body;
  }
  if (text === undefined) {
    const root = session.cwd;
    const hits: string[] = [];
    for (const [path, body] of Object.entries(session.files)) {
      if (path !== root && !path.startsWith(`${root}/`)) continue;
      body.split("\n").forEach((line, index) => {
        if (!matcher(line)) return;
        hits.push(flags.includes("-n") ? `${path}:${index + 1}:${line}` : `${path}:${line}`);
      });
    }
    return ok(session, hits.join("\n"));
  }
  const lines = text.split("\n").map((line, index) => ({ line, index }));
  const hits = lines.filter((item) => matcher(item.line) && (item.line !== "" || pattern === ""));
  return ok(session, hits.map((item) => (flags.includes("-n") ? `${item.index + 1}:${item.line}` : item.line)).join("\n"));
}

function sliceLines(session: Session, args: string[], stdin: string | undefined, mode: "head" | "tail"): ShellOut {
  let count = 10;
  const rest = [...args];
  if (rest[0] === "-n" && rest[1]) {
    count = Number(rest[1]) || 10;
    rest.splice(0, 2);
  } else if (rest[0]?.startsWith("-") && rest[0] !== "-") {
    count = Number(rest[0].slice(1)) || 10;
    rest.shift();
  }
  const file = rest[0];
  let text = stdin;
  if (file) {
    const body = session.files[resolve(session.cwd, file)];
    if (body === undefined) return fail(session, `fayl yoxdur: ${file}`);
    text = body;
  }
  if (text === undefined) return fail(session, `${mode} <fayl>`);
  const lines = text.replace(/\n$/, "").split("\n");
  const picked = mode === "head" ? lines.slice(0, count) : lines.slice(-count);
  return ok(session, picked.join("\n"));
}

function wc(session: Session, args: string[], stdin?: string): ShellOut {
  const linesOnly = args.includes("-l");
  const file = args.find((arg) => !arg.startsWith("-"));
  let text = stdin;
  if (file) {
    const body = session.files[resolve(session.cwd, file)];
    if (body === undefined) return fail(session, `fayl yoxdur: ${file}`);
    text = body;
  }
  if (text === undefined) return fail(session, "wc <fayl>");
  const lineCount = text === "" ? 0 : text.split("\n").length - (text.endsWith("\n") ? 1 : 0);
  if (linesOnly) return ok(session, String(lineCount));
  const words = text.trim() ? text.trim().split(/\s+/).length : 0;
  return ok(session, `${lineCount} ${words} ${new TextEncoder().encode(text).length}`);
}

function textFrom(session: Session, file: string | undefined, stdin?: string) {
  if (file) {
    const body = session.files[at(session, file)];
    if (body === undefined) return { error: `fayl yoxdur: ${file}` };
    return { text: body };
  }
  if (stdin !== undefined) return { text: stdin };
  return {};
}

function linesOf(text: string) {
  return text.replace(/\n$/, "").split("\n");
}

function sortCmd(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  const reverse = args.includes("-r");
  const numeric = args.includes("-n");
  const file = args.find((arg) => !arg.startsWith("-")) || answer;
  const got = textFrom(session, args.some((arg) => !arg.startsWith("-")) ? file : stdin === undefined ? file : undefined, stdin);
  if (!args.some((arg) => !arg.startsWith("-")) && stdin === undefined && !answer) {
    return askFor(session, { id: "text", title: "Sırala", label: "Fayl adı", resume: "sort" });
  }
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "sort <fayl>");
  const lines = linesOf(got.text);
  lines.sort((left, right) => (numeric ? Number(left) - Number(right) : left < right ? -1 : left > right ? 1 : 0));
  if (reverse) lines.reverse();
  return ok(session, lines.join("\n"));
}

function uniqCmd(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  const file = args.find((arg) => !arg.startsWith("-")) || (!stdin ? answer : undefined);
  if (!file && stdin === undefined) return askFor(session, { id: "text", title: "Təkrarları at", label: "Fayl adı", resume: "uniq" });
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "uniq <fayl>");
  const out: string[] = [];
  for (const line of linesOf(got.text)) if (out[out.length - 1] !== line) out.push(line);
  return ok(session, out.join("\n"));
}

function cutCmd(session: Session, args: string[], stdin?: string): ShellOut {
  let delim = "\t";
  let field = 1;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-d" && args[index + 1]) {
      delim = args[index + 1] ?? "\t";
      index += 1;
      continue;
    }
    if (arg.startsWith("-d") && arg.length > 2) {
      delim = arg.slice(2);
      continue;
    }
    if (arg === "-f" && args[index + 1]) {
      field = Number(args[index + 1]) || 1;
      index += 1;
      continue;
    }
    if (arg.startsWith("-f") && arg.length > 2) {
      field = Number(arg.slice(2)) || 1;
      continue;
    }
    rest.push(arg);
  }
  const got = textFrom(session, rest[0], stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "cut -d, -f1 <fayl>");
  const picked = linesOf(got.text).map((line) => line.split(delim)[field - 1] ?? "");
  return ok(session, picked.join("\n"));
}

function diffCmd(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  const pair = names.length >= 2 ? names : (answer ?? "").trim().split(/\s+/).filter(Boolean);
  if (pair.length < 2) return askFor(session, { id: "text", title: "Fərq", label: "fayl1 fayl2", resume: "diff" });
  const left = session.files[resolve(session.cwd, pair[0] ?? "")];
  const right = session.files[resolve(session.cwd, pair[1] ?? "")];
  if (left === undefined || right === undefined) return fail(session, "hər iki fayl olmalıdır");
  const a = linesOf(left);
  const b = linesOf(right);
  if (a.length > 400 || b.length > 400) return fail(session, "400 sətirdən uzundur");
  return ok(session, lineDiff(a, b) || "fərq yoxdur");
}

function lineDiff(left: string[], right: string[]) {
  const width = left.length;
  const height = right.length;
  const grid: number[][] = Array.from({ length: width + 1 }, () => Array(height + 1).fill(0));
  for (let i = width - 1; i >= 0; i -= 1) {
    for (let j = height - 1; j >= 0; j -= 1) {
      grid[i][j] = left[i] === right[j] ? (grid[i + 1]?.[j + 1] ?? 0) + 1 : Math.max(grid[i + 1]?.[j] ?? 0, grid[i]?.[j + 1] ?? 0);
    }
  }
  const out: string[] = [];
  let i = 0;
  let j = 0;
  while (i < width && j < height) {
    if (left[i] === right[j]) {
      i += 1;
      j += 1;
    } else if ((grid[i + 1]?.[j] ?? 0) >= (grid[i]?.[j + 1] ?? 0)) {
      out.push(`- ${left[i]}`);
      i += 1;
    } else {
      out.push(`+ ${right[j]}`);
      j += 1;
    }
  }
  while (i < width) out.push(`- ${left[i++]}`);
  while (j < height) out.push(`+ ${right[j++]}`);
  return out.join("\n");
}

function base64Cmd(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  const decode = args.includes("-d") || args.includes("--decode");
  const file = args.find((arg) => !arg.startsWith("-")) || (!stdin ? answer : undefined);
  if (!file && stdin === undefined) return askFor(session, { id: "text", title: "Base64", label: "Fayl adı", resume: decode ? "base64 -d" : "base64" });
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "base64 <fayl>");
  try {
    return ok(session, decode ? b64ToUtf8(got.text) : utf8ToB64(got.text.replace(/\n$/, "")));
  } catch {
    return fail(session, "base64 deyil");
  }
}

function utf8ToB64(text: string) {
  const bytes = new TextEncoder().encode(text);
  let bin = "";
  for (const byte of bytes) bin += String.fromCharCode(byte);
  return btoa(bin);
}

function b64ToUtf8(value: string) {
  const bin = atob(value.replace(/\s/g, ""));
  const bytes = Uint8Array.from(bin, (ch) => ch.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

function exprCmd(session: Session, args: string[], answer?: string): ShellOut {
  const raw = (args.join(" ") || answer || "").trim();
  if (!raw) return askFor(session, { id: "text", title: "Hesab", label: "2 + 3 * 4", resume: "expr" });
  if (!/^[\d\s+*/%().-]+$/.test(raw)) return fail(session, "yalnız rəqəm və + - * / % ( )");
  try {
    const value = Function(`"use strict"; return (${raw})`)() as unknown;
    if (typeof value !== "number" || !Number.isFinite(value)) return fail(session, "hesab alınmadı");
    return ok(session, String(value));
  } catch {
    return fail(session, "hesab alınmadı");
  }
}

function sedCmd(session: Session, args: string[], stdin?: string): ShellOut {
  const expr = args.find((arg) => arg.startsWith("s/"));
  if (!expr) return fail(session, "sed 's/köhnə/yeni/' fayl");
  const match = /^s\/((?:\\\/|[^/])*)\/((?:\\\/|[^/])*)\/(g?)$/.exec(expr);
  if (!match || !match[1]) return fail(session, "sed 's/köhnə/yeni/' fayl");
  const from = match[1].replace(/\\\//g, "/");
  const to = (match[2] ?? "").replace(/\\\//g, "/");
  const file = args.find((arg) => arg !== expr && !arg.startsWith("-"));
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "sed fayl və ya boru istəyir");
  const next = linesOf(got.text).map((line) => (match[3] === "g" ? line.split(from).join(to) : line.replace(from, to)));
  const body = `${next.join("\n")}\n`;
  if (args.includes("-i") && file) {
    session.files[resolve(session.cwd, file)] = body;
    return ok(session, "");
  }
  return ok(session, next.join("\n"));
}

function trCmd(session: Session, args: string[], stdin?: string): ShellOut {
  const sets = args.filter((arg) => !arg.startsWith("-"));
  if (sets.length < 2) return fail(session, "echo salam | tr a-z A-Z");
  if (stdin === undefined) return fail(session, "boru lazımdır");
  const from = expandSet(sets[0] ?? "");
  const to = expandSet(sets[1] ?? "");
  const map = new Map<string, string>();
  for (let index = 0; index < from.length; index += 1) map.set(from[index] ?? "", to[Math.min(index, to.length - 1)] ?? "");
  return ok(session, [...stdin].map((ch) => map.get(ch) ?? ch).join(""));
}

function expandSet(value: string) {
  let out = "";
  for (let index = 0; index < value.length; index += 1) {
    if (value[index + 1] === "-" && value[index + 2]) {
      const start = value.charCodeAt(index);
      const end = value.charCodeAt(index + 2);
      const step = start <= end ? 1 : -1;
      for (let code = start; step > 0 ? code <= end : code >= end; code += step) out += String.fromCharCode(code);
      index += 2;
    } else out += value[index];
  }
  return out;
}

function seqCmd(session: Session, args: string[]): ShellOut {
  const nums = args.map(Number);
  if (!nums.length || nums.some((item) => !Number.isFinite(item))) return fail(session, "seq 1 5");
  let start = 1;
  let step = 1;
  let end = nums[0] ?? 1;
  if (nums.length === 2) {
    start = nums[0] ?? 1;
    end = nums[1] ?? start;
  }
  if (nums.length >= 3) {
    start = nums[0] ?? 1;
    step = nums[1] ?? 1;
    end = nums[2] ?? start;
  }
  if (step === 0) return fail(session, "addım 0 ola bilməz");
  const out: string[] = [];
  const cap = 400;
  if (step > 0) for (let value = start; value <= end && out.length < cap; value += step) out.push(String(value));
  else for (let value = start; value >= end && out.length < cap; value += step) out.push(String(value));
  if (out.length === cap) out.push("… kəsildi");
  return ok(session, out.join("\n"));
}

function statCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || answer;
  if (!name) return askFor(session, { id: "text", title: "Ölçü", label: "Fayl adı", resume: "stat" });
  const path = resolve(session.cwd, name);
  const body = session.files[path];
  if (body === undefined) return fail(session, `fayl yoxdur: ${path}`);
  const mode = session.modes[path] ?? "644";
  return ok(session, `${path}\n${new TextEncoder().encode(body).length} bayt\n${linesOf(body).length} sətir\n${mode}`);
}

function duCmd(session: Session, args: string[]): ShellOut {
  const start = resolve(session.cwd, args.find((arg) => !arg.startsWith("-")) ?? ".");
  let total = 0;
  for (const [path, body] of Object.entries(session.files)) {
    if (path === start || path.startsWith(`${start}/`) || (start !== "/" && parent(path) === start)) total += new TextEncoder().encode(body).length;
  }
  return ok(session, `${total}  ${start}`);
}

function treeCmd(session: Session, args: string[]): ShellOut {
  const start = resolve(session.cwd, args[0] ?? ".");
  if (!hasDir(session, start) && session.files[start] === undefined) return fail(session, `yoxdur: ${start}`);
  const lines = [start];
  const walk = (dir: string, prefix: string) => {
    const kids: Array<{ name: string; path: string; directory: boolean }> = [];
    for (const item of session.dirs) {
      if (parent(item) === dir) kids.push({ name: item.split("/").pop() ?? item, path: item, directory: true });
    }
    for (const file of Object.keys(session.files)) {
      if (parent(file) === dir) kids.push({ name: file.split("/").pop() ?? file, path: file, directory: false });
    }
    kids.sort((left, right) => left.name.localeCompare(right.name));
    kids.forEach((kid, index) => {
      if (lines.length > 200) return;
      const last = index === kids.length - 1;
      lines.push(`${prefix}${last ? "└── " : "├── "}${kid.name}${kid.directory ? "/" : ""}`);
      if (kid.directory) walk(kid.path, `${prefix}${last ? "    " : "│   "}`);
    });
  };
  if (hasDir(session, start)) walk(start, "");
  return ok(session, lines.join("\n"));
}

function whichCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "Haradadır", label: "Əmr", resume: "which" });
  if (COMMANDS.includes(name) || name === "python" || name === "g++" || name === "cc") {
    return ok(session, `${session.envVars.PREFIX || "/usr"}/bin/${name}`);
  }
  return fail(session, `tapılmadı: ${name}`);
}

function nlCmd(session: Session, args: string[], stdin?: string): ShellOut {
  const file = args.find((arg) => !arg.startsWith("-"));
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "nl <fayl>");
  return ok(session, linesOf(got.text).map((line, index) => `${String(index + 1).padStart(4, " ")}  ${line}`).join("\n"));
}

function tacCmd(session: Session, args: string[], stdin?: string): ShellOut {
  const file = args.find((arg) => !arg.startsWith("-"));
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "tac <fayl>");
  return ok(session, linesOf(got.text).reverse().join("\n"));
}

function jqCmd(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  const rest = args.filter((arg) => !arg.startsWith("-"));
  if (!rest.length && answer) rest.push(...answer.trim().split(/\s+/));
  let filter = ".";
  let file: string | undefined;
  if (rest.length === 1) {
    const only = rest[0] ?? ".";
    if (session.files[resolve(session.cwd, only)] !== undefined) file = only;
    else filter = only;
  } else if (rest.length >= 2) {
    filter = rest[0] ?? ".";
    file = rest[1];
  }
  if (!file && stdin === undefined && !answer) return askFor(session, { id: "text", title: "JSON", label: ".açar fayl.json", resume: "jq" });
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "jq . fayl.json");
  let data: unknown;
  try {
    data = JSON.parse(got.text);
  } catch {
    return fail(session, "JSON deyil");
  }
  const value = applyJq(data, filter);
  if (value instanceof Error) return fail(session, value.message);
  return ok(session, typeof value === "string" ? value : JSON.stringify(value, null, 2));
}

function applyJq(data: unknown, filter: string): unknown {
  if (!filter || filter === ".") return data;
  if (filter === "keys") {
    if (data == null || typeof data !== "object" || Array.isArray(data)) return new Error("obyekt deyil");
    return Object.keys(data);
  }
  if (filter === "length") {
    if (typeof data === "string" || Array.isArray(data)) return data.length;
    if (data && typeof data === "object") return Object.keys(data).length;
    return new Error("uzunluq yoxdur");
  }
  let current = data;
  const parts = filter.replace(/^\./, "").split(".").filter(Boolean);
  for (const part of parts) {
    const indexed = /^([^[]+)?\[(\d+)\]$/.exec(part);
    if (indexed) {
      if (indexed[1]) {
        if (current == null || typeof current !== "object") return new Error(`yoxdur: ${part}`);
        current = (current as Record<string, unknown>)[indexed[1]];
      }
      if (!Array.isArray(current)) return new Error("massiv deyil");
      current = current[Number(indexed[2])];
    } else {
      if (current == null || typeof current !== "object" || Array.isArray(current)) return new Error(`yoxdur: ${part}`);
      current = (current as Record<string, unknown>)[part];
    }
    if (current === undefined) return new Error(`yoxdur: ${part}`);
  }
  return current;
}

function calCmd(session: Session): ShellOut {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();
  const first = new Date(year, month, 1).getDay();
  const days = new Date(year, month + 1, 0).getDate();
  const offset = (first + 6) % 7;
  const cells = [...Array(offset).fill("  "), ...Array.from({ length: days }, (_, index) => String(index + 1).padStart(2, " "))];
  const rows: string[] = [];
  for (let index = 0; index < cells.length; index += 7) rows.push(cells.slice(index, index + 7).join(" "));
  const title = now.toLocaleDateString("az-AZ", { month: "long", year: "numeric" });
  return ok(session, [title, "Be Ça Çə Ca Cü Şn Bz", ...rows].join("\n"));
}

function find(session: Session, args: string[]): ShellOut {
  const nameFlag = args.indexOf("-name");
  const needle = nameFlag >= 0 ? args[nameFlag + 1] : "";
  const startArg = args.find((arg) => arg !== "-name" && arg !== needle && !arg.startsWith("-")) ?? ".";
  const start = resolve(session.cwd, startArg);
  const files = Object.keys(session.files)
    .filter((file) => file === start || file.startsWith(`${start}/`))
    .filter((file) => !needle || (file.split("/").pop() ?? "").includes(needle.replaceAll("*", "")))
    .sort();
  return ok(session, files.join("\n"));
}

function edit(session: Session, arg?: string): ShellOut {
  if (!arg) return askFor(session, { id: "text", title: "Redaktə", label: "Fayl adı", resume: "edit" });
  const path = resolve(session.cwd, arg);
  if (session.files[path] === undefined) session.files[path] = "";
  ensureDir(session, parent(path));
  return { ...ok(session, path), open: path };
}

function exportVar(session: Session, body: string): ShellOut {
  if (!body) return ok(session, Object.entries(session.envVars).filter(([key]) => key !== "?").map(([key, value]) => `${key}=${value}`).join("\n"));
  const cut = body.indexOf("=");
  if (cut < 0) return ok(session, `${body}=${session.envVars[body] ?? ""}`);
  session.envVars[body.slice(0, cut)] = body.slice(cut + 1);
  return ok(session, "");
}

function uname(session: Session) {
  if (!session.env) return "mühit seçilməyib · Alpine, Debian və ya Ubuntu";
  const name = session.env === "alpine" ? "Alpine Linux 3.24.1" : session.env === "debian" ? "Debian" : "Ubuntu";
  return `Linux nibras 6.1 aarch64 ${name}`;
}

function python(session: Session, args: string[], answer?: string): ShellOut {
  if (!session.pkgs.includes("python")) return fail(session, "Python seçilməyib.\nnibras install python");
  if (!session.env) return fail(session, "Əvvəl mühit seç: nibras install alpine");
  if (!args.length) return { ...ok(session, "Python · çıxış: exit()"), repl: "python" };
  if (args[0] === "-c") {
    const code = args.slice(1).join(" ") || answer;
    if (!code) return askFor(session, { id: "text", title: "Python", label: "Kod", resume: "python3 -c" });
    return { ...ok(session, ""), python: { code } };
  }
  const path = resolve(session.cwd, args[0]);
  const code = session.files[path];
  if (code === undefined) return fail(session, `fayl yoxdur: ${path}`);
  return { ...ok(session, ""), python: { code, file: path, argv: [path, ...args.slice(1)] } };
}

function nodeCmd(session: Session, args: string[], answer?: string): ShellOut {
  if (!session.pkgs.includes("node")) return fail(session, "Node seçilməyib.\nnibras install node");
  if (!session.env) return fail(session, "Əvvəl mühit seç: nibras install alpine");
  if (!args.length) return { ...ok(session, "Node · çıxış: exit"), repl: "node" };
  if (args[0] === "-e") {
    const code = args.slice(1).join(" ") || answer;
    if (!code) return askFor(session, { id: "text", title: "Node", label: "Kod", resume: "node -e" });
    return { ...ok(session, ""), node: { code } };
  }
  const path = resolve(session.cwd, args[0]);
  const code = session.files[path];
  if (code === undefined) return fail(session, `fayl yoxdur: ${path}`);
  return { ...ok(session, ""), node: { code, file: path, argv: ["node", path, ...args.slice(1)] } };
}

function php(session: Session, args: string[], answer?: string): ShellOut {
  if (!session.pkgs.includes("php")) return fail(session, "PHP seçilməyib.\nnibras install php");
  if (!session.env) return fail(session, "Əvvəl mühit seç: nibras install alpine");
  if (!args.length || args[0] === "-v") return { ...ok(session, ""), php: { code: "", version: true } };
  if (args[0] === "-r") {
    const code = args.slice(1).join(" ") || answer;
    if (!code) return askFor(session, { id: "text", title: "PHP", label: "Kod", resume: "php -r" });
    return { ...ok(session, ""), php: { code } };
  }
  const path = resolve(session.cwd, args[0]);
  const code = session.files[path];
  if (code === undefined) return fail(session, `fayl yoxdur: ${path}`);
  return { ...ok(session, ""), php: { code, file: path } };
}

function git(session: Session, args: string[], answer?: string): ShellOut {
  const sub = args[0];
  if (!sub) return ok(session, "git init | clone | status | add | commit | log | branch | switch | diff | rebase | pull | push");
  if (sub === "clone") return gitClone(session, args, answer);
  if (sub === "init") {
    session.gitRoot = session.cwd;
    session.branch = session.branch || "main";
    ensureDir(session, `${session.cwd}/.git`);
    return ok(session, `boş Git deposu: ${session.cwd}/.git`);
  }
  if (sub === "config") {
    if (args[1] === "user.name") {
      if (!answer) return askFor(session, { id: "git-name", title: "Git adı", label: "Ad", resume: "git config user.name" });
      session.gitName = answer.trim();
      return ok(session, "ad saxlandı");
    }
    if (args[1] === "user.email") {
      if (!answer) return askFor(session, { id: "git-email", title: "Git poçtu", label: "E-poçt", resume: "git config user.email" });
      session.gitEmail = answer.trim();
      return ok(session, "poçt saxlandı");
    }
    return fail(session, "git config user.name | user.email");
  }
  if (sub === "remote") {
    if (args[1] === "-v" || !args[1]) return ok(session, session.remote ? `origin  ${session.remote}` : "uzaq depo yoxdur");
    if (args[1] === "add") {
      const url = args[3] || (args[2] && args[2] !== "origin" ? args[2] : "") || answer;
      if (!url || url === "origin") {
        return askFor(session, { id: "remote", title: "GitHub deposu", label: "sahib/ad", resume: "git remote add origin" });
      }
      session.remote = cleanRepo(url);
      return session.remote.includes("/") ? ok(session, `origin  ${session.remote}`) : fail(session, "depo belə yazılır: sahib/ad");
    }
    return fail(session, "git remote -v | git remote add origin sahib/ad");
  }
  if (sub === "pull") return gitPull(session, answer);
  if (!session.gitRoot) return fail(session, "əvvəl: git init");
  if (sub === "status") {
    if (!session.staged.length) return ok(session, "əlavə olunmayıb");
    return ok(session, ["hazırdır:", ...session.staged].join("\n"));
  }
  if (sub === "add") {
    const target = args[1] || ".";
    const root = target === "." ? session.gitRoot : resolve(session.cwd, target);
    const files = Object.keys(session.files).filter((file) => file === root || file.startsWith(`${root}/`) || file.startsWith(`${session.gitRoot}/`));
    const picked = target === "." ? files.filter((file) => file.startsWith(`${session.gitRoot}/`) || file === session.gitRoot) : files.filter((file) => file === root || file.startsWith(`${root}/`));
    session.staged = [...new Set(picked)];
    return ok(session, session.staged.length ? `${session.staged.length} fayl hazırdır` : "fayl yoxdur");
  }
  if (sub === "log") {
    if (!session.commits.length) return ok(session, "hələ commit yoxdur");
    return ok(session, session.commits.map((item, index) => `${session.commits.length - index}  ${item.message}`).join("\n"));
  }
  if (sub === "branch") return gitBranch(session, args[1] || answer);
  if (sub === "switch" || sub === "checkout") return gitSwitch(session, args[1] || answer);
  if (sub === "diff") return gitDiff(session);
  if (sub === "rebase") return gitRebase(session, args[1] || answer);
  if (sub === "commit") {
    if (!session.gitName) return askFor(session, { id: "git-name", title: "Commit üçün ad", label: "Ad", resume: "git commit" });
    if (!session.gitEmail) return askFor(session, { id: "git-email", title: "Commit üçün poçt", label: "E-poçt", resume: "git commit" });
    const flag = args.indexOf("-m");
    const message = flag >= 0 ? args[flag + 1] : answer;
    if (!message) return askFor(session, { id: "text", title: "Commit", label: "Mesaj", resume: "git commit" });
    const files = session.staged.length ? session.staged : Object.keys(session.files).filter((file) => file.startsWith(`${session.gitRoot}/`));
    session.commits.unshift({ message, files });
    session.staged = [];
    rememberHead(session);
    return ok(session, `[${session.branch || "main"}] ${message}`);
  }
  if (sub === "push") return push(session, args, answer);
  return fail(session, "git init | clone | pull | branch | switch | diff | rebase | push");
}

function gitClone(session: Session, args: string[], answer?: string): ShellOut {
  const repo = cleanRepo(args[1] || answer || "");
  if (!repo) return askFor(session, { id: "remote", title: "Klonla", label: "sahib/ad", resume: "git clone" });
  if (!repo.includes("/")) return fail(session, "depo belə yazılır: sahib/ad");
  const folder = args[2] || repo.split("/")[1] || "";
  if (!folder || folder.includes("..") || !/^[\w.-]+$/.test(folder)) return fail(session, "ad səhvdir");
  const dest = resolve(session.cwd, folder);
  const filled = Object.keys(session.files).some((file) => file === dest || file.startsWith(`${dest}/`));
  if (filled) return fail(session, `qovluq doludur: ${dest}`);
  return { ...ok(session, ""), clone: { repo, dest, pull: false } };
}

function gitPull(session: Session, answer?: string): ShellOut {
  if (!session.remote) return askFor(session, { id: "remote", title: "GitHub deposu", label: "sahib/ad", resume: "git pull" });
  if (!/^[\w.-]+\/[\w.-]+$/.test(session.remote)) return fail(session, "depo belə yazılır: sahib/ad");
  if (!session.gitRoot) session.gitRoot = session.cwd;
  void answer;
  return { ...ok(session, ""), clone: { repo: session.remote, dest: session.gitRoot, pull: true } };
}

function snapshot(session: Session) {
  const root = session.gitRoot;
  const out: Record<string, string> = {};
  if (!root) return out;
  for (const [path, body] of Object.entries(session.files)) {
    if (path !== root && !path.startsWith(`${root}/`)) continue;
    if (path.includes("/.git")) continue;
    const rel = path.slice(root.length).replace(/^\//, "");
    if (!rel || body.length > 20_000) continue;
    out[rel] = body;
    if (Object.keys(out).length >= 40) break;
  }
  return out;
}

function rememberHead(session: Session) {
  const name = session.branch || "main";
  session.branch = name;
  const prev = session.heads[name];
  session.heads[name] = { files: snapshot(session), base: { ...(prev?.base ?? {}) } };
}

function applyTree(session: Session, tree: Record<string, string>) {
  const root = session.gitRoot;
  const current = snapshot(session);
  for (const rel of Object.keys(current)) {
    if (!(rel in tree)) delete session.files[`${root}/${rel}`];
  }
  for (const [rel, body] of Object.entries(tree)) {
    if (rel.includes("..")) continue;
    const path = `${root}/${rel}`;
    ensureDir(session, parent(path));
    session.files[path] = body;
  }
}

function gitBranch(session: Session, name?: string): ShellOut {
  if (!name) {
    const names = new Set([session.branch || "main", ...Object.keys(session.heads)]);
    return ok(session, [...names].map((item) => `${item === session.branch ? "* " : "  "}${item}`).join("\n"));
  }
  if (!/^[\w.-]+$/.test(name)) return fail(session, "ad səhvdir");
  if (session.heads[name] || name === session.branch) return fail(session, `var: ${name}`);
  const shot = snapshot(session);
  session.heads[name] = { files: { ...shot }, base: { ...shot } };
  return ok(session, `${name} yaradıldı`);
}

function gitSwitch(session: Session, name?: string): ShellOut {
  if (!name) return askFor(session, { id: "text", title: "Budaq", label: "ad", resume: "git switch" });
  if (!session.heads[name]) return fail(session, `budaq yoxdur: ${name}`);
  rememberHead(session);
  applyTree(session, session.heads[name]?.files ?? {});
  session.branch = name;
  return ok(session, name);
}

function gitDiff(session: Session): ShellOut {
  const head = session.heads[session.branch || "main"]?.files ?? {};
  const now = snapshot(session);
  const keys = [...new Set([...Object.keys(head), ...Object.keys(now)])].sort();
  const lines: string[] = [];
  for (const key of keys) {
    if (head[key] === now[key]) continue;
    if (head[key] === undefined) lines.push(`+ ${key}`);
    else if (now[key] === undefined) lines.push(`- ${key}`);
    else lines.push(`~ ${key}`);
    if (lines.length >= 80) break;
  }
  return ok(session, lines.length ? lines.join("\n") : "fərq yoxdur");
}

function gitRebase(session: Session, target?: string): ShellOut {
  if (!target) return askFor(session, { id: "text", title: "Rebase", label: "main", resume: "git rebase" });
  const onto = session.heads[target];
  if (!onto) return fail(session, `budaq yoxdur: ${target}`);
  const name = session.branch || "main";
  if (name === target) return ok(session, "artıq bu budaqdasan");
  const mine = session.heads[name] ?? { files: snapshot(session), base: {} };
  const base = mine.base ?? {};
  const result: Record<string, string> = { ...onto.files };
  const keys = new Set([...Object.keys(mine.files), ...Object.keys(base)]);
  for (const key of keys) {
    const was = base[key];
    const now = mine.files[key];
    const there = onto.files[key];
    if (now === was) continue;
    if (there !== was && now !== there) return fail(session, `konflikt: ${key}`);
    if (now === undefined) delete result[key];
    else result[key] = now;
  }
  applyTree(session, result);
  session.heads[name] = { files: result, base: { ...onto.files } };
  return ok(session, `${name} → ${target}`);
}

export function importRepo(session: Session, dest: string, files: GithubFile[], repo: string, pull: boolean): ShellOut {
  const root = resolve(session.cwd, dest);
  const branch = session.branch || "main";
  session.branch = branch;
  const head = session.heads[branch]?.files ?? {};
  for (const file of files) {
    if (!file.path || file.path.includes("..") || file.path.startsWith("/")) continue;
    const path = `${root}/${file.path}`.replace(/\/{2,}/g, "/");
    const rel = file.path.replace(/^\//, "");
    const incoming = file.content.endsWith("\n") || file.content === "" ? file.content : `${file.content}\n`;
    const current = session.files[path];
    if (pull && current !== undefined && head[rel] !== undefined && current !== head[rel] && current !== incoming) {
      return fail(session, `konflikt: ${rel}`);
    }
  }
  let count = 0;
  for (const file of files.slice(0, 40)) {
    if (!file.path || file.path.includes("..") || file.path.startsWith("/")) continue;
    const path = `${root}/${file.path}`.replace(/\/{2,}/g, "/");
    ensureDir(session, parent(path));
    session.files[path] = file.content.endsWith("\n") || file.content === "" ? file.content : `${file.content}\n`;
    count += 1;
  }
  session.gitRoot = root;
  session.remote = repo;
  session.cwd = root;
  session.heads[branch] = { files: snapshot(session), base: { ...(session.heads[branch]?.base ?? {}) } };
  if (!pull) session.commits.unshift({ message: "clone", files: Object.keys(session.heads[branch].files) });
  const cut = files.length > 40 ? "\n… kəsildi" : "";
  return ok(session, `${pull ? `${count} fayl yeniləndi` : `${count} fayl · ${root}`}${cut}`);
}

export function saveRemoteFile(session: Session, name: string, content: string): ShellOut {
  if (!name || name.includes("..") || content.length > 100_000) return fail(session, "fayl çox böyükdür");
  const path = resolve(session.cwd, name);
  ensureDir(session, parent(path));
  session.files[path] = content.endsWith("\n") || content === "" ? content : `${content}\n`;
  return ok(session, path);
}

function push(session: Session, args: string[], answer?: string): ShellOut {
  const yes = args.includes("--yes") || answer === "bəli";
  if (!session.gitRoot) return fail(session, "əvvəl: git init");
  if (!session.remote) return askFor(session, { id: "remote", title: "GitHub deposu", label: "sahib/ad", resume: "git push" });
  if (!/^[\w.-]+\/[\w.-]+$/.test(session.remote)) return fail(session, "depo belə yazılır: sahib/ad");
  if (!session.hasToken) {
    return askFor(session, { id: "token", title: "GitHub tokeni", label: "Token ekrana yazılmır", secret: true, resume: yes ? "git push --yes" : "git push" });
  }
  const files = githubFiles(session);
  if (!files.length) return fail(session, "göndəriləcək fayl yoxdur");
  if (!yes) {
    return askFor(session, {
      id: "confirm",
      title: "GitHub-a göndərilsin?",
      label: `${session.remote} · ${files.length} fayl · depo yoxdursa özəl yaradılır`,
      resume: "git push --yes",
    });
  }
  const message = session.commits[0]?.message || "Nibras Terminal";
  return { ...ok(session, ""), github: { repo: session.remote, message, files } };
}

function githubFiles(session: Session): GithubFile[] {
  const root = session.gitRoot || "/root";
  return Object.entries(session.files)
    .filter(([path, body]) => (path === root || path.startsWith(`${root}/`)) && !path.includes("/.git") && body.length < 100_000)
    .slice(0, 40)
    .map(([path, content]) => ({ path: path.slice(root.length).replace(/^\//, ""), content }))
    .filter((file) => file.path);
}

function nibras(session: Session, args: string[], answer?: string): ShellOut {
  const sub = args[0];
  if (!sub) {
    const tools = session.pkgs.length ? session.pkgs.join(", ") : "yoxdur";
    return ok(session, [`mühit: ${session.env ?? "yoxdur"}`, `alətlər: ${tools}`, `qovluq: ${session.cwd}`, diskLine(session)].join("\n"));
  }
  if (sub === "ssh" && args[1] === "sil") return { ...ok(session, "SSH şifrəsi silindi."), clearSsh: true };
  if (sub === "disk") return ok(session, diskLine(session));
  if (sub === "zibil") {
    if (!session.trash.length) return ok(session, "zibil boşdur");
    return ok(session, session.trash.map((item, index) => `${index + 1}  ${item.path}`).join("\n"));
  }
  if (sub === "qaytar") {
    if (!session.trash.length) return fail(session, "zibil boşdur");
    const pick = args[1] || answer;
    let index = 0;
    if (pick) {
      const number = Number(pick);
      index = Number.isInteger(number) && number >= 1 ? number - 1 : session.trash.findIndex((item) => item.path === resolve(session.cwd, pick) || item.path.endsWith(`/${pick}`));
    }
    const item = session.trash[index];
    if (!item) return fail(session, "zibil boşdur");
    if (session.files[item.path] !== undefined) return fail(session, `var: ${item.path}`);
    ensureDir(session, parent(item.path));
    session.files[item.path] = item.body;
    session.trash.splice(index, 1);
    return ok(session, `qaytarıldı: ${item.path}`);
  }
  if (sub === "gitignore") {
    const path = resolve(session.cwd, ".gitignore");
    if (session.files[path] !== undefined) return ok(session, ".gitignore artıq var");
    session.files[path] = "node_modules/\n.env\n*.zip\n";
    return ok(session, ".gitignore");
  }
  if (sub === "token") {
    if (args[1] === "sil") {
      session.hasToken = false;
      return { ...ok(session, "Token silindi."), clearToken: true };
    }
    if (args[1]) {
      session.hasToken = true;
      return { ...ok(session, "Token saxlandı. Ekrana yazılmır."), token: args.slice(1).join(" ") };
    }
    if (!answer) return askFor(session, { id: "token", title: "Token", label: "GitHub tokeni. Ekrana yazılmır.", secret: true, resume: "nibras token" });
    session.hasToken = true;
    return ok(session, "Token saxlandı. Ekrana yazılmır.");
  }
  if (sub === "stop") {
    session.server = null;
    return ok(session, "server dayandı");
  }
  if (sub === "serve") {
    const html = session.files[resolve(session.cwd, "index.html")] ?? session.files["/root/Projects/web/index.html"];
    if (!html) return fail(session, "index.html yoxdur. nibras layihe web");
    session.server = { port: 8000, html };
    return ok(session, "önizləmə açıldı · dayandırmaq: nibras stop");
  }
  if (sub === "md") return markdown(session, args[1] || answer);
  if (sub === "layihe" || sub === "project") return project(session, args[1], args[2] || answer);
  if (sub === "sil" || sub === "remove") return removePkg(session, args[1] || answer);
  if (sub === "install") return install(session, args[1], args.includes("--yes") || answer === "bəli");
  return fail(session, "nibras install | sil | disk | token | layihe | serve | md | stop | zibil | qaytar | gitignore");
}

function install(session: Session, name: string | undefined, yes: boolean): ShellOut {
  if (!name) return fail(session, "nibras install alpine | debian | ubuntu | python | node | php | git | build");
  const key = alias(name);
  if (!key) return fail(session, `naməlum paket: ${name}`);
  if (ENVS.includes(key as EnvName)) {
    const env = key as EnvName;
    if (session.env === env) return ok(session, `${env} artıq seçilib`);
    const replacing = Boolean(session.env && session.env !== env);
    const size = SIZES[env] ?? 0;
    const used = usedBytes(session) - (session.env ? (SIZES[session.env] ?? 0) : 0);
    if (!yes && (replacing || size > 8_000_000)) {
      const extra = replacing ? ` ${session.env} silinəcək. Eyni anda iki mühit saxlanmır.` : "";
      return askFor(session, {
        id: "confirm",
        title: `${env} seçilsin?`,
        label: `${formatBytes(size)} yer tutacaq. Boş ${formatBytes(Math.max(0, DISK_BUDGET - used - size))}.${extra}`,
        resume: `nibras install ${env} --yes`,
      });
    }
    if (used + size > DISK_BUDGET) return fail(session, "yer çatmır");
    session.env = env;
    seedSystem(session, env);
    return ok(session, `${env} seçildi · ${formatBytes(size)}\n${diskLine(session)}`);
  }
  if (session.pkgs.includes(key)) return ok(session, `${key} artıq seçilib`);
  if (!session.env) return fail(session, "Əvvəl mühit seç: nibras install alpine");
  const size = SIZES[key] ?? 0;
  if (!yes && size > 8_000_000) {
    return askFor(session, {
      id: "confirm",
      title: `${key} yüklənsin?`,
      label: `${formatBytes(size)}. İlk işlətmədə gəlir, tətbiqin içinə basdırılmır. Boş ${formatBytes(Math.max(0, DISK_BUDGET - usedBytes(session) - size))}.`,
      resume: `nibras install ${key} --yes`,
    });
  }
  if (usedBytes(session) + size > DISK_BUDGET) return fail(session, "yer çatmır");
  session.pkgs.push(key);
  return ok(session, `${key} hazırdır · ${formatBytes(size)}`);
}

function seedSystem(session: Session, env: EnvName) {
  const pretty = env === "ubuntu" ? "Ubuntu 24.04.3 LTS" : env === "debian" ? "Debian GNU/Linux 12 (bookworm)" : "Alpine Linux 3.24.1";
  const version = env === "ubuntu" ? "24.04" : env === "debian" ? "12" : "3.24.1";
  const code = env === "ubuntu" ? "noble" : env === "debian" ? "bookworm" : "";
  ensureDir(session, "/etc");
  session.files["/etc/os-release"] = `NAME="${pretty}"\nID=${env}\nVERSION_ID="${version}"\nPRETTY_NAME="${pretty}"\n${code ? `VERSION_CODENAME=${code}\n` : ""}`;
  session.files["/etc/hostname"] = "nibras\n";
  session.files["/etc/motd"] = `${pretty}\nNibras Terminal\n`;
  if (env === "alpine") {
    session.user = "root";
    session.home = "/root";
    session.envVars.HOME = "/root";
    session.envVars.USER = "root";
    session.envVars.LOGNAME = "root";
    writePasswd(session);
    seedPhone(session);
    return;
  }
  ensureDir(session, "/etc/apt");
  ensureDir(session, "/home/nibras");
  session.files["/etc/lsb-release"] =
    `DISTRIB_ID=${env === "ubuntu" ? "Ubuntu" : "Debian"}\nDISTRIB_RELEASE=${version}\nDISTRIB_CODENAME=${code}\nDISTRIB_DESCRIPTION="${pretty}"\n`;
  session.files["/etc/apt/sources.list"] = "deb nibras noble main\n# python node php git build\n";
  if (!session.files["/home/nibras/.bashrc"]) session.files["/home/nibras/.bashrc"] = "alias ll='ls -l'\nalias la='ls -a'\nexport TERM=nibras\n";
  session.user = "nibras";
  session.home = "/home/nibras";
  session.cwd = "/home/nibras";
  session.envVars.HOME = "/home/nibras";
  session.envVars.USER = "nibras";
  session.envVars.LOGNAME = "nibras";
  session.aliases.ll = "ls -l";
  session.aliases.la = "ls -a";
  writePasswd(session);
  seedPhone(session);
}

function writePasswd(session: Session) {
  const lines = ["root:x:0:0:root:/root:/bin/bash"];
  if (session.env !== "alpine") lines.push("nibras:x:1000:1000:Nibras:/home/nibras:/bin/bash");
  const current = session.files["/etc/passwd"] ?? "";
  for (const line of current.split("\n")) {
    const name = line.split(":")[0];
    if (name && name !== "root" && name !== "nibras") lines.push(line);
  }
  session.files["/etc/passwd"] = `${lines.filter(Boolean).join("\n")}\n`;
}

function passwdRow(session: Session, name: string) {
  return (session.files["/etc/passwd"] ?? "").split("\n").find((line) => line.startsWith(`${name}:`));
}

function sudoCmd(session: Session, args: string[], answer: string | undefined, stdin: string | undefined): ShellOut {
  const cut = args.findIndex((arg) => !arg.startsWith("-"));
  if (cut < 0) return ok(session, "root");
  const hash = session.shadow[session.user];
  if (session.user !== "root" && hash) {
    if (!answer) return askFor(session, { id: "text", title: "sudo", label: "Şifrə ekrana yazılmır", secret: true, resume: `sudo ${args.join(" ")}` });
    if (sha256(answer) !== hash) return fail(session, "şifrə səhvdir");
  }
  const previous = session.user;
  session.user = "root";
  session.envVars.USER = "root";
  const result = dispatch(session, args[cut] ?? "", args.slice(cut + 1), undefined, stdin);
  if (!result.ask) {
    result.session.user = previous;
    result.session.envVars.USER = previous;
  }
  return result;
}

function idLine(session: Session) {
  if ((session.user || "root") === "root") return "uid=0(root) gid=0(root)";
  const row = passwdRow(session, session.user);
  const uid = row?.split(":")[2] || "1000";
  return `uid=${uid}(${session.user}) gid=${uid}(${session.user})`;
}

function lsbRelease(session: Session): ShellOut {
  const body = session.files["/etc/lsb-release"];
  if (!body) return fail(session, "əvvəl Ubuntu və ya Debian seç");
  return ok(session, body.replace(/\n$/, "").replaceAll("DISTRIB_", "").replaceAll("=", ": "));
}

function dpkgCmd(session: Session, args: string[]): ShellOut {
  if (session.env === "alpine") return fail(session, "Alpine-də apk yaz");
  if (!session.env) return fail(session, "Əvvəl mühit seç");
  if (args[0] === "-s" || args[0] === "--status") {
    const key = alias(args[1] || "");
    if (!key || !session.pkgs.includes(key)) return fail(session, `quraşdırılmayıb: ${args[1] || ""}`.trim());
    return ok(session, `Package: ${key}\nStatus: install ok installed\n${PKG_INFO[key] ?? ""}`.trim());
  }
  const lines = session.pkgs.map((name) => `ii  ${name}  ${PKG_INFO[name] ?? ""}`.trimEnd());
  return ok(session, lines.length ? lines.join("\n") : "paket yoxdur");
}

function ipCmd(session: Session) {
  return ok(session, "1: lo    inet 127.0.0.1/8");
}

function ssCmd(session: Session) {
  if (!session.server) return ok(session, "dinləyən port yoxdur");
  return ok(session, `LISTEN  127.0.0.1:${session.server.port}`);
}

function psCmd(session: Session) {
  const lines = ["1  nibras"];
  if (session.server) lines.push(`2  server  127.0.0.1:${session.server.port}`);
  for (const job of session.jobs.filter((item) => item.state === "işləyir")) lines.push(`${job.id}  ${job.cmd}`);
  return ok(session, lines.join("\n"));
}

function jobsCmd(session: Session) {
  if (!session.jobs.length) return ok(session, "iş yoxdur");
  return ok(session, session.jobs.map((job) => `[${job.id}]  ${job.state}  ${job.cmd}`).join("\n"));
}

function sleepCmd(session: Session, args: string[]): ShellOut {
  const seconds = Number(args[0] ?? "1");
  if (!Number.isFinite(seconds) || seconds < 0) return fail(session, "sleep saniyə");
  const capped = Math.min(3, seconds);
  const note = seconds > 3 ? "ən çoxu 3 saniyə" : "";
  return { ...ok(session, note), sleep: Math.round(capped * 1000) };
}

function lnCmd(session: Session, args: string[]): ShellOut {
  const symbolic = args.includes("-s");
  const names = args.filter((arg) => !arg.startsWith("-"));
  if (!symbolic) return fail(session, "ln -s hədəf ad");
  const [target, name] = names;
  if (!target || !name) return fail(session, "ln -s hədəf ad");
  const path = at(session, name, false);
  if (path === "/" || path === session.home) return fail(session, "bu yol olmaz");
  session.links[path] = target;
  ensureDir(session, parent(path));
  return ok(session, path);
}

function readlinkCmd(session: Session, args: string[]): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-"));
  if (!name) return fail(session, "readlink ad");
  const path = at(session, name, false);
  const target = session.links[path];
  if (!target) return fail(session, `keçid deyil: ${name}`);
  return ok(session, target);
}

function addUser(session: Session, name?: string): ShellOut {
  if (!name) return askFor(session, { id: "text", title: "İstifadəçi", label: "qısa ad", resume: "adduser" });
  if (!/^[\w.-]+$/.test(name) || name === "root") return fail(session, "ad səhvdir");
  if (passwdRow(session, name)) return fail(session, `var: ${name}`);
  const home = `/home/${name}`;
  ensureDir(session, home);
  session.files["/etc/passwd"] = `${session.files["/etc/passwd"] ?? ""}${name}:x:1001:1001:${name}:${home}:/bin/bash\n`;
  return ok(session, home);
}

function passwdCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || session.user;
  if (!passwdRow(session, name) && name !== "root") return fail(session, `istifadəçi yoxdur: ${name}`);
  if (!answer) return askFor(session, { id: "text", title: "Şifrə", label: "Ekrana yazılmır", secret: true, resume: `passwd ${name}` });
  if (answer.length < 4) return fail(session, "şifrə qısadır");
  session.shadow[name] = sha256(answer);
  return ok(session, "şifrə saxlandı");
}

function suCmd(session: Session, name?: string): ShellOut {
  if (!name) return askFor(session, { id: "text", title: "su", label: "istifadəçi", resume: "su" });
  const row = passwdRow(session, name);
  if (!row && name !== "root") return fail(session, `istifadəçi yoxdur: ${name}`);
  const home = row?.split(":")[5] || (name === "root" ? "/root" : `/home/${name}`);
  session.user = name;
  session.home = home;
  session.cwd = hasDir(session, home) ? home : session.cwd;
  session.envVars.HOME = home;
  session.envVars.USER = name;
  session.envVars.LOGNAME = name;
  return ok(session, name);
}

function sourceCmd(session: Session, name?: string): ShellOut {
  if (!name) return fail(session, "source fayl");
  const path = at(session, name);
  const body = session.files[path];
  if (body === undefined) return fail(session, `fayl yoxdur: ${name}`);
  return runSequence(session, body, undefined, 1);
}

function at(session: Session, input: string, follow = true) {
  const raw = input === "~" || input.startsWith("~/") ? `${session.home}${input.slice(1)}` : input;
  const path = resolve(session.cwd, raw, session.home || "/root");
  return follow ? followLink(session, path) : path;
}

function followLink(session: Session, path: string) {
  let current = path;
  for (let hop = 0; hop < 8; hop += 1) {
    const target = session.links[current];
    if (!target) return current;
    current = target.startsWith("/") ? resolve("/", target) : resolve(parent(current), target);
  }
  return current;
}

function restOf(parts: Array<{ text: string; next: string }>, index: number) {
  const bits: string[] = [];
  for (let cursor = index + 1; cursor < parts.length; cursor += 1) {
    const glue = parts[cursor - 1]?.next === "and" ? " && " : parts[cursor - 1]?.next === "or" ? " || " : "; ";
    bits.push(`${glue}${parts[cursor]?.text ?? ""}`);
  }
  return bits.join("").trim();
}

function removePkg(session: Session, name?: string): ShellOut {
  if (!name) return askFor(session, { id: "text", title: "Nə silinsin?", label: "alpine, python, node, php…", resume: "nibras sil" });
  const key = alias(name);
  if (!key) return fail(session, `naməlum: ${name}`);
  if (ENVS.includes(key as EnvName)) {
    if (session.env !== key) return fail(session, "seçilməyib");
    session.env = null;
    return ok(session, `${key} silindi`);
  }
  session.pkgs = session.pkgs.filter((item) => item !== key);
  if (key === "node") session.server = null;
  return ok(session, `${key} silindi`);
}

function project(session: Session, kind?: string, name?: string): ShellOut {
  if (!kind) return fail(session, "nibras layihe python | node | web | php | c | md");
  if (!name) return askFor(session, { id: "text", title: "Layihə adı", label: "Qısa ad", resume: `nibras layihe ${kind}` });
  const safe = name.trim().replace(/[^\w.-]+/g, "-");
  if (!safe) return fail(session, "ad boşdur");
  const root = `/root/Projects/${safe}`;
  ensureDir(session, "/root/Projects");
  ensureDir(session, root);
  if (kind === "python") session.files[`${root}/main.py`] = 'print("Salam, Nibras Terminal!")\n';
  else if (kind === "node") {
    session.files[`${root}/server.js`] = [
      'const http = require("http");',
      "const server = http.createServer((req, res) => {",
      '  res.writeHead(200, {"Content-Type": "text/plain; charset=utf-8"});',
      '  res.end("Salam, Nibras Terminal!\\n");',
      "});",
      'server.listen(3000, "127.0.0.1", () => console.log("Server: http://127.0.0.1:3000"));',
      "",
    ].join("\n");
  } else if (kind === "web") {
    const html =
      "<!doctype html><html lang=\"az\"><meta charset=\"utf-8\"><title>Nibras</title><body style=\"margin:0;min-height:100vh;display:grid;place-items:center;background:#07101c;color:#f3ecdf;font-family:Georgia,serif\"><h1>Nibras Terminal</h1></body></html>\n";
    session.files[`${root}/index.html`] = html;
  } else if (kind === "php") {
    session.files[`${root}/index.php`] = "<?php echo \"Salam, Nibras Terminal!\";\n";
  } else if (kind === "c") {
    session.files[`${root}/main.c`] = '#include <stdio.h>\nint main(void) {\n    printf("Salam, Nibras Terminal!\\n");\n    return 0;\n}\n';
  } else if (kind === "md" || kind === "qeyd") {
    session.files[`${root}/qeyd.md`] = "# Qeyd\n\n**Nibras Terminal**\n\n- birinci\n- ikinci\n\n`nibras md qeyd.md`\n";
  } else return fail(session, "şablon: python, node, web, php, c, md");
  session.cwd = root;
  const open = Object.keys(session.files).find((file) => file.startsWith(`${root}/`));
  return { ...ok(session, root), open };
}

function seedPhone(session: Session) {
  const home = session.home || "/root";
  const prefix = "/data/data/com.nibras.terminal/files/usr";
  session.envVars.PREFIX = prefix;
  session.envVars.TMPDIR = "/data/data/com.nibras.terminal/files/usr/tmp";
  ensureDir(session, `${prefix}/bin`);
  ensureDir(session, `${prefix}/etc`);
  ensureDir(session, `${prefix}/tmp`);
  ensureDir(session, `${home}/.nibras`);
  if (!session.files[`${prefix}/etc/motd`]) session.files[`${prefix}/etc/motd`] = "Nibras PREFIX. Əmrlər bu qabıqda işləyir.\n";
  if (!session.files[`${home}/.nibras/nibras.properties`]) {
    session.files[`${home}/.nibras/nibras.properties`] =
      "extra-keys = [['ESC','/','-','HOME','UP','END','PGUP'],['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN']]\n";
  }
}

export function extraKeyRows(session: Session): string[][] {
  const fallback = [
    ["ESC", "/", "-", "HOME", "UP", "END", "PGUP"],
    ["TAB", "CTRL", "ALT", "LEFT", "DOWN", "RIGHT", "PGDN"],
  ];
  const home = session.home || "/root";
  const body = session.files[`${home}/.nibras/nibras.properties`] ?? session.files[`${home}/.termux/termux.properties`] ?? "";
  const match = /extra-keys\s*=\s*(\[[\s\S]*\])/.exec(body);
  if (!match?.[1]) return fallback;
  try {
    const parsed = JSON.parse(match[1].replace(/'/g, '"')) as unknown;
    if (!Array.isArray(parsed)) return fallback;
    const rows = parsed.slice(0, 3).map((row) => (Array.isArray(row) ? row.map((item) => String(item).slice(0, 8)).filter(Boolean).slice(0, 12) : []));
    return rows.some((row) => row.length) ? rows : fallback;
  } catch {
    return fallback;
  }
}

function phonePkg(session: Session, args: string[], answer?: string): ShellOut {
  const verb = args[0] || answer;
  const manager = session.env === "alpine" ? "apk" : "apt";
  if (!verb || verb === "help") return ok(session, "pkg update | install | uninstall | search | list-all | list-installed | show");
  if (verb === "update" || verb === "upgrade") return pkg(session, manager, ["update"]);
  if (verb === "search") return pkg(session, manager, ["search", args[1] || ""]);
  if (verb === "list-all" || verb === "list") return pkg(session, manager, ["list"]);
  if (verb === "list-installed") {
    const lines = session.pkgs.map((name) => `* ${name}`);
    return ok(session, lines.length ? lines.join("\n") : "paket yoxdur");
  }
  if (verb === "show") return pkg(session, manager, ["show", args[1] || answer || ""]);
  if (verb === "install") {
    const name = args[1] || answer;
    if (!name) return askFor(session, { id: "text", title: "Paket", label: "python, node, php…", resume: "pkg install" });
    return pkg(session, manager, ["install", name, ...args.filter((arg) => arg === "--yes")], answer);
  }
  if (verb === "uninstall" || verb === "remove") return removePkg(session, args[1] || answer);
  if (verb === "reinstall") {
    const name = args[1] || answer;
    if (!name) return askFor(session, { id: "text", title: "Paket", label: "python, node, php…", resume: "pkg reinstall" });
    removePkg(session, name);
    return pkg(session, manager, ["install", name, "--yes"], "bəli");
  }
  return fail(session, "pkg update | install | uninstall | search | list-all");
}

function distroCmd(session: Session, args: string[], answer?: string): ShellOut {
  const sub = args[0] || "list";
  if (sub === "list") return ok(session, ENVS.map((name) => `${session.env === name ? "* " : "  "}${name}`).join("\n"));
  const name = args[1] || answer;
  if (sub === "install" || sub === "login") {
    if (!name) return askFor(session, { id: "text", title: "Mühit", label: "ubuntu, debian, alpine", resume: `nibras-distro ${sub}` });
    return install(session, name, sub === "login" || args.includes("--yes") || answer === "bəli");
  }
  if (sub === "remove" || sub === "remove-all") return removePkg(session, name);
  return fail(session, "nibras-distro list | install | login | remove");
}

function topCmd(session: Session): ShellOut {
  const lines = [
    `istifadəçi  ${session.user || "root"}`,
    `mühit  ${session.env ?? "seçilməyib"}`,
    `prefix  ${session.envVars.PREFIX || "-"}`,
    `disk  ${formatBytes(usedBytes(session))}`,
    session.server ? `server  127.0.0.1:${session.server.port}` : "server  yoxdur",
  ];
  if (session.jobs.length) lines.push(...session.jobs.map((job) => `[${job.id}] ${job.state} ${job.cmd}`));
  return ok(session, lines.join("\n"));
}

function keygenCmd(session: Session, args: string[]): ShellOut {
  const flag = args.indexOf("-f");
  const asked = flag >= 0 ? args[flag + 1] : "";
  const path = at(session, asked || `${session.home || "/root"}/.ssh/id_ecdsa`, false);
  if (session.files[path] !== undefined) return fail(session, `var: ${path}`);
  return { ...ok(session, ""), keygen: { path } };
}

export function writeKey(session: Session, path: string, priv: string, pub: string): Session {
  const next = structuredClone(session);
  ensureDir(next, parent(path));
  next.files[path] = priv.endsWith("\n") ? priv : `${priv}\n`;
  next.files[`${path}.pub`] = pub.endsWith("\n") ? pub : `${pub}\n`;
  return next;
}

function phoneCmd(session: Session, cmd: string, args: string[], answer?: string): ShellOut {
  if (cmd === "nibras-setup-storage") {
    const root = `${session.home || "/root"}/storage`;
    for (const name of ["shared", "downloads", "dcim", "pictures", "music", "movies"]) ensureDir(session, `${root}/${name}`);
    session.files[`${root}/README.txt`] = "shared  downloads  dcim  pictures  music  movies\n";
    return ok(session, root);
  }
  if (cmd === "nibras-reload-settings") {
    const path = `${session.home || "/root"}/.nibras/nibras.properties`;
    const body = session.files[path];
    if (!body) return fail(session, `fayl yoxdur: ${path}`);
    if (!/extra-keys\s*=/.test(body)) return fail(session, "extra-keys yoxdur");
    const match = /extra-keys\s*=\s*(\[[\s\S]*\])/.exec(body);
    const raw = match ? match[1] : "";
    try {
      JSON.parse(raw.replace(/'/g, '"'));
    } catch {
      return fail(session, "extra-keys oxunmadı");
    }
    return ok(session, "açarlar yeniləndi");
  }
  if (cmd === "nibras-clipboard-get") return { ...ok(session, ""), device: { kind: "clip-get" } };
  if (cmd === "nibras-clipboard-set") {
    const text = args.join(" ") || answer;
    if (!text) return askFor(session, { id: "text", title: "Lövhə", label: "Mətn", resume: "nibras-clipboard-set" });
    return { ...ok(session, ""), device: { kind: "clip-set", text } };
  }
  if (cmd === "nibras-toast") {
    const text = args.join(" ") || answer;
    if (!text) return askFor(session, { id: "text", title: "Toast", label: "Mətn", resume: "nibras-toast" });
    return { ...ok(session, ""), device: { kind: "toast", text } };
  }
  if (cmd === "nibras-vibrate") {
    const ms = Math.min(1000, Math.max(1, Number(args[0] || 200) || 200));
    return { ...ok(session, ""), device: { kind: "vibrate", ms } };
  }
  if (cmd === "nibras-battery-status") return { ...ok(session, ""), device: { kind: "battery" } };
  if (cmd === "nibras-tts-speak") {
    const text = args.join(" ") || answer;
    if (!text) return askFor(session, { id: "text", title: "Səs", label: "Mətn", resume: "nibras-tts-speak" });
    return { ...ok(session, ""), device: { kind: "speak", text } };
  }
  if (cmd === "nibras-open-url") {
    const url = args[0] || answer || "";
    if (!url) return askFor(session, { id: "text", title: "Ünvan", label: "https://", resume: "nibras-open-url" });
    let parsed: URL;
    try {
      parsed = new URL(url);
    } catch {
      return fail(session, "ünvan səhvdir");
    }
    if (parsed.protocol !== "https:" || isBlockedHost(parsed.hostname)) return fail(session, "bu ünvan bağlıdır");
    return { ...ok(session, ""), device: { kind: "open", url } };
  }
  if (cmd === "nibras-notification") {
    const title = args[0] || answer || "Nibras";
    const body = args.slice(1).join(" ") || title;
    return { ...ok(session, ""), device: { kind: "notify", title, body } };
  }
  if (cmd === "nibras-dialog") {
    if (!answer) return askFor(session, { id: "text", title: args[0] || "Dialog", label: "Mətn", resume: "nibras-dialog" });
    return ok(session, answer);
  }
  if (cmd === "nibras-wake-lock") return { ...ok(session, ""), device: { kind: "wake-lock" } };
  if (cmd === "nibras-wake-unlock") return { ...ok(session, ""), device: { kind: "wake-unlock" } };
  if (cmd === "nibras-share") {
    const text = args.join(" ") || answer;
    if (!text) return askFor(session, { id: "text", title: "Paylaş", label: "Mətn", resume: "nibras-share" });
    return { ...ok(session, ""), device: { kind: "share", text } };
  }
  if (cmd === "nibras-wifi-connectioninfo") return { ...ok(session, ""), device: { kind: "network" } };
  return fail(session, "nibras-setup-storage | clipboard | toast | vibrate | battery-status | tts-speak | open-url | notification | dialog | wake-lock | share");
}

function pkg(session: Session, cmd: string, args: string[], answer?: string): ShellOut {
  if (cmd === "apk" && session.env && session.env !== "alpine") return fail(session, "Bu mühitdə apt install yaz");
  if ((cmd === "apt" || cmd === "apt-get") && session.env === "alpine") return fail(session, "Alpine-də apk add yaz");
  const verb = args[0];
  const query = (verb === "search" || verb === "list" ? args[1] || answer || "" : "").toLowerCase();
  if (verb === "update" || verb === "upgrade") {
    if (!session.env) return fail(session, "Əvvəl mühit seç");
    return ok(session, `${cmd} siyahısı hazır · ${Object.keys(PKG_INFO).length} paket`);
  }
  if (verb === "list" || verb === "search") {
    const lines = Object.keys(PKG_INFO)
      .filter((name) => !query || name.includes(query))
      .map((name) => `${session.pkgs.includes(name) ? "* " : "  "}${name}`);
    return ok(session, lines.length ? lines.join("\n") : "tapılmadı");
  }
  if (verb === "show") {
    const key = alias(args[1] || answer || "");
    if (!key || !PKG_INFO[key]) return fail(session, `${args[1] ?? "paket"} bu seçim siyahısında yoxdur`);
    return ok(session, `${key}\n${PKG_INFO[key]}`);
  }
  if (!session.env) return fail(session, "Əvvəl mühit seç");
  if (verb !== "install" && verb !== "add") return fail(session, `${cmd} update | list | search | show | install`);
  const key = alias(args[1] || "");
  if (!key || !PKG_INFO[key]) return fail(session, `${args[1] ?? "paket"} bu seçim siyahısında yoxdur`);
  return install(session, key, answer === "bəli" || args.includes("--yes"));
}

const PKG_INFO: Record<string, string> = {
  python: "Python 3. Telefonun içində işləyir, tətbiqə basdırılmır.",
  node: "Node.js. Skript və yerli server.",
  php: "PHP skript.",
  git: "Git əmrləri artıq buradadır. Bu seçim aləti qeyd edir.",
  build: "Sadə C: gcc, printf, if, while, for.",
};

function alias(name: string) {
  const map: Record<string, string> = {
    alpine: "alpine",
    debian: "debian",
    ubuntu: "ubuntu",
    python: "python",
    python3: "python",
    py3: "python",
    node: "node",
    nodejs: "node",
    git: "git",
    php: "php",
    "php-cli": "php",
    build: "build",
    gcc: "build",
    "build-base": "build",
    "build-essential": "build",
  };
  return map[name];
}

function pingCmd(session: Session, args: string[], answer?: string): ShellOut {
  const host = (args.find((arg) => !arg.startsWith("-")) || answer || "").replace(/^https?:\/\//, "").split("/")[0]?.split(":")[0] ?? "";
  if (!host) return askFor(session, { id: "text", title: "Ping", label: "github.com", resume: "ping" });
  if (!/^[a-z0-9.-]+$/i.test(host) || isBlockedHost(host)) return fail(session, "bu ünvan bağlıdır");
  return { ...ok(session, ""), probe: { host } };
}

export function isBlockedHost(host: string) {
  const name = host.toLowerCase().replace(/\.$/, "");
  if (!name || name === "localhost" || name.endsWith(".local") || name.endsWith(".internal") || name.endsWith(".localhost")) return true;
  return isPrivateIp(name);
}

export function isPrivateIp(host: string) {
  if (host === "::1" || host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd")) return true;
  const parts = host.split(".");
  if (parts.length !== 4) return false;
  const nums = parts.map((part) => (/^\d{1,3}$/.test(part) ? Number(part) : NaN));
  if (nums.some((part) => !Number.isInteger(part) || part < 0 || part > 255)) return false;
  const [a, b] = nums;
  if (a === 0 || a === 10 || a === 127) return true;
  if (a === 169 && b === 254) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a === 192 && b === 168) return true;
  return false;
}

function sshCmd(session: Session, args: string[], answer?: string): ShellOut {
  const parsed = parseSsh(args);
  if (!parsed.host) return askFor(session, { id: "text", title: "SSH", label: "istifadəçi@ünvan", resume: "ssh" });
  if (isBlockedHost(parsed.host)) return fail(session, "bu ünvan bağlıdır");
  if (!session.hasSsh) {
    if (!answer) return askFor(session, { id: "ssh", title: "SSH şifrəsi", label: "Ekrana yazılmır", secret: true, resume: sshResume(parsed) });
    if (!parsed.command) {
      return {
        ...askFor(session, { id: "text", title: "Uzaq əmr", label: "məsələn id", resume: `ssh ${parsed.user}@${parsed.host} -p ${parsed.port}` }),
        keepSsh: answer,
      };
    }
    return { ...ok(session, ""), keepSsh: answer, remote: { kind: "exec", user: parsed.user, host: parsed.host, port: parsed.port, command: parsed.command } };
  }
  const command = parsed.command || answer;
  if (!command) return askFor(session, { id: "text", title: "Uzaq əmr", label: "məsələn id", resume: `ssh ${parsed.user}@${parsed.host} -p ${parsed.port}` });
  return { ...ok(session, ""), remote: { kind: "exec", user: parsed.user, host: parsed.host, port: parsed.port, command } };
}

function sftpCmd(session: Session, args: string[], answer?: string): ShellOut {
  const verb = args[0];
  if (verb !== "get" && verb !== "put") return fail(session, "sftp get istifadəçi@ünvan:fayl yerli\nsftp put yerli istifadəçi@ünvan:fayl");
  const spec = verb === "get" ? args[1] : args[2];
  const local = verb === "get" ? args[2] : args[1];
  const login = parseRemotePath(spec || "");
  if (!login || !local) {
    return askFor(session, { id: "text", title: "SFTP", label: verb === "get" ? "istifadəçi@ünvan:fayl yerli" : "yerli istifadəçi@ünvan:fayl", resume: `sftp ${verb}` });
  }
  if (isBlockedHost(login.host)) return fail(session, "bu ünvan bağlıdır");
  const remote: NonNullable<ShellOut["remote"]> = { kind: verb === "get" ? "get" : "put", user: login.user, host: login.host, port: 22, remotePath: login.path, localPath: local };
  if (remote.kind === "put") {
    const path = resolve(session.cwd, local);
    const content = session.files[path];
    if (content === undefined) return fail(session, `fayl yoxdur: ${local}`);
    if (content.length > 100_000 || content.includes("\0")) return fail(session, "fayl çox böyükdür");
    remote.content = content;
  }
  if (!session.hasSsh) {
    if (!answer) return askFor(session, { id: "ssh", title: "SSH şifrəsi", label: "Ekrana yazılmır", secret: true, resume: `sftp ${args.join(" ")}` });
    return { ...ok(session, ""), keepSsh: answer, remote };
  }
  return { ...ok(session, ""), remote };
}

function parseSsh(args: string[]) {
  let port = 22;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    if (args[index] === "-p" && args[index + 1]) {
      port = Number(args[index + 1]);
      index += 1;
      continue;
    }
    rest.push(args[index] ?? "");
  }
  const match = /^(?:([^@]+)@)?([^:\s]+)$/.exec(rest[0] || "");
  const host = match?.[2] ?? "";
  const user = match?.[1] || "root";
  const command = rest.slice(1).join(" ").trim();
  if (!Number.isInteger(port) || port < 1 || port > 65535) port = 22;
  return { user, host, port, command };
}

function sshResume(parsed: { user: string; host: string; port: number; command: string }) {
  const base = `ssh ${parsed.user}@${parsed.host} -p ${parsed.port}`;
  return parsed.command ? `${base} ${parsed.command}` : base;
}

function parseRemotePath(spec: string) {
  const match = /^([^@]+)@([^:]+):(.+)$/.exec(spec);
  if (!match?.[1] || !match[2] || !match[3] || match[3].includes("..")) return undefined;
  return { user: match[1], host: match[2], path: match[3] };
}

function askFor(session: Session, ask: Ask): ShellOut {
  return { session, output: "", ok: false, ask };
}

function ok(session: Session, output: string): ShellOut {
  return { session, output, ok: true };
}

function fail(session: Session, output: string): ShellOut {
  const path = "/var/log/nibras.log";
  const prev = session.files[path] ?? "";
  const line = output.split("\n")[0]?.slice(0, 180) ?? "";
  if (line) {
    session.dirs = session.dirs.includes("/var/log") ? session.dirs : [...session.dirs, "/var", "/var/log"];
    const body = `${prev}${line}\n`.split("\n").slice(-41).join("\n");
    session.files[path] = body.endsWith("\n") ? body : `${body}\n`;
  }
  return { session, output, ok: false };
}

function pause(result: ShellOut) {
  return Boolean(
    result.ask ||
      result.python ||
      result.node ||
      result.php ||
      result.github ||
      result.repl ||
      result.open ||
      result.fetch ||
      result.clone ||
      result.probe ||
      result.remote ||
      result.sleep ||
      result.device ||
      result.keygen ||
      result.output === "\u0000clear",
  );
}

function stamp(result: ShellOut) {
  if (!result.ask) result.session.envVars["?"] = result.ok === false ? "1" : "0";
  return result;
}

function testCmd(session: Session, args: string[], bracket: boolean): ShellOut {
  const list = bracket ? args.slice(0, args.at(-1) === "]" ? -1 : args.length) : args;
  if (bracket && args.at(-1) !== "]") return fail(session, "[ ... ]");
  const pass = (value: boolean) => (value ? ok(session, "") : fail(session, ""));
  if (list[0] === "-f") return pass(session.files[at(session, list[1] ?? "")] !== undefined);
  if (list[0] === "-d") return pass(hasDir(session, at(session, list[1] ?? "")));
  if (list[0] === "-e") {
    const path = at(session, list[1] ?? "");
    return pass(session.files[path] !== undefined || hasDir(session, path) || session.links[path] !== undefined);
  }
  if (list[0] === "-z") return pass(!(list[1] ?? ""));
  if (list[0] === "-n") return pass(Boolean(list[1]));
  if (list.length === 3 && (list[1] === "=" || list[1] === "!=")) return pass(list[1] === "=" ? list[0] === list[2] : list[0] !== list[2]);
  return fail(session, "test -f fayl | test -d qovluq | test a = b");
}

function teeCmd(session: Session, args: string[], stdin?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-"));
  if (!name) return fail(session, "tee fayl");
  if (stdin === undefined) return fail(session, "boru lazımdır");
  const path = resolve(session.cwd, name);
  ensureDir(session, parent(path));
  const chunk = stdin.endsWith("\n") || !stdin ? stdin : `${stdin}\n`;
  session.files[path] = args.includes("-a") ? `${session.files[path] ?? ""}${chunk}` : chunk;
  return ok(session, stdin);
}

function revCmd(session: Session, args: string[], stdin?: string): ShellOut {
  const got = textFrom(session, args.find((arg) => !arg.startsWith("-")), stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "rev <fayl>");
  return ok(session, linesOf(got.text).map((line) => [...line].reverse().join("")).join("\n"));
}

function baseNameCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "basename", label: "Yol", resume: "basename" });
  const base = name.split("/").filter(Boolean).pop() ?? name;
  const suffix = args[1];
  return ok(session, suffix && base.endsWith(suffix) ? base.slice(0, -suffix.length) : base);
}

function dirNameCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "dirname", label: "Yol", resume: "dirname" });
  const parts = name.split("/").filter(Boolean);
  if (parts.length < 2) return ok(session, name.startsWith("/") ? "/" : ".");
  return ok(session, `${name.startsWith("/") ? "/" : ""}${parts.slice(0, -1).join("/")}`);
}

function namedText(session: Session, args: string[], answer: string | undefined, stdin: string | undefined, title: string, resume: string) {
  const name = args.find((arg) => !arg.startsWith("-")) || answer;
  if (!name) {
    if (stdin !== undefined) return { text: stdin, label: "-" };
    return { ask: askFor(session, { id: "text", title, label: "Fayl adı", resume }) };
  }
  const got = textFrom(session, name, undefined);
  if (got.error) return { error: got.error };
  return { text: got.text ?? "", label: name };
}

function fileCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "Növ", label: "Fayl adı", resume: "file" });
  const path = resolve(session.cwd, name);
  if (session.files[path] === undefined && hasDir(session, path)) return ok(session, `${name}: qovluq`);
  const body = session.files[path];
  if (body === undefined) return fail(session, `fayl yoxdur: ${path}`);
  if (!body) return ok(session, `${name}: boş`);
  if (body.startsWith("#!")) return ok(session, `${name}: skript`);
  const trimmed = body.trim();
  if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
    try {
      JSON.parse(trimmed);
      return ok(session, `${name}: JSON`);
    } catch {
      /* mətn */
    }
  }
  if (name.endsWith(".md")) return ok(session, `${name}: Markdown`);
  const bytes = new TextEncoder().encode(body);
  const odd = [...bytes].filter((byte) => byte < 9 || (byte > 13 && byte < 32)).length;
  return ok(session, `${name}: ${odd > bytes.length / 10 ? "ikili" : "mətn"}`);
}

function xxdCmd(session: Session, args: string[], answer?: string, stdin?: string): ShellOut {
  const got = namedText(session, args, answer, stdin, "Hex", "xxd");
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const bytes = [...new TextEncoder().encode(got.text ?? "")];
  const lines: string[] = [];
  const cap = Math.min(bytes.length, 4096);
  for (let offset = 0; offset < cap; offset += 16) {
    const chunk = bytes.slice(offset, offset + 16);
    const hex = chunk.map((byte) => byte.toString(16).padStart(2, "0")).join("");
    const grouped = hex.replace(/(.{4})/g, "$1 ").trimEnd();
    const ascii = chunk.map((byte) => (byte >= 32 && byte < 127 ? String.fromCharCode(byte) : ".")).join("");
    lines.push(`${offset.toString(16).padStart(8, "0")}: ${grouped.padEnd(39, " ")}  ${ascii}`);
  }
  if (bytes.length > cap) lines.push("… kəsildi");
  return ok(session, lines.join("\n"));
}

function sumCmd(session: Session, cmd: "sha256sum" | "sha1sum" | "md5sum", args: string[], answer?: string, stdin?: string): ShellOut {
  const got = namedText(session, args, answer, stdin, "Yoxlama", cmd);
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const body = got.text ?? "";
  const hash = cmd === "sha256sum" ? sha256(body) : cmd === "sha1sum" ? sha1(body) : md5(body);
  return ok(session, `${hash}  ${got.label}`);
}

function foldCmd(session: Session, args: string[], answer?: string, stdin?: string): ShellOut {
  let width = 80;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-w") {
      width = Number(args[index + 1]);
      index += 1;
    } else if (arg.startsWith("-w")) width = Number(arg.slice(2));
    else rest.push(arg);
  }
  if (!Number.isFinite(width) || width < 1) return fail(session, "fold -w 40");
  const got = namedText(session, rest, answer, stdin, "Bük", "fold");
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const lines = linesOf(got.text ?? "").flatMap((line) => {
    if ([...line].length <= width) return [line];
    const chars = [...line];
    const wrapped: string[] = [];
    for (let index = 0; index < chars.length; index += width) wrapped.push(chars.slice(index, index + width).join(""));
    return wrapped;
  });
  return ok(session, lines.join("\n"));
}

function pasteCmd(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  if (names.length < 2 && answer) names.push(...answer.trim().split(/\s+/));
  if (names.length < 2) return askFor(session, { id: "text", title: "Birləşdir", label: "fayl1 fayl2", resume: "paste" });
  const columns = names.map((name) => {
    const body = session.files[resolve(session.cwd, name)];
    return body === undefined ? null : linesOf(body);
  });
  if (columns.some((column) => !column)) return fail(session, `fayl yoxdur: ${names[columns.findIndex((column) => !column)]}`);
  const height = Math.max(...columns.map((column) => column?.length ?? 0));
  const lines: string[] = [];
  for (let row = 0; row < height && lines.length < 400; row += 1) lines.push(columns.map((column) => column?.[row] ?? "").join("\t"));
  if (height > 400) lines.push("… kəsildi");
  return ok(session, lines.join("\n"));
}

function realPathCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args[0] || answer;
  if (!name) return askFor(session, { id: "text", title: "Yol", label: "Yol", resume: "realpath" });
  const path = resolve(session.cwd, name);
  if (session.files[path] === undefined && !hasDir(session, path)) return fail(session, `yoxdur: ${path}`);
  return ok(session, path);
}

function uptime(session: Session) {
  const minutes = Math.max(0, Math.floor((Date.now() - session.started) / 60000));
  const hours = Math.floor(minutes / 60);
  return `iş vaxtı: ${Math.floor(hours / 24)} gün ${hours % 24} saat ${minutes % 60} dəq`;
}

function rmdirCmd(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  if (!names.length && answer) names.push(answer);
  if (!names.length) return askFor(session, { id: "text", title: "Boş qovluq", label: "Ad", resume: "rmdir" });
  for (const name of names) {
    const path = resolve(session.cwd, name);
    if (path === "/" || path === "/root") return fail(session, "bu qovluq silinmir");
    if (session.files[path] !== undefined || !hasDir(session, path)) return fail(session, `qovluq yoxdur: ${path}`);
    const filled =
      Object.keys(session.files).some((file) => file.startsWith(`${path}/`)) ||
      session.dirs.some((dir) => dir !== path && dir.startsWith(`${path}/`));
    if (filled) return fail(session, `qovluq boş deyil: ${path}`);
    session.dirs = session.dirs.filter((dir) => dir !== path);
  }
  return ok(session, "");
}

function unsetCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || answer;
  if (!name) return askFor(session, { id: "text", title: "Sil", label: "Ad", resume: "unset" });
  delete session.envVars[name];
  return ok(session, "");
}

function printEnv(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || answer;
  if (!name) return ok(session, Object.entries(session.envVars).filter(([key]) => key !== "?").map(([key, value]) => `${key}=${value}`).join("\n"));
  if (!(name in session.envVars)) return fail(session, `yoxdur: ${name}`);
  return ok(session, session.envVars[name] ?? "");
}

function stringsCmd(session: Session, args: string[], answer?: string, stdin?: string): ShellOut {
  let min = 4;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-n") {
      min = Number(args[index + 1]);
      index += 1;
    } else if (arg.startsWith("-n")) min = Number(arg.slice(2));
    else rest.push(arg);
  }
  if (!Number.isFinite(min) || min < 1) return fail(session, "strings -n 4");
  const got = namedText(session, rest, answer, stdin, "Mətn", "strings");
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const runs = (got.text ?? "").match(/[\t\n\r\x20-\x7e]{1,}/g) ?? [];
  return ok(session, runs.map((run) => run.replaceAll("\n", " ")).filter((run) => [...run].length >= min).slice(0, 400).join("\n"));
}

function shufCmd(session: Session, args: string[], answer?: string, stdin?: string): ShellOut {
  let take = 0;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-n") {
      take = Number(args[index + 1]);
      index += 1;
    } else rest.push(arg);
  }
  const got = namedText(session, rest, answer, stdin, "Qarışdır", "shuf");
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const rows = linesOf(got.text ?? "");
  for (let index = rows.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(Math.random() * (index + 1));
    const hold = rows[index] ?? "";
    rows[index] = rows[swap] ?? "";
    rows[swap] = hold;
  }
  return ok(session, (take > 0 ? rows.slice(0, take) : rows).join("\n"));
}

function commCmd(session: Session, args: string[], answer?: string): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  if (names.length < 2 && answer) names.push(...answer.trim().split(/\s+/));
  if (names.length < 2) return askFor(session, { id: "text", title: "Fərq", label: "fayl1 fayl2", resume: "comm" });
  const columns = names.slice(0, 2).map((name) => {
    const body = session.files[resolve(session.cwd, name)];
    return body === undefined ? null : linesOf(body);
  });
  if (columns.some((column) => !column)) return fail(session, `fayl yoxdur: ${names[columns.findIndex((column) => !column)]}`);
  const left = columns[0] ?? [];
  const right = columns[1] ?? [];
  const lines: string[] = [];
  let i = 0;
  let j = 0;
  while ((i < left.length || j < right.length) && lines.length < 400) {
    const a = left[i];
    const b = right[j];
    if (b === undefined || (a !== undefined && a < b)) {
      lines.push(a ?? "");
      i += 1;
    } else if (a === undefined || b < a) {
      lines.push(`\t${b}`);
      j += 1;
    } else {
      lines.push(`\t\t${a}`);
      i += 1;
      j += 1;
    }
  }
  if (i < left.length || j < right.length) lines.push("… kəsildi");
  return ok(session, lines.join("\n"));
}

function expandCmd(session: Session, args: string[], answer?: string, stdin?: string): ShellOut {
  const got = namedText(session, args.filter((arg) => !arg.startsWith("-")), answer, stdin, "Genişlət", "expand");
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const lines = linesOf(got.text ?? "").map((line) => {
    let column = 0;
    let out = "";
    for (const char of line) {
      if (char !== "\t") {
        out += char;
        column += 1;
      } else {
        const spaces = 8 - (column % 8);
        out += " ".repeat(spaces);
        column += spaces;
      }
    }
    return out;
  });
  return ok(session, lines.join("\n"));
}

function fmtCmd(session: Session, args: string[], answer?: string, stdin?: string): ShellOut {
  let width = 72;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-w") {
      width = Number(args[index + 1]);
      index += 1;
    } else if (arg.startsWith("-w")) width = Number(arg.slice(2));
    else rest.push(arg);
  }
  if (!Number.isFinite(width) || width < 1) return fail(session, "fmt -w 40");
  const got = namedText(session, rest, answer, stdin, "Düz", "fmt");
  if (got.ask) return got.ask;
  if (got.error) return fail(session, got.error);
  const words = (got.text ?? "").trim().split(/\s+/).filter(Boolean);
  const lines: string[] = [];
  let line = "";
  for (const word of words) {
    if (!line) line = word;
    else if (line.length + 1 + word.length <= width) line = `${line} ${word}`;
    else {
      lines.push(line);
      line = word;
    }
    if (lines.length >= 400) break;
  }
  if (line && lines.length < 400) lines.push(line);
  return ok(session, lines.join("\n"));
}

function mktempCmd(session: Session, args: string[]): ShellOut {
  const pattern = args.find((arg) => !arg.startsWith("-")) || "tmp.XXXXXX";
  const stamp = Math.random().toString(36).slice(2, 8).padEnd(6, "0");
  const name = pattern.includes("X") ? pattern.replace(/X+$/, stamp) : `${pattern}.${stamp}`;
  const path = resolve(session.cwd, name.startsWith("/") ? name : `/tmp/${name}`);
  ensureDir(session, parent(path));
  session.files[path] = "";
  return ok(session, path);
}

function factorCmd(session: Session, args: string[], answer?: string): ShellOut {
  const raw = args.filter((arg) => !arg.startsWith("-"));
  if (!raw.length && answer) raw.push(...answer.trim().split(/\s+/));
  if (!raw.length) return askFor(session, { id: "text", title: "Faktor", label: "12 84", resume: "factor" });
  const lines: string[] = [];
  for (const item of raw) {
    const value = Number(item);
    if (!Number.isInteger(value) || value < 1 || value > 1_000_000_000_000) return fail(session, "factor 12");
    lines.push(`${value}: ${primeFactors(value).join(" ")}`.trimEnd());
  }
  return ok(session, lines.join("\n"));
}

function primeFactors(value: number) {
  const found: number[] = [];
  let rest = value;
  while (rest % 2 === 0 && found.length < 64) {
    found.push(2);
    rest /= 2;
  }
  for (let prime = 3; prime * prime <= rest && found.length < 64; prime += 2) {
    while (rest % prime === 0) {
      found.push(prime);
      rest /= prime;
    }
  }
  if (rest > 1) found.push(rest);
  return found;
}

function xargsCmd(session: Session, args: string[], stdin?: string): ShellOut {
  if (stdin === undefined) return fail(session, "boru lazımdır");
  const words = stdin.trim() ? stdin.trim().split(/\s+/).slice(0, 200) : [];
  const cmd = args[0] || "echo";
  if (cmd === "xargs" || cmd === "sudo") return fail(session, "xargs echo");
  return dispatch(session, cmd, [...args.slice(1), ...words], undefined, undefined);
}

function readCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || "REPLY";
  if (!/^[A-Za-z_][\w]*$/.test(name)) return fail(session, "ad səhvdir");
  if (answer === undefined) return askFor(session, { id: "text", title: "Dəyər", label: name, resume: `read ${name}` });
  session.envVars[name] = answer;
  return ok(session, "");
}

function cpuCount() {
  const nav = (globalThis as { navigator?: { hardwareConcurrency?: number } }).navigator;
  const count = nav?.hardwareConcurrency;
  return count && count > 0 ? count : 1;
}

function typeCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || answer;
  if (!name) return askFor(session, { id: "text", title: "Əmr", label: "Ad", resume: "type" });
  if (session.aliases[name]) return ok(session, `${name}=${session.aliases[name]}`);
  if (COMMANDS.includes(name) || name === "python" || name === "g++" || name === "cc" || name === "egrep" || name === "fgrep" || name === "sudo") {
    return ok(session, `${name} əmridir`);
  }
  return fail(session, `tapılmadı: ${name}`);
}

function commandCmd(session: Session, args: string[], answer?: string): ShellOut {
  const name = args.find((arg) => !arg.startsWith("-")) || answer;
  if (!name) return askFor(session, { id: "text", title: "Əmr", label: "Ad", resume: "command -v" });
  return whichCmd(session, [name]);
}

const C_MARK = "#!nibras-c\n";

function gcc(session: Session, args: string[], answer?: string): ShellOut {
  if (!session.pkgs.includes("build")) return fail(session, "C seçilməyib.\nnibras install build");
  if (!session.env) return fail(session, "Əvvəl mühit seç");
  const file = args.find((arg) => arg.endsWith(".c") || arg.endsWith(".cpp")) || (answer?.endsWith(".c") || answer?.endsWith(".cpp") ? answer : undefined);
  if (!file) return askFor(session, { id: "text", title: "C faylı", label: "məsələn main.c", resume: "gcc" });
  const path = resolve(session.cwd, file);
  const code = session.files[path];
  if (code === undefined) return fail(session, `fayl yoxdur: ${path}`);
  const outFlag = args.indexOf("-o");
  const outName = outFlag >= 0 ? args[outFlag + 1] || "a.out" : "a.out";
  const outPath = resolve(session.cwd, outName);
  ensureDir(session, parent(outPath));
  session.files[outPath] = `${C_MARK}${code.endsWith("\n") ? code : `${code}\n`}`;
  const result = runC(code);
  if (result.error) return fail(session, result.error);
  return ok(session, result.output);
}

function runBinary(session: Session, name: string): ShellOut {
  const path = resolve(session.cwd, name);
  const body = session.files[path];
  if (body === undefined) return fail(session, `yoxdur: ${path}`);
  if (!body.startsWith(C_MARK)) return fail(session, "bu fayl icra olunan C proqramı deyil. Əvvəl gcc yaz");
  const result = runC(body.slice(C_MARK.length));
  if (result.error) return fail(session, result.error);
  return ok(session, result.output);
}

function fetchText(session: Session, args: string[], answer?: string): ShellOut {
  const rest = [...args];
  let dest = "";
  const flag = rest.indexOf("-o");
  if (flag >= 0) {
    dest = rest[flag + 1] ?? "";
    rest.splice(flag, 2);
  }
  const url = rest.find((arg) => arg.startsWith("https://")) || (answer?.startsWith("https://") ? answer : "");
  if (!url) return askFor(session, { id: "text", title: "Ünvan", label: "https://…", resume: dest ? `curl -o ${dest}` : "curl" });
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return fail(session, "ünvan səhvdir");
  }
  if (parsed.protocol !== "https:" || !FETCH_HOSTS.has(parsed.hostname)) {
    return fail(session, "yalnız GitHub və jsDelivr https ünvanı");
  }
  const name = dest || decodeURIComponent(parsed.pathname.split("/").pop() || "yukleme.txt");
  const path = resolve(session.cwd, name);
  ensureDir(session, parent(path));
  return { session, output: "", ok: true, fetch: { url, dest: path } };
}

function script(session: Session, name?: string): ShellOut {
  if (!name) return askFor(session, { id: "text", title: "Skript", label: "fayl adı", resume: "sh" });
  const path = resolve(session.cwd, name);
  const body = session.files[path];
  if (body === undefined) return fail(session, `fayl yoxdur: ${path}`);
  let current = session;
  const lines: string[] = [];
  let ran = 0;
  for (const raw of body.split("\n")) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    ran += 1;
    if (ran > 200) {
      lines.push("skript kəsildi");
      break;
    }
    const result = runLine(current, line);
    current = result.session;
    if (pause(result)) {
      const output = [lines.join("\n"), result.output].filter(Boolean).join("\n");
      return { ...result, session: current, output };
    }
    if (result.output && result.output !== "\u0000clear") lines.push(result.output);
    if (result.ok === false) return fail(current, [lines.join("\n"), result.output].filter(Boolean).join("\n"));
  }
  return ok(current, lines.join("\n"));
}

const FETCH_HOSTS = new Set(["github.com", "raw.githubusercontent.com", "gist.githubusercontent.com", "cdn.jsdelivr.net", "objects.githubusercontent.com"]);

const TOPICS: Record<string, string> = {
  curl: "curl https://…\ncurl -o ad.txt https://…\nYalnız GitHub və jsDelivr. Mətn, 256 KB-dək.",
  wget: "wget https://…\ncurl ilə eyni qayda.",
  gcc: "Əvvəl mühit, sonra nibras install build.\ngcc main.c\nSadə C: printf, int, if, while, for.\nSonra ./a.out",
  git: "git init\ngit clone sahib/ad\ngit pull\ngit branch\ngit switch ad\ngit rebase main\ngit push\nToken xanada qalır. Klon və çəkmə GitHub-dan real gəlir, ən çoxu 40 mətn faylı.",
  ping: "ping github.com\nICMP yox. Ad Cloudflare DNS ilə tapılır, sonra https vaxtı ölçülür.\nŞəxsi ünvanlar bağlıdır.",
  ssh: "ssh istifadəçi@ünvan əmr\nŞifrə xanaya yazılır, ekrana düşmür.\nŞifrəni sil: nibras ssh sil\nŞəxsi ünvanlar bağlıdır.",
  sftp: "sftp get istifadəçi@ünvan:fayl yerli.txt\nsftp put yerli.txt istifadəçi@ünvan:fayl\nEyni SSH şifrəsi. Mətn, 100 KB-dək.",
  apt: "apt update\napt search python\napt install python\nYalnız bu terminalın işlətdiyi paketlər. Alpine-də apk.",
  python3: "nibras install python\npython3 main.py\npython3 — söhbət. Çıxış: exit",
  node: "nibras install node\nnode fayl.js\nnode -e \"console.log(1)\"",
  php: "nibras install php\nphp fayl.php\nphp -r \"echo 1;\"",
  nibras: "nibras install | sil | disk | token | layihe | serve | md | stop | zibil | qaytar | gitignore",
  alias: "alias salam=echo Salam\nsalam\nunalias salam\nSkript düyməsi bir neçə sətri ardıcıl işlədir.",
  sort: "sort fayl\nsort -n -r fayl\necho ilə boru da olur.",
  diff: "diff köhnə.txt yeni.txt\n- silinən, + əlavə.",
  expr: "expr '2 + 3 * 4'\nYalnız rəqəm və + - * / %.",
  jq: "jq . fayl.json\njq .ad fayl.json",
  base64: "base64 fayl\nbase64 -d fayl",
  sed: "sed 's/köhnə/yeni/g' fayl\nsed -i faylın özünü dəyişir.",
  sha256sum: "sha256sum fayl\necho -n salam | sha256sum",
  md5sum: "md5sum fayl\nBorudan da işləyir.",
  xxd: "xxd fayl\nHər sətirdə 16 bayt. Uzun çıxış kəsilir.",
  file: "file ad\nQovluq, boş, JSON, Markdown, skript, mətn və ya ikili.",
  fold: "fold -w 40 fayl\nUzun sətri bölür.",
  paste: "paste bir.txt iki.txt\nSətirləri tab ilə yanaşı qoyur.",
  realpath: "realpath yol\n. və .. təmizlənir.",
  uptime: "uptime\nSessiya nə vaxtdan açıqdır.",
  hostname: "hostname\nnibras",
  arch: "arch\naarch64",
  rmdir: "rmdir qovluq\nYalnız boş qovluğu silir.",
  shuf: "shuf fayl\nSətirləri qarışdırır. shuf -n 1 fayl",
  factor: "factor 12 84\nSadə vuruqlar.",
  xargs: "echo a b | xargs echo\nBorudakı sözləri əmrə əlavə edir.",
  read: "read AD\nDəyər xanaya yazılır, echo $AD ilə görünür.",
  strings: "strings fayl\nƏn azı 4 hərflik oxunan hissələr.",
  egrep: "egrep '[0-9]' fayl\nNümunə axtarışı. fgrep düz mətndir.",
  awk: "awk '{print $1}' fayl\nawk -F: '{print $1}'\n$1 birinci sütun, $0 bütün sətir.",
  tar: "tar -cf arxiv.tar fayl\ntar -tf arxiv.tar\ntar -xf arxiv.tar\nMətn arxivi. 200 KB-dək.",
  for: "for ad in a b; do echo $ad; done\nƏn çoxu 40 döngü.",
  column: "column -t fayl\nSütunları hizalayır.",
};

function helpTopic(session: Session, name?: string): ShellOut {
  if (!name) return ok(session, HELP);
  return ok(session, TOPICS[name] ?? `kömək yoxdur: ${name}\nhelp`);
}

const COMMANDS = ["help", "man", "clear", "pwd", "ls", "cd", "cat", "mkdir", "rmdir", "touch", "rm", "cp", "mv", "echo", "grep", "egrep", "fgrep", "rg", "head", "tail", "wc", "sort", "uniq", "cut", "diff", "base64", "expr", "sed", "tr", "seq", "stat", "du", "tree", "which", "nl", "tac", "jq", "cal", "find", "edit", "history", "export", "unset", "printenv", "alias", "unalias", "curl", "wget", "ping", "ssh", "sftp", "gcc", "python3", "node", "php", "git", "nibras", "sh", "uname", "df", "true", "false", "test", "tee", "rev", "basename", "dirname", "file", "xxd", "sha256sum", "sha1sum", "md5sum", "fold", "paste", "realpath", "hostname", "arch", "uptime", "strings", "shuf", "comm", "expand", "fmt", "mktemp", "factor", "xargs", "read", "nproc", "tty", "logname", "who", "type", "command", "sudo", "awk", "column", "cmp", "join", "split", "truncate", "dos2unix", "unix2dos", "less", "more", "tar", "chmod", "time", "groups", "apt", "apk", "apt-cache", "dpkg", "lsb_release", "ip", "ss", "jobs", "sleep", "ln", "readlink", "adduser", "passwd", "su", "source", "nano", "vi", "pkg", "nibras-distro", "top", "htop", "ssh-keygen", "nibras-setup-storage", "nibras-clipboard-get", "nibras-clipboard-set", "nibras-toast", "nibras-vibrate", "nibras-battery-status", "nibras-tts-speak", "nibras-open-url", "nibras-notification", "nibras-dialog", "nibras-reload-settings", "pushd", "popd", "dirs", "watch", "fc", "open", "reset", "logcat", "adb", "nibras-wake-lock", "nibras-wake-unlock", "nibras-share", "nibras-wifi-connectioninfo"];

export function complete(session: Session, line: string): string[] {
  if (!line || /\s$/.test(line)) return [];
  const cut = Math.max(line.lastIndexOf(" "), line.lastIndexOf("\t"));
  const prefix = cut >= 0 ? line.slice(0, cut + 1) : "";
  const token = cut >= 0 ? line.slice(cut + 1) : line;
  if (!prefix.trim()) {
    const names = [...COMMANDS, ...Object.keys(session.aliases)];
    return names.filter((cmd) => cmd.startsWith(token) && cmd !== token).slice(0, 6).map((cmd) => prefix + cmd);
  }
  const slash = token.lastIndexOf("/");
  const dirToken = slash >= 0 ? token.slice(0, slash + 1) : "";
  const base = slash >= 0 ? token.slice(slash + 1) : token;
  const dir = resolve(session.cwd, dirToken || ".");
  const names = new Set<string>();
  for (const item of session.dirs) if (parent(item) === dir) names.add(`${item.split("/").pop()}/`);
  for (const file of Object.keys(session.files)) if (parent(file) === dir) names.add(file.split("/").pop() ?? file);
  return [...names]
    .filter((name) => name.startsWith(base) && name !== base)
    .sort()
    .slice(0, 6)
    .map((name) => `${prefix}${dirToken}${name}`);
}

function diskLine(session: Session) {
  const used = usedBytes(session);
  return `${formatBytes(used)} / ${formatBytes(DISK_BUDGET)} · boş ${formatBytes(Math.max(0, DISK_BUDGET - used))}`;
}

function cleanRepo(value: string) {
  return value.trim().replace(/^https:\/\/github\.com\//, "").replace(/\.git$/, "");
}

function shellQuote(value: string) {
  return /^[\w./-]+$/.test(value) ? value : `'${value.replaceAll("'", "'\\''")}'`;
}

export function resolve(cwd: string, input: string, home = "/root") {
  let raw = input;
  if (raw === "~" || raw.startsWith("~/")) raw = `${home}${raw.slice(1)}`;
  raw = raw.startsWith("/") ? raw : `${cwd}/${raw}`;
  const parts: string[] = [];
  for (const part of raw.split("/")) {
    if (!part || part === ".") continue;
    if (part === "..") parts.pop();
    else parts.push(part);
  }
  return `/${parts.join("/")}` || "/";
}

function parent(path: string) {
  if (path === "/") return "/";
  const cut = path.lastIndexOf("/");
  return cut <= 0 ? "/" : path.slice(0, cut);
}

function ensureDir(session: Session, dir: string) {
  if (dir !== "/" && !session.dirs.includes(dir)) session.dirs.push(dir);
  if (dir !== "/") ensureDir(session, parent(dir));
}

function hasDir(session: Session, dir: string) {
  if (dir === "/") return true;
  if (session.files[dir] !== undefined) return false;
  return session.dirs.includes(dir) || Object.keys(session.files).some((file) => file.startsWith(`${dir}/`));
}

function promptOf(session: Session) {
  const host = session.env ?? "nibras";
  const home = session.home || "/root";
  const user = session.user || "root";
  let short = "~";
  if (session.cwd !== home) short = session.cwd.startsWith(`${home}/`) ? `~/${session.cwd.slice(home.length + 1)}` : session.cwd;
  return `${user}@${host}:${short}${user === "root" ? "#" : "$"}`;
}

export function prompt(session: Session) {
  return promptOf(session);
}

export function listHere(session: Session) {
  const dir = session.cwd;
  const folders: string[] = [];
  const files: string[] = [];
  for (const item of session.dirs) {
    if (parent(item) !== dir) continue;
    const base = item.split("/").pop() ?? "";
    if (base && !base.startsWith(".")) folders.push(base);
  }
  for (const file of Object.keys(session.files)) {
    if (parent(file) !== dir) continue;
    const base = file.split("/").pop() ?? file;
    if (!base.startsWith(".")) files.push(base);
  }
  folders.sort();
  files.sort();
  return { folders, files };
}

export function packHere(session: Session) {
  const prefix = session.cwd === "/" ? "/" : `${session.cwd}/`;
  const rows: Array<{ name: string; text: string }> = [];
  for (const [path, text] of Object.entries(session.files)) {
    const inside = session.cwd === "/" ? path.startsWith("/") : path.startsWith(prefix);
    if (!inside) continue;
    const rel = session.cwd === "/" ? path.slice(1) : path.slice(prefix.length);
    if (!rel || rel.split("/").some((part) => part.startsWith("."))) continue;
    if (text.includes("\0") || text.length > 200_000) continue;
    rows.push({ name: rel, text });
  }
  rows.sort((a, b) => a.name.localeCompare(b.name));
  return rows;
}

function crc32(data: Uint8Array) {
  let crc = 0xffffffff;
  for (const byte of data) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function le(value: number, bytes: number) {
  const out: number[] = [];
  for (let i = 0; i < bytes; i += 1) out.push((value >>> (8 * i)) & 255);
  return out;
}

export function storeZip(entries: Array<{ name: string; text: string }>) {
  const encoder = new TextEncoder();
  const locals: number[] = [];
  const central: number[] = [];
  let offset = 0;
  for (const entry of entries) {
    const name = encoder.encode(entry.name);
    const data = encoder.encode(entry.text);
    const sum = crc32(data);
    const local = [
      ...le(0x04034b50, 4),
      ...le(20, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(sum, 4),
      ...le(data.length, 4),
      ...le(data.length, 4),
      ...le(name.length, 2),
      ...le(0, 2),
      ...name,
      ...data,
    ];
    locals.push(...local);
    central.push(
      ...le(0x02014b50, 4),
      ...le(20, 2),
      ...le(20, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(sum, 4),
      ...le(data.length, 4),
      ...le(data.length, 4),
      ...le(name.length, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 2),
      ...le(0, 4),
      ...le(offset, 4),
      ...name,
    );
    offset += local.length;
  }
  const end = [
    ...le(0x06054b50, 4),
    ...le(0, 2),
    ...le(0, 2),
    ...le(entries.length, 2),
    ...le(entries.length, 2),
    ...le(central.length, 4),
    ...le(offset, 4),
    ...le(0, 2),
  ];
  return new Uint8Array([...locals, ...central, ...end]);
}

function keepTrash(session: Session, path: string) {
  const body = session.files[path];
  if (body === undefined) return;
  if (body.length <= 200_000) {
    session.trash.unshift({ path, body });
    if (session.trash.length > 20) session.trash.length = 20;
  }
  delete session.files[path];
  delete session.modes[path];
}

function splitSequence(line: string) {
  const parts: Array<{ text: string; next: "and" | "or" | "seq" }> = [];
  let current = "";
  let quote: "'" | '"' | "" = "";
  let depth = 0;
  let noted = "";
  const push = (next: "and" | "or" | "seq") => {
    parts.push({ text: current.trim(), next });
    current = "";
    noted = "";
  };
  const finishWord = () => {
    const trimmed = current.trimEnd();
    if (!trimmed || trimmed === noted) return;
    noted = trimmed;
    const match = /(\S+)$/.exec(trimmed);
    const word = match?.[1] ?? "";
    if (!word) return;
    const before = trimmed.slice(0, trimmed.length - word.length).trim();
    const opens = !before || /(?:;|&&|\|\||\b(?:do|then|else))$/.test(before);
    if (word === "{" || word.endsWith("{")) depth += 1;
    if ((word === "}" || word.endsWith("}")) && depth) depth -= 1;
    if (!opens) return;
    if (word === "for" || word === "if" || word === "while" || word === "case") depth += 1;
    if ((word === "done" || word === "fi" || word === "esac") && depth) depth -= 1;
  };
  for (let index = 0; index < line.length; index += 1) {
    const ch = line[index] ?? "";
    if (quote) {
      current += ch;
      if (ch === quote) quote = "";
      continue;
    }
    if (ch === "'" || ch === '"') {
      quote = ch;
      current += ch;
      continue;
    }
    if ((ch === " " || ch === "\t") && current && !/\s$/.test(current)) finishWord();
    if (depth === 0 && ch === "&" && line[index + 1] === "&") {
      finishWord();
      push("and");
      index += 1;
      continue;
    }
    if (depth === 0 && ch === "|" && line[index + 1] === "|") {
      finishWord();
      push("or");
      index += 1;
      continue;
    }
    if (depth === 0 && ch === ";") {
      finishWord();
      push("seq");
      continue;
    }
    current += ch;
  }
  finishWord();
  if (current.trim()) parts.push({ text: current.trim(), next: "seq" });
  return parts;
}

function splitPipeline(line: string, session: Session) {
  let quote: "'" | '"' | "" = "";
  let cut = -1;
  let append = false;
  for (let index = 0; index < line.length; index += 1) {
    const ch = line[index] ?? "";
    if (quote) {
      if (ch === quote) quote = "";
      continue;
    }
    if (ch === "'" || ch === '"') {
      quote = ch;
      continue;
    }
    if (ch === ">") {
      cut = index;
      append = line[index + 1] === ">";
      break;
    }
  }
  const body = cut >= 0 ? line.slice(0, cut) : line;
  const redirectPath = cut >= 0 ? splitArgs(line.slice(cut + (append ? 2 : 1)), session)[0] : undefined;
  const redirect = redirectPath ? { path: redirectPath, append } : undefined;
  const commands: string[] = [];
  let current = "";
  quote = "";
  for (let index = 0; index < body.length; index += 1) {
    const ch = body[index] ?? "";
    if (quote) {
      current += ch;
      if (ch === quote) quote = "";
      continue;
    }
    if (ch === "'" || ch === '"') {
      quote = ch;
      current += ch;
      continue;
    }
    if (ch === "|") {
      if (current.trim()) commands.push(current.trim());
      current = "";
      continue;
    }
    current += ch;
  }
  if (current.trim()) commands.push(current.trim());
  return { commands: commands.length ? commands : [""], redirect };
}

function splitArgs(line: string, session: Session) {
  const out: string[] = [];
  let current = "";
  let quote: "'" | '"' | "" = "";
  let quoted = false;
  const push = () => {
    if (!current) return;
    if (quoted || !/[*?]/.test(current)) out.push(current);
    else {
      const expanded = expandGlob(session, current);
      out.push(...(expanded.length ? expanded : [current]));
    }
    current = "";
    quoted = false;
  };
  for (let index = 0; index < line.length; index += 1) {
    const ch = line[index] ?? "";
    if (quote) {
      if (ch === quote) {
        quote = "";
        quoted = true;
      } else if (ch === "$" && quote === '"') {
        const read = readVar(line, index, session);
        current += read.value;
        index += read.size - 1;
      } else current += ch;
      continue;
    }
    if (ch === "'" || ch === '"') {
      quote = ch;
      continue;
    }
    if (ch === " " || ch === "\t") {
      push();
      continue;
    }
    if (ch === "$") {
      const read = readVar(line, index, session);
      current += read.value;
      index += read.size - 1;
      continue;
    }
    current += ch;
  }
  push();
  return out;
}

function expandGlob(session: Session, token: string) {
  const slash = token.lastIndexOf("/");
  const dirToken = slash >= 0 ? token.slice(0, slash) || "/" : ".";
  const base = slash >= 0 ? token.slice(slash + 1) : token;
  if (!base || !/[*?]/.test(base)) return [token];
  const dir = resolve(session.cwd, dirToken);
  const showDot = base.startsWith(".");
  const source = base.replace(/[.+^${}()|[\]\\]/g, "\\$&").replaceAll("*", ".*").replaceAll("?", ".");
  const re = new RegExp(`^${source}$`);
  const names = new Set<string>();
  for (const item of session.dirs) {
    if (parent(item) !== dir) continue;
    const name = item.split("/").pop() ?? "";
    if (!name || (!showDot && name.startsWith("."))) continue;
    if (re.test(name)) names.add(slash >= 0 ? `${token.slice(0, slash)}/${name}` : name);
  }
  for (const file of Object.keys(session.files)) {
    if (parent(file) !== dir) continue;
    const name = file.split("/").pop() ?? "";
    if (!showDot && name.startsWith(".")) continue;
    if (re.test(name)) names.add(slash >= 0 ? `${token.slice(0, slash)}/${name}` : name);
  }
  return [...names].sort();
}

function readVar(line: string, index: number, session: Session) {
  if (line.startsWith("$((", index)) {
    const end = line.indexOf("))", index + 3);
    if (end < 0) return { value: "$", size: 1 };
    return { value: arith(line.slice(index + 3, end), session) ?? "", size: end + 2 - index };
  }
  if (line[index + 1] === "?") return { value: session.envVars["?"] ?? "0", size: 2 };
  if (line[index + 1] === "{") {
    const end = line.indexOf("}", index + 2);
    if (end < 0) return { value: "$", size: 1 };
    const name = line.slice(index + 2, end);
    return { value: session.envVars[name] ?? "", size: end - index + 1 };
  }
  let end = index + 1;
  while (end < line.length && /[\w]/.test(line[end] ?? "")) end += 1;
  const name = line.slice(index + 1, end);
  if (!name) return { value: "$", size: 1 };
  return { value: session.envVars[name] ?? "", size: end - index };
}

type NodeOpts = { repl?: boolean; scope?: Record<string, unknown>; file?: string; argv?: string[] };

export function runNode(code: string, session: Session, opts: NodeOpts = {}): { output: string; session: Session } {
  const logs: string[] = [];
  const cwdRef = { cwd: session.cwd };
  const fs = makeFs(session, cwdRef);
  const fakeConsole = { log: (...parts: unknown[]) => logs.push(parts.map(String).join(" ")) };
  const http = {
    createServer(handler: (req: unknown, res: { writeHead: (code?: number, headers?: Record<string, string>) => void; end: (body: string) => void }) => void) {
      return {
        listen(port = 3000, host?: string, cb?: () => void) {
          const done = typeof host === "function" ? host : cb;
          const res = {
            writeHead(_code?: number, headers?: Record<string, string>) {
              res.headers = headers ?? {};
            },
            headers: {} as Record<string, string>,
            end(body: string) {
              const type = res.headers["Content-Type"] ?? "";
              session.server = { port: typeof port === "number" ? port : 3000, html: type.includes("html") ? body : wrapPlain(body) };
            },
          };
          handler({}, res);
          done?.();
        },
      };
    },
  };
  const pathMod = {
    join: (...parts: string[]) => resolve(parts[0]?.startsWith("/") ? "/" : cwdRef.cwd, parts.join("/")),
    dirname: (value: string) => parent(value),
  };
  const scriptDir = opts.file ? parent(opts.file) : session.cwd;
  const processObj = {
    argv: opts.argv ?? ["node", opts.file ?? ""],
    cwd: () => cwdRef.cwd,
    chdir: (dir: string) => {
      cwdRef.cwd = resolve(cwdRef.cwd, dir);
      session.cwd = cwdRef.cwd;
    },
    env: session.envVars,
    exit: () => undefined,
  };
  const requireFrom = (fromDir: string) => (name: string) => {
    if (name === "http") return http;
    if (name === "fs") return fs;
    if (name === "path") return pathMod;
    if (name.startsWith(".")) {
      const file = resolve(fromDir, name.endsWith(".js") ? name : `${name}.js`);
      const source = session.files[file];
      if (source === undefined) throw new Error(`modul yoxdur: ${name}`);
      const module = { exports: {} as Record<string, unknown> };
      const runner = new Function("require", "console", "module", "exports", "process", "__dirname", "__filename", source) as (...args: unknown[]) => void;
      runner(requireFrom(parent(file)), fakeConsole, module, module.exports, processObj, parent(file), file);
      return module.exports;
    }
    throw new Error(`modul yoxdur: ${name}`);
  };
  const scope = opts.scope ?? {};
  const body = opts.repl
    ? `with (scope) { let __v; try { __v = (${code}); } catch (error) { if (!(error instanceof SyntaxError)) throw error; ${code}; __v = undefined; } if (typeof __v !== "undefined") console.log(typeof __v === "string" ? __v : String(__v)); }`
    : code;
  const runner = new Function("require", "console", "module", "exports", "process", "__dirname", "__filename", "fs", "scope", body) as (
    ...args: unknown[]
  ) => void;
  const file = opts.file ?? session.cwd;
  runner(requireFrom(scriptDir), fakeConsole, { exports: {} }, {}, processObj, scriptDir, file, fs, scope);
  return { output: logs.join("\n"), session };
}

function makeFs(session: Session, cwdRef: { cwd: string }) {
  const pathOf = (value: string) => (value.startsWith("/") ? resolve("/", value) : resolve(cwdRef.cwd, value));
  return {
    readFileSync(value: string) {
      const path = pathOf(value);
      const body = session.files[path];
      if (body === undefined) throw new Error(`ENOENT: ${path}`);
      return body;
    },
    writeFileSync(value: string, data: unknown) {
      const path = pathOf(value);
      ensureDir(session, parent(path));
      session.files[path] = String(data);
    },
    appendFileSync(value: string, data: unknown) {
      const path = pathOf(value);
      ensureDir(session, parent(path));
      session.files[path] = `${session.files[path] ?? ""}${String(data)}`;
    },
    existsSync(value: string) {
      const path = pathOf(value);
      return session.files[path] !== undefined || hasDir(session, path);
    },
    mkdirSync(value: string) {
      ensureDir(session, pathOf(value));
    },
    readdirSync(value: string) {
      const dir = pathOf(value);
      const names = new Set<string>();
      for (const item of session.dirs) if (parent(item) === dir) names.add(item.split("/").pop() ?? item);
      for (const file of Object.keys(session.files)) if (parent(file) === dir) names.add(file.split("/").pop() ?? file);
      return [...names];
    },
  };
}

function markdown(session: Session, name?: string): ShellOut {
  const path = name
    ? resolve(session.cwd, name)
    : Object.keys(session.files).find((file) => (file === session.cwd || file.startsWith(`${session.cwd}/`)) && file.endsWith(".md"));
  if (!path) return askFor(session, { id: "text", title: "Markdown", label: "Fayl adı", resume: "nibras md" });
  const body = session.files[path];
  if (body === undefined) return fail(session, `fayl yoxdur: ${path}`);
  session.server = { port: 8000, html: renderMarkdown(body) };
  return ok(session, "önizləmə açıldı · dayandırmaq: nibras stop");
}

function renderMarkdown(source: string) {
  const esc = (value: string) => value.replace(/[&<>]/g, (ch) => (ch === "&" ? "&" + "amp;" : ch === "<" ? "&" + "lt;" : "&" + "gt;"));
  const inline = (value: string) =>
    esc(value)
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>")
      .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2">$1</a>');
  const lines = source.replace(/\r\n/g, "\n").split("\n");
  const out: string[] = [];
  let list = false;
  let code = false;
  const codeBuf: string[] = [];
  const closeList = () => {
    if (!list) return;
    out.push("</ul>");
    list = false;
  };
  for (const line of lines) {
    if (line.startsWith("```")) {
      if (code) {
        out.push(`<pre><code>${esc(codeBuf.join("\n"))}</code></pre>`);
        codeBuf.length = 0;
        code = false;
      } else {
        closeList();
        code = true;
      }
      continue;
    }
    if (code) {
      codeBuf.push(line);
      continue;
    }
    if (!line.trim()) {
      closeList();
      continue;
    }
    const heading = /^(#{1,3})\s+(.*)$/.exec(line);
    if (heading) {
      closeList();
      out.push(`<h${heading[1]?.length}>${inline(heading[2] ?? "")}</h${heading[1]?.length}>`);
      continue;
    }
    if (/^[-*]\s+/.test(line)) {
      if (!list) {
        out.push("<ul>");
        list = true;
      }
      out.push(`<li>${inline(line.replace(/^[-*]\s+/, ""))}</li>`);
      continue;
    }
    closeList();
    out.push(`<p>${inline(line)}</p>`);
  }
  closeList();
  if (code) out.push(`<pre><code>${esc(codeBuf.join("\n"))}</code></pre>`);
  return `<!doctype html><html lang="az"><meta charset="utf-8"><style>body{margin:0;background:#07101c;color:#f3ecdf;font-family:Georgia,serif}main{max-width:40rem;margin:0 auto;padding:1.5rem}a{color:#d6b36a}code,pre{font-family:ui-monospace,monospace}pre{background:#10192b;padding:.75rem;overflow:auto}h1,h2,h3{font-weight:500}</style><body><main>${out.join("\n")}</main></body></html>`;
}

function wrapPlain(text: string) {
  const safe = text.replace(/[&<>]/g, (ch) => (ch === "&" ? "&" + "amp;" : ch === "<" ? "&" + "lt;" : "&" + "gt;"));
  return `<!doctype html><html><body style="margin:0;min-height:100vh;display:grid;place-items:center;background:#07101c;color:#f3ecdf;font-family:Georgia,serif"><p>${safe}</p></body></html>`;
}

function arith(raw: string, session: Session) {
  const expanded = raw.replace(/\$\{([A-Za-z_]\w*)\}|\$([A-Za-z_]\w*)/g, (_full, braced: string, plain: string) => {
    const value = session.envVars[braced || plain] ?? "0";
    return /^-?\d+$/.test(value) ? value : "0";
  });
  if (!/^[\d\s+*/%().-]+$/.test(expanded)) return null;
  try {
    const value = Function(`"use strict"; return (${expanded})`)() as unknown;
    if (typeof value !== "number" || !Number.isFinite(value)) return null;
    return String(value);
  } catch {
    return null;
  }
}

function control(session: Session, line: string, answer: string | undefined, depth: number): ShellOut | undefined {
  const trimmed = line.trim();
  if (/^for(?:\s|$)/.test(trimmed)) {
    const loop = /^for\s+([A-Za-z_]\w*)\s+in\s+([\s\S]+?)\s*;\s*do\s+([\s\S]+)\s*;\s*done$/.exec(trimmed);
    if (!loop) return fail(session, "for ad in a b; do echo $ad; done");
    return runFor(session, loop[1] ?? "", loop[2] ?? "", loop[3] ?? "", answer, depth);
  }
  if (/^if(?:\s|$)/.test(trimmed)) {
    const cond = /^if\s+([\s\S]+?)\s*;\s*then\s+([\s\S]+?)(?:\s*;\s*else\s+([\s\S]+?))?\s*;\s*fi$/.exec(trimmed);
    if (!cond) return fail(session, "if test -f fayl; then echo var; fi");
    return runIf(session, cond[1] ?? "", cond[2] ?? "", cond[3], answer, depth);
  }
  if (/^while(?:\s|$)/.test(trimmed)) {
    const loop = /^while\s+([\s\S]+?)\s*;\s*do\s+([\s\S]+)\s*;\s*done$/.exec(trimmed);
    if (!loop) return fail(session, "while test -f fayl; do echo var; done");
    return runWhile(session, loop[1] ?? "", loop[2] ?? "", answer, depth);
  }
  if (/^case(?:\s|$)/.test(trimmed)) {
    const found = /^case\s+(\S+)\s+in\s+([\s\S]+?)\s*;\s*esac$/.exec(trimmed);
    if (!found) return fail(session, "case $AD in a) echo bir;; esac");
    return runCase(session, found[1] ?? "", found[2] ?? "", answer, depth);
  }
  const fn = /^([A-Za-z_]\w*)\(\)\s*\{\s*([\s\S]+?)\s*;?\s*\}$/.exec(trimmed);
  if (fn) {
    session.functions[fn[1] ?? ""] = (fn[2] ?? "").trim();
    return ok(session, "");
  }
  return undefined;
}

function runWhile(session: Session, testLine: string, body: string, answer: string | undefined, depth: number): ShellOut {
  let current = session;
  const outputs: string[] = [];
  for (let index = 0; index < 30; index += 1) {
    const check = runSequence(current, testLine, index === 0 ? answer : undefined, depth + 1);
    current = check.session;
    if (check.ask || pause(check)) return { ...check, session: current, output: outputs.join("\n") };
    if (check.ok === false) return ok(current, outputs.join("\n"));
    const result = runSequence(current, body, undefined, depth + 1);
    current = result.session;
    if (result.output && result.output !== "\u0000clear") outputs.push(result.output);
    if (pause(result)) return { ...result, session: current, output: outputs.join("\n") };
    if (result.ok === false) return fail(current, outputs.join("\n"));
  }
  outputs.push("… kəsildi");
  return ok(current, outputs.join("\n"));
}

function runCase(session: Session, valueSrc: string, arms: string, answer: string | undefined, depth: number): ShellOut {
  const value = splitArgs(valueSrc, session).join(" ");
  const parts = arms.split(";;").map((part) => part.trim()).filter(Boolean);
  for (const part of parts) {
    const cut = part.indexOf(")");
    if (cut < 0) continue;
    const pattern = part.slice(0, cut).trim();
    const body = part.slice(cut + 1).trim();
    if (pattern !== "*" && pattern !== value) continue;
    if (!body) return ok(session, "");
    return runSequence(session, body, answer, depth + 1);
  }
  return ok(session, "");
}

function runFor(session: Session, name: string, wordsSrc: string, body: string, answer: string | undefined, depth: number): ShellOut {
  const words = splitArgs(wordsSrc, session);
  const list = words.slice(0, 40);
  let current = session;
  const outputs: string[] = [];
  for (let index = 0; index < list.length; index += 1) {
    current.envVars[name] = list[index] ?? "";
    const result = runSequence(current, body, index === 0 ? answer : undefined, depth + 1);
    current = result.session;
    if (result.output && result.output !== "\u0000clear") outputs.push(result.output);
    if (result.ask || result.python || result.node || result.php || result.github || result.fetch || result.repl || result.open) {
      return { ...result, session: current, output: outputs.join("\n") };
    }
    if (result.ok === false) return fail(current, outputs.join("\n"));
  }
  if (words.length > 40) outputs.push("… kəsildi");
  return ok(current, outputs.join("\n"));
}

function runIf(session: Session, testLine: string, thenBody: string, elseBody: string | undefined, answer: string | undefined, depth: number): ShellOut {
  const check = runSequence(session, testLine, answer, depth + 1);
  if (check.ask || check.python || check.node || check.php || check.github || check.fetch || check.repl || check.open) return check;
  const branch = check.ok === false ? elseBody : thenBody;
  if (!branch) return ok(check.session, check.output);
  const result = runSequence(check.session, branch, undefined, depth + 1);
  if (!check.output || check.output === "\u0000clear") return result;
  return { ...result, output: [check.output, result.output].filter(Boolean).join("\n") };
}

function peelErr(line: string) {
  const merged = line.replace(/\s2>&1\s*$/, "");
  const match = /^(.*\S)\s+2(>>?)\s*(\S+)\s*$/.exec(merged);
  if (!match) return { line: merged, err: undefined as { path: string; append: boolean } | undefined };
  return { line: match[1] ?? merged, err: { path: match[3] ?? "", append: match[2] === ">>" } };
}

function hideErr(session: Session, last: ShellOut, err?: { path: string; append: boolean }): ShellOut {
  if (!err || last.ask) return last;
  const path = resolve(session.cwd, err.path);
  ensureDir(session, parent(path));
  const prev = session.files[path] ?? "";
  const chunk = last.output.endsWith("\n") || !last.output ? last.output : `${last.output}\n`;
  session.files[path] = err.append ? `${prev}${chunk}` : chunk;
  return { ...last, session, output: "" };
}

function timeCmd(session: Session, args: string[], answer: string | undefined, stdin: string | undefined): ShellOut {
  const cmd = args[0];
  if (!cmd) return fail(session, "time əmr");
  const started = Date.now();
  const ran = dispatch(session, cmd, args.slice(1), answer, stdin);
  if (ran.ask || ran.python || ran.node || ran.php || ran.github || ran.fetch || ran.repl || ran.output === "\u0000clear") return ran;
  const note = `${Date.now() - started} ms`;
  return { ...ran, output: ran.output ? `${ran.output}\n${note}` : note };
}

function awkCmd(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  let sep = "\\s+";
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-F" && args[index + 1]) {
      sep = args[index + 1] ?? sep;
      index += 1;
      continue;
    }
    if (arg.startsWith("-F") && arg.length > 2) {
      sep = arg.slice(2);
      continue;
    }
    rest.push(arg);
  }
  const program = (rest[0] || answer || "").trim();
  if (!program) return askFor(session, { id: "text", title: "Sütun", label: "{print $1}", resume: "awk" });
  const compact = program.replace(/\s+/g, " ");
  const match = /^\{print (.+)\}$/.exec(compact);
  if (!match?.[1]) return fail(session, "awk '{print $1}'");
  const file = rest[1];
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "awk '{print $1}' fayl");
  const fields = splitPrint(match[1]);
  const lines = linesOf(got.text);
  const out = lines.slice(0, 400).map((line) => {
    const parts = sep === "\\s+" ? line.trim().split(/\s+/) : line.split(sep);
    return fields.map((field) => awkField(field, line, parts)).join(" ");
  });
  if (lines.length > 400) out.push("… kəsildi");
  return ok(session, out.join("\n"));
}

function splitPrint(text: string) {
  return text.split(",").map((part) => part.trim()).filter(Boolean);
}

function awkField(field: string, line: string, parts: string[]) {
  if (field === "$0") return line;
  if (field === "NF") return String(parts.filter(Boolean).length);
  const number = /^\$(\d+)$/.exec(field);
  if (number) return parts[Number(number[1]) - 1] ?? "";
  if ((field.startsWith('"') && field.endsWith('"')) || (field.startsWith("'") && field.endsWith("'"))) return field.slice(1, -1);
  return field;
}

function columnCmd(session: Session, args: string[], answer: string | undefined, stdin?: string): ShellOut {
  let sep = "\\s+";
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-t") continue;
    if (arg === "-s" && args[index + 1]) {
      sep = args[index + 1] ?? sep;
      index += 1;
      continue;
    }
    if (!arg.startsWith("-")) rest.push(arg);
  }
  const file = rest[0] || answer;
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, "column -t fayl");
  const rows = linesOf(got.text)
    .slice(0, 400)
    .map((line) => (sep === "\\s+" ? line.trim().split(/\s+/) : line.split(sep)).slice(0, 12));
  const width: number[] = [];
  for (const row of rows) row.forEach((cell, index) => { width[index] = Math.max(width[index] ?? 0, cell.length); });
  return ok(session, rows.map((row) => row.map((cell, index) => (index === row.length - 1 ? cell : cell.padEnd(width[index] ?? 0))).join("  ")).join("\n"));
}

function cmpCmd(session: Session, args: string[]): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  if (names.length < 2) return fail(session, "cmp fayl1 fayl2");
  const left = session.files[resolve(session.cwd, names[0] ?? "")];
  const right = session.files[resolve(session.cwd, names[1] ?? "")];
  if (left === undefined) return fail(session, `fayl yoxdur: ${names[0]}`);
  if (right === undefined) return fail(session, `fayl yoxdur: ${names[1]}`);
  return left === right ? ok(session, "") : fail(session, "fərqlidir");
}

function joinCmd(session: Session, args: string[]): ShellOut {
  const names = args.filter((arg) => !arg.startsWith("-"));
  if (names.length < 2) return fail(session, "join fayl1 fayl2");
  const left = session.files[resolve(session.cwd, names[0] ?? "")];
  const right = session.files[resolve(session.cwd, names[1] ?? "")];
  if (left === undefined) return fail(session, `fayl yoxdur: ${names[0]}`);
  if (right === undefined) return fail(session, `fayl yoxdur: ${names[1]}`);
  const table = new Map<string, string[]>();
  for (const line of linesOf(right)) {
    const cells = line.trim().split(/\s+/);
    const key = cells[0];
    if (!key) continue;
    const list = table.get(key) ?? [];
    list.push(cells.slice(1).join(" "));
    table.set(key, list);
  }
  const out: string[] = [];
  for (const line of linesOf(left)) {
    const cells = line.trim().split(/\s+/);
    const key = cells[0];
    const hits = key ? table.get(key) : undefined;
    if (!key || !hits?.length) continue;
    const extra = hits.shift() ?? "";
    out.push([key, cells.slice(1).join(" "), extra].filter(Boolean).join(" "));
  }
  return ok(session, out.join("\n"));
}

function splitCmd(session: Session, args: string[]): ShellOut {
  let count = 1000;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-l" && args[index + 1]) {
      count = Number(args[index + 1]) || 1000;
      index += 1;
      continue;
    }
    if (!arg.startsWith("-")) rest.push(arg);
  }
  const name = rest[0];
  if (!name) return fail(session, "split -l 2 fayl");
  const body = session.files[resolve(session.cwd, name)];
  if (body === undefined) return fail(session, `fayl yoxdur: ${name}`);
  const size = Math.min(400, Math.max(1, count));
  const rows = body.endsWith("\n") ? body.slice(0, -1).split("\n") : body.split("\n");
  if (Math.ceil(rows.length / size) > 20) return fail(session, "çox hissə");
  for (let index = 0; index < rows.length; index += size) {
    const piece = Math.floor(index / size);
    const suffix = `x${String.fromCharCode(97 + Math.floor(piece / 26))}${String.fromCharCode(97 + (piece % 26))}`;
    session.files[resolve(session.cwd, suffix)] = `${rows.slice(index, index + size).join("\n")}\n`;
  }
  return ok(session, "");
}

function truncateCmd(session: Session, args: string[]): ShellOut {
  let size: number | undefined;
  const rest: string[] = [];
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index] ?? "";
    if (arg === "-s" && args[index + 1]) {
      size = Number(args[index + 1]);
      index += 1;
      continue;
    }
    if (!arg.startsWith("-")) rest.push(arg);
  }
  const name = rest[0];
  if (!name || size === undefined || !Number.isInteger(size) || size < 0 || size > 200_000) return fail(session, "truncate -s 0 fayl");
  const path = resolve(session.cwd, name);
  const body = session.files[path] ?? "";
  ensureDir(session, parent(path));
  session.files[path] = new TextDecoder().decode(new TextEncoder().encode(body).slice(0, size));
  return ok(session, "");
}

function lineEndCmd(session: Session, args: string[], stdin: string | undefined, toDos: boolean): ShellOut {
  const file = args.find((arg) => !arg.startsWith("-"));
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) return fail(session, toDos ? "unix2dos fayl" : "dos2unix fayl");
  const next = toDos ? got.text.replace(/\r?\n/g, "\r\n") : got.text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  if (file) {
    session.files[resolve(session.cwd, file)] = next;
    return ok(session, "");
  }
  return ok(session, next);
}

function pageCmd(session: Session, args: string[], answer: string | undefined, stdin: string | undefined, count: number): ShellOut {
  const file = args.find((arg) => !arg.startsWith("-")) || answer;
  const got = textFrom(session, file, stdin);
  if (got.error) return fail(session, got.error);
  if (got.text === undefined) {
    return askFor(session, { id: "text", title: count > 20 ? "Less" : "More", label: "Fayl adı", resume: count > 20 ? "less" : "more" });
  }
  const lines = linesOf(got.text);
  const picked = lines.slice(0, count);
  if (lines.length > count) picked.push("… kəsildi");
  return ok(session, picked.join("\n"));
}

function chmodCmd(session: Session, args: string[]): ShellOut {
  const mode = args[0];
  const name = args[1];
  if (!mode || !name || !/^[0-7]{3,4}$/.test(mode)) return fail(session, "chmod 644 fayl");
  const path = resolve(session.cwd, name);
  if (session.files[path] === undefined) return fail(session, `fayl yoxdur: ${name}`);
  session.modes[path] = mode;
  return ok(session, "");
}

function tarCmd(session: Session, args: string[], answer?: string): ShellOut {
  const source = args.length ? args : answer?.trim().split(/\s+/) ?? [];
  if (!source.length) return askFor(session, { id: "text", title: "Arxiv", label: "-tf arxiv.tar", resume: "tar" });
  const flags = source.filter((arg) => arg.startsWith("-")).join("");
  const rest = source.filter((arg) => !arg.startsWith("-"));
  const archiveName = rest[0];
  if (!archiveName || (!flags.includes("c") && !flags.includes("t") && !flags.includes("x"))) return fail(session, "tar -cf arxiv.tar fayl");
  const archive = resolve(session.cwd, archiveName);
  if (flags.includes("c")) return tarCreate(session, archive, rest.slice(1));
  const body = session.files[archive];
  if (body === undefined) return fail(session, `fayl yoxdur: ${archiveName}`);
  return flags.includes("t") ? tarList(session, body) : tarExtract(session, body);
}

function tarCreate(session: Session, archive: string, names: string[]): ShellOut {
  if (!names.length) return fail(session, "tar -cf arxiv.tar fayl");
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (const name of names) {
    const path = resolve(session.cwd, name);
    const body = session.files[path];
    if (body === undefined) return fail(session, `fayl yoxdur: ${name}`);
    const rel = name.replace(/^\.\//, "");
    if (!rel || rel.length > 100 || rel.split("/").includes("..")) return fail(session, `ad səhvdir: ${name}`);
    const data = new TextEncoder().encode(body);
    total += data.length + 512;
    if (total > 200_000 || chunks.length >= 80) return fail(session, "arxiv çox böyükdür");
    const pad = (512 - (data.length % 512)) % 512;
    chunks.push(tarHeader(rel, data.length), data, new Uint8Array(pad));
  }
  chunks.push(new Uint8Array(1024));
  const bytes = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0));
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  ensureDir(session, parent(archive));
  session.files[archive] = latin1(bytes);
  return ok(session, archive.split("/").pop() ?? archive);
}

function tarList(session: Session, body: string): ShellOut {
  const names: string[] = [];
  for (const item of readTar(body)) {
    if (typeof item === "string") return fail(session, item);
    names.push(item.name);
    if (names.length > 40) break;
  }
  return ok(session, names.join("\n"));
}

function tarExtract(session: Session, body: string): ShellOut {
  const items = readTar(body);
  let count = 0;
  for (const item of items) {
    if (typeof item === "string") return fail(session, item);
    if (item.name.split("/").includes("..")) return fail(session, "arxiv təhlükəlidir");
    const path = resolve(session.cwd, item.name);
    if (!(session.cwd === "/" ? path.startsWith("/") : path.startsWith(`${session.cwd}/`) || path === session.cwd)) {
      return fail(session, "arxiv təhlükəlidir");
    }
    ensureDir(session, parent(path));
    session.files[path] = item.text;
    count += 1;
    if (count > 40) break;
  }
  return ok(session, `${count} fayl çıxdı`);
}

function readTar(body: string) {
  const bytes = fromLatin1(body);
  const items: Array<{ name: string; text: string } | string> = [];
  let offset = 0;
  while (offset + 512 <= bytes.length) {
    const header = bytes.subarray(offset, offset + 512);
    offset += 512;
    if (header.every((byte) => byte === 0)) break;
    const name = readAscii(header, 0, 100);
    if (!name) break;
    const size = parseInt(readAscii(header, 124, 12).trim() || "0", 8);
    if (!Number.isFinite(size) || size < 0 || size > 200_000) {
      items.push("arxiv səhvdir");
      break;
    }
    const data = bytes.subarray(offset, offset + size);
    offset += size + ((512 - (size % 512)) % 512);
    const kind = header[156] ?? 0;
    if (kind === 53) {
      items.push({ name, text: "" });
      continue;
    }
    items.push({ name, text: new TextDecoder().decode(data) });
  }
  return items;
}

function tarHeader(name: string, size: number) {
  const buf = new Uint8Array(512);
  putAscii(buf, 0, name);
  putAscii(buf, 100, "0000644\0");
  putAscii(buf, 108, "0000000\0");
  putAscii(buf, 116, "0000000\0");
  putAscii(buf, 124, `${size.toString(8).padStart(11, "0")}\0`);
  putAscii(buf, 136, "00000000000\0");
  buf.fill(0x20, 148, 156);
  buf[156] = 48;
  putAscii(buf, 257, "ustar\0");
  putAscii(buf, 263, "00");
  let sum = 0;
  for (const byte of buf) sum += byte;
  putAscii(buf, 148, `${sum.toString(8).padStart(6, "0")}\0 `);
  return buf;
}

function putAscii(buf: Uint8Array, offset: number, text: string) {
  for (let index = 0; index < text.length && offset + index < buf.length; index += 1) buf[offset + index] = text.charCodeAt(index);
}

function readAscii(buf: Uint8Array, offset: number, length: number) {
  let text = "";
  for (let index = 0; index < length; index += 1) {
    const byte = buf[offset + index] ?? 0;
    if (!byte) break;
    text += String.fromCharCode(byte);
  }
  return text;
}

function latin1(bytes: Uint8Array) {
  let text = "";
  for (let index = 0; index < bytes.length; index += 0x8000) {
    text += String.fromCharCode(...bytes.subarray(index, Math.min(index + 0x8000, bytes.length)));
  }
  return text;
}

function fromLatin1(text: string) {
  const bytes = new Uint8Array(text.length);
  for (let index = 0; index < text.length; index += 1) bytes[index] = text.charCodeAt(index) & 0xff;
  return bytes;
}

export const HELP = `Üç yol, eyni əmrlər
  terminala yaz  ·  Seçimlər düyməsi  ·  xana (token, ad, təsdiq)

Fayl
  ls  cd  pwd  cat  mkdir  touch  rm  cp  mv  edit  tree  stat
  grep  head  tail  wc  sort  uniq  cut  diff  sed  find
  file  realpath  xxd  sha256sum  md5sum  fold  paste
  hostname  arch  uptime  nproc  tty  logname
  ping  ssh  sftp  apt  apk
  rmdir  strings  shuf  comm  expand  fmt  mktemp
  factor  xargs  read  unset  printenv  sha1sum  egrep  sudo
  awk  column  cmp  join  split  tar  less  chmod

Mətn
  expr '2 + 3 * 4'    base64 fayl    jq . fayl.json
  seq 1 5    cal    nl    tac
  echo salam | tr a-z A-Z
  alias salam=echo Salam
  test -f fayl && echo var    false || echo $?
  echo salam | tee qeyd.txt
  for i in a b; do echo $i; done
  if test -f fayl; then echo var; fi
  echo $((2+3))    false 2> xeta.txt
  nibras zibil    nibras qaytar

Mühit, bir dəfəyə biri
  nibras install alpine | debian | ubuntu
  nibras install python | node | php | git | build
  nibras sil <ad>    nibras disk    nibras

İş
  nibras layihe python | node | web | php | c | md
  python3 fayl.py    node fayl.js    php fayl.php
  gcc fayl.c         curl <https-ünvan>
  nibras md qeyd.md  nibras serve    nibras stop

Git
  git clone sahib/ad
  git pull | branch | switch | diff | rebase
  git init | add . | commit | remote | push
  Token xanaya yazılır, ekrana düşmür.

Ubuntu
  nibras install ubuntu
  lsb_release -a
  apt update | apt install python
  dpkg -l
  adduser ad | su ad | sudo əmr
  ln -s hədəf ad
  Bu, telefon üçün Ubuntu qabığıdır. Tam paket arxivi kök sistemdədir, burada işləyən paketlər seçilir.

Telefon
  pkg update | pkg install python
  nibras-distro list | login ubuntu
  nibras-setup-storage
  nibras-clipboard-set | nibras-battery-status | nibras-toast
  nibras-wake-lock | nibras-wifi-connectioninfo | nibras-share
  cd - | pushd | popd | !! | Ctrl-R
  logcat terminal jurnalıdır. adb və sistem logcat-i burada yoxdur.
  Açar sırası: ESC TAB CTRL ALT və oxlar. ~/.nibras/nibras.properties
  SMS, zəng və kamera yoxdur.

Kömək: help əmr. Məsələn help curl.`;
