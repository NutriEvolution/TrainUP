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
    if(view === "impostazioni"){ view = setPrev || "oggi"; render(); window.scrollTo(0,0); return true; }
    if(view === "piano" && planOpen){ planOpen = null; render(); window.scrollTo(0,0); return true; }
    if(view !== "oggi"){ view = "oggi"; planOpen = null; weekDraft = null; render(); window.scrollTo(0,0); return true; }
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
  const LASTB = "ripresa.lastbackup";
  window.onFileSaved = function(ok){ U.bmsg = ok ? "Backup salvato." : "Backup non salvato."; if(ok){ try{ localStorage.setItem(LASTB, String(Date.now())); }catch(e){} } render(); };
  window.onFileOpened = function(text){
    try{
      const o = JSON.parse(text);
      if(!o || o.app !== "ripresa-forza" || !o.data) throw 0;
      localStorage.setItem(LS, JSON.stringify(o.data));
      if(o.draft) localStorage.setItem(DRAFT, JSON.stringify(o.draft)); else localStorage.removeItem(DRAFT);
      if(o.runDraft) localStorage.setItem(RUN_DRAFT, JSON.stringify(o.runDraft)); else localStorage.removeItem(RUN_DRAFT);
      S = load(); S.settings.levelChosen = true; saveLocal(); U.bmsg = "Dati ripristinati dal backup del " + (o.savedAt ? new Date(o.savedAt).toLocaleDateString("it-IT") : "file scelto") + ".";
      const ob = document.getElementById("onb"); if(ob){ ob.remove(); onb = null; }
    }catch(e){ U.bmsg = "Questo file non è un backup di TrainUP."; if(document.getElementById("onb")) alert("Questo file non è un backup di TrainUP."); }
    render();
  };
  function backupObj(){ return {app:"ripresa-forza", v:2, savedAt:new Date().toISOString(), data:S, draft:loadDraft(), runDraft:loadRunDraft()}; }
  function exportBackup(){ A.saveFile(`trainup-backup-${todayStr()}.json`, JSON.stringify(backupObj())); }

  // --- backup automatico in una cartella del telefono: un file al giorno, gli ultimi 5; foto, video e figure in «TrainUP media» ---
  const AUTO_LAST = "ripresa.autob.last", AUTO_DIRTY = "ripresa.autob.dirty", AUTO_KEEP = 5;
  let autoDir = ""; try{ autoDir = A.backupDirName ? A.backupDirName() : ""; }catch(e){}
  let autoTimer = null;
  const lsGet = k => { try{ return localStorage.getItem(k); }catch(e){ return null; } }, lsSet = (k, v) => { try{ localStorage.setItem(k, v); }catch(e){} };
  function autoBackupNow(){ clearTimeout(autoTimer); if(!autoDir || !A.autoBackup) return; U.amsg = ""; A.autoBackup(todayStr(), JSON.stringify(backupObj()), AUTO_KEEP); }
  window.onAutoBackup = function(err){ if(!err){ lsSet(AUTO_LAST, String(Date.now())); lsSet(AUTO_DIRTY, "0"); U.amsg = ""; } else U.amsg = "Backup automatico non riuscito: " + err + "."; if(document.getElementById("appDati")) render(); };
  window.onFigureImported = function(n){
    if(n == null) return;
    FIGS = null;
    U.fmsg = n === -1 ? "Questo file non è un pacchetto di figure di TrainUP." : n === -2 ? "Figure recuperate da «TrainUP media»." : "Figure importate: " + n + " esercizi.";
    render();
  };
  window.onMirror = function(err){ const m = err ? "Copia di foto e video non riuscita: " + err + "." : ""; if(m !== (U.mmsg || "")){ U.mmsg = m; if(document.getElementById("appDati")) render(); } };
  window.onBackupDir = function(name){ if(name != null){ autoDir = name || "cartella scelta"; autoBackupNow(); } render(); };
  const _save = saveLocal;
  saveLocal = function(){ _save.apply(this, arguments); if(!autoDir) return; lsSet(AUTO_DIRTY, "1"); clearTimeout(autoTimer); autoTimer = setTimeout(autoBackupNow, 30000); };
  const _resume = window.onAndroidResume;
  window.onAndroidResume = function(){ if(_resume) _resume.apply(this, arguments); if(autoDir && lsGet(AUTO_DIRTY) === "1") autoBackupNow(); };
  if(autoDir && lsGet(AUTO_DIRTY) === "1") setTimeout(autoBackupNow, 5000);
  if(autoDir && A.mirrorNow) setTimeout(() => A.mirrorNow(), 8000);

  // --- "App e dati" nelle Impostazioni ---
  const _imp = renderImpostazioni;
  renderImpostazioni = function(){
    const h = _imp.apply(this, arguments);
    const i = U.info, ver = A.version();
    let last = 0; try{ last = Number(localStorage.getItem(LASTB) || 0); }catch(e){}
    const days = last ? Math.floor((Date.now() - last) / 864e5) : null;
    const autoLast = Number(lsGet(AUTO_LAST) || 0);
    const nFig = Object.keys((figs().ex) || {}).length;
    const when = t => { const d = new Date(t), today = new Date(); const dd = Math.round((new Date(today.toDateString()) - new Date(d.toDateString())) / 864e5); const hm = d.toLocaleTimeString("it-IT", {hour:"2-digit", minute:"2-digit"}); return dd === 0 ? "oggi " + hm : dd === 1 ? "ieri " + hm : d.toLocaleDateString("it-IT", {day:"numeric", month:"short"}); };
    const sec = `<div class="o-sec" id="appDati"><div class="o-label">App e dati</div>
      <div class="o-list" style="margin-top:4px"><div class="o-row" style="cursor:default"><span class="n">Versione installata</span><span class="p"><b style="color:var(--ink)">${esc(ver)}</b></span></div>
      ${i && i.available ? `<div class="o-row" style="cursor:default"><span class="n">Disponibile</span><span class="p"><b style="color:var(--ink)">${esc(i.version)}</b></span></div>` : ""}</div>
      <button class="btn" data-aact="${i && i.available ? "install" : "check"}" ${U.checking ? "disabled" : ""} style="margin-top:8px">${U.checking ? "Controllo…" : i && i.available ? "Aggiorna ora" : "Cerca aggiornamenti"}</button>
      ${U.progress != null ? `<div class="updbar" style="margin-top:8px"><i style="width:${U.progress}%"></i></div>` : ""}
      ${U.msg ? `<p class="small muted" style="margin-top:6px">${esc(U.msg)}</p>` : ""}
      <div class="o-label" style="margin-top:18px">Backup</div>
      <div class="o-list" style="margin-top:4px">
        <button class="o-row" data-aact="autoDir"><span class="n">Automatico<small>${autoDir ? esc(autoDir) + " · ultimi " + AUTO_KEEP : "Scegli una cartella del telefono"}</small></span><span class="p">${autoDir ? (autoLast ? esc(when(autoLast)) : "attivo") : "spento"} ›</span></button>
        <button class="o-row" data-aact="export"><span class="n">Manuale<small>Un file dove vuoi tu (Download, Drive)</small></span><span class="p">${last ? esc(when(last)) : "mai"} ›</span></button>
      </div>
      <div class="row" style="margin-top:10px;flex-wrap:wrap;gap:8px">${autoDir ? `<button class="btn sm" data-aact="autoNow">Salva ora</button>` : ""}<button class="btn sm ${autoDir ? "ghost" : ""}" data-aact="import">Ripristina</button>${autoDir ? `<button class="btn sm ghost" data-aact="autoOff" style="color:var(--danger)">Disattiva</button>` : ""}</div>
      ${U.amsg || U.mmsg || U.bmsg ? `<p class="small" style="margin-top:6px;color:${U.amsg || U.mmsg ? "var(--warn)" : "var(--muted)"}">${esc(U.amsg || U.mmsg || U.bmsg)}</p>` : ""}
      <p class="tiny muted" style="margin-top:6px">${autoDir ? `Foto, video e figure copiati anche in «TrainUP media».` : "Foto e video si copiano solo con il backup automatico."}</p>
      <div class="o-label" style="margin-top:18px">Figure dei manuali</div>
      <div class="o-list" style="margin-top:4px">
        <button class="o-row" data-aact="figImport"><span class="n">${nFig ? "Importate" : "Importa il pacchetto"}<small>${nFig ? nFig + " esercizi" : "File .zip con le figure dei tuoi libri"}</small></span><span class="p">${nFig ? "sostituisci" : ""} ›</span></button>
      </div>
      ${nFig ? `<div class="row" style="margin-top:10px"><button class="btn sm ghost" data-aact="figClear" style="color:var(--danger)">Togli le figure</button></div>` : ""}
      ${U.fmsg ? `<p class="small muted" style="margin-top:6px">${esc(U.fmsg)}</p>` : ""}
    </div>`;
    const k = h.indexOf('<div class="o-sec" id="appDati">');
    return k >= 0 ? h.slice(0, k) + sec : h + sec;
  };
  // pulsante di ripristino nella prima schermata dopo l'installazione
  const _onb = onbHtml;
  onbHtml = function(){
    const h = _onb.apply(this, arguments);
    if(!onb || onb.step !== 0) return h;
    return h.replace('</label>', `</label><div class="hint small" style="margin-top:4px"><b>Hai reinstallato l'app?</b> Se hai un file di backup, ripristinalo: ritrovi programma, allenamenti e misure.<br><button class="btn sm" data-aact="importOnb" style="margin-top:8px">Ripristina un backup</button></div>`);
  };
  if(onb) onbRender();

  document.addEventListener("click", e => {
    const b = e.target.closest("[data-aact]"); if(!b) return;
    const a = b.dataset.aact;
    if(a === "check") checkNow();
    else if(a === "install" && U.info && U.info.url){ U.msg = ""; U.progress = 0; render(); A.installUpdate(U.info.url); }
    else if(a === "export") exportBackup();
    else if(a === "import"){ if(confirm("Ripristinare il backup? I dati attuali verranno sostituiti.")) A.openFile(); }
    else if(a === "importOnb") A.openFile();
    else if(a === "autoDir") A.chooseBackupDir();
    else if(a === "figImport"){ U.fmsg = ""; A.figureImport(); }
    else if(a === "figClear"){ if(confirm("Togliere le figure dei manuali dall'app? Puoi reimportarle quando vuoi.")){ A.figureClear(); FIGS = null; U.fmsg = ""; render(); } }
    else if(a === "autoNow") autoBackupNow();
    else if(a === "autoOff"){ if(confirm("Disattivare il backup automatico? I file già salvati restano nella cartella.")){ A.clearBackupDir(); autoDir = ""; render(); } }
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
