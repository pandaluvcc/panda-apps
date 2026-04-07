<template>
  <div class="app-home">
    <div class="hero">
      <div class="hero-wordmark">
        <svg width="28" height="28" viewBox="0 0 52 52" fill="none">
          <rect class="lr lr1" x="6" y="6" width="16" height="16" rx="4" fill="#4f55f1"/>
          <rect class="lr lr2" x="30" y="6" width="16" height="16" rx="4" fill="rgba(255,255,255,0.55)"/>
          <rect class="lr lr3" x="6" y="30" width="16" height="16" rx="4" fill="rgba(255,255,255,0.35)"/>
          <rect class="lr lr4" x="30" y="30" width="16" height="16" rx="4" fill="rgba(255,255,255,0.18)"/>
        </svg>
        <span class="wordmark-text">Panda</span>
      </div>
    </div>

    <div class="cards-section">
      <div class="app-card" @click="goToApp('/grid')">
        <div class="card-header">
          <div class="card-icon grid-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 7 13.5 15.5 8.5 10.5 2 17"/><polyline points="16 7 22 7 22 13"/></svg>
          </div>
          <span class="card-tag">交易</span>
        </div>
        <div class="card-body">
          <h2 class="card-title">网格计划</h2>
          <p class="card-desc">加密货币网格交易策略管理</p>
        </div>
        <button class="pill-btn pill-dark">打开</button>
      </div>

      <div class="app-card" @click="goToApp('/snap/calendar')">
        <div class="card-header">
          <div class="card-icon snap-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>
          </div>
          <span class="card-tag">记账</span>
        </div>
        <div class="card-body">
          <h2 class="card-title">快记账</h2>
          <p class="card-desc">个人账单随手记</p>
        </div>
        <button class="pill-btn pill-dark">打开</button>
      </div>

      <div class="app-card app-card--coming">
        <div class="card-header">
          <div class="card-icon coming-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          </div>
          <span class="card-tag card-tag--muted">开发中</span>
        </div>
        <div class="card-body">
          <h2 class="card-title card-title--muted">更多应用</h2>
          <p class="card-desc">敬请期待</p>
        </div>
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
  --c-bg: #191c1f; --c-white: #ffffff; --c-surface: #f4f4f4;
  --c-brand: #4f55f1; --c-slate: #505a63; --c-gray: #8d969e; --c-border: #e2e2e4;
  min-height: 100dvh; background: var(--c-bg); display: flex; flex-direction: column;
  touch-action: pan-y; overscroll-behavior-x: none;
}
.hero {
  background: var(--c-bg);
  padding: 16px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 0;
}
.hero-wordmark { display: flex; align-items: center; gap: 8px; }
.wordmark-text { font-size: 14px; font-weight: 500; color: rgba(255,255,255,0.5); }
@keyframes pop-in { 0%{opacity:0;transform:scale(0.3)} 72%{transform:scale(1.14)} 100%{opacity:1;transform:scale(1)} }
.lr { opacity: 0; transform-origin: center; animation: pop-in 0.36s cubic-bezier(0.34,1.56,0.64,1) forwards; }
.lr1{animation-delay:.04s}.lr2{animation-delay:.11s}.lr3{animation-delay:.18s}.lr4{animation-delay:.25s}
.cards-section { flex: 1; background: var(--c-surface); border-radius: 20px 20px 0 0; padding: 24px 16px; padding-bottom: calc(72px + env(safe-area-inset-bottom)); display: flex; flex-direction: column; gap: 10px; }
.app-card { background: #fff; border-radius: 18px; padding: 18px 18px 16px; display: flex; flex-direction: column; gap: 14px; transition: transform .12s ease, opacity .12s ease; -webkit-tap-highlight-color: transparent; cursor: pointer; }
.app-card:active { transform: scale(0.975); opacity: .88; }
.app-card--coming { opacity: .45; cursor: default; } .app-card--coming:active { transform: none; opacity: .45; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-icon { width: 42px; height: 42px; border-radius: 11px; display: flex; align-items: center; justify-content: center; }
.grid-icon { background: #eeeffe; color: var(--c-brand); } .snap-icon { background: #fff0eb; color: #e05a28; } .coming-icon { background: #ebebeb; color: var(--c-gray); }
.card-tag { font-size: 10px; font-weight: 600; letter-spacing: .7px; text-transform: uppercase; color: var(--c-gray); background: var(--c-surface); padding: 3px 9px; border-radius: 9999px; }
.card-tag--muted { color: #b5babe; }
.card-body { display: flex; flex-direction: column; gap: 3px; }
.card-title { font-size: 20px; font-weight: 500; letter-spacing: -.3px; color: var(--c-bg); margin: 0; }
.card-title--muted { color: var(--c-slate); }
.card-desc { font-size: 13px; color: var(--c-gray); margin: 0; line-height: 1.5; }
.pill-btn { align-self: flex-start; border: none; border-radius: 9999px; font-size: 13px; font-weight: 500; min-height: 36px; padding: 0 20px; display: flex; align-items: center; cursor: pointer; transition: opacity .15s ease; -webkit-tap-highlight-color: transparent; }
.pill-btn:active { opacity: .7; }
.pill-dark { background: var(--c-bg); color: var(--c-white); }
.pill-outline { background: transparent; color: var(--c-slate); border: 1.5px solid var(--c-border); cursor: default; }
.pill-btn:disabled { pointer-events: none; }
.bottom-copy { text-align: center; font-size: 11px; color: #c5c8cb; margin: 8px 0 0; }
</style>
