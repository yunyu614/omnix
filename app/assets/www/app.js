/* ================= ModRepo 1.6.0 ================= */
"use strict";
const A = window.Android || {};

const THEMES = [
  { id:"mist", n:"雾泉", c:["#006699","#167F94"] },
  { id:"celadon", n:"青瓷", c:["#2F7966","#487565"] },
  { id:"violet", n:"暮云", c:["#6750A4","#8A6B91"] },
  { id:"sakura", n:"樱粉", c:["#B5546E","#C97F92"] },
  { id:"amber", n:"琥珀", c:["#B0703A","#C98A45"] },
  { id:"mocha", n:"摩卡", c:["#8B5E3C","#A67C52"] },
  { id:"graphite", n:"石墨", c:["#3C4854","#55636F"] },
  { id:"ocean", n:"深海", c:["#0C6E8C","#1D8FA6"] },
];

const MANAGERS = [
  { n:"Magisk",        repo:"topjohnwu/Magisk",            pkg:"com.topjohnwu.magisk", d:"官方稳定版，系统级 root 与模块框架" },
  { n:"KernelSU",      repo:"tiann/KernelSU",              pkg:"me.weishu.kernelsu",   d:"内核级 root，官方稳定版" },
  { n:"KernelSU-Next", repo:"KernelSU-Next/KernelSU-Next", pkg:"com.rifsxd.ksunext",   d:"社区延续分支，支持 KPM" },
  { n:"SukiSU Ultra",  repo:"SukiSU-Ultra/SukiSU-Ultra",   pkg:"com.sukisu.ultra",     d:"基于 KernelSU 的增强分支，内置 SUSFS" },
  { n:"APatch",        repo:"bmax121/APatch",              pkg:"com.blink.apatch",     d:"boot 分区 patch 方案，免内核编译" },
];

const TOOLS = [
  { id:"dl",    icon:"⬇️", n:"下载中心",    d:"实时进度、历史记录与安装" },
  { id:"mgr",   icon:"📦", n:"管理器收录",  d:"官方 Root 管理器与版本下载" },
  { id:"apps",  icon:"🧊", n:"应用冻结",    d:"冻结或启用指定应用（pm disable）" },
  { id:"spoof", icon:"🎭", n:"机型伪装",    d:"改机型/品牌/指纹，可一键还原" },
  { id:"part",  icon:"💽", n:"镜像分区",    d:"分区导出 / 刷入 · by-name 表" },
  { id:"appbackup", icon:"📤", n:"应用备份", d:"自选应用导出安装包（含 split）" },
  { id:"font",  icon:"🧬", n:"字库备份",    d:"persist / modem / nvdata 关键分区" },
  { id:"lsp",    icon:"🧩", n:"刷入 LSPosed", d:"一键刷入，可选附带 Zygisk Next" },
  { id:"appcat", icon:"🎁", n:"应用收录",   d:"Shizuku / Termux / MT / Scene 等" },
  { id:"datamgr", icon:"🗂", n:"订阅与备份", d:"自定义订阅源 · 配置导出导入" },
];

const SRC = {
  ksu:    { n:"KernelSU 官方",  kind:"ksuHtml", url:"https://modules.kernelsu.org/en/" },
  ksunext:{ n:"KernelSU-Next",  kind:"json", repo:"KernelSU-Next/KernelSU-Next-Modules-Repo", path:"modules.json" },
  alt:    { n:"Magisk Alt Repo", kind:"alt",  repo:"Magisk-Modules-Alt-Repo/json-v2", path:"json/modules.json" },
  lsp:    { n:"LSPosed 官方",    kind:"lspPage", base:"https://modules.lsposed.org/module-list/" },
  xposed: { n:"Xposed 模块组织", kind:"org", org:"Xposed-Modules-Repo" },
};

const TITLES = {
  home:["首页","Root 环境与设备概览"],
  mod:["模块","KernelSU 与 LSPosed 模块索引"],
  tools:["多功能","系统工具与模块管理"],
  set:["设置","外观、翻译与关于"],
};

const state = {
  mode:"day", theme:"mist", bar:"float", tr:"auto", subs:[], page:"home", sub:null,
  tab:"mod", installed:{}, local:[], cache:{}, altZip:null, trCache:{},
  mod:[], lsp:[], lspPage:0, lspPageCount:0, lspTotal:0, sheetOpen:false,
  appsAll:[], detail:{}, slot:{ ab:false, slot:"", count:0 },
  modsAll:[], dlAct:{}, dlHist:[], fontEst:{}, pa:1, na:1,
};

/* ---------- 主题 ---------- */
let sysDark = null;   // 由 Java 上报的系统真实夜间状态
const prefNight = ()=>{
  if (typeof sysDark === "boolean") return sysDark;
  return !!(window.matchMedia && window.matchMedia("(prefers-color-scheme:dark)").matches);
};
const isNight = ()=> state.mode === "night" || (state.mode === "auto" && prefNight());
function applyTheme(){
  const night = isNight(), html = document.documentElement;
  html.dataset.theme = state.theme;
  html.dataset.mode = night ? "night" : "day";
  html.style.setProperty("--pa", String(state.pa));
  html.style.setProperty("--na", String(state.na));
  document.getElementById("nav").className = state.bar;
  requestAnimationFrame(()=>{
    const bg = getComputedStyle(html).getPropertyValue("--bg").trim();
    if (A.bars && bg) A.bars(bg, !night);
    if (A.setIcon) A.setIcon(state.theme);
    if (A.log) A.log("THEME " + state.theme + "/" + html.dataset.mode + " sysDark=" + sysDark + " mode=" + state.mode);
  });
}
if (window.matchMedia){ try { window.matchMedia("(prefers-color-scheme:dark)").addEventListener("change", ()=>{ if (state.mode==="auto") applyTheme(); }); } catch(e){} }

/* ---------- 配置 ---------- */
function loadCfg(){
  state.mode  = A.cfgGet("mode","day");
  state.theme = A.cfgGet("theme","ocean");
  if (A.cfgGet("themeDefault","") !== "ocean"){ state.theme = "ocean"; A.cfgSet("themeDefault","ocean"); }
  state.bar   = A.cfgGet("bar","float");
  state.tr    = A.cfgGet("tr","auto");
  state.pa    = 0.86;   // 预置轻微透明
  state.na    = 0.9;
  try { state.subs = JSON.parse(A.cfgGet("subs","[]")) || []; } catch(e){ state.subs = []; }
  try { state.cache = JSON.parse(A.cfgGet("relCache","{}")) || {}; } catch(e){ state.cache = {}; }
  try { state.trCache = JSON.parse(A.cfgGet("trCache","{}")) || []; } catch(e){ state.trCache = {}; }
  try { state.pwHist = JSON.parse(A.cfgGet("pwHist","[]")) || []; } catch(e){ state.pwHist = []; }
  try { state.reports = JSON.parse(A.cfgGet("reports","[]")) || []; } catch(e){ state.reports = []; }
  try { state.appPower = JSON.parse(A.cfgGet("appPower","{}")) || {}; } catch(e){ state.appPower = {}; }
}
function saveCfg(){
  A.cfgSet("mode",state.mode); A.cfgSet("theme",state.theme); A.cfgSet("bar",state.bar);
  A.cfgSet("tr",state.tr); A.cfgSet("subs",JSON.stringify(state.subs));

}
const saveCache = ()=> A.cfgSet("relCache", JSON.stringify(state.cache));

/* ---------- HTTP ---------- */
let seq = 0;
const pending = new Map();
function http(url){
  return new Promise((res, rej)=>{
    const id = "r" + (++seq);
    pending.set(id,{res,rej});
    A.fetch(id, url);
    setTimeout(()=>{ if (pending.has(id)){ pending.delete(id); rej(new Error("超时")); } }, 30000);
  });
}
function httpPost(url, payload, ctype){
  return new Promise((res, rej)=>{
    const id = "p" + (++seq);
    pending.set(id,{res,rej});
    A.post(id, url, payload, ctype || "application/json");
    setTimeout(()=>{ if (pending.has(id)){ pending.delete(id); rej(new Error("超时")); } }, 30000);
  });
}
function onHttp(d){
  const p = pending.get(d.id);
  if (!p) return;
  pending.delete(d.id);
  const body = A.body ? String(A.body(d.id) || "") : "";
  if (d.code >= 200 && d.code < 400) p.res(body);
  else p.rej(new Error("HTTP " + d.code));
}

