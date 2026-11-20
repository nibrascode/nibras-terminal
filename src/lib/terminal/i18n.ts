export type Lang = "az" | "tr" | "en" | "ru";
export const LANGS: Lang[] = ["az", "tr", "en", "ru"];

type Tongue = Exclude<Lang, "az">;

const packs: Partial<Record<Tongue, Record<string, string>>> = {};
const jobs: Partial<Record<Tongue, Promise<void>>> = {};

const loaders: Record<Tongue, () => Promise<{ default: Record<string, string> }>> = {
  tr: () => import("./packs/tr.ts"),
  en: () => import("./packs/en.ts"),
  ru: () => import("./packs/ru.ts"),
};

export function langReady(lang: Lang) {
  return lang === "az" || Boolean(packs[lang]);
}

export function loadLang(lang: Lang) {
  if (lang === "az" || packs[lang]) return Promise.resolve();
  if (!jobs[lang]) {
    jobs[lang] = loaders[lang]()
      .then((mod) => {
        packs[lang] = mod.default;
      })
      .catch((error) => {
        delete jobs[lang];
        throw error;
      });
  }
  return jobs[lang];
}

const RULES: Array<{ re: RegExp; tr: string; en: string; ru: string }> = [
  { re: /^YouTube-dan video endirmək olmur\. Yerli videonu birləşdirmək üçün: ffmpeg concat$/, tr: "YouTube'dan video indirilmez. Yerel videoyu birleştirmek için: ffmpeg concat", en: "YouTube downloads are not available. To join videos already on the phone: ffmpeg concat", ru: "Скачивание с YouTube недоступно. Склеить видео с телефона: ffmpeg concat" },
  { re: /^(\d+) video birləşdi · nibras\.webm$/, tr: "$1 video birleşti · nibras.webm", en: "$1 videos joined · nibras.webm", ru: "склеено видео: $1 · nibras.webm" },
  { re: /^iki video seç$/, tr: "iki video seç", en: "pick two videos", ru: "выберите два видео" },
  { re: /^video açılmadı$/, tr: "video açılmadı", en: "video did not open", ru: "видео не открылось" },
  { re: /^video birləşmədi$/, tr: "video birleşmedi", en: "could not join the videos", ru: "видео не склеилось" },
  { re: /^paket adı səhvdir$/, tr: "paket adı yanlış", en: "bad package name", ru: "неверное имя пакета" },
  { re: /^pip install paket$/, tr: "pip install paket", en: "pip install package", ru: "pip install пакет" },
  { re: /^paket yoxdur$/, tr: "paket yok", en: "no packages", ru: "пакетов нет" },
  { re: /^(.+) quraşdı$/, tr: "$1 kuruldu", en: "$1 installed", ru: "$1 установлен" },
  { re: /^quraşmadı$/, tr: "kurulmadı", en: "did not install", ru: "не установилось" },
  { re: /^bu brauzer qovluq açmır$/, tr: "bu tarayıcı klasör açmıyor", en: "this browser cannot open a folder", ru: "этот браузер не открывает папку" },
  { re: /^qovluq seçilmədi$/, tr: "klasör seçilmedi", en: "no folder selected", ru: "папка не выбрана" },
  { re: /^(\d+) fayl açıldı · (.+)$/, tr: "$1 dosya açıldı · $2", en: "$1 files opened · $2", ru: "открыто файлов: $1 · $2" },
  { re: /^host yoxdur$/, tr: "host yok", en: "no hosts", ru: "хостов нет" },
  { re: /^host yoxdur: (.+)$/, tr: "host yok: $1", en: "no host: $1", ru: "нет хоста: $1" },
  { re: /^snippet yoxdur$/, tr: "snippet yok", en: "no snippets", ru: "сниппетов нет" },
  { re: /^snippet yoxdur: (.+)$/, tr: "snippet yok: $1", en: "no snippet: $1", ru: "нет сниппета: $1" },
  { re: /^səs tanınmır$/, tr: "ses tanınmıyor", en: "speech was not recognized", ru: "речь не распознана" },
  { re: /^sensor yoxdur$/, tr: "sensör yok", en: "no sensor", ru: "нет датчика" },
  { re: /^ehtiyat səhvdir$/, tr: "yedek hatalı", en: "bad backup", ru: "плохая копия" },
  { re: /^fayl seçilmədi$/, tr: "dosya seçilmedi", en: "no file selected", ru: "файл не выбран" },
  { re: /^(\d+) fayl saxlandı · nibras-backup\.json$/, tr: "$1 dosya kaydedildi · nibras-backup.json", en: "$1 files saved · nibras-backup.json", ru: "сохранено файлов: $1 · nibras-backup.json" },
  { re: /^(\d+) fayl qayıtdı$/, tr: "$1 dosya geri geldi", en: "$1 files restored", ru: "восстановлено файлов: $1" },
  { re: /^Bəlkə: (\S+)$/, tr: "Belki: $1", en: "Maybe: $1", ru: "Возможно: $1" },
  { re: /^ifadə səhvdir$/, tr: "ifade yanlış", en: "bad expression", ru: "неверное выражение" },
  { re: /^(\S+) seçildi · (.+)$/, tr: "$1 seçildi · $2", en: "$1 selected · $2", ru: "$1 выбран · $2" },
  { re: /^(\S+) artıq seçilib$/, tr: "$1 zaten seçili", en: "$1 is already selected", ru: "$1 уже выбран" },
  { re: /^(\S+) hazırdır · (.+)$/, tr: "$1 hazır · $2", en: "$1 is ready · $2", ru: "$1 готов · $2" },
  { re: /^(\S+) silindi\.?$/, tr: "$1 silindi", en: "$1 removed", ru: "$1 удалён" },
  { re: /^tanınmayan əmr: (.+)$/, tr: "bilinmeyen komut: $1", en: "unknown command: $1", ru: "неизвестная команда: $1" },
  { re: /^fayl yoxdur: (.+)$/, tr: "dosya yok: $1", en: "no such file: $1", ru: "нет файла: $1" },
  { re: /^qovluq yoxdur: (.+)$/, tr: "klasör yok: $1", en: "no such folder: $1", ru: "нет папки: $1" },
  { re: /^kömək yoxdur: (.+)$/, tr: "yardım yok: $1", en: "no help for: $1", ru: "нет справки: $1" },
  { re: /^naməlum paket: (.+)$/, tr: "bilinmeyen paket: $1", en: "unknown package: $1", ru: "неизвестный пакет: $1" },
  { re: /^ləqəb yoxdur: (.+)$/, tr: "takma ad yok: $1", en: "no alias: $1", ru: "нет псевдонима: $1" },
  { re: /^yoxdur: (.+)$/, tr: "yok: $1", en: "not found: $1", ru: "нет: $1" },
  { re: /^mühit: (.+)$/, tr: "ortam: $1", en: "environment: $1", ru: "среда: $1" },
  { re: /^alətlər: (.+)$/, tr: "araçlar: $1", en: "tools: $1", ru: "инструменты: $1" },
  { re: /^qovluq: (.+)$/, tr: "klasör: $1", en: "folder: $1", ru: "папка: $1" },
  { re: /^(\d+) fayl hazırdır$/, tr: "$1 dosya hazır", en: "$1 files ready", ru: "готово файлов: $1" },
  { re: /^iş vaxtı: (\d+) gün (\d+) saat (\d+) dəq$/, tr: "çalışma: $1 gün $2 saat $3 dk", en: "up $1d $2h $3m", ru: "время: $1 д $2 ч $3 мин" },
  { re: /^(\S+): qovluq$/, tr: "$1: klasör", en: "$1: folder", ru: "$1: папка" },
  { re: /^(\S+): boş$/, tr: "$1: boş", en: "$1: empty", ru: "$1: пусто" },
  { re: /^(\S+): skript$/, tr: "$1: betik", en: "$1: script", ru: "$1: скрипт" },
  { re: /^(\S+): mətn$/, tr: "$1: metin", en: "$1: text", ru: "$1: текст" },
  { re: /^(\S+): ikili$/, tr: "$1: ikili", en: "$1: binary", ru: "$1: двоичный" },
  { re: /^(\S+) əmridir$/, tr: "$1 komuttur", en: "$1 is a command", ru: "$1 — команда" },
  { re: /^qovluq boş deyil: (.+)$/, tr: "klasör boş değil: $1", en: "folder is not empty: $1", ru: "папка не пуста: $1" },
  { re: /^qaytarıldı: (.+)$/, tr: "geri alındı: $1", en: "restored: $1", ru: "восстановлено: $1" },
  { re: /^zibil boşdur$/, tr: "çöp boş", en: "trash is empty", ru: "корзина пуста" },
  { re: /^fərqlidir$/, tr: "farklı", en: "differ", ru: "различаются" },
  { re: /^çox dərin$/, tr: "çok derin", en: "too deep", ru: "слишком глубоко" },
  { re: /^arxiv çox böyükdür$/, tr: "arşiv çok büyük", en: "archive is too big", ru: "архив слишком большой" },
  { re: /^arxiv təhlükəlidir$/, tr: "arşiv güvensiz", en: "archive is unsafe", ru: "архив небезопасен" },
  { re: /^arxiv səhvdir$/, tr: "arşiv bozuk", en: "archive is invalid", ru: "архив повреждён" },
  { re: /^çox hissə$/, tr: "çok parça", en: "too many pieces", ru: "слишком много частей" },
  { re: /^(\d+) fayl çıxdı$/, tr: "$1 dosya çıktı", en: "$1 files extracted", ru: "извлечено файлов: $1" },
  { re: /^(\d+) ms$/, tr: "$1 ms", en: "$1 ms", ru: "$1 мс" },
  { re: /^qovluq doludur: (.+)$/, tr: "klasör dolu: $1", en: "folder is not empty: $1", ru: "папка не пуста: $1" },
  { re: /^budaq yoxdur: (.+)$/, tr: "dal yok: $1", en: "no branch: $1", ru: "нет ветки: $1" },
  { re: /^konflikt: (.+)$/, tr: "çakışma: $1", en: "conflict: $1", ru: "конфликт: $1" },
  { re: /^bu ünvan bağlıdır$/, tr: "bu adres kapalı", en: "this address is blocked", ru: "этот адрес закрыт" },
  { re: /^SSH şifrəsi silindi\.$/, tr: "SSH parolası silindi", en: "SSH password removed", ru: "пароль SSH удалён" },
  { re: /^(\d+) fayl yeniləndi$/, tr: "$1 dosya güncellendi", en: "$1 files updated", ru: "обновлено файлов: $1" },
  { re: /^(\S+) yaradıldı$/, tr: "$1 oluşturuldu", en: "$1 created", ru: "$1 создана" },
  { re: /^fərq yoxdur$/, tr: "fark yok", en: "no difference", ru: "нет отличий" },
  { re: /^artıq bu budaqdasan$/, tr: "zaten bu daldasın", en: "already on this branch", ru: "уже на этой ветке" },
  { re: /^ad tapılmadı$/, tr: "ad bulunamadı", en: "name not found", ru: "имя не найдено" },
  { re: /^https açılmadı$/, tr: "https açılmadı", en: "https did not open", ru: "https не открылся" },
  { re: /^bağlanmadı$/, tr: "bağlanamadı", en: "could not connect", ru: "не подключилось" },
  { re: /^vaxt bitdi$/, tr: "süre doldu", en: "timed out", ru: "время вышло" },
  { re: /^şifrə səhvdir$/, tr: "parola yanlış", en: "wrong password", ru: "неверный пароль" },
  { re: /^şifrə saxlandı$/, tr: "parola kaydedildi", en: "password saved", ru: "пароль сохранён" },
  { re: /^şifrə qısadır$/, tr: "parola kısa", en: "password is short", ru: "пароль короткий" },
  { re: /^istifadəçi yoxdur: (.+)$/, tr: "kullanıcı yok: $1", en: "no such user: $1", ru: "нет пользователя: $1" },
  { re: /^keçid deyil: (.+)$/, tr: "bağ değil: $1", en: "not a link: $1", ru: "не ссылка: $1" },
  { re: /^əvvəl Ubuntu və ya Debian seç$/, tr: "önce Ubuntu veya Debian seç", en: "choose Ubuntu or Debian first", ru: "сначала выбери Ubuntu или Debian" },
  { re: /^dinləyən port yoxdur$/, tr: "dinleyen port yok", en: "nothing is listening", ru: "нет слушающего порта" },
  { re: /^iş yoxdur$/, tr: "iş yok", en: "no jobs", ru: "нет задач" },
  { re: /^ən çoxu 3 saniyə$/, tr: "en fazla 3 saniye", en: "3 seconds at most", ru: "не больше 3 секунд" },
  { re: /^paket yoxdur$/, tr: "paket yok", en: "no packages", ru: "нет пакетов" },
  { re: /^açarlar yeniləndi$/, tr: "tuşlar yenilendi", en: "keys reloaded", ru: "клавиши обновлены" },
  { re: /^lövhəyə yazıldı$/, tr: "panoya yazıldı", en: "copied to the clipboard", ru: "скопировано в буфер" },
  { re: /^lövhə oxunmadı$/, tr: "pano okunamadı", en: "clipboard could not be read", ru: "буфер не прочитан" },
  { re: /^batareya oxunmur$/, tr: "pil okunamıyor", en: "battery cannot be read", ru: "батарея не читается" },
  { re: /^səs yoxdur$/, tr: "ses yok", en: "no speech", ru: "нет речи" },
  { re: /^bildiriş bağlıdır$/, tr: "bildirim kapalı", en: "notifications are blocked", ru: "уведомления закрыты" },
  { re: /^nibras-distro yaz$/, tr: "nibras-distro yaz", en: "use nibras-distro", ru: "пиши nibras-distro" },
  { re: /^(nibras-\S+) yaz$/, tr: "$1 yaz", en: "use $1", ru: "пиши $1" },
  { re: /^(.+) brauzerdə hazırdır\. Kök arxiv yalnız APK-da yüklənir\.$/, tr: "$1 tarayıcıda hazır. Kök arşiv yalnız APK'da indirilir.", en: "$1 is ready in the browser. The root archive is downloaded only in the APK.", ru: "$1 готов в браузере. Корневой архив скачивается только в APK." },
  { re: /^(.+) brauzerdə hazırdır\. Yükləmə yoxdur\.$/, tr: "$1 tarayıcıda hazır. İndirme yok.", en: "$1 is ready in the browser. Nothing to download.", ru: "$1 готов в браузере. Скачивание не нужно." },
  { re: /^stək boşdur$/, tr: "yığın boş", en: "the stack is empty", ru: "стек пуст" },
  { re: /^keçmiş boşdur$/, tr: "geçmiş boş", en: "history is empty", ru: "история пуста" },
  { re: /^keçmişdə yoxdur$/, tr: "geçmişte yok", en: "not in history", ru: "нет в истории" },
  { re: /^watch dayandı$/, tr: "watch durdu", en: "watch stopped", ru: "watch остановлен" },
  { re: /^adb bu ünvanda yoxdur\. Kabel və Android Studio lazımdır$/, tr: "adb bu adreste yok. Kablo ve Android Studio gerekir", en: "adb is not on this site. A cable and Android Studio are required", ru: "adb здесь нет. Нужны кабель и Android Studio" },
  { re: /^oyaqlıq yoxdur$/, tr: "uyanık tutma yok", en: "no wake lock", ru: "нет блокировки сна" },
  { re: /^şəbəkə oxunmur$/, tr: "ağ okunamıyor", en: "the network cannot be read", ru: "сеть не читается" },
  { re: /^paylaşıldı$/, tr: "paylaşıldı", en: "shared", ru: "отправлено" },
  { re: /^ekran oyaqdır$/, tr: "ekran uyanık", en: "the screen stays awake", ru: "экран не гаснет" },
  { re: /^ekran buraxıldı$/, tr: "ekran bırakıldı", en: "the screen can sleep", ru: "экран может гаснуть" },
];

