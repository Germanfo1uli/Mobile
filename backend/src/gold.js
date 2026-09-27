const CACHE_MILLISECONDS = 6 * 60 * 60 * 1000;
let cached = null;

function cbrDate(date) {
  return [String(date.getUTCDate()).padStart(2, "0"), String(date.getUTCMonth() + 1).padStart(2, "0"), date.getUTCFullYear()].join("/");
}

export function parseGoldXml(xml) {
  const records = [...xml.matchAll(/<Record\s+Date="([^"]+)"\s+Code="1">[\s\S]*?<Buy>([^<]+)<\/Buy>/gi)];
  const last = records.at(-1);
  if (!last) throw new Error("Bank of Russia returned no gold quotation");
  const rublesPerGram = Number(last[2].trim().replace(",", "."));
  if (!Number.isFinite(rublesPerGram) || rublesPerGram <= 0) throw new Error("Invalid gold quotation from Bank of Russia");
  return { rublesPerGram, date: last[1] };
}

export async function loadGoldRate(now = Date.now()) {
  if (cached && now - cached.loadedAt < CACHE_MILLISECONDS) return cached.rate;
  const to = new Date(now);
  const from = new Date(now - 14 * 24 * 60 * 60 * 1000);
  const url = new URL("https://www.cbr.ru/scripts/xml_metall.asp");
  url.searchParams.set("date_req1", cbrDate(from));
  url.searchParams.set("date_req2", cbrDate(to));
  const response = await fetch(url, { headers: { "user-agent": "AltHunt/1.0" }, signal: AbortSignal.timeout(8_000) });
  if (!response.ok) throw new Error(`Bank of Russia request failed: ${response.status}`);
  const rate = parseGoldXml(await response.text());
  cached = { loadedAt: now, rate };
  return rate;
}
