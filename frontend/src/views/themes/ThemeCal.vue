<template>
  <div class="app-home">
    <div class="hero">
      <div class="hero-wordmark">
        <svg width="24" height="24" viewBox="0 0 52 52" fill="none">
          <rect class="lr lr1" x="6" y="6" width="16" height="16" rx="4" fill="#242424"/>
          <rect class="lr lr2" x="30" y="6" width="16" height="16" rx="4" fill="#898989"/>
          <rect class="lr lr3" x="6" y="30" width="16" height="16" rx="4" fill="#c8c8c8"/>
          <rect class="lr lr4" x="30" y="30" width="16" height="16" rx="4" fill="#e8e8e8"/>
        </svg>
        <span class="wordmark-text">Panda</span>
      </div>
    </div>

    <div class="cards-section">
      <div class="app-card" @click="goToApp('/grid')">
        <div class="card-inner">
          <div class="card-left">
            <div class="card-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg></div>
            <div class="card-text"><h2 class="card-title">网格计划</h2><p class="card-desc">加密货币网格交易策略管理</p></div>
          </div>
          <button class="pill-badge">打开</button>
        </div>
      </div>
      <div class="app-card" @click="goToApp('/snap/calendar')">
        <div class="card-inner">
          <div class="card-left">
            <div class="card-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg></div>
            <div class="card-text"><h2 class="card-title">快记账</h2><p class="card-desc">个人账单随手记</p></div>
          </div>
          <button class="pill-badge">打开</button>
        </div>
      </div>
      <div class="app-card card-coming">
        <div class="card-inner">
          <div class="card-left">
            <div class="card-icon icon-muted"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
            <div class="card-text"><h2 class="card-title title-muted">更多应用</h2><p class="card-desc">开发中，敬请期待</p></div>
          </div>
          <span class="badge-muted">即将推出</span>
        </div>
      </div>
    </div>
    <div class="footer-section"><p class="footer-copy">© 2025 Panda Apps</p></div>
  </div>
</template>

<script setup>
defineProps({ goToApp: { type: Function, required: true } })
</script>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Cal+Sans&display=swap');
.app-home{--c-charcoal:#242424;--c-white:#ffffff;--c-mid-gray:#898989;--c-lt-gray:#f5f5f5;--shadow-card:rgba(19,19,22,.7) 0px 1px 5px -4px,rgba(34,42,53,.08) 0px 0px 0px 1px,rgba(34,42,53,.05) 0px 4px 8px 0px;--shadow-card-hover:rgba(19,19,22,.7) 0px 2px 8px -4px,rgba(34,42,53,.12) 0px 0px 0px 1px,rgba(34,42,53,.08) 0px 6px 14px 0px;min-height:100dvh;background:var(--c-white);display:flex;flex-direction:column;touch-action:pan-y;overscroll-behavior-x:none;font-family:'Inter',-apple-system,sans-serif;}
.hero { padding:16px 20px 20px; display:flex; flex-direction:column; gap:0; border-bottom:1px solid rgba(34,42,53,.08); }
.hero-wordmark{display:flex;align-items:center;gap:8px;}
.wordmark-text{font-family:'Cal Sans','Inter',sans-serif;font-size:16px;font-weight:600;color:var(--c-charcoal);}
@keyframes pop-in{0%{opacity:0;transform:scale(0.3)}72%{transform:scale(1.12)}100%{opacity:1;transform:scale(1)}}
.lr{opacity:0;transform-origin:center;animation:pop-in .36s cubic-bezier(0.34,1.56,0.64,1) forwards;}
.lr1{animation-delay:.04s}.lr2{animation-delay:.11s}.lr3{animation-delay:.18s}.lr4{animation-delay:.25s}
.cards-section{padding:12px 16px;display:flex;flex-direction:column;gap:10px;}
.app-card{background:var(--c-white);border-radius:12px;box-shadow:var(--shadow-card);cursor:pointer;transition:box-shadow .2s ease,transform .12s ease;-webkit-tap-highlight-color:transparent;}
.app-card:active{box-shadow:var(--shadow-card-hover);transform:scale(0.978);}
.card-coming{cursor:default;opacity:.5;}.card-coming:active{transform:none;box-shadow:var(--shadow-card);}
.card-inner{display:flex;align-items:center;justify-content:space-between;padding:18px;gap:12px;}
.card-left{display:flex;align-items:center;gap:14px;min-width:0;}
.card-icon{width:40px;height:40px;border-radius:8px;background:var(--c-lt-gray);display:flex;align-items:center;justify-content:center;color:var(--c-charcoal);flex-shrink:0;box-shadow:rgba(34,42,53,.06) 0 0 0 1px;}
.icon-muted{color:var(--c-mid-gray);}
.card-text{min-width:0;}
.card-title{font-family:'Cal Sans','Inter',sans-serif;font-size:16px;font-weight:600;line-height:1.2;color:var(--c-charcoal);margin:0 0 2px;}
.title-muted{color:var(--c-mid-gray);}
.card-desc{font-size:12px;line-height:1.5;color:var(--c-mid-gray);margin:0;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}
.pill-badge{flex-shrink:0;height:32px;padding:0 14px;background:var(--c-charcoal);color:var(--c-white);border:none;border-radius:9999px;font-size:12px;font-weight:600;cursor:pointer;box-shadow:rgba(255,255,255,.15) 0 2px 0 inset;transition:opacity .15s ease;-webkit-tap-highlight-color:transparent;white-space:nowrap;}
.pill-badge:active{opacity:.7;}
.badge-muted{display:inline-flex;align-items:center;height:32px;padding:0 14px;border-radius:9999px;font-size:12px;font-weight:600;color:var(--c-mid-gray);box-shadow:rgba(34,42,53,.10) 0 0 0 1px;white-space:nowrap;}
.footer-section{margin-top:auto;padding:20px 20px calc(72px + env(safe-area-inset-bottom));text-align:center;border-top:1px solid rgba(34,42,53,.06);}
.footer-copy{font-size:11px;color:var(--c-mid-gray);margin:0;}
</style>
