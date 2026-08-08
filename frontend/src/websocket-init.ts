// @ts-nocheck
(function() {
  console.log("=== WS BOOTSTRAP ===");
  
  // Global AudioContext — resumed on first user click
  var audioCtx = null;
  
  // Create AudioContext on first user interaction
  document.addEventListener("click", function() {
    if (!audioCtx) {
      try {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        console.log("[WS] AudioContext created, state:", audioCtx.state);
      } catch(e) { console.warn("[WS] AudioContext error:", e); }
    }
    if (audioCtx && audioCtx.state === "suspended") {
      audioCtx.resume().then(function() {
        console.log("[WS] AudioContext resumed");
      });
    }
  });
  
  // Tab notification
  var alertCount = 0;
  var originalTitle = document.title;
  
  document.addEventListener("visibilitychange", function() {
    if (!document.hidden) {
      alertCount = 0;
      document.title = originalTitle;
    }
  });
  
  // Inject pulse animation CSS
  var styleEl = document.createElement("style");
  styleEl.textContent = "@keyframes bnaPulse { 0%,100% { transform: scale(1); } 50% { transform: scale(1.08); filter: drop-shadow(0 0 8px rgba(26,140,78,0.6)); } }";
  document.head.appendChild(styleEl);

  function notifyTab() {
    if (document.hidden) {
      alertCount++;
      document.title = "🔔 BNA-FLUX (" + alertCount + ")";
    }
  }
  
  function playSound(niveau) { console.log("[WS] playSound called, niveau=" + niveau + ", audioCtx=" + (audioCtx ? audioCtx.state : "null"));
    try {
      if (!audioCtx) {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      }
      if (audioCtx.state === "suspended") {
        audioCtx.resume();
      }
      var ctx = audioCtx;
      
      if (niveau === "CRITIQUE") {
        var o1 = ctx.createOscillator(); var g1 = ctx.createGain();
        o1.type = "square"; o1.frequency.value = 880; g1.gain.value = 0.15;
        o1.connect(g1); g1.connect(ctx.destination);
        o1.start(); g1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15); o1.stop(ctx.currentTime + 0.15);
        setTimeout(function(){ var o2=ctx.createOscillator(); var g2=ctx.createGain(); o2.type="square"; o2.frequency.value=660; g2.gain.value=0.15; o2.connect(g2); g2.connect(ctx.destination); o2.start(); g2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime+0.2); o2.stop(ctx.currentTime+0.2); }, 150);
        setTimeout(function(){ var o3=ctx.createOscillator(); var g3=ctx.createGain(); o3.type="square"; o3.frequency.value=880; g3.gain.value=0.15; o3.connect(g3); g3.connect(ctx.destination); o3.start(); g3.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime+0.15); o3.stop(ctx.currentTime+0.15); }, 300);
      } else if (niveau === "MOYEN") {
        var o1 = ctx.createOscillator(); var g1 = ctx.createGain();
        o1.type = "sine"; o1.frequency.value = 440; g1.gain.value = 0.08;
        o1.connect(g1); g1.connect(ctx.destination);
        o1.start(); g1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15); o1.stop(ctx.currentTime + 0.15);
      } else if (niveau === "ELEVE") {
        var o1 = ctx.createOscillator(); var g1 = ctx.createGain();
        o1.type = "sine"; o1.frequency.value = 660; g1.gain.value = 0.12;
        o1.connect(g1); g1.connect(ctx.destination);
        o1.start(); g1.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.2); o1.stop(ctx.currentTime + 0.2);
        setTimeout(function(){ var o2=ctx.createOscillator(); var g2=ctx.createGain(); o2.type="sine"; o2.frequency.value=880; g2.gain.value=0.12; o2.connect(g2); g2.connect(ctx.destination); o2.start(); g2.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime+0.3); o2.stop(ctx.currentTime+0.3); }, 200);
      }
    } catch(e) { console.warn("[WS] Sound error:", e); }
  }
  
  function c() {
    var t = localStorage.getItem("bna_token_acces");
    if (!t) { setTimeout(c, 2000); return; }
    console.log("[WS] Connecting...");
    var w = new WebSocket("ws://localhost/ws/websocket");
    w.onopen = function() {
      console.log("[WS] Connected");
      w.send("CONNECT\nAuthorization:Bearer " + t + "\naccept-version:1.1\n\n\0");
    };
    w.onmessage = function(e) {
      console.log("[WS] RAW DATA:", typeof e.data, e.data.substring(0, 150));
      if (e.data.indexOf("CONNECTED") === 0) {
        console.log("[WS] STOMP OK");
        w.send("SUBSCRIBE\nid:0\ndestination:/topic/alertes\n\n\0");
      }
      if (e.data.indexOf("MESSAGE") === 0) {
        var body;
        var clMatch = e.data.match(/content-length:(\d+)/i);
        if (clMatch) {
          var len = parseInt(clMatch[1]);
          var headerEnd = e.data.indexOf("\n\n");
          if (headerEnd > 0) body = e.data.substring(headerEnd + 2, headerEnd + 2 + len);
        } else {
          var parts = e.data.split("\n\n");
          body = parts[parts.length - 1];
        }
        if (body) {
          body = body.replace(/\x00/g, "").trim();
          try {
            var n = JSON.parse(body);
            console.log("[WS] Alert: " + n.titre);
            notifyTab();
            // Pulse the BNA logo on alert
            var logos = document.querySelectorAll('img[src*="bna-logo"], img[src*="top-logo"]');
            logos.forEach(function(logo) {
              logo.style.animation = "none";
              logo.offsetHeight; // trigger reflow
              logo.style.animation = "bnaPulse 0.6s ease-in-out";
              setTimeout(function() { logo.style.animation = ""; }, 600);
            });
            playSound(n.niveau);
            var cl = {CRITIQUE:"#e74c3c",ELEVE:"#e67e22",MOYEN:"#f39c12",INFO:"#3498db"};
            var el = document.createElement("div");
            el.style.cssText = "position:fixed;top:20px;right:20px;z-index:99999;padding:16px 24px;border-radius:12px;color:#fff;font-family:sans-serif;font-size:14px;font-weight:bold;box-shadow:0 8px 32px rgba(0,0,0,0.4);cursor:pointer;max-width:400px;background:" + (cl[n.niveau]||"#333");
            el.innerHTML = "<b>" + n.titre + "</b><br><small>" + n.message + "</small>";
            el.onclick = function() { el.remove(); };
            document.body.appendChild(el);
            setTimeout(function() { el.remove(); }, 8000);
          } catch(err) {}
        }
      }
    };
    w.onclose = function() { console.log("[WS] Closed"); setTimeout(c, 5000); };
    w.onerror = function() { console.warn("[WS] Error"); };
  }
  // Keyboard shortcuts for power users
  document.addEventListener("keydown", function(e) {
    // Block shortcuts on login page and if not authenticated
    if (window.location.pathname === "/connexion" || window.location.pathname === "/") {
      return;
    }
    var token = localStorage.getItem("bna_token_acces");
    if (!token) {
      return;
    }
    if (e.ctrlKey || e.metaKey) {
      switch(e.key.toLowerCase()) {
        case "d":
          e.preventDefault();
          window.location.href = "/tableau-bord";
          break;
        case "g":
          e.preventDefault();
          window.location.href = "/transactions";
          break;
        case "r":
          e.preventDefault();
          window.location.href = "/testeur-regle";
          break;
        case "p":
          e.preventDefault();
          var path = window.location.pathname;
          if (path.indexOf("/transactions/") === 0) {
            var id = path.split("/").pop();
            var token = localStorage.getItem("bna_token_acces");
            fetch("/api/transactions/" + id + "/export-pdf", {
              headers: { "Authorization": "Bearer " + token }
            }).then(function(r) { return r.blob(); }).then(function(b) {
              var url = URL.createObjectURL(b);
              var a = document.createElement("a");
              a.href = url; a.download = "transaction-" + id + ".pdf";
              a.click(); URL.revokeObjectURL(url);
            });
          }
          break;
        case "/":
          e.preventDefault();
          alert("Raccourcis clavier:\nCtrl+D = Tableau de bord\nCtrl+G = Transactions\nCtrl+R = Testeur SpEL\nCtrl+P = Export PDF\nCtrl+/ = Aide");
          break;
      }
    }
    if (e.key === "Escape") {
      window.history.back();
    }
  });
  
  c();
})();