const MONTHS = ["yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avqust", "sentyabr", "oktyabr", "noyabr", "dekabr"];

function fill(template: string, match: RegExpMatchArray) {
  return template.replace(/\$(\d)/g, (_, index) => match[Number(index)] ?? "");
}

function translateLine(lang: Tongue, line: string) {
  const pack = packs[lang];
  if (!pack) return line;
  if (pack[line]) return pack[line];
  for (const rule of RULES) {
    const match = rule.re.exec(line);
    if (match) return fill(rule[lang], match);
  }
  let next = line.replace(" · boş ", lang === "en" ? " · free " : lang === "ru" ? " · свободно " : " · boş ");
  for (const name of MONTHS) {
    const word = pack[`__m:${name}`];
    if (word) next = next.replaceAll(name, word);
  }
  return next;
}

export function say(lang: Lang, text: string) {
  if (lang === "az" || !text || !packs[lang]) return text;
  const pack = packs[lang];
  if (pack[text]) return pack[text];
  return text.split("\n").map((line) => translateLine(lang, line)).join("\n");
}

export function isLang(value: string | null): value is Lang {
  return value === "az" || value === "tr" || value === "en" || value === "ru";
}

export function linesLabel(lang: Lang, count: number) {
  if (lang === "tr" && packs.tr) return `${count} satır`;
  if (lang === "en" && packs.en) return `${count} ${count === 1 ? "line" : "lines"}`;
  if (lang === "ru" && packs.ru) {
    const mod = count % 10;
    const teen = count % 100;
    const word = mod === 1 && teen !== 11 ? "строка" : mod >= 2 && mod <= 4 && (teen < 12 || teen > 14) ? "строки" : "строк";
    return `${count} ${word}`;
  }
  return `${count} sətir`;
}

export function deleteAsk(lang: Lang, name: string) {
  if (lang === "tr" && packs.tr) return `${name} silinsin mi?`;
  if (lang === "en" && packs.en) return `Delete ${name}?`;
  if (lang === "ru" && packs.ru) return `Удалить ${name}?`;
  return `${name} silinsin?`;
}

export function renameLabel(lang: Lang) {
  if (lang === "en" && packs.en) return "Rename";
  if (lang === "ru" && packs.ru) return "Имя";
  if (lang === "tr" && packs.tr) return "Adlandır";
  return "Ad";
}

export function clipText(text: string) {
  if (text.length <= 8000) return text;
  const cut = text.lastIndexOf("\n", 8000);
  return `${text.slice(0, cut > 200 ? cut : 8000)}\n… kəsildi`;
}
