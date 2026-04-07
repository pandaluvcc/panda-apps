<template>
  <div class="app-home">
    <div class="hero">
      <div class="hero-wordmark">
        <svg width="26" height="26" viewBox="0 0 52 52" fill="none">
          <rect class="lr lr1" x="6" y="6" width="16" height="16" rx="4" fill="#9fe870"/>
          <rect class="lr lr2" x="30" y="6" width="16" height="16" rx="4" fill="#163300"/>
          <rect class="lr lr3" x="6" y="30" width="16" height="16" rx="4" fill="#454745"/>
          <rect class="lr lr4" x="30" y="30" width="16" height="16" rx="4" fill="#e8ebe6"/>
        </svg>
        <span class="wordmark-text">Panda</span>
      </div>
    </div>

    <div class="cards-section">
      <p class="section-label">我的应用</p>
      <div class="app-card" @click="goToApp('/grid')">
        <div class="card-top"><div class="card-icon grid-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg></div><span class="card-badge">交易策略</span></div>
        <h2 class="card-title">网格计划</h2>
        <p class="card-desc">加密货币网格交易策略管理</p>
        <button class="pill-btn pill-dark">打开 →</button>
      </div>
      <div class="app-card" @click="goToApp('/snap/calendar')">
        <div class="card-top"><div class="card-icon snap-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg></div><span class="card-badge">财务记录</span></div>
        <h2 class="card-title">快记账</h2>
        <p class="card-desc">个人账单随手记</p>
        <button class="pill-btn pill-dark">打开 →</button>
      </div>
      <div class="app-card app-card--coming">
        <div class="card-top"><div class="card-icon coming-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><span class="card-badge badge-muted">开发中</span></div>
        <h2 class="card-title title-muted">更多应用</h2>
        <p class="card-desc">敬请期待</p>
        <button class="pill-btn pill-outline" disabled>即将推出</button>
      </div>
      <p class="bottom-copy">© 2025 Panda Apps</p>
    </div>
  </div>
</template>

<script setup>
defineProps({ goToApp: { type: Function, required: true } })
</script>

<style scoped>
.app-home {
  --w-black:#0e0f0c;--w-white:#ffffff;--w-green:#9fe870;--w-green-dk:#163300;
  --w-mint:#e2f6d5;--w-surface:#f4f4f4;--w-slate:#454745;--w-gray:#868685;--w-border:#e2e2e4;
  min-height:100dvh;background:var(--w-white);display:flex;flex-direction:column;
  touch-action:pan-y;overscroll-behavior-x:none;font-feature-settings:"calt" 1;
}
.hero { background:var(--w-white); padding:16px 20px 20px; display:flex; flex-direction:column; gap:0; }
.hero-wordmark{display:flex;align-items:center;gap:8px;}
.wordmark-text{font-size:15px;font-weight:600;color:var(--w-black);letter-spacing:-.2px;}
@keyframes pop-in{0%{opacity:0;transform:scale(0.3)}72%{transform:scale(1.14)}100%{opacity:1;transform:scale(1)}}
.lr{opacity:0;transform-origin:center;animation:pop-in .36s cubic-bezier(0.34,1.56,0.64,1) forwards;}
.lr1{animation-delay:.04s}.lr2{animation-delay:.11s}.lr3{animation-delay:.18s}.lr4{animation-delay:.25s}
.pill-btn{display:inline-flex;align-items:center;gap:6px;border:none;border-radius:9999px;font-size:14px;font-weight:600;min-height:44px;padding:0 22px;cursor:pointer;transition:transform .18s cubic-bezier(0.34,1.56,0.64,1),opacity .15s ease;-webkit-tap-highlight-color:transparent;align-self:flex-start;font-feature-settings:"calt" 1;}
.pill-btn:hover{transform:scale(1.05)}.pill-btn:active{transform:scale(0.95)}
.pill-dark{background:var(--w-black);color:var(--w-white);}
.pill-outline{background:transparent;color:var(--w-slate);border:1.5px solid var(--w-border);cursor:default;}
.pill-btn:disabled{pointer-events:none;}
.cards-section{flex:1;background:var(--w-surface);border-radius:24px 24px 0 0;padding:24px 16px;padding-bottom:calc(72px + env(safe-area-inset-bottom));display:flex;flex-direction:column;gap:12px;}
.section-label{font-size:11px;font-weight:600;letter-spacing:.8px;text-transform:uppercase;color:var(--w-gray);margin:0 4px 4px;}
.app-card{background:#fff;border-radius:30px;box-shadow:rgba(14,15,12,.12) 0 0 0 1px;padding:22px 22px 20px;display:flex;flex-direction:column;gap:10px;cursor:pointer;transition:transform .18s cubic-bezier(0.34,1.56,0.64,1);-webkit-tap-highlight-color:transparent;}
.app-card:active{transform:scale(0.975);}
.app-card--coming{opacity:.45;cursor:default;}.app-card--coming:active{transform:none;}
.card-top{display:flex;align-items:center;justify-content:space-between;}
.card-icon{width:42px;height:42px;border-radius:50%;display:flex;align-items:center;justify-content:center;}
.grid-icon{background:#dbeafe;color:#1d4ed8;}
.snap-icon{background:#fff3e0;color:#b45309;}
.coming-icon{background:#ebebeb;color:var(--w-gray);}
.card-badge{font-size:11px;font-weight:600;letter-spacing:.5px;color:var(--w-gray);background:var(--w-surface);padding:4px 10px;border-radius:9999px;}
.badge-muted{color:#b5babe;}
.card-title{font-size:22px;font-weight:900;line-height:.95;letter-spacing:-.4px;color:var(--w-black);margin:4px 0 0;font-feature-settings:"calt" 1;}
.title-muted{color:var(--w-slate);}
.card-desc{font-size:13px;line-height:1.5;color:var(--w-gray);margin:0;}
.bottom-copy{text-align:center;font-size:11px;color:#c2c5be;margin:8px 0 0;}
</style>
