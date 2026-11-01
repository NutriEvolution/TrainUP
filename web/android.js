/* ===== Integrazione Android (aggiunta solo nella versione APK) ===== */
(function(){
  // schermata con il logo: 2 secondi dall'apertura, poi dissolvenza
  const sp = document.getElementById("splash");
  if(sp) setTimeout(() => { sp.classList.add("out"); setTimeout(() => sp.remove(), 350); }, Math.max(0, 2000 - performance.now()));
  const A = window.Android;
  if(!A) return;
  const U = window.__upd = {info:null, progress:null, checking:false, msg:""};

  // --- notifica di fine recupero quando l'app è in background ---
  let lastAlarm = 0;
  const _rRender = rRender;
  rRender = function(){
    _rRender.apply(this, arguments);
    try{
      const want = (R && R.screen === "rest" && R.restEnd && R.restEnd > Date.now()) ? R.restEnd : 0;
      if(want !== lastAlarm){
        if(want) A.scheduleAlarm(String(want), "Recupero finito", "Torna all'app per la prossima serie");
        else A.cancelAlarm();
        lastAlarm = want;
      }
    }catch(e){}
  };
  const _open = openRunner, _close = closeRunner;
  openRunner = function(){ try{ A.keepScreenOn(true); }catch(e){} return _open.apply(this, arguments); };
  closeRunner = function(){ try{ A.keepScreenOn(false); A.cancelAlarm(); }catch(e){} lastAlarm = 0; return _close.apply(this, arguments); };
  window.onAndroidResume = function(){ try{ if(R && !$("#runner").hidden) rRender(); }catch(e){} };

  // --- tasto Indietro ---
  window.androidBack = function(){
    const sh = $("#sheet");
    if(sh && !sh.hidden){ closeSheet(); return true; }
    const rr = document.getElementById("runr");
    if(rr && !rr.hidden){ act("runExit", {}, null); return true; }
    const rn = $("#runner");
    if(rn && !rn.hidden){ act("exitRunner", {}, null); return true; }
    if(view !== "oggi"){ view = "oggi"; render(); window.scrollTo(0,0); return true; }
    return false;
  };

  // --- aggiornamenti da GitHub ---
  window.onUpdateInfo = function(info){
    U.checking = false; U.info = info;
    U.msg = info.error ? "Controllo non riuscito: sei connesso a internet?" : (info.available ? "" : "Hai già l'ultima versione.");
    render();
  };
  window.onUpdateProgress = function(p){
    if(p === -2) U.msg = "Consenti a TrainUP di installare app, poi torna qui e tocca di nuovo Aggiorna.";
    else if(p === -1) U.msg = "Download non riuscito. Riprova.";
    U.progress = p >= 0 && p < 100 ? p : null;
    if(p === 100) U.msg = "Download completato: conferma l'installazione.";
    render();
  };
  window.onUpdateRejected = function(why){ U.progress = null; U.msg = "Aggiornamento bloccato: " + why + ". Non è stato installato nulla."; render(); };
  window.updateBannerHtml = function(){
    const i = U.info;
    if(!i || !i.available) return "";
    const pr = U.progress;
    return `<div class="card stack upd"><div class="row between"><b>Nuova versione ${esc(i.version)}</b><span class="pill acc">aggiornamento</span></div>
      ${i.notes ? `<p class="small muted" style="white-space:pre-line;margin:0">${esc(i.notes.slice(0, 400))}</p>` : ""}
      ${pr != null ? `<div class="updbar"><i style="width:${pr}%"></i></div><span class="small muted">Download ${pr}%</span>`
        : `<button class="btn primary" data-aact="install">Aggiorna ora</button>`}
      ${U.msg ? `<span class="small muted">${esc(U.msg)}</span>` : ""}</div>`;
  };
  function checkNow(){ U.checking = true; U.msg = ""; render(); A.checkUpdate(); }

  // --- backup dei dati ---
  window.onFileSaved = function(ok){ U.bmsg = ok ? "Backup salvato." : "Backup non salvato."; render(); };
  window.onFileOpened = function(text){
    try{
      const o = JSON.parse(text);
      if(!o || o.app !== "ripresa-forza" || !o.data) throw 0;
      localStorage.setItem(LS, JSON.stringify(o.data));
      if(o.draft) localStorage.setItem(DRAFT, JSON.stringify(o.draft)); else localStorage.removeItem(DRAFT);
      S = load(); U.bmsg = "Dati ripristinati.";
    }catch(e){ U.bmsg = "Questo file non è un backup di TrainUP."; }
    render();
  };
  function exportBackup(){
    const o = {app:"ripresa-forza", v:1, savedAt:new Date().toISOString(), data:S, draft:loadDraft()};
    A.saveFile(`trainup-backup-${todayStr()}.json`, JSON.stringify(o));
  }

  // --- scheda "App e dati" in fondo a Programma ---
  const _prog = renderProgramma;
  renderProgramma = function(){
    const h = _prog.apply(this, arguments);
    const i = U.info, ver = A.version();
    const card = `<div class="card stack"><h2>App e dati</h2>
      <div class="row between"><span>Versione installata</span><b>${esc(ver)}</b></div>
      ${i && i.available ? `<div class="row between"><span>Disponibile</span><b>${esc(i.version)}</b></div>` : ""}
      <div class="row"><button class="btn" data-aact="${i && i.available ? "install" : "check"}" ${U.checking ? "disabled" : ""}>${U.checking ? "Controllo…" : i && i.available ? "Aggiorna ora" : "Cerca aggiornamenti"}</button></div>
      ${U.progress != null ? `<div class="updbar"><i style="width:${U.progress}%"></i></div>` : ""}
      ${U.msg ? `<span class="small muted">${esc(U.msg)}</span>` : ""}
      <hr style="border:0;border-top:1px solid var(--line);margin:4px 0">
      <p class="small muted" style="margin:0">I dati restano solo su questo telefono. Se disinstalli l'app o cambi telefono si perdono: salva un backup ogni tanto (per esempio su Google Drive).</p>
      <div class="row"><button class="btn" data-aact="export">Salva backup</button><button class="btn ghost" data-aact="import">Ripristina</button></div>
      ${U.bmsg ? `<span class="small muted">${esc(U.bmsg)}</span>` : ""}</div>`;
    const k = h.lastIndexOf("</div>");
    return k >= 0 ? h.slice(0, k) + card + h.slice(k) : h + card;
  };

  document.addEventListener("click", e => {
    const b = e.target.closest("[data-aact]"); if(!b) return;
    const a = b.dataset.aact;
    if(a === "check") checkNow();
    else if(a === "install" && U.info && U.info.url){ U.msg = ""; U.progress = 0; render(); A.installUpdate(U.info.url); }
    else if(a === "export") exportBackup();
    else if(a === "import"){ if(confirm("Ripristinare il backup? I dati attuali verranno sostituiti.")) A.openFile(); }
  });

  const st = document.createElement("style");
  st.textContent = `.updbar{height:8px;border-radius:99px;background:var(--surface2);overflow:hidden}.updbar i{display:block;height:100%;background:var(--accent);transition:width .2s}.upd{border:1.5px solid var(--accent)}`;
  document.head.appendChild(st);

  render();
  // controllo automatico all'avvio (al massimo una volta ogni 6 ore)
  try{
    const last = Number(localStorage.getItem("ripresa.updcheck") || 0);
    if(Date.now() - last > 6*3600e3){ localStorage.setItem("ripresa.updcheck", String(Date.now())); A.checkUpdate(); }
  }catch(e){ A.checkUpdate(); }
})();
