/**
 * JNative Music — thin Audio + DOM bridge (charter §24).
 * Owns: HTMLAudioElement, DOM event→REST fill, bridge attrs for WASM PlayerStore.
 * Does not own: playlist business rules beyond rendering server JSON.
 */
(function () {
  const state = {
    csrf: "",
    user: null,
    queue: [],
    index: 0,
    repeat: 0, // 0 seq 1 all 2 one 3 shuffle
    route: "home",
    searchTimer: 0,
    searchGen: 0,
  };

  const audio = new Audio();
  audio.preload = "metadata";

  function $(id) { return document.getElementById(id); }
  function bridge() { return $("jn-bridge"); }
  function setAttr(name, value) {
    const el = bridge();
    if (el) el.setAttribute(name, value == null ? "" : String(value));
  }

  function fmt(t) {
    t = Math.max(0, Math.floor(t || 0));
    const m = Math.floor(t / 60);
    const s = t % 60;
    return m + ":" + String(s).padStart(2, "0");
  }

  async function api(method, url, body) {
    const headers = { "Content-Type": "application/json" };
    if (state.csrf && method !== "GET") headers["X-CSRF-Token"] = state.csrf;
    const res = await fetch(url, {
      method,
      credentials: "same-origin",
      headers,
      body: body ? JSON.stringify(body) : undefined,
    });
    const text = await res.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch (e) { json = null; }
    return { ok: res.ok, status: res.status, json, text };
  }

  function showRoute(name) {
    state.route = name;
    ["home", "search", "charts", "mymusic", "login", "album", "artist", "playlist", "lyrics"]
      .forEach((r) => {
        const el = $("view-" + r);
        if (el) el.classList.toggle("hidden", r !== name);
      });
    document.querySelectorAll("#main-nav button").forEach((b) => {
      b.classList.toggle("active", b.getAttribute("data-route") === name);
    });
  }

  function trackRow(t, extra) {
    const tr = document.createElement("tr");
    const play = document.createElement("button");
    play.className = "play";
    play.type = "button";
    play.textContent = "▶";
    play.addEventListener("click", () => playTrack(t, state.queue));
    const td0 = document.createElement("td"); td0.appendChild(play);
    const td1 = document.createElement("td"); td1.textContent = t.title || "";
    const td2 = document.createElement("td"); td2.textContent = extra || t.artistName || "";
    tr.appendChild(td0); tr.appendChild(td1); tr.appendChild(td2);
    if (t.albumTitle) {
      const td3 = document.createElement("td"); td3.textContent = t.albumTitle;
      tr.appendChild(td3);
    }
    return tr;
  }

  function fillTracks(tbodyId, tracks, extraField) {
    const tb = $(tbodyId);
    if (!tb) return;
    tb.innerHTML = "";
    (tracks || []).forEach((t) => tb.appendChild(trackRow(t, extraField ? t[extraField] : null)));
  }

  function playTrack(track, queue) {
    if (queue && queue.length) state.queue = queue.slice();
    else state.queue = [track];
    state.index = Math.max(0, state.queue.findIndex((x) => x.id === track.id));
    if (state.index < 0) state.index = 0;
    const t = state.queue[state.index];
    $("player-title").textContent = t.title || "Track";
    $("player-sub").textContent = (t.artistName || "") + " · " + (t.albumTitle || "");
    audio.src = t.mediaUrl;
    audio.play().catch(() => {});
    setAttr("data-playing", "1");
    api("POST", "/api/music/recordPlay", { trackId: String(t.id) });
    loadLyrics(t.id);
    syncPlayerStore();
  }

  function syncPlayerStore() {
    const t = state.queue[state.index];
    setAttr("data-track-id", t ? String(t.id) : "");
    setAttr("data-repeat", String(state.repeat));
    const store = $("boot-data");
    if (store) {
      store.textContent = JSON.stringify({
        currentTrack: t || null,
        queue: state.queue,
        index: state.index,
        playing: !audio.paused,
        currentTime: audio.currentTime || 0,
        duration: audio.duration || 0,
        volume: audio.volume,
        repeatMode: state.repeat,
      });
    }
  }

  async function loadLyrics(id) {
    const res = await api("GET", "/api/music/lyrics?trackId=" + id);
    const box = $("lyrics-lines");
    if (!box) return;
    box.innerHTML = "";
    (res.json || []).forEach((line) => {
      const d = document.createElement("div");
      d.className = "line";
      d.dataset.start = String(line.startMs || 0);
      d.textContent = line.text || "";
      box.appendChild(d);
    });
  }

  function highlightLyrics() {
    const ms = Math.floor((audio.currentTime || 0) * 1000);
    document.querySelectorAll("#lyrics-lines .line").forEach((el) => {
      const start = parseInt(el.dataset.start || "0", 10);
      el.classList.toggle("active", ms >= start);
    });
  }

  function nextTrack(force) {
    if (!state.queue.length) return;
    if (state.repeat === 2 && !force) {
      audio.currentTime = 0;
      audio.play();
      return;
    }
    if (state.repeat === 3) {
      state.index = Math.floor(Math.random() * state.queue.length);
    } else {
      state.index += 1;
      if (state.index >= state.queue.length) {
        if (state.repeat === 1) state.index = 0;
        else { state.index = state.queue.length - 1; audio.pause(); return; }
      }
    }
    playTrack(state.queue[state.index], state.queue);
  }

  async function loadHome() {
    const res = await api("GET", "/api/music/getHome");
    if (!res.ok) return;
    const home = res.json || {};
    fillTracks("home-tracks", home.recommended || []);
    state.queue = home.recommended || [];
    const cards = $("home-albums");
    cards.innerHTML = "";
    (home.newAlbums || []).forEach((a) => {
      const c = document.createElement("div");
      c.className = "card";
      c.innerHTML = "<div class='t'></div><div class='s'></div>";
      c.querySelector(".t").textContent = a.title;
      c.querySelector(".s").textContent = a.artistName;
      c.addEventListener("click", () => openAlbum(a.id));
      cards.appendChild(c);
    });
  }

  async function openAlbum(id) {
    const res = await api("GET", "/api/music/getAlbum?id=" + id);
    if (!res.ok) return;
    showRoute("album");
    $("album-title").textContent = (res.json.album && res.json.album.title) || "Album";
    fillTracks("album-tracks", res.json.tracks || []);
    state.queue = res.json.tracks || [];
  }

  async function openArtist(id) {
    const res = await api("GET", "/api/music/getArtist?id=" + id);
    if (!res.ok) return;
    showRoute("artist");
    $("artist-title").textContent = (res.json.artist && res.json.artist.name) || "Artist";
    fillTracks("artist-tracks", res.json.tracks || []);
    const cards = $("artist-albums");
    cards.innerHTML = "";
    (res.json.albums || []).forEach((a) => {
      const c = document.createElement("div");
      c.className = "card";
      c.textContent = a.title;
      c.addEventListener("click", () => openAlbum(a.id));
      cards.appendChild(c);
    });
  }

  async function openPlaylist(id) {
    const meta = await api("GET", "/api/music/getPlaylist?id=" + id);
    const tracks = await api("GET", "/api/music/playlistTracks?id=" + id);
    showRoute("playlist");
    $("playlist-title").textContent = (meta.json && meta.json.title) || "Playlist";
    fillTracks("playlist-tracks", tracks.json || []);
    state.queue = tracks.json || [];
  }

  async function loadCharts() {
    const res = await api("GET", "/api/music/charts");
    fillTracks("charts-tracks", res.json || [], "playCount");
  }

  async function loadMyMusic() {
    const fav = await api("GET", "/api/music/myFavorites");
    const hist = await api("GET", "/api/music/recentlyPlayed");
    const pls = await api("GET", "/api/music/myPlaylists");
    fillTracks("fav-tracks", fav.json || []);
    fillTracks("hist-tracks", hist.json || []);
    const box = $("my-playlists");
    box.innerHTML = "";
    (pls.json || []).forEach((p) => {
      const c = document.createElement("div");
      c.className = "card";
      c.innerHTML = "<div class='t'></div><div class='s'></div>";
      c.querySelector(".t").textContent = p.title;
      c.querySelector(".s").textContent = p.trackCount + " tracks";
      c.addEventListener("click", () => openPlaylist(p.id));
      box.appendChild(c);
    });
  }

  async function doSearch(q) {
    const gen = ++state.searchGen;
    $("search-status").textContent = "Searching…";
    const res = await api("GET", "/api/music/search?q=" + encodeURIComponent(q));
    if (gen !== state.searchGen) return;
    if (!res.ok) {
      $("search-status").textContent = "Search failed";
      return;
    }
    $("search-status").textContent = "";
    fillTracks("search-tracks", (res.json && res.json.tracks) || []);
  }

  async function refreshUser() {
    const res = await api("GET", "/api/auth/currentUser");
    if (res.ok && res.json && res.json.user) {
      state.user = res.json.user;
      state.csrf = res.json.csrfToken || "";
      $("user-label").textContent = state.user.displayName || state.user.email;
    } else {
      state.user = null;
      $("user-label").textContent = "Guest";
    }
  }

  function bind() {
    document.querySelectorAll("#main-nav button").forEach((b) => {
      b.addEventListener("click", () => {
        const r = b.getAttribute("data-route");
        showRoute(r);
        if (r === "home") loadHome();
        if (r === "charts") loadCharts();
        if (r === "mymusic") loadMyMusic();
      });
    });
    $("search-input").addEventListener("input", (e) => {
      const q = e.target.value.trim();
      clearTimeout(state.searchTimer);
      state.searchTimer = setTimeout(() => {
        showRoute("search");
        doSearch(q);
      }, 250);
    });
    $("btn-login").addEventListener("click", async () => {
      const email = $("login-email").value;
      const password = $("login-password").value;
      const res = await api("POST", "/api/auth/login", { email, password });
      if (!res.ok) {
        $("login-error").textContent = (res.json && res.json.message) || "login failed";
        return;
      }
      state.csrf = res.json.csrfToken || "";
      state.user = res.json.user;
      $("user-label").textContent = state.user.displayName;
      $("login-error").textContent = "";
      showRoute("home");
      loadHome();
    });
    $("btn-play").addEventListener("click", () => {
      if (audio.paused) {
        if (!audio.src && state.queue[0]) playTrack(state.queue[0], state.queue);
        else audio.play();
      } else audio.pause();
    });
    $("btn-next").addEventListener("click", () => nextTrack(true));
    $("btn-prev").addEventListener("click", () => {
      if (audio.currentTime > 3) audio.currentTime = 0;
      else {
        state.index = Math.max(0, state.index - 1);
        if (state.queue[state.index]) playTrack(state.queue[state.index], state.queue);
      }
    });
    $("btn-repeat").addEventListener("click", () => {
      state.repeat = (state.repeat + 1) % 4;
      $("btn-repeat").textContent = ["→", "🔁", "🔂", "🔀"][state.repeat];
      syncPlayerStore();
    });
    $("seek").addEventListener("input", (e) => {
      const d = audio.duration || 0;
      if (d > 0) audio.currentTime = (parseInt(e.target.value, 10) / 1000) * d;
    });
    $("volume").addEventListener("input", (e) => {
      audio.volume = parseInt(e.target.value, 10) / 100;
      syncPlayerStore();
    });
    audio.addEventListener("timeupdate", () => {
      const d = audio.duration || 0;
      if (d > 0) $("seek").value = String(Math.floor((audio.currentTime / d) * 1000));
      $("player-time").textContent = fmt(audio.currentTime) + " / " + fmt(d);
      setAttr("data-time", String(audio.currentTime || 0));
      setAttr("data-duration", String(d));
      highlightLyrics();
      syncPlayerStore();
    });
    audio.addEventListener("play", () => { $("btn-play").textContent = "❚❚"; setAttr("data-playing", "1"); });
    audio.addEventListener("pause", () => { $("btn-play").textContent = "▶"; setAttr("data-playing", "0"); });
    audio.addEventListener("ended", () => nextTrack(false));

    // WASM PlayerStore command channel
    const obs = new MutationObserver((muts) => {
      for (const m of muts) {
        if (m.attributeName === "data-cmd") {
          const raw = bridge().getAttribute("data-cmd");
          if (!raw) continue;
          const parts = raw.split("|");
          if (parts[0] === "play" && parts[1]) {
            playTrack({ id: 0, title: "Track", mediaUrl: parts[1], artistName: "", albumTitle: "" }, []);
          } else if (parts[0] === "pause") audio.pause();
          else if (parts[0] === "seek") audio.currentTime = parseFloat(parts[1] || "0");
          else if (parts[0] === "volume") audio.volume = parseFloat(parts[1] || "1");
          setAttr("data-cmd", "");
        }
      }
    });
    obs.observe(bridge(), { attributes: true });
  }

  document.addEventListener("DOMContentLoaded", async () => {
    bind();
    audio.volume = 0.8;
    await refreshUser();
    await loadHome();
    // Mount WASM PlayerStore shell (optional; UI already interactive via DOM bridge)
    try {
      const mod = await import("/wasm/app.js");
      if (mod && mod.jnativeStart) await mod.jnativeStart("/wasm/app.wasm");
    } catch (e) {
      console.warn("WASM shell not loaded yet:", e.message || e);
    }
  });
})();
