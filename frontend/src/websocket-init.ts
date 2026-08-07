// @ts-nocheck
(function() {
  console.log("=== WS BOOTSTRAP ===");
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
      console.log("[WS] RAW:", e.data.substring(0, 100));
      if (e.data.indexOf("CONNECTED") === 0) {
        console.log("[WS] STOMP OK - subscribing");
        w.send("SUBSCRIBE\nid:0\ndestination:/topic/alertes\n\n\0");
        return;
      }
      if (e.data.indexOf("MESSAGE") === 0) {
        console.log("[WS] MESSAGE received!");
        var parts = e.data.split("\n\n");
        console.log("[WS] Parts:", parts.length);
        if (parts.length >= 2) {
          var body = parts[parts.length - 1].replace(/\x00/g, "");
          console.log("[WS] Body:", body.substring(0, 200));
          try {
            var n = JSON.parse(body);
            console.log("[WS] ALERT:", n.titre);
            var cl = {CRITIQUE:"#e74c3c",ELEVE:"#e67e22",MOYEN:"#f39c12",INFO:"#3498db"};
            var el = document.createElement("div");
            el.style.cssText = "position:fixed;top:20px;right:20px;z-index:99999;padding:16px 24px;border-radius:12px;color:#fff;font-family:sans-serif;font-size:14px;font-weight:bold;box-shadow:0 8px 32px rgba(0,0,0,0.4);cursor:pointer;max-width:400px;background:" + (cl[n.niveau]||"#333");
            el.innerHTML = "<b>" + n.titre + "</b><br><small>" + n.message + "</small>";
            el.onclick = function() { el.remove(); };
            document.body.appendChild(el);
            setTimeout(function() { el.remove(); }, 8000);
          } catch(err) { console.warn("[WS] Parse error:", err); }
        }
        return;
      }
    };
    w.onclose = function() { console.log("[WS] Closed"); setTimeout(c, 5000); };
    w.onerror = function() { console.warn("[WS] Error"); };
  }
  c();
})();
