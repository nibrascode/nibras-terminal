type Tok = { t: string; v: string };
type Val = number | string;

class ParseError extends Error {}

export function runC(source: string): { output: string; error?: string } {
  try {
    const machine = new Machine(lex(source));
    machine.program();
    return { output: machine.output.replace(/\s+$/, "") };
  } catch (error) {
    return { output: "", error: error instanceof Error ? error.message : "C xətası" };
  }
}

class Machine {
  output = "";
  private index = 0;
  private vars = new Map<string, Val>();
  private readonly toks: Tok[];

  constructor(toks: Tok[]) {
    this.toks = toks;
  }

  program() {
    let found = false;
    while (!this.eof()) {
      if (this.looksLikeFunction()) {
        const name = this.toks[this.index + 1]?.v;
        this.eat();
        this.eat();
        this.skipParams();
        if (name === "main") {
          try {
            this.block(true);
          } catch (error) {
            if (!(error instanceof Return)) throw error;
          }
          found = true;
          return;
        }
        this.block(false);
        continue;
      }
      throw new ParseError("bu C hissəsi dəstəklənmir");
    }
    if (!found) throw new ParseError("main funksiyası yoxdur");
  }

  private looksLikeFunction() {
    const kind = this.toks[this.index]?.v;
    const name = this.toks[this.index + 1];
    const paren = this.toks[this.index + 2];
    return (kind === "int" || kind === "void") && name?.t === "id" && paren?.v === "(";
  }

  private skipParams() {
    let depth = 0;
    while (!this.eof()) {
      const value = this.eat().v;
      if (value === "(") depth += 1;
      if (value === ")") {
        depth -= 1;
        if (depth === 0) return;
      }
    }
  }

  private block(run: boolean) {
    this.expect("{");
    while (!this.eof() && !this.peek("}")) this.stmt(run);
    this.expect("}");
  }

  private stmt(run: boolean) {
    const word = this.toks[this.index]?.v;
    if (word && ["struct", "char", "float", "double", "switch", "typedef", "enum", "union", "do", "const", "static"].includes(word)) {
      throw new ParseError(`${word} bu tərcüməçidə yoxdur. printf, int, if, while, for işləyir.`);
    }
    if (this.peek("{")) return this.block(run);
    if (this.peek("if")) return this.ifStmt(run);
    if (this.peek("while")) return this.whileStmt(run);
    if (this.peek("for")) return this.forStmt(run);
    if (this.peek("return")) {
      this.eat();
      if (!this.peek(";")) this.expr(run);
      this.expect(";");
      if (run) throw new Return();
      return;
    }
    if (this.peek("printf") || this.peek("puts")) return this.printStmt(run);
    if (this.peek("int")) return this.decl(run);
    this.expr(run);
    this.expect(";");
  }

  private ifStmt(run: boolean) {
    this.expect("if");
    this.expect("(");
    const cond = this.expr(run);
    this.expect(")");
    this.stmt(run && Boolean(cond));
    if (this.peek("else")) {
      this.eat();
      this.stmt(run && !cond);
    }
  }

  private whileStmt(run: boolean) {
    this.expect("while");
    this.expect("(");
    const start = this.index;
    let guard = 0;
    for (;;) {
      this.index = start;
      const cond = this.expr(run);
      this.expect(")");
      if (!run || !cond) {
        this.stmt(false);
        return;
      }
      try {
        this.stmt(true);
      } catch (error) {
        if (error instanceof Break) return;
        throw error;
      }
      guard += 1;
      if (guard > 100_000) throw new ParseError("döngü çox uzundur");
    }
  }

  private forStmt(run: boolean) {
    this.expect("for");
    this.expect("(");
    if (this.peek("int")) this.decl(run);
    else if (!this.peek(";")) {
      this.expr(run);
      this.expect(";");
    } else this.eat();
    const condAt = this.index;
    const cond = () => {
      this.index = condAt;
      if (this.peek(";")) return 1;
      return this.expr(run);
    };
    if (!this.peek(";")) this.expr(false);
    this.expect(";");
    const stepAt = this.index;
    const step = () => {
      this.index = stepAt;
      if (!this.peek(")")) this.expr(run);
    };
    this.expr(false);
    this.expect(")");
    const bodyAt = this.index;
    let guard = 0;
    for (;;) {
      this.index = condAt;
      const ok = cond();
      this.expect(";");
      this.expr(false);
      this.expect(")");
      if (!run || !ok) {
        this.index = bodyAt;
        this.stmt(false);
        return;
      }
      this.index = bodyAt;
      try {
        this.stmt(true);
      } catch (error) {
        if (error instanceof Break) return;
        throw error;
      }
      this.index = stepAt;
      step();
      this.expect(")");
      guard += 1;
      if (guard > 100_000) throw new ParseError("döngü çox uzundur");
    }
  }

