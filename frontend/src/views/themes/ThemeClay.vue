<template>
  <div class="app-home">
    <div class="hero">
      <div class="hero-wordmark">
        <svg width="26" height="26" viewBox="0 0 52 52" fill="none">
          <rect class="lr lr1" x="6" y="6" width="16" height="16" rx="4" fill="#078a52"/>
          <rect class="lr lr2" x="30" y="6" width="16" height="16" rx="4" fill="#3bd3fd"/>
          <rect class="lr lr3" x="6" y="30" width="16" height="16" rx="4" fill="#fbbd41"/>
          <rect class="lr lr4" x="30" y="30" width="16" height="16" rx="4" fill="#c1b0ff"/>
        </svg>
        <span class="wordmark-text">Panda Apps</span>
      </div>
    </div>

    <div class="cards-wrap">
      <div class="app-card card-blueberry" @click="goToApp('/grid')">
        <div class="card-top"><span class="card-label">交易策略</span><div class="card-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg></div></div>
        <h2 class="card-title">网格计划</h2>
        <p class="card-desc">加密货币网格交易策略管理</p>
        <button class="card-btn" @click.stop="goToApp('/grid')">打开应用</button>
      </div>
      <div class="app-card card-slushie" @click="goToApp('/snap/calendar')">
        <div class="card-top"><span class="card-label">财务记录</span><div class="card-icon"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg></div></div>
        <h2 class="card-title">快记账</h2>
        <p class="card-desc">个人账单随手记</p>
        <button class="card-btn" @click.stop="goToApp('/snap/calendar')">打开应用</button>
      </div>
      <div class="app-card card-coming">
        <div class="card-top"><span class="card-label label-muted">开发中</span><div class="card-icon icon-muted"><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div></div>
        <h2 class="card-title title-muted">更多应用</h2>
        <p class="card-desc">敬请期待</p>
      </div>
    </div>

    <footer class="page-footer"><p class="footer-copy">© 2025 Panda Apps</p></footer>
  </div>
</template>

<script setup>
defineProps({ goToApp: { type: Function, required: true } })
</script>

<style scoped>
.app-home{--cl-cream:#faf9f7;--cl-black:#000000;--cl-silver:#9f9b93;--cl-charcoal:#55534e;--cl-oat:#dad4c8;--cl-oat-lt:#eee9df;--cl-matcha-800:#02492a;--cl-matcha-600:#078a52;--cl-slushie-800:#0089ad;--cl-slushie-500:#3bd3fd;--shadow-clay:rgba(0,0,0,.10) 0px 1px 1px,rgba(0,0,0,.04) 0px -1px 1px inset,rgba(0,0,0,.05) 0px -0.5px 1px;min-height:100dvh;background:var(--cl-cream);display:flex;flex-direction:column;touch-action:pan-y;overscroll-behavior-x:none;}
.hero { padding:16px 20px 20px; display:flex; flex-direction:column; gap:0; border-bottom:1px solid var(--cl-oat); }
.hero-wordmark{display:flex;align-items:center;gap:8px;}
.wordmark-text{font-size:15px;font-weight:600;color:var(--cl-black);letter-spacing:-.2px;}
@keyframes pop-in{0%{opacity:0;transform:scale(0.3)}72%{transform:scale(1.14)}100%{opacity:1;transform:scale(1)}}
.lr{opacity:0;transform-origin:center;animation:pop-in .36s cubic-bezier(0.34,1.56,0.64,1) forwards;}
.lr1{animation-delay:.04s}.lr2{animation-delay:.11s}.lr3{animation-delay:.18s}.lr4{animation-delay:.25s}
.cards-wrap{padding:16px 16px 0;display:flex;flex-direction:column;gap:10px;}
.app-card{border-radius:24px;padding:22px 22px 20px;display:flex;flex-direction:column;gap:10px;cursor:pointer;transition:transform .2s cubic-bezier(0.34,1.56,0.64,1),box-shadow .2s ease;-webkit-tap-highlight-color:transparent;}
.card-blueberry{background:#01418d;border:1px solid #3a6bbf;box-shadow:var(--shadow-clay);color:#fff;}
.card-blueberry:active{transform:rotateZ(-2deg) scale(0.975);box-shadow:rgb(0,0,0) -4px 4px;}
.card-slushie{background:var(--cl-slushie-800);border:1px solid var(--cl-slushie-500);box-shadow:var(--shadow-clay);color:#fff;}
.card-slushie:active{transform:rotateZ(2deg) scale(0.975);box-shadow:rgb(0,0,0) -4px 4px;}
.card-coming{background:transparent;border:1px dashed var(--cl-oat);opacity:.6;cursor:default;color:var(--cl-charcoal);}.card-coming:active{transform:none;}
.card-top{display:flex;align-items:center;justify-content:space-between;}
.card-label{font-size:10px;font-weight:600;letter-spacing:1.08px;text-transform:uppercase;color:rgba(255,255,255,.6);}
.label-muted{color:var(--cl-silver);}
.card-icon{width:36px;height:36px;border-radius:50%;background:rgba(255,255,255,.15);display:flex;align-items:center;justify-content:center;color:#fff;}
.icon-muted{background:var(--cl-oat-lt);color:var(--cl-silver);}
.card-title{font-size:26px;font-weight:600;line-height:1.1;letter-spacing:-.6px;color:#fff;margin:4px 0 0;}
.title-muted{color:var(--cl-charcoal);}
.card-desc{font-size:13px;line-height:1.5;color:rgba(255,255,255,.65);margin:0;}
.card-btn{align-self:flex-start;margin-top:4px;height:36px;padding:0 18px;background:rgba(255,255,255,.15);color:#fff;border:1px solid rgba(255,255,255,.3);border-radius:1584px;font-size:13px;font-weight:500;cursor:pointer;box-shadow:var(--shadow-clay);transition:transform .2s cubic-bezier(0.34,1.56,0.64,1),box-shadow .2s ease,background .15s ease;-webkit-tap-highlight-color:transparent;}
.card-btn:hover{transform:rotateZ(-8deg) translateY(-4px);box-shadow:rgb(0,0,0) -5px 5px;background:rgba(255,255,255,.25);}
.card-btn:active{transform:rotateZ(-4deg) scale(0.95);box-shadow:rgb(0,0,0) -2px 2px;}
.page-footer{margin-top:auto;padding:16px 20px calc(72px + env(safe-area-inset-bottom));text-align:center;border-top:1px solid var(--cl-oat);}
.footer-copy{font-size:11px;color:var(--cl-silver);margin:0;}
</style>