/* ---------- 工具 ---------- */
function cmpVer(a,b){
  const pa = String(a||"").replace(/^[^\d]*/,"").split(/[.\-_+ (]/).map(x=>parseInt(x)||0);
  const pb = String(b||"").replace(/^[^\d]*/,"").split(/[.\-_+ (]/).map(x=>parseInt(x)||0);
  for (let i=0;i<Math.max(pa.length,pb.length);i++){ const d=(pa[i]||0)-(pb[i]||0); if (d) return d; }
  return 0;
}
const esc = s => String(s==null?"":s).replace(/[&<>"']/g,c=>({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;" }[c]));
const unesc = s => String(s==null?"":s).replace(/&amp;/g,"&").replace(/&lt;/g,"<").replace(/&gt;/g,">").replace(/&quot;/g,'"').replace(/&#39;/g,"'");
const set = (id,v)=>{ const el=document.getElementById(id); if (el) el.textContent = v||"—"; };

/* ---------- 页面 & 返回 ---------- */
function showPage(id, title, sub){
  document.querySelectorAll(".page").forEach(s=>s.classList.toggle("on", s.id === "p-" + id));
  document.getElementById("pg-title").textContent = title;
  document.getElementById("pg-sub").textContent = sub || "";
  document.getElementById("scroll").scrollTop = 0;
}
function updateDepth(){
  const d = (state.sub ? 1 : 0) + (state.page !== "home" ? 1 : 0) + (state.sheetOpen ? 1 : 0);
  if (A.setDepth) A.setDepth(d);
}
/* ---------- 导航栈（支持任意层级返回） ---------- */
let viewStack = [{ kind:"main", page:"home" }];

function renderView(v){
  if (v.kind === "main"){
    state.page = v.page; state.sub = null;
    document.querySelectorAll("#nav button").forEach(b=>b.classList.toggle("on", b.dataset.p===v.page));
    const t = TITLES[v.page] || TITLES.home;
    showPage(v.page, t[0], t[1]);
  } else {
    state.sub = v.id;
    showPage(v.id, v.title, v.sub || "");
  }
  updateDepth();
}

function go(p){
  // 切底栏 = 重置栈
  closeSheet();
  viewStack = [{ kind:"main", page:p }];
  renderView(viewStack[viewStack.length - 1]);
  if (p === "mod") loadTab();
  if (p === "tools") renderTools();
}

function openSub(id, title, sub){
  viewStack.push({ kind:"sub", id, title, sub });
  renderView(viewStack[viewStack.length - 1]);
}

function goBack(){
  if (state.sheetOpen){ closeSheet(); return; }
  if (viewStack.length > 1){
    viewStack.pop();
    renderView(viewStack[viewStack.length - 1]);
    return;
  }
  if (state.page !== "home"){ go("home"); return; }
  if (A.finishApp) A.finishApp();
}

window.__back = goBack;
document.querySelectorAll("#nav button").forEach(b=>b.onclick = ()=> go(b.dataset.p));
document.querySelectorAll("[data-back]").forEach(b=>b.onclick = goBack);
document.getElementById("dl-pill").onclick = ()=>{
  openSub("dl","下载中心","进度 · 历史 · 安装");
  renderDl();
};

/* ---------- 事件 ---------- */
window.onNativeEvent = function(ev, data){
  let d = {}; try { d = JSON.parse(data||"{}"); } catch(e){}
  if (ev === "env") fillEnv(d);
  else if (ev === "http") onHttp(d);
  else if (ev === "dlp" || ev === "dl") onDl(d, ev);
  else if (ev === "sys") onSys(d);
  else if (ev === "installed") onInstalled(d);
  else if (ev === "frozen") onFrozen(d);
  else if (ev === "opdone") onOpDone(d);
  else if (ev === "toast") toast(data);
  else if (ev === "backup") doImportText(data);
};

/* ---------- 环境 ---------- */
function fillEnv(e){
  if (typeof e.dark === "boolean"){
    const changed = sysDark !== e.dark;
    sysDark = e.dark;
    if (changed && state.mode === "auto") applyTheme();
  }
  const root = !!e.root;
  const img = document.getElementById("mgr-icon");
  const fb = document.getElementById("mgr-fallback");
  const dot = document.getElementById("mgr-dot");
  const mgr = (e.managers || [])[0];
  if (mgr && mgr.icon){
    img.src = "data:image/png;base64," + mgr.icon;
    img.style.display = "";
    fb.style.display = "none";
  } else {
    img.style.display = "none";
    fb.style.display = "";
  }
  dot.className = "dot " + (root ? "ok" : (e.su ? "wait" : ""));
  if (A.log) A.log("MGRICON " + (mgr ? mgr.name : "none") + " b64len=" + (mgr && mgr.icon ? mgr.icon.length : 0));
  const mgrs = e.managers || [];
  set("e-mgr", mgrs.length ? mgrs.map(m=>m.name).join(" / ") : (root ? "未知实现" : "无"));
  set("e-mgrver", mgrs.length ? (mgrs[0].ver||"—") : "—");
  set("e-selinux", e.selinux);
  document.getElementById("env-chips").innerHTML =
    [["Android",e.android],["SDK",e.sdk],["架构",e.abi]].filter(x=>x[1])
      .map(([k,v])=>`<span class="c">${esc(k)} <b>${esc(v)}</b></span>`).join("");
  set("d-model", [e.brand,e.model].filter(Boolean).join(" "));
  set("d-android", "Android " + (e.android||"—"));
  set("d-kernel", e.kernel); set("d-abi", e.abi); set("d-ram", e.ram);
  set("d-screen", e.screen); set("d-cpu", e.cpu); set("d-rom", e.rom);
}

/* ---------- 设备详情 ---------- */
document.getElementById("dev-more").onclick = ()=>{
  openSub("dev", "设备详情", "厂商 / 屏幕 / 网络 / 系统");
  if (A.detail) A.detail();
};
document.getElementById("dev-grid").onclick = ()=> document.getElementById("dev-more").click();
document.querySelectorAll(".mcounts .mcount").forEach(card=>card.onclick = ()=>{
  state.modKind = card.dataset.kind === "xposed" ? "xposed" : "root";
  openSub("modules", state.modKind === "xposed" ? "Xposed 模块" : "Root 模块",
          state.modKind === "xposed" ? "LSPosed 模块清单" : "KSU / Magisk 模块清单");
  renderModsList();
  if (A.modules) A.modules();
});
document.querySelectorAll("#seg-mods .chip").forEach(b=>b.onclick = ()=>{
  document.querySelectorAll("#seg-mods .chip").forEach(x=>x.classList.remove("on"));
  b.classList.add("on");
  state.modFilter = b.dataset.v;
  renderModsList();
});

function onSys(d){
  if (A.log) A.log("SYS " + d.id + " n=" + (Array.isArray(d.data) ? d.data.length : Object.keys(d.data||{}).length));
  if (d.id === "detail") renderDetail(d.data || {});
  else if (d.id === "apps"){
    state.appsAll = d.data || [];
    renderApps();
    if (state.sub === "appbackup") renderAbList();
  }
  else if (d.id === "battery") renderBattery(d.data || {});
  else if (d.id === "parts") renderParts(d.data || []);
  else if (d.id === "modules"){
    state.modsAll = d.data || [];
    const rootN = state.modsAll.filter(m=>m.kind !== "xposed").length;
    const xpN = state.modsAll.filter(m=>m.kind === "xposed").length;
    set("mc-root", String(rootN));
    set("mc-xp", String(xpN));
    renderModsList();
  }
  else if (d.id === "fontest"){
    state.fontEst[d.mode] = d.data || {};
    if (A.log) A.log("EST " + d.mode + " parts=" + ((d.data||{}).count) + " size=" + ((d.data||{}).size));
    set("est-" + (d.mode === "full" ? "full" : "key"), "预估 " + ((d.data||{}).size || "—"));
  }
  else if (d.id === "slot"){
    state.slot = d.data || { ab:false };
    if (state.sub === "part") renderParts(state.partsCache || []);
  }
}
function kvRows(obj){
  return Object.entries(obj).filter(([k,v])=>v!=null && String(v).length)
    .map(([k,v])=>`<div class="kv"><span class="lbl">${esc(k)}</span><b>${esc(v)}</b></div>`).join("");
}
function renderDetail(j){
  const v = j.vendor||{}, s = j.screen||{}, n = j.net||{}, sy = j.system||{}, st = j.storage||{};
  const body = `
    <div class="glass card"><h2 class="sec first">厂商信息</h2>
      ${kvRows({ "品牌":v.brand, "厂商":v.manufacturer, "机型":v.model, "设备代号":v.device,
                 "产品":v.product, "主板":v.board, "硬件":v.hardware })}</div>
    <div class="glass card"><h2 class="sec first">屏幕</h2>
      ${kvRows({ "分辨率":s.res, "物理尺寸":s.size, "DPI":s.dpi, "密度":s.density, "刷新率":s.fps })}</div>
    <div class="glass card"><h2 class="sec first">网络</h2>
      ${kvRows({ "连接类型":n.type, "状态":n.state, "本机 IP":n.ip, "接口":n.iface, "运营商":n.operator })}</div>
    <div class="glass card"><h2 class="sec first">系统数据</h2>
      ${kvRows({ "Android":sy.android, "SDK":sy.sdk, "安全补丁":sy.patch, "版本号":sy.build,
                 "内核":sy.kernel, "架构":sy.arch, "SELinux":sy.selinux, "运行时长":sy.uptime,
                 "引导程序":sy.bootloader, "CPU":j.cpu })}</div>
    <div class="glass card"><h2 class="sec first">存储 / 内存</h2>
      ${kvRows({ "数据分区总量":st.total, "可用空间":st.free, "内存":j.meminfo })}</div>
    <div class="glass card"><h2 class="sec first">指纹</h2>
      <p class="note" style="margin:0;word-break:break-all">${esc(v.fingerprint||"—")}</p></div>`;
  document.getElementById("dev-body").innerHTML = body;
}

/* ---------- 多功能 ---------- */
function renderTools(){
  const box = document.getElementById("tool-grid");
  box.innerHTML = TOOLS.map(t=>`
    <div class="glass tool" data-t="${t.id}">
      <div class="ti">${t.icon}</div>
      <div class="tn">${esc(t.n)}</div>
    </div>`).join("");
  box.querySelectorAll(".tool").forEach(el=>el.onclick = ()=> openTool(el.dataset.t));
}
function openTool(id){
  if (id === "mgr"){ openSub("mgr","管理器收录","官方 Root 管理器"); if (!document.getElementById("mgr-list").children.length) renderManagers(); return; }
  if (id === "apps"){ openSub("apps","应用冻结 / 启用","pm disable / enable"); if (A.apps) A.apps(); return; }
  if (id === "spoof"){ openSpoof(); return; }

  if (id === "part"){
    openSub("part","镜像分区","导出 / 刷入");
    if (A.slot) A.slot();
    if (A.parts) A.parts();
    return;
  }
  if (id === "appbackup"){
    openSub("appbackup","应用备份","导出 APK");
    state.abSel = new Set();
    if (A.apps) A.apps(); else renderAbList();
    return;
  }
  if (id === "font"){
    openSub("font","字库备份","关键数据 / 完整备份");
    if (A.fontEstimate){ A.fontEstimate("key"); A.fontEstimate("full"); }
    return;
  }
  if (id === "lsp"){ flashLsposed(); return; }
  if (id === "appcat"){ openSub("appcat","应用收录","常用玩机应用"); renderAppCat(); return; }
  if (id === "dl"){ openSub("dl","下载中心","进度 · 历史 · 安装"); renderDl(); return; }
  if (id === "datamgr"){ openSub("datamgr","订阅与备份","订阅源与配置"); renderSubs(); return; }
}

/* ---------- 管理器 ---------- */
function renderManagers(){
  const box = document.getElementById("mgr-list");
  box.innerHTML = MANAGERS.map((m,i)=>`
    <div class="glass mgr-card" data-i="${i}">
      <div class="mgr-head">
        <div class="mm">
          <div class="mn">${esc(m.n)} <span class="ver" data-ver="${i}">…</span></div>
          <div class="mr">github.com/${esc(m.repo)}</div>
        </div>
        <svg class="chev" viewBox="0 0 24 24"><path d="M6 9l6 6 6-6"/></svg>
      </div>
      <div class="mgr-body" data-body="${i}" style="display:none">
        <p class="mgr-desc">${esc(m.d)}</p>
        <div class="mgr-meta" data-meta="${i}">—</div>
        <div class="acts">
          <button class="btn sm" data-dl="${i}">下载最新版</button>
          <button class="btn sm ghost" data-gh="${i}">GitHub</button>
        </div>
      </div>
    </div>`).join("");
  document.getElementById("mgr-cnt").textContent = MANAGERS.length + " 项";
  box.querySelectorAll(".mgr-head").forEach(h=>h.onclick = ()=>{
    const body = h.parentElement.querySelector(".mgr-body");
    const open = body.style.display !== "none";
    body.style.display = open ? "none" : "block";
    h.parentElement.classList.toggle("open", !open);
  });
  box.querySelectorAll("[data-dl]").forEach(b=>b.onclick = ev=>{ ev.stopPropagation(); downloadManager(MANAGERS[+b.dataset.dl], b); });
  box.querySelectorAll("[data-gh]").forEach(b=>b.onclick = ev=>{ ev.stopPropagation(); A.openUrl("https://github.com/" + MANAGERS[+b.dataset.gh].repo); });
  MANAGERS.forEach((m,i)=> loadManagerVersion(m,i));
}
async function loadManagerVersion(m,i){
  const verEl = document.querySelector(`[data-ver="${i}"]`);
  const metaEl = document.querySelector(`[data-meta="${i}"]`);
  const inst = m.pkg ? state.installed[m.pkg] : "";
  const loc = localHit(m.n);
  const bits = [];
  if (inst) bits.push("已装 v" + inst);
  if (loc && loc.ver) bits.push("本地 v" + loc.ver);
  if (metaEl) metaEl.textContent = bits.length ? bits.join(" · ") : "未安装 · 无本地文件";
  try {
    const r = await ghLatest(m.repo);
    m._latest = r;
    if (verEl){
      verEl.textContent = "最新 " + (r.tag || ("v"+r.ver));
      const cur = inst || (loc && loc.ver) || "";
      if (cur && r.ver && cmpVer(r.ver, cur) > 0){ verEl.textContent += " · 有更新"; verEl.classList.add("upd"); }
    }
  } catch(e){ if (verEl) verEl.textContent = "获取失败"; }
}

/* ---------- 应用冻结 ---------- */
function renderApps(){
  const box = document.getElementById("app-list");
  const q = (document.getElementById("q-app").value||"").toLowerCase();
  const filt = (document.querySelector("#seg-app .chip.on")||{}).dataset?.v || "all";
  const list = state.appsAll.filter(a=>{
    if (filt === "user" && a.sys) return false;
    if (filt === "sys" && !a.sys) return false;
    return !q || (a.label+a.pkg).toLowerCase().includes(q);
  });
  document.getElementById("app-cnt").textContent = list.length + " 个应用";
  if (!list.length){ box.innerHTML = '<div class="glass card"><div class="empty">没有匹配的应用</div></div>'; return; }
  box.innerHTML = list.map(a=>`
    <div class="glass mgr">
      <div class="mm">
        <div class="mn">${esc(a.label)} ${a.sys?'<span class="badge-src">系统</span>':""} ${a.frozen?'<span class="tag warn">已冻结</span>':""}</div>
        <div class="mr">${esc(a.pkg)}${a.ver?" · v"+esc(a.ver):""}</div>
      </div>
      <button class="btn sm ${a.frozen?"":"ghost"}" data-pkg="${esc(a.pkg)}" data-dis="${a.frozen?0:1}">
        ${a.frozen?"启用":"冻结"}</button>
    </div>`).join("");
  box.querySelectorAll("button[data-pkg]").forEach(b=>b.onclick = ()=>{
    const pkg = b.dataset.pkg, dis = b.dataset.dis === "1";
    b.disabled = true; b.textContent = "…";
    A.freezePkg(pkg, dis);
  });
}
document.getElementById("q-app").oninput = renderApps;
document.querySelectorAll("#seg-app .chip").forEach(b=>b.onclick = ()=>{
  document.querySelectorAll("#seg-app .chip").forEach(x=>x.classList.remove("on"));
  b.classList.add("on"); renderApps();
});
function onFrozen(d){
  toast((d.disable ? "已冻结：" : "已启用：") + d.pkg + (d.ok ? "" : "（失败）"));
  if (A.apps) A.apps();
}

function onOpDone(d){
  if (d.kind === "export"){
    showSheet(d.ok ? "导出完成" : "导出失败",
      `分区镜像：${d.path}\n\n${String(d.msg||"").slice(0,900)}`,
      [{ label:"好", pri:true, fn:closeSheet }]);
    if (A.parts) A.parts();
  } else if (d.kind === "flash"){
    showSheet(d.ok ? "刷入完成" : "刷入失败",
      `分区：${d.path}\n\n${String(d.msg||"").slice(0,900)}`,
      [{ label:"好", pri:true, fn:closeSheet }]);
  } else if (d.kind === "toggle"){
    toast((d.enabled ? "已启用：" : "已禁用：") + d.id + (d.ok ? "" : "（失败）"));
    if (A.modules) A.modules();
    if (curModItem && curModItem.id === d.id) curModItem.enabled = d.enabled;
  } else if (d.kind === "spoof"){
    showSheet(d.ok ? "完成" : "失败", String(d.msg||""), [{ label:"好", pri:true, fn:closeSheet }]);
    if (A.spoofProps) A.spoofProps();
  if (A.guardStatus) A.guardStatus();
  } else if (d.kind === "daemon" || d.kind === "boot"){
    toast(String(d.msg || ""));
    if (A.tuneStatus) A.tuneStatus();
  } else if (d.kind === "action"){
    showSheet(d.ok ? "动作已执行" : "执行失败", String(d.msg||"").slice(0,1200),
      [{ label:"好", pri:true, fn:closeSheet }]);
    if (A.modules) A.modules();
  } else if (d.kind === "appbackup"){
    showSheet(d.ok ? "导出完成" : "导出失败",
      `目录：${d.path}\n\n${String(d.msg||"").slice(0,1200)}`,
      [{ label:"好", pri:true, fn:closeSheet }]);
  } else if (d.kind === "font"){
    const out = document.getElementById("font-out");
    if (out){
      out.innerHTML = `<div class="glass card">
        <div class="kv"><span class="lbl">结果</span><b>${d.ok?"成功":"失败"}</b></div>
        <div class="kv"><span class="lbl">目录</span><b style="word-break:break-all;text-align:right">${esc(d.path)}</b></div>
        <pre class="sheet-body" style="margin-top:8px;white-space:pre-wrap">${esc(d.msg||"")}</pre></div>`;
    }
    toast(d.ok ? "字库备份完成" : "字库备份失败");
  }
}

/* ---------- 应用备份 ---------- */
function renderAbList(){
  const box = document.getElementById("ab-list");
  if (!box) return;
  const q = (document.getElementById("q-ab").value||"").toLowerCase();
  const filt = (document.querySelector("#seg-ab .chip.on")||{}).dataset?.v || "all";
  const list = (state.appsAll||[]).filter(a=>{
    if (filt === "user" && a.sys) return false;
    if (filt === "sys" && !a.sys) return false;
    return !q || (a.label+a.pkg).toLowerCase().includes(q);
  });
  set("ab-cnt", list.length + " 个应用");
  updateAbSel();
  if (!list.length){ box.innerHTML = '<div class="glass card"><div class="empty">没有匹配的应用</div></div>'; return; }

  const draw = ()=>{
    box.innerHTML = list.map((a,i)=>{
      const on = state.abSel.has(a.pkg);
      const icon = iconCache.get(a.pkg);
      return `<div class="use-item" data-pkg="${esc(a.pkg)}">
        <div class="use-ico">${icon ? `<img src="data:image/png;base64,${icon}">` : `<span class="ph">${esc((a.label||"?")[0])}</span>`}</div>
        <div class="use-b">
          <div class="use-top"><span>${esc(a.label)}</span>
            <span class="tag" style="${on?"color:var(--acc1)":""}">${on?"已选":"选择"}</span></div>
          <div class="use-sub"><span>${esc(a.pkg)}${a.sys?" · 系统":""}</span><span>${a.ver?"v"+esc(a.ver):""}</span></div>
        </div>
      </div>`;
    }).join("");
    box.querySelectorAll(".use-item").forEach(el=>el.onclick = ()=>{
      const pkg = el.dataset.pkg;
      if (state.abSel.has(pkg)) state.abSel.delete(pkg); else state.abSel.add(pkg);
      renderAbList();
    });
  };
  draw();
  loadIcons(list.slice(0, 80).map(a=>a.pkg), ()=>{
    if (!box.querySelector(".use-ico img")) draw();
  });
}

function updateAbSel(){
  const n = state.abSel ? state.abSel.size : 0;
  set("ab-sel", "已选 " + n);
  const btn = document.getElementById("ab-export");
  if (btn) btn.textContent = "导出所选安装包" + (n ? `（${n}）` : "");
}
document.getElementById("q-ab").oninput = renderAbList;
document.getElementById("ab-all").onclick = ()=>{
  const q = (document.getElementById("q-ab").value||"").toLowerCase();
  const filt = (document.querySelector("#seg-ab .chip.on")||{}).dataset?.v || "all";
  (state.appsAll||[]).forEach(a=>{
    if (filt === "user" && a.sys) return;
    if (filt === "sys" && !a.sys) return;
    if (!q || (a.label+a.pkg).toLowerCase().includes(q)) state.abSel.add(a.pkg);
  });
  renderAbList();
};
document.querySelectorAll("#seg-ab .chip").forEach(b=>b.onclick = ()=>{
  document.querySelectorAll("#seg-ab .chip").forEach(x=>x.classList.remove("on"));
  b.classList.add("on"); renderAbList();
});
document.getElementById("ab-none").onclick = ()=>{ state.abSel.clear(); renderAbList(); };
document.getElementById("ab-export").onclick = ()=>{
  const n = state.abSel.size;
  if (!n){ toast("先选择要备份的应用"); return; }
  showSheet("导出安装包", `将导出 ${n} 个应用到\nDownload/ModRepo/appbackup/`, [
    { label:"开始导出", pri:true, fn:()=>{
        closeSheet();
        showSheet("导出中…", "正在复制安装包，请稍候", []);
        A.exportApps(Array.from(state.abSel).join(","));
    }},
    { label:"取消", fn:closeSheet },
  ]);
};

/* ---------- 电池 ---------- */
function renderBattery(b){
  const rows = {
    "电量": b.percent != null ? b.percent + " %" : "",
    "状态": b.status, "健康": b.health, "温度": b.temp ? b.temp + " ℃" : "",
    "电压": b.voltage ? (Number(b.voltage)/1000).toFixed(2) + " V" : "",
    "电流": b.current, "功率": b.power, "充电状态": b.plugged,
    "循环次数": b.cycles, "满电容量": b.chargeFull, "当前容量": b.chargeNow,
    "设计容量": b.design, "电池技术": b.tech,
  };
  document.getElementById("batt-body").innerHTML =
    `<div class="glass card">${kvRows(rows)}</div>`;
}

/* ---------- 机型伪装 ---------- */
const SPOOF_PRESETS = [
  { n:"Google Pixel 9 Pro", p:{
    "ro.product.brand":"google", "ro.product.model":"GEC77", "ro.product.device":"caiman",
    "ro.product.manufacturer":"Google", "ro.product.name":"caiman", "ro.product.board":"caiman",
    "ro.build.fingerprint":"google/caiman/caiman:14/AP1A.240505.005/11671074:user/release-keys",
    "ro.build.display.id":"AP1A.240505.005", "ro.build.version.incremental":"11671074",
    "ro.board.platform":"zuma", "ro.soc.model":"Tensor G4" } },
  { n:"小米 15 Pro", p:{
    "ro.product.brand":"Xiaomi", "ro.product.model":"25101PNADC", "ro.product.device":"sheng",
    "ro.product.manufacturer":"Xiaomi", "ro.product.name":"sheng", "ro.product.board":"sun",
    "ro.build.fingerprint":"Xiaomi/sheng/sheng:15/AQ3A.240812.001/OS2.0.1.0:user/release-keys",
    "ro.build.display.id":"OS2.0.1.0", "ro.build.version.incremental":"OS2.0.1.0",
    "ro.board.platform":"sun", "ro.soc.model":"Snapdragon 8 Elite" } },
  { n:"ROG 游戏手机 9", p:{
    "ro.product.brand":"asus", "ro.product.model":"AI2501", "ro.product.device":"AI2501",
    "ro.product.manufacturer":"asus", "ro.product.name":"AI2501", "ro.product.board":"sun",
    "ro.build.fingerprint":"asus/AI2501/AI2501:15/AQ3A.240812.001/1.0.0:user/release-keys",
    "ro.build.display.id":"1.0.0", "ro.build.version.incremental":"1.0.0",
    "ro.board.platform":"sun", "ro.soc.model":"Snapdragon 8 Elite" } },
  { n:"一加 13", p:{
    "ro.product.brand":"OnePlus", "ro.product.model":"PJZ110", "ro.product.device":"ossi",
    "ro.product.manufacturer":"OnePlus", "ro.product.name":"PJZ110", "ro.product.board":"sun",
    "ro.build.fingerprint":"OnePlus/PJZ110/ossi:15/AQ3A.240812.001/1.0.0:user/release-keys",
    "ro.build.display.id":"1.0.0", "ro.build.version.incremental":"1.0.0",
    "ro.board.platform":"sun", "ro.soc.model":"Snapdragon 8 Elite" } },
];
const SPOOF_KEYS = [
  "ro.product.brand","ro.product.model","ro.product.device","ro.product.manufacturer",
  "ro.product.name","ro.product.board","ro.build.fingerprint","ro.build.display.id",
  "ro.build.version.incremental","ro.board.platform","ro.soc.model"
];
let spoofProps = {};

function openSpoof(){
  openSub("spoof","机型伪装","品牌 / 型号 / 指纹");
  if (A.spoofProps) A.spoofProps();
}
function renderSpoof(payload){
  spoofProps = payload.data || {};
  set("sp-state", payload.masked ? "已伪装（可还原）" : "原始状态");
  set("sp-cur", (spoofProps["ro.product.brand"]||"?") + " " + (spoofProps["ro.product.model"]||"?"));
  set("sp-cnt", payload.masked ? "已伪装" : "未伪装");

  const ps = document.getElementById("sp-presets");
  ps.innerHTML = SPOOF_PRESETS.map((x,i)=>`
    <div class="glass mgr sp-preset" data-p="${i}">
      <div class="mm">
        <div class="mn">${esc(x.n)}</div>
        <div class="mr">${esc(x.p["ro.soc.model"]||"")} · ${esc(x.p["ro.product.board"]||"")}</div>
      </div>
      <span class="ver">套用</span>
    </div>`).join("");
  ps.querySelectorAll("[data-p]").forEach(el=>el.onclick = ()=>{
    const preset = SPOOF_PRESETS[+el.dataset.p];
    renderFields({ ...spoofProps, ...preset.p });
    toast("已填入 " + preset.n + "，点「应用伪装」生效");
  });
  renderFields(spoofProps);
}
function renderFields(vals){
  const box = document.getElementById("sp-fields");
  box.innerHTML = SPOOF_KEYS.map(k=>`
    <div class="f-row">
      <span class="f-k">${esc(k.replace("ro.",""))}</span>
      <input data-k="${esc(k)}" value="${esc(vals[k]||"")}" placeholder="留空不修改">
    </div>`).join("");
}
document.getElementById("sp-apply").onclick = ()=>{
  const out = {};
  document.querySelectorAll("#sp-fields input[data-k]").forEach(i=>{
    if (i.value.trim()) out[i.dataset.k] = i.value.trim();
  });
  if (!Object.keys(out).length){ toast("没有填任何参数"); return; }
  showSheet("应用伪装", `将写入 ${Object.keys(out).length} 项系统属性，原值会自动备份。`, [
    { label:"确认写入", pri:true, fn:()=>{ closeSheet(); A.spoofApply(JSON.stringify(out)); } },
    { label:"取消", fn:closeSheet },
  ]);
};
document.getElementById("sp-restore").onclick = ()=>{
  showSheet("还原原始机型", "将从备份恢复所有被修改的属性。", [
    { label:"确认还原", pri:true, fn:()=>{ closeSheet(); A.spoofRestore(); } },
    { label:"取消", fn:closeSheet },
  ]);
};

/* ---------- 应用耗电（带图标） ---------- */
const iconCache = new Map();
function loadIcons(pkgs, cb){
  const need = pkgs.filter(p=>!iconCache.has(p));
  if (!need.length){ cb(); return; }
  try {
    const map = JSON.parse(A.pkgIcons ? A.pkgIcons(need.join(",")) : "{}");
    Object.keys(map).forEach(k=>iconCache.set(k, map[k]));
  } catch(e){}
  cb();
}

/* ---------- 分区 ---------- *//* ---------- 分区 ---------- */
function renderParts(list){
  state.partsCache = list;
  const slot = state.slot || {};
  const abDevice = !!slot.ab;
  const cur = (slot.slot || "").toLowerCase();
  document.getElementById("part-cnt").textContent =
    list.length + " 个分区" + (abDevice ? " · A/B（当前 " + (cur ? cur.toUpperCase() : "未知") + " 槽）" : "");

  const box = document.getElementById("part-list");
  if (!list.length){ box.innerHTML = '<div class="glass card"><div class="empty">没有读到分区</div></div>'; return; }

  // A/B 设备：把同一 base 的 _a/_b 排在一起
  const ordered = abDevice
    ? list.slice().sort((x,y)=> x.base === y.base ? (x.slot > y.slot ? 1 : -1) : (x.base > y.base ? 1 : -1))
    : list;

  box.innerHTML = ordered.map((p,i)=>{
    const slotBadge = "";
    return `<div class="glass mgr" data-i="${i}">
      <div class="mm">
        <div class="mn">${esc(p.name)} ${slotBadge}</div>
        <div class="mr">${esc(p.dev || "—")}${p.size?" · "+esc(p.size):""}</div>
      </div>
      <div class="acts">
        <button class="btn sm ghost" data-exp="${i}">导出</button>
        <button class="btn sm" data-flash="${i}">刷入</button>
      </div>
    </div>`;
  }).join("");

  box.querySelectorAll("[data-exp]").forEach(b=>b.onclick = ()=>{
    const p = ordered[+b.dataset.exp];
    b.disabled = true; b.textContent = "导出中";
    A.exportPart(p.name, p.dev);
  });

  box.querySelectorAll("[data-flash]").forEach(b=>b.onclick = ()=>{
    const p = ordered[+b.dataset.flash];
    // A/B 设备：始终让用户选槽位（有 _a/_b 变体的分区）
    const hasSlots = abDevice && (p.slot || (A.exists && A.exists(p.dev + "_a")));
    if (hasSlots){
      const base = p.base || p.name;
      const devA = p.dev.replace(/_[ab]$/, "") + "_a";
      const devB = p.dev.replace(/_[ab]$/, "") + "_b";
      const warn = (dev)=> (cur && dev.slice(-1) !== cur)
        ? "\n\n注意：写入的是非活动槽位，需 bootctl set-active 或下次 OTA 后才会生效。"
        : "";
      showSheet("选择槽位 · " + base,
        `分区：${p.base}  容量：${p.size || "未知"}\n当前活动槽位：${cur ? cur.toUpperCase() : "未知"}\n\n请选择要写入的分区槽位；随后会弹出文件选择器。${warn(p.dev)}`,
        [
          { label:`刷入 A 槽（${cur==="a"?"当前":"非活动"}）`, pri:true, fn:()=>{ closeSheet(); confirmFlash(base + "_a", devA); } },
          { label:`刷入 B 槽（${cur==="b"?"当前":"非活动"}）`, fn:()=>{ closeSheet(); confirmFlash(base + "_b", devB); } },
          { label:"取消", fn:closeSheet },
        ]);
    } else {
      confirmFlash(p.name, p.dev);
    }
  });
}

function confirmFlash(name, dev){
  showSheet("刷入分区 · 确认",
    `目标分区：${name}\n设备节点：${dev}\n\n下一步选择 .img 镜像，选好后会直接 dd 写入。\n写错分区或镜像不匹配可能导致无法开机，请确认来源。`,
    [
      { label:"选择镜像并刷入", pri:true, fn:()=>{ closeSheet(); A.flashPart(name, dev); } },
      { label:"取消", fn:closeSheet },
    ]);
}

/* ---------- 字库备份 ---------- */
function doFontBackup(mode){
  const out = document.getElementById("font-out");
  out.innerHTML = '<div class="glass card"><div class="empty">备份中…（' +
    (mode === "full" ? "完整备份分区较多，可能需要几分钟" : "关键分区，稍等") + '）</div></div>';
  A.fontBackup(mode);
}
document.getElementById("btn-font-key").onclick = ()=> doFontBackup("key");
document.getElementById("btn-font-full").onclick = ()=>{
  const est = state.fontEst.full || {};
  showSheet("完整备份 · 确认",
    `将备份 ${est.count || "?"} 个分区，预计占用 ${est.size || "未知"}。\n保存位置：Download/ModRepo/font-store/full/\n\n确认剩余空间充足后开始。`,
    [
      { label:"开始完整备份", pri:true, fn:()=>{ closeSheet(); doFontBackup("full"); } },
      { label:"取消", fn:closeSheet },
    ]);
};

/* ---------- 应用收录 ---------- */
const APPCAT = [
  { n:"Shizuku",      repo:"RikkaApps/Shizuku", pkg:"moe.shizuku.privileged.api", d:"把系统 API 授权给普通应用，免 root 也能用高级功能" },
  { n:"Termux",       repo:"termux/termux-app", pkg:"com.termux",                d:"Android 上的终端与 Linux 环境" },
  { n:"MT 管理器",    pkg:"bin.mt.plus",        url:"https://mt2.cn/",           d:"文件管理 / APK 反编译 / 编辑" },
  { n:"Scene",         pkg:"com.omarea.vtools",  url:"https://omarea.com/#/", d:"性能调度 / 温控 / 系统参数工具箱" },
  { n:"爱玩机工具箱",  pkg:"com.byyoung.setting", url:"https://www.coolapk.com/apk/com.byyoung.setting", d:"玩机工具合集" },
];
function renderAppCat(){
  const box = document.getElementById("appcat-list");
  document.getElementById("appcat-cnt").textContent = APPCAT.length + " 项";
  box.innerHTML = APPCAT.map((a,i)=>{
    const inst = a.pkg && state.installed[a.pkg];
    const ver = a.pkg && A.pkgVer ? (A.pkgVer(a.pkg) || "") : (inst || "");
    return `<div class="glass mgr" data-i="${i}">
      <div class="mm">
        <div class="mn">${esc(a.n)} ${ver?'<span class="ver">已装 v'+esc(ver)+'</span>':'<span class="tag">未安装</span>'}</div>
        <div class="mr">${esc(a.d)}</div>
      </div>
      <div class="acts">
        ${a.repo?'<button class="btn sm" data-dl="'+i+'">下载</button>':""}
        <button class="btn sm ghost" data-go="${i}">${a.repo?"GitHub":"官网"}</button>
      </div>
    </div>`;
  }).join("");
  box.querySelectorAll("[data-dl]").forEach(b=>b.onclick = async ()=>{
    const a = APPCAT[+b.dataset.dl];
    b.disabled = true; b.textContent = "解析";
    try {
      const r = await ghLatest(a.repo, true);
      if (!r.url) throw new Error("无 APK 资产");
      doFetchFile(r.url, a.n.replace(/\s+/g,"") + "-" + (r.ver||"latest") + ".apk", b);
    } catch(e){ b.disabled = false; b.textContent = "下载"; toast("失败：" + e.message); }
  });
  box.querySelectorAll("[data-go]").forEach(b=>b.onclick = ()=>{
    const a = APPCAT[+b.dataset.go];
    A.openUrl(a.repo ? ("https://github.com/" + a.repo) : a.url);
  });
}

/* ---------- GitHub ---------- */
async function ghLatest(repo, force){
  const key = "gh:" + repo, now = Date.now();
  if (!force && state.cache[key] && now - state.cache[key].t < 3600e3) return state.cache[key].v;
  const j = JSON.parse(await http("https://api.github.com/repos/" + repo + "/releases/latest"));
  if (!j || j.message) throw new Error(j && j.message || "no release");
  const assets = j.assets || [];
  const pick = assets.find(a=>/\.apk$/i.test(a.name) && !/debug/i.test(a.name))
            || assets.find(a=>/\.apk$/i.test(a.name))
            || assets.find(a=>/\.zip$/i.test(a.name)) || null;
  const out = { ver:String(j.tag_name||"").replace(/^v/,""), tag:j.tag_name||"",
    url: pick?pick.browser_download_url:"", file: pick?pick.name:"", size: pick?pick.size:0 };
  state.cache[key] = { t:now, v:out }; saveCache();
  return out;
}
function b64utf8(b64){
  const bin = atob(String(b64).replace(/\s/g,""));
  const bytes = new Uint8Array(bin.length);
  for (let i=0;i<bin.length;i++) bytes[i] = bin.charCodeAt(i);
  try { return new TextDecoder("utf-8").decode(bytes); } catch(e){ return decodeURIComponent(escape(bin)); }
}
async function ghFile(repo, path){
  const j = JSON.parse(await http("https://api.github.com/repos/" + repo + "/contents/" + path + "?ref=main"));
  if (!j || !j.content) throw new Error((j && j.message) || "读取失败");
  return b64utf8(j.content);
}

/* ---------- 本地 ---------- */
function refreshLocal(){
  try { state.local = JSON.parse(A.localFiles ? A.localFiles() : "[]") || []; } catch(e){ state.local = []; }
  try { state.installed = JSON.parse(A.installed ? A.installed() : "{}") || {}; } catch(e){ state.installed = {}; }
}
const normName = s => String(s||"").toLowerCase().replace(/[\s\-_.]/g,"");
function localHit(name){
  const key = normName(name);
  let best = null;
  for (const f of state.local){
    const fn = normName(f.name);
    if (fn.startsWith(key) || (key.length > 4 && fn.includes(key))){
      const m = f.name.match(/(\d+(?:\.\d+){0,3})/);
      const ver = m ? m[1] : "";
      if (!best || cmpVer(ver, best.ver) > 0) best = { ver, name:f.name, size:f.size };
    }
  }
  return best;
}

/* ---------- 模块数据 ---------- */
function parseKsu(html){
  const out = [], chunks = html.split('data-stars="');
  for (let i=1;i<chunks.length;i++){
    const c = chunks[i];
    const mSlug = c.match(/\/en\/modules\/([^/"']+)\//);
    const mName = c.match(/class="link">([^<]+)<\/a>/);
    if (!mSlug || !mName) continue;
    const mDesc = c.match(/line-clamp-3 text-muted">([^<]*)</);
    const mVer  = c.match(/label-success"[^>]*>([^<]+)</);
    const mIcon = c.match(/<img src="([^"]+)"/);
    out.push({ name:unesc(mName[1]), desc:unesc(mDesc?mDesc[1]:""), ver:unesc(mVer?mVer[1]:""),
      stars:parseInt(c)||0, src:SRC.ksu.n, kind:"ksu",
      icon: mIcon ? (mIcon[1].startsWith("http") ? mIcon[1] : "https://modules.kernelsu.org" + mIcon[1]) : "",
      url:"https://modules.kernelsu.org/en/modules/" + mSlug[1] + "/" });
  }
  return out;
}
function parseModulesJson(body){
  let j; try { j = JSON.parse(body); } catch(e){ return []; }
  const arr = Array.isArray(j) ? j : (j.modules || []);
  return arr.filter(m=>m && m.name).map(m=>({
    name:m.name, desc:m.description || m.summary || "", ver:String(m.latestVersion || m.version || ""),
    stars:0, author:m.author || "", src:SRC.ksunext.n, kind:"repo",
    repo:(String(m.repoUrl || m.sourceUrl || "").match(/github\.com\/([\w.-]+\/[\w.-]+)/)||[])[1] || "",
    icon:"", url:m.repoUrl || "",
  }));
}
function parseAlt(body){
  let j; try { j = JSON.parse(body); } catch(e){ return []; }
  return (j.modules || []).filter(m=>m && m.id).map(m=>({
    name:m.name || m.id, desc:m.description || "", ver:String(m.version || ""),
    stars:0, author:m.author || "", src:SRC.alt.n, kind:"alt", id:m.id, code:m.versionCode||0,
    repo:"", icon:"", url:"",
  }));
}
function parseLspPage(body){
  let j; try { j = JSON.parse(body); } catch(e){ return { list:[], page:0, pages:0, total:0 }; }
  const list = (j.modules || []).map(m=>{
    const title = (m.description || "").trim() || m.name;
    return { name:title, pkg:m.name, desc:m.summary || "", ver:String(m.latestRelease||"").replace(/^\d+[-_]/,""),
      stars:m.stargazerCount||0, author:m.firstContributor||"", src:SRC.lsp.n, kind:"repo", icon:"",
      repo:(String(m.url||"").match(/github\.com\/([\w.-]+\/[\w.-]+)/)||[])[1] || "", url:m.url||"" };
  });
  return { list, page:j.page||0, pages:j.pageCount||0, total:j.total||0 };
}
function parseOrg(body){
  let j; try { j = JSON.parse(body); } catch(e){ return []; }
  if (!Array.isArray(j)) return [];
  return j.map(r=>{
    const owner = (r.owner && r.owner.login) || "";
    return { name:r.name, desc:r.description || "", ver:"", stars:r.stargazers_count||0,
      author:owner, src:SRC.xposed.n, kind:"repo",
      repo: owner + "/" + r.name, url:r.html_url,
      icon: owner ? "https://github.com/" + owner + ".png?size=96" : "" };
  });
}

async function loadModTab(force){
  if (state.mod.length && !force){ renderMods(); return; }
  document.getElementById("mod-list").innerHTML = '<div class="glass mod"><div class="b"><div class="ds">读取模块索引…</div></div></div>';
  const [a,b,c] = await Promise.all([
    http(SRC.ksu.url).then(parseKsu).catch(()=>[]),
    ghFile(SRC.ksunext.repo, SRC.ksunext.path).then(parseModulesJson).catch(()=>[]),
    ghFile(SRC.alt.repo, SRC.alt.path).then(parseAlt).catch(()=>[]),
  ]);
  state.mod = [].concat(a,b,c);
  renderMods();
}
async function loadLspTab(force, more){
  if (!more && state.lsp.length && !force){ renderMods(); return; }
  if (!more) document.getElementById("mod-list").innerHTML = '<div class="glass mod"><div class="b"><div class="ds">读取 LSPosed 索引…</div></div></div>';
  const batch = more ? 10 : 5;
  const from = state.lspPage + 1;
  const tasks = [];
  for (let i=from;i<from+batch;i++){
    if (state.lspPageCount && i > state.lspPageCount) break;
    tasks.push(http(SRC.lsp.base + i + ".json").then(parseLspPage).catch(()=>null));
  }
  if (!state.lsp.length)
    tasks.push(http("https://api.github.com/orgs/" + SRC.xposed.org + "/repos?per_page=100&sort=updated").then(parseOrg).catch(()=>[]));
  const res = await Promise.all(tasks);
  for (const r of res){
    if (!r) continue;
    if (Array.isArray(r)){ if (r.length) state.lsp = state.lsp.concat(r); continue; }
    if (r.list && r.list.length){
      state.lspPage = Math.max(state.lspPage, r.page||0);
      state.lspPageCount = r.pages||0;
      state.lspTotal = r.total||0;
      state.lsp = state.lsp.concat(r.list);
    }
  }
  renderMods();
}
function loadTab(force){
  if (state.tab === "mod") loadModTab(force); else loadLspTab(force,false);
}

/* ---------- 模块卡片：有图标用图标，无图标只显示名字 ---------- */
function renderMods(){
  const list = state.tab === "mod" ? state.mod : state.lsp;
  document.getElementById("src-line").textContent = state.tab === "mod"
    ? "来源：KernelSU 官方 · KernelSU-Next · Magisk Alt Repo"
    : "来源：LSPosed 官方 · Xposed 模块组织";
  document.getElementById("src-total").textContent = state.tab === "mod"
    ? list.length + " 个模块"
    : (state.lspTotal ? list.length + " / " + state.lspTotal : list.length + " 个模块");
  const box = document.getElementById("mod-list");
  if (!list.length){ box.innerHTML = '<div class="glass mod"><div class="b"><div class="ds">暂无数据</div></div></div>'; return; }
  box.innerHTML = list.map((m,i)=>{
    const ico = m.icon
      ? `<div class="mi"><img src="${esc(m.icon)}" loading="lazy" onerror="this.parentElement.style.display='none'"></div>`
      : "";
    return `<div class="glass mod ${m.icon?"":"noicon"}" data-i="${i}">
      ${ico}
      <div class="b">
        <div class="nm">${esc(m.name)} ${m.ver?`<span class="ver">${esc(m.ver)}</span>`:""}</div>
        <div class="ds">${esc(m.desc || m.pkg || (m.author?"作者 "+m.author:"") || "")}</div>
        <div class="mt"><span class="badge-src">${esc(m.src)}</span>${m.stars?`<span>★ ${m.stars}</span>`:""}${m.pkg?`<span>${esc(m.pkg)}</span>`:""}</div>
      </div>
      <button class="dl"><svg viewBox="0 0 24 24"><path d="M12 4v11M7 11l5 5 5-5M5 20h14"/></svg></button>
    </div>`;
  }).join("");
  box.querySelectorAll(".mod").forEach(el=>{
    const m = list[+el.dataset.i];
    el.querySelector(".dl").onclick = ev=>{ ev.stopPropagation(); downloadModule(m, ev.currentTarget); };
    el.onclick = ()=> openModule(m);
  });
  const more = document.getElementById("more-btn");
  const showMore = state.tab === "lsp" && state.lspPage < state.lspPageCount;
  more.style.display = showMore ? "" : "none";
  more.textContent = showMore ? `加载更多（${state.lspPage}/${state.lspPageCount} 页）` : "";
  filterMods();
}

/* ---------- 翻译：国内 / 国外 ---------- */
async function trTencent(text){
  const payload = JSON.stringify({
    header:{ fn:"auto_translation", client_key:"browser-chrome" },
    type:"plain", model_category:"normal",
    source:{ lang:"auto", text_list:[text] }, target:{ lang:"zh" }
  });
  const r = await httpPost("https://transmart.qq.com/api/imt", payload, "application/json");
  const j = JSON.parse(r);
  const out = (j.auto_translation || [])[0];
  if (!out) throw new Error("腾讯翻译无结果");
  return out;
}
async function trGoogle(text){
  const q = encodeURIComponent(text);
  const r = await http("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=" + q);
  const j = JSON.parse(r);
  if (!j || !j[0]) throw new Error("Google 无结果");
  return j[0].map(s=>s[0]).join("");
}
async function trMyMemory(text){
  const q = encodeURIComponent(text.slice(0, 480));
  const r = await http("https://api.mymemory.translated.net/get?q=" + q + "&langpair=en|zh-CN");
  const j = JSON.parse(r);
  const out = j.responseData && j.responseData.translatedText;
  if (!out) throw new Error("MyMemory 无结果");
  return out;
}
async function translate(text){
  if (!text) return "";
  const key = text.slice(0,60) + "|" + state.tr;
  if (state.trCache[key]) return state.trCache[key];
  const order = state.tr === "cn" ? ["cn"] : state.tr === "intl" ? ["intl"] : ["cn","intl"];
  let lastErr = null;
  for (const ch of order){
    try {
      const out = ch === "cn" ? await trTencent(text) : await trGoogle(text).catch(()=>trMyMemory(text));
      state.trCache[key] = out;
      A.cfgSet("trCache", JSON.stringify(state.trCache));
      return out;
    } catch(e){ lastErr = e; }
  }
  throw lastErr || new Error("翻译失败");
}

/* ---------- 下载管理（实时进度 + 历史） ---------- */
const dlWaiters = new Map();

function renderDl(){
  const act = Object.values(state.dlAct);
  const badge = document.getElementById("dl-badge");
  const pill = document.getElementById("dl-pill");
  if (badge){
    badge.style.display = act.length > 1 ? "" : "none";
    badge.textContent = String(act.length);
  }
  if (pill){
    pill.style.display = act.length ? "" : "none";
    const t = document.getElementById("dl-pill-txt");
    if (t && act.length === 1){
      const d = act[0];
      const pct = d.total > 0 ? Math.floor(d.done*100/d.total) : 0;
      t.textContent = pct + "%";
    } else if (t) t.textContent = "下载中";
  }
  const box = document.getElementById("dl-active");
  if (box){
    box.innerHTML = act.length ? act.map(d=>{
      const pct = d.total > 0 ? Math.min(100, Math.floor(d.done*100/d.total)) : 0;
      return `<div class="glass dl-item">
        <div class="top"><span>${esc(d.name)}</span><span>${pct}%</span></div>
        <div class="bar"><i style="width:${pct}%"></i></div>
        <div class="st"><span>${fmtBytes(d.done)} / ${d.total>0?fmtBytes(d.total):"未知"}</span><span>${esc(d.status||"下载中")}</span></div>
      </div>`;
    }).join("") : '<div class="glass card"><div class="empty">没有正在进行的下载</div></div>';
  }
  const hist = document.getElementById("dl-history");
  if (hist){
    hist.innerHTML = state.dlHist.length ? state.dlHist.map((h,i)=>`
      <div class="glass mod-row">
        <div class="mm">
          <div class="mn">${esc(h.name)}</div>
          <div class="mr">${esc(h.path)}</div>
          <div class="mr">${fmtBytes(h.size||0)} · ${esc(h.time||"")}${h.ok===false?" · 失败":""}</div>
        </div>
        <div class="acts">
          <button class="ico-btn" data-inst="${i}">安装</button>
          <button class="ico-btn" data-delh="${i}">✕</button>
        </div>
      </div>`).join("") : '<div class="glass card"><div class="empty">暂无历史</div></div>';
    hist.querySelectorAll("[data-inst]").forEach(b=>b.onclick = ()=>{
      const h = state.dlHist[+b.dataset.inst];
      askInstall(h.path, h.name);
    });
    hist.querySelectorAll("[data-delh]").forEach(b=>b.onclick = ()=>{
      const i = +b.dataset.delh, h = state.dlHist[i];
      showSheet("删除这条记录", `文件：${h.name}\n路径：${h.path}`, [
        { label:"删除记录与文件", pri:true, fn:()=>{
            const ok = A.deletePath ? A.deletePath(h.path) : false;
            state.dlHist.splice(i,1); saveHist(); renderDl(); refreshLocal();
            closeSheet(); toast(ok ? "已删除" : "记录已删，文件删除失败");
        }},
        { label:"只删记录", fn:()=>{ state.dlHist.splice(i,1); saveHist(); renderDl(); closeSheet(); } },
        { label:"取消", fn:closeSheet },
      ]);
    });
  }
}
function fmtBytes(b){
  b = Number(b)||0;
  if (b >= 1073741824) return (b/1073741824).toFixed(2) + " GB";
  if (b >= 1048576) return (b/1048576).toFixed(1) + " MB";
  if (b >= 1024) return (b/1024).toFixed(0) + " KB";
  return b + " B";
}
function saveHist(){
  if (state.dlHist.length > 100) state.dlHist.length = 100;
  A.cfgSet("dlHist", JSON.stringify(state.dlHist));
}
function dlStart(url, name){
  return new Promise((resolve)=>{
    const id = "d" + (++seq);
    state.dlAct[id] = { id, name, url, done:0, total:0, status:"下载中" };
    renderDl();
    dlWaiters.set(id, resolve);
    A.fetchFile(id, url, name);
  });
}
let dlRaf = 0;
function scheduleDlRender(){
  if (dlRaf) return;
  dlRaf = requestAnimationFrame(()=>{ dlRaf = 0; renderDl(); });
}
function onDl(d, ev){
  const cur = state.dlAct[d.id];
  if (ev === "dlp"){
    if (cur){ cur.done = d.done; cur.total = d.total; scheduleDlRender(); }
    return;
  }
  delete state.dlAct[d.id];
  const w = dlWaiters.get(d.id);
  dlWaiters.delete(d.id);
  renderDl();
  if (d.ok){
    const name = d.path.split("/").pop();
    state.dlHist.unshift({ name, path:d.path, size:Number(d.size)||0,
      time:new Date().toLocaleString("zh-CN",{hour12:false}).slice(0,16), ok:true });
    saveHist(); renderDl();
    refreshLocal();
    if (w) w({ ok:true, path:d.path });
    else askInstall(d.path, name);
  } else {
    toast("下载失败：" + (d.err||"未知"));
    if (w) w({ ok:false, err:d.err });
  }
}
function askInstall(path, name){
  const isApk = /\.apk$/i.test(name);
  showSheet(isApk ? "下载完成 · 安装应用" : "下载完成 · 刷入模块",
    `文件：${name}\n保存路径：${path}\n\n` +
    (isApk ? "将执行 pm install -r 安装该应用。" : "将调用 ksud / magisk 刷入该模块，需要 root。"),
    [
      { label: isApk ? "立即安装" : "立即刷入", pri:true, fn:()=>{
          closeSheet();
          showSheet("正在处理…", path, []);
          if (isApk) A.installApk(path); else A.installZip(path);
      }},
      { label:"稍后", fn:closeSheet },
    ]);
}
let installWaiter = null;
function flashZip(path){
  return new Promise((resolve)=>{
    installWaiter = resolve;
    A.installZip(path);
  });
}
function onInstalled(d){
  closeSheet();
  showSheet(d.ok ? "完成" : "失败",
    `文件：${String(d.path).split("/").pop()}\n路径：${d.path}\n\n${String(d.msg||"").slice(0,1200)}`,
    [{ label:"好", pri:true, fn:closeSheet }]);
  refreshLocal();
  if (installWaiter){ const w = installWaiter; installWaiter = null; w(d); }
}
async function downloadManager(m, btn){
  btn.disabled = true; btn.textContent = "解析";
  try {
    const r = m._latest || await ghLatest(m.repo, true);
    if (!r.url) throw new Error("无可用安装包");
    btn.textContent = "下载中";
    const res = await dlStart(r.url, m.n.replace(/\s+/g,"") + "-" + (r.ver||"latest") + ".apk");
    btn.disabled = false; btn.textContent = "下载最新版";
    if (res.ok) askInstall(res.path, res.path.split("/").pop());
  } catch(e){ btn.disabled = false; btn.textContent = "下载最新版"; toast("失败：" + e.message); }
}
async function altZipFor(id, code){
  if (!state.altZip){
    try {
      const j = JSON.parse(await ghFile("Magisk-Modules-Alt-Repo/json", "modules.json"));
      state.altZip = {}; (j.modules||[]).forEach(x=>{ state.altZip[x.id] = x.zip_url; });
    } catch(e){ state.altZip = {}; }
  }
  return state.altZip[id] || ("https://github.com/Magisk-Modules-Alt-Repo/" + id + "/archive/" + (code||"HEAD") + ".zip");
}
async function downloadModule(m, btn){
  btn.disabled = true; btn.textContent = "";
  try {
    let url = "", name = "";
    if (m.kind === "ksu"){
      const page = await http(m.url);
      const z = page.match(/https:\/\/github\.com\/[^"'\s]+\/releases\/download\/[^"'\s]+\.(?:zip|apk)/);
      if (!z) throw new Error("模块页无直链");
      url = z[0]; name = z[0].split("/").pop();
    } else if (m.kind === "alt"){
      url = await altZipFor(m.id, m.code);
      name = (m.id||"module") + "-" + (m.ver||"latest") + ".zip";
    } else {
      if (!m.repo) throw new Error("缺少仓库地址");
      const r = await ghLatest(m.repo);
      if (!r.url) throw new Error("无发布资产");
      url = r.url;
      name = m.name.replace(/\s+/g,"").slice(0,40) + "-" + (r.ver||"latest") + (/\.apk$/i.test(r.file||"") ? ".apk" : ".zip");
    }
    toast("开始下载 " + name);
    const res = await dlStart(url, name);
    btn.disabled = false;
    btn.innerHTML = '<svg viewBox="0 0 24 24"><path d="M12 4v11M7 11l5 5 5-5M5 20h14"/></svg>';
    if (res.ok) askInstall(res.path, res.path.split("/").pop());
  } catch(e){
    btn.disabled = false;
    btn.innerHTML = '<svg viewBox="0 0 24 24"><path d="M12 4v11M7 11l5 5 5-5M5 20h14"/></svg>';
    toast("下载失败：" + e.message);
  }
}

/* ---------- 已安装模块页 ---------- */
function renderModsList(){
  const box = document.getElementById("mods-list");
  if (!box) return;
  const all = state.modsAll || [];
  const roots = all.filter(m=>m.kind !== "xposed");
  const xps = all.filter(m=>m.kind === "xposed");
  set("mc-root", String(roots.length));
  set("mc-xp", String(xps.length));

  const kind = state.modKind || "root";
  const list = kind === "xposed" ? xps : roots;
  set("mods-cnt", (kind === "xposed" ? "Xposed " : "Root ") + list.length + " 个");
  if (!list.length){
    box.innerHTML = `<div class="glass card"><div class="empty">没有 ${kind === "xposed" ? "Xposed" : "Root"} 模块</div></div>`;
    return;
  }

  const draw = ()=>{
    box.innerHTML = list.map((m,i)=>{
      const icon = kind === "xposed" ? iconCache.get(m.id) : null;
      return `<div class="glass mod-row" data-i="${i}">
        ${icon ? `<div class="use-ico"><img src="data:image/png;base64,${icon}"></div>` : ""}
        <div class="mm">
          <div class="mn">${esc(m.name)}
            ${m.enabled?"":'<span class="tag warn">已禁用</span>'}${m.pending?'<span class="tag">待生效</span>':""}</div>
          <div class="mt" style="display:flex;gap:10px;font-size:11px;color:var(--mut);margin-top:3px">
            ${m.ver?"<span>v"+esc(m.ver)+"</span>":""}${m.size?"<span>"+esc(m.size)+"</span>":""}
            ${kind==="xposed"?"<span>"+esc(m.id)+"</span>":""}
          </div>
        </div>
      </div>`;
    }).join("");
    box.querySelectorAll(".mod-row").forEach(el=>{
      el.onclick = ()=> openModuleDetail(list[+el.dataset.i]);
    });
  };
  draw();
  if (kind === "xposed") loadIcons(xps.map(m=>m.id).slice(0, 80), ()=>{
    if (!box.querySelector(".use-ico img")) draw();
  });
}

/* ---------- 模块详情 ---------- */
let curModItem = null;
function openModuleDetail(m){
  curModItem = m;
  openSub("mdet", m.name, m.kind === "xposed" ? "Xposed 模块" : "Root 模块");
  set("md-title2", m.name);
  set("md-n", m.name);
  set("md-v", m.ver ? ("v" + m.ver + (m.verCode ? " (" + m.verCode + ")" : "")) : "—");
  set("md-s", m.size || "—");
  set("md-k", m.kind === "xposed" ? "Xposed / LSPosed" : "Root（KSU / Magisk）");
  document.getElementById("md-d2").textContent = m.desc || "（该模块没有提供介绍）";

  const acts = [];
  if (m.kind !== "xposed"){
    acts.push({ label: m.enabled ? "禁用模块" : "启用模块", ghost: !m.enabled,
      fn: ()=>{ A.setModuleEnabled(m.id, !m.enabled); closeAct(); } });
  }
  if (m.action) acts.push({ label:"运行动作脚本", fn: ()=>{
      closeAct();
      showSheet("正在执行…", "action.sh", []);
      A.moduleAction(m.id);
  }});
  if (m.kind === "xposed") acts.push({ label:"在 LSPosed 中管理", ghost:true, fn: ()=>{ A.openManager(); } });
  acts.push({ label:"卸载", danger:true, fn: ()=>{
      closeSheet();
      showSheet("卸载模块 · 确认",
        `模块：${m.name}\n${m.kind === "xposed" ? "包名：" + m.id : "目录：" + m.dir}\n\n此操作不可撤销。`,
        [
          { label:"确认卸载", pri:true, fn:()=>{ closeSheet(); A.uninstallModule(m.id, m.kind || "root"); } },
          { label:"取消", fn:closeSheet },
        ]);
  }});

  const box = document.getElementById("md-acts");
  box.innerHTML = "";
  acts.forEach(a=>{
    const b = document.createElement("button");
    b.className = "btn wide" + (a.ghost ? " ghost" : "");
    if (a.danger){ b.style.background = "transparent"; b.style.color = "#d9534f";
      b.style.border = "1px solid rgba(217,83,79,.4)"; }
    b.textContent = a.label;
    b.onclick = a.fn;
    box.appendChild(b);
  });
}
function closeAct(){ /* 占位：动作后刷新列表 */ if (A.modules) A.modules(); }

/* ---------- LSPosed 一键刷入 ---------- */
const LSP_LOCAL = "/storage/emulated/0/Download/advanced/Magisk模块/LSPosed_7948.zip";
let lspBundleInfo = null;
function renderLspBundle(d){
  if (d.error){ toast("内置模块解包失败：" + d.error); return; }
  lspBundleInfo = d;
  showSheet("刷入 LSPosed · " + (d.ver || ""),
    `内置模块：${d.name || "LSPosed"}  ${d.ver || ""}（versionCode ${d.verCode || "-"}）\n` +
    `大小：${fmtBytes(d.size || 0)}\n\n是否同时刷入 Zygisk Next（LSPosed 的 Zygisk 支持依赖它）？`,
    [
      { label:"只刷 LSPosed", fn:()=>{ closeSheet(); doFlashLsp(false); } },
      { label:"同时刷 Zygisk Next", pri:true, fn:()=>{ closeSheet(); doFlashLsp(true); } },
      { label:"取消", fn:closeSheet },
    ]);
}
function flashLsposed(){
  showSheet("准备内置模块…", "正在解包 LSPosed", []);
  A.lspBundle();
}
async function flashLsposedOld(){
  const has = A.exists && A.exists(LSP_LOCAL);
  showSheet("刷入 LSPosed",
    `模块文件：${has ? LSP_LOCAL : "本地没有，将从 LSPosed/LSPosed 下载最新 zip"}\n\n` +
    "是否同时刷入 Zygisk Next（LSPosed 的 Zygisk 支持依赖它）？",
    [
      { label:"只刷 LSPosed", fn:()=>{ closeSheet(); doFlashLsp(false); } },
      { label:"同时刷 Zygisk Next", pri:true, fn:()=>{ closeSheet(); doFlashLsp(true); } },
      { label:"取消", fn:closeSheet },
    ]);
}
async function doFlashLsp(withZygisk){
  showSheet("准备刷入…", "解析文件…", []);
  try {
    let lspPath = null;
    if (lspBundleInfo && lspBundleInfo.path) lspPath = lspBundleInfo.path;
    else if (A.exists && A.exists(LSP_LOCAL)) lspPath = LSP_LOCAL;
    else {
      const r = await ghLatest("LSPosed/LSPosed", true);
      if (!r.url) throw new Error("LSPosed 无 zip 资产");
      const d1 = await dlStart(r.url, "LSPosed-" + (r.ver||"latest") + ".zip");
      if (!d1.ok) throw new Error("LSPosed 下载失败");
      lspPath = d1.path;
    }
    let zp = null;
    if (withZygisk){
      document.getElementById("sheet-body").textContent = "下载 Zygisk Next…";
      const z = await ghLatest("Dr-TSNG/ZygiskNext", true);
      if (!z.url) throw new Error("Zygisk Next 无 zip 资产");
      const d2 = await dlStart(z.url, "ZygiskNext-" + (z.ver||"latest") + ".zip");
      if (!d2.ok) throw new Error("Zygisk Next 下载失败");
      zp = d2.path;
    }
    let log = "";
    document.getElementById("sheet-body").textContent = "刷入 LSPosed…";
    const r1 = await flashZip(lspPath);
    log += "LSPosed: " + (r1.ok ? "成功" : "失败") + "\n" + String(r1.msg||"").slice(0,600) + "\n";
    if (zp){
      document.getElementById("sheet-body").textContent = "刷入 Zygisk Next…";
      const r2 = await flashZip(zp);
      log += "\nZygisk Next: " + (r2.ok ? "成功" : "失败") + "\n" + String(r2.msg||"").slice(0,600);
    }
    showSheet("刷入结果", log + "\n\n重启后生效。", [{ label:"好", pri:true, fn:closeSheet }]);
    if (A.modules) A.modules();
if (A.cfgGet("probe","0") === "1"){ setTimeout(()=>{ if (A.fontEstimate){ A.fontEstimate("key"); A.fontEstimate("full"); } }, 2500); }
  } catch(e){
    showSheet("刷入失败", String(e.message||e), [{ label:"好", pri:true, fn:closeSheet }]);
  }
}

/* ---------- 模块详情整页 ---------- */
let curMod = null;
function openModule(m){
  curMod = m;
  openSub("mdetail", m.name, m.src || "");
  document.getElementById("md-title").textContent = m.name;

  const ico = m.icon
    ? `<div class="mi"><img src="${esc(m.icon)}" onerror="this.parentNode.innerHTML='${esc((m.name[0]||"M").toUpperCase())}'"></div>`
    : `<div class="mi">${esc((m.name[0]||"M").toUpperCase())}</div>`;
  document.getElementById("md-head").innerHTML = `${ico}
    <div class="b">
      <div class="nm">${esc(m.name)}</div>
      <div class="sub">${esc(m.pkg || m.repo || "")}${m.ver ? " · v" + esc(m.ver) : ""}</div>
    </div>`;

  document.getElementById("md-desc").textContent = m.desc || "（暂无简介）";
  const tro = document.getElementById("md-trout");
  tro.classList.add("hidden"); tro.textContent = "";

  document.getElementById("md-meta").innerHTML = [
    ["来源仓库", m.src], ["包名", m.pkg], ["版本", m.ver], ["作者", m.author],
    ["Star", m.stars || ""], ["GitHub", m.repo],
  ].filter(x=>x[1]).map(([k,v])=>`<div class="kv"><span class="lbl">${esc(k)}</span><b style="word-break:break-all;text-align:right">${esc(v)}</b></div>`).join("")
    || '<div class="empty">无更多信息</div>';

  const gh = document.getElementById("md-gh");
  gh.style.display = (m.url || m.repo) ? "" : "none";
  gh.onclick = ()=> A.openUrl(m.url || ("https://github.com/" + m.repo));

  document.getElementById("md-dl").onclick = (ev)=> downloadModule(m, ev.currentTarget);
}
document.getElementById("md-tr").onclick = async ()=>{
  const box = document.getElementById("md-trout");
  box.classList.remove("hidden");
  if (!curMod) return;
  const text = curMod.desc || curMod.name;
  box.textContent = "翻译中…";
  try { box.textContent = await translate(text); }
  catch(e){ box.textContent = "翻译失败：" + e.message; }
};
document.getElementById("md-copy").onclick = ()=>{
  if (!curMod) return;
  const t = curMod.desc || curMod.name;
  try { A.copyText ? A.copyText(t) : null; toast("已复制"); } catch(e){ toast("复制失败"); }
};

/* ---------- 过滤 ---------- */
function filterMods(){
  const q = (document.getElementById("q-mod").value||"").trim().toLowerCase();
  const sort = (document.querySelector("#sort-mod .chip.on")||{}).dataset?.s || "all";
  const box = document.getElementById("mod-list");
  const list = state.tab === "mod" ? state.mod : state.lsp;
  const items = Array.from(box.querySelectorAll(".mod"));
  if (sort !== "all"){
    items.sort((a,b)=>{
      const ma = list[+a.dataset.i], mb = list[+b.dataset.i];
      if (!ma || !mb) return 0;
      return sort === "hot" ? (mb.stars||0)-(ma.stars||0) : cmpVer(mb.ver, ma.ver);
    });
    items.forEach(el=>box.appendChild(el));
  }
  items.forEach(el=>{
    const m = list[+el.dataset.i];
    const hay = ((m.name||"")+(m.desc||"")+(m.author||"")+(m.repo||"")+(m.pkg||"")).toLowerCase();
    el.style.display = (!q || hay.includes(q)) ? "" : "none";
  });
}
document.getElementById("q-mod").oninput = filterMods;
document.querySelectorAll("#sort-mod .chip").forEach(b=>b.onclick = ()=>{
  document.querySelectorAll("#sort-mod .chip").forEach(x=>x.classList.remove("on"));
  b.classList.add("on"); filterMods();
});
document.querySelectorAll("#seg-tab button").forEach(b=>b.onclick = ()=>{
  document.querySelectorAll("#seg-tab button").forEach(x=>x.classList.remove("on"));
  b.classList.add("on"); state.tab = b.dataset.v;
  document.getElementById("q-mod").value = "";
  loadTab();
});
document.getElementById("more-btn").onclick = ()=> loadLspTab(false, true);

/* ---------- 设置 ---------- */
function bindSeg(id, key, after){
  const box = document.getElementById(id);
  box.querySelectorAll("button").forEach(b=>{
    b.classList.toggle("on", b.dataset.v === state[key]);
    b.onclick = ()=>{
      box.querySelectorAll("button").forEach(x=>x.classList.remove("on"));
      b.classList.add("on"); state[key] = b.dataset.v; saveCfg(); after && after();
    };
  });
}
bindSeg("seg-mode","mode", applyTheme);
bindSeg("seg-bar","bar", applyTheme);
bindSeg("seg-tr","tr");
function renderThemes(){
  const box = document.getElementById("theme-grid");
  box.innerHTML = THEMES.map(t=>`
    <button class="theme-b ${t.id===state.theme?"on":""}" data-t="${t.id}" style="--t1:${t.c[0]};--t2:${t.c[1]}">
      <i></i><span>${t.n}</span></button>`).join("");
  box.querySelectorAll(".theme-b").forEach(b=>b.onclick = ()=>{
    state.theme = b.dataset.t; saveCfg(); renderThemes(); applyTheme();
  });
}
function renderSubs(){
  const box = document.getElementById("sub-list");
  if (!state.subs.length){ box.innerHTML = '<div class="empty">还没有订阅源</div>'; return; }
  box.innerHTML = state.subs.map((s,i)=>`
    <div class="sub ${s.on===false?"off":""}">
      <div class="si"><div class="sn">${esc(s.name)} ${s.on===false?'<span class="tag">停用</span>':""}</div>
        <div class="su">${esc(s.url)}</div></div>
      <div class="ob">
        <button class="mini" data-a="toggle" data-i="${i}">${s.on===false?"启用":"停用"}</button>
        <button class="mini" data-a="edit" data-i="${i}">编辑</button>
        <button class="mini del" data-a="del" data-i="${i}">删除</button>
      </div></div>`).join("");
  box.querySelectorAll(".mini").forEach(b=>b.onclick = ()=>{
    const i = +b.dataset.i, a = b.dataset.a;
    if (a === "toggle") state.subs[i].on = state.subs[i].on === false;
    else if (a === "del"){ if (!confirm("删除「"+state.subs[i].name+"」？")) return; state.subs.splice(i,1); }
    else if (a === "edit"){
      const s = state.subs[i];
      const name = prompt("订阅名称", s.name); if (name === null) return;
      const url = prompt("链接 / owner/repo", s.url); if (url === null) return;
      state.subs[i] = { name:name.trim()||url.trim(), url:url.trim(), on:s.on!==false };
    }
    saveCfg(); renderSubs();
  });
}
document.getElementById("add-sub").onclick = ()=>{
  const url = prompt("输入 GitHub 仓库（owner/repo）或 JSON 索引链接");
  if (!url || !url.trim()) return;
  const u = url.trim();
  const gh = u.match(/github\.com\/([\w.-]+)\/([\w.-]+)/);
  state.subs.push({ name: gh ? gh[1]+"/"+gh[2].replace(/\.git$/,"") : u.replace(/^https?:\/\//,"").slice(0,40), url:u, on:true });
  saveCfg(); renderSubs(); toast("已添加订阅");
};
document.getElementById("btn-export").onclick = ()=>{
  const payload = JSON.stringify({ app:"Omnix", version:A.version?A.version():"1.6.0", time:new Date().toISOString(),
    mode:state.mode, theme:state.theme, bar:state.bar, tr:state.tr, subs:state.subs }, null, 2);
  const r = String(A.exportBackup(payload)).split("\u0001");
  toast(r[0]==="ok" ? "已导出：" + r[1] : "导出失败：" + r[1]);
};
document.getElementById("btn-import").onclick = ()=> A.importBackup();
function doImportText(txt){
  try {
    const j = JSON.parse(txt);
    if (j.app !== "Omnix" && j.app !== "ModRepo"){ toast("不是 ModRepo 备份"); return; }
    ["mode","theme","bar","tr"].forEach(k=>{ if (j[k]) state[k] = j[k]; });
    if (Array.isArray(j.subs)) state.subs = j.subs;
    saveCfg(); applyTheme(); renderThemes(); renderSubs();
    document.querySelectorAll("#seg-mode button").forEach(b=>b.classList.toggle("on", b.dataset.v===state.mode));
    document.querySelectorAll("#seg-bar button").forEach(b=>b.classList.toggle("on", b.dataset.v===state.bar));
    document.querySelectorAll("#seg-tr button").forEach(b=>b.classList.toggle("on", b.dataset.v===state.tr));
    toast("导入完成");
  } catch(e){ toast("导入失败：格式错误"); }
}

/* ---------- 温控常驻通知 ---------- */
(function bindTuneNotify(){
  const b = document.getElementById("btn-tune-notify");
  if (!b) return;
  let on = false;
  b.onclick = ()=>{
    on = !on;
    if (on){ A.startTuneNotify(); b.textContent = "关闭"; b.classList.add("ghost"); }
    else { A.stopTuneNotify(); b.textContent = "开启"; b.classList.remove("ghost"); }
  };
})();

/* ---------- 权限管理 ---------- */
const PERMS = [
  { k:"storage", n:"存储",      d:"下载与导出文件到 Download" },
  { k:"root",    n:"Root 授权", d:"刷入、冻结、分区、字库等操作依赖" },
];
function renderPerms(){
  const box = document.getElementById("perm-list");
  if (!box) return;
  let st = {};
  try { st = JSON.parse(A.permStatus ? A.permStatus() : "{}"); } catch(e){ st = {}; }
  const granted = PERMS.filter(p=>st[p.k]);
  set("perm-cnt", granted.length + "/" + PERMS.length + " 已授权");
  box.innerHTML = PERMS.map(p=>{
    const ok = !!st[p.k];
    return `<div class="glass mgr">
      <div class="mm">
        <div class="mn">${esc(p.n)}
          <span class="perm-state ${ok?"ok":""}"><i></i>${ok?"已授权":"未授权"}</span></div>
        <div class="mr">${esc(p.d)}</div>
      </div>
      <button class="btn sm ${ok?"ghost":""}" data-perm="${p.k}">${ok?"查看":"去授权"}</button>
    </div>`;
  }).join("");
  box.querySelectorAll("[data-perm]").forEach(b=>b.onclick = ()=>{
    A.openPerm(b.dataset.perm);
    setTimeout(renderPerms, 1200);
  });
}
document.getElementById("btn-perms").onclick = ()=>{
  openSub("perms","权限管理","通知 / 使用情况 / 电池 / 存储 / Root");
  renderPerms();
};
document.addEventListener("visibilitychange", ()=>{
  if (document.hidden){
    return;
  }
  if (state.sub === "perms") renderPerms();
  if (state.mode === "auto" && A.env) A.env();
});

/* ---------- 打赏与交流群 ---------- */


document.getElementById("btn-support").onclick = ()=> openSub("support","打赏与交流群","");
document.getElementById("btn-copy-group").onclick = ()=>{
  if (A.copyText) A.copyText("1137331073");
  toast("群号已复制：1137331073");
};
document.getElementById("btn-open-group").onclick = ()=>{
  A.openUrl("https://qm.qq.com/q/moshovKCSk");
};

/* ---------- 弹层 ---------- */
function showSheet(title, body, acts){
  document.getElementById("sheet-title").textContent = title;
  document.getElementById("sheet-body").textContent = body;
  const box = document.getElementById("sheet-act");
  box.innerHTML = "";
  (acts||[]).forEach(a=>{
    const b = document.createElement("button");
    b.className = "btn wide" + (a.pri?"":" ghost");
    b.textContent = a.label; b.onclick = a.fn;
    box.appendChild(b);
  });
  document.getElementById("sheet").classList.remove("hidden");
  state.sheetOpen = true; updateDepth();
}
function closeSheet(){
  document.getElementById("sheet").classList.add("hidden");
  state.sheetOpen = false; updateDepth();
}
document.getElementById("sheet-x").onclick = closeSheet;
document.getElementById("sheet").onclick = e=>{ if (e.target.id === "sheet") closeSheet(); };

const TERMS = `一、协议范围
本协议是你与 Omnix（奥米，下称"本应用"）之间的使用约定。安装或使用本应用，即视为你已阅读并同意本协议全部内容。

二、功能说明
本应用提供：模块仓库检索与下载（KernelSU / LSPosed 等）、模块安装与刷入、Root 管理器收录、应用冻结/启用、电池与分区信息、分区导出与刷入、字库（关键分区）备份、应用安装包备份导出、文本翻译、下载管理等功能。

三、使用要求
1. 本应用需要 Root 权限才能使用部分功能，请确认你已理解相关风险；
2. 你应确保所安装、刷入的模块来源可信，并自行备份数据；
3. 你不得将本应用用于违反所在地法律法规的用途。

四、免责声明
1. 刷入模块、冻结应用、写分区、备份/恢复关键分区操作均由你主动触发，属高危行为，可能导致系统异常、数据丢失、设备无法开机；
2. 因使用本应用产生的任何直接或间接损失，由你自行承担，开发者不承担法律或经济责任；
3. 本应用不生产、不托管、不修改任何模块文件，模块内容由第三方作者负责。

五、赞赏说明
应用内赞赏入口为自愿行为，金额固定，不构成购买服务、解锁功能或任何形式的对价关系。

六、协议变更
功能调整时本协议会同步更新，更新后的协议随应用版本发布即生效。`;

const LEGAL = `侵权与内容说明
1. 本应用仅在设备本地请求各模块仓库（如 modules.kernelsu.org、modules.lsposed.org、GitHub 上的公开仓库）的公开索引与下载地址，不存储、不镜像任何模块文件；
2. 应用内展示的模块名称、简介、图标版权归各自作者所有，本应用仅作展示用途；
3. 若你是权利人对某条目有异议，可通过交流群（1137331073）或项目 GitHub 提出，核实后将移除相关引用。

隐私说明
1. 本应用不上传、不收集你的设备信息；
2. 已安装模块、应用列表、电池与分区信息仅在本机内存中读取并展示，不做外发；
3. 下载记录、主题、订阅源等配置仅保存在本机，可随备份文件导出。

风险提示
分区刷入、字库备份与恢复、模块刷入等操作会直接写入系统分区，请务必确认目标分区与镜像匹配。因误操作造成的设备损坏由使用者自行承担。`;

document.getElementById("btn-terms").onclick = ()=> showSheet("用户协议", TERMS);
document.getElementById("btn-legal").onclick = ()=> showSheet("侵权与免责说明", LEGAL);
document.getElementById("gh-link").onclick = ()=> A.openUrl("https://github.com/yunyu614/omnix");

/* 清空下载历史（可选是否删本地文件） */
(function bindClear(){
  const el = document.getElementById("dl-clear");
  if (!el) return;
  el.onclick = ()=>{
    if (!state.dlHist.length){ toast("历史已经是空的"); return; }
    const n = state.dlHist.length;
    showSheet("清空下载记录",
      `共 ${n} 条记录。\n\n可选择是否同时删除本地文件（Download/ModRepo 下的安装包）。`,
      [
        { label:"只清空记录（保留文件）", pri:true, fn:()=>{
            state.dlHist = []; saveHist(); renderDl(); closeSheet(); toast("记录已清空");
        }},
        { label:"清空并删除本地文件", fn:()=>{
            let ok = 0;
            state.dlHist.forEach(h=>{ if (A.deletePath && A.deletePath(h.path)) ok++; });
            state.dlHist = []; saveHist(); renderDl(); refreshLocal();
            closeSheet(); toast("已删除文件 " + ok + " 个");
        }},
        { label:"取消", fn:closeSheet },
      ]);
  };
})();

/* ---------- toast ---------- */
let toastTimer = null;
function toast(msg){
  let el = document.getElementById("toast");
  if (!el){
    el = document.createElement("div");
    el.id = "toast";
    el.style.cssText = "position:fixed;left:50%;bottom:calc(96px + env(safe-area-inset-bottom));transform:translateX(-50%);"+
      "background:var(--glass);color:var(--txt);border:1px solid var(--line);padding:10px 18px;border-radius:14px;"+
      "font-size:13px;font-weight:600;z-index:80;box-shadow:0 16px 36px -18px rgba(var(--shadow),.9);"+
      "-webkit-backdrop-filter:blur(20px);backdrop-filter:blur(20px);max-width:86%;text-align:center;opacity:0;transition:opacity .2s";
    document.body.appendChild(el);
  }
  el.textContent = msg; el.style.opacity = "1";
  clearTimeout(toastTimer);
  toastTimer = setTimeout(()=>{ el.style.opacity = "0"; }, 2600);
}
window.onerror = (m,s,l)=>{ if (A.log) A.log("JSERR " + m + " @" + l); };

/* ---------- 启动 ---------- */
loadCfg();
try { if (A.systemDark) sysDark = A.systemDark() === "1"; } catch(e){}
applyTheme(); renderThemes(); renderSubs(); renderTools(); 
document.getElementById("ab-ver").textContent = A.version ? A.version() : "1.6.0";
refreshLocal();
if (A.env) A.env(); else fillEnv({ root:false });
if (A.modules) A.modules();
if (A.cfgGet("probe","0") === "1"){ setTimeout(()=>{ if (A.fontEstimate){ A.fontEstimate("key"); A.fontEstimate("full"); } }, 2500); }
state.dlHist = (()=>{ try { return JSON.parse(A.cfgGet("dlHist","[]"))||[]; } catch(e){ return []; } })();
renderDl();
updateDepth();
if (A.log && A.sample) A.log("SAMPLE0 " + A.sample());
if (A.cfgGet("agreed","") !== "1") document.getElementById("welcome").classList.remove("hidden");
document.getElementById("welcome-ok").onclick = ()=>{
  A.cfgSet("agreed","1");
  document.getElementById("welcome").classList.add("hidden");
};
if (A.log) A.log("BOOT " + (A.version?A.version():"?") + " pages=" + document.querySelectorAll(".page").length);
if (A.cfgGet("probe","0") === "1"){
  setTimeout(()=>{ if (A.detail) A.detail(); if (A.battery) A.battery(); if (A.parts) A.parts(); if (A.apps) A.apps(); }, 1500);
  setTimeout(async ()=>{
    try { const t = await translate("Android anti-detect module with profile isolation"); if (A.log) A.log("TR ok " + t.slice(0,30)); }
    catch(e){ if (A.log) A.log("TR fail " + e.message); }
  }, 4000);
}