  private printStmt(run: boolean) {
    const kind = this.eat().v;
    this.expect("(");
    if (kind === "puts") {
      const value = this.expr(run);
      this.expect(")");
      this.expect(";");
      if (run) this.output += `${String(value)}\n`;
      return;
    }
    const fmt = this.expr(run);
    const args: Val[] = [];
    while (this.peek(",")) {
      this.eat();
      args.push(this.expr(run));
    }
    this.expect(")");
    this.expect(";");
    if (run) this.output += formatPrintf(String(fmt), args);
  }

  private decl(run: boolean) {
    this.expect("int");
    const name = this.ident();
    let value: Val = 0;
    if (this.peek("=")) {
      this.eat();
      value = this.expr(run);
    }
    this.expect(";");
    if (run) this.vars.set(name, Number(value) || 0);
  }

  private expr(run: boolean): Val {
    return this.assign(run);
  }

  private assign(run: boolean): Val {
    if (this.toks[this.index]?.t === "id" && this.toks[this.index + 1]?.v === "=") {
      const name = this.eat().v;
      this.eat();
      const value = this.assign(run);
      if (run) this.vars.set(name, value);
      return value;
    }
    return this.or(run);
  }

  private or(run: boolean): Val {
    let left = this.and(run);
    while (this.peek("||")) {
      this.eat();
      const right = this.and(run);
      left = truthy(left) || truthy(right) ? 1 : 0;
    }
    return left;
  }

  private and(run: boolean): Val {
    let left = this.cmp(run);
    while (this.peek("&&")) {
      this.eat();
      const right = this.cmp(run);
      left = truthy(left) && truthy(right) ? 1 : 0;
    }
    return left;
  }

  private cmp(run: boolean): Val {
    let left = this.add(run);
    while (this.peek("==") || this.peek("!=") || this.peek("<=") || this.peek(">=") || this.peek("<") || this.peek(">")) {
      const op = this.eat().v;
      const right = this.add(run);
      const ok =
        op === "==" ? left === right : op === "!=" ? left !== right : op === "<" ? Number(left) < Number(right) : op === ">" ? Number(left) > Number(right) : op === "<=" ? Number(left) <= Number(right) : Number(left) >= Number(right);
      left = ok ? 1 : 0;
    }
    return left;
  }

  private add(run: boolean): Val {
    let left = this.mul(run);
    while (this.peek("+") || this.peek("-")) {
      const op = this.eat().v;
      const right = this.mul(run);
      left = op === "+" ? Number(left) + Number(right) : Number(left) - Number(right);
    }
    return left;
  }

  private mul(run: boolean): Val {
    let left = this.unary(run);
    while (this.peek("*") || this.peek("/") || this.peek("%")) {
      const op = this.eat().v;
      const right = this.unary(run);
      const a = Number(left);
      const b = Number(right);
      if ((op === "/" || op === "%") && b === 0) throw new ParseError("sıfıra bölmək olmaz");
      left = op === "*" ? a * b : op === "/" ? Math.trunc(a / b) : a % b;
    }
    return left;
  }

  private unary(run: boolean): Val {
    if (this.peek("!") || this.peek("-") || this.peek("+")) {
      const op = this.eat().v;
      const value = this.unary(run);
      if (op === "!") return truthy(value) ? 0 : 1;
      if (op === "-") return -Number(value);
      return Number(value);
    }
    if (this.peek("++") || this.peek("--")) {
      const op = this.eat().v;
      const name = this.ident();
      const next = Number(this.vars.get(name) ?? 0) + (op === "++" ? 1 : -1);
      if (run) this.vars.set(name, next);
      return next;
    }
    return this.primary(run);
  }

