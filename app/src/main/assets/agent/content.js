// Runs in every top-level page. The app sends {id, cmd, args} over a native port and gets {id, result} back.
(() => {
  if (window.top !== window) return;

  const MAX_CHARS = 30000;
  const TRUNCATED = "[잘림]";
  const SELECTOR =
    "a[href],button,input:not([type=hidden]),textarea,select," +
    "[role=button],[role=link],[role=tab],[role=checkbox],[role=menuitem],[onclick]";

  let refs = new Map();
  let highlighted = null;
  let previousOutline = "";

  function visible(el) {
    const rect = el.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    const style = getComputedStyle(el);
    return style.visibility !== "hidden" && style.display !== "none";
  }

  function kindOf(el) {
    const tag = el.tagName.toLowerCase();
    const type = (el.getAttribute("type") || "").toLowerCase();
    const role = el.getAttribute("role");
    if (tag === "input") {
      if (type === "password") return "password";
      if (type === "checkbox" || type === "radio") return "checkbox";
      if (["button", "submit", "reset", "image"].includes(type)) return "button";
      return "textbox";
    }
    if (tag === "textarea") return "textbox";
    if (tag === "select") return "select";
    if (["link", "tab", "checkbox", "menuitem", "button"].includes(role)) return role;
    if (tag === "a") return "link";
    return "button";
  }

  function clean(text) {
    return (text || "").replace(/\s+/g, " ").trim();
  }

  function labelOf(el) {
    const kind = kindOf(el);
    const candidates = [
      el.getAttribute("aria-label"),
      el.labels && el.labels[0] ? el.labels[0].innerText : "",
      el.getAttribute("placeholder"),
      kind === "textbox" || kind === "password" || kind === "select" ? "" : el.innerText,
      kind === "button" && el.tagName.toLowerCase() === "input" ? el.value : "",
      el.getAttribute("title"),
      el.getAttribute("alt"),
      el.querySelector && el.querySelector("img[alt]") ? el.querySelector("img[alt]").getAttribute("alt") : "",
      el.getAttribute("name"),
    ];
    for (const candidate of candidates) {
      const text = clean(candidate);
      if (text) return text.slice(0, 80).replace(/"/g, "'");
    }
    return "";
  }

  function describe(ref, el) {
    const kind = kindOf(el);
    let line = `[${ref}] ${kind} "${labelOf(el)}"`;
    if (kind === "password") line += ' value=""'; // never expose a password
    else if (kind === "textbox") line += ` value="${clean(el.value).slice(0, 100).replace(/"/g, "'")}"`;
    else if (kind === "checkbox") line += ` checked=${el.checked === true || el.getAttribute("aria-checked") === "true"}`;
    else if (kind === "select" && el.selectedOptions && el.selectedOptions[0]) {
      line += ` value="${clean(el.selectedOptions[0].text).slice(0, 100).replace(/"/g, "'")}"`;
    }
    return line;
  }

  function snapshot() {
    refs = new Map();
    const lines = [];
    let hasPasswordField = false;
    let ref = 0;
    for (const el of document.querySelectorAll(SELECTOR)) {
      if (!visible(el)) continue;
      ref += 1;
      refs.set(ref, el);
      if (kindOf(el) === "password") hasPasswordField = true;
      lines.push(describe(ref, el));
    }
    const body = document.body ? document.body.innerText : "";
    const bodyText = body.replace(/[ \t ]+/g, " ").replace(/\n\s*\n\s*\n+/g, "\n\n").trim();
    let text = lines.join("\n") + "\n--- 본문 ---\n" + bodyText;
    if (text.length > MAX_CHARS) text = text.slice(0, MAX_CHARS) + TRUNCATED;
    return { url: location.href, title: document.title, text, hasPasswordField };
  }

  function find(ref) {
    const el = refs.get(ref);
    return el && el.isConnected ? el : null;
  }

  function missing(ref) {
    return { error: `요소 [${ref}]을 찾을 수 없습니다. 페이지를 다시 읽으세요.` };
  }

  function info(args) {
    const el = find(args.ref);
    return el ? { label: labelOf(el), kind: kindOf(el) } : missing(args.ref);
  }

  function click(args) {
    const el = find(args.ref);
    if (!el) return missing(args.ref);
    el.scrollIntoView({ block: "center" });
    el.click();
    return { ok: `[${args.ref}] 요소를 눌렀습니다.` };
  }

  function type(args) {
    const el = find(args.ref);
    if (!el) return missing(args.ref);
    const kind = kindOf(el);
    if (kind === "password") return { error: "비밀번호는 사용자가 직접 입력해야 합니다." };
    if (kind !== "textbox" && kind !== "select") return { error: `[${args.ref}] 요소는 입력창이 아닙니다.` };
    el.scrollIntoView({ block: "center" });
    el.focus();
    el.value = args.text; // the content script sees the native setter, so page frameworks notice the events below
    el.dispatchEvent(new InputEvent("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
    if (args.submit) {
      const key = { key: "Enter", code: "Enter", keyCode: 13, which: 13, bubbles: true, cancelable: true };
      const proceed = el.dispatchEvent(new KeyboardEvent("keydown", key));
      el.dispatchEvent(new KeyboardEvent("keyup", key));
      if (proceed && el.form) {
        if (el.form.requestSubmit) el.form.requestSubmit();
        else el.form.submit();
      }
    }
    return { ok: `[${args.ref}] 입력창에 입력했습니다.` + (args.submit ? " 제출했습니다." : "") };
  }

  function scroll(args) {
    const amount = Math.round(window.innerHeight * 0.8) * (args.direction === "up" ? -1 : 1);
    window.scrollBy(0, amount);
    const height = document.documentElement ? document.documentElement.scrollHeight : 0;
    return { ok: `스크롤했습니다. 현재 위치 ${Math.round(window.scrollY)} / 전체 높이 ${height}` };
  }

  function highlight(args) {
    if (highlighted && highlighted.isConnected) highlighted.style.outline = previousOutline;
    highlighted = null;
    const el = args.ref == null ? null : find(args.ref);
    if (el) {
      highlighted = el;
      previousOutline = el.style.outline;
      el.style.outline = "2px solid #ff7a00";
    }
    return { ok: "" };
  }

  const commands = { snapshot, info, click, type, scroll, highlight };

  const port = browser.runtime.connectNative("browser");
  port.onMessage.addListener((message) => {
    let result;
    try {
      const command = commands[message.cmd];
      result = command ? command(message.args || {}) : { error: `알 수 없는 명령: ${message.cmd}` };
    } catch (e) {
      result = { error: `페이지에서 오류가 났습니다: ${e}` };
    }
    port.postMessage({ id: message.id, result });
  });
})();
