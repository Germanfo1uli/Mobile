import http from "node:http";
import { execFileSync } from "node:child_process";

const psql = process.env.BUGS_PSQL;
const port = Number(process.env.BUGS_VIEWER_PORT || 8090);
const postgresPort = Number(process.env.BUGS_PG_PORT || 5432);
if (!psql) throw new Error("BUGS_PSQL is required");

const escape = (value) => String(value ?? "")
  .replaceAll("&", "&amp;").replaceAll("<", "&lt;")
  .replaceAll(">", "&gt;").replaceAll('"', "&quot;");

function query(sql, csv = false) {
  const args = ["-h", "127.0.0.1", "-p", String(postgresPort), "-U", "bugs", "-d", "bugs", "-v", "ON_ERROR_STOP=1"];
  args.push(csv ? "--csv" : "-At", "-c", sql);
  return execFileSync(psql, args, { encoding: "utf8", windowsHide: true }).trim();
}

function parseCsv(text) {
  const rows = [];
  let row = [], value = "", quoted = false;
  for (let i = 0; i <= text.length; i++) {
    const char = text[i] ?? "\n";
    if (quoted && char === '"' && text[i + 1] === '"') { value += '"'; i++; }
    else if (char === '"') quoted = !quoted;
    else if (!quoted && (char === "," || char === "\n")) {
      row.push(value.replace(/\r$/, "")); value = "";
      if (char === "\n") { rows.push(row); row = []; }
    } else value += char;
  }
  return rows.filter((item) => item.length > 1 || item[0]);
}

function layout(title, content) {
  return `<!doctype html><html lang="ru"><head><meta charset="utf-8">
  <meta http-equiv="refresh" content="5"><title>${escape(title)}</title><style>
  body{font-family:Segoe UI,sans-serif;margin:0;background:#10131a;color:#eaf0ff}header{padding:20px 28px;background:#171d29;position:sticky;top:0}main{padding:24px 28px}a{color:#74b7ff}table{border-collapse:collapse;width:100%;background:#171d29}th,td{border:1px solid #354057;padding:9px;text-align:left;vertical-align:top}th{background:#273149}.pill{display:inline-block;padding:4px 9px;background:#245c3f;border-radius:999px}.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:14px}.card{padding:18px;background:#171d29;border:1px solid #354057;border-radius:8px}.muted{color:#9ba8bd}</style></head>
  <body><header><strong>ALTHUNT PostgreSQL</strong> <span class="pill">bugs · 127.0.0.1:${postgresPort}</span></header><main>${content}</main></body></html>`;
}

function home() {
  const names = query("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").split(/\r?\n/).filter(Boolean);
  const cards = names.map((name) => {
    const count = query(`SELECT count(*) FROM \"${name}\"`);
    return `<div class="card"><h2><a href="/table?name=${encodeURIComponent(name)}">${escape(name)}</a></h2><p><strong>${escape(count)}</strong> строк</p></div>`;
  }).join("");
  return layout("База ALTHUNT", `<h1>База приложения</h1><p class="muted">Обновление каждые 5 секунд. Панель работает только на чтение.</p><div class="cards">${cards}</div>`);
}

function table(name) {
  if (!/^[a-z_][a-z0-9_]*$/.test(name)) throw new Error("Некорректное имя таблицы");
  const allowed = new Set(query("SELECT tablename FROM pg_tables WHERE schemaname='public'").split(/\r?\n/).filter(Boolean));
  if (!allowed.has(name)) throw new Error("Таблица не найдена");
  const rows = parseCsv(query(`SELECT * FROM \"${name}\" ORDER BY 1 DESC LIMIT 100`, true));
  const head = rows.shift() || [];
  const htmlRows = rows.map((row) => `<tr>${row.map((value) => `<td>${escape(value)}</td>`).join("")}</tr>`).join("");
  return layout(name, `<p><a href="/">← Все таблицы</a></p><h1>${escape(name)}</h1><p class="muted">До 100 последних строк</p><table><thead><tr>${head.map((value) => `<th>${escape(value)}</th>`).join("")}</tr></thead><tbody>${htmlRows}</tbody></table>`);
}

http.createServer((request, response) => {
  try {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const body = url.pathname === "/table" ? table(url.searchParams.get("name") || "") : home();
    response.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
    response.end(body);
  } catch (error) {
    response.writeHead(500, { "content-type": "text/html; charset=utf-8" });
    response.end(layout("Ошибка", `<h1>Ошибка</h1><pre>${escape(error.message)}</pre><p><a href="/">Повторить</a></p>`));
  }
}).listen(port, "127.0.0.1", () => console.log(`DB viewer: http://127.0.0.1:${port}`));
