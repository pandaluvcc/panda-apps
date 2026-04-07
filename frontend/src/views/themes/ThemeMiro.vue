<template>
  <div class="app-home">
    <div class="hero">
      <div class="hero-wordmark">
        <svg width="26" height="26" viewBox="0 0 52 52" fill="none">
          <rect class="lr lr1" x="6" y="6" width="16" height="16" rx="4" fill="#5b76fe"/>
          <rect class="lr lr2" x="30" y="6" width="16" height="16" rx="4" fill="#ffc6c6"/>
          <rect class="lr lr3" x="6" y="30" width="16" height="16" rx="4" fill="#c3faf5"/>
          <rect class="lr lr4" x="30" y="30" width="16" height="16" rx="4" fill="#ffe6cd"/>
        </svg>
        <span class="wordmark-text">Panda Apps</span>
      </div>
    </div>

    <div class="cards-section">
      <div class="app-card card-coral" @click="goToApp('/grid')">
        <div class="card-top"><div class="card-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg></div><span class="card-tag">交易策略</span></div>
        <h2 class="card-title">网格计划</h2>
        <p class="card-desc">加密货币网格交易策略管理</p>
        <div class="card-footer"><span class="card-cta">打开应用 →</span></div>
      </div>
      <div class="app-card card-teal" @click="goToApp('/snap/calendar')">
        <div class="card-top"><div class="card-icon"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg></div><span class="card-tag">财务记录</span></div>
        <h2 class="card-title">快记账</h2>
        <p class="card-desc">个人账单随手记</p>
        <div class="card-footer"><span class="card-cta">打开应用 →</span></div>
      </div>
      <div class="app-card card-coming">
        <div class="card-top"><div class="card-icon icon-coming"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div><span class="card-tag tag-muted">开发中</span></div>
        <h2 class="card-title title-muted">更多应用</h2>
        <p class="card-desc">敬请期待</p>
      </div>
    </div>
    <p class="bottom-copy">© 2025 Panda Apps</p>
  </div>
</template>

<script setup>
defineProps({ goToApp: { type: Function, required: true } })
</script>

<style scoped>
.app-home{--m-black:#1c1c1e;--m-white:#ffffff;--m-blue:#5b76fe;--m-blue-dk:#2a41b6;--m-slate:#555a6a;--m-gray:#a5a8b5;--m-border:#c7cad5;--m-ring:rgb(224,226,232);--m-coral-l:#ffc6c6;--m-teal-l:#c3faf5;--m-surface:#f7f8f5;min-height:100dvh;background:var(--m-white);display:flex;flex-direction:column;touch-action:pan-y;overscroll-behavior-x:none;}
.hero { padding:16px 20px 20px; display:flex; flex-direction:column; gap:0; border-bottom:1px solid var(--m-ring); }
.hero-wordmark{display:flex;align-items:center;gap:8px;}
.wordmark-text{font-size:15px;font-weight:600;color:var(--m-black);letter-spacing:-.2px;}
@keyframes pop-in{0%{opacity:0;transform:scale(0.3)}72%{transform:scale(1.14)}100%{opacity:1;transform:scale(1)}}
.lr{opacity:0;transform-origin:center;animation:pop-in .36s cubic-bezier(0.34,1.56,0.64,1) forwards;}
.lr1{animation-delay:.04s}.lr2{animation-delay:.11s}.lr3{animation-delay:.18s}.lr4{animation-delay:.25s}
.cards-section{padding:16px 16px 4px;display:flex;flex-direction:column;gap:10px;}
.app-card{border-radius:20px;padding:20px 20px 18px;display:flex;flex-direction:column;gap:10px;cursor:pointer;box-shadow:var(--m-ring) 0 0 0 1px;transition:box-shadow .15s ease,transform .12s ease;-webkit-tap-highlight-color:transparent;}
.app-card:active{transform:scale(0.977);box-shadow:var(--m-ring) 0 0 0 2px;}
.card-coral{background:var(--m-coral-l);}
.card-teal{background:var(--m-teal-l);}
.card-coming{background:#f8f8fb;opacity:.5;cursor:default;}.card-coming:active{transform:none;}
.card-top{display:flex;align-items:center;justify-content:space-between;}
.card-icon{width:40px;height:40px;border-radius:50%;background:rgba(255,255,255,.65);display:flex;align-items:center;justify-content:center;color:var(--m-black);box-shadow:var(--m-ring) 0 0 0 1px;}
.icon-coming{color:var(--m-gray);}
.card-tag{font-size:10px;font-weight:600;letter-spacing:.6px;text-transform:uppercase;color:var(--m-slate);background:rgba(255,255,255,.55);padding:3px 9px;border-radius:9999px;}
.tag-muted{color:var(--m-gray);}
.card-title{font-size:22px;font-weight:500;line-height:1.15;letter-spacing:-.6px;color:var(--m-black);margin:0;}
.title-muted{color:var(--m-slate);}
.card-desc{font-size:13px;line-height:1.5;color:var(--m-slate);margin:0;}
.card-footer{margin-top:4px;}
.card-cta{font-size:13px;font-weight:600;color:var(--m-black);opacity:.7;}
.bottom-copy{text-align:center;font-size:11px;color:var(--m-gray);padding:16px 0 calc(72px + env(safe-area-inset-bottom));margin:0;}
</style>