  private primary(run: boolean): Val {
    if (this.peek("(")) {
      this.eat();
      const value = this.expr(run);
      this.expect(")");
      return value;
    }
    const tok = this.eat();
    if (tok.t === "num") return Number(tok.v);
    if (tok.t === "str") return tok.v;
    if (tok.t === "id") {
      if (this.peek("++") || this.peek("--")) {
        const op = this.eat().v;
        const current = Number(this.vars.get(tok.v) ?? 0);
        if (run) this.vars.set(tok.v, current + (op === "++" ? 1 : -1));
        return current;
      }
      if (!run) return 0;
      if (!this.vars.has(tok.v)) throw new ParseError(`dəyişən yoxdur: ${tok.v}`);
      return this.vars.get(tok.v) ?? 0;
    }
    throw new ParseError(`gözlənilməyən: ${tok.v}`);
  }

  private ident() {
    const tok = this.eat();
    if (tok.t !== "id") throw new ParseError("ad gözlənilir");
    return tok.v;
  }

  private peek(value: string) {
    return this.toks[this.index]?.v === value;
  }

  private eat() {
    const tok = this.toks[this.index];
    if (!tok) throw new ParseError("kod yarımçıq qalıb");
    this.index += 1;
    return tok;
  }

  private expect(value: string) {
    const tok = this.eat();
    if (tok.v !== value) throw new ParseError(`${value} gözlənilirdi`);
  }

  private eof() {
    return this.index >= this.toks.length;
  }
}

class Return extends Error {}
class Break extends Error {}

function truthy(value: Val) {
  return value !== 0 && value !== "";
}

function formatPrintf(fmt: string, args: Val[]) {
  let index = 0;
  let out = "";
  for (let i = 0; i < fmt.length; i += 1) {
    if (fmt[i] === "%" && i + 1 < fmt.length) {
      const spec = fmt[i + 1];
      if (spec === "d" || spec === "i") out += String(Number(args[index++] ?? 0));
      else if (spec === "s") out += String(args[index++] ?? "");
      else if (spec === "c") out += String.fromCharCode(Number(args[index++] ?? 0));
      else if (spec === "%") out += "%";
      else throw new ParseError(`printf formatı dəstəklənmir: %${spec}`);
      i += 1;
      continue;
    }
    out += fmt[i];
  }
  return out;
}

function lex(source: string) {
  const out: Tok[] = [];
  let i = 0;
  const ops = ["==", "!=", "<=", ">=", "&&", "||", "++", "--"];
  while (i < source.length) {
    const ch = source[i] ?? "";
    if (/\s/.test(ch)) {
      i += 1;
      continue;
    }
    if (ch === "/" && source[i + 1] === "/") {
      i = source.indexOf("\n", i);
      if (i < 0) break;
      continue;
    }
    if (ch === "/" && source[i + 1] === "*") {
      const end = source.indexOf("*/", i + 2);
      if (end < 0) throw new ParseError("şərh bağlanmayıb");
      i = end + 2;
      continue;
    }
    if (ch === "#") {
      i = source.indexOf("\n", i);
      if (i < 0) break;
      continue;
    }
    if (ch === '"' || ch === "'") {
      const quote = ch;
      let value = "";
      i += 1;
      while (i < source.length && source[i] !== quote) {
        if (source[i] === "\\") {
          const esc = source[i + 1];
          value += esc === "n" ? "\n" : esc === "t" ? "\t" : esc === "\\" ? "\\" : esc === "0" ? "\0" : (esc ?? "");
          i += 2;
          continue;
        }
        value += source[i];
        i += 1;
      }
      if (source[i] !== quote) throw new ParseError("sətir bağlanmayıb");
      i += 1;
      out.push({ t: "str", v: value });
      continue;
    }
    if (/[0-9]/.test(ch)) {
      let value = "";
      while (i < source.length && /[0-9]/.test(source[i] ?? "")) {
        value += source[i];
        i += 1;
      }
      out.push({ t: "num", v: value });
      continue;
    }
    if (/[A-Za-z_]/.test(ch)) {
      let value = "";
      while (i < source.length && /[A-Za-z0-9_]/.test(source[i] ?? "")) {
        value += source[i];
        i += 1;
      }
      out.push({ t: "id", v: value });
      continue;
    }
    const two = source.slice(i, i + 2);
    if (ops.includes(two)) {
      out.push({ t: "op", v: two });
      i += 2;
      continue;
    }
    out.push({ t: "op", v: ch });
    i += 1;
  }
  return out;
}
