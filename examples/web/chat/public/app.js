// Author: 张晓达
let ws = null;

async function api(path, method, body) {
  const opts = { method, headers: {}, credentials: "include" };
  if (body) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const text = await res.text();
  return { ok: res.ok, status: res.status, text };
}

function appendMsg(line) {
  const el = document.createElement("div");
  el.textContent = line;
  document.getElementById("messages").appendChild(el);
}

async function refreshMe() {
  const r = await api("/api/me", "GET");
  document.getElementById("me").textContent = r.ok ? r.text : "not logged in";
  if (r.ok && !ws) {
    connectWs();
    loadHistory();
  }
}

async function loadHistory() {
  const r = await api("/api/rooms/1/messages", "GET");
  if (!r.ok) return;
  const arr = JSON.parse(r.text);
  arr.forEach((m) => appendMsg(m.displayName + ": " + m.content));
}

function connectWs() {
  ws = new WebSocket((location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws/chat");
  ws.onmessage = (e) => appendMsg(e.data);
  ws.onclose = () => { ws = null; };
}

document.getElementById("register").onclick = async () => {
  await api("/api/register", "POST", {
    email: email.value,
    displayName: displayName.value,
    password: password.value
  });
  await refreshMe();
};

document.getElementById("login").onclick = async () => {
  await api("/api/login", "POST", {
    email: email.value,
    password: password.value
  });
  await refreshMe();
};

document.getElementById("logout").onclick = async () => {
  await api("/api/logout", "POST");
  if (ws) ws.close();
  await refreshMe();
};

document.getElementById("send").onclick = () => {
  if (!ws || ws.readyState !== 1) return;
  let name = "anon";
  let userId = "1";
  try {
    const me = JSON.parse(document.getElementById("me").textContent);
    if (me.displayName) name = me.displayName;
    if (me.userId) userId = String(me.userId);
  } catch (e) {}
  ws.send(JSON.stringify({ type: "CHAT_MESSAGE", content: content.value, displayName: name, userId: userId }));
  content.value = "";
};

refreshMe();
