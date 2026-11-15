// Runs in every top-level page. The app sends {id, cmd, args} over a native port and gets {id, result} back.
(() => {
  if (window.top !== window) return;

  const MAX_CHARS = 30000;
  const TRUNCATED = "[truncated]";
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
    let text = lines.join("\n") + "\n--- page text ---\n" + bodyText;
    if (text.length > MAX_CHARS) text = text.slice(0, MAX_CHARS) + TRUNCATED;
    return { url: location.href, title: document.title, text, hasPasswordField };
  }

  function find(ref) {
    const el = refs.get(ref);
    return el && el.isConnected ? el : null;
  }

  function missing(ref) {
    return { error: `Element [${ref}] was not found. Read the page again.` };
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
    return { ok: `Clicked element [${args.ref}].` };
  }

  function type(args) {
    const el = find(args.ref);
    if (!el) return missing(args.ref);
    const kind = kindOf(el);
    if (kind === "password") return { error: "Passwords must be typed by the user themselves." };
    if (kind !== "textbox" && kind !== "select") return { error: `Element [${args.ref}] is not an input.` };
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
    return { ok: `Typed into input [${args.ref}].` + (args.submit ? " Submitted." : "") };
  }

  function scroll(args) {
    const amount = Math.round(window.innerHeight * 0.8) * (args.direction === "up" ? -1 : 1);
    window.scrollBy(0, amount);
    const height = document.documentElement ? document.documentElement.scrollHeight : 0;
    return { ok: `Scrolled. Position ${Math.round(window.scrollY)} of page height ${height}.` };
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
      result = command ? command(message.args || {}) : { error: `Unknown command: ${message.cmd}` };
    } catch (e) {
      result = { error: `The page raised an error: ${e}` };
    }
    port.postMessage({ id: message.id, result });
  });
})();
